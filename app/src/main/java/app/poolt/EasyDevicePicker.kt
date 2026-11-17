package app.poolt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val red = Color(0xFFE6263E)
private val muted = Color(0xFFB8B6C0)
private val tile = Color(0xFF24232B)
private val popularBrands = listOf("LG", "Samsung", "Sony", "Philips", "TCL", "Hisense", "Xiaomi")

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerContent(state: MainUiState, vm: MainViewModel) {
    var wifi by rememberSaveable { mutableStateOf(state.mode == ControlMode.WIFI || !state.irAvailable) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val height = LocalConfiguration.current.screenHeightDp.dp
    if (advanced) {
        Column(Modifier.fillMaxWidth().height(height * .88f)) {
            TextButton(onClick = { advanced = false }) {
                Icon(Icons.Default.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text("Назад к подключению", fontSize = 15.sp)
            }
            LegacyDevicePickerContent(state, vm)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().height(height * .87f)
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tv, null, Modifier.size(26.dp), tint = red)
            Spacer(Modifier.width(10.dp))
            Text("Добавить телевизор", fontSize = 24.sp,
                fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth()
                .background(Color(0xFF29262E), RoundedCornerShape(18.dp))
                .padding(4.dp)
        ) {
            listOf(true to "Wi-Fi", false to "ИК-пульт").forEach { (mode, label) ->
                Box(
                    Modifier.weight(1f)
                        .background(
                            if (wifi == mode) Brush.verticalGradient(
                                listOf(Color(0xFFBA253B), Color(0xFF81182A))
                            ) else Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            ), RoundedCornerShape(15.dp)
                        )
                        .clickable { wifi = mode; vm.clearIrTrial() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
        ) {
            if (wifi) EasyWifiPicker(state, vm) else EasyIrPicker(state, vm)
            Spacer(Modifier.height(30.dp))
            TextButton(
                onClick = { advanced = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Другая техника и расширенные настройки",
                    color = muted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun PickerPrimary(
    title: String,
    icon: ImageVector,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 65.dp)
            .shadow(
                elevation = if (enabled) 7.dp else 0.dp,
                shape = shape,
                ambientColor = Color(0x1ECA2037),
                spotColor = Color(0x2CDC304A)
            )
            .background(
                if (enabled) Brush.verticalGradient(
                    listOf(Color(0xFFF03D55), Color(0xFFC51631), Color(0xFF941127))
                ) else Brush.verticalGradient(
                    listOf(Color(0xFF38353C), Color(0xFF27242B))
                ), shape
            )
            .border(1.dp, if (enabled) Color(0xFFEA6B78)
                else Color(0xFF47414B), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) CircularProgressIndicator(
            Modifier.size(25.dp), strokeWidth = 2.dp, color = Color.White
        ) else Icon(icon, null, Modifier.size(26.dp), tint = Color.White)
        Spacer(Modifier.width(11.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else muted)
    }
}

@Composable
private fun EasyWifiPicker(state: MainUiState, vm: MainViewModel) {
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var manualBrand by rememberSaveable { mutableStateOf("LG") }

    LaunchedEffect(Unit) {
        if (!state.hasScannedWifi && !state.isScanning) vm.scan()
    }

    Text(
        "Найдём телевизор в вашей сети",
        fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Включите ТВ и подключите телефон к тому же Wi-Fi.",
        color = muted, fontSize = 15.sp, lineHeight = 21.sp
    )
    Spacer(Modifier.height(20.dp))
    PickerPrimary(
        title = if (state.isScanning) "Поиск телевизоров…" else "Найти телевизор",
        icon = Icons.Default.Wifi,
        enabled = !state.isScanning && !state.isPairing,
        loading = state.isScanning
    ) { vm.scan() }
    Spacer(Modifier.height(17.dp))

    if (state.savedWifiDevices.isNotEmpty()) {
        Text("Ваши телевизоры", fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        state.savedWifiDevices.forEach { device ->
            EasyDeviceRow(device, state, vm)
        }
        Spacer(Modifier.height(12.dp))
    }

    val newDevices = state.devices.filterNot { found ->
        state.savedWifiDevices.any {
            it.address == found.address && it.driverId == found.driverId
        }
    }
    if (newDevices.isNotEmpty()) {
        Text("Найдено рядом", fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        newDevices.forEach { device ->
            EasyDeviceRow(device, state, vm)
        }
    }

    if (state.isPairing) {
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = red)
        Spacer(Modifier.height(10.dp))
        Text("Подтвердите подключение на экране телевизора.",
            fontSize = 15.sp, color = Color.White)
    } else if (!state.isScanning && state.hasScannedWifi &&
        state.devices.isEmpty() && state.savedWifiDevices.isEmpty()) {
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth()
                .background(tile, RoundedCornerShape(16.dp))
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.InfoOutline, null, tint = muted)
            Spacer(Modifier.width(12.dp))
            Text("Телевизор пока не найден.",
                fontSize = 15.sp, color = muted)
        }
    }

    if (!state.isPairing && state.wifiScanMessage.startsWith("Не удалось подключиться")) {
        Spacer(Modifier.height(9.dp))
        Text("ТВ не ответил. Проверьте его запрос на подключение.",
            fontSize = 15.sp, color = Color(0xFFFFB8A7))
    }

    Spacer(Modifier.height(24.dp))
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { manualOpen = !manualOpen }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.HelpOutline, null, tint = red)
        Spacer(Modifier.width(10.dp))
        Text("Телевизор не находится?",
            fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Icon(
            if (manualOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            null, tint = muted
        )
    }
    if (manualOpen) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Проверьте Wi-Fi и разрешите управление с телефона в настройках ТВ.",
            fontSize = 15.sp, lineHeight = 21.sp, color = muted
        )
        Spacer(Modifier.height(15.dp))
        OutlinedTextField(
            value = state.manualIp,
            onValueChange = vm::setManualIp,
            label = { Text("IP-адрес телевизора") },
            placeholder = { Text("192.168.1.100") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("LG", "Samsung", "Roku").forEach { name ->
                FilterChip(
                    selected = manualBrand == name,
                    onClick = { manualBrand = name },
                    label = { Text(name, fontSize = 15.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF913348)
                    )
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        PickerPrimary(
            title = "Подключить по IP",
            icon = Icons.Default.Link,
            enabled = state.manualIp.count { it == '.' } == 3 && !state.isPairing
        ) { state.presets.firstOrNull { it.brand == manualBrand }?.let(vm::addManual) }
    }
}

@Composable
private fun EasyDeviceRow(device: Device, state: MainUiState, vm: MainViewModel) {
    val selected = state.selected?.address == device.address &&
        state.selected?.driverId == device.driverId
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF33303A), Color(0xFF1D1B22))),
                RoundedCornerShape(17.dp)
            )
            .border(
                1.dp, if (selected) Color(0xFF96455A)
                else Color(0xFF55505D), RoundedCornerShape(17.dp)
            )
            .clickable(enabled = !state.isPairing) { vm.selectDevice(device) }
            .padding(horizontal = 15.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Tv, null, Modifier.size(26.dp), tint = Color.White)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (selected && state.selected?.isOnline == true) "Подключено"
                else if (selected && state.isPairing) "Подключаем…"
                else "Нажмите для подключения",
                fontSize = 13.sp, color = muted
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = muted)
    }
}

@Composable
private fun EasyIrPicker(state: MainUiState, vm: MainViewModel) {
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableIntStateOf(0) }
    var started by rememberSaveable { mutableStateOf(false) }
    var confirmed by rememberSaveable { mutableStateOf(false) }

    val options = remember(state.irCatalog, state.irProfiles, brand, model) {
        irCandidates(state, brand, model)
    }
    val candidate = options.getOrNull(current)
    val matched = candidate != null && state.irTrialProfile?.id ==
        (candidate.profile?.id ?: "irdb:" + candidate.entry?.path)

    fun runTest(option: TrialCandidate?) {
        if (option == null) return
        started = true
        confirmed = false
        if (option.profile != null) {
            vm.testIrProfile(option.profile)
            vm.sendIrTestCommand(RemoteCommand.POWER)
        } else if (option.entry != null) {
            vm.testIrEntry(option.entry, sendPowerAfterLoad = true)
        }
    }
    fun next() {
        if (options.isEmpty()) return
        current = (current + 1) % options.size
        vm.clearIrTrial()
        runTest(options.getOrNull(current))
    }
    fun reset() {
        started = false
        confirmed = false
        current = 0
        vm.clearIrTrial()
    }

    if (!state.irAvailable) {
        Text("На этом телефоне ИК-передатчик недоступен. Используйте Wi-Fi.",
            fontSize = 16.sp, color = Color(0xFFFFC0AD))
        return
    }

    if (!started) {
        Text("Найдём подходящий пульт", fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = brand,
            onValueChange = { brand = it; reset() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Марка телевизора") },
            placeholder = { Text("Например, LG") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            popularBrands.forEach { name ->
                FilterChip(
                    selected = canonicalBrand(brand).equals(name, true),
                    onClick = { brand = name; reset() },
                    label = { Text(name, fontSize = 15.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF843044)
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; reset() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Модель (необязательно)") },
            placeholder = { Text("OLED55C3, UE55AU7100…") },
            singleLine = true
        )
        Spacer(Modifier.height(22.dp))
        if (brand.isNotBlank() && options.isEmpty()) {
            Text("Кодов для этой марки пока нет.",
                fontSize = 15.sp, color = muted)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = vm::loadWorldwideIrCatalog) {
                Text("Обновить каталог", fontSize = 15.sp)
            }
        } else PickerPrimary(
            title = "Проверить пульт",
            icon = Icons.Default.PowerSettingsNew,
            enabled = candidate != null
        ) { runTest(candidate) }
        if (brand.isBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("Сначала выберите марку.", fontSize = 15.sp, color = muted)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    canonicalBrand(brand) + if (model.isNotBlank()) " · $model" else "",
                    fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text("Вариант ${current + 1} из ${options.size}", fontSize = 15.sp, color = muted)
            }
            TextButton(onClick = { reset() }) { Text("Изменить", color = muted) }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            if (confirmed) "Телевизор реагирует!"
            else "Включился телевизор?",
            fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (confirmed) "Сохраните этот пульт или проверьте другие кнопки."
            else "Наведите телефон на ТВ и проверьте питание.",
            fontSize = 16.sp, color = muted, lineHeight = 22.sp
        )
        Spacer(Modifier.height(19.dp))
        PickerPrimary(
            title = if (state.isTestingIr) "Подготавливаем сигнал…" else "Повторить Power",
            icon = Icons.Default.PowerSettingsNew,
            enabled = !state.isTestingIr && candidate != null,
            loading = state.isTestingIr
        ) { runTest(candidate) }

        val error = state.irTestMessage
        if (!state.isTestingIr && error.isNotBlank() &&
            !error.startsWith("Команда отправлена") &&
            !error.startsWith("Наведите телефон")) {
            Spacer(Modifier.height(13.dp))
            Text(error, fontSize = 15.sp, color = Color(0xFFFFB9B6))
        }

        if (matched && state.irPowerTestSent && !state.isTestingIr) {
            Spacer(Modifier.height(19.dp))
            if (!confirmed) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { confirmed = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25664C))
                    ) { Text("Да, работает", fontSize = 15.sp) }
                    OutlinedButton(
                        onClick = { next() },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("Нет, дальше", fontSize = 15.sp) }
                }
            } else {
                PickerPrimary(
                    title = "Сохранить пульт",
                    icon = Icons.Default.Check
                ) { vm.confirmIrTrial() }
                Spacer(Modifier.height(15.dp))
                Text("Проверить другие кнопки", fontSize = 16.sp, color = muted)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RemoteCommand.OK to "OK",
                        RemoteCommand.VOLUME_UP to "VOL+",
                        RemoteCommand.BACK to "Назад"
                    ).forEach { (cmd, label) ->
                        OutlinedButton(
                            onClick = { vm.sendIrTestCommand(cmd) },
                            enabled = state.irTrialProfile?.commands?.containsKey(cmd) == true,
                            modifier = Modifier.weight(1f)
                        ) { Text(label, fontSize = 14.sp) }
                    }
                }
            }
        }
        if (!confirmed) {
            Spacer(Modifier.height(15.dp))
            TextButton(onClick = { next() }, modifier = Modifier.fillMaxWidth()) {
                Text("Попробовать следующий вариант", fontSize = 15.sp, color = muted)
            }
        }
    }
}
