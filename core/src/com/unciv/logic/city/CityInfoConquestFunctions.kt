package com.unciv.logic.city

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.ui.utils.withoutItem
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Helper class for containing 200 lines of "how to move cities between civs" */
class CityInfoConquestFunctions(val city: CityInfo){
    private val tileBasedRandom = Random(city.getCenterTile().position.toString().hashCode())
    
    private fun getGoldForCapturingCity(conqueringCiv: CivilizationInfo): Int {
        val baseGold = 20 + 10 * city.population.population + tileBasedRandom.nextInt(40)
        val turnModifier = max(0, min(50, city.civInfo.gameInfo.turns - city.turnAcquired)) / 50f
        val cityModifier = if (city.containsBuildingUnique("Doubles Gold given to enemy if city is captured")) 2f else 1f
        val conqueringCivModifier = if (conqueringCiv.hasUnique("Receive triple Gold from Barbarian encampments and pillaging Cities")) 3f else 1f

        val goldPlundered = baseGold * turnModifier * cityModifier * conqueringCivModifier
        return goldPlundered.toInt()
    }
    
    private fun destroyBuildingsOnCapture() {
        city.apply {
            // Remove all national wonders (must come after the palace relocation because that's a national wonder too!)
            for (building in cityConstructions.getBuiltBuildings()) {
                when {
                    building.hasUnique("Never destroyed when the city is captured") -> continue
                    building.hasUnique("Destroyed when the city is captured") ->
                        cityConstructions.removeBuilding(building.name)
                    else -> {
                        if (tileBasedRandom.nextInt(100) < 34) {
                            cityConstructions.removeBuilding(building.name)
                        }
                    }
                }
            }
        }
    }
    
    /** Function for stuff that should happen on any capture, be it puppet, annex or liberate. 
     * Stuff that should happen any time a city is moved between civs, so also when trading,
     * should go in `this.moveToCiv()`, which this function also calls. 
     */
    private fun conquerCity(conqueringCiv: CivilizationInfo, conqueredCiv: CivilizationInfo, receivingCiv: CivilizationInfo) {
        val goldPlundered = getGoldForCapturingCity(conqueringCiv)
        city.apply {
            conqueringCiv.addGold(goldPlundered)
            conqueringCiv.addNotification("Received [$goldPlundered] Gold for capturing [$name]", getCenterTile().position, NotificationIcon.Gold)

            val reconqueredCityWhileStillInResistance = previousOwner == conqueringCiv.civName && resistanceCounter != 0
            
            this@CityInfoConquestFunctions.moveToCiv(receivingCiv)

            destroyBuildingsOnCapture()
            
            Battle.destroyIfDefeated(conqueredCiv, conqueringCiv)

            health = getMaxHealth() / 2 // I think that cities recover to half health when conquered?
            if (population.population > 1) population.addPopulation(-1 - population.population / 4) // so from 2-4 population, remove 1, from 5-8, remove 2, etc.
            reassignPopulation()

            resistanceCounter = 
                if (reconqueredCityWhileStillInResistance || foundingCiv == receivingCiv.civName) 0
                else population.population // I checked, and even if you puppet there's resistance for conquering
        }
    }


    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: CivilizationInfo) {
        // Gain gold for plundering city
        val goldPlundered = getGoldForCapturingCity(conqueringCiv)
        city.apply {
            
            val oldCiv = civInfo

            // must be before moving the city to the conquering civ,
            // so the repercussions are properly checked
            diplomaticRepercussionsForConqueringCity(oldCiv, conqueringCiv)

            conquerCity(conqueringCiv, oldCiv, conqueringCiv)
            
            isPuppet = true
            cityStats.update()
            // The city could be producing something that puppets shouldn't, like units
            cityConstructions.currentConstructionIsUserSet = false
            cityConstructions.constructionQueue.clear()
            cityConstructions.chooseNextConstruction()
        }
    }

    fun annexCity() {
        city.isPuppet = false
        city.cityConstructions.inProgressConstructions.clear() // undo all progress of the previous civ on units etc.
        city.cityStats.update()
        if (!UncivGame.Current.consoleMode)
            UncivGame.Current.worldScreen.shouldUpdate = true
    }

    private fun diplomaticRepercussionsForConqueringCity(oldCiv: CivilizationInfo, conqueringCiv: CivilizationInfo) {
        val currentPopulation = city.population.population
        val percentageOfCivPopulationInThatCity = currentPopulation * 100f /
                oldCiv.cities.sumBy { it.population.population }
        val aggroGenerated = 10f + percentageOfCivPopulationInThatCity.roundToInt()

        // How can you conquer a city but not know the civ you conquered it from?!
        // I don't know either, but some of our players have managed this, and crashed their game!
        if (!conqueringCiv.knows(oldCiv))
            conqueringCiv.makeCivilizationsMeet(oldCiv)

        oldCiv.getDiplomacyManager(conqueringCiv)
                .addModifier(DiplomaticModifiers.CapturedOurCities, -aggroGenerated)

        for (thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() }) {
            val aggroGeneratedForOtherCivs = (aggroGenerated / 10).roundToInt().toFloat()
            if (thirdPartyCiv.isAtWarWith(oldCiv)) // You annoyed our enemy?
                thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                        .addModifier(DiplomaticModifiers.SharedEnemy, aggroGeneratedForOtherCivs) // Cool, keep at at! =D
            else thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                    .addModifier(DiplomaticModifiers.WarMongerer, -aggroGeneratedForOtherCivs) // Uncool bro.
        }
    }

    fun liberateCity(conqueringCiv: CivilizationInfo) {
        city.apply {
            if (foundingCiv == "") { // this should never happen but just in case...
                this@CityInfoConquestFunctions.puppetCity(conqueringCiv)
                this@CityInfoConquestFunctions.annexCity()
                return
            }

            val foundingCiv = civInfo.gameInfo.civilizations.first { it.civName == foundingCiv }
            if (foundingCiv.isDefeated()) // resurrected civ
                for (diploManager in foundingCiv.diplomacy.values)
                    if (diploManager.diplomaticStatus == DiplomaticStatus.War)
                        diploManager.makePeace()

            val oldCiv = civInfo
            
            diplomaticRepercussionsForLiberatingCity(conqueringCiv, oldCiv)
            
            conquerCity(conqueringCiv, oldCiv, foundingCiv)

            if (foundingCiv.cities.size == 1) cityConstructions.addBuilding(capitalCityIndicator()) // Resurrection!
            isPuppet = false
            cityStats.update()

            // Move units out of the city when liberated
            for (unit in getCenterTile().getUnits())
                unit.movement.teleportToClosestMoveableTile()
            for (unit in getTiles().flatMap { it.getUnits() }.toList())
                if (!unit.movement.canPassThrough(unit.currentTile))
                    unit.movement.teleportToClosestMoveableTile()
        }
    }


    private fun diplomaticRepercussionsForLiberatingCity(conqueringCiv: CivilizationInfo, conqueredCiv: CivilizationInfo) {
        val foundingCiv = conqueredCiv.gameInfo.civilizations.first { it.civName == city.foundingCiv }
        val percentageOfCivPopulationInThatCity = city.population.population *
                100f / (foundingCiv.cities.sumBy { it.population.population } + city.population.population)
        val respectForLiberatingOurCity = 10f + percentageOfCivPopulationInThatCity.roundToInt()

        // In order to get "plus points" in Diplomacy, you have to establish diplomatic relations if you haven't yet
        if (!conqueringCiv.knows(foundingCiv))
            conqueringCiv.makeCivilizationsMeet(foundingCiv)

        if (foundingCiv.isMajorCiv()) {
            foundingCiv.getDiplomacyManager(conqueringCiv)
                    .addModifier(DiplomaticModifiers.CapturedOurCities, respectForLiberatingOurCity)
        } else {
            //Liberating a city state gives a large amount of influence, and peace
            foundingCiv.getDiplomacyManager(conqueringCiv).influence = 90f
            if (foundingCiv.isAtWarWith(conqueringCiv)) {
                val tradeLogic = TradeLogic(foundingCiv, conqueringCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))
                tradeLogic.acceptTrade()
            }
        }

        val otherCivsRespecForLiberating = (respectForLiberatingOurCity / 10).roundToInt().toFloat()
        for (thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() && it != conqueredCiv }) {
            thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                    .addModifier(DiplomaticModifiers.LiberatedCity, otherCivsRespecForLiberating) // Cool, keep at at! =D
        }
    }


    fun moveToCiv(newCivInfo: CivilizationInfo) {
        city.apply {
            val oldCiv = civInfo
            civInfo.cities = civInfo.cities.toMutableList().apply { remove(city) }
            newCivInfo.cities = newCivInfo.cities.toMutableList().apply { add(city) }
            civInfo = newCivInfo
            if (isOriginalCapital) civInfo.hasEverOwnedOriginalCapital = true
            hasJustBeenConquered = false
            turnAcquired = civInfo.gameInfo.turns
            previousOwner = oldCiv.civName

            // now that the tiles have changed, we need to reassign population
            for (it in workedTiles.filterNot { tiles.contains(it) }) {
                workedTiles = workedTiles.withoutItem(it)
                population.autoAssignPopulation()
            }

            // Remove/relocate palace for old Civ
            val capitalCityIndicator = capitalCityIndicator()
            if (cityConstructions.isBuilt(capitalCityIndicator)) {
                cityConstructions.removeBuilding(capitalCityIndicator)
                if (oldCiv.cities.isNotEmpty()) {
                    oldCiv.cities.first().cityConstructions.addBuilding(capitalCityIndicator) // relocate palace
                }
            }

            for (building in cityConstructions.getBuiltBuildings()) {
                if (building.isNationalWonder && !building.hasUnique("Never destroyed when the city is captured"))
                    cityConstructions.removeBuilding(building.name)
            }

            // Place palace for newCiv if this is the only city they have
            if (newCivInfo.cities.count() == 1) {
                cityConstructions.addBuilding(capitalCityIndicator)
            }

            isBeingRazed = false

            // Transfer unique buildings
            for (building in cityConstructions.getBuiltBuildings()) {
                val civEquivalentBuilding = newCivInfo.getEquivalentBuilding(building.name)
                if (building != civEquivalentBuilding) {
                    cityConstructions.removeBuilding(building.name)
                    cityConstructions.addBuilding(civEquivalentBuilding.name)
                }
            }
            
            if (civInfo.gameInfo.hasReligionEnabled()) religion.removeUnknownPantheons()

            tryUpdateRoadStatus()
            cityStats.update()
        }
    }


}