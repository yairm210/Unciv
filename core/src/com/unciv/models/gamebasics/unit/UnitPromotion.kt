package com.unciv.models.gamebasics.unit

import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.stats.INamed

class UnitPromotion : ICivilopedia, INamed{
    override lateinit var name: String
    override val description: String
        get(){
            return effect
        }
    var prerequisites = listOf<String>()
    lateinit var effect:String;
    var unitTypes = listOf<UnitType>()
}