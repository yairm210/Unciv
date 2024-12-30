package com.unciv.models.ruleset.nation

import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.INamed
import com.unciv.ui.components.extensions.colorFromRGB

class CityStateType: INamed {
    override var name = ""

    var friendBonusUniques = ArrayList<String>()
    val friendBonusUniqueMap by lazy { friendBonusUniques.toUniqueMap() }
    var allyBonusUniques = ArrayList<String>()
    val allyBonusUniqueMap by lazy { allyBonusUniques.toUniqueMap() }
    private fun ArrayList<String>.toUniqueMap() =
        UniqueMap(asSequence().map { Unique(it, sourceObjectType = UniqueTarget.CityState, sourceObjectName = name) })

    var color: List<Int> = listOf(255,255,255)
    private val colorObject by lazy { colorFromRGB(color) }
    fun getColor() = colorObject
}
