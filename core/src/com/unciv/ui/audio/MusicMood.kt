package com.unciv.ui.audio

object MusicMood {
    const val Theme = "Theme"
    const val Peace = "Peace"
    const val War = "War"
    const val Defeat = "Defeat"
    const val Menu = "Menu"
    const val Ambient = "Ambient"

    val themeOrPeace = listOf(Theme, Peace)
    fun peaceOrWar(isAtWar: Boolean) = if (isAtWar) War else Peace
}
