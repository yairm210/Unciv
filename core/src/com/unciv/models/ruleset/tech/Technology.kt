package com.unciv.models.ruleset.tech

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.objectdescriptions.TechnologyDescriptions
import yairm210.purity.annotations.Readonly

class Technology: RulesetObject() {

    var cost: Int = 0
    var prerequisites = HashSet<String>()
    override fun getUniqueTarget() = UniqueTarget.Tech

    var column: TechColumn? = null // The column that this tech is in the tech tree
    var row: Int = 0
    var quote = ""

    @Readonly fun era(): String = column!!.era

    @Readonly fun isContinuallyResearchable() = hasUnique(UniqueType.ResearchableMultipleTimes)


    /** Get Civilization-specific description for TechPicker or AlertType.TechResearched */
    fun getDescription(viewingCiv: Civilization) =
            TechnologyDescriptions.getDescription(this, viewingCiv)

    override fun makeLink() = "Technology/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset) =
            TechnologyDescriptions.getCivilopediaTextLines(this, ruleset)

    override fun era(ruleset: Ruleset) = ruleset.eras[era()]

    @Readonly
    fun matchesFilter(filter: String, state: GameContext? = null, multiFilter: Boolean = true): Boolean {
        return if (multiFilter) MultiFilter.multiFilter(filter, {
            matchesSingleFilter(filter) ||
                state != null && hasTagUnique(filter, state) ||
                state == null && hasTagUnique(filter)
        })
        else matchesSingleFilter(filter) ||
            state != null && hasTagUnique(filter, state) ||
            state == null && hasTagUnique(filter)
    }

    @Readonly
    fun matchesSingleFilter(filter: String): Boolean {
        return when (filter) {
            in Constants.all -> true
            name -> true
            era() -> true
            else -> false
        }
    }

    // Wrapper so that if the way to require a tech with a Unique ever changes, this only needs to change in one place.
    @Readonly
    fun uniqueIsRequirementForThisTech(unique: Unique): Boolean =
            unique.type == UniqueType.OnlyAvailable
            // OnlyAvailableWhen can take multiple conditionals, in which case the true conditional is implicitly the conjunction of all those conditionals.
            // If an OnlyAvailableWhen there are multiple conditionals, one of which requires this tech,
            // then IHasUniques.techsRequiredByUniques() will list this tech as required (because it is),
            // but uniqueIsRequirementForThisTech() will *not* identify that OnlyAvailableWhen as a requirement for this tech (because it's more complicated than that).
            && unique.modifiers.size == 1
            && unique.modifiers[0].let { it.type == UniqueType.ConditionalTech && it.params[0] == name }

    @Readonly fun uniqueIsNotRequirementForThisTech(unique: Unique): Boolean = !uniqueIsRequirementForThisTech(unique)
}
