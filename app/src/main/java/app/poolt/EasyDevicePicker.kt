package app.poolt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.poolt.core.Device
import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand
import app.poolt.ir.IrCatalogEntry
import app.poolt.ir.IrProfile

private val red = Color(0xFFE93650)
private val muted = Color(0xFFABB0BC)
private val tile = Color(0xFF252630)
private val popularBrands = listOf("LG", "Samsung", "Sony", "Philips", "Panasonic", "TCL", "Hisense", "Xiaomi")

private data class TrialCandidate(
    val key: String,
    val label: String,
    val profile: IrProfile? = null,
    val entry: IrCatalogEntry? = null
)

private fun canonicalBrand(text: String): String = when (text.trim().lowercase()) {
    "лджи", "элджи", "лг" -> "LG"
    "самсунг", "самс" -> "Samsung"
    "сони" -> "Sony"
    "филипс" -> "Philips"
    "панасоник" -> "Panasonic"
    "ксиаоми", "сяоми" -> "Xiaomi"
    else -> text.trim()
}

private fun modelToken(text: String): String =
    text.lowercase().filter { it.isLetterOrDigit() }

private fun looksLikeTv(raw: String): Boolean {
    val kind = raw.uppercase()
    return listOf("TV", "PLASMA", "LCD", "LED", "OLED", "QLED", "REAR PROJECTION",
        "UNKNOWN_", "MONITOR").any { it in kind }
}

private fun irCandidates(state: MainUiState, brandInput: String, modelInput: String): List<TrialCandidate> {
    val brand = canonicalBrand(brandInput)
    if (brand.isBlank()) return emptyList()
    val model = modelToken(modelInput)
    val saved = state.irProfiles.filter {
        it.brand.equals(brand, true) && it.deviceType == DeviceType.TV &&
            RemoteCommand.POWER in it.commands
    }.sortedWith(
        compareByDescending<IrProfile> { model.isNotEmpty() && modelToken(it.model).contains(model) }
            .thenByDescending { it.id.endsWith(".generic") }
    ).map {
        TrialCandidate("profile:" + it.id, it.model, profile = it)
    }

    // IRDB identifies most entries by protocol/device codes, not by
    // a verified TV model. Model text only ranks plausible candidates.
    val external = state.irCatalog.filter {
        it.brand.equals(brand, true) && looksLikeTv(it.deviceType)
    }.sortedWith(
        compareByDescending<IrCatalogEntry> {
            model.isNotEmpty() && modelToken(it.deviceType).contains(model)
        }.thenByDescending { it.deviceType.equals("TV", true) }
            .thenBy { it.codeSet }
    ).distinctBy { it.codeSet }.map {
        TrialCandidate("irdb:" + it.path,
            if (it.deviceType.equals("TV", true)) "Пульт TV · ${it.codeSet}"
            else it.deviceType.replace("Unknown_", "") + " · ${it.codeSet}",
            entry = it)
    }
    val all = (saved + external).distinctBy { it.key }
    return if (model.isNotEmpty()) all.sortedWith(
        compareByDescending<TrialCandidate> {
            modelToken(it.label).contains(model)
        }.thenByDescending { it.profile != null }
    ) else all
}

@Composable
fun DevicePickerContent(state: MainUiState, vm: MainViewModel) {
    var wifi by rememberSaveable { mutableStateOf(state.mode == ControlMode.WIFI || !state.irAvailable) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val height = LocalConfiguration.current.screenHeightDp.dp
    if (advanced) {
        Column(Modifier.fillMaxWidth().height(height * 0.88f)) {
            TextButton(onClick = { advanced = false }) {
                Icon(Icons.Default.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text("Назад к подключению телевизора")
            }
            LegacyDevicePickerContent(state, vm)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth()
            .heightIn(max = height * 0.88f)
            .padding(horizontal = 18.dp)
    ) {
        Text("Подключить телевизор", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Выберите удобный способ. Настройка займёт несколько действий.",
            fontSize = 14.sp, color = muted)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().background(tile, RoundedCornerShape(15.dp)).padding(4.dp)
        ) {
            listOf(false to "ИК-пульт", true to "По Wi-Fi").forEach { (mode, label) ->
                Box(
                    Modifier.weight(1f)
                        .background(if (wifi == mode) Color(0xFF913248) else Color.Transparent,
                            RoundedCornerShape(12.dp))
                        .clickable { wifi = mode; vm.clearIrTrial() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (wifi) EasyWifiPicker(state, vm)
            else EasyIrPicker(state, vm)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { advanced = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Другая техника или расширенная настройка", color = muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun EasyIrPicker(state: MainUiState, vm: MainViewModel) {
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableIntStateOf(0) }
    var confirmedPower by rememberSaveable { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    val all = remember(state.irCatalog, state.irProfiles, brand, model) {
        irCandidates(state, brand, model)
    }
    val candidate = all.getOrNull(current)
    val exactBrand = canonicalBrand(brand)
    val isReady = state.irAvailable
    val hasLoaded = candidate != null &&
        state.irTrialProfile?.id == (candidate.profile?.id ?: "irdb:" + candidate.entry?.path)

    fun resetAttempt() {
        started = false
        confirmedPower = false
        vm.clearIrTrial()
    }

    if (!isReady) {
        Text("В этом телефоне Android не обнаружил ИК-передатчик. " +
            "ИК-сигналы отправить нельзя; используйте Wi-Fi.",
            color = Color(0xFFFFB88C), fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = brand,
        onValueChange = { brand = it; current = 0; resetAttempt() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Производитель") },
        placeholder = { Text("Например, LG или Samsung") },
        leadingIcon = { Icon(Icons.Default.Tv, null) },
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        popularBrands.forEach { name ->
            SuggestionChip(
                onClick = { brand = name; current = 0; resetAttempt() },
                label = { Text(name, fontSize = 13.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (exactBrand.equals(name, true)) Color(0xFF642C3C)
                    else Color(0xFF252630)
                )
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = model,
        onValueChange = { model = it; current = 0; resetAttempt() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Модель телевизора (необязательно)") },
        placeholder = { Text("Например, OLED55C3 или UE55AU7100") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        supportingText = { Text("Модель помогает найти подходящий код. Если точного совпадения нет, попробуем коды марки.") }
    )
    Spacer(Modifier.height(8.dp))
    if (exactBrand.isBlank()) {
        Text("Начните с выбора марки телевизора.", color = muted, fontSize = 15.sp)
        return
    }
    if (all.isEmpty()) {
        Text(
            if (state.isLoadingIrCatalog) "Загружаем варианты пультов…"
            else "Для этой марки пока нет доступных ИК-кодов. Проверьте название марки или обновите каталог.",
            color = muted, fontSize = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = vm::loadWorldwideIrCatalog) { Text("Обновить каталог") }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Вариант ${current + 1} из ${all.size}",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(candidate?.label ?: "", fontSize = 12.sp, color = muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
    }
    Spacer(Modifier.height(9.dp))
    Text(
        "1. Наведите телефон на выключенный телевизор. Нажмите кнопку питания.",
        fontSize = 15.sp, color = muted
    )
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            if (candidate != null) {
                started = true
                confirmedPower = false
                when {
                    candidate.profile != null -> {
                        vm.testIrProfile(candidate.profile)
                        vm.sendIrTestCommand(RemoteCommand.POWER)
                    }
                    candidate.entry != null -> vm.testIrEntry(candidate.entry, sendPowerAfterLoad = true)
                }
            }
        },
        enabled = isReady && !state.isTestingIr && candidate != null,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = red)
    ) {
        Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(27.dp))
        Spacer(Modifier.width(12.dp))
        Text(if (started) "Повторить Power" else "Проверить питание",
            fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
    if (state.isTestingIr) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (state.irTestMessage.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(state.irTestMessage, color = muted, fontSize = 13.sp)
    }
    if (started && hasLoaded && !state.isTestingIr) {
        Spacer(Modifier.height(14.dp))
        Text("2. Телевизор включился или отреагировал?", fontSize = 16.sp)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { confirmedPower = true },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF285B49))
            ) { Text("Да, работает", fontSize = 15.sp) }
            OutlinedButton(
                onClick = {
                    current = if (current < all.lastIndex) current + 1 else 0
                    resetAttempt()
                },
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("Нет, следующий", fontSize = 14.sp) }
        }
        if (confirmedPower) {
            Spacer(Modifier.height(12.dp))
            Text("Питание подтверждено. Можно проверить другие кнопки или сохранить пульт.",
                color = muted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RemoteCommand.OK to "OK",
                    RemoteCommand.VOLUME_UP to "VOL+",
                    RemoteCommand.BACK to "Назад"
                ).forEach { (cmd, title) ->
                    OutlinedButton(
                        onClick = { vm.sendIrTestCommand(cmd) },
                        enabled = state.irTrialProfile?.commands?.containsKey(cmd) == true,
                        modifier = Modifier.weight(1f)
                    ) { Text(title, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::confirmIrTrial,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = red)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Сохранить этот пульт", fontSize = 16.sp)
            }
        }
    } else if (started && !state.isTestingIr && !hasLoaded && state.irTestMessage.contains("не", true)) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                current = if (current < all.lastIndex) current + 1 else 0
                resetAttempt()
            }, modifier = Modifier.fillMaxWidth()
        ) { Text("Следующий код") }
    }
    Spacer(Modifier.height(11.dp))
    TextButton(
        onClick = {
            current = if (current < all.lastIndex) current + 1 else 0
            resetAttempt()
        }, modifier = Modifier.fillMaxWidth()
    ) { Text("Пропустить код и попробовать следующий", color = muted) }
}

@Composable
private fun EasyWifiPicker(state: MainUiState, vm: MainViewModel) {
    var manualBrand by rememberSaveable { mutableStateOf("LG") }
    LaunchedEffect(Unit) {
        if (!state.hasScannedWifi && !state.isScanning) vm.scan()
    }
    Text("Телефон и включённый телевизор должны быть в одной сети Wi-Fi.",
        color = muted, fontSize = 15.sp)
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = vm::scan, enabled = !state.isScanning,
        modifier = Modifier.fillMaxWidth().height(55.dp),
        colors = ButtonDefaults.buttonColors(containerColor = red)
    ) {
        if (state.isScanning) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
        } else Icon(Icons.Default.WifiFind, null)
        Spacer(Modifier.width(9.dp))
        Text(if (state.isScanning) "Ищем телевизоры…" else "Найти телевизор", fontSize = 16.sp)
    }
    Spacer(Modifier.height(10.dp))
    if (state.wifiScanMessage.isNotBlank()) {
        Text(state.wifiScanMessage, color = muted, fontSize = 14.sp)
        Spacer(Modifier.height(9.dp))
    }
    if (state.isPairing) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Подтвердите разрешение на телевизоре, если появится запрос.",
            color = Color(0xFFFFC3AB), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
    }
    if (state.savedWifiDevices.isNotEmpty()) {
        Text("Сохранённые", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(7.dp))
        state.savedWifiDevices.forEach { device ->
            EasyDeviceRow(device, state, vm)
        }
    }
    if (state.devices.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("Найденные в сети", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(7.dp))
        state.devices.filterNot { found ->
            state.savedWifiDevices.any { saved ->
                saved.driverId == found.driverId && saved.address == found.address
            }
        }.forEach { device ->
            EasyDeviceRow(device, state, vm)
        }
    }
    Spacer(Modifier.height(17.dp))
    Text("Не нашли телевизор? Подключить по IP", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Spacer(Modifier.height(6.dp))
    Text("IP телевизора обычно указан в его настройках сети. " +
        "Это работает и когда роутер блокирует автоматическое обнаружение.",
        color = muted, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.manualIp, onValueChange = vm::setManualIp,
        modifier = Modifier.fillMaxWidth(), label = { Text("IP телевизора") },
        placeholder = { Text("192.168.1.100") }, singleLine = true
    )
    Spacer(Modifier.height(9.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("LG", "Samsung", "Roku").forEach { brand ->
            FilterChip(
                selected = manualBrand == brand,
                onClick = { manualBrand = brand },
                label = { Text(brand) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF813246)
                )
            )
        }
    }
    Button(
        onClick = {
            state.presets.firstOrNull { it.brand == manualBrand }?.let(vm::addManual)
        },
        enabled = state.manualIp.count { it == '.' } == 3 && !state.isPairing,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Подключить ${manualBrand} по IP")
    }
    Spacer(Modifier.height(8.dp))
    Text("Если устройство не находится: выключите VPN, убедитесь, что это не гостевой Wi-Fi, " +
        "а на телевизоре разрешено управление со смартфона.",
        color = muted, fontSize = 12.sp)
}

@Composable
private fun EasyDeviceRow(device: Device, state: MainUiState, vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp)
            .background(tile, RoundedCornerShape(15.dp))
            .clickable(enabled = !state.isPairing) { vm.selectDevice(device) }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Tv, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${device.brand ?: "TV"} · ${device.address ?: ""} · Подключить",
                fontSize = 12.sp, color = muted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = muted)
    }
}
