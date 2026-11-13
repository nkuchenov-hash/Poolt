package app.poolt

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shares the exact same space as the D-pad. The parent only displays this
 * when LG pointer input has been negotiated successfully.
 * A finger drag moves the TV pointer; tapping clicks. Never vibrate on
 * every drag event, as that would create an unpleasant continuous buzz.
 */
@Composable
fun LgTouchpadSurface(
    state: MainUiState,
    vm: MainViewModel,
    size: Dp
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier.size(size)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1C2029), Color(0xFF151922), Color(0xFF0F1119))
                )
            )
            .border(1.dp, Color(0xFF323843), shape)
            .pointerInput(state.selected?.id) {
                detectDragGestures { change, delta ->
                    change.consume()
                    vm.movePointer(delta.x, delta.y)
                }
            }
            .pointerInput(state.selected?.id, state.hapticEnabled) {
                detectTapGestures(onTap = {
                    if (state.hapticEnabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    vm.clickPointer()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color(0xFF818A9A)
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "ТАЧПАД",
                color = Color(0xFFD1D6E0),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Свайп — курсор · касание — OK",
                color = Color(0xFF9198A6),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
