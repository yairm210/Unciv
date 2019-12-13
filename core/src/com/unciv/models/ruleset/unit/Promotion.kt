package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.ICivilopedia
import com.unciv.models.stats.INamed

class Promotion : ICivilopedia, INamed{
    override lateinit var name: String
    override val description: String
        get(){
            return effect
        }
    var prerequisites = listOf<String>()
    lateinit var effect:String
    var unitTypes = listOf<String>() // The json parser woulddn't agree to deserialize this as a list of UnitTypes. =(
}