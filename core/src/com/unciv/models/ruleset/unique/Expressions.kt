package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset

class Expressions : ICountable {
    //TODO these are all placeholders!

    override fun matches(parameterText: String, ruleset: Ruleset?): ICountable.MatchResult {
        return ICountable.MatchResult.No
    }

    override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
        return null
    }

    override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
        return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    }
}
