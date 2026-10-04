package app.poolt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.poolt.core.*
import app.poolt.data.DemoDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicePreset(
    val brand: String,
    val model: String,
    val driverId: String,
    val type: DeviceType = DeviceType.TV
)

data class MainUiState(
    val devices: List<Device> = emptyList(),
    val selected: Device? = null,
    val presets: List<DevicePreset> = listOf(
        DevicePreset("Samsung", "Smart TV", "samsung.smarttv"),
        DevicePreset("LG", "webOS TV", "lg.webos"),
        DevicePreset("Google", "Android TV / Google TV", "android.tv"),
        DevicePreset("Roku", "Roku TV / Player", "roku.ecp", DeviceType.MEDIA_PLAYER),
        DevicePreset("Sony", "BRAVIA", "sony.bravia"),
        DevicePreset("Philips", "Smart TV", "philips.smarttv"),
        DevicePreset("TCL", "Google TV / Roku TV", "tcl.smarttv"),
        DevicePreset("Hisense", "VIDAA / Google TV", "hisense.smarttv")
    ),
    val isScanning: Boolean = false,
    val showDevicePicker: Boolean = false,
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

    fun openPicker() { _state.value = _state.value.copy(showDevicePicker = true) }
    fun closePicker() { _state.value = _state.value.copy(showDevicePicker = false) }

    fun choosePreset(preset: DevicePreset) {
        _state.value = _state.value.copy(
            selected = Device(
                id = "manual-" + preset.driverId,
                name = preset.brand + " " + preset.model,
                type = preset.type,
                driverId = preset.driverId,
                brand = preset.brand,
                model = preset.model,
                isOnline = false
            ),
            showDevicePicker = false
        )
    }

    fun send(command: RemoteCommand) {
        val device = _state.value.selected ?: return
        val driver = registry.driverFor(device)
        if (driver == null) {
            _state.value = _state.value.copy(lastCommand = command)
            return
        }
        viewModelScope.launch {
            if (driver.send(device, command).isSuccess) {
                _state.value = _state.value.copy(lastCommand = command)
            }
        }
    }
}
