package com.openofficepdf.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = BrandOnSecondary,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    onTertiary = BrandOnTertiary,
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = BrandOnTertiaryContainer,
    surface = BrandSurface,
    onSurface = BrandOnSurface,
    surfaceVariant = BrandSurfaceVariant,
    onSurfaceVariant = BrandOnSurfaceVariant,
    background = BrandBackground,
    onBackground = BrandOnBackground,
    outline = BrandOutline,
    outlineVariant = BrandOutlineVariant,
    error = BrandError,
    onError = BrandOnError,
    errorContainer = BrandErrorContainer,
    onErrorContainer = BrandOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkBrandPrimary,
    onPrimary = DarkBrandOnPrimary,
    primaryContainer = DarkBrandPrimaryContainer,
    onPrimaryContainer = DarkBrandOnPrimaryContainer,
    surface = DarkBrandSurface,
    onSurface = DarkBrandOnSurface,
    surfaceVariant = DarkBrandSurfaceVariant,
    onSurfaceVariant = DarkBrandOnSurfaceVariant,
    background = DarkBrandBackground,
    onBackground = DarkBrandOnBackground,
    outline = DarkBrandOutline,
    outlineVariant = DarkBrandOutlineVariant
)

@Composable
fun OpenOfficeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
