package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CubeMove
import com.example.renderer.InteractiveCube3DView
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.ExploreUiState

/**
 * Rastreador leve de recomposições do Modo Explorar para comparação técnica com o Modo Praticar.
 */
object ExploreRecompositionTracker {
    var viewportRecompositions: Int = 0
    var controlsRecompositions: Int = 0

    fun reset() {
        viewportRecompositions = 0
        controlsRecompositions = 0
    }
}

internal val EXPLORE_FAMILIES = listOf("F", "R", "U", "L", "D", "B", "M", "E", "S")

/**
 * Resolve explicitamente o movimento do cubo a partir da família estática do Modo Explorar
 * e do modo modificador (0 = normal, 1 = inverso ', 2 = 180° 2).
 * Falha de forma explícita (IllegalArgumentException via requireNotNull) caso haja erro de
 * configuração estática interna, evitando ocultar silenciosamente botões na interface.
 */
internal fun resolveExploreMove(family: String, modifierMode: Int): CubeMove {
    val notation = when (modifierMode) {
        0 -> family
        1 -> "$family'"
        2 -> "${family}2"
        else -> throw IllegalArgumentException(
            "Modo modificador inválido no Modo Explorar: $modifierMode (esperado 0, 1 ou 2)."
        )
    }
    return requireNotNull(CubeMove.fromNotation(notation)) {
        "Configuração interna inválida em EXPLORE_FAMILIES: notação '$notation' (família='$family', modificador=$modifierMode) não corresponde a um CubeMove válido."
    }
}

@Composable
fun ExploreScreen(
    exploreState: ExploreUiState,
    isDarkTheme: Boolean,
    onExecuteMove: (CubeMove) -> Unit,
    onUndoMove: () -> Unit,
    onScrambleCube: () -> Unit,
    onResetCubeSolved: () -> Unit,
    onRecenterCamera: () -> Unit,
    onOpenNotationGuide: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    val currentOnExecuteMove by rememberUpdatedState(onExecuteMove)
    val currentOnUndoMove by rememberUpdatedState(onUndoMove)
    val currentOnScrambleCube by rememberUpdatedState(onScrambleCube)
    val currentOnResetCubeSolved by rememberUpdatedState(onResetCubeSolved)
    val currentOnRecenterCamera by rememberUpdatedState(onRecenterCamera)
    val currentOnOpenNotationGuide by rememberUpdatedState(onOpenNotationGuide)
    val currentOnBackToHome by rememberUpdatedState(onBackToHome)

    val stableExecuteMove = remember { { m: CubeMove -> currentOnExecuteMove(m) } }
    val stableUndoMove = remember { { currentOnUndoMove() } }
    val stableScrambleCube = remember { { currentOnScrambleCube() } }
    val stableResetCubeSolved = remember { { currentOnResetCubeSolved() } }
    val stableRecenterCamera = remember { { currentOnRecenterCamera() } }
    val stableOpenNotationGuide = remember { { currentOnOpenNotationGuide() } }
    val stableBackToHome = remember { { currentOnBackToHome() } }

    val isCubeSolved = remember(exploreState.cubeState) { exploreState.cubeState.isSolved() }
    val canUndo = exploreState.moveHistory.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("explore_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Cabeçalho Compacto e Fixo
            ExploreCompactHeader(
                feedbackMessage = exploreState.feedbackMessage,
                onBackToHome = stableBackToHome,
                onOpenNotationGuide = stableOpenNotationGuide
            )

            // 2. Faixa dedicada de status do cubo (separada do viewport 3D para evitar sobreposição com active_move_banner)
            ExploreStatusStrip(
                isCubeSolved = isCubeSolved,
                moveCount = exploreState.moveHistory.size
            )

            // 3. Área Principal do Cubo 3D (elástica, ocupando o centro da tela sem exigir rolagem)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SideEffect {
                    ExploreRecompositionTracker.viewportRecompositions++
                }

                InteractiveCube3DView(
                    cubeState = exploreState.cubeState,
                    activeAnimation = exploreState.activeAnimation,
                    isDarkTheme = isDarkTheme,
                    autoRotateIdle = false,
                    allowFaceSwipeMoves = true,
                    cameraResetTrigger = exploreState.cameraResetCounter,
                    showCameraControlsOverlay = true,
                    onMoveSwiped = stableExecuteMove,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 4. Painel Fixo de Comandos de Camadas e Ações Rápidas (isolado de recomposições de quadros intermediários)
            ExploreControlsCard(
                canUndo = canUndo,
                onExecuteMove = stableExecuteMove,
                onUndoMove = stableUndoMove,
                onScrambleCube = stableScrambleCube,
                onResetCubeSolved = stableResetCubeSolved,
                onRecenterCamera = stableRecenterCamera
            )
        }
    }
}

@Composable
private fun ExploreStatusStrip(
    isCubeSolved: Boolean,
    moveCount: Int
) {
    val themeColors = CuboMestreTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (isCubeSolved) {
                themeColors.successBadgeBg.copy(alpha = 0.96f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.testTag("explore_status_badge")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                if (isCubeSolved) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = if (isCubeSolved) {
                        "Cubo Resolvido • $moveCount mov."
                    } else {
                        "Em exploração • $moveCount mov."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCubeSolved) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExploreCompactHeader(
    feedbackMessage: String?,
    onBackToHome: () -> Unit,
    onOpenNotationGuide: () -> Unit
) {
    val themeColors = CuboMestreTheme.colors
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
                    .testTag("explore_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar ao início",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column {
                Text(
                    text = "Explorar Cubo 3D",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = feedbackMessage ?: "Arraste camadas ou gire a câmera livremente",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (feedbackMessage != null) themeColors.turquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onOpenNotationGuide,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("explore_notation_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = "Guia de Notação",
                tint = themeColors.turquoiseAccent
            )
        }
    }
}

@Composable
private fun ExploreControlsCard(
    canUndo: Boolean,
    onExecuteMove: (CubeMove) -> Unit,
    onUndoMove: () -> Unit,
    onScrambleCube: () -> Unit,
    onResetCubeSolved: () -> Unit,
    onRecenterCamera: () -> Unit
) {
    SideEffect {
        ExploreRecompositionTracker.controlsRecompositions++
    }

    val themeColors = CuboMestreTheme.colors
    var modifierMode by remember { mutableIntStateOf(0) } // 0 = Normal (90°), 1 = Inverso ('), 2 = Meia-volta (2)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Linha 1: Seletor de sentido (90° Horário / Inverso ' / 180° Duplo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Camadas",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(3.dp)
                ) {
                    val modes = listOf("90°" to 0, "Inverso (')" to 1, "180° (2)" to 2)
                    modes.forEach { (label, idx) ->
                        val selected = modifierMode == idx
                        Surface(
                            color = if (selected) themeColors.turquoiseAccent else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { modifierMode = idx }
                                .testTag("explore_mod_$idx")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Linha 2: Botões das 9 famílias (F, R, U, L, D, B, M, E, S)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                EXPLORE_FAMILIES.forEach { fam ->
                    val move = resolveExploreMove(fam, modifierMode)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.dp, themeColors.turquoiseAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .clickable { onExecuteMove(move) }
                            .testTag("explore_move_btn_${move.notation}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = move.notation,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = themeColors.turquoiseAccent,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = famLabelShort(fam),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Linha 3: Embaralhar, Desfazer, Câmera e Restaurar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onScrambleCube,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.violetAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("scramble_cube_button")
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Embaralhar",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onUndoMove,
                    enabled = canUndo,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("undo_explore_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = if (canUndo) themeColors.turquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Desfazer",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onRecenterCamera,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("explore_recenter_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Câmera",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onResetCubeSolved,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("restore_solved_cube_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = themeColors.successText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Restaurar",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun famLabelShort(family: String): String = when (family) {
    "F" -> "Frente"
    "R" -> "Direita"
    "U" -> "Cima"
    "L" -> "Esq."
    "D" -> "Baixo"
    "B" -> "Trás"
    "M" -> "Meio V"
    "E" -> "Meio H"
    "S" -> "Meio Z"
    else -> family
}
