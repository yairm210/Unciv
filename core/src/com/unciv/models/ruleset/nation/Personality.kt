package com.unciv.models.ruleset.nation

import com.unciv.Constants

class Personality {

    var diplomacy:HashMap<String, Float>
    //Those two are not yet implemented
    var buildings:HashMap<String, Float>
    var citystates:HashMap<String, Float>

    constructor(){
        diplomacy = baseDiplomacy()
        buildings = baseBuildings()
        citystates = baseCityStates()
    }
    private fun baseDiplomacy():HashMap<String, Float>{
        return hashMapOf(
            "war" to 1f,
            "trade" to 1f,
            "openborders" to 1f,
            "friendship" to 1f,
            "alliance" to 1f
        )
    }

    private fun baseBuildings():HashMap<String, Float>{
        return hashMapOf(
            "science" to 1f,
            "production" to 1f,
            "gold" to 1f,
            "culture" to 1f,
            "faith" to 1f,
            "food" to 1f,
            "happiness" to 1f,
            "training" to 1f,
            "military" to 1f,
            "wonder" to 1f,
            "worker" to 1f,
            "workboat" to 1f
        )
    }

    private fun baseCityStates():HashMap<String, Float>{
        return hashMapOf(
            "protect" to 1f,
            "conquer" to 1f,
            "ally" to 1f,
            "bully" to 1f,
            "interact" to 1f
        )
    }
    fun getMinimunMotivationToAttack():Float{
        //The standard minimun is about 20% of the times motivationToDeclareWar is run
        var bias = 100 - diplomacy["war"]!! * 20
        return when {
            //The attack function behaves diffently across its domain
            bias >= 89f ->(7.97 * bias - 686.33).toFloat()
            bias == 80f-> 20f //Standard
            bias >=78f->(2.77 * bias - 199.47).toFloat()
            bias >=22f-> (0.59 * bias - 26.81).toFloat()
            bias >=11f -> (1.13 * bias - 42.35).toFloat()
            bias > 0f -> (2.76 * bias - 59.7).toFloat()
            else -> -500f
        }
    }

    fun getTradeModifier():Float{
        return  diplomacy["trade"]!!
    }

    fun getMinimumOpenBordersRoll():Int{
        return  - (diplomacy["openborders"]!! * 70).toInt() + 100
    }

    fun getMinimumDefensivePactRoll():Int{
        return  - (diplomacy["alliance"]!! * 70).toInt() + 100
    }

    fun getMinimumDeclarationOfFriendshipMotivation():Float{
        //Standard is DoF will be asked about 20% of possibilities
        var bias = 100 - diplomacy["friendship"]!! * 20
        return when {
            //Again, function behaves differently across its domain
            bias >= 82f -> (3.77 * bias - 312.16).toFloat()
            bias == 80f-> 0f //Standard
            bias >= 50f-> (0.91 * bias - 72.77).toFloat()
            bias >= 12 -> (3.36 * bias - 194.49).toFloat()
            bias > 0f -> (6.11 * bias - 229.37).toFloat()
            else -> -500f
        }
    }
}
