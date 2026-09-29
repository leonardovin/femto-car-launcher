package io.github.seijikohara.femto.data.navigation

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NavigationGuidance"

// Upper bound for the manoeuvre arrow: the card draws it at icon size, and a
// full-resolution large icon would be held for the whole guidance session.
private const val MANEUVER_MAX_PX = 192

/**
 * Process-wide holder of the active navigation guidance, fed by the launcher's
 * notification listener (the same listener grant the music card uses, so no
 * extra permission is involved). One slot: the most recent guidance
 * notification wins, and its removal clears the card.
 */
internal object NavigationGuidanceRepository {
    private val mutableGuidance = MutableStateFlow<NavigationGuidance?>(null)

    val guidance: StateFlow<NavigationGuidance?> = mutableGuidance.asStateFlow()

    fun onPosted(
        context: Context,
        sbn: StatusBarNotification,
    ) {
        if (!sbn.isGuidance()) return
        mutableGuidance.value =
            runCatching { sbn.toGuidance(context) }
                .onFailure { Log.w(TAG, "unreadable guidance notification from ${sbn.packageName}", it) }
                .getOrNull()
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.isGuidance() && mutableGuidance.value?.packageName == sbn.packageName) {
            mutableGuidance.value = null
        }
    }

    /** Re-seed from the full notification list (listener (re)connected). */
    fun onSnapshot(
        context: Context,
        active: List<StatusBarNotification>,
    ) {
        mutableGuidance.value =
            active
                .filter { it.isGuidance() }
                .maxByOrNull { it.postTime }
                ?.let { sbn -> runCatching { sbn.toGuidance(context) }.getOrNull() }
    }

    fun onDisconnected() {
        mutableGuidance.value = null
    }
}

// A navigation app's guidance is its ongoing navigation-category notification.
// Some builds leave the category unset, so an ongoing notification on a
// navigation-named channel counts too; anything else the app posts (location
// sharing, traffic alerts) is ignored.
private fun StatusBarNotification.isGuidance(): Boolean =
    NavigationApp.fromPackage(packageName) != null &&
        isOngoing &&
        (
            notification.category == Notification.CATEGORY_NAVIGATION ||
                notification.channelId?.contains("nav", ignoreCase = true) == true
        )

private fun StatusBarNotification.toGuidance(context: Context): NavigationGuidance {
    val extras = notification.extras
    return guidanceFrom(
        packageName = packageName,
        title = extras.text(Notification.EXTRA_TITLE),
        text = extras.text(Notification.EXTRA_TEXT) ?: extras.text(Notification.EXTRA_BIG_TEXT),
        subText = extras.text(Notification.EXTRA_SUB_TEXT),
        maneuver = notification.maneuverBitmap(context),
        contentIntent = notification.contentIntent,
    )
}

/** Pure mapping from the notification fields to [NavigationGuidance]; blank fields become null. */
internal fun guidanceFrom(
    packageName: String,
    title: String?,
    text: String?,
    subText: String?,
    maneuver: Bitmap?,
    contentIntent: PendingIntent?,
): NavigationGuidance =
    NavigationGuidance(
        packageName = packageName,
        distance = title?.trim()?.takeIf { it.isNotEmpty() },
        instruction = text?.trim()?.takeIf { it.isNotEmpty() },
        eta = subText?.trim()?.takeIf { it.isNotEmpty() },
        maneuver = maneuver,
        contentIntent = contentIntent,
    )

private fun Bundle.text(key: String): String? = getCharSequence(key)?.toString()

private fun Notification.maneuverBitmap(context: Context): Bitmap? =
    (getLargeIcon() ?: smallIcon)
        ?.let { icon: Icon -> icon.loadDrawable(context) }
        ?.let { drawable ->
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: MANEUVER_MAX_PX
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: MANEUVER_MAX_PX
            val scale = minOf(1f, MANEUVER_MAX_PX.toFloat() / maxOf(width, height))
            drawable.toBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
        }
