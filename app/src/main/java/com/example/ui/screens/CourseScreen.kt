package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.curriculum.CurriculumRepository
import com.example.curriculum.CurriculumStep
import com.example.data.StepStatus
import com.example.data.UserProgressState
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.StickerBlue
import com.example.ui.theme.StickerGreen
import com.example.ui.theme.StickerOrange
import com.example.ui.theme.StickerRed
import com.example.ui.theme.StickerWhite
import com.example.ui.theme.StickerYellow
import com.example.viewmodel.LearningMode

@Composable
fun CourseScreen(
    progressState: UserProgressState,
    onSelectStep: (stepNumber: Int, mode: LearningMode) -> Unit,
    onResetStepProgress: (stepNumber: Int) -> Unit,
    onOpenNotationGuide: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    val themeColors = CuboMestreTheme.colors
    var showStudyFlowGuide by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("course_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("course_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar para a tela inicial",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = "Método de Camadas",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "As 7 etapas oficiais para resolver o 3×3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { showStudyFlowGuide = !showStudyFlowGuide },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (showStudyFlowGuide) {
                                    themeColors.turquoiseAccent.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .testTag("toggle_study_flow_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "Como estudar o curso",
                            tint = themeColors.turquoiseAccent
                        )
                    }

                    IconButton(
                        onClick = onOpenNotationGuide,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("course_notation_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = "Abrir Guia de Notação",
                            tint = themeColors.turquoiseAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Resumo de progresso geral
            val completedCount = progressState.completedSteps.size.coerceAtMost(7)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Seu Progresso: $completedCount / 7 Etapas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progressState.completionFraction },
                            color = themeColors.turquoiseAccent,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "${(progressState.completionFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = themeColors.turquoiseAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Orientação pedagógica discreta (opcional e dispensável)
            if (showStudyFlowGuide) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            themeColors.turquoiseAccent.copy(alpha = 0.45f),
                            RoundedCornerShape(16.dp)
                        )
                        .testTag("course_study_flow_banner")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Fluxo recomendado de estudo",
                                style = MaterialTheme.typography.titleSmall,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showStudyFlowGuide = false },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .testTag("dismiss_study_flow_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar orientação de estudo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "1. Escolha uma etapa • 2. Observe os casos no modo Aprender • " +
                                "3. Reproduza os movimentos no cubo virtual • 4. Experimente no modo Praticar • " +
                                "5. Marque a etapa como concluída quando compreender ou completar o objetivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lista das 7 Etapas em Cartões
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("course_steps_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(CurriculumRepository.steps, key = { it.number }) { step ->
                    val status = progressState.getStepStatus(step.number)
                    StepCourseCard(
                        step = step,
                        status = status,
                        onLearnClick = { onSelectStep(step.number, LearningMode.LEARN) },
                        onPracticeClick = { onSelectStep(step.number, LearningMode.PRACTICE) },
                        onRepeatClick = {
                            onResetStepProgress(step.number)
                            onSelectStep(step.number, LearningMode.LEARN)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCourseCard(
    step: CurriculumStep,
    status: StepStatus,
    onLearnClick: () -> Unit,
    onPracticeClick: () -> Unit,
    onRepeatClick: () -> Unit
) {
    val themeColors = CuboMestreTheme.colors
    val statusColor = when (status) {
        StepStatus.COMPLETED -> themeColors.successText
        StepStatus.IN_PROGRESS -> themeColors.turquoiseAccent
        StepStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (status == StepStatus.IN_PROGRESS) 1.5.dp else 1.dp,
                color = if (status == StepStatus.IN_PROGRESS) {
                    themeColors.turquoiseAccent.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onLearnClick() }
            .testTag("step_card_${step.number}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Ilustração visual da etapa no cubo 3x3
                StepMiniCubeDiagram(stepNumber = step.number)

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = themeColors.violetAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "ETAPA ${step.number}/7 • ${step.cases.size} ${if (step.cases.size == 1) "caso" else "casos"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.violetText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            color = statusColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                if (status == StepStatus.COMPLETED) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = status.labelPt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )

                    Text(
                        text = step.goalSimple,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botões de ação da Etapa: Aprender | Praticar | Repetir (rótulos concisos sem corte)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLearnClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.turquoiseAccent,
                        contentColor = themeColors.onTurquoiseAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("step_${step.number}_learn_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Aprender",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onPracticeClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("step_${step.number}_practice_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = themeColors.violetAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Praticar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                if (status == StepStatus.COMPLETED) {
                    IconButton(
                        onClick = onRepeatClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("step_${step.number}_repeat_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Repetir etapa concluída",
                            tint = themeColors.warningText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ilustração 3x3 representativa do objetivo visual de cada uma das 7 etapas do PDF.
 * Utiliza base escura dedicada de alto contraste e contorno nítido por adesivo para garantir
 * visibilidade imediata das peças brancas (Etapa 1 e Etapa 2) tanto no tema claro quanto no escuro.
 */
@Composable
private fun StepMiniCubeDiagram(stepNumber: Int) {
    val themeColors = CuboMestreTheme.colors
    val d = themeColors.miniCubeDimmedCell
    val w = StickerWhite
    val y = StickerYellow
    val b = StickerBlue
    val o = StickerOrange
    val g = StickerGreen
    val r = StickerRed

    val gridColors: List<Color> = when (stepNumber) {
        1 -> listOf(
            d, w, d,
            w, w, w,
            d, w, d
        )
        2 -> listOf(
            w, w, w,
            w, w, w,
            w, w, w
        )
        3 -> listOf(
            d, b, d,
            b, b, b,
            b, b, b
        )
        4 -> listOf(
            d, y, d,
            y, y, y,
            d, y, d
        )
        5 -> listOf(
            y, y, y,
            y, y, y,
            y, y, y
        )
        6 -> listOf(
            g, d, g,
            g, g, g,
            g, g, g
        )
        else -> listOf(
            b, o, y,
            g, w, r,
            y, b, o
        )
    }

    val strokeColor = themeColors.miniCubeStickerStroke

    Surface(
        color = themeColors.miniCubeSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .size(64.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .testTag("step_mini_cube_$stepNumber")
    ) {
        Canvas(modifier = Modifier.padding(8.dp)) {
            val cellSize = (size.minDimension - 8.dp.toPx()) / 3f
            val gap = 4.dp.toPx()
            val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            val strokeWidthPx = 1.dp.toPx()
            for (row in 0..2) {
                for (col in 0..2) {
                    val color = gridColors[row * 3 + col]
                    val topLeft = Offset(col * (cellSize + gap), row * (cellSize + gap))
                    val cellDim = Size(cellSize, cellSize)
                    drawRoundRect(
                        color = color,
                        topLeft = topLeft,
                        size = cellDim,
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = strokeColor,
                        topLeft = topLeft,
                        size = cellDim,
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidthPx)
                    )
                }
            }
        }
    }
}
