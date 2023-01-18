package com.unciv.models.ruleset.nation

import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.extensions.colorFromRGB

class CityStateType: INamed {
    override var name = ""
    var friendBonusUniques = ArrayList<String>()
    val friendBonusUniqueMap by lazy { UniqueMap().apply { addUniques(friendBonusUniques.map { Unique(it, sourceObjectType = UniqueTarget.CityState) }) } }
    var allyBonusUniques = ArrayList<String>()
    val allyBonusUniqueMap by lazy { UniqueMap().apply { addUniques(allyBonusUniques.map { Unique(it, sourceObjectType = UniqueTarget.CityState) }) } }

    lateinit var color:List<Int>
    private val colorObject by lazy { colorFromRGB(color) }
    fun getColor() = colorObject
}
