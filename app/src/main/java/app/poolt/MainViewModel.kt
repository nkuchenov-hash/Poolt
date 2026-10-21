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
import app.poolt.network.SsdpDiscovery
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
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
    val irVerified: Boolean = false,
    val irCatalog: List<IrCatalogEntry> = emptyList(),
    val irQuery: String = "",
    val isLoadingIrCatalog: Boolean = false,
    val irTrialProfile: IrProfile? = null,
    val isTestingIr: Boolean = false,
    val irTestMessage: String = "",
    val devices: List<Device> = emptyList(),
    val selected: Device? = null,
    val presets: List<DevicePreset> = listOf(
        DevicePreset("Samsung", "Smart TV 2016+", "samsung.smarttv"),
        DevicePreset("LG", "webOS TV", "lg.webos"),
        DevicePreset("Roku", "Roku TV / Player", "roku.ecp", DeviceType.MEDIA_PLAYER)
    ),
    val isScanning: Boolean = false,
    val wifiScanMessage: String = "",
    val hasScannedWifi: Boolean = false,
    val isPairing: Boolean = false,
    val pointerChecking: Boolean = false,
    val pointerReady: Boolean = false,
    val pointerConfirmed: Boolean = false,
    val pointerStatus: String = "",
    val showDevicePicker: Boolean = false,
    val manualIp: String = "",
    val connectionMessage: String = "",
    val lastCommand: RemoteCommand? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val irTransmitter = IrTransmitter(app)
    private val irRepository = IrProfileRepository(app)

    private val lgDriver = LgWebOsDriver(app)
    private val registry = DriverRegistry(listOf(
        RokuDriver(),
        SamsungDriver(app),
        lgDriver
    ))
    private var lastPointerMoveAt = 0L


    private val initialProfiles = runCatching { irRepository.cached() }.getOrDefault(emptyList())
    private val selectionPrefs =
        app.getSharedPreferences("poolt_selection", Context.MODE_PRIVATE)

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
        val last = selectionPrefs.getString("selected_ir", null)
        if (last != null) {
            initialProfiles.firstOrNull { it.id == last }?.let {
                _state.value = _state.value.copy(
                    selectedIrProfile = it,
                    irVerified = selectionPrefs.getString("verified_ir", "") == it.id
                )
            }
        }
        val ip = selectionPrefs.getString("wifi_ip", null)
        val driverId = selectionPrefs.getString("wifi_driver", null)
        if (ip != null && driverId != null) {
            val brand = when (driverId) {
                "lg.webos" -> "LG"
                "samsung.smarttv" -> "Samsung"
                "roku.ecp" -> "Roku"
                else -> "TV"
            }
            _state.value = _state.value.copy(
                mode = if (selectionPrefs.getString("mode", "IR") == "WIFI") ControlMode.WIFI else ControlMode.IR,
                selected = Device("$driverId@$ip", "$brand Smart TV",
                    type = DeviceType.TV, driverId = driverId, brand = brand,
                    model = "Сохранённое устройство", address = ip, isOnline = false)
            )
        }
    }

    fun setMode(mode: ControlMode) {
        selectionPrefs.edit().putString("mode", mode.name).apply()
        _state.value = _state.value.copy(
            mode = mode,
            connectionMessage = when (mode) {
                ControlMode.IR -> if (_state.value.irAvailable) "ИК-порт готов" else "ИК-передатчик недоступен"
                ControlMode.WIFI -> if (_state.value.devices.isEmpty()) "Wi‑Fi: устройства не найдены" else "Wi‑Fi: найдено " + _state.value.devices.size
            }
        )
    }

    fun selectIrProfile(profile: IrProfile) {
        selectionPrefs.edit().putString("selected_ir", profile.id)
            .remove("verified_ir").putString("mode", "IR").apply()
        _state.value = _state.value.copy(
            mode = ControlMode.IR,
            selectedIrProfile = profile,
            irVerified = false,
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
                    selectedIrProfile = profiles.firstOrNull { it.id == currentId } ?: profiles.firstOrNull(),
                    irVerified = profiles.any { it.id == currentId } && _state.value.irVerified
                )
            }
        }
    }

    fun scan() {
        if (_state.value.isScanning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true,
                wifiScanMessage = "Ищем телевизоры в локальной сети…")
            val cm = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val onLocalNetwork = network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                network?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true ||
                network?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val result = withContext(Dispatchers.IO) {
                runCatching { SsdpDiscovery.discover(getApplication()) }
            }
            val devices = result.getOrDefault(emptyList())
            val message = when {
                result.isFailure -> "Ошибка поиска: " +
                    (result.exceptionOrNull()?.localizedMessage ?: "проверьте соединение")
                devices.isNotEmpty() -> "Найдено телевизоров: " + devices.size + ". Выберите устройство."
                !onLocalNetwork -> "Подключите телефон к Wi-Fi сети телевизора и повторите поиск."
                else -> "Телевизоры не найдены. Включите ТВ, убедитесь, что он и телефон в одной сети, и разрешите управление с телефона на ТВ."
            }
            _state.value = _state.value.copy(devices = devices, isScanning = false,
                hasScannedWifi = true, wifiScanMessage = message, connectionMessage = message)
        }
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

    fun clearIrTrial() {
        _state.value = _state.value.copy(
            irTrialProfile = null, irTestMessage = "", isTestingIr = false
        )
    }

    // Loading a code set is NOT proof it controls the whole TV.
    // The user must verify individual navigation/volume commands before saving.
    fun testIrEntry(entry: IrCatalogEntry) {
        if (_state.value.isTestingIr) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                irTrialProfile = null, isTestingIr = true,
                irTestMessage = "Загружаем код-набор…"
            )
            val result = withContext(Dispatchers.IO) { irRepository.fetchIrdbProfile(entry) }
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    isTestingIr = false,
                    irTestMessage = "Код не поддерживается: " +
                        (result.exceptionOrNull()?.message ?: "ошибка")
                )
                return@launch
            }
            val profile = result.getOrThrow()
            val required = setOf(RemoteCommand.UP, RemoteCommand.DOWN,
                RemoteCommand.LEFT, RemoteCommand.RIGHT,
                RemoteCommand.OK, RemoteCommand.VOLUME_UP)
            val missing = required.filterNot { profile.commands.containsKey(it) }
            _state.value = _state.value.copy(
                isTestingIr = false,
                irTrialProfile = profile,
                irTestMessage = if (missing.isEmpty()) {
                    "Код загружен. Проверьте кнопки по очереди на телевизоре."
                } else {
                    "Этот код неполный: отсутствует " + missing.size +
                        " основных команд. Рекомендуем следующий код."
                }
            )
        }
    }

    fun sendIrTestCommand(command: RemoteCommand) {
        val profile = _state.value.irTrialProfile ?: return
        val result = irTransmitter.send(profile, command)
        _state.value = _state.value.copy(
            irTestMessage = if (result.isSuccess) "Команда отправлена. Проверьте реакцию телевизора."
                else "Не удалось отправить: " + (result.exceptionOrNull()?.message ?: "нет кода")
        )
    }

    fun confirmIrTrial() {
        val profile = _state.value.irTrialProfile ?: return
        selectionPrefs.edit().putString("selected_ir", profile.id)
            .putString("verified_ir", profile.id).putString("mode", "IR").apply()
        val merged = _state.value.irProfiles.filterNot { it.id == profile.id } + profile
        _state.value = _state.value.copy(
            irProfiles = merged, selectedIrProfile = profile, irVerified = true,
            irTrialProfile = null, irTestMessage = "", mode = ControlMode.IR,
            showDevicePicker = false, connectionMessage = "ИК-профиль выбран · " + profile.brand
        )
    }

    fun closePicker() { _state.value = _state.value.copy(showDevicePicker = false) }
    fun setManualIp(value: String) { _state.value = _state.value.copy(manualIp = value.filter { it.isDigit() || it == '.' }) }

    fun selectDevice(device: Device) {
        selectionPrefs.edit().putString("wifi_ip", device.address)
            .putString("wifi_driver", device.driverId).putString("mode", "WIFI").apply()
        _state.value = _state.value.copy(
            mode = ControlMode.WIFI,
            selected = device,
            pointerReady = false,
            pointerConfirmed = false,
            pointerStatus = "",
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
            pointerReady = false,
            pointerConfirmed = false,
            pointerStatus = "",
            showDevicePicker = false
        )
        pairSelected()
    }

    fun pairSelected() {
        val device = _state.value.selected ?: return
        val driver = registry.driverFor(device) ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isPairing = true, pointerChecking = false,
                pointerReady = false, pointerConfirmed = false,
                pointerStatus = "", connectionMessage = "Подключение к " + device.name + "…"
            )
            val result = driver.pair(device)
            if (_state.value.selected?.id != device.id) return@launch
            _state.value = _state.value.copy(
                isPairing = false,
                selected = device.copy(isOnline = result.isSuccess),
                connectionMessage = if (result.isSuccess) "Wi-Fi подключено"
                    else (result.exceptionOrNull()?.message ?: "Ошибка подключения")
            )
            if (result.isSuccess && device.driverId == "lg.webos") {
                checkLgPointer()
            }
        }
    }

    fun checkLgPointer() {
        val device = _state.value.selected ?: return
        if (device.driverId != "lg.webos" || !device.isOnline) return
        if (_state.value.pointerChecking) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                pointerChecking = true, pointerReady = false, pointerConfirmed = false,
                pointerStatus = "Проверяем поддержку курсора webOS…"
            )
            val result = runCatching {
                kotlinx.coroutines.withTimeout(10000L) { lgDriver.probePointer(device) }
            }.getOrElse { Result.failure(it) }
            if (_state.value.selected?.id != device.id) return@launch
            val previousConfirmation = selectionPrefs.getString("confirmed_pointer_ip", null) == device.address
            _state.value = _state.value.copy(
                pointerChecking = false,
                pointerReady = result.isSuccess,
                pointerConfirmed = result.isSuccess && previousConfirmation,
                pointerStatus = if (result.isSuccess) "Управление курсором доступно. Проверьте движение на ТВ."
                    else "Управление курсором недоступно: " +
                        (result.exceptionOrNull()?.localizedMessage ?: "LG не разрешил подключение")
            )
        }
    }

    fun confirmLgPointerWorks() {
        val device = _state.value.selected ?: return
        if (device.driverId != "lg.webos" || !_state.value.pointerReady || !device.isOnline) return
        selectionPrefs.edit().putString("confirmed_pointer_ip", device.address).apply()
        _state.value = _state.value.copy(
            pointerConfirmed = true, pointerStatus = "Управление курсором проверено на телевизоре"
        )
    }

    fun reportLgPointerProblem() {
        selectionPrefs.edit().remove("confirmed_pointer_ip").apply()
        _state.value = _state.value.copy(
            pointerConfirmed = false, pointerStatus = "Курсор не подтверждён. Попробуйте повторить подключение."
        )
    }

    fun movePointer(dx: Float, dy: Float) {
        val device = _state.value.selected ?: return
        if (_state.value.mode != ControlMode.WIFI ||
            device.driverId != "lg.webos" || !device.isOnline) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPointerMoveAt < 30L) return
        lastPointerMoveAt = now
        val moveX = (dx * 1.7f).toInt()
        val moveY = (dy * 1.7f).toInt()
        if (moveX == 0 && moveY == 0) return
        viewModelScope.launch {
            val result = lgDriver.movePointer(device, moveX, moveY)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    connectionMessage = "Тачпад: " +
                        (result.exceptionOrNull()?.localizedMessage ?: "нет соединения")
                )
            }
        }
    }

    fun clickPointer() {
        val device = _state.value.selected ?: return
        if (device.driverId != "lg.webos" || !device.isOnline) return
        viewModelScope.launch { lgDriver.clickPointer(device) }
    }

    fun scrollPointer(amount: Float) {
        val device = _state.value.selected ?: return
        if (device.driverId != "lg.webos" || !device.isOnline) return
        viewModelScope.launch { lgDriver.scrollPointer(device, 0, amount.toInt()) }
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
