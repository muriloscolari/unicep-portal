package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.platform.PredictiveBackState

val LocalPredictiveBackProgress = staticCompositionLocalOf<() -> PredictiveBackState> {
    { PredictiveBackState() }
}

class BackOverlayController {
    private val handlers = mutableStateListOf<() -> Unit>()
    val hasOverlay: Boolean get() = handlers.isNotEmpty()

    fun push(onBack: () -> Unit) {
        handlers.add(onBack)
    }

    fun remove(onBack: () -> Unit) {
        handlers.remove(onBack)
    }

    fun popTop(): Boolean {
        val top = handlers.lastOrNull() ?: return false
        top.invoke()
        return true
    }
}

val LocalBackOverlayController = staticCompositionLocalOf<BackOverlayController?> { null }

@Composable
fun RegisterBackOverlay(enabled: Boolean = true, onBack: () -> Unit) {
    val controller = LocalBackOverlayController.current ?: return
    val currentOnBack = rememberUpdatedState(onBack)
    DisposableEffect(enabled, controller) {
        if (!enabled) return@DisposableEffect onDispose {}
        val callback: () -> Unit = { currentOnBack.value() }
        controller.push(callback)
        onDispose { controller.remove(callback) }
    }
}

private class BoundaryHapticTracker(
    private val canScrollBackward: () -> Boolean,
    private val canScrollForward: () -> Boolean
) : NestedScrollConnection {
    private var prevCanBackward = canScrollBackward()
    private var prevCanForward = canScrollForward()
    private var lastTriggerMs = 0L

    private fun triggerBoundary(intensity: Float) {
        val now = PlatformBridge.currentTimeMillis()
        if (now - lastTriggerMs < 150L) return
        lastTriggerMs = now
        PlatformBridge.performHaptic(
            type = PortalHapticType.BOUNDARY_THUD,
            intensity = intensity.coerceIn(0.35f, 1f)
        )
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        prevCanBackward = canScrollBackward()
        prevCanForward = canScrollForward()
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val nowBackward = canScrollBackward()
        val nowForward = canScrollForward()

        // Bateu no topo durante a rolagem / inércia
        if (prevCanBackward && !nowBackward) {
            val strength = ((abs(consumed.y) + abs(available.y)) / 90f).coerceIn(0.42f, 1f)
            triggerBoundary(strength)
        } else if (!nowBackward && available.y > 14f && source == NestedScrollSource.SideEffect) {
            val strength = (available.y / 90f).coerceIn(0.45f, 1f)
            triggerBoundary(strength)
        }

        // Bateu no fim da página durante a rolagem / inércia
        if (prevCanForward && !nowForward) {
            val strength = ((abs(consumed.y) + abs(available.y)) / 90f).coerceIn(0.42f, 1f)
            triggerBoundary(strength)
        } else if (!nowForward && available.y < -14f && source == NestedScrollSource.SideEffect) {
            val strength = (abs(available.y) / 90f).coerceIn(0.45f, 1f)
            triggerBoundary(strength)
        }

        prevCanBackward = nowBackward
        prevCanForward = nowForward
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val vy = available.y
        if (vy > 220f && !canScrollBackward()) {
            triggerBoundary((vy / 3200f).coerceIn(0.45f, 1f))
        } else if (vy < -220f && !canScrollForward()) {
            triggerBoundary((abs(vy) / 3200f).coerceIn(0.45f, 1f))
        }
        return Velocity.Zero
    }
}

fun Modifier.appleScrollBoundaryHaptics(listState: LazyListState): Modifier = composed {
    val connection = remember(listState) {
        BoundaryHapticTracker(
            canScrollBackward = { listState.canScrollBackward },
            canScrollForward = { listState.canScrollForward }
        )
    }
    nestedScroll(connection)
}

fun Modifier.appleScrollBoundaryHaptics(scrollState: ScrollState): Modifier = composed {
    val connection = remember(scrollState) {
        BoundaryHapticTracker(
            canScrollBackward = { scrollState.canScrollBackward },
            canScrollForward = { scrollState.canScrollForward }
        )
    }
    nestedScroll(connection)
}

fun Modifier.predictiveBackSurfaceTransform(
    readBack: () -> PredictiveBackState,
    slideDp: Float = 0f,
    verticalSlideDp: Float = 110f,
    scaleDrop: Float = 0.05f,
    pivotY: Float = 1f
): Modifier = graphicsLayer {
    val back = readBack()
    if (!back.active || back.progress <= 0.001f) return@graphicsLayer
    val p = back.progress.coerceIn(0f, 1f)
    val dir = if (back.swipeEdge == 1) -1f else 1f
    val s = (1f - scaleDrop * p).coerceIn(0.85f, 1f)
    transformOrigin = TransformOrigin(0.5f, pivotY)
    scaleX = s
    scaleY = s
    translationX = if (slideDp != 0f) dir * slideDp.dp.toPx() * p else 0f
    translationY = if (verticalSlideDp != 0f) verticalSlideDp.dp.toPx() * p else 0f
}

fun Modifier.predictiveBackScrimAlpha(
    readBack: () -> PredictiveBackState
): Modifier = graphicsLayer {
    val back = readBack()
    if (!back.active || back.progress <= 0.001f) return@graphicsLayer
    alpha = (1f - back.progress.coerceIn(0f, 1f) * 0.65f).coerceIn(0.2f, 1f)
}
