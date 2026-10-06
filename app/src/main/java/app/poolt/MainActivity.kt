package app.poolt

import android.os.Bundle
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.widget.TextView
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
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
private val Accent = Color(0xFF3A82F6)

class MainActivity : ComponentActivity() {
    companion object {
        private const val SPLASH_BASE64 = "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[... ELLIPSIZATION ...]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"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private var splashFinished = false
    private var splashTimerStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(AndroidColor.BLACK) }

        val splashBytes = android.util.Base64.decode(SPLASH_BASE64, android.util.Base64.DEFAULT)
        val splashBitmap = android.graphics.BitmapFactory.decodeByteArray(splashBytes, 0, splashBytes.size)
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

        val loader = FrameLayout(this)
        val loaderParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(46),
            Gravity.BOTTOM
        ).apply {
            leftMargin = dp(56)
            rightMargin = dp(56)
            bottomMargin = dp(28)
        }

        val baseText = TextView(this).apply {
            text = "ПУЛЬТ"
            setTextColor(AndroidColor.rgb(72, 18, 20))
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        val fillText = TextView(this).apply {
            text = "ПУЛЬТ"
            setTextColor(AndroidColor.rgb(158, 32, 39))
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        loader.addView(baseText, FrameLayout.LayoutParams(-1, -1))
        loader.addView(fillText, FrameLayout.LayoutParams(-1, -1))
        root.addView(loader, loaderParams)

        loader.post {
            fillText.clipBounds = Rect(0, 0, 0, loader.height)
            ValueAnimator.ofInt(0, loader.width).apply {
                duration = 1600L
                addUpdateListener {
                    val w = it.animatedValue as Int
                    fillText.clipBounds = Rect(0, 0, w, loader.height)
                }
                start()
            }
        }

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
                Text("ИК-пульт", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.irAvailable) "ИК-передатчик телефона доступен" else "На этом телефоне Android не видит ИК-передатчик",
                    Modifier.padding(horizontal = 20.dp),
                    color = if (state.irAvailable) Color(0xFF30D158) else Color(0xFFFF9F0A)
                )
                Spacer(Modifier.height(8.dp))

                state.irProfiles.forEach { profile ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectIrProfile(profile) }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Panel2),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.SettingsRemote, null, tint = Color.White) }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(profile.brand, fontWeight = FontWeight.SemiBold)
                            Text(profile.model, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.selectedIrProfile?.id == profile.id && state.mode == ControlMode.IR) {
                            Icon(Icons.Default.Check, null, tint = Color(0xFF30D158))
                        } else {
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
