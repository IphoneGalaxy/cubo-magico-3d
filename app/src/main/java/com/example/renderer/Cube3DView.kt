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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.curriculum.HighlightMode
import com.example.model.CubeMove
import com.example.model.CubeState
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
enum class TouchInteractionMode(val labelPt: String) {
    MOVE_LAYERS("Mover camadas"),
    ROTATE_CAMERA("Girar câmera")
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
    var onLayerMoveResolved: (CubeMove) -> Unit = {},
    var onCameraUpdated: () -> Unit = {}
) {
    var state: GestureInteractionState = GestureInteractionState.IDLE
        private set

    var screenDensity: Float = 1f

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var initialHit: CubeTouchHit? = null
    private var lockedToLayerIntent = false
    private var moveDispatchedInGesture = false

    val orbitTouchSlopPx: Float
        get() = 8f * screenDensity.coerceAtLeast(1f)

    val layerSwipeSlopPx: Float
        get() = 18f * screenDensity.coerceAtLeast(1f)

    fun onDown(x: Float, y: Float) {
        renderer.onUserTouchStart()
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

    fun onPointerDown(pointerCount: Int) {
        renderer.onUserTouchStart()
        if (pointerCount >= 2) {
            // Dois ou mais dedos: prioriza imediatamente o zoom por pinça e cancela qualquer giro pendente
            state = GestureInteractionState.PINCH_ZOOM
            initialHit = null
            lockedToLayerIntent = false
            renderer.previewTouchHit = null
            renderer.previewTouchMove = null
            onCameraUpdated()
        }
    }

    fun onPinchScale(scaleFactor: Float) {
        if (state != GestureInteractionState.PINCH_ZOOM) {
            state = GestureInteractionState.PINCH_ZOOM
            initialHit = null
            lockedToLayerIntent = false
            renderer.previewTouchHit = null
            renderer.previewTouchMove = null
        }
        if (scaleFactor > 0.01f && scaleFactor != 1.0f) {
            renderer.cameraDistance = (renderer.cameraDistance / scaleFactor)
                .coerceIn(CubeGLRenderer.MIN_DISTANCE, CubeGLRenderer.MAX_DISTANCE)
            synchronized(this) {
                renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 0f))
            }
            onCameraUpdated()
        }
    }

    fun onMove(x: Float, y: Float) {
        // Se o gesto foi classificado como PINCH_ZOOM ou IDLE, ignora movimentos de 1 dedo
        if (state == GestureInteractionState.PINCH_ZOOM || state == GestureInteractionState.IDLE) {
            lastX = x
            lastY = y
            return
        }

        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y

        // CASO 1: Modo Mover Camadas -> NUNCA move a câmera!
        if (lockedToLayerIntent) {
            if (isAnimatingProvider()) {
                state = GestureInteractionState.ANIMATING
                return
            }

            // Se o usuário iniciou o toque ligeiramente fora da borda do cubo e arrastou para dentro,
            // captura a primeira peça atingida ao cruzar a silhueta do cubo.
            if (initialHit == null) {
                val enteredHit = renderer.pickCubieAtScreen(x, y, allowEdgePadding = true)
                if (enteredHit != null) {
                    initialHit = enteredHit
                    renderer.previewTouchHit = enteredHit
                    onCameraUpdated()
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

        if (state == GestureInteractionState.PENDING_GESTURE) {
            if (totalDist >= orbitTouchSlopPx) {
                state = GestureInteractionState.CAMERA_ORBIT
            } else {
                return
            }
        }

        if (state == GestureInteractionState.CAMERA_ORBIT) {
            renderer.cameraYaw = (renderer.cameraYaw - dx * 0.40f) % 360f
            renderer.cameraPitch = (renderer.cameraPitch + dy * 0.34f).coerceIn(-72f, 72f)
            onCameraUpdated()
        }
    }

    fun onUpOrCancel() {
        renderer.onUserTouchEnd()
        val hadPreview = renderer.previewTouchHit != null || renderer.previewTouchMove != null
        state = GestureInteractionState.IDLE
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
    celebrationPulse: Float = 0f,
    cameraResetTrigger: Int = 0,
    showCameraControlsOverlay: Boolean = true,
    onMoveSwiped: (CubeMove) -> Unit = {}
) {
    val isRobolectric = remember {
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    val renderer = remember { CubeGLRenderer() }
    var glSurfaceViewRef by remember { mutableStateOf<CubeTouchGLSurfaceView?>(null) }
    var localResetCount by remember { mutableIntStateOf(0) }
    var touchInteractionMode by remember { mutableStateOf(TouchInteractionMode.MOVE_LAYERS) }

    val currentAllowFaceSwipe by rememberUpdatedState(allowFaceSwipeMoves)
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
            newAutoRotateIdle = autoRotateIdle,
            newCelebrationPulse = celebrationPulse
        )
        glSurfaceViewRef?.let { glView ->
            val desiredMode = if (autoRotateIdle || activeAnimation != null || celebrationPulse > 0f) {
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
            if (glView.renderMode != desiredMode) {
                glView.renderMode = desiredMode
            }
            glView.requestRender()
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
            .clip(RoundedCornerShape(22.dp))
            .testTag("cube_3d_viewport")
    ) {
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
                        newAutoRotateIdle = autoRotateIdle,
                        newCelebrationPulse = celebrationPulse
                    )
                    CubeTouchGLSurfaceView(
                        context = ctx,
                        cubeRenderer = renderer,
                        allowFaceSwipeProvider = { currentAllowFaceSwipe },
                        isAnimatingProvider = { currentIsAnimating },
                        touchModeProvider = { currentTouchMode },
                        onSwipeMove = { move -> currentOnMoveSwiped(move) }
                    ).also { glView ->
                        glSurfaceViewRef = glView
                    }
                },
                update = { glView ->
                    glView.arbiter.allowFaceSwipeProvider = { currentAllowFaceSwipe }
                    glView.arbiter.isAnimatingProvider = { currentIsAnimating }
                    glView.arbiter.touchModeProvider = { currentTouchMode }
                },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(Unit) {
                glSurfaceViewRef?.onResume()
                onDispose {
                    glSurfaceViewRef?.onPause()
                }
            }
        } else {
            // Ambiente de teste local JVM (Robolectric): exibe marcador determinístico sem exigir GPU EGL nativa
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cubo 3D (${cubeState.cubies.size} peças)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Indicador superior compacto da direção/eixo do movimento em execução (sem encobrir o cubo)
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
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .border(1.dp, TurquoisePrimary.copy(alpha = 0.65f), RoundedCornerShape(50))
                        .testTag("active_move_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${move.notation} ${directionArrowForMove(move)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = TurquoisePrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = move.titlePt,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Controles inferiores do viewport 3D: Seletor explícito (Mover camadas | Girar câmera) + Recentralizar câmera
        if (showCameraControlsOverlay) {
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
                            color = if (isMoveLayers) TurquoisePrimary else Color.Transparent,
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
                                    tint = if (isMoveLayers) Color(0xFF00201C) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Camadas",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMoveLayers) Color(0xFF00201C) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (!isMoveLayers) VioletAccent else Color.Transparent,
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
                                    text = "Câmera",
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
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
                            tint = TurquoisePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Arraste p/ girar câmera • Pinça p/ zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = { localResetCount++ },
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
                    contentDescription = "Recentralizar câmera",
                    tint = TurquoisePrimary
                )
            }
        }
    }
}

private fun directionArrowForMove(move: CubeMove): String = when (move) {
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
private class CubeTouchGLSurfaceView(
    context: Context,
    val cubeRenderer: CubeGLRenderer,
    allowFaceSwipeProvider: () -> Boolean,
    isAnimatingProvider: () -> Boolean,
    touchModeProvider: () -> TouchInteractionMode,
    onSwipeMove: (CubeMove) -> Unit
) : GLSurfaceView(context) {

    val arbiter = CubeGestureArbiter(
        renderer = cubeRenderer,
        allowFaceSwipeProvider = allowFaceSwipeProvider,
        isAnimatingProvider = isAnimatingProvider,
        touchModeProvider = touchModeProvider,
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
                arbiter.onPointerDown(2)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                arbiter.onPinchScale(detector.scaleFactor)
                return true
            }
        }
    )

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(cubeRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Impede que qualquer container pai do Compose intercepte o toque para rolar a página
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                arbiter.onDown(event.x, event.y)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                arbiter.onPointerDown(event.pointerCount)
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    arbiter.onMove(event.x, event.y)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                arbiter.onUpOrCancel()
            }
        }
        return true
    }
}
