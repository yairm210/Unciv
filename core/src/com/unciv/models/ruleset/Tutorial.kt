package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget

class Tutorial : RulesetObject() {
    override var name = ""  // overridden only to have the name seen first by TranslationFileWriter
    //todo migrate to civilopediaText then remove or deprecate
    val steps: ArrayList<String>? = null
    override fun getUniqueTarget() = UniqueTarget.Tutorial
    override fun makeLink() = "Tutorial/$name"
}
