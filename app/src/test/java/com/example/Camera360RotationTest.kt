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
}
