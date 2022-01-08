package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.translations.tr

class UnhappinessEffect : RulesetObject() {
    override fun getUniqueTarget() = UniqueTarget.Unhappiness
    override fun makeLink() = "" //No own category on Civilopedia screen
    val unhappiness = 0;

    // Used to add these to the 'unhappiness' tutorial in the civilopedia
    fun toCivilopediaLines(): String {
        var lines = "${name.tr()}:\n"
        lines += uniques.joinToString("\n") { "    ${it.tr()}" }
        return lines
    }
}