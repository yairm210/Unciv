package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine
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
            matchesSingleFilter(filter) ||
                state != null && hasTagUnique(filter, state) ||
                state == null && hasTagUnique(filter)
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
        ruleset.eras[branch.era]!!.eraNumber * 10000 +
                ruleset.policyBranches.keys.indexOf(branch.name) * 100 +
                policyBranchType.ordinal

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lineList = ArrayList<FormattedLine>()

        lineList += if (this is PolicyBranch) {
            val era = ruleset.eras[era]
            val eraColor = era?.getHexColor() ?: ""
            val eraLink = era?.makeLink() ?: ""
            FormattedLine("{Unlocked at} {${branch.era}}", header = 4, color = eraColor, link = eraLink)
        } else {
            FormattedLine("Policy branch: [${branch.name}]", link = branch.makeLink())
        }

        if (policyBranchType != PolicyBranchType.BranchComplete && requires != null && requires!!.isNotEmpty()) {
            lineList += FormattedLine()
            if (requires!!.size == 1)
                requires!!.first().let { lineList += FormattedLine("Requires [$it]", link = "Policy/$it") }
            else {
                lineList += FormattedLine("Requires all of the following:")
                requires!!.forEach {
                    lineList += FormattedLine(it, link = "Policy/$it")
                }
            }
        }

        val leadsTo = ruleset.policies.values.filter {
            it.requires != null && name in it.requires!!
                    && it.policyBranchType != PolicyBranchType.BranchComplete
        }
        if (leadsTo.isNotEmpty()) {
            lineList += FormattedLine()
            if (leadsTo.size == 1)
                leadsTo.first().let { lineList += FormattedLine("Leads to [${it.name}]", link = it.makeLink()) }
            else {
                lineList += FormattedLine("Leads to:")
                leadsTo.forEach {
                    lineList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
                }
            }
        }

        fun isEnabledByPolicy(rulesetObject: IRulesetObject) =
                rulesetObject.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals).any {
                    it.getModifiers(UniqueType.ConditionalAfterPolicyOrBelief).any { it.params[0] == name } }
                || rulesetObject.getMatchingUniques(UniqueType.Unavailable).any {
                    it.getModifiers(UniqueType.ConditionalBeforePolicyOrBelief).any { it.params[0] == name }
                }

        val enabledBuildings = ruleset.buildings.values.filter { isEnabledByPolicy(it) }
        val enabledUnits = ruleset.units.values.filter { isEnabledByPolicy(it) }

        if (enabledBuildings.isNotEmpty() || enabledUnits.isNotEmpty()) {
            lineList += FormattedLine("Enables:")
            for (building in enabledBuildings)
                lineList += FormattedLine(building.name, link = building.makeLink(), indent = 1)
            for (unit in enabledUnits)
                lineList += FormattedLine(unit.name, link = unit.makeLink(), indent = 1)
        }


        fun isDisabledByPolicy(rulesetObject: IRulesetObject): Boolean {
            if (rulesetObject.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals).any {
                    it.getModifiers(UniqueType.ConditionalBeforePolicyOrBelief).any { it.params[0] == name }
                })
                return true

            if (rulesetObject.getMatchingUniques(UniqueType.Unavailable, GameContext.IgnoreConditionals).any {
                    it.getModifiers(UniqueType.ConditionalAfterPolicyOrBelief).any { it.params[0] == name } })
                return true
            
            return false
        }


        val disabledBuildings = ruleset.buildings.values.filter { isDisabledByPolicy(it) }
        val disabledUnits = ruleset.units.values.filter { isDisabledByPolicy(it) }

        if (disabledBuildings.isNotEmpty() || disabledUnits.isNotEmpty()) {
            lineList += FormattedLine("Disables:")
            for (building in disabledBuildings)
                lineList += FormattedLine(building.name, link = building.makeLink(), indent = 1)
            for (unit in disabledUnits)
                lineList += FormattedLine(unit.name, link = unit.makeLink(), indent = 1)
        }

        uniquesToCivilopediaTextLines(lineList)

        return lineList
    }

}
