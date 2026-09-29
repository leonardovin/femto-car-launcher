package io.github.seijikohara.femto.data.fonts

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

private const val TAG = "OpenTypeCmap"

private const val TTC_TAG = 0x74746366L // 'ttcf'
private const val CMAP_TABLE_TAG = 0x636d6170L // 'cmap'

/**
 * Best-effort reader for a font file's own character map (Android 9 fork).
 *
 * Below API 29 `Paint.hasGlyph` answers through the system fallback chain, so
 * every installed face "covers" CJK. Reading the file's 'cmap' table instead
 * asks the face itself. Handles the Unicode subtable formats fonts actually
 * ship (4 for the BMP, 12 for full Unicode), a bare sfnt and the first font of
 * a TrueType Collection; returns null for anything else so the caller can fall
 * back to the Paint probe.
 */
internal object OpenTypeCmap {
    fun coveredOrNull(
        file: File,
        codePoints: List<Int>,
    ): Set<Int>? =
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val lookup = readLookup(raf) ?: return@use null
                codePoints.filterTo(mutableSetOf()) { lookup(it) }
            }
        }.onFailure { Log.w(TAG, "cmap read failed for ${file.name}", it) }
            .getOrNull()

    private fun readLookup(raf: RandomAccessFile): ((Int) -> Boolean)? {
        val sfntOffset = if (raf.u32(0) == TTC_TAG) raf.u32(12) else 0L
        val numTables = raf.u16(sfntOffset + 4)
        val cmapOffset =
            (0 until numTables)
                .map { sfntOffset + 12 + it * 16L }
                .firstOrNull { raf.u32(it) == CMAP_TABLE_TAG }
                ?.let { raf.u32(it + 8) }
                ?: return null
        val subtables =
            (0 until raf.u16(cmapOffset + 2)).map { index ->
                val record = cmapOffset + 4 + index * 8L
                Triple(raf.u16(record), raf.u16(record + 2), cmapOffset + raf.u32(record + 4))
            }
        // Prefer full-Unicode tables, then BMP ones (platform 3 = Windows,
        // 0 = Unicode); the format decides the parser.
        val preferred =
            listOf(3 to 10, 0 to 4, 0 to 6, 3 to 1, 0 to 3, 0 to 1, 0 to 0)
                .firstNotNullOfOrNull { (platform, encoding) ->
                    subtables.firstOrNull { it.first == platform && it.second == encoding }
                } ?: return null
        val offset = preferred.third
        return when (raf.u16(offset)) {
            4 -> format4Lookup(raf, offset)
            12 -> format12Lookup(raf, offset)
            else -> null
        }
    }

    private fun format4Lookup(
        raf: RandomAccessFile,
        offset: Long,
    ): (Int) -> Boolean {
        val segCountX2 = raf.u16(offset + 6)
        val endCodes = offset + 14
        val startCodes = endCodes + segCountX2 + 2
        val idDeltas = startCodes + segCountX2
        val idRangeOffsets = idDeltas + segCountX2
        return lookup@{ codePoint ->
            if (codePoint > 0xFFFF) return@lookup false
            // endCode is sorted ascending: the first segment ending at or after
            // the code point is the only one that can hold it.
            val segment =
                lowerBound(segCountX2 / 2) { raf.u16(endCodes + it * 2L) >= codePoint }
                    ?: return@lookup false
            val start = raf.u16(startCodes + segment * 2L)
            if (start > codePoint) return@lookup false
            val delta = raf.u16(idDeltas + segment * 2L)
            val rangeOffsetPos = idRangeOffsets + segment * 2L
            val rangeOffset = raf.u16(rangeOffsetPos)
            val glyph =
                if (rangeOffset == 0) {
                    (codePoint + delta) and 0xFFFF
                } else {
                    raf
                        .u16(rangeOffsetPos + rangeOffset + (codePoint - start) * 2L)
                        .let { if (it == 0) 0 else (it + delta) and 0xFFFF }
                }
            glyph != 0
        }
    }

    private fun format12Lookup(
        raf: RandomAccessFile,
        offset: Long,
    ): (Int) -> Boolean {
        val groups = raf.u32(offset + 12)
        return lookup@{ codePoint ->
            // Groups are sorted by endCharCode, the same shape as format 4.
            val index =
                lowerBound(groups.toInt()) { raf.u32(offset + 16 + it * 12L + 4) >= codePoint }
                    ?: return@lookup false
            raf.u32(offset + 16 + index * 12L) <= codePoint
        }
    }

    // The first index in 0 until [size] for which the monotonic [atOrAfter]
    // holds, or null when none does.
    private inline fun lowerBound(
        size: Int,
        atOrAfter: (Int) -> Boolean,
    ): Int? {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (atOrAfter(mid)) high = mid else low = mid + 1
        }
        return low.takeIf { it < size }
    }
}

private fun RandomAccessFile.u32(offset: Long): Long {
    seek(offset)
    return (read().toLong() shl 24) or (read().toLong() shl 16) or (read().toLong() shl 8) or read().toLong()
}

private fun RandomAccessFile.u16(offset: Long): Int {
    seek(offset)
    return (read() shl 8) or read()
}
