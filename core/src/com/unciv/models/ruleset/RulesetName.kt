package com.unciv.models.ruleset

import yairm210.purity.annotations.Readonly

data class RulesetName(
    val name: String,
    val source: String,
    val originRuleset: String
)

@Readonly
internal fun IRulesetObject.toRulesetName(ruleset: Ruleset): RulesetName =
    RulesetName(
        name,
        this::class.simpleName ?: "RulesetObject",
        originRuleset.ifEmpty { ruleset.name }
    )

@Readonly
internal fun Ruleset.rulesetName(name: String, source: String, originRuleset: String = this.name): RulesetName =
    RulesetName(name, source, originRuleset)
