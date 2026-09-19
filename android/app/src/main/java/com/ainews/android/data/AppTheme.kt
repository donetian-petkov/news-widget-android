package com.ainews.android.data

/**
 * The look of the app and the widget, ported from the Mac widget's vibes so both
 * products offer the same choices. [System] follows the phone's light/dark setting.
 */
enum class AppVibe {
    System,
    Light,
    Dark,
    Newspaper,
    Cinema,
    SciFi,
    CyberWitch,
    Fantasy,
    Anime,
    Arcade,
    ;

    val label: String
        get() = when (this) {
            System -> "Follow system"
            Light -> "Light"
            Dark -> "Dark"
            Newspaper -> "Newspaper"
            Cinema -> "Cinema"
            SciFi -> "Sci-Fi"
            CyberWitch -> "Cyber Witch"
            Fantasy -> "Fantasy"
            Anime -> "Anime"
            Arcade -> "Arcade"
        }

    /** True when the palette is a dark one, so system chrome can match it. */
    fun isDark(systemInDarkMode: Boolean): Boolean =
        when (this) {
            System -> systemInDarkMode
            Light -> false
            else -> true
        }

    fun palette(systemInDarkMode: Boolean): ThemePalette =
        when (this) {
            System -> if (systemInDarkMode) Dark.palette(true) else Light.palette(false)

            Light -> ThemePalette(
                background = 0xFFF3F6FB,
                backgroundAlt = 0xFFE7EDF6,
                panel = 0xFFFFFFFF,
                panelBorder = 0xFFC2D0E4,
                textPrimary = 0xFF14203A,
                textSecondary = 0xFF415472,
                textMuted = 0xFF6B7D96,
                accentBlue = 0xFF0E63D4,
                accentCyan = 0xFF0C946E,
                accentGold = 0xFFB8791F,
                accentRose = 0xFFC23B6A,
            )

            Dark -> ThemePalette(
                background = 0xFF070C1A,
                backgroundAlt = 0xFF0C152C,
                panel = 0xFF10182A,
                panelBorder = 0xFF3B61B9,
                textPrimary = 0xFFE7EFFF,
                textSecondary = 0xFF9AABCD,
                textMuted = 0xFF7787AB,
                accentBlue = 0xFF72B0FF,
                accentCyan = 0xFF22DCB7,
                accentGold = 0xFFFFBA45,
                accentRose = 0xFFFF6E96,
            )

            Newspaper -> ThemePalette(
                background = 0xFF0B1117,
                backgroundAlt = 0xFF141C25,
                panel = 0xFF161D25,
                panelBorder = 0xFF96A6B6,
                textPrimary = 0xFFEDF2F7,
                textSecondary = 0xFFC5D2DF,
                textMuted = 0xFF9BABBA,
                accentBlue = 0xFFA7B2BE,
                accentCyan = 0xFF8E9DB0,
                accentGold = 0xFFAA8E54,
                accentRose = 0xFFC08A6A,
            )

            Cinema -> ThemePalette(
                background = 0xFF0B0910,
                backgroundAlt = 0xFF1C1321,
                panel = 0xFF241716,
                panelBorder = 0xFFDAA75F,
                textPrimary = 0xFFF6EFE6,
                textSecondary = 0xFFE7D4BD,
                textMuted = 0xFFB39B84,
                accentBlue = 0xFFC27078,
                accentCyan = 0xFFDAA75F,
                accentGold = 0xFFEEC36E,
                accentRose = 0xFFBA425F,
            )

            SciFi -> ThemePalette(
                background = 0xFF050D1A,
                backgroundAlt = 0xFF0D192D,
                panel = 0xFF0D192D,
                panelBorder = 0xFF7ED2FF,
                textPrimary = 0xFFE9F5FF,
                textSecondary = 0xFFCBDEEE,
                textMuted = 0xFF8AA3BE,
                accentBlue = 0xFF2CD4FF,
                accentCyan = 0xFF7884FF,
                accentGold = 0xFF6ED6FF,
                accentRose = 0xFF9C7BFF,
            )

            CyberWitch -> ThemePalette(
                background = 0xFF070916,
                backgroundAlt = 0xFF141230,
                panel = 0xFF141230,
                panelBorder = 0xFFC26EFF,
                textPrimary = 0xFFEDF2FF,
                textSecondary = 0xFFCEDAF6,
                textMuted = 0xFF9AA3D6,
                accentBlue = 0xFF72DBFF,
                accentCyan = 0xFF4AD6FF,
                accentGold = 0xFFE0B0FF,
                accentRose = 0xFFAE64FF,
            )

            Fantasy -> ThemePalette(
                background = 0xFF08110F,
                backgroundAlt = 0xFF112320,
                panel = 0xFF112320,
                panelBorder = 0xFF93D18A,
                textPrimary = 0xFFE9F4EA,
                textSecondary = 0xFFCEE0D3,
                textMuted = 0xFF9BB3A2,
                accentBlue = 0xFF5CC78F,
                accentCyan = 0xFF93D18A,
                accentGold = 0xFFEEB554,
                accentRose = 0xFFE0B058,
            )

            Anime -> ThemePalette(
                background = 0xFF08081A,
                backgroundAlt = 0xFF22123A,
                panel = 0xFF22123A,
                panelBorder = 0xFFF26CD1,
                textPrimary = 0xFFF1F5FF,
                textSecondary = 0xFFE3D7F6,
                textMuted = 0xFFB0A5D6,
                accentBlue = 0xFF6ABBFF,
                accentCyan = 0xFF58D8FF,
                accentGold = 0xFFFF5CBD,
                accentRose = 0xFFFF5CB0,
            )

            Arcade -> ThemePalette(
                background = 0xFF050912,
                backgroundAlt = 0xFF0A181E,
                panel = 0xFF0A181E,
                panelBorder = 0xFF5BFFCB,
                textPrimary = 0xFFE6FAFF,
                textSecondary = 0xFFBDE1EE,
                textMuted = 0xFF8AB0BE,
                accentBlue = 0xFF54E9FF,
                accentCyan = 0xFF4CFFB0,
                accentGold = 0xFFFF4CE6,
                accentRose = 0xFFFF4CE6,
            )
        }
}

data class ThemePalette(
    val background: Long,
    val backgroundAlt: Long,
    val panel: Long,
    val panelBorder: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val textMuted: Long,
    val accentBlue: Long,
    val accentCyan: Long,
    val accentGold: Long,
    val accentRose: Long,
) {
    /** Mixes two palette colours, for tinted surfaces like the alert card. */
    fun blend(from: Long, to: Long, ratio: Float): Long {
        val clamped = ratio.coerceIn(0f, 1f)
        fun channel(shift: Int): Long {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * clamped).toLong().coerceIn(0, 255)
        }
        return (0xFFL shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    val alertPanel: Long get() = blend(panel, accentRose, 0.22f)
    val successPanel: Long get() = blend(panel, accentCyan, 0.20f)
    val infoPanel: Long get() = blend(panel, accentBlue, 0.16f)
    val goldPanel: Long get() = blend(panel, accentGold, 0.24f)
    val chipPanel: Long get() = blend(panel, textMuted, 0.18f)
}

/**
 * The palette Android derives from the wallpaper. Available from Android 12, and used by
 * both the app and the widget when the user turns wallpaper colours on.
 */
fun dynamicThemePalette(context: android.content.Context, darkMode: Boolean): ThemePalette? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
    fun color(id: Int): Long = (context.getColor(id).toLong() and 0xFFFFFFFFL)
    return if (darkMode) {
        ThemePalette(
            background = color(android.R.color.system_neutral1_900),
            backgroundAlt = color(android.R.color.system_neutral1_800),
            panel = color(android.R.color.system_neutral2_800),
            panelBorder = color(android.R.color.system_accent1_600),
            textPrimary = color(android.R.color.system_neutral1_50),
            textSecondary = color(android.R.color.system_neutral2_200),
            textMuted = color(android.R.color.system_neutral2_400),
            accentBlue = color(android.R.color.system_accent1_200),
            accentCyan = color(android.R.color.system_accent3_200),
            accentGold = color(android.R.color.system_accent2_200),
            accentRose = color(android.R.color.system_accent3_100),
        )
    } else {
        ThemePalette(
            background = color(android.R.color.system_neutral1_50),
            backgroundAlt = color(android.R.color.system_neutral1_100),
            panel = color(android.R.color.system_neutral1_0),
            panelBorder = color(android.R.color.system_accent1_200),
            textPrimary = color(android.R.color.system_neutral1_900),
            textSecondary = color(android.R.color.system_neutral2_700),
            textMuted = color(android.R.color.system_neutral2_600),
            accentBlue = color(android.R.color.system_accent1_600),
            accentCyan = color(android.R.color.system_accent3_600),
            accentGold = color(android.R.color.system_accent2_600),
            accentRose = color(android.R.color.system_accent3_700),
        )
    }
}
