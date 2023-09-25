package com.unciv.models.ruleset.tech

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.objectdescriptions.TechnologyDescriptions

class Technology: RulesetObject() {

    var cost: Int = 0
    var prerequisites = HashSet<String>()
    override fun getUniqueTarget() = UniqueTarget.Tech

    var column: TechColumn? = null // The column that this tech is in the tech tree
    var row: Int = 0
    var quote = ""

    fun era(): String = column!!.era

    fun isContinuallyResearchable() = hasUnique(UniqueType.ResearchableMultipleTimes)


    /** Get Civilization-specific description for TechPicker or AlertType.TechResearched */
    fun getDescription(viewingCiv: Civilization) =
            TechnologyDescriptions.getDescription(this, viewingCiv)

    override fun makeLink() = "Technology/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset) =
            TechnologyDescriptions.getCivilopediaTextLines(this, ruleset)

    fun matchesFilter(filter: String): Boolean {
        return when (filter) {
            "All" -> true
            name -> true
            era() -> true
            else -> uniques.contains(filter)
        }
    }
}
