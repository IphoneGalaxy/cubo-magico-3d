package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProgressRepository
import com.example.data.StepStatus
import com.example.model.CubeColor
import com.example.model.CubeFace
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.ActiveLayerAnimation
import com.example.renderer.CubeGLRenderer
import com.example.renderer.CubeGestureArbiter
import com.example.renderer.GestureInteractionState
import com.example.renderer.TouchInteractionMode
import com.example.viewmodel.ExplanationPanelState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTrace
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `auditoria deterministica da maquina de estados de gestos 3D camada vs camera vs zoom`() {
        val renderer = CubeGLRenderer()
        renderer.configureViewport(800, 800)
        renderer.resetCamera()

        var cubeState = CubeState.createSolved()
        val dispatchedMoves = mutableListOf<CubeMove>()
        var isAnimating = false
        var touchMode = TouchInteractionMode.MOVE_LAYERS

        val arbiter = CubeGestureArbiter(
            renderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { isAnimating },
            touchModeProvider = { touchMode },
            onLayerMoveResolved = { move ->
                dispatchedMoves.add(move)
                cubeState = cubeState.applyMove(move)
            }
        )

        // 1. Gesto de CAMADA (F, R, U, L, D, B, M): NUNCA deve alterar cameraYaw, cameraPitch ou cameraDistance
        val initialYaw = renderer.cameraYaw
        val initialPitch = renderer.cameraPitch
        val initialDist = renderer.cameraDistance

        val ufrFrontScreen = renderer.projectWorldToScreen(floatArrayOf(0.95f, 0.95f, 1.45f))!!
        arbiter.onDown(ufrFrontScreen[0], ufrFrontScreen[1])
        assertEquals(GestureInteractionState.PENDING_GESTURE, arbiter.state)

        // Arrasta para cima sobre a coluna direita da face frontal -> deve executar R sem tocar na câmera
        arbiter.onMove(ufrFrontScreen[0] + 15f, ufrFrontScreen[1] - 90f)
        assertEquals(GestureInteractionState.LAYER_ROTATION, arbiter.state)
        assertEquals(listOf(CubeMove.R), dispatchedMoves)
        assertEquals("cameraYaw deve permanecer 100% inalterado durante giro de camada", initialYaw, renderer.cameraYaw, 0.0f)
        assertEquals("cameraPitch deve permanecer 100% inalterado durante giro de camada", initialPitch, renderer.cameraPitch, 0.0f)
        assertEquals("cameraDistance deve permanecer 100% inalterado durante giro de camada", initialDist, renderer.cameraDistance, 0.0f)

        // Continuação do arrasto no mesmo gesto não deve disparar segundo movimento nem mover a câmera
        arbiter.onMove(ufrFrontScreen[0] + 30f, ufrFrontScreen[1] - 180f)
        assertEquals(1, dispatchedMoves.size)
        assertEquals(initialYaw, renderer.cameraYaw, 0.0f)
        assertEquals(initialPitch, renderer.cameraPitch, 0.0f)

        arbiter.onUpOrCancel()
        assertEquals(GestureInteractionState.IDLE, arbiter.state)

        // 2. Toque iniciado sobre o cubo ENQUANTO uma animação está ativa:
        //    JAMAIS deve cair no fallback de girar a câmera! E assim que a animação termina, executa o giro da camada.
        isAnimating = true
        val frontCenterScreen = renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
        arbiter.onDown(frontCenterScreen[0], frontCenterScreen[1])
        assertEquals(GestureInteractionState.ANIMATING, arbiter.state)

        arbiter.onMove(frontCenterScreen[0], frontCenterScreen[1] + 50f)
        assertEquals("Câmera não pode girar quando o usuário toca no cubo durante animação", initialYaw, renderer.cameraYaw, 0.0f)
        assertEquals("Câmera não pode girar quando o usuário toca no cubo durante animação", initialPitch, renderer.cameraPitch, 0.0f)

        // Animação encerra no meio do gesto do usuário -> o movimento M deve ser reconhecido sem mexer na câmera
        isAnimating = false
        arbiter.onMove(frontCenterScreen[0], frontCenterScreen[1] + 110f)
        assertEquals(GestureInteractionState.LAYER_ROTATION, arbiter.state)
        assertEquals(CubeMove.M, dispatchedMoves.last())
        assertEquals(initialYaw, renderer.cameraYaw, 0.0f)
        assertEquals(initialPitch, renderer.cameraPitch, 0.0f)
        arbiter.onUpOrCancel()

        // 3. No modo Mover Camadas (TouchInteractionMode.MOVE_LAYERS), mesmo se o toque começar fora da silhueta
        //    do cubo, a câmera permanece 100% travada (JAMAIS orbita involuntariamente!)
        val stateBeforeOutsideTouch = cubeState
        val movesBeforeOutsideTouch = dispatchedMoves.size
        arbiter.onDown(20f, 20f) // Fora da malha do cubo, mas em Modo Camadas
        assertEquals(GestureInteractionState.PENDING_GESTURE, arbiter.state)
        arbiter.onMove(80f, 50f) // Continua fora da malha do cubo
        assertEquals("Em Modo Camadas, arrastar fora do cubo jamais pode orbitar a câmera", initialYaw, renderer.cameraYaw, 0.0f)
        assertEquals("Em Modo Camadas, arrastar fora do cubo jamais pode orbitar a câmera", initialPitch, renderer.cameraPitch, 0.0f)
        assertEquals(movesBeforeOutsideTouch, dispatchedMoves.size)
        arbiter.onUpOrCancel()

        // 4. Modo explícito "Girar câmera" (TouchInteractionMode.ROTATE_CAMERA):
        //    Arrastar orbita a câmera e soltar o dedo para imediatamente no ângulo escolhido sem alterar o CubeState
        renderer.resetCamera()
        touchMode = TouchInteractionMode.ROTATE_CAMERA
        val centerPt = renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
        arbiter.onDown(centerPt[0], centerPt[1])
        arbiter.onMove(centerPt[0] + 120f, centerPt[1] - 80f)
        assertEquals(GestureInteractionState.CAMERA_ORBIT, arbiter.state)
        assertEquals("Modo Girar Câmera não pode executar movimentos de camada", movesBeforeOutsideTouch, dispatchedMoves.size)
        assertEquals("CubeState deve permanecer idêntico após orbitar a câmera", stateBeforeOutsideTouch, cubeState)
        val yawAtFingerUp = renderer.cameraYaw
        val pitchAtFingerUp = renderer.cameraPitch
        assertTrue("Modo Girar Câmera deve rotacionar cameraYaw", yawAtFingerUp != CubeGLRenderer.DEFAULT_YAW)
        assertTrue("Modo Girar Câmera deve rotacionar cameraPitch", pitchAtFingerUp != CubeGLRenderer.DEFAULT_PITCH)
        arbiter.onUpOrCancel()
        assertEquals(GestureInteractionState.IDLE, arbiter.state)
        // Após soltar o dedo, eventos subsequentes sem novo toque não movem a câmera
        arbiter.onMove(centerPt[0] + 240f, centerPt[1] - 160f)
        assertEquals("Câmera deve parar imediatamente ao soltar o dedo", yawAtFingerUp, renderer.cameraYaw, 0.0f)
        assertEquals("Câmera deve parar imediatamente ao soltar o dedo", pitchAtFingerUp, renderer.cameraPitch, 0.0f)
        touchMode = TouchInteractionMode.MOVE_LAYERS

        // 5. Gesto de PINÇA (Zoom com 2 dedos): cancela qualquer giro pendente e altera apenas cameraDistance
        renderer.resetCamera()
        val ptOnCube = renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
        arbiter.onDown(ptOnCube[0], ptOnCube[1])
        arbiter.onPointerDown(2) // Segundo dedo toca a tela
        assertEquals(GestureInteractionState.PINCH_ZOOM, arbiter.state)
        arbiter.onPinchScale(1.25f)
        arbiter.onMove(ptOnCube[0] + 100f, ptOnCube[1] - 100f)
        assertEquals("Pinça de zoom jamais pode disparar movimento de camada", movesBeforeOutsideTouch, dispatchedMoves.size)
        assertEquals("Pinça de zoom não altera cameraYaw", CubeGLRenderer.DEFAULT_YAW, renderer.cameraYaw, 0.0f)
        assertTrue("Pinça de zoom deve aproximar cameraDistance", renderer.cameraDistance < CubeGLRenderer.DEFAULT_DISTANCE)
        arbiter.onUpOrCancel()

        // 6. Consistência de movimentos sob ângulos inclinados de câmera (yaw = 48°, pitch = -34°)
        renderer.cameraYaw = 48f
        renderer.cameraPitch = -34f
        renderer.cameraDistance = 6.7f
        renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 0f))

        val tiltedTopEdge = renderer.projectWorldToScreen(floatArrayOf(0f, 0.95f, 1.45f))!!
        val hitTiltedTop = renderer.pickCubieAtScreen(tiltedTopEdge[0], tiltedTopEdge[1])
        assertNotNull("Raycasting deve encontrar a peça superior frontal sob câmera inclinada", hitTiltedTop)
        val resolvedU = renderer.resolveMoveFromSwipe(hitTiltedTop!!, -120f, -25f)
        assertEquals("Arrasto horizontal para a esquerda no topo deve resolver U mesmo em câmera inclinada", CubeMove.U, resolvedU)
    }

    @Test
    fun `teste obrigatorio A e D - animacao fisica 3D em 0 25 50 75 100 por cento e sincronizacao das 27 pecas e 54 adesivos`() {
        val renderer = CubeGLRenderer()
        renderer.buildGeometryBuffers()

        // 1. Verifica malha fechada chanfrada (44 triângulos = 132 vértices por peça) e adesivos octogonais (24 vértices em 6 faces)
        assertEquals(132, renderer.getBodyVertexCountForAudit())
        assertEquals(24, renderer.getStickerVertexCountForAudit())
        assertEquals(6, renderer.getStickerFaceCountForAudit())

        val solved = CubeState.createSolved()

        // 2. TESTE A: Verifica quadros intermediários em 0%, 25%, 50%, 75% e 100% para R, U, M, E, S e R2
        val auditedMoves = listOf(CubeMove.R, CubeMove.U, CubeMove.M, CubeMove.E, CubeMove.S, CubeMove.R2)
        val checkpoints = listOf(0.0f, 0.25f, 0.50f, 0.75f, 1.0f)

        for (move in auditedMoves) {
            val rotatingCubies = solved.cubies.filter { move.affectsCubie(it) }
            val staticCubies = solved.cubies.filter { !move.affectsCubie(it) }
            assertEquals("Exatamente 9 peças devem pertencer à camada de ${move.notation}", 9, rotatingCubies.size)
            assertEquals("Exatamente 18 peças devem permanecer imóveis durante ${move.notation}", 18, staticCubies.size)

            // Escolhe uma peça da camada que não está no eixo central de rotação para medir seu deslocamento espacial 3D
            val sampleCubie = rotatingCubies.first { cubie ->
                when (move.axis) {
                    com.example.model.RotationAxis.X -> cubie.y != 0 || cubie.z != 0
                    com.example.model.RotationAxis.Y -> cubie.x != 0 || cubie.z != 0
                    com.example.model.RotationAxis.Z -> cubie.x != 0 || cubie.y != 0
                }
            }

            val positionsAtCheckpoints = checkpoints.map { progress ->
                val anim = ActiveLayerAnimation(move, progress)
                // Verifica que as 18 peças fora da camada permanecem 100% imóveis em todos os quadros
                for (staticCubie in staticCubies) {
                    val pos0 = renderer.computeCubieWorldCenter(staticCubie, null)
                    val posAnim = renderer.computeCubieWorldCenter(staticCubie, anim)
                    val staticDelta = sqrt(
                        (pos0[0] - posAnim[0]).let { it * it } +
                            (pos0[1] - posAnim[1]).let { it * it } +
                            (pos0[2] - posAnim[2]).let { it * it }
                    )
                    assertEquals(
                        "Peça fora da camada (${staticCubie.id}) não pode se mover em progress=$progress durante ${move.notation}",
                        0.0f,
                        staticDelta,
                        1e-5f
                    )
                }
                renderer.computeCubieWorldCenter(sampleCubie, anim)
            }

            // Verifica que cada marco (0% -> 25% -> 50% -> 75% -> 100%) produz uma posição 3D distinta e contínua no arco de rotação
            for (k in 0 until positionsAtCheckpoints.lastIndex) {
                val pA = positionsAtCheckpoints[k]
                val pB = positionsAtCheckpoints[k + 1]
                val stepDist = sqrt(
                    (pB[0] - pA[0]).let { it * it } +
                        (pB[1] - pA[1]).let { it * it } +
                        (pB[2] - pA[2]).let { it * it }
                )
                assertTrue(
                    "Movimento ${move.notation} deve deslocar fisicamente as peças entre ${checkpoints[k] * 100}% e ${checkpoints[k + 1] * 100}% (stepDist=$stepDist)",
                    stepDist > 0.15f
                )
            }
        }

        // 3. Verifica que o ViewModel emite todos os quadros intermediários (incluindo 0%, 25%, 50%, 75%, 100%)
        //    antes de atualizar o CubeState quando a animação multi-frame está ativa
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        vm.forceMultiFrameAnimationInTest = true
        vm.recordedAnimationFramesForAudit.clear()
        composeTestRule.runOnUiThread {
            vm.openExploreScreen()
            vm.resetExploreCubeSolved()
            vm.executeExploreMove(CubeMove.R)
        }
        // Avança o relógio do MainLooper do Robolectric em incrementos de 16ms para disparar cada quadro de delay(16L)
        repeat(40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
            composeTestRule.waitForIdle()
        }
        vm.forceMultiFrameAnimationInTest = false

        val recordedRFrames = vm.recordedAnimationFramesForAudit.filter { it.move == CubeMove.R }
        assertTrue(
            "ViewModel deve emitir múltiplos quadros intermediários para R (encontrados: ${recordedRFrames.size})",
            recordedRFrames.size >= 16
        )
        for (cp in checkpoints) {
            assertTrue(
                "Marco de progresso ${cp * 100}% deve estar presente nos quadros emitidos pelo ViewModel",
                recordedRFrames.any { kotlin.math.abs(it.progress - cp) < 1e-3f }
            )
        }
        assertEquals(CubeState.createSolved().applyMove(CubeMove.R), vm.exploreState.value.cubeState)

        // 4. TESTE D: Verifica continuidade geométrica de TODOS os 27 movimentos (F, R, U, L, D, B, M, E, S, inversos e duplos):
        //    - Zero sobreposição física entre as 27 peças durante a rotação
        //    - Zero salto visual (0.000f) na transição progress=1.0f -> CubeState.applyMove(move)
        val baseState = CubeState.createSolved().applySequence("R U F' L D' M E S'")
        assertEquals(27, baseState.cubies.size)
        assertEquals(54, baseState.cubies.sumOf { it.stickers.size })
        assertEquals(27, CubeMove.entries.size)

        for (move in CubeMove.entries) {
            val postMoveState = baseState.applyMove(move)

            for (step in 0..4) {
                val progress = step / 4f
                val anim = ActiveLayerAnimation(move, progress)
                val centers = baseState.cubies.map { cubie ->
                    renderer.computeCubieWorldCenter(cubie, anim)
                }
                for (i in centers.indices) {
                    for (j in i + 1 until centers.size) {
                        val dx = centers[i][0] - centers[j][0]
                        val dy = centers[i][1] - centers[j][1]
                        val dz = centers[i][2] - centers[j][2]
                        val dist = sqrt(dx * dx + dy * dy + dz * dz)
                        assertTrue(
                            "Sobreposição entre peças $i e $j durante ${move.notation} em progress=$progress (dist=$dist)",
                            dist >= 0.975f
                        )
                    }
                }
            }

            val animEnd = ActiveLayerAnimation(move, 1.0f)
            for (beforeCubie in baseState.cubies) {
                val afterCubie = postMoveState.cubies.first { it.id == beforeCubie.id }
                val centerAtAnimEnd = renderer.computeCubieWorldCenter(beforeCubie, animEnd)
                val centerPostState = renderer.computeCubieWorldCenter(afterCubie, null)

                val centerDelta = sqrt(
                    (centerAtAnimEnd[0] - centerPostState[0]).let { it * it } +
                        (centerAtAnimEnd[1] - centerPostState[1]).let { it * it } +
                        (centerAtAnimEnd[2] - centerPostState[2]).let { it * it }
                )
                assertTrue(
                    "Salto visual na peça ${beforeCubie.id} ao finalizar ${move.notation}: delta=$centerDelta",
                    centerDelta < 1e-4f
                )

                for ((beforeFace, color) in beforeCubie.stickers) {
                    val (animStickerPos, animStickerNorm) =
                        renderer.computeStickerWorldCenterAndNormal(beforeCubie, beforeFace, animEnd)

                    val matchingAfterFace = afterCubie.stickers.entries.firstOrNull { entry ->
                        entry.value == color && run {
                            val (postPos, _) = renderer.computeStickerWorldCenterAndNormal(afterCubie, entry.key, null)
                            val d = sqrt(
                                (animStickerPos[0] - postPos[0]).let { v -> v * v } +
                                    (animStickerPos[1] - postPos[1]).let { v -> v * v } +
                                    (animStickerPos[2] - postPos[2]).let { v -> v * v }
                            )
                            d < 1e-3f
                        }
                    }
                    assertNotNull(
                        "Adesivo $color da peça ${beforeCubie.id} dessincronizado após ${move.notation}",
                        matchingAfterFace
                    )
                    val (_, postStickerNorm) =
                        renderer.computeStickerWorldCenterAndNormal(afterCubie, matchingAfterFace!!.key, null)
                    val normDelta = sqrt(
                        (animStickerNorm[0] - postStickerNorm[0]).let { it * it } +
                            (animStickerNorm[1] - postStickerNorm[1]).let { it * it } +
                            (animStickerNorm[2] - postStickerNorm[2]).let { it * it }
                    )
                    assertTrue(
                        "Normal do adesivo dessincronizada após ${move.notation}: normDelta=$normDelta",
                        normDelta < 1e-3f
                    )
                }
            }
        }

        // 5. Verifica enquadramento em múltiplos tamanhos e proporções de tela durante movimentos em 45°
        val viewports = listOf(
            320 to 480,   // Compacto retrato
            360 to 780,   // Smartphone estreito retrato
            600 to 600,   // Quadrado / dobrável
            780 to 360,   // Paisagem
            800 to 1280,  // Tablet retrato
            1280 to 800   // Tablet paisagem
        )
        for ((width, height) in viewports) {
            renderer.configureViewport(width, height)
            renderer.resetCamera()
            for (move in listOf(CubeMove.F, CubeMove.R, CubeMove.U, CubeMove.L, CubeMove.D, CubeMove.B, CubeMove.M, CubeMove.E, CubeMove.S)) {
                val midAnim = ActiveLayerAnimation(move, 0.5f)
                for (cubie in solved.cubies) {
                    val worldCenter = renderer.computeCubieWorldCenter(cubie, midAnim)
                    val screenPt = renderer.projectWorldToScreen(worldCenter)
                    assertNotNull(screenPt)
                    val sx = screenPt!![0]
                    val sy = screenPt[1]
                    assertTrue(
                        "Peça ${cubie.id} fora da margem horizontal ($sx) em ${width}x$height",
                        sx > width * 0.08f && sx < width * 0.92f
                    )
                    assertTrue(
                        "Peça ${cubie.id} fora da margem vertical ($sy) em ${width}x$height",
                        sy > height * 0.08f && sy < height * 0.92f
                    )
                }
            }
        }
    }

    @Test
    fun `teste obrigatorio B e C - manipulacao manual das 3 linhas U E D e 3 colunas L M R e F S B em multiplos angulos de camera e extremidade direita`() {
        val renderer = CubeGLRenderer()
        renderer.configureViewport(900, 900)
        renderer.resetCamera()

        val dispatchedMoves = mutableListOf<CubeMove>()
        val arbiter = CubeGestureArbiter(
            renderer = renderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { TouchInteractionMode.MOVE_LAYERS },
            onLayerMoveResolved = { move -> dispatchedMoves.add(move) }
        )

        fun performSwipeBetweenWorldPoints(startWorld: FloatArray, endWorld: FloatArray): CubeMove? {
            dispatchedMoves.clear()
            val p0 = renderer.projectWorldToScreen(startWorld)!!
            val p1 = renderer.projectWorldToScreen(endWorld)!!
            val yawBefore = renderer.cameraYaw
            val pitchBefore = renderer.cameraPitch

            arbiter.onDown(p0[0], p0[1])
            // Interpola 4 pontos ao longo do arrasto como um dedo real
            for (step in 1..4) {
                val t = step / 4f
                arbiter.onMove(
                    p0[0] + (p1[0] - p0[0]) * t,
                    p0[1] + (p1[1] - p0[1]) * t
                )
            }
            arbiter.onUpOrCancel()

            // TESTE C: Garante que nenhum gesto de camada jamais alterou cameraYaw ou cameraPitch
            assertEquals("cameraYaw não pode mudar em Modo Camadas", yawBefore, renderer.cameraYaw, 0.0f)
            assertEquals("cameraPitch não pode mudar em Modo Camadas", pitchBefore, renderer.cameraPitch, 0.0f)
            return dispatchedMoves.lastOrNull()
        }

        // 1. Ângulo padrão da câmera (yaw = -34°, pitch = -24°):
        //    Testa todas as 3 linhas horizontais na face frontal (U, E, D) nos dois sentidos
        assertEquals(CubeMove.U, performSwipeBetweenWorldPoints(floatArrayOf(0.8f, 1.0f, 1.48f), floatArrayOf(-0.8f, 1.0f, 1.48f)))
        assertEquals(CubeMove.U_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(-0.8f, 1.0f, 1.48f), floatArrayOf(0.8f, 1.0f, 1.48f)))

        assertEquals(CubeMove.E_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(0.8f, 0.0f, 1.48f), floatArrayOf(-0.8f, 0.0f, 1.48f)))
        assertEquals(CubeMove.E, performSwipeBetweenWorldPoints(floatArrayOf(-0.8f, 0.0f, 1.48f), floatArrayOf(0.8f, 0.0f, 1.48f)))

        assertEquals(CubeMove.D_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(0.8f, -1.0f, 1.48f), floatArrayOf(-0.8f, -1.0f, 1.48f)))
        assertEquals(CubeMove.D, performSwipeBetweenWorldPoints(floatArrayOf(-0.8f, -1.0f, 1.48f), floatArrayOf(0.8f, -1.0f, 1.48f)))

        //    Testa todas as 3 colunas verticais na face frontal (L, M, R) nos dois sentidos
        assertEquals(CubeMove.L, performSwipeBetweenWorldPoints(floatArrayOf(-1.0f, 0.8f, 1.48f), floatArrayOf(-1.0f, -0.8f, 1.48f)))
        assertEquals(CubeMove.L_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(-1.0f, -0.8f, 1.48f), floatArrayOf(-1.0f, 0.8f, 1.48f)))

        assertEquals(CubeMove.M, performSwipeBetweenWorldPoints(floatArrayOf(0.0f, 0.8f, 1.48f), floatArrayOf(0.0f, -0.8f, 1.48f)))
        assertEquals(CubeMove.M_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(0.0f, -0.8f, 1.48f), floatArrayOf(0.0f, 0.8f, 1.48f)))

        assertEquals(CubeMove.R, performSwipeBetweenWorldPoints(floatArrayOf(1.0f, -0.8f, 1.48f), floatArrayOf(1.0f, 0.8f, 1.48f)))
        assertEquals(CubeMove.R_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(1.0f, 0.8f, 1.48f), floatArrayOf(1.0f, -0.8f, 1.48f)))

        //    Testa especificamente a extremidade direita / crista chanfrada da face frontal (x = 1.42, z = 1.40)
        assertEquals(
            "Arrasto vertical na extremidade direita da face frontal deve girar R",
            CubeMove.R,
            performSwipeBetweenWorldPoints(floatArrayOf(1.42f, -0.7f, 1.40f), floatArrayOf(1.42f, 0.7f, 1.40f))
        )

        //    Testa todas as 3 fatias laterais visíveis na face direita/topo (F, S, B)
        assertEquals(CubeMove.F, performSwipeBetweenWorldPoints(floatArrayOf(1.48f, 0.8f, 1.0f), floatArrayOf(1.48f, -0.8f, 1.0f)))
        assertEquals(CubeMove.F_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(1.48f, -0.8f, 1.0f), floatArrayOf(1.48f, 0.8f, 1.0f)))

        assertEquals(CubeMove.S, performSwipeBetweenWorldPoints(floatArrayOf(1.48f, 0.8f, 0.0f), floatArrayOf(1.48f, -0.8f, 0.0f)))
        assertEquals(CubeMove.S_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(1.48f, -0.8f, 0.0f), floatArrayOf(1.48f, 0.8f, 0.0f)))

        assertEquals(CubeMove.B_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(1.48f, 0.8f, -1.0f), floatArrayOf(1.48f, -0.8f, -1.0f)))
        assertEquals(CubeMove.B, performSwipeBetweenWorldPoints(floatArrayOf(1.48f, -0.8f, -1.0f), floatArrayOf(1.48f, 0.8f, -1.0f)))

        // 2. Repete o teste após girar a câmera para outro ângulo (yaw = -34°, pitch = 28°, vendo Frente + Esquerda + Topo)
        renderer.cameraYaw = -34f
        renderer.cameraPitch = 28f
        renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 0f))

        assertEquals(CubeMove.U, performSwipeBetweenWorldPoints(floatArrayOf(0.8f, 1.0f, 1.48f), floatArrayOf(-0.8f, 1.0f, 1.48f)))
        assertEquals(CubeMove.E_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(0.8f, 0.0f, 1.48f), floatArrayOf(-0.8f, 0.0f, 1.48f)))
        assertEquals(CubeMove.D_PRIME, performSwipeBetweenWorldPoints(floatArrayOf(0.8f, -1.0f, 1.48f), floatArrayOf(-0.8f, -1.0f, 1.48f)))

        assertEquals(CubeMove.L, performSwipeBetweenWorldPoints(floatArrayOf(-1.0f, 0.8f, 1.48f), floatArrayOf(-1.0f, -0.8f, 1.48f)))
        assertEquals(CubeMove.M, performSwipeBetweenWorldPoints(floatArrayOf(0.0f, 0.8f, 1.48f), floatArrayOf(0.0f, -0.8f, 1.48f)))
        assertEquals(CubeMove.R, performSwipeBetweenWorldPoints(floatArrayOf(1.0f, -0.8f, 1.48f), floatArrayOf(1.0f, 0.8f, 1.48f)))
        // Sob yaw = -34°, a coluna direita (x = +1.42) fica na extremidade direita da tela!
        assertEquals(
            "Sob câmera yaw=-34°, a coluna direita na extremidade direita da tela deve responder com R",
            CubeMove.R,
            performSwipeBetweenWorldPoints(floatArrayOf(1.42f, -0.7f, 1.42f), floatArrayOf(1.42f, 0.7f, 1.42f))
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `auditoria completa de UX nativa sem rolagem de pagina painel expansivel 3 estados reproducao pratica e DataStore`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Cubo Mestre 3D", context.getString(R.string.app_name))

        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        // 1. Tela Inicial Nativa: todos os elementos essenciais visíveis e clicáveis SEM rolagem de página
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cube_3d_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("student_progress_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_learning_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explore_cube_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notation_guide_button").assertIsDisplayed()

        // Modal de Notação
        composeTestRule.onNodeWithTag("notation_guide_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notation_guide_modal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_notation_modal_button").performClick()
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        // 2. Navega para a Tela do Curso e entra na Aula da Etapa 1 (sem precisar rolar a Home)
        composeTestRule.onNodeWithTag("start_learning_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("course_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("step_1_learn_btn").performClick()
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        // 3. Tela de Aula com Áreas Estáveis: cabeçalho, abas, cubo 3D central, casos, controles, painel e rodapé visíveis sem rolagem
        composeTestRule.onNodeWithTag("lesson_screen").assertIsDisplayed()
        val cubeBoundsLesson = composeTestRule.onNodeWithTag("cube_3d_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Cubo 3D na aula deve ter destaque protagonista", cubeBoundsLesson.width > 120f && cubeBoundsLesson.height > 120f)
        ShadowTrace.reset()

        // 4. Painel Inferior Expansível de 3 Estados (Recolhido, Intermediário, Expandido)
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        assertEquals(ExplanationPanelState.COLLAPSED, vm.lessonState.value.explanationPanelState)

        composeTestRule.onNodeWithTag("panel_state_INTERMEDIATE").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ExplanationPanelState.INTERMEDIATE, vm.lessonState.value.explanationPanelState)

        composeTestRule.onNodeWithTag("panel_state_EXPANDED").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ExplanationPanelState.EXPANDED, vm.lessonState.value.explanationPanelState)
        composeTestRule.onNodeWithTag("explanation_scroll_container").assertIsDisplayed()

        composeTestRule.onNodeWithTag("panel_state_COLLAPSED").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ExplanationPanelState.COLLAPSED, vm.lessonState.value.explanationPanelState)
        ShadowTrace.reset()

        // 5. Modo Aprender: concluir demonstração exibe "Demonstração concluída." e NÃO marca a etapa como concluída na prática
        composeTestRule.onNodeWithTag("speed_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("highlight_pieces_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("step_forward_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Demonstração de 1 movimento deve estar concluída", vm.lessonState.value.isDemoCompleted)
        assertFalse(
            "Concluir demonstração no modo Aprender NÃO deve marcar conclusão prática automaticamente",
            vm.lessonState.value.isPracticeGoalAchieved
        )
        composeTestRule.onNodeWithTag("demo_completed_banner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("step_backward_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_demo_button").performClick()
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        // 6. Modo Praticar: testa seletor Camadas/Câmera, famílias de movimentos (R, L, U, D, F, B, M), Desfazer, Reiniciar e conclusão real do objetivo
        composeTestRule.onNodeWithTag("mode_practice_tab").performClick()
        composeTestRule.waitForIdle()

        // Verifica seletor explícito de modo de toque no viewport 3D (Camadas | Câmera)
        composeTestRule.onNodeWithTag("touch_mode_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("touch_mode_camera_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("touch_mode_layers_btn").performClick()
        composeTestRule.waitForIdle()

        // Seleciona família U e executa U via botão de sentido
        composeTestRule.onNodeWithTag("practice_family_U").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("practice_family_action_U").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Movimento executado: U", vm.lessonState.value.feedbackMessage)

        // Desfaz o movimento U sem reiniciar o caso inteiro
        composeTestRule.onNodeWithTag("practice_undo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Movimento desfeito", vm.lessonState.value.feedbackMessage)

        // Reinicia o caso prático e executa o movimento correto (R) para atingir o objetivo da Etapa 1
        composeTestRule.onNodeWithTag("practice_reset_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("practice_family_R").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("practice_family_action_R").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Objetivo da Etapa 1 deve ser atingido no modo Praticar", vm.lessonState.value.isPracticeGoalAchieved)
        composeTestRule.onNodeWithTag("goal_achieved_banner").assertIsDisplayed()
        ShadowTrace.reset()

        // 7. Reprodução completa de pelo menos 1 caso de CADA UMA das 7 etapas via ViewModel
        for (stepNum in 1..7) {
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = stepNum, caseIndex = 0, mode = com.example.viewmodel.LearningMode.LEARN)
            }
            composeTestRule.waitForIdle()
            val totalMoves = vm.lessonState.value.demoMoves.size
            assertTrue("Etapa $stepNum Caso 1 deve possuir movimentos de demonstração", totalMoves > 0)
            repeat(totalMoves) {
                composeTestRule.runOnUiThread {
                    vm.stepDemoForward()
                }
                composeTestRule.waitForIdle()
            }
            assertTrue("Reprodução da Etapa $stepNum deve concluir todos os movimentos", vm.lessonState.value.isDemoCompleted)
            assertTrue(
                "Estado do cubo após reprodução da Etapa $stepNum deve atingir o objetivo da etapa",
                vm.lessonState.value.cubeState.isStepGoalAchieved(stepNum)
            )
            if (stepNum == 7) {
                assertTrue("Etapa 7 concluída deve deixar o cubo 100% resolvido", vm.lessonState.value.cubeState.isSolved())
            }
            ShadowTrace.reset()
        }

        // 8. Verifica persistência local via DataStore após fechar e reabrir (recreate) a Activity
        val repo = ProgressRepository(context)
        val savedBeforeRecreate = runBlocking { repo.progressFlow.first() }
        assertTrue("Etapa 1 deve estar salva como concluída no DataStore", savedBeforeRecreate.completedSteps.contains(1))
        assertEquals(7, savedBeforeRecreate.lastVisitedStep)

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        ShadowTrace.reset()

        val savedAfterRecreate = runBlocking { repo.progressFlow.first() }
        assertTrue("Progresso da Etapa 1 deve persistir após recriar a Activity", savedAfterRecreate.completedSteps.contains(1))
        assertEquals(StepStatus.COMPLETED, savedAfterRecreate.getStepStatus(1))
        assertEquals(7, savedAfterRecreate.lastVisitedStep)

        // Verifica que na reabertura na Tela Inicial, o cartão Continuar redireciona para a última etapa salva (Etapa 7)
        composeTestRule.runOnUiThread {
            vm.navigateTo(com.example.viewmodel.AppScreen.HOME)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("student_progress_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lesson_screen").assertIsDisplayed()
        assertEquals("Botão Continuar deve abrir a última etapa visitada (Etapa 7)", 7, vm.lessonState.value.stepNumber)
        ShadowTrace.reset()

        // 9. Volta à Tela Inicial e testa a Tela de Exploração sem necessidade de rolagem de página
        composeTestRule.onNodeWithTag("lesson_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("course_back_button").performClick()
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        composeTestRule.onNodeWithTag("explore_cube_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explore_screen").assertIsDisplayed()
        val cubeBoundsExplore = composeTestRule.onNodeWithTag("cube_3d_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Cubo 3D na exploração deve ter largura e altura positivas", cubeBoundsExplore.width > 120f && cubeBoundsExplore.height > 120f)

        composeTestRule.onNodeWithTag("explore_move_btn_R").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("undo_explore_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scramble_cube_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("restore_solved_cube_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Restaurar Cubo deve devolver o cubo ao estado resolvido", vm.exploreState.value.cubeState.isSolved())
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-land", fontScale = 1.3f)
    fun `verifica interface sem truncamento com fonte ampliada e visibilidade do cubo 3D em paisagem`() {
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        val homeCubeBounds = composeTestRule.onNodeWithTag("cube_3d_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Cubo 3D na Home em paisagem deve ser visível", homeCubeBounds.width > 180f && homeCubeBounds.height > 140f)

        composeTestRule.onNodeWithTag("start_learning_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("course_steps_list").performScrollToNode(hasTestTag("step_3_learn_btn"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("step_3_learn_btn").performClick()
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        composeTestRule.onNodeWithTag("lesson_screen").assertIsDisplayed()
        val lessonCubeBounds = composeTestRule.onNodeWithTag("cube_3d_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Cubo 3D na Aula em paisagem com fonte 1.3x deve permanecer visível", lessonCubeBounds.width > 180f && lessonCubeBounds.height > 100f)

        // Seleciona Caso 2 do Passo 3 e expande o painel de explicação para verificar a nota de validação pedagógica do PDF
        composeTestRule.onNodeWithTag("case_chip_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("panel_state_EXPANDED").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pdf_validation_note_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `auditoria comparativa de fluidez recomposicoes area util 50 a 60 por cento no modo praticar e cubo 3D interativo na Home`() {
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }

        // =========================================================================
        // 1. VALIDAÇÃO DA HOME 3D INTERATIVA (Auto-rotação + Pausa ao tocar + Giros 3D + Restaurar + Isolamento)
        // =========================================================================
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_restore_cube_button").assertIsDisplayed()

        // 1a. Verifica auto-rotação em repouso, pausa imediata ao tocar e retomada suave após inatividade
        val homeRenderer = CubeGLRenderer().apply {
            configureViewport(600, 600)
            resetCamera()
            autoRotateIdle = true
            clearAutoRotateCooldownForTest()
        }
        val homeArbiter = CubeGestureArbiter(
            renderer = homeRenderer,
            allowFaceSwipeProvider = { true },
            isAnimatingProvider = { false },
            touchModeProvider = { TouchInteractionMode.MOVE_LAYERS }
        )

        val yawIdle0 = homeRenderer.cameraYaw
        assertTrue("Home em repouso deve girar suavemente", homeRenderer.advanceIdleAutoRotationIfEligible())
        val yawIdle1 = homeRenderer.cameraYaw
        assertTrue("cameraYaw deve avançar durante auto-rotação em repouso", yawIdle1 != yawIdle0)

        // Toque do usuário inicia -> auto-rotação deve parar IMEDIATAMENTE
        val ptFront = homeRenderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
        homeArbiter.onDown(ptFront[0], ptFront[1])
        assertFalse("Auto-rotação deve pausar imediatamente durante o toque", homeRenderer.advanceIdleAutoRotationIfEligible())
        assertEquals("cameraYaw não pode mudar enquanto o dedo está na tela", yawIdle1, homeRenderer.cameraYaw, 0.0f)

        // Solta o dedo -> permanece pausado durante a janela curta de inatividade (1800ms)
        homeArbiter.onUpOrCancel()
        assertFalse("Auto-rotação deve aguardar o intervalo curto de inatividade após soltar o dedo", homeRenderer.advanceIdleAutoRotationIfEligible())

        // Avança o relógio do sistema além da janela de inatividade (1900ms) -> retoma suavemente do mesmo ângulo
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1900))
        assertTrue("Auto-rotação deve retomar suavemente após período curto de inatividade", homeRenderer.advanceIdleAutoRotationIfEligible())
        assertEquals("Retomada suave deve partir do ângulo atual sem salto", (yawIdle1 + 0.35f) % 360f, homeRenderer.cameraYaw, 1e-4f)

        // 1b. Verifica giros físicos 3D multi-frame na Home (R, U, M, E, S) e isolamento total de estado
        val lessonBeforeHomeMoves = vm.lessonState.value.cubeState
        val exploreBeforeHomeMoves = vm.exploreState.value.cubeState
        val progressBeforeHomeMoves = vm.progressState.value

        vm.forceMultiFrameAnimationInTest = true
        for (move in listOf(CubeMove.R, CubeMove.U, CubeMove.M, CubeMove.E, CubeMove.S)) {
            vm.recordedAnimationFramesForAudit.clear()
            composeTestRule.runOnUiThread {
                vm.executeHomePreviewMove(move)
            }
            repeat(28) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                composeTestRule.waitForIdle()
            }
            val frames = vm.recordedAnimationFramesForAudit.filter { it.move == move }
            assertTrue("Cubo da Home deve emitir >= 20 quadros 3D reais para ${move.notation}", frames.size >= 20)
            for (cp in listOf(0.0f, 0.25f, 0.50f, 0.75f, 1.0f)) {
                assertTrue(
                    "Cubo da Home deve passar pelo marco ${cp * 100}% durante ${move.notation}",
                    frames.any { kotlin.math.abs(it.progress - cp) < 1e-3f }
                )
            }
        }
        vm.forceMultiFrameAnimationInTest = false

        assertFalse("Cubo da Home deve refletir os movimentos R U M E S executados", vm.homePreviewState.value.cubeState.isSolved())
        assertEquals("Movimentos na Home JAMAIS podem afetar o estado da aula", lessonBeforeHomeMoves, vm.lessonState.value.cubeState)
        assertEquals("Movimentos na Home JAMAIS podem afetar o estado do Modo Explorar", exploreBeforeHomeMoves, vm.exploreState.value.cubeState)
        assertEquals("Movimentos na Home JAMAIS podem afetar o progresso do aluno", progressBeforeHomeMoves, vm.progressState.value)

        // Botão discreto "Restaurar" na Home restaura o cubo ao estado resolvido
        composeTestRule.onNodeWithTag("home_restore_cube_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Botão Restaurar na Home deve devolver o cubo da Home ao estado resolvido", vm.homePreviewState.value.cubeState.isSolved())
        ShadowTrace.reset()

        // =========================================================================
        // 2. ÁREA INTERATIVA DO CUBO NO MODO PRATICAR (50% a 60% da altura da tela em retrato)
        // =========================================================================
        composeTestRule.runOnUiThread {
            vm.openLesson(stepNumber = 2, caseIndex = 0, mode = com.example.viewmodel.LearningMode.PRACTICE)
        }
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        val lessonRootBounds = composeTestRule.onNodeWithTag("lesson_screen").fetchSemanticsNode().boundsInRoot
        val practiceCubeBounds = composeTestRule.onNodeWithTag("cube_3d_viewport").fetchSemanticsNode().boundsInRoot
        val cubeHeightRatio = practiceCubeBounds.height / lessonRootBounds.height
        assertTrue(
            "No Modo Praticar em retrato, o cubo 3D deve ocupar entre 50% e 60% da altura útil (medido: ${(cubeHeightRatio * 100).toInt()}%, cube=${practiceCubeBounds.height}px, tela=${lessonRootBounds.height}px)",
            cubeHeightRatio in 0.50f..0.64f
        )

        // Verifica que todas as 9 famílias (R, L, U, D, F, B, M, E, S) e os 3 botões da família ativa estão visíveis por padrão
        for (fam in listOf("R", "L", "U", "D", "F", "B", "M", "E", "S")) {
            composeTestRule.onNodeWithTag("practice_family_$fam").assertIsDisplayed()
        }
        // O teclado completo começa recolhido para priorizar a área do cubo 3D
        assertFalse("Teclado completo deve iniciar recolhido por padrão no Modo Praticar", vm.lessonState.value.isPracticeMovesExpanded)

        // Botão secundário abre o teclado completo e permite recolhê-lo rapidamente sem reiniciar o cubo
        composeTestRule.onNodeWithTag("toggle_practice_moves_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Botão secundário deve expandir o teclado completo", vm.lessonState.value.isPracticeMovesExpanded)
        composeTestRule.onNodeWithTag("practice_full_keyboard_row").assertIsDisplayed()

        composeTestRule.onNodeWithTag("toggle_practice_moves_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse("Botão secundário deve recolher o teclado completo devolvendo espaço ao cubo", vm.lessonState.value.isPracticeMovesExpanded)
        ShadowTrace.reset()

        // =========================================================================
        // 3. COMPARAÇÃO TÉCNICA DE FLUIDEZ E RECOMPOSIÇÕES: MODO EXPLORAR vs. MODO PRATICAR
        // =========================================================================
        // Mesmo se a velocidade didática da demonstração estiver em 0.5x (lenta), os movimentos manuais
        // no Modo Praticar devem ter exatamente a mesma duração ágil e contagem de quadros do Modo Explorar!
        composeTestRule.runOnUiThread {
            vm.setAnimationSpeed(0.5f)
        }
        composeTestRule.waitForIdle()

        val testMoves = listOf(
            CubeMove.R, CubeMove.R_PRIME, CubeMove.R2,
            CubeMove.U, CubeMove.U_PRIME,
            CubeMove.M, CubeMove.E, CubeMove.S, CubeMove.F
        )

        // 3a. Medição no Modo Explorar
        composeTestRule.runOnUiThread {
            vm.openExploreScreen()
            vm.resetExploreCubeSolved()
        }
        composeTestRule.waitForIdle()
        com.example.ui.screens.ExploreRecompositionTracker.reset()
        vm.forceMultiFrameAnimationInTest = true

        val exploreFrameCounts = mutableMapOf<CubeMove, Int>()
        for (move in testMoves) {
            vm.recordedAnimationFramesForAudit.clear()
            val controlsBefore = com.example.ui.screens.ExploreRecompositionTracker.controlsRecompositions
            composeTestRule.runOnUiThread {
                vm.executeExploreMove(move)
            }
            // Durante os quadros intermediários (antes do último frame), o cartão de controles não deve recompor
            repeat(10) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                composeTestRule.waitForIdle()
            }
            val controlsDuringAnim = com.example.ui.screens.ExploreRecompositionTracker.controlsRecompositions - controlsBefore
            assertEquals("Modo Explorar: cartão de controles deve ter 0 recomposições durante quadros intermediários de ${move.notation}", 0, controlsDuringAnim)

            repeat(22) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                composeTestRule.waitForIdle()
            }
            exploreFrameCounts[move] = vm.recordedAnimationFramesForAudit.count { it.move == move }
        }

        // 3b. Medição no Modo Praticar (com os mesmos 9 movimentos)
        composeTestRule.runOnUiThread {
            vm.openLesson(stepNumber = 7, caseIndex = 0, mode = com.example.viewmodel.LearningMode.PRACTICE)
        }
        composeTestRule.waitForIdle()
        com.example.ui.screens.LessonRecompositionTracker.reset()

        val practiceFrameCounts = mutableMapOf<CubeMove, Int>()
        for (move in testMoves) {
            vm.recordedAnimationFramesForAudit.clear()
            val controlsBefore = com.example.ui.screens.LessonRecompositionTracker.controlsRecompositions
            val viewportBefore = com.example.ui.screens.LessonRecompositionTracker.viewportRecompositions
            composeTestRule.runOnUiThread {
                vm.executePracticeMove(move)
            }
            repeat(10) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                composeTestRule.waitForIdle()
            }
            val controlsDuringAnim = com.example.ui.screens.LessonRecompositionTracker.controlsRecompositions - controlsBefore
            val viewportDuringAnim = com.example.ui.screens.LessonRecompositionTracker.viewportRecompositions - viewportBefore
            assertEquals(
                "Modo Praticar: cartões de controle devem ter 0 recomposições durante quadros intermediários de ${move.notation}",
                0,
                controlsDuringAnim
            )
            assertTrue(
                "Modo Praticar: viewport 3D deve recompor a cada quadro intermediário de ${move.notation} (medido: $viewportDuringAnim)",
                viewportDuringAnim >= 8
            )

            repeat(22) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                composeTestRule.waitForIdle()
            }
            practiceFrameCounts[move] = vm.recordedAnimationFramesForAudit.count { it.move == move }
        }
        vm.forceMultiFrameAnimationInTest = false

        // Verifica paridade exata de quadros/duração entre Modo Explorar e Modo Praticar para todos os movimentos testados
        for (move in testMoves) {
            assertEquals(
                "Modo Praticar e Modo Explorar devem ter exatamente a mesma contagem de quadros e duração para ${move.notation}",
                exploreFrameCounts[move],
                practiceFrameCounts[move]
            )
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testPedagogicalUxHomeButtonsCaseSelectorAndNotationGuideNonOverlap() {
        // =========================================================================
        // 1. BOTÕES DA HOME SEM TRUNCAMENTO EM DIFERENTES LARGURAS E ESCALAS DE FONTE
        // =========================================================================
        val testConfigs = listOf(
            "w320dp-h720dp-xhdpi" to 1.0f,
            "w360dp-h780dp-xhdpi" to 1.0f,
            "w393dp-h852dp-xhdpi" to 1.0f,
            "w412dp-h915dp-xhdpi" to 1.0f,
            "w360dp-h780dp-xhdpi" to 1.3f,
            "w360dp-h780dp-xhdpi" to 1.5f,
            "w780dp-h360dp-land-xhdpi" to 1.0f
        )

        for ((qualifiers, fontScale) in testConfigs) {
            org.robolectric.RuntimeEnvironment.setQualifiers(qualifiers)
            org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = fontScale
            composeTestRule.activityRule.scenario.onActivity { act ->
                act.resources.configuration.fontScale = fontScale
                act.recreate()
            }
            composeTestRule.waitForIdle()
            ShadowTrace.reset()

            val homeBounds = composeTestRule.onNodeWithTag("home_screen").fetchSemanticsNode().boundsInRoot
            val startBtn = composeTestRule.onNodeWithTag("start_learning_button")
            val exploreBtn = composeTestRule.onNodeWithTag("explore_cube_button")
            val notationBtn = composeTestRule.onNodeWithTag("notation_guide_button")

            startBtn.assertIsDisplayed()
            exploreBtn.assertIsDisplayed()
            notationBtn.assertIsDisplayed()

            val startText = startBtn.fetchSemanticsNode().config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                .joinToString(" ") { it.text }
            val exploreText = exploreBtn.fetchSemanticsNode().config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                .joinToString(" ") { it.text }
            val notationText = notationBtn.fetchSemanticsNode().config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                .joinToString(" ") { it.text }

            assertEquals("Rótulo completo de Começar a aprender em $qualifiers (font=$fontScale)", "Começar a aprender", startText)
            assertEquals("Rótulo completo de Explorar Cubo em $qualifiers (font=$fontScale)", "Explorar Cubo", exploreText)
            assertEquals("Rótulo completo de Guia de Notação em $qualifiers (font=$fontScale)", "Guia de Notação", notationText)

            val exploreBounds = exploreBtn.fetchSemanticsNode().boundsInRoot
            val notationBounds = notationBtn.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Botão Explorar Cubo deve caber inteiramente na tela em $qualifiers (font=$fontScale)",
                exploreBounds.bottom <= homeBounds.bottom + 2f && exploreBounds.right <= homeBounds.right + 2f
            )
            assertTrue(
                "Botão Guia de Notação deve caber inteiramente na tela em $qualifiers (font=$fontScale)",
                notationBounds.bottom <= homeBounds.bottom + 2f && notationBounds.right <= homeBounds.right + 2f
            )
            ShadowTrace.reset()
        }

        // Restaura dimensão e escala de fonte padrão (360x780dp, fontScale = 1.0f)
        org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h780dp-xhdpi")
        org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = 1.0f
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            act.resources.configuration.fontScale = 1.0f
            act.recreate()
        }
        composeTestRule.waitForIdle()
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        ShadowTrace.reset()

        // =========================================================================
        // 2. ORIENTAÇÃO PEDAGÓGICA DO CURSO E SELEÇÃO DIDÁTICA DE CASOS + ALGORITMOS
        // =========================================================================
        composeTestRule.runOnUiThread {
            vm.openCourseScreen()
        }
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        // Verifica o cartão opcional de fluxo de estudo na tela do Curso e o botão de dispensar/reabrir
        composeTestRule.onNodeWithTag("course_study_flow_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dismiss_study_flow_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodes(hasTestTag("course_study_flow_banner")).fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("toggle_study_flow_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("course_study_flow_banner").assertIsDisplayed()
        ShadowTrace.reset()

        // Abre a Etapa 5 no Modo Aprender (possui múltiplos casos e algoritmos longos no Caso 2/3)
        composeTestRule.runOnUiThread {
            vm.openLesson(stepNumber = 5, caseIndex = 0, mode = com.example.viewmodel.LearningMode.LEARN)
        }
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        // Verifica a dica inicial dispensável sobre os casos
        composeTestRule.onNodeWithTag("case_orientation_tip_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dismiss_case_tip_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodes(hasTestTag("case_orientation_tip_banner")).fetchSemanticsNodes().size)

        // Verifica o cabeçalho didático do caso, a descrição curta e a separação visual do algoritmo
        composeTestRule.onNodeWithTag("case_selector_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("case_short_description").assertIsDisplayed()
        composeTestRule.onNodeWithTag("algorithm_section_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_move_status_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("algorithm_sequence_row").assertIsDisplayed()

        val case0InitialCube = vm.lessonState.value.cubeState
        val completedBeforeCaseSwitch = vm.progressState.value.completedSteps

        // Avança para o Caso 2 (que possui 15 movimentos) usando o botão "Próximo"
        composeTestRule.onNodeWithTag("next_case_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Deve avançar para o Caso 2 (índice 1)", 1, vm.lessonState.value.caseIndex)
        org.junit.Assert.assertNotEquals("Estado inicial do Caso 2 deve ser distinto do Caso 1", case0InitialCube, vm.lessonState.value.cubeState)
        assertEquals("Trocar de caso não deve alterar etapas concluídas", completedBeforeCaseSwitch, vm.progressState.value.completedSteps)

        // Como o Caso 2 da Etapa 5 possui > 7 movimentos, verifica o botão "Ver todos" / "Compactar"
        composeTestRule.onNodeWithTag("toggle_full_algorithm_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("algorithm_full_flow_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_full_algorithm_button").performClick()
        composeTestRule.waitForIdle()

        // Avança um movimento na demonstração e verifica sincronização do destaque e do texto de movimento atual
        composeTestRule.onNodeWithTag("step_forward_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, vm.lessonState.value.currentMoveIndex)
        val moveStatusAfterStep = composeTestRule.onNodeWithTag("current_move_status_text").fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .joinToString(" ") { it.text }
        assertTrue("Status do movimento atual deve indicar Mov. 1", moveStatusAfterStep.contains("Mov. 1/"))

        // Verifica distinção clara ao marcar manualmente a etapa como concluída
        composeTestRule.onNodeWithTag("complete_step_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            "Etapa 5 marcada manualmente como concluída.",
            vm.lessonState.value.feedbackMessage
        )
        ShadowTrace.reset()

        // =========================================================================
        // 3. GUIA DE NOTAÇÃO 3D SEM SOBREPOSIÇÃO E COM ROLAGEM INDEPENDENTE
        // =========================================================================
        composeTestRule.runOnUiThread {
            vm.setNotationGuideVisible(true)
        }
        composeTestRule.waitForIdle()
        ShadowTrace.reset()

        composeTestRule.onNodeWithTag("notation_guide_modal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notation_orientation_text").assertIsDisplayed()
        val cubeBoxBoundsBeforeScroll = composeTestRule.onNodeWithTag("notation_cube_viewport_box").fetchSemanticsNode().boundsInRoot
        val moveCardBounds = composeTestRule.onNodeWithTag("notation_selected_move_card").fetchSemanticsNode().boundsInRoot
        val listScrollBounds = composeTestRule.onNodeWithTag("notation_list_scroll").fetchSemanticsNode().boundsInRoot

        // Comprova matematicamente que o cartão do movimento selecionado e a lista rolável ficam FORA do cubo 3D (zero sobreposição!)
        assertTrue(
            "O cartão de explicação do movimento selecionado jamais deve sobrepor o cubo 3D (cubeBottom=${cubeBoxBoundsBeforeScroll.bottom}, cardTop=${moveCardBounds.top})",
            cubeBoxBoundsBeforeScroll.bottom <= moveCardBounds.top + 1f
        )
        assertTrue(
            "A lista rolável de notações jamais deve sobrepor o cubo 3D ou o cartão do movimento (cardBottom=${moveCardBounds.bottom}, listTop=${listScrollBounds.top})",
            moveCardBounds.bottom <= listScrollBounds.top + 1f
        )

        // Rola até o final da lista (família S) e verifica que o cubo 3D permanece estático na mesma posição
        composeTestRule.onNodeWithTag("notation_btn_S", useUnmergedTree = true).performScrollTo()
        composeTestRule.waitForIdle()
        val cubeBoxBoundsAfterScroll = composeTestRule.onNodeWithTag("notation_cube_viewport_box").fetchSemanticsNode().boundsInRoot
        assertEquals(
            "O cubo 3D do Guia de Notação deve permanecer fixo durante a rolagem da lista inferior",
            cubeBoxBoundsBeforeScroll.top,
            cubeBoxBoundsAfterScroll.top,
            0.5f
        )

        // Seleciona S na lista, verifica atualização do título e testa os botões Repetir e Restaurar
        composeTestRule.onNodeWithTag("notation_btn_S").performClick()
        composeTestRule.waitForIdle()
        assertEquals(CubeMove.S, vm.notationPreviewMove.value)

        composeTestRule.onNodeWithTag("notation_repeat_move_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(CubeMove.S, vm.notationPreviewMove.value)

        composeTestRule.onNodeWithTag("notation_restore_cube_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue("Botão Restaurar no Guia de Notação deve restaurar o cubo ao estado resolvido", vm.notationPreviewCube.value.isSolved())

        composeTestRule.onNodeWithTag("close_notation_modal_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(vm.showNotationGuideModal.value)
        ShadowTrace.reset()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testLessonPedagogicalTextLegibilityAndTwoLevelCaseExplanationAcrossWidthsAndFontScales() {
        // Função auxiliar que inspeciona o TextLayoutResult real renderizado pelo Compose
        // e comprova matematicamente que não há corte visual nem reticências (hasVisualOverflow == false)
        fun collectNodeTextLayouts(tag: String): List<androidx.compose.ui.text.TextLayoutResult> {
            val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            fun collectFrom(sNode: androidx.compose.ui.semantics.SemanticsNode) {
                if (sNode.config.contains(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)) {
                    sNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(textLayouts)
                }
                sNode.children.forEach { collectFrom(it) }
            }
            collectFrom(node)
            return textLayouts
        }

        fun assertNoVisualTextOverflow(tag: String, contextInfo: String): String {
            val textLayouts = collectNodeTextLayouts(tag)
            assertTrue("Nó '$tag' deve produzir TextLayoutResult ($contextInfo)", textLayouts.isNotEmpty())
            for (layout in textLayouts) {
                val renderedText = layout.layoutInput.text.text
                assertFalse(
                    "Texto de '$tag' ('$renderedText') NÃO pode sofrer overflow/truncamento em $contextInfo (didOverflowWidth=${layout.didOverflowWidth}, didOverflowHeight=${layout.didOverflowHeight}, lineCount=${layout.lineCount})",
                    layout.hasVisualOverflow
                )
            }
            return textLayouts.joinToString("") { it.layoutInput.text.text }
        }

        // 1. Testa as combinações obrigatórias de larguras (320dp, 360dp, 393dp, 412dp) e fontes (1.0x, 1.3x, 1.5x)
        // cobrindo etapas de títulos longos (Etapas 1, 3, 5, 6, 7), casos longos e Modos Aprender e Praticar.
        val screenConfigs = listOf(
            "w320dp-h740dp-xhdpi" to 1.0f,
            "w320dp-h740dp-xhdpi" to 1.3f,
            "w320dp-h740dp-xhdpi" to 1.5f,
            "w360dp-h780dp-xhdpi" to 1.0f,
            "w360dp-h780dp-xhdpi" to 1.3f,
            "w360dp-h780dp-xhdpi" to 1.5f,
            "w393dp-h852dp-xhdpi" to 1.0f,
            "w393dp-h852dp-xhdpi" to 1.3f,
            "w412dp-h915dp-xhdpi" to 1.0f,
            "w412dp-h915dp-xhdpi" to 1.5f
        )

        val stepsAndCasesToAudit = listOf(
            Triple(1, 0, com.example.viewmodel.LearningMode.LEARN),    // Caso 1 de 6 — Meio na camada intermediária (Direita)
            Triple(1, 5, com.example.viewmodel.LearningMode.LEARN),    // Caso 6 de 6 — Meio invertido no topo
            Triple(3, 1, com.example.viewmodel.LearningMode.LEARN),    // Etapa 3 Caso 2 (PDF)
            Triple(5, 2, com.example.viewmodel.LearningMode.LEARN),    // Etapa 5 Caso 2 (algoritmo longo de 23 mov.)
            Triple(7, 0, com.example.viewmodel.LearningMode.LEARN),    // Etapa 7: Posicionar os Meios Finais (Minerva)
            Triple(7, 2, com.example.viewmodel.LearningMode.PRACTICE)  // Modo Praticar na Etapa 7
        )

        for ((qualifiers, fontScale) in screenConfigs) {
            org.robolectric.RuntimeEnvironment.setQualifiers(qualifiers)
            org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = fontScale
            lateinit var vm: com.example.viewmodel.CuboMestreViewModel
            composeTestRule.activityRule.scenario.onActivity { act ->
                act.resources.configuration.fontScale = fontScale
                act.recreate()
            }
            composeTestRule.waitForIdle()
            composeTestRule.activityRule.scenario.onActivity { act ->
                vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
            }
            ShadowTrace.reset()

            for ((stepNum, caseIdx, mode) in stepsAndCasesToAudit) {
                composeTestRule.runOnUiThread {
                    vm.openLesson(stepNumber = stepNum, caseIndex = caseIdx, mode = mode)
                }
                composeTestRule.waitForIdle()

                val ctx = "$qualifiers | font=${fontScale}x | Etapa $stepNum Caso ${caseIdx + 1} ($mode)"
                val step = com.example.curriculum.CurriculumRepository.getStep(stepNum)
                val lessonCase = step.cases[caseIdx]

                // Verifica que o cubo 3D permanece visível e com área útil adequada
                val cubeBounds = composeTestRule.onNodeWithTag("cube_3d_viewport").fetchSemanticsNode().boundsInRoot
                assertTrue("Cubo 3D deve ter altura e largura amplas em $ctx (w=${cubeBounds.width}, h=${cubeBounds.height})", cubeBounds.width > 120f && cubeBounds.height > 100f)

                // Verifica legibilidade integral (zero overflow visual) dos textos pedagógicos principais
                val renderedStepTitle = assertNoVisualTextOverflow("lesson_step_title", ctx)
                val renderedCaseHeader = assertNoVisualTextOverflow("case_selector_header", ctx)
                val renderedCaseFullTitle = assertNoVisualTextOverflow("case_full_title_text", ctx)
                assertNoVisualTextOverflow("collapsed_goal_text", ctx)

                // Confirma que o título da etapa e o nome completo da situação do caso correspondem integralmente ao currículo
                assertEquals("Título da etapa deve estar completo em $ctx", step.title, renderedStepTitle)
                assertEquals("Cabeçalho do seletor deve exibir número e total em $ctx", "Caso ${caseIdx + 1} de ${step.cases.size}", renderedCaseHeader)
                assertEquals("Linha 2 do caso deve exibir o título completo da situação em $ctx", lessonCase.situationTitle, renderedCaseFullTitle)

                // Verifica os chips compactos ("Caso 1", "Caso 2"...) sem truncamento
                val chipLabel = assertNoVisualTextOverflow("case_chip_$caseIdx", ctx)
                assertEquals("Chip deve usar rótulo compacto 'Caso X' em $ctx", "Caso ${caseIdx + 1}", chipLabel)

                if (mode == com.example.viewmodel.LearningMode.LEARN) {
                    assertNoVisualTextOverflow("case_short_description", ctx)
                    assertNoVisualTextOverflow("current_move_status_text", ctx)
                }
                ShadowTrace.reset()
            }
        }

        // Restaura configuração padrão 360x780dp @ 1.0x e valida o componente de 2º nível ("Entender o caso")
        org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h780dp-xhdpi")
        org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = 1.0f
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            act.resources.configuration.fontScale = 1.0f
            act.recreate()
        }
        composeTestRule.waitForIdle()
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        ShadowTrace.reset()

        // Abre Etapa 3, Caso 2 (possui descrição completa, dica do professor, algoritmo de 9 mov. e nota de validação do PDF)
        composeTestRule.runOnUiThread {
            vm.openLesson(stepNumber = 3, caseIndex = 1, mode = com.example.viewmodel.LearningMode.LEARN)
        }
        composeTestRule.waitForIdle()

        // Avança 2 movimentos na demonstração para comprovar que abrir/fechar o modal preserva 100% do estado do cubo e progresso
        composeTestRule.onNodeWithTag("step_forward_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("step_forward_button").performClick()
        composeTestRule.waitForIdle()

        val cubeBeforeModal = vm.lessonState.value.cubeState
        val moveIdxBeforeModal = vm.lessonState.value.currentMoveIndex
        assertEquals(1, moveIdxBeforeModal)

        // Abre o Modo Detalhado clicando em "Entender o caso"
        composeTestRule.onNodeWithTag("open_case_detail_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("case_detail_modal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("case_detail_full_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("case_detail_full_description").assertIsDisplayed()
        composeTestRule.onNodeWithTag("case_detail_pedagogical_note").assertIsDisplayed()
        composeTestRule.onNodeWithTag("case_detail_algorithm_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("case_detail_goal_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("case_detail_pdf_note").performScrollTo().assertIsDisplayed()

        val step3 = com.example.curriculum.CurriculumRepository.getStep(3)
        val case3_2 = step3.cases[1]
        val modalDesc = composeTestRule.onNodeWithTag("case_detail_full_description", useUnmergedTree = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals("Modal detalhado deve exibir a descrição integral original sem cortes", case3_2.description, modalDesc)

        // Fecha o modal detalhado e comprova que o cubo 3D e o índice do movimento permanecem intactos
        composeTestRule.onNodeWithTag("return_to_cube_from_detail_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodes(hasTestTag("case_detail_modal")).fetchSemanticsNodes().size)
        assertEquals("Fechar explicação detalhada não pode alterar o estado do cubo 3D", cubeBeforeModal, vm.lessonState.value.cubeState)
        assertEquals("Fechar explicação detalhada não pode reiniciar o progresso da demonstração", moveIdxBeforeModal, vm.lessonState.value.currentMoveIndex)
        ShadowTrace.reset()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testStep4Case2HorizontalLineTwoPedagogicalPhasesAndMathematicalSync() {
        composeTestRule.waitForIdle()
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        ShadowTrace.reset()

        // Abre Etapa 4, Caso 2 ("Linha Horizontal", id = "s4_c2")
        composeTestRule.runOnUiThread {
            vm.openLesson(stepNumber = 4, caseIndex = 1, mode = com.example.viewmodel.LearningMode.LEARN)
        }
        composeTestRule.waitForIdle()

        val step4 = com.example.curriculum.CurriculumRepository.getStep(4)
        val case2 = step4.cases[1]
        assertEquals("s4_c2", case2.id)
        assertEquals("F U R U' R' F' F U R U' R' F'", case2.fullResolutionAlgorithm)
        assertEquals(12, case2.fullMoveList.size)
        assertEquals(2, case2.pedagogicalPhases.size)

        // Preserva F' (6º movimento, índice 5) e F (7º movimento, índice 6) na transição
        assertEquals(CubeMove.F_PRIME, case2.fullMoveList[5])
        assertEquals(CubeMove.F, case2.fullMoveList[6])

        // 1. Valida que o estado inicial representa a linha amarela horizontal orientada corretamente e F2L intacto
        val initialCube = vm.lessonState.value.cubeState
        assertTrue(
            "Estado inicial do Caso 2 da Etapa 4 deve ter a linha amarela horizontal orientada no topo (UL e UR)",
            initialCube.isYellowHorizontalLineOnTop()
        )
        assertFalse("Estado inicial não pode ser L nem cruz completa", initialCube.isYellowLShapeOnTop())
        assertFalse("Estado inicial não pode ter a cruz amarela pronta", initialCube.isYellowCrossOriented())
        assertTrue("Duas primeiras camadas devem estar resolvidas no estado inicial", initialCube.isSecondLayerSolved())

        // Verifica apresentação visual da Fase 1 na UI
        composeTestRule.onNodeWithTag("pedagogical_phases_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phase_transition_badge", useUnmergedTree = true).assertExists()

        fun readTagText(tag: String): String {
            return composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .joinToString("") { it.text }
        }

        assertEquals("Fase 1 — Transformar a linha em L", readTagText("active_phase_title_text"))
        assertEquals(
            "Primeira execução: transformar a linha horizontal em formato de L.",
            readTagText("active_phase_description_text")
        )
        assertEquals("F U R U' R' F'", readTagText("active_phase_algorithm_text"))

        // 2. Executa os 6 movimentos da Fase 1 passo a passo (Próximo) e valida a configuração intermediária em L
        repeat(6) { stepIdx ->
            composeTestRule.onNodeWithTag("step_forward_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals("Índice do movimento após passo ${stepIdx + 1}", stepIdx, vm.lessonState.value.currentMoveIndex)
        }

        val intermediateLCube = vm.lessonState.value.cubeState
        assertEquals(5, vm.lessonState.value.currentMoveIndex)
        assertTrue(
            "Após 6 movimentos (Fase 1), o cubo deve apresentar o formato de L esperado no topo (UB e UL amarelos)",
            intermediateLCube.isYellowLShapeOnTop()
        )
        assertTrue(
            "Após 6 movimentos (Fase 1), as duas primeiras camadas devem permanecer resolvidas",
            intermediateLCube.isSecondLayerSolved()
        )
        assertFalse(
            "Após 6 movimentos, a cruz amarela ainda não está completa",
            intermediateLCube.isYellowCrossOriented()
        )

        // Verifica destaque visual das duas arestas amarelas que formam o L (HighlightMode.YELLOW_L_SHAPE)
        val lShapeHighlightedPieces = intermediateLCube.cubies
            .filter { com.example.curriculum.CurriculumRepository.isCubieHighlighted(it, com.example.curriculum.HighlightMode.YELLOW_L_SHAPE) }
            .map { Triple(it.x, it.y, it.z) }
            .toSet()
        assertTrue("Centro amarelo (0,1,0) deve estar destacado no L", lShapeHighlightedPieces.contains(Triple(0, 1, 0)))
        assertTrue("Aresta amarela traseira UB (0,1,-1) deve estar destacada no L", lShapeHighlightedPieces.contains(Triple(0, 1, -1)))
        assertTrue("Aresta amarela esquerda UL (-1,1,0) deve estar destacada no L", lShapeHighlightedPieces.contains(Triple(-1, 1, 0)))
        assertEquals("Exatamente 3 peças (centro + 2 arestas do L) devem ser destacadas", 3, lShapeHighlightedPieces.size)

        // Verifica mensagem intermediária obrigatória e crachá visual do formato de L
        composeTestRule.onNodeWithTag("l_shape_intermediate_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phase_transition_message_banner").assertIsDisplayed()
        assertEquals(
            "Agora temos o formato de L. Vamos repetir o algoritmo para completar a cruz.",
            readTagText("phase_transition_message_text")
        )
        assertEquals(
            "Agora temos o formato de L. Vamos repetir o algoritmo para completar a cruz.",
            vm.lessonState.value.feedbackMessage
        )

        // 3. Executa os 6 movimentos da Fase 2 passo a passo (sem restaurar nem reinicializar o cubo)
        repeat(6) { idxInPhase2 ->
            composeTestRule.onNodeWithTag("step_forward_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(6 + idxInPhase2, vm.lessonState.value.currentMoveIndex)
            // Confirma que a UI mudou para a Fase 2 assim que entrou no movimento 7 (índice 6)
            assertEquals("Fase 2 — Completar a cruz amarela", readTagText("active_phase_title_text"))
            assertEquals(
                "Segunda execução: completar a cruz amarela.",
                readTagText("active_phase_description_text")
            )
            assertEquals("F U R U' R' F'", readTagText("active_phase_algorithm_text"))
        }

        // 4. Valida que após 12 movimentos a cruz amarela está completa, F2L intacto e objetivo da Etapa 4 atingido
        val finalCrossCube = vm.lessonState.value.cubeState
        assertEquals(11, vm.lessonState.value.currentMoveIndex)
        assertTrue("Demonstração de 12 movimentos deve estar concluída", vm.lessonState.value.isDemoCompleted)
        assertTrue("Após 12 movimentos, a cruz amarela deve estar completa", finalCrossCube.isYellowCrossOriented())
        assertTrue("Após 12 movimentos, as duas primeiras camadas devem permanecer resolvidas", finalCrossCube.isSecondLayerSolved())
        assertTrue(
            "Posição final deve corresponder ao objetivo da Etapa 4",
            finalCrossCube.isStepGoalAchieved(4)
        )

        // 5. Valida sincronização perfeita ao voltar passo a passo (Anterior) da Fase 2 para a Fase 1 e Reiniciar
        repeat(6) {
            composeTestRule.onNodeWithTag("step_backward_button").performClick()
            composeTestRule.waitForIdle()
        }
        assertEquals(5, vm.lessonState.value.currentMoveIndex)
        assertEquals(
            "Voltar 6 movimentos a partir do final deve restaurar exatamente o estado intermediário em L",
            intermediateLCube,
            vm.lessonState.value.cubeState
        )
        assertEquals(
            "Agora temos o formato de L. Vamos repetir o algoritmo para completar a cruz.",
            readTagText("phase_transition_message_text")
        )

        // Volta os 6 movimentos restantes até o início
        repeat(6) {
            composeTestRule.onNodeWithTag("step_backward_button").performClick()
            composeTestRule.waitForIdle()
        }
        assertEquals(-1, vm.lessonState.value.currentMoveIndex)
        assertEquals(
            "Voltar todos os 12 movimentos deve restaurar exatamente a linha horizontal inicial",
            initialCube,
            vm.lessonState.value.cubeState
        )

        // 6. Valida execução isolada da Fase 1 e depois Fase 2 (sem reinicializar o cubo entre elas)
        composeTestRule.onNodeWithTag("play_phase_1_button").performClick()
        // Avança o relógio de corrotinas/animação até concluir a Fase 1 (6 movimentos)
        var guard = 0
        while (vm.lessonState.value.isPlayingDemo && guard < 60) {
            composeTestRule.mainClock.advanceTimeBy(250)
            composeTestRule.waitForIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper(250, java.util.concurrent.TimeUnit.MILLISECONDS)
            guard++
        }
        composeTestRule.waitForIdle()
        assertEquals("Executar Fase 1 deve parar no índice 5 (formato de L)", 5, vm.lessonState.value.currentMoveIndex)
        assertTrue("Cubo após Fase 1 automática deve exibir o L amarelo", vm.lessonState.value.cubeState.isYellowLShapeOnTop())

        // Agora aciona Fase 2 diretamente a partir do L (sem restaurar o cubo)
        composeTestRule.onNodeWithTag("play_phase_2_button").performClick()
        guard = 0
        while (vm.lessonState.value.isPlayingDemo && guard < 60) {
            composeTestRule.mainClock.advanceTimeBy(250)
            composeTestRule.waitForIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper(250, java.util.concurrent.TimeUnit.MILLISECONDS)
            guard++
        }
        composeTestRule.waitForIdle()
        assertEquals("Executar Fase 2 deve concluir no índice 11 (cruz amarela)", 11, vm.lessonState.value.currentMoveIndex)
        assertTrue("Cubo após Fase 2 automática deve ter a cruz amarela completa", vm.lessonState.value.cubeState.isYellowCrossOriented())
        assertTrue("Duas primeiras camadas permanecem resolvidas", vm.lessonState.value.cubeState.isSecondLayerSolved())

        // Reiniciar restaura a linha horizontal inicial sem dessincronização
        composeTestRule.onNodeWithTag("reset_demo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(-1, vm.lessonState.value.currentMoveIndex)
        assertEquals(initialCube, vm.lessonState.value.cubeState)
        ShadowTrace.reset()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testTransactionalPlaybackPauseResumeStepAndCancellationSyncSuite() {
        composeTestRule.waitForIdle()
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        ShadowTrace.reset()

        // Habilita execução multi-quadro determinística (0%..25%..50%..75%..100%)
        vm.forceMultiFrameAnimationInTest = true

        try {
            fun stepClock16ms() {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
            }

            // Função auxiliar para avançar o relógio determinístico até a animação/reprodução estabilizar
            fun drainAllAnimations(maxTicks: Int = 800) {
                var ticks = 0
                while ((vm.lessonState.value.isPlayingDemo ||
                        vm.lessonState.value.isPausePending ||
                        vm.lessonState.value.activeAnimation != null ||
                        vm.lessonState.value.executingMoveIndex != null) &&
                    ticks < maxTicks
                ) {
                    stepClock16ms()
                    ticks++
                }
                composeTestRule.waitForIdle()
            }

            // Função auxiliar que valida a invariante fundamental:
            // O CubeState deve corresponder EXATAMENTE (todas as 27 peças e orientações) a initialCube.applyMoves(moves.take(currentMoveIndex + 1))
            fun assertMathematicalSyncInvariant(initialCube: CubeState, moves: List<CubeMove>, context: String) {
                val state = vm.lessonState.value
                val confirmedCount = state.currentMoveIndex + 1
                assertTrue("confirmedCount ($confirmedCount) deve estar entre 0 e ${moves.size} ($context)", confirmedCount in 0..moves.size)
                val expectedCube = initialCube.applyMoves(moves.take(confirmedCount))
                assertEquals(
                    "Invariante violada em [$context]: CubeState diverge do estado matemático de $confirmedCount movimento(s) confirmado(s)",
                    expectedCube,
                    state.cubeState
                )
            }

            // =========================================================================
            // TESTE A & TESTE B: Pausa durante uma rotação (0%, 25%, 50%, 75%, antes do término e no intervalo)
            // + Retomada até o último movimento consolidando cada movimento exatamente 1 vez
            // =========================================================================
            data class PauseScenario(
                val name: String,
                val targetMoveNumber: Int, // 0 = 1º movimento, 1 = 2º movimento
                val pauseAtProgress: Float?, // null = pausar no intervalo entre o movimento 0 e o movimento 1
                val resumeWhilePausePending: Boolean = false
            )

            val scenarios = listOf(
                PauseScenario("1. Pausar antes do primeiro movimento (progress = 0.0)", targetMoveNumber = 0, pauseAtProgress = 0.0f),
                PauseScenario("2. Pausar em ~25% da animação do 1º movimento", targetMoveNumber = 0, pauseAtProgress = 0.25f),
                PauseScenario("3. Pausar em ~50% da animação do 1º movimento", targetMoveNumber = 0, pauseAtProgress = 0.50f),
                PauseScenario("4. Pausar em ~75% da animação do 1º movimento", targetMoveNumber = 0, pauseAtProgress = 0.75f),
                PauseScenario("5. Pausar imediatamente antes do término (100% pré-commit)", targetMoveNumber = 0, pauseAtProgress = 1.0f),
                PauseScenario("6. Pausar no intervalo entre o 1º e o 2º movimento", targetMoveNumber = 0, pauseAtProgress = null),
                PauseScenario("7. Pausar em ~50% do 2º movimento e retomar ainda durante pause-pending", targetMoveNumber = 1, pauseAtProgress = 0.50f, resumeWhilePausePending = true)
            )

            for (scenario in scenarios) {
                composeTestRule.runOnUiThread {
                    vm.openLesson(stepNumber = 2, caseIndex = 2, mode = com.example.viewmodel.LearningMode.LEARN)
                    vm.committedLessonMovesForAudit.clear()
                    vm.recordedAnimationFramesForAudit.clear()
                }
                composeTestRule.waitForIdle()

                val initialCube = vm.lessonState.value.cubeState
                val moves = vm.lessonState.value.demoMoves
                assertEquals(listOf(CubeMove.R, CubeMove.U2, CubeMove.R_PRIME, CubeMove.U_PRIME), moves.take(4))
                val expectedFinalCube = initialCube.applyMoves(moves)

                var pauseTriggered = false
                if (scenario.pauseAtProgress != null) {
                    vm.onAnimationFrameEmitted = { _, frame, totalFrames, progress ->
                        val execIdx = vm.lessonState.value.executingMoveIndex
                        val targetProgress = scenario.pauseAtProgress
                        val matchesFrame = if (targetProgress <= 0f) {
                            frame == 0
                        } else if (targetProgress >= 1f) {
                            frame == totalFrames
                        } else {
                            kotlin.math.abs(progress - targetProgress) < 0.001f
                        }
                        if (!pauseTriggered && execIdx == scenario.targetMoveNumber && matchesFrame) {
                            pauseTriggered = true
                            // Antes de pausar, o movimento em execução AINDA NÃO pode ter sido considerado concluído!
                            assertEquals(
                                "Antes do commit em [${scenario.name}], currentMoveIndex deve ser ${scenario.targetMoveNumber - 1}",
                                scenario.targetMoveNumber - 1,
                                vm.lessonState.value.currentMoveIndex
                            )
                            assertMathematicalSyncInvariant(initialCube, moves, "${scenario.name} - instante exato antes de pauseDemo()")
                            vm.pauseDemo()
                            // Imediatamente após pauseDemo(), currentMoveIndex e cubeState continuam 100% sincronizados
                            assertMathematicalSyncInvariant(initialCube, moves, "${scenario.name} - imediatamente após pauseDemo()")
                        }
                    }
                } else {
                    vm.onAnimationFrameEmitted = null
                }

                // Inicia reprodução
                composeTestRule.runOnUiThread {
                    vm.togglePlayDemo()
                }

                if (scenario.pauseAtProgress == null) {
                    // Avança exatamente até o 1º movimento terminar (entra no delay entre movimento 0 e movimento 1)
                    var ticks = 0
                    while (vm.lessonState.value.currentMoveIndex < 0 && ticks < 100) {
                        stepClock16ms()
                        ticks++
                    }
                    // Estamos no intervalo entre o 1º movimento (concluído, índice 0) e o 2º movimento (ainda não iniciado)
                    assertEquals(0, vm.lessonState.value.currentMoveIndex)
                    assertNull(vm.lessonState.value.activeAnimation)
                    composeTestRule.runOnUiThread {
                        vm.pauseDemo()
                    }
                    pauseTriggered = true
                } else {
                    // Avança até o gatilho de pausa ser acionado
                    var ticks = 0
                    while (!pauseTriggered && ticks < 150) {
                        stepClock16ms()
                        ticks++
                    }
                }

                assertTrue("Cenário [${scenario.name}] deve ter acionado a pausa", pauseTriggered)
                vm.onAnimationFrameEmitted = null

                if (scenario.resumeWhilePausePending) {
                    // Retoma imediatamente enquanto o movimento pausado ainda está finalizando seus quadros (isPausePending == true)
                    assertTrue(vm.lessonState.value.isPausePending)
                    composeTestRule.runOnUiThread {
                        vm.togglePlayDemo()
                    }
                    assertFalse(vm.lessonState.value.isPausePending)
                    assertTrue(vm.lessonState.value.isPlayingDemo)
                } else {
                    // Aguarda a pausa estabilizar (finalizar o movimento em curso se progress > 0)
                    drainAllAnimations()
                    assertFalse("Demonstração deve estar pausada em [${scenario.name}]", vm.lessonState.value.isPlayingDemo)
                    assertFalse("isPausePending deve ser falso após estabilizar em [${scenario.name}]", vm.lessonState.value.isPausePending)
                    assertNull("activeAnimation deve ser nulo após estabilizar em [${scenario.name}]", vm.lessonState.value.activeAnimation)
                    assertNull("executingMoveIndex deve ser nulo após estabilizar em [${scenario.name}]", vm.lessonState.value.executingMoveIndex)

                    val expectedIndexAfterPause = if (scenario.pauseAtProgress != null && scenario.pauseAtProgress <= 0f) {
                        scenario.targetMoveNumber - 1
                    } else {
                        scenario.targetMoveNumber
                    }
                    assertEquals(
                        "Índice confirmado após pausa em [${scenario.name}]",
                        expectedIndexAfterPause,
                        vm.lessonState.value.currentMoveIndex
                    )
                    assertMathematicalSyncInvariant(initialCube, moves, "${scenario.name} - após estabilizar pausa")

                    // TESTE B: Retoma a demonstração a partir da pausa
                    composeTestRule.runOnUiThread {
                        vm.togglePlayDemo()
                    }
                }

                // Executa até o último movimento da demonstração
                drainAllAnimations(maxTicks = 800)

                assertFalse(vm.lessonState.value.isPlayingDemo)
                assertTrue("Demonstração deve estar marcada como concluída após retomada em [${scenario.name}]", vm.lessonState.value.isDemoCompleted)
                assertEquals(moves.lastIndex, vm.lessonState.value.currentMoveIndex)
                assertEquals(
                    "CubeState final após retomada em [${scenario.name}] deve ser idêntico à aplicação matemática sequencial completa",
                    expectedFinalCube,
                    vm.lessonState.value.cubeState
                )
                assertEquals(
                    "Cada movimento do algoritmo deve ter sido consolidado EXATAMENTE UMA VEZ em [${scenario.name}]",
                    moves,
                    vm.committedLessonMovesForAudit.toList()
                )
                ShadowTrace.reset()
            }

            // =========================================================================
            // TESTE C: Avançar e Retroceder (stepDemoForward / stepDemoBackward)
            // incluindo chamadas rápidas/concorrentes durante animações em andamento
            // =========================================================================
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 5, caseIndex = 0, mode = com.example.viewmodel.LearningMode.LEARN) // Sune: R U R' U R U2 R'
                vm.committedLessonMovesForAudit.clear()
            }
            composeTestRule.waitForIdle()

            val suneInitial = vm.lessonState.value.cubeState
            val suneMoves = vm.lessonState.value.demoMoves
            assertEquals(7, suneMoves.size)

            // C1: Avança 3 movimentos sequencialmente, verificando sincronização a cada passo
            for (expectedIdx in 0..2) {
                composeTestRule.runOnUiThread { vm.stepDemoForward() }
                drainAllAnimations()
                assertEquals(expectedIdx, vm.lessonState.value.currentMoveIndex)
                assertMathematicalSyncInvariant(suneInitial, suneMoves, "Teste C1 - Avançar passo $expectedIdx")
            }

            // C2: Retrocede 2 movimentos sequencialmente, verificando restauração exata pelo inverso
            for (expectedIdx in 1 downTo 0) {
                composeTestRule.runOnUiThread { vm.stepDemoBackward() }
                drainAllAnimations()
                assertEquals(expectedIdx, vm.lessonState.value.currentMoveIndex)
                assertMathematicalSyncInvariant(suneInitial, suneMoves, "Teste C2 - Retroceder para $expectedIdx")
            }

            // C3: Rajada rápida de comandos enquanto uma camada gira (Avançar x3 + Anterior x2 + Avançar x1 -> saldo +2 -> índice 2)
            composeTestRule.runOnUiThread {
                vm.stepDemoForward()
            }
            // Avança 48ms (no meio da rotação do movimento 1) e dispara vários comandos rápidos concorrentes
            composeTestRule.mainClock.advanceTimeBy(48)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(48, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertNotNull("Camada deve estar girando durante a rajada concorrente", vm.lessonState.value.activeAnimation)

            composeTestRule.runOnUiThread {
                vm.stepDemoForward()
                vm.stepDemoForward()
                vm.stepDemoBackward()
                vm.stepDemoBackward()
                vm.stepDemoForward()
            }
            drainAllAnimations(maxTicks = 600)

            assertEquals("Após rajada rápida (+3 -2 +1 a partir de 0), currentMoveIndex deve ser 2", 2, vm.lessonState.value.currentMoveIndex)
            assertMathematicalSyncInvariant(suneInitial, suneMoves, "Teste C3 - Após rajada concorrente de Avançar/Anterior")

            // Retrocede os 3 movimentos restantes até o estado inicial (-1)
            composeTestRule.runOnUiThread {
                vm.stepDemoBackward()
                vm.stepDemoBackward()
                vm.stepDemoBackward()
            }
            drainAllAnimations()
            assertEquals(-1, vm.lessonState.value.currentMoveIndex)
            assertEquals("Retroceder até -1 deve restaurar o estado inicial exato do caso", suneInitial, vm.lessonState.value.cubeState)
            ShadowTrace.reset()

            // =========================================================================
            // TESTE D: Cancelamentos seguros e navegação durante animações ativas
            // (Reiniciar, trocar de caso, trocar de etapa, alternar modo, sair da aula)
            // =========================================================================
            // D1: Reiniciar no meio de uma animação
            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            composeTestRule.mainClock.advanceTimeBy(80)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(80, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertNotNull(vm.lessonState.value.activeAnimation)

            composeTestRule.runOnUiThread {
                vm.resetLessonCube()
            }
            // Avança o tempo para provar que a corrotina cancelada jamais modifica o estado após o reset
            drainAllAnimations()
            assertEquals(-1, vm.lessonState.value.currentMoveIndex)
            assertNull(vm.lessonState.value.activeAnimation)
            assertNull(vm.lessonState.value.executingMoveIndex)
            assertFalse(vm.lessonState.value.isPlayingDemo)
            assertEquals(suneInitial, vm.lessonState.value.cubeState)

            // D2: Trocar de caso durante animação
            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            composeTestRule.mainClock.advanceTimeBy(80)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(80, java.util.concurrent.TimeUnit.MILLISECONDS)
            composeTestRule.runOnUiThread {
                vm.selectCase(1)
            }
            val expectedCase1Cube = com.example.curriculum.CurriculumRepository.getStep(5).cases[1].createInitialState()
            drainAllAnimations()
            assertEquals(1, vm.lessonState.value.caseIndex)
            assertEquals(-1, vm.lessonState.value.currentMoveIndex)
            assertNull(vm.lessonState.value.activeAnimation)
            assertEquals(expectedCase1Cube, vm.lessonState.value.cubeState)

            // D3: Trocar de etapa durante animação
            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            composeTestRule.mainClock.advanceTimeBy(80)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(80, java.util.concurrent.TimeUnit.MILLISECONDS)
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 6, caseIndex = 0, mode = com.example.viewmodel.LearningMode.LEARN)
            }
            val expectedStep6Cube = com.example.curriculum.CurriculumRepository.getStep(6).cases[0].createInitialState()
            drainAllAnimations()
            assertEquals(6, vm.lessonState.value.stepNumber)
            assertEquals(-1, vm.lessonState.value.currentMoveIndex)
            assertNull(vm.lessonState.value.activeAnimation)
            assertEquals(expectedStep6Cube, vm.lessonState.value.cubeState)

            // D4: Alternar para Modo Praticar durante animação
            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            composeTestRule.mainClock.advanceTimeBy(80)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(80, java.util.concurrent.TimeUnit.MILLISECONDS)
            composeTestRule.runOnUiThread {
                vm.selectLearningMode(com.example.viewmodel.LearningMode.PRACTICE)
            }
            drainAllAnimations()
            assertEquals(com.example.viewmodel.LearningMode.PRACTICE, vm.lessonState.value.mode)
            assertNull(vm.lessonState.value.activeAnimation)
            assertEquals(expectedStep6Cube, vm.lessonState.value.cubeState)

            // D5: Sair da aula (navegar para Home / Explorar) durante animação
            composeTestRule.runOnUiThread {
                vm.selectLearningMode(com.example.viewmodel.LearningMode.LEARN)
                vm.stepDemoForward()
            }
            drainAllAnimations()
            val confirmedAfterOneMove = vm.lessonState.value.cubeState
            assertEquals(0, vm.lessonState.value.currentMoveIndex)

            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            composeTestRule.mainClock.advanceTimeBy(80)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(80, java.util.concurrent.TimeUnit.MILLISECONDS)
            // Cancela navegando para Home no meio do 2º movimento: o estado confirmado do 1º movimento deve ser preservado sem aplicar o 2º parcial!
            composeTestRule.runOnUiThread {
                vm.navigateTo(com.example.viewmodel.AppScreen.HOME)
            }
            drainAllAnimations()
            assertEquals(com.example.viewmodel.AppScreen.HOME, vm.currentScreen.value)
            assertNull(vm.lessonState.value.activeAnimation)
            assertNull(vm.lessonState.value.executingMoveIndex)
            assertEquals(0, vm.lessonState.value.currentMoveIndex)
            assertEquals(confirmedAfterOneMove, vm.lessonState.value.cubeState)
            ShadowTrace.reset()

            // =========================================================================
            // TESTE E: Duas fases pedagógicas do Caso 2 da Etapa 4 (12 movimentos: F U R U' R' F' F U R U' R' F')
            // Pausa, retomada, avanço e retrocesso antes, durante e depois da transição entre os movimentos 6 e 7
            // =========================================================================
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 4, caseIndex = 1, mode = com.example.viewmodel.LearningMode.LEARN)
                vm.committedLessonMovesForAudit.clear()
            }
            composeTestRule.waitForIdle()

            val s4c2Initial = vm.lessonState.value.cubeState
            val s4c2Moves = vm.lessonState.value.demoMoves
            assertEquals(12, s4c2Moves.size)
            assertTrue(s4c2Initial.isYellowHorizontalLineOnTop())

            // E1: Inicia Fase 1, pausa no meio do 3º movimento (índice 2) a 50%, e retoma Fase 1 até o formato de L (índice 5)
            var pausedMidPhase1 = false
            vm.onAnimationFrameEmitted = { _, _, _, progress ->
                if (!pausedMidPhase1 && vm.lessonState.value.executingMoveIndex == 2 && kotlin.math.abs(progress - 0.50f) < 0.001f) {
                    pausedMidPhase1 = true
                    vm.pauseDemo()
                }
            }
            composeTestRule.runOnUiThread {
                vm.playDemoPhase(1)
            }
            var guard = 0
            while (!pausedMidPhase1 && guard < 150) {
                stepClock16ms()
                guard++
            }
            assertTrue(pausedMidPhase1)
            vm.onAnimationFrameEmitted = null
            drainAllAnimations()

            // Deve ter consolidado exatamente os 3 primeiros movimentos (índices 0, 1, 2 -> currentMoveIndex == 2)
            assertEquals(2, vm.lessonState.value.currentMoveIndex)
            assertMathematicalSyncInvariant(s4c2Initial, s4c2Moves, "Teste E1 - Após pausa no meio da Fase 1")

            // Retoma Fase 1 até concluir o 6º movimento (índice 5 -> formato de L)
            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            drainAllAnimations(maxTicks = 400)

            assertEquals("Fase 1 retomada deve parar exatamente no índice 5 (6º movimento F')", 5, vm.lessonState.value.currentMoveIndex)
            assertFalse(vm.lessonState.value.isPlayingDemo)
            assertTrue("Após Fase 1, o topo deve estar no formato de L", vm.lessonState.value.cubeState.isYellowLShapeOnTop())
            assertTrue("Duas primeiras camadas intactas no L", vm.lessonState.value.cubeState.isSecondLayerSolved())
            assertMathematicalSyncInvariant(s4c2Initial, s4c2Moves, "Teste E1 - Fim da Fase 1 (formato de L)")

            // E2: Na fronteira entre Fase 1 (índice 5, F') e Fase 2 (índice 6, F):
            // Avança para o movimento 7 (índice 6, F), retrocede para o movimento 6 (índice 5, F'), retrocede para o 5 (índice 4, R') e avança de volta ao 6 (índice 5)
            composeTestRule.runOnUiThread { vm.stepDemoForward() } // executa índice 6 (F)
            drainAllAnimations()
            assertEquals(6, vm.lessonState.value.currentMoveIndex)
            assertMathematicalSyncInvariant(s4c2Initial, s4c2Moves, "Teste E2 - Avançou para índice 6 (início da Fase 2)")

            composeTestRule.runOnUiThread { vm.stepDemoBackward() } // desfaz índice 6 (F), voltando ao índice 5 (L)
            drainAllAnimations()
            assertEquals(5, vm.lessonState.value.currentMoveIndex)
            assertTrue("Retroceder do índice 6 para 5 deve restaurar o formato de L", vm.lessonState.value.cubeState.isYellowLShapeOnTop())
            assertMathematicalSyncInvariant(s4c2Initial, s4c2Moves, "Teste E2 - Retrocedeu para índice 5 (formato de L)")

            composeTestRule.runOnUiThread { vm.stepDemoBackward() } // desfaz índice 5 (F'), voltando ao índice 4
            drainAllAnimations()
            assertEquals(4, vm.lessonState.value.currentMoveIndex)
            assertMathematicalSyncInvariant(s4c2Initial, s4c2Moves, "Teste E2 - Retrocedeu para índice 4")

            composeTestRule.runOnUiThread { vm.stepDemoForward() } // refaz índice 5 (F'), voltando ao L
            drainAllAnimations()
            assertEquals(5, vm.lessonState.value.currentMoveIndex)
            assertTrue(vm.lessonState.value.cubeState.isYellowLShapeOnTop())

            // E3: Inicia Fase 2 a partir do L (sem restaurar o cubo), pausa no meio do movimento 9 (índice 8) a 75% e retoma até o final (índice 11)
            var pausedMidPhase2 = false
            vm.onAnimationFrameEmitted = { _, _, _, progress ->
                if (!pausedMidPhase2 && vm.lessonState.value.executingMoveIndex == 8 && kotlin.math.abs(progress - 0.75f) < 0.001f) {
                    pausedMidPhase2 = true
                    vm.pauseDemo()
                }
            }
            composeTestRule.runOnUiThread {
                vm.playDemoPhase(2)
            }
            guard = 0
            while (!pausedMidPhase2 && guard < 150) {
                stepClock16ms()
                guard++
            }
            assertTrue(pausedMidPhase2)
            vm.onAnimationFrameEmitted = null
            drainAllAnimations()

            assertEquals(8, vm.lessonState.value.currentMoveIndex)
            assertMathematicalSyncInvariant(s4c2Initial, s4c2Moves, "Teste E3 - Após pausa no meio da Fase 2")

            // Retoma até concluir a cruz amarela (índice 11)
            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            drainAllAnimations(maxTicks = 400)

            assertEquals(11, vm.lessonState.value.currentMoveIndex)
            assertTrue(vm.lessonState.value.isDemoCompleted)
            assertTrue("Cruz amarela deve estar completa ao final da Fase 2", vm.lessonState.value.cubeState.isYellowCrossOriented())
            assertTrue("Duas primeiras camadas devem permanecer resolvidas", vm.lessonState.value.cubeState.isSecondLayerSolved())
            assertTrue("Objetivo da Etapa 4 deve ser atingido", vm.lessonState.value.cubeState.isStepGoalAchieved(4))
            assertMathematicalSyncInvariant(s4c2Initial, s4c2Moves, "Teste E3 - Conclusão da Fase 2 (Cruz Amarela)")
        } finally {
            vm.onAnimationFrameEmitted = null
            vm.forceMultiFrameAnimationInTest = false
            ShadowTrace.reset()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testMultitouchPointerTransitionsPinchCameraAndLayerArbitrationSuite() {
        composeTestRule.waitForIdle()
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowTrace.reset()

        // Construtor determinístico de MotionEvent real (1, 2 ou 3+ ponteiros com IDs e índices arbitrários)
        data class PointerSpec(val id: Int, val x: Float, val y: Float)

        fun buildMotionEvent(
            downTime: Long,
            eventTime: Long,
            actionMasked: Int,
            actionIndex: Int = 0,
            pointers: List<PointerSpec>
        ): android.view.MotionEvent {
            val props = Array(pointers.size) { idx ->
                android.view.MotionEvent.PointerProperties().apply {
                    id = pointers[idx].id
                    toolType = android.view.MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = Array(pointers.size) { idx ->
                android.view.MotionEvent.PointerCoords().apply {
                    x = pointers[idx].x
                    y = pointers[idx].y
                    pressure = 1.0f
                    size = 1.0f
                }
            }
            val combinedAction = if (actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN ||
                actionMasked == android.view.MotionEvent.ACTION_POINTER_UP
            ) {
                actionMasked or (actionIndex shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            } else {
                actionMasked
            }
            return android.view.MotionEvent.obtain(
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
                android.view.InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
        }

        val renderer = CubeGLRenderer()
        renderer.configureViewport(800, 800)
        renderer.resetCamera()

        var cubeState = CubeState.createSolved()
        val dispatchedMoves = mutableListOf<CubeMove>()
        var isAnimating = false
        var touchMode = TouchInteractionMode.MOVE_LAYERS
        var allowFaceSwipe = true

        val glView = com.example.renderer.CubeTouchGLSurfaceView(
            context = context,
            cubeRenderer = renderer,
            allowFaceSwipeProvider = { allowFaceSwipe },
            isAnimatingProvider = { isAnimating },
            touchModeProvider = { touchMode },
            onSwipeMove = { move ->
                dispatchedMoves.add(move)
                cubeState = cubeState.applyMove(move)
            }
        )

        fun sendEvent(
            downTime: Long,
            eventTime: Long,
            actionMasked: Int,
            actionIndex: Int = 0,
            vararg pointers: PointerSpec
        ) {
            val ev = buildMotionEvent(downTime, eventTime, actionMasked, actionIndex, pointers.toList())
            try {
                glView.onTouchEvent(ev)
            } finally {
                ev.recycle()
            }
        }

        // =========================================================================
        // TESTE A: Pinça normal
        // Sequência: ACTION_DOWN -> ACTION_POINTER_DOWN -> ACTION_MOVE (2 dedos) -> ACTION_POINTER_UP -> ACTION_UP
        // =========================================================================
        renderer.resetCamera()
        dispatchedMoves.clear()
        cubeState = CubeState.createSolved()
        val initialDistA = renderer.cameraDistance
        val initialYawA = renderer.cameraYaw
        val initialPitchA = renderer.cameraPitch

        val t0 = 1000L
        sendEvent(t0, t0, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 300f, 400f))
        assertEquals(GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)
        assertTrue("Toque na Home/cubo deve sinalizar isUserTouching = true", renderer.isUserTouching)

        sendEvent(t0, t0 + 16, android.view.MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(0, 300f, 400f), PointerSpec(1, 500f, 400f))
        assertEquals(GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)
        assertNull("Pinça deve limpar previewTouchHit", renderer.previewTouchHit)
        assertNull("Pinça deve limpar previewTouchMove", renderer.previewTouchMove)

        // Afasta os 2 dedos (span de 200px para 360px -> aproxima o zoom / reduz cameraDistance)
        sendEvent(t0, t0 + 32, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 220f, 400f), PointerSpec(1, 580f, 400f))
        assertTrue("Distância da câmera deve diminuir ao afastar os 2 dedos na pinça", renderer.cameraDistance < initialDistA)
        assertEquals("Pinça não pode alterar cameraYaw", initialYawA, renderer.cameraYaw, 0.0f)
        assertEquals("Pinça não pode alterar cameraPitch", initialPitchA, renderer.cameraPitch, 0.0f)
        assertTrue("Nenhuma camada pode girar durante a pinça", dispatchedMoves.isEmpty())

        sendEvent(t0, t0 + 48, android.view.MotionEvent.ACTION_POINTER_UP, 1, PointerSpec(0, 220f, 400f), PointerSpec(1, 580f, 400f))
        assertEquals("Após retirar 1 dedo, restando 1 dedo, deve sair de PINCH_ZOOM para PENDING_GESTURE", GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)
        assertTrue("Enquanto 1 dedo permanece na tela, isUserTouching deve continuar true", renderer.isUserTouching)

        sendEvent(t0, t0 + 64, android.view.MotionEvent.ACTION_UP, 0, PointerSpec(0, 220f, 400f))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)
        assertFalse("Ao retirar todos os dedos, isUserTouching deve voltar para false", renderer.isUserTouching)
        assertEquals(CubeState.createSolved(), cubeState)
        assertTrue(dispatchedMoves.isEmpty())

        // =========================================================================
        // TESTE B: Transição de pinça para câmera (2 dedos -> retira 1 dedo -> orbita câmera com o dedo restante)
        // =========================================================================
        renderer.resetCamera()
        touchMode = TouchInteractionMode.ROTATE_CAMERA
        val tB = 2000L
        sendEvent(tB, tB, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 250f, 350f))
        sendEvent(tB, tB + 16, android.view.MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(0, 250f, 350f), PointerSpec(1, 550f, 450f))
        // Aproxima os 2 dedos (reduz span -> afasta cameraDistance)
        sendEvent(tB, tB + 32, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 320f, 380f), PointerSpec(1, 480f, 420f))
        val distAfterPinchB = renderer.cameraDistance
        assertTrue("Pinça aproximando dedos deve aumentar cameraDistance", distAfterPinchB > CubeGLRenderer.DEFAULT_DISTANCE)
        assertEquals(CubeGLRenderer.DEFAULT_YAW, renderer.cameraYaw, 0.0f)
        assertEquals(CubeGLRenderer.DEFAULT_PITCH, renderer.cameraPitch, 0.0f)

        // Levanta o dedo 0 (índice 0), deixando APENAS o dedo 1 (em 480, 420) na tela!
        sendEvent(tB, tB + 48, android.view.MotionEvent.ACTION_POINTER_UP, 0, PointerSpec(0, 320f, 380f), PointerSpec(1, 480f, 420f))
        assertEquals(GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)
        assertEquals(1, glView.activePointerId)
        assertEquals(480f, glView.arbiter.downX, 0.01f)
        assertEquals(420f, glView.arbiter.downY, 0.01f)
        assertEquals("Retirar um dedo não pode causar salto em cameraYaw", CubeGLRenderer.DEFAULT_YAW, renderer.cameraYaw, 0.0f)
        assertEquals("Retirar um dedo não pode causar salto em cameraPitch", CubeGLRenderer.DEFAULT_PITCH, renderer.cameraPitch, 0.0f)

        // Move o dedo 1 (que agora está no índice 0 do MotionEvent mas com id = 1) abaixo do touch slop (3px) -> ainda não orbita
        sendEvent(tB, tB + 64, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(1, 483f, 422f))
        assertEquals(GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)
        assertEquals(CubeGLRenderer.DEFAULT_YAW, renderer.cameraYaw, 0.0f)

        // Continua arrastando o dedo 1 suavemente (+20px X, -15px Y e depois +20px X, -15px Y) -> deve orbitar sem salto brusco!
        sendEvent(tB, tB + 80, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(1, 503f, 407f))
        assertEquals(GestureInteractionState.CAMERA_ORBIT, glView.arbiter.state)
        val yawStep1 = renderer.cameraYaw
        val pitchStep1 = renderer.cameraPitch
        val deltaYaw1 = kotlin.math.abs(yawStep1 - CubeGLRenderer.DEFAULT_YAW)
        assertTrue("Órbita pós-pinça deve ser suave e proporcional (sem salto de troca de ponteiro): deltaYaw1=$deltaYaw1", deltaYaw1 in 1f..15f)

        sendEvent(tB, tB + 96, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(1, 523f, 392f))
        val yawStep2 = renderer.cameraYaw
        val pitchStep2 = renderer.cameraPitch
        assertTrue("Órbita deve continuar de forma contínua", yawStep2 < yawStep1 && pitchStep2 < pitchStep1)
        assertEquals("Distância do zoom deve ser preservada durante a órbita", distAfterPinchB, renderer.cameraDistance, 0.001f)
        assertTrue("Nenhuma camada pode girar no modo Câmera", dispatchedMoves.isEmpty())

        sendEvent(tB, tB + 112, android.view.MotionEvent.ACTION_UP, 0, PointerSpec(1, 523f, 392f))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)
        assertEquals(yawStep2, renderer.cameraYaw, 0.0f)
        assertEquals(pitchStep2, renderer.cameraPitch, 0.0f)

        // =========================================================================
        // TESTE C: Transição de pinça para camadas (2 dedos -> retira 1 dedo -> arrasta peça no modo Camadas)
        // =========================================================================
        renderer.resetCamera()
        touchMode = TouchInteractionMode.MOVE_LAYERS
        dispatchedMoves.clear()
        cubeState = CubeState.createSolved()

        // Projeta a peça frontal-direita (x = +1, y = 0, z = +1.45) na tela após um leve zoom
        val tC = 3000L
        sendEvent(tC, tC, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 260f, 400f))
        sendEvent(tC, tC + 16, android.view.MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(0, 260f, 400f), PointerSpec(1, 540f, 400f))
        sendEvent(tC, tC + 32, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 220f, 400f), PointerSpec(1, 580f, 400f))
        assertEquals(GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)
        assertTrue("Nenhum movimento disparado durante a pinça", dispatchedMoves.isEmpty())

        // Posiciona o ponteiro 1 exatamente sobre a coluna direita da face frontal (x = +0.95, y = 0.0, z = +1.45) antes de soltar o ponteiro 0
        val rightColPt = renderer.projectWorldToScreen(floatArrayOf(0.95f, 0.0f, 1.45f))!!
        sendEvent(tC, tC + 48, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 200f, 400f), PointerSpec(1, rightColPt[0], rightColPt[1]))
        assertTrue("Movimento com 2 dedos sobre peça jamais deve girar camada", dispatchedMoves.isEmpty())

        // Retira o ponteiro 0 (actionIndex = 0), mantendo o ponteiro 1 (id = 1) sobre a peça da coluna R
        sendEvent(tC, tC + 64, android.view.MotionEvent.ACTION_POINTER_UP, 0, PointerSpec(0, 200f, 400f), PointerSpec(1, rightColPt[0], rightColPt[1]))
        assertEquals(GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)
        assertTrue("Retirar o segundo dedo jamais pode disparar uma rotação instantânea", dispatchedMoves.isEmpty())
        assertNull("Ao retirar o segundo dedo, nenhuma peça antiga deve permanecer destacada", renderer.previewTouchHit)
        assertNull("Ao retirar o segundo dedo, nenhuma seta de movimento deve estar ativa", renderer.previewTouchMove)

        // Movimento abaixo do layerSwipeSlopPx (ex: 6px para cima) -> ainda NÃO dispara o giro da camada
        sendEvent(tC, tC + 80, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(1, rightColPt[0], rightColPt[1] - 6f))
        assertTrue("Deslocamento menor que layerSwipeSlopPx não pode disparar giro de camada", dispatchedMoves.isEmpty())

        // Arrasta intencionalmente para cima além do layerSwipeSlopPx (-85px em Y) -> deve disparar exatamente R!
        val yawBeforeLayerSwipe = renderer.cameraYaw
        val pitchBeforeLayerSwipe = renderer.cameraPitch
        sendEvent(tC, tC + 96, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(1, rightColPt[0] + 10f, rightColPt[1] - 85f))
        assertEquals(GestureInteractionState.LAYER_ROTATION, glView.arbiter.state)
        assertEquals("Deve disparar exatamente o movimento R após superar o limiar", listOf(CubeMove.R), dispatchedMoves)
        assertEquals("Câmera (Yaw) não pode girar no modo Camadas", yawBeforeLayerSwipe, renderer.cameraYaw, 0.0f)
        assertEquals("Câmera (Pitch) não pode girar no modo Camadas", pitchBeforeLayerSwipe, renderer.cameraPitch, 0.0f)

        // Continuar arrastando no mesmo gesto não pode duplicar o movimento
        sendEvent(tC, tC + 112, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(1, rightColPt[0] + 20f, rightColPt[1] - 150f))
        assertEquals("Exatamente 1 movimento por gesto deve ser disparado", 1, dispatchedMoves.size)

        sendEvent(tC, tC + 128, android.view.MotionEvent.ACTION_UP, 0, PointerSpec(1, rightColPt[0] + 20f, rightColPt[1] - 150f))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)
        assertEquals(CubeState.createSolved().applyMove(CubeMove.R), cubeState)

        // =========================================================================
        // TESTE D: Identidade dos ponteiros (ID != 0, troca de ordem de índices, retirada do 1º vs 2º dedo)
        // =========================================================================
        renderer.resetCamera()
        touchMode = TouchInteractionMode.ROTATE_CAMERA
        val tD = 4000L

        // D1: Ponteiros com IDs não-sequenciais (id = 3 em (150, 200) e id = 7 em (620, 580)).
        // Retira o segundo dedo (id = 7, actionIndex = 1) e mantém o primeiro (id = 3, actionIndex = 0).
        sendEvent(tD, tD, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(3, 150f, 200f))
        assertEquals(3, glView.activePointerId)
        sendEvent(tD, tD + 16, android.view.MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(3, 150f, 200f), PointerSpec(7, 620f, 580f))
        sendEvent(tD, tD + 32, android.view.MotionEvent.ACTION_POINTER_UP, 1, PointerSpec(3, 155f, 205f), PointerSpec(7, 620f, 580f))
        assertEquals("Ponteiro sobrevivente deve ser id=3", 3, glView.activePointerId)
        assertEquals(155f, glView.arbiter.downX, 0.01f)
        assertEquals(205f, glView.arbiter.downY, 0.01f)

        // Agora reconecta id = 7 no índice 0 e id = 3 no índice 1 (ordem de índices invertida!) e retira id = 3 (actionIndex = 1)
        sendEvent(tD, tD + 48, android.view.MotionEvent.ACTION_POINTER_DOWN, 0, PointerSpec(7, 610f, 570f), PointerSpec(3, 155f, 205f))
        sendEvent(tD, tD + 64, android.view.MotionEvent.ACTION_POINTER_UP, 1, PointerSpec(7, 610f, 570f), PointerSpec(3, 155f, 205f))
        assertEquals("Ponteiro sobrevivente deve ser id=7", 7, glView.activePointerId)
        assertEquals(610f, glView.arbiter.downX, 0.01f)
        assertEquals(570f, glView.arbiter.downY, 0.01f)

        // Move id=7 por (25px, 0px) e verifica ausência total de salto brusco entre (155, 205) e (610, 570)
        val yawBeforeDMove = renderer.cameraYaw
        sendEvent(tD, tD + 80, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(7, 635f, 570f))
        val yawDeltaD = kotlin.math.abs(renderer.cameraYaw - yawBeforeDMove)
        assertTrue("Deslocamento deve usar apenas os 25px do ponteiro id=7 (yawDelta=$yawDeltaD)", yawDeltaD in 5f..15f)
        sendEvent(tD, tD + 96, android.view.MotionEvent.ACTION_UP, 0, PointerSpec(7, 635f, 570f))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)

        // =========================================================================
        // TESTE E: Três dedos (1 dedo -> 2 dedos -> 3 dedos -> 2 dedos -> 1 dedo -> nenhum dedo)
        // =========================================================================
        renderer.resetCamera()
        touchMode = TouchInteractionMode.MOVE_LAYERS
        dispatchedMoves.clear()
        cubeState = CubeState.createSolved()
        val tE = 5000L

        // 1 dedo
        sendEvent(tE, tE, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 250f, 400f))
        assertEquals(1, glView.activePointerCount)
        assertEquals(GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)

        // 2 dedos
        sendEvent(tE, tE + 16, android.view.MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(0, 250f, 400f), PointerSpec(1, 550f, 400f))
        assertEquals(2, glView.activePointerCount)
        assertEquals(GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)

        // 3 dedos
        sendEvent(
            tE, tE + 32, android.view.MotionEvent.ACTION_POINTER_DOWN, 2,
            PointerSpec(0, 250f, 400f), PointerSpec(1, 550f, 400f), PointerSpec(2, 400f, 200f)
        )
        assertEquals(3, glView.activePointerCount)
        assertEquals(GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)

        // Move com 3 dedos (afastando do centro) -> ajusta zoom previsivelmente sem girar camadas
        val distBefore3FingerPinch = renderer.cameraDistance
        sendEvent(
            tE, tE + 48, android.view.MotionEvent.ACTION_MOVE, 0,
            PointerSpec(0, 200f, 430f), PointerSpec(1, 600f, 430f), PointerSpec(2, 400f, 140f)
        )
        assertTrue("Pinça com 3 dedos deve ajustar cameraDistance", renderer.cameraDistance < distBefore3FingerPinch)
        assertTrue("Pinça com 3 dedos não pode disparar movimentos de camada", dispatchedMoves.isEmpty())

        // 3 dedos -> 2 dedos (retira o ponteiro do meio no índice 1, id = 1)
        sendEvent(
            tE, tE + 64, android.view.MotionEvent.ACTION_POINTER_UP, 1,
            PointerSpec(0, 200f, 430f), PointerSpec(1, 600f, 430f), PointerSpec(2, 400f, 140f)
        )
        assertEquals(2, glView.activePointerCount)
        assertEquals("Com 2 dedos restantes na tela, o estado deve permanecer PINCH_ZOOM", GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)
        assertTrue(dispatchedMoves.isEmpty())

        // Move com os 2 dedos restantes (id=0 e id=2)
        val topMidPt = renderer.projectWorldToScreen(floatArrayOf(0.0f, 0.95f, 1.45f))!!
        sendEvent(
            tE, tE + 80, android.view.MotionEvent.ACTION_MOVE, 0,
            PointerSpec(0, 180f, 440f), PointerSpec(2, topMidPt[0], topMidPt[1])
        )
        assertEquals(GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)
        assertTrue(dispatchedMoves.isEmpty())

        // 2 dedos -> 1 dedo (retira id=0 no actionIndex=0, restando apenas id=2 sobre o topo da face frontal)
        sendEvent(
            tE, tE + 96, android.view.MotionEvent.ACTION_POINTER_UP, 0,
            PointerSpec(0, 180f, 440f), PointerSpec(2, topMidPt[0], topMidPt[1])
        )
        assertEquals(1, glView.activePointerCount)
        assertEquals(2, glView.activePointerId)
        assertEquals("Ao voltar para 1 dedo após 3 dedos, deve entrar em PENDING_GESTURE", GestureInteractionState.PENDING_GESTURE, glView.arbiter.state)
        assertTrue(dispatchedMoves.isEmpty())

        // Com o último dedo sobrevivente (id=2), arrasta para a esquerda na camada superior -> executa U!
        sendEvent(tE, tE + 112, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(2, topMidPt[0] - 95f, topMidPt[1] - 18f))
        assertEquals(GestureInteractionState.LAYER_ROTATION, glView.arbiter.state)
        assertEquals(listOf(CubeMove.U), dispatchedMoves)

        // 1 dedo -> nenhum dedo
        sendEvent(tE, tE + 128, android.view.MotionEvent.ACTION_UP, 0, PointerSpec(2, topMidPt[0] - 95f, topMidPt[1] - 18f))
        assertEquals(0, glView.activePointerCount)
        assertEquals(android.view.MotionEvent.INVALID_POINTER_ID, glView.activePointerId)
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)

        // =========================================================================
        // TESTE F: Cancelamento (ACTION_CANCEL durante pinça, órbita de câmera e seleção de camada)
        // =========================================================================
        dispatchedMoves.clear()
        cubeState = CubeState.createSolved()
        val tF = 6000L

        // F1: ACTION_CANCEL durante uma pinça
        sendEvent(tF, tF, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 300f, 400f))
        sendEvent(tF, tF + 16, android.view.MotionEvent.ACTION_POINTER_DOWN, 1, PointerSpec(0, 300f, 400f), PointerSpec(1, 500f, 400f))
        assertEquals(GestureInteractionState.PINCH_ZOOM, glView.arbiter.state)
        sendEvent(tF, tF + 32, android.view.MotionEvent.ACTION_CANCEL, 0, PointerSpec(0, 300f, 400f), PointerSpec(1, 500f, 400f))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)
        assertEquals(0, glView.activePointerCount)
        assertEquals(android.view.MotionEvent.INVALID_POINTER_ID, glView.activePointerId)
        assertFalse(renderer.isUserTouching)
        assertTrue(dispatchedMoves.isEmpty())

        // F2: ACTION_CANCEL durante uma órbita de câmera
        touchMode = TouchInteractionMode.ROTATE_CAMERA
        sendEvent(tF + 100, tF + 100, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, 400f, 400f))
        sendEvent(tF + 100, tF + 116, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, 450f, 380f))
        assertEquals(GestureInteractionState.CAMERA_ORBIT, glView.arbiter.state)
        sendEvent(tF + 100, tF + 132, android.view.MotionEvent.ACTION_CANCEL, 0, PointerSpec(0, 450f, 380f))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)
        assertFalse(renderer.isUserTouching)
        assertTrue(dispatchedMoves.isEmpty())

        // F3: ACTION_CANCEL durante uma seleção/prévia de camada (antes de atingir layerSwipeSlopPx)
        renderer.resetCamera()
        touchMode = TouchInteractionMode.MOVE_LAYERS
        val centerFacePt = renderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
        sendEvent(tF + 200, tF + 200, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, centerFacePt[0], centerFacePt[1]))
        // Arrasta 55% do slop -> ativa previewTouchHit e previewTouchMove, mas ainda não dispara movimento
        val halfSlop = glView.arbiter.layerSwipeSlopPx * 0.55f
        sendEvent(tF + 200, tF + 216, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, centerFacePt[0], centerFacePt[1] + halfSlop))
        assertNotNull("Deve ter selecionado peça em preview antes do cancelamento", renderer.previewTouchHit)
        assertNotNull("Deve ter sugerido movimento em preview antes do cancelamento", renderer.previewTouchMove)
        assertTrue("Não deve ter disparado movimento antes do slop completo", dispatchedMoves.isEmpty())

        sendEvent(tF + 200, tF + 232, android.view.MotionEvent.ACTION_CANCEL, 0, PointerSpec(0, centerFacePt[0], centerFacePt[1] + halfSlop))
        assertEquals(GestureInteractionState.IDLE, glView.arbiter.state)
        assertNull("ACTION_CANCEL deve limpar previewTouchHit", renderer.previewTouchHit)
        assertNull("ACTION_CANCEL deve limpar previewTouchMove", renderer.previewTouchMove)
        assertNull("ACTION_CANCEL deve limpar initialHit", glView.arbiter.initialHit)
        assertFalse(renderer.isUserTouching)
        assertTrue("ACTION_CANCEL jamais pode executar o movimento que estava apenas sugerido", dispatchedMoves.isEmpty())

        // =========================================================================
        // TESTE G: Compatibilidade com as animações (giro já disparado + 2º dedo durante a animação)
        // Integrado diretamente ao CuboMestreViewModel no Modo Explorar com animação multi-quadro real
        // =========================================================================
        vm.forceMultiFrameAnimationInTest = true
        try {
            composeTestRule.runOnUiThread {
                vm.navigateTo(com.example.viewmodel.AppScreen.EXPLORE)
                vm.resetExploreCubeSolved()
            }
            composeTestRule.waitForIdle()

            renderer.resetCamera()
            val vmRenderer = CubeGLRenderer().apply {
                configureViewport(800, 800)
                resetCamera()
            }
            val vmDispatchedMoves = mutableListOf<CubeMove>()
            val vmGlView = com.example.renderer.CubeTouchGLSurfaceView(
                context = context,
                cubeRenderer = vmRenderer,
                allowFaceSwipeProvider = { true },
                isAnimatingProvider = { vm.exploreState.value.activeAnimation != null },
                touchModeProvider = { TouchInteractionMode.MOVE_LAYERS },
                onSwipeMove = { move ->
                    vmDispatchedMoves.add(move)
                    vm.executeExploreMove(move)
                }
            )

            fun sendVmEvent(
                downTime: Long,
                eventTime: Long,
                actionMasked: Int,
                actionIndex: Int = 0,
                vararg pointers: PointerSpec
            ) {
                val ev = buildMotionEvent(downTime, eventTime, actionMasked, actionIndex, pointers.toList())
                try {
                    vmGlView.onTouchEvent(ev)
                } finally {
                    ev.recycle()
                }
            }

            val rPt = vmRenderer.projectWorldToScreen(floatArrayOf(0.95f, 0f, 1.45f))!!
            val tG = 7000L
            // 1. Inicia arraste de camada com 1 dedo e ultrapassa o limiar -> dispara R e inicia animação 3D no ViewModel
            sendVmEvent(tG, tG, android.view.MotionEvent.ACTION_DOWN, 0, PointerSpec(0, rPt[0], rPt[1]))
            sendVmEvent(tG, tG + 16, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, rPt[0] + 10f, rPt[1] - 90f))
            assertEquals("Movimento R deve ter sido aceito e enviado ao ViewModel", listOf(CubeMove.R), vmDispatchedMoves)

            // Avança 48ms para que a rotação de R esteja no meio da animação física (~25%..50%)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(48, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertNotNull("Animação 3D de R deve estar em andamento quando o 2º dedo toca a tela", vm.exploreState.value.activeAnimation)

            // 2. Enquanto R está animando, o usuário encosta o 2º dedo e realiza uma pinça!
            val distBeforeAnimPinch = vmRenderer.cameraDistance
            sendVmEvent(
                tG, tG + 64, android.view.MotionEvent.ACTION_POINTER_DOWN, 1,
                PointerSpec(0, rPt[0] + 10f, rPt[1] - 90f), PointerSpec(1, rPt[0] - 200f, rPt[1] - 90f)
            )
            assertEquals(GestureInteractionState.PINCH_ZOOM, vmGlView.arbiter.state)
            sendVmEvent(
                tG, tG + 80, android.view.MotionEvent.ACTION_MOVE, 0,
                PointerSpec(0, rPt[0] + 70f, rPt[1] - 90f), PointerSpec(1, rPt[0] - 280f, rPt[1] - 90f)
            )
            assertTrue("Pinça durante animação deve ajustar cameraDistance", vmRenderer.cameraDistance < distBeforeAnimPinch)
            assertEquals("Pinça jamais pode duplicar o movimento nem disparar outro", 1, vmDispatchedMoves.size)
            assertNotNull("A animação previamente aceita deve continuar em andamento sem ser cancelada", vm.exploreState.value.activeAnimation)

            // 3. O usuário levanta o 2º dedo enquanto a animação ainda está terminando e move o dedo 1 residualmente
            sendVmEvent(
                tG, tG + 96, android.view.MotionEvent.ACTION_POINTER_UP, 1,
                PointerSpec(0, rPt[0] + 70f, rPt[1] - 90f), PointerSpec(1, rPt[0] - 280f, rPt[1] - 90f)
            )
            assertEquals("Com animação ativa ao sair da pinça, deve entrar em ANIMATING", GestureInteractionState.ANIMATING, vmGlView.arbiter.state)
            sendVmEvent(tG, tG + 112, android.view.MotionEvent.ACTION_MOVE, 0, PointerSpec(0, rPt[0] + 75f, rPt[1] - 120f))
            assertEquals("Movimento residual durante animação pós-pinça não pode disparar novo giro", 1, vmDispatchedMoves.size)

            // 4. A animação termina seus quadros até 100% e consolida R no CubeState
            var ticks = 0
            while (vm.exploreState.value.activeAnimation != null && ticks < 100) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                ticks++
            }
            sendVmEvent(tG, tG + 300, android.view.MotionEvent.ACTION_UP, 0, PointerSpec(0, rPt[0] + 75f, rPt[1] - 120f))

            assertNull(vm.exploreState.value.activeAnimation)
            assertEquals("Exatamente 1 movimento (R) deve constar no histórico", listOf(CubeMove.R), vm.exploreState.value.moveHistory)
            assertEquals("CubeState final deve corresponder exatamente a Solved.applyMove(R)", CubeState.createSolved().applyMove(CubeMove.R), vm.exploreState.value.cubeState)
        } finally {
            vm.forceMultiFrameAnimationInTest = false
        }

        // =========================================================================
        // TESTE H: Telas e densidades diferentes (mdpi 1.0x, xhdpi 2.0x, xxhdpi 3.0x, xxxhdpi 3.5x)
        // =========================================================================
        data class ScreenSpec(val width: Int, val height: Int, val density: Float)
        val screenConfigs = listOf(
            ScreenSpec(320, 560, 1.0f),   // mdpi compacto
            ScreenSpec(360, 780, 2.0f),   // xhdpi padrão
            ScreenSpec(412, 915, 3.0f),   // xxhdpi moderno
            ScreenSpec(800, 1280, 3.5f)   // xxxhdpi alta densidade / tablet
        )

        for (spec in screenConfigs) {
            val testRenderer = CubeGLRenderer().apply {
                configureViewport(spec.width, spec.height)
                resetCamera()
            }
            val movesForDensity = mutableListOf<CubeMove>()
            val testArbiter = CubeGestureArbiter(
                renderer = testRenderer,
                allowFaceSwipeProvider = { true },
                isAnimatingProvider = { false },
                touchModeProvider = { TouchInteractionMode.MOVE_LAYERS },
                onLayerMoveResolved = { movesForDensity.add(it) }
            ).apply {
                screenDensity = spec.density
            }

            assertEquals(8f * spec.density, testArbiter.orbitTouchSlopPx, 0.01f)
            assertEquals(18f * spec.density, testArbiter.layerSwipeSlopPx, 0.01f)
            assertEquals(6f * spec.density, testArbiter.pinchTouchSlopPx, 0.01f)

            // Simula pinça -> retirada de 1 dedo -> arrasto intencional de 24dp sobre a camada central M
            val centerScreen = testRenderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
            testArbiter.onDown(centerScreen[0] - 40f, centerScreen[1], pointerId = 0)
            testArbiter.onPointerDown(2, activeX = centerScreen[0] - 40f, activeY = centerScreen[1], primaryPointerId = 0)
            testArbiter.onPinchScale(1.10f)

            val updatedCenter = testRenderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))!!
            testArbiter.onTransitionToSinglePointer(updatedCenter[0], updatedCenter[1], remainingPointerId = 1)

            // Arrasta apenas 10dp (< 18dp de layerSwipeSlopPx) -> não deve disparar
            testArbiter.onMove(updatedCenter[0], updatedCenter[1] + 10f * spec.density)
            assertTrue("Em densidade ${spec.density}x, 10dp não pode disparar movimento", movesForDensity.isEmpty())

            // Arrasta 26dp (> 18dp de layerSwipeSlopPx) para baixo na coluna central -> deve disparar M
            testArbiter.onMove(updatedCenter[0], updatedCenter[1] + 26f * spec.density)
            assertEquals(
                "Em tela ${spec.width}x${spec.height} @ ${spec.density}x, arraste vertical central de 26dp pós-pinça deve resolver M",
                listOf(CubeMove.M),
                movesForDensity
            )
            testArbiter.onUpOrCancel()
        }
        ShadowTrace.reset()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testExploreStatusBadgeAndActiveMoveBannerZeroOverlapAcrossWidthsFontScalesAndThemes() {
        fun collectNodeTextLayouts(tag: String): List<androidx.compose.ui.text.TextLayoutResult> {
            val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            fun collectFrom(sNode: androidx.compose.ui.semantics.SemanticsNode) {
                if (sNode.config.contains(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)) {
                    sNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(textLayouts)
                }
                sNode.children.forEach { collectFrom(it) }
            }
            collectFrom(node)
            return textLayouts
        }

        fun assertNoVisualTextOverflow(tag: String, contextInfo: String): String {
            val textLayouts = collectNodeTextLayouts(tag)
            assertTrue("Nó '$tag' deve produzir TextLayoutResult ($contextInfo)", textLayouts.isNotEmpty())
            for (layout in textLayouts) {
                val renderedText = layout.layoutInput.text.text
                assertFalse(
                    "Texto de '$tag' ('$renderedText') NÃO pode sofrer corte vertical em $contextInfo (didOverflowHeight=${layout.didOverflowHeight})",
                    layout.didOverflowHeight
                )
                assertFalse(
                    "Texto de '$tag' ('$renderedText') NÃO pode exceder maxLines em $contextInfo",
                    layout.multiParagraph.didExceedMaxLines
                )
                for (line in 0 until layout.lineCount) {
                    assertFalse(
                        "Linha $line de '$tag' ('$renderedText') NÃO pode sofrer elipse/truncamento em $contextInfo",
                        layout.isLineEllipsized(line)
                    )
                }
                assertEquals(
                    "Todos os caracteres de '$tag' ('$renderedText') devem estar visíveis no layout em $contextInfo",
                    renderedText.length,
                    layout.getLineEnd(layout.lineCount - 1)
                )
            }
            return textLayouts.joinToString(" ") { it.layoutInput.text.text }
        }

        val widthQualifiers = listOf(
            "w320dp-h740dp-xhdpi",
            "w360dp-h780dp-xhdpi",
            "w393dp-h852dp-xhdpi"
        )
        val fontScales = listOf(1.0f, 1.3f, 1.5f)
        val themes = listOf(true, false) // true = escuro, false = claro
        val movesToVerify = listOf(
            CubeMove.R,       // horário
            CubeMove.F,       // horário
            CubeMove.R_PRIME, // anti-horário
            CubeMove.F_PRIME, // anti-horário
            CubeMove.R2,      // 180°
            CubeMove.F2       // 180°
        )

        try {
            for (qualifiers in widthQualifiers) {
                for (fontScale in fontScales) {
                    org.robolectric.RuntimeEnvironment.setQualifiers(qualifiers)
                    org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = fontScale
                    lateinit var vm: com.example.viewmodel.CuboMestreViewModel
                    composeTestRule.activityRule.scenario.onActivity { act ->
                        act.resources.configuration.fontScale = fontScale
                        act.recreate()
                    }
                    composeTestRule.waitForIdle()
                    composeTestRule.activityRule.scenario.onActivity { act ->
                        vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
                    }
                    vm.forceMultiFrameAnimationInTest = true

                    for (isDark in themes) {
                        composeTestRule.runOnUiThread {
                            if (vm.progressState.value.isDarkTheme != isDark) {
                                vm.toggleDarkTheme()
                            }
                            vm.openExploreScreen()
                        }
                        composeTestRule.waitForIdle()

                        for (isSolvedState in listOf(true, false)) {
                            composeTestRule.runOnUiThread {
                                vm.forceMultiFrameAnimationInTest = false
                                vm.resetExploreCubeSolved()
                                if (isSolvedState) {
                                    // Simula 8 movimentos que preservam o cubo resolvido ("Cubo Resolvido • 8 mov.")
                                    repeat(4) {
                                        vm.executeExploreMove(CubeMove.R)
                                        vm.executeExploreMove(CubeMove.R_PRIME)
                                    }
                                } else {
                                    vm.scrambleExploreCube()
                                }
                            }
                            composeTestRule.waitForIdle()

                            for (move in movesToVerify) {
                                composeTestRule.runOnUiThread {
                                    vm.forceMultiFrameAnimationInTest = true
                                    vm.executeExploreMove(move)
                                }
                                composeTestRule.waitForIdle()

                                val ctx = "$qualifiers | font=${fontScale}x | dark=$isDark | solved=$isSolvedState | move=${move.notation}"

                                val statusNode = composeTestRule.onNodeWithTag("explore_status_badge").assertIsDisplayed().fetchSemanticsNode()
                                val bannerNode = composeTestRule.onNodeWithTag("active_move_banner").assertIsDisplayed().fetchSemanticsNode()
                                val viewportNode = composeTestRule.onNodeWithTag("cube_3d_viewport").assertIsDisplayed().fetchSemanticsNode()

                                val statusBounds = statusNode.boundsInRoot
                                val bannerBounds = bannerNode.boundsInRoot
                                val viewportBounds = viewportNode.boundsInRoot

                                // 1. Zero interseção geométrica entre explore_status_badge e active_move_banner
                                assertFalse(
                                    "Não pode haver interseção entre explore_status_badge ($statusBounds) e active_move_banner ($bannerBounds) em $ctx",
                                    statusBounds.overlaps(bannerBounds)
                                )
                                assertTrue(
                                    "explore_status_badge deve ficar inteiramente acima do viewport 3D e do active_move_banner em $ctx (statusBottom=${statusBounds.bottom}, viewportTop=${viewportBounds.top}, bannerTop=${bannerBounds.top})",
                                    statusBounds.bottom <= viewportBounds.top + 1f && statusBounds.bottom <= bannerBounds.top + 1f
                                )
                                assertTrue(
                                    "active_move_banner deve estar contido dentro dos limites horizontais do viewport 3D em $ctx (banner=$bannerBounds, viewport=$viewportBounds)",
                                    bannerBounds.left >= viewportBounds.left - 1f && bannerBounds.right <= viewportBounds.right + 1f
                                )

                                // 2. Textos totalmente legíveis, sem cortes ou overflow visual
                                val statusText = assertNoVisualTextOverflow("explore_status_badge", ctx)
                                val bannerText = assertNoVisualTextOverflow("active_move_banner", ctx)

                                val expectedStatusPrefix = if (isSolvedState) "Cubo Resolvido • 8 mov." else "Em exploração •"
                                assertTrue(
                                    "Texto de explore_status_badge ('$statusText') deve conter '$expectedStatusPrefix' em $ctx",
                                    statusText.contains(expectedStatusPrefix)
                                )
                                assertTrue(
                                    "Texto de active_move_banner ('$bannerText') deve conter notação '${move.notation}' e direção '${move.titlePt}' em $ctx",
                                    bannerText.contains(move.notation) && bannerText.contains(move.titlePt)
                                )

                                // Limpa animação em curso sem alterar o estado base (resolvido 8 mov. / em exploração)
                                composeTestRule.runOnUiThread {
                                    vm.onAppBackgrounded()
                                    vm.onAppForegrounded()
                                    vm.forceMultiFrameAnimationInTest = false
                                }
                                composeTestRule.waitForIdle()
                            }
                        }
                    }
                    vm.forceMultiFrameAnimationInTest = false
                    ShadowTrace.reset()
                }
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h780dp-xhdpi")
            org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = 1.0f
            ShadowTrace.reset()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testLightAndDarkThemeContrastMiniaturesAndThemeSwitchingRegressionSuite() {
        fun collectNodeTextLayouts(tag: String): List<androidx.compose.ui.text.TextLayoutResult> {
            val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            fun collectFrom(sNode: androidx.compose.ui.semantics.SemanticsNode) {
                if (sNode.config.contains(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)) {
                    sNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(textLayouts)
                }
                sNode.children.forEach { collectFrom(it) }
            }
            collectFrom(node)
            return textLayouts
        }

        fun assertNoVisualTextOverflow(tag: String, contextDesc: String): String {
            val layouts = collectNodeTextLayouts(tag)
            assertTrue("Deve haver pelo menos um TextLayoutResult em '$tag' ($contextDesc)", layouts.isNotEmpty())
            val combinedText = layouts.joinToString(" | ") { it.layoutInput.text.text }
            for (layout in layouts) {
                val renderedText = layout.layoutInput.text.text
                assertFalse(
                    "Proibido corte/overflow visual em '$tag' ('$renderedText') em [$contextDesc]",
                    layout.hasVisualOverflow
                )
            }
            return combinedText
        }

        // =========================================================================
        // 1. AUDITORIA MATEMÁTICA DE CONTRASTE WCAG 2.1 (TEMA CLARO E TEMA ESCURO)
        // =========================================================================
        val lightSurface = com.example.ui.theme.LightSurface
        val lightSurfaceVariant = com.example.ui.theme.LightCard
        val lightBackground = com.example.ui.theme.LightBackground
        val lightTokens: com.example.ui.theme.CuboMestreColors = com.example.ui.theme.LightCuboMestreColors

        val darkSurface = com.example.ui.theme.NavySurface
        val darkSurfaceVariant = com.example.ui.theme.GraphiteCard
        val darkBackground = com.example.ui.theme.NavyBackground
        val darkTokens: com.example.ui.theme.CuboMestreColors = com.example.ui.theme.DarkCuboMestreColors

        val themeSuites: List<Triple<String, Triple<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>, com.example.ui.theme.CuboMestreColors>> = listOf(
            Triple("Tema Claro", Triple(lightSurface, lightSurfaceVariant, lightBackground), lightTokens),
            Triple("Tema Escuro", Triple(darkSurface, darkSurfaceVariant, darkBackground), darkTokens)
        )

        for ((themeName, bgTriple, tokens) in themeSuites) {
            val (surface, surfaceVariant, background) = bgTriple
            val textPrimary = if (tokens.isDark) com.example.ui.theme.TextPrimaryDark else com.example.ui.theme.TextPrimaryLight
            val textSecondary = if (tokens.isDark) com.example.ui.theme.TextSecondaryDark else com.example.ui.theme.TextSecondaryLight

            val bgList: List<Pair<String, androidx.compose.ui.graphics.Color>> = listOf(
                "surface" to surface,
                "surfaceVariant" to surfaceVariant,
                "background" to background
            )
            for ((bgName, bg) in bgList) {
                val cPrimary = com.example.ui.theme.calculateEffectiveWcagContrast(textPrimary, bg)
                val cSecondary = com.example.ui.theme.calculateEffectiveWcagContrast(textSecondary, bg)
                val cTurquoise = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.turquoiseAccent, bg)
                val cViolet = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.violetText, bg)
                val cSuccess = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.successText, bg)
                val cWarning = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.warningText, bg)

                assertTrue("$themeName: textPrimary sobre $bgName ($cPrimary:1) deve ser >= 4.5:1", cPrimary >= 4.5f)
                assertTrue("$themeName: textSecondary sobre $bgName ($cSecondary:1) deve ser >= 4.5:1", cSecondary >= 4.5f)
                assertTrue("$themeName: turquoiseAccent sobre $bgName ($cTurquoise:1) deve ser >= 4.5:1", cTurquoise >= 4.5f)
                assertTrue("$themeName: violetText sobre $bgName ($cViolet:1) deve ser >= 4.5:1", cViolet >= 4.5f)
                assertTrue("$themeName: successText sobre $bgName ($cSuccess:1) deve ser >= 4.5:1", cSuccess >= 4.5f)
                assertTrue("$themeName: warningText sobre $bgName ($cWarning:1) deve ser >= 4.5:1", cWarning >= 4.5f)
            }

            // Chips e cartões translúcidos (sobre surface e surfaceVariant)
            val cardBgList: List<Pair<String, androidx.compose.ui.graphics.Color>> = listOf(
                "surface" to surface,
                "surfaceVariant" to surfaceVariant
            )
            for ((bgName, bg) in cardBgList) {
                val turquoiseChipBg = androidx.compose.ui.graphics.lerp(bg, tokens.turquoiseAccent, 0.22f)
                val violetChipBg = androidx.compose.ui.graphics.lerp(bg, tokens.violetAccent, 0.16f)
                val successChipBg = androidx.compose.ui.graphics.lerp(bg, tokens.successText, 0.15f)
                val warningChipBg = androidx.compose.ui.graphics.lerp(bg, tokens.warningText, 0.15f)

                val cTurquoiseChip = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.turquoiseAccent, turquoiseChipBg)
                val cVioletChip = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.violetText, violetChipBg)
                val cSuccessChip = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.successText, successChipBg)
                val cWarningChip = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.warningText, warningChipBg)

                assertTrue("$themeName: turquoiseAccent em chip sobre $bgName ($cTurquoiseChip:1) deve ser >= 4.5:1", cTurquoiseChip >= 4.5f)
                assertTrue("$themeName: violetText em chip ('▶ 2 Fases' / 'A seguir') sobre $bgName ($cVioletChip:1) deve ser >= 4.5:1", cVioletChip >= 4.5f)
                assertTrue("$themeName: successText em chip ('Concluída') sobre $bgName ($cSuccessChip:1) deve ser >= 4.5:1", cSuccessChip >= 4.5f)
                assertTrue("$themeName: warningText em chip ('Em andamento') sobre $bgName ($cWarningChip:1) deve ser >= 4.5:1", cWarningChip >= 4.5f)
            }

            // Botões preenchidos e emblemas (contraste interno >= 4.5:1 e contraste do componente >= 3.0:1)
            val cTurquoiseBtnText = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.onTurquoiseAccent, tokens.turquoiseAccent)
            val cVioletBtnText = com.example.ui.theme.calculateEffectiveWcagContrast(androidx.compose.ui.graphics.Color.White, tokens.violetAccent)
            val cSuccessBadgeText = com.example.ui.theme.calculateEffectiveWcagContrast(androidx.compose.ui.graphics.Color.White, tokens.successBadgeBg)

            assertTrue("$themeName: texto sobre botão turquesa ($cTurquoiseBtnText:1) deve ser >= 4.5:1", cTurquoiseBtnText >= 4.5f)
            assertTrue("$themeName: texto branco sobre botão violeta ($cVioletBtnText:1) deve ser >= 4.5:1", cVioletBtnText >= 4.5f)
            assertTrue("$themeName: texto branco sobre emblema verde ($cSuccessBadgeText:1) deve ser >= 4.5:1", cSuccessBadgeText >= 4.5f)

            val cTurquoiseBtnVsBg = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.turquoiseAccent, surface)
            val cVioletBtnVsBg = com.example.ui.theme.calculateEffectiveWcagContrast(tokens.violetAccent, surface)
            assertTrue("$themeName: botão turquesa vs surface ($cTurquoiseBtnVsBg:1) deve ser >= 3.0:1", cTurquoiseBtnVsBg >= 3.0f)
            assertTrue("$themeName: botão violeta vs surface ($cVioletBtnVsBg:1) deve ser >= 3.0:1", cVioletBtnVsBg >= 3.0f)

            // Miniaturas 3x3 (Etapas 1 e 2 com peças brancas StickerWhite sobre miniCubeSurface)
            val cWhiteStickerVsMiniBase = com.example.ui.theme.calculateEffectiveWcagContrast(
                com.example.ui.theme.StickerWhite,
                tokens.miniCubeSurface
            )
            val cWhiteStickerVsDimmedCell = com.example.ui.theme.calculateEffectiveWcagContrast(
                com.example.ui.theme.StickerWhite,
                tokens.miniCubeDimmedCell
            )
            assertTrue(
                "$themeName: peças brancas das miniaturas (Etapas 1 e 2) vs base da miniatura ($cWhiteStickerVsMiniBase:1) deve ser >= 3.0:1 (esperado > 10:1)",
                cWhiteStickerVsMiniBase >= 10.0f
            )
            assertTrue(
                "$themeName: peças brancas das miniaturas vs células neutras ($cWhiteStickerVsDimmedCell:1) deve ser >= 3.0:1",
                cWhiteStickerVsDimmedCell >= 3.0f
            )
        }

        // =========================================================================
        // 2. VALIDAÇÃO VISUAL E DE TRANSIÇÃO CLARO <-> ESCURO EM TODAS AS TELAS
        // =========================================================================
        val testConfigs = listOf(
            "w320dp-h720dp-xhdpi" to 1.0f,
            "w360dp-h780dp-xhdpi" to 1.0f,
            "w393dp-h852dp-xhdpi" to 1.0f,
            "w360dp-h780dp-xhdpi" to 1.3f,
            "w360dp-h780dp-xhdpi" to 1.5f
        )

        try {
            for ((qualifiers, fontScale) in testConfigs) {
                org.robolectric.RuntimeEnvironment.setQualifiers(qualifiers)
                org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = fontScale
                lateinit var vm: com.example.viewmodel.CuboMestreViewModel
                composeTestRule.activityRule.scenario.onActivity { act ->
                    act.resources.configuration.fontScale = fontScale
                    act.recreate()
                }
                composeTestRule.waitForIdle()
                composeTestRule.activityRule.scenario.onActivity { act ->
                    vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
                }

                for (targetDark in listOf(false, true)) {
                    val ctx = "$qualifiers | font=${fontScale}x | dark=$targetDark"

                    composeTestRule.runOnUiThread {
                        if (vm.progressState.value.isDarkTheme != targetDark) {
                            vm.toggleDarkTheme()
                        }
                        vm.navigateTo(com.example.viewmodel.AppScreen.HOME)
                    }
                    composeTestRule.waitForIdle()
                    assertEquals("Estado do tema deve ser $targetDark em $ctx", targetDark, vm.progressState.value.isDarkTheme)

                    // A. Home Screen
                    composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("theme_toggle_button").assertIsDisplayed()
                    val startBtn = composeTestRule.onNodeWithTag("start_learning_button").assertIsDisplayed()
                    val exploreBtn = composeTestRule.onNodeWithTag("explore_cube_button").assertIsDisplayed()
                    val notationBtn = composeTestRule.onNodeWithTag("notation_guide_button").assertIsDisplayed()

                    val startText = startBtn.fetchSemanticsNode().config
                        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                        .joinToString(" ") { it.text }
                    val exploreText = exploreBtn.fetchSemanticsNode().config
                        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                        .joinToString(" ") { it.text }
                    val notationText = notationBtn.fetchSemanticsNode().config
                        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                        .joinToString(" ") { it.text }
                    assertEquals("Home start button text in $ctx", "Começar a aprender", startText)
                    assertEquals("Home explore button text in $ctx", "Explorar Cubo", exploreText)
                    assertEquals("Home notation button text in $ctx", "Guia de Notação", notationText)

                    // B. Course Screen (Trilha das 7 Etapas + Miniaturas Etapas 1 e 2 + Estados Concluída / Em andamento / Não iniciada)
                    composeTestRule.runOnUiThread {
                        vm.openLesson(1, 0, com.example.viewmodel.LearningMode.LEARN)
                        vm.markCurrentStepCompletedManually()
                        vm.openCourseScreen()
                    }
                    composeTestRule.waitForIdle()

                    composeTestRule.onNodeWithTag("step_card_1").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("step_card_2").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("step_mini_cube_1", useUnmergedTree = true).assertIsDisplayed()
                    composeTestRule.onNodeWithTag("step_mini_cube_2", useUnmergedTree = true).assertIsDisplayed()

                    val mini1Bounds = composeTestRule.onNodeWithTag("step_mini_cube_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val mini2Bounds = composeTestRule.onNodeWithTag("step_mini_cube_2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue("Miniatura da Etapa 1 deve ter dimensões válidas em $ctx", mini1Bounds.width > 20f && mini1Bounds.height > 20f)
                    assertTrue("Miniatura da Etapa 2 deve ter dimensões válidas em $ctx", mini2Bounds.width > 20f && mini2Bounds.height > 20f)

                    // C. Lesson Screen — Etapa 4 Caso 2 (com painel de 2 fases "▶ 2 Fases", barra de velocidade e seletor de modos)
                    composeTestRule.runOnUiThread {
                        vm.openLesson(4, 1, com.example.viewmodel.LearningMode.LEARN)
                    }
                    composeTestRule.waitForIdle()

                    composeTestRule.onNodeWithTag("play_full_phases_button").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
                    assertNoVisualTextOverflow("lesson_step_title", "Lesson StepTitle $ctx")
                    assertNoVisualTextOverflow("case_selector_header", "Lesson CaseHeader $ctx")
                    assertNoVisualTextOverflow("case_full_title_text", "Lesson CaseFullTitle $ctx")
                    assertNoVisualTextOverflow("case_short_description", "Lesson CaseShortDesc $ctx")
                    assertNoVisualTextOverflow("current_move_status_text", "Lesson MoveStatus $ctx")
                    assertNoVisualTextOverflow("collapsed_goal_text", "Lesson CollapsedGoal $ctx")

                    // Alterna entre Claro e Escuro ao vivo dentro da lição e verifica que progresso e estado do cubo são preservados
                    val cubeBeforeToggle = vm.lessonState.value.cubeState
                    val completedBeforeToggle = vm.progressState.value.completedSteps
                    composeTestRule.runOnUiThread {
                        vm.toggleDarkTheme()
                    }
                    composeTestRule.waitForIdle()
                    assertEquals("Alternar tema não deve alterar o estado matemático do cubo", cubeBeforeToggle, vm.lessonState.value.cubeState)
                    assertEquals("Alternar tema não deve alterar etapas concluídas", completedBeforeToggle, vm.progressState.value.completedSteps)
                    composeTestRule.runOnUiThread {
                        vm.toggleDarkTheme() // Restaura targetDark
                    }
                    composeTestRule.waitForIdle()

                    // Modo Praticar
                    composeTestRule.runOnUiThread {
                        vm.selectLearningMode(com.example.viewmodel.LearningMode.PRACTICE)
                    }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("practice_interactive_card").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("toggle_practice_moves_button").assertIsDisplayed()

                    // D. Guia de Notação
                    composeTestRule.runOnUiThread {
                        vm.setNotationGuideVisible(true)
                    }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("notation_guide_modal").assertIsDisplayed()
                    composeTestRule.onNodeWithTag("notation_selected_move_card").assertIsDisplayed()
                    composeTestRule.runOnUiThread {
                        vm.setNotationGuideVisible(false)
                    }
                    composeTestRule.waitForIdle()
                }
                ShadowTrace.reset()
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h780dp-xhdpi")
            org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = 1.0f
            ShadowTrace.reset()
        }
    }

    /**
     * TESTE 22 — AUDITORIA ROBOLECTRIC DA ETAPA 6 ("POSICIONAR OS CANTOS FINAIS"):
     * Valida na interface real e no CuboMestreViewModel:
     * 1. Miniatura da Etapa 6 (step_mini_cube_6) visível na tela de Curso.
     * 2. Caso 1 (s6_c1): badge ao vivo "stage6_corner_pair_badge" exibindo "Par Laranja em Trás",
     *    avanço/retrocesso passo a passo (stepDemoForward / stepDemoBackward) pelos 9 movimentos,
     *    alcance do estado final com os 4 cantos posicionados e resetLessonCase().
     * 3. Caso 2 (s6_c2): painel de 4 fases pedagógicas (play_phase_1_button..play_phase_4_button e
     *    play_full_phases_button), transição entre os marcos (Nenhum par -> Par Verde na Esquerda após
     *    movimento 9 -> Par Verde em Trás após movimento 10 U -> 4 pares após movimento 19 ->
     *    4 cantos alinhados aos centros após movimento 20 U') e zero overflow textual.
     */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testStage6CornersUiPhasesStepForwardBackwardAndReset() {
        lateinit var vm: com.example.viewmodel.CuboMestreViewModel
        composeTestRule.waitForIdle()
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
        }

        // 1. Verifica miniatura da Etapa 6 na tela de Curso
        composeTestRule.runOnUiThread {
            vm.openCourseScreen()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("course_steps_list").performScrollToNode(hasTestTag("step_card_6"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("step_mini_cube_6", useUnmergedTree = true).assertIsDisplayed()

        // 2. Abre Etapa 6 — Caso 1 (s6_c1) no Modo Aprender
        composeTestRule.runOnUiThread {
            vm.openLesson(6, 0, com.example.viewmodel.LearningMode.LEARN)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("stage6_corner_pair_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stage6_corner_pair_text", useUnmergedTree = true)
            .assertTextContains("Par Laranja em Trás", substring = true)

        // Avança os 9 movimentos de s6_c1 via stepDemoForward()
        val s6c1InitialCube = vm.lessonState.value.cubeState
        assertEquals(listOf(CubeFace.BACK), s6c1InitialCube.findSidesWithCornerPairs())
        assertEquals(CubeColor.ORANGE, s6c1InitialCube.cornerPairColorOnSide(CubeFace.BACK))

        repeat(9) {
            composeTestRule.runOnUiThread {
                vm.stepDemoForward()
            }
            composeTestRule.waitForIdle()
        }
        assertEquals(8, vm.lessonState.value.currentMoveIndex)
        assertTrue(vm.lessonState.value.isDemoCompleted)
        assertTrue(vm.lessonState.value.cubeState.areTopCornersPermuted())
        composeTestRule.onNodeWithTag("stage6_corner_pair_text", useUnmergedTree = true)
            .assertTextContains("4 pares alinhados aos centros", substring = true)

        // Retrocede todos os 9 movimentos via stepDemoBackward() e verifica restauração exata
        repeat(9) {
            composeTestRule.runOnUiThread {
                vm.stepDemoBackward()
            }
            composeTestRule.waitForIdle()
        }
        assertEquals(-1, vm.lessonState.value.currentMoveIndex)
        assertEquals(s6c1InitialCube, vm.lessonState.value.cubeState)

        // Avança 4 movimentos e executa resetLessonCase()
        repeat(4) {
            composeTestRule.runOnUiThread {
                vm.stepDemoForward()
            }
            composeTestRule.waitForIdle()
        }
        composeTestRule.runOnUiThread {
            vm.resetLessonCase()
        }
        composeTestRule.waitForIdle()
        assertEquals(-1, vm.lessonState.value.currentMoveIndex)
        assertEquals(s6c1InitialCube, vm.lessonState.value.cubeState)

        // 3. Seleciona Caso 2 (s6_c2) e verifica painel de 4 fases e marcos matemáticos
        composeTestRule.runOnUiThread {
            vm.selectCase(1)
        }
        composeTestRule.waitForIdle()

        val s6c2InitialCube = vm.lessonState.value.cubeState
        assertTrue(s6c2InitialCube.findSidesWithCornerPairs().isEmpty())
        composeTestRule.onNodeWithTag("pedagogical_phases_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_phase_1_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_phase_2_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_phase_3_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_phase_4_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_full_phases_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stage6_corner_pair_text", useUnmergedTree = true)
            .assertTextContains("Nenhum par nas laterais", substring = true)

        // Fase 1: 9 movimentos -> forma Par Verde na Esquerda (LEFT)
        repeat(9) {
            composeTestRule.runOnUiThread {
                vm.stepDemoForward()
            }
            composeTestRule.waitForIdle()
        }
        assertEquals(8, vm.lessonState.value.currentMoveIndex)
        assertEquals(listOf(CubeFace.LEFT), vm.lessonState.value.cubeState.findSidesWithCornerPairs())
        assertEquals(CubeColor.GREEN, vm.lessonState.value.cubeState.cornerPairColorOnSide(CubeFace.LEFT))
        composeTestRule.onNodeWithTag("stage6_corner_pair_text", useUnmergedTree = true)
            .assertTextContains("Par Verde na Esquerda", substring = true)
        composeTestRule.onNodeWithTag("phase_transition_message_banner").assertIsDisplayed()

        // Fase 2: 10º movimento (U) -> leva Par Verde para Trás (BACK)
        composeTestRule.runOnUiThread {
            vm.stepDemoForward()
        }
        composeTestRule.waitForIdle()
        assertEquals(9, vm.lessonState.value.currentMoveIndex)
        assertEquals(listOf(CubeFace.BACK), vm.lessonState.value.cubeState.findSidesWithCornerPairs())
        assertEquals(CubeColor.GREEN, vm.lessonState.value.cubeState.cornerPairColorOnSide(CubeFace.BACK))
        composeTestRule.onNodeWithTag("stage6_corner_pair_text", useUnmergedTree = true)
            .assertTextContains("Par Verde em Trás", substring = true)

        // Fase 3: movimentos 11..19 -> forma pares nas 4 faces laterais (aguardando U')
        repeat(9) {
            composeTestRule.runOnUiThread {
                vm.stepDemoForward()
            }
            composeTestRule.waitForIdle()
        }
        assertEquals(18, vm.lessonState.value.currentMoveIndex)
        assertEquals(4, vm.lessonState.value.cubeState.findSidesWithCornerPairs().size)
        assertFalse(vm.lessonState.value.cubeState.areTopCornersPermuted())
        composeTestRule.onNodeWithTag("stage6_corner_pair_text", useUnmergedTree = true)
            .assertTextContains("gire U'", substring = true)

        // Fase 4: 20º movimento (U') -> 4 pares alinhados aos centros laterais
        composeTestRule.runOnUiThread {
            vm.stepDemoForward()
        }
        composeTestRule.waitForIdle()
        assertEquals(19, vm.lessonState.value.currentMoveIndex)
        assertTrue(vm.lessonState.value.isDemoCompleted)
        assertTrue(vm.lessonState.value.cubeState.areTopCornersPermuted())
        composeTestRule.onNodeWithTag("stage6_corner_pair_text", useUnmergedTree = true)
            .assertTextContains("4 pares alinhados aos centros", substring = true)

        // Verifica retrocesso completo dos 20 movimentos de s6_c2 e resetLessonCase()
        repeat(20) {
            composeTestRule.runOnUiThread {
                vm.stepDemoBackward()
            }
            composeTestRule.waitForIdle()
        }
        assertEquals(-1, vm.lessonState.value.currentMoveIndex)
        assertEquals(s6c2InitialCube, vm.lessonState.value.cubeState)

        // 4. Verifica o botão "Entender o caso" (open_case_detail_button) nos Casos 1 e 2 da Etapa 6:
        //    - Explicação dos 6 pontos pedagógicos (stage6_headlights_rule_1..6)
        //    - Ilustração visual da comparação dos 2 adesivos laterais superiores (stage6_headlights_face_diagram)
        //    - Exemplo prático do Caso 1 (par laranja em trás com centro verde) e do Caso 2 (sem par inicial)
        for (targetCaseIdx in listOf(0, 1)) {
            composeTestRule.runOnUiThread {
                vm.selectCase(targetCaseIdx)
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("open_case_detail_button").assertIsDisplayed().performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("case_detail_modal").assertIsDisplayed()
            composeTestRule.onNodeWithTag("case_detail_full_description").assertIsDisplayed()
            composeTestRule.onNodeWithTag("stage6_headlights_visual_guide_card").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("stage6_headlights_face_diagram").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("stage6_diagram_left_corner_sticker", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("stage6_diagram_right_corner_sticker", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("stage6_diagram_top_edge_sticker", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("stage6_diagram_center_sticker", useUnmergedTree = true).assertIsDisplayed()

            for (ruleNum in 1..6) {
                composeTestRule.onNodeWithTag("stage6_headlights_rule_$ruleNum").performScrollTo().assertIsDisplayed()
            }
            composeTestRule.onNodeWithTag("stage6_headlights_example_box").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("return_to_cube_from_detail_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(0, composeTestRule.onAllNodes(hasTestTag("case_detail_modal")).fetchSemanticsNodes().size)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testHomeHeroViewportTopIndicatorsZeroOverlapAcrossWidthsFontScalesThemesAndMoves() {
        fun collectNodeTextLayouts(tag: String): List<androidx.compose.ui.text.TextLayoutResult> {
            val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            fun collectFrom(sNode: androidx.compose.ui.semantics.SemanticsNode) {
                if (sNode.config.contains(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)) {
                    sNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(textLayouts)
                }
                sNode.children.forEach { collectFrom(it) }
            }
            collectFrom(node)
            return textLayouts
        }

        fun assertNoVisualTextOverflow(tag: String, contextInfo: String): String {
            val textLayouts = collectNodeTextLayouts(tag)
            assertTrue("Nó '$tag' deve produzir TextLayoutResult ($contextInfo)", textLayouts.isNotEmpty())
            for (layout in textLayouts) {
                val renderedText = layout.layoutInput.text.text
                assertFalse(
                    "Texto de '$tag' ('$renderedText') NÃO pode sofrer corte vertical em $contextInfo (didOverflowHeight=${layout.didOverflowHeight})",
                    layout.didOverflowHeight
                )
                assertFalse(
                    "Texto de '$tag' ('$renderedText') NÃO pode exceder maxLines em $contextInfo",
                    layout.multiParagraph.didExceedMaxLines
                )
                for (line in 0 until layout.lineCount) {
                    assertFalse(
                        "Linha $line de '$tag' ('$renderedText') NÃO pode sofrer elipse/truncamento em $contextInfo",
                        layout.isLineEllipsized(line)
                    )
                }
                assertEquals(
                    "Todos os caracteres de '$tag' ('$renderedText') devem estar visíveis no layout em $contextInfo",
                    renderedText.length,
                    layout.getLineEnd(layout.lineCount - 1)
                )
            }
            return textLayouts.joinToString(" ") { it.layoutInput.text.text }
        }

        // Combinações de larguras compactas (320dp, 360dp), larguras maiores (411dp, 720dp paisagem) e escalas de fonte (1.0x, 1.3x, 1.5x)
        val screenAndFontConfigs = listOf(
            "w320dp-h740dp-xhdpi" to 1.0f,
            "w320dp-h740dp-xhdpi" to 1.3f,
            "w320dp-h740dp-xhdpi" to 1.5f,
            "w360dp-h780dp-xhdpi" to 1.0f,
            "w360dp-h780dp-xhdpi" to 1.3f,
            "w360dp-h780dp-xhdpi" to 1.5f,
            "w411dp-h891dp-xhdpi" to 1.0f,
            "w720dp-h420dp-land-xhdpi" to 1.0f
        )
        val themes = listOf(true, false) // true = escuro, false = claro
        val movesToVerify = listOf(
            CubeMove.R,       // descrição curta: "Direita Subindo"
            CubeMove.B_PRIME, // descrição longa: "Trás Anti-horário"
            CubeMove.S_PRIME  // descrição muito longa: "Meio Lateral Anti-horário"
        )

        try {
            for ((qualifiers, fontScale) in screenAndFontConfigs) {
                org.robolectric.RuntimeEnvironment.setQualifiers(qualifiers)
                org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = fontScale
                lateinit var vm: com.example.viewmodel.CuboMestreViewModel
                composeTestRule.activityRule.scenario.onActivity { act ->
                    act.resources.configuration.fontScale = fontScale
                    act.recreate()
                }
                composeTestRule.waitForIdle()
                composeTestRule.activityRule.scenario.onActivity { act ->
                    vm = androidx.lifecycle.ViewModelProvider(act)[com.example.viewmodel.CuboMestreViewModel::class.java]
                }

                for (isDark in themes) {
                    composeTestRule.runOnUiThread {
                        if (vm.progressState.value.isDarkTheme != isDark) {
                            vm.toggleDarkTheme()
                        }
                        vm.navigateTo(com.example.viewmodel.AppScreen.HOME)
                        vm.forceMultiFrameAnimationInTest = false
                        vm.resetHomePreviewCube()
                    }
                    composeTestRule.waitForIdle()

                    val baseCtx = "$qualifiers | font=${fontScale}x | dark=$isDark"

                    // A. Estado inicial sem movimento ativo
                    val badgeIdleNode = composeTestRule.onNodeWithTag("home_interactive_cube_badge").assertIsDisplayed().fetchSemanticsNode()
                    val restoreIdleNode = composeTestRule.onNodeWithTag("home_restore_cube_button").assertIsDisplayed().fetchSemanticsNode()
                    val viewportIdleNode = composeTestRule.onNodeWithTag("cube_3d_viewport").assertIsDisplayed().fetchSemanticsNode()
                    val touchModeIdleNode = composeTestRule.onNodeWithTag("touch_mode_selector").assertIsDisplayed().fetchSemanticsNode()
                    val recenterIdleNode = composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed().fetchSemanticsNode()

                    assertEquals(
                        "Sem movimento ativo, active_move_banner não deve estar visível em $baseCtx",
                        0,
                        composeTestRule.onAllNodes(hasTestTag("active_move_banner")).fetchSemanticsNodes().size
                    )

                    val badgeIdleBounds = badgeIdleNode.boundsInRoot
                    val restoreIdleBounds = restoreIdleNode.boundsInRoot
                    val viewportIdleBounds = viewportIdleNode.boundsInRoot

                    assertFalse(
                        "Selo 'Cubo 3×3 Interativo' ($badgeIdleBounds) e botão 'Restaurar' ($restoreIdleBounds) jamais podem se sobrepor em $baseCtx",
                        badgeIdleBounds.overlaps(restoreIdleBounds)
                    )
                    assertTrue(
                        "Selo 'Cubo 3×3 Interativo' deve ficar à esquerda do botão 'Restaurar' em $baseCtx (badgeRight=${badgeIdleBounds.right}, restoreLeft=${restoreIdleBounds.left})",
                        badgeIdleBounds.right < restoreIdleBounds.left
                    )

                    val badgeText = assertNoVisualTextOverflow("home_interactive_cube_badge", baseCtx)
                    val restoreText = assertNoVisualTextOverflow("home_restore_cube_button", baseCtx)
                    assertEquals("Cubo 3×3 Interativo", badgeText)
                    assertEquals("Restaurar", restoreText)

                    // B, C, D, K. Execução de movimentos curtos e longos com presença simultânea dos 3 elementos superiores e controles inferiores
                    for (move in movesToVerify) {
                        composeTestRule.runOnUiThread {
                            vm.forceMultiFrameAnimationInTest = true
                            vm.executeHomePreviewMove(move)
                        }
                        composeTestRule.waitForIdle()

                        val ctx = "$baseCtx | move=${move.notation} (${move.titlePt})"

                        val badgeNode = composeTestRule.onNodeWithTag("home_interactive_cube_badge").assertIsDisplayed().fetchSemanticsNode()
                        val restoreNode = composeTestRule.onNodeWithTag("home_restore_cube_button").assertIsDisplayed().fetchSemanticsNode()
                        val bannerNode = composeTestRule.onNodeWithTag("active_move_banner").assertIsDisplayed().fetchSemanticsNode()
                        val viewportNode = composeTestRule.onNodeWithTag("cube_3d_viewport").assertIsDisplayed().fetchSemanticsNode()
                        val touchModeNode = composeTestRule.onNodeWithTag("touch_mode_selector").assertIsDisplayed().fetchSemanticsNode()
                        val recenterNode = composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed().fetchSemanticsNode()

                        val badgeBounds = badgeNode.boundsInRoot
                        val restoreBounds = restoreNode.boundsInRoot
                        val bannerBounds = bannerNode.boundsInRoot
                        val viewportBounds = viewportNode.boundsInRoot
                        val touchModeBounds = touchModeNode.boundsInRoot
                        val recenterBounds = recenterNode.boundsInRoot

                        // 1. Zero sobreposição geométrica entre os 3 elementos superiores
                        assertFalse(
                            "Zero interseção entre home_interactive_cube_badge ($badgeBounds) e home_restore_cube_button ($restoreBounds) em $ctx",
                            badgeBounds.overlaps(restoreBounds)
                        )
                        assertFalse(
                            "Zero interseção entre home_interactive_cube_badge ($badgeBounds) e active_move_banner ($bannerBounds) em $ctx",
                            badgeBounds.overlaps(bannerBounds)
                        )
                        assertFalse(
                            "Zero interseção entre home_restore_cube_button ($restoreBounds) e active_move_banner ($bannerBounds) em $ctx",
                            restoreBounds.overlaps(bannerBounds)
                        )

                        // 2. Faixa dedicada do indicador de movimento abaixo da 1ª faixa superior e acima dos controles inferiores
                        val topRowBottom = maxOf(badgeBounds.bottom, restoreBounds.bottom)
                        assertTrue(
                            "active_move_banner deve ficar abaixo da faixa superior em $ctx (bannerTop=${bannerBounds.top}, topRowBottom=$topRowBottom)",
                            bannerBounds.top >= topRowBottom - 0.5f
                        )
                        assertFalse(
                            "active_move_banner ($bannerBounds) não pode sobrepor touch_mode_selector ($touchModeBounds) em $ctx",
                            bannerBounds.overlaps(touchModeBounds)
                        )
                        assertFalse(
                            "active_move_banner ($bannerBounds) não pode sobrepor recenter_camera_button ($recenterBounds) em $ctx",
                            bannerBounds.overlaps(recenterBounds)
                        )
                        assertFalse(
                            "touch_mode_selector ($touchModeBounds) e recenter_camera_button ($recenterBounds) não podem se sobrepor em $ctx",
                            touchModeBounds.overlaps(recenterBounds)
                        )

                        // 3. Todos os 3 elementos superiores contidos nos limites do viewport 3D
                        for ((name, rect) in listOf(
                            "home_interactive_cube_badge" to badgeBounds,
                            "home_restore_cube_button" to restoreBounds,
                            "active_move_banner" to bannerBounds
                        )) {
                            assertTrue(
                                "$name ($rect) deve estar contido horizontalmente no cube_3d_viewport ($viewportBounds) em $ctx",
                                rect.left >= viewportBounds.left - 1f && rect.right <= viewportBounds.right + 1f
                            )
                        }

                        // 4. Estabilidade visual: aparecer o active_move_banner não desloca o selo nem o botão Restaurar
                        assertTrue(
                            "Selo 'Cubo 3×3 Interativo' não pode saltar ao exibir active_move_banner em $ctx (idle=$badgeIdleBounds, active=$badgeBounds)",
                            kotlin.math.abs(badgeBounds.top - badgeIdleBounds.top) < 1f &&
                                kotlin.math.abs(badgeBounds.left - badgeIdleBounds.left) < 1f
                        )
                        assertTrue(
                            "Botão 'Restaurar' não pode saltar ao exibir active_move_banner em $ctx (idle=$restoreIdleBounds, active=$restoreBounds)",
                            kotlin.math.abs(restoreBounds.top - restoreIdleBounds.top) < 1f &&
                                kotlin.math.abs(restoreBounds.right - restoreIdleBounds.right) < 1f
                        )

                        // 5. Textos completos e sem truncamento em todos os 3 elementos
                        assertEquals("Cubo 3×3 Interativo", assertNoVisualTextOverflow("home_interactive_cube_badge", ctx))
                        assertEquals("Restaurar", assertNoVisualTextOverflow("home_restore_cube_button", ctx))
                        val bannerText = assertNoVisualTextOverflow("active_move_banner", ctx)
                        assertTrue(
                            "Texto de active_move_banner ('$bannerText') deve conter '${move.notation}' e '${move.titlePt}' em $ctx",
                            bannerText.contains(move.notation) && bannerText.contains(move.titlePt)
                        )

                        // I. Avança a animação até o término e confirma o desaparecimento limpo do indicador temporário
                        repeat(30) {
                            org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                            composeTestRule.waitForIdle()
                        }
                        assertNull(
                            "activeAnimation deve ser nulo após concluir o movimento em $ctx",
                            vm.homePreviewState.value.activeAnimation
                        )
                        assertEquals(
                            "active_move_banner deve desaparecer após o término da animação em $ctx",
                            0,
                            composeTestRule.onAllNodes(hasTestTag("active_move_banner")).fetchSemanticsNodes().size
                        )
                    }

                    // J. Funcionamento normal do botão "Restaurar" após os movimentos
                    assertFalse(
                        "Cubo da Home deve estar embaralhado antes de clicar em Restaurar em $baseCtx",
                        vm.homePreviewState.value.cubeState.isSolved()
                    )
                    composeTestRule.onNodeWithTag("home_restore_cube_button").performClick()
                    composeTestRule.waitForIdle()
                    assertTrue(
                        "Clicar em 'Restaurar' deve restaurar o cubo da Home ao estado resolvido em $baseCtx",
                        vm.homePreviewState.value.cubeState.isSolved()
                    )

                    vm.forceMultiFrameAnimationInTest = false
                    ShadowTrace.reset()
                }
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h780dp-xhdpi")
            org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.fontScale = 1.0f
            ShadowTrace.reset()
        }
    }
}








