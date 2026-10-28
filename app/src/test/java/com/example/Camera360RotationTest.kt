package com.example

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.example.curriculum.CurriculumRepository
import com.example.model.CubeFace
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.CameraQuaternion
import com.example.renderer.Cube3DViewAuditHolder
import com.example.renderer.CubeGLRenderer
import com.example.renderer.CubeGestureArbiter
import com.example.renderer.CubeTouchGLSurfaceView
import com.example.renderer.GestureInteractionState
import com.example.renderer.TouchInteractionMode
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CuboMestreViewModel
import com.example.viewmodel.LearningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Suíte de testes automatizados para a Rotação Livre 360° da Câmera 3D:
 * 1. Rotação vertical contínua além dos antigos limites (-72°/+72° e -78°/+78°).
 * 2. Inversão visual completa de 180° (face UP na parte inferior da tela e DOWN na parte superior).
 * 3. Rotação acumulada de 360° (e múltiplas voltas) retornando à orientação inicial.
 * 4. Ausência de saltos, singularidades ou inversão de controles ao atravessar os polos (±90°).
 * 5. Estabilidade numérica e normalização da orientação por quaternion e base ortonormal.
 * 6. Funcionamento do zoom por pinça após inverter a visão em 180°.
 * 7. Raycasting e resolução de movimentos de camada (resolveMoveFromSwipe) em diferentes orientações (incluindo polos e 180°).
 * 8. Ausência de movimentos matemáticos involuntários em CubeState, histórico ou algoritmos pedagógicos.
 * 9. Retorno à orientação inicial pelo botão de recentralização (recenter_camera_button e resetCamera).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class Camera360RotationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun vectorDistance(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun dot3(a: FloatArray, b: FloatArray): Float =
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun norm3(v: FloatArray): Float =
        sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    /**
     * TESTES 1, 2 e 3:
     * - Rotação vertical contínua além dos antigos limites (-72°/+72° e -78°/+78°).
     * - Inversão visual completa de 180° (face UP projetada na parte inferior da tela e face DOWN na parte superior).
     * - Rotação acumulada de 360° (retornando à orientação 3D inicial).
     */
    @Test
    fun testContinuousVerticalRotationBeyondOldLimits180InversionAnd360Loop() {
        val renderer = CubeGLRenderer().apply {
            configureViewport(800, 800)
            resetCamera()
        }
        val arbiter = CubeGestureArbiter(
            renderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { TouchInteractionMode.ROTATE_CAMERA }
        ).apply {
            screenDensity = 1f
        }

        val initialQuat = renderer.cameraQuaternion
        val initialEye = renderer.getCameraEyePosition()
        val initialUp = renderer.getCameraUpVector()
        val initialRight = renderer.getCameraRightVector()

        // Na orientação inicial (pitch = 26°), a face UP (0, +1.45, 0) projeta acima da face DOWN (0, -1.45, 0) na tela (menor Y em pixels)
        val initialUpScreen = renderer.projectWorldToScreen(floatArrayOf(0f, 1.45f, 0f))!!
        val initialDownScreen = renderer.projectWorldToScreen(floatArrayOf(0f, -1.45f, 0f))!!
        assertTrue(
            "Na orientação inicial, a face UP deve estar visualmente acima da face DOWN na tela",
            initialUpScreen[1] < initialDownScreen[1]
        )
        assertFalse("Câmera não deve iniciar de cabeça para baixo", renderer.isCameraUpsideDown)

        // 1. Arrasta verticalmente ultrapassando os antigos limites de +72° e +78° até +116° (+90° de incremento a partir de 26°)
        arbiter.onDown(400f, 100f, pointerId = 0)
        // Supera o orbitTouchSlopPx (8px) sem deslocamento vertical ainda significativo
        arbiter.onMove(400f, 110f)
        assertEquals(GestureInteractionState.CAMERA_ORBIT, arbiter.state)

        // Compensa o pequeno dy de ativação do slop e aplica exatamente +90° de rotação vertical
        renderer.resetCamera()
        val dyFor1Degree = 1f / CubeGLRenderer.PITCH_SENSITIVITY
        var currentY = 110f

        repeat(90) {
            currentY += dyFor1Degree
            arbiter.onMove(400f, currentY)
        }
        assertEquals(
            "cameraPitch deve ultrapassar livremente os antigos limites de 72° e 78° (26° + 90° = 116°)",
            CubeGLRenderer.DEFAULT_PITCH + 90f,
            renderer.cameraPitch,
            0.05f
        )
        assertTrue(
            "Após girar +90° a partir de 26° (pitch = 116° > 90°), a câmera já atravessou o polo norte e está invertida",
            renderer.isCameraUpsideDown
        )

        // 2. Continua mais +90° para completar exatamente 180° de virada vertical (pitch = 26° + 180° = 206°)
        repeat(90) {
            currentY += dyFor1Degree
            arbiter.onMove(400f, currentY)
        }
        assertEquals(
            "Após 180° de virada vertical, cameraPitch deve ser 206°",
            CubeGLRenderer.DEFAULT_PITCH + 180f,
            renderer.cameraPitch,
            0.05f
        )
        assertTrue("Com 180° de rotação vertical, isCameraUpsideDown deve ser true", renderer.isCameraUpsideDown)

        // Na virada vertical de 180°, eyePos e upDir são exatamente opostos aos vetores iniciais, enquanto rightDir permanece inalterado
        val invertedEye = renderer.getCameraEyePosition()
        val invertedUp = renderer.getCameraUpVector()
        val invertedRight = renderer.getCameraRightVector()
        assertEquals(-initialEye[0], invertedEye[0], 0.02f)
        assertEquals(-initialEye[1], invertedEye[1], 0.02f)
        assertEquals(-initialEye[2], invertedEye[2], 0.02f)
        assertEquals(-initialUp[0], invertedUp[0], 0.02f)
        assertEquals(-initialUp[1], invertedUp[1], 0.02f)
        assertEquals(-initialUp[2], invertedUp[2], 0.02f)
        assertEquals(initialRight[0], invertedRight[0], 0.02f)
        assertEquals(initialRight[1], invertedRight[1], 0.02f)
        assertEquals(initialRight[2], invertedRight[2], 0.02f)

        // Verifica inversão visual de 180° na tela: a face UP agora aparece na parte INFERIOR da tela (maior Y)
        // e a face DOWN aparece na parte SUPERIOR da tela (menor Y)
        val invertedUpScreen = renderer.projectWorldToScreen(floatArrayOf(0f, 1.45f, 0f))!!
        val invertedDownScreen = renderer.projectWorldToScreen(floatArrayOf(0f, -1.45f, 0f))!!
        assertTrue(
            "Após virada vertical de 180°, a face UP deve estar visualmente na parte inferior da tela (Y=${invertedUpScreen[1]}) e a face DOWN na parte superior (Y=${invertedDownScreen[1]})",
            invertedUpScreen[1] > invertedDownScreen[1]
        )

        // 3. Continua mais +180° para completar uma volta vertical completa de 360° (pitch = 26° + 360° = 386°)
        repeat(180) {
            currentY += dyFor1Degree
            arbiter.onMove(400f, currentY)
        }
        arbiter.onUpOrCancel()

        assertEquals(
            "Após rotação acumulada de 360°, cameraPitch deve registrar 386° (26° + 360°)",
            CubeGLRenderer.DEFAULT_PITCH + 360f,
            renderer.cameraPitch,
            0.1f
        )
        assertEquals(
            "normalizedCameraPitch após 360° deve coincidir com DEFAULT_PITCH (26°)",
            CubeGLRenderer.DEFAULT_PITCH,
            renderer.normalizedCameraPitch,
            0.1f
        )
        assertFalse("Após 360°, a câmera deve voltar à orientação normal (não invertida)", renderer.isCameraUpsideDown)
        assertTrue(
            "A orientação 3D (quaternion) após 360° deve coincidir com a orientação inicial",
            renderer.cameraQuaternion.angularDistanceDegrees(initialQuat) < 0.1f
        )
        assertTrue("Posição da câmera após 360° deve coincidir com a inicial", vectorDistance(initialEye, renderer.getCameraEyePosition()) < 0.02f)
        assertTrue("Vetor Up após 360° deve coincidir com o inicial", vectorDistance(initialUp, renderer.getCameraUpVector()) < 0.02f)

        val fullLoopUpScreen = renderer.projectWorldToScreen(floatArrayOf(0f, 1.45f, 0f))!!
        val fullLoopDownScreen = renderer.projectWorldToScreen(floatArrayOf(0f, -1.45f, 0f))!!
        assertTrue(
            "Após 360°, a face UP volta para a parte superior da tela",
            fullLoopUpScreen[1] < fullLoopDownScreen[1]
        )

        // 4. Verifica também rotação vertical negativa além de -72° e -78° até -360°
        renderer.resetCamera()
        val dyNeg1Deg = -1f / CubeGLRenderer.PITCH_SENSITIVITY
        repeat(360) {
            renderer.orbitCamera(0f, dyNeg1Deg)
        }
        assertEquals(CubeGLRenderer.DEFAULT_PITCH - 360f, renderer.cameraPitch, 0.1f)
        assertTrue(
            "Após -360° verticais, a orientação 3D deve coincidir com a inicial",
            renderer.cameraQuaternion.angularDistanceDegrees(initialQuat) < 0.1f
        )
    }

    /**
     * TESTES 4 e 5:
     * - Ausência total de saltos de câmera ou singularidades ao atravessar os polos Norte (+90°) e Sul (-90°).
     * - Ausência de inversão de controle horizontal quando o cubo está de cabeça para baixo (180°).
     * - Estabilidade numérica e normalização contínua do quaternion e da base ortonormal após milhares de passos.
     */
    @Test
    fun testPoleCrossingSmoothnessNoControlInversionAndQuaternionNormalizationStability() {
        val renderer = CubeGLRenderer().apply {
            configureViewport(800, 800)
            resetCamera()
        }

        val dyPerDegree = 1f / CubeGLRenderer.PITCH_SENSITIVITY
        val expectedEyeStep = (CubeGLRenderer.DEFAULT_DISTANCE * Math.toRadians(1.0)).toFloat()
        val expectedUpStep = Math.toRadians(1.0).toFloat()

        var prevEye = renderer.getCameraEyePosition()
        var prevUp = renderer.getCameraUpVector()
        var prevRight = renderer.getCameraRightVector()

        // Atravessa dois ciclos completos de 360° (720 passos de 1°), passando duas vezes pelo Polo Norte (+90°/+270°) e Polo Sul
        repeat(720) { step ->
            renderer.orbitCamera(0f, dyPerDegree)
            val currEye = renderer.getCameraEyePosition()
            val currUp = renderer.getCameraUpVector()
            val currRight = renderer.getCameraRightVector()

            val eyeDelta = vectorDistance(prevEye, currEye)
            val upDelta = vectorDistance(prevUp, currUp)
            val rightDelta = vectorDistance(prevRight, currRight)

            // Em uma implementação com setLookAtM e up fixo (0,1,0), ao cruzar ±90° o vetor Up e o vetor Right sofrem um salto de ~2.0 (180°)!
            // Com quaternions, cada passo de 1° move eyePos em ~0.1326 e upDir em ~0.01745 em TODOS os ângulos, inclusive em 89°->90°->91°.
            assertEquals(
                "Passo $step (pitch=${renderer.cameraPitch}°): deslocamento de eyePos deve ser estritamente contínuo sem salto no polo",
                expectedEyeStep,
                eyeDelta,
                0.015f
            )
            assertEquals(
                "Passo $step (pitch=${renderer.cameraPitch}°): rotação de upDir deve ser estritamente contínua sem flip de 180° no polo",
                expectedUpStep,
                upDelta,
                0.005f
            )
            assertTrue(
                "Passo $step: rightDir deve permanecer estável durante rotação puramente vertical",
                rightDelta < 0.002f
            )
            assertTrue(
                "Passo $step: cameraQuaternion deve permanecer unitário",
                renderer.cameraQuaternion.isNormalized(1e-4f)
            )

            prevEye = currEye
            prevUp = currUp
            prevRight = currRight
        }

        // Verifica que os controles horizontais NÃO ficam invertidos quando o cubo está de cabeça para baixo (180°):
        // Tanto na posição normal (0°) quanto invertida (180°), arrastar o dedo para a direita (dx > 0)
        // deve mover o ponto central da face visível para a DIREITA na tela (screenX aumenta).
        for (pitchAngle in listOf(0f, 26f, 90f, 180f, 206f, -90f, -180f)) {
            renderer.cameraYaw = 0f
            renderer.cameraPitch = pitchAngle
            val eyeDir = renderer.getCameraEyeDirection()
            // Ponto na superfície frontal visível do cubo voltada para a câmera
            val frontSurfacePoint = floatArrayOf(
                eyeDir[0] * 1.45f,
                eyeDir[1] * 1.45f,
                eyeDir[2] * 1.45f
            )
            val screenBefore = renderer.projectWorldToScreen(frontSurfacePoint)!!
            // Arrasta o dedo para a direita na tela (dx = +25px)
            renderer.orbitCamera(dx = 25f, dy = 0f)
            val screenAfterRightDrag = renderer.projectWorldToScreen(frontSurfacePoint)!!
            assertTrue(
                "Em pitch=$pitchAngle°, arrastar o dedo para a direita (dx > 0) deve mover a superfície visível do cubo para a direita na tela (antes=${screenBefore[0]}, depois=${screenAfterRightDrag[0]})",
                screenAfterRightDrag[0] > screenBefore[0] + 2f
            )

            // E arrastar o dedo para baixo na tela (dy = +25px) deve mover a superfície visível para baixo na tela (screenY aumenta)
            val eyeDir2 = renderer.getCameraEyeDirection()
            val frontSurfacePoint2 = floatArrayOf(
                eyeDir2[0] * 1.45f,
                eyeDir2[1] * 1.45f,
                eyeDir2[2] * 1.45f
            )
            val screenBeforeDown = renderer.projectWorldToScreen(frontSurfacePoint2)!!
            renderer.orbitCamera(dx = 0f, dy = 25f)
            val screenAfterDownDrag = renderer.projectWorldToScreen(frontSurfacePoint2)!!
            assertTrue(
                "Em pitch=$pitchAngle°, arrastar o dedo para baixo (dy > 0) deve mover a superfície visível do cubo para baixo na tela (antes=${screenBeforeDown[1]}, depois=${screenAfterDownDrag[1]})",
                screenAfterDownDrag[1] > screenBeforeDown[1] + 2f
            )
        }

        // Estabilidade e ortonormalidade após 2.000 rotações diagonais combinadas (dx e dy variados)
        renderer.resetCamera()
        repeat(2000) { i ->
            val dx = if (i % 3 == 0) 14f else -9f
            val dy = if (i % 5 == 0) -19f else 17f
            renderer.orbitCamera(dx, dy)
        }
        assertTrue(
            "Após 2.000 passos diagonais, cameraQuaternion deve continuar normalizado (norm=${renderer.cameraQuaternion.norm()})",
            renderer.cameraQuaternion.isNormalized(1e-4f)
        )
        val finalRight = renderer.getCameraRightVector()
        val finalUp = renderer.getCameraUpVector()
        val finalEyeDir = renderer.getCameraEyeDirection()
        assertEquals(1f, norm3(finalRight), 1e-4f)
        assertEquals(1f, norm3(finalUp), 1e-4f)
        assertEquals(1f, norm3(finalEyeDir), 1e-4f)
        assertEquals("right e up devem permanecer ortogonais", 0f, dot3(finalRight, finalUp), 1e-4f)
        assertEquals("up e eyeDir devem permanecer ortogonais", 0f, dot3(finalUp, finalEyeDir), 1e-4f)
        assertEquals("right e eyeDir devem permanecer ortogonais", 0f, dot3(finalRight, finalEyeDir), 1e-4f)
    }

    /**
     * TESTES 6 e 7:
     * - Funcionamento correto do zoom por pinça após inverter a visão em 180°.
     * - Raycasting correto (pickCubieAtScreen) e resolução de movimentos de camada (resolveMoveFromSwipe)
     *   em diferentes orientações: padrão, Polo Norte (+90°), Polo Sul (-90°), e invertida em 180°.
     */
    @Test
    fun testZoomAndRaycastingAndLayerManipulationAcross360Orientations() {
        val renderer = CubeGLRenderer().apply {
            configureViewport(800, 800)
            resetCamera()
        }
        val dispatchedMoves = mutableListOf<CubeMove>()
        var currentMode = TouchInteractionMode.ROTATE_CAMERA
        val arbiter = CubeGestureArbiter(
            renderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { currentMode },
            onLayerMoveResolved = { dispatchedMoves.add(it) }
        ).apply {
            screenDensity = 1f
        }

        // 1. Inverte a câmera em 180° na vertical (a partir de yaw=0°, pitch=0° -> pitch=180°)
        renderer.cameraYaw = 0f
        renderer.cameraPitch = 0f
        val dyFor180 = 180f / CubeGLRenderer.PITCH_SENSITIVITY
        renderer.orbitCamera(0f, dyFor180)
        assertTrue("Câmera deve estar invertida em 180°", renderer.isCameraUpsideDown)
        val quatInverted = renderer.cameraQuaternion

        // 2. Executa zoom por pinça com a visão invertida em 180°
        val distBeforeZoom = renderer.cameraDistance
        arbiter.onDown(300f, 400f, pointerId = 0)
        arbiter.onPointerDown(2, activeX = 300f, activeY = 400f, primaryPointerId = 0)
        arbiter.onPinchScale(1.25f) // aproxima (diminui cameraDistance)
        arbiter.onUpOrCancel()

        val distAfterZoomIn = renderer.cameraDistance
        assertTrue(
            "Zoom in após inverter 180° deve reduzir cameraDistance ($distBeforeZoom -> $distAfterZoomIn)",
            distAfterZoomIn < distBeforeZoom
        )
        assertTrue("Orientação invertida de 180° deve ser preservada durante o zoom", renderer.isCameraUpsideDown)
        assertTrue(
            "Quaternion de orientação não deve mudar durante o zoom",
            renderer.cameraQuaternion.angularDistanceDegrees(quatInverted) < 1e-4f
        )

        // Zoom out também respeita os limites MIN_DISTANCE..MAX_DISTANCE
        arbiter.onDown(300f, 400f, pointerId = 0)
        arbiter.onPointerDown(2, activeX = 300f, activeY = 400f, primaryPointerId = 0)
        arbiter.onPinchScale(0.65f)
        arbiter.onUpOrCancel()
        assertTrue("Zoom out após inverter 180° deve aumentar cameraDistance", renderer.cameraDistance > distAfterZoomIn)

        // 3. Raycasting no Polo Norte (yaw=0°, pitch=+90°): centro da tela deve atingir CubeFace.UP (0, +1, 0)
        renderer.resetCamera()
        renderer.cameraYaw = 0f
        renderer.cameraPitch = 90f
        val hitNorthPole = renderer.pickCubieAtScreen(400f, 400f)
        assertNotNull("Raycasting no Polo Norte deve atingir o cubo", hitNorthPole)
        assertEquals(CubeFace.UP, hitNorthPole!!.hitFace)
        assertEquals(0, hitNorthPole.cubieX)
        assertEquals(1, hitNorthPole.cubieY)
        assertEquals(0, hitNorthPole.cubieZ)

        // 4. Raycasting no Polo Sul (yaw=0°, pitch=-90°): centro da tela deve atingir CubeFace.DOWN (0, -1, 0)
        renderer.cameraYaw = 0f
        renderer.cameraPitch = -90f
        val hitSouthPole = renderer.pickCubieAtScreen(400f, 400f)
        assertNotNull("Raycasting no Polo Sul deve atingir o cubo", hitSouthPole)
        assertEquals(CubeFace.DOWN, hitSouthPole!!.hitFace)
        assertEquals(0, hitSouthPole.cubieX)
        assertEquals(-1, hitSouthPole.cubieY)
        assertEquals(0, hitSouthPole.cubieZ)

        // 5. Raycasting e manipulação de camadas com a câmera invertida em 180° (yaw=180°, pitch=180°):
        // Em (yaw=180°, pitch=180°), o olho está em (0, 0, +dist) olhando para CubeFace.FRONT,
        // porém de cabeça para baixo (upDir = (0, -1, 0)): a camada DOWN (y = -1) está no topo da tela
        // e a camada UP (y = +1) está na parte inferior da tela!
        renderer.cameraYaw = 180f
        renderer.cameraPitch = 180f
        assertTrue("Em (yaw=180°, pitch=180°), a câmera está de cabeça para baixo", renderer.isCameraUpsideDown)

        val centerFrontHit = renderer.pickCubieAtScreen(400f, 400f)
        assertNotNull(centerFrontHit)
        assertEquals("Centro da tela em (yaw=180°, pitch=180°) deve atingir CubeFace.FRONT", CubeFace.FRONT, centerFrontHit!!.hitFace)

        // Projeta a peça (x=0, y=+1, z=+1.45) da camada UP na tela (que agora está na metade INFERIOR da tela, screenY > 400)
        val upEdgeOnInvertedScreen = renderer.projectWorldToScreen(floatArrayOf(0f, 0.95f, 1.45f))!!
        assertTrue(
            "Com câmera invertida em 180°, a aresta UP deve projetar na metade inferior da tela (Y=${upEdgeOnInvertedScreen[1]})",
            upEdgeOnInvertedScreen[1] > 420f
        )
        val hitOnInvertedUpLayer = renderer.pickCubieAtScreen(upEdgeOnInvertedScreen[0], upEdgeOnInvertedScreen[1])
        assertNotNull(hitOnInvertedUpLayer)
        assertEquals(CubeFace.FRONT, hitOnInvertedUpLayer!!.hitFace)
        assertEquals("Peça tocada na parte inferior da tela invertida deve ser da camada y = +1 (UP)", 1, hitOnInvertedUpLayer.cubieY)

        // Com o cubo de cabeça para baixo, arrastar para a DIREITA na camada y = +1 (que está embaixo na tela)
        // corresponde ao giro físico U (pois na visão normal U move a frente de +X para -X, que invertido em 180° vai para a direita na tela)
        currentMode = TouchInteractionMode.MOVE_LAYERS
        dispatchedMoves.clear()
        arbiter.onDown(upEdgeOnInvertedScreen[0], upEdgeOnInvertedScreen[1], pointerId = 0)
        arbiter.onMove(upEdgeOnInvertedScreen[0] + 60f, upEdgeOnInvertedScreen[1])
        arbiter.onUpOrCancel()
        assertEquals(
            "Manipulação de camada com visão invertida em 180° deve resolver o movimento coerente com o arraste na tela",
            listOf(CubeMove.U),
            dispatchedMoves
        )
    }

    /**
     * TESTES 8 e 9:
     * - Girar a visão em 180° e 360° no modo Câmera jamais altera CubeState, histórico de movimentos ou algoritmos pedagógicos.
     * - O botão existente de recentralizar câmera (recenter_camera_button) retorna imediatamente à orientação inicial.
     */
    @Test
    fun testCameraRotationPreservesCubeStateAndPedagogicalStateAndRecenterButtonRestoresDefault() {
        lateinit var vm: CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = ViewModelProvider(act)[CuboMestreViewModel::class.java]
        }

        // 1. Verifica no Modo Explorar
        composeTestRule.runOnUiThread {
            vm.navigateTo(AppScreen.EXPLORE)
            vm.resetExploreCubeSolved()
            vm.executeExploreMove(CubeMove.R)
            vm.executeExploreMove(CubeMove.U)
        }
        composeTestRule.waitForIdle()

        val cubeBeforeOrbit = vm.exploreState.value.cubeState
        val historyBeforeOrbit = vm.exploreState.value.moveHistory.toList()
        assertEquals(listOf(CubeMove.R, CubeMove.U), historyBeforeOrbit)

        // Seleciona o modo "Câmera" na interface
        composeTestRule.onNodeWithTag("touch_mode_camera_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val activeRenderer = Cube3DViewAuditHolder.lastActiveRenderer
        assertNotNull("InteractiveCube3DView deve ter registrado seu CubeGLRenderer ativo", activeRenderer)
        val renderer = activeRenderer!!

        // Simula gestos reais de 1 dedo no modo Câmera através de CubeTouchGLSurfaceView girando 180° e 360° na vertical
        val glView = CubeTouchGLSurfaceView(
            context = RuntimeEnvironment.getApplication(),
            cubeRenderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { TouchInteractionMode.ROTATE_CAMERA },
            onSwipeMove = { move -> vm.executeExploreMove(move) }
        )

        val downTime = SystemClock.uptimeMillis()
        fun sendSingleTouch(action: Int, x: Float, y: Float, timeOffset: Long) {
            val ev = MotionEvent.obtain(
                downTime,
                downTime + timeOffset,
                action,
                x,
                y,
                0
            )
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                glView.onTouchEvent(ev)
            } finally {
                ev.recycle()
            }
        }

        // Arrasta verticalmente para inverter a câmera em 180° (+540px de arraste vertical)
        sendSingleTouch(MotionEvent.ACTION_DOWN, 400f, 100f, 0L)
        var yPos = 100f
        for (step in 1..30) {
            yPos += 20f
            sendSingleTouch(MotionEvent.ACTION_MOVE, 400f, yPos, step * 16L)
        }
        sendSingleTouch(MotionEvent.ACTION_UP, 400f, yPos, 500L)

        assertTrue(
            "Após arraste vertical longo no modo Câmera, cameraPitch deve superar 180° (atual=${renderer.cameraPitch}°)",
            renderer.cameraPitch > 180f
        )
        assertTrue("Câmera deve estar de cabeça para baixo após o gesto", renderer.isCameraUpsideDown)

        // Garante que girar a visão NÃO alterou CubeState nem histórico de movimentos
        assertEquals("Girar a câmera jamais deve alterar CubeState", cubeBeforeOrbit, vm.exploreState.value.cubeState)
        assertEquals("Girar a câmera jamais deve alterar moveHistory", historyBeforeOrbit, vm.exploreState.value.moveHistory)

        // Aplica zoom alterado e aciona o botão de recentralizar câmera (recenter_camera_button) na UI
        renderer.cameraDistance = 6.1f
        composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Botão recentralizar deve restaurar DEFAULT_YAW", CubeGLRenderer.DEFAULT_YAW, renderer.cameraYaw, 0.01f)
        assertEquals("Botão recentralizar deve restaurar DEFAULT_PITCH", CubeGLRenderer.DEFAULT_PITCH, renderer.cameraPitch, 0.01f)
        assertEquals("Botão recentralizar deve restaurar DEFAULT_DISTANCE", CubeGLRenderer.DEFAULT_DISTANCE, renderer.cameraDistance, 0.01f)
        assertFalse("Após recentralizar, a câmera não deve estar de cabeça para baixo", renderer.isCameraUpsideDown)
        val expectedDefaultQuat = CameraQuaternion.fromYawPitch(CubeGLRenderer.DEFAULT_YAW, CubeGLRenderer.DEFAULT_PITCH)
        assertTrue(
            "Botão recentralizar deve restaurar o quaternion inicial exato",
            renderer.cameraQuaternion.angularDistanceDegrees(expectedDefaultQuat) < 0.01f
        )

        // 2. Verifica também na tela de Aula (Modo Aprender - Etapa 4, Caso 2) que girar a câmera 360° preserva estado e algoritmos pedagógicos
        composeTestRule.runOnUiThread {
            vm.openLesson(stepNumber = 4, caseIndex = 1, mode = LearningMode.LEARN)
        }
        composeTestRule.waitForIdle()

        val lessonCubeBefore = vm.lessonState.value.cubeState
        val lessonMoveIdxBefore = vm.lessonState.value.currentMoveIndex
        val step4Case2AlgoBefore = CurriculumRepository.getStep(4).cases[1].fullResolutionAlgorithm

        val lessonRenderer = Cube3DViewAuditHolder.lastActiveRenderer!!
        lessonRenderer.orbitCamera(dx = 80f, dy = 530f) // vira ~180° na vertical
        assertTrue(lessonRenderer.isCameraUpsideDown)

        assertEquals("Girar câmera na aula não altera cubeState", lessonCubeBefore, vm.lessonState.value.cubeState)
        assertEquals("Girar câmera na aula não altera currentMoveIndex", lessonMoveIdxBefore, vm.lessonState.value.currentMoveIndex)
        assertEquals("F U R U' R' F' F U R U' R' F'", CurriculumRepository.getStep(4).cases[1].fullResolutionAlgorithm)
        assertEquals(step4Case2AlgoBefore, CurriculumRepository.getStep(4).cases[1].fullResolutionAlgorithm)

        composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(CubeGLRenderer.DEFAULT_PITCH, lessonRenderer.cameraPitch, 0.01f)
        assertFalse(lessonRenderer.isCameraUpsideDown)
    }

    /**
     * TESTE COMPLEMENTAR DA HOME (10 Verificações da Rotação Automática com Câmera 360°):
     * 1. A rotação automática modifica o quaternion e a orientação efetiva da câmera.
     * 2. A matriz de visualização/projeção acompanha corretamente o quaternion (projectWorldToScreen e vetores da base).
     * 3. A câmera para imediatamente quando o usuário toca.
     * 4. A orientação permanece estável durante a interação.
     * 5. A rotação retorna suavemente após o período de inatividade (1800ms).
     * 6. A retomada funciona após uma inversão manual de 180° (de cabeça para baixo).
     * 7. A Home é a única tela com rotação automática em repouso.
     * 8. A recentralização restaura a orientação inicial.
     * 9. O zoom e o raycasting permanecem corretos.
     * 10. Não há alteração indevida no estado matemático do cubo.
     */
    @Test
    fun testHomeIdleAutoRotationQuaternionViewMatrixTouchPause180InversionAndScreenIsolation() {
        lateinit var vm: CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = ViewModelProvider(act)[CuboMestreViewModel::class.java]
        }

        // Garante que estamos na tela inicial (Home) com o cubo resolvido
        composeTestRule.runOnUiThread {
            vm.navigateTo(AppScreen.HOME)
            vm.resetHomePreviewCube()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()

        val homeRenderer = Cube3DViewAuditHolder.lastActiveRenderer
        assertNotNull("O renderer da Home deve estar registrado no Cube3DViewAuditHolder", homeRenderer)
        val renderer = homeRenderer!!
        renderer.configureViewport(800, 800)
        renderer.resetCamera()
        renderer.clearAutoRotateCooldownForTest()

        val initialCubeState = renderer.cubeState
        val initialHomeVmCube = vm.homePreviewState.value.cubeState
        assertTrue("Na Home, autoRotateIdle deve estar habilitado", renderer.autoRotateIdle)
        assertTrue("Na Home em repouso, isCurrentlyAutoRotating() deve ser true", renderer.isCurrentlyAutoRotating())

        val quat0 = renderer.cameraQuaternion
        val eye0 = renderer.getCameraEyePosition()
        val right0 = renderer.getCameraRightVector()
        val frontPointWorld = floatArrayOf(0f, 0f, 1.45f)
        val screen0 = renderer.projectWorldToScreen(frontPointWorld)!!

        // 1 e 2. Avança 20 quadros de rotação automática em repouso (20 * 0.35° = 7.0°)
        // e verifica que tanto cameraQuaternion quanto os vetores extraídos da base e a projeção 2D (vpMatrix = proj * view) mudam continuamente
        var prevQuat = quat0
        var prevEye = eye0
        var prevScreenX = screen0[0]
        repeat(20) { step ->
            assertTrue("Passo $step: advanceIdleAutoRotationIfEligible deve retornar true na Home em repouso", renderer.advanceIdleAutoRotationIfEligible())
            val currQuat = renderer.cameraQuaternion
            val currEye = renderer.getCameraEyePosition()
            val currScreen = renderer.projectWorldToScreen(frontPointWorld)!!

            assertTrue("Passo $step: cameraQuaternion deve permanecer unitário", currQuat.isNormalized(1e-4f))
            assertEquals(
                "Passo $step: cada quadro de rotação automática deve girar o quaternion em 0.35°",
                0.35f,
                prevQuat.angularDistanceDegrees(currQuat),
                0.02f
            )
            assertTrue(
                "Passo $step: posição efetiva do olho da câmera (viewMatrix) deve atualizar a cada quadro",
                vectorDistance(prevEye, currEye) > 0.01f
            )
            assertTrue(
                "Passo $step: projeção em tela (vpMatrix) deve acompanhar a rotação do quaternion",
                abs(currScreen[0] - prevScreenX) > 0.05f
            )

            prevQuat = currQuat
            prevEye = currEye
            prevScreenX = currScreen[0]
        }

        // Verifica também que onDrawFrame avança o quaternion e sincroniza a ViewMatrix
        val quatBeforeDraw = renderer.cameraQuaternion
        val eyeBeforeDraw = renderer.getCameraEyePosition()
        renderer.onDrawFrame(null)
        assertEquals(0.35f, quatBeforeDraw.angularDistanceDegrees(renderer.cameraQuaternion), 0.02f)
        assertTrue(vectorDistance(eyeBeforeDraw, renderer.getCameraEyePosition()) > 0.01f)
        assertTrue("Vetor Right da câmera deve ter mudado após a rotação automática", vectorDistance(right0, renderer.getCameraRightVector()) > 0.05f)

        // 3 e 4. Pausa imediata ao tocar e estabilidade da orientação durante a interação
        var currentTouchMode = TouchInteractionMode.MOVE_LAYERS
        val arbiter = CubeGestureArbiter(
            renderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { currentTouchMode }
        ).apply {
            screenDensity = 1f
        }

        val centerFrontScreen = renderer.projectWorldToScreen(frontPointWorld)!!
        arbiter.onDown(centerFrontScreen[0], centerFrontScreen[1], pointerId = 0)
        assertTrue("isUserTouching deve ser true imediatamente após onDown", renderer.isUserTouching)
        assertFalse("isCurrentlyAutoRotating() deve ser false durante o toque", renderer.isCurrentlyAutoRotating())

        val quatDuringTouch = renderer.cameraQuaternion
        val eyeDuringTouch = renderer.getCameraEyePosition()
        val screenDuringTouch = renderer.projectWorldToScreen(frontPointWorld)!!

        // Mesmo se múltiplos quadros de renderização ou verificações de ociosidade ocorrerem durante o toque, a câmera fica 100% imóvel
        repeat(15) {
            assertFalse("advanceIdleAutoRotationIfEligible deve retornar false enquanto o usuário toca", renderer.advanceIdleAutoRotationIfEligible())
            renderer.onDrawFrame(null)
        }
        assertEquals("Quaternion não pode mudar durante o toque", 0f, quatDuringTouch.angularDistanceDegrees(renderer.cameraQuaternion), 1e-5f)
        assertEquals("Posição da câmera não pode mudar durante o toque", 0f, vectorDistance(eyeDuringTouch, renderer.getCameraEyePosition()), 1e-5f)
        val screenAfterHold = renderer.projectWorldToScreen(frontPointWorld)!!
        assertEquals(screenDuringTouch[0], screenAfterHold[0], 1e-4f)
        assertEquals(screenDuringTouch[1], screenAfterHold[1], 1e-4f)

        // 5. Solta o dedo -> permanece pausado durante a janela de inatividade (< 1800ms) e retoma suavemente após 1800ms
        arbiter.onUpOrCancel()
        assertFalse("isUserTouching deve voltar a false ao soltar o dedo", renderer.isUserTouching)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1000))
        assertFalse("Antes de 1800ms de inatividade, a rotação automática deve continuar aguardando", renderer.advanceIdleAutoRotationIfEligible())
        assertEquals(0f, quatDuringTouch.angularDistanceDegrees(renderer.cameraQuaternion), 1e-5f)

        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(900)) // Total = 1900ms >= 1800ms
        assertTrue("Após 1800ms de inatividade, isCurrentlyAutoRotating() deve voltar a true", renderer.isCurrentlyAutoRotating())
        assertTrue("Após 1800ms, advanceIdleAutoRotationIfEligible deve retomar a rotação", renderer.advanceIdleAutoRotationIfEligible())
        assertEquals(
            "A retomada após o toque deve partir suavemente da orientação atual (0.35° sem salto)",
            0.35f,
            quatDuringTouch.angularDistanceDegrees(renderer.cameraQuaternion),
            0.02f
        )

        // 6. Retomada da rotação automática após uma inversão manual de 180° (cubo de cabeça para baixo)
        currentTouchMode = TouchInteractionMode.ROTATE_CAMERA
        renderer.resetCamera()
        val dyFor180 = 180f / CubeGLRenderer.PITCH_SENSITIVITY
        arbiter.onDown(400f, 100f, pointerId = 0)
        arbiter.onMove(400f, 110f) // supera o slop entrando em CAMERA_ORBIT
        assertEquals(GestureInteractionState.CAMERA_ORBIT, arbiter.state)
        renderer.resetCamera()
        var dragY = 110f
        repeat(180) {
            dragY += dyFor180 / 180f
            arbiter.onMove(400f, dragY)
        }
        arbiter.onUpOrCancel()

        assertTrue("Após girar 180° na vertical, a câmera deve estar de cabeça para baixo", renderer.isCameraUpsideDown)
        val quatInvertedBeforeResume = renderer.cameraQuaternion
        val upInvertedBeforeResume = renderer.getCameraUpVector()
        assertTrue("Vetor Up Y deve ser negativo quando invertido em 180°", upInvertedBeforeResume[1] < -0.5f)

        // Aguarda janela de 1800ms de inatividade após a virada de 180°
        assertFalse("Logo após soltar o giro de 180°, a rotação automática aguarda o cooldown", renderer.advanceIdleAutoRotationIfEligible())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1900))

        var prevInvQuat = quatInvertedBeforeResume
        repeat(30) { step ->
            assertTrue("Passo invertido $step: auto-rotação deve funcionar com o cubo invertido em 180°", renderer.advanceIdleAutoRotationIfEligible())
            val currInvQuat = renderer.cameraQuaternion
            assertTrue("Quaternion deve permanecer normalizado durante auto-rotação invertida", currInvQuat.isNormalized(1e-4f))
            assertEquals(
                "Passo invertido $step: auto-rotação com cubo de cabeça para baixo deve avançar 0.35° sem salto ou flip",
                0.35f,
                prevInvQuat.angularDistanceDegrees(currInvQuat),
                0.02f
            )
            assertTrue("Cubo deve permanecer de cabeça para baixo durante a auto-rotação após 180°", renderer.isCameraUpsideDown)
            prevInvQuat = currInvQuat
        }

        // 9. Zoom e raycasting permanecem corretos na Home após rotação automática e inversão de 180°
        val distBeforePinch = renderer.cameraDistance
        arbiter.onDown(300f, 400f, pointerId = 0)
        arbiter.onPointerDown(2, activeX = 300f, activeY = 400f, primaryPointerId = 0)
        assertEquals(GestureInteractionState.PINCH_ZOOM, arbiter.state)
        arbiter.onPinchScale(1.20f)
        arbiter.onUpOrCancel()
        assertTrue("Zoom in na Home deve reduzir cameraDistance", renderer.cameraDistance < distBeforePinch)

        val hitCenterInverted = renderer.pickCubieAtScreen(400f, 400f)
        assertNotNull("Raycasting no centro da tela deve continuar atingindo o cubo após auto-rotação e zoom", hitCenterInverted)
        val swipeMove = renderer.resolveMoveFromSwipe(hitCenterInverted!!, dragDx = 65f, dragDy = 0f)
        assertNotNull("resolveMoveFromSwipe deve resolver movimento válido após auto-rotação e inversão", swipeMove)

        // 8. Recentralização (resetCamera e botão recenter_camera_button) restaura a orientação inicial
        composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(CubeGLRenderer.DEFAULT_YAW, renderer.cameraYaw, 0.01f)
        assertEquals(CubeGLRenderer.DEFAULT_PITCH, renderer.cameraPitch, 0.01f)
        assertEquals(CubeGLRenderer.DEFAULT_DISTANCE, renderer.cameraDistance, 0.01f)
        assertFalse("Após recentralizar na Home, a câmera não deve estar invertida", renderer.isCameraUpsideDown)
        val expectedDefaultQuat = CameraQuaternion.fromYawPitch(CubeGLRenderer.DEFAULT_YAW, CubeGLRenderer.DEFAULT_PITCH)
        assertTrue(
            "Recentralizar deve restaurar o quaternion inicial padrão",
            renderer.cameraQuaternion.angularDistanceDegrees(expectedDefaultQuat) < 0.01f
        )

        // 10. Nenhuma alteração indevida no estado matemático do cubo durante toda a rotação automática, órbita 180° e zoom
        assertEquals("A rotação automática e a câmera 360° jamais podem alterar renderer.cubeState", initialCubeState, renderer.cubeState)
        assertEquals("A rotação automática e a câmera 360° jamais podem alterar homePreviewState.cubeState", initialHomeVmCube, vm.homePreviewState.value.cubeState)
        assertTrue("O cubo da Home deve permanecer resolvido", vm.homePreviewState.value.cubeState.isSolved())

        // 7. A Home é a única tela com rotação automática em repouso (Isolamento de Aprender, Praticar, Explorar e Guia de Notação)
        // 7a. Guia de Notação aberto -> autoRotateIdle no modal é false
        composeTestRule.runOnUiThread {
            vm.setNotationGuideVisible(true)
        }
        composeTestRule.waitForIdle()
        val notationRenderer = Cube3DViewAuditHolder.lastActiveRenderer!!
        notationRenderer.clearAutoRotateCooldownForTest()
        assertFalse("No Guia de Notação, autoRotateIdle deve ser false", notationRenderer.autoRotateIdle)
        assertFalse("No Guia de Notação, advanceIdleAutoRotationIfEligible deve retornar false", notationRenderer.advanceIdleAutoRotationIfEligible())
        composeTestRule.runOnUiThread {
            vm.setNotationGuideVisible(false)
        }
        composeTestRule.waitForIdle()

        // 7b. Tela de Aula — Modo Aprender
        composeTestRule.runOnUiThread {
            vm.openLesson(stepNumber = 1, caseIndex = 0, mode = LearningMode.LEARN)
        }
        composeTestRule.waitForIdle()
        val learnRenderer = Cube3DViewAuditHolder.lastActiveRenderer!!
        learnRenderer.clearAutoRotateCooldownForTest()
        val learnQuatBefore = learnRenderer.cameraQuaternion
        assertFalse("No Modo Aprender, autoRotateIdle deve ser false", learnRenderer.autoRotateIdle)
        assertFalse("No Modo Aprender, advanceIdleAutoRotationIfEligible deve retornar false", learnRenderer.advanceIdleAutoRotationIfEligible())
        learnRenderer.onDrawFrame(null)
        assertEquals(0f, learnQuatBefore.angularDistanceDegrees(learnRenderer.cameraQuaternion), 1e-5f)

        // 7c. Tela de Aula — Modo Praticar
        composeTestRule.runOnUiThread {
            vm.selectLearningMode(LearningMode.PRACTICE)
        }
        composeTestRule.waitForIdle()
        val practiceRenderer = Cube3DViewAuditHolder.lastActiveRenderer!!
        practiceRenderer.clearAutoRotateCooldownForTest()
        val practiceQuatBefore = practiceRenderer.cameraQuaternion
        assertFalse("No Modo Praticar, autoRotateIdle deve ser false", practiceRenderer.autoRotateIdle)
        assertFalse("No Modo Praticar, advanceIdleAutoRotationIfEligible deve retornar false", practiceRenderer.advanceIdleAutoRotationIfEligible())
        practiceRenderer.onDrawFrame(null)
        assertEquals(0f, practiceQuatBefore.angularDistanceDegrees(practiceRenderer.cameraQuaternion), 1e-5f)

        // 7d. Modo Explorar
        composeTestRule.runOnUiThread {
            vm.openExploreScreen()
        }
        composeTestRule.waitForIdle()
        val exploreRenderer = Cube3DViewAuditHolder.lastActiveRenderer!!
        exploreRenderer.clearAutoRotateCooldownForTest()
        val exploreQuatBefore = exploreRenderer.cameraQuaternion
        assertFalse("No Modo Explorar, autoRotateIdle deve ser false", exploreRenderer.autoRotateIdle)
        assertFalse("No Modo Explorar, advanceIdleAutoRotationIfEligible deve retornar false", exploreRenderer.advanceIdleAutoRotationIfEligible())
        exploreRenderer.onDrawFrame(null)
        assertEquals(0f, exploreQuatBefore.angularDistanceDegrees(exploreRenderer.cameraQuaternion), 1e-5f)
    }

    /**
     * TESTES DE ROTAÇÃO COM DOIS DEDOS (TWO-FINGER TWIST / CAMERA ROLL) — CENÁRIOS 1 A 9:
     * 1. Torção horária com dois dedos gira o cubo no sentido horário na tela.
     * 2. Torção anti-horária com dois dedos gira o cubo no sentido anti-horário na tela.
     * 3. Torção acumulada de 180° e 360° (incluindo travessia da fronteira -180°/+180° de atan2) mantém estabilidade numérica e unitariedade do quaternion.
     * 4. Torção com dois dedos corrige inclinação lateral após rotações diagonais combinadas.
     * 5. Torção com dois dedos funciona com a mesma coerência visual quando o cubo está invertido em 180°.
     * 6. Zoom por pinça puro continua funcionando sem regressão e sem roll indevido.
     * 7. Zoom e torção simultâneos funcionam juntos de forma fluida e sem conflito.
     * 8. Movimento paralelo ou radial dos dois dedos sem variação angular não provoca roll indevido.
     * 9. Ruído angular mínimo (< twistTouchSlopDeg = 1.2°) é filtrado e não provoca tremores na câmera.
     */
    @Test
    fun testTwoFingerTwistClockwiseCounterClockwise180And360BoundaryDiagonalTiltCorrectionAndCoexistenceWithPinch() {
        val context = RuntimeEnvironment.getApplication()
        val renderer = CubeGLRenderer().apply {
            configureViewport(800, 800)
            resetCamera()
        }
        val dispatchedMoves = mutableListOf<CubeMove>()
        val glView = CubeTouchGLSurfaceView(
            context = context,
            cubeRenderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { TouchInteractionMode.MOVE_LAYERS },
            onSwipeMove = { dispatchedMoves.add(it) }
        )
        glView.arbiter.screenDensity = 1f

        data class PointerSpec(val id: Int, val x: Float, val y: Float)

        fun sendTouch(
            downTime: Long,
            eventTime: Long,
            actionMasked: Int,
            actionIndex: Int = 0,
            vararg pointers: PointerSpec
        ) {
            val props = Array(pointers.size) { idx ->
                MotionEvent.PointerProperties().apply {
                    id = pointers[idx].id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = Array(pointers.size) { idx ->
                MotionEvent.PointerCoords().apply {
                    x = pointers[idx].x
                    y = pointers[idx].y
                    pressure = 1.0f
                    size = 1.0f
                }
            }
            val combinedAction = if (actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                actionMasked == MotionEvent.ACTION_POINTER_UP
            ) {
                actionMasked or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            } else {
                actionMasked
            }
            val ev = MotionEvent.obtain(
                downTime,
                eventTime,
                combinedAction,
                pointers.size,
                props,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
            try {
                glView.onTouchEvent(ev)
            } finally {
                ev.recycle()
            }
        }

        fun pointerPairAtAngle(
            centerX: Float,
            centerY: Float,
            radius: Float,
            angleDeg: Float,
            id1: Int = 0,
            id2: Int = 1
        ): Pair<PointerSpec, PointerSpec> {
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = (radius * kotlin.math.cos(rad)).toFloat()
            val dy = (radius * kotlin.math.sin(rad)).toFloat()
            return Pair(
                PointerSpec(id1, centerX - dx, centerY - dy),
                PointerSpec(id2, centerX + dx, centerY + dy)
            )
        }

        // =========================================================================
        // 1 e 2. Torção horária (+30°) e anti-horária (-30°) com dois dedos:
        // Verifica direção visual real na tela (ponto superior da face visível gira para a direita no sentido horário
        // e de volta para a esquerda no sentido anti-horário) sem alterar a posição do olho (eyePos).
        // =========================================================================
        renderer.cameraYaw = 0f
        renderer.cameraPitch = 0f
        assertEquals(0f, renderer.cameraRoll, 1e-4f)
        val initialEye = renderer.getCameraEyePosition()
        val topFrontWorld = floatArrayOf(0f, 1.0f, 1.45f)
        val topScreenInitial = renderer.projectWorldToScreen(topFrontWorld)!!
        assertEquals(400f, topScreenInitial[0], 0.5f)
        assertTrue("Ponto superior deve projetar acima do centro (Y < 400)", topScreenInitial[1] < 350f)

        val t1 = 1000L
        val (p0Start, p1Start) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = 0f)
        sendTouch(t1, t1, MotionEvent.ACTION_DOWN, 0, p0Start)
        sendTouch(t1, t1 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, p0Start, p1Start)

        // Torce +30° no sentido horário (em coordenadas Android +Y para baixo, ângulo crescente = sentido horário)
        for (step in 1..15) {
            val angle = step * 2f
            val (p0, p1) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = angle)
            sendTouch(t1, t1 + 16 + step * 16L, MotionEvent.ACTION_MOVE, 0, p0, p1)
        }
        sendTouch(t1, t1 + 300, MotionEvent.ACTION_POINTER_UP, 1, p0Start, p1Start)
        sendTouch(t1, t1 + 316, MotionEvent.ACTION_UP, 0, p0Start)

        assertEquals("Torção horária de +30° deve acumular cameraRoll = +30°", 30f, renderer.cameraRoll, 0.2f)
        assertEquals(
            "Roll em torno do eixo de visão local não deve alterar a posição do olho da câmera",
            0f,
            vectorDistance(initialEye, renderer.getCameraEyePosition()),
            1e-4f
        )
        val topScreenAfterClockwise = renderer.projectWorldToScreen(topFrontWorld)!!
        assertTrue(
            "Após torção horária (+30°), o topo do cubo deve deslocar-se para a DIREITA na tela (X=${topScreenAfterClockwise[0]} > ${topScreenInitial[0]})",
            topScreenAfterClockwise[0] > topScreenInitial[0] + 15f
        )

        // Agora torce -60° no sentido anti-horário (de +30° para -30° líquidos)
        val t2 = 2000L
        val (p0Cw, p1Cw) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = 30f)
        sendTouch(t2, t2, MotionEvent.ACTION_DOWN, 0, p0Cw)
        sendTouch(t2, t2 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, p0Cw, p1Cw)
        for (step in 1..30) {
            val angle = 30f - step * 2f
            val (p0, p1) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = angle)
            sendTouch(t2, t2 + 16 + step * 16L, MotionEvent.ACTION_MOVE, 0, p0, p1)
        }
        sendTouch(t2, t2 + 600, MotionEvent.ACTION_POINTER_UP, 1, p0Cw, p1Cw)
        sendTouch(t2, t2 + 616, MotionEvent.ACTION_UP, 0, p0Cw)

        assertEquals("Após torção anti-horária de -60°, cameraRoll deve ser -30°", -30f, renderer.cameraRoll, 0.2f)
        val topScreenAfterCounterClockwise = renderer.projectWorldToScreen(topFrontWorld)!!
        assertTrue(
            "Após torção anti-horária (-30° líquidos), o topo do cubo deve deslocar-se para a ESQUERDA do centro (X=${topScreenAfterCounterClockwise[0]} < ${topScreenInitial[0]})",
            topScreenAfterCounterClockwise[0] < topScreenInitial[0] - 15f
        )

        // =========================================================================
        // 3. Torção acumulada de 180° e 360° (incluindo travessia de -180°/+180° de atan2 sem salto):
        // =========================================================================
        renderer.resetCamera()
        val initialDefaultQuat = renderer.cameraQuaternion
        val initialDefaultUp = renderer.getCameraUpVector()
        val initialDefaultRight = renderer.getCameraRightVector()

        val t3 = 3000L
        // Inicia em 150° para atravessar explicitamente a fronteira +180° / -180° durante a volta de 360°
        val startAngle360 = 150f
        val (p0LoopStart, p1LoopStart) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = startAngle360)
        sendTouch(t3, t3, MotionEvent.ACTION_DOWN, 0, p0LoopStart)
        sendTouch(t3, t3 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, p0LoopStart, p1LoopStart)

        var prevQuat = renderer.cameraQuaternion
        for (step in 1..180) {
            val angle = startAngle360 + step * 2f // avança +2° por evento até completar +360° (cruzando 180° -> -178° no passo 16)
            val (p0, p1) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = angle)
            sendTouch(t3, t3 + 16 + step * 16L, MotionEvent.ACTION_MOVE, 0, p0, p1)

            val currQuat = renderer.cameraQuaternion
            val stepAngularDist = prevQuat.angularDistanceDegrees(currQuat)
            assertEquals(
                "Passo $step (angle=$angle°): incremento de roll deve ser estritamente 2° sem salto na fronteira ±180°",
                2f,
                stepAngularDist,
                0.05f
            )
            assertTrue("Quaternion deve manter-se unitário no passo $step", currQuat.isNormalized(1e-4f))
            prevQuat = currQuat

            if (step == 90) {
                // Em 180° de roll acumulado: upDir e rightDir inverteram de sentido (-initialUp, -initialRight)
                assertEquals(180f, renderer.cameraRoll, 0.2f)
                val upAt180 = renderer.getCameraUpVector()
                val rightAt180 = renderer.getCameraRightVector()
                assertEquals(-initialDefaultUp[0], upAt180[0], 0.02f)
                assertEquals(-initialDefaultUp[1], upAt180[1], 0.02f)
                assertEquals(-initialDefaultUp[2], upAt180[2], 0.02f)
                assertEquals(-initialDefaultRight[0], rightAt180[0], 0.02f)
                assertEquals(-initialDefaultRight[1], rightAt180[1], 0.02f)
                assertEquals(-initialDefaultRight[2], rightAt180[2], 0.02f)
            }
        }
        sendTouch(t3, t3 + 4000, MotionEvent.ACTION_POINTER_UP, 1, p0LoopStart, p1LoopStart)
        sendTouch(t3, t3 + 4016, MotionEvent.ACTION_UP, 0, p0LoopStart)

        assertEquals("Após volta completa de 360°, cameraRoll deve registrar 360°", 360f, renderer.cameraRoll, 0.2f)
        assertEquals("normalizedCameraRoll após 360° deve ser 0°", 0f, renderer.normalizedCameraRoll, 0.2f)
        assertTrue(
            "Após 360° de torção, cameraQuaternion deve coincidir com a orientação inicial",
            renderer.cameraQuaternion.angularDistanceDegrees(initialDefaultQuat) < 0.1f
        )

        // =========================================================================
        // 4. Correção de inclinação lateral após rotações diagonais de 1 dedo:
        // =========================================================================
        renderer.cameraYaw = 0f
        renderer.cameraPitch = 45f
        // Com a câmera inclinada (pitch = 45°), orbitar no referencial local da câmera introduz inclinação lateral (rightDir.y != 0)
        renderer.orbitCamera(dx = 120f, dy = 0f)
        val rightTilted = renderer.getCameraRightVector()
        val upTilted = renderer.getCameraUpVector()
        val eyeTilted = renderer.getCameraEyePosition()
        // Como a pós-multiplicação por qRoll(theta) transforma right' = cos(theta)*right + sin(theta)*up,
        // para nivelar right'.y = 0 basta aplicar theta = atan2(-right.y, up.y):
        val correctiveRollDeg = Math.toDegrees(kotlin.math.atan2((-rightTilted[1]).toDouble(), upTilted[1].toDouble())).toFloat()
        assertTrue("Inclinação lateral deve estar presente antes da torção corretiva (medido: $correctiveRollDeg°)", abs(correctiveRollDeg) > 25f)

        renderer.rollCamera(correctiveRollDeg)
        val rightCorrected = renderer.getCameraRightVector()
        assertEquals(
            "Torção corretiva deve nivelar perfeitamente a inclinação lateral (rightDir.y == 0)",
            0f,
            rightCorrected[1],
            1e-3f
        )
        assertEquals(
            "Nivelar a inclinação lateral com roll não deve alterar o ponto de vista (eyePos)",
            0f,
            vectorDistance(eyeTilted, renderer.getCameraEyePosition()),
            1e-4f
        )

        // =========================================================================
        // 5. Torção com dois dedos quando o cubo está invertido em 180° (de cabeça para baixo):
        // =========================================================================
        renderer.cameraYaw = 0f
        renderer.cameraPitch = 180f // câmera invertida em 180° olhando de -Z para +Z com up = (0, -1, 0)
        assertTrue("Câmera deve estar invertida em 180°", renderer.isCameraUpsideDown)
        // Na câmera invertida em 180°, o ponto visualmente no TOPO da tela na face visível (-Z) é (0, -1.0, -1.45)
        val visualTopWhenInvertedWorld = floatArrayOf(0f, -1.0f, -1.45f)
        val screenBeforeTwistInverted = renderer.projectWorldToScreen(visualTopWhenInvertedWorld)!!
        assertEquals(400f, screenBeforeTwistInverted[0], 0.5f)
        assertTrue("Ponto visualmente no topo deve estar na metade superior da tela (Y < 400)", screenBeforeTwistInverted[1] < 350f)

        // Torce +25° no sentido horário com dois dedos
        renderer.rollCamera(25f)
        val screenAfterTwistInverted = renderer.projectWorldToScreen(visualTopWhenInvertedWorld)!!
        assertTrue(
            "Mesmo com o cubo invertido em 180°, torcer 2 dedos no sentido horário (+25°) deve girar o topo visual para a DIREITA na tela (antes=${screenBeforeTwistInverted[0]}, depois=${screenAfterTwistInverted[0]})",
            screenAfterTwistInverted[0] > screenBeforeTwistInverted[0] + 12f
        )

        // =========================================================================
        // 6, 7, 8 e 9. Coexistência com Zoom por Pinça, Zoom+Twist simultâneos, Movimento sem variação angular e Filtro de Ruído:
        // =========================================================================
        // 6 & 8: Pinça radial pura (distância aumenta de 200px para 360px mantendo ângulo 0°) -> altera APENAS zoom, zero roll!
        renderer.resetCamera()
        val distBeforePurePinch = renderer.cameraDistance
        val quatBeforePurePinch = renderer.cameraQuaternion
        val t6 = 6000L
        sendTouch(t6, t6, MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 300f, 400f))
        sendTouch(t6, t6 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(0, 300f, 400f), PointerSpec(1, 500f, 400f))
        sendTouch(t6, t6 + 32, MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 220f, 400f), PointerSpec(1, 580f, 400f))
        sendTouch(t6, t6 + 48, MotionEvent.ACTION_POINTER_UP, 1, PointerSpec(0, 220f, 400f), PointerSpec(1, 580f, 400f))
        sendTouch(t6, t6 + 64, MotionEvent.ACTION_UP, 0, PointerSpec(0, 220f, 400f))

        assertTrue("Pinça pura deve reduzir cameraDistance", renderer.cameraDistance < distBeforePurePinch)
        assertEquals("Pinça radial sem variação angular deve manter cameraRoll == 0°", 0f, renderer.cameraRoll, 1e-5f)
        assertEquals(
            "Pinça radial sem variação angular não deve alterar cameraQuaternion",
            0f,
            quatBeforePurePinch.angularDistanceDegrees(renderer.cameraQuaternion),
            1e-5f
        )

        // 8b: Translação paralela pura de dois dedos (ambos se movem +60px em X e +40px em Y mantendo distância e ângulo) -> nem zoom nem roll!
        renderer.resetCamera()
        val distBeforeParallel = renderer.cameraDistance
        val quatBeforeParallel = renderer.cameraQuaternion
        val t8 = 8000L
        sendTouch(t8, t8, MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 300f, 400f))
        sendTouch(t8, t8 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(0, 300f, 400f), PointerSpec(1, 500f, 400f))
        sendTouch(t8, t8 + 32, MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 330f, 420f), PointerSpec(1, 530f, 420f))
        sendTouch(t8, t8 + 48, MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 360f, 440f), PointerSpec(1, 560f, 440f))
        sendTouch(t8, t8 + 64, MotionEvent.ACTION_POINTER_UP, 1, PointerSpec(0, 360f, 440f), PointerSpec(1, 560f, 440f))
        sendTouch(t8, t8 + 80, MotionEvent.ACTION_UP, 0, PointerSpec(0, 360f, 440f))

        assertEquals("Movimento paralelo de 2 dedos não deve alterar cameraDistance", distBeforeParallel, renderer.cameraDistance, 1e-4f)
        assertEquals("Movimento paralelo de 2 dedos não deve provocar roll", 0f, renderer.cameraRoll, 1e-5f)
        assertEquals(0f, quatBeforeParallel.angularDistanceDegrees(renderer.cameraQuaternion), 1e-5f)

        // 9: Ruído angular mínimo (< twistTouchSlopDeg = 1.2°) e span constante -> filtrado sem tremores!
        renderer.resetCamera()
        val quatBeforeNoise = renderer.cameraQuaternion
        val distBeforeNoise = renderer.cameraDistance
        val t9 = 9000L
        val (p0N0, p1N0) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = 0f)
        val (p0N1, p1N1) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = 0.65f)
        val (p0N2, p1N2) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = -0.45f)
        val (p0N3, p1N3) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = 0.90f)
        sendTouch(t9, t9, MotionEvent.ACTION_DOWN, 0, p0N0)
        sendTouch(t9, t9 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, p0N0, p1N0)
        sendTouch(t9, t9 + 32, MotionEvent.ACTION_MOVE, 0, p0N1, p1N1)
        sendTouch(t9, t9 + 48, MotionEvent.ACTION_MOVE, 0, p0N2, p1N2)
        sendTouch(t9, t9 + 64, MotionEvent.ACTION_MOVE, 0, p0N3, p1N3)
        sendTouch(t9, t9 + 80, MotionEvent.ACTION_POINTER_UP, 1, p0N3, p1N3)
        sendTouch(t9, t9 + 96, MotionEvent.ACTION_UP, 0, p0N3)

        assertEquals("Ruído angular menor que 1.2° não deve alterar cameraRoll", 0f, renderer.cameraRoll, 1e-5f)
        assertEquals("Ruído angular menor que 1.2° não deve alterar cameraQuaternion", 0f, quatBeforeNoise.angularDistanceDegrees(renderer.cameraQuaternion), 1e-5f)
        assertEquals("Torção pura com raio constante não deve alterar cameraDistance", distBeforeNoise, renderer.cameraDistance, 1e-4f)

        // 7: Zoom e torção simultâneos (raio aumenta de 120px para 210px enquanto o ângulo gira +40°) -> ambos funcionam juntos!
        renderer.resetCamera()
        val distBeforeSimultaneous = renderer.cameraDistance
        val t7 = 7000L
        val (p0Sim0, p1Sim0) = pointerPairAtAngle(400f, 400f, radius = 120f, angleDeg = 0f)
        sendTouch(t7, t7, MotionEvent.ACTION_DOWN, 0, p0Sim0)
        sendTouch(t7, t7 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, p0Sim0, p1Sim0)
        for (step in 1..10) {
            val radius = 120f + step * 9f   // 120px -> 210px (span 240px -> 420px)
            val angle = step * 4f           // 0° -> +40°
            val (p0, p1) = pointerPairAtAngle(400f, 400f, radius = radius, angleDeg = angle)
            sendTouch(t7, t7 + 16 + step * 16L, MotionEvent.ACTION_MOVE, 0, p0, p1)
        }
        sendTouch(t7, t7 + 300, MotionEvent.ACTION_POINTER_UP, 1, p0Sim0, p1Sim0)
        sendTouch(t7, t7 + 316, MotionEvent.ACTION_UP, 0, p0Sim0)

        assertTrue("Durante zoom + torção simultâneos, cameraDistance deve diminuir", renderer.cameraDistance < distBeforeSimultaneous - 0.5f)
        assertEquals("Durante zoom + torção simultâneos, cameraRoll deve acumular +40°", 40f, renderer.cameraRoll, 0.25f)
        assertTrue("Nenhum movimento de camada deve ser disparado durante o multitoque", dispatchedMoves.isEmpty())

        // 9b: Rotação muito lenta dos dedos (micro-passos de 0.05° por quadro, abaixo de minTwistFrameDeltaDeg = 0.08°)
        // não pode perder deslocamento acumulado ao longo do gesto:
        renderer.resetCamera()
        val tSlow = 9500L
        val (pSlow0, pSlow1) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = 0f)
        sendTouch(tSlow, tSlow, MotionEvent.ACTION_DOWN, 0, pSlow0)
        sendTouch(tSlow, tSlow + 16, MotionEvent.ACTION_POINTER_DOWN, 1, pSlow0, pSlow1)
        // 100 micro-passos de +0.05° = +5.0° totais
        for (step in 1..100) {
            val angle = step * 0.05f
            val (p0, p1) = pointerPairAtAngle(400f, 400f, radius = 150f, angleDeg = angle)
            sendTouch(tSlow, tSlow + 16 + step * 16L, MotionEvent.ACTION_MOVE, 0, p0, p1)
        }
        sendTouch(tSlow, tSlow + 2000, MotionEvent.ACTION_POINTER_UP, 1, pSlow0, pSlow1)
        sendTouch(tSlow, tSlow + 2016, MotionEvent.ACTION_UP, 0, pSlow0)
        assertEquals(
            "Rotação muito lenta (0.05°/frame por 100 quadros) deve acumular +5.0° sem perda de micro-movimentos",
            5.0f,
            renderer.cameraRoll,
            0.15f
        )
    }

    /**
     * TESTES DE ROTAÇÃO COM DOIS DEDOS (TWO-FINGER TWIST / CAMERA ROLL) — CENÁRIOS 10 A 17:
     * 10. Troca ou remoção de ponteiros (IDs não-sequenciais, inversão de índices, 3 dedos -> 2 dedos) não causa salto brusco de orientação.
     * 11. Transição de 2 dedos para 1 dedo preserva a orientação atual e permite novo gesto de 1 dedo (órbita ou camada).
     * 12. ACTION_CANCEL encerra o gesto de dois dedos de forma limpa (cancelando prévia de camada pendente).
     * 13. Nenhum movimento matemático involuntário ocorre durante o gesto de dois dedos.
     * 14. CubeState, histórico de movimentos e algoritmos pedagógicos permanecem intactos.
     * 15. A rotação automática da Home continua funcionando normalmente após torção com dois dedos.
     * 16. O raycasting e os movimentos de camada continuam corretos após torção com dois dedos (ex.: roll de 90° e 180°).
     * 17. O botão de recentralização restaura a orientação inicial (incluindo cameraRoll = 0° e cameraQuaternion).
     */
    @Test
    fun testTwoFingerTwistPointerTransitionsCancelHomeAutoRotationRaycastingAndRecenter() {
        lateinit var vm: CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = ViewModelProvider(act)[CuboMestreViewModel::class.java]
        }
        val context = RuntimeEnvironment.getApplication()

        data class PointerSpec(val id: Int, val x: Float, val y: Float)

        fun buildEvent(
            downTime: Long,
            eventTime: Long,
            actionMasked: Int,
            actionIndex: Int = 0,
            pointers: List<PointerSpec>
        ): MotionEvent {
            val props = Array(pointers.size) { idx ->
                MotionEvent.PointerProperties().apply {
                    id = pointers[idx].id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = Array(pointers.size) { idx ->
                MotionEvent.PointerCoords().apply {
                    x = pointers[idx].x
                    y = pointers[idx].y
                    pressure = 1.0f
                    size = 1.0f
                }
            }
            val combinedAction = if (actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                actionMasked == MotionEvent.ACTION_POINTER_UP
            ) {
                actionMasked or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            } else {
                actionMasked
            }
            return MotionEvent.obtain(
                downTime,
                eventTime,
                combinedAction,
                pointers.size,
                props,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
        }

        fun pointerPairAtAngle(
            centerX: Float,
            centerY: Float,
            radius: Float,
            angleDeg: Float,
            id1: Int = 0,
            id2: Int = 1
        ): Pair<PointerSpec, PointerSpec> {
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = (radius * kotlin.math.cos(rad)).toFloat()
            val dy = (radius * kotlin.math.sin(rad)).toFloat()
            return Pair(
                PointerSpec(id1, centerX - dx, centerY - dy),
                PointerSpec(id2, centerX + dx, centerY + dy)
            )
        }

        val renderer = CubeGLRenderer().apply {
            configureViewport(800, 800)
            resetCamera()
        }
        val dispatchedMoves = mutableListOf<CubeMove>()
        var touchMode = TouchInteractionMode.MOVE_LAYERS
        val glView = CubeTouchGLSurfaceView(
            context = context,
            cubeRenderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { touchMode },
            onSwipeMove = { dispatchedMoves.add(it) }
        )
        glView.arbiter.screenDensity = 1f

        fun sendTouch(
            downTime: Long,
            eventTime: Long,
            actionMasked: Int,
            actionIndex: Int = 0,
            vararg pointers: PointerSpec
        ) {
            val ev = buildEvent(downTime, eventTime, actionMasked, actionIndex, pointers.toList())
            try {
                glView.onTouchEvent(ev)
            } finally {
                ev.recycle()
            }
        }

        // =========================================================================
        // 10 & 11. Identidade de ponteiros (IDs 4 e 9, inversão da ordem nos índices do MotionEvent,
        //          3 dedos -> 2 dedos -> 1 dedo sem salto de 180°):
        // =========================================================================
        val t10 = 10000L
        val (pA0, pB0) = pointerPairAtAngle(400f, 400f, radius = 140f, angleDeg = 0f, id1 = 4, id2 = 9)
        sendTouch(t10, t10, MotionEvent.ACTION_DOWN, 0, pA0)
        sendTouch(t10, t10 + 16, MotionEvent.ACTION_POINTER_DOWN, 1, pA0, pB0)

        // Torce +20° com os ponteiros na ordem [id=4, id=9], e depois envia os mesmos ponteiros na ordem invertida de array [id=9, id=4]!
        // Como o árbitro busca cada dedo pelo ID (findPointerIndex), inverter a ordem no array NÃO pode gerar salto de 180°!
        val (pA10, pB10) = pointerPairAtAngle(400f, 400f, radius = 140f, angleDeg = 10f, id1 = 4, id2 = 9)
        sendTouch(t10, t10 + 32, MotionEvent.ACTION_MOVE, 0, pA10, pB10)
        val (pA20, pB20) = pointerPairAtAngle(400f, 400f, radius = 140f, angleDeg = 20f, id1 = 4, id2 = 9)
        // Ordem invertida no array do evento: pB20 (id=9) no índice 0 e pA20 (id=4) no índice 1
        sendTouch(t10, t10 + 48, MotionEvent.ACTION_MOVE, 0, pB20, pA20)
        assertEquals(
            "Inverter a ordem dos índices no MotionEvent mantendo os IDs 4 e 9 não pode causar salto de 180°",
            20f,
            renderer.cameraRoll,
            0.2f
        )

        // Adiciona um 3º dedo (id=12): 3 dedos ativos não devem produzir roll inesperado
        val rollBefore3rdFinger = renderer.cameraRoll
        val pC = PointerSpec(12, 400f, 180f)
        sendTouch(t10, t10 + 64, MotionEvent.ACTION_POINTER_DOWN, 2, pA20, pB20, pC)
        sendTouch(
            t10, t10 + 80, MotionEvent.ACTION_MOVE, 0,
            PointerSpec(4, pA20.x - 15f, pA20.y + 20f),
            PointerSpec(9, pB20.x + 15f, pB20.y - 20f),
            PointerSpec(12, 420f, 160f)
        )
        assertEquals("Movimento com 3 dedos não deve alterar cameraRoll", rollBefore3rdFinger, renderer.cameraRoll, 1e-4f)

        // Retira o dedo id=4 (actionIndex=0), restando 2 dedos (id=9 e id=12) -> reancora sem salto!
        sendTouch(
            t10, t10 + 96, MotionEvent.ACTION_POINTER_UP, 0,
            PointerSpec(4, pA20.x - 15f, pA20.y + 20f),
            PointerSpec(9, pB20.x + 15f, pB20.y - 20f),
            PointerSpec(12, 420f, 160f)
        )
        assertEquals("Retirar 1 dedo passando de 3 para 2 dedos não deve causar salto em cameraRoll", rollBefore3rdFinger, renderer.cameraRoll, 1e-4f)

        // Retira mais 1 dedo (id=12, actionIndex=1), restando apenas 1 dedo (id=9) -> preserva orientação e transita para PENDING_GESTURE
        val quatBeforeSingleTransition = renderer.cameraQuaternion
        sendTouch(
            t10, t10 + 112, MotionEvent.ACTION_POINTER_UP, 1,
            PointerSpec(9, 450f, 400f),
            PointerSpec(12, 420f, 160f)
        )
        assertEquals(GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)
        assertEquals(0f, quatBeforeSingleTransition.angularDistanceDegrees(renderer.cameraQuaternion), 1e-5f)

        // Com o dedo restante (id=9), no modo ROTATE_CAMERA, arrasta para orbitar normalmente
        touchMode = TouchInteractionMode.ROTATE_CAMERA
        sendTouch(t10, t10 + 128, MotionEvent.ACTION_MOVE, 0, PointerSpec(9, 480f, 400f))
        assertEquals(GestureInteractionState.CAMERA_ORBIT, glView.arbiter.state)
        assertTrue("Dedo sobrevivente após torção deve conseguir orbitar a câmera suavemente", renderer.cameraQuaternion.angularDistanceDegrees(quatBeforeSingleTransition) > 1f)
        sendTouch(t10, t10 + 144, MotionEvent.ACTION_UP, 0, PointerSpec(9, 480f, 400f))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)

        // =========================================================================
        // 12 & 13. Cancelamento de camada pendente ao entrar o 2º dedo e ACTION_CANCEL durante torção:
        // =========================================================================
        renderer.resetCamera()
        touchMode = TouchInteractionMode.MOVE_LAYERS
        dispatchedMoves.clear()
        val frontCenterScreen = renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
        val t12 = 12000L

        // 1º dedo toca sobre a face frontal e arrasta 55% do layerSwipeSlopPx (ativa previewTouchHit e previewTouchMove sem confirmar movimento)
        sendTouch(t12, t12, MotionEvent.ACTION_DOWN, 0, PointerSpec(0, frontCenterScreen[0], frontCenterScreen[1]))
        val previewStepPx = glView.arbiter.layerSwipeSlopPx * 0.55f
        sendTouch(t12, t12 + 16, MotionEvent.ACTION_MOVE, 0, PointerSpec(0, frontCenterScreen[0], frontCenterScreen[1] + previewStepPx))
        assertNotNull("Deve haver previewTouchHit antes do 2º dedo", renderer.previewTouchHit)
        assertNotNull("Deve haver previewTouchMove antes do 2º dedo", renderer.previewTouchMove)
        assertTrue("Movimento ainda não foi confirmado", dispatchedMoves.isEmpty())

        // 2º dedo toca a tela e realiza uma torção de +15° -> cancela imediatamente o preview de camada!
        val (pTw0, pTw1) = pointerPairAtAngle(400f, 400f, radius = 140f, angleDeg = 0f)
        sendTouch(t12, t12 + 32, MotionEvent.ACTION_POINTER_DOWN, 1, pTw0, pTw1)
        assertEquals(GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)
        assertEquals("2º dedo deve limpar previewTouchHit imediatamente", null, renderer.previewTouchHit)
        assertEquals("2º dedo deve limpar previewTouchMove imediatamente", null, renderer.previewTouchMove)

        val (pTw15A, pTw15B) = pointerPairAtAngle(400f, 400f, radius = 140f, angleDeg = 15f)
        sendTouch(t12, t12 + 48, MotionEvent.ACTION_MOVE, 0, pTw15A, pTw15B)
        assertEquals(15f, renderer.cameraRoll, 0.2f)
        assertTrue("Torção com 2 dedos jamais deve disparar movimento de camada", dispatchedMoves.isEmpty())

        // ACTION_CANCEL durante a torção encerra o gesto de forma limpa
        sendTouch(t12, t12 + 64, MotionEvent.ACTION_CANCEL, 0, pTw15A, pTw15B)
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)
        assertEquals(0, glView.activePointerCount)
        assertFalse("Após ACTION_CANCEL, isUserTouching deve ser false", renderer.isUserTouching)
        assertTrue("Nenhum movimento involuntário após ACTION_CANCEL", dispatchedMoves.isEmpty())

        // =========================================================================
        // 16. Raycasting e resolução de movimentos de camada (resolveMoveFromSwipe) após torção com 2 dedos:
        // Se o cubo estiver olhando para a face FRONT (yaw=0°, pitch=0°) mas girado em +90° (sentido horário) por torção,
        // a face R (x = +1) agora está na parte INFERIOR da tela, e a face U (y = +1) está na DIREITA da tela!
        // =========================================================================
        renderer.cameraYaw = 0f
        renderer.cameraPitch = 0f
        renderer.rollCamera(90f) // gira o cubo +90° no sentido horário na tela

        // Projeta a peça (x=0, y=+1, z=+1.45) da camada U: após +90° horários, ela deve estar à DIREITA do centro (X > 440, Y ~ 400)
        val uEdgeAfter90Roll = renderer.projectWorldToScreen(floatArrayOf(0f, 0.95f, 1.45f))!!
        assertTrue(
            "Após roll horário de +90°, a aresta U deve estar à direita do centro na tela (X=${uEdgeAfter90Roll[0]})",
            uEdgeAfter90Roll[0] > 440f && abs(uEdgeAfter90Roll[1] - 400f) < 15f
        )
        val hitUAfter90Roll = renderer.pickCubieAtScreen(uEdgeAfter90Roll[0], uEdgeAfter90Roll[1])
        assertNotNull("Raycasting deve atingir a peça após torção de +90°", hitUAfter90Roll)
        assertEquals(CubeFace.FRONT, hitUAfter90Roll!!.hitFace)
        assertEquals(0, hitUAfter90Roll.cubieX)
        assertEquals(1, hitUAfter90Roll.cubieY)

        // Na visão normal (0°), o movimento U desloca a face frontal para a ESQUERDA (-X_screen).
        // Após girar o cubo +90° no sentido horário na tela, esse mesmo movimento físico U desloca a face frontal para CIMA (-Y_screen)!
        dispatchedMoves.clear()
        touchMode = TouchInteractionMode.MOVE_LAYERS
        val t16 = 16000L
        sendTouch(t16, t16, MotionEvent.ACTION_DOWN, 0, PointerSpec(0, uEdgeAfter90Roll[0], uEdgeAfter90Roll[1]))
        val fullSwipePx = glView.arbiter.layerSwipeSlopPx * 1.5f
        sendTouch(t16, t16 + 16, MotionEvent.ACTION_MOVE, 0, PointerSpec(0, uEdgeAfter90Roll[0], uEdgeAfter90Roll[1] - fullSwipePx))
        sendTouch(t16, t16 + 32, MotionEvent.ACTION_UP, 0, PointerSpec(0, uEdgeAfter90Roll[0], uEdgeAfter90Roll[1] - fullSwipePx))
        assertEquals(
            "Após torção de +90°, arrastar verticalmente para cima na camada U (que está à direita na tela) deve resolver U",
            listOf(CubeMove.U),
            dispatchedMoves
        )

        // =========================================================================
        // 14, 15 e 17. Integração com Home (rotação automática após torção), Explorar, Aprender e Botão Recentralizar:
        // =========================================================================
        composeTestRule.runOnUiThread {
            vm.navigateTo(AppScreen.HOME)
            vm.resetHomePreviewCube()
        }
        composeTestRule.waitForIdle()

        val homeRenderer = Cube3DViewAuditHolder.lastActiveRenderer!!
        homeRenderer.configureViewport(800, 800)
        homeRenderer.resetCamera()
        homeRenderer.clearAutoRotateCooldownForTest()
        val homeCubeBefore = vm.homePreviewState.value.cubeState

        // Executa torção de +35° com dois dedos na Home via CubeGestureArbiter
        val homeArbiter = CubeGestureArbiter(
            renderer = homeRenderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { TouchInteractionMode.MOVE_LAYERS }
        ).apply {
            screenDensity = 1f
        }
        homeArbiter.onDown(260f, 400f, pointerId = 0)
        homeArbiter.onPointerDown(2, activeX = 260f, activeY = 400f, primaryPointerId = 0)
        homeArbiter.anchorTwoFingerTwist(0, 260f, 400f, 1, 540f, 400f)
        val (hP0, hP1) = pointerPairAtAngle(400f, 400f, radius = 140f, angleDeg = 35f)
        homeArbiter.updateTwoFingerTwist(0, hP0.x, hP0.y, 1, hP1.x, hP1.y)
        assertEquals(35f, homeRenderer.cameraRoll, 0.2f)
        assertFalse("Enquanto os 2 dedos estão na tela, a Home não deve auto-girar", homeRenderer.advanceIdleAutoRotationIfEligible())

        homeArbiter.onUpOrCancel()
        val quatAfterHomeTwist = homeRenderer.cameraQuaternion

        // Após 1900ms de inatividade, a rotação automática da Home retoma suavemente a partir da orientação torcida!
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1900))
        assertTrue("Rotação automática da Home deve retomar após torção com 2 dedos", homeRenderer.advanceIdleAutoRotationIfEligible())
        assertEquals(
            "Retomada da rotação automática após torção deve partir da orientação atual (passo de 0.35° sem salto)",
            0.35f,
            quatAfterHomeTwist.angularDistanceDegrees(homeRenderer.cameraQuaternion),
            0.02f
        )
        assertEquals("Estado matemático do cubo na Home deve permanecer intacto", homeCubeBefore, vm.homePreviewState.value.cubeState)

        // 17. Botão de recentralização restaura yaw, pitch, roll = 0° e cameraQuaternion inicial
        composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(CubeGLRenderer.DEFAULT_YAW, homeRenderer.cameraYaw, 0.01f)
        assertEquals(CubeGLRenderer.DEFAULT_PITCH, homeRenderer.cameraPitch, 0.01f)
        assertEquals("Recentralizar câmera deve zerar cameraRoll", 0f, homeRenderer.cameraRoll, 0.01f)
        val defaultQuat = CameraQuaternion.fromYawPitch(CubeGLRenderer.DEFAULT_YAW, CubeGLRenderer.DEFAULT_PITCH)
        assertTrue(
            "Recentralizar câmera deve restaurar exatamente o quaternion padrão",
            homeRenderer.cameraQuaternion.angularDistanceDegrees(defaultQuat) < 0.01f
        )

        // Verifica também que torção no Modo Explorar preserva CubeState, histórico e algoritmos pedagógicos
        composeTestRule.runOnUiThread {
            vm.navigateTo(AppScreen.EXPLORE)
            vm.resetExploreCubeSolved()
            vm.executeExploreMove(CubeMove.F)
        }
        composeTestRule.waitForIdle()
        val exploreCubeBefore = vm.exploreState.value.cubeState
        val exploreHistoryBefore = vm.exploreState.value.moveHistory.toList()
        val exploreRenderer = Cube3DViewAuditHolder.lastActiveRenderer!!
        exploreRenderer.rollCamera(-45f)
        assertEquals(-45f, exploreRenderer.cameraRoll, 0.01f)
        assertEquals("Torção no Modo Explorar não altera CubeState", exploreCubeBefore, vm.exploreState.value.cubeState)
        assertEquals("Torção no Modo Explorar não altera histórico", exploreHistoryBefore, vm.exploreState.value.moveHistory)
        assertEquals("F U R U' R' F' F U R U' R' F'", CurriculumRepository.getStep(4).cases[1].fullResolutionAlgorithm)

        composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(0f, exploreRenderer.cameraRoll, 0.01f)
        assertTrue(exploreRenderer.cameraQuaternion.angularDistanceDegrees(defaultQuat) < 0.01f)
    }
}

