package app.poolt

import android.os.Bundle
import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.poolt.core.Device
import app.poolt.core.RemoteCommand

private val Bg = Color(0xFF0A0A0C)
private val Panel = Color(0xFF17171B)
private val Panel2 = Color(0xFF202026)
private val Muted = Color(0xFF9A9AA1)
private val Accent = Color(0xFF9E2027)

class MainActivity : ComponentActivity() {
    private var splashFinished = false
    private var splashTimerStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(AndroidColor.BLACK) }

        val splashBytes = buildString {
            for (i in 1..11) {
                val name = "splash_parts/" + i.toString().padStart(2, '0') + ".txt"
                append(assets.open(name).bufferedReader().use { it.readText() }.trim())
            }
        }.let { Base64.decode(it, Base64.DEFAULT) }

        val splashBitmap = BitmapFactory.decodeByteArray(splashBytes, 0, splashBytes.size)
            ?: error("Poolt splash decode failed")

        val splash = ImageView(this).apply {
            setImageBitmap(splashBitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(
            splash,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        setContentView(root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || splashTimerStarted || splashFinished) return

        splashTimerStarted = true
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed || splashFinished) return@postDelayed
            splashFinished = true
            enableEdgeToEdge()
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = Bg,
                        surface = Bg,
                        primary = Accent,
                        onBackground = Color.White,
                        onSurface = Color.White
                    )
                ) { PooltApp() }
            }
        }, 1600L)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PooltApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { vm.openPicker() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Poolt", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Muted)
                        }
                        Text(
                            when (state.mode) {
                                ControlMode.IR -> state.selectedIrProfile?.let { it.brand + " · " + it.model } ?: "Выбрать ИК-профиль"
                                ControlMode.WIFI -> state.selected?.name ?: "Wi‑Fi устройство"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.openPicker() }) {
                        Icon(Icons.Default.Add, "Добавить", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ModeSwitcher(state.mode, vm::setMode)
            Spacer(Modifier.height(12.dp))
            StatusCard(state, onClick = vm::openPicker, onReconnect = vm::pairSelected)

            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SquareButton(Icons.Default.PowerSettingsNew, "Power") { vm.send(RemoteCommand.POWER) }
                SquareButton(Icons.Default.Input, "Source") { vm.send(RemoteCommand.SOURCE) }
                SquareButton(Icons.Default.Settings, "Settings") { vm.send(RemoteCommand.SETTINGS) }
                SquareButton(Icons.Default.Home, "Home") { vm.send(RemoteCommand.HOME) }
            }

            Spacer(Modifier.height(22.dp))
            DPad(vm::send)
            Spacer(Modifier.height(22.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TallRocker(
                    Modifier.weight(1f),
                    top = Icons.Default.Add,
                    center = "VOL",
                    bottom = Icons.Default.Remove,
                    onTop = { vm.send(RemoteCommand.VOLUME_UP) },
                    onBottom = { vm.send(RemoteCommand.VOLUME_DOWN) }
                )
                Column(
                    Modifier.weight(1.25f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WideButton(Icons.Default.VolumeOff, "Mute") { vm.send(RemoteCommand.MUTE) }
                    WideButton(Icons.Default.Menu, "Menu") { vm.send(RemoteCommand.MENU) }
                    WideButton(Icons.Default.ArrowBack, "Back") { vm.send(RemoteCommand.BACK) }
                }
                TallRocker(
                    Modifier.weight(1f),
                    top = Icons.Default.KeyboardArrowUp,
                    center = "CH",
                    bottom = Icons.Default.KeyboardArrowDown,
                    onTop = { vm.send(RemoteCommand.CHANNEL_UP) },
                    onBottom = { vm.send(RemoteCommand.CHANNEL_DOWN) }
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WideButton(Icons.Default.FastRewind, "Rew", Modifier.weight(1f)) { vm.send(RemoteCommand.REWIND) }
                WideButton(Icons.Default.PlayArrow, "Play", Modifier.weight(1f)) { vm.send(RemoteCommand.PLAY_PAUSE) }
                WideButton(Icons.Default.FastForward, "Fwd", Modifier.weight(1f)) { vm.send(RemoteCommand.FAST_FORWARD) }
            }

            Spacer(Modifier.height(18.dp))
            NumberPad(vm::send)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showDevicePicker) {
        ModalBottomSheet(
            onDismissRequest = vm::closePicker,
            containerColor = Panel,
            contentColor = Color.White
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {
                Text("Универсальный ИК-пульт", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.irAvailable) "ИК-передатчик телефона доступен" else "На этом телефоне Android не видит ИК-передатчик",
                    Modifier.padding(horizontal = 20.dp),
                    color = if (state.irAvailable) Color(0xFF30D158) else Color(0xFFFF9F0A)
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.irQuery,
                    onValueChange = vm::setIrQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    label = { Text("Бренд, тип или модель") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (state.irCatalog.isEmpty()) "Загрузка мирового каталога…" else
                            "IRDB: " + state.irCatalog.size + " код-сетов · " +
                                state.irCatalog.map { it.brand }.distinct().size + " брендов",
                        color = Muted,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.isLoadingIrCatalog) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = vm::loadWorldwideIrCatalog) { Icon(Icons.Default.Refresh, "Обновить каталог") }
                    }
                }

                val shownIrEntries = remember(state.irCatalog, state.irQuery) {
                    val q = state.irQuery.trim()
                    state.irCatalog
                        .asSequence()
                        .filter {
                            q.isBlank() ||
                                it.brand.contains(q, true) ||
                                it.deviceType.contains(q, true) ||
                                it.codeSet.contains(q, true)
                        }
                        .take(if (q.isBlank()) 40 else 100)
                        .toList()
                }

                shownIrEntries.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectWorldwideIrEntry(entry) }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Panel2),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.SettingsRemote, null, tint = Color.White) }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(entry.brand, fontWeight = FontWeight.SemiBold)
                            Text(entry.deviceType + " · " + entry.codeSet, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }

                if (state.irCatalog.isEmpty() && !state.isLoadingIrCatalog) {
                    Text("Встроенные профили", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Muted)
                    state.irProfiles.forEach { profile ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.selectIrProfile(profile) }.padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(profile.brand + " · " + profile.model, Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Panel2)
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Wi‑Fi пульты", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Дополнительный режим для Smart TV", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = vm::scan) {
                        if (state.isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Поиск")
                    }
                }

                if (state.devices.isNotEmpty()) {
                    state.devices.forEach { found -> DeviceRow(found) { vm.selectDevice(found) } }
                } else {
                    Text("Автоматически ничего не найдено", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = Muted)
                }

                OutlinedTextField(
                    value = state.manualIp,
                    onValueChange = vm::setManualIp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    label = { Text("IP Smart TV") },
                    singleLine = true
                )

                state.presets.forEach { preset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.addManual(preset) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, null, tint = Muted)
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(preset.brand, fontWeight = FontWeight.SemiBold)
                            Text(preset.model, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSwitcher(mode: ControlMode, onMode: (ControlMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(4.dp)
    ) {
        listOf(ControlMode.IR to "ИК", ControlMode.WIFI to "Wi‑Fi").forEach { item ->
            val selected = mode == item.first
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Panel2 else Color.Transparent)
                    .clickable { onMode(item.first) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(item.second, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color.White else Muted)
            }
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState, onClick: () -> Unit, onReconnect: () -> Unit) {
    val title = when (state.mode) {
        ControlMode.IR -> state.selectedIrProfile?.brand ?: "ИК-пульт"
        ControlMode.WIFI -> state.selected?.brand ?: "Wi‑Fi устройство"
    }
    val subtitle = when (state.mode) {
        ControlMode.IR -> (state.selectedIrProfile?.model ?: "Профиль не выбран") + " · " + state.connectionMessage
        ControlMode.WIFI -> (state.selected?.model ?: state.selected?.address ?: "Не выбрано") + " · " + state.connectionMessage
    }
    val online = when (state.mode) {
        ControlMode.IR -> state.irAvailable && state.selectedIrProfile != null
        ControlMode.WIFI -> state.selected?.isOnline == true
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Panel2), contentAlignment = Alignment.Center) {
            if (state.isPairing && state.mode == ControlMode.WIFI) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(if (state.mode == ControlMode.IR) Icons.Default.SettingsRemote else Icons.Default.Tv, null, tint = Color.White)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        if (state.mode == ControlMode.WIFI && state.selected != null && !online && !state.isPairing) {
            IconButton(onClick = onReconnect) { Icon(Icons.Default.Link, "Подключить", tint = Color.White) }
        } else {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (online) Color(0xFF30D158) else Color(0xFF5A5A60)))
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Tv, null, tint = Color.White)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(device.name, fontWeight = FontWeight.SemiBold)
            Text(device.driverId, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun SquareButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(58.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Panel, contentColor = Color.White)
        ) { Icon(icon, label) }
        Spacer(Modifier.height(5.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun DPad(onCommand: (RemoteCommand) -> Unit) {
    Box(Modifier.size(248.dp).clip(CircleShape).background(Panel)) {
        IconButton({ onCommand(RemoteCommand.UP) }, Modifier.align(Alignment.TopCenter).size(76.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(40.dp), tint = Color.White)
        }
        IconButton({ onCommand(RemoteCommand.DOWN) }, Modifier.align(Alignment.BottomCenter).size(76.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(40.dp), tint = Color.White)
        }
        IconButton({ onCommand(RemoteCommand.LEFT) }, Modifier.align(Alignment.CenterStart).size(76.dp)) {
            Icon(Icons.Default.KeyboardArrowLeft, "Left", Modifier.size(40.dp), tint = Color.White)
        }
        IconButton({ onCommand(RemoteCommand.RIGHT) }, Modifier.align(Alignment.CenterEnd).size(76.dp)) {
            Icon(Icons.Default.KeyboardArrowRight, "Right", Modifier.size(40.dp), tint = Color.White)
        }
        Button(
            onClick = { onCommand(RemoteCommand.OK) },
            modifier = Modifier.align(Alignment.Center).size(92.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Panel2)
        ) { Text("OK", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}

@Composable
private fun TallRocker(modifier: Modifier, top: ImageVector, center: String, bottom: ImageVector, onTop: () -> Unit, onBottom: () -> Unit) {
    Column(
        modifier.height(174.dp).clip(RoundedCornerShape(26.dp)).background(Panel),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onTop, modifier = Modifier.fillMaxWidth().height(58.dp)) { Icon(top, null, tint = Color.White) }
        Text(center, color = Muted, fontWeight = FontWeight.Bold)
        IconButton(onClick = onBottom, modifier = Modifier.fillMaxWidth().height(58.dp)) { Icon(bottom, null, tint = Color.White) }
    }
}

@Composable
private fun WideButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White)
    ) {
        Icon(icon, null, Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun NumberPad(onCommand: (RemoteCommand) -> Unit) {
    val rows = listOf(
        listOf("1" to RemoteCommand.NUMBER_1, "2" to RemoteCommand.NUMBER_2, "3" to RemoteCommand.NUMBER_3),
        listOf("4" to RemoteCommand.NUMBER_4, "5" to RemoteCommand.NUMBER_5, "6" to RemoteCommand.NUMBER_6),
        listOf("7" to RemoteCommand.NUMBER_7, "8" to RemoteCommand.NUMBER_8, "9" to RemoteCommand.NUMBER_9),
        listOf("Guide" to RemoteCommand.GUIDE, "0" to RemoteCommand.NUMBER_0, "Info" to RemoteCommand.INFO)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { pair ->
                    Button(
                        onClick = { onCommand(pair.second) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Color.White)
                    ) { Text(pair.first, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
