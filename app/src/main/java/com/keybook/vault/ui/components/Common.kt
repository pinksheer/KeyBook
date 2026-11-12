package com.keybook.vault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keybook.vault.data.ItemType
import com.keybook.vault.data.label
import com.keybook.vault.ui.theme.KeybookColors
import com.keybook.vault.ui.theme.KeybookTypography

@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(KeybookColors.Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(KeybookColors.Mint.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.06f),
                    radius = size.width * 0.78f,
                ),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.92f, size.height * 0.06f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(KeybookColors.Gold.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.62f),
                    radius = size.width * 0.72f,
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.02f, size.height * 0.62f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(KeybookColors.Mint.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(size.width * 0.45f, size.height * 1.02f),
                    radius = size.width * 0.65f,
                ),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.45f, size.height * 1.02f),
            )
            // A quiet field of dust gives the dark surface some depth.
            val dust = listOf(
                0.12f to 0.18f, 0.82f to 0.24f, 0.64f to 0.11f, 0.28f to 0.38f,
                0.91f to 0.48f, 0.07f to 0.54f, 0.73f to 0.67f, 0.38f to 0.76f,
                0.17f to 0.88f, 0.86f to 0.92f, 0.52f to 0.58f, 0.96f to 0.73f,
            )
            dust.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = if (index % 3 == 0) KeybookColors.Gold.copy(alpha = 0.16f)
                    else KeybookColors.Mint.copy(alpha = 0.13f),
                    radius = if (index % 4 == 0) 1.6f else 1.0f,
                    center = Offset(size.width * x, size.height * y),
                )
            }
        }
        content()
    }
}

@Composable
fun VaultMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    glow: Boolean = true,
) {
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (glow) Modifier.shadow(
                    elevation = size * 0.22f,
                    shape = shape,
                    ambientColor = KeybookColors.Mint.copy(alpha = 0.35f),
                    spotColor = KeybookColors.Mint.copy(alpha = 0.35f),
                ) else Modifier
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B3A31), Color(0xFF0B1714)),
                )
            )
            .border(1.dp, KeybookColors.Mint.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(size * 0.17f)) {
            val w = this.size.width
            val h = this.size.height
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.02f)
                cubicTo(w * 0.68f, h * 0.10f, w * 0.86f, h * 0.14f, w * 0.97f, h * 0.17f)
                lineTo(w * 0.97f, h * 0.47f)
                cubicTo(w * 0.97f, h * 0.72f, w * 0.77f, h * 0.90f, w * 0.5f, h * 0.99f)
                cubicTo(w * 0.23f, h * 0.90f, w * 0.03f, h * 0.72f, w * 0.03f, h * 0.47f)
                lineTo(w * 0.03f, h * 0.17f)
                cubicTo(w * 0.14f, h * 0.14f, w * 0.32f, h * 0.10f, w * 0.5f, h * 0.02f)
                close()
            }
            drawPath(
                shield,
                Brush.linearGradient(
                    colors = listOf(KeybookColors.MintStrong, KeybookColors.Mint, KeybookColors.MintDim),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            )
            drawPath(
                shield,
                KeybookColors.Ink.copy(alpha = 0.38f),
                style = Stroke(width = w * 0.035f),
            )
            drawCircle(
                color = KeybookColors.Ink,
                radius = w * 0.145f,
                center = Offset(w * 0.5f, h * 0.42f),
            )
            val stem = Path().apply {
                moveTo(w * 0.40f, h * 0.53f)
                lineTo(w * 0.60f, h * 0.53f)
                lineTo(w * 0.65f, h * 0.76f)
                lineTo(w * 0.35f, h * 0.76f)
                close()
            }
            drawPath(stem, KeybookColors.Ink)
            drawCircle(
                color = KeybookColors.Mint.copy(alpha = 0.9f),
                radius = w * 0.045f,
                center = Offset(w * 0.5f, h * 0.41f),
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    borderColor: Color = KeybookColors.Outline,
    colors: List<Color> = listOf(KeybookColors.SurfaceRaised, KeybookColors.Surface),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(
                                        role = Role.Button,
                    onClick = onClick,
                ) else Modifier
            ),
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val alpha = if (enabled && !loading) 1f else 0.55f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = if (enabled) 18.dp else 0.dp,
                shape = shape,
                ambientColor = KeybookColors.Mint.copy(alpha = 0.25f),
                spotColor = KeybookColors.Mint.copy(alpha = 0.3f),
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        KeybookColors.MintStrong.copy(alpha = alpha),
                        KeybookColors.Mint.copy(alpha = alpha),
                    )
                )
            )
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = KeybookColors.Ink,
                strokeWidth = 2.5.dp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = KeybookColors.Ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    color = KeybookColors.Ink,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(KeybookColors.Surface.copy(alpha = 0.7f))
            .border(1.dp, KeybookColors.Outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = KeybookColors.TextMuted, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = if (enabled) KeybookColors.Text else KeybookColors.TextFaint, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = KeybookColors.TextMuted,
    container: Color = KeybookColors.Surface.copy(alpha = 0.78f),
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, KeybookColors.OutlineSoft, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(KeybookColors.Surface.copy(alpha = 0.8f))
                    .border(1.dp, KeybookColors.OutlineSoft, CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = KeybookColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = KeybookColors.MintDim,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = KeybookColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
    }
}

@Composable
fun TypeGlyph(type: ItemType, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val (icon, tint, background) = when (type) {
        ItemType.PASSWORD -> Triple(Icons.Rounded.Key, KeybookColors.Mint, KeybookColors.MintContainer)
        ItemType.TOTP -> Triple(Icons.Rounded.Timer, KeybookColors.Gold, Color(0xFF33280F))
        ItemType.PASSKEY -> Triple(Icons.Rounded.Fingerprint, KeybookColors.Passkey, Color(0xFF342417))
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(
                Brush.linearGradient(
                    listOf(background.copy(alpha = 0.95f), background.copy(alpha = 0.55f)),
                )
            )
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.43f))
    }
}

@Composable
fun TypePill(type: ItemType, modifier: Modifier = Modifier) {
    val (tint, background) = when (type) {
        ItemType.PASSWORD -> KeybookColors.Mint to KeybookColors.MintContainer
        ItemType.TOTP -> KeybookColors.Gold to Color(0xFF33280F)
        ItemType.PASSKEY -> KeybookColors.Passkey to Color(0xFF342417)
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background.copy(alpha = 0.8f))
            .border(1.dp, tint.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            type.label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
            color = tint,
        )
    }
}

@Composable
fun KeybookTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = KeybookColors.TextFaint) } },
        leadingIcon = leadingIcon?.let {
            {
                Icon(it, contentDescription = null, tint = KeybookColors.TextFaint, modifier = Modifier.size(20.dp))
            }
        },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        enabled = enabled,
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KeybookColors.Mint.copy(alpha = 0.72f),
            unfocusedBorderColor = KeybookColors.Outline,
            disabledBorderColor = KeybookColors.OutlineSoft,
            errorBorderColor = KeybookColors.Danger.copy(alpha = 0.8f),
            focusedContainerColor = KeybookColors.Surface.copy(alpha = 0.9f),
            unfocusedContainerColor = KeybookColors.Surface.copy(alpha = 0.58f),
            disabledContainerColor = KeybookColors.Surface.copy(alpha = 0.3f),
            errorContainerColor = KeybookColors.DangerContainer.copy(alpha = 0.25f),
            cursorColor = KeybookColors.Mint,
            focusedTextColor = KeybookColors.Text,
            unfocusedTextColor = KeybookColors.Text,
            errorTextColor = KeybookColors.Text,
            focusedLabelColor = KeybookColors.Mint,
            unfocusedLabelColor = KeybookColors.TextFaint,
            errorLabelColor = KeybookColors.Danger,
            focusedLeadingIconColor = KeybookColors.Mint,
            unfocusedLeadingIconColor = KeybookColors.TextFaint,
            focusedTrailingIconColor = KeybookColors.Mint,
            unfocusedTrailingIconColor = KeybookColors.TextFaint,
            errorSupportingTextColor = KeybookColors.Danger,
            focusedSupportingTextColor = KeybookColors.TextFaint,
            unfocusedSupportingTextColor = KeybookColors.TextFaint,
        ),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KeybookColors.TextFaint,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun CountdownRing(
    progress: Float,
    seconds: Int,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    color: Color = KeybookColors.Gold,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280),
        label = "totp-progress",
    )
    val density = LocalDensity.current
    val stroke = with(density) { 2.5.dp.toPx() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = KeybookColors.OutlineSoft,
                style = Stroke(width = stroke),
                radius = this.size.minDimension / 2 - stroke,
            )
            drawArc(
                color = if (animated < 0.2f) KeybookColors.Danger else color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(stroke, stroke),
                size = Size(this.size.width - stroke * 2, this.size.height - stroke * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            seconds.toString(),
            color = if (animated < 0.2f) KeybookColors.Danger else KeybookColors.TextMuted,
            style = KeybookTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
        )
    }
}

@Composable
fun DashedHalo(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = KeybookColors.Mint.copy(alpha = 0.16f),
                radius = size.minDimension / 2,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)),
                ),
            )
            drawCircle(
                color = KeybookColors.Gold.copy(alpha = 0.10f),
                radius = size.minDimension * 0.36f,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        content()
    }
}

@Composable
fun FieldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, KeybookColors.Outline, Color.Transparent)
                )
            ),
    )
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(38.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(KeybookColors.Outline),
    )
}

@Composable
fun RoundAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(tint.copy(alpha = 0.11f))
                .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = KeybookColors.TextMuted)
    }
}

