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

private val Rim = Color(0xFF763244)
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
            .shadow(13.dp, CircleShape, ambientColor = Color(0x85391425), spotColor = Color.Black)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF332A35), Color(0xFF27232D), Color(0xFF14151D))
                )
            )
            .border(
                1.6.dp,
                Brush.sweepGradient(
                    listOf(
                        Color(0xFF9E3550), Color(0xFF49414E),
                        Color(0xFFC7435F), Color(0xFF514351),
                        Color(0xFF9E3550)
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
            drawCircle(Color(0xFFD04E65).copy(alpha = .28f),
                radius = rad - 3.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
            drawCircle(Color(0xFF461F2C).copy(alpha = .9f),
                radius = rad * .60f, style = Stroke(width = 1.5.dp.toPx()))
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
                .shadow(if (pressed) 1.dp else 8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (pressed) listOf(Color(0xFF101319), Color(0xFF252B35))
                        else listOf(Color(0xFF2A303A), Color(0xFF0B0D13))
                    )
                )
                .border(
                    1.5.dp,
                    if (pressed) Color(0xFFF45573) else Color(0xFF9B4B5E),
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
                    listOf(Color(0xFF802D43), Color(0x00252A34))
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
            tint = if (pressed) Color(0xFFFFB2C1) else Light
        )
    }
}
