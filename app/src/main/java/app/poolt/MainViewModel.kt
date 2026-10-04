package app.poolt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.poolt.core.*
import app.poolt.drivers.LgWebOsDriver
import app.poolt.drivers.RokuDriver
import app.poolt.drivers.SamsungDriver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicePreset(val brand: String, val model: String, val driverId: String, val type: DeviceType = DeviceType.TV)

data class MainUiState(
    val devices: List<Device> = emptyList(),
    val selected: Device? = null,
    val presets: List<DevicePreset> = listOf(
        DevicePreset("Samsung", "Smart TV 2016+", "samsung.smarttv"),
        DevicePreset("LG", "webOS TV", "lg.webos"),
        DevicePreset("Roku", "Roku TV / Player", "roku.ecp", DeviceType.MEDIA_PLAYER)
    ),
    val isScanning: Boolean = false,
    val isPairing: Boolean = false,
    val showDevicePicker: Boolean = false,
    val manualIp: String = "",
    val connectionMessage: String = "Поиск устройств…",
    val lastCommand: RemoteCommand? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val registry = DriverRegistry(listOf(
        RokuDriver(),
        SamsungDriver(app),
        LgWebOsDriver(app)
    ))
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init { scan() }

    fun scan() = viewModelScope.launch {
        _state.value = _state.value.copy(isScanning = true, connectionMessage = "Ищу устройства в Wi‑Fi…")
        val devices = registry.discoverAll()
        _state.value = _state.value.copy(
            devices = devices,
            isScanning = false,
            connectionMessage = if (devices.isEmpty()) "Ничего не найдено. Можно добавить по IP." else "Найдено: " + devices.size
        )
    }

    fun openPicker() { _state.value = _state.value.copy(showDevicePicker = true) }
    fun closePicker() { _state.value = _state.value.copy(showDevicePicker = false) }
    fun setManualIp(value: String) { _state.value = _state.value.copy(manualIp = value.filter { it.isDigit() || it == '.' }) }

    fun selectDevice(device: Device) {
        _state.value = _state.value.copy(selected = device, showDevicePicker = false)
        pairSelected()
    }

    fun addManual(preset: DevicePreset) {
        val ip = _state.value.manualIp.trim()
        if (ip.isBlank()) {
            _state.value = _state.value.copy(connectionMessage = "Введите IP устройства")
            return
        }
        val device = Device(
            id = "manual-" + preset.driverId + "@" + ip,
            name = preset.brand + " · " + ip,
            type = preset.type,
            driverId = preset.driverId,
            brand = preset.brand,
            model = preset.model,
            address = ip,
            isOnline = false
        )
        _state.value = _state.value.copy(selected = device, showDevicePicker = false)
        pairSelected()
    }

    fun pairSelected() {
        val device = _state.value.selected ?: return
        val driver = registry.driverFor(device) ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true, connectionMessage = "Подключение к " + device.name + "…")
            val result = driver.pair(device)
            _state.value = _state.value.copy(
                isPairing = false,
                selected = if (result.isSuccess) device.copy(isOnline = true) else device.copy(isOnline = false),
                connectionMessage = if (result.isSuccess) "Подключено" else (result.exceptionOrNull()?.message ?: "Ошибка подключения")
            )
        }
    }

    fun send(command: RemoteCommand) {
        val device = _state.value.selected ?: run {
            _state.value = _state.value.copy(connectionMessage = "Сначала выберите устройство")
            return
        }
        val driver = registry.driverFor(device) ?: return
        viewModelScope.launch {
            val result = driver.send(device, command)
            _state.value = _state.value.copy(
                lastCommand = if (result.isSuccess) command else _state.value.lastCommand,
                connectionMessage = if (result.isSuccess) "Команда отправлена" else (result.exceptionOrNull()?.message ?: "Ошибка команды")
            )
        }
    }
}
