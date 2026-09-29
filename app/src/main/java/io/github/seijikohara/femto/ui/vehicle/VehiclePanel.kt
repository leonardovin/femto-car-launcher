package io.github.seijikohara.femto.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.AirVent
import com.composables.icons.lucide.Fan
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Power
import com.composables.icons.lucide.RefreshCcw
import com.composables.icons.lucide.Snowflake
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Wind
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.vehicle.DriveMode
import io.github.seijikohara.femto.data.vehicle.Seat
import io.github.seijikohara.femto.data.vehicle.VehicleState
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MaximizePanel
import io.github.seijikohara.femto.ui.home.components.PanelIconButton
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.panelMetric
import io.github.seijikohara.femto.ui.theme.tileLabel
import java.util.Locale

private val ControlGap = 12.dp

/** The vehicle panel bound to its [VehicleViewModel] (opened by the dock's Climate button). */
@Composable
internal fun VehiclePanelHost(
    onClose: () -> Unit,
    onOpenClimateApp: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    val viewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VehiclePanel(
        state = state,
        onAction = viewModel::onAction,
        onClose = onClose,
        onOpenClimateApp = onOpenClimateApp,
        hazeState = hazeState,
        glassConfig = glassConfig,
        modifier = modifier,
    )
}

/**
 * Climate, seats, media volume and drive mode for the head unit's vehicle
 * service — the controls the separate shizuku-bottom-bar dock used to carry,
 * now inside the launcher. Every control is a ≥ MinTouchTarget tile; values
 * the car has not reported render as a dash rather than a guess.
 */
@Composable
internal fun VehiclePanel(
    state: VehicleState,
    onAction: (VehicleAction) -> Unit,
    onClose: () -> Unit,
    onOpenClimateApp: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = MaximizePanel(
    title = stringResource(R.string.vehicle_panel_title),
    onClose = onClose,
    onOpenExternal = onOpenClimateApp,
    openExternalLabel = stringResource(R.string.vehicle_open_climate_app),
    hazeState = hazeState,
    glassConfig = glassConfig,
    modifier = modifier,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ControlGap),
    ) {
        CabinTemperatures(state)
        Row(horizontalArrangement = Arrangement.spacedBy(ControlGap)) {
            Stepper(
                label = stringResource(R.string.vehicle_driver),
                value = state.driverTempC.celsiusLabel(),
                onStep = { onAction(VehicleAction.StepTemperature(Seat.DRIVER, it)) },
            )
            Stepper(
                label = stringResource(R.string.vehicle_fan),
                value = state.fanSpeed?.toString() ?: "–",
                icon = Lucide.Fan,
                onStep = { onAction(VehicleAction.StepFan(it)) },
            )
            Stepper(
                label = stringResource(R.string.vehicle_passenger),
                value = state.passengerTempC.celsiusLabel(),
                onStep = { onAction(VehicleAction.StepTemperature(Seat.PASSENGER, it)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ControlGap)) {
            ToggleTile(Lucide.Power, stringResource(R.string.vehicle_power), state.hvacOn) {
                onAction(VehicleAction.TogglePower)
            }
            ToggleTile(Lucide.Snowflake, stringResource(R.string.vehicle_ac), state.acOn) {
                onAction(VehicleAction.ToggleAc)
            }
            ToggleTile(Lucide.Sparkles, stringResource(R.string.vehicle_auto), state.autoOn) {
                onAction(VehicleAction.ToggleAuto)
            }
            ToggleTile(Lucide.RefreshCcw, stringResource(R.string.vehicle_recirculation), state.recirculating) {
                onAction(VehicleAction.ToggleRecirculation)
            }
            ToggleTile(Lucide.Wind, stringResource(R.string.vehicle_defrost), state.frontDefrost) {
                onAction(VehicleAction.ToggleFrontDefrost)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ControlGap)) {
            Seat.entries.forEach { seat ->
                val who = stringResource(
                    if (seat ==
                        Seat.DRIVER
                    ) {
                        R.string.vehicle_driver
                    } else {
                        R.string.vehicle_passenger
                    },
                )
                LevelTile(
                    Lucide.Flame,
                    stringResource(R.string.vehicle_seat_heat, who),
                    state.seatHeat(seat),
                    state.seatHeatMax,
                ) {
                    onAction(VehicleAction.CycleSeatHeat(seat))
                }
                LevelTile(
                    Lucide.AirVent,
                    stringResource(R.string.vehicle_seat_vent, who),
                    state.seatVent(seat),
                    state.seatVentMax,
                ) {
                    onAction(VehicleAction.CycleSeatVent(seat))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ControlGap)) {
            Stepper(
                label = stringResource(R.string.vehicle_volume),
                value = state.mediaVolume?.toString() ?: "–",
                onStep = { onAction(VehicleAction.StepVolume(it)) },
            )
            DriveMode.entries.forEach { mode ->
                ToggleTile(null, stringResource(mode.labelRes()), state.driveMode?.let { it == mode }) {
                    onAction(VehicleAction.SetDriveMode(mode))
                }
            }
        }
    }
}

@Composable
private fun CabinTemperatures(state: VehicleState) {
    if (state.insideTempC == null && state.outsideTempC == null) return
    Text(
        text =
            stringResource(
                R.string.vehicle_cabin_temperatures,
                state.insideTempC.celsiusLabel(),
                state.outsideTempC.celsiusLabel(),
            ),
        style = MaterialTheme.typography.cardMeta(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// A labelled − value + control (temperature, fan, volume).
@Composable
private fun RowScope.Stepper(
    label: String,
    value: String,
    onStep: (Int) -> Unit,
    icon: ImageVector? = null,
) = Column(
    modifier = Modifier.weight(1f).tile(active = false),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    TileLabel(label, icon)
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanelIconButton(Lucide.Minus, stringResource(R.string.vehicle_decrease, label), onClick = { onStep(-1) })
        Text(
            text = value,
            style = MaterialTheme.typography.panelMetric(),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        PanelIconButton(Lucide.Plus, stringResource(R.string.vehicle_increase, label), onClick = { onStep(1) })
    }
}

// An on/off tile; [on] null means the car has not reported the state yet.
@Composable
private fun RowScope.ToggleTile(
    icon: ImageVector?,
    label: String,
    on: Boolean?,
    onClick: () -> Unit,
) {
    val stateText = stringResource(if (on == true) R.string.vehicle_state_on else R.string.vehicle_state_off)
    Column(
        modifier =
            Modifier
                .weight(1f)
                .tile(active = on == true)
                .clickable(role = Role.Switch, onClick = onClick)
                .semantics { stateDescription = stateText },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) TileIcon(icon, active = on == true)
        TileLabel(label, null, active = on == true)
    }
}

// A cycling level tile (seat heat / ventilation): the level as filled dots.
@Composable
private fun RowScope.LevelTile(
    icon: ImageVector,
    label: String,
    level: Int?,
    max: Int,
    onClick: () -> Unit,
) {
    val active = (level ?: 0) > 0
    Column(
        modifier =
            Modifier
                .weight(1f)
                .tile(active = active)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label ${level ?: 0}/$max" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TileIcon(icon, active)
        Text(
            text = (1..max).joinToString("") { if (it <= (level ?: 0)) "●" else "○" },
            style = MaterialTheme.typography.cardMeta(),
            color = tileContentColor(active),
        )
        TileLabel(label, null, active)
    }
}

@Composable
private fun TileIcon(
    icon: ImageVector,
    active: Boolean,
) = FemtoIcon(
    imageVector = icon,
    contentDescription = null,
    tint = tileContentColor(active),
    modifier = Modifier.size(FemtoDimens.InlineIconSize + 8.dp),
)

@Composable
private fun TileLabel(
    label: String,
    icon: ImageVector?,
    active: Boolean = false,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    if (icon != null) {
        FemtoIcon(
            imageVector = icon,
            contentDescription = null,
            tint = tileContentColor(active),
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.tileLabel(),
        color = tileContentColor(active),
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun tileContentColor(active: Boolean) =
    if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

@Composable
private fun Modifier.tile(active: Boolean): Modifier =
    this
        .heightIn(min = FemtoDimens.MinTouchTarget + 24.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(
            if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ).padding(vertical = 8.dp, horizontal = 4.dp)

private fun Float?.celsiusLabel(): String = this?.let { String.format(Locale.ROOT, "%.1f°", it) } ?: "–"

private fun DriveMode.labelRes(): Int =
    when (this) {
        DriveMode.ECO -> R.string.vehicle_drive_mode_eco
        DriveMode.NORMAL -> R.string.vehicle_drive_mode_normal
        DriveMode.SPORT -> R.string.vehicle_drive_mode_sport
    }

@PreviewLightDark
@Preview(name = "Vehicle panel", widthDp = 1000, heightDp = 560)
@Composable
private fun VehiclePanelPreview() {
    FemtoTheme {
        VehiclePanel(
            state =
                VehicleState(
                    available = true,
                    hvacOn = true,
                    acOn = true,
                    autoOn = false,
                    recirculating = false,
                    frontDefrost = false,
                    driverTempC = 21.5f,
                    passengerTempC = 23f,
                    fanSpeed = 3,
                    driverSeatHeat = 2,
                    passengerSeatVent = 1,
                    mediaVolume = 15,
                    driveMode = DriveMode.NORMAL,
                    insideTempC = 27f,
                    outsideTempC = 31f,
                ),
            onAction = {},
            onClose = {},
            onOpenClimateApp = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
