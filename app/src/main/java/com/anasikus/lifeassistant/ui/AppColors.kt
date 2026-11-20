package com.anasikus.lifeassistant.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.anasikus.lifeassistant.settings.AppPreferences

object AppColors {
    val Background get() = if (AppPreferences.dark) Color(0xFF0B0812) else Color(0xFFF7F6FA)
    val Card get() = if (AppPreferences.dark) Color(0xFF1A1425) else Color.White
    val Accent get() = when (AppPreferences.accent) {
        "BLUE" -> if (AppPreferences.dark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
        "GREEN" -> if (AppPreferences.dark) Color(0xFF34D399) else Color(0xFF047857)
        "ROSE" -> if (AppPreferences.dark) Color(0xFFF472B6) else Color(0xFFBE185D)
        "ORANGE" -> if (AppPreferences.dark) Color(0xFFFB923C) else Color(0xFFC2410C)
        else -> if (AppPreferences.dark) Color(0xFFA855F7) else Color(0xFF7E22CE)
    }
    val MainText get() = if (AppPreferences.dark) Color(0xFFF5F0FF) else Color(0xFF201B2B)
    val SecondaryText get() = if (AppPreferences.dark) Color(0xFFB7AFC5) else Color(0xFF635B70)
    val Green get() = if (AppPreferences.dark) Color(0xFF86EFAC) else Color(0xFF15743F)
    val Yellow get() = if (AppPreferences.dark) Color(0xFFFCD34D) else Color(0xFF805600)
    val Red get() = if (AppPreferences.dark) Color(0xFFFDA4AF) else Color(0xFFBA233F)
    val LatePurple get() = if (AppPreferences.dark) Color(0xFFE9B8FF) else Color(0xFF7927A8)
}

fun assistantColorScheme(primary: Color = AppColors.Accent, surface: Color = AppColors.Card,
    background: Color = AppColors.Background, onSurface: Color = AppColors.MainText,
    onSurfaceVariant: Color = AppColors.SecondaryText): ColorScheme {
    val base = if (AppPreferences.dark) darkColorScheme() else lightColorScheme()
    return base.copy(primary = primary, surface = surface, background = background, onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant, onBackground = AppColors.MainText, secondary = AppColors.Accent,
        onPrimary = if (AppPreferences.dark) AppColors.Background else Color.White, outline = AppColors.SecondaryText)
}
