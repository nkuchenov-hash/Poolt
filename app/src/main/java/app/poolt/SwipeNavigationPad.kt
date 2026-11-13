package app.poolt

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.poolt.core.RemoteCommand
import kotlin.math.abs

/**
 * A broad, quiet touch surface. Motion is NEVER haptic; click has its
 * own handler so the parent vibrates only for actual taps.
 * For TVs without pointer support, swipes send directional D-pad commands.
 */
@Composable
fun SwipeNavigationPad(
    width: Dp,
    height: Dp,
    onMove: (RemoteCommand) -> Unit,
    onTap: () -> Unit
) {
    val move by rememberUpdatedState(onMove)
    val tap by rememberUpdatedState(onTap)
    var fingerDown by remember { mutableStateOf(false) }
    val pressDepth by animateFloatAsState(
        if (fingerDown) 1f else 0f, animationSpec = spring(), label = "swipe-surface-press"
    )
    val shape = RoundedCornerShape(29.dp)
    Box(
        Modifier.size(width, height)
            .graphicsLayer {
                scaleX = 1f - .008f * pressDepth
                scaleY = 1f - .015f * pressDepth
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF202632), Color(0xFF191F2A), Color(0xFF0F131B))
                )
            )
            .border(1.dp, Color(0xFF404855), shape)
            .pointerInput(Unit) {
                var accumX = 0f
                var accumY = 0f
                val threshold = 53.dp.toPx()
                detectDragGestures(
                    onDragStart = { accumX = 0f; accumY = 0f; fingerDown = true },
                    onDragEnd = { fingerDown = false },
                    onDragCancel = { fingerDown = false },
                    onDrag = { change, delta ->
                        change.consume()
                        accumX += delta.x
                        accumY += delta.y
                        if (abs(accumX) >= threshold || abs(accumY) >= threshold) {
                            val horizontal = abs(accumX) >= abs(accumY)
                            move(
                                if (horizontal) {
                                    if (accumX > 0) RemoteCommand.RIGHT else RemoteCommand.LEFT
                                } else {
                                    if (accumY > 0) RemoteCommand.DOWN else RemoteCommand.UP
                                }
                            )
                            accumX = 0f
                            accumY = 0f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        fingerDown = true
                        tryAwaitRelease()
                        fingerDown = false
                    },
                    onTap = { tap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        TouchSurfaceTexture()
    }
}

@Composable
fun TouchSurfaceTexture() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 19.dp.toPx()
        var y = step
        while (y < size.height - step * .5f) {
            var x = step
            while (x < size.width - step * .5f) {
                drawCircle(
                    color = Color(0xFFB0B8C5).copy(alpha = .075f),
                    radius = 1.0.dp.toPx(), center = Offset(x, y)
                )
                x += step
            }
            y += step
        }
        // A tiny center depression, not a large label or control target.
        drawCircle(
            color = Color(0xFF111621),
            radius = 12.dp.toPx()
        )
        drawCircle(
            color = Color(0xFF535D6D).copy(alpha = .40f),
            radius = 12.dp.toPx(),
            style = Stroke(width = .85.dp.toPx())
        )
    }
}
