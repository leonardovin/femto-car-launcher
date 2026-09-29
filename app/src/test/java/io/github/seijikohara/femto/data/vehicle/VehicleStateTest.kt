package io.github.seijikohara.femto.data.vehicle

import io.github.seijikohara.femto.ui.vehicle.VehicleAction
import io.github.seijikohara.femto.ui.vehicle.toCommand
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleStateTest {
    private val reported =
        mapOf(
            VehicleKeys.HVAC_POWER to "1",
            VehicleKeys.HVAC_AC to "0",
            VehicleKeys.HVAC_CYCLE_MODE to "0",
            VehicleKeys.HVAC_DRIVER_TEMP to "21.5",
            VehicleKeys.HVAC_FAN_SPEED to "3",
            VehicleKeys.DRIVER_SEAT_HEAT to "2",
            VehicleKeys.SEAT_HEAT_MAX to "3",
            VehicleKeys.MEDIA_VOLUME to "15",
            VehicleKeys.MEDIA_VOLUME_MAX to "39",
            VehicleKeys.DRIVE_MODE to "2",
        )

    @Test
    fun `decodes the service's raw values`() {
        val state = vehicleStateFrom(reported, available = true)

        assertEquals(true, state.hvacOn)
        assertEquals(false, state.acOn)
        assertEquals(21.5f, state.driverTempC)
        assertEquals(3, state.fanSpeed)
        assertEquals(DriveMode.ECO, state.driveMode)
        assertNull(state.passengerTempC)
    }

    @Test
    fun `cycle mode zero means recirculation on`() {
        assertEquals(true, vehicleStateFrom(reported, available = true).recirculating)
        assertEquals(
            VehicleKeys.HVAC_CYCLE_MODE to "1",
            VehicleCommand.SetRecirculation(false).encode(VehicleState.Unavailable),
        )
    }

    @Test
    fun `temperature writes are clamped and formatted with one decimal`() {
        val state = VehicleState.Unavailable

        assertEquals(
            VehicleKeys.HVAC_DRIVER_TEMP to "32.0",
            VehicleCommand.SetTemperature(Seat.DRIVER, 40f).encode(state),
        )
        assertEquals(
            VehicleKeys.HVAC_PASSENGER_TEMP to "22.5",
            VehicleCommand.SetTemperature(Seat.PASSENGER, 22.5f).encode(state),
        )
    }

    @Test
    fun `stepping the temperature moves by half a degree from the reported value`() {
        val state = vehicleStateFrom(reported, available = true)

        assertEquals(
            VehicleCommand.SetTemperature(Seat.DRIVER, 22f),
            VehicleAction.StepTemperature(Seat.DRIVER, 1).toCommand(state),
        )
    }

    @Test
    fun `seat heat cycles back to off after the car's maximum`() {
        val state = vehicleStateFrom(reported + (VehicleKeys.DRIVER_SEAT_HEAT to "3"), available = true)

        assertEquals(
            VehicleCommand.SetSeatHeat(Seat.DRIVER, 0),
            VehicleAction.CycleSeatHeat(Seat.DRIVER).toCommand(state),
        )
    }

    @Test
    fun `fan and volume stay inside the car's range`() {
        val state = vehicleStateFrom(reported + (VehicleKeys.HVAC_FAN_SPEED to "7"), available = true)

        assertEquals(VehicleCommand.SetFanSpeed(MAX_FAN_SPEED), VehicleAction.StepFan(1).toCommand(state))
        assertEquals(VehicleKeys.MEDIA_VOLUME to "39", VehicleCommand.SetMediaVolume(50).encode(state))
    }

    @Test
    fun `drive modes encode to the car's raw values`() {
        assertEquals(
            listOf("2", "0", "1"),
            DriveMode.entries.map { VehicleCommand.SetDriveMode(it).encode(VehicleState.Unavailable).second },
        )
    }
}
