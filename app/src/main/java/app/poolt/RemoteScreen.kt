package app.poolt

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
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
    val send: (RemoteCommand) -> Unit = { command ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        vm.send(command)
    }
    Scaffold(
        containerColor = ScreenBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(ScreenBg)
                    .padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable { vm.openPicker() }
                        .padding(vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name), fontSize = 21.sp,
                            fontWeight = FontWeight.Bold, color = White)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(19.dp), tint = SoftText)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ready = when (state.mode) {
                            ControlMode.IR -> state.irAvailable && state.irVerified &&
                                state.selectedIrProfile != null
                            ControlMode.WIFI -> state.selected?.isOnline == true
                        }
                        Box(
                            Modifier.size(7.dp).clip(CircleShape).background(
                                if (ready) Green else Color(0xFF62646A)
                            )
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = when (state.mode) {
                                ControlMode.IR -> state.selectedIrProfile?.let {
                                    it.brand + " · ИК · " +
                                        if (state.irVerified) "проверен" else "не проверен"
                                } ?: "Выбрать устройство"
                                ControlMode.WIFI -> when {
                                    state.selected == null -> "Выбрать устройство"
                                    state.selected?.driverId == "lg.webos" && state.pointerConfirmed ->
                                        "LG webOS · Wi-Fi · курсор проверен"
                                    state.selected?.driverId == "lg.webos" ->
                                        "LG webOS · " + if (ready) "Wi-Fi" else "не подключён"
                                    else -> state.selected?.name.orEmpty() +
                                        " · " + if (ready) "Wi-Fi" else "не подключён"
                                }
                            },
                            fontSize = 12.sp, color = SoftText, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(SurfaceTop, SurfaceBottom)))
                        .border(1.dp, Color(0xFF3B3C41), CircleShape)
                        .clickable { vm.openPicker() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "Добавить устройство", tint = White) }
            }
        }
    ) { scaffoldPadding ->
        Column(
            Modifier.fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 15.dp, end = 15.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // One cohesive remote body. The very subtle red edge light is decorative
            // and does not change the size or hit area of the controls.
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(27.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF18151B), Color(0xFF101015), Color(0xFF0F1014))
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF803039), Color(0xFF303039), Color(0xFF501D2C))
                        ),
                        shape = RoundedCornerShape(27.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 17.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteRoundKey(Icons.Default.PowerSettingsNew, "Питание", true, 53.dp) {
                        send(RemoteCommand.POWER)
                    }
                    RemoteRoundKey(Icons.Default.Input, "Источник", false, 53.dp) {
                        send(RemoteCommand.SOURCE)
                    }
                    RemoteRoundKey(Icons.Default.Home, "Главная", false, 53.dp) {
                        send(RemoteCommand.HOME)
                    }
                    RemoteRoundKey(Icons.Default.Menu, "Меню", false, 53.dp) {
                        send(RemoteCommand.MENU)
                    }
                }

                Spacer(Modifier.height(20.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val rockerWidth = 59.dp
                    val gap = 7.dp
                    val dialSize = (maxWidth - rockerWidth * 2 - gap * 2)
                        .coerceIn(170.dp, 235.dp)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeonRocker(
                            Modifier.width(rockerWidth),
                            "VOL", Icons.Default.Add, Icons.Default.Remove,
                            { send(RemoteCommand.VOLUME_UP) },
                            { send(RemoteCommand.VOLUME_DOWN) }
                        )
                        Spacer(Modifier.width(gap))
                        CompassPad(dialSize, send)
                        Spacer(Modifier.width(gap))
                        NeonRocker(
                            Modifier.width(rockerWidth),
                            "CH", Icons.Default.KeyboardArrowUp,
                            Icons.Default.KeyboardArrowDown,
                            { send(RemoteCommand.CHANNEL_UP) },
                            { send(RemoteCommand.CHANNEL_DOWN) }
                        )
                    }
                }
                Spacer(Modifier.height(19.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteRoundKey(Icons.Default.ArrowBack, "Назад", false, 54.dp) {
                        send(RemoteCommand.BACK)
                    }
                    RemoteRoundKey(Icons.Default.VolumeOff, "Без звука", false, 54.dp) {
                        send(RemoteCommand.MUTE)
                    }
                    RemoteRoundKey(Icons.Default.Settings, "Настройки", false, 54.dp) {
                        send(RemoteCommand.SETTINGS)
                    }
                }

                if (state.mode == ControlMode.WIFI &&
                    state.selected?.driverId == "lg.webos"
                ) {
                    Spacer(Modifier.height(14.dp))
                    LgTouchpad(state, vm)
                }

                Spacer(Modifier.height(10.dp))
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
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription, tint = White, modifier = Modifier.size(24.dp)) }
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
    Column(
        modifier.height(191.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(SurfaceTop, SurfaceBottom)))
            .border(
                width = 1.dp,
                color = Color(0xFF573039),
                shape = RoundedCornerShape(26.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.fillMaxWidth().height(68.dp).clickable(onClick = onTop),
            contentAlignment = Alignment.Center
        ) {
            Icon(topIcon, title + " +", tint = White, modifier = Modifier.size(29.dp))
        }
        Text(title, color = Color(0xFFDEE0E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.fillMaxWidth().height(68.dp).clickable(onClick = onBottom),
            contentAlignment = Alignment.Center
        ) {
            Icon(bottomIcon, title + " −", tint = White, modifier = Modifier.size(29.dp))
        }
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
                .clickable { send(RemoteCommand.OK) },
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
        modifier.clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = White, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun MoreRemoteKeys(onCommand: (RemoteCommand) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Dialpad, null, tint = SoftText)
        Spacer(Modifier.width(10.dp))
        Text(
            "Цифры и медиакнопки",
            color = Color(0xFFCACBD0), fontSize = 15.sp, modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            "Развернуть", tint = SoftText
        )
    }
    if (expanded) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RectKey("◀◀", Modifier.weight(1f)) { onCommand(RemoteCommand.REWIND) }
            RectKey("▶/Ⅱ", Modifier.weight(1f)) { onCommand(RemoteCommand.PLAY_PAUSE) }
            RectKey("▶▶", Modifier.weight(1f)) { onCommand(RemoteCommand.FAST_FORWARD) }
        }
        Spacer(Modifier.height(12.dp))
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

@Composable
private fun RectKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(47.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF292930), Color(0xFF1A1A20))))
            .border(1.dp, Color(0xFF393941), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}
