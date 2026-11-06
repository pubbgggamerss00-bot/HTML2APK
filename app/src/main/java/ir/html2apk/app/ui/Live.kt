package ir.html2apk.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.html2apk.app.ui.theme.AuroraEmerald
import ir.html2apk.app.ui.theme.AuroraLavender
import ir.html2apk.app.ui.theme.AuroraSky
import ir.html2apk.app.ui.theme.SurfaceDim
import kotlin.math.cos
import kotlin.math.sin

/**
 * «تم زنده» — the live background of the app.
 *
 * Three soft aurora blobs (emerald / sky / lavender) drift slowly over the deep
 * navy base on independent sine cycles, so the surface is never static but never
 * distracting either. Drawn on a single Canvas — one invalidation per frame,
 * no allocations in the draw path.
 */
@Composable
fun LiveAuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val p1 by transition.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(17000, easing = LinearEasing)), label = "p1",
    )
    val p2 by transition.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(23000, easing = LinearEasing)), label = "p2",
    )
    val p3 by transition.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(29000, easing = LinearEasing)), label = "p3",
    )

    Canvas(modifier) {
        drawRect(SurfaceDim)
        val w = size.width
        val h = size.height

        fun blob(cx: Float, cy: Float, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        // emerald blob — upper area
        blob(
            cx = w * (0.80f + 0.14f * cos(p1)),
            cy = h * (0.16f + 0.09f * sin(p1 * 1.3f)),
            radius = w * 0.75f,
            color = AuroraEmerald.copy(alpha = 0.10f),
        )
        // sky blob — middle-left
        blob(
            cx = w * (0.16f + 0.13f * cos(p2 + 1.2f)),
            cy = h * (0.38f + 0.11f * sin(p2)),
            radius = w * 0.72f,
            color = AuroraSky.copy(alpha = 0.08f),
        )
        // lavender blob — bottom
        blob(
            cx = w * (0.52f + 0.18f * cos(p3)),
            cy = h * (0.88f + 0.06f * sin(p3 + 2.1f)),
            radius = w * 0.82f,
            color = AuroraLavender.copy(alpha = 0.07f),
        )
    }
}

/**
 * A slowly breathing glow dot — the "alive" status indicator.
 * Used next to the build status and on the active tab badge.
 */
@Composable
fun PulsingDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseT",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // expanding halo
        Box(
            modifier = Modifier
                .size(size)
                .scale(1f + 1.1f * t)
                .alpha((1f - t) * 0.55f)
                .background(color, CircleShape),
        )
        // solid core
        Box(
            modifier = Modifier
                .size(size * 0.52f)
                .background(color, CircleShape),
        )
    }
}

/**
 * Soft breathing glow behind an active element (tab pill, success badge).
 * The alpha oscillates gently so the UI keeps feeling alive without noise.
 */
@Composable
fun BreathingGlow(color: Color, modifier: Modifier = Modifier, maxAlpha: Float = 0.16f) {
    val transition = rememberInfiniteTransition(label = "breath")
    val t by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathT",
    )
    val alpha = maxAlpha * (0.55f + 0.45f * t)
    Box(
        modifier
            .background(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent))),
    )
}

/** Status color of the build lifecycle, shared by status pill and tab badge. */
@Composable
fun statusColor(running: Boolean, failed: Boolean, succeeded: Boolean): Color = when {
    running -> MaterialTheme.colorScheme.secondary
    failed -> MaterialTheme.colorScheme.error
    succeeded -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.primary
}
