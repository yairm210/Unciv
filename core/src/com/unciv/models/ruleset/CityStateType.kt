package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.extensions.colorFromRGB

class CityStateType: INamed {
    override var name = ""
    var friendBonusUniques = ArrayList<String>()
    val friendBonusUniqueMap by lazy { UniqueMap().addUniques(friendBonusUniques.map { Unique(it) }) }
    var allyBonusUniques = ArrayList<String>()
    val allyBonusUniqueMap by lazy { UniqueMap().addUniques(allyBonusUniques.map { Unique(it) }) }

    lateinit var color:List<Int>
    private val colorObject by lazy { colorFromRGB(color) }
    fun getColor() = colorObject
}
