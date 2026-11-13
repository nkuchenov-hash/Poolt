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
            Row(
                Modifier.fillMaxWidth()
                    .background(ScreenBg)
                    .statusBarsPadding()
                    .height(60.dp)
                    .padding(start = 17.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { feedback(); vm.openPicker() }
                        .padding(vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Пультофон", fontSize = 16.sp, color = White,
                        fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val connected = when (state.mode) {
                            ControlMode.IR -> state.irVerified && state.selectedIrProfile != null && state.irAvailable
                            ControlMode.WIFI -> state.selected?.isOnline == true
                        }
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(if (connected) Green else Color(0xFF686C77))
                        )
                        Spacer(Modifier.width(7.dp))
                        val name = when (state.mode) {
                            ControlMode.IR -> state.selectedIrProfile?.let { "${it.brand} · ИК" }
                            ControlMode.WIFI -> state.selected?.let {
                                it.brand?.let { brand -> if (it.name.startsWith(brand, true)) it.name else brand + " · " + it.name }
                                    ?: it.name
                            }
                        } ?: "Выбрать телевизор"
                        Text(
                            name, color = SoftText, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, "Выбрать телевизор",
                            Modifier.size(16.dp), tint = SoftText)
                    }
                }
                IconButton(
                    onClick = { feedback(); vm.openPicker() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, "Добавить телевизор",
                        Modifier.size(23.dp), tint = White)
                }
                IconButton(
                    onClick = { feedback(); showAppSettings = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Settings, "Настройки",
                        Modifier.size(22.dp), tint = SoftText)
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
                            val padWidth = maxWidth.coerceAtMost(420.dp)
                            val padHeight = padWidth * 0.67f
                            if (pointerAvailable) {
                                LgTouchpadSurface(state, vm, padWidth, padHeight)
                            } else {
                                SwipeNavigationPad(
                                    width = padWidth,
                                    height = padHeight,
                                    onMove = { command -> vm.send(command) },
                                    onTap = { send(RemoteCommand.OK) }
                                )
                            }
                        } else {
                            PhysicalCompassPad(dialSize, send)
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
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "round-physical-press"
    )
    val colors = when {
        isPower && pressed -> listOf(Color(0xFF9E1E32), Color(0xFFDA354C))
        isPower -> listOf(Color(0xFFF54A60), Color(0xFFB91C37))
        pressed -> listOf(Color(0xFF11151C), Color(0xFF373D47))
        else -> listOf(Color(0xFF323844), Color(0xFF1A1D25))
    }
    Box(
        Modifier.size(diameter)
            .graphicsLayer {
                scaleX = 1f - press * .06f
                scaleY = 1f - press * .07f
                translationY = press * 3.dp.toPx()
            }
            .shadow(
                if (pressed) 1.dp else 7.dp,
                CircleShape, false,
                ambientColor = Color(0x80000000),
                spotColor = Color.Black
            )
            .clip(CircleShape)
            .background(Brush.verticalGradient(colors))
            .border(
                1.2.dp,
                if (isPower) Color(0xFFEF6B7E)
                else if (pressed) Color(0xFF313944)
                else Color(0xFF525865),
                CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = White, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun CompactActionKey(
    icon: ImageVector,
    title: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "small-physical-press"
    )
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(79.dp)
            .graphicsLayer {
                scaleX = 1f - depth * .045f
                scaleY = 1f - depth * .065f
                translationY = depth * 3.dp.toPx()
            }
            .shadow(if (pressed) 1.dp else 5.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(Color(0xFF151920), Color(0xFF343B47))
                    else listOf(Color(0xFF303744), Color(0xFF171C26))
                )
            )
            .border(1.dp, if (pressed) Color(0xFF2D3542) else Color(0xFF4C5361), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, title, Modifier.size(25.dp), tint = White)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 12.sp, color = SoftText, maxLines = 1)
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
            .shadow(5.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2B323F), Color(0xFF151A23))))
            .border(1.dp, Color(0xFF4A515F), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        RockerHalf(
            icon = topIcon,
            title = "$title +",
            onClick = onTop
        )
        Text(
            title, color = Color(0xFFD3D8E1), fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1
        )
        RockerHalf(
            icon = bottomIcon,
            title = "$title −",
            onClick = onBottom
        )
    }
}

@Composable
private fun RockerHalf(icon: ImageVector, title: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "rocker-half-press"
    )
    Box(
        Modifier.fillMaxWidth().height(70.dp)
            .graphicsLayer {
                scaleX = 1f - .04f * depth
                scaleY = 1f - .045f * depth
                translationY = 2.dp.toPx() * depth
            }
            .background(if (pressed) Color(0xFF161B24) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, title, tint = White, modifier = Modifier.size(30.dp))
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
    val depth by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "remote-key-depth"
    )
    return this.graphicsLayer {
        scaleX = 1f - depth * .05f
        scaleY = 1f - depth * .065f
        translationY = depth * 2.8.dp.toPx()
    }.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
