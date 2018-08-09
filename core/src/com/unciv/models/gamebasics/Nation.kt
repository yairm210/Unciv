package com.unciv.models.gamebasics

import com.badlogic.gdx.graphics.Color
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.colorFromRGB

class Nation : INamed {
    override lateinit var name: String
    lateinit var RGB: List<Int>
    fun getColor(): Color {
        return colorFromRGB(RGB[0], RGB[1], RGB[2])
    }
    lateinit var cities: List<String>
}
