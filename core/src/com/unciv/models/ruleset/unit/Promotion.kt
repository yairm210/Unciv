package com.unciv.models.ruleset.unit

import com.unciv.models.stats.INamed
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr

class Promotion : INamed{
    override lateinit var name: String
    var prerequisites = listOf<String>()
    lateinit var effect:String
    var unitTypes = listOf<String>() // The json parser woulddn't agree to deserialize this as a list of UnitTypes. =(

    fun getDescription(promotionsForUnitType: Collection<Promotion>, forCivilopedia:Boolean=false):String {
        // we translate it before it goes in to get uniques like "vs units in rough terrain" and after to get "vs city
        val stringBuilder = StringBuilder()
        stringBuilder.appendln(Translations.translateBonusOrPenalty(effect.tr()))

        if(prerequisites.isNotEmpty()) {
            val prerequisitesString:ArrayList<String> = arrayListOf()
            for (i in prerequisites.filter { promotionsForUnitType.any { promotion ->  promotion.name==it } }){
                prerequisitesString.add(i.tr())
            }
            stringBuilder.appendln("{Requires}: ".tr()+prerequisitesString.joinToString(" OR ".tr()))
        }
        if(forCivilopedia){
            val unitTypesString = unitTypes.joinToString(", "){it.tr()}
            stringBuilder.appendln("Available for [$unitTypesString]".tr())
        }
        return stringBuilder.toString()
    }
}