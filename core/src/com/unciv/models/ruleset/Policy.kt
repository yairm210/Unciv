package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine

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
        fun isBranchCompleteByName(name: String) = name.endsWith(branchCompleteSuffix)
    }

    /** Used in PolicyPickerScreen to display Policy properties */
    fun getDescription(): String {
        val policyText = ArrayList<String>()
        policyText += name
        policyText += uniques

        if (policyBranchType != PolicyBranchType.BranchComplete) {
            policyText += if (requires!!.isNotEmpty())
                "Requires [" + requires!!.joinToString { it.tr() } + "]"
            else
                "{Unlocked at} {${branch.era}}"
        }
        return policyText.joinToString("\n") { it.tr() }
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

        if (uniques.isNotEmpty()) {
            lineList += FormattedLine()
            uniqueObjects.forEach {
                if (!it.hasFlag(UniqueFlag.HiddenToUsers))
                    lineList += FormattedLine(it)
            }
        }

        return lineList
    }

}

