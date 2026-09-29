package io.github.seijikohara.femto.data.common

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Whether this app holds the HOME role, i.e. is the device's default home app.
 * Shared because two domains read it: diagnostics reports it, and the updater
 * picks its post-install path by it (the home app may start an activity from
 * the background, though not always; see the updater's afterUpdateAction).
 */
internal fun Context.holdsHomeRole(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getSystemService<RoleManager>()?.isRoleHeld(RoleManager.ROLE_HOME) == true
    } else {
        // Android 9 has no RoleManager: the default home is whichever activity
        // the platform resolves for a bare HOME intent.
        @Suppress("DEPRECATION")
        packageManager
            .resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo
            ?.packageName == packageName
    }
