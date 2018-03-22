package com.unciv.models.gamebasics

import com.badlogic.gdx.graphics.Color
import com.unciv.models.stats.INamed

class Civilization : INamed {
    override lateinit var name: String
    lateinit var RGB: List<Int>
    fun getColor(): Color {
        return Color(RGB[0]/256f, RGB[1]/256f, RGB[2]/256f, 1f)
    }
}
