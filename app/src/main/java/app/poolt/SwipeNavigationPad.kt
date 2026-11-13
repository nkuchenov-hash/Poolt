package app.poolt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.poolt.core.RemoteCommand
import kotlin.math.abs

/**
 * Fallback touch surface for devices without cursor protocols.
 * Swipe transmits standard D-pad commands; tapping transmits OK.
 * It is not advertised as a real pointer cursor.
 */
@Composable
fun SwipeNavigationPad(
    size: Dp,
    onCommand: (RemoteCommand) -> Unit
) {
    val shape = RoundedCornerShape(29.dp)
    Box(
        Modifier.size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF252C37), Color(0xFF141922))))
            .border(1.dp, Color(0xFF353D4B), shape)
            .pointerInput(onCommand) {
                var distanceX = 0f
                var distanceY = 0f
                val threshold = 37.dp.toPx()
                detectDragGestures(
                    onDragStart = { distanceX = 0f; distanceY = 0f },
                    onDrag = { change, drag ->
                        change.consume()
                        distanceX += drag.x
                        distanceY += drag.y
                        if (abs(distanceX) >= threshold || abs(distanceY) >= threshold) {
                            val horizontal = abs(distanceX) >= abs(distanceY)
                            onCommand(
                                if (horizontal) {
                                    if (distanceX > 0) RemoteCommand.RIGHT else RemoteCommand.LEFT
                                } else {
                                    if (distanceY > 0) RemoteCommand.DOWN else RemoteCommand.UP
                                }
                            )
                            distanceX = 0f
                            distanceY = 0f
                        }
                    }
                )
            }
            .pointerInput(onCommand) {
                detectTapGestures(onTap = { onCommand(RemoteCommand.OK) })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Swipe, null, Modifier.size(29.dp), tint = Color(0xFFB9C1CD))
            Spacer(Modifier.height(10.dp))
            Text("ТАЧПАД", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Text("Свайпы — стрелки, касание — OK", color = Color(0xFF98A1AE), fontSize = 11.sp)
        }
    }
}
