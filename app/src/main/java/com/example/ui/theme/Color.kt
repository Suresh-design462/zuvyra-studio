package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ZUVYRA Studio Palette — OLED Obsidian Glass with Violet, Indigo & Cyan accents
val StudioObsidian = Color(0xFF06070B)
val StudioGraphite = Color(0xFF0D0F18)
val StudioCharcoal = Color(0xFF131624)
val StudioSlate = Color(0xFF1B1F32)
val StudioBorder = Color(0xFF2A304A)

val ZuvyraViolet = Color(0xFF8B5CF6)
val ZuvyraVioletGlow = Color(0xFFA78BFA)
val ZuvyraIndigo = Color(0xFF6366F1)
val ZuvyraCyan = Color(0xFF38BDF8)
val ZuvyraMagenta = Color(0xFFE879F9)
val ZuvyraAmber = Color(0xFFF59E0B)
val ZuvyraEmerald = Color(0xFF10B981)
val ZuvyraCoral = Color(0xFFF43F5E)

val StudioTextPrimary = Color(0xFFF8FAFC)
val StudioTextSecondary = Color(0xFF94A3B8)
val StudioTextMuted = Color(0xFF64748B)

// Light Studio Palette
val LightCanvas = Color(0xFFF4F5FA)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFECEEF6)
val LightBorder = Color(0xFFD6DAE8)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)

enum class AccentColorOption(val label: String, val primary: Color, val secondary: Color) {
    VIOLET("Studio Violet", ZuvyraViolet, ZuvyraCyan),
    INDIGO("Electric Indigo", ZuvyraIndigo, ZuvyraVioletGlow),
    CYAN("Horizon Blue", Color(0xFF0EA5E9), ZuvyraIndigo),
    MAGENTA("Prism Magenta", Color(0xFFD946EF), ZuvyraAmber)
}
