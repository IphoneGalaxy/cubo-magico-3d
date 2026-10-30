package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.curriculum.CurriculumRepository
import com.example.curriculum.CurriculumStep
import com.example.curriculum.LessonCase
import com.example.data.ProgressRepository
import com.example.data.UserProgressState
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.ActiveLayerAnimation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    COURSE,
    LESSON,
    EXPLORE
}

enum class LearningMode(val labelPt: String, val shortLabelPt: String) {
    LEARN("Modo 1: Aprender", "Aprender"),
    PRACTICE("Modo 2: Praticar", "Praticar")
}

/**
 * Estados do painel inferior expansível de explicação na tela de aula:
 * - [COLLAPSED]: Mostra apenas o objetivo resumido e o movimento atual.
 * - [INTERMEDIATE]: Mostra objetivo, descrição do caso e dica principal.
 * - [EXPANDED]: Mostra explicação completa da etapa, notação, nota de validação e orientações adicionais com rolagem interna.
 */
enum class ExplanationPanelState(val labelPt: String) {
    COLLAPSED("Recolhido"),
    INTERMEDIATE("Resumo"),
    EXPANDED("Completo")
}

data class LessonUiState(
    val stepNumber: Int = 1,
    val caseIndex: Int = 0,
    val mode: LearningMode = LearningMode.LEARN,
    val cubeState: CubeState = CurriculumRepository.steps.first().cases.first().createInitialState(),
    val activeAnimation: ActiveLayerAnimation? = null,
    val currentMoveIndex: Int = -1,
    val executingMoveIndex: Int? = null,
    val isPlayingDemo: Boolean = false,
    val isPausePending: Boolean = false,
    val practiceMoveHistory: List<CubeMove> = emptyList(),
    val isPracticeGoalAchieved: Boolean = false,
    val isDemoCompleted: Boolean = false,
    val celebrationPulse: Float = 0f,
    val cameraResetCounter: Int = 0,
    val selectedNotationMoveInfo: CubeMove? = null,
    val explanationPanelState: ExplanationPanelState = ExplanationPanelState.COLLAPSED,
    val selectedPracticeFamily: String = "R",
    val isPracticeMovesExpanded: Boolean = false,
    val feedbackMessage: String? = null
) {
    val currentStep: CurriculumStep = CurriculumRepository.getStep(stepNumber)

    val currentCase: LessonCase = currentStep.cases.getOrElse(caseIndex) { currentStep.cases.first() }

    val demoMoves: List<CubeMove> = currentCase.fullMoveList

    val visualMoveIndex: Int
        get() = executingMoveIndex ?: currentMoveIndex

    val guidedMoveForHighlight: CubeMove?
        get() = when {
            activeAnimation != null -> activeAnimation.move
            mode == LearningMode.LEARN && visualMoveIndex in demoMoves.indices -> demoMoves[visualMoveIndex]
            mode == LearningMode.LEARN && visualMoveIndex == -1 && demoMoves.isNotEmpty() -> demoMoves.first()
            else -> selectedNotationMoveInfo
        }
}

data class ExploreUiState(
    val cubeState: CubeState = CubeState.createSolved(),
    val activeAnimation: ActiveLayerAnimation? = null,
    val moveHistory: List<CubeMove> = emptyList(),
    val lastScramble: List<CubeMove> = emptyList(),
    val cameraResetCounter: Int = 0,
    val celebrationPulse: Float = 0f,
    val feedbackMessage: String? = null
)

data class HomePreviewUiState(
    val cubeState: CubeState = CubeState.createSolved(),
    val activeAnimation: ActiveLayerAnimation? = null,
    val moveHistory: List<CubeMove> = emptyList(),
    val cameraResetCounter: Int = 0
)

class CuboMestreViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MANUAL_MOVE_90_DURATION_MS = 320L
        const val MANUAL_MOVE_180_DURATION_MS = 420L
        const val MANUAL_UNDO_DURATION_MS = 280L
        const val MAX_AUDIT_RECORDED_FRAMES = 1024
        const val MAX_AUDIT_COMMITTED_MOVES = 512
    }

    private enum class LessonStepCommand {
        FORWARD,
        BACKWARD
    }

    private val progressRepository = ProgressRepository(application.applicationContext)

    val progressState: StateFlow<UserProgressState> = progressRepository.progressFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserProgressState()
    )

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _showNotationGuideModal = MutableStateFlow(false)
    val showNotationGuideModal: StateFlow<Boolean> = _showNotationGuideModal.asStateFlow()

    private val _notationPreviewMove = MutableStateFlow(CubeMove.R)
    val notationPreviewMove: StateFlow<CubeMove> = _notationPreviewMove.asStateFlow()

    private val _notationPreviewCube = MutableStateFlow(CubeState.createSolved())
    val notationPreviewCube: StateFlow<CubeState> = _notationPreviewCube.asStateFlow()

    private val _notationPreviewAnim = MutableStateFlow<ActiveLayerAnimation?>(null)
    val notationPreviewAnim: StateFlow<ActiveLayerAnimation?> = _notationPreviewAnim.asStateFlow()

    private val _notationCameraResetCounter = MutableStateFlow(0)
    val notationCameraResetCounter: StateFlow<Int> = _notationCameraResetCounter.asStateFlow()

    private val _lessonState = MutableStateFlow(LessonUiState())
    val lessonState: StateFlow<LessonUiState> = _lessonState.asStateFlow()

    private val _exploreState = MutableStateFlow(ExploreUiState())
    val exploreState: StateFlow<ExploreUiState> = _exploreState.asStateFlow()

    private val _homePreviewState = MutableStateFlow(HomePreviewUiState())
    val homePreviewState: StateFlow<HomePreviewUiState> = _homePreviewState.asStateFlow()

    private var demoJob: Job? = null
    private var singleMoveJob: Job? = null
    private var exploreAnimJob: Job? = null
    private var homeAnimJob: Job? = null
    private var notationPreviewJob: Job? = null
    private var celebrationJob: Job? = null

    // Controle transacional de época e pausa para impedir dessincronizações e corrotinas órfãs
    private var lessonSessionEpoch: Long = 0L
    private var exploreSessionEpoch: Long = 0L
    private var homeSessionEpoch: Long = 0L
    private var notationSessionEpoch: Long = 0L
    private var pauseRequested: Boolean = false
    private var activeDemoTargetEndIndex: Int? = null
    private var activeDemoPhaseNumber: Int? = null

    // Filas seguras para serializar comandos rápidos sem perder nem duplicar movimentos
    private val lessonStepQueue = ArrayDeque<LessonStepCommand>()
    private val practiceMoveQueue = ArrayDeque<CubeMove>()
    private val exploreMoveQueue = ArrayDeque<CubeMove>()
    private val homeMoveQueue = ArrayDeque<CubeMove>()

    private val isHeadlessTest: Boolean =
        android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    /**
     * Indica se o aplicativo encontra-se em primeiro plano (entre ON_RESUME e ON_PAUSE).
     */
    var isAppInForeground: Boolean = true
        private set

    /**
     * Controla se os históricos de telemetria de auditoria estão ativos.
     * Desativado por padrão em produção Android real para evitar acúmulo de memória;
     * ativado em ambiente de teste JVM (Robolectric) com limite máximo estrito (ring buffer).
     */
    var auditTelemetryEnabled: Boolean = isHeadlessTest
        private set

    /**
     * Permite habilitar a execução completa quadro a quadro (0%..25%..50%..75%..100%) em testes de auditoria,
     * mantendo os testes de UI rápidos por padrão.
     */
    var forceMultiFrameAnimationInTest: Boolean = false

    /**
     * Histórico auditável limitado de quadros intermediários emitidos pelo ViewModel antes de aplicar o movimento ao CubeState.
     */
    val recordedAnimationFramesForAudit = mutableListOf<ActiveLayerAnimation>()

    /**
     * Histórico auditável limitado dos movimentos efetivamente consolidados no CubeState da aula (em ordem de commit).
     */
    val committedLessonMovesForAudit = mutableListOf<CubeMove>()

    /**
     * Callback opcional de instrumentação para testes determinísticos inspecionarem/interromperem
     * em marcos exatos de progresso (0%, 25%, 50%, 75%, 100%).
     */
    var onAnimationFrameEmitted: ((move: CubeMove, frame: Int, totalFrames: Int, progress: Float) -> Unit)? = null

    fun setAuditTelemetryEnabled(enabled: Boolean) {
        auditTelemetryEnabled = enabled
        if (!enabled) {
            clearAuditTelemetry()
        }
    }

    fun clearAuditTelemetry() {
        recordedAnimationFramesForAudit.clear()
        committedLessonMovesForAudit.clear()
        onAnimationFrameEmitted = null
    }

    private fun recordAuditFrame(anim: ActiveLayerAnimation) {
        if (!auditTelemetryEnabled) return
        if (recordedAnimationFramesForAudit.size >= MAX_AUDIT_RECORDED_FRAMES) {
            recordedAnimationFramesForAudit.removeAt(0)
        }
        recordedAnimationFramesForAudit.add(anim)
    }

    private fun recordAuditCommittedMove(move: CubeMove) {
        if (!auditTelemetryEnabled) return
        if (committedLessonMovesForAudit.size >= MAX_AUDIT_COMMITTED_MOVES) {
            committedLessonMovesForAudit.removeAt(0)
        }
        committedLessonMovesForAudit.add(move)
    }

    private val shouldSkipAnimationLoop: Boolean
        get() = isHeadlessTest && !forceMultiFrameAnimationInTest

    // =========================================================================
    // NAVEGAÇÃO ENTRE TELAS
    // =========================================================================

    fun navigateTo(screen: AppScreen) {
        stopAllAnimations()
        _currentScreen.value = screen
    }

    fun openCourseScreen() {
        navigateTo(AppScreen.COURSE)
    }

    fun startOrContinueLearning() {
        val savedStep = progressState.value.lastVisitedStep.coerceIn(1, 7)
        val step = CurriculumRepository.getStep(savedStep)
        val savedCaseIdx = step.cases.indexOfFirst { it.id == progressState.value.lastVisitedCaseId }
            .let { if (it >= 0) it else 0 }
        openLesson(stepNumber = savedStep, caseIndex = savedCaseIdx, mode = LearningMode.LEARN)
    }

    fun openLesson(
        stepNumber: Int,
        caseIndex: Int = 0,
        mode: LearningMode = LearningMode.LEARN
    ) {
        stopAllAnimations()
        val validStep = stepNumber.coerceIn(1, 7)
        val step = CurriculumRepository.getStep(validStep)
        val validCaseIndex = caseIndex.coerceIn(0, step.cases.lastIndex)
        val lessonCase = step.cases[validCaseIndex]
        val initialCube = lessonCase.createInitialState()
        val defaultFamily = lessonCase.fullMoveList.firstOrNull()?.baseFamily ?: "R"

        _lessonState.value = LessonUiState(
            stepNumber = validStep,
            caseIndex = validCaseIndex,
            mode = mode,
            cubeState = initialCube,
            activeAnimation = null,
            currentMoveIndex = -1,
            executingMoveIndex = null,
            isPlayingDemo = false,
            isPausePending = false,
            practiceMoveHistory = emptyList(),
            isPracticeGoalAchieved = false,
            isDemoCompleted = false,
            celebrationPulse = 0f,
            cameraResetCounter = _lessonState.value.cameraResetCounter + 1,
            explanationPanelState = ExplanationPanelState.COLLAPSED,
            selectedPracticeFamily = defaultFamily,
            isPracticeMovesExpanded = false,
            feedbackMessage = null
        )
        _currentScreen.value = AppScreen.LESSON

        viewModelScope.launch {
            progressRepository.recordStepVisited(validStep, lessonCase.id)
        }
    }

    fun openExploreScreen() {
        stopAllAnimations()
        _currentScreen.value = AppScreen.EXPLORE
    }

    fun setNotationGuideVisible(visible: Boolean) {
        _showNotationGuideModal.value = visible
        if (visible) {
            triggerNotationPreview(_notationPreviewMove.value)
        } else {
            notationSessionEpoch++
            notationPreviewJob?.cancel()
            notationPreviewJob = null
            _notationPreviewAnim.value = null
        }
    }

    fun resetNotationPreviewCube() {
        notationSessionEpoch++
        notationPreviewJob?.cancel()
        notationPreviewJob = null
        _notationPreviewAnim.value = null
        _notationPreviewCube.value = CubeState.createSolved()
        _notationCameraResetCounter.value = _notationCameraResetCounter.value + 1
    }

    fun triggerNotationPreview(move: CubeMove) {
        notationSessionEpoch++
        val epoch = notationSessionEpoch
        notationPreviewJob?.cancel()
        _notationPreviewMove.value = move
        _notationPreviewCube.value = CubeState.createSolved()
        _notationPreviewAnim.value = null

        notationPreviewJob = viewModelScope.launch {
            if (!shouldSkipAnimationLoop) {
                val durationMs = 460L
                val frameMs = 16L
                val steps = (durationMs / frameMs).toInt().coerceAtLeast(16)
                for (i in 0..steps) {
                    if (!isActive || epoch != notationSessionEpoch) return@launch
                    val anim = ActiveLayerAnimation(move, i.toFloat() / steps)
                    recordAuditFrame(anim)
                    _notationPreviewAnim.value = anim
                    if (i < steps) {
                        delay(frameMs)
                    }
                }
            }
            if (!isActive || epoch != notationSessionEpoch) return@launch
            _notationPreviewAnim.value = null
            _notationPreviewCube.value = _notationPreviewCube.value.applyMove(move)
        }
    }

    // =========================================================================
    // CONTROLES DA TELA DA AULA (APRENDER & PRATICAR)
    // =========================================================================

    fun selectCase(caseIndex: Int) {
        val current = _lessonState.value
        val step = current.currentStep
        val validIdx = caseIndex.coerceIn(0, step.cases.lastIndex)
        openLesson(
            stepNumber = current.stepNumber,
            caseIndex = validIdx,
            mode = current.mode
        )
    }

    fun selectNextCase() {
        val current = _lessonState.value
        val nextIdx = (current.caseIndex + 1).coerceAtMost(current.currentStep.cases.lastIndex)
        if (nextIdx != current.caseIndex) {
            selectCase(nextIdx)
        }
    }

    fun selectPreviousCase() {
        val current = _lessonState.value
        val prevIdx = (current.caseIndex - 1).coerceAtLeast(0)
        if (prevIdx != current.caseIndex) {
            selectCase(prevIdx)
        }
    }

    fun selectLearningMode(mode: LearningMode) {
        val current = _lessonState.value
        if (current.mode == mode) return
        openLesson(
            stepNumber = current.stepNumber,
            caseIndex = current.caseIndex,
            mode = mode
        )
    }

    fun setExplanationPanelState(panelState: ExplanationPanelState) {
        // Preserva integralmente o estado do cubo e da câmera ao expandir/recolher o painel informativo
        _lessonState.update { it.copy(explanationPanelState = panelState) }
    }

    fun cycleExplanationPanelState() {
        val next = when (_lessonState.value.explanationPanelState) {
            ExplanationPanelState.COLLAPSED -> ExplanationPanelState.INTERMEDIATE
            ExplanationPanelState.INTERMEDIATE -> ExplanationPanelState.EXPANDED
            ExplanationPanelState.EXPANDED -> ExplanationPanelState.COLLAPSED
        }
        setExplanationPanelState(next)
    }

    fun selectPracticeFamily(family: String) {
        _lessonState.update { it.copy(selectedPracticeFamily = family) }
    }

    fun togglePracticeMovesExpanded() {
        _lessonState.update { it.copy(isPracticeMovesExpanded = !it.isPracticeMovesExpanded) }
    }

    private fun cancelLessonJobsForReset() {
        lessonSessionEpoch++
        pauseRequested = false
        activeDemoTargetEndIndex = null
        activeDemoPhaseNumber = null
        lessonStepQueue.clear()
        practiceMoveQueue.clear()
        demoJob?.cancel()
        demoJob = null
        singleMoveJob?.cancel()
        singleMoveJob = null
        celebrationJob?.cancel()
        celebrationJob = null
    }

    fun resetLessonCase() = resetLessonCube()

    fun resetLessonCube() {
        cancelLessonJobsForReset()
        val current = _lessonState.value
        val freshCube = current.currentCase.createInitialState()
        _lessonState.update {
            it.copy(
                cubeState = freshCube,
                activeAnimation = null,
                currentMoveIndex = -1,
                executingMoveIndex = null,
                isPlayingDemo = false,
                isPausePending = false,
                practiceMoveHistory = emptyList(),
                isPracticeGoalAchieved = false,
                isDemoCompleted = false,
                celebrationPulse = 0f,
                selectedNotationMoveInfo = null,
                feedbackMessage = null
            )
        }
    }

    fun recenterLessonCamera() {
        _lessonState.update { it.copy(cameraResetCounter = it.cameraResetCounter + 1) }
    }

    fun selectMoveInfo(move: CubeMove?) {
        _lessonState.update { it.copy(selectedNotationMoveInfo = move) }
    }

    /**
     * Reproduz ou pausa a demonstração animada passo a passo no Modo Aprender.
     * Nota importante: concluir a demonstração no Modo Aprender exibe "Demonstração concluída."
     * e NÃO registra automaticamente que o aluno concluiu a prática da etapa.
     */
    fun togglePlayDemo() {
        val state = _lessonState.value
        if (state.isPlayingDemo) {
            pauseDemo()
            return
        }
        startDemoPlayback(
            restartIfCompleted = true,
            targetEndIndex = activeDemoTargetEndIndex,
            phaseNumber = activeDemoPhaseNumber
        )
    }

    /**
     * Pausa determinística e transacional:
     * - Se chamada antes do primeiro movimento (progress == 0f) ou no intervalo entre movimentos,
     *   interrompe imediatamente sem iniciar o próximo movimento.
     * - Se chamada durante uma rotação física em andamento (0% < progress <= 100%), sinaliza
     *   [pauseRequested] para finalizar com segurança o movimento atual até 100%, consolidá-lo
     *   uma única vez no [CubeState] e permanecer pausado antes do próximo movimento.
     */
    fun pauseDemo() {
        val snap = _lessonState.value
        val anim = snap.activeAnimation
        val isMidMove = demoJob?.isActive == true &&
            snap.executingMoveIndex != null &&
            anim != null &&
            anim.progress > 0f

        if (isMidMove) {
            pauseRequested = true
            _lessonState.update {
                it.copy(
                    isPlayingDemo = false,
                    isPausePending = true
                )
            }
        } else {
            pauseRequested = false
            demoJob?.cancel()
            demoJob = null
            _lessonState.update {
                it.copy(
                    isPlayingDemo = false,
                    isPausePending = false,
                    activeAnimation = null,
                    executingMoveIndex = null
                )
            }
        }
    }

    fun repeatDemoSequence() {
        resetLessonCube()
        startDemoPlayback(restartIfCompleted = false, targetEndIndex = null, phaseNumber = null)
    }

    /**
     * Executa uma fase pedagógica específica do caso atual (ex.: Fase 1 ou Fase 2 do Caso 2 da Etapa 4).
     * - phaseNumber = 0: Executa a demonstração completa das 2 fases desde o início.
     * - Fase 1 (phaseNumber = 1): Inicia a partir da Linha Horizontal (ou retoma se pausada no meio da Fase 1)
     *   e executa os 6 primeiros movimentos (F U R U' R' F'), parando na configuração intermediária em formato de L.
     * - Fase 2 (phaseNumber = 2): Se o cubo já estiver na configuração intermediária de L (ou em andamento na Fase 2),
     *   executa os 6 movimentos seguintes (F U R U' R' F') SEM restaurar ou reinicializar o cubo até formar a Cruz Amarela.
     */
    fun playDemoPhase(phaseNumber: Int) {
        if (phaseNumber == 0) {
            repeatDemoSequence()
            return
        }

        val current = _lessonState.value
        val phases = current.currentCase.pedagogicalPhases
        val targetPhase = phases.firstOrNull { it.phaseNumber == phaseNumber } ?: return
        val moves = current.demoMoves
        if (moves.isEmpty()) return

        // Se já estiver reproduzindo exatamente esta fase, evita duplicação; se estiver em pause-pending, retoma
        if (demoJob?.isActive == true && activeDemoPhaseNumber == phaseNumber) {
            if (pauseRequested) {
                pauseRequested = false
                _lessonState.update {
                    it.copy(
                        isPlayingDemo = true,
                        isPausePending = false,
                        feedbackMessage = targetPhase.executionBannerText
                    )
                }
            }
            return
        }

        // Se outro movimento não pertencente ao início desta fase estiver em andamento, cancela com segurança
        val isFinishingPreviousPhaseEnd =
            (demoJob?.isActive == true || singleMoveJob?.isActive == true) &&
                current.executingMoveIndex == targetPhase.startMoveIndex - 1

        if (!isFinishingPreviousPhaseEnd) {
            // Se estiver no meio de uma animação que precisa ser reiniciada para outra fase, cancela
            val anim = current.activeAnimation
            if (anim == null || anim.progress <= 0f || activeDemoPhaseNumber != phaseNumber) {
                cancelLessonJobsForReset()
            }
        } else {
            pauseRequested = true
            lessonStepQueue.clear()
        }

        val snapAfterCancel = _lessonState.value
        val initialCube = snapAfterCancel.currentCase.createInitialState()

        if (phaseNumber == 1) {
            val expectedCubeAtCurrent = if (snapAfterCancel.currentMoveIndex in 0 until targetPhase.endMoveIndex) {
                initialCube.applyMoves(moves.take(snapAfterCancel.currentMoveIndex + 1))
            } else {
                null
            }
            val canResumeMidPhase1 = expectedCubeAtCurrent != null && snapAfterCancel.cubeState == expectedCubeAtCurrent
            if (!canResumeMidPhase1) {
                _lessonState.update {
                    it.copy(
                        cubeState = initialCube,
                        activeAnimation = null,
                        currentMoveIndex = -1,
                        executingMoveIndex = null,
                        isPlayingDemo = true,
                        isPausePending = false,
                        isDemoCompleted = false,
                        celebrationPulse = 0f,
                        feedbackMessage = targetPhase.executionBannerText
                    )
                }
            } else {
                _lessonState.update {
                    it.copy(
                        activeAnimation = null,
                        executingMoveIndex = null,
                        isPlayingDemo = true,
                        isPausePending = false,
                        isDemoCompleted = false,
                        feedbackMessage = targetPhase.executionBannerText
                    )
                }
            }
        } else {
            val expectedStartIdx = targetPhase.startMoveIndex - 1
            val canContinueWithoutReset = if (isFinishingPreviousPhaseEnd) {
                true
            } else if (snapAfterCancel.currentMoveIndex in expectedStartIdx until targetPhase.endMoveIndex) {
                val expectedCube = initialCube.applyMoves(moves.take(snapAfterCancel.currentMoveIndex + 1))
                snapAfterCancel.cubeState == expectedCube
            } else {
                false
            }

            if (!canContinueWithoutReset) {
                val stateAtPhaseStart = initialCube.applyMoves(moves.take(targetPhase.startMoveIndex))
                _lessonState.update {
                    it.copy(
                        cubeState = stateAtPhaseStart,
                        activeAnimation = null,
                        currentMoveIndex = expectedStartIdx,
                        executingMoveIndex = null,
                        isPlayingDemo = true,
                        isPausePending = false,
                        isDemoCompleted = false,
                        celebrationPulse = 0f,
                        feedbackMessage = targetPhase.executionBannerText
                    )
                }
            } else {
                _lessonState.update {
                    it.copy(
                        isPlayingDemo = true,
                        isPausePending = false,
                        isDemoCompleted = false,
                        feedbackMessage = targetPhase.executionBannerText
                    )
                }
            }
        }

        startDemoPlayback(
            restartIfCompleted = false,
            targetEndIndex = targetPhase.endMoveIndex,
            phaseNumber = phaseNumber
        )
    }

    private fun startDemoPlayback(
        restartIfCompleted: Boolean,
        targetEndIndex: Int? = null,
        phaseNumber: Int? = null
    ) {
        val current = _lessonState.value
        if (current.mode != LearningMode.LEARN) return
        val moves = current.demoMoves
        val phases = current.currentCase.pedagogicalPhases
        if (moves.isEmpty()) return

        val effectiveEndIndex = (targetEndIndex ?: moves.lastIndex).coerceIn(0, moves.lastIndex)

        // Se já houver um demoJob em execução:
        if (demoJob?.isActive == true) {
            if (pauseRequested) {
                // O usuário havia solicitado pausa durante uma rotação física e clicou em Reproduzir antes do término:
                // cancela o pedido de pausa para continuar perfeitamente após o movimento em curso!
                pauseRequested = false
                activeDemoTargetEndIndex = effectiveEndIndex
                activeDemoPhaseNumber = phaseNumber
                _lessonState.update {
                    it.copy(
                        isPlayingDemo = true,
                        isPausePending = false
                    )
                }
            }
            return
        }

        // Remove passos manuais ainda não iniciados na fila, mas aguarda qualquer passo único já em rotação terminar
        lessonStepQueue.clear()
        val inFlightSingleMoveJob = singleMoveJob

        if (restartIfCompleted &&
            inFlightSingleMoveJob?.isActive != true &&
            (current.isDemoCompleted || current.currentMoveIndex >= effectiveEndIndex)
        ) {
            val freshCube = current.currentCase.createInitialState()
            activeDemoTargetEndIndex = moves.lastIndex
            activeDemoPhaseNumber = null
            _lessonState.update {
                it.copy(
                    cubeState = freshCube,
                    activeAnimation = null,
                    currentMoveIndex = -1,
                    executingMoveIndex = null,
                    isDemoCompleted = false,
                    celebrationPulse = 0f,
                    feedbackMessage = null
                )
            }
        } else {
            activeDemoTargetEndIndex = effectiveEndIndex
            activeDemoPhaseNumber = phaseNumber
        }

        pauseRequested = false
        val sessionEpoch = lessonSessionEpoch
        _lessonState.update {
            it.copy(
                isPlayingDemo = true,
                isPausePending = false
            )
        }

        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            if (inFlightSingleMoveJob?.isActive == true) {
                inFlightSingleMoveJob.join()
            }
            if (!isActive || sessionEpoch != lessonSessionEpoch) return@launch
            if (pauseRequested) {
                pauseRequested = false
                _lessonState.update {
                    it.copy(
                        isPlayingDemo = false,
                        isPausePending = false,
                        activeAnimation = null,
                        executingMoveIndex = null
                    )
                }
                return@launch
            }

            while (isActive && sessionEpoch == lessonSessionEpoch) {
                val snap = _lessonState.value
                val endIdx = (activeDemoTargetEndIndex ?: moves.lastIndex).coerceIn(0, moves.lastIndex)
                val nextIndex = snap.currentMoveIndex + 1

                if (nextIndex > endIdx || nextIndex > moves.lastIndex) {
                    val isFinalMove = nextIndex > moves.lastIndex
                    val completedPhase = phases.firstOrNull { it.endMoveIndex == snap.currentMoveIndex }
                    activeDemoTargetEndIndex = null
                    activeDemoPhaseNumber = null
                    pauseRequested = false
                    _lessonState.update {
                        it.copy(
                            isPlayingDemo = false,
                            isPausePending = false,
                            activeAnimation = null,
                            executingMoveIndex = null,
                            isDemoCompleted = isFinalMove,
                            feedbackMessage = if (isFinalMove) {
                                "Demonstração concluída."
                            } else {
                                completedPhase?.completionSummaryText ?: it.feedbackMessage
                            }
                        )
                    }
                    break
                }

                val move = moves[nextIndex]
                // Destaca o movimento em execução em executingMoveIndex SEM avançar currentMoveIndex antes do commit!
                _lessonState.update {
                    it.copy(
                        executingMoveIndex = nextIndex,
                        selectedNotationMoveInfo = move,
                        feedbackMessage = "Movimento ${nextIndex + 1}/${moves.size}: ${move.notation}"
                    )
                }

                val committed = animateLessonMoveInternal(
                    move = move,
                    sessionEpoch = sessionEpoch,
                    onCommitState = { stateAtCommit ->
                        val currentEndIdx = (activeDemoTargetEndIndex ?: moves.lastIndex).coerceIn(0, moves.lastIndex)
                        val reachedTargetEnd = nextIndex >= currentEndIdx
                        val reachedFullEnd = nextIndex >= moves.lastIndex
                        val stoppingNow = pauseRequested || reachedTargetEnd || reachedFullEnd
                        val phaseAtMove = phases.firstOrNull {
                            it.endMoveIndex == nextIndex && nextIndex < moves.lastIndex
                        }

                        stateAtCommit.copy(
                            cubeState = stateAtCommit.cubeState.applyMove(move),
                            currentMoveIndex = nextIndex,
                            executingMoveIndex = null,
                            activeAnimation = null,
                            isPlayingDemo = !stoppingNow,
                            isPausePending = false,
                            isDemoCompleted = reachedFullEnd,
                            feedbackMessage = when {
                                reachedFullEnd -> "Demonstração concluída."
                                phaseAtMove != null -> phaseAtMove.completionSummaryText
                                else -> "Movimento ${nextIndex + 1}/${moves.size}: ${move.notation}"
                            }
                        )
                    }
                )

                if (!committed || !isActive || sessionEpoch != lessonSessionEpoch) {
                    break
                }

                val currentEndIdx = (activeDemoTargetEndIndex ?: moves.lastIndex).coerceIn(0, moves.lastIndex)
                if (pauseRequested) {
                    pauseRequested = false
                    break
                }

                if (nextIndex >= currentEndIdx || nextIndex >= moves.lastIndex) {
                    activeDemoTargetEndIndex = null
                    activeDemoPhaseNumber = null
                    break
                }

                val completedPhase = phases.firstOrNull { it.endMoveIndex == nextIndex && nextIndex < moves.lastIndex }
                if (completedPhase != null) {
                    if (!shouldSkipAnimationLoop) {
                        delay(420L)
                    }
                } else {
                    val speed = progressState.value.animationSpeedMultiplier.coerceIn(0.4f, 2.0f)
                    val pauseMs = (300L / speed).toLong().coerceAtLeast(110L)
                    delay(pauseMs)
                }

                if (pauseRequested || !_lessonState.value.isPlayingDemo) {
                    pauseRequested = false
                    _lessonState.update {
                        it.copy(
                            isPlayingDemo = false,
                            isPausePending = false,
                            activeAnimation = null,
                            executingMoveIndex = null
                        )
                    }
                    break
                }
            }
        }
        demoJob = job
        job.start()
    }

    /**
     * Avança exatamente um movimento na demonstração de maneira serializada e transacional.
     */
    fun stepDemoForward() {
        val snap = _lessonState.value
        if (snap.mode != LearningMode.LEARN) return
        if (demoJob?.isActive == true) {
            pauseDemo()
        }
        activeDemoTargetEndIndex = null
        activeDemoPhaseNumber = null

        if (lessonStepQueue.size < 32) {
            lessonStepQueue.addLast(LessonStepCommand.FORWARD)
        }
        drainLessonStepQueue()
    }

    /**
     * Retrocede exatamente um movimento na demonstração aplicando o movimento inverso
     * somente sobre movimentos cujo estado matemático já foi efetivamente consolidado.
     */
    fun stepDemoBackward() {
        val snap = _lessonState.value
        if (snap.mode != LearningMode.LEARN) return
        if (demoJob?.isActive == true) {
            pauseDemo()
        }
        activeDemoTargetEndIndex = null
        activeDemoPhaseNumber = null

        // Se nenhuma animação nem comando anterior estiver em fila e currentMoveIndex < 0, nada a desfazer
        if (demoJob?.isActive != true &&
            singleMoveJob?.isActive != true &&
            lessonStepQueue.isEmpty() &&
            snap.currentMoveIndex < 0
        ) {
            return
        }

        if (lessonStepQueue.size < 32) {
            lessonStepQueue.addLast(LessonStepCommand.BACKWARD)
        }
        drainLessonStepQueue()
    }

    private fun drainLessonStepQueue() {
        if (singleMoveJob?.isActive == true) return
        val sessionEpoch = lessonSessionEpoch
        val activeDemoToWait = demoJob

        val stepJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            if (activeDemoToWait?.isActive == true) {
                activeDemoToWait.join()
            }
            if (!isActive || sessionEpoch != lessonSessionEpoch) return@launch

            while (lessonStepQueue.isNotEmpty() && isActive && sessionEpoch == lessonSessionEpoch) {
                val cmd = lessonStepQueue.removeFirst()
                val snap = _lessonState.value
                if (snap.mode != LearningMode.LEARN) {
                    lessonStepQueue.clear()
                    break
                }
                val moves = snap.demoMoves
                val phases = snap.currentCase.pedagogicalPhases

                when (cmd) {
                    LessonStepCommand.FORWARD -> {
                        val nextIndex = snap.currentMoveIndex + 1
                        if (nextIndex > moves.lastIndex) continue
                        val move = moves[nextIndex]
                        _lessonState.update {
                            it.copy(
                                executingMoveIndex = nextIndex,
                                selectedNotationMoveInfo = move,
                                feedbackMessage = "Movimento ${nextIndex + 1}/${moves.size}: ${move.notation}"
                            )
                        }
                        val committed = animateLessonMoveInternal(
                            move = move,
                            sessionEpoch = sessionEpoch,
                            onCommitState = { current ->
                                val completedPhase = phases.firstOrNull {
                                    it.endMoveIndex == nextIndex && nextIndex < moves.lastIndex
                                }
                                val isFinal = nextIndex == moves.lastIndex
                                current.copy(
                                    cubeState = current.cubeState.applyMove(move),
                                    currentMoveIndex = nextIndex,
                                    executingMoveIndex = null,
                                    activeAnimation = null,
                                    isDemoCompleted = isFinal,
                                    feedbackMessage = when {
                                        isFinal -> "Demonstração concluída."
                                        completedPhase != null -> completedPhase.completionSummaryText
                                        else -> "Movimento ${nextIndex + 1}/${moves.size}: ${move.notation}"
                                    }
                                )
                            }
                        )
                        if (!committed) break
                    }

                    LessonStepCommand.BACKWARD -> {
                        val currentIdx = snap.currentMoveIndex
                        if (currentIdx < 0 || currentIdx > moves.lastIndex) continue
                        val moveToUndo = moves[currentIdx]
                        val inverseMove = moveToUndo.inverse()
                        val targetIdx = currentIdx - 1
                        _lessonState.update {
                            it.copy(
                                isDemoCompleted = false,
                                executingMoveIndex = currentIdx,
                                selectedNotationMoveInfo = moveToUndo,
                                feedbackMessage = "Passo revertido: ${moveToUndo.notation}"
                            )
                        }
                        val committed = animateLessonMoveInternal(
                            move = inverseMove,
                            sessionEpoch = sessionEpoch,
                            onCommitState = { current ->
                                val phaseMilestone = phases.firstOrNull {
                                    it.endMoveIndex == targetIdx && targetIdx < moves.lastIndex
                                }
                                current.copy(
                                    cubeState = current.cubeState.applyMove(inverseMove),
                                    currentMoveIndex = targetIdx,
                                    executingMoveIndex = null,
                                    activeAnimation = null,
                                    isDemoCompleted = false,
                                    feedbackMessage = phaseMilestone?.completionSummaryText
                                        ?: "Passo revertido: ${moveToUndo.notation}"
                                )
                            }
                        )
                        if (!committed) break
                    }
                }
            }
        }
        singleMoveJob = stepJob
        stepJob.start()
    }

    /**
     * Executa um movimento do usuário no Modo Praticar (por toque no cubo 3D ou botão de notação).
     * Se uma animação estiver em andamento, enfileira com segurança para não perder eventos.
     */
    fun executePracticeMove(move: CubeMove) {
        val snap = _lessonState.value
        if (snap.mode != LearningMode.PRACTICE) return

        if (snap.activeAnimation != null || singleMoveJob?.isActive == true) {
            if (practiceMoveQueue.size < 4) {
                practiceMoveQueue.addLast(move)
            }
            return
        }

        practiceMoveQueue.addLast(move)
        drainPracticeMoveQueue()
    }

    private fun drainPracticeMoveQueue() {
        if (singleMoveJob?.isActive == true) return
        val sessionEpoch = lessonSessionEpoch
        singleMoveJob = viewModelScope.launch {
            while (practiceMoveQueue.isNotEmpty() && isActive && sessionEpoch == lessonSessionEpoch) {
                val nextMove = practiceMoveQueue.removeFirst()
                val manualDuration = if (kotlin.math.abs(nextMove.glAngleDegrees) > 100f) {
                    MANUAL_MOVE_180_DURATION_MS
                } else {
                    MANUAL_MOVE_90_DURATION_MS
                }
                var reachedGoalAfterCommit = false
                val committed = animateLessonMoveInternal(
                    move = nextMove,
                    sessionEpoch = sessionEpoch,
                    useDidacticSpeed = false,
                    customDurationMs = manualDuration,
                    onCommitState = { current ->
                        val updatedCube = current.cubeState.applyMove(nextMove)
                        val goalReached = updatedCube.isStepGoalAchieved(current.stepNumber)
                        reachedGoalAfterCommit = goalReached
                        current.copy(
                            cubeState = updatedCube,
                            activeAnimation = null,
                            executingMoveIndex = null,
                            selectedNotationMoveInfo = nextMove,
                            practiceMoveHistory = current.practiceMoveHistory + nextMove,
                            isPracticeGoalAchieved = goalReached,
                            feedbackMessage = if (goalReached) {
                                "Excelente! Você completou esta etapa."
                            } else {
                                "Movimento executado: ${nextMove.notation}"
                            }
                        )
                    }
                )
                if (!committed) break

                if (reachedGoalAfterCommit) {
                    practiceMoveQueue.clear()
                    val snap = _lessonState.value
                    val step = snap.currentStep
                    val caseId = snap.currentCase.id
                    progressRepository.markCaseCompleted(
                        stepNumber = snap.stepNumber,
                        caseId = caseId,
                        allStepCaseIds = step.cases.map { c -> c.id }
                    )
                    progressRepository.markStepCompleted(snap.stepNumber)
                    triggerLessonCelebration()
                    break
                }
            }
        }
    }

    fun undoPracticeMove() {
        val snap = _lessonState.value
        if (snap.mode != LearningMode.PRACTICE || snap.activeAnimation != null || singleMoveJob?.isActive == true) return
        val lastMove = snap.practiceMoveHistory.lastOrNull() ?: return

        practiceMoveQueue.clear()
        val sessionEpoch = lessonSessionEpoch
        singleMoveJob = viewModelScope.launch {
            val inverseMove = lastMove.inverse()
            animateLessonMoveInternal(
                move = inverseMove,
                sessionEpoch = sessionEpoch,
                useDidacticSpeed = false,
                customDurationMs = MANUAL_UNDO_DURATION_MS,
                onCommitState = { current ->
                    val updatedCube = current.cubeState.applyMove(inverseMove)
                    val goalReached = updatedCube.isStepGoalAchieved(current.stepNumber)
                    current.copy(
                        cubeState = updatedCube,
                        activeAnimation = null,
                        executingMoveIndex = null,
                        practiceMoveHistory = current.practiceMoveHistory.dropLast(1),
                        isPracticeGoalAchieved = goalReached,
                        feedbackMessage = "Movimento desfeito"
                    )
                }
            )
        }
    }

    fun markCurrentStepCompletedManually() {
        val stepNum = _lessonState.value.stepNumber
        val step = _lessonState.value.currentStep
        val caseId = _lessonState.value.currentCase.id
        viewModelScope.launch {
            progressRepository.markCaseCompleted(
                stepNumber = stepNum,
                caseId = caseId,
                allStepCaseIds = step.cases.map { it.id }
            )
            progressRepository.markStepCompleted(stepNum)
        }
        _lessonState.update {
            it.copy(feedbackMessage = "Etapa $stepNum marcada manualmente como concluída.")
        }
        triggerLessonCelebration()
    }

    fun resetStepStatus(stepNumber: Int) {
        val step = CurriculumRepository.getStep(stepNumber)
        viewModelScope.launch {
            progressRepository.resetStepProgress(stepNumber, step.cases.map { it.id })
        }
    }

    /**
     * Executa os quadros intermediários da rotação 3D e aplica o commit matemático de forma
     * estritamente atômica somente após o último quadro (100%), desde que a sessão/corrotina
     * não tenha sido cancelada.
     */
    private suspend fun animateLessonMoveInternal(
        move: CubeMove,
        sessionEpoch: Long = lessonSessionEpoch,
        useDidacticSpeed: Boolean = true,
        customDurationMs: Long? = null,
        onCommitState: ((LessonUiState) -> LessonUiState)? = null
    ): Boolean {
        if (!kotlinx.coroutines.currentCoroutineContext().isActive || sessionEpoch != lessonSessionEpoch) {
            return false
        }

        if (!shouldSkipAnimationLoop) {
            val durationMs = if (customDurationMs != null && !useDidacticSpeed) {
                customDurationMs
            } else {
                val speed = progressState.value.animationSpeedMultiplier.coerceIn(0.4f, 2.0f)
                val baseDuration = if (kotlin.math.abs(move.glAngleDegrees) > 100f) 560L else 420L
                (baseDuration / speed).toLong().coerceAtLeast(180L)
            }
            val frameMs = 16L
            val rawFrames = (durationMs / frameMs).toInt().coerceAtLeast(16)
            // Arredonda para múltiplo de 4 para garantir marcos exatos de 0%, 25%, 50%, 75% e 100%
            val totalFrames = ((rawFrames + 3) / 4) * 4

            for (frame in 0..totalFrames) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive || sessionEpoch != lessonSessionEpoch) {
                    return false
                }
                val progress = frame.toFloat() / totalFrames.toFloat()
                val anim = ActiveLayerAnimation(move, progress)
                recordAuditFrame(anim)
                _lessonState.update {
                    it.copy(activeAnimation = anim)
                }
                onAnimationFrameEmitted?.invoke(move, frame, totalFrames, progress)

                if (!kotlinx.coroutines.currentCoroutineContext().isActive || sessionEpoch != lessonSessionEpoch) {
                    return false
                }
                if (frame < totalFrames) {
                    delay(frameMs)
                }
            }
        }

        if (!kotlinx.coroutines.currentCoroutineContext().isActive || sessionEpoch != lessonSessionEpoch) {
            return false
        }

        recordAuditCommittedMove(move)
        _lessonState.update { current ->
            if (onCommitState != null) {
                onCommitState(current)
            } else {
                current.copy(
                    cubeState = current.cubeState.applyMove(move),
                    activeAnimation = null,
                    executingMoveIndex = null
                )
            }
        }
        return true
    }

    private fun triggerLessonCelebration() {
        celebrationJob?.cancel()
        if (isHeadlessTest) {
            _lessonState.update { it.copy(celebrationPulse = 0f) }
            return
        }
        val sessionEpoch = lessonSessionEpoch
        celebrationJob = viewModelScope.launch {
            val frames = 28
            for (i in 1..frames) {
                if (!isActive || sessionEpoch != lessonSessionEpoch) return@launch
                val t = i.toFloat() / frames
                val pulse = kotlin.math.sin(t * Math.PI * 2).toFloat().let { kotlin.math.abs(it) }
                _lessonState.update { it.copy(celebrationPulse = pulse) }
                delay(20L)
            }
            if (isActive && sessionEpoch == lessonSessionEpoch) {
                _lessonState.update { it.copy(celebrationPulse = 0f) }
            }
        }
    }

    // =========================================================================
    // CONTROLES DA TELA DE EXPLORAÇÃO (SANDBOX LIVRE)
    // =========================================================================

    fun executeExploreMove(move: CubeMove) {
        if (_exploreState.value.activeAnimation != null || exploreAnimJob?.isActive == true) {
            if (exploreMoveQueue.size < 4) {
                exploreMoveQueue.addLast(move)
            }
            return
        }
        exploreMoveQueue.addLast(move)
        drainExploreMoveQueue()
    }

    private fun drainExploreMoveQueue() {
        if (exploreAnimJob?.isActive == true) return
        val epoch = exploreSessionEpoch
        exploreAnimJob = viewModelScope.launch {
            while (exploreMoveQueue.isNotEmpty() && isActive && epoch == exploreSessionEpoch) {
                val nextMove = exploreMoveQueue.removeFirst()
                val duration = if (kotlin.math.abs(nextMove.glAngleDegrees) > 100f) {
                    MANUAL_MOVE_180_DURATION_MS
                } else {
                    MANUAL_MOVE_90_DURATION_MS
                }
                val committed = animateExploreMoveInternal(
                    move = nextMove,
                    durationMs = duration,
                    epoch = epoch,
                    onCommitState = { current ->
                        current.copy(
                            cubeState = current.cubeState.applyMove(nextMove),
                            activeAnimation = null,
                            moveHistory = current.moveHistory + nextMove,
                            feedbackMessage = "Movimento executado: ${nextMove.notation}"
                        )
                    }
                )
                if (!committed) break
            }
        }
    }

    fun undoExploreMove() {
        val snap = _exploreState.value
        if (snap.activeAnimation != null || exploreAnimJob?.isActive == true) return
        val lastMove = snap.moveHistory.lastOrNull() ?: return

        exploreMoveQueue.clear()
        val epoch = exploreSessionEpoch
        exploreAnimJob = viewModelScope.launch {
            val inverseMove = lastMove.inverse()
            animateExploreMoveInternal(
                move = inverseMove,
                durationMs = MANUAL_UNDO_DURATION_MS,
                epoch = epoch,
                onCommitState = { current ->
                    current.copy(
                        cubeState = current.cubeState.applyMove(inverseMove),
                        activeAnimation = null,
                        moveHistory = current.moveHistory.dropLast(1),
                        feedbackMessage = "Movimento desfeito"
                    )
                }
            )
        }
    }

    fun scrambleExploreCube() {
        if (_exploreState.value.activeAnimation != null || exploreAnimJob?.isActive == true) return
        exploreMoveQueue.clear()
        val epoch = exploreSessionEpoch
        val scramble = CubeState.generateScramble(length = 16)
        exploreAnimJob = viewModelScope.launch {
            _exploreState.update { it.copy(feedbackMessage = "Embaralhando cubo 3×3...") }
            for (move in scramble) {
                if (!isActive || epoch != exploreSessionEpoch) return@launch
                val committed = animateExploreMoveInternal(
                    move = move,
                    durationMs = 110L,
                    epoch = epoch,
                    onCommitState = { current ->
                        current.copy(
                            cubeState = current.cubeState.applyMove(move),
                            activeAnimation = null,
                            moveHistory = current.moveHistory + move
                        )
                    }
                )
                if (!committed) return@launch
            }
            if (isActive && epoch == exploreSessionEpoch) {
                _exploreState.update {
                    it.copy(
                        lastScramble = scramble,
                        feedbackMessage = "Cubo embaralhado (${scramble.size} movimentos)"
                    )
                }
            }
        }
    }

    fun resetExploreCubeSolved() {
        exploreSessionEpoch++
        exploreMoveQueue.clear()
        exploreAnimJob?.cancel()
        exploreAnimJob = null
        _exploreState.update {
            it.copy(
                cubeState = CubeState.createSolved(),
                activeAnimation = null,
                moveHistory = emptyList(),
                lastScramble = emptyList(),
                celebrationPulse = 0f,
                feedbackMessage = "Cubo restaurado ao estado resolvido"
            )
        }
    }

    fun recenterExploreCamera() {
        _exploreState.update {
            it.copy(
                cameraResetCounter = it.cameraResetCounter + 1,
                feedbackMessage = "Câmera recentralizada"
            )
        }
    }

    private suspend fun animateExploreMoveInternal(
        move: CubeMove,
        durationMs: Long,
        epoch: Long = exploreSessionEpoch,
        onCommitState: ((ExploreUiState) -> ExploreUiState)? = null
    ): Boolean {
        if (!kotlinx.coroutines.currentCoroutineContext().isActive || epoch != exploreSessionEpoch) {
            return false
        }
        if (!shouldSkipAnimationLoop) {
            val frameMs = 16L
            val rawFrames = (durationMs / frameMs).toInt().coerceAtLeast(8)
            val totalFrames = ((rawFrames + 3) / 4) * 4
            for (frame in 0..totalFrames) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive || epoch != exploreSessionEpoch) {
                    return false
                }
                val progress = frame.toFloat() / totalFrames.toFloat()
                val anim = ActiveLayerAnimation(move, progress)
                recordAuditFrame(anim)
                _exploreState.update {
                    it.copy(activeAnimation = anim)
                }
                if (frame < totalFrames) {
                    delay(frameMs)
                }
            }
        }
        if (!kotlinx.coroutines.currentCoroutineContext().isActive || epoch != exploreSessionEpoch) {
            return false
        }
        _exploreState.update { current ->
            if (onCommitState != null) {
                onCommitState(current)
            } else {
                current.copy(
                    cubeState = current.cubeState.applyMove(move),
                    activeAnimation = null
                )
            }
        }
        return true
    }

    // =========================================================================
    // PRÉVIA 3D INTERATIVA DA TELA INICIAL (HOME) — ESTADO 100% ISOLADO
    // =========================================================================

    fun executeHomePreviewMove(move: CubeMove) {
        if (_homePreviewState.value.activeAnimation != null || homeAnimJob?.isActive == true) {
            if (homeMoveQueue.size < 4) {
                homeMoveQueue.addLast(move)
            }
            return
        }
        homeMoveQueue.addLast(move)
        drainHomeMoveQueue()
    }

    private fun drainHomeMoveQueue() {
        if (homeAnimJob?.isActive == true) return
        val epoch = homeSessionEpoch
        homeAnimJob = viewModelScope.launch {
            while (homeMoveQueue.isNotEmpty() && isActive && epoch == homeSessionEpoch) {
                val nextMove = homeMoveQueue.removeFirst()
                val duration = if (kotlin.math.abs(nextMove.glAngleDegrees) > 100f) {
                    MANUAL_MOVE_180_DURATION_MS
                } else {
                    MANUAL_MOVE_90_DURATION_MS
                }
                val committed = animateHomeMoveInternal(
                    move = nextMove,
                    durationMs = duration,
                    epoch = epoch,
                    onCommitState = { current ->
                        current.copy(
                            cubeState = current.cubeState.applyMove(nextMove),
                            activeAnimation = null,
                            moveHistory = current.moveHistory + nextMove
                        )
                    }
                )
                if (!committed) break
            }
        }
    }

    fun resetHomePreviewCube() {
        homeSessionEpoch++
        homeMoveQueue.clear()
        homeAnimJob?.cancel()
        homeAnimJob = null
        _homePreviewState.update {
            it.copy(
                cubeState = CubeState.createSolved(),
                activeAnimation = null,
                moveHistory = emptyList()
            )
        }
    }

    private suspend fun animateHomeMoveInternal(
        move: CubeMove,
        durationMs: Long,
        epoch: Long = homeSessionEpoch,
        onCommitState: ((HomePreviewUiState) -> HomePreviewUiState)? = null
    ): Boolean {
        if (!kotlinx.coroutines.currentCoroutineContext().isActive || epoch != homeSessionEpoch) {
            return false
        }
        if (!shouldSkipAnimationLoop) {
            val frameMs = 16L
            val rawFrames = (durationMs / frameMs).toInt().coerceAtLeast(16)
            val totalFrames = ((rawFrames + 3) / 4) * 4
            for (frame in 0..totalFrames) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive || epoch != homeSessionEpoch) {
                    return false
                }
                val progress = frame.toFloat() / totalFrames.toFloat()
                val anim = ActiveLayerAnimation(move, progress)
                recordAuditFrame(anim)
                _homePreviewState.update {
                    it.copy(activeAnimation = anim)
                }
                if (frame < totalFrames) {
                    delay(frameMs)
                }
            }
        }
        if (!kotlinx.coroutines.currentCoroutineContext().isActive || epoch != homeSessionEpoch) {
            return false
        }
        _homePreviewState.update { current ->
            if (onCommitState != null) {
                onCommitState(current)
            } else {
                current.copy(
                    cubeState = current.cubeState.applyMove(move),
                    activeAnimation = null
                )
            }
        }
        return true
    }

    // =========================================================================
    // PREFERÊNCIAS GLOBAIS (VELOCIDADE, TEMA, DESTAQUE DE PEÇAS)
    // =========================================================================

    fun setAnimationSpeed(multiplier: Float) {
        viewModelScope.launch {
            progressRepository.setAnimationSpeed(multiplier)
        }
    }

    fun toggleDarkTheme() {
        val current = progressState.value.isDarkTheme
        viewModelScope.launch {
            progressRepository.setDarkTheme(!current)
        }
    }

    fun toggleHighlightTargetPieces() {
        val current = progressState.value.highlightTargetPieces
        viewModelScope.launch {
            progressRepository.setHighlightTargetPieces(!current)
        }
    }

    val activeDemoTargetEndIndexForAudit: Int?
        get() = activeDemoTargetEndIndex

    val activeDemoPhaseNumberForAudit: Int?
        get() = activeDemoPhaseNumber

    fun stopAllAnimations(preserveDemoPhaseConfig: Boolean = false) {
        lessonSessionEpoch++
        exploreSessionEpoch++
        homeSessionEpoch++
        notationSessionEpoch++
        pauseRequested = false
        val targetEnd = activeDemoTargetEndIndex
        val shouldKeepPhaseConfig = preserveDemoPhaseConfig &&
            targetEnd != null &&
            _lessonState.value.currentMoveIndex < targetEnd
        if (!shouldKeepPhaseConfig) {
            activeDemoTargetEndIndex = null
            activeDemoPhaseNumber = null
        }
        lessonStepQueue.clear()
        practiceMoveQueue.clear()
        exploreMoveQueue.clear()
        homeMoveQueue.clear()
        demoJob?.cancel()
        demoJob = null
        singleMoveJob?.cancel()
        singleMoveJob = null
        exploreAnimJob?.cancel()
        exploreAnimJob = null
        homeAnimJob?.cancel()
        homeAnimJob = null
        notationPreviewJob?.cancel()
        notationPreviewJob = null
        celebrationJob?.cancel()
        celebrationJob = null
        _lessonState.update {
            it.copy(
                isPlayingDemo = false,
                isPausePending = false,
                activeAnimation = null,
                executingMoveIndex = null,
                celebrationPulse = 0f
            )
        }
        _exploreState.update { it.copy(activeAnimation = null, celebrationPulse = 0f) }
        _homePreviewState.update { it.copy(activeAnimation = null) }
        _notationPreviewAnim.value = null
    }

    /**
     * Chamado quando o aplicativo entra em segundo plano (ON_PAUSE / ON_STOP).
     * Interrompe todas as animações em curso de maneira transacional, garantindo que:
     * - nenhuma corrotina continue emitindo quadros em background;
     * - nenhum movimento incompleto seja aplicado ao CubeState nem avance currentMoveIndex;
     * - a configuração da fase pedagógica em andamento (Fase 1, Fase 2 ou sequência completa)
     *   seja preservada para que o botão Reproduzir continue até o fim da fase correspondente;
     * - o estado matemático confirmado (CubeState, histórico e progresso DataStore) seja preservado
     *   integralmente para retomada previsível ao voltar para o primeiro plano.
     */
    fun onAppBackgrounded() {
        isAppInForeground = false
        stopAllAnimations(preserveDemoPhaseConfig = true)
    }

    /**
     * Chamado quando o aplicativo retorna ao primeiro plano (ON_RESUME).
     */
    fun onAppForegrounded() {
        isAppInForeground = true
    }

    override fun onCleared() {
        stopAllAnimations()
        clearAuditTelemetry()
        super.onCleared()
    }
}

