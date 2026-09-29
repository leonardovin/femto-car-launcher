package io.github.seijikohara.femto.ui.settings.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Wrench
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.privileged.SetupReport
import io.github.seijikohara.femto.data.privileged.ShizukuState
import io.github.seijikohara.femto.ui.settings.HeadUnitAction
import io.github.seijikohara.femto.ui.settings.HeadUnitUiState
import io.github.seijikohara.femto.ui.settings.HeadUnitViewModel
import io.github.seijikohara.femto.ui.settings.HeadUnitViewModelFactory
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/** Settings > System > Head unit, bound to its own [HeadUnitViewModel]. */
@Composable
internal fun HeadUnitRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: HeadUnitViewModel =
        viewModel(factory = HeadUnitViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HeadUnitSection(uiState = uiState, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * The Android 9 fork's head-unit block: Shizuku access, the one-tap setup a
 * locked-down unit needs, and electing the launcher as HOME. Rows that need
 * Shizuku stay visible but explain why they are idle when it is not ready.
 */
@Composable
internal fun HeadUnitSection(
    uiState: HeadUnitUiState,
    onAction: (HeadUnitAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    val ready = uiState.shizuku == ShizukuState.READY
    SettingsSubheader(title = stringResource(R.string.settings_head_unit))
    ActionRow(
        title = stringResource(R.string.settings_shizuku_access),
        summary =
            stringResource(
                when (uiState.shizuku) {
                    ShizukuState.UNAVAILABLE -> R.string.settings_shizuku_unavailable
                    ShizukuState.NEEDS_PERMISSION -> R.string.settings_shizuku_needs_permission
                    ShizukuState.READY -> R.string.settings_shizuku_ready
                },
            ),
        onClick = { if (uiState.shizuku == ShizukuState.NEEDS_PERMISSION) onAction(HeadUnitAction.RequestAccess) },
        icon = Lucide.ShieldCheck,
    )
    ActionRow(
        title = stringResource(R.string.settings_head_unit_setup),
        summary = setupSummary(ready, uiState.busy, uiState.lastSetup),
        summaryLiveRegion = true,
        onClick = { if (ready) onAction(HeadUnitAction.ApplySetup) },
        icon = Lucide.Wrench,
    )
    ActionRow(
        title = stringResource(R.string.settings_make_default_home),
        summary =
            when {
                !ready -> stringResource(R.string.settings_needs_shizuku)
                uiState.homeElected == true -> stringResource(R.string.settings_default_home_done)
                uiState.homeElected == false -> stringResource(R.string.settings_default_home_failed)
                else -> stringResource(R.string.settings_default_home_hint)
            },
        summaryLiveRegion = true,
        onClick = { if (ready) onAction(HeadUnitAction.MakeDefaultHome) },
        icon = Lucide.House,
    )
}

@Composable
private fun setupSummary(
    ready: Boolean,
    busy: Boolean,
    report: SetupReport?,
): String =
    when {
        !ready -> stringResource(R.string.settings_needs_shizuku)
        busy -> stringResource(R.string.settings_head_unit_setup_running)
        report == null -> stringResource(R.string.settings_head_unit_setup_hint)
        report.succeeded -> stringResource(R.string.settings_head_unit_setup_done, report.applied)
        else -> stringResource(R.string.settings_head_unit_setup_partial, report.failed.joinToString())
    }

@PreviewLightDark
@Composable
private fun HeadUnitSectionPreview() {
    FemtoTheme {
        HeadUnitSection(
            uiState = HeadUnitUiState.Initial.copy(shizuku = ShizukuState.READY),
            onAction = {},
        )
    }
}
