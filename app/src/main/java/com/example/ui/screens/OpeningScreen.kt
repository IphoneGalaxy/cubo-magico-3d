package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.curriculum.HighlightMode
import com.example.model.CubeState
import com.example.renderer.InteractiveCube3DView
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TurquoisePrimary
import com.example.ui.theme.VioletAccent

/**
 * Tela de Abertura (OpeningScreen) da Etapa 1.1:
 * - Cubo 3D flutuando livremente SEM moldura quadrada, SEM fundo de cartão e SEM recortes (transparentBackground = true).
 * - Cubo 3D estritamente de apresentação visual (gesturesEnabled = false, autoRotateIdle = true): gira continuamente de forma
 *   suave, ignorando completamente toques, arrastes, pinça ou torção.
 * - Permanece aberta sem avanço automático por temporizador até que o usuário toque no botão de entrada.
 * - Na primeira instalação segue para a escolha de idioma; nas execuções seguintes segue diretamente para o Menu Principal.
 */
@Composable
fun OpeningScreen(
    isDarkTheme: Boolean,
    onContinue: () -> Unit
) {
    val strings = CuboMestreTheme.strings
    val themeColors = CuboMestreTheme.colors
    val showcaseCube = remember { CubeState.createSolved() }
    var entranceVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entranceVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "opening_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val backgroundBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF070B14),
                Color(0xFF0E1525),
                Color(0xFF070B14)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF4F7FB),
                Color(0xFFEAF0F9),
                Color(0xFFF4F7FB)
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .testTag("opening_screen")
    ) {
        val isCompactHeight = maxHeight < 680.dp
        val floatingCubeSize = when {
            maxHeight < 640.dp -> 210.dp
            maxHeight < 740.dp -> 244.dp
            else -> 276.dp
        }.coerceAtMost(maxWidth - 40.dp)

        // Halos radiais e anéis geométricos de profundidade atrás do cubo 3D flutuante
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width * 0.5f, size.height * 0.37f)
            val outerRadius = size.minDimension * 0.56f * glowPulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.25f else 0.16f),
                        VioletAccent.copy(alpha = if (isDarkTheme) 0.16f else 0.10f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = outerRadius.coerceAtLeast(24f)
                ),
                radius = outerRadius.coerceAtLeast(24f),
                center = centerOffset
            )
            // Anel orbital sutil ao redor do cubo flutuante (sem caixa ou moldura retangular)
            drawCircle(
                color = TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.16f else 0.12f),
                radius = (size.minDimension * 0.34f).coerceAtLeast(20f),
                center = centerOffset,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = VioletAccent.copy(alpha = if (isDarkTheme) 0.12f else 0.09f),
                radius = (size.minDimension * 0.42f).coerceAtLeast(24f),
                center = centerOffset,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 16.dp else 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Selo Superior de Identidade Tecnológica com ícone oficial de design-assets
            Surface(
                color = if (isDarkTheme) Color(0xFF182235).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            themeColors.turquoiseAccent.copy(alpha = 0.65f),
                            themeColors.violetAccent.copy(alpha = 0.65f)
                        )
                    )
                ),
                modifier = Modifier.testTag("opening_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_auto_awesome),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = strings.openingBadge.trim(),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = themeColors.turquoiseAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Vitrine Central: Cubo 3D Flutuante Livre (SEM MOLDURA, SEM FUNDO DE CARTÃO, SEM TOQUE) + Identidade
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(floatingCubeSize)
                        .testTag("opening_cube_showcase"),
                    contentAlignment = Alignment.Center
                ) {
                    // Halos luminosos concêntricos diretamente atrás do cubo flutuante
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension * 0.48f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.30f * glowPulse else 0.18f * glowPulse),
                                    VioletAccent.copy(alpha = if (isDarkTheme) 0.18f * glowPulse else 0.10f * glowPulse),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = r.coerceAtLeast(16f)
                            ),
                            radius = r.coerceAtLeast(16f),
                            center = center
                        )
                    }

                    // Cubo 3D flutuando livremente:
                    // - transparentBackground = true (sem preenchimento retangular opaco e sem clip de cantos)
                    // - gesturesEnabled = false (ignora 100% de toques, arrastes, pinça e torção)
                    // - initialCameraDistance = 8.2f (folga espacial generosa para que nenhum vértice encoste nas bordas durante o giro)
                    // - autoRotateIdle = true (rotação contínua suave)
                    InteractiveCube3DView(
                        cubeState = showcaseCube,
                        activeAnimation = null,
                        modifier = Modifier.fillMaxSize(),
                        highlightMode = HighlightMode.ALL_VIVID,
                        highlightEnabled = false,
                        isDarkTheme = isDarkTheme,
                        autoRotateIdle = true,
                        allowFaceSwipeMoves = false,
                        gesturesEnabled = false,
                        transparentBackground = true,
                        initialCameraDistance = 8.2f,
                        showCameraControlsOverlay = false,
                        showActiveMoveBanner = false
                    )
                }

                // Título e Subtítulo da Marca com tipografia Space Grotesk & Plus Jakarta Sans
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.homeAppName,
                        fontFamily = SpaceGroteskFontFamily,
                        style = if (isCompactHeight) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("opening_title")
                    )

                    Text(
                        text = strings.openingSubtitle,
                        fontFamily = PlusJakartaSansFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("opening_subtitle")
                    )
                }

                // Pílulas de Pilares do App com ícones da biblioteca design-assets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OpeningFeaturePill(
                        iconRes = R.drawable.ic_rounded_school,
                        label = strings.openingFeatureLearn,
                        accentColor = themeColors.turquoiseAccent,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    OpeningFeaturePill(
                        iconRes = R.drawable.ic_rounded_explore_3d,
                        label = strings.openingFeatureExplore,
                        accentColor = themeColors.violetAccent,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    OpeningFeaturePill(
                        iconRes = R.drawable.ic_rounded_camera_cube,
                        label = strings.openingFeatureSolve,
                        accentColor = themeColors.warningText,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // 3. Rodapé com CTA Principal e Assinatura Técnica
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.turquoiseAccent,
                        contentColor = themeColors.onTurquoiseAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("opening_continue_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = strings.openingContinueButton,
                            fontFamily = SpaceGroteskFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(themeColors.onTurquoiseAccent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_forward),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = strings.openingVersionLabel,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(if (entranceVisible) 1f else 0.6f)
                )
            }
        }
    }
}

@Composable
private fun OpeningFeaturePill(
    iconRes: Int,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(50),
        modifier = modifier.border(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.42f),
            shape = RoundedCornerShape(50)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Emblema vetorial isométrico de Cubo 3×3 compartilhado pelo cabeçalho do Menu Principal
 * e pela tela de Boas-Vindas & Idioma.
 */
@Composable
fun IsometricCubeHeroIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f
        val r = size.minDimension * 0.42f
        val dx = r * 0.866f
        val dy = r * 0.5f

        val topFace = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + dx, cy - dy)
            lineTo(cx, cy)
            lineTo(cx - dx, cy - dy)
            close()
        }
        val leftFace = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - dx, cy - dy)
            lineTo(cx, cy)
            lineTo(cx, cy + r)
            lineTo(cx - dx, cy + dy)
            close()
        }
        val rightFace = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy)
            lineTo(cx + dx, cy - dy)
            lineTo(cx + dx, cy + dy)
            lineTo(cx, cy + r)
            close()
        }

        drawPath(topFace, color = com.example.ui.theme.StickerYellow)
        drawPath(leftFace, color = com.example.ui.theme.StickerBlue)
        drawPath(rightFace, color = com.example.ui.theme.StickerRed)

        val strokePx = (size.minDimension * 0.055f).coerceAtLeast(1.2f)
        val borderStroke = Stroke(width = strokePx)
        val lineColor = Color(0xFF0A0E17)
        drawPath(topFace, color = lineColor, style = borderStroke)
        drawPath(leftFace, color = lineColor, style = borderStroke)
        drawPath(rightFace, color = lineColor, style = borderStroke)
    }
}

