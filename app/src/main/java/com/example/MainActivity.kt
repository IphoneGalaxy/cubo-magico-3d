package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.NotationGuideModal
import com.example.ui.components.SettingsDialogModal
import com.example.ui.components.SolveMyCubeStage3Modal
import com.example.ui.screens.CourseScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LessonScreen
import com.example.ui.screens.OpeningScreen
import com.example.ui.screens.WelcomeLanguageScreen
import com.example.ui.theme.CuboMestreTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CuboMestreViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuboMestreApp()
        }
    }
}

@Composable
fun CuboMestreApp(
    viewModel: CuboMestreViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    viewModel.onAppForegrounded()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val lessonState by viewModel.lessonState.collectAsStateWithLifecycle()
    val exploreState by viewModel.exploreState.collectAsStateWithLifecycle()
    val homePreviewState by viewModel.homePreviewState.collectAsStateWithLifecycle()
    val showNotationModal by viewModel.showNotationGuideModal.collectAsStateWithLifecycle()
    val showSettingsModal by viewModel.showSettingsModal.collectAsStateWithLifecycle()
    val showSolvePreviewModal by viewModel.showSolveMyCubePreviewModal.collectAsStateWithLifecycle()
    val notationPreviewMove by viewModel.notationPreviewMove.collectAsStateWithLifecycle()
    val notationPreviewCube by viewModel.notationPreviewCube.collectAsStateWithLifecycle()
    val notationPreviewAnim by viewModel.notationPreviewAnim.collectAsStateWithLifecycle()
    val notationCameraResetCounter by viewModel.notationCameraResetCounter.collectAsStateWithLifecycle()

    CuboMestreTheme(
        darkTheme = progressState.isDarkTheme,
        appLanguage = progressState.appLanguage
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Crossfade(
                targetState = currentScreen,
                animationSpec = tween(durationMillis = 160),
                label = "screen_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { screen ->
                when (screen) {
                    AppScreen.OPENING -> {
                        OpeningScreen(
                            isDarkTheme = progressState.isDarkTheme,
                            isActive = (currentScreen == AppScreen.OPENING),
                            onContinue = { viewModel.completeOpeningScreen() }
                        )
                    }

                    AppScreen.WELCOME_LANGUAGE -> {
                        BackHandler(enabled = progressState.hasCompletedOnboarding) {
                            viewModel.navigateTo(AppScreen.HOME)
                        }
                        WelcomeLanguageScreen(
                            selectedLanguage = progressState.appLanguage,
                            isDarkTheme = progressState.isDarkTheme,
                            onSelectLanguage = { lang -> viewModel.selectAppLanguage(lang) },
                            onConfirmStart = { lang -> viewModel.completeWelcomeOnboarding(lang) }
                        )
                    }

                    AppScreen.HOME -> HomeScreen(
                        progressState = progressState,
                        homePreviewState = homePreviewState,
                        onStartLearningClick = { viewModel.openCourseScreen() },
                        onContinueLessonClick = { viewModel.startOrContinueLearning() },
                        onExploreCubeClick = { viewModel.openExploreScreen() },
                        onOpenNotationGuide = { viewModel.setNotationGuideVisible(true) },
                        onToggleTheme = { viewModel.toggleDarkTheme() },
                        onExecuteHomePreviewMove = { move -> viewModel.executeHomePreviewMove(move) },
                        onRestoreHomePreviewCube = { viewModel.resetHomePreviewCube() },
                        onPracticeClick = { viewModel.startOrContinuePractice() },
                        onOpenSettingsClick = { viewModel.setSettingsModalVisible(true) },
                        onToggleLanguageQuickClick = { viewModel.toggleAppLanguageQuick() },
                        onOpenSolveMyCubePreview = { viewModel.setSolveMyCubePreviewModalVisible(true) }
                    )

                    AppScreen.COURSE -> CourseScreen(
                        progressState = progressState,
                        onSelectStep = { stepNum, mode ->
                            viewModel.openLesson(stepNumber = stepNum, caseIndex = 0, mode = mode)
                        },
                        onResetStepProgress = { stepNum ->
                            viewModel.resetStepStatus(stepNum)
                        },
                        onOpenNotationGuide = { viewModel.setNotationGuideVisible(true) },
                        onBackToHome = { viewModel.navigateTo(AppScreen.HOME) }
                    )

                    AppScreen.LESSON -> LessonScreen(
                        lessonState = lessonState,
                        progressState = progressState,
                        onSelectStep = { stepNum ->
                            viewModel.openLesson(stepNumber = stepNum, caseIndex = 0, mode = lessonState.mode)
                        },
                        onSelectCase = { caseIdx -> viewModel.selectCase(caseIdx) },
                        onSelectMode = { mode -> viewModel.selectLearningMode(mode) },
                        onPlayPauseDemo = { viewModel.togglePlayDemo() },
                        onStepForwardDemo = { viewModel.stepDemoForward() },
                        onStepBackwardDemo = { viewModel.stepDemoBackward() },
                        onRepeatSequenceDemo = { viewModel.repeatDemoSequence() },
                        onResetLessonCube = { viewModel.resetLessonCube() },
                        onExecutePracticeMove = { move -> viewModel.executePracticeMove(move) },
                        onUndoPracticeMove = { viewModel.undoPracticeMove() },
                        onMarkStepCompleted = { viewModel.markCurrentStepCompletedManually() },
                        onToggleHighlightPieces = { viewModel.toggleHighlightTargetPieces() },
                        onCycleSpeed = { speed -> viewModel.setAnimationSpeed(speed) },
                        onSetExplanationPanelState = { state -> viewModel.setExplanationPanelState(state) },
                        onSelectPracticeFamily = { family -> viewModel.selectPracticeFamily(family) },
                        onTogglePracticeMovesExpanded = { viewModel.togglePracticeMovesExpanded() },
                        onPlayDemoPhase = { phaseNum -> viewModel.playDemoPhase(phaseNum) },
                        onOpenNotationGuide = { viewModel.setNotationGuideVisible(true) },
                        onBackToCourse = { viewModel.openCourseScreen() }
                    )

                    AppScreen.EXPLORE -> ExploreScreen(
                        exploreState = exploreState,
                        isDarkTheme = progressState.isDarkTheme,
                        onExecuteMove = { move -> viewModel.executeExploreMove(move) },
                        onUndoMove = { viewModel.undoExploreMove() },
                        onScrambleCube = { viewModel.scrambleExploreCube() },
                        onResetCubeSolved = { viewModel.resetExploreCubeSolved() },
                        onRecenterCamera = { viewModel.recenterExploreCamera() },
                        onOpenNotationGuide = { viewModel.setNotationGuideVisible(true) },
                        onBackToHome = { viewModel.navigateTo(AppScreen.HOME) }
                    )
                }
            }

            if (showNotationModal) {
                NotationGuideModal(
                    selectedMove = notationPreviewMove,
                    previewCubeState = notationPreviewCube,
                    previewAnimation = notationPreviewAnim,
                    isDarkTheme = progressState.isDarkTheme,
                    onSelectMove = { move -> viewModel.triggerNotationPreview(move) },
                    onDismiss = { viewModel.setNotationGuideVisible(false) },
                    cameraResetCounter = notationCameraResetCounter,
                    onResetCube = { viewModel.resetNotationPreviewCube() }
                )
            }

            if (showSettingsModal) {
                SettingsDialogModal(
                    progressState = progressState,
                    onSelectLanguage = { lang -> viewModel.selectAppLanguage(lang) },
                    onSetDarkTheme = { isDark -> viewModel.setDarkThemeExplicit(isDark) },
                    onSetAnimationSpeed = { speed -> viewModel.setAnimationSpeed(speed) },
                    onToggleHighlightPieces = { viewModel.toggleHighlightTargetPieces() },
                    onReopenWelcomeScreen = { viewModel.openWelcomeLanguageScreen() },
                    onDismiss = { viewModel.setSettingsModalVisible(false) }
                )
            }

            if (showSolvePreviewModal) {
                SolveMyCubeStage3Modal(
                    onStartLearnClick = { viewModel.openCourseScreen() },
                    onExploreCubeClick = { viewModel.openExploreScreen() },
                    onDismiss = { viewModel.setSolveMyCubePreviewModalVisible(false) }
                )
            }

            // Prioridade máxima para o botão/gesto Voltar do Android quando qualquer modal global estiver aberto:
            // ao entrar em composição no momento da abertura do modal, registra-se no topo da pilha LIFO do
            // OnBackPressedDispatcher acima dos BackHandlers das telas (Course, Lesson, Explore e submodais).
            if (showNotationModal || showSettingsModal || showSolvePreviewModal) {
                BackHandler(enabled = true) {
                    when {
                        showNotationModal -> viewModel.setNotationGuideVisible(false)
                        showSettingsModal -> viewModel.setSettingsModalVisible(false)
                        showSolvePreviewModal -> viewModel.setSolveMyCubePreviewModalVisible(false)
                    }
                }
            }
        }
    }
}
