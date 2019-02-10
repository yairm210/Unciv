package com.unciv.models.gamebasics

import com.badlogic.gdx.graphics.Color
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.colorFromRGB

class Nation : INamed {
    override lateinit var name: String
    lateinit var leaderName: String
    lateinit var mainColor: List<Int>
    var unique:String?=null
    var secondaryColor: List<Int>?=null
    fun getColor(): Color {
        return colorFromRGB(mainColor[0], mainColor[1], mainColor[2])
    }
    fun getSecondaryColor(): Color {
        if(secondaryColor==null) return Color.BLACK
        return colorFromRGB(secondaryColor!![0], secondaryColor!![1], secondaryColor!![2])
    }
    lateinit var cities: List<String>
}
