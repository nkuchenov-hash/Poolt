package app.poolt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.poolt.core.RemoteCommand

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { PooltApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PooltApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val device = state.selected

    Scaffold(
        containerColor = Color(0xFFF7F7F8),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Poolt", fontWeight = FontWeight.Bold)
                        Text(device?.name ?: "No device selected", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.scan() }) {
                        if (state.isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Scan")
                    }
                    IconButton(onClick = {}) { Icon(Icons.Default.Add, "Add device") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (device?.isOnline == true) Color(0xFF34C759) else Color.LightGray))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(device?.name ?: "Searching for devices…", fontWeight = FontWeight.SemiBold)
                        Text(device?.address ?: "Same Wi-Fi network", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RemoteButton(Icons.Default.PowerSettingsNew, "Power") { vm.send(RemoteCommand.POWER) }
                RemoteButton(Icons.Default.Home, "Home") { vm.send(RemoteCommand.HOME) }
                RemoteButton(Icons.Default.VolumeOff, "Mute") { vm.send(RemoteCommand.MUTE) }
            }

            Spacer(Modifier.height(28.dp))
            DPad(vm::send)
            Spacer(Modifier.height(28.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RemoteButton(Icons.Default.VolumeDown, "Volume down") { vm.send(RemoteCommand.VOLUME_DOWN) }
                Text("VOL", Modifier.align(Alignment.CenterVertically), fontWeight = FontWeight.SemiBold)
                RemoteButton(Icons.Default.VolumeUp, "Volume up") { vm.send(RemoteCommand.VOLUME_UP) }
            }

            state.lastCommand?.let {
                Spacer(Modifier.height(24.dp))
                Text("Sent: " + it.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun RemoteButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(58.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF171719))
    ) { Icon(icon, description) }
}

@Composable
private fun DPad(onCommand: (RemoteCommand) -> Unit) {
    Box(Modifier.size(230.dp).clip(CircleShape).background(Color.White)) {
        IconButton({ onCommand(RemoteCommand.UP) }, Modifier.align(Alignment.TopCenter).size(72.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(38.dp))
        }
        IconButton({ onCommand(RemoteCommand.DOWN) }, Modifier.align(Alignment.BottomCenter).size(72.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(38.dp))
        }
        IconButton({ onCommand(RemoteCommand.LEFT) }, Modifier.align(Alignment.CenterStart).size(72.dp)) {
            Icon(Icons.Default.KeyboardArrowLeft, "Left", Modifier.size(38.dp))
        }
        IconButton({ onCommand(RemoteCommand.RIGHT) }, Modifier.align(Alignment.CenterEnd).size(72.dp)) {
            Icon(Icons.Default.KeyboardArrowRight, "Right", Modifier.size(38.dp))
        }
        Button(
            onClick = { onCommand(RemoteCommand.OK) },
            modifier = Modifier.align(Alignment.Center).size(82.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171719))
        ) { Text("OK") }
    }
}
