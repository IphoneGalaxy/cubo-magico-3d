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
    val isPlayingDemo: Boolean = false,
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

    val guidedMoveForHighlight: CubeMove?
        get() = when {
            activeAnimation != null -> activeAnimation.move
            mode == LearningMode.LEARN && currentMoveIndex in demoMoves.indices -> demoMoves[currentMoveIndex]
            mode == LearningMode.LEARN && currentMoveIndex == -1 && demoMoves.isNotEmpty() -> demoMoves.first()
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

    // Filas seguras para não perder movimentos repetidos durante animações
    private val practiceMoveQueue = ArrayDeque<CubeMove>()
    private val exploreMoveQueue = ArrayDeque<CubeMove>()
    private val homeMoveQueue = ArrayDeque<CubeMove>()

    private val isHeadlessTest: Boolean =
        android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    /**
     * Permite habilitar a execução completa quadro a quadro (0%..25%..50%..75%..100%) em testes de auditoria,
     * mantendo os testes de UI rápidos por padrão.
     */
    var forceMultiFrameAnimationInTest: Boolean = false

    /**
     * Histórico auditável de quadros intermediários emitidos pelo ViewModel antes de aplicar o movimento ao CubeState.
     */
    val recordedAnimationFramesForAudit = mutableListOf<ActiveLayerAnimation>()

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
            isPlayingDemo = false,
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
            notationPreviewJob?.cancel()
            _notationPreviewAnim.value = null
        }
    }

    fun resetNotationPreviewCube() {
        notationPreviewJob?.cancel()
        _notationPreviewAnim.value = null
        _notationPreviewCube.value = CubeState.createSolved()
        _notationCameraResetCounter.value = _notationCameraResetCounter.value + 1
    }

    fun triggerNotationPreview(move: CubeMove) {
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
                    if (!isActive) return@launch
                    val anim = ActiveLayerAnimation(move, i.toFloat() / steps)
                    recordedAnimationFramesForAudit.add(anim)
                    _notationPreviewAnim.value = anim
                    delay(frameMs)
                }
            }
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

    fun resetLessonCube() {
        stopAllAnimations()
        val current = _lessonState.value
        val freshCube = current.currentCase.createInitialState()
        _lessonState.update {
            it.copy(
                cubeState = freshCube,
                activeAnimation = null,
                currentMoveIndex = -1,
                isPlayingDemo = false,
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
        startDemoPlayback(restartIfCompleted = true)
    }

    fun pauseDemo() {
        demoJob?.cancel()
        singleMoveJob?.cancel()
        _lessonState.update { it.copy(isPlayingDemo = false, activeAnimation = null) }
    }

    fun repeatDemoSequence() {
        resetLessonCube()
        startDemoPlayback(restartIfCompleted = false)
    }

    /**
     * Executa uma fase pedagógica específica do caso atual (ex.: Fase 1 ou Fase 2 do Caso 2 da Etapa 4).
     * - Fase 1 (phaseNumber = 1): Inicia a partir da Linha Horizontal e executa os 6 primeiros movimentos (F U R U' R' F'),
     *   parando na configuração intermediária em formato de L.
     * - Fase 2 (phaseNumber = 2): Se o cubo já estiver na configuração intermediária de L (ou em andamento na Fase 2),
     *   executa os 6 movimentos seguintes (F U R U' R' F') SEM restaurar ou reinicializar o cubo até formar a Cruz Amarela.
     */
    fun playDemoPhase(phaseNumber: Int) {
        demoJob?.cancel()
        singleMoveJob?.cancel()

        val current = _lessonState.value
        val phases = current.currentCase.pedagogicalPhases
        val targetPhase = phases.firstOrNull { it.phaseNumber == phaseNumber } ?: return
        val moves = current.demoMoves
        if (moves.isEmpty()) return

        if (phaseNumber == 1) {
            val freshCube = current.currentCase.createInitialState()
            _lessonState.update {
                it.copy(
                    cubeState = freshCube,
                    activeAnimation = null,
                    currentMoveIndex = -1,
                    isPlayingDemo = true,
                    isDemoCompleted = false,
                    celebrationPulse = 0f,
                    feedbackMessage = targetPhase.executionBannerText
                )
            }
        } else {
            val expectedStartIdx = targetPhase.startMoveIndex - 1
            val canContinueWithoutReset =
                current.currentMoveIndex in expectedStartIdx until targetPhase.endMoveIndex
            if (!canContinueWithoutReset) {
                val stateAtPhaseStart = current.currentCase
                    .createInitialState()
                    .applyMoves(moves.take(targetPhase.startMoveIndex))
                _lessonState.update {
                    it.copy(
                        cubeState = stateAtPhaseStart,
                        activeAnimation = null,
                        currentMoveIndex = expectedStartIdx,
                        isPlayingDemo = true,
                        isDemoCompleted = false,
                        celebrationPulse = 0f,
                        feedbackMessage = targetPhase.executionBannerText
                    )
                }
            } else {
                _lessonState.update {
                    it.copy(
                        activeAnimation = null,
                        isPlayingDemo = true,
                        isDemoCompleted = false,
                        feedbackMessage = targetPhase.executionBannerText
                    )
                }
            }
        }

        demoJob = viewModelScope.launch {
            while (isActive) {
                val snap = _lessonState.value
                val nextIndex = snap.currentMoveIndex + 1
                if (nextIndex > targetPhase.endMoveIndex || nextIndex > moves.lastIndex) {
                    val isFinalMove = targetPhase.endMoveIndex >= moves.lastIndex
                    _lessonState.update {
                        it.copy(
                            isPlayingDemo = false,
                            activeAnimation = null,
                            isDemoCompleted = isFinalMove,
                            feedbackMessage = if (isFinalMove) {
                                "Demonstração concluída."
                            } else {
                                targetPhase.completionSummaryText
                            }
                        )
                    }
                    break
                }

                val move = moves[nextIndex]
                _lessonState.update {
                    it.copy(
                        currentMoveIndex = nextIndex,
                        selectedNotationMoveInfo = move,
                        feedbackMessage = "Movimento ${nextIndex + 1}/${moves.size}: ${move.notation}"
                    )
                }

                animateLessonMoveInternal(move)

                if (nextIndex == targetPhase.endMoveIndex) {
                    val isFinalMove = targetPhase.endMoveIndex >= moves.lastIndex
                    _lessonState.update {
                        it.copy(
                            isPlayingDemo = false,
                            activeAnimation = null,
                            isDemoCompleted = isFinalMove,
                            feedbackMessage = if (isFinalMove) {
                                "Demonstração concluída."
                            } else {
                                targetPhase.completionSummaryText
                            }
                        )
                    }
                    break
                }

                val speed = progressState.value.animationSpeedMultiplier.coerceIn(0.4f, 2.0f)
                val pauseMs = (300L / speed).toLong().coerceAtLeast(110L)
                delay(pauseMs)
            }
        }
    }

    private fun startDemoPlayback(restartIfCompleted: Boolean) {
        demoJob?.cancel()
        singleMoveJob?.cancel()

        val current = _lessonState.value
        val moves = current.demoMoves
        val phases = current.currentCase.pedagogicalPhases
        if (moves.isEmpty()) return

        if (restartIfCompleted && (current.isDemoCompleted || current.currentMoveIndex >= moves.lastIndex)) {
            val freshCube = current.currentCase.createInitialState()
            _lessonState.update {
                it.copy(
                    cubeState = freshCube,
                    currentMoveIndex = -1,
                    isDemoCompleted = false,
                    celebrationPulse = 0f,
                    feedbackMessage = null
                )
            }
        }

        _lessonState.update { it.copy(isPlayingDemo = true, feedbackMessage = null) }

        demoJob = viewModelScope.launch {
            while (isActive) {
                val snap = _lessonState.value
                val nextIndex = snap.currentMoveIndex + 1
                if (nextIndex > moves.lastIndex) {
                    _lessonState.update {
                        it.copy(
                            isPlayingDemo = false,
                            activeAnimation = null,
                            isDemoCompleted = true,
                            feedbackMessage = "Demonstração concluída."
                        )
                    }
                    break
                }

                val move = moves[nextIndex]
                _lessonState.update {
                    it.copy(
                        currentMoveIndex = nextIndex,
                        selectedNotationMoveInfo = move,
                        feedbackMessage = "Movimento ${nextIndex + 1}/${moves.size}: ${move.notation}"
                    )
                }

                animateLessonMoveInternal(move)

                val completedPhase = phases.firstOrNull { it.endMoveIndex == nextIndex && nextIndex < moves.lastIndex }
                if (completedPhase != null) {
                    _lessonState.update {
                        it.copy(feedbackMessage = completedPhase.completionSummaryText)
                    }
                    if (!shouldSkipAnimationLoop) {
                        delay(420L)
                    }
                } else {
                    val speed = progressState.value.animationSpeedMultiplier.coerceIn(0.4f, 2.0f)
                    val pauseMs = (300L / speed).toLong().coerceAtLeast(110L)
                    delay(pauseMs)
                }
            }
        }
    }

    /**
     * Avança exatamente um movimento na demonstração.
     */
    fun stepDemoForward() {
        pauseDemo()
        val snap = _lessonState.value
        val moves = snap.demoMoves
        val phases = snap.currentCase.pedagogicalPhases
        val nextIndex = snap.currentMoveIndex + 1
        if (nextIndex > moves.lastIndex) return

        val move = moves[nextIndex]
        singleMoveJob = viewModelScope.launch {
            _lessonState.update {
                it.copy(
                    currentMoveIndex = nextIndex,
                    selectedNotationMoveInfo = move,
                    feedbackMessage = "Movimento ${nextIndex + 1}/${moves.size}: ${move.notation}"
                )
            }
            animateLessonMoveInternal(move)
            val completedPhase = phases.firstOrNull { it.endMoveIndex == nextIndex && nextIndex < moves.lastIndex }
            if (nextIndex == moves.lastIndex) {
                _lessonState.update {
                    it.copy(
                        isDemoCompleted = true,
                        feedbackMessage = "Demonstração concluída."
                    )
                }
            } else if (completedPhase != null) {
                _lessonState.update {
                    it.copy(feedbackMessage = completedPhase.completionSummaryText)
                }
            }
        }
    }

    /**
     * Retrocede exatamente um movimento na demonstração aplicando o movimento inverso.
     */
    fun stepDemoBackward() {
        pauseDemo()
        val snap = _lessonState.value
        val moves = snap.demoMoves
        val phases = snap.currentCase.pedagogicalPhases
        val currentIdx = snap.currentMoveIndex
        if (currentIdx < 0 || currentIdx > moves.lastIndex) return

        val moveToUndo = moves[currentIdx]
        val inverseMove = moveToUndo.inverse()
        val targetIdx = currentIdx - 1

        singleMoveJob = viewModelScope.launch {
            _lessonState.update {
                it.copy(
                    isDemoCompleted = false,
                    selectedNotationMoveInfo = moveToUndo,
                    feedbackMessage = "Passo revertido: ${moveToUndo.notation}"
                )
            }
            animateLessonMoveInternal(inverseMove)
            val phaseMilestone = phases.firstOrNull { it.endMoveIndex == targetIdx && targetIdx < moves.lastIndex }
            _lessonState.update {
                it.copy(
                    currentMoveIndex = targetIdx,
                    feedbackMessage = phaseMilestone?.completionSummaryText ?: "Passo revertido: ${moveToUndo.notation}"
                )
            }
        }
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
        singleMoveJob = viewModelScope.launch {
            while (practiceMoveQueue.isNotEmpty() && isActive) {
                val nextMove = practiceMoveQueue.removeFirst()
                val manualDuration = if (kotlin.math.abs(nextMove.glAngleDegrees) > 100f) {
                    MANUAL_MOVE_180_DURATION_MS
                } else {
                    MANUAL_MOVE_90_DURATION_MS
                }
                // Inicia a rotação 3D imediatamente sem recomposição prévia dos cartões de controle
                animateLessonMoveInternal(
                    move = nextMove,
                    useDidacticSpeed = false,
                    customDurationMs = manualDuration
                )

                val updatedCube = _lessonState.value.cubeState
                val stepNum = _lessonState.value.stepNumber
                val goalReached = updatedCube.isStepGoalAchieved(stepNum)

                _lessonState.update {
                    it.copy(
                        selectedNotationMoveInfo = nextMove,
                        practiceMoveHistory = it.practiceMoveHistory + nextMove,
                        isPracticeGoalAchieved = goalReached,
                        feedbackMessage = if (goalReached) {
                            "Excelente! Você completou esta etapa."
                        } else {
                            "Movimento executado: ${nextMove.notation}"
                        }
                    )
                }

                if (goalReached) {
                    practiceMoveQueue.clear()
                    val step = _lessonState.value.currentStep
                    val caseId = _lessonState.value.currentCase.id
                    progressRepository.markCaseCompleted(
                        stepNumber = stepNum,
                        caseId = caseId,
                        allStepCaseIds = step.cases.map { c -> c.id }
                    )
                    progressRepository.markStepCompleted(stepNum)
                    triggerLessonCelebration()
                    break
                }
            }
        }
    }

    fun undoPracticeMove() {
        val snap = _lessonState.value
        if (snap.mode != LearningMode.PRACTICE || snap.activeAnimation != null) return
        val lastMove = snap.practiceMoveHistory.lastOrNull() ?: return

        practiceMoveQueue.clear()
        singleMoveJob?.cancel()
        singleMoveJob = viewModelScope.launch {
            animateLessonMoveInternal(
                move = lastMove.inverse(),
                useDidacticSpeed = false,
                customDurationMs = MANUAL_UNDO_DURATION_MS
            )
            val updatedCube = _lessonState.value.cubeState
            val goalReached = updatedCube.isStepGoalAchieved(snap.stepNumber)
            _lessonState.update {
                it.copy(
                    practiceMoveHistory = it.practiceMoveHistory.dropLast(1),
                    isPracticeGoalAchieved = goalReached,
                    feedbackMessage = "Movimento desfeito"
                )
            }
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

    private suspend fun animateLessonMoveInternal(
        move: CubeMove,
        useDidacticSpeed: Boolean = true,
        customDurationMs: Long? = null
    ) {
        if (shouldSkipAnimationLoop) {
            _lessonState.update {
                it.copy(
                    cubeState = it.cubeState.applyMove(move),
                    activeAnimation = null
                )
            }
            return
        }

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
            val progress = frame.toFloat() / totalFrames.toFloat()
            val anim = ActiveLayerAnimation(move, progress)
            recordedAnimationFramesForAudit.add(anim)
            _lessonState.update {
                it.copy(activeAnimation = anim)
            }
            if (frame < totalFrames) {
                delay(frameMs)
            }
        }

        _lessonState.update {
            it.copy(
                cubeState = it.cubeState.applyMove(move),
                activeAnimation = null
            )
        }
    }

    private fun triggerLessonCelebration() {
        celebrationJob?.cancel()
        if (isHeadlessTest) {
            _lessonState.update { it.copy(celebrationPulse = 0f) }
            return
        }
        celebrationJob = viewModelScope.launch {
            val frames = 28
            for (i in 1..frames) {
                val t = i.toFloat() / frames
                val pulse = kotlin.math.sin(t * Math.PI * 2).toFloat().let { kotlin.math.abs(it) }
                _lessonState.update { it.copy(celebrationPulse = pulse) }
                delay(20L)
            }
            _lessonState.update { it.copy(celebrationPulse = 0f) }
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
        exploreAnimJob = viewModelScope.launch {
            while (exploreMoveQueue.isNotEmpty() && isActive) {
                val nextMove = exploreMoveQueue.removeFirst()
                val duration = if (kotlin.math.abs(nextMove.glAngleDegrees) > 100f) {
                    MANUAL_MOVE_180_DURATION_MS
                } else {
                    MANUAL_MOVE_90_DURATION_MS
                }
                animateExploreMoveInternal(nextMove, durationMs = duration)
                _exploreState.update {
                    it.copy(
                        moveHistory = it.moveHistory + nextMove,
                        feedbackMessage = "Movimento executado: ${nextMove.notation}"
                    )
                }
            }
        }
    }

    fun undoExploreMove() {
        val snap = _exploreState.value
        if (snap.activeAnimation != null) return
        val lastMove = snap.moveHistory.lastOrNull() ?: return

        exploreMoveQueue.clear()
        exploreAnimJob?.cancel()
        exploreAnimJob = viewModelScope.launch {
            animateExploreMoveInternal(lastMove.inverse(), durationMs = MANUAL_UNDO_DURATION_MS)
            _exploreState.update {
                it.copy(
                    moveHistory = it.moveHistory.dropLast(1),
                    feedbackMessage = "Movimento desfeito"
                )
            }
        }
    }

    fun scrambleExploreCube() {
        if (_exploreState.value.activeAnimation != null) return
        exploreMoveQueue.clear()
        exploreAnimJob?.cancel()
        val scramble = CubeState.generateScramble(length = 16)
        exploreAnimJob = viewModelScope.launch {
            _exploreState.update { it.copy(feedbackMessage = "Embaralhando cubo 3×3...") }
            for (move in scramble) {
                if (!isActive) break
                animateExploreMoveInternal(move, durationMs = 110L)
                _exploreState.update {
                    it.copy(moveHistory = it.moveHistory + move)
                }
            }
            _exploreState.update {
                it.copy(
                    lastScramble = scramble,
                    feedbackMessage = "Cubo embaralhado (${scramble.size} movimentos)"
                )
            }
        }
    }

    fun resetExploreCubeSolved() {
        exploreMoveQueue.clear()
        exploreAnimJob?.cancel()
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

    private suspend fun animateExploreMoveInternal(move: CubeMove, durationMs: Long) {
        if (shouldSkipAnimationLoop) {
            _exploreState.update {
                it.copy(
                    cubeState = it.cubeState.applyMove(move),
                    activeAnimation = null
                )
            }
            return
        }
        val frameMs = 16L
        val rawFrames = (durationMs / frameMs).toInt().coerceAtLeast(8)
        val totalFrames = ((rawFrames + 3) / 4) * 4
        for (frame in 0..totalFrames) {
            val progress = frame.toFloat() / totalFrames.toFloat()
            val anim = ActiveLayerAnimation(move, progress)
            recordedAnimationFramesForAudit.add(anim)
            _exploreState.update {
                it.copy(activeAnimation = anim)
            }
            if (frame < totalFrames) {
                delay(frameMs)
            }
        }
        _exploreState.update {
            it.copy(
                cubeState = it.cubeState.applyMove(move),
                activeAnimation = null
            )
        }
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
        homeAnimJob = viewModelScope.launch {
            while (homeMoveQueue.isNotEmpty() && isActive) {
                val nextMove = homeMoveQueue.removeFirst()
                val duration = if (kotlin.math.abs(nextMove.glAngleDegrees) > 100f) {
                    MANUAL_MOVE_180_DURATION_MS
                } else {
                    MANUAL_MOVE_90_DURATION_MS
                }
                animateHomeMoveInternal(nextMove, durationMs = duration)
                _homePreviewState.update {
                    it.copy(moveHistory = it.moveHistory + nextMove)
                }
            }
        }
    }

    fun resetHomePreviewCube() {
        homeMoveQueue.clear()
        homeAnimJob?.cancel()
        _homePreviewState.update {
            it.copy(
                cubeState = CubeState.createSolved(),
                activeAnimation = null,
                moveHistory = emptyList()
            )
        }
    }

    private suspend fun animateHomeMoveInternal(move: CubeMove, durationMs: Long) {
        if (shouldSkipAnimationLoop) {
            _homePreviewState.update {
                it.copy(
                    cubeState = it.cubeState.applyMove(move),
                    activeAnimation = null
                )
            }
            return
        }
        val frameMs = 16L
        val rawFrames = (durationMs / frameMs).toInt().coerceAtLeast(16)
        val totalFrames = ((rawFrames + 3) / 4) * 4
        for (frame in 0..totalFrames) {
            val progress = frame.toFloat() / totalFrames.toFloat()
            val anim = ActiveLayerAnimation(move, progress)
            recordedAnimationFramesForAudit.add(anim)
            _homePreviewState.update {
                it.copy(activeAnimation = anim)
            }
            if (frame < totalFrames) {
                delay(frameMs)
            }
        }
        _homePreviewState.update {
            it.copy(
                cubeState = it.cubeState.applyMove(move),
                activeAnimation = null
            )
        }
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

    private fun stopAllAnimations() {
        practiceMoveQueue.clear()
        exploreMoveQueue.clear()
        homeMoveQueue.clear()
        demoJob?.cancel()
        singleMoveJob?.cancel()
        exploreAnimJob?.cancel()
        homeAnimJob?.cancel()
        celebrationJob?.cancel()
        _lessonState.update { it.copy(isPlayingDemo = false, activeAnimation = null, celebrationPulse = 0f) }
        _exploreState.update { it.copy(activeAnimation = null, celebrationPulse = 0f) }
        _homePreviewState.update { it.copy(activeAnimation = null) }
    }
}
