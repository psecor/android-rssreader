package net.secorp.rssreader.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A 3D "constellation" head — surface points joined to their nearest
 * neighbours, dense where there is facial detail and sparse across the skull
 * — swaying slowly between profile and three-quarter view. Used as the
 * "all caught up" empty-state illustration.
 *
 * It is also a toy: drag sideways to spin it. A fling keeps it turning with
 * friction, then it eases back into the idle sway ([HeadSpin]). The gesture
 * only locks on once the finger moves horizontally past touch slop, so a
 * straight pull-down still reaches the list's pull-to-refresh.
 *
 * Geometry is procedural ([HeadModel]): a sculpted profile of horizontal
 * cross-sections with Gaussian bumps for brow, eye sockets, nose, lips, chin
 * and ears. Points are rejection-sampled over the surface with probability
 * driven by local detail, then each is linked to its k nearest neighbours.
 * Everything is built once; per frame we rotate, project and bucket segments
 * by depth × detail into a handful of reused [Path]s, so the draw is a dozen
 * calls regardless of point count.
 */
@Composable
fun WireframeHead(
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    dotColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val spin = remember { HeadSpin() }
    // Only the draw phase reads these, so a frame tick redraws the Canvas
    // without recomposing anything.
    var yaw by remember { mutableFloatStateOf(spin.yaw) }
    var pitch by remember { mutableFloatStateOf(spin.pitch) }

    LaunchedEffect(spin) {
        val startNanos = withFrameNanos { it }
        var lastNanos = startNanos
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
                lastNanos = now
                spin.step(nowMs = (now - startNanos) / 1_000_000, dtSec = dt)
                yaw = spin.yaw
                pitch = spin.pitch
            }
        }
    }

    val model = remember { HeadModel }
    val scratch = remember { RenderScratch(model.pointCount) }

    Canvas(
        modifier = modifier.pointerInput(spin) {
            val radPerPx = DRAG_RAD_PER_DP / density
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                // Horizontal slop only: a vertical pull belongs to pull-to-refresh.
                val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    spin.dragStart()
                    spin.dragBy(overSlop * radPerPx, 0f)
                } ?: return@awaitEachGesture
                var lastMoveAt = first.uptimeMillis
                drag(first.id) { change ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val delta = change.positionChange()
                    spin.dragBy(delta.x * radPerPx, delta.y * radPerPx)
                    lastMoveAt = change.uptimeMillis
                }
                // A pause before letting go means "put it down", not "fling it".
                val upAt = currentEvent.changes.firstOrNull()?.uptimeMillis ?: lastMoveAt
                if (upAt - lastMoveAt > 100) {
                    spin.release(0f, 0f)
                } else {
                    val v = tracker.calculateVelocity()
                    spin.release(v.x * radPerPx, v.y * radPerPx)
                }
            }
        },
    ) {
        val scale = size.minDimension * 0.34f
        val cx = size.width / 2f
        val cy = size.height / 2f - scale * 0.10f

        // Soft halo so the figure reads as lit on either theme.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(lineColor.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(cx, cy),
                radius = scale * 1.6f,
            ),
            radius = scale * 1.6f,
            center = Offset(cx, cy),
        )

        model.project(yaw, pitch, scale, cx, cy, scratch)

        val stroke = Stroke(width = 1.dp.toPx())
        for (depth in 0 until DEPTH_BANDS) {
            val depthAlpha = depthAlpha(depth)
            for (detail in 0 until DETAIL_BANDS) {
                val alpha = depthAlpha * (0.35f + 0.65f * (detail + 0.5f) / DETAIL_BANDS)
                drawPath(
                    path = scratch.linePaths[depth * DETAIL_BANDS + detail],
                    color = lineColor.copy(alpha = alpha),
                    style = stroke,
                )
            }
            drawPoints(
                points = scratch.dotBands[depth],
                pointMode = PointMode.Points,
                color = dotColor.copy(alpha = depthAlpha * 0.95f),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Neurons: coloured nodes with a soft glow, and their connections
        // tinted to match so they read as pathways through the grey mesh.
        val neuronStroke = Stroke(width = 1.3.dp.toPx())
        for (depth in 0 until DEPTH_BANDS) {
            val depthAlpha = depthAlpha(depth)
            for (colour in 0 until NEURON_COLOURS.size) {
                val slot = colour * DEPTH_BANDS + depth
                val tint = NEURON_COLOURS[colour]
                drawPath(
                    path = scratch.neuronPaths[slot],
                    color = tint.copy(alpha = depthAlpha * 0.85f),
                    style = neuronStroke,
                )
                val dots = scratch.neuronDots[slot]
                drawPoints(
                    points = dots,
                    pointMode = PointMode.Points,
                    color = tint.copy(alpha = depthAlpha * 0.28f),
                    strokeWidth = 9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawPoints(
                    points = dots,
                    pointMode = PointMode.Points,
                    color = tint.copy(alpha = depthAlpha),
                    strokeWidth = 3.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private const val DEPTH_BANDS = 6
private const val DETAIL_BANDS = 3

/** Red / green / blue "neuron" tints; theme-independent on purpose. */
private val NEURON_COLOURS = listOf(
    Color(0xFFFF5F6D), // red
    Color(0xFF5BE39A), // green
    Color(0xFF5FAEFF), // blue
)

/** Depth t in [0 back, 1 front] → alpha. Back faces stay as a faint ghost. */
private fun depthAlpha(band: Int): Float {
    val t = (band + 0.5f) / DEPTH_BANDS
    return 0.08f + 0.92f * t.pow(2.0f)
}

/** Radians of rotation per dp of finger travel (matches the web client's per-CSS-px rate). */
private const val DRAG_RAD_PER_DP = 0.012f

/**
 * Orientation state machine for the head, mirroring the web client's
 * `WireframeHead.tsx`. Pure Kotlin so it can be unit-tested; the composable
 * feeds it frame ticks and gesture deltas. Angles in radians, time in ms
 * since the head appeared.
 */
internal class HeadSpin {
    enum class Mode {
        /** Idle sway between profile and three-quarter view. */
        AUTO,
        /** Following the finger. */
        DRAG,
        /** Spinning down under friction after release. */
        COAST,
        /** Easing back into the sway. */
        RETURN,
    }

    var mode: Mode = Mode.AUTO
        private set
    var yaw: Float = autoYaw(0L)
        private set
    var pitch: Float = autoPitch(0L)
        private set

    private var vYaw = 0f
    private var vPitch = 0f
    private var nowMs = 0L
    private var releasedAt = 0L
    private var trackYaw = 0f
    private var trackPitch = 0f

    fun step(nowMs: Long, dtSec: Float) {
        this.nowMs = nowMs
        when (mode) {
            Mode.AUTO -> {
                yaw = autoYaw(nowMs)
                pitch = autoPitch(nowMs)
            }
            Mode.DRAG -> Unit
            Mode.COAST -> {
                yaw += vYaw * dtSec
                pitch = clampPitch(pitch + vPitch * dtSec)
                val decay = exp(-FRICTION * dtSec)
                vYaw *= decay
                vPitch *= decay
                if (nowMs - releasedAt > IDLE_BEFORE_RETURN_MS && abs(vYaw) < 0.05f) {
                    mode = Mode.RETURN
                    trackYaw = autoYaw(nowMs)
                    trackPitch = autoPitch(nowMs)
                }
            }
            Mode.RETURN -> {
                // Ride along with the sway, then close the remaining gap
                // exponentially. Chasing the moving target alone would leave a
                // standing lag and never satisfy the exit test below.
                val targetYaw = autoYaw(nowMs)
                val targetPitch = autoPitch(nowMs)
                yaw += targetYaw - trackYaw
                pitch += targetPitch - trackPitch
                trackYaw = targetYaw
                trackPitch = targetPitch
                val k = 1f - exp(-RETURN_RATE * dtSec)
                val dYaw = wrapAngle(targetYaw - yaw)
                val dPitch = targetPitch - pitch
                yaw += dYaw * k
                pitch += dPitch * k
                if (abs(dYaw) < 0.003f && abs(dPitch) < 0.003f) mode = Mode.AUTO
            }
        }
    }

    fun dragStart() {
        mode = Mode.DRAG
        vYaw = 0f
        vPitch = 0f
    }

    fun dragBy(dYaw: Float, dPitch: Float) {
        yaw += dYaw
        pitch = clampPitch(pitch + dPitch)
    }

    /** Velocities in rad/s; zero to set the head down where it is. */
    fun release(vYaw: Float, vPitch: Float) {
        this.vYaw = clampSpin(vYaw)
        this.vPitch = clampSpin(vPitch)
        releasedAt = nowMs
        mode = Mode.COAST
    }

    companion object {
        const val PITCH_LIMIT = 0.7f
        private const val MAX_SPIN = 12f // rad/s
        private const val FRICTION = 2.5f // per second
        private const val RETURN_RATE = 1.8f // per second
        private const val IDLE_BEFORE_RETURN_MS = 1500L

        /** Sway between full profile (nose pointing left) and three-quarter view. */
        fun autoYaw(tMs: Long): Float = -1.62f + 0.8f * sway(tMs, 9_000)
        fun autoPitch(tMs: Long): Float = -0.06f + 0.12f * sway(tMs, 6_500)

        /** Ping-pong 0→1→0 over 2×period with an ease-in-out. */
        private fun sway(tMs: Long, periodMs: Int): Float {
            val phase = (tMs % (2 * periodMs)).toFloat() / periodMs
            val tri = if (phase <= 1f) phase else 2f - phase
            return 0.5f - 0.5f * cos(PI.toFloat() * tri)
        }

        private fun wrapAngle(a: Float): Float {
            val twoPi = (2 * PI).toFloat()
            return a - twoPi * (a / twoPi).roundToInt()
        }

        private fun clampPitch(p: Float) = p.coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        private fun clampSpin(v: Float) = v.coerceIn(-MAX_SPIN, MAX_SPIN)
    }
}

internal class RenderScratch(pointCount: Int) {
    val x = FloatArray(pointCount)
    val y = FloatArray(pointCount)
    val z = FloatArray(pointCount)
    val linePaths = Array(DEPTH_BANDS * DETAIL_BANDS) { Path() }
    val dotBands = Array(DEPTH_BANDS) { ArrayList<Offset>(pointCount) }
    /** Indexed by colour * DEPTH_BANDS + depth. */
    val neuronPaths = Array(NEURON_COLOURS.size * DEPTH_BANDS) { Path() }
    val neuronDots = Array(NEURON_COLOURS.size * DEPTH_BANDS) { ArrayList<Offset>(64) }
}

/** Internal (not private) so a JVM unit test can force initialisation. */
internal object HeadModel {
    private const val POINT_COUNT = 2200
    private const val NEIGHBOURS = 4
    private const val MAX_EDGE = 0.40f
    private const val Y_TOP = 0.995f
    private const val Y_BOTTOM = -1.18f
    private const val TWO_PI = (2 * PI).toFloat()

    /** (y, halfWidth, frontDepth, backDepth) from crown to base of neck. */
    private val PROFILE = arrayOf(
        floatArrayOf(1.00f, 0.10f, 0.10f, 0.12f),
        floatArrayOf(0.93f, 0.40f, 0.42f, 0.48f),
        floatArrayOf(0.80f, 0.62f, 0.64f, 0.74f),
        floatArrayOf(0.60f, 0.74f, 0.76f, 0.88f),
        floatArrayOf(0.35f, 0.79f, 0.80f, 0.94f),
        floatArrayOf(0.10f, 0.80f, 0.80f, 0.92f),
        floatArrayOf(-0.12f, 0.78f, 0.74f, 0.84f),
        floatArrayOf(-0.35f, 0.75f, 0.74f, 0.70f),
        floatArrayOf(-0.55f, 0.66f, 0.72f, 0.52f),
        floatArrayOf(-0.72f, 0.54f, 0.70f, 0.40f),
        floatArrayOf(-0.86f, 0.42f, 0.62f, 0.34f),
        floatArrayOf(-0.98f, 0.35f, 0.38f, 0.34f),
        floatArrayOf(-1.18f, 0.38f, 0.40f, 0.40f),
    )

    /**
     * Front-facing bumps: (amplitude, thetaCenter, thetaSigma, yCenter, ySigma).
     * theta 0 = straight ahead; applied to radius, weighted by cos(theta).
     */
    private val FRONT_FEATURES = arrayOf(
        floatArrayOf(0.08f, 0.00f, 0.12f, -0.30f, 0.14f), // nose bridge
        floatArrayOf(0.17f, 0.00f, 0.22f, -0.47f, 0.09f), // nose tip
        floatArrayOf(-0.09f, 0.40f, 0.18f, -0.16f, 0.08f), // eye socket
        floatArrayOf(-0.09f, -0.40f, 0.18f, -0.16f, 0.08f), // eye socket
        floatArrayOf(0.05f, 0.00f, 0.70f, -0.03f, 0.06f), // brow ridge
        floatArrayOf(0.05f, 0.80f, 0.30f, -0.32f, 0.12f), // cheekbone
        floatArrayOf(0.05f, -0.80f, 0.30f, -0.32f, 0.12f), // cheekbone
        floatArrayOf(0.06f, 0.00f, 0.32f, -0.64f, 0.05f), // lips
        floatArrayOf(-0.04f, 0.00f, 0.26f, -0.73f, 0.04f), // under-lip
        floatArrayOf(0.08f, 0.00f, 0.30f, -0.84f, 0.07f), // chin
    )

    /** Lateral bumps (ears), applied to width, weighted by |sin(theta)|. */
    private val SIDE_FEATURES = arrayOf(
        floatArrayOf(0.10f, 1.55f, 0.20f, -0.12f, 0.20f),
        floatArrayOf(0.10f, -1.55f, 0.20f, -0.12f, 0.20f),
    )

    /** Eye clusters: density only, no geometry. */
    private val EYE_L = floatArrayOf(0.10f, 0.40f, 0.14f, -0.14f, 0.05f)
    private val EYE_R = floatArrayOf(0.10f, -0.40f, 0.14f, -0.14f, 0.05f)

    // NOTE: everything the init block touches must be declared above it —
    // object properties initialise in declaration order.
    private val profileScratch = FloatArray(3)

    val pointCount: Int
    private val mx: FloatArray
    private val my: FloatArray
    private val mz: FloatArray
    /** Per-point detail score in [0, 1]; brightens edges around features. */
    private val detail: FloatArray
    /** Per-point neuron colour index into NEURON_COLOURS, or -1 for a plain node. */
    private val neuron: IntArray
    private val edgeA: IntArray
    private val edgeB: IntArray

    init {
        val rng = Random(7)
        val xs = FloatArray(POINT_COUNT)
        val ys = FloatArray(POINT_COUNT)
        val zs = FloatArray(POINT_COUNT)
        val ds = FloatArray(POINT_COUNT)
        var n = 0
        var tries = 0
        while (n < POINT_COUNT && tries < POINT_COUNT * 200) {
            tries++
            val y = Y_BOTTOM + (Y_TOP - Y_BOTTOM) * rng.nextFloat()
            val theta = -PI.toFloat() + TWO_PI * rng.nextFloat()
            val d = detailAt(theta, y)
            // Dense near features, sparse over the skull and back.
            var w = 0.14f + min(1f, d / 0.10f) * 0.86f
            if (y > 0.3f) w *= 0.7f
            if (rng.nextFloat() < w) {
                surfacePoint(theta, y, xs, ys, zs, n)
                ds[n] = min(1f, d / 0.20f)
                n++
            }
        }
        pointCount = n
        mx = xs.copyOf(n); my = ys.copyOf(n); mz = zs.copyOf(n); detail = ds.copyOf(n)

        // A sparse scatter of neurons, denser up in the skull than on the face.
        neuron = IntArray(n) { i ->
            val p = if (my[i] > -0.1f) 0.07f else 0.02f
            if (rng.nextFloat() < p) rng.nextInt(NEURON_COLOURS.size) else -1
        }

        val (a, b) = nearestNeighbourEdges()
        edgeA = a; edgeB = b
    }

    val neuronCount: Int get() = neuron.count { it >= 0 }

    val edgeCount: Int get() = edgeA.size

    private fun gaussian(f: FloatArray, theta: Float, y: Float): Float {
        val dt = (theta - f[1]) / f[2]
        val dy = (y - f[3]) / f[4]
        return f[0] * exp(-0.5f * (dt * dt + dy * dy))
    }

    private fun profileAt(y: Float, out: FloatArray) {
        for (i in 0 until PROFILE.size - 1) {
            val a = PROFILE[i]
            val b = PROFILE[i + 1]
            if (y <= a[0] && y >= b[0]) {
                var t = (a[0] - y) / (a[0] - b[0])
                t = t * t * (3f - 2f * t) // smoothstep: rounder transitions
                out[0] = a[1] + (b[1] - a[1]) * t
                out[1] = a[2] + (b[2] - a[2]) * t
                out[2] = a[3] + (b[3] - a[3]) * t
                return
            }
        }
        val last = PROFILE.last()
        out[0] = last[1]; out[1] = last[2]; out[2] = last[3]
    }

    private fun surfacePoint(theta: Float, y: Float, xs: FloatArray, ys: FloatArray, zs: FloatArray, i: Int) {
        profileAt(y, profileScratch)
        val w = profileScratch[0]
        val f = profileScratch[1]
        val b = profileScratch[2]
        val c = cos(theta)
        val s = sin(theta)
        var radial = 1f
        if (c > 0f) {
            var bump = 0f
            for (feat in FRONT_FEATURES) bump += gaussian(feat, theta, y)
            radial += bump * c
        }
        var side = 0f
        for (feat in SIDE_FEATURES) side += gaussian(feat, theta, y)
        xs[i] = w * s * (1f + side * abs(s)) * radial
        ys[i] = y
        zs[i] = (if (c >= 0f) f else b) * c * radial
    }

    /** How much facial detail lives at (theta, y): drives sampling density and brightness. */
    private fun detailAt(theta: Float, y: Float): Float {
        val c = cos(theta)
        var d = 0f
        if (c > 0f) {
            for (feat in FRONT_FEATURES) d += abs(gaussian(feat, theta, y)) * c
            // Eye clusters: density only, no geometry.
            d += gaussian(EYE_L, theta, y) * c
            d += gaussian(EYE_R, theta, y) * c
        }
        var side = 0f
        for (feat in SIDE_FEATURES) side += gaussian(feat, theta, y)
        d += side * abs(sin(theta))
        // Profile silhouette down the front midline, and the neck lines.
        if (abs(theta) < 0.10f && y > -0.98f && y < 0.45f) d += 0.12f
        if (y < -0.95f && abs(sin(theta)) < 0.25f) d += 0.06f
        return d
    }

    /**
     * Link each point to its [NEIGHBOURS] nearest (within [MAX_EDGE]) using a
     * uniform grid so the one-time build stays a few milliseconds.
     */
    private fun nearestNeighbourEdges(): Pair<IntArray, IntArray> {
        val cell = MAX_EDGE
        val minX = mx.min(); val minY = my.min(); val minZ = mz.min()
        val nx = floor((mx.max() - minX) / cell).toInt() + 1
        val ny = floor((my.max() - minY) / cell).toInt() + 1
        val nz = floor((mz.max() - minZ) / cell).toInt() + 1
        val grid = Array(nx * ny * nz) { ArrayList<Int>(8) }
        fun cellIndex(i: Int): Int {
            val gx = ((mx[i] - minX) / cell).toInt()
            val gy = ((my[i] - minY) / cell).toInt()
            val gz = ((mz[i] - minZ) / cell).toInt()
            return (gz * ny + gy) * nx + gx
        }
        for (i in 0 until pointCount) grid[cellIndex(i)].add(i)

        val edges = HashSet<Long>(pointCount * NEIGHBOURS)
        val bestD = FloatArray(NEIGHBOURS)
        val bestJ = IntArray(NEIGHBOURS)
        val maxD2 = MAX_EDGE * MAX_EDGE
        for (i in 0 until pointCount) {
            bestD.fill(Float.MAX_VALUE); bestJ.fill(-1)
            val gx = ((mx[i] - minX) / cell).toInt()
            val gy = ((my[i] - minY) / cell).toInt()
            val gz = ((mz[i] - minZ) / cell).toInt()
            for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) {
                val cx = gx + dx; val cy = gy + dy; val cz = gz + dz
                if (cx !in 0 until nx || cy !in 0 until ny || cz !in 0 until nz) continue
                for (j in grid[(cz * ny + cy) * nx + cx]) {
                    if (j == i) continue
                    val ddx = mx[i] - mx[j]; val ddy = my[i] - my[j]; val ddz = mz[i] - mz[j]
                    val d2 = ddx * ddx + ddy * ddy + ddz * ddz
                    if (d2 > maxD2 || d2 >= bestD[NEIGHBOURS - 1]) continue
                    // insertion into the small sorted best-list
                    var k = NEIGHBOURS - 1
                    while (k > 0 && bestD[k - 1] > d2) {
                        bestD[k] = bestD[k - 1]; bestJ[k] = bestJ[k - 1]; k--
                    }
                    bestD[k] = d2; bestJ[k] = j
                }
            }
            for (k in 0 until NEIGHBOURS) {
                val j = bestJ[k]
                if (j < 0) continue
                val lo = min(i, j); val hi = max(i, j)
                edges.add(lo.toLong() shl 32 or hi.toLong())
            }
        }
        val a = IntArray(edges.size)
        val b = IntArray(edges.size)
        var e = 0
        for (key in edges) {
            a[e] = (key shr 32).toInt()
            b[e] = (key and 0xffffffffL).toInt()
            e++
        }
        return a to b
    }

    /**
     * Rotate (yaw about Y, then pitch about X), perspective-project into screen
     * space, then bucket every edge by depth × detail and every point by depth.
     */
    fun project(yaw: Float, pitch: Float, scale: Float, cx: Float, cy: Float, out: RenderScratch) {
        val cosY = cos(yaw); val sinY = sin(yaw)
        val cosP = cos(pitch); val sinP = sin(pitch)
        val camera = 4f
        for (i in 0 until pointCount) {
            val x0 = mx[i]; val y0 = my[i]; val z0 = mz[i]
            val x1 = x0 * cosY + z0 * sinY
            val z1 = -x0 * sinY + z0 * cosY
            val y2 = y0 * cosP - z1 * sinP
            val z2 = y0 * sinP + z1 * cosP
            val k = camera / (camera - z2)
            out.x[i] = cx + x1 * k * scale
            out.y[i] = cy - y2 * k * scale
            out.z[i] = z2
        }
        for (p in out.linePaths) p.rewind()
        for (p in out.neuronPaths) p.rewind()
        for (d in out.dotBands) d.clear()
        for (d in out.neuronDots) d.clear()
        for (i in 0 until pointCount) {
            val depth = depthBand(out.z[i])
            val colour = neuron[i]
            val target = if (colour >= 0) out.neuronDots[colour * DEPTH_BANDS + depth] else out.dotBands[depth]
            target.add(Offset(out.x[i], out.y[i]))
        }
        for (e in edgeA.indices) {
            val a = edgeA[e]; val b = edgeB[e]
            val depth = depthBand((out.z[a] + out.z[b]) * 0.5f)
            val colour = if (neuron[a] >= 0) neuron[a] else neuron[b]
            val path = if (colour >= 0) {
                out.neuronPaths[colour * DEPTH_BANDS + depth]
            } else {
                val det = (((detail[a] + detail[b]) * 0.5f).coerceIn(0f, 0.999f) * DETAIL_BANDS).toInt()
                out.linePaths[depth * DETAIL_BANDS + det]
            }
            path.moveTo(out.x[a], out.y[a])
            path.lineTo(out.x[b], out.y[b])
        }
    }

    /** Model-space z runs roughly -1..1; map to a band index, 0 = furthest back. */
    private fun depthBand(z: Float): Int {
        val t = ((z + 1f) * 0.5f).coerceIn(0f, 0.999f)
        return (t * DEPTH_BANDS).toInt()
    }
}
