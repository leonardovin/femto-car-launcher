package io.github.seijikohara.femto.data.common

import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The [ContextCompat.registerReceiver] export flag for a receiver that listens
 * only to platform broadcasts (time ticks, battery, Bluetooth state).
 *
 * On API 33+ the platform honours `RECEIVER_NOT_EXPORTED` natively and still
 * delivers system broadcasts. Below that, androidx emulates the flag by
 * guarding the receiver with an app-private signature permission, which the
 * system sender does not hold — `BATTERY_CHANGED` and friends are then
 * dropped ("Permission Denial"). These actions are protected broadcasts only
 * the system may send, so registering the receiver exported there is safe.
 */
internal val systemBroadcastReceiverFlags: Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.RECEIVER_NOT_EXPORTED
    } else {
        ContextCompat.RECEIVER_EXPORTED
    }
