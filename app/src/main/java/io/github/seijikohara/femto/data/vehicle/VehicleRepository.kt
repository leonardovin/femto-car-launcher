package io.github.seijikohara.femto.data.vehicle

import android.content.Context
import android.util.Log
import com.beantechs.intelligentvehiclecontrol.IIntelligentVehicleControlService
import com.beantechs.intelligentvehiclecontrol.sdk.IListener
import io.github.seijikohara.femto.data.privileged.ShizukuGateway
import io.github.seijikohara.femto.data.privileged.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VehicleRepository"
private const val SERVICE_NAME = "com.beantechs.intelligentvehiclecontrol"
private const val SET_ACTION = "cmd.common.request.set"

// The service registers late in the head unit's boot; keep looking while
// Shizuku is up but the service is not.
private const val RECONNECT_DELAY_MS = 10_000L

/**
 * The launcher's link to the head unit's vehicle service (climate, seats,
 * media volume, drive mode), ported from shizuku-bottom-bar's ServiceManager
 * read/write path. The service is only reachable with Shizuku's identity, so
 * the link follows [ShizukuGateway.state]; everywhere else (phones, other head
 * units) [state] stays [VehicleState.Unavailable] and the climate UI hides.
 */
internal object VehicleRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val values = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val service = MutableStateFlow<IIntelligentVehicleControlService?>(null)
    private val guard = HvacPopupGuard(scope) { ShizukuGateway.run(*it) }

    @Volatile
    private var started = false

    private val listener =
        object : IListener.Stub() {
            override fun onDataChanged(
                key: String?,
                value: String?,
            ) {
                if (key != null) values.update { it + (key to value) }
            }
        }

    val state: StateFlow<VehicleState> =
        combine(service, values) { connected, current -> vehicleStateFrom(current, available = connected != null) }
            .stateIn(scope, SharingStarted.Eagerly, VehicleState.Unavailable)

    fun start(context: Context) {
        if (started) return
        started = true
        val packageName = context.packageName
        ShizukuGateway.start()
        scope.launch {
            ShizukuGateway.state.collectLatest { shizuku ->
                disconnect(packageName)
                while (shizuku == ShizukuState.READY && service.value == null) {
                    connect(packageName)
                    if (service.value == null) delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    /** Write [command] to the car; false when it could not be delivered. */
    suspend fun send(command: VehicleCommand): Boolean {
        val target = service.value ?: return false
        val (key, value) = command.encode(state.value)
        val write: suspend () -> Boolean = {
            withContext(Dispatchers.IO) {
                runCatching { target.request(SET_ACTION, key, value) }
                    .onFailure { Log.w(TAG, "write $key=$value failed", it) }
                    .isSuccess
            }
        }
        val delivered = if (key in VehicleKeys.HvacKeys) guard.around(write) else write()
        // Optimistic: the car confirms through the listener, but the UI should
        // not wait a round trip to show the tap.
        if (delivered) values.update { it + (key to value) }
        if (!delivered) service.value = null
        return delivered
    }

    private suspend fun connect(packageName: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val binder = ShizukuGateway.systemService(SERVICE_NAME) ?: return@runCatching null
                if (!binder.pingBinder()) return@runCatching null
                val remote = IIntelligentVehicleControlService.Stub.asInterface(binder)
                remote.registerDataChangedListener(packageName, listener)
                remote.addListenerKey(packageName, VehicleKeys.All.toTypedArray())
                val initial = remote.fetchDatas(VehicleKeys.All.toTypedArray()).orEmpty()
                values.value = VehicleKeys.All.zip(initial.toList()).toMap()
                remote
            }.onFailure { Log.w(TAG, "vehicle service unavailable", it) }
                .getOrNull()
                ?.let { service.value = it }
        }

    private suspend fun disconnect(packageName: String) =
        withContext(Dispatchers.IO) {
            service.value?.let { remote ->
                runCatching { remote.unRegisterDataChangedListener(packageName, listener) }
            }
            service.value = null
        }
}
