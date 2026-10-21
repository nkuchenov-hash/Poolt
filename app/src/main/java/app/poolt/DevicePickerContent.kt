package app.poolt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.poolt.core.DeviceType
import app.poolt.ir.IrCatalogEntry

private val PickerBg = Color(0xFF17171B)
private val PickerTile = Color(0xFF242428)
private val PickerAccent = Color(0xFFC52A35)
private val PickerMuted = Color(0xFFB8B8BE)

private data class Kind(val code: String, val title: String, val icon: ImageVector)

private val kinds = listOf(
    Kind("TV", "Телевизор", Icons.Default.Tv),
    Kind("SET_TOP", "Приставка", Icons.Default.Dns),
    Kind("AC", "Кондиционер", Icons.Default.AcUnit),
    Kind("AUDIO", "Аудио и саундбар", Icons.Default.Speaker),
    Kind("PROJECTOR", "Проектор", Icons.Default.Videocam),
    Kind("FAN", "Вентилятор", Icons.Default.Air),
    Kind("OTHER", "Другая техника", Icons.Default.DevicesOther)
)

private fun kindOf(raw: String): String {
    val s = raw.uppercase().replace("_", " ").replace("-", " ")
    return when {
        s.contains("TV") || s.contains("TELEVISION") -> "TV"
        s.contains("SAT") || s.contains("CABLE") || s.contains("SET TOP") ||
            s.contains("STB") || s.contains("CONVERTER") || s.contains("DVB") -> "SET_TOP"
        s.contains("AIR CONDITION") || s == "AC" || s.contains("HVAC") -> "AC"
        s.contains("AUDIO") || s.contains("AMP") || s.contains("RECEIVER") ||
            s.contains("SOUNDBAR") || s.contains("HI FI") || s.contains("SPEAKER") -> "AUDIO"
        s.contains("PROJECTOR") || s.contains("BEAMER") -> "PROJECTOR"
        s.contains("FAN") -> "FAN"
        else -> "OTHER"
    }
}

private fun kindOf(type: DeviceType): String = when (type) {
    DeviceType.TV -> "TV"
    DeviceType.SET_TOP_BOX, DeviceType.MEDIA_PLAYER -> "SET_TOP"
    DeviceType.AIR_CONDITIONER -> "AC"
    DeviceType.AUDIO, DeviceType.SOUNDBAR -> "AUDIO"
    DeviceType.PROJECTOR -> "PROJECTOR"
    DeviceType.FAN -> "FAN"
    else -> "OTHER"
}

@Composable
fun DevicePickerContent(state: MainUiState, vm: MainViewModel) {
    var wifi by remember { mutableStateOf(state.mode == ControlMode.WIFI) }
    var type by remember { mutableStateOf<String?>(null) }
    var brand by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var trialIndex by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    val candidates = remember(state.irCatalog, type, brand) {
        if (type == null || brand == null) emptyList()
        else state.irCatalog.filter { kindOf(it.deviceType) == type && it.brand.equals(brand, true) }
            .sortedBy { it.codeSet.lowercase() }
    }
    val local = remember(state.irProfiles, type, brand) {
        state.irProfiles.filter { type != null && brand != null &&
            kindOf(it.deviceType) == type && it.brand.equals(brand, true) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!wifi && (type != null || brand != null || testing)) {
                IconButton(onClick = {
                    when {
                        testing -> { testing = false; vm.clearIrTrial() }
                        brand != null -> { brand = null; search = ""; vm.clearIrTrial() }
                        else -> { type = null; search = "" }
                    }
                }) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
            }
            Column(Modifier.weight(1f)) {
                Text("Добавить устройство", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        wifi -> "Поиск в домашней сети"
                        testing -> "Проверка пульта"
                        brand != null -> "${kinds.firstOrNull { it.code == type }?.title ?: "Устройство"} · ${brand}"
                        type != null -> "Выберите производителя"
                        else -> "Что будем управлять?"
                    },
                    color = PickerMuted,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().background(PickerTile, RoundedCornerShape(14.dp)).padding(4.dp)
        ) {
            listOf(false to "ИК-пульт", true to "Wi-Fi").forEach { (item, text) ->
                Box(
                    Modifier.weight(1f)
                        .background(if (wifi == item) Color(0xFF48484E) else Color.Transparent, RoundedCornerShape(11.dp))
                        .clickable { wifi = item }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(text, fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (wifi) {
            WifiDevicePicker(state, vm)
        } else {
            if (!state.irAvailable) {
                Text("Android не обнаружил ИК-передатчик на телефоне. Выбор кодов доступен, но для передачи сигналов нужен ИК-порт.", color = Color(0xFFFFB66B), fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
            }
            when {
                type == null -> {
                    Text("Тип устройства", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    kinds.forEach { item ->
                        PickerRow(item.title, "", item.icon) {
                            type = item.code; brand = null; search = ""; testing = false
                        }
                    }
                }
                brand == null -> {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Найти бренд, например LG") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    val names = remember(type, state.irCatalog, state.irProfiles) {
                        (state.irCatalog.filter { kindOf(it.deviceType) == type }.map { it.brand } +
                         state.irProfiles.filter { kindOf(it.deviceType) == type }.map { it.brand })
                            .distinctBy { it.lowercase() }
                            .sortedWith(compareBy<String> {
                                val common = listOf("LG", "Samsung", "Sony", "Philips", "Xiaomi", "Panasonic", "TCL", "Hisense")
                                val index = common.indexOfFirst { popular -> it.equals(popular, true) }
                                if (index >= 0) index else 100
                            }.thenBy { it.lowercase() })
                    }
                    if (state.isLoadingIrCatalog && names.isEmpty()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Загружаем каталог производителей…", color = PickerMuted)
                    }
                    if (names.isEmpty() && !state.isLoadingIrCatalog) {
                        Text("Каталог недоступен. Нажмите «Обновить» и проверьте соединение.", color = PickerMuted)
                    }
                    names.filter { search.isBlank() || it.contains(search, true) }.take(70).forEach { name ->
                        PickerRow(name, "", Icons.Default.ChevronRight) { brand = name; search = ""; testing = false }
                    }
                    if (!state.isLoadingIrCatalog) TextButton(onClick = vm::loadWorldwideIrCatalog) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Обновить каталог")
                    }
                }
                testing -> {
                    Text("Наведите телефон на ${brand}. Проверим кнопку питания.", fontSize = 16.sp)
                    Spacer(Modifier.height(14.dp))
                    if (candidates.isNotEmpty()) {
                        Text("Вариант ${trialIndex + 1} из ${candidates.size}", color = PickerMuted)
                        Text("Код-набор: ${candidates[trialIndex.coerceIn(0, candidates.lastIndex)].codeSet}",
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { vm.testIrEntry(candidates[trialIndex.coerceIn(0, candidates.lastIndex)]) },
                            enabled = !state.isTestingIr,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PickerAccent)
                        ) {
                            if (state.isTestingIr) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.PowerSettingsNew, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Проверить POWER", fontSize = 16.sp)
                        }
                        if (state.irTestMessage.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(state.irTestMessage, color = PickerMuted)
                        }
                        if (state.irTrialProfile != null) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = vm::confirmIrTrial,
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Работает — сохранить") }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                trialIndex = (trialIndex + 1) % candidates.size
                                vm.clearIrTrial()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Не работает — следующий код") }
                    } else {
                        Text("Для этого бренда нет загруженных код-наборов. Обновите каталог или выберите доступный встроенный профиль.", color = PickerMuted)
                    }
                }
                else -> {
                    Text("Модель или код пульта", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Не знаете модель? Приложение поможет проверить коды по очереди.", color = PickerMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { testing = true; trialIndex = 0; vm.clearIrTrial() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PickerAccent)
                    ) { Icon(Icons.Default.AutoFixHigh, null); Spacer(Modifier.width(9.dp)); Text("Не знаю модель — подобрать код", fontSize = 15.sp) }
                    Spacer(Modifier.height(16.dp))
                    if (local.isNotEmpty()) {
                        Text("Быстрый выбор", color = PickerMuted, fontWeight = FontWeight.SemiBold)
                        local.forEach { profile ->
                            PickerRow(profile.model, "Встроенный профиль", Icons.Default.SettingsRemote) {
                                vm.selectIrProfile(profile)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (candidates.isNotEmpty()) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Модель / номер код-набора") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Дополнительные коды IRDB", color = PickerMuted, fontWeight = FontWeight.SemiBold)
                        candidates.withIndex().filter {
                            search.isBlank() || it.value.codeSet.contains(search, true)
                        }.take(60).forEach { indexed ->
                            PickerRow("Набор ${indexed.value.codeSet}", "Проверить совместимость", Icons.Default.SettingsRemote) {
                                trialIndex = indexed.index
                                testing = true
                                vm.clearIrTrial()
                            }
                        }
                    } else if (state.isLoadingIrCatalog) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (local.isEmpty()) {
                        Text("Коды для выбранного бренда не найдены. Обновите каталог.", color = PickerMuted)
                    }
                    TextButton(onClick = vm::loadWorldwideIrCatalog) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Обновить код-наборы")
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 7.dp)
            .background(PickerTile, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = PickerMuted, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = PickerMuted)
    }
}

@Composable
private fun WifiDevicePicker(state: MainUiState, vm: MainViewModel) {
    Text("Smart TV и устройства в одной сети Wi-Fi", color = PickerMuted)
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = vm::scan,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !state.isScanning,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF35353B),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF29292D),
            disabledContentColor = Color.White
        )
    ) {
        if (state.isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text("Найти устройства", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
    state.devices.forEach { device ->
        PickerRow(device.name, device.driverId, Icons.Default.Wifi) { vm.selectDevice(device) }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.manualIp,
        onValueChange = vm::setManualIp,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("IP-адрес телевизора") },
        singleLine = true
    )
    state.presets.forEach { preset ->
        PickerRow(preset.brand, preset.model, Icons.Default.Tv) { vm.addManual(preset) }
    }
}
