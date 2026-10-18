package com.shovel.filemanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

// Timeline in milliseconds.
private const val IMPACT_MS = 380f
private const val FADE_START_MS = 940f
private const val FADE_MS = 200f
private const val TOTAL_MS = FADE_START_MS + FADE_MS

private val SplashBackground = Color(0xFF0B1625)
private val GroundColor = Color(0xFF6D4B35)
private val GroundLightColor = Color(0xFFA8794F)
private val SoilColor = Color(0xFF4B3427)

private class DirtParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val travelX: Float,
    val rise: Float,
    val fall: Float,
    val fadeStartMs: Float,
)

private val dirtParticles = listOf(
    DirtParticle(43f, 75f, 4f, GroundLightColor, -22f, -17f, 4f, 720f),
    DirtParticle(62f, 75f, 4f, GroundLightColor, 22f, -19f, 5f, 720f),
    DirtParticle(48f, 77f, 3f, GroundColor, -12f, -23f, 3f, 700f),
    DirtParticle(57f, 77f, 3f, GroundColor, 13f, -21f, 3f, 700f),
)

private val shovelParts = listOf(
    Color(0xFF9B6238) to "M46,14 C46,11.8 47.8,10 50,10 H58 C60.2,10 62,11.8 62,14 V18 C62,20.2 60.2,22 58,22 H50 C47.8,22 46,20.2 46,18 Z",
    Color(0xFF754626) to "M54,10 H58 C60.2,10 62,11.8 62,14 V18 C62,20.2 60.2,22 58,22 H54 Z",
    Color(0xFF9B6238) to "M51,20 H57 V55 H51 Z",
    Color(0xFF754626) to "M54,20 H57 V55 H54 Z",
    Color(0xFF5DD6C0) to "M47,51 H61 L68,67 C70,72 68,75 64,78 L54,86 L44,78 C40,75 38,72 40,67 Z",
    Color(0xFF32A997) to "M54,51 H61 L68,67 C70,72 68,75 64,78 L54,86 Z",
)

/**
 * Start-up animation drawn inside the app: a shovel drops from the top of the screen, sticks into the
 * ground, wobbles once and a little dirt pops out, then the whole thing fades away.
 * Android's own splash screen stays blank in the same colour, so the two blend together.
 */
@Composable
fun StartupSplash(onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val shapes = remember { shovelParts.map { (color, data) -> color to PathParser().parsePathString(data).toPath() } }
    LaunchedEffect(Unit) {
        clock.animateTo(TOTAL_MS, tween(durationMillis = TOTAL_MS.toInt(), easing = LinearEasing))
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - clamp01((clock.value - FADE_START_MS) / FADE_MS) }
            .background(SplashBackground)
            // Taps must not reach the app while it is still being covered.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { change -> change.consume() }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawScene(clock.value, shapes) }
    }
}

private fun DrawScope.drawScene(ms: Float, shapes: List<Pair<Color, Path>>) {
    // Everything below is drawn in "art units": the artwork is 96 units wide.
    val unit = min(size.width, size.height * 0.6f) / 96f
    val left = size.width / 2f - 54f * unit
    val top = size.height / 2f - 52f * unit
    // How far above the artwork's resting place the shovel must start to be completely off screen.
    val startOffset = (52f - size.height / 2f / unit) - 90f

    val translateY = if (ms >= IMPACT_MS) 0f else lerp(startOffset, 0f, accelerate(ms / IMPACT_MS))
    val sinceImpact = ms - IMPACT_MS
    val rotation = when {
        sinceImpact <= 0f -> 0f
        sinceImpact < 110f -> -6f * decelerate(sinceImpact / 110f)
        sinceImpact < 240f -> -6f + 10f * accelerateDecelerate((sinceImpact - 110f) / 130f)
        sinceImpact < 350f -> 4f - 4f * decelerate((sinceImpact - 240f) / 110f)
        else -> 0f
    }
    val dip = if (sinceImpact > 0f) clamp01(sinceImpact / 80f) else 0f

    withTransform({
        translate(left, top)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        withTransform({
            translate(0f, translateY)
            rotate(rotation, pivot = Offset(54f, 86f))
        }) {
            shapes.forEach { (color, path) -> drawPath(path, color) }
        }

        // The ground parts around the blade once the shovel has landed.
        val notch = 79f + 5f * dip
        val lightNotch = 82f + 5f * dip
        val ground = Path().apply {
            moveTo(12f, 79f)
            lineTo(38f, 79f)
            lineTo(43f, notch)
            lineTo(65f, notch)
            lineTo(70f, 79f)
            lineTo(96f, 79f)
            lineTo(96f, 94f)
            lineTo(12f, 94f)
            close()
        }
        val light = Path().apply {
            moveTo(12f, 79f)
            lineTo(38f, 79f)
            lineTo(43f, notch)
            lineTo(65f, notch)
            lineTo(70f, 79f)
            lineTo(96f, 79f)
            lineTo(96f, 82f)
            lineTo(71f, 82f)
            lineTo(66f, lightNotch)
            lineTo(42f, lightNotch)
            lineTo(37f, 82f)
            lineTo(12f, 82f)
            close()
        }
        drawPath(ground, GroundColor)
        drawPath(light, GroundLightColor)
        drawRect(SoilColor, topLeft = Offset(12f, 85f), size = Size(23f, 2f))
        drawRect(SoilColor, topLeft = Offset(73f, 85f), size = Size(23f, 2f))

        if (sinceImpact > 0f) {
            dirtParticles.forEach { particle ->
                val dx = particle.travelX * decelerate(clamp01(sinceImpact / 520f))
                val dy = if (sinceImpact < 220f) {
                    particle.rise * decelerate(sinceImpact / 220f)
                } else {
                    particle.rise + (particle.fall - particle.rise) * accelerate(clamp01((sinceImpact - 220f) / 300f))
                }
                var alpha = clamp01(sinceImpact / 20f)
                if (ms > particle.fadeStartMs) alpha = 1f - clamp01((ms - particle.fadeStartMs) / 180f)
                if (alpha > 0f) {
                    drawRect(
                        color = particle.color.copy(alpha = alpha),
                        topLeft = Offset(particle.x + dx, particle.y + dy),
                        size = Size(particle.size, particle.size),
                    )
                }
            }
        }
    }
}

private fun accelerate(t: Float): Float = t * t

private fun decelerate(t: Float): Float = 1f - (1f - t) * (1f - t)

private fun accelerateDecelerate(t: Float): Float = (0.5 - 0.5 * cos(PI * t)).toFloat()

private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
