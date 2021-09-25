package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget

class RuinReward : RulesetObject() {
    val notification: String = ""

    override fun getUniqueTarget() = UniqueTarget.Ruins

    val excludedDifficulties: List<String> = listOf()
    val weight: Int = 1
    val color: String = ""  // For Civilopedia

    override fun makeLink() = "" //No own category on Civilopedia screen
}
