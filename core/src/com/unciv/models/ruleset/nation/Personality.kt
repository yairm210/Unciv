package com.unciv.models.ruleset.nation

import com.unciv.Constants
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
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
    Militaristic, // Building a mility but not nessesarily using it
    WarMongering, // Declaring war and expanding or defending
    Trading, // Trading frequency, open borders and liberating city-states
    Diplomatic, // Likelyhood of signing friendship, defensive pact, peace treaty and other diplomatic actions
    Loyalty, // Likelyhood to make a long lasting aliance with another civ and join wars with them
    Expansion, // Building new cities, oposite of a cultural victory, capturing cities instead of razing or liberating
}

class Personality: RulesetObject() {
    var production: Float = 5f
    var food: Float = 5f
    var gold: Float = 5f
    var science: Float = 5f
    var culture: Float = 5f
    var happiness: Float = 5f
    var faith: Float = 5f
    
    var militaristic: Float = 5f
    var warMongering: Float = 5f // Todo: Look into where this should be inserted
    var trading: Float = 5f
    var diplomatic: Float = 5f
    var loyalty: Float = 5f
    var expansion: Float = 5f

    var priorities = LinkedHashMap<String, Int>()
    var preferredVictoryType: String = Constants.neutralVictoryType
    var isNeutralPersonality: Boolean = false

    private fun nameToVariable(value: PersonalityValue):KMutableProperty0<Float> {
        return when(value) {
            PersonalityValue.Production -> ::production
            PersonalityValue.Food -> ::food
            PersonalityValue.Gold -> ::gold
            PersonalityValue.Science -> ::science
            PersonalityValue.Culture -> ::culture
            PersonalityValue.Happiness -> ::happiness
            PersonalityValue.Faith -> ::faith
            PersonalityValue.Militaristic -> ::militaristic
            PersonalityValue.WarMongering -> ::warMongering
            PersonalityValue.Trading -> ::trading
            PersonalityValue.Diplomatic -> ::diplomatic
            PersonalityValue.Loyalty -> ::loyalty
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
