package com.unciv.models.ruleset.nation

import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.ui.components.extensions.colorFromRGB
import yairm210.purity.annotations.Readonly

class CityStateType : RulesetObject() {
    override fun getUniqueTarget() = UniqueTarget.CityState

    var friendBonusUniques = ArrayList<String>()
    @delegate:Transient
    val friendBonusUniqueMap: UniqueMap by lazy { uniqueMapProvider(uniqueObjectsProvider(friendBonusUniques)) }

    var allyBonusUniques = ArrayList<String>()
    @delegate:Transient
    val allyBonusUniqueMap: UniqueMap by lazy { uniqueMapProvider(uniqueObjectsProvider(allyBonusUniques)) }

    var color: List<Int> = listOf(255, 255, 255)
    private val colorObject by lazy { colorFromRGB(color) }
    @Readonly fun getColor() = colorObject

    override fun makeLink() = "CityStateType/$name"
}
