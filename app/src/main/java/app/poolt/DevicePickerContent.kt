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
    var verified by remember(brand, trialIndex) { mutableStateOf(setOf<app.poolt.core.RemoteCommand>()) }
    var showRawCodes by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(wifi, type, brand, testing, trialIndex) { scroll.scrollTo(0) }

    val candidates = remember(state.irCatalog, type, brand) {
        if (type == null || brand == null) emptyList()
        else state.irCatalog.filter { kindOf(it.deviceType) == type && it.brand.equals(brand, true) }
            .sortedWith(compareByDescending<IrCatalogEntry> { it.codeSet.contains("LG", true) }
                .thenBy { it.codeSet.lowercase() })
    }
    val local = remember(state.irProfiles, type, brand) {
        state.irProfiles.filter {
            type != null && brand != null &&
                kindOf(it.deviceType) == type && it.brand.equals(brand, true)
        }
    }
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    Column(
        Modifier.fillMaxWidth().height(screen * 0.78f)
            .padding(horizontal = 20.dp).padding(bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!wifi && (type != null || brand != null || testing)) {
                IconButton(onClick = {
                    when {
                        testing -> { testing = false; vm.clearIrTrial() }
                        brand != null -> { brand = null; search = ""; vm.clearIrTrial() }
                        else -> { type = null; search = "" }
                    }
                }) { Icon(Icons.Default.ArrowBack, "Назад") }
            }
            Column(Modifier.weight(1f)) {
                Text("Добавить устройство", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(when {
                    wifi -> "Телевизоры в вашей сети"
                    testing -> "Проверяем каждую кнопку"
                    brand != null -> "${brand} · ${kinds.firstOrNull { it.code == type }?.title ?: "Устройство"}"
                    type != null -> "Выберите производителя"
                    else -> "Выберите тип устройства"
                }, color = PickerMuted, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().background(PickerTile, RoundedCornerShape(13.dp)).padding(4.dp)
        ) {
            listOf(false to "ИК", true to "Wi-Fi").forEach { (flag, label) ->
                Box(
                    Modifier.weight(1f)
                        .background(if (wifi == flag) Color(0xFF48484D) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { wifi = flag }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            if (wifi) {
                WifiDevicePicker(state, vm)
            } else {
                if (!state.irAvailable) {
                    Text(
                        "В телефоне не найден ИК-передатчик. Для Smart TV можно использовать Wi-Fi.",
                        color = Color(0xFFFFB16D), fontSize = 16.sp
                    )
                    Spacer(Modifier.height(12.dp))
                }
                when {
                    type == null -> {
                        Text("Что будем управлять?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(9.dp))
                        kinds.forEach { item ->
                            PickerRow(item.title, "", item.icon) {
                                type = item.code; brand = null; search = ""; testing = false
                            }
                        }
                    }
                    brand == null -> {
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Найти бренд, например LG") },
                            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
                        )
                        Spacer(Modifier.height(10.dp))
                        val names = remember(type, state.irCatalog, state.irProfiles) {
                            (state.irCatalog.filter { kindOf(it.deviceType) == type }.map { it.brand } +
                             state.irProfiles.filter { kindOf(it.deviceType) == type }.map { it.brand })
                                .distinctBy { it.lowercase() }
                                .sortedWith(compareBy<String> {
                                    val common = listOf("LG", "Samsung", "Sony", "Xiaomi", "Philips", "Panasonic", "TCL", "Hisense")
                                    val idx = common.indexOfFirst { popular -> it.equals(popular, true) }
                                    if (idx < 0) 100 else idx
                                }.thenBy { it.lowercase() })
                        }
                        names.filter { search.isBlank() || it.contains(search, true) }.take(80).forEach { name ->
                            PickerRow(name, "", Icons.Default.ChevronRight) {
                                brand = name; search = ""; showRawCodes = false
                            }
                        }
                        if (names.isEmpty()) {
                            Text(if (state.isLoadingIrCatalog) "Загружаем каталог…" else "Не найдено брендов. Проверьте интернет.",
                                color = PickerMuted, fontSize = 16.sp)
                        }
                        TextButton(onClick = vm::loadWorldwideIrCatalog) { Text("Обновить каталог") }
                    }
                    testing -> {
                        Text("Код ${trialIndex + 1} из ${candidates.size.coerceAtLeast(1)}",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Телевизор должен реагировать именно на указанную кнопку. Одного включения недостаточно.",
                            fontSize = 16.sp, color = PickerMuted)
                        Spacer(Modifier.height(12.dp))
                        if (candidates.isNotEmpty()) {
                            if (state.irTrialProfile == null && !state.isTestingIr) {
                                Button(
                                    onClick = { vm.testIrEntry(candidates[trialIndex.coerceIn(0, candidates.lastIndex)]) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37373E), contentColor = Color.White)
                                ) { Text("Загрузить код для проверки", fontSize = 16.sp) }
                            }
                            if (state.isTestingIr) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (state.irTestMessage.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(state.irTestMessage, color = PickerMuted, fontSize = 16.sp)
                            }
                            val profile = state.irTrialProfile
                            if (profile != null) {
                                val tests = listOf(
                                    app.poolt.core.RemoteCommand.VOLUME_UP to "Громкость +",
                                    app.poolt.core.RemoteCommand.UP to "Вверх",
                                    app.poolt.core.RemoteCommand.DOWN to "Вниз",
                                    app.poolt.core.RemoteCommand.LEFT to "Влево",
                                    app.poolt.core.RemoteCommand.RIGHT to "Вправо",
                                    app.poolt.core.RemoteCommand.OK to "OK"
                                )
                                Spacer(Modifier.height(10.dp))
                                tests.forEach { (cmd, label) ->
                                    val supported = profile.commands.containsKey(cmd)
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { vm.sendIrTestCommand(cmd) },
                                            enabled = supported,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                        ) { Text(label, fontSize = 16.sp) }
                                        Spacer(Modifier.width(8.dp))
                                        Checkbox(
                                            checked = cmd in verified,
                                            onCheckedChange = { ok ->
                                                verified = if (ok) verified + cmd else verified - cmd
                                            },
                                            enabled = supported,
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2FAE69))
                                        )
                                        Text("Работает", fontSize = 14.sp, color = PickerMuted)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = vm::confirmIrTrial,
                                    enabled = verified.size == tests.size,
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF35353C), contentColor = Color.White
                                    )
                                ) { Text("Все кнопки работают — сохранить", fontSize = 16.sp) }
                                if (verified.size < tests.size) {
                                    Text("Подтверждено ${verified.size} из ${tests.size}. Непроверенный пульт не сохраняем как подходящий.",
                                        color = PickerMuted, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    trialIndex = (trialIndex + 1) % candidates.size
                                    vm.clearIrTrial()
                                    verified = emptySet()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Не подходит — следующий код", fontSize = 16.sp, color = Color.White) }
                        } else Text("Для этой модели нет доступных кодов. Попробуйте Wi-Fi.", fontSize = 16.sp)
                    }
                    else -> {
                        Text("Телевизор ${brand}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Если это Smart TV, лучше Wi-Fi: будут доступны дополнительные функции, включая курсор на LG.",
                            color = PickerMuted, fontSize = 16.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { wifi = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(7.dp))
                            Text("Найти Smart TV по Wi-Fi", color = Color.White, fontSize = 16.sp) }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { testing = true; trialIndex = 0; verified = emptySet(); vm.clearIrTrial() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35353B), contentColor = Color.White)
                        ) { Text("Не знаю модель — проверить коды", fontSize = 16.sp) }
                        if (local.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text("Встроенные пульты", color = PickerMuted, fontSize = 16.sp)
                            local.forEach { profile ->
                                PickerRow(profile.model, "Не проверен на вашем телевизоре",
                                    Icons.Default.SettingsRemote) { vm.selectIrProfile(profile) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showRawCodes = !showRawCodes }) {
                            Text(if (showRawCodes) "Скрыть код-наборы" else "Показать код-наборы (для опытных)")
                        }
                        if (showRawCodes) {
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Номер кода") }, singleLine = true
                            )
                            candidates.withIndex()
                                .filter { search.isEmpty() || it.value.codeSet.contains(search, true) }
                                .take(50).forEach { indexed ->
                                    PickerRow("Код ${indexed.value.codeSet}", "", Icons.Default.ChevronRight) {
                                        trialIndex = indexed.index; testing = true; vm.clearIrTrial()
                                    }
                                }
                        }
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
    var manual by remember { mutableStateOf(false) }
    Text("Телефон и телевизор должны быть в одной сети Wi-Fi.", color = PickerMuted, fontSize = 16.sp)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = vm::scan,
        enabled = !state.isScanning,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF303036), contentColor = Color.White
        )
    ) {
        if (state.isScanning) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Icon(Icons.Default.Search, null, tint = Color.White)
        Spacer(Modifier.width(9.dp))
        Text(if (state.isScanning) "Поиск…" else "Найти устройства",
            fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(14.dp))
    if (state.wifiScanMessage.isNotBlank()) {
        Text(state.wifiScanMessage, color = PickerMuted, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
    }
    if (state.devices.isNotEmpty()) {
        Text("Найденные устройства", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        state.devices.forEach { device ->
            PickerRow(device.name, "Нажмите для подключения", Icons.Default.Tv) { vm.selectDevice(device) }
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = { manual = !manual }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Tune, null, tint = PickerMuted)
        Spacer(Modifier.width(6.dp))
        Text(if (manual) "Скрыть ручную настройку" else "Не нашли телевизор? Ручная настройка",
            color = PickerMuted, fontSize = 16.sp)
    }
    if (manual) {
        Text("Используйте этот раздел, только если телевизор не находится автоматически.",
            fontSize = 16.sp, color = PickerMuted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.manualIp, onValueChange = vm::setManualIp,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP-адрес ТВ (дополнительно)") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        state.presets.forEach { preset ->
            PickerRow(preset.brand + " Smart TV", "Подключить вручную", Icons.Default.Wifi) {
                vm.addManual(preset)
            }
        }
    }
}
