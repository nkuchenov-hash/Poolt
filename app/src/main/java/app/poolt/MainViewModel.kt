package app.poolt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.poolt.core.*
import app.poolt.drivers.LgWebOsDriver
import app.poolt.drivers.RokuDriver
import app.poolt.drivers.SamsungDriver
import app.poolt.ir.IrProfile
import app.poolt.ir.IrCatalogEntry
import app.poolt.ir.IrProfileRepository
import app.poolt.ir.IrTransmitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ControlMode { IR, WIFI }

data class DevicePreset(val brand: String, val model: String, val driverId: String, val type: DeviceType = DeviceType.TV)

data class MainUiState(
    val mode: ControlMode = ControlMode.IR,
    val irAvailable: Boolean = false,
    val irProfiles: List<IrProfile> = emptyList(),
    val selectedIrProfile: IrProfile? = null,
    val irCatalog: List<IrCatalogEntry> = emptyList(),
    val irQuery: String = "",
    val isLoadingIrCatalog: Boolean = false,
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
    val connectionMessage: String = "",
    val lastCommand: RemoteCommand? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val irTransmitter = IrTransmitter(app)
    private val irRepository = IrProfileRepository(app)

    private val registry = DriverRegistry(listOf(
        RokuDriver(),
        SamsungDriver(app),
        LgWebOsDriver(app)
    ))

    private val initialProfiles = runCatching { irRepository.cached() }.getOrDefault(emptyList())

    private val _state = MutableStateFlow(
        MainUiState(
            irAvailable = irTransmitter.hasEmitter,
            irProfiles = initialProfiles,
            selectedIrProfile = initialProfiles.firstOrNull(),
            irCatalog = runCatching { irRepository.cachedIrdbIndex() }.getOrDefault(emptyList()),
            connectionMessage = if (irTransmitter.hasEmitter) "ИК-порт готов" else "На телефоне не найден ИК-передатчик"
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        refreshIrCatalog()
    }

    fun setMode(mode: ControlMode) {
        _state.value = _state.value.copy(
            mode = mode,
            connectionMessage = when (mode) {
                ControlMode.IR -> if (_state.value.irAvailable) "ИК-порт готов" else "ИК-передатчик недоступен"
                ControlMode.WIFI -> if (_state.value.devices.isEmpty()) "Wi‑Fi: устройства не найдены" else "Wi‑Fi: найдено " + _state.value.devices.size
            }
        )
    }

    fun selectIrProfile(profile: IrProfile) {
        _state.value = _state.value.copy(
            mode = ControlMode.IR,
            selectedIrProfile = profile,
            showDevicePicker = false,
            connectionMessage = if (_state.value.irAvailable) "ИК-профиль выбран" else "На телефоне нет ИК-порта"
        )
    }

    fun refreshIrCatalog() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { irRepository.refresh() }
            if (result.isSuccess) {
                val profiles = result.getOrThrow()
                val currentId = _state.value.selectedIrProfile?.id
                _state.value = _state.value.copy(
                    irProfiles = profiles,
                    selectedIrProfile = profiles.firstOrNull { it.id == currentId } ?: profiles.firstOrNull()
                )
            }
        }
    }

    fun scan() = viewModelScope.launch {
        _state.value = _state.value.copy(isScanning = true)
        val devices = registry.discoverAll()
        _state.value = _state.value.copy(
            devices = devices,
            isScanning = false,
            connectionMessage = if (_state.value.mode == ControlMode.WIFI) {
                if (devices.isEmpty()) "Wi‑Fi: ничего не найдено. Можно добавить по IP." else "Wi‑Fi: найдено " + devices.size
            } else _state.value.connectionMessage
        )
    }

    fun openPicker() {
        _state.value = _state.value.copy(showDevicePicker = true)
        if (_state.value.irCatalog.isEmpty()) loadWorldwideIrCatalog()
    }

    fun setIrQuery(value: String) {
        _state.value = _state.value.copy(irQuery = value)
    }

    fun loadWorldwideIrCatalog() {
        if (_state.value.isLoadingIrCatalog) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingIrCatalog = true)
            val result = withContext(Dispatchers.IO) { irRepository.fetchIrdbIndex() }
            _state.value = _state.value.copy(
                isLoadingIrCatalog = false,
                irCatalog = result.getOrElse { _state.value.irCatalog },
                connectionMessage = if (result.isSuccess)
                    "Мировой ИК-каталог загружен: " + result.getOrThrow().size + " код-сетов"
                else "Не удалось обновить мировой каталог: " + (result.exceptionOrNull()?.message ?: "ошибка")
            )
        }
    }

    fun selectWorldwideIrEntry(entry: IrCatalogEntry) {
        viewModelScope.launch {
            _state.value = _state.value.copy(connectionMessage = "Загрузка ИК-кода " + entry.brand + "…")
            val result = withContext(Dispatchers.IO) { irRepository.fetchIrdbProfile(entry) }
            if (result.isSuccess) {
                val profile = result.getOrThrow()
                val merged = (_state.value.irProfiles.filterNot { it.id == profile.id } + profile)
                _state.value = _state.value.copy(
                    mode = ControlMode.IR,
                    irProfiles = merged,
                    selectedIrProfile = profile,
                    showDevicePicker = false,
                    connectionMessage = "ИК-профиль загружен · " + profile.brand
                )
            } else {
                _state.value = _state.value.copy(
                    connectionMessage = result.exceptionOrNull()?.message ?: "Этот ИК-профиль пока не поддерживается"
                )
            }
        }
    }
    fun closePicker() { _state.value = _state.value.copy(showDevicePicker = false) }
    fun setManualIp(value: String) { _state.value = _state.value.copy(manualIp = value.filter { it.isDigit() || it == '.' }) }

    fun selectDevice(device: Device) {
        _state.value = _state.value.copy(
            mode = ControlMode.WIFI,
            selected = device,
            showDevicePicker = false
        )
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
        _state.value = _state.value.copy(
            mode = ControlMode.WIFI,
            selected = device,
            showDevicePicker = false
        )
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
                connectionMessage = if (result.isSuccess) "Wi‑Fi подключено" else (result.exceptionOrNull()?.message ?: "Ошибка подключения")
            )
        }
    }

    fun send(command: RemoteCommand) {
        when (_state.value.mode) {
            ControlMode.IR -> sendIr(command)
            ControlMode.WIFI -> sendWifi(command)
        }
    }

    private fun sendIr(command: RemoteCommand) {
        val profile = _state.value.selectedIrProfile ?: run {
            _state.value = _state.value.copy(connectionMessage = "Выберите ИК-профиль")
            return
        }
        val result = irTransmitter.send(profile, command)
        _state.value = _state.value.copy(
            lastCommand = if (result.isSuccess) command else _state.value.lastCommand,
            connectionMessage = if (result.isSuccess) "ИК-команда отправлена" else (result.exceptionOrNull()?.message ?: "Ошибка ИК")
        )
    }

    private fun sendWifi(command: RemoteCommand) {
        val device = _state.value.selected ?: run {
            _state.value = _state.value.copy(connectionMessage = "Сначала выберите Wi‑Fi устройство")
            return
        }
        val driver = registry.driverFor(device) ?: return
        viewModelScope.launch {
            val result = driver.send(device, command)
            _state.value = _state.value.copy(
                lastCommand = if (result.isSuccess) command else _state.value.lastCommand,
                connectionMessage = if (result.isSuccess) "Wi‑Fi команда отправлена" else (result.exceptionOrNull()?.message ?: "Ошибка команды")
            )
        }
    }
}
