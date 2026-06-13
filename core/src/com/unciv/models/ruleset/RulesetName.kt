package com.unciv.models.ruleset

import com.unciv.models.stats.INamed
import yairm210.purity.annotations.Readonly

/** A ruleset name entry for checks that need to reason about translation keys across ruleset sources.
 *
 *  The same raw [name] can be valid in more than one place, but still share a translation key.
 *  [source] identifies the object type or field that contributed it, and [originRuleset] helps
 *  validation distinguish built-in reuse from modded name collisions.
 */
data class RulesetName(
    val name: String,
    val source: String,
    val originRuleset: String
)

@Readonly
internal fun INamed.toRulesetName(ruleset: Ruleset, getINamed: (Ruleset.() -> Sequence<INamed>)? = null): RulesetName =
    RulesetName(
        name,
        this::class.simpleName ?: "RulesetObject",
        (this as? IRulesetObject)?.originRuleset?.ifEmpty { null } ?: findOrigin(ruleset, getINamed)
    )

@Readonly
internal fun Ruleset.rulesetName(name: String, source: String, originRuleset: String = this.name): RulesetName =
    RulesetName(name, source, originRuleset)

@Readonly
private fun INamed.findOrigin(ruleset: Ruleset, getINamed: (Ruleset.() -> Sequence<INamed>)?): String {
    if (getINamed == null || ruleset.name.isNotEmpty())
        return ruleset.name
    val componentRulesets = ruleset.mods.mapNotNull { RulesetCache[it] }
    for (i in componentRulesets.size - 1 downTo 0) { // asReversed isn't recognized as readonly
        val componentRuleset = componentRulesets[i]
        if (componentRuleset.getINamed().any { it.name == this.name })
            return componentRuleset.name
    }
    return ""
}
