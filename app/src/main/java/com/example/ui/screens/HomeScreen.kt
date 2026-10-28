package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.curriculum.CurriculumRepository
import com.example.data.UserProgressState
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.ActiveLayerAnimation
import com.example.renderer.InteractiveCube3DView
import com.example.ui.theme.StickerBlue
import com.example.ui.theme.StickerGreen
import com.example.ui.theme.StickerOrange
import com.example.ui.theme.StickerRed
import com.example.ui.theme.StickerWhite
import com.example.ui.theme.StickerYellow
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TurquoisePrimary
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.HomePreviewUiState

@Composable
fun HomeScreen(
    progressState: UserProgressState,
    onStartLearningClick: () -> Unit,
    onContinueLessonClick: () -> Unit,
    onExploreCubeClick: () -> Unit,
    onOpenNotationGuide: () -> Unit,
    onToggleTheme: () -> Unit,
    homePreviewState: HomePreviewUiState = HomePreviewUiState(),
    onExecuteHomePreviewMove: (CubeMove) -> Unit = {},
    onRestoreHomePreviewCube: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentOnExecuteHomeMove by rememberUpdatedState(onExecuteHomePreviewMove)
    val currentOnRestoreHomeCube by rememberUpdatedState(onRestoreHomePreviewCube)
    val stableExecuteHomeMove = remember { { move: CubeMove -> currentOnExecuteHomeMove(move) } }
    val stableRestoreHomeCube = remember { { currentOnRestoreHomeCube() } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWideScreen = maxWidth >= 680.dp || (maxWidth > maxHeight && maxHeight < 520.dp)

        if (isWideScreen) {
            // Layout adaptativo horizontal (Paisagem / Tablet): Sem rolagem vertical, duas colunas equilibradas
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeHeaderBar(
                        isDarkTheme = progressState.isDarkTheme,
                        onToggleTheme = onToggleTheme
                    )

                    HomeHeroCubeViewport(
                        cubeState = homePreviewState.cubeState,
                        activeAnimation = homePreviewState.activeAnimation,
                        cameraResetCounter = homePreviewState.cameraResetCounter,
                        isDarkTheme = progressState.isDarkTheme,
                        onMoveSwiped = stableExecuteHomeMove,
                        onRestoreCube = stableRestoreHomeCube,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    StudentProgressBannerCard(
                        progressState = progressState,
                        onContinueLessonClick = onContinueLessonClick
                    )

                    HomePrimaryActionsSection(
                        onStartLearningClick = onStartLearningClick,
                        onExploreCubeClick = onExploreCubeClick,
                        onOpenNotationGuide = onOpenNotationGuide
                    )
                }
            }
        } else {
            // Layout retrato nativo fixo: Sem rolagem vertical! Tudo visível na primeira tela.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Cabeçalho compacto
                HomeHeaderBar(
                    isDarkTheme = progressState.isDarkTheme,
                    onToggleTheme = onToggleTheme
                )

                // 2. Prévia 3D Interativa Real (auto-rotação suave em repouso + giros físicos 3D de camada + botão Restaurar)
                HomeHeroCubeViewport(
                    cubeState = homePreviewState.cubeState,
                    activeAnimation = homePreviewState.activeAnimation,
                    cameraResetCounter = homePreviewState.cameraResetCounter,
                    isDarkTheme = progressState.isDarkTheme,
                    onMoveSwiped = stableExecuteHomeMove,
                    onRestoreCube = stableRestoreHomeCube,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // 3. Cartão de progresso e botão rápido para continuar última etapa
                StudentProgressBannerCard(
                    progressState = progressState,
                    onContinueLessonClick = onContinueLessonClick
                )

                // 4. Botões principais (Começar a Aprender, Explorar Cubo, Guia de Notação)
                HomePrimaryActionsSection(
                    onStartLearningClick = onStartLearningClick,
                    onExploreCubeClick = onExploreCubeClick,
                    onOpenNotationGuide = onOpenNotationGuide
                )
            }
        }
    }
}

@Composable
private fun HomeHeaderBar(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = TurquoisePrimary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                MiniCubeGridBadge()
            }

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("theme_toggle_button")
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Alternar tema claro/escuro",
                tint = TurquoisePrimary
            )
        }
    }
}

@Composable
private fun MiniCubeGridBadge() {
    val colors = listOf(
        StickerWhite, StickerRed, StickerBlue,
        StickerOrange, TurquoisePrimary, StickerGreen,
        StickerYellow, StickerWhite, VioletAccent
    )
    Canvas(modifier = Modifier.size(22.dp)) {
        val cell = size.width / 3f
        val pad = 1.2.dp.toPx()
        for (r in 0..2) {
            for (c in 0..2) {
                val color = colors[r * 3 + c]
                drawRect(
                    color = color,
                    topLeft = Offset(c * cell + pad, r * cell + pad),
                    size = Size(cell - pad * 2, cell - pad * 2)
                )
            }
        }
    }
}

@Composable
private fun HomeHeroCubeViewport(
    cubeState: CubeState,
    activeAnimation: ActiveLayerAnimation?,
    cameraResetCounter: Int,
    isDarkTheme: Boolean,
    onMoveSwiped: (CubeMove) -> Unit,
    onRestoreCube: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            InteractiveCube3DView(
                cubeState = cubeState,
                activeAnimation = activeAnimation,
                isDarkTheme = isDarkTheme,
                autoRotateIdle = true,
                allowFaceSwipeMoves = true,
                cameraResetTrigger = cameraResetCounter,
                showCameraControlsOverlay = true,
                onMoveSwiped = onMoveSwiped,
                modifier = Modifier.fillMaxSize()
            )

            // Selo compacto no canto superior esquerdo
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(SuccessEmerald)
                    )
                    Text(
                        text = "Cubo 3×3 Interativo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Botão discreto para restaurar o cubo da Home ao estado resolvido
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .border(
                        width = 1.dp,
                        color = TurquoisePrimary.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onRestoreCube() }
                    .testTag("home_restore_cube_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restaurar cubo inicial",
                        tint = TurquoisePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Restaurar",
                        style = MaterialTheme.typography.labelSmall,
                        color = TurquoisePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentProgressBannerCard(
    progressState: UserProgressState,
    onContinueLessonClick: () -> Unit
) {
    val completedCount = progressState.completedSteps.size
    val progressFraction = progressState.completionFraction
    val overallPercentage = (progressFraction * 100).toInt()
    val lastStep = CurriculumRepository.getStep(progressState.lastVisitedStep)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onContinueLessonClick() }
            .testTag("student_progress_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = Color.Gray.copy(alpha = 0.25f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = TurquoisePrimary,
                                startAngle = -90f,
                                sweepAngle = 360f * progressFraction,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "$overallPercentage%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TurquoisePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$completedCount de 7 etapas concluídas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Continuar: Etapa ${lastStep.number} — ${lastStep.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = TurquoisePrimary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("continue_lesson_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TurquoisePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Continuar",
                            style = MaterialTheme.typography.labelMedium,
                            color = TurquoisePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = TurquoisePrimary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun HomePrimaryActionsSection(
    onStartLearningClick: () -> Unit,
    onExploreCubeClick: () -> Unit,
    onOpenNotationGuide: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val fontScale = configuration.fontScale

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Mantém os dois botões secundários lado a lado quando houver largura suficiente para exibir
        // os rótulos completos sem reticências; empilha de forma enxuta em telas estreitas ou fontes ampliadas.
        val stackSecondaryButtons = maxWidth < 350.dp || fontScale > 1.15f
        val compactVertical = stackSecondaryButtons || fontScale > 1.25f

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compactVertical) 6.dp else 8.dp)
        ) {
            // Botão Primário — Começar a aprender
            Button(
                onClick = onStartLearningClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactVertical) 44.dp else 50.dp)
                    .testTag("start_learning_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp,
                    vertical = 0.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TurquoisePrimary,
                    contentColor = Color(0xFF00201C)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.size(if (compactVertical) 18.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_start_learning),
                    style = if (fontScale > 1.3f) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(if (compactVertical) 16.dp else 18.dp)
                )
            }

            if (stackSecondaryButtons) {
                // Em telas estreitas (ex.: 320dp) ou com fontes ampliadas (1.3x / 1.5x),
                // organiza os botões secundários em largura total para exibir 100% do texto sem corte.
                OutlinedButton(
                    onClick = onExploreCubeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("explore_cube_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 0.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = TurquoisePrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_explore_cube),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = onOpenNotationGuide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("notation_guide_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 0.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = VioletAccent,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_notation_guide),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                // Em larguras padrão com escala de fonte normal, mantém os dois botões lado a lado
                // com padding horizontal ajustado para que os nomes completos nunca sejam truncados.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExploreCubeClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("explore_cube_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = TurquoisePrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_explore_cube),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenNotationGuide,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("notation_guide_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = VioletAccent,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_notation_guide),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
