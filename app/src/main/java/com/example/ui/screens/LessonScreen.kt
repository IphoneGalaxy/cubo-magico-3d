package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.curriculum.CurriculumRepository
import com.example.data.UserProgressState
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.ActiveLayerAnimation
import com.example.renderer.InteractiveCube3DView
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StickerGreen
import com.example.ui.theme.StickerOrange
import com.example.ui.theme.StickerYellow
import com.example.viewmodel.ExplanationPanelState
import com.example.viewmodel.LearningMode
import com.example.viewmodel.LessonUiState

/**
 * Rastreador leve de recomposições para auditoria de desempenho entre o viewport 3D
 * e os cartões estáticos de controle durante animações de camadas.
 */
object LessonRecompositionTracker {
    var viewportRecompositions: Int = 0
    var controlsRecompositions: Int = 0

    fun reset() {
        viewportRecompositions = 0
        controlsRecompositions = 0
    }
}

private val PRACTICE_FAMILIES = listOf("R", "L", "U", "D", "F", "B", "M", "E", "S")

private val ALL_PRACTICE_QUICK_MOVES = listOf(
    CubeMove.R, CubeMove.R_PRIME, CubeMove.R2,
    CubeMove.L, CubeMove.L_PRIME, CubeMove.L2,
    CubeMove.U, CubeMove.U_PRIME, CubeMove.U2,
    CubeMove.D, CubeMove.D_PRIME, CubeMove.D2,
    CubeMove.F, CubeMove.F_PRIME, CubeMove.F2,
    CubeMove.B, CubeMove.B_PRIME, CubeMove.B2,
    CubeMove.M, CubeMove.M_PRIME, CubeMove.M2,
    CubeMove.E, CubeMove.E_PRIME, CubeMove.E2,
    CubeMove.S, CubeMove.S_PRIME, CubeMove.S2
)

/**
 * Tela de Aula estruturada como aplicativo 3D nativo de alta fluidez:
 * - Cabeçalho adaptativo sem corte de título da etapa.
 * - Seletor Aprender / Praticar sempre visível.
 * - Área principal do Cubo 3D ocupando entre 50% e 60% da altura disponível no Modo Praticar.
 * - Seletor de casos em duas linhas ("Caso X de Y" + título descritivo completo) e chips compactos ("Caso 1", "Caso 2"...).
 * - Componente pedagógico em dois níveis: resumo compacto compreensível + modal detalhado rolável ("Entender o caso").
 * - Painel inferior expansível em 3 estados (Recolhido, Resumo, Completo) sem reiniciar o cubo.
 * - Isolamento estrito de parâmetros para que quadros intermediários de animação 3D (activeAnimation)
 *   atualizem apenas o viewport 3D sem recompor os cartões de controle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    lessonState: LessonUiState,
    progressState: UserProgressState,
    onSelectStep: (Int) -> Unit,
    onSelectCase: (Int) -> Unit,
    onSelectMode: (LearningMode) -> Unit,
    onPlayPauseDemo: () -> Unit,
    onStepForwardDemo: () -> Unit,
    onStepBackwardDemo: () -> Unit,
    onRepeatSequenceDemo: () -> Unit,
    onResetLessonCube: () -> Unit,
    onExecutePracticeMove: (CubeMove) -> Unit,
    onUndoPracticeMove: () -> Unit,
    onMarkStepCompleted: () -> Unit,
    onToggleHighlightPieces: () -> Unit,
    onCycleSpeed: (Float) -> Unit,
    onOpenNotationGuide: () -> Unit,
    onBackToCourse: () -> Unit,
    onSetExplanationPanelState: (ExplanationPanelState) -> Unit = {},
    onSelectPracticeFamily: (String) -> Unit = {},
    onTogglePracticeMovesExpanded: () -> Unit = {},
    onPlayDemoPhase: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = CuboMestreTheme.strings
    val stepNumber = lessonState.stepNumber
    val caseIndex = lessonState.caseIndex
    val step = remember(stepNumber) { CurriculumRepository.getStep(stepNumber) }
    val isStepCompleted = progressState.completedSteps.contains(stepNumber)
    val canUndoPractice = lessonState.practiceMoveHistory.isNotEmpty()

    var showCaseDetailModal by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(stepNumber, caseIndex) {
        showCaseDetailModal = false
    }

    BackHandler(onBack = {
        if (showCaseDetailModal) {
            showCaseDetailModal = false
        } else {
            onBackToCourse()
        }
    })

    // Callbacks estáveis para permitir que o Compose pule 100% da recomposição dos cartões
    // estáticos durante os quadros intermediários de activeAnimation (0%..100%).
    val currentOnSelectStep by rememberUpdatedState(onSelectStep)
    val currentOnSelectCase by rememberUpdatedState(onSelectCase)
    val currentOnSelectMode by rememberUpdatedState(onSelectMode)
    val currentOnPlayPauseDemo by rememberUpdatedState(onPlayPauseDemo)
    val currentOnStepForwardDemo by rememberUpdatedState(onStepForwardDemo)
    val currentOnStepBackwardDemo by rememberUpdatedState(onStepBackwardDemo)
    val currentOnRepeatSequenceDemo by rememberUpdatedState(onRepeatSequenceDemo)
    val currentOnResetLessonCube by rememberUpdatedState(onResetLessonCube)
    val currentOnExecutePracticeMove by rememberUpdatedState(onExecutePracticeMove)
    val currentOnUndoPracticeMove by rememberUpdatedState(onUndoPracticeMove)
    val currentOnMarkStepCompleted by rememberUpdatedState(onMarkStepCompleted)
    val currentOnToggleHighlightPieces by rememberUpdatedState(onToggleHighlightPieces)
    val currentOnCycleSpeed by rememberUpdatedState(onCycleSpeed)
    val currentOnOpenNotationGuide by rememberUpdatedState(onOpenNotationGuide)
    val currentOnBackToCourse by rememberUpdatedState(onBackToCourse)
    val currentOnSetExplanationPanelState by rememberUpdatedState(onSetExplanationPanelState)
    val currentOnSelectPracticeFamily by rememberUpdatedState(onSelectPracticeFamily)
    val currentOnTogglePracticeMovesExpanded by rememberUpdatedState(onTogglePracticeMovesExpanded)
    val currentOnPlayDemoPhase by rememberUpdatedState(onPlayDemoPhase)

    val stableSelectStep = remember { { s: Int -> currentOnSelectStep(s) } }
    val stableSelectCase = remember { { c: Int -> currentOnSelectCase(c) } }
    val stableSelectMode = remember { { m: LearningMode -> currentOnSelectMode(m) } }
    val stablePlayPause = remember { { currentOnPlayPauseDemo() } }
    val stableStepForward = remember { { currentOnStepForwardDemo() } }
    val stableStepBackward = remember { { currentOnStepBackwardDemo() } }
    val stableRepeatDemo = remember { { currentOnRepeatSequenceDemo() } }
    val stableResetCube = remember { { currentOnResetLessonCube() } }
    val stableExecuteMove = remember { { m: CubeMove -> currentOnExecutePracticeMove(m) } }
    val stableUndoMove = remember { { currentOnUndoPracticeMove() } }
    val stableMarkCompleted = remember { { currentOnMarkStepCompleted() } }
    val stableToggleHighlight = remember { { currentOnToggleHighlightPieces() } }
    val stableCycleSpeed = remember { { sp: Float -> currentOnCycleSpeed(sp) } }
    val stableOpenNotation = remember { { currentOnOpenNotationGuide() } }
    val stableBackToCourse = remember { { currentOnBackToCourse() } }
    val stableSetPanelState = remember { { st: ExplanationPanelState -> currentOnSetExplanationPanelState(st) } }
    val stableSelectFamily = remember { { f: String -> currentOnSelectPracticeFamily(f) } }
    val stableToggleExpanded = remember { { currentOnTogglePracticeMovesExpanded() } }
    val stablePlayPhase = remember { { p: Int -> currentOnPlayDemoPhase(p) } }
    val stableOpenCaseDetail = remember { { showCaseDetailModal = true } }
    val stableCloseCaseDetail = remember { { showCaseDetailModal = false } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("lesson_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isLandscape = maxWidth > maxHeight && maxHeight < 540.dp

        if (isLandscape) {
            // Layout adaptativo em paisagem / tablets horizontais: Cubo 3D à esquerda e controles fixos à direita
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                LessonCompactHeader(
                    stepNumber = stepNumber,
                    shortTitle = step.localizedTitle(strings.language),
                    speedMultiplier = progressState.animationSpeedMultiplier,
                    highlightTargetPieces = progressState.highlightTargetPieces,
                    onBackToCourse = stableBackToCourse,
                    onCycleSpeed = stableCycleSpeed,
                    onToggleHighlightPieces = stableToggleHighlight,
                    onOpenNotationGuide = stableOpenNotation,
                    onOpenCaseDetail = stableOpenCaseDetail
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Coluna Esquerda: Cubo 3D Protagonista
                    Box(
                        modifier = Modifier
                            .weight(0.50f)
                            .fillMaxHeight()
                    ) {
                        LessonCubeViewportBox(
                            stepNumber = stepNumber,
                            caseIndex = caseIndex,
                            currentMoveIndex = lessonState.currentMoveIndex,
                            mode = lessonState.mode,
                            cubeState = lessonState.cubeState,
                            activeAnimation = lessonState.activeAnimation,
                            guidedMove = lessonState.guidedMoveForHighlight,
                            isDemoCompleted = lessonState.isDemoCompleted,
                            isPracticeGoalAchieved = lessonState.isPracticeGoalAchieved,
                            celebrationPulse = lessonState.celebrationPulse,
                            cameraResetCounter = lessonState.cameraResetCounter,
                            feedbackMessage = lessonState.feedbackMessage,
                            highlightTargetPieces = progressState.highlightTargetPieces,
                            isDarkTheme = progressState.isDarkTheme,
                            onExecutePracticeMove = stableExecuteMove,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Coluna Direita: Modos, Casos, Controles, Painel Expansível e Rodapé de Etapas
                    Column(
                        modifier = Modifier
                            .weight(0.50f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LessonModeSelectorBar(
                                currentMode = lessonState.mode,
                                onSelectMode = stableSelectMode
                            )

                            LessonCaseAndAlgorithmCard(
                                stepNumber = stepNumber,
                                caseIndex = caseIndex,
                                mode = lessonState.mode,
                                currentMoveIndex = lessonState.currentMoveIndex,
                                executingMoveIndex = lessonState.executingMoveIndex,
                                onSelectCase = stableSelectCase,
                                onOpenCaseDetail = stableOpenCaseDetail,
                                onPlayDemoPhase = stablePlayPhase,
                                onRepeatSequenceDemo = stableRepeatDemo,
                                isCompactHeight = true
                            )

                            if (lessonState.mode == LearningMode.LEARN) {
                                LearnPlaybackControlsCard(
                                    stepNumber = stepNumber,
                                    caseIndex = caseIndex,
                                    currentMoveIndex = lessonState.currentMoveIndex,
                                    isPlayingDemo = lessonState.isPlayingDemo,
                                    onStepBackward = stableStepBackward,
                                    onPlayPause = stablePlayPause,
                                    onStepForward = stableStepForward,
                                    onRepeatSequence = stableRepeatDemo,
                                    onResetDemo = stableResetCube
                                )
                            } else {
                                PracticeInteractiveCard(
                                    selectedFamily = lessonState.selectedPracticeFamily,
                                    isMovesExpanded = lessonState.isPracticeMovesExpanded,
                                    canUndo = canUndoPractice,
                                    onSelectFamily = stableSelectFamily,
                                    onToggleExpanded = stableToggleExpanded,
                                    onExecuteMove = stableExecuteMove,
                                    onUndo = stableUndoMove,
                                    onReset = stableResetCube
                                )
                            }

                            ExpandableExplanationPanel(
                                stepNumber = stepNumber,
                                caseIndex = caseIndex,
                                currentMoveIndex = lessonState.visualMoveIndex,
                                selectedNotationMove = lessonState.selectedNotationMoveInfo,
                                panelState = lessonState.explanationPanelState,
                                onChangeState = stableSetPanelState,
                                onOpenCaseDetail = stableOpenCaseDetail,
                                hasOuterVerticalScroll = true
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        StepNavigationFooterBar(
                            stepNumber = stepNumber,
                            isStepCompleted = isStepCompleted,
                            onSelectStep = stableSelectStep,
                            onMarkStepCompleted = stableMarkCompleted
                        )
                    }
                }
            }
        } else {
            // Layout Retrato Nativo Fixo: Cubo 3D protagonista ocupando 50% a 60% da altura no Modo Praticar
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // 1. Cabeçalho Compacto e Adaptativo
                LessonCompactHeader(
                    stepNumber = stepNumber,
                    shortTitle = step.localizedTitle(strings.language),
                    speedMultiplier = progressState.animationSpeedMultiplier,
                    highlightTargetPieces = progressState.highlightTargetPieces,
                    onBackToCourse = stableBackToCourse,
                    onCycleSpeed = stableCycleSpeed,
                    onToggleHighlightPieces = stableToggleHighlight,
                    onOpenNotationGuide = stableOpenNotation,
                    onOpenCaseDetail = stableOpenCaseDetail
                )

                // 2. Seletor Aprender / Praticar Sempre Visível
                LessonModeSelectorBar(
                    currentMode = lessonState.mode,
                    onSelectMode = stableSelectMode
                )

                // 3. Área Central Protagonista do Cubo 3D (50% a 60% da altura útil no Modo Praticar)
                LessonCubeViewportBox(
                    stepNumber = stepNumber,
                    caseIndex = caseIndex,
                    currentMoveIndex = lessonState.currentMoveIndex,
                    mode = lessonState.mode,
                    cubeState = lessonState.cubeState,
                    activeAnimation = lessonState.activeAnimation,
                    guidedMove = lessonState.guidedMoveForHighlight,
                    isDemoCompleted = lessonState.isDemoCompleted,
                    isPracticeGoalAchieved = lessonState.isPracticeGoalAchieved,
                    celebrationPulse = lessonState.celebrationPulse,
                    cameraResetCounter = lessonState.cameraResetCounter,
                    feedbackMessage = lessonState.feedbackMessage,
                    highlightTargetPieces = progressState.highlightTargetPieces,
                    isDarkTheme = progressState.isDarkTheme,
                    onExecutePracticeMove = stableExecuteMove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // 4. Seletor de Caso Atual e Resumo/Algoritmo
                LessonCaseAndAlgorithmCard(
                    stepNumber = stepNumber,
                    caseIndex = caseIndex,
                    mode = lessonState.mode,
                    currentMoveIndex = lessonState.currentMoveIndex,
                    executingMoveIndex = lessonState.executingMoveIndex,
                    onSelectCase = stableSelectCase,
                    onOpenCaseDetail = stableOpenCaseDetail,
                    onPlayDemoPhase = stablePlayPhase,
                    onRepeatSequenceDemo = stableRepeatDemo
                )

                // 5. Barra de Controles Sempre Acessível (Reprodução no Modo Aprender | Famílias Compactas no Modo Praticar)
                if (lessonState.mode == LearningMode.LEARN) {
                    LearnPlaybackControlsCard(
                        stepNumber = stepNumber,
                        caseIndex = caseIndex,
                        currentMoveIndex = lessonState.currentMoveIndex,
                        isPlayingDemo = lessonState.isPlayingDemo,
                        onStepBackward = stableStepBackward,
                        onPlayPause = stablePlayPause,
                        onStepForward = stableStepForward,
                        onRepeatSequence = stableRepeatDemo,
                        onResetDemo = stableResetCube
                    )
                } else {
                    PracticeInteractiveCard(
                        selectedFamily = lessonState.selectedPracticeFamily,
                        isMovesExpanded = lessonState.isPracticeMovesExpanded,
                        canUndo = canUndoPractice,
                        onSelectFamily = stableSelectFamily,
                        onToggleExpanded = stableToggleExpanded,
                        onExecuteMove = stableExecuteMove,
                        onUndo = stableUndoMove,
                        onReset = stableResetCube
                    )
                }

                // 6. Painel Inferior Expansível para Explicações Detalhadas (Recolhido | Resumo | Completo)
                ExpandableExplanationPanel(
                    stepNumber = stepNumber,
                    caseIndex = caseIndex,
                    currentMoveIndex = lessonState.visualMoveIndex,
                    selectedNotationMove = lessonState.selectedNotationMoveInfo,
                    panelState = lessonState.explanationPanelState,
                    onChangeState = stableSetPanelState,
                    onOpenCaseDetail = stableOpenCaseDetail
                )

                // 7. Barra Inferior Compacta de Navegação entre Etapas: Anterior | Concluir | Próxima
                StepNavigationFooterBar(
                    stepNumber = stepNumber,
                    isStepCompleted = isStepCompleted,
                    onSelectStep = stableSelectStep,
                    onMarkStepCompleted = stableMarkCompleted
                )
            }
        }

        // Modal de Explicação Pedagógica Completa do Caso (Modo Detalhado de 2º nível):
        // Abre sem desmontar nem reiniciar o estado do cubo 3D ou da animação em andamento.
        if (showCaseDetailModal) {
            CaseDetailedExplanationModal(
                stepNumber = stepNumber,
                caseIndex = caseIndex,
                currentMoveIndex = lessonState.visualMoveIndex,
                onDismiss = stableCloseCaseDetail
            )
        }
    }
}

@Composable
private fun LessonCompactHeader(
    stepNumber: Int,
    shortTitle: String,
    speedMultiplier: Float,
    highlightTargetPieces: Boolean,
    onBackToCourse: () -> Unit,
    onCycleSpeed: (Float) -> Unit,
    onToggleHighlightPieces: () -> Unit,
    onOpenNotationGuide: () -> Unit,
    onOpenCaseDetail: () -> Unit = {}
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Quando a tela é estreita (< 460dp, ou seja, qualquer celular em retrato),
        // posiciona os controles superiores na Linha 1 e dedica 100% da largura da Linha 2 ao título completo da etapa,
        // eliminando qualquer corte ou reticências ("Fazer a Cruz Bran...") mesmo em 320dp e fonte 1.5x.
        val useDedicatedTitleRow = maxWidth < 460.dp

        val speedLabel = when {
            speedMultiplier < 0.75f -> "0.5x"
            speedMultiplier > 1.25f -> "1.5x"
            else -> "1.0x"
        }

        if (useDedicatedTitleRow) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onBackToCourse,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("lesson_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.lessonBackToCourseDesc,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            color = themeColors.turquoiseAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$stepNumber/7",
                                style = MaterialTheme.typography.labelMedium,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable {
                                    val nextSpeed = when {
                                        speedMultiplier < 0.75f -> 1.0f
                                        speedMultiplier < 1.25f -> 1.5f
                                        else -> 0.5f
                                    }
                                    onCycleSpeed(nextSpeed)
                                }
                                .testTag("speed_toggle_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Speed,
                                    contentDescription = strings.lessonSpeedDesc,
                                    tint = themeColors.turquoiseAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = speedLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(
                            onClick = onToggleHighlightPieces,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (highlightTargetPieces) {
                                        themeColors.turquoiseAccent.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .testTag("highlight_pieces_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = strings.lessonHighlightPiecesDesc,
                                tint = if (highlightTargetPieces) {
                                    themeColors.turquoiseAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenNotationGuide,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("lesson_notation_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = strings.lessonNotationGuideDesc,
                                tint = themeColors.violetAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Text(
                    text = shortTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (fontScale >= 1.25f) 3 else 2,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCaseDetail() }
                        .testTag("lesson_step_title")
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackToCourse,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("lesson_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.lessonBackToCourseDesc,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Surface(
                        color = themeColors.turquoiseAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$stepNumber/7",
                            style = MaterialTheme.typography.labelMedium,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = shortTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenCaseDetail() }
                            .testTag("lesson_step_title")
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                val nextSpeed = when {
                                    speedMultiplier < 0.75f -> 1.0f
                                    speedMultiplier < 1.25f -> 1.5f
                                    else -> 0.5f
                                }
                                onCycleSpeed(nextSpeed)
                            }
                            .testTag("speed_toggle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = strings.lessonSpeedDesc,
                                tint = themeColors.turquoiseAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = speedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleHighlightPieces,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (highlightTargetPieces) {
                                    themeColors.turquoiseAccent.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .testTag("highlight_pieces_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = strings.lessonHighlightPiecesDesc,
                            tint = if (highlightTargetPieces) {
                                themeColors.turquoiseAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenNotationGuide,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("lesson_notation_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = strings.lessonNotationGuideDesc,
                            tint = themeColors.violetAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonModeSelectorBar(
    currentMode: LearningMode,
    onSelectMode: (LearningMode) -> Unit
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LearningMode.entries.forEach { mode ->
            val isSelected = currentMode == mode
            val tag = if (mode == LearningMode.LEARN) "mode_learn_tab" else "mode_practice_tab"
            Surface(
                color = if (isSelected) themeColors.turquoiseAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clickable { onSelectMode(mode) }
                    .testTag(tag)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = mode.localizedShortLabel(strings.language),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonCubeViewportBox(
    stepNumber: Int,
    caseIndex: Int = 0,
    currentMoveIndex: Int = -1,
    mode: LearningMode,
    cubeState: CubeState,
    activeAnimation: ActiveLayerAnimation?,
    guidedMove: CubeMove?,
    isDemoCompleted: Boolean,
    isPracticeGoalAchieved: Boolean,
    celebrationPulse: Float,
    cameraResetCounter: Int,
    feedbackMessage: String?,
    highlightTargetPieces: Boolean,
    isDarkTheme: Boolean,
    onExecutePracticeMove: (CubeMove) -> Unit,
    modifier: Modifier = Modifier
) {
    val step = remember(stepNumber) { CurriculumRepository.getStep(stepNumber) }
    val currentCase = remember(step, caseIndex) {
        step.cases.getOrElse(caseIndex) { step.cases.first() }
    }
    val isLShapeIntermediateMilestone = stepNumber == 4 &&
        mode == LearningMode.LEARN &&
        currentCase.pedagogicalPhases.size == 2 &&
        currentMoveIndex == currentCase.pedagogicalPhases.first().endMoveIndex &&
        activeAnimation == null

    val effectiveHighlightMode = when {
        isLShapeIntermediateMilestone -> com.example.curriculum.HighlightMode.YELLOW_L_SHAPE
        stepNumber == 6 && activeAnimation == null ->
            CurriculumRepository.resolveStage6DynamicHighlightMode(cubeState)
        else -> step.highlightMode
    }
    val effectiveHighlightEnabled = highlightTargetPieces || isLShapeIntermediateMilestone

    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    SideEffect {
        LessonRecompositionTracker.viewportRecompositions++
    }

    Box(modifier = modifier) {
        InteractiveCube3DView(
            cubeState = cubeState,
            activeAnimation = activeAnimation,
            guidedMove = guidedMove,
            highlightMode = effectiveHighlightMode,
            highlightEnabled = effectiveHighlightEnabled,
            isDarkTheme = isDarkTheme,
            autoRotateIdle = false,
            allowFaceSwipeMoves = mode == LearningMode.PRACTICE,
            celebrationPulse = celebrationPulse,
            cameraResetTrigger = cameraResetCounter,
            showCameraControlsOverlay = true,
            onMoveSwiped = { move ->
                if (mode == LearningMode.PRACTICE) {
                    onExecutePracticeMove(move)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Destaque visual quando a Fase 1 do Caso 2 (Etapa 4) conclui os 6 primeiros movimentos e forma o L
        AnimatedVisibility(
            visible = isLShapeIntermediateMilestone,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, themeColors.warningText.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .testTag("l_shape_intermediate_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = themeColors.warningText,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = strings.lessonLShapeFormedBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. No Modo Aprender: ao terminar uma demonstração, mostra "Demonstração concluída."
        val showDemoDoneBadge = mode == LearningMode.LEARN && isDemoCompleted
        AnimatedVisibility(
            visible = showDemoDoneBadge,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .testTag("demo_completed_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = strings.lessonDemoCompletedBanner,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. No Modo Praticar: quando o aluno atinge o objetivo matemático da etapa
        val showPracticeSuccessBadge = mode == LearningMode.PRACTICE && isPracticeGoalAchieved
        AnimatedVisibility(
            visible = showPracticeSuccessBadge,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Surface(
                color = themeColors.successBadgeBg.copy(alpha = 0.96f),
                shape = RoundedCornerShape(50),
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("goal_achieved_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = if (stepNumber == 7) Icons.Default.EmojiEvents else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.lessonGoalAchievedBanner,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Feedback compacto de movimentos durante a prática ("Movimento executado: R'", "Movimento desfeito")
        val showCompactFeedback = !feedbackMessage.isNullOrBlank() &&
            !showPracticeSuccessBadge &&
            !showDemoDoneBadge &&
            !isLShapeIntermediateMilestone
        AnimatedVisibility(
            visible = showCompactFeedback,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .testTag("lesson_feedback_badge")
            ) {
                Text(
                    text = feedbackMessage.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.turquoiseAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // 4. Na Etapa 6 ("Posicionar os Cantos Finais"): indicador em tempo real do par de cantos ("faróis")
        if (stepNumber == 6) {
            val stage6StatusText = remember(cubeState, strings.language) {
                CurriculumRepository.describeStage6CornerState(cubeState, strings.language)
            }
            val isBackReady = remember(cubeState) {
                cubeState.hasCornerPairOnSide(com.example.model.CubeFace.BACK) &&
                    !cubeState.areTopCornersPermuted()
            }
            val isAllPairsReady = remember(cubeState) {
                cubeState.areTopCornersPermuted()
            }
            val badgeBorderColor = when {
                isAllPairsReady -> themeColors.successBadgeBg
                isBackReady -> themeColors.turquoiseAccent
                else -> themeColors.warningText
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .border(1.dp, badgeBorderColor.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .testTag("stage6_corner_pair_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isAllPairsReady || isBackReady) Icons.Default.CheckCircle else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = badgeBorderColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stage6StatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.testTag("stage6_corner_pair_text")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonCaseAndAlgorithmCard(
    stepNumber: Int,
    caseIndex: Int,
    mode: LearningMode,
    currentMoveIndex: Int,
    executingMoveIndex: Int? = null,
    onSelectCase: (Int) -> Unit,
    onOpenCaseDetail: () -> Unit = {},
    onPlayDemoPhase: (Int) -> Unit = {},
    onRepeatSequenceDemo: () -> Unit = {},
    isCompactHeight: Boolean = false
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    val step = remember(stepNumber) { CurriculumRepository.getStep(stepNumber) }
    val currentCase = remember(step, caseIndex) {
        step.cases.getOrElse(caseIndex) { step.cases.first() }
    }
    val demoMoves = currentCase.fullMoveList
    val visualMoveIndex = executingMoveIndex ?: currentMoveIndex
    val totalCases = step.cases.size
    val caseNum = caseIndex + 1
    val canPrevCase = caseIndex > 0
    val canNextCase = caseIndex < step.cases.lastIndex
    val caseSituationTitle = currentCase.localizedSituationTitle(strings.language)

    var showCaseOrientationTip by rememberSaveable { mutableStateOf(true) }
    var showFullAlgorithm by rememberSaveable(stepNumber, caseIndex) { mutableStateOf(false) }

    val chipScrollState = rememberScrollState()
    val algScrollState = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale

    LaunchedEffect(caseIndex) {
        val targetChipPx = ((caseIndex - 1) * 110).coerceAtLeast(0)
        chipScrollState.animateScrollTo(targetChipPx)
    }

    LaunchedEffect(visualMoveIndex) {
        if (visualMoveIndex >= 0 && mode == LearningMode.LEARN) {
            val targetPx = (visualMoveIndex * 76 - 80).coerceAtLeast(0)
            algScrollState.animateScrollTo(targetPx)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lesson_case_and_algorithm_card")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = if (mode == LearningMode.PRACTICE || isCompactHeight) 3.dp else 4.dp
                )
        ) {
            val showNavText = mode == LearningMode.LEARN && maxWidth >= 340.dp && fontScale < 1.20f

            Column(modifier = Modifier.fillMaxWidth()) {
                // =================================================================
                // LINHA 1: Anterior | Caso X de Y (com weight(1f) sem aperto) | Próximo
                // =================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(24.dp)
                            .clickable(enabled = canPrevCase) { onSelectCase(caseIndex - 1) }
                            .testTag("prev_case_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = strings.lessonPrevCaseDesc,
                                tint = if (canPrevCase) {
                                    themeColors.turquoiseAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            if (showNavText) {
                                Text(
                                    text = strings.lessonPrevCaseButton,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canPrevCase) {
                                        themeColors.turquoiseAccent
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Text(
                        text = strings.lessonCaseHeader(caseNum, totalCases),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .testTag("case_selector_header")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(24.dp)
                            .clickable(enabled = canNextCase) { onSelectCase(caseIndex + 1) }
                            .testTag("next_case_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            if (showNavText) {
                                Text(
                                    text = strings.lessonNextCaseButton,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canNextCase) {
                                        themeColors.turquoiseAccent
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = strings.lessonNextCaseDesc,
                                tint = if (canNextCase) {
                                    themeColors.turquoiseAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // =================================================================
                // LINHA 2: Nome completo ou título descritivo do caso (largura total, sem corte)
                // =================================================================
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = caseSituationTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = themeColors.turquoiseAccent,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (fontScale >= 1.25f) 3 else 2,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCaseDetail() }
                        .testTag("case_full_title_text")
                )

                // =================================================================
                // LINHA 3: Botão "Entender o caso" + Chips compactos ("Caso 1", "Caso 2"...)
                // =================================================================
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScrollState)
                        .testTag("case_chips_row"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = themeColors.turquoiseAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(22.dp)
                            .clickable { onOpenCaseDetail() }
                            .testTag("open_case_detail_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = strings.lessonUnderstandCaseButton,
                                tint = themeColors.turquoiseAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = strings.lessonUnderstandCaseButton,
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.widthIn(min = (100 * fontScale).dp)
                            )
                        }
                    }

                    if (totalCases > 1) {
                        step.cases.forEachIndexed { idx, _ ->
                            val selected = idx == caseIndex
                            Surface(
                                color = if (selected) themeColors.violetAccent else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .width((60 * fontScale).dp)
                                    .height(22.dp)
                                    .clickable { onSelectCase(idx) }
                                    .testTag("case_chip_$idx")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = strings.lessonCaseChip(idx + 1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (mode == LearningMode.LEARN) {
                    if (!isCompactHeight) {
                        // Orientação inicial discreta e dispensável explicando o papel dos casos
                        if (showCaseOrientationTip) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                color = themeColors.turquoiseAccent.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .testTag("case_orientation_tip_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.lessonCaseOrientationTip,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = if (fontScale >= 1.25f) 4 else 3,
                                        lineHeight = 14.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { showCaseOrientationTip = false },
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .testTag("dismiss_case_tip_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.lessonDismissCaseTipDesc,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Modo Compacto da Descrição do Caso: resumo autocontido sem corte no meio da frase
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentCase.localizedCompactSummary(strings.language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (fontScale >= 1.25f) 4 else 3,
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCaseDetail() }
                                .testTag("case_short_description")
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // =================================================================
                    // ALGORITMO DO CASO & MOVIMENTO ATUALMENTE EXECUTADO
                    // =================================================================
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp)
                            )
                            .testTag("algorithm_section_container")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val phases = currentCase.pedagogicalPhases
                            val activePhase = if (phases.isNotEmpty()) {
                                phases.firstOrNull { visualMoveIndex in it.startMoveIndex..it.endMoveIndex }
                                    ?: if (visualMoveIndex < 0) phases.first() else phases.last()
                            } else {
                                null
                            }

                            if (activePhase != null) {
                                // Painel de Fases Pedagógicas (Etapa 4 Caso 2: 2 fases; Etapa 6 Caso 2: 4 fases)
                                val localizedCompletionSummary = activePhase.localizedCompletionSummaryText(strings.language)
                                Surface(
                                    color = themeColors.turquoiseAccent.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            themeColors.turquoiseAccent.copy(alpha = 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .testTag("pedagogical_phases_panel")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = activePhase.localizedTitle(strings.language),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = themeColors.turquoiseAccent,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 2,
                                                lineHeight = 13.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("active_phase_title_text")
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    text = activePhase.algorithmText,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = themeColors.violetText,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    modifier = Modifier
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                                        .testTag("active_phase_algorithm_text")
                                                )
                                            }
                                        }

                                        Text(
                                            text = activePhase.localizedExecutionBannerText(strings.language),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = if (fontScale >= 1.25f) 3 else 2,
                                            lineHeight = 13.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("active_phase_description_text")
                                        )

                                        if (currentMoveIndex == activePhase.endMoveIndex &&
                                            executingMoveIndex == null &&
                                            localizedCompletionSummary.isNotBlank()
                                        ) {
                                            Surface(
                                                color = themeColors.warningText.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(
                                                        1.dp,
                                                        themeColors.warningText.copy(alpha = 0.6f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .testTag("phase_transition_message_banner")
                                            ) {
                                                Text(
                                                    text = localizedCompletionSummary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = themeColors.warningText,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 3,
                                                    lineHeight = 13.sp,
                                                    modifier = Modifier
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        .testTag("phase_transition_message_text")
                                                )
                                            }
                                        }

                                        // Botões para executar fases individuais (sem reinicializar o cubo) ou Demonstração Completa
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (phases.size == 2) {
                                                val isPhase1Active = activePhase.phaseNumber == 1
                                                val isPhase2Active = activePhase.phaseNumber == 2

                                                Surface(
                                                    color = if (isPhase1Active) themeColors.turquoiseAccent else MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(22.dp)
                                                        .clickable { onPlayDemoPhase(1) }
                                                        .testTag("play_phase_1_button")
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text(
                                                            text = strings.lessonPlayPhase1Button,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isPhase1Active) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = if (isPhase2Active) themeColors.turquoiseAccent else MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(22.dp)
                                                        .clickable { onPlayDemoPhase(2) }
                                                        .testTag("play_phase_2_button")
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        Text(
                                                            text = strings.lessonPlayPhase2Button,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isPhase2Active) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = themeColors.violetAccent.copy(alpha = 0.16f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .height(22.dp)
                                                        .border(1.dp, themeColors.violetAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                        .clickable { onPlayDemoPhase(0) }
                                                        .testTag("play_full_phases_button")
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(horizontal = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = strings.lessonPlay2PhasesButton,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = themeColors.violetText,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            } else {
                                                phases.forEach { phase ->
                                                    val isPhaseActive = activePhase.phaseNumber == phase.phaseNumber
                                                    Surface(
                                                        color = if (isPhaseActive) themeColors.turquoiseAccent else MaterialTheme.colorScheme.surface,
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(22.dp)
                                                            .clickable { onPlayDemoPhase(phase.phaseNumber) }
                                                            .testTag("play_phase_${phase.phaseNumber}_button")
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                            Text(
                                                                text = strings.lessonPlayPhaseShortButton(phase.phaseNumber),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (isPhaseActive) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurface,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }

                                                Surface(
                                                    color = themeColors.violetAccent.copy(alpha = 0.16f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .height(22.dp)
                                                        .border(1.dp, themeColors.violetAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                        .clickable { onPlayDemoPhase(0) }
                                                        .testTag("play_full_phases_button")
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(horizontal = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = strings.lessonPlayNPhasesButton(phases.size),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = themeColors.violetText,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val activeMove = demoMoves.getOrNull(visualMoveIndex)
                            val moveStatusText = when {
                                activeMove != null ->
                                    strings.lessonCurrentMoveStatus(
                                        visualMoveIndex + 1,
                                        demoMoves.size,
                                        activeMove.notation,
                                        activeMove.localizedTitle(strings.language)
                                    )
                                demoMoves.isNotEmpty() ->
                                    strings.lessonFirstMoveStatus(
                                        demoMoves.first().notation,
                                        demoMoves.first().localizedTitle(strings.language)
                                    )
                                else -> strings.lessonAlgorithmDefaultTitle
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        phases.size == 2 -> strings.lessonFullAlgorithm2PhasesTitle(demoMoves.size)
                                        phases.size == 4 -> strings.lessonFullAlgorithm4PhasesTitle(demoMoves.size)
                                        else -> strings.lessonAlgorithmTitle(demoMoves.size)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColors.violetText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )

                                if (demoMoves.size > 7) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .clickable { showFullAlgorithm = !showFullAlgorithm }
                                            .testTag("toggle_full_algorithm_button")
                                    ) {
                                        Text(
                                            text = if (showFullAlgorithm) strings.lessonCollapseAlgorithmButton else strings.lessonViewAllAlgorithmButton,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = themeColors.turquoiseAccent,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Linha dedicada para o movimento atual (evita corte horizontal com o título do algoritmo)
                            Text(
                                text = moveStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (fontScale >= 1.25f) 3 else 2,
                                lineHeight = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenCaseDetail() }
                                    .testTag("current_move_status_text")
                            )

                            // Faixa horizontal auto-rolável mantendo o movimento atual em evidência
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(algScrollState)
                                    .testTag("algorithm_sequence_row")
                            ) {
                                demoMoves.forEachIndexed { index, move ->
                                    if (phases.size == 2 && index == 6) {
                                        Surface(
                                            color = themeColors.warningText.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .border(1.dp, themeColors.warningText, RoundedCornerShape(6.dp))
                                                .testTag("phase_transition_badge")
                                        ) {
                                            Text(
                                                text = strings.lessonPhaseTransitionLBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = themeColors.warningText,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (phases.size == 4 && (index == 9 || index == 10 || index == 19)) {
                                        val badgeLabel = when (index) {
                                            9 -> strings.lessonPhaseTransitionLeftPairBadge
                                            10 -> strings.lessonPhaseTransitionBackPairBadge
                                            else -> strings.lessonPhaseTransition4PairsBadge
                                        }
                                        Surface(
                                            color = themeColors.warningText.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .border(1.dp, themeColors.warningText, RoundedCornerShape(6.dp))
                                                .testTag("phase_transition_badge_$index")
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = themeColors.warningText,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    val isCurrent = index == visualMoveIndex
                                    val isDone = index <= currentMoveIndex && index != visualMoveIndex
                                    val bgColor = when {
                                        isCurrent -> themeColors.turquoiseAccent
                                        isDone -> themeColors.turquoiseAccent.copy(alpha = 0.16f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                    val txtColor = when {
                                        isCurrent -> themeColors.onTurquoiseAccent
                                        isDone -> themeColors.turquoiseAccent
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Surface(
                                        color = bgColor,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .border(
                                                width = if (isCurrent) 2.dp else 1.dp,
                                                color = if (isCurrent) {
                                                    Color.White
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .testTag("alg_token_$index")
                                    ) {
                                        Text(
                                            text = move.notation,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = txtColor,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Visualização expandida opcional quando a sequência é longa
                            if (showFullAlgorithm && demoMoves.size > 7) {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp)
                                        .testTag("algorithm_full_flow_row"),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    demoMoves.forEachIndexed { index, move ->
                                        val isCurrent = index == visualMoveIndex
                                        Surface(
                                            color = if (isCurrent) {
                                                themeColors.turquoiseAccent.copy(alpha = 0.18f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${index + 1}.${move.notation}",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCurrent) themeColors.turquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnPlaybackControlsCard(
    stepNumber: Int,
    caseIndex: Int,
    currentMoveIndex: Int,
    isPlayingDemo: Boolean,
    onStepBackward: () -> Unit,
    onPlayPause: () -> Unit,
    onStepForward: () -> Unit,
    onRepeatSequence: () -> Unit,
    onResetDemo: () -> Unit
) {
    val step = remember(stepNumber) { CurriculumRepository.getStep(stepNumber) }
    val currentCase = remember(step, caseIndex) {
        step.cases.getOrElse(caseIndex) { step.cases.first() }
    }
    val demoMoves = currentCase.fullMoveList
    val canBack = currentMoveIndex >= 0 && !isPlayingDemo
    val canForward = currentMoveIndex < demoMoves.lastIndex && !isPlayingDemo
    val fontScale = LocalDensity.current.fontScale
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            val isTight = maxWidth < 340.dp || fontScale >= 1.3f
            val iconBtnSize = if (isTight) 38.dp else 42.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onResetDemo,
                    modifier = Modifier
                        .size(iconBtnSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("reset_demo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = strings.lessonResetDemoDesc,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onStepBackward,
                    enabled = canBack,
                    modifier = Modifier
                        .size(iconBtnSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("step_backward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = strings.lessonPrevMoveDesc,
                        tint = if (canBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                Button(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = if (isTight) 10.dp else 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.turquoiseAccent,
                        contentColor = themeColors.onTurquoiseAccent
                    ),
                    modifier = Modifier
                        .height(iconBtnSize)
                        .testTag("play_pause_button")
                ) {
                    if (!isTight) {
                        Icon(
                            imageVector = if (isPlayingDemo) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingDemo) strings.lessonPauseButton else strings.lessonPlayButton
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isPlayingDemo) strings.lessonPauseButton else strings.lessonPlayButton,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onStepForward,
                    enabled = canForward,
                    modifier = Modifier
                        .size(iconBtnSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("step_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = strings.lessonNextMoveDesc,
                        tint = if (canForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                IconButton(
                    onClick = onRepeatSequence,
                    modifier = Modifier
                        .size(iconBtnSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("repeat_sequence_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = strings.lessonRepeatSequenceDesc,
                        tint = themeColors.violetAccent
                    )
                }
            }
        }
    }
}

/**
 * Painel compacto de movimentos do Modo Praticar:
 * - Mantém SEMPRE visível a seleção por família (R, L, U, D, F, B, M, E, S) e os 3 botões da família (Normal, Inverso ', Duplo 2).
 * - Recolhe o teclado completo por padrão para garantir 50%–60% da altura da tela para o cubo 3D.
 * - Oferece botão secundário ("Teclado" / "Recolher") para abrir/fechar a grade completa sob demanda.
 * - Recebe apenas parâmetros primitivos estáveis, evitando recomposição durante os quadros intermediários da animação 3D.
 */
@Composable
private fun PracticeInteractiveCard(
    selectedFamily: String,
    isMovesExpanded: Boolean,
    canUndo: Boolean,
    onSelectFamily: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onExecuteMove: (CubeMove) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    SideEffect {
        LessonRecompositionTracker.controlsRecompositions++
    }

    val activeFamily = selectedFamily.ifEmpty { "R" }
    val familyMoves = remember(activeFamily) {
        listOfNotNull(
            CubeMove.fromNotation(activeFamily),
            CubeMove.fromNotation("$activeFamily'"),
            CubeMove.fromNotation("${activeFamily}2")
        )
    }
    val fontScale = LocalDensity.current.fontScale
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("practice_interactive_card")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val isCompactLabels = maxWidth < 340.dp || fontScale >= 1.25f

            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Linha de ações compacta: Família ativa | Botão secundário "Teclado completo" | Desfazer | Reiniciar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = strings.practiceFamilyTitle(activeFamily),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(25.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    RoundedCornerShape(50)
                                )
                                .clickable { onToggleExpanded() }
                                .testTag("toggle_practice_moves_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(horizontal = 7.dp)
                            ) {
                                Text(
                                    text = when {
                                        isMovesExpanded && isCompactLabels -> strings.practiceHideCompactButton
                                        isMovesExpanded -> strings.practiceHideKeyboardButton
                                        isCompactLabels -> strings.practiceKeyboardCompactButton
                                        else -> strings.practiceFullKeyboardButton
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColors.turquoiseAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = if (isMovesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = strings.practiceToggleKeyboardDesc,
                                    tint = themeColors.turquoiseAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("practice_undo_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = strings.practiceUndoDesc,
                                tint = if (canUndo) {
                                    themeColors.turquoiseAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = onReset,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("practice_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.practiceResetDesc,
                                tint = themeColors.warningText,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // 2. Seletor por família de movimentos sempre visível: R, L, U, D, F, B, M, E, S
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    PRACTICE_FAMILIES.forEach { fam ->
                        val isSelected = fam == activeFamily
                        Surface(
                            color = if (isSelected) themeColors.violetAccent else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clickable { onSelectFamily(fam) }
                                .testTag("practice_family_$fam")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = fam,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // 3. Os 3 botões correspondentes da família selecionada (Normal, Inverso ', Duplo 2) sempre visíveis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    familyMoves.forEachIndexed { idx, move ->
                        val subLabel = when (idx) {
                            0 -> strings.practiceMoveNormalLabel
                            1 -> strings.practiceMoveInverseLabel
                            else -> strings.practiceMoveDoubleLabel
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable { onExecuteMove(move) }
                                .testTag("practice_family_action_${move.notation}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = move.notation,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = themeColors.turquoiseAccent,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                                if (!isCompactLabels) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = subLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Teclado completo expansível sob demanda (recolhido por padrão para devolver espaço ao cubo 3D)
                if (isMovesExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("practice_full_keyboard_row"),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ALL_PRACTICE_QUICK_MOVES.forEach { move ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onExecuteMove(move) }
                                    .testTag("practice_move_${move.notation}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 9.dp)
                                ) {
                                    Text(
                                        text = move.notation,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = themeColors.turquoiseAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Painel Inferior Expansível de 3 estados (Recolhido, Resumo, Completo).
 * No estado [ExplanationPanelState.COLLAPSED] (padrão), exibe o objetivo curto completo (sem reticências)
 * e permite abrir a explicação completa em Resumo, Completo ou no modal detalhado.
 */
@Composable
private fun ExpandableExplanationPanel(
    stepNumber: Int,
    caseIndex: Int,
    currentMoveIndex: Int,
    selectedNotationMove: CubeMove?,
    panelState: ExplanationPanelState,
    onChangeState: (ExplanationPanelState) -> Unit,
    onOpenCaseDetail: () -> Unit = {},
    hasOuterVerticalScroll: Boolean = false
) {
    val step = remember(stepNumber) { CurriculumRepository.getStep(stepNumber) }
    val currentCase = remember(step, caseIndex) {
        step.cases.getOrElse(caseIndex) { step.cases.first() }
    }
    val activeMove = currentCase.fullMoveList.getOrNull(currentMoveIndex) ?: selectedNotationMove
    val fontScale = LocalDensity.current.fontScale
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .testTag("expandable_explanation_panel")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            val stackGoalAndButtons = maxWidth < 330.dp || (fontScale >= 1.20f && maxWidth < 360.dp)

            Column(modifier = Modifier.fillMaxWidth()) {
                if (stackGoalAndButtons) {
                    Text(
                        text = step.localizedShortGoal(strings.language),
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.turquoiseAccent,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (fontScale >= 1.25f) 3 else 2,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCaseDetail() }
                            .testTag("collapsed_goal_text")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExplanationPanelState.entries.forEach { st ->
                                val selected = panelState == st
                                Surface(
                                    color = if (selected) themeColors.turquoiseAccent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .height(20.dp)
                                        .clickable { onChangeState(st) }
                                        .testTag("panel_state_${st.name}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = st.localizedLabel(strings.language),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) themeColors.turquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = step.localizedShortGoal(strings.language),
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                                .clickable { onOpenCaseDetail() }
                                .testTag("collapsed_goal_text")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExplanationPanelState.entries.forEach { st ->
                                val selected = panelState == st
                                Surface(
                                    color = if (selected) themeColors.turquoiseAccent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .height(22.dp)
                                        .clickable { onChangeState(st) }
                                        .testTag("panel_state_${st.name}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = st.localizedLabel(strings.language),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) themeColors.turquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Estado INTERMEDIÁRIO (Resumo): Objetivo completo + Descrição do Caso + Dica principal sem corte
                if (panelState == ExplanationPanelState.INTERMEDIATE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val intermediateModifier = if (hasOuterVerticalScroll) {
                        Modifier.fillMaxWidth().testTag("intermediate_scroll_container")
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 86.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("intermediate_scroll_container")
                    }
                    Column(
                        modifier = intermediateModifier,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = strings.panelStepGoalPrefix(step.localizedGoalSimple(strings.language)),
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentCase.localizedDescription(strings.language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.panelTipPrefix(currentCase.localizedPedagogicalNote(strings.language)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Estado EXPANDIDO (Completo): Explicação integral com rolagem exclusiva dentro deste painel
                if (panelState == ExplanationPanelState.EXPANDED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val expandedModifier = if (hasOuterVerticalScroll) {
                        Modifier.fillMaxWidth().testTag("explanation_scroll_container")
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 105.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("explanation_scroll_container")
                    }
                    Column(
                        modifier = expandedModifier,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = strings.panelStepHeader(step.number, step.localizedTitle(strings.language)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.panelGoalPrefix(step.localizedGoalSimple(strings.language)),
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = step.localizedDetailedTheory(strings.language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentCase.localizedDescription(strings.language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.panelTeacherTipPrefix(currentCase.localizedPedagogicalNote(strings.language)),
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.turquoiseAccent
                        )
                        if (activeMove != null) {
                            Text(
                                text = strings.panelNotationMoveExplanation(
                                    activeMove.notation,
                                    activeMove.localizedExplanation(strings.language)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        currentCase.localizedPdfValidationNote(strings.language)?.let { pdfNote ->
                            Surface(
                                color = themeColors.warningText.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, themeColors.warningText.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .testTag("pdf_validation_note_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = themeColors.warningText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = pdfNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal de 2º nível ("Entender o caso" / "Ver explicação") que apresenta a explicação pedagógica
 * integral do caso e da etapa com rolagem vertical completa, sem reiniciar nem alterar o estado do cubo 3D.
 */
@Composable
private fun CaseDetailedExplanationModal(
    stepNumber: Int,
    caseIndex: Int,
    currentMoveIndex: Int,
    onDismiss: () -> Unit
) {
    val step = remember(stepNumber) { CurriculumRepository.getStep(stepNumber) }
    val currentCase = remember(step, caseIndex) {
        step.cases.getOrElse(caseIndex) { step.cases.first() }
    }
    val totalCases = step.cases.size
    val caseNum = caseIndex + 1
    val moves = currentCase.fullMoveList
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .testTag("case_detail_modal"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Cabeçalho fixo do modal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = strings.caseModalStepHeader(step.number, step.localizedTitle(strings.language)),
                            style = MaterialTheme.typography.labelMedium,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.caseModalTitleHeader(
                                caseNum,
                                totalCases,
                                currentCase.localizedSituationTitle(strings.language)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("case_detail_full_title")
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("close_case_detail_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.caseModalCloseDesc,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Conteúdo pedagógico completo com rolagem vertical
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag("case_detail_scroll"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Descrição Completa da Situação do Caso
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.caseModalWhatHappensTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentCase.localizedDescription(strings.language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("case_detail_full_description")
                            )
                        }
                    }

                    // 2. Dica do Professor
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = themeColors.turquoiseAccent.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.caseModalTeacherTipTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentCase.localizedPedagogicalNote(strings.language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("case_detail_pedagogical_note")
                            )
                        }
                    }

                    // 2B. Guia Visual Exclusivo da Etapa 6 ("Como identificar um par / 'faróis'")
                    if (stepNumber == 6) {
                        Stage6HeadlightsVisualGuideCard(caseIndex = caseIndex)
                    }

                    // 3. Algoritmo Completo e Explicação Movimento a Movimento
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = strings.caseModalFullAlgorithmTitle(moves.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = themeColors.violetText,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentCase.fullResolutionAlgorithm,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.titleSmall,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("case_detail_algorithm_text")
                            )

                            if (currentCase.pedagogicalPhases.isNotEmpty()) {
                                currentCase.pedagogicalPhases.forEach { phase ->
                                    val phaseCompletion = phase.localizedCompletionSummaryText(strings.language)
                                    Surface(
                                        color = themeColors.turquoiseAccent.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .testTag("case_detail_phase_${phase.phaseNumber}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = phase.localizedTitle(strings.language),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = themeColors.turquoiseAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = phase.localizedExecutionBannerText(strings.language),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = strings.caseModalPhaseAlgorithmPrefix(phase.algorithmText),
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = themeColors.violetText,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (phaseCompletion.isNotBlank()) {
                                                Text(
                                                    text = phaseCompletion,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = themeColors.warningText,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            moves.forEachIndexed { idx, move ->
                                val isCurrent = idx == currentMoveIndex
                                Surface(
                                    color = if (isCurrent) {
                                        themeColors.turquoiseAccent.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = strings.caseModalMoveStepHeader(
                                                idx + 1,
                                                move.notation,
                                                move.localizedTitle(strings.language)
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isCurrent) themeColors.turquoiseAccent else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = move.localizedExplanation(strings.language),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Objetivo Pedagógico e Teoria da Etapa
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.caseModalStepGoalTitle(step.number),
                                style = MaterialTheme.typography.labelMedium,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = step.localizedGoalSimple(strings.language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("case_detail_goal_text")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = step.localizedDetailedTheory(strings.language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("case_detail_theory_text")
                            )
                        }
                    }

                    // 5. Nota de Validação do PDF (quando existir)
                    currentCase.localizedPdfValidationNote(strings.language)?.let { pdfNote ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = themeColors.warningText.copy(alpha = 0.14f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, themeColors.warningText.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .testTag("case_detail_pdf_note")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = strings.caseModalOrientationTipTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = themeColors.warningText,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pdfNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.turquoiseAccent,
                        contentColor = themeColors.onTurquoiseAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("return_to_cube_from_detail_button")
                ) {
                    Text(
                        text = strings.caseModalReturnToCubeButton,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Ilustração visual e guia passo a passo exibido dentro de "Entender o caso" na Etapa 6 (Casos 1 e 2),
 * esclarecendo de forma simples e visual quais dois adesivos laterais da fileira superior devem ser
 * comparados para encontrar um par ("faróis") e por que não procuramos dois cantos com as mesmas 3 cores.
 */
@Composable
private fun Stage6HeadlightsVisualGuideCard(
    caseIndex: Int
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    val isCase1 = caseIndex == 0
    val pairStickerColor = if (isCase1) StickerOrange else StickerGreen
    val pairColorLabel = if (isCase1) strings.stage6HeadlightsOrangeLabel else strings.stage6HeadlightsGreenLabel
    val centerStickerColor = if (isCase1) StickerGreen else StickerOrange
    val centerColorLabel = if (isCase1) strings.stage6HeadlightsGreenLabel else strings.stage6HeadlightsOrangeLabel
    val faceTitle = if (isCase1) {
        strings.stage6HeadlightsCase1FaceTitle
    } else {
        strings.stage6HeadlightsCase2FaceTitle
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .testTag("stage6_headlights_visual_guide_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = strings.stage6HeadlightsGuideTitle,
                style = MaterialTheme.typography.labelMedium,
                color = themeColors.turquoiseAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("stage6_headlights_guide_title")
            )

            // Ilustração visual de uma face lateral mostrando exatamente os 2 adesivos comparados
            Surface(
                color = themeColors.miniCubeSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, themeColors.miniCubeStickerStroke, RoundedCornerShape(10.dp))
                    .testTag("stage6_headlights_face_diagram")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = faceTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = strings.stage6HeadlightsTopYellowCaption,
                        style = MaterialTheme.typography.labelSmall,
                        color = StickerYellow,
                        textAlign = TextAlign.Center
                    )

                    // Faixa superior representando os 3 adesivos amarelos no topo (U)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(StickerYellow)
                                    .border(1.dp, themeColors.miniCubeStickerStroke, RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    // Grade 3×3 da face lateral com destaque nos 2 cantos da fileira superior
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Fileira superior destacada (onde comparamos os 2 cantos)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, themeColors.turquoiseAccent, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            // 1º Canto superior da lateral
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(pairStickerColor)
                                    .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                                    .testTag("stage6_diagram_left_corner_sticker")
                            ) {
                                Text(
                                    text = strings.stage6HeadlightsFirstCornerBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // Meio superior (ignorado na Etapa 6)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(themeColors.miniCubeDimmedCell)
                                    .border(1.dp, themeColors.miniCubeStickerStroke, RoundedCornerShape(6.dp))
                                    .testTag("stage6_diagram_top_edge_sticker")
                            ) {
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 2º Canto superior da lateral
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(pairStickerColor)
                                    .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                                    .testTag("stage6_diagram_right_corner_sticker")
                            ) {
                                Text(
                                    text = strings.stage6HeadlightsSecondCornerBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // 2ª fileira (com o centro da face)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(centerStickerColor.copy(alpha = 0.7f))
                                    .border(1.dp, themeColors.miniCubeStickerStroke, RoundedCornerShape(6.dp))
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(centerStickerColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                    .testTag("stage6_diagram_center_sticker")
                            ) {
                                Text(
                                    text = "C",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(centerStickerColor.copy(alpha = 0.7f))
                                    .border(1.dp, themeColors.miniCubeStickerStroke, RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    Text(
                        text = strings.stage6HeadlightsDiagramCaption(pairColorLabel, centerColorLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("stage6_headlights_diagram_caption")
                    )
                }
            }

            // Os 6 pontos pedagógicos fundamentais explicados de maneira simples
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stage6_headlights_six_rules_list"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val rules = strings.stage6HeadlightsSixRules
                rules.forEachIndexed { idx, ruleText ->
                    Text(
                        text = ruleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("stage6_headlights_rule_${idx + 1}")
                    )
                }
            }

            // Exemplo pedagógico explícito (Caso 1 e Caso 2)
            Surface(
                color = themeColors.turquoiseAccent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                    .testTag("stage6_headlights_example_box")
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = strings.stage6HeadlightsExamplesTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.turquoiseAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.stage6HeadlightsExampleCase1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.stage6HeadlightsExampleCase2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Barra inferior compacta de navegação entre etapas:
 * Anterior | Concluir | Próxima
 */
@Composable
private fun StepNavigationFooterBar(
    stepNumber: Int,
    isStepCompleted: Boolean,
    onSelectStep: (Int) -> Unit,
    onMarkStepCompleted: () -> Unit
) {
    val canPrev = stepNumber > 1
    val canNext = stepNumber < 7
    val fontScale = LocalDensity.current.fontScale
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val showIcons = maxWidth >= 340.dp && fontScale < 1.25f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { if (canPrev) onSelectStep(stepNumber - 1) },
                enabled = canPrev,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("prev_step_button")
            ) {
                if (showIcons) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.footerPrevStepDesc,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = strings.footerPrevStepButton,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = onMarkStepCompleted,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("complete_step_button")
            ) {
                if (showIcons) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = strings.footerCompleteStepDesc,
                        tint = themeColors.successText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = if (isStepCompleted) strings.footerCompletedStepButton else strings.footerCompleteStepButton,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isStepCompleted) themeColors.successText else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Button(
                onClick = { if (canNext) onSelectStep(stepNumber + 1) },
                enabled = canNext,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.violetAccent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("next_step_button")
            ) {
                Text(
                    text = strings.footerNextStepButton,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                if (showIcons) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = strings.footerNextStepDesc,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
