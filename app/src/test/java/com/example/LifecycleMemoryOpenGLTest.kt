package com.example

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.curriculum.HighlightMode
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.ActiveLayerAnimation
import com.example.renderer.CubeGLRenderer
import com.example.renderer.CubeTouchGLSurfaceView
import com.example.renderer.GestureInteractionState
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CuboMestreViewModel
import com.example.viewmodel.LearningMode
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
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowTrace
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifecycleMemoryOpenGLTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private data class PointerSpec(val id: Int, val x: Float, val y: Float)

    private fun buildMotionEvent(
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

    /**
     * Suíte Completa do Prompt 3/4:
     * Audita e valida memória (ausência de crescimento ilimitado de telemetria), ciclo de vida Android
     * (ON_PAUSE / ON_STOP / ON_RESUME / onDetachedFromWindow), interrupção e retomada previsível de animações
     * em todos os modos (Home, Aprender, Praticar e Explorar), recriação de superfície OpenGL ES 2.0
     * e disciplina de renderMode (RENDERMODE_CONTINUOUSLY vs RENDERMODE_WHEN_DIRTY).
     */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testMemoryLifecycleSurfaceRecreationAndRenderModeSuite() {
        composeTestRule.waitForIdle()
        lateinit var vm: CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = ViewModelProvider(act)[CuboMestreViewModel::class.java]
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowTrace.reset()

        val renderer = CubeGLRenderer().apply {
            configureViewport(800, 800)
            resetCamera()
        }

        try {
            // =========================================================================
            // TESTE A: Uso prolongado sem crescimento infinito (Modo Produção vs Modo Auditoria Limitado)
            // =========================================================================
            vm.setAuditTelemetryEnabled(false)
            renderer.setAuditTelemetryEnabled(false)
            assertFalse("Em modo de produção, auditTelemetryEnabled do ViewModel deve estar falso", vm.auditTelemetryEnabled)
            assertFalse("Em modo de produção, auditTelemetryEnabled do Renderer deve estar falso", renderer.auditTelemetryEnabled)

            composeTestRule.runOnUiThread {
                vm.openExploreScreen()
                vm.resetExploreCubeSolved()
            }
            composeTestRule.waitForIdle()

            val moveCycle = listOf(
                CubeMove.R, CubeMove.U, CubeMove.F, CubeMove.L,
                CubeMove.D, CubeMove.B, CubeMove.M, CubeMove.E, CubeMove.S
            )
            // Executa 225 movimentos manuais consecutivos + 225 sincronizações/quadros do renderer em modo de produção
            for (i in 0 until 225) {
                val mv = moveCycle[i % moveCycle.size]
                composeTestRule.runOnUiThread {
                    vm.executeExploreMove(mv)
                }
                val anim = ActiveLayerAnimation(mv, (i % 10) / 10f)
                renderer.syncSceneState(
                    newCubeState = vm.exploreState.value.cubeState,
                    newAnimation = anim,
                    newGuidedMove = mv,
                    newHighlightMode = HighlightMode.ALL_VIVID,
                    newHighlightEnabled = false,
                    newIsDarkBackground = true,
                    newAutoRotateIdle = false,
                    newCelebrationPulse = 0f
                )
                renderer.onDrawFrame(null)
            }
            composeTestRule.waitForIdle()

            assertEquals(
                "Com telemetria de auditoria desativada (produção), recordedAnimationFramesForAudit não deve acumular elementos",
                0,
                vm.recordedAnimationFramesForAudit.size
            )
            assertEquals(
                "Com telemetria de auditoria desativada (produção), committedLessonMovesForAudit não deve acumular elementos",
                0,
                vm.committedLessonMovesForAudit.size
            )
            assertEquals(
                "Com telemetria de auditoria desativada (produção), recordedAnimationAngles não deve acumular elementos",
                0,
                renderer.getRecordedAnimationAnglesForAudit().size
            )

            // Agora reativa telemetria de auditoria e verifica o teto estrito (ring buffer) e ausência de duplicatas
            vm.setAuditTelemetryEnabled(true)
            renderer.setAuditTelemetryEnabled(true)
            vm.clearAuditTelemetry()
            renderer.clearRecordedAnimationAnglesForAudit()

            // Verifica que syncSceneState + onDrawFrame com o mesmo ângulo NÃO duplica o registro
            val sampleAnim = ActiveLayerAnimation(CubeMove.R, 0.5f)
            renderer.syncSceneState(
                newCubeState = CubeState.createSolved(),
                newAnimation = sampleAnim,
                newGuidedMove = CubeMove.R,
                newHighlightMode = HighlightMode.ALL_VIVID,
                newHighlightEnabled = false,
                newIsDarkBackground = true,
                newAutoRotateIdle = false,
                newCelebrationPulse = 0f
            )
            renderer.onDrawFrame(null)
            assertEquals(
                "syncSceneState seguido de onDrawFrame para o mesmo quadro não deve duplicar o ângulo",
                1,
                renderer.getRecordedAnimationAnglesForAudit().size
            )

            // Emite 2.200 ângulos distintos no renderer e verifica o limite MAX_AUDIT_RECORDED_ANGLES (1024)
            for (i in 1..2200) {
                val p = (i % 1000) / 1000f + (i * 1e-5f)
                renderer.syncSceneState(
                    newCubeState = CubeState.createSolved(),
                    newAnimation = ActiveLayerAnimation(CubeMove.U, p.coerceIn(0f, 1f)),
                    newGuidedMove = null,
                    newHighlightMode = HighlightMode.ALL_VIVID,
                    newHighlightEnabled = false,
                    newIsDarkBackground = true,
                    newAutoRotateIdle = false,
                    newCelebrationPulse = 0f
                )
            }
            assertTrue(
                "recordedAnimationAngles nunca deve ultrapassar MAX_AUDIT_RECORDED_ANGLES (${CubeGLRenderer.MAX_AUDIT_RECORDED_ANGLES})",
                renderer.getRecordedAnimationAnglesForAudit().size <= CubeGLRenderer.MAX_AUDIT_RECORDED_ANGLES
            )

            // Emite 650 movimentos em aula para verificar o limite MAX_AUDIT_COMMITTED_MOVES (512)
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 1, caseIndex = 0, mode = LearningMode.PRACTICE)
            }
            composeTestRule.waitForIdle()
            for (i in 0 until 650) {
                val mv = if (i % 2 == 0) CubeMove.U else CubeMove.U_PRIME
                composeTestRule.runOnUiThread {
                    vm.executePracticeMove(mv)
                }
            }
            composeTestRule.waitForIdle()
            assertEquals(
                "committedLessonMovesForAudit deve respeitar estritamente o teto MAX_AUDIT_COMMITTED_MOVES (${CuboMestreViewModel.MAX_AUDIT_COMMITTED_MOVES})",
                CuboMestreViewModel.MAX_AUDIT_COMMITTED_MOVES,
                vm.committedLessonMovesForAudit.size
            )

            // =========================================================================
            // TESTE B: Pausar e retomar o aplicativo durante uma demonstração (Modo Aprender)
            // =========================================================================
            vm.forceMultiFrameAnimationInTest = true
            vm.clearAuditTelemetry()
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 4, caseIndex = 1, mode = LearningMode.LEARN)
                vm.resetLessonCube()
            }
            composeTestRule.waitForIdle()

            val lessonInitialCube = vm.lessonState.value.currentCase.createInitialState()
            val lessonDemoMoves = vm.lessonState.value.demoMoves
            assertTrue("Caso 2 da Etapa 4 deve ter pelo menos 6 movimentos", lessonDemoMoves.size >= 6)

            // Inicia a demonstração e avança até que o movimento 0 termine e o movimento 1 esteja em ~50% da rotação física
            var move1InterruptedInBackground = false
            vm.onAnimationFrameEmitted = { _, frame, totalFrames, _ ->
                if (vm.lessonState.value.currentMoveIndex == 0 &&
                    vm.lessonState.value.executingMoveIndex == 1 &&
                    frame == totalFrames / 2 &&
                    !move1InterruptedInBackground
                ) {
                    move1InterruptedInBackground = true
                    // Simula a ida do aplicativo para background (ON_PAUSE / ON_STOP) exatamente no meio do 2º movimento!
                    vm.onAppBackgrounded()
                }
            }

            composeTestRule.runOnUiThread {
                vm.togglePlayDemo()
            }
            var waitLoops = 0
            while (!move1InterruptedInBackground && waitLoops < 200) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                waitLoops++
            }
            ShadowLooper.idleMainLooper(64, TimeUnit.MILLISECONDS)
            vm.onAnimationFrameEmitted = null

            assertTrue("Deve ter interrompido o movimento 1 na ida para background", move1InterruptedInBackground)
            val pausedLessonSnap = vm.lessonState.value
            assertFalse("isAppInForeground deve ser false em background", vm.isAppInForeground)
            assertFalse("isPlayingDemo deve ser false após ir para background", pausedLessonSnap.isPlayingDemo)
            assertFalse("isPausePending deve ser false após ir para background", pausedLessonSnap.isPausePending)
            assertNull("activeAnimation deve ser limpo ao ir para background", pausedLessonSnap.activeAnimation)
            assertNull("executingMoveIndex deve ser limpo ao ir para background", pausedLessonSnap.executingMoveIndex)
            assertEquals(
                "currentMoveIndex deve permanecer no último movimento confirmado (0), sem avançar pelo movimento interrompido",
                0,
                pausedLessonSnap.currentMoveIndex
            )
            assertEquals(
                "CubeState deve refletir exatamente o movimento 0 confirmado",
                lessonInitialCube.applyMoves(lessonDemoMoves.take(1)),
                pausedLessonSnap.cubeState
            )

            // Retorna o aplicativo ao primeiro plano (ON_RESUME) e retoma a demonstração até o fim
            composeTestRule.runOnUiThread {
                vm.onAppForegrounded()
                vm.togglePlayDemo()
            }
            assertTrue("isAppInForeground deve voltar a true no primeiro plano", vm.isAppInForeground)

            var finishLoops = 0
            while (vm.lessonState.value.isPlayingDemo && finishLoops < 1500) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                finishLoops++
            }
            composeTestRule.waitForIdle()

            val finishedLessonSnap = vm.lessonState.value
            assertFalse(finishedLessonSnap.isPlayingDemo)
            assertTrue("A demonstração retomada após o background deve concluir normalmente", finishedLessonSnap.isDemoCompleted)
            assertEquals(lessonDemoMoves.lastIndex, finishedLessonSnap.currentMoveIndex)
            assertEquals(
                "Ao concluir após retomada do background, o CubeState deve coincidir exatamente com toda a sequência aplicada uma única vez",
                lessonInitialCube.applyMoves(lessonDemoMoves),
                finishedLessonSnap.cubeState
            )
            assertEquals(
                "Nenhum movimento pode ter sido duplicado ou omitido entre o pause de background e a retomada",
                lessonDemoMoves,
                vm.committedLessonMovesForAudit.toList()
            )

            // =========================================================================
            // TESTE C: Pausar e retomar durante o Modo Praticar
            // =========================================================================
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 1, caseIndex = 0, mode = LearningMode.PRACTICE)
                vm.resetLessonCube()
            }
            composeTestRule.waitForIdle()
            val practiceStartCube = vm.lessonState.value.cubeState

            // Executa 1 movimento completo (F) no Modo Praticar
            composeTestRule.runOnUiThread {
                vm.executePracticeMove(CubeMove.F)
            }
            var pTicks = 0
            while (vm.lessonState.value.activeAnimation != null && pTicks < 100) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                pTicks++
            }
            assertEquals(listOf(CubeMove.F), vm.lessonState.value.practiceMoveHistory)
            assertEquals(practiceStartCube.applyMove(CubeMove.F), vm.lessonState.value.cubeState)

            // Inicia um 2º movimento (R) e minimiza o app no meio da animação (48ms)
            composeTestRule.runOnUiThread {
                vm.executePracticeMove(CubeMove.R)
            }
            ShadowLooper.idleMainLooper(48, TimeUnit.MILLISECONDS)
            assertNotNull("O 2º movimento do Modo Praticar deve estar animando antes do ON_PAUSE", vm.lessonState.value.activeAnimation)

            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
            }
            ShadowLooper.idleMainLooper(64, TimeUnit.MILLISECONDS)

            assertNull("activeAnimation deve ser nulo após minimizar no Modo Praticar", vm.lessonState.value.activeAnimation)
            assertEquals(
                "O histórico de prática deve preservar o movimento confirmado (F) sem corrupção",
                listOf(CubeMove.F),
                vm.lessonState.value.practiceMoveHistory
            )
            assertEquals(
                "O CubeState da prática deve preservar exatamente o estado confirmado antes de minimizar",
                practiceStartCube.applyMove(CubeMove.F),
                vm.lessonState.value.cubeState
            )

            // Retorna ao primeiro plano e executa novo movimento no Modo Praticar
            composeTestRule.runOnUiThread {
                vm.onAppForegrounded()
                vm.executePracticeMove(CubeMove.U)
            }
            pTicks = 0
            while (vm.lessonState.value.activeAnimation != null && pTicks < 100) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                pTicks++
            }
            assertEquals(listOf(CubeMove.F, CubeMove.U), vm.lessonState.value.practiceMoveHistory)
            assertEquals(practiceStartCube.applyMoves(listOf(CubeMove.F, CubeMove.U)), vm.lessonState.value.cubeState)

            // =========================================================================
            // TESTE D: Pausar e retomar durante o Modo Explorar
            // =========================================================================
            composeTestRule.runOnUiThread {
                vm.openExploreScreen()
                vm.resetExploreCubeSolved()
            }
            composeTestRule.waitForIdle()

            // Executa 2 movimentos confirmados (R, U) no Modo Explorar
            for (mv in listOf(CubeMove.R, CubeMove.U)) {
                composeTestRule.runOnUiThread {
                    vm.executeExploreMove(mv)
                }
                var eTicks = 0
                while (vm.exploreState.value.activeAnimation != null && eTicks < 100) {
                    ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                    eTicks++
                }
            }
            assertEquals(listOf(CubeMove.R, CubeMove.U), vm.exploreState.value.moveHistory)

            // Inicia 3º movimento (F) e minimiza o app no meio da animação
            renderer.cameraYaw = 62f
            renderer.cameraPitch = -18f
            renderer.cameraDistance = 6.8f
            composeTestRule.runOnUiThread {
                vm.executeExploreMove(CubeMove.F)
            }
            ShadowLooper.idleMainLooper(48, TimeUnit.MILLISECONDS)
            assertNotNull(vm.exploreState.value.activeAnimation)

            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
            }
            ShadowLooper.idleMainLooper(64, TimeUnit.MILLISECONDS)

            assertNull("activeAnimation no Modo Explorar deve ser limpo ao minimizar", vm.exploreState.value.activeAnimation)
            assertEquals(
                "Histórico do Modo Explorar deve manter os movimentos confirmados (R, U)",
                listOf(CubeMove.R, CubeMove.U),
                vm.exploreState.value.moveHistory
            )
            assertEquals(
                CubeState.createSolved().applyMoves(listOf(CubeMove.R, CubeMove.U)),
                vm.exploreState.value.cubeState
            )
            // Câmera preservada
            assertEquals(62f, renderer.cameraYaw, 0.01f)
            assertEquals(-18f, renderer.cameraPitch, 0.01f)
            assertEquals(6.8f, renderer.cameraDistance, 0.01f)

            // Retorna ao primeiro plano e desfaz o último movimento confirmado (U)
            composeTestRule.runOnUiThread {
                vm.onAppForegrounded()
                vm.undoExploreMove()
            }
            var uTicks = 0
            while (vm.exploreState.value.activeAnimation != null && uTicks < 100) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                uTicks++
            }
            assertEquals(listOf(CubeMove.R), vm.exploreState.value.moveHistory)
            assertEquals(CubeState.createSolved().applyMove(CubeMove.R), vm.exploreState.value.cubeState)

            // =========================================================================
            // TESTE E & TESTE H: Pausar e retomar na Home + Verificação de renderMode e Idempotência
            // =========================================================================
            composeTestRule.runOnUiThread {
                vm.navigateTo(AppScreen.HOME)
                vm.resetHomePreviewCube()
            }
            composeTestRule.waitForIdle()

            val homeGlView = CubeTouchGLSurfaceView(
                context = context,
                cubeRenderer = renderer
            )
            // Configura como Home em primeiro plano (autoRotateIdle = true -> RENDERMODE_CONTINUOUSLY)
            homeGlView.updateDesiredRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
            homeGlView.handleLifecycleResume()
            assertTrue("GLSurfaceView deve estar ativa após handleLifecycleResume()", homeGlView.isGlSurfaceResumed)
            assertEquals(1, homeGlView.resumeCallCountForAudit)
            assertEquals(
                "Em primeiro plano na Home com rotação ociosa, renderMode deve ser RENDERMODE_CONTINUOUSLY",
                GLSurfaceView.RENDERMODE_CONTINUOUSLY,
                homeGlView.effectiveRenderModeForAudit
            )

            // Chamada duplicada a handleLifecycleResume() deve ser idempotente!
            homeGlView.handleLifecycleResume()
            assertEquals("handleLifecycleResume() deve ser idempotente sem chamadas duplicadas", 1, homeGlView.resumeCallCountForAudit)

            // Em modo estático (ex.: Aula parada sem autoRotate e sem animação), renderMode deve cair para RENDERMODE_WHEN_DIRTY
            homeGlView.updateDesiredRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
            assertEquals(
                "Quando nenhuma animação nem rotação ociosa estiver ativa, renderMode deve ser RENDERMODE_WHEN_DIRTY",
                GLSurfaceView.RENDERMODE_WHEN_DIRTY,
                homeGlView.effectiveRenderModeForAudit
            )

            // Volta para modo contínuo (ex.: animação ativa ou Home) e simula um toque em andamento antes de minimizar
            homeGlView.updateDesiredRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
            val downEv = MotionEvent.obtain(1000L, 1000L, MotionEvent.ACTION_DOWN, 400f, 400f, 0)
            try {
                homeGlView.onTouchEvent(downEv)
            } finally {
                downEv.recycle()
            }
            assertTrue("Toque deve estar ativo antes de pausar", renderer.isUserTouching)

            // Minimiza o app (handleLifecyclePause)
            homeGlView.handleLifecyclePause()
            assertFalse("isGlSurfaceResumed deve ser false em background", homeGlView.isGlSurfaceResumed)
            assertEquals(1, homeGlView.pauseCallCountForAudit)
            assertFalse("Pausar a superfície deve encerrar qualquer toque ativo", renderer.isUserTouching)
            assertEquals(GestureInteractionState.IDLE, homeGlView.arbiter.state)
            assertEquals(
                "Em background, effectiveRenderModeForAudit deve ser forçado para RENDERMODE_WHEN_DIRTY",
                GLSurfaceView.RENDERMODE_WHEN_DIRTY,
                homeGlView.effectiveRenderModeForAudit
            )

            // Chamada duplicada a handleLifecyclePause() deve ser idempotente!
            homeGlView.handleLifecyclePause()
            assertEquals("handleLifecyclePause() deve ser idempotente sem chamadas duplicadas", 1, homeGlView.pauseCallCountForAudit)

            // Retoma ao primeiro plano -> restaura RENDERMODE_CONTINUOUSLY automaticamente
            homeGlView.handleLifecycleResume()
            assertTrue(homeGlView.isGlSurfaceResumed)
            assertEquals(2, homeGlView.resumeCallCountForAudit)
            assertEquals(
                "Ao voltar para o primeiro plano, deve restaurar o modo desejado (RENDERMODE_CONTINUOUSLY)",
                GLSurfaceView.RENDERMODE_CONTINUOUSLY,
                homeGlView.effectiveRenderModeForAudit
            )

            // =========================================================================
            // TESTE F: Alternância rápida entre telas (Home -> Aprender -> Praticar -> Explorar -> Guia -> Home)
            // =========================================================================
            for (cycle in 0 until 6) {
                composeTestRule.runOnUiThread {
                    vm.navigateTo(AppScreen.HOME)
                    vm.executeHomePreviewMove(CubeMove.R)
                    vm.openLesson(stepNumber = (cycle % 7) + 1, caseIndex = 0, mode = LearningMode.LEARN)
                    vm.togglePlayDemo()
                    vm.selectLearningMode(LearningMode.PRACTICE)
                    vm.executePracticeMove(CubeMove.U)
                    vm.openExploreScreen()
                    vm.executeExploreMove(CubeMove.F)
                    vm.setNotationGuideVisible(true)
                    vm.triggerNotationPreview(CubeMove.M)
                    vm.setNotationGuideVisible(false)
                    vm.navigateTo(AppScreen.HOME)
                }
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            }
            composeTestRule.waitForIdle()
            assertEquals(AppScreen.HOME, vm.currentScreen.value)
            assertNull("Após alternância rápida entre telas, nenhuma animação de aula pode ficar presa", vm.lessonState.value.activeAnimation)
            assertNull("Após alternância rápida entre telas, nenhuma animação do Explorar pode ficar presa", vm.exploreState.value.activeAnimation)
            assertNull("Após fechar o Guia de Notação, nenhuma animação do modal pode ficar presa", vm.notationPreviewAnim.value)
            assertFalse("isPlayingDemo deve ser false fora da tela de aula", vm.lessonState.value.isPlayingDemo)

            // =========================================================================
            // TESTE G: Recriação de superfície OpenGL ES 2.0 (onSurfaceCreated + onSurfaceChanged + onDrawFrame)
            // =========================================================================
            val glRecreateRenderer = CubeGLRenderer()
            val expectedCubeAfterMoves = CubeState.createSolved().applyMoves(listOf(CubeMove.R, CubeMove.U, CubeMove.F_PRIME))
            glRecreateRenderer.syncSceneState(
                newCubeState = expectedCubeAfterMoves,
                newAnimation = null,
                newGuidedMove = CubeMove.R,
                newHighlightMode = HighlightMode.ALL_VIVID,
                newHighlightEnabled = true,
                newIsDarkBackground = true,
                newAutoRotateIdle = false,
                newCelebrationPulse = 0f
            )
            glRecreateRenderer.cameraYaw = 52f
            glRecreateRenderer.cameraPitch = 31f
            glRecreateRenderer.cameraDistance = 7.1f

            // Simula 3 ciclos completos de criação/recriação de contexto EGL e mudança de orientação (retrato/paisagem)
            val dimensions = listOf(1080 to 1920, 1920 to 1080, 1080 to 2400)
            for ((idx, dim) in dimensions.withIndex()) {
                glRecreateRenderer.onSurfaceCreated(null, null)
                glRecreateRenderer.onSurfaceChanged(null, dim.first, dim.second)
                glRecreateRenderer.onDrawFrame(null)

                assertEquals(idx + 1, glRecreateRenderer.surfaceCreationCountForAudit)
                assertEquals(dim, glRecreateRenderer.lastViewportDimensionsForAudit)
                assertEquals(132, glRecreateRenderer.getBodyVertexCountForAudit())
                assertEquals(24, glRecreateRenderer.getStickerVertexCountForAudit())
                assertEquals(6, glRecreateRenderer.getStickerFaceCountForAudit())
            }
            // Estado do cubo, câmera e raycasting 3D devem continuar íntegros após as 3 recriações de superfície
            assertEquals(expectedCubeAfterMoves, glRecreateRenderer.cubeState)
            assertEquals(52f, glRecreateRenderer.cameraYaw, 0.01f)
            assertEquals(31f, glRecreateRenderer.cameraPitch, 0.01f)
            assertEquals(7.1f, glRecreateRenderer.cameraDistance, 0.01f)
            val centerScreenAfterRecreate = glRecreateRenderer.projectWorldToScreen(floatArrayOf(0f, 0f, 1.45f))
            assertNotNull("Projeção 3D->2D deve funcionar após recriação de superfície OpenGL", centerScreenAfterRecreate)
            val hitAfterRecreate = glRecreateRenderer.pickCubieAtScreen(centerScreenAfterRecreate!![0], centerScreenAfterRecreate[1])
            assertNotNull("Raycasting 3D deve funcionar após recriação de superfície OpenGL", hitAfterRecreate)

            // =========================================================================
            // TESTE I: Regressão combinada dos Prompts 1 e 2 junto com Ciclo de Vida (Prompt 3)
            // =========================================================================
            val regArbiterMoves = mutableListOf<CubeMove>()
            val regView = CubeTouchGLSurfaceView(
                context = context,
                cubeRenderer = glRecreateRenderer,
                onSwipeMove = { regArbiterMoves.add(it) }
            )
            regView.handleLifecycleResume()
            val p0 = PointerSpec(0, 300f, 600f)
            val p1 = PointerSpec(1, 700f, 600f)
            val evDown0 = buildMotionEvent(9000L, 9000L, MotionEvent.ACTION_DOWN, 0, listOf(p0))
            val evDown1 = buildMotionEvent(9000L, 9016L, MotionEvent.ACTION_POINTER_DOWN, 1, listOf(p0, p1))
            val evUp0 = buildMotionEvent(9000L, 9032L, MotionEvent.ACTION_POINTER_UP, 0, listOf(p0, p1))
            try {
                regView.onTouchEvent(evDown0)
                regView.onTouchEvent(evDown1)
                assertEquals(GestureInteractionState.PINCH_ZOOM, regView.arbiter.state)
                regView.onTouchEvent(evUp0)
                assertEquals(1, regView.activePointerCount)
                assertEquals(1, regView.activePointerId)
                assertEquals(GestureInteractionState.PENDING_GESTURE, regView.arbiter.state)
            } finally {
                evDown0.recycle()
                evDown1.recycle()
                evUp0.recycle()
            }
            // Se o ciclo de vida pausar com 1 dedo ainda apoiado na tela, o estado tátil deve ser limpo sem disparar giro fantasma
            regView.handleLifecyclePause()
            assertEquals(0, regView.activePointerCount)
            assertEquals(MotionEvent.INVALID_POINTER_ID, regView.activePointerId)
            assertEquals(GestureInteractionState.IDLE, regView.arbiter.state)
            assertTrue("Pausa de ciclo de vida com dedo apoiado não pode disparar giro acidental", regArbiterMoves.isEmpty())
        } finally {
            vm.forceMultiFrameAnimationInTest = false
            vm.setAuditTelemetryEnabled(true)
            renderer.setAuditTelemetryEnabled(true)
            vm.onAppForegrounded()
            ShadowTrace.reset()
        }
    }
}
