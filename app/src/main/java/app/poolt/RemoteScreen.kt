package app.poolt

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.poolt.core.RemoteCommand

private val ScreenBg = Color(0xFF08080C)
private val SurfaceTop = Color(0xFF29292F)
private val SurfaceBottom = Color(0xFF141418)
private val KeySurface = Color(0xFF1D1E23)
private val SoftText = Color(0xFFB7B5BE)
private val Red = Color(0xFFE6263E) // Matches the approved splash-screen wordmark
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
                Modifier.fillMaxWidth().background(ScreenBg)
                    .statusBarsPadding()
                    .padding(start = 19.dp, end = 15.dp, top = 11.dp, bottom = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandPowerMark()
                    Spacer(Modifier.width(11.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Red)) { append("Пульт") }
                            withStyle(SpanStyle(color = White)) { append("офон") }
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    BrandHeaderAction(Icons.Default.Add, "Добавить телевизор") {
                        feedback()
                        vm.openPicker()
                    }
                    Spacer(Modifier.width(9.dp))
                    BrandHeaderAction(Icons.Default.Settings, "Настройки") {
                        feedback()
                        showAppSettings = true
                    }
                }
                Spacer(Modifier.height(13.dp))
                val connected = when (state.mode) {
                    ControlMode.IR -> state.irVerified &&
                        state.selectedIrProfile != null && state.irAvailable
                    ControlMode.WIFI -> state.selected?.isOnline == true
                }
                val selectedName = when (state.mode) {
                    ControlMode.IR -> state.selectedIrProfile?.let { "${it.brand} · ИК" }
                    ControlMode.WIFI -> state.selected?.let {
                        it.brand?.let { brand ->
                            if (it.name.startsWith(brand, true)) it.name
                            else "$brand · ${it.name}"
                        } ?: it.name
                    }
                } ?: "Выбрать телевизор"
                Row(
                    Modifier.widthIn(max = 300.dp)
                        .heightIn(min = 41.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Brush.verticalGradient(listOf(
                            Color(0xFF272027), Color(0xFF17141A)
                        )))
                        .border(1.dp, Color(0xFF5E404A), RoundedCornerShape(21.dp))
                        .clickable { feedback(); vm.openPicker() }
                        .padding(start = 14.dp, end = 11.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (connected) Green else Red)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        selectedName,
                        modifier = Modifier.weight(1f, fill = false),
                        color = White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.KeyboardArrowDown, "Выбрать телевизор",
                        modifier = Modifier.size(20.dp), tint = SoftText)
                }
            }
        }
    ) { scaffoldPadding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(scaffoldPadding)
                .navigationBarsPadding()
                .background(Brush.verticalGradient(listOf(
                    ScreenBg, Color(0xFF0B090E), Color(0xFF11090E)
                )))
        ) {
          val minimumRemoteHeight = (maxHeight - 10.dp).coerceAtLeast(648.dp)
          Column(
              Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                  .padding(start = 15.dp, end = 15.dp, bottom = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally
          ) {
            // Primary controls receive most visual space. Secondary actions
            // are intentionally separate from the navigation ring.
            Column(
                Modifier.fillMaxWidth().heightIn(min = minimumRemoteHeight)
                    .padding(horizontal = 1.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteRoundKey(Icons.Default.PowerSettingsNew, "Питание", true, 64.dp) {
                        send(RemoteCommand.POWER)
                    }
                    RemoteRoundKey(Icons.Default.Home, "Главная", false, 64.dp) {
                        send(RemoteCommand.HOME)
                    }
                    RemoteRoundKey(Icons.Default.Keyboard, "Клавиатура для ввода текста", false, 64.dp) {
                        feedback()
                        keyboardOpen = true
                    }
                    RemoteRoundKey(Icons.Default.Input, "Источник", false, 64.dp) {
                        send(RemoteCommand.SOURCE)
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val dialSize = maxWidth.coerceAtMost(280.dp)
                    val touchWidth = maxWidth.coerceAtMost(420.dp)
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (useTouchpad) {
                            val padWidth = touchWidth
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

                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(23.dp))
                                .background(Brush.verticalGradient(listOf(
                                    Color(0xFF25191E), Color(0xFF151217)
                                )))
                                .border(1.dp, Color(0xFF7B3644), RoundedCornerShape(23.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(false to "Джойстик", true to "Тачпад")
                                .forEach { (touchpad, title) ->
                                    val active = useTouchpad == touchpad
                                    Row(
                                        Modifier.clip(RoundedCornerShape(19.dp))
                                            .background(if (active) Brush.verticalGradient(
                                                listOf(Color(0xFFDC3048), Color(0xFF9F182F))
                                            ) else Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Transparent)
                                            ))
                                            .livePress {
                                                if (useTouchpad != touchpad) feedback()
                                                useTouchpad = touchpad
                                            }
                                            .padding(horizontal = 15.dp, vertical = 12.dp),
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
                    val rockerWidth = if (maxWidth < 325.dp) 66.dp else 76.dp
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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

                MoreRemoteKeys(send, feedback)
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
private fun BrandPowerMark() {
    Canvas(Modifier.size(29.dp)) {
        val width = 2.6.dp.toPx()
        drawArc(
            color = Red, startAngle = 205f, sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(size.width * .08f, size.height * .01f),
            size = Size(size.width * .84f, size.height * .41f),
            style = Stroke(width = width, cap = StrokeCap.Round)
        )
        drawArc(
            color = Red, startAngle = -46f, sweepAngle = 272f,
            useCenter = false,
            topLeft = Offset(size.width * .14f, size.height * .27f),
            size = Size(size.width * .72f, size.height * .70f),
            style = Stroke(width = width, cap = StrokeCap.Round)
        )
        drawLine(
            color = Red, start = Offset(size.width * .50f, size.height * .32f),
            end = Offset(size.width * .50f, size.height * .67f),
            strokeWidth = width, cap = StrokeCap.Round
        )
    }
}

@Composable
private fun BrandHeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier.size(43.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(
                Color(0xFF26242B), Color(0xFF151419)
            )))
            .border(1.dp, Color(0xFF51404A), shape)
            .livePress(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, modifier = Modifier.size(24.dp), tint = White)
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
        label = "quick-key-press"
    )
    val shape = RoundedCornerShape(20.dp)
    val colors = when {
        isPower && pressed -> listOf(Color(0xFFA11128), Color(0xFFDA2A41))
        isPower -> listOf(Color(0xFFEF3F53), Color(0xFFAC1129))
        pressed -> listOf(Color(0xFF15151A), Color(0xFF44303B))
        else -> listOf(Color(0xFF353039), Color(0xFF18171D))
    }
    Box(
        Modifier.size(diameter)
            .graphicsLayer {
                scaleX = 1f - press * .055f
                scaleY = 1f - press * .075f
                translationY = press * 3.dp.toPx()
            }
            .shadow(
                elevation = if (pressed) 1.dp else 6.dp,
                shape = shape,
                ambientColor = if (isPower) Color(0x22C7243B) else Color(0x1A000000),
                spotColor = if (isPower) Color(0x32E6263E) else Color(0x55000000)
            )
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(
                1.1.dp,
                if (isPower) Color(0xFFEC6170)
                else if (pressed) Color(0xFFAD4557)
                else Color(0xFF68414C),
                shape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = White, modifier = Modifier.size(27.dp))
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
        label = "small-key-depth"
    )
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(79.dp)
            .graphicsLayer {
                scaleX = 1f - depth * .05f
                scaleY = 1f - depth * .065f
                translationY = depth * 3.dp.toPx()
            }
            .shadow(3.dp, shape, ambientColor = Color(0x15000000),
                spotColor = Color(0x26000000))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(Color(0xFF1A151C), Color(0xFF46212E))
                    else listOf(Color(0xFF34303A), Color(0xFF17151C))
                )
            )
            .border(1.dp, if (pressed) Color(0xFFB5394F) else Color(0xFF63404D), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, title, Modifier.size(25.dp), tint = White)
        Spacer(Modifier.height(5.dp))
        Text(title, fontSize = 13.sp, color = White, maxLines = 1)
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
            .shadow(4.dp, shape, ambientColor = Color(0x13000000),
                spotColor = Color(0x24000000))
            .clip(shape)
            .background(Brush.verticalGradient(
                listOf(Color(0xFF34303A), Color(0xFF17151C))
            ))
            .border(1.dp, Color(0xFF6B3A49), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        RockerHalf(
            icon = topIcon,
            title = "$title +",
            onClick = onTop
        )
        Text(
            title, color = White, fontSize = 13.sp,
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
        label = "rocker-press"
    )
    Box(
        Modifier.fillMaxWidth().height(70.dp)
            .graphicsLayer {
                scaleX = 1f - .04f * depth
                scaleY = 1f - .048f * depth
                translationY = 2.5.dp.toPx() * depth
            }
            .background(if (pressed) Color(0xFF51202E) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, title, tint = White, modifier = Modifier.size(30.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreRemoteKeys(onCommand: (RemoteCommand) -> Unit, feedback: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(
                Color(0xFF2C2029), Color(0xFF171319)
            )))
            .border(1.dp, Color(0xFF6E3545), RoundedCornerShape(16.dp))
            .livePress { feedback(); expanded = true }
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Dialpad, "Открыть цифровую клавиатуру", tint = White)
        Spacer(Modifier.width(12.dp))
        Text(
            "Цифры и медиакнопки",
            color = White, fontSize = 15.sp, modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.KeyboardArrowDown, "Открыть медиакнопки", tint = SoftText)
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
            .background(Brush.verticalGradient(listOf(Color(0xFF332C36), Color(0xFF18161C))))
            .border(1.dp, Color(0xFF60404B), RoundedCornerShape(15.dp))
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
