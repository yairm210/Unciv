package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget

class Tutorial : RulesetObject() {
    //todo migrate to civilopediaText then remove or deprecate
    val steps: Array<String>? = null
    override fun getUniqueTarget() = UniqueTarget.Tutorial
    override fun makeLink() = "Tutorial/$name"
}
