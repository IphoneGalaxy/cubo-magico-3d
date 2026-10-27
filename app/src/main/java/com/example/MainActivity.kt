package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.NotationGuideModal
import com.example.ui.screens.CourseScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LessonScreen
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
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val lessonState by viewModel.lessonState.collectAsStateWithLifecycle()
    val exploreState by viewModel.exploreState.collectAsStateWithLifecycle()
    val homePreviewState by viewModel.homePreviewState.collectAsStateWithLifecycle()
    val showNotationModal by viewModel.showNotationGuideModal.collectAsStateWithLifecycle()
    val notationPreviewMove by viewModel.notationPreviewMove.collectAsStateWithLifecycle()
    val notationPreviewCube by viewModel.notationPreviewCube.collectAsStateWithLifecycle()
    val notationPreviewAnim by viewModel.notationPreviewAnim.collectAsStateWithLifecycle()
    val notationCameraResetCounter by viewModel.notationCameraResetCounter.collectAsStateWithLifecycle()

    CuboMestreTheme(darkTheme = progressState.isDarkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Crossfade(
                targetState = currentScreen,
                label = "screen_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { screen ->
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        progressState = progressState,
                        homePreviewState = homePreviewState,
                        onStartLearningClick = { viewModel.openCourseScreen() },
                        onContinueLessonClick = { viewModel.startOrContinueLearning() },
                        onExploreCubeClick = { viewModel.openExploreScreen() },
                        onOpenNotationGuide = { viewModel.setNotationGuideVisible(true) },
                        onToggleTheme = { viewModel.toggleDarkTheme() },
                        onExecuteHomePreviewMove = { move -> viewModel.executeHomePreviewMove(move) },
                        onRestoreHomePreviewCube = { viewModel.resetHomePreviewCube() }
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
        }
    }
}
