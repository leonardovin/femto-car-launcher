package io.github.seijikohara.femto.data.vehicle

import io.github.seijikohara.femto.data.privileged.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The beantechs climate app; it draws a full-screen overlay on every HVAC change.
private const val HVAC_PACKAGE = "com.beantechs.hvac"

// Long enough for a burst of +/- taps to share one suspension.
private const val RESUME_DELAY_MS = 300L

// Lets the force-stop settle before the write reaches the car.
private const val SETTLE_MS = 150L

/**
 * Port of shizuku-bottom-bar's HvacSuspensionController: the OEM climate app
 * pops its own overlay whenever a climate property changes, covering the
 * launcher. While the launcher writes one, that app is disabled and stopped,
 * and it comes back [RESUME_DELAY_MS] after the last write. It is left alone
 * when the user has it open on purpose.
 */
internal class HvacPopupGuard(
    private val scope: CoroutineScope,
    private val shell: suspend (Array<String>) -> ShellResult,
) {
    private val mutex = Mutex()
    private var suspended = false
    private var resumeJob: Job? = null

    suspend fun <T> around(block: suspend () -> T): T {
        mutex.withLock {
            resumeJob?.cancel()
            if (!suspended && !hvacInForeground()) {
                shell(arrayOf("pm", "disable-user", "--user", "0", HVAC_PACKAGE))
                shell(arrayOf("am", "force-stop", HVAC_PACKAGE))
                suspended = true
                delay(SETTLE_MS)
            }
        }
        return try {
            block()
        } finally {
            scheduleResume()
        }
    }

    /** Re-enable the OEM climate app unconditionally (recovery after a crash mid-write). */
    suspend fun heal() {
        mutex.withLock {
            resumeJob?.cancel()
            shell(arrayOf("pm", "enable", HVAC_PACKAGE))
            suspended = false
        }
    }

    private fun scheduleResume() {
        resumeJob?.cancel()
        resumeJob =
            scope.launch {
                delay(RESUME_DELAY_MS)
                mutex.withLock {
                    if (suspended) {
                        shell(arrayOf("pm", "enable", HVAC_PACKAGE))
                        suspended = false
                    }
                }
            }
    }

    private suspend fun hvacInForeground(): Boolean =
        shell(arrayOf("sh", "-c", "dumpsys activity activities | grep ResumedActivity"))
            .stdout
            .contains(HVAC_PACKAGE)
}
