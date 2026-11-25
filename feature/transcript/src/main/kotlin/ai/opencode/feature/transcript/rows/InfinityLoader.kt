package ai.opencode.feature.transcript.rows

import ai.opencode.core.motion.rememberReduceMotion
import ai.opencode.core.ui.OpenCodeTheme
import ai.opencode.feature.transcript.list.LocalTranscriptTimelinePaused
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * The app's infinity mark as a loader: the logo's own shape, faint, with a light that travels
 * along its curve. It takes [color] (black or white by theme) and holds still, fully drawn, under
 * Reduce Motion, when [animate] is false or while the transcript's timelines are paused.
 */
@Composable
fun InfinityLoader(
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    color: Color = OpenCodeTheme.colors.text,
    animate: Boolean = true,
) {
    val moving = animate && !rememberReduceMotion() && !LocalTranscriptTimelinePaused.current
    val outline = remember { InfinityShape.outlinePath() }
    val point = remember { FloatArray(2) }
    // Only a moving loader runs a clock; a still one draws once.
    val phase = if (moving) {
        rememberInfiniteTransition(label = "InfinityLoader").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2000, easing = LinearEasing)),
            label = "InfinityLoaderPhase",
        )
    } else {
        null
    }
    Canvas(modifier.size(width, width * InfinityShape.ASPECT)) {
        val w = size.width
        if (!moving) {
            withTransform({ scale(w, w, Offset.Zero) }) { drawPath(outline, color.copy(alpha = 0.55f)) }
            return@Canvas
        }
        withTransform({ scale(w, w, Offset.Zero) }) { drawPath(outline, color.copy(alpha = 0.22f)) }
        val head = phase?.value ?: 0f
        // The light and its tail, kept inside the logo's own shape.
        withTransform({
            scale(w, w, Offset.Zero)
            clipPath(outline)
        }) {
            for (k in TAIL - 1 downTo 0) {
                val s = (((head - k * TAIL_STEP) % 1f) + 1f) % 1f
                InfinityShape.pointAt(s, point)
                val falloff = 1f - k.toFloat() / TAIL
                val edge = (minOf(s, 1f - s) / EDGE).coerceIn(0f, 1f)
                val alpha = falloff * falloff * edge * edge * (3f - 2f * edge)
                drawCircle(color.copy(alpha = alpha), radius = GLOW_RADIUS, center = Offset(point[0], point[1]))
            }
        }
    }
}

private const val TAIL = 16
private const val TAIL_STEP = 0.012f
private const val GLOW_RADIUS = 0.05f
private const val EDGE = 0.06f

/** The logo traced from `ic_launcher_art.png`, in units of its own width: outline and centre line. */
private object InfinityShape {
    /** Height over width. */
    const val ASPECT = 0.5927f

    private val OUTLINE = floatArrayOf(
        0.981f, 0.124f, 0.949f, 0.073f, 0.914f, 0.040f, 0.867f, 0.014f, 0.839f, 0.005f,
        0.806f, 0.000f, 0.778f, 0.000f, 0.743f, 0.005f, 0.684f, 0.026f, 0.617f, 0.068f,
        0.561f, 0.119f, 0.519f, 0.166f, 0.470f, 0.233f, 0.516f, 0.185f, 0.580f, 0.133f,
        0.647f, 0.094f, 0.712f, 0.072f, 0.741f, 0.066f, 0.788f, 0.066f, 0.820f, 0.073f,
        0.864f, 0.096f, 0.897f, 0.131f, 0.909f, 0.154f, 0.920f, 0.199f, 0.916f, 0.241f,
        0.899f, 0.285f, 0.883f, 0.308f, 0.858f, 0.332f, 0.834f, 0.350f, 0.811f, 0.362f,
        0.781f, 0.372f, 0.741f, 0.379f, 0.680f, 0.374f, 0.621f, 0.355f, 0.563f, 0.327f,
        0.434f, 0.252f, 0.381f, 0.226f, 0.341f, 0.210f, 0.278f, 0.196f, 0.229f, 0.194f,
        0.185f, 0.201f, 0.154f, 0.212f, 0.119f, 0.229f, 0.079f, 0.259f, 0.051f, 0.288f,
        0.024f, 0.329f, 0.009f, 0.367f, 0.000f, 0.411f, 0.002f, 0.453f, 0.012f, 0.491f,
        0.024f, 0.516f, 0.045f, 0.544f, 0.079f, 0.570f, 0.100f, 0.580f, 0.129f, 0.589f,
        0.185f, 0.593f, 0.241f, 0.582f, 0.302f, 0.556f, 0.348f, 0.524f, 0.386f, 0.488f,
        0.420f, 0.448f, 0.453f, 0.397f, 0.402f, 0.446f, 0.341f, 0.493f, 0.274f, 0.531f,
        0.227f, 0.549f, 0.199f, 0.554f, 0.154f, 0.554f, 0.135f, 0.551f, 0.094f, 0.531f,
        0.070f, 0.507f, 0.056f, 0.481f, 0.049f, 0.455f, 0.051f, 0.413f, 0.068f, 0.367f,
        0.086f, 0.343f, 0.122f, 0.309f, 0.159f, 0.290f, 0.194f, 0.280f, 0.219f, 0.276f,
        0.273f, 0.280f, 0.341f, 0.301f, 0.416f, 0.337f, 0.540f, 0.411f, 0.586f, 0.434f,
        0.642f, 0.455f, 0.699f, 0.467f, 0.743f, 0.469f, 0.792f, 0.462f, 0.827f, 0.451f,
        0.874f, 0.428f, 0.913f, 0.400f, 0.946f, 0.365f, 0.977f, 0.315f, 0.993f, 0.271f,
        1.000f, 0.227f, 0.997f, 0.175f,
    )

    /** The ribbon's centre line, from one tip around both loops to the other. */
    private val CENTER = floatArrayOf(
        0.473f, 0.228f, 0.498f, 0.199f, 0.524f, 0.171f, 0.551f, 0.144f, 0.580f, 0.119f,
        0.611f, 0.096f, 0.643f, 0.076f, 0.677f, 0.059f, 0.713f, 0.046f, 0.751f, 0.039f,
        0.789f, 0.035f, 0.827f, 0.040f, 0.863f, 0.053f, 0.895f, 0.074f, 0.921f, 0.102f,
        0.941f, 0.135f, 0.952f, 0.171f, 0.956f, 0.209f, 0.953f, 0.247f, 0.943f, 0.284f,
        0.926f, 0.318f, 0.903f, 0.348f, 0.874f, 0.374f, 0.842f, 0.394f, 0.807f, 0.409f,
        0.769f, 0.417f, 0.731f, 0.421f, 0.693f, 0.418f, 0.655f, 0.410f, 0.619f, 0.399f,
        0.584f, 0.384f, 0.550f, 0.367f, 0.516f, 0.348f, 0.483f, 0.329f, 0.450f, 0.309f,
        0.417f, 0.291f, 0.382f, 0.274f, 0.347f, 0.258f, 0.311f, 0.247f, 0.273f, 0.239f,
        0.235f, 0.238f, 0.197f, 0.242f, 0.160f, 0.252f, 0.126f, 0.269f, 0.094f, 0.291f,
        0.068f, 0.318f, 0.046f, 0.350f, 0.032f, 0.385f, 0.026f, 0.423f, 0.029f, 0.461f,
        0.041f, 0.497f, 0.062f, 0.528f, 0.092f, 0.553f, 0.127f, 0.567f, 0.165f, 0.572f,
        0.203f, 0.570f, 0.241f, 0.562f, 0.276f, 0.548f, 0.310f, 0.530f, 0.342f, 0.509f,
        0.371f, 0.485f, 0.399f, 0.458f, 0.425f, 0.430f, 0.450f, 0.401f,
    )

    private val cumulative: FloatArray = FloatArray(CENTER.size / 2).also { c ->
        for (i in 1 until c.size) {
            val dx = CENTER[i * 2] - CENTER[(i - 1) * 2]
            val dy = CENTER[i * 2 + 1] - CENTER[(i - 1) * 2 + 1]
            c[i] = c[i - 1] + sqrt(dx * dx + dy * dy)
        }
    }
    private val total: Float = cumulative[cumulative.size - 1]

    fun outlinePath(): Path {
        val n = OUTLINE.size / 2
        fun x(i: Int) = OUTLINE[(i % n) * 2]
        fun y(i: Int) = OUTLINE[(i % n) * 2 + 1]
        val path = Path()
        path.moveTo((x(0) + x(1)) / 2f, (y(0) + y(1)) / 2f)
        for (i in 1..n) {
            path.quadraticTo(x(i), y(i), (x(i) + x(i + 1)) / 2f, (y(i) + y(i + 1)) / 2f)
        }
        path.close()
        return path
    }

    /** The point [fraction] (0 to 1) of the way along the centre line, written to [out]. */
    fun pointAt(fraction: Float, out: FloatArray) {
        val target = fraction.coerceIn(0f, 1f) * total
        var i = 1
        while (i < cumulative.size - 1 && cumulative[i] < target) i++
        val span = cumulative[i] - cumulative[i - 1]
        val f = if (span <= 0f) 0f else ((target - cumulative[i - 1]) / span).coerceIn(0f, 1f)
        out[0] = CENTER[(i - 1) * 2] + (CENTER[i * 2] - CENTER[(i - 1) * 2]) * f
        out[1] = CENTER[(i - 1) * 2 + 1] + (CENTER[i * 2 + 1] - CENTER[(i - 1) * 2 + 1]) * f
    }
}
