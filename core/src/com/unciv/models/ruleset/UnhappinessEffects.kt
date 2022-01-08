package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget

class UnhappinessEffect : RulesetObject() {
    override fun getUniqueTarget() = UniqueTarget.Unhappiness
    override fun makeLink() = "" //No own category on Civilopedia screen
    val unhappiness = 0;
}