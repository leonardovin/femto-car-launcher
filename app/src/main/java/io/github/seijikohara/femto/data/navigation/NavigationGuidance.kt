package io.github.seijikohara.femto.data.navigation

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * The live turn-by-turn state a navigation app publishes in its ongoing
 * notification, read without any maps API key or billing (the fork's
 * Google-Maps-through-the-app integration; see ANDROID9-FORK-PLAN.md).
 *
 * The notification's layout is the app's own, so every field is best-effort:
 * Google Maps puts the distance to the next manoeuvre in the title, the
 * instruction in the text, the ETA summary in the sub-text and the manoeuvre
 * arrow in the large icon. A field the app leaves empty is null, and the card
 * degrades to whatever is present.
 */
@Immutable
internal data class NavigationGuidance(
    val packageName: String,
    val distance: String?,
    val instruction: String?,
    val eta: String?,
    val maneuver: Bitmap?,
    // Brings the navigation app back to the front on a tap on the card.
    val contentIntent: PendingIntent?,
)

/** The navigation apps whose guidance notification the dashboard mirrors. */
internal enum class NavigationApp(
    val packageName: String,
) {
    GOOGLE_MAPS("com.google.android.apps.maps"),
    WAZE("com.waze"),
    ;

    companion object {
        fun fromPackage(packageName: String): NavigationApp? = entries.firstOrNull { it.packageName == packageName }
    }
}
