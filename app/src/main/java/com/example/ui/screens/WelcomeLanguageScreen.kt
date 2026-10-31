package com.example.ui.screens

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurface
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.StickerBlue
import com.example.ui.theme.TurquoisePrimary
import com.example.ui.theme.VioletAccent

/**
 * Tela de Boas-Vindas e Escolha de Idioma na Primeira Execução (Etapa 1 Refinada).
 *
 * - Permite escolher entre English (United States) [en-US] e Português (Brasil) [pt-BR],
 *   atualizando instantaneamente os textos da tela e salvando a escolha no DataStore ao tocar em
 *   Começar / Get Started.
 * - Integrada visualmente à nova identidade da abertura, com atmosfera luminosa turquesa/violeta,
 *   ilustração isométrica 3×3×3 das faces coloridas do cubo e cartões de idioma modernos com
 *   ícones da biblioteca `design-assets`.
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

    val backgroundBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                NavyBackground,
                NavySurface,
                GraphiteCard,
                NavyBackground
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                LightBackground,
                LightSurface,
                LightCard,
                LightBackground
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .testTag("welcome_language_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompact = maxWidth < 350.dp || maxHeight < 650.dp

        // Atmosfera luminosa de fundo alinhada à OpeningScreen
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val topGlowCenter = Offset(w * 0.5f, h * 0.22f)
            val radius = (size.minDimension * 0.58f).coerceAtLeast(24f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.18f else 0.10f),
                        VioletAccent.copy(alpha = if (isDarkTheme) 0.12f else 0.07f),
                        Color.Transparent
                    ),
                    center = topGlowCenter,
                    radius = radius
                ),
                radius = radius,
                center = topGlowCenter
            )

            drawCircle(
                color = TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.12f else 0.08f),
                radius = size.minDimension * 0.36f,
                center = topGlowCenter,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .verticalScroll(scrollState)
                .padding(
                    horizontal = if (isCompact) 16.dp else 22.dp,
                    vertical = if (isCompact) 12.dp else 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp)
        ) {
            // 1. Selo superior de boas-vindas com ícone da biblioteca design-assets
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(themeColors.turquoiseAccent, themeColors.violetAccent)
                        ),
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Cartão de identidade visual com cubo isométrico 3×3×3 colorido e destaques
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.72f else 0.48f),
                                StickerBlue.copy(alpha = if (isDarkTheme) 0.55f else 0.38f),
                                VioletAccent.copy(alpha = if (isDarkTheme) 0.72f else 0.48f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .testTag("welcome_hero_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isCompact) 14.dp else 18.dp,
                            vertical = if (isCompact) 12.dp else 16.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)
                ) {
                    // Emblema central 3×3×3 com halo neon sem comprimir os cartões abaixo
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 64.dp else 76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.22f else 0.14f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(listOf(TurquoisePrimary, VioletAccent)),
                                shape = RoundedCornerShape(22.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IsometricCubeHeroIllustration(
                            modifier = Modifier.size(if (isCompact) 46.dp else 56.dp)
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

                    // Pílulas visuais de recursos do app com ícones da biblioteca design-assets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WelcomeFeatureChip(
                            iconRes = R.drawable.ic_3d_rotation,
                            text = activeStrings.welcomeFeatureInteractive,
                            tint = themeColors.turquoiseAccent,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        WelcomeFeatureChip(
                            iconRes = R.drawable.ic_layers,
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
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_language),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = activeStrings.welcomeSelectLanguageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("welcome_select_language_heading")
                    )
                }

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

            Spacer(modifier = Modifier.height(2.dp))

            // 4. Botão principal de confirmação: Começar / Get Started
            Button(
                onClick = { onConfirmStart(selectedLanguage) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 48.dp else 54.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.26f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("welcome_get_started_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.turquoiseAccent,
                    contentColor = themeColors.onTurquoiseAccent
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
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
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
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
        modifier = modifier.border(
            width = 1.dp,
            color = tint.copy(alpha = 0.38f),
            shape = RoundedCornerShape(10.dp)
        )
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

    val cardModifier = if (isSelected) {
        modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    listOf(themeColors.turquoiseAccent, themeColors.violetAccent)
                ),
                shape = RoundedCornerShape(18.dp)
            )
    } else {
        modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp)
            )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = cardModifier
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
                // Emblema do idioma com borda neon quando selecionado
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isSelected) {
                                themeColors.turquoiseAccent.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) {
                                themeColors.turquoiseAccent
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(13.dp)
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
                        color = if (isSelected) {
                            themeColors.turquoiseAccent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSelected) {
                Surface(
                    color = themeColors.turquoiseAccent.copy(alpha = 0.16f),
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_check_circle),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(22.dp)
                            .testTag("${testTag}_selected_icon")
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
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
