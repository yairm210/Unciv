package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder.Companion.buildCivilopediaText
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

open class Policy : RulesetObject() {
    lateinit var branch: PolicyBranch // not in json - added in gameBasics

    override fun getUniqueTarget() = UniqueTarget.Policy
    var row: Int = 0
    var column: Int = 0
    var requires: ArrayList<String>? = null

    /** Indicates whether a [Policy] is a [PolicyBranch] starting policy, a normal one, or the branch completion */
    enum class PolicyBranchType {BranchStart, Member, BranchComplete}

    /** Indicates whether this [Policy] is a [PolicyBranch] starting policy, a normal one, or the branch completion */
    val policyBranchType: PolicyBranchType by lazy { when {
        this is PolicyBranch -> PolicyBranchType.BranchStart
        isBranchCompleteByName(name) -> PolicyBranchType.BranchComplete
        else -> PolicyBranchType.Member
    } }

    companion object {
        const val branchCompleteSuffix = " Complete"
        /** Some tests to count policies by completion or not use only the String collection without instantiating them.
         *  To keep the hardcoding in one place, this is public and should be used instead of duplicating it.
         */
        @Pure fun isBranchCompleteByName(name: String) = name.endsWith(branchCompleteSuffix)
    }

    @Readonly
    fun matchesFilter(filter: String, state: GameContext? = null): Boolean =
        MultiFilter.multiFilter(filter, {
            matchesSingleFilter(it) ||
                state != null && hasTagUnique(it, state) ||
                state == null && hasTagUnique(it)
        })

    // Remember policy branches are duplicated in `policies` (as subclass carrying more information),
    // so filtering by a policy branch name matches only the branch itself, filtering by "[name] branch"
    // will match all policies in that branch plus the branch itself (since the loader sets a branch's branch to itself).
    @Readonly
    fun matchesSingleFilter(filter: String): Boolean {
        return when(filter) {
            in Constants.all -> true
            name -> true
            "[${branch.name}] branch" -> true
            else -> false
        }
    }

    /** Used in PolicyPickerScreen to display Policy properties */
    fun getDescription(): String {
        return (if (policyBranchType == PolicyBranchType.Member) name.tr() + "\n" else "") +
            uniqueObjects.filterNot {
                it.isHiddenToUsers()
                    || it.type == UniqueType.OnlyAvailable
                    || it.type == UniqueType.OneTimeGlobalAlert
            }
            .joinToString("\n") { "• ${it.getDisplayText().tr()}" }
    }

    override fun makeLink() = "Policy/$name"
    override fun getSortGroup(ruleset: Ruleset) =
        (ruleset.eras[branch.era]?.eraNumber ?: 0) * 10000 +
                ruleset.policyBranches.keys.indexOf(branch.name) * 100 +
                policyBranchType.ordinal
    override fun getSubCategory(ruleset: Ruleset): String? = branch.name

    override fun getCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        if (this@Policy is PolicyBranch) {
            val era = ruleset.eras[era]
            val eraColor = era?.getHexColor() ?: ""
            val eraLink = era?.makeLink() ?: ""
            add("{Unlocked at} {${branch.era}}", header = 4, color = eraColor, link = eraLink)
        } else {
            add("Policy branch: [${branch.name}]", link = branch.makeLink())
        }

        if (policyBranchType != PolicyBranchType.BranchComplete && !requires.isNullOrEmpty()) {
            add()
            if (requires!!.size == 1)
                requires!!.first().let { add("Requires [$it]", link = "Policy/$it") }
            else {
                add("Requires all of the following:")
                requires!!.forEach { add(it, link = "Policy/$it") }
            }
        }

        val leadsTo = ruleset.policies.values.filter {
            it.requires != null && name in it.requires!!
                    && it.policyBranchType != PolicyBranchType.BranchComplete
        }
        if (leadsTo.isNotEmpty()) {
            add()
            if (leadsTo.size == 1)
                leadsTo.first().let { add("Leads to [${it.name}]", link = it.makeLink()) }
            else {
                add("Leads to:")
                leadsTo.forEach {
                    add(it.name, link = it.makeLink(), indent = 1)
                }
            }
        }

        fun IRulesetObject.hasUniqueWithConditional(uniqueType: UniqueType, condition: UniqueType) =
            getMatchingUniques(uniqueType, GameContext.IgnoreConditionals).any { unique ->
                unique.getModifiers(condition).any { it.params[0] == this@Policy.name }
            }

        fun IRulesetObject.isEnabledByThisPolicy() =
            hasUniqueWithConditional(UniqueType.OnlyAvailable, UniqueType.ConditionalAfterPolicyOrBelief) ||
            hasUniqueWithConditional(UniqueType.Unavailable, UniqueType.ConditionalBeforePolicyOrBelief)
        val enabledBuildings = ruleset.buildings.values.filter { it.isEnabledByThisPolicy() }
        val enabledUnits = ruleset.units.values.filter { it.isEnabledByThisPolicy() }

        if (enabledBuildings.isNotEmpty() || enabledUnits.isNotEmpty()) {
            add("Enables:")
            for (building in enabledBuildings)
                add(building.name, link = building.makeLink(), indent = 1)
            for (unit in enabledUnits)
                add(unit.name, link = unit.makeLink(), indent = 1)
        }

        fun IRulesetObject.isDisabledByThisPolicy() =
            hasUniqueWithConditional(UniqueType.OnlyAvailable, UniqueType.ConditionalBeforePolicyOrBelief) ||
            hasUniqueWithConditional(UniqueType.Unavailable, UniqueType.ConditionalAfterPolicyOrBelief)
        val disabledBuildings = ruleset.buildings.values.filter { it.isDisabledByThisPolicy() }
        val disabledUnits = ruleset.units.values.filter { it.isDisabledByThisPolicy() }

        if (disabledBuildings.isNotEmpty() || disabledUnits.isNotEmpty()) {
            add("Disables:")
            for (building in disabledBuildings)
                add(building.name, link = building.makeLink(), indent = 1)
            for (unit in disabledUnits)
                add(unit.name, link = unit.makeLink(), indent = 1)
        }

        addUniques()
    }

}
