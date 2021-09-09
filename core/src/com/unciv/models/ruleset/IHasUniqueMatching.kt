package com.unciv.models.ruleset

import com.unciv.logic.civilization.CivilizationInfo

interface IHasUniqueMatching {

    fun getMatchingUniques(uniqueTemplate: String): Sequence<Unique>

    fun getMatchingApplyingUniques(uniqueTemplate: String, civilizationInfo: CivilizationInfo?): Sequence<Unique> {
        return getMatchingUniques(uniqueTemplate)
            .filter { it.conditionalsApply(civilizationInfo) }
    }

    fun hasUnique(uniqueTemplate: String) = getMatchingUniques(uniqueTemplate).any()

    fun hasApplyingUnique(uniqueTemplate: String, civilizationInfo: CivilizationInfo?): Boolean {
        return getMatchingApplyingUniques(uniqueTemplate, civilizationInfo).any()
    }
}