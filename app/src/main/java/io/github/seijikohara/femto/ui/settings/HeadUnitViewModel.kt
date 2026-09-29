package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.privileged.HeadUnitSetup
import io.github.seijikohara.femto.data.privileged.SetupReport
import io.github.seijikohara.femto.data.privileged.ShizukuGateway
import io.github.seijikohara.femto.data.privileged.ShizukuState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The head-unit (Shizuku) block of Settings > System. */
internal data class HeadUnitUiState(
    val shizuku: ShizukuState,
    val busy: Boolean,
    val lastSetup: SetupReport?,
    // Null until the "make default home" row has been used this session.
    val homeElected: Boolean?,
) {
    companion object {
        val Initial = HeadUnitUiState(ShizukuState.UNAVAILABLE, busy = false, lastSetup = null, homeElected = null)
    }
}

internal sealed interface HeadUnitAction {
    data object RequestAccess : HeadUnitAction

    data object ApplySetup : HeadUnitAction

    data object MakeDefaultHome : HeadUnitAction
}

private data class LocalState(
    val busy: Boolean = false,
    val lastSetup: SetupReport? = null,
    val homeElected: Boolean? = null,
)

internal class HeadUnitViewModel(
    shizukuState: Flow<ShizukuState>,
    private val requestAccess: () -> Unit,
    private val setup: HeadUnitSetup,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<HeadUnitUiState> =
        combine(shizukuState, local) { shizuku, state ->
            HeadUnitUiState(shizuku, state.busy, state.lastSetup, state.homeElected)
        }.stateIn(viewModelScope, WhileUiSubscribed, HeadUnitUiState.Initial)

    fun onAction(action: HeadUnitAction) {
        when (action) {
            HeadUnitAction.RequestAccess -> {
                requestAccess()
            }

            HeadUnitAction.ApplySetup -> {
                runBusy { copy(lastSetup = setup.grantLauncherAccess()) }
            }

            HeadUnitAction.MakeDefaultHome -> {
                runBusy { copy(homeElected = setup.makeDefaultHome()) }
            }
        }
    }

    private fun runBusy(block: suspend LocalState.() -> LocalState) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val next = local.value.block()
            local.value = next.copy(busy = false)
        }
    }
}

internal class HeadUnitViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        ShizukuGateway.start()

        @Suppress("UNCHECKED_CAST")
        return HeadUnitViewModel(
            shizukuState = ShizukuGateway.state,
            requestAccess = ShizukuGateway::requestPermission,
            setup = HeadUnitSetup(application),
        ) as T
    }
}
