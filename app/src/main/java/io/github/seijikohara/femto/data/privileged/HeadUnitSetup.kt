package io.github.seijikohara.femto.data.privileged

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import io.github.seijikohara.femto.data.music.MusicSessionListenerService
import io.github.seijikohara.femto.data.system.SystemPermissionSignals

/** What one head-unit setup pass did: how many steps applied and which failed. */
internal data class SetupReport(
    val applied: Int,
    val failed: List<String>,
) {
    val succeeded: Boolean get() = failed.isEmpty()
}

/**
 * The manual steps a locked-down head unit makes hard (its Settings app is
 * often trimmed or hidden), done through Shizuku instead:
 *
 * - [grantLauncherAccess]: notification-listener access (music card + Google
 *   Maps guidance), every requested runtime permission, and the battery
 *   optimisation exemption. Idempotent, so it runs whenever Shizuku becomes
 *   ready.
 * - [makeDefaultHome]: elect this launcher as the HOME app — only on an
 *   explicit tap, never automatically, because it replaces the user's launcher.
 *
 * [shell] is the privileged command runner (tests substitute a fake).
 */
internal class HeadUnitSetup(
    private val context: Context,
    private val shell: suspend (Array<String>) -> ShellResult = { ShizukuGateway.run(*it) },
) {
    private val packageName: String get() = context.packageName

    suspend fun grantLauncherAccess(): SetupReport {
        val listener = ComponentName(context, MusicSessionListenerService::class.java).flattenToString()
        val steps =
            buildList {
                add("notification listener" to arrayOf("cmd", "notification", "allow_listener", listener))
                missingRuntimePermissions().forEach { permission ->
                    add(permission to arrayOf("pm", "grant", packageName, permission))
                }
                add("battery optimisation" to arrayOf("dumpsys", "deviceidle", "whitelist", "+$packageName"))
            }
        val failed = steps.filterNot { (_, command) -> shell(command).succeeded }.map { (label, _) -> label }
        // Permission-gated flows re-read without waiting for a restart.
        SystemPermissionSignals.refreshes.tryEmit(Unit)
        return SetupReport(applied = steps.size - failed.size, failed = failed)
    }

    suspend fun makeDefaultHome(): Boolean {
        val home =
            context.packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName),
                    0,
                ).firstOrNull()
                ?.activityInfo
                ?: return false
        val component = ComponentName(home.packageName, home.name).flattenToString()
        return shell(arrayOf("cmd", "package", "set-home-activity", component)).succeeded
    }

    @Suppress("DEPRECATION")
    private fun missingRuntimePermissions(): List<String> {
        val info: PackageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags ?: IntArray(requested.size)
        return requested
            .filterIndexed { index, _ -> flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED == 0 }
            .filter { permission ->
                runCatching {
                    context.packageManager.getPermissionInfo(permission, 0).protection ==
                        PermissionInfo.PROTECTION_DANGEROUS
                }.getOrDefault(false)
            }
    }
}
