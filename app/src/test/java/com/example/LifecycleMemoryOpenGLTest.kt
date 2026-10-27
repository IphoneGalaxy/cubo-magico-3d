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

    /**
     * CORREÇÃO 1 DO PROMPT 3/4:
     * Valida a preservação da fase pedagógica (Fase 1, Fase 2 e sequência completa de 2 fases)
     * do Caso 2 da Etapa 4 (Cruz Amarela — Linha Horizontal) ao minimizar o aplicativo durante
     * a animação e retomar pelo botão Reproduzir (togglePlayDemo), bem como o descarte seguro
     * ao trocar de aula, selecionar outro caso, reiniciar ou navegar para outra tela.
     */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testPreservePedagogicalPhaseOnMinimizeAndResumePhase1AndPhase2() {
        composeTestRule.waitForIdle()
        lateinit var vm: CuboMestreViewModel
        composeTestRule.activityRule.scenario.onActivity { act ->
            vm = ViewModelProvider(act)[CuboMestreViewModel::class.java]
        }
        vm.forceMultiFrameAnimationInTest = true
        vm.setAuditTelemetryEnabled(true)

        try {
            // Abre Etapa 4, Caso 2 (caseIndex = 1: Linha Horizontal, 12 movimentos em 2 fases de 6)
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 4, caseIndex = 1, mode = LearningMode.LEARN)
                vm.resetLessonCube()
            }
            composeTestRule.waitForIdle()

            val case2Moves = vm.lessonState.value.demoMoves
            assertEquals("O Caso 2 da Etapa 4 deve ter 12 movimentos", 12, case2Moves.size)
            val initialHorizontalLineCube = vm.lessonState.value.currentCase.createInitialState()
            val expectedLShapeAfterPhase1 = initialHorizontalLineCube.applyMoves(case2Moves.take(6))
            val expectedYellowCrossAfterPhase2 = initialHorizontalLineCube.applyMoves(case2Moves)

            // -----------------------------------------------------------------
            // 1. FASE 1 ISOLADA: playDemoPhase(1) -> minimizar no meio -> voltar -> Reproduzir
            // -----------------------------------------------------------------
            composeTestRule.runOnUiThread {
                vm.playDemoPhase(1)
            }
            // Avança até confirmar 2 movimentos (currentMoveIndex == 1) e estar no meio do 3º (executingMoveIndex == 2)
            var ticks = 0
            while ((vm.lessonState.value.currentMoveIndex < 1 ||
                    vm.lessonState.value.executingMoveIndex != 2 ||
                    (vm.lessonState.value.activeAnimation?.progress ?: 0f) <= 0.15f) &&
                ticks < 300
            ) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                ticks++
            }
            assertEquals(1, vm.lessonState.value.currentMoveIndex)
            assertEquals(2, vm.lessonState.value.executingMoveIndex)
            assertEquals(5, vm.activeDemoTargetEndIndexForAudit)
            assertEquals(1, vm.activeDemoPhaseNumberForAudit)

            // Minimiza o aplicativo durante o 3º movimento da Fase 1
            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
            }
            // Avança o relógio em background para garantir que nenhum movimento é executado em segundo plano
            ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)

            assertFalse("isPlayingDemo deve ser false em background", vm.lessonState.value.isPlayingDemo)
            assertNull("activeAnimation deve ser nulo em background", vm.lessonState.value.activeAnimation)
            assertNull("executingMoveIndex deve ser nulo em background", vm.lessonState.value.executingMoveIndex)
            assertEquals(
                "currentMoveIndex deve permanecer no último movimento matematicamente confirmado (índice 1)",
                1,
                vm.lessonState.value.currentMoveIndex
            )
            assertEquals(
                "cubeState deve corresponder exatamente aos 2 primeiros movimentos confirmados da Fase 1",
                initialHorizontalLineCube.applyMoves(case2Moves.take(2)),
                vm.lessonState.value.cubeState
            )
            assertEquals(
                "O limite da Fase 1 (índice 5) deve ser preservado ao minimizar",
                5,
                vm.activeDemoTargetEndIndexForAudit
            )
            assertEquals(
                "O número da Fase 1 deve ser preservado ao minimizar",
                1,
                vm.activeDemoPhaseNumberForAudit
            )

            // Retorna ao primeiro plano e pressiona Reproduzir (togglePlayDemo)
            composeTestRule.runOnUiThread {
                vm.onAppForegrounded()
                vm.togglePlayDemo()
            }
            ticks = 0
            while (vm.lessonState.value.isPlayingDemo && ticks < 600) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                ticks++
            }
            ShadowLooper.idleMainLooper(64, TimeUnit.MILLISECONDS)

            assertFalse("A Fase 1 retomada deve parar ao concluir o 6º movimento", vm.lessonState.value.isPlayingDemo)
            assertEquals(
                "Ao retomar a Fase 1 via Reproduzir, a demonstração deve parar exatamente no movimento 6 (índice 5)",
                5,
                vm.lessonState.value.currentMoveIndex
            )
            assertEquals(
                "Ao concluir a Fase 1 retomada, o cubo deve estar no formato intermediário de L",
                expectedLShapeAfterPhase1,
                vm.lessonState.value.cubeState
            )
            assertFalse("Concluir apenas a Fase 1 não deve marcar a demonstração inteira como concluída", vm.lessonState.value.isDemoCompleted)

            // -----------------------------------------------------------------
            // 2. FASE 2 ISOLADA: playDemoPhase(2) -> minimizar no 1º movimento e no meio -> voltar -> Reproduzir
            // -----------------------------------------------------------------
            composeTestRule.runOnUiThread {
                vm.resetLessonCube()
                vm.playDemoPhase(2)
            }
            // Avança 48ms para interromper durante o 1º movimento da Fase 2 (executingMoveIndex == 6, currentMoveIndex == 5)
            ShadowLooper.idleMainLooper(48, TimeUnit.MILLISECONDS)
            assertEquals(5, vm.lessonState.value.currentMoveIndex)
            assertEquals(6, vm.lessonState.value.executingMoveIndex)
            assertEquals(expectedLShapeAfterPhase1, vm.lessonState.value.cubeState)

            // Minimiza durante o 1º movimento da Fase 2
            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
            }
            ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)

            assertEquals(
                "Ao minimizar no 1º movimento da Fase 2, currentMoveIndex deve preservar o início da Fase 2 (índice 5)",
                5,
                vm.lessonState.value.currentMoveIndex
            )
            assertEquals(
                "Ao minimizar no 1º movimento da Fase 2, o estado inicial em L da Fase 2 deve ser preservado",
                expectedLShapeAfterPhase1,
                vm.lessonState.value.cubeState
            )
            assertEquals(11, vm.activeDemoTargetEndIndexForAudit)
            assertEquals(2, vm.activeDemoPhaseNumberForAudit)

            // Retorna ao primeiro plano, pressiona Reproduzir, deixa avançar até confirmar o movimento 8 (índice 7) e minimiza novamente
            composeTestRule.runOnUiThread {
                vm.onAppForegrounded()
                vm.togglePlayDemo()
            }
            ticks = 0
            while ((vm.lessonState.value.currentMoveIndex < 7 ||
                    vm.lessonState.value.executingMoveIndex != 8 ||
                    (vm.lessonState.value.activeAnimation?.progress ?: 0f) <= 0.15f) &&
                ticks < 400
            ) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                ticks++
            }
            assertEquals(7, vm.lessonState.value.currentMoveIndex)
            assertEquals(8, vm.lessonState.value.executingMoveIndex)

            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
            }
            ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)

            assertEquals(7, vm.lessonState.value.currentMoveIndex)
            assertEquals(initialHorizontalLineCube.applyMoves(case2Moves.take(8)), vm.lessonState.value.cubeState)
            assertEquals(11, vm.activeDemoTargetEndIndexForAudit)
            assertEquals(2, vm.activeDemoPhaseNumberForAudit)

            // Retorna ao primeiro plano e pressiona Reproduzir para concluir a Fase 2 até o movimento 12
            composeTestRule.runOnUiThread {
                vm.onAppForegrounded()
                vm.togglePlayDemo()
            }
            ticks = 0
            while (vm.lessonState.value.isPlayingDemo && ticks < 600) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                ticks++
            }
            ShadowLooper.idleMainLooper(64, TimeUnit.MILLISECONDS)

            assertFalse(vm.lessonState.value.isPlayingDemo)
            assertEquals(
                "A Fase 2 retomada deve concluir no movimento 12 (índice 11)",
                11,
                vm.lessonState.value.currentMoveIndex
            )
            assertEquals(
                "O estado final após concluir a Fase 2 retomada deve formar a Cruz Amarela",
                expectedYellowCrossAfterPhase2,
                vm.lessonState.value.cubeState
            )
            assertTrue("Ao concluir o movimento 12 da Fase 2, isDemoCompleted deve ser true", vm.lessonState.value.isDemoCompleted)

            // -----------------------------------------------------------------
            // 3. DUAS FASES COMPLETAS: playDemoPhase(0) -> minimizar -> voltar -> Reproduzir até o fim
            // -----------------------------------------------------------------
            composeTestRule.runOnUiThread {
                vm.playDemoPhase(0)
            }
            ticks = 0
            while ((vm.lessonState.value.currentMoveIndex < 3 ||
                    vm.lessonState.value.executingMoveIndex != 4) &&
                ticks < 400
            ) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                ticks++
            }
            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
            }
            ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
            assertEquals(3, vm.lessonState.value.currentMoveIndex)
            assertEquals(initialHorizontalLineCube.applyMoves(case2Moves.take(4)), vm.lessonState.value.cubeState)

            composeTestRule.runOnUiThread {
                vm.onAppForegrounded()
                vm.togglePlayDemo()
            }
            ticks = 0
            while (vm.lessonState.value.isPlayingDemo && ticks < 1000) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                ticks++
            }
            assertEquals(11, vm.lessonState.value.currentMoveIndex)
            assertEquals(expectedYellowCrossAfterPhase2, vm.lessonState.value.cubeState)
            assertTrue(vm.lessonState.value.isDemoCompleted)

            // -----------------------------------------------------------------
            // 4. DESCARTE OBRIGATÓRIO AO REINICIAR, TROCAR DE CASO, TROCAR DE AULA OU NAVEGAR
            // -----------------------------------------------------------------
            // 4a. Reiniciar cubo (resetLessonCube)
            composeTestRule.runOnUiThread {
                vm.playDemoPhase(1)
            }
            ShadowLooper.idleMainLooper(48, TimeUnit.MILLISECONDS)
            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
                vm.onAppForegrounded()
                vm.resetLessonCube()
            }
            assertNull("resetLessonCube deve limpar activeDemoTargetEndIndex", vm.activeDemoTargetEndIndexForAudit)
            assertNull("resetLessonCube deve limpar activeDemoPhaseNumber", vm.activeDemoPhaseNumberForAudit)

            // 4b. Selecionar outro caso (selectCase)
            composeTestRule.runOnUiThread {
                vm.playDemoPhase(1)
            }
            ShadowLooper.idleMainLooper(48, TimeUnit.MILLISECONDS)
            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
                vm.onAppForegrounded()
                vm.selectCase(0)
            }
            assertNull("selectCase deve limpar activeDemoTargetEndIndex", vm.activeDemoTargetEndIndexForAudit)
            assertNull("selectCase deve limpar activeDemoPhaseNumber", vm.activeDemoPhaseNumberForAudit)

            // 4c. Trocar de etapa/aula (openLesson)
            composeTestRule.runOnUiThread {
                vm.selectCase(1)
                vm.playDemoPhase(1)
            }
            ShadowLooper.idleMainLooper(48, TimeUnit.MILLISECONDS)
            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
                vm.onAppForegrounded()
                vm.openLesson(stepNumber = 2, caseIndex = 0, mode = LearningMode.LEARN)
            }
            assertNull("openLesson deve limpar activeDemoTargetEndIndex", vm.activeDemoTargetEndIndexForAudit)
            assertNull("openLesson deve limpar activeDemoPhaseNumber", vm.activeDemoPhaseNumberForAudit)

            // 4d. Navegar para outra tela (openExploreScreen)
            composeTestRule.runOnUiThread {
                vm.openLesson(stepNumber = 4, caseIndex = 1, mode = LearningMode.LEARN)
                vm.playDemoPhase(1)
            }
            ShadowLooper.idleMainLooper(48, TimeUnit.MILLISECONDS)
            composeTestRule.runOnUiThread {
                vm.onAppBackgrounded()
                vm.onAppForegrounded()
                vm.openExploreScreen()
            }
            assertNull("openExploreScreen deve limpar activeDemoTargetEndIndex", vm.activeDemoTargetEndIndexForAudit)
            assertNull("openExploreScreen deve limpar activeDemoPhaseNumber", vm.activeDemoPhaseNumberForAudit)
        } finally {
            vm.forceMultiFrameAnimationInTest = false
            vm.onAppForegrounded()
        }
    }

    /**
     * CORREÇÃO 2 DO PROMPT 3/4:
     * Valida o tratamento determinístico de falhas de compilação e vinculação de shaders OpenGL ES 2.0
     * em loadShader() e createProgram(), incluindo logs vazios/nulos, retorno zero em glCreateShader/glCreateProgram,
     * liberação segura de todos os recursos temporários e bloqueio de uso de shaderProgram inválido em onDrawFrame().
     */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun testOpenGLShaderAndProgramErrorHandlingAndSafeResourceRelease() {
        class FakeGlShaderBackend(
            var nextVertexShaderId: Int = 101,
            var nextFragmentShaderId: Int = 102,
            var nextProgramId: Int = 501,
            var vertexCompileStatus: Int = android.opengl.GLES20.GL_TRUE,
            var fragmentCompileStatus: Int = android.opengl.GLES20.GL_TRUE,
            var programLinkStatus: Int = android.opengl.GLES20.GL_TRUE,
            var shaderInfoLog: String? = "",
            var programInfoLog: String? = ""
        ) : com.example.renderer.GlShaderBackend {
            val deletedShaders = mutableListOf<Int>()
            val detachedShaders = mutableListOf<Pair<Int, Int>>()
            val deletedPrograms = mutableListOf<Int>()
            val usedPrograms = mutableListOf<Int>()

            override fun glCreateShader(type: Int): Int {
                return if (type == android.opengl.GLES20.GL_VERTEX_SHADER) {
                    nextVertexShaderId
                } else {
                    nextFragmentShaderId
                }
            }

            override fun glShaderSource(shader: Int, string: String) {}
            override fun glCompileShader(shader: Int) {}

            override fun glGetShaderiv(shader: Int, pname: Int, params: IntArray, offset: Int) {
                if (pname == android.opengl.GLES20.GL_COMPILE_STATUS) {
                    params[offset] = if (shader == nextVertexShaderId) vertexCompileStatus else fragmentCompileStatus
                }
            }

            override fun glGetShaderInfoLog(shader: Int): String? = shaderInfoLog

            override fun glDeleteShader(shader: Int) {
                deletedShaders.add(shader)
            }

            override fun glCreateProgram(): Int = nextProgramId
            override fun glAttachShader(program: Int, shader: Int) {}
            override fun glLinkProgram(program: Int) {}

            override fun glGetProgramiv(program: Int, pname: Int, params: IntArray, offset: Int) {
                if (pname == android.opengl.GLES20.GL_LINK_STATUS) {
                    params[offset] = programLinkStatus
                }
            }

            override fun glGetProgramInfoLog(program: Int): String? = programInfoLog

            override fun glDetachShader(program: Int, shader: Int) {
                detachedShaders.add(program to shader)
            }

            override fun glDeleteProgram(program: Int) {
                deletedPrograms.add(program)
            }

            override fun glUseProgram(program: Int) {
                usedPrograms.add(program)
            }
        }

        // Caso 1: glCreateShader retornando 0
        val backendShaderZero = FakeGlShaderBackend(nextVertexShaderId = 0)
        var diag1: String? = null
        val resShaderZero = CubeGLRenderer.loadShader(
            type = android.opengl.GLES20.GL_VERTEX_SHADER,
            shaderCode = "void main() {}",
            backend = backendShaderZero,
            onDiagnostic = { diag1 = it }
        )
        assertEquals("loadShader deve retornar 0 quando glCreateShader retorna 0", 0, resShaderZero)
        assertNotNull("Diagnóstico deve ser emitido quando glCreateShader retorna 0", diag1)
        assertTrue(backendShaderZero.deletedShaders.isEmpty())

        // Caso 2: GL_COMPILE_STATUS == 0 com log VAZIO ("") — deve falhar, deletar o shader e retornar 0
        val backendCompileEmptyLog = FakeGlShaderBackend(
            nextVertexShaderId = 11,
            vertexCompileStatus = 0,
            shaderInfoLog = ""
        )
        var diag2: String? = null
        val resCompileEmpty = CubeGLRenderer.loadShader(
            type = android.opengl.GLES20.GL_VERTEX_SHADER,
            shaderCode = "invalid vertex shader",
            backend = backendCompileEmptyLog,
            onDiagnostic = { diag2 = it }
        )
        assertEquals("loadShader deve retornar 0 quando GL_COMPILE_STATUS == 0 mesmo com infoLog vazio", 0, resCompileEmpty)
        assertEquals("O shader inválido deve ser liberado via glDeleteShader mesmo com infoLog vazio", listOf(11), backendCompileEmptyLog.deletedShaders)
        assertNotNull("Diagnóstico deve ser preenchido independentemente de infoLog vazio", diag2)

        // Caso 3: Vertex shader compila (id=21), mas Fragment shader falha (id=22, GL_COMPILE_STATUS == 0 e infoLog null)
        // Ambos os shaders (22 e 21) devem ser deletados sem vazamento
        val backendFragFail = FakeGlShaderBackend(
            nextVertexShaderId = 21,
            nextFragmentShaderId = 22,
            vertexCompileStatus = android.opengl.GLES20.GL_TRUE,
            fragmentCompileStatus = 0,
            shaderInfoLog = null
        )
        var diag3: String? = null
        val resFragFail = CubeGLRenderer.createProgram(
            backend = backendFragFail,
            onDiagnostic = { diag3 = it }
        )
        assertEquals("createProgram deve retornar 0 se o fragment shader falhar", 0, resFragFail)
        assertEquals(
            "Tanto o fragment shader com falha (22) quanto o vertex shader já compilado (21) devem ser deletados",
            listOf(22, 21),
            backendFragFail.deletedShaders
        )
        assertNotNull(diag3)

        // Caso 4: Ambos os shaders compilam (31, 32), mas glCreateProgram() retorna 0
        val backendProgZero = FakeGlShaderBackend(
            nextVertexShaderId = 31,
            nextFragmentShaderId = 32,
            nextProgramId = 0
        )
        var diag4: String? = null
        val resProgZero = CubeGLRenderer.createProgram(
            backend = backendProgZero,
            onDiagnostic = { diag4 = it }
        )
        assertEquals("createProgram deve retornar 0 quando glCreateProgram retorna 0", 0, resProgZero)
        assertEquals(
            "Ambos os shaders temporários (31, 32) devem ser liberados quando glCreateProgram retorna 0",
            listOf(31, 32),
            backendProgZero.deletedShaders
        )
        assertNotNull(diag4)

        // Caso 5: GL_LINK_STATUS == 0 com log VAZIO ("") — deve desanexar/deletar shaders, deletar programa e retornar 0
        val backendLinkFailEmptyLog = FakeGlShaderBackend(
            nextVertexShaderId = 41,
            nextFragmentShaderId = 42,
            nextProgramId = 400,
            programLinkStatus = 0,
            programInfoLog = "   "
        )
        var diag5: String? = null
        val resLinkFail = CubeGLRenderer.createProgram(
            backend = backendLinkFailEmptyLog,
            onDiagnostic = { diag5 = it }
        )
        assertEquals("createProgram deve retornar 0 quando GL_LINK_STATUS == 0 mesmo com infoLog em branco", 0, resLinkFail)
        assertEquals(listOf(400 to 41, 400 to 42), backendLinkFailEmptyLog.detachedShaders)
        assertEquals(listOf(41, 42), backendLinkFailEmptyLog.deletedShaders)
        assertEquals("O programa com falha de link (400) deve ser deletado via glDeleteProgram", listOf(400), backendLinkFailEmptyLog.deletedPrograms)
        assertNotNull("Diagnóstico de falha de link deve ser registrado mesmo com log vazio", diag5)

        // Caso 6: Renderizador não deve chamar glUseProgram quando o programa é inválido (0),
        // e deve voltar a renderizar normalmente quando a superfície recriar um programa válido (> 0)
        val failingRenderer = CubeGLRenderer(shaderBackend = backendLinkFailEmptyLog)
        failingRenderer.onSurfaceCreated(null, null)
        failingRenderer.onSurfaceChanged(null, 800, 800)
        failingRenderer.onDrawFrame(null)

        assertFalse("isShaderProgramValidForAudit deve ser false quando createProgram falha", failingRenderer.isShaderProgramValidForAudit)
        assertEquals(0, failingRenderer.shaderProgramForAudit)
        assertEquals(1, failingRenderer.skippedDrawFramesDueToInvalidProgramForAudit)
        assertTrue("glUseProgram NUNCA deve ser chamado com programa inválido (0)", backendLinkFailEmptyLog.usedPrograms.isEmpty())
        assertNotNull(failingRenderer.lastShaderDiagnosticForAudit)

        // Agora simula recriação da superfície com compilação e vinculação bem-sucedidas
        val successBackend = FakeGlShaderBackend(
            nextVertexShaderId = 51,
            nextFragmentShaderId = 52,
            nextProgramId = 777,
            vertexCompileStatus = android.opengl.GLES20.GL_TRUE,
            fragmentCompileStatus = android.opengl.GLES20.GL_TRUE,
            programLinkStatus = android.opengl.GLES20.GL_TRUE
        )
        val validRenderer = CubeGLRenderer(shaderBackend = successBackend)
        validRenderer.onSurfaceCreated(null, null)
        validRenderer.onSurfaceChanged(null, 800, 800)
        validRenderer.onDrawFrame(null)

        assertTrue("isShaderProgramValidForAudit deve ser true com programa válido", validRenderer.isShaderProgramValidForAudit)
        assertEquals(777, validRenderer.shaderProgramForAudit)
        assertNull("lastShaderDiagnosticForAudit deve ser limpo após criação bem-sucedida", validRenderer.lastShaderDiagnosticForAudit)
        assertEquals(listOf(777 to 51, 777 to 52), successBackend.detachedShaders)
        assertEquals(listOf(51, 52), successBackend.deletedShaders)
        assertTrue(successBackend.deletedPrograms.isEmpty())
        assertEquals("glUseProgram deve ser chamado com o ID válido (777)", listOf(777), successBackend.usedPrograms)
    }
}
