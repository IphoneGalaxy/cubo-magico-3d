package com.example.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.curriculum.HighlightMode
import com.example.data.AppLanguage
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TurquoisePrimary
import com.example.ui.theme.VioletAccent
import kotlin.math.hypot

/**
 * Estados explícitos da máquina de estados de arbitragem de gestos 3D.
 * Depois que um gesto sai de [PENDING_GESTURE] para [LAYER_ROTATION], [CAMERA_ORBIT] ou [PINCH_ZOOM],
 * sua responsabilidade jamais muda até o término completo do toque ([IDLE]).
 */
enum class GestureInteractionState {
    IDLE,
    PENDING_GESTURE,
    LAYER_ROTATION,
    CAMERA_ORBIT,
    PINCH_ZOOM,
    ANIMATING
}

/**
 * Modo de controle tátil selecionável pelo usuário para eliminar ambiguidades.
 * - [MOVE_LAYERS]: Gestos de 1 dedo manipulam EXCLUSIVAMENTE as camadas do cubo (a câmera fica 100% travada).
 * - [ROTATE_CAMERA]: Gestos de 1 dedo orbitam EXCLUSIVAMENTE a câmera sem mover peças.
 */
enum class TouchInteractionMode(
    val labelPt: String,
    val labelEn: String
) {
    MOVE_LAYERS("Mover camadas", "Move layers"),
    ROTATE_CAMERA("Girar câmera", "Rotate camera");

    fun localizedLabel(language: AppLanguage): String =
        if (language == AppLanguage.EN_US) labelEn else labelPt
}

/**
 * Árbitro determinístico de gestos 3D que separa estritamente:
 * 1) Giro de camada ([GestureInteractionState.LAYER_ROTATION])
 * 2) Órbita de câmera ([GestureInteractionState.CAMERA_ORBIT])
 * 3) Zoom por pinça ([GestureInteractionState.PINCH_ZOOM])
 *
 * Garante matematicamente que:
 * - No modo [TouchInteractionMode.MOVE_LAYERS], gestos de 1 dedo JAMAIS alteram cameraYaw ou cameraPitch,
 *   mesmo se o toque iniciar próximo à borda externa da silhueta do cubo.
 * - No modo [TouchInteractionMode.ROTATE_CAMERA], gestos de 1 dedo apenas orbitam a câmera e param
 *   imediatamente ao soltar o dedo (sem inércia contínua).
 */
class CubeGestureArbiter(
    private val renderer: CubeGLRenderer,
    var allowFaceSwipeProvider: () -> Boolean = { true },
    var isAnimatingProvider: () -> Boolean = { false },
    var touchModeProvider: () -> TouchInteractionMode = { TouchInteractionMode.MOVE_LAYERS },
    var gesturesEnabledProvider: () -> Boolean = { true },
    var onLayerMoveResolved: (CubeMove) -> Unit = {},
    var onCameraUpdated: () -> Unit = {}
) {
    var state: GestureInteractionState = GestureInteractionState.IDLE
        private set

    var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
        private set

    var activePointerCount: Int = 0
        private set

    var screenDensity: Float = 1f

    var downX: Float = 0f
        private set
    var downY: Float = 0f
        private set
    var lastX: Float = 0f
        private set
    var lastY: Float = 0f
        private set
    var initialHit: CubeTouchHit? = null
        private set
    var lockedToLayerIntent: Boolean = false
        private set
    var moveDispatchedInGesture: Boolean = false
        private set

    var transitionedFromMultiTouch: Boolean = false
        private set

    var twistPointerId1: Int = MotionEvent.INVALID_POINTER_ID
        private set
    var twistPointerId2: Int = MotionEvent.INVALID_POINTER_ID
        private set
    var twistAnchorValid: Boolean = false
        private set
    var initialTwistAngleDeg: Float = 0f
        private set
    var lastTwistAngleDeg: Float = 0f
        private set
    var lastTwistSpanPx: Float = 0f
        private set
    var accumulatedTwistFromAnchorDeg: Float = 0f
        private set
    var twistInProgress: Boolean = false
        private set

    val orbitTouchSlopPx: Float
        get() = 8f * screenDensity.coerceAtLeast(1f)

    val layerSwipeSlopPx: Float
        get() = 18f * screenDensity.coerceAtLeast(1f)

    val pinchTouchSlopPx: Float
        get() = 6f * screenDensity.coerceAtLeast(1f)

    val twistTouchSlopDeg: Float = 1.2f

    val minTwistFrameDeltaDeg: Float = 0.08f

    val minTwistSpanPx: Float
        get() = 24f * screenDensity.coerceAtLeast(1f)

    val maxSingleFrameSpanJumpForTwistPx: Float
        get() = kotlin.math.max(75f, 32f * screenDensity.coerceAtLeast(1f))

    fun clearTwoFingerTwistAnchor() {
        twistPointerId1 = MotionEvent.INVALID_POINTER_ID
        twistPointerId2 = MotionEvent.INVALID_POINTER_ID
        twistAnchorValid = false
        initialTwistAngleDeg = 0f
        lastTwistAngleDeg = 0f
        lastTwistSpanPx = 0f
        accumulatedTwistFromAnchorDeg = 0f
        twistInProgress = false
    }

    fun anchorTwoFingerTwist(
        pointerId1: Int,
        x1: Float,
        y1: Float,
        pointerId2: Int,
        x2: Float,
        y2: Float
    ) {
        if (pointerId1 == pointerId2 ||
            pointerId1 == MotionEvent.INVALID_POINTER_ID ||
            pointerId2 == MotionEvent.INVALID_POINTER_ID
        ) {
            clearTwoFingerTwistAnchor()
            return
        }
        val span = hypot(x2 - x1, y2 - y1)
        if (span < minTwistSpanPx) {
            clearTwoFingerTwistAnchor()
            return
        }
        val angle = CubeGLRenderer.computeTwoFingerAngleDegrees(x1, y1, x2, y2)
        twistPointerId1 = pointerId1
        twistPointerId2 = pointerId2
        initialTwistAngleDeg = angle
        lastTwistAngleDeg = angle
        lastTwistSpanPx = span
        accumulatedTwistFromAnchorDeg = 0f
        twistInProgress = false
        twistAnchorValid = true
    }

    fun onTwoFingerTwist(deltaAngleDegrees: Float) {
        if (!gesturesEnabledProvider()) return
        if (activePointerCount == 1 && transitionedFromMultiTouch) {
            return
        }
        if (state != GestureInteractionState.PINCH_ZOOM) {
            state = GestureInteractionState.PINCH_ZOOM
            initialHit = null
            lockedToLayerIntent = false
            renderer.previewTouchHit = null
            renderer.previewTouchMove = null
        }
        if (!deltaAngleDegrees.isFinite() || kotlin.math.abs(deltaAngleDegrees) < 1e-4f) {
            return
        }
        renderer.rollCamera(deltaAngleDegrees)
        onCameraUpdated()
    }

    fun updateTwoFingerTwist(
        pointerId1: Int,
        x1: Float,
        y1: Float,
        pointerId2: Int,
        x2: Float,
        y2: Float
    ) {
        if (!gesturesEnabledProvider()) {
            clearTwoFingerTwistAnchor()
            return
        }
        if (activePointerCount != 2 || (activePointerCount == 1 && transitionedFromMultiTouch)) {
            clearTwoFingerTwistAnchor()
            return
        }
        val span = hypot(x2 - x1, y2 - y1)
        if (span < minTwistSpanPx) {
            clearTwoFingerTwistAnchor()
            return
        }
        if (!twistAnchorValid ||
            pointerId1 != twistPointerId1 ||
            pointerId2 != twistPointerId2 ||
            kotlin.math.abs(span - lastTwistSpanPx) > maxSingleFrameSpanJumpForTwistPx
        ) {
            anchorTwoFingerTwist(pointerId1, x1, y1, pointerId2, x2, y2)
            return
        }
        lastTwistSpanPx = span
        val currentAngle = CubeGLRenderer.computeTwoFingerAngleDegrees(x1, y1, x2, y2)

        if (!twistInProgress) {
            val rawDelta = CubeGLRenderer.normalizeAngleDeltaDegrees(currentAngle - lastTwistAngleDeg)
            lastTwistAngleDeg = currentAngle
            accumulatedTwistFromAnchorDeg += rawDelta
            if (kotlin.math.abs(accumulatedTwistFromAnchorDeg) >= twistTouchSlopDeg) {
                twistInProgress = true
                onTwoFingerTwist(accumulatedTwistFromAnchorDeg)
            }
        } else {
            val rawDelta = CubeGLRenderer.normalizeAngleDeltaDegrees(currentAngle - lastTwistAngleDeg)
            if (kotlin.math.abs(rawDelta) >= minTwistFrameDeltaDeg) {
                lastTwistAngleDeg = currentAngle
                onTwoFingerTwist(rawDelta)
            }
        }
    }

    fun onDown(x: Float, y: Float, pointerId: Int = 0) {
        if (!gesturesEnabledProvider()) {
            state = GestureInteractionState.IDLE
            activePointerId = MotionEvent.INVALID_POINTER_ID
            activePointerCount = 0
            initialHit = null
            lockedToLayerIntent = false
            moveDispatchedInGesture = false
            return
        }
        renderer.onUserTouchStart()
        activePointerId = pointerId
        activePointerCount = 1
        transitionedFromMultiTouch = false
        clearTwoFingerTwistAnchor()
        downX = x
        downY = y
        lastX = x
        lastY = y
        moveDispatchedInGesture = false

        val canManipulateLayers = allowFaceSwipeProvider() &&
            touchModeProvider() == TouchInteractionMode.MOVE_LAYERS

        if (canManipulateLayers) {
            // No modo Camadas, trava estritamente a intenção em manipulação de camadas:
            // um gesto de 1 dedo JAMAIS cairá no fallback de girar a câmera!
            lockedToLayerIntent = true
            val hit = renderer.pickCubieAtScreen(x, y, allowEdgePadding = true)
            initialHit = hit
            renderer.previewTouchHit = hit
            renderer.previewTouchMove = null
            onCameraUpdated()
        } else {
            lockedToLayerIntent = false
            initialHit = null
            renderer.previewTouchHit = null
            renderer.previewTouchMove = null
        }

        state = if (isAnimatingProvider() && lockedToLayerIntent) {
            GestureInteractionState.ANIMATING
        } else {
            GestureInteractionState.PENDING_GESTURE
        }
    }

    fun onPointerDown(
        pointerCount: Int,
        activeX: Float = lastX,
        activeY: Float = lastY,
        primaryPointerId: Int = activePointerId
    ) {
        if (!gesturesEnabledProvider()) {
            state = GestureInteractionState.IDLE
            return
        }
        renderer.onUserTouchStart()
        activePointerCount = pointerCount.coerceAtLeast(2)
        activePointerId = primaryPointerId
        lastX = activeX
        lastY = activeY
        if (pointerCount != 2) {
            clearTwoFingerTwistAnchor()
        }
        if (pointerCount >= 2) {
            // Dois ou mais dedos: prioriza imediatamente o gesto multitoque (pinça + torção) e cancela qualquer giro pendente ainda não confirmado.
            // Nota: se um movimento válido já havia sido enviado ao ViewModel (moveDispatchedInGesture == true),
            // sua animação prossegue normalmente sem ser desfeita, enquanto novos giros ficam bloqueados durante o multitoque.
            state = GestureInteractionState.PINCH_ZOOM
            initialHit = null
            lockedToLayerIntent = false
            renderer.previewTouchHit = null
            renderer.previewTouchMove = null
            onCameraUpdated()
        }
    }

    fun updateMultiTouchReference(activeX: Float, activeY: Float) {
        lastX = activeX
        lastY = activeY
    }

    fun onPinchScale(scaleFactor: Float) {
        if (!gesturesEnabledProvider()) return
        // Impede que callbacks residuais de escala reativem PINCH_ZOOM quando apenas 1 dedo permaneceu na tela
        if (activePointerCount == 1 && transitionedFromMultiTouch) {
            return
        }
        if (state != GestureInteractionState.PINCH_ZOOM) {
            state = GestureInteractionState.PINCH_ZOOM
            initialHit = null
            lockedToLayerIntent = false
            renderer.previewTouchHit = null
            renderer.previewTouchMove = null
        }
        if (scaleFactor > 0.01f && scaleFactor != 1.0f) {
            val clampedFactor = scaleFactor.coerceIn(0.45f, 2.25f)
            renderer.cameraDistance = (renderer.cameraDistance / clampedFactor)
                .coerceIn(CubeGLRenderer.MIN_DISTANCE, CubeGLRenderer.MAX_DISTANCE)
            synchronized(this) {
                renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 0f))
            }
            onCameraUpdated()
        }
    }

    /**
     * Chamado em ACTION_POINTER_UP quando ainda restam 2 ou mais dedos na tela (ex.: 3 dedos -> 2 dedos).
     * Mantém o estado em [GestureInteractionState.PINCH_ZOOM] e atualiza o ponteiro de referência sem saltos.
     */
    fun onMultiPointersRemaining(
        remainingPointerCount: Int,
        survivingX: Float,
        survivingY: Float,
        survivingPointerId: Int
    ) {
        renderer.onUserTouchStart()
        activePointerCount = remainingPointerCount.coerceAtLeast(2)
        activePointerId = survivingPointerId
        downX = survivingX
        downY = survivingY
        lastX = survivingX
        lastY = survivingY
        state = GestureInteractionState.PINCH_ZOOM
        initialHit = null
        lockedToLayerIntent = false
        renderer.previewTouchHit = null
        renderer.previewTouchMove = null
        clearTwoFingerTwistAnchor()
    }

    /**
     * Chamado em ACTION_POINTER_UP quando resta exatamente 1 dedo na tela após uma interação multitoque.
     * Encerra a interpretação de pinça/torção, atualiza as coordenadas de referência para o ponteiro sobrevivente,
     * limpa seleções antigas e prepara uma nova classificação de gesto de 1 dedo respeitando o touch slop.
     */
    fun onTransitionToSinglePointer(
        remainingX: Float,
        remainingY: Float,
        remainingPointerId: Int = activePointerId
    ) {
        // Mantém o estado de toque ativo para que a Home não retome a rotação automática enquanto o dedo permanece na tela
        renderer.onUserTouchStart()
        activePointerCount = 1
        activePointerId = remainingPointerId
        transitionedFromMultiTouch = true
        clearTwoFingerTwistAnchor()

        // Zera deslocamentos acumulados da pinça e ancora no dedo sobrevivente
        downX = remainingX
        downY = remainingY
        lastX = remainingX
        lastY = remainingY

        // Elimina qualquer seleção antiga de peça/camada e permite exatamente 1 novo movimento se houver novo arraste intencional
        initialHit = null
        moveDispatchedInGesture = false
        renderer.previewTouchHit = null
        renderer.previewTouchMove = null

        val canManipulateLayers = allowFaceSwipeProvider() &&
            touchModeProvider() == TouchInteractionMode.MOVE_LAYERS
        lockedToLayerIntent = canManipulateLayers

        state = if (isAnimatingProvider() && lockedToLayerIntent) {
            GestureInteractionState.ANIMATING
        } else {
            GestureInteractionState.PENDING_GESTURE
        }
        onCameraUpdated()
    }

    /**
     * Ressincroniza com segurança caso o ID do ponteiro ativo mude inesperadamente durante um evento de movimento,
     * evitando saltos bruscos de câmera ou cálculos de deslocamento inválidos.
     */
    fun onPointerIdRebound(newX: Float, newY: Float, newPointerId: Int) {
        activePointerId = newPointerId
        clearTwoFingerTwistAnchor()
        downX = newX
        downY = newY
        lastX = newX
        lastY = newY
        initialHit = null
        renderer.previewTouchHit = null
        renderer.previewTouchMove = null
    }

    fun onMove(x: Float, y: Float) {
        if (!gesturesEnabledProvider()) return
        // Se o gesto estiver em PINCH_ZOOM (2+ dedos) ou IDLE (sem toque), ignora movimentos de 1 dedo
        if (state == GestureInteractionState.PINCH_ZOOM || state == GestureInteractionState.IDLE) {
            lastX = x
            lastY = y
            return
        }

        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y

        // Se ainda não confirmou rotação de camada ou órbita, sincroniza a intenção com o modo atual
        if (state == GestureInteractionState.PENDING_GESTURE || state == GestureInteractionState.ANIMATING) {
            lockedToLayerIntent = allowFaceSwipeProvider() &&
                touchModeProvider() == TouchInteractionMode.MOVE_LAYERS
        }

        // CASO 1: Modo Mover Camadas -> NUNCA move a câmera!
        if (lockedToLayerIntent) {
            if (isAnimatingProvider()) {
                state = GestureInteractionState.ANIMATING
                if (transitionedFromMultiTouch) {
                    // Se uma animação disparada antes da pinça ainda está terminando, mantém a origem atualizada
                    // no dedo restante para impedir que resíduos da pinça disparem outro giro ao fim da animação.
                    downX = x
                    downY = y
                    initialHit = null
                }
                return
            }

            if (state == GestureInteractionState.ANIMATING) {
                state = GestureInteractionState.PENDING_GESTURE
            }

            val totalDxFromDown = x - downX
            val totalDyFromDown = y - downY
            val distFromDown = hypot(totalDxFromDown, totalDyFromDown)

            if (initialHit == null) {
                if (transitionedFromMultiTouch) {
                    // Após retirar o 2º dedo, só realiza raycasting quando o dedo restante realmente iniciar um deslocamento
                    if (distFromDown < 2f) {
                        return
                    }
                    val hitAtRemainingOrigin = renderer.pickCubieAtScreen(downX, downY, allowEdgePadding = true)
                    if (hitAtRemainingOrigin != null) {
                        initialHit = hitAtRemainingOrigin
                        renderer.previewTouchHit = hitAtRemainingOrigin
                        onCameraUpdated()
                    } else {
                        val enteredHit = renderer.pickCubieAtScreen(x, y, allowEdgePadding = true)
                        if (enteredHit != null) {
                            initialHit = enteredHit
                            downX = x
                            downY = y
                            renderer.previewTouchHit = enteredHit
                            onCameraUpdated()
                        }
                    }
                } else {
                    // Toque de 1 dedo iniciado fora da silhueta do cubo que entrou na malha durante o arraste:
                    // ancora downX/downY no ponto de entrada na peça para exigir o layerSwipeSlopPx sobre a peça.
                    val enteredHit = renderer.pickCubieAtScreen(x, y, allowEdgePadding = true)
                    if (enteredHit != null) {
                        initialHit = enteredHit
                        downX = x
                        downY = y
                        renderer.previewTouchHit = enteredHit
                        onCameraUpdated()
                    }
                }
            }

            val hit = initialHit ?: return
            val totalDx = x - downX
            val totalDy = y - downY
            val totalDist = hypot(totalDx, totalDy)

            if (totalDist >= layerSwipeSlopPx * 0.45f && !moveDispatchedInGesture) {
                val previewMove = renderer.resolveMoveFromSwipe(hit, totalDx, totalDy)
                if (previewMove != renderer.previewTouchMove) {
                    renderer.previewTouchMove = previewMove
                    onCameraUpdated()
                }
            }

            if (totalDist >= layerSwipeSlopPx) {
                state = GestureInteractionState.LAYER_ROTATION
                if (!moveDispatchedInGesture) {
                    val move = renderer.resolveMoveFromSwipe(hit, totalDx, totalDy)
                    if (move != null) {
                        moveDispatchedInGesture = true
                        renderer.previewTouchMove = move
                        onLayerMoveResolved(move)
                    }
                }
            }
            return
        }

        // CASO 2: Modo Girar Câmera (ou tela onde allowFaceSwipeMoves == false) -> Apenas orbita a câmera!
        val totalDx = x - downX
        val totalDy = y - downY
        val totalDist = hypot(totalDx, totalDy)

        if (state == GestureInteractionState.PENDING_GESTURE || state == GestureInteractionState.ANIMATING) {
            if (totalDist >= orbitTouchSlopPx) {
                state = GestureInteractionState.CAMERA_ORBIT
            } else {
                return
            }
        }

        if (state == GestureInteractionState.CAMERA_ORBIT) {
            renderer.orbitCamera(dx, dy)
            onCameraUpdated()
        }
    }

    fun onUpOrCancel() {
        if (gesturesEnabledProvider()) {
            renderer.onUserTouchEnd()
        }
        val hadPreview = renderer.previewTouchHit != null || renderer.previewTouchMove != null
        state = GestureInteractionState.IDLE
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerCount = 0
        transitionedFromMultiTouch = false
        clearTwoFingerTwistAnchor()
        initialHit = null
        lockedToLayerIntent = false
        moveDispatchedInGesture = false
        renderer.previewTouchHit = null
        renderer.previewTouchMove = null
        if (hadPreview) {
            onCameraUpdated()
        }
    }
}

/**
 * Componente Compose reutilizável do Cubo Mágico 3D Interativo.
 */
@Composable
fun InteractiveCube3DView(
    cubeState: CubeState,
    activeAnimation: ActiveLayerAnimation?,
    modifier: Modifier = Modifier,
    guidedMove: CubeMove? = null,
    highlightMode: HighlightMode = HighlightMode.ALL_VIVID,
    highlightEnabled: Boolean = false,
    isDarkTheme: Boolean = true,
    autoRotateIdle: Boolean = false,
    allowFaceSwipeMoves: Boolean = true,
    gesturesEnabled: Boolean = true,
    transparentBackground: Boolean = false,
    initialCameraDistance: Float = CubeGLRenderer.DEFAULT_DISTANCE,
    celebrationPulse: Float = 0f,
    cameraResetTrigger: Int = 0,
    showCameraControlsOverlay: Boolean = true,
    showActiveMoveBanner: Boolean = true,
    onMoveSwiped: (CubeMove) -> Unit = {}
) {
    val strings = CuboMestreTheme.strings
    val isRobolectric = remember {
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    val renderer = remember { CubeGLRenderer() }
    var glSurfaceViewRef by remember { mutableStateOf<CubeTouchGLSurfaceView?>(null) }
    var localResetCount by remember { mutableIntStateOf(0) }
    var touchInteractionMode by remember { mutableStateOf(TouchInteractionMode.MOVE_LAYERS) }

    val currentAllowFaceSwipe by rememberUpdatedState(allowFaceSwipeMoves)
    val currentGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val currentIsAnimating by rememberUpdatedState(activeAnimation != null)
    val currentTouchMode by rememberUpdatedState(touchInteractionMode)
    val currentOnMoveSwiped by rememberUpdatedState(onMoveSwiped)

    // Sincronização atômica única por recomposição (evita duplicidade entre SideEffect e AndroidView.update),
    // garantindo que todos os quadros intermediários (0%..100%) de activeAnimation sejam desenhados sem contenção de lock.
    SideEffect {
        renderer.syncSceneState(
            newCubeState = cubeState,
            newAnimation = activeAnimation,
            newGuidedMove = guidedMove,
            newHighlightMode = highlightMode,
            newHighlightEnabled = highlightEnabled,
            newIsDarkBackground = isDarkTheme,
            newAutoRotateIdle = autoRotateIdle && isLifecycleResumed,
            newCelebrationPulse = celebrationPulse,
            newTransparentBackground = transparentBackground,
            newInitialCameraDistance = initialCameraDistance
        )
        Cube3DViewAuditHolder.lastActiveRenderer = renderer
        glSurfaceViewRef?.let { glView ->
            glView.gesturesEnabledProvider = { currentGesturesEnabled }
            glView.arbiter.gesturesEnabledProvider = { currentGesturesEnabled }
            val desiredMode = if (isLifecycleResumed && (autoRotateIdle || activeAnimation != null || celebrationPulse > 0f)) {
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
            glView.updateDesiredRenderMode(desiredMode)
            if (isLifecycleResumed) {
                glView.requestRender()
            }
        }
    }

    LaunchedEffect(cameraResetTrigger, localResetCount) {
        if (cameraResetTrigger > 0 || localResetCount > 0) {
            renderer.resetCamera()
            glSurfaceViewRef?.requestRender()
        }
    }

    Box(
        modifier = modifier
            .let { base ->
                if (transparentBackground) base else base.clip(RoundedCornerShape(22.dp))
            }
            .testTag("cube_3d_viewport")
    ) {
        if (!transparentBackground) {
            // Halo de iluminação de estúdio sutil ao fundo
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.56f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.13f else 0.08f),
                            VioletAccent.copy(alpha = if (isDarkTheme) 0.06f else 0.04f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(10f)
                    ),
                    radius = radius.coerceAtLeast(10f),
                    center = center
                )
            }
        }

        if (!isRobolectric) {
            AndroidView(
                factory = { ctx ->
                    renderer.syncSceneState(
                        newCubeState = cubeState,
                        newAnimation = activeAnimation,
                        newGuidedMove = guidedMove,
                        newHighlightMode = highlightMode,
                        newHighlightEnabled = highlightEnabled,
                        newIsDarkBackground = isDarkTheme,
                        newAutoRotateIdle = autoRotateIdle && isLifecycleResumed,
                        newCelebrationPulse = celebrationPulse,
                        newTransparentBackground = transparentBackground,
                        newInitialCameraDistance = initialCameraDistance
                    )
                    CubeTouchGLSurfaceView(
                        context = ctx,
                        cubeRenderer = renderer,
                        allowFaceSwipeProvider = { currentAllowFaceSwipe },
                        isAnimatingProvider = { currentIsAnimating },
                        touchModeProvider = { currentTouchMode },
                        gesturesEnabledProvider = { currentGesturesEnabled },
                        transparentSurface = transparentBackground,
                        onSwipeMove = { move -> currentOnMoveSwiped(move) }
                    ).also { glView ->
                        val initialDesiredMode = if (autoRotateIdle || activeAnimation != null || celebrationPulse > 0f) {
                            GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        } else {
                            GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        }
                        glView.updateDesiredRenderMode(initialDesiredMode)
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            glView.handleLifecycleResume()
                        }
                        glSurfaceViewRef = glView
                    }
                },
                update = { glView ->
                    glView.gesturesEnabledProvider = { currentGesturesEnabled }
                    glView.arbiter.gesturesEnabledProvider = { currentGesturesEnabled }
                    glView.arbiter.allowFaceSwipeProvider = { currentAllowFaceSwipe }
                    glView.arbiter.isAnimatingProvider = { currentIsAnimating }
                    glView.arbiter.touchModeProvider = { currentTouchMode }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Ambiente de teste local JVM (Robolectric): exibe marcador determinístico sem exigir GPU EGL nativa
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.cubeViewportAccessibilityDesc(cubeState.cubies.size),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        DisposableEffect(lifecycleOwner, glSurfaceViewRef) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                        isLifecycleResumed = true
                        glSurfaceViewRef?.handleLifecycleResume()
                    }
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                        isLifecycleResumed = false
                        renderer.syncSceneState(
                            newCubeState = renderer.cubeState,
                            newAnimation = null,
                            newGuidedMove = renderer.guidedMove,
                            newHighlightMode = renderer.highlightMode,
                            newHighlightEnabled = renderer.highlightEnabled,
                            newIsDarkBackground = renderer.isDarkBackground,
                            newAutoRotateIdle = false,
                            newCelebrationPulse = 0f,
                            newTransparentBackground = renderer.transparentBackground,
                            newInitialCameraDistance = renderer.initialCameraDistance
                        )
                        glSurfaceViewRef?.handleLifecyclePause()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                isLifecycleResumed = true
                glSurfaceViewRef?.handleLifecycleResume()
            }
            onDispose {
                if (Cube3DViewAuditHolder.lastActiveRenderer === renderer) {
                    Cube3DViewAuditHolder.lastActiveRenderer = null
                }
                lifecycleOwner.lifecycle.removeObserver(observer)
                glSurfaceViewRef?.handleLifecyclePause()
            }
        }

        val themeColors = CuboMestreTheme.colors

        // Indicador superior compacto da direção/eixo do movimento em execução (sem encobrir o cubo)
        if (showActiveMoveBanner) {
            val bannerMove = activeAnimation?.move
            AnimatedVisibility(
                visible = bannerMove != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                bannerMove?.let { move ->
                    ActiveMoveIndicatorBanner(move = move)
                }
            }
        }

        // Controles inferiores do viewport 3D: Seletor explícito (Mover camadas | Girar câmera) + Recentralizar câmera
        if (showCameraControlsOverlay && gesturesEnabled) {
            if (allowFaceSwipeMoves) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(50)
                        )
                        .testTag("touch_mode_selector")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(3.dp)
                    ) {
                        val isMoveLayers = touchInteractionMode == TouchInteractionMode.MOVE_LAYERS
                        Surface(
                            color = if (isMoveLayers) themeColors.turquoiseAccent else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { touchInteractionMode = TouchInteractionMode.MOVE_LAYERS }
                                .testTag("touch_mode_layers_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TouchApp,
                                    contentDescription = null,
                                    tint = if (isMoveLayers) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = strings.viewportTouchModeLayers,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMoveLayers) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (!isMoveLayers) themeColors.violetAccent else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { touchInteractionMode = TouchInteractionMode.ROTATE_CAMERA }
                                .testTag("touch_mode_camera_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Explore,
                                    contentDescription = null,
                                    tint = if (!isMoveLayers) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = strings.viewportTouchModeCamera,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isMoveLayers) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Explore,
                            contentDescription = null,
                            tint = themeColors.turquoiseAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = strings.viewportCameraHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    localResetCount++
                    renderer.resetCamera()
                    glSurfaceViewRef?.requestRender()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                    .testTag("recenter_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.CenterFocusStrong,
                    contentDescription = strings.viewportRecenterCameraDescription,
                    tint = themeColors.turquoiseAccent
                )
            }
        }
    }
}

object Cube3DViewAuditHolder {
    @Volatile
    var lastActiveRenderer: CubeGLRenderer? = null
}

@Composable
fun ActiveMoveIndicatorBanner(
    move: CubeMove,
    modifier: Modifier = Modifier
) {
    val strings = CuboMestreTheme.strings
    val themeColors = CuboMestreTheme.colors
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val localizedTitle = move.localizedTitle(strings.language)
    val titleLength = localizedTitle.length
    val effectiveScale = when {
        titleLength >= 22 -> fontScale.coerceAtMost(1.06f)
        titleLength >= 16 -> fontScale.coerceAtMost(1.14f)
        else -> fontScale.coerceAtMost(1.22f)
    }
    val notationFontSize = with(density) { (12.dp.toPx() * effectiveScale).toSp() }
    val titleFontSize = with(density) { (11.dp.toPx() * effectiveScale).toSp() }
    val horizontalPad = if (fontScale > 1.2f || titleLength >= 20) 10.dp else 12.dp

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
        shape = RoundedCornerShape(50),
        tonalElevation = 4.dp,
        modifier = modifier
            .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.65f), RoundedCornerShape(50))
            .testTag("active_move_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = horizontalPad, vertical = 4.dp)
        ) {
            Text(
                text = "${move.notation} ${directionArrowForMove(move)}",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = themeColors.turquoiseAccent,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = notationFontSize,
                    lineHeight = notationFontSize * 1.2f
                ),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = localizedTitle,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = titleFontSize,
                    lineHeight = titleFontSize * 1.2f
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

internal fun directionArrowForMove(move: CubeMove): String = when (move) {
    CubeMove.R, CubeMove.L_PRIME, CubeMove.M_PRIME -> "↑"
    CubeMove.R_PRIME, CubeMove.L, CubeMove.M -> "↓"
    CubeMove.U, CubeMove.D_PRIME, CubeMove.E_PRIME -> "←"
    CubeMove.U_PRIME, CubeMove.D, CubeMove.E -> "→"
    CubeMove.F, CubeMove.B_PRIME, CubeMove.S -> "↻"
    CubeMove.F_PRIME, CubeMove.B, CubeMove.S_PRIME -> "↺"
    else -> "180°"
}

/**
 * GLSurfaceView customizada integrada com o [CubeGestureArbiter] para separação determinística
 * entre rotação de camada, órbita de câmera e zoom por pinça, bloqueando interceptação de scroll pai.
 */
@SuppressLint("ViewConstructor")
class CubeTouchGLSurfaceView(
    context: Context,
    val cubeRenderer: CubeGLRenderer,
    allowFaceSwipeProvider: () -> Boolean = { true },
    isAnimatingProvider: () -> Boolean = { false },
    touchModeProvider: () -> TouchInteractionMode = { TouchInteractionMode.MOVE_LAYERS },
    var gesturesEnabledProvider: () -> Boolean = { true },
    transparentSurface: Boolean = false,
    onSwipeMove: (CubeMove) -> Unit = {}
) : GLSurfaceView(context) {

    private val isRobolectricEnv: Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
        private set

    var activePointerCount: Int = 0
        private set

    private var effectivePointersInCurrentEvent: Int = 0
    private var scaleAppliedInCurrentEvent: Boolean = false
    private var pinchInProgress: Boolean = false
    private var initialPinchSpan: Float = 0f
    private var lastPinchSpan: Float = 0f

    val arbiter = CubeGestureArbiter(
        renderer = cubeRenderer,
        allowFaceSwipeProvider = allowFaceSwipeProvider,
        isAnimatingProvider = isAnimatingProvider,
        touchModeProvider = touchModeProvider,
        gesturesEnabledProvider = gesturesEnabledProvider,
        onLayerMoveResolved = { move ->
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                onSwipeMove(move)
            } else {
                post { onSwipeMove(move) }
            }
        },
        onCameraUpdated = { requestRender() }
    ).apply {
        screenDensity = context.resources.displayMetrics.density.coerceAtLeast(1f)
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!gesturesEnabledProvider() || effectivePointersInCurrentEvent < 2) return false
                if (initialPinchSpan <= 0f) {
                    initialPinchSpan = detector.currentSpan
                }
                arbiter.onPointerDown(
                    pointerCount = effectivePointersInCurrentEvent,
                    activeX = arbiter.lastX,
                    activeY = arbiter.lastY,
                    primaryPointerId = activePointerId
                )
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!gesturesEnabledProvider() || effectivePointersInCurrentEvent < 2) return false
                val refSpan = if (initialPinchSpan > 0f) initialPinchSpan else detector.currentSpan
                if (!pinchInProgress && kotlin.math.abs(detector.currentSpan - refSpan) < arbiter.pinchTouchSlopPx) {
                    return true
                }
                val factor = detector.scaleFactor
                if (kotlin.math.abs(factor - 1.0f) > 0.002f) {
                    scaleAppliedInCurrentEvent = true
                    pinchInProgress = true
                    arbiter.onPinchScale(factor)
                }
                return true
            }
        }
    ).apply {
        // Desativa QuickScale de 1 dedo (duplo toque + arraste) para que 1 dedo seja exclusivo para camadas/câmera
        isQuickScaleEnabled = false
    }

    var isGlSurfaceResumed: Boolean = false
        private set

    var resumeCallCountForAudit: Int = 0
        private set

    var pauseCallCountForAudit: Int = 0
        private set

    var effectiveRenderModeForAudit: Int = RENDERMODE_WHEN_DIRTY
        private set

    private var desiredForegroundRenderMode: Int = RENDERMODE_CONTINUOUSLY

    init {
        if (!isRobolectricEnv) {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            if (transparentSurface) {
                setZOrderOnTop(true)
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            }
            preserveEGLContextOnPause = true
            setRenderer(cubeRenderer)
            renderMode = RENDERMODE_WHEN_DIRTY
        }
    }

    private fun applyRenderModeInternal(mode: Int) {
        effectiveRenderModeForAudit = mode
        if (!isRobolectricEnv && renderMode != mode) {
            renderMode = mode
        }
    }

    fun updateDesiredRenderMode(mode: Int) {
        desiredForegroundRenderMode = mode
        val targetMode = if (isGlSurfaceResumed) mode else RENDERMODE_WHEN_DIRTY
        applyRenderModeInternal(targetMode)
    }

    /**
     * Retoma a GLThread de maneira idempotente, evitando chamadas duplicadas a [onResume]
     * e restaurando o modo de renderização adequado ao primeiro plano.
     */
    fun handleLifecycleResume() {
        if (isGlSurfaceResumed) {
            applyRenderModeInternal(desiredForegroundRenderMode)
            return
        }
        isGlSurfaceResumed = true
        resumeCallCountForAudit++
        if (!isRobolectricEnv) {
            super.onResume()
        }
        applyRenderModeInternal(desiredForegroundRenderMode)
        requestRender()
    }

    /**
     * Pausa a GLThread de maneira idempotente, encerrando gestos ativos, rebaixando
     * [renderMode] para [RENDERMODE_WHEN_DIRTY] e evitando chamadas duplicadas a [onPause].
     */
    fun handleLifecyclePause() {
        cancelActiveTouchInteraction()
        applyRenderModeInternal(RENDERMODE_WHEN_DIRTY)
        if (!isGlSurfaceResumed) return
        isGlSurfaceResumed = false
        pauseCallCountForAudit++
        if (!isRobolectricEnv) {
            super.onPause()
        }
    }

    fun cancelActiveTouchInteraction() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerCount = 0
        effectivePointersInCurrentEvent = 0
        scaleAppliedInCurrentEvent = false
        pinchInProgress = false
        initialPinchSpan = 0f
        lastPinchSpan = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
        arbiter.onUpOrCancel()
    }

    override fun onDetachedFromWindow() {
        handleLifecyclePause()
        super.onDetachedFromWindow()
    }

    override fun requestRender() {
        if (!isRobolectricEnv && isGlSurfaceResumed) {
            super.requestRender()
        }
    }

    private fun computeActiveSpan(event: MotionEvent, ignoreIndex: Int = -1): Float {
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == ignoreIndex) continue
            sumX += event.getX(i)
            sumY += event.getY(i)
            count++
        }
        if (count < 2) return 0f
        val focusX = sumX / count
        val focusY = sumY / count
        var devSum = 0f
        for (i in 0 until event.pointerCount) {
            if (i == ignoreIndex) continue
            devSum += hypot(event.getX(i) - focusX, event.getY(i) - focusY)
        }
        // Multiplicado por 2 para coincidir com a distância euclidiana exata entre 2 ponteiros
        return (devSum / count) * 2f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gesturesEnabledProvider()) {
            return false
        }
        // Impede que qualquer container pai do Compose intercepte o toque para rolar a página
        parent?.requestDisallowInterceptTouchEvent(true)
        arbiter.screenDensity = context.resources.displayMetrics.density.coerceAtLeast(1f)

        val action = event.actionMasked
        val actionIndex = event.actionIndex

        effectivePointersInCurrentEvent = when (action) {
            MotionEvent.ACTION_POINTER_UP -> (event.pointerCount - 1).coerceAtLeast(0)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
            else -> event.pointerCount
        }
        scaleAppliedInCurrentEvent = false
        scaleDetector.onTouchEvent(event)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val pointerId = event.getPointerId(0)
                activePointerId = pointerId
                activePointerCount = 1
                pinchInProgress = false
                initialPinchSpan = 0f
                lastPinchSpan = 0f
                arbiter.onDown(event.getX(0), event.getY(0), pointerId = pointerId)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerCount = event.pointerCount
                if (activePointerId == MotionEvent.INVALID_POINTER_ID || event.findPointerIndex(activePointerId) < 0) {
                    val safeIdx = actionIndex.coerceIn(0, event.pointerCount - 1)
                    activePointerId = event.getPointerId(safeIdx)
                }
                val activeIdx = event.findPointerIndex(activePointerId).coerceAtLeast(0)
                val span = computeActiveSpan(event)
                lastPinchSpan = span
                if (event.pointerCount == 2 || initialPinchSpan <= 0f) {
                    initialPinchSpan = span
                    pinchInProgress = false
                }
                arbiter.onPointerDown(
                    pointerCount = event.pointerCount,
                    activeX = event.getX(activeIdx),
                    activeY = event.getY(activeIdx),
                    primaryPointerId = activePointerId
                )
                if (event.pointerCount == 2) {
                    arbiter.anchorTwoFingerTwist(
                        pointerId1 = event.getPointerId(0),
                        x1 = event.getX(0),
                        y1 = event.getY(0),
                        pointerId2 = event.getPointerId(1),
                        x2 = event.getX(1),
                        y2 = event.getY(1)
                    )
                } else {
                    arbiter.clearTwoFingerTwistAnchor()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                activePointerCount = event.pointerCount
                if (event.pointerCount >= 2) {
                    if (arbiter.state != GestureInteractionState.PINCH_ZOOM) {
                        val activeIdx = event.findPointerIndex(activePointerId).coerceAtLeast(0)
                        arbiter.onPointerDown(
                            pointerCount = event.pointerCount,
                            activeX = event.getX(activeIdx),
                            activeY = event.getY(activeIdx),
                            primaryPointerId = activePointerId
                        )
                    }
                    val currentSpan = computeActiveSpan(event)
                    if (!scaleAppliedInCurrentEvent && currentSpan > 10f && lastPinchSpan > 10f) {
                        if (pinchInProgress || kotlin.math.abs(currentSpan - initialPinchSpan) >= arbiter.pinchTouchSlopPx) {
                            pinchInProgress = true
                            val manualScale = currentSpan / lastPinchSpan
                            if (kotlin.math.abs(manualScale - 1.0f) > 0.002f) {
                                lastPinchSpan = currentSpan
                                arbiter.onPinchScale(manualScale)
                            }
                        } else {
                            lastPinchSpan = currentSpan
                        }
                    } else if (currentSpan > 10f && (scaleAppliedInCurrentEvent || lastPinchSpan <= 10f)) {
                        lastPinchSpan = currentSpan
                    }
                    if (event.pointerCount == 2) {
                        val idx1 = if (arbiter.twistAnchorValid) event.findPointerIndex(arbiter.twistPointerId1) else -1
                        val idx2 = if (arbiter.twistAnchorValid) event.findPointerIndex(arbiter.twistPointerId2) else -1
                        if (idx1 >= 0 && idx2 >= 0 && idx1 != idx2) {
                            arbiter.updateTwoFingerTwist(
                                pointerId1 = arbiter.twistPointerId1,
                                x1 = event.getX(idx1),
                                y1 = event.getY(idx1),
                                pointerId2 = arbiter.twistPointerId2,
                                x2 = event.getX(idx2),
                                y2 = event.getY(idx2)
                            )
                        } else {
                            arbiter.anchorTwoFingerTwist(
                                pointerId1 = event.getPointerId(0),
                                x1 = event.getX(0),
                                y1 = event.getY(0),
                                pointerId2 = event.getPointerId(1),
                                x2 = event.getX(1),
                                y2 = event.getY(1)
                            )
                        }
                    } else {
                        arbiter.clearTwoFingerTwistAnchor()
                    }
                    val activeIdx = event.findPointerIndex(activePointerId)
                    if (activeIdx >= 0) {
                        arbiter.updateMultiTouchReference(event.getX(activeIdx), event.getY(activeIdx))
                    }
                } else if (event.pointerCount == 1) {
                    var pointerIdx = event.findPointerIndex(activePointerId)
                    if (pointerIdx < 0) {
                        activePointerId = event.getPointerId(0)
                        pointerIdx = 0
                        arbiter.onPointerIdRebound(
                            newX = event.getX(0),
                            newY = event.getY(0),
                            newPointerId = activePointerId
                        )
                        return true
                    }
                    arbiter.onMove(event.getX(pointerIdx), event.getY(pointerIdx))
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = actionIndex
                val liftedPointerId = event.getPointerId(liftedIndex)
                val remainingPointers = (event.pointerCount - 1).coerceAtLeast(0)
                activePointerCount = remainingPointers

                if (remainingPointers == 0) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    pinchInProgress = false
                    initialPinchSpan = 0f
                    lastPinchSpan = 0f
                    parent?.requestDisallowInterceptTouchEvent(false)
                    arbiter.onUpOrCancel()
                    return true
                }

                // Determina o índice do ponteiro sobrevivente sem presumir que estará no índice 0
                val survivingIndex = if (liftedPointerId == activePointerId || event.findPointerIndex(activePointerId) < 0) {
                    var chosenIdx = -1
                    for (i in 0 until event.pointerCount) {
                        if (i != liftedIndex) {
                            chosenIdx = i
                            break
                        }
                    }
                    chosenIdx.coerceAtLeast(0)
                } else {
                    val existingIdx = event.findPointerIndex(activePointerId)
                    if (existingIdx >= 0 && existingIdx != liftedIndex) {
                        existingIdx
                    } else {
                        (if (liftedIndex == 0) 1 else 0).coerceIn(0, event.pointerCount - 1)
                    }
                }

                activePointerId = event.getPointerId(survivingIndex)
                val survivingX = event.getX(survivingIndex)
                val survivingY = event.getY(survivingIndex)

                if (remainingPointers == 1) {
                    pinchInProgress = false
                    initialPinchSpan = 0f
                    lastPinchSpan = 0f
                    arbiter.onTransitionToSinglePointer(
                        remainingX = survivingX,
                        remainingY = survivingY,
                        remainingPointerId = activePointerId
                    )
                } else {
                    val newSpan = computeActiveSpan(event, ignoreIndex = liftedIndex)
                    lastPinchSpan = newSpan
                    initialPinchSpan = newSpan
                    arbiter.onMultiPointersRemaining(
                        remainingPointerCount = remainingPointers,
                        survivingX = survivingX,
                        survivingY = survivingY,
                        survivingPointerId = activePointerId
                    )
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelActiveTouchInteraction()
            }
        }
        return true
    }
}
