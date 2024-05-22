package com.unciv.models.ruleset.nation

import com.unciv.Constants
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.Stat
import kotlin.reflect.KMutableProperty0

/**
 * Type of Personality focus. Typically ranges from 0 (no focus) to 10 (double focus)
 */
enum class PersonalityValue {
    // Stat focused personalities
    Production,
    Food,
    Gold,
    Science,
    Culture,
    Happiness,
    Faith,
    // Behaviour focused personalities
    Military, // Building a military but not nessesarily using it
    Aggressive, // Declaring war determines expanding or defending
    Commerce, // Trading frequency, open borders and liberating city-states, less negative diplomacy impact
    Diplomacy, // Likelyhood of signing friendship, defensive pact, peace treaty and other diplomatic actions
    Loyal, // Likelyhood to make a long lasting aliance with another civ and join wars with them
    Expansion; // Founding/capturing new cities, oposite of a cultural victory

    companion object  {
        operator fun get(stat: Stat): PersonalityValue {
            return when (stat) {
                Stat.Production -> Production
                Stat.Food -> Food
                Stat.Gold -> Gold
                Stat.Science -> Science
                Stat.Culture -> Culture
                Stat.Happiness -> Happiness
                Stat.Faith -> Faith
            }
        }
    }
}

class Personality: RulesetObject() {
    var production: Float = 5f
    var food: Float = 5f
    var gold: Float = 5f
    var science: Float = 5f
    var culture: Float = 5f
    var happiness: Float = 5f
    var faith: Float = 5f

    var military: Float = 5f
    var aggressive: Float = 5f
    var commerce: Float = 5f
    var diplomacy: Float = 5f
    var loyal: Float = 5f
    var expansion: Float = 5f

    var priorities = LinkedHashMap<String, Int>()
    var preferredVictoryType: String = Constants.neutralVictoryType
    var isNeutralPersonality: Boolean = false

    private fun nameToVariable(value: PersonalityValue): KMutableProperty0<Float> {
        return when(value) {
            PersonalityValue.Production -> ::production
            PersonalityValue.Food -> ::food
            PersonalityValue.Gold -> ::gold
            PersonalityValue.Science -> ::science
            PersonalityValue.Culture -> ::culture
            PersonalityValue.Happiness -> ::happiness
            PersonalityValue.Faith -> ::faith
            PersonalityValue.Military -> ::military
            PersonalityValue.Aggressive -> ::aggressive
            PersonalityValue.Commerce -> ::commerce
            PersonalityValue.Diplomacy -> ::diplomacy
            PersonalityValue.Loyal -> ::loyal
            PersonalityValue.Expansion -> ::expansion
        }
    }

    companion object {
        val neutralPersonality: Personality by lazy {
            val base = Personality()
            base.isNeutralPersonality = true
            base
        }
    }

    /**
     * Scales the value to a more meaningful range, where 10 is 2, and 5 is 1
     */
    fun scaledFocus(value: PersonalityValue): Float {
        return nameToVariable(value).get() / 5
    }

    operator fun get(value: PersonalityValue): Float {
        return nameToVariable(value).get()
    }

    operator fun set(personalityValue: PersonalityValue, value: Float){
        nameToVariable(personalityValue).set(value)
    }

    override fun getUniqueTarget() = UniqueTarget.Personality

    override fun makeLink(): String {
        return ""
    }

}
