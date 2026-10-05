package app.poolt

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PooltApp() {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Poolt", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Диагностика запуска", color = Muted, style = MaterialTheme.typography.labelMedium)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.SettingsRemote,
                contentDescription = null,
                tint = Color(0xFF9E2027),
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text("Poolt запущен", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Базовый экран без IR, Wi‑Fi и каталога.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
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
