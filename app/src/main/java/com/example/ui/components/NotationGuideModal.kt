package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.curriculum.CurriculumRepository
import com.example.curriculum.NotationGuideGroup
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.renderer.ActiveLayerAnimation
import com.example.renderer.InteractiveCube3DView
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun NotationGuideModal(
    selectedMove: CubeMove,
    previewCubeState: CubeState,
    previewAnimation: ActiveLayerAnimation?,
    isDarkTheme: Boolean,
    onSelectMove: (CubeMove) -> Unit,
    onDismiss: () -> Unit,
    cameraResetCounter: Int = 0,
    onResetCube: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .widthIn(max = 760.dp)
                .testTag("notation_guide_modal"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                val themeColors = CuboMestreTheme.colors
                val strings = CuboMestreTheme.strings
                val isLandscape = maxWidth > maxHeight && maxHeight < 540.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Cabeçalho compacto do Guia de Notação
                    NotationModalHeader(onDismiss = onDismiss)

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Orientação pedagógica curta
                    Surface(
                        color = themeColors.turquoiseAccent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                themeColors.turquoiseAccent.copy(alpha = 0.40f),
                                RoundedCornerShape(10.dp)
                            )
                            .testTag("notation_orientation_banner")
                    ) {
                        Text(
                            text = strings.notationOrientationBanner,
                            style = MaterialTheme.typography.labelMedium,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("notation_orientation_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLandscape) {
                        // Em paisagem: Área 3D e painel do movimento selecionado à esquerda; lista rolável à direita
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.46f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Notation3DPreviewViewport(
                                    selectedMove = selectedMove,
                                    previewCubeState = previewCubeState,
                                    previewAnimation = previewAnimation,
                                    isDarkTheme = isDarkTheme,
                                    cameraResetCounter = cameraResetCounter,
                                    onSelectMove = onSelectMove,
                                    onResetCube = onResetCube,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )

                                NotationSelectedMoveCard(
                                    selectedMove = selectedMove,
                                    onReplayMove = { onSelectMove(selectedMove) }
                                )
                            }

                            NotationScrollableListSection(
                                selectedMove = selectedMove,
                                onSelectMove = onSelectMove,
                                modifier = Modifier
                                    .weight(0.54f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        // Em retrato:
                        // ÁREA SUPERIOR FIXA (Cubo 3D 100% desobstruído + Barra de identificação/repetição fora do cubo)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notation_3d_preview_area"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Notation3DPreviewViewport(
                                selectedMove = selectedMove,
                                previewCubeState = previewCubeState,
                                previewAnimation = previewAnimation,
                                isDarkTheme = isDarkTheme,
                                cameraResetCounter = cameraResetCounter,
                                onSelectMove = onSelectMove,
                                onResetCube = onResetCube,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                            )

                            // Barra dedicada FORA do viewport 3D (zero sobreposição com o cubo!)
                            NotationSelectedMoveCard(
                                selectedMove = selectedMove,
                                onReplayMove = { onSelectMove(selectedMove) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ÁREA INFERIOR ROLÁVEL (Lista das 9 famílias: F, R, U, L, D, B, M, E, S)
                        NotationScrollableListSection(
                            selectedMove = selectedMove,
                            onSelectMove = onSelectMove,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotationModalHeader(onDismiss: () -> Unit) {
    val strings = CuboMestreTheme.strings
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.notationModalTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.notationModalSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("close_notation_modal_button")
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = strings.notationCloseContentDesc,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun Notation3DPreviewViewport(
    selectedMove: CubeMove,
    previewCubeState: CubeState,
    previewAnimation: ActiveLayerAnimation?,
    isDarkTheme: Boolean,
    cameraResetCounter: Int,
    onSelectMove: (CubeMove) -> Unit,
    onResetCube: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .testTag("notation_cube_viewport_box")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            InteractiveCube3DView(
                cubeState = previewCubeState,
                activeAnimation = previewAnimation,
                guidedMove = selectedMove,
                isDarkTheme = isDarkTheme,
                autoRotateIdle = false,
                allowFaceSwipeMoves = true,
                cameraResetTrigger = cameraResetCounter,
                showCameraControlsOverlay = true,
                onMoveSwiped = { swipedMove -> onSelectMove(swipedMove) },
                modifier = Modifier.fillMaxSize()
            )

            // Botão discreto no canto superior direito para restaurar o cubo e a câmera
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .border(
                        width = 1.dp,
                        color = themeColors.turquoiseAccent.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onResetCube() }
                    .testTag("notation_restore_cube_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = strings.notationRestoreContentDesc,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = strings.notationRestoreButton,
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.turquoiseAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NotationSelectedMoveCard(
    selectedMove: CubeMove,
    onReplayMove: () -> Unit
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                themeColors.turquoiseAccent.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp)
            )
            .testTag("notation_selected_move_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "${selectedMove.notation} — ${selectedMove.localizedTitle(strings.language)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = themeColors.turquoiseAccent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("notation_selected_move_title")
                )
                Text(
                    text = selectedMove.localizedExplanation(strings.language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("notation_selected_move_explanation")
                )
            }

            Button(
                onClick = onReplayMove,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.turquoiseAccent,
                    contentColor = themeColors.onTurquoiseAccent
                ),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("notation_repeat_move_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = strings.notationRepeatContentDesc,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = strings.notationRepeatButton,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NotationScrollableListSection(
    selectedMove: CubeMove,
    onSelectMove: (CubeMove) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = CuboMestreTheme.strings
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag("notation_list_scroll"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Regras de sufixo (' e 2)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = strings.notationHowToReadTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = strings.notationHowToReadBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Cartões de cada família de movimento (F, R, U, L, D, B, M, E, S)
        CurriculumRepository.notationGroups.forEach { group ->
            NotationFamilyCard(
                group = group,
                selectedMove = selectedMove,
                onSelectMove = onSelectMove
            )
        }
    }
}

@Composable
private fun NotationFamilyCard(
    group: NotationGuideGroup,
    selectedMove: CubeMove,
    onSelectMove: (CubeMove) -> Unit
) {
    val themeColors = CuboMestreTheme.colors
    val strings = CuboMestreTheme.strings
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notation_family_card_${group.family}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${group.family} — ${group.localizedName(strings.language)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = group.localizedDescription(strings.language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.moves.forEach { move ->
                    val isSelected = move == selectedMove
                    Surface(
                        color = if (isSelected) themeColors.turquoiseAccent else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) themeColors.turquoiseAccent else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectMove(move) }
                            .testTag("notation_btn_${move.notation}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = move.notation,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) themeColors.onTurquoiseAccent else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall
                            )
                            val sub = when {
                                move.notation.endsWith("'") -> strings.notationSubCounterClockwise
                                move.notation.endsWith("2") -> strings.notationSubDouble180
                                else -> strings.notationSubClockwise
                            }
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) themeColors.onTurquoiseAccent else themeColors.violetText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
