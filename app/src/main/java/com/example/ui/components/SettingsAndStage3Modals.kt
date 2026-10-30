package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AppLanguage
import com.example.data.UserProgressState
import com.example.ui.screens.LanguageSelectionOptionCard
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.TurquoisePrimary
import com.example.ui.theme.VioletAccent

/**
 * Modal de Configurações e Idioma do Menu Principal (Etapa 1).
 * Permite alternar a qualquer momento entre Português (pt-BR) e English (en-US),
 * alternar tema claro/escuro, ajustar velocidade didática e rever a tela de boas-vindas.
 */
@Composable
fun SettingsDialogModal(
    progressState: UserProgressState,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAnimationSpeed: (Float) -> Unit,
    onToggleHighlightPieces: () -> Unit,
    onReopenWelcomeScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = CuboMestreTheme.strings
    val themeColors = CuboMestreTheme.colors
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("settings_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabeçalho
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rounded_settings),
                            contentDescription = null,
                            tint = themeColors.turquoiseAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.settingsModalTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("settings_modal_title")
                        )
                        Text(
                            text = strings.settingsModalSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. Seção de Idioma (en-US / pt-BR)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.settingsLanguageSectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    LanguageSelectionOptionCard(
                        language = AppLanguage.EN_US,
                        isSelected = progressState.appLanguage == AppLanguage.EN_US,
                        onClick = { onSelectLanguage(AppLanguage.EN_US) },
                        testTag = "settings_lang_en_us"
                    )
                    LanguageSelectionOptionCard(
                        language = AppLanguage.PT_BR,
                        isSelected = progressState.appLanguage == AppLanguage.PT_BR,
                        onClick = { onSelectLanguage(AppLanguage.PT_BR) },
                        testTag = "settings_lang_pt_br"
                    )
                }

                // 2. Seção de Tema Visual (Escuro Neon / Claro Alto Contraste)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.settingsThemeSectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionChip(
                            label = strings.settingsThemeDarkLabel,
                            iconRes = R.drawable.ic_rounded_dark_mode,
                            isSelected = progressState.isDarkTheme,
                            onClick = { onSetDarkTheme(true) },
                            testTag = "settings_theme_dark",
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = strings.settingsThemeLightLabel,
                            iconRes = R.drawable.ic_rounded_light_mode,
                            isSelected = !progressState.isDarkTheme,
                            onClick = { onSetDarkTheme(false) },
                            testTag = "settings_theme_light",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Velocidade da demonstração e Destaque de Peças
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.settingsSpeedSectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.5f to "0.5x", 1.0f to "1.0x", 1.5f to "1.5x").forEach { (speedVal, label) ->
                            val isSelected = kotlin.math.abs(progressState.animationSpeedMultiplier - speedVal) < 0.1f
                            Surface(
                                color = if (isSelected) {
                                    themeColors.turquoiseAccent
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable { onSetAnimationSpeed(speedVal) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) {
                                            themeColors.onTurquoiseAccent
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.settingsHighlightPiecesLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.settingsHighlightPiecesSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = progressState.highlightTargetPieces,
                            onCheckedChange = { onToggleHighlightPieces() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = themeColors.onTurquoiseAccent,
                                checkedTrackColor = themeColors.turquoiseAccent
                            )
                        )
                    }
                }

                // 4. Botão para rever a Tela de Boas-Vindas e Idioma
                OutlinedButton(
                    onClick = onReopenWelcomeScreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("reopen_welcome_screen_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_language),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.settingsReopenWelcomeButton,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("close_settings_modal_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.turquoiseAccent,
                        contentColor = themeColors.onTurquoiseAccent
                    )
                ) {
                    Text(
                        text = strings.settingsDoneButton,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val themeColors = CuboMestreTheme.colors
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(44.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) themeColors.turquoiseAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (isSelected) themeColors.turquoiseAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Modal informativo da futura funcionalidade "Resolver meu cubo / Solve My Cube" (Etapa 3).
 * Explica com transparência as duas modalidades planejadas (Câmera e Manual) sem aparentar
 * funcionalidade quebrada, oferecendo atalhos imediatos para Aprender ou Explorar o Cubo 3D.
 */
@Composable
fun SolveMyCubeStage3Modal(
    onStartLearnClick: () -> Unit,
    onExploreCubeClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = CuboMestreTheme.strings
    val themeColors = CuboMestreTheme.colors
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(TurquoisePrimary, VioletAccent)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("solve_my_cube_roadmap_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = themeColors.violetAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rounded_auto_awesome),
                            contentDescription = null,
                            tint = themeColors.turquoiseAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = strings.solveModalBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = strings.solveModalTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("solve_modal_title")
                )

                Text(
                    text = strings.solveModalDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Modalidade 1: Câmera
                Stage3ModalityPreviewItem(
                    iconRes = R.drawable.ic_rounded_camera_cube,
                    title = strings.solveModalCameraTitle,
                    description = strings.solveModalCameraBody,
                    accentColor = themeColors.turquoiseAccent,
                    testTag = "solve_modal_camera_card"
                )

                // Modalidade 2: Manual
                Stage3ModalityPreviewItem(
                    iconRes = R.drawable.ic_rounded_grid_manual,
                    title = strings.solveModalManualTitle,
                    description = strings.solveModalManualBody,
                    accentColor = themeColors.violetText,
                    testTag = "solve_modal_manual_card"
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onStartLearnClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("solve_modal_start_learn_btn"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.turquoiseAccent,
                        contentColor = themeColors.onTurquoiseAccent
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_school),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.solveModalTryLearnNow,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onExploreCubeClick()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("solve_modal_explore_btn"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(
                            text = strings.exploreCubeButton,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("close_solve_preview_modal_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(
                            text = strings.solveModalCloseButton,
                            style = MaterialTheme.typography.labelLarge,
                            color = themeColors.turquoiseAccent,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stage3ModalityPreviewItem(
    iconRes: Int,
    title: String,
    description: String,
    accentColor: Color,
    testTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
