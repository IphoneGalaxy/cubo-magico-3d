package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CubeState
import com.example.renderer.InteractiveCube3DView
import com.example.ui.theme.CuboMestreTheme
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.StickerBlue
import com.example.ui.theme.StickerGreen
import com.example.ui.theme.StickerOrange
import com.example.ui.theme.StickerRed
import com.example.ui.theme.StickerWhite
import com.example.ui.theme.StickerYellow
import com.example.ui.theme.TurquoisePrimary
import com.example.ui.theme.VioletAccent
import kotlinx.coroutines.delay

/**
 * Tela de Abertura (Etapa 1) — Identidade visual neon moderna do Cubo Mestre 3D,
 * apresentando o cubo mágico 3D como protagonista visual, iluminação suave turquesa/violeta
 * e elementos geométricos discretos sem esperas artificiais longas.
 */
@Composable
fun OpeningScreen(
    isDarkTheme: Boolean,
    autoAdvanceEnabled: Boolean = true,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = CuboMestreTheme.strings
    val themeColors = CuboMestreTheme.colors

    LaunchedEffect(autoAdvanceEnabled) {
        if (autoAdvanceEnabled) {
            delay(1350L)
            onContinue()
        }
    }

    val isHeadlessTest = remember {
        android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    val glowPulse = if (isHeadlessTest) {
        0.9f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "opening_neon_pulse")
        val animatedGlow by infiniteTransition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_pulse"
        )
        animatedGlow
    }

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
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .testTag("opening_screen"),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 640.dp
        val cubeBoxSize = when {
            maxHeight < 520.dp -> 150.dp
            isCompactHeight -> 190.dp
            else -> 230.dp
        }

        // Fundo geométrico discreto com iluminação suave estilo neon
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerTop = Offset(w * 0.5f, h * 0.36f)
            val maxRad = size.minDimension * 0.62f * glowPulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.20f else 0.12f),
                        VioletAccent.copy(alpha = if (isDarkTheme) 0.12f else 0.07f),
                        Color.Transparent
                    ),
                    center = centerTop,
                    radius = maxRad.coerceAtLeast(20f)
                ),
                radius = maxRad.coerceAtLeast(20f),
                center = centerTop
            )

            // Anéis orbitais sutis
            drawCircle(
                color = TurquoisePrimary.copy(alpha = if (isDarkTheme) 0.14f else 0.10f),
                radius = size.minDimension * 0.34f,
                center = centerTop,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = VioletAccent.copy(alpha = if (isDarkTheme) 0.12f else 0.08f),
                radius = size.minDimension * 0.44f,
                center = centerTop,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 16.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Selo superior tecnológico
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(themeColors.turquoiseAccent, themeColors.violetAccent)
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .testTag("opening_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rounded_auto_awesome),
                        contentDescription = null,
                        tint = themeColors.turquoiseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.openingBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.turquoiseAccent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Protagonista central: Cubo Mágico 3D interativo + moldura luminosa
            Box(
                modifier = Modifier
                    .size(cubeBoxSize)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                TurquoisePrimary.copy(alpha = 0.75f),
                                VioletAccent.copy(alpha = 0.75f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(10.dp)
                    .testTag("opening_cube_showcase"),
                contentAlignment = Alignment.Center
            ) {
                InteractiveCube3DView(
                    cubeState = CubeState.createSolved(),
                    activeAnimation = null,
                    isDarkTheme = isDarkTheme,
                    autoRotateIdle = true,
                    allowFaceSwipeMoves = false,
                    showCameraControlsOverlay = false,
                    showActiveMoveBanner = false,
                    modifier = Modifier.fillMaxSize()
                )

                // Emblema cromático nos cantos para reforçar profundidade visual
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    VibrantCubeEmblemCanvas(modifier = Modifier.size(18.dp))
                }
            }

            // Identidade tipográfica e recursos principais
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.appName,
                    style = if (isCompactHeight) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.displayMedium
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("opening_title")
                )

                Text(
                    text = strings.openingSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .testTag("opening_subtitle")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Destaques em pílulas com ícones Material Rounded
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OpeningFeaturePill(
                        iconRes = R.drawable.ic_rounded_cube_3d,
                        label = strings.openingFeature3D,
                        accentColor = themeColors.turquoiseAccent,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    OpeningFeaturePill(
                        iconRes = R.drawable.ic_rounded_school,
                        label = strings.openingFeature7Stages,
                        accentColor = themeColors.violetText,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // Botão de entrada imediata (sem obrigar espera)
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("opening_continue_button"),
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
                    text = strings.openingEnterButton,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
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

/**
 * Ilustração vetorial cromática do cubo 3×3 usando as cores oficiais das faces e a paleta neon.
 */
@Composable
fun VibrantCubeEmblemCanvas(modifier: Modifier = Modifier) {
    val colors = listOf(
        StickerWhite, StickerRed, StickerBlue,
        StickerOrange, TurquoisePrimary, StickerGreen,
        StickerYellow, VioletAccent, StickerWhite
    )
    Canvas(modifier = modifier) {
        val cell = size.width / 3f
        val pad = (size.width * 0.055f).coerceAtLeast(1f)
        val radius = CornerRadius(cell * 0.22f, cell * 0.22f)
        for (r in 0..2) {
            for (c in 0..2) {
                drawRoundRect(
                    color = colors[r * 3 + c],
                    topLeft = Offset(c * cell + pad, r * cell + pad),
                    size = Size(cell - pad * 2, cell - pad * 2),
                    cornerRadius = radius
                )
            }
        }
    }
}

/**
 * Ilustração isométrica 3D de alta definição das 3 faces visíveis do Cubo Mágico
 * para enriquecer cartões de boas-vindas e cabeçalhos.
 */
@Composable
fun IsometricCubeHeroIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.44f
        val dx = r * 0.866f
        val dy = r * 0.5f

        val topFace = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + dx, cy - dy)
            lineTo(cx, cy)
            lineTo(cx - dx, cy - dy)
            close()
        }
        val leftFace = Path().apply {
            moveTo(cx - dx, cy - dy)
            lineTo(cx, cy)
            lineTo(cx, cy + r)
            lineTo(cx - dx, cy + dy)
            close()
        }
        val rightFace = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + dx, cy - dy)
            lineTo(cx + dx, cy + dy)
            lineTo(cx, cy + r)
            close()
        }

        drawPath(topFace, color = StickerYellow)
        drawPath(leftFace, color = StickerBlue)
        drawPath(rightFace, color = StickerRed)

        val strokeColor = NavyBackground.copy(alpha = 0.85f)
        val strokeW = (size.minDimension * 0.045f).coerceAtLeast(1.5f)
        drawPath(topFace, color = strokeColor, style = Stroke(width = strokeW))
        drawPath(leftFace, color = strokeColor, style = Stroke(width = strokeW))
        drawPath(rightFace, color = strokeColor, style = Stroke(width = strokeW))
    }
}
