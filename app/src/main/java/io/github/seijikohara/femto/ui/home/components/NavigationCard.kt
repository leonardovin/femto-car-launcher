package io.github.seijikohara.femto.ui.home.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Navigation
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.navigation.NavigationGuidance
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.panelMetric

// The manoeuvre arrow reads at a glance from the driver's seat: larger than a
// hero icon, smaller than the speed numeral block beside it.
private val ManeuverSize = 56.dp

/**
 * Turn-by-turn guidance mirrored from the navigation app's notification
 * (Google Maps / Waze), shown over the map while a route is active. No maps
 * API or key is involved: the app does the routing, the card only echoes its
 * next manoeuvre, and a tap brings the app back to the front.
 */
@Composable
internal fun NavigationCard(
    guidance: NavigationGuidance,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = Row(
    modifier =
        modifier
            .widthIn(max = FemtoDimens.SpeedOverlayMaxWidth)
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
            .clickable(onClick = onTap)
            .padding(horizontal = FemtoDimens.OverlayPaddingHorizontal, vertical = FemtoDimens.CardPaddingCompact),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    ManeuverIcon(guidance.maneuver)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = guidance.distance ?: stringResource(R.string.navigation_active),
            style = MaterialTheme.typography.panelMetric(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        guidance.instruction?.let { instruction ->
            Text(
                text = instruction,
                style = MaterialTheme.typography.cardCta(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        guidance.eta?.let { eta ->
            Text(
                text = eta,
                style = MaterialTheme.typography.cardMeta(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Navigation apps draw their manoeuvre arrows as single-colour glyphs, so the
// bitmap is tinted to the theme's content colour and reads in light and dark.
@Composable
private fun ManeuverIcon(maneuver: Bitmap?) {
    val tint = MaterialTheme.colorScheme.onSurface
    if (maneuver != null) {
        val image = remember(maneuver) { maneuver.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(ManeuverSize),
        )
    } else {
        Icon(
            imageVector = Lucide.Navigation,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ManeuverSize),
        )
    }
}

@PreviewLightDark
@Preview(name = "Navigation card", widthDp = 480, heightDp = 140)
@Composable
private fun NavigationCardPreview() {
    FemtoTheme {
        NavigationCard(
            guidance =
                NavigationGuidance(
                    packageName = "com.google.android.apps.maps",
                    distance = "300 m",
                    instruction = "Turn right onto Av. Paulista",
                    eta = "12 min · 4.2 km · 10:42",
                    maneuver = null,
                    contentIntent = null,
                ),
            onTap = {},
        )
    }
}
