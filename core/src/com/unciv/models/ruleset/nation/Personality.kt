package com.unciv.models.ruleset.nation

import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.objectdescriptions.NationDescriptions.getCivilopediaTextHeaderImpl
import com.unciv.ui.objectdescriptions.NationDescriptions.getCivilopediaTextLinesImpl
import com.unciv.ui.objectdescriptions.NationDescriptions.getShortDescription
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/**
 *  A Leader Personality as defined in Personalities.json
 *
 *  * Linked to a Nation via [Nation.personality]
 *  * Contains biases for stats ([production]..[faith]) and others ([military]..[denounceWillingness])
 *  * These biases can be indexed with a [PersonalityValue] as key, [get]/[set]
 *  * Defines [preferredVictoryType]
 *  * Defines Policy preferences in [priorities]
 *  * Can have [civilopediaText], but not [uniques] (effect not implemented, guarded by `RulesetValidator`)
 *  * API: [scaledFocus], [inverseScaledFocus], [inverseModifierFocus], [scaleStats]
 */
class Personality : RulesetObject() {
    var production: Float = 5f
    var food: Float = 5f
    var gold: Float = 5f
    var science: Float = 5f
    var culture: Float = 5f
    var happiness: Float = 5f
    var faith: Float = 5f

    var military: Float = 5f
    var aggressive: Float = 5f
    var declareWar: Float = 5f
    var commerce: Float = 5f
    var diplomacy: Float = 5f
    var loyal: Float = 5f
    var expansion: Float = 5f
    var denounceWillingness: Float = 5f

    var priorities = LinkedHashMap<String, Int>()
    var preferredVictoryType: String = Constants.neutralVictoryType
    var isNeutralPersonality: Boolean = false

    @Pure
    private fun nameToVariable(value: PersonalityValue) = value.getProperty(this)

    companion object {
        val neutralPersonality: Personality by lazy {
            val base = Personality()
            base.isNeutralPersonality = true
            base
        }
    }

    /**
     * Scales the value to a more meaningful range, where 10 is 2, and 5 is 1, and 0 is 0
     */
    @Readonly
    fun scaledFocus(value: PersonalityValue): Float {
        return nameToVariable(value).get() / 5
    }

    /**
     * Inverse scales the value to a more meaningful range, where 0 is 2, and 5 is 1 and 10 is 0
     */
    @Readonly
    fun inverseScaledFocus(value: PersonalityValue): Float {
        return  (10 - nameToVariable(value).get()) / 5
    }

    /**
     * @param weight a value between 0 and 1 that determines how much the modifier deviates from 1
     * @return a modifier between 0 and 2 centered around 1 based off of the personality value and the weight given 
     */
    @Readonly
    fun modifierFocus(value: PersonalityValue, weight: Float): Float {
        return 1f + (scaledFocus(value) - 1) * weight
    }

    /**
     * An inverted version of [modifierFocus], a personality value of 0 becomes a 10, 8 becomes a 2, etc.
     * @param weight a value between 0 and 1 that determines how much the modifier deviates from 1
     * @return a modifier between 0 and 2 centered around 1 based off of the personality value and the weight given
     */
    @Readonly
    fun inverseModifierFocus(value: PersonalityValue, weight: Float): Float {
        return 1f + (inverseScaledFocus(value) - 1) * weight
    }

    /**
     * Scales the stats based on the personality and the weight given
     * @param weight a positive value that determines how much the personality should impact the stats given 
     */
    fun scaleStats(stats: Stats, weight: Float): Stats {
        Stat.entries.forEach { stats[it] *= modifierFocus(PersonalityValue[it], weight) }
        return stats
    }

    @Readonly
    operator fun get(value: PersonalityValue): Float = nameToVariable(value).get()

    operator fun set(personalityValue: PersonalityValue, value: Float){
        nameToVariable(personalityValue).set(value)
    }

    override fun getUniqueTarget() = UniqueTarget.Personality

    ////// Civilopedia //////
    override fun makeLink(): String {
        return "Personality/$name"
    }
    override fun getIconName() = ""
    override fun getCivilopediaTextHeader() = getCivilopediaTextHeaderImpl()
    override fun getCivilopediaTextLines(ruleset: Ruleset) = getCivilopediaTextLinesImpl(ruleset)
    /** Used in Nation Civilopedia UI */
    override fun toString() = getShortDescription()
}
