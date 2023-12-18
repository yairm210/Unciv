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
        //The standard minimun is get 30% of the times motivationToDeclareWar is run
        var bias = diplomacy["war"]!! * 30
        return when {
            //The attack function behaves diffently across its image
            bias >= 75f -> (1.76 * bias - 39.79).toFloat()
            bias > 30f-> (0.45 * bias - 6.31).toFloat()
            bias == 30f-> 7f //Standard
            bias >= 14f -> (2.33 * bias - 138.2).toFloat()
            bias > 0f -> (5.5 * bias - 417.33).toFloat()
            else -> 500f
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
        //Standard is DoF will be asked about 18% of possibilities
        var bias = diplomacy["friendship"]!! * 18
        return when {
            //Again, function behaves differently across its image
            //Looks like a logarithmic function but thats more easily understandable and adjustable
            //And result is similar
            bias >= 90f -> (6.24 * bias - 148.93).toFloat()
            bias >= 78f-> (2.49 * bias - 117).toFloat()
            bias == 18f-> 0f //Standard
            bias >= 9f -> (0.77 * bias - 62.74).toFloat()
            bias > 0f -> (6.18 * bias - 554.74).toFloat()
            else -> 500f
        }
    }
}
