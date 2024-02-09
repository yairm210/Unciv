package com.unciv.models.ruleset.nation

import com.unciv.Constants
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import kotlin.reflect.KMutableProperty0

enum class PersonalityValue{
    Production,
    Food,
    Gold,
    Science,
    Culture,
    Happiness,
    Faith,
    Military,
    WarMongering,
}

class Personality : RulesetObject() {
    var production: Float = 5f
    var food: Float = 5f
    var gold: Float = 5f
    var science: Float = 5f
    var culture: Float = 5f
    var happiness: Float = 5f
    var faith: Float = 5f
    var military: Float = 5f
    var warMongering: Float = 5f // Todo: Look into where this should be inserted
    var policy = LinkedHashMap<String, Int>()
    var preferredVictoryType: String = Constants.neutralVictoryType
    var basePersonality: Boolean = false

    private fun nameToVariable(value: PersonalityValue):KMutableProperty0<Float> {
        return when(value) {
            PersonalityValue.Production -> ::production
            PersonalityValue.Food -> ::food
            PersonalityValue.Gold -> ::gold
            PersonalityValue.Science -> ::science
            PersonalityValue.Culture -> ::culture
            PersonalityValue.Happiness -> ::happiness
            PersonalityValue.Faith -> ::faith
            PersonalityValue.Military -> ::military
            PersonalityValue.WarMongering -> ::warMongering
        }
    }

    companion object {
        fun basePersonality(): Personality {
            val base = Personality()
            base.basePersonality = true
            return base
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
