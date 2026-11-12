package com.keybook.vault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val KeybookColorScheme = darkColorScheme(
    primary = KeybookColors.Mint,
    onPrimary = KeybookColors.Ink,
    primaryContainer = KeybookColors.MintContainer,
    onPrimaryContainer = KeybookColors.MintStrong,
    inversePrimary = KeybookColors.MintDim,
    secondary = KeybookColors.Gold,
    onSecondary = KeybookColors.Ink,
    secondaryContainer = KeybookColors.SurfaceSoft,
    onSecondaryContainer = KeybookColors.Gold,
    tertiary = KeybookColors.Blue,
    onTertiary = KeybookColors.Ink,
    background = KeybookColors.Ink,
    onBackground = KeybookColors.Text,
    surface = KeybookColors.Ink,
    onSurface = KeybookColors.Text,
    surfaceVariant = KeybookColors.SurfaceRaised,
    onSurfaceVariant = KeybookColors.TextMuted,
    surfaceTint = KeybookColors.Mint,
    inverseSurface = KeybookColors.Text,
    inverseOnSurface = KeybookColors.Ink,
    error = KeybookColors.Danger,
    onError = KeybookColors.Ink,
    errorContainer = KeybookColors.DangerContainer,
    onErrorContainer = KeybookColors.Danger,
    outline = KeybookColors.Outline,
    outlineVariant = KeybookColors.OutlineSoft,
    scrim = KeybookColors.InkDeep,
)

private val KeybookShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KeybookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeybookColorScheme,
        typography = KeybookTypography,
        shapes = KeybookShapes,
        content = content,
    )
}
