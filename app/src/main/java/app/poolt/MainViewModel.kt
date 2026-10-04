package app.poolt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.poolt.core.*
import app.poolt.data.DemoDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val devices: List<Device> = emptyList(),
    val selected: Device? = null,
    val isScanning: Boolean = false,
    val lastCommand: RemoteCommand? = null
)

class MainViewModel : ViewModel() {
    private val registry = DriverRegistry(listOf(DemoDriver()))
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init { scan() }

    fun scan() = viewModelScope.launch {
        _state.value = _state.value.copy(isScanning = true)
        val devices = registry.discoverAll()
        _state.value = _state.value.copy(
            devices = devices,
            selected = _state.value.selected ?: devices.firstOrNull(),
            isScanning = false
        )
    }

    fun send(command: RemoteCommand) {
        val device = _state.value.selected ?: return
        val driver = registry.driverFor(device) ?: return
        viewModelScope.launch {
            if (driver.send(device, command).isSuccess) {
                _state.value = _state.value.copy(lastCommand = command)
            }
        }
    }
}
