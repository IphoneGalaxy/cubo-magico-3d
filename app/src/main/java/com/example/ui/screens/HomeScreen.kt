package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.curriculum.CurriculumRepository
import com.example.data.AppLanguage
import com.example.data.UserProgressState
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.ActiveLayerAnimation
import com.example.renderer.ActiveMoveIndicatorBanner
import com.example.renderer.InteractiveCube3DView
import com.example.ui.theme.CuboMestreTheme
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
    onPracticeClick: () -> Unit = onContinueLessonClick,
    onOpenSettingsClick: () -> Unit = {},
    onToggleLanguageQuickClick: () -> Unit = {},
    onOpenSolveMyCubePreview: () -> Unit = {},
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
        val fontScale = LocalConfiguration.current.fontScale
        val isWideScreen = maxWidth >= 680.dp || (maxWidth > maxHeight && maxHeight < 520.dp)
        val isCompactVertical = maxHeight < 760.dp || fontScale > 1.15f

        if (isWideScreen) {
            // Layout adaptativo horizontal (Paisagem / Tablet): Sem rolagem vertical, duas colunas equilibradas
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeHeaderBar(
                        isDarkTheme = progressState.isDarkTheme,
                        appLanguage = progressState.appLanguage,
                        isCompact = true,
                        onToggleLanguage = onToggleLanguageQuickClick,
                        onOpenSettings = onOpenSettingsClick,
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
                    SolveMyCubeHeroCard(
                        isCompact = true,
                        onOpenPreview = onOpenSolveMyCubePreview
                    )

                    StudentProgressBannerCard(
                        progressState = progressState,
                        isCompact = true,
                        onContinueLessonClick = onContinueLessonClick,
                        onPracticeClick = onPracticeClick
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
                    .padding(
                        horizontal = if (maxWidth < 340.dp) 12.dp else 16.dp,
                        vertical = if (isCompactVertical) 6.dp else 10.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isCompactVertical) 6.dp else 8.dp)
            ) {
                // 1. Cabeçalho do Menu Principal (Identidade visual, idioma, configurações e tema)
                HomeHeaderBar(
                    isDarkTheme = progressState.isDarkTheme,
                    appLanguage = progressState.appLanguage,
                    isCompact = isCompactVertical,
                    onToggleLanguage = onToggleLanguageQuickClick,
                    onOpenSettings = onOpenSettingsClick,
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

                // 3. Cartão Principal em Destaque — Resolver meu cubo / Solve My Cube (Etapa 3: Câmera & Manual)
                SolveMyCubeHeroCard(
                    isCompact = isCompactVertical,
                    onOpenPreview = onOpenSolveMyCubePreview
                )

                // 4. Cartão de progresso, botão Continuar e acesso rápido ao Modo Praticar
                StudentProgressBannerCard(
                    progressState = progressState,
                    isCompact = isCompactVertical,
                    onContinueLessonClick = onContinueLessonClick,
                    onPracticeClick = onPracticeClick
                )

                // 5. Botões principais do Hub (Começar a Aprender, Explorar Cubo, Guia de Notação)
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
    appLanguage: AppLanguage,
    isCompact: Boolean,
    onToggleLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCompact) 36.dp else 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(themeColors.turquoiseAccent, themeColors.violetAccent)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                VibrantCubeEmblemCanvas(modifier = Modifier.size(if (isCompact) 20.dp else 22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strings.homeSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Seletor rápido de idioma (PT / EN)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .height(36.dp)
                    .border(
                        width = 1.dp,
                        color = themeColors.turquoiseAccent.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onToggleLanguage() }
                    .testTag("language_selector_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 9.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_language),
                        contentDescription = strings.settingsLanguageSectionTitle,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = appLanguage.shortCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Acesso rápido a Configurações
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("home_settings_button")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rounded_settings),
                    contentDescription = strings.settingsButton,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Alternador rápido de Tema Claro / Escuro
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isDarkTheme) R.drawable.ic_rounded_light_mode else R.drawable.ic_rounded_dark_mode
                    ),
                    contentDescription = "Alternar tema claro/escuro",
                    tint = themeColors.turquoiseAccent,
                    modifier = Modifier.size(18.dp)
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
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            val density = LocalDensity.current
            val fontScale = density.fontScale
            val isCompactViewport = maxWidth < 340.dp || fontScale > 1.2f
            val topOverlayScale = when {
                maxWidth < 310.dp -> fontScale.coerceAtMost(1.08f)
                maxWidth < 350.dp -> fontScale.coerceAtMost(1.16f)
                else -> fontScale.coerceAtMost(1.25f)
            }
            val topBadgeFontSize = with(density) { (11.dp.toPx() * topOverlayScale).toSp() }
            val pillHorizontalPad = if (isCompactViewport) 8.dp else 10.dp

            InteractiveCube3DView(
                cubeState = cubeState,
                activeAnimation = activeAnimation,
                isDarkTheme = isDarkTheme,
                autoRotateIdle = true,
                allowFaceSwipeMoves = true,
                cameraResetTrigger = cameraResetCounter,
                showCameraControlsOverlay = true,
                showActiveMoveBanner = false,
                onMoveSwiped = onMoveSwiped,
                modifier = Modifier.fillMaxSize()
            )

            // Cabeçalho superior estruturado em duas faixas sem sobreposição:
            // 1ª faixa fixa: selo "Cubo 3×3 Interativo" à esquerda e ação "Restaurar" à direita.
            // 2ª faixa dedicada (abaixo da 1ª faixa): indicador temporário do movimento em execução.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompactViewport) 8.dp else 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selo compacto no canto superior esquerdo
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                            .testTag("home_interactive_cube_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactViewport) 5.dp else 6.dp),
                            modifier = Modifier.padding(horizontal = pillHorizontalPad, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SuccessEmerald)
                            )
                            Text(
                                text = strings.interactiveCubeBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = topBadgeFontSize,
                                    lineHeight = topBadgeFontSize * 1.2f
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Botão discreto para restaurar o cubo da Home ao estado resolvido
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = themeColors.turquoiseAccent.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onRestoreCube() }
                            .testTag("home_restore_cube_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = pillHorizontalPad, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.restoreCubeButton,
                                tint = themeColors.turquoiseAccent,
                                modifier = Modifier.size(if (isCompactViewport) 13.dp else 14.dp)
                            )
                            Text(
                                text = strings.restoreCubeButton,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = topBadgeFontSize,
                                    lineHeight = topBadgeFontSize * 1.2f
                                ),
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                val bannerMove = activeAnimation?.move
                AnimatedVisibility(
                    visible = bannerMove != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    bannerMove?.let { move ->
                        ActiveMoveIndicatorBanner(move = move)
                    }
                }
            }
        }
    }
}

/**
 * 8.2 Cartão Principal em Destaque — "Resolver meu cubo / Solve My Cube" (Etapa 3).
 * Apresenta visualmente as duas modalidades planejadas (Resolver com câmera / Resolver manualmente),
 * claramente identificadas como recurso da Etapa 3.
 */
@Composable
private fun SolveMyCubeHeroCard(
    isCompact: Boolean,
    onOpenPreview: () -> Unit
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        TurquoisePrimary.copy(alpha = 0.65f),
                        VioletAccent.copy(alpha = 0.65f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onOpenPreview() }
            .testTag("solve_my_cube_hero_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_auto_awesome),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.solveMyCubeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = themeColors.violetAccent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("solve_my_cube_stage3_badge")
                ) {
                    Text(
                        text = strings.solveMyCubeStage3Badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.violetText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Duas modalidades planejadas para a Etapa 3: Câmera e Manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SolveModalityPreviewChip(
                    iconRes = R.drawable.ic_rounded_camera_cube,
                    label = strings.solveWithCameraLabel,
                    accentColor = themeColors.turquoiseAccent,
                    onClick = onOpenPreview,
                    testTag = "solve_with_camera_preview_chip",
                    modifier = Modifier.weight(1f)
                )
                SolveModalityPreviewChip(
                    iconRes = R.drawable.ic_rounded_grid_manual,
                    label = strings.solveManuallyLabel,
                    accentColor = themeColors.violetText,
                    onClick = onOpenPreview,
                    testTag = "solve_manually_preview_chip",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SolveModalityPreviewChip(
    iconRes: Int,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudentProgressBannerCard(
    progressState: UserProgressState,
    isCompact: Boolean,
    onContinueLessonClick: () -> Unit,
    onPracticeClick: () -> Unit
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    val completedCount = progressState.completedSteps.size
    val progressFraction = progressState.completionFraction
    val overallPercentage = (progressFraction * 100).toInt()
    val lastStep = CurriculumRepository.getStep(progressState.lastVisitedStep)
    val trackArcColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val activeArcColor = themeColors.turquoiseAccent

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onContinueLessonClick() }
            .testTag("student_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = if (isCompact) 6.dp else 8.dp
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(if (isCompact) 36.dp else 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = trackArcColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = activeArcColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progressFraction,
                                useCenter = false,
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "$overallPercentage%",
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.stagesCompletedFormat(completedCount),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.continueStageFormat(lastStep.number, lastStep.title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Botão rápido de Praticar (Etapa 1 Hub)
                    Surface(
                        color = themeColors.violetAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { onPracticeClick() }
                            .testTag("home_practice_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_rounded_practice),
                                contentDescription = null,
                                tint = themeColors.violetText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = strings.practiceButton,
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.violetText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Chip Continuar aula
                    Surface(
                        color = themeColors.turquoiseAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("continue_lesson_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_rounded_play),
                                contentDescription = null,
                                tint = themeColors.turquoiseAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = strings.continueButton,
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.turquoiseAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 6.dp))

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50)),
                color = themeColors.turquoiseAccent,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
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
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    val configuration = LocalConfiguration.current
    val fontScale = configuration.fontScale

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Mantém os dois botões secundários lado a lado quando houver largura suficiente para exibir
        // os rótulos completos sem reticências; empilha de forma enxuta em telas estreitas ou fontes ampliadas.
        val stackSecondaryButtons = maxWidth < 350.dp || fontScale > 1.15f
        val compactVertical = stackSecondaryButtons || fontScale > 1.25f

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compactVertical) 5.dp else 7.dp)
        ) {
            // Botão Primário — Começar a aprender / Start learning
            Button(
                onClick = onStartLearningClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactVertical) 42.dp else 46.dp)
                    .testTag("start_learning_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(
                    horizontal = 14.dp,
                    vertical = 0.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.turquoiseAccent,
                    contentColor = themeColors.onTurquoiseAccent
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rounded_school),
                    contentDescription = null,
                    modifier = Modifier.size(if (compactVertical) 18.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.startLearningButton,
                    style = if (compactVertical || fontScale > 1.3f) {
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
                        .height(38.dp)
                        .testTag("explore_cube_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 0.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_explore_3d),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.exploreCubeButton,
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
                        .height(38.dp)
                        .testTag("notation_guide_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 0.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_notation_book),
                        contentDescription = null,
                        tint = themeColors.violetAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.notationGuideButton,
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
                            .height(44.dp)
                            .testTag("explore_cube_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(
                            horizontal = 10.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rounded_explore_3d),
                            contentDescription = null,
                            tint = themeColors.turquoiseAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.exploreCubeButton,
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
                            .height(44.dp)
                            .testTag("notation_guide_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(
                            horizontal = 10.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rounded_notation_book),
                            contentDescription = null,
                            tint = themeColors.violetAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.notationGuideButton,
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
