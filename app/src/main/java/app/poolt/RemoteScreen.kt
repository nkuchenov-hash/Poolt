package app.poolt

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.poolt.core.RemoteCommand

private val ScreenBg = Color(0xFF08090D)
private val SurfaceTop = Color(0xFF29292F)
private val SurfaceBottom = Color(0xFF141418)
private val KeySurface = Color(0xFF1D1E23)
private val SoftText = Color(0xFFA9ABB4)
private val Red = Color(0xFFEF2945)
private val White = Color(0xFFF6F6F8)
private val Green = Color(0xFF35C873)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = remember(context) { HapticEngine(context) }
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.keyboardSession) {
        if (state.keyboardSession > 0) keyboardOpen = true
    }
    var showAppSettings by rememberSaveable { mutableStateOf(false) }
    var useTouchpad by remember(state.selected?.id) { mutableStateOf(false) }
    val isLgWifi = state.mode == ControlMode.WIFI &&
        state.selected?.driverId == "lg.webos"
    val pointerAvailable = isLgWifi && state.selected?.isOnline == true && state.pointerReady
    val feedback: () -> Unit = { haptics.tap(view, state.hapticEnabled) }
    val send: (RemoteCommand) -> Unit = { command ->
        feedback()
        vm.send(command)
    }
    Scaffold(
        containerColor = ScreenBg,
        topBar = {
            Column(
                Modifier.fillMaxWidth().background(ScreenBg).statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { feedback(); showAppSettings = true }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.Settings, "Настройки приложения", tint = SoftText)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(17.dp))
                            .background(Color(0xFF1C2028))
                            .border(1.dp, Color(0xFF363C48), RoundedCornerShape(17.dp))
                            .clickable { feedback(); vm.openPicker() }
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tv, null, Modifier.size(21.dp), tint = SoftText)
                        Spacer(Modifier.width(10.dp))
                        val ready = when (state.mode) {
                            ControlMode.IR -> state.irAvailable && state.irVerified && state.selectedIrProfile != null
                            ControlMode.WIFI -> state.selected?.isOnline == true
                        }
                        Column(Modifier.weight(1f)) {
                            val selectedName = when (state.mode) {
                                ControlMode.IR -> state.selectedIrProfile?.let { "${it.brand} · ИК" }
                                ControlMode.WIFI -> state.selected?.let { device ->
                                    device.brand?.let { brand ->
                                        if (device.name.startsWith(brand, true)) device.name else "$brand · ${device.name}"
                                    } ?: device.name
                                }
                            } ?: "Выбрать телевизор"
                            Text(selectedName, color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when {
                                    ready -> if (state.mode == ControlMode.WIFI) "Подключено по Wi-Fi" else "ИК-пульт проверен"
                                    state.isPairing -> "Подключаем…"
                                    state.selected != null && state.mode == ControlMode.WIFI -> "Не подключён · нажми для выбора"
                                    state.selectedIrProfile != null && state.mode == ControlMode.IR -> "ИК-пульт не проверен"
                                    else -> "Нажми, чтобы выбрать устройство"
                                },
                                color = if (ready) Green else SoftText,
                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.ExpandMore, null, Modifier.size(23.dp), tint = SoftText)
                    }
                    Box(
                        Modifier.size(51.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1C2028))
                            .border(1.dp, Color(0xFF363C48), RoundedCornerShape(16.dp))
                            .clickable { feedback(); vm.openPicker() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "Добавить устройство", Modifier.size(25.dp), tint = White)
                    }
                }
                if (state.savedWifiDevices.size + state.savedIrProfileIds.size > 1) {
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        state.savedWifiDevices.forEach { device ->
                            val active = state.mode == ControlMode.WIFI &&
                                state.selected?.driverId == device.driverId &&
                                state.selected?.address == device.address
                            Text(
                                device.brand ?: device.name,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (active) Color(0xFF40343F) else Color(0xFF1B1D24))
                                    .clickable { feedback(); vm.selectDevice(device) }
                                    .padding(horizontal = 13.dp, vertical = 8.dp),
                                color = if (active) White else SoftText, fontSize = 12.sp
                            )
                        }
                        state.irProfiles.filter { it.id in state.savedIrProfileIds }.forEach { profile ->
                            Text(
                                "${profile.brand} · ИК",
                                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1B1D24))
                                    .clickable { feedback(); vm.selectSavedIrProfile(profile) }
                                    .padding(horizontal = 13.dp, vertical = 8.dp),
                                color = SoftText, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(scaffoldPadding).navigationBarsPadding()
        ) {
          val minimumRemoteHeight = (maxHeight - 75.dp).coerceAtLeast(435.dp)
          Column(
              Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                  .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
          ) {
            // Primary controls receive most visual space. Secondary actions
            // are intentionally separate from the navigation ring.
            Column(
                Modifier.fillMaxWidth().heightIn(min = minimumRemoteHeight)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteRoundKey(Icons.Default.PowerSettingsNew, "Питание", true, 56.dp) {
                        send(RemoteCommand.POWER)
                    }
                    RemoteRoundKey(Icons.Default.Home, "Главная", false, 56.dp) {
                        send(RemoteCommand.HOME)
                    }
                    RemoteRoundKey(Icons.Default.Keyboard, "Клавиатура для ввода текста", false, 56.dp) {
                        feedback()
                        keyboardOpen = true
                    }
                    RemoteRoundKey(Icons.Default.Input, "Источник", false, 56.dp) {
                        send(RemoteCommand.SOURCE)
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val dialSize = maxWidth.coerceAtMost(258.dp)
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (useTouchpad) {
                            if (pointerAvailable) LgTouchpadSurface(state, vm, dialSize)
                            else SwipeNavigationPad(dialSize, send)
                        } else {
                            CompassPad(dialSize, send)
                        }

                        Spacer(Modifier.height(13.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1F222B))
                                .border(1.dp, Color(0xFF383D49), RoundedCornerShape(20.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(false to "Джойстик", true to "Тачпад")
                                .forEach { (touchpad, title) ->
                                    val active = useTouchpad == touchpad
                                    Row(
                                        Modifier.clip(RoundedCornerShape(16.dp))
                                            .background(if (active) Color(0xFF41424F) else Color.Transparent)
                                            .livePress {
                                                if (useTouchpad != touchpad) feedback()
                                                useTouchpad = touchpad
                                            }
                                            .padding(horizontal = 13.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (touchpad) Icons.Default.TouchApp else Icons.Default.Gamepad,
                                            title, Modifier.size(19.dp),
                                            tint = if (active) White else SoftText
                                        )
                                        Text(title, color = if (active) White else SoftText,
                                            fontSize = 13.sp, fontWeight = if (active)
                                                FontWeight.SemiBold else FontWeight.Normal)
                                    }
                                }
                        }
                        if (useTouchpad && pointerAvailable && !state.pointerConfirmed) {
                            TextButton(onClick = vm::confirmLgPointerWorks) {
                                Text("Курсор работает? Подтвердить", color = SoftText, fontSize = 12.sp)
                            }
                        }
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val rockerWidth = if (maxWidth < 325.dp) 68.dp else 78.dp
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeonRocker(
                            Modifier.width(rockerWidth),
                            "VOL", Icons.Default.Add, Icons.Default.Remove,
                            { send(RemoteCommand.VOLUME_UP) },
                            { send(RemoteCommand.VOLUME_DOWN) }
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactActionKey(
                                    Icons.Default.ArrowBack, "Назад", Modifier.weight(1f)
                                ) { send(RemoteCommand.BACK) }
                                CompactActionKey(
                                    Icons.Default.VolumeOff, "Звук", Modifier.weight(1f)
                                ) { send(RemoteCommand.MUTE) }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactActionKey(
                                    Icons.Default.Menu, "Меню", Modifier.weight(1f)
                                ) { send(RemoteCommand.MENU) }
                                CompactActionKey(
                                    Icons.Default.ListAlt, "Гид", Modifier.weight(1f)
                                ) { send(RemoteCommand.GUIDE) }
                            }
                        }
                        NeonRocker(
                            Modifier.width(rockerWidth),
                            "CH", Icons.Default.KeyboardArrowUp,
                            Icons.Default.KeyboardArrowDown,
                            { send(RemoteCommand.CHANNEL_UP) },
                            { send(RemoteCommand.CHANNEL_DOWN) }
                        )
                    }
                }

                MoreRemoteKeys(send)
            }
            if (state.mode == ControlMode.WIFI &&
                state.selected != null && state.selected?.isOnline != true
            ) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = vm::pairSelected) {
                    Icon(Icons.Default.Refresh, null, tint = SoftText)
                    Spacer(Modifier.width(6.dp))
                    Text("Повторить подключение", color = White, fontSize = 15.sp)
                }
            }
          }
        }
    }

    if (keyboardOpen) {
        TvKeyboardSheet(state = state, vm = vm, onDismiss = { keyboardOpen = false })
    }

    if (showAppSettings) {
        ModalBottomSheet(
            onDismissRequest = { showAppSettings = false },
            containerColor = Color(0xFF17171B),
            contentColor = White
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Text("Настройки приложения", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Виброотклик", color = White, fontSize = 16.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("Короткая вибрация при нажатии кнопок пульта", color = SoftText, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.hapticEnabled,
                        onCheckedChange = vm::setHapticEnabled
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (state.showDevicePicker) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = vm::closePicker,
            containerColor = Color(0xFF17171B),
            contentColor = White
        ) {
            DevicePickerContent(state, vm)
        }
    }
}

@Composable
private fun RemoteRoundKey(
    icon: ImageVector,
    contentDescription: String,
    isPower: Boolean,
    diameter: Dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = CircleShape
    val colors = if (isPower) {
        listOf(Color(0xFFF34457), Color(0xFFC61E38))
    } else if (pressed) {
        listOf(Color(0xFF32323A), Color(0xFF1D1D23))
    } else {
        listOf(Color(0xFF2B2C31), Color(0xFF17181C))
    }
    Box(
        Modifier.size(diameter)
            .shadow(if (isPower) 8.dp else 4.dp, shape, false,
                ambientColor = if (isPower) Red.copy(alpha = 0.26f) else Color.Black,
                spotColor = if (isPower) Red.copy(alpha = 0.3f) else Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(
                1.dp,
                if (isPower) Color(0xFFFF6270) else Color(0xFF3F4147),
                shape
            )
            .graphicsLayer {
                scaleX = if (pressed) 0.93f else 1f
                scaleY = if (pressed) 0.93f else 1f
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription, tint = White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun CompactActionKey(
    icon: ImageVector,
    title: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    Column(
        modifier.height(79.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF252A34), Color(0xFF191C25)))
            )
            .border(1.dp, Color(0xFF343944), shape)
            .livePress(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, title, Modifier.size(23.dp), tint = White)
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 11.sp, color = SoftText, maxLines = 1)
    }
}

@Composable
private fun NeonRocker(
    modifier: Modifier,
    title: String,
    topIcon: ImageVector,
    bottomIcon: ImageVector,
    onTop: () -> Unit,
    onBottom: () -> Unit
) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier.height(166.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF242934), Color(0xFF151922))))
            .border(1.dp, Color(0xFF3E4350), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).livePress(onTop),
            contentAlignment = Alignment.Center
        ) { Icon(topIcon, title + " up", Modifier.size(27.dp), tint = White) }
        Text(
            title, color = SoftText, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1
        )
        Box(
            Modifier.fillMaxWidth().height(72.dp).livePress(onBottom),
            contentAlignment = Alignment.Center
        ) { Icon(bottomIcon, title + " down", Modifier.size(27.dp), tint = White) }
    }
}

@Composable
private fun CompassPad(
    diameter: Dp,
    send: (RemoteCommand) -> Unit
) {
    Box(
        Modifier.size(diameter)
            .shadow(8.dp, CircleShape, clip = false,
                ambientColor = Color(0xFF4A1625), spotColor = Color(0xFF451321))
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF2B2C32), Color(0xFF19191E), Color(0xFF111216))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF853644),
                        Color(0xFF393940),
                        Color(0xFF5C2432),
                        Color(0xFF393940),
                        Color(0xFF853644)
                    )
                ),
                shape = CircleShape
            )
    ) {
        val directionSize = (diameter * 0.34f).coerceAtLeast(54.dp)
        CompassKey(
            Icons.Default.KeyboardArrowUp,
            "Вверх",
            Modifier.align(Alignment.TopCenter).size(directionSize)
        ) { send(RemoteCommand.UP) }
        CompassKey(
            Icons.Default.KeyboardArrowDown,
            "Вниз",
            Modifier.align(Alignment.BottomCenter).size(directionSize)
        ) { send(RemoteCommand.DOWN) }
        CompassKey(
            Icons.Default.KeyboardArrowLeft,
            "Влево",
            Modifier.align(Alignment.CenterStart).size(directionSize)
        ) { send(RemoteCommand.LEFT) }
        CompassKey(
            Icons.Default.KeyboardArrowRight,
            "Вправо",
            Modifier.align(Alignment.CenterEnd).size(directionSize)
        ) { send(RemoteCommand.RIGHT) }
        Box(
            Modifier.align(Alignment.Center)
                .size((diameter * 0.41f).coerceAtLeast(74.dp))
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF25262C), Color(0xFF0B0C10))
                    )
                )
                .border(1.dp, Color(0xFF5B3540), CircleShape)
                .livePress { send(RemoteCommand.OK) },
            contentAlignment = Alignment.Center
        ) {
            Text("OK", color = White, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        }
    }
}

@Composable
private fun CompassKey(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.clip(CircleShape).livePress(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = White, modifier = Modifier.size(30.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreRemoteKeys(onCommand: (RemoteCommand) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KeySurface)
            .livePress { expanded = true }
            .padding(vertical = 12.dp, horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Dialpad, "Открыть цифровую клавиатуру", tint = White)
        Spacer(Modifier.width(12.dp))
        Text(
            "Цифры и медиакнопки",
            color = White, fontSize = 15.sp, modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.KeyboardArrowUp, "Показать снизу", tint = SoftText)
    }
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF17171B),
            contentColor = White
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Клавиатура пульта", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RectKey("◀◀", Modifier.weight(1f)) { onCommand(RemoteCommand.REWIND) }
                    RectKey("▶/Ⅱ", Modifier.weight(1f)) { onCommand(RemoteCommand.PLAY_PAUSE) }
                    RectKey("▶▶", Modifier.weight(1f)) { onCommand(RemoteCommand.FAST_FORWARD) }
                }
                Spacer(Modifier.height(16.dp))
                val rows = listOf(
                    listOf("1" to RemoteCommand.NUMBER_1, "2" to RemoteCommand.NUMBER_2, "3" to RemoteCommand.NUMBER_3),
                    listOf("4" to RemoteCommand.NUMBER_4, "5" to RemoteCommand.NUMBER_5, "6" to RemoteCommand.NUMBER_6),
                    listOf("7" to RemoteCommand.NUMBER_7, "8" to RemoteCommand.NUMBER_8, "9" to RemoteCommand.NUMBER_9),
                    listOf("Гид" to RemoteCommand.GUIDE, "0" to RemoteCommand.NUMBER_0, "Инфо" to RemoteCommand.INFO)
                )
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { (label, command) ->
                            RectKey(label, Modifier.weight(1f)) { onCommand(command) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RectKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(47.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF292930), Color(0xFF1A1A20))))
            .border(1.dp, Color(0xFF393941), RoundedCornerShape(15.dp))
            .livePress(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}

@Composable
private fun Modifier.livePress(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "remote-key-press"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = if (pressed) 0.78f else 1f
    }.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
