package app.poolt

import android.os.Bundle
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.animation.ValueAnimator
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
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
private val Accent = Color(0xFFE6263E)

class MainActivity : ComponentActivity() {
    private var splashFinished = false
    private var splashTimerStarted = false
    private var wordFillView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(AndroidColor.BLACK) }

        val artwork = BitmapFactory.decodeResource(resources, R.drawable.poolt_splash)
        val splash = ImageView(this).apply {
            setImageBitmap(artwork)
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

        // Recolour only the bright pixels of the original Poolt lettering.
        // No second font, no added words and no JPEG approximation.
        val wordFill = ImageView(this).apply {
            setImageBitmap(makeWordFill(artwork))
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipBounds = Rect(0, 0, 0, 0)
        }
        root.addView(
            wordFill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        wordFillView = wordFill
        setContentView(root)
    }

    private fun makeWordFill(artwork: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(artwork.width, artwork.height, Bitmap.Config.ARGB_8888)
        val x0 = (artwork.width * 300f / 864f).toInt()
        val y0 = (artwork.height * 1250f / 1536f).toInt()
        val right = (artwork.width * 580f / 864f).toInt().coerceAtMost(artwork.width)
        val bottom = (artwork.height * 1350f / 1536f).toInt().coerceAtMost(artwork.height)
        val w = right - x0
        val h = bottom - y0
        if (w <= 0 || h <= 0) return result
        val original = IntArray(w * h)
        val coloured = IntArray(w * h)
        artwork.getPixels(original, 0, w, x0, y0, w, h)
        for (i in original.indices) {
            val c = original[i]
            val brightness = minOf(AColor.red(c), AColor.green(c), AColor.blue(c))
            val alpha = ((brightness - 30).coerceIn(0, 160) * 255 / 160)
            coloured[i] = AColor.argb(alpha, 232, 64, 75)
        }
        result.setPixels(coloured, 0, w, x0, y0, w, h)
        return result
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || splashTimerStarted || splashFinished) return

        splashTimerStarted = true
        // Clip the whole correctly-aligned overlay, not guessed window coordinates.
        wordFillView?.doOnLayout { fill ->
            if (isFinishing || isDestroyed || splashFinished) return@doOnLayout
            val width = fill.width
            val height = fill.height
            if (width <= 0 || height <= 0) return@doOnLayout
            fill.clipBounds = Rect(0, 0, 0, height)
            ValueAnimator.ofInt(0, width).apply {
                duration = 1450L
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animation ->
                    fill.clipBounds = Rect(0, 0, animation.animatedValue as Int, height)
                }
                start()
            }
        }
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
                        onPrimary = Color.White,
                        secondary = Accent,
                        onSecondary = Color.White,
                        primaryContainer = Panel2,
                        onPrimaryContainer = Color.White,
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
    val view = LocalView.current
    val send: (RemoteCommand) -> Unit = { command ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        vm.send(command)
    }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val ready = when (state.mode) {
                                ControlMode.IR -> state.irAvailable && state.selectedIrProfile != null && state.irVerified
                                ControlMode.WIFI -> state.selected?.isOnline == true
                            }
                            Text(
                                text = when (state.mode) {
                                    ControlMode.IR -> state.selectedIrProfile?.let {
                                        it.brand + " · ИК · " +
                                            if (state.irVerified) "кнопки проверены" else "совместимость не проверена"
                                    } ?: "Выберите устройство"
                                    ControlMode.WIFI -> {
                                        val device = state.selected
                                        when {
                                            device == null -> "Выберите устройство"
                                            device.driverId != "lg.webos" -> device.name + " · Wi-Fi"
                                            !device.isOnline -> "LG webOS · не подключён"
                                            state.pointerConfirmed -> "LG webOS · курсор проверен"
                                            state.pointerChecking -> "LG webOS · проверяем курсор"
                                            state.pointerReady -> "LG webOS · курсор не проверен"
                                            else -> "LG webOS · курсор недоступен"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                color = Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(7.dp))
                            Box(
                                Modifier.size(7.dp).clip(CircleShape).background(
                                    if (ready) Color(0xFF35C873) else Color(0xFF606069)
                                )
                            )
                        }
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
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SquareButton(Icons.Default.PowerSettingsNew, "Power", accent = true) { send(RemoteCommand.POWER) }
                SquareButton(Icons.Default.Input, "Source") { send(RemoteCommand.SOURCE) }
                SquareButton(Icons.Default.Settings, "Settings") { send(RemoteCommand.SETTINGS) }
                SquareButton(Icons.Default.Home, "Home") { send(RemoteCommand.HOME) }
            }

            Spacer(Modifier.height(22.dp))
            DPad(send)
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
                    onTop = { send(RemoteCommand.VOLUME_UP) },
                    onBottom = { send(RemoteCommand.VOLUME_DOWN) }
                )
                Column(
                    Modifier.weight(1.25f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WideButton(Icons.Default.VolumeOff, "Mute") { send(RemoteCommand.MUTE) }
                    WideButton(Icons.Default.Menu, "Menu") { send(RemoteCommand.MENU) }
                    WideButton(Icons.Default.ArrowBack, "Back") { send(RemoteCommand.BACK) }
                }
                TallRocker(
                    Modifier.weight(1f),
                    top = Icons.Default.KeyboardArrowUp,
                    center = "CH",
                    bottom = Icons.Default.KeyboardArrowDown,
                    onTop = { send(RemoteCommand.CHANNEL_UP) },
                    onBottom = { send(RemoteCommand.CHANNEL_DOWN) }
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WideButton(Icons.Default.FastRewind, "Rew", Modifier.weight(1f)) { send(RemoteCommand.REWIND) }
                WideButton(Icons.Default.PlayArrow, "Play", Modifier.weight(1f)) { send(RemoteCommand.PLAY_PAUSE) }
                WideButton(Icons.Default.FastForward, "Fwd", Modifier.weight(1f)) { send(RemoteCommand.FAST_FORWARD) }
            }

            Spacer(Modifier.height(18.dp))
            NumberPad(send)
            if (state.mode == ControlMode.WIFI && state.selected?.driverId == "lg.webos") {
                Spacer(Modifier.height(22.dp))
                LgTouchpad(state, vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showDevicePicker) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = vm::closePicker,
            containerColor = Panel,
            contentColor = Color.White
        ) {
            DevicePickerContent(state, vm)
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
private fun SquareButton(icon: ImageVector, label: String, accent: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(58.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Panel,
                contentColor = if (accent) Color(0xFFF22E47) else Color.White
            )
        ) { Icon(icon, label, tint = if (accent) Color(0xFFF22E47) else Color.White) }
        Spacer(Modifier.height(5.dp))
        Text(label, color = Muted, fontSize = 12.sp)
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
            modifier = Modifier.align(Alignment.Center).size(92.dp)
                .border(1.dp, Color(0xFFE6263E), CircleShape),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Color.White)
        ) { Text("OK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
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
