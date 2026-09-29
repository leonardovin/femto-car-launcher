package io.github.seijikohara.femto.data.vehicle

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * The head unit's vehicle properties the launcher reads and writes, as the
 * beantechs IntelligentVehicleControl service names them. Value domains are the
 * ones shizuku-bottom-bar has proven on the Haval H6 GT (its
 * CarCommandRegistry / CarValueMaps are the reference).
 */
internal object VehicleKeys {
    const val HVAC_POWER = "car.hvac.power_mode"
    const val HVAC_AC = "car.hvac.ac_enable"
    const val HVAC_AUTO = "car.hvac.auto_enable"

    // Inverted: "0" means recirculation on.
    const val HVAC_CYCLE_MODE = "car.hvac.cycle_mode"
    const val HVAC_FRONT_DEFROST = "car.hvac.front_defrost_enable"
    const val HVAC_DRIVER_TEMP = "car.hvac.driver_temperature"
    const val HVAC_PASSENGER_TEMP = "car.hvac.pass_temperature"
    const val HVAC_FAN_SPEED = "car.hvac.fan_speed"
    const val DRIVER_SEAT_HEAT = "car.comfort_setting.driver_seat_heating_level"
    const val DRIVER_SEAT_VENT = "car.comfort_setting.driver_seat_ventilation_level"
    const val PASSENGER_SEAT_HEAT = "car.comfort_setting.passenger_seat_heating_level"
    const val PASSENGER_SEAT_VENT = "car.comfort_setting.passenger_seat_ventilation_level"
    const val SEAT_HEAT_MAX = "car.comfort_setting.seat_heating_max_level"
    const val SEAT_VENT_MAX = "car.comfort_setting.seat_ventilation_max_level"
    const val MEDIA_VOLUME = "sys.settings.audio.media_volume"
    const val MEDIA_VOLUME_MAX = "sys.settings.audio.media_volume_range"
    const val DRIVE_MODE = "car.drive_setting.drive_mode"
    const val INSIDE_TEMP = "car.basic.inside_temp"
    const val OUTSIDE_TEMP = "car.basic.outside_temp"

    /** Every key the launcher subscribes to. */
    val All: List<String> =
        listOf(
            HVAC_POWER,
            HVAC_AC,
            HVAC_AUTO,
            HVAC_CYCLE_MODE,
            HVAC_FRONT_DEFROST,
            HVAC_DRIVER_TEMP,
            HVAC_PASSENGER_TEMP,
            HVAC_FAN_SPEED,
            DRIVER_SEAT_HEAT,
            DRIVER_SEAT_VENT,
            PASSENGER_SEAT_HEAT,
            PASSENGER_SEAT_VENT,
            SEAT_HEAT_MAX,
            SEAT_VENT_MAX,
            MEDIA_VOLUME,
            MEDIA_VOLUME_MAX,
            DRIVE_MODE,
            INSIDE_TEMP,
            OUTSIDE_TEMP,
        )

    /**
     * Writes that pop the OEM climate app's own overlay over the screen; the
     * [HvacPopupGuard] parks that app for the duration of the write.
     */
    val HvacKeys: Set<String> =
        setOf(
            HVAC_POWER,
            HVAC_AC,
            HVAC_AUTO,
            HVAC_CYCLE_MODE,
            HVAC_FRONT_DEFROST,
            HVAC_DRIVER_TEMP,
            HVAC_PASSENGER_TEMP,
            HVAC_FAN_SPEED,
        )
}

internal const val MIN_CABIN_TEMP_C = 16f
internal const val MAX_CABIN_TEMP_C = 32f
internal const val CABIN_TEMP_STEP_C = 0.5f
internal const val MIN_FAN_SPEED = 1
internal const val MAX_FAN_SPEED = 7
private const val FALLBACK_SEAT_LEVEL_MAX = 3
private const val FALLBACK_MEDIA_VOLUME_MAX = 39

internal enum class DriveMode(
    val raw: String,
) {
    ECO("2"),
    NORMAL("0"),
    SPORT("1"),
    ;

    companion object {
        fun fromRaw(raw: String?): DriveMode? = entries.firstOrNull { it.raw == raw?.trim() }
    }
}

internal enum class Seat { DRIVER, PASSENGER }

/** A snapshot of the vehicle properties; null fields are ones the car has not reported. */
@Immutable
internal data class VehicleState(
    // Whether the vehicle service is connected (Shizuku ready and the service found).
    val available: Boolean,
    val hvacOn: Boolean? = null,
    val acOn: Boolean? = null,
    val autoOn: Boolean? = null,
    val recirculating: Boolean? = null,
    val frontDefrost: Boolean? = null,
    val driverTempC: Float? = null,
    val passengerTempC: Float? = null,
    val fanSpeed: Int? = null,
    val driverSeatHeat: Int? = null,
    val driverSeatVent: Int? = null,
    val passengerSeatHeat: Int? = null,
    val passengerSeatVent: Int? = null,
    val seatHeatMax: Int = FALLBACK_SEAT_LEVEL_MAX,
    val seatVentMax: Int = FALLBACK_SEAT_LEVEL_MAX,
    val mediaVolume: Int? = null,
    val mediaVolumeMax: Int = FALLBACK_MEDIA_VOLUME_MAX,
    val driveMode: DriveMode? = null,
    val insideTempC: Float? = null,
    val outsideTempC: Float? = null,
) {
    fun seatHeat(seat: Seat): Int? = if (seat == Seat.DRIVER) driverSeatHeat else passengerSeatHeat

    fun seatVent(seat: Seat): Int? = if (seat == Seat.DRIVER) driverSeatVent else passengerSeatVent

    companion object {
        val Unavailable = VehicleState(available = false)
    }
}

/** Decode the raw property map the service reports into a [VehicleState]. */
internal fun vehicleStateFrom(
    values: Map<String, String?>,
    available: Boolean,
): VehicleState {
    fun bool(key: String) =
        when (values[key]?.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }

    fun int(key: String) = values[key]?.trim()?.toIntOrNull()

    fun float(key: String) = values[key]?.trim()?.toFloatOrNull()

    return VehicleState(
        available = available,
        hvacOn = bool(VehicleKeys.HVAC_POWER),
        acOn = bool(VehicleKeys.HVAC_AC),
        autoOn = bool(VehicleKeys.HVAC_AUTO),
        recirculating = bool(VehicleKeys.HVAC_CYCLE_MODE)?.not(),
        frontDefrost = bool(VehicleKeys.HVAC_FRONT_DEFROST),
        driverTempC = float(VehicleKeys.HVAC_DRIVER_TEMP),
        passengerTempC = float(VehicleKeys.HVAC_PASSENGER_TEMP),
        fanSpeed = int(VehicleKeys.HVAC_FAN_SPEED),
        driverSeatHeat = int(VehicleKeys.DRIVER_SEAT_HEAT),
        driverSeatVent = int(VehicleKeys.DRIVER_SEAT_VENT),
        passengerSeatHeat = int(VehicleKeys.PASSENGER_SEAT_HEAT),
        passengerSeatVent = int(VehicleKeys.PASSENGER_SEAT_VENT),
        seatHeatMax = int(VehicleKeys.SEAT_HEAT_MAX)?.takeIf { it > 0 } ?: FALLBACK_SEAT_LEVEL_MAX,
        seatVentMax = int(VehicleKeys.SEAT_VENT_MAX)?.takeIf { it > 0 } ?: FALLBACK_SEAT_LEVEL_MAX,
        mediaVolume = int(VehicleKeys.MEDIA_VOLUME),
        mediaVolumeMax = int(VehicleKeys.MEDIA_VOLUME_MAX)?.takeIf { it > 0 } ?: FALLBACK_MEDIA_VOLUME_MAX,
        driveMode = DriveMode.fromRaw(values[VehicleKeys.DRIVE_MODE]),
        insideTempC = float(VehicleKeys.INSIDE_TEMP),
        outsideTempC = float(VehicleKeys.OUTSIDE_TEMP),
    )
}

/** A write the launcher can ask of the vehicle. */
internal sealed interface VehicleCommand {
    data class SetHvacPower(
        val on: Boolean,
    ) : VehicleCommand

    data class SetAc(
        val on: Boolean,
    ) : VehicleCommand

    data class SetAuto(
        val on: Boolean,
    ) : VehicleCommand

    data class SetRecirculation(
        val on: Boolean,
    ) : VehicleCommand

    data class SetFrontDefrost(
        val on: Boolean,
    ) : VehicleCommand

    data class SetTemperature(
        val seat: Seat,
        val celsius: Float,
    ) : VehicleCommand

    data class SetFanSpeed(
        val level: Int,
    ) : VehicleCommand

    data class SetSeatHeat(
        val seat: Seat,
        val level: Int,
    ) : VehicleCommand

    data class SetSeatVent(
        val seat: Seat,
        val level: Int,
    ) : VehicleCommand

    data class SetMediaVolume(
        val level: Int,
    ) : VehicleCommand

    data class SetDriveMode(
        val mode: DriveMode,
    ) : VehicleCommand
}

/**
 * The (key, value) write for [command], clamped to the domain the car accepts
 * (seat and volume maxima come from [state], which carries the car's own).
 */
internal fun VehicleCommand.encode(state: VehicleState): Pair<String, String> {
    fun flag(on: Boolean) = if (on) "1" else "0"
    return when (this) {
        is VehicleCommand.SetHvacPower -> {
            VehicleKeys.HVAC_POWER to flag(on)
        }

        is VehicleCommand.SetAc -> {
            VehicleKeys.HVAC_AC to flag(on)
        }

        is VehicleCommand.SetAuto -> {
            VehicleKeys.HVAC_AUTO to flag(on)
        }

        is VehicleCommand.SetRecirculation -> {
            VehicleKeys.HVAC_CYCLE_MODE to flag(!on)
        }

        is VehicleCommand.SetFrontDefrost -> {
            VehicleKeys.HVAC_FRONT_DEFROST to flag(on)
        }

        is VehicleCommand.SetTemperature -> {
            val key = if (seat == Seat.DRIVER) VehicleKeys.HVAC_DRIVER_TEMP else VehicleKeys.HVAC_PASSENGER_TEMP
            key to String.format(Locale.US, "%.1f", celsius.coerceIn(MIN_CABIN_TEMP_C, MAX_CABIN_TEMP_C))
        }

        is VehicleCommand.SetFanSpeed -> {
            VehicleKeys.HVAC_FAN_SPEED to "${level.coerceIn(MIN_FAN_SPEED, MAX_FAN_SPEED)}"
        }

        is VehicleCommand.SetSeatHeat -> {
            val key = if (seat == Seat.DRIVER) VehicleKeys.DRIVER_SEAT_HEAT else VehicleKeys.PASSENGER_SEAT_HEAT
            key to "${level.coerceIn(0, state.seatHeatMax)}"
        }

        is VehicleCommand.SetSeatVent -> {
            val key = if (seat == Seat.DRIVER) VehicleKeys.DRIVER_SEAT_VENT else VehicleKeys.PASSENGER_SEAT_VENT
            key to "${level.coerceIn(0, state.seatVentMax)}"
        }

        is VehicleCommand.SetMediaVolume -> {
            VehicleKeys.MEDIA_VOLUME to "${level.coerceIn(0, state.mediaVolumeMax)}"
        }

        is VehicleCommand.SetDriveMode -> {
            VehicleKeys.DRIVE_MODE to mode.raw
        }
    }
}
