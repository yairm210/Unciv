package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.objectdescriptions.uniquesToDescription
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen

class GreatPerson : RulesetObject() {
    /** A list of unit names that this Great Person applies to (Great Engineer, Great Scientist, etc). */
    var units = ArrayList<String>()

    fun clone(): GreatPerson {
        val newGreatPerson = GreatPerson()

        // RulesetObject fields
        newGreatPerson.name = name
        newGreatPerson.uniques = uniques

        // GreatPerson fields
        newGreatPerson.units = units
        return newGreatPerson
    }

    override fun getUniqueTarget() = UniqueTarget.GreatPerson
    override fun makeLink() = "Great People/$name"
    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lines = ArrayList<FormattedLine>()
        lines.add(FormattedLine(extraImage = "GreatPeople/$name", centered = true))
        for (unitName in units) {
            lines.add(FormattedLine(unitName, link = "Unit/$unitName"))
        }
        uniquesToCivilopediaTextLines(lines)
        return lines
    }
}
