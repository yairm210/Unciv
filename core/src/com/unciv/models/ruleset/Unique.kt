package com.unciv.models.ruleset

import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText

class Unique(val text:String){
    val placeholderText = text.getPlaceholderText()
    val params = text.getPlaceholderParameters()
    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val stats: Stats by lazy {
        val firstStatParam = params.firstOrNull { Stats.isStats(it) }
        if (firstStatParam == null) Stats() // So badly-defined stats don't crash the entire game
        else Stats.parse(firstStatParam)
    }
}

class UniqueMap:HashMap<String, ArrayList<Unique>>() {
    fun addUnique(unique: Unique) {
        if (!containsKey(unique.placeholderText)) this[unique.placeholderText] = ArrayList()
        this[unique.placeholderText]!!.add(unique)
    }

    fun getUniques(placeholderText: String): List<Unique> {
        val result = this[placeholderText]
        if (result == null) return listOf()
        else return result
    }

    fun getAllUniques() = this.asSequence().flatMap { it.value.asSequence() }
}

// Buildings, techs and policies can have 'triggered' effects
object UniqueTriggerActivation {
    fun triggerCivwideUnique(unique: Unique, civInfo: CivilizationInfo, cityInfo: CityInfo? = null) {
        val chosenCity = if (cityInfo != null) cityInfo else civInfo.cities.firstOrNull { it.isCapital() }
        when (unique.placeholderText) {
            "Free [] appears" -> {
                val unitName = unique.params[0]
                val unit = civInfo.gameInfo.ruleSet.units[unitName]
                if (chosenCity != null && unit != null && (!unit.uniques.contains("Founds a new city") || !civInfo.isOneCityChallenger()))
                    civInfo.addUnit(unitName, chosenCity)
            }
            "[] free [] units appear" -> {
                val unitName = unique.params[1]
                val unit = civInfo.gameInfo.ruleSet.units[unitName]
                if (chosenCity != null && unit != null && (!unit.uniques.contains("Founds a new city") || !civInfo.isOneCityChallenger()))
                    for (i in 1..unique.params[0].toInt())
                        civInfo.addUnit(unitName, chosenCity)
            }
            // spectators get all techs at start of game, and if (in a mod) a tech gives a free policy, the game stucks on the policy picker screen
            "Free Social Policy" -> if (!civInfo.isSpectator()) civInfo.policies.freePolicies++
            "Empire enters golden age" ->
                civInfo.goldenAges.enterGoldenAge()
            "Free Great Person" -> {
                if (civInfo.isPlayerCivilization()) civInfo.greatPeople.freeGreatPeople++
                else {
                    val greatPeople = civInfo.getGreatPeople()
                    if (greatPeople.isEmpty()) return
                    var greatPerson = civInfo.getGreatPeople().random()

                    val preferredVictoryType = civInfo.victoryType()
                    if (preferredVictoryType == VictoryType.Cultural) {
                        val culturalGP = greatPeople.firstOrNull { it.uniques.contains("Great Person - [Culture]") }
                        if (culturalGP != null) greatPerson = culturalGP
                    }
                    if (preferredVictoryType == VictoryType.Scientific) {
                        val scientificGP = greatPeople.firstOrNull { it.uniques.contains("Great Person - [Science]") }
                        if (scientificGP != null) greatPerson = scientificGP
                    }

                    civInfo.addUnit(greatPerson.name, chosenCity)
                }
            }
            "+1 population in each city" ->
                for (city in civInfo.cities) {
                    city.population.population += 1
                    city.population.autoAssignPopulation()
                }
            "Free Technology" -> civInfo.tech.freeTechs += 1

            "Quantity of strategic resources produced by the empire increased by 100%" -> civInfo.updateDetailedCivResources()
            "+20% attack bonus to all Military Units for 30 turns" -> civInfo.policies.autocracyCompletedTurns = 30

            "Reveals the entire map" -> civInfo.exploredTiles.addAll(civInfo.gameInfo.tileMap.values.asSequence().map { it.position })

            "[] units gain the [] promotion" -> {
                val filter = unique.params[0]
                val promotion = unique.params[1]
                for (unit in civInfo.getCivUnits())
                    if (unit.matchesFilter(filter)
                            || civInfo.gameInfo.ruleSet.unitPromotions.values.any {
                                it.name == promotion && unit.type.name in it.unitTypes
                            })
                        unit.promotions.addPromotion(promotion, isFree = true)
            }
        }
    }

}