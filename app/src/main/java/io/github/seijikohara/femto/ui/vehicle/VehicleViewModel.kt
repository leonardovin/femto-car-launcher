package io.github.seijikohara.femto.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.vehicle.CABIN_TEMP_STEP_C
import io.github.seijikohara.femto.data.vehicle.DriveMode
import io.github.seijikohara.femto.data.vehicle.MAX_FAN_SPEED
import io.github.seijikohara.femto.data.vehicle.MIN_FAN_SPEED
import io.github.seijikohara.femto.data.vehicle.Seat
import io.github.seijikohara.femto.data.vehicle.VehicleCommand
import io.github.seijikohara.femto.data.vehicle.VehicleRepository
import io.github.seijikohara.femto.data.vehicle.VehicleState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Used when the car has not reported a value yet, so a first tap still lands
// on a sensible setting instead of doing nothing.
private const val DEFAULT_CABIN_TEMP_C = 22f

/** Taps on the vehicle panel; each maps to one [VehicleCommand]. */
internal sealed interface VehicleAction {
    data class StepTemperature(
        val seat: Seat,
        val steps: Int,
    ) : VehicleAction

    data class StepFan(
        val steps: Int,
    ) : VehicleAction

    data object TogglePower : VehicleAction

    data object ToggleAc : VehicleAction

    data object ToggleAuto : VehicleAction

    data object ToggleRecirculation : VehicleAction

    data object ToggleFrontDefrost : VehicleAction

    // Seat levels cycle 0 → max → 0, as the bottom bar's seat buttons do.
    data class CycleSeatHeat(
        val seat: Seat,
    ) : VehicleAction

    data class CycleSeatVent(
        val seat: Seat,
    ) : VehicleAction

    data class StepVolume(
        val steps: Int,
    ) : VehicleAction

    data class SetDriveMode(
        val mode: DriveMode,
    ) : VehicleAction
}

/** Maps a panel tap to the write it means against the [state] it was made on. */
internal fun VehicleAction.toCommand(state: VehicleState): VehicleCommand =
    when (this) {
        is VehicleAction.StepTemperature -> {
            val current = (if (seat == Seat.DRIVER) state.driverTempC else state.passengerTempC) ?: DEFAULT_CABIN_TEMP_C
            VehicleCommand.SetTemperature(seat, current + steps * CABIN_TEMP_STEP_C)
        }

        is VehicleAction.StepFan -> {
            VehicleCommand.SetFanSpeed(((state.fanSpeed ?: 0) + steps).coerceIn(MIN_FAN_SPEED, MAX_FAN_SPEED))
        }

        VehicleAction.TogglePower -> {
            VehicleCommand.SetHvacPower(state.hvacOn != true)
        }

        VehicleAction.ToggleAc -> {
            VehicleCommand.SetAc(state.acOn != true)
        }

        VehicleAction.ToggleAuto -> {
            VehicleCommand.SetAuto(state.autoOn != true)
        }

        VehicleAction.ToggleRecirculation -> {
            VehicleCommand.SetRecirculation(state.recirculating != true)
        }

        VehicleAction.ToggleFrontDefrost -> {
            VehicleCommand.SetFrontDefrost(state.frontDefrost != true)
        }

        is VehicleAction.CycleSeatHeat -> {
            VehicleCommand.SetSeatHeat(seat, ((state.seatHeat(seat) ?: 0) + 1) % (state.seatHeatMax + 1))
        }

        is VehicleAction.CycleSeatVent -> {
            VehicleCommand.SetSeatVent(seat, ((state.seatVent(seat) ?: 0) + 1) % (state.seatVentMax + 1))
        }

        is VehicleAction.StepVolume -> {
            VehicleCommand.SetMediaVolume((state.mediaVolume ?: 0) + steps)
        }

        is VehicleAction.SetDriveMode -> {
            VehicleCommand.SetDriveMode(mode)
        }
    }

internal class VehicleViewModel(
    val uiState: StateFlow<VehicleState>,
    private val send: suspend (VehicleCommand) -> Boolean,
) : ViewModel() {
    fun onAction(action: VehicleAction) {
        val command = action.toCommand(uiState.value)
        viewModelScope.launch { send(command) }
    }
}

internal object VehicleViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return VehicleViewModel(VehicleRepository.state, VehicleRepository::send) as T
    }
}
