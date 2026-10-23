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
import androidx.compose.ui.graphics.Brush
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
            Icon(Icons.Default.Mouse, null, tint = Color(0xFFF3334D))
            Spacer(Modifier.width(10.dp))
            Text("ТАЧПАД LG", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
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
            } else if (state.pointerChecking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Проверяем поддержку курсора на телевизоре…",
                    color = Color(0xFFB5B5BC), fontSize = 16.sp)
            } else if (!state.pointerReady) {
                Text(
                    if (state.pointerStatus.isBlank()) "Возможность управлять курсором ещё не проверена."
                    else state.pointerStatus,
                    color = Color(0xFFB5B5BC), fontSize = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = vm::checkLgPointer, modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить курсор LG", color = Color.White, fontSize = 16.sp)
                }
            } else {
                if (state.pointerConfirmed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF35C873))
                        Spacer(Modifier.width(8.dp))
                        Text("Курсор работает — подтверждено на ТВ",
                            color = Color.White, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Box(
                    Modifier.fillMaxWidth().height(178.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF21171D), Color(0xFF131217), Color(0xFF171318))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFFF02A45), Color(0xFF8C2637), Color(0xFFEB3147))
                            ),
                            RoundedCornerShape(20.dp)
                        )
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
                        Text("Водите пальцем · касание — выбор",
                            color = Color(0xFFBBB8BE), fontSize = 15.sp)
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
                if (!state.pointerConfirmed) {
                    Spacer(Modifier.height(12.dp))
                    Text("Проведите пальцем по тачпаду. Курсор на экране LG перемещается?",
                        color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = vm::confirmLgPointerWorks,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF35353B), contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("Да, работает", fontSize = 14.sp) }
                        OutlinedButton(
                            onClick = vm::reportLgPointerProblem,
                            modifier = Modifier.weight(1f)
                        ) { Text("Нет", color = Color.White, fontSize = 14.sp) }
                    }
                    if (state.pointerStatus.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.pointerStatus, color = Color(0xFFB5B5BC), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
