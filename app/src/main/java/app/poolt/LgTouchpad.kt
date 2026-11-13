package app.poolt

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Broad real LG webOS cursor pad. Swipe moves the remote pointer and makes
 * NO vibration; a separate tap emits OK and a single optional haptic pulse.
 */
@Composable
fun LgTouchpadSurface(
    state: MainUiState,
    vm: MainViewModel,
    width: Dp,
    height: Dp
) {
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = remember(context) { HapticEngine(context) }
    val deviceId = state.selected?.id
    val enabledHaptic by rememberUpdatedState(state.hapticEnabled)
    var fingerDown by remember { mutableStateOf(false) }
    val depth by animateFloatAsState(
        if (fingerDown) 1f else 0f,
        animationSpec = spring(), label = "lg-touch-press"
    )
    val shape = RoundedCornerShape(29.dp)

    Box(
        Modifier.size(width, height)
            .graphicsLayer {
                scaleX = 1f - .008f * depth
                scaleY = 1f - .015f * depth
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF202632), Color(0xFF191F2A), Color(0xFF0F131B))
                )
            )
            .border(1.dp, Color(0xFF404855), shape)
            .pointerInput(deviceId) {
                detectDragGestures(
                    onDragStart = { fingerDown = true },
                    onDragEnd = { fingerDown = false },
                    onDragCancel = { fingerDown = false }
                ) { change, delta ->
                    change.consume()
                    vm.movePointer(delta.x, delta.y)
                }
            }
            .pointerInput(deviceId) {
                detectTapGestures(
                    onPress = {
                        fingerDown = true
                        tryAwaitRelease()
                        fingerDown = false
                    },
                    onTap = {
                        haptics.tap(view, enabledHaptic)
                        vm.clickPointer()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        TouchSurfaceTexture()
    }
}
