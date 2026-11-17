package app.poolt

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.poolt.core.RemoteCommand
import kotlin.math.cos
import kotlin.math.sin

private val Rim = Color(0xFF81293B)
private val Light = Color(0xFFECEEF4)

/**
 * Tactile wheel with four independent pressable sectors.
 * Dark mechanical depth and restrained crimson accents form the signature wheel.
 * The center OK stays a separate button and the hit zones never overlap.
 */
@Composable
fun PhysicalCompassPad(diameter: Dp, onCommand: (RemoteCommand) -> Unit) {
    Box(
        Modifier.size(diameter)
            .shadow(12.dp, CircleShape, ambientColor = Color(0x16000000), spotColor = Color(0x1D9A2239))
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF4A444E), Color(0xFF2C2832), Color(0xFF131219))
                )
            )
            .border(
                1.8.dp,
                Brush.sweepGradient(
                    listOf(
                        Color(0xFF994053), Color(0xFF472A36),
                        Color(0xFF784352), Color(0xFF94808D),
                        Color(0xFFAA394E)
                    )
                ),
                CircleShape
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val rad = size.minDimension / 2f
            val c = center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x00272E39), Color(0x8F090D13)),
                    center = Offset(c.x + rad * .24f, c.y + rad * .34f),
                    radius = rad * 1.25f
                ),
                radius = rad
            )
            drawCircle(
                Color(0xFF9F5160).copy(alpha = .27f),
                radius = rad - 3.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
            // The top rim catches reflected light; lower rim keeps the brand red.
            drawArc(
                color = Color(0xFFCAC0CB).copy(alpha = .40f),
                startAngle = 203f, sweepAngle = 126f,
                useCenter = false,
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(
                    size.width - 6.dp.toPx(), size.height - 6.dp.toPx()
                ),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(Color(0xFF4D2330).copy(alpha = .62f),
                radius = rad * .60f, style = Stroke(width = 1.8.dp.toPx()))
            for (degrees in listOf(45.0, 135.0, 225.0, 315.0)) {
                val r = degrees * Math.PI / 180.0
                val direction = Offset(cos(r).toFloat(), sin(r).toFloat())
                drawLine(
                    color = Color(0xFF0D1119).copy(alpha = .60f),
                    start = c + direction * (rad * .57f),
                    end = c + direction * (rad * .97f),
                    strokeWidth = 2.0.dp.toPx(), cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF5D6674).copy(alpha = .22f),
                    start = c + direction * (rad * .58f) + Offset(1.dp.toPx(), 1.dp.toPx()),
                    end = c + direction * (rad * .96f) + Offset(1.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = .85.dp.toPx()
                )
            }
        }

        val zoneHeight = diameter * .33f
        val zoneWidth = diameter * .51f
        PhysicalDirection(
            icon = Icons.Default.KeyboardArrowUp,
            label = "Вверх",
            modifier = Modifier.align(Alignment.TopCenter).size(zoneWidth, zoneHeight),
            tiltX = -6f, tiltY = 0f
        ) { onCommand(RemoteCommand.UP) }
        PhysicalDirection(
            icon = Icons.Default.KeyboardArrowDown,
            label = "Вниз",
            modifier = Modifier.align(Alignment.BottomCenter).size(zoneWidth, zoneHeight),
            tiltX = 6f, tiltY = 0f
        ) { onCommand(RemoteCommand.DOWN) }
        PhysicalDirection(
            icon = Icons.Default.KeyboardArrowLeft,
            label = "Влево",
            modifier = Modifier.align(Alignment.CenterStart).size(zoneHeight, zoneWidth),
            tiltX = 0f, tiltY = -6f
        ) { onCommand(RemoteCommand.LEFT) }
        PhysicalDirection(
            icon = Icons.Default.KeyboardArrowRight,
            label = "Вправо",
            modifier = Modifier.align(Alignment.CenterEnd).size(zoneHeight, zoneWidth),
            tiltX = 0f, tiltY = 6f
        ) { onCommand(RemoteCommand.RIGHT) }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val press by animateFloatAsState(
            if (pressed) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessHigh),
            label = "ok-depth"
        )
        Box(
            Modifier.align(Alignment.Center)
                .size(diameter * .40f)
                .graphicsLayer {
                    scaleX = 1f - press * .055f
                    scaleY = 1f - press * .055f
                    translationY = 3.dp.toPx() * press
                }
                .shadow(if (pressed) 1.dp else 5.dp, CircleShape,
                    ambientColor = Color(0x1A000000), spotColor = Color(0x3D9E1B35))
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (pressed) listOf(Color(0xFF26111B), Color(0xFF101015))
                        else listOf(Color(0xFF51404B), Color(0xFF302431), Color(0xFF121116))
                    )
                )
                .border(
                    1.5.dp,
                    if (pressed) Color(0xFFD75063) else Color(0xFFAE4556),
                    CircleShape
                )
                .clickable(
                    interactionSource = interaction, indication = null,
                    onClick = { onCommand(RemoteCommand.OK) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("OK", color = Light, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PhysicalDirection(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    tiltX: Float,
    tiltY: Float,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateFloatAsState(
        if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "sector-depth"
    )
    Box(
        modifier
            .graphicsLayer {
                rotationX = tiltX * depth
                rotationY = tiltY * depth
                scaleX = 1f - .028f * depth
                scaleY = 1f - .055f * depth
                translationY = 2.4.dp.toPx() * depth
            }
            .clip(CircleShape)
            .background(
                if (pressed) Brush.radialGradient(
                    listOf(Color(0xFF4A2C37), Color(0x00252A34))
                ) else Brush.radialGradient(
                    listOf(Color(0x00242A34), Color(0x00242A34))
                )
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = label,
            modifier = Modifier.size(35.dp),
            tint = if (pressed) Color.White else Light
        )
    }
}
