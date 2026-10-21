package app.poolt

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LgTouchpad(state: MainUiState, vm: MainViewModel) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val view = LocalView.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Mouse, null, tint = Color(0xFFE6263E))
            Spacer(Modifier.width(10.dp))
            Text("Тачпад LG", fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                "Свернуть", tint = Color(0xFFB5B5BC))
        }
        if (expanded) {
            if (state.selected?.isOnline != true) {
                Text("Подключите LG по Wi-Fi для управления курсором.",
                    color = Color(0xFFB5B5BC), fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::pairSelected, modifier = Modifier.fillMaxWidth()) {
                    Text("Подключить LG", color = Color.White, fontSize = 16.sp)
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF222228))
                        .border(1.dp, Color(0xFF44444C), RoundedCornerShape(18.dp))
                        .pointerInput(state.selected?.id) {
                            detectDragGestures { change, delta ->
                                change.consume()
                                vm.movePointer(delta.x, delta.y)
                            }
                        }
                        .pointerInput(state.selected?.id) {
                            detectTapGestures(onTap = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                vm.clickPointer()
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.TouchApp, null, Modifier.size(32.dp),
                            tint = Color(0xFF85858D))
                        Spacer(Modifier.height(12.dp))
                        Text("Двигайте пальцем · касание — выбор",
                            color = Color(0xFFDADADD), fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            vm.scrollPointer(-65f)
                        }, modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.KeyboardArrowUp, null); Text(" Вверх", fontSize = 15.sp) }
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            vm.scrollPointer(65f)
                        }, modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.KeyboardArrowDown, null); Text(" Вниз", fontSize = 15.sp) }
                }
            }
        }
    }
}
