package io.github.seijikohara.femto.data.privileged

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private const val TAG = "ShizukuGateway"
private const val PERMISSION_REQUEST_CODE = 0x5A5A

/** Where the launcher stands with Shizuku (ADB-level access on the head unit). */
internal enum class ShizukuState {
    // The Shizuku service is not running (or not installed) on this device.
    UNAVAILABLE,

    // Shizuku runs, but the user has not allowed the launcher yet.
    NEEDS_PERMISSION,

    // Shizuku runs and the launcher may use it.
    READY,
}

/** A privileged command's trimmed stdout and exit status; -1 when it could not run. */
internal data class ShellResult(
    val stdout: String,
    val exitCode: Int,
) {
    val succeeded: Boolean get() = exitCode == 0
}

/**
 * The launcher's one door to Shizuku, ported from shizuku-bottom-bar's
 * PrivilegedShell / ShizukuUtils. Everything privileged — the head-unit
 * self-setup and the vehicle service binder — goes through here, and all of it
 * degrades to "unavailable" on devices without Shizuku (phones, other units),
 * where the launcher behaves exactly as upstream.
 */
internal object ShizukuGateway {
    private val mutableState = MutableStateFlow(ShizukuState.UNAVAILABLE)

    val state: StateFlow<ShizukuState> = mutableState.asStateFlow()

    @Volatile
    private var started = false

    /** Idempotent; wires the binder and permission listeners once per process. */
    fun start() {
        if (started) return
        started = true
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { refresh() }
            Shizuku.addBinderDeadListener { mutableState.value = ShizukuState.UNAVAILABLE }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        }.onFailure { Log.w(TAG, "Shizuku listeners unavailable", it) }
        refresh()
    }

    fun refresh() {
        mutableState.value =
            runCatching {
                when {
                    !Shizuku.pingBinder() -> ShizukuState.UNAVAILABLE
                    Shizuku.isPreV11() -> ShizukuState.UNAVAILABLE
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                    else -> ShizukuState.NEEDS_PERMISSION
                }
            }.getOrDefault(ShizukuState.UNAVAILABLE)
    }

    /** Shows Shizuku's own allow dialog; the result lands through [state]. */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { Log.w(TAG, "Shizuku permission request failed", it) }
    }

    /** Runs [command] with ADB-level privileges; never throws. */
    suspend fun run(vararg command: String): ShellResult =
        withContext(Dispatchers.IO) {
            if (state.value != ShizukuState.READY) return@withContext ShellResult("", -1)
            runCatching { runBlockingProcess(arrayOf(*command)) }
                .onFailure { Log.w(TAG, "command failed: ${command.joinToString(" ")}", it) }
                .getOrDefault(ShellResult("", -1))
        }

    /**
     * A system service binder whose calls run with Shizuku's identity (how the
     * bottom bar reaches the vehicle service); null when unavailable.
     */
    fun systemService(name: String): IBinder? =
        if (state.value != ShizukuState.READY) {
            null
        } else {
            runCatching { SystemServiceHelper.getSystemService(name)?.let(::ShizukuBinderWrapper) }
                .onFailure { Log.w(TAG, "system service $name unavailable", it) }
                .getOrNull()
        }

    // Shizuku.newProcess is not public API on v13; the service stub is, and
    // this is the same call path the bottom bar has used on the car.
    private fun runBlockingProcess(command: Array<String>): ShellResult {
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: return ShellResult("", -1)
        val process = service.newProcess(command, null, null) ?: return ShellResult("", -1)
        return try {
            val stdout =
                process.inputStream
                    ?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
                    ?.bufferedReader()
                    ?.use { it.readText().trim() }
                    .orEmpty()
            ShellResult(stdout, process.waitFor())
        } finally {
            runCatching { process.destroy() }
        }
    }
}
