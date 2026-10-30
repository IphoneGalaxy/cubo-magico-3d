package com.example.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppLanguage
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.TurquoisePrimary
import com.example.ui.theme.VioletAccent

/**
 * Tela de Boas-Vindas e Escolha de Idioma na Primeira Execução (Etapa 1).
 * Permite escolher entre English (United States) [en-US] e Português (Brasil) [pt-BR],
 * atualizando instantaneamente os textos da tela e salvando a escolha no DataStore ao tocar em Começar / Get Started.
 */
@Composable
fun WelcomeLanguageScreen(
    selectedLanguage: AppLanguage,
    isDarkTheme: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onConfirmStart: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStrings = stringsForLanguage(selectedLanguage)
    val themeColors = CuboMestreTheme.colors
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("welcome_language_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompact = maxWidth < 350.dp || maxHeight < 640.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = if (isCompact) 16.dp else 22.dp, vertical = if (isCompact) 14.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp)
        ) {
            // 1. Selo de boas-vindas
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = themeColors.turquoiseAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50)
                    )
                    .testTag("welcome_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_language),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = activeStrings.welcomeBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.turquoiseAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. Cartão de identidade visual com emblema 3D e mensagem acolhedora
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.6f else 0.4f),
                                VioletAccent.copy(alpha = if (isDarkTheme) 0.6f else 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = if (isCompact) 14.dp else 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 62.dp else 72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(listOf(TurquoisePrimary, VioletAccent)),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IsometricCubeHeroIllustration(
                            modifier = Modifier.size(if (isCompact) 42.dp else 48.dp)
                        )
                    }

                    Text(
                        text = activeStrings.welcomeTitle,
                        style = if (isCompact) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("welcome_title_text")
                    )

                    Text(
                        text = activeStrings.welcomeSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("welcome_subtitle_text")
                    )

                    // Pílulas visuais de recursos do app
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WelcomeFeatureChip(
                            iconRes = R.drawable.ic_rounded_cube_3d,
                            text = activeStrings.welcomeFeatureInteractive,
                            tint = themeColors.turquoiseAccent,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        WelcomeFeatureChip(
                            iconRes = R.drawable.ic_rounded_school,
                            text = activeStrings.welcomeFeatureMethod,
                            tint = themeColors.violetText,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // 3. Seletor de Idioma (English en-US e Português pt-BR)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = activeStrings.welcomeSelectLanguageTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("welcome_select_language_heading")
                )

                LanguageSelectionOptionCard(
                    language = AppLanguage.EN_US,
                    isSelected = selectedLanguage == AppLanguage.EN_US,
                    onClick = { onSelectLanguage(AppLanguage.EN_US) },
                    testTag = "language_option_en_us"
                )

                LanguageSelectionOptionCard(
                    language = AppLanguage.PT_BR,
                    isSelected = selectedLanguage == AppLanguage.PT_BR,
                    onClick = { onSelectLanguage(AppLanguage.PT_BR) },
                    testTag = "language_option_pt_br"
                )

                Text(
                    text = activeStrings.welcomeSelectLanguageHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Botão de confirmação: Começar / Get Started
            Button(
                onClick = { onConfirmStart(selectedLanguage) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_get_started_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.turquoiseAccent,
                    contentColor = themeColors.onTurquoiseAccent
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rounded_play),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeStrings.welcomeGetStartedButton,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WelcomeFeatureChip(
    iconRes: Int,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
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
fun LanguageSelectionOptionCard(
    language: AppLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val themeColors = CuboMestreTheme.colors
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isSelected) {
        themeColors.turquoiseAccent
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                themeColors.turquoiseAccent.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = language.shortCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            themeColors.turquoiseAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = language.nativeSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSelected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rounded_check_circle),
                    contentDescription = null,
                    tint = themeColors.turquoiseAccent,
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("${testTag}_selected_icon")
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}
