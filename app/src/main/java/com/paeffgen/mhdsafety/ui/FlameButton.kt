package com.paeffgen.mhdsafety.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Ember = Color(0xFFB71C1C)
private val Flame = Color(0xFFE65100)
private val Glow = Color(0xFFFFA000)

/** Knopf mit glühendem Hintergrund und flackernden Flammen links und rechts. */
@Composable
fun FlameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flammen")
    // Jede Flamme in eigenem Takt, damit sie nicht im Gleichschritt hüpfen.
    val flickers = listOf(330, 410, 370, 290).mapIndexed { i, ms ->
        transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "flamme$i",
        )
    }
    val sway = transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wiegen",
    )
    val glow = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "glut",
    )

    val shape = RoundedCornerShape(50)
    val shift = glow.value * 240f
    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Ember, Flame, Glow, Flame, Ember),
                    start = Offset(shift, 0f),
                    end = Offset(shift + 360f, 120f),
                    tileMode = TileMode.Mirror,
                ),
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FlickeringFlame(flickers[0], sway, mirror = false)
            FlickeringFlame(flickers[1], sway, mirror = true)
            Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            FlickeringFlame(flickers[2], sway, mirror = false)
            FlickeringFlame(flickers[3], sway, mirror = true)
        }
    }
}

@Composable
private fun FlickeringFlame(flicker: State<Float>, sway: State<Float>, mirror: Boolean) {
    Text(
        "🔥",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.graphicsLayer {
            // Von unten her flackern, wie eine echte Flamme.
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleY = flicker.value
            scaleX = 2f - flicker.value * 0.9f
            rotationZ = if (mirror) -sway.value else sway.value
            alpha = 0.75f + (flicker.value - 0.82f) * 0.7f
        },
    )
}
