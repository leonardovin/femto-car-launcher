package io.github.seijikohara.femto.data.fonts

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenTypeCmapTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `reads coverage from a format 4 subtable`() {
        val font = fontCoveringUppercaseLatin()

        assertEquals(
            setOf('A'.code, 'Z'.code),
            OpenTypeCmap.coveredOrNull(font, listOf('A'.code, 'Z'.code, 'g'.code, '中'.code)),
        )
    }

    @Test
    fun `a file without a cmap table yields null so the caller falls back`() {
        val file = temp.newFile("empty.ttf").apply { writeBytes(ByteArray(64)) }

        assertNull(OpenTypeCmap.coveredOrNull(file, listOf('A'.code)))
    }

    // A minimal sfnt: one 'cmap' table with a Windows/BMP format 4 subtable
    // mapping A..Z to glyphs 1..26, plus the mandatory 0xFFFF end segment.
    private fun fontCoveringUppercaseLatin(): File {
        val subtable =
            bytes {
                writeShort(4) // format
                writeShort(32) // length: 14 header + 4 * (2 * segCount) + 2 pad
                writeShort(0) // language
                writeShort(4) // segCountX2
                writeShort(4) // searchRange
                writeShort(1) // entrySelector
                writeShort(0) // rangeShift
                writeShort('Z'.code) // endCode[0]
                writeShort(0xFFFF) // endCode[1]
                writeShort(0) // reservedPad
                writeShort('A'.code) // startCode[0]
                writeShort(0xFFFF) // startCode[1]
                writeShort((1 - 'A'.code) and 0xFFFF) // idDelta[0]
                writeShort(1) // idDelta[1]
                writeShort(0) // idRangeOffset[0]
                writeShort(0) // idRangeOffset[1]
            }
        val cmap =
            bytes {
                writeShort(0) // version
                writeShort(1) // numTables
                writeShort(3) // platform: Windows
                writeShort(1) // encoding: Unicode BMP
                writeInt(12) // subtable offset from the cmap start
                write(subtable)
            }
        val font =
            bytes {
                writeInt(0x00010000) // sfnt version
                writeShort(1) // numTables
                writeShort(16) // searchRange
                writeShort(0) // entrySelector
                writeShort(0) // rangeShift
                writeInt(0x636d6170) // 'cmap'
                writeInt(0) // checksum
                writeInt(28) // table offset: 12 header + 16 record
                writeInt(cmap.size)
                write(cmap)
            }
        return temp.newFile("latin.ttf").apply { writeBytes(font) }
    }

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).use(block) }.toByteArray()
}
