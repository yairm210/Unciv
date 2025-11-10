package com.unciv.logic.city.managers

import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.battle.Battle
import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityFocus
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.UnitPromotions
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Helper class for containing 200 lines of "how to move cities between civs" */
class CityConquestFunctions(val city: City) {
    private val tileBasedRandom = Random(city.getCenterTile().position.toString().hashCode())

    @Readonly
    private fun getGoldForCapturingCity(conqueringCiv: Civilization): Int {
        val baseGold = 20 + 10 * city.population.population + tileBasedRandom.nextInt(40)
        val turnModifier = max(0, min(50, city.civ.gameInfo.turns - city.turnAcquired)) / 50f
        val cityModifier = if (city.containsBuildingUnique(UniqueType.DoublesGoldFromCapturingCity)) 2f else 1f
        val conqueringCivModifier = if (conqueringCiv.hasUnique(UniqueType.TripleGoldFromEncampmentsAndCities)) 3f else 1f

        val goldPlundered = baseGold * turnModifier * cityModifier * conqueringCivModifier
        return goldPlundered.toInt()
    }

    private fun destroyBuildingsOnCapture() {
        // Possibly remove other buildings
        for (building in city.cityConstructions.getBuiltBuildings()) {
            when {
                building.hasUnique(UniqueType.NotDestroyedWhenCityCaptured) || building.isWonder -> continue
                building.hasUnique(UniqueType.IndicatesCapital, city.state) -> continue // Palace needs to stay a just a bit longer so moveToCiv isn't confused
                building.hasUnique(UniqueType.DestroyedWhenCityCaptured) ->
                    city.cityConstructions.removeBuilding(building)
                // Regular buildings have a 34% chance of removal
                tileBasedRandom.nextInt(100) < 34 -> city.cityConstructions.removeBuilding(building)
            }
        }
    }
    
    private fun removeAutoPromotion() {
        city.unitShouldUseSavedPromotion = HashMap<String, Boolean>()
        city.unitToPromotions = HashMap<String, UnitPromotions>()
    }

    private fun removeBuildingsOnMoveToCiv() {
        // Remove all buildings provided for free to this city
        // At this point, the city has *not* yet moved to the new civ
        for (building in city.civ.civConstructions.getFreeBuildingNames(city)) {
            city.cityConstructions.removeBuilding(building)
        }
        city.cityConstructions.freeBuildingsProvidedFromThisCity.clear()

        for (building in city.cityConstructions.getBuiltBuildings()) {
            // Remove national wonders
            if (building.isNationalWonder && !building.hasUnique(UniqueType.NotDestroyedWhenCityCaptured))
                city.cityConstructions.removeBuilding(building)

            // Check if we exceed MaxNumberBuildable for any buildings
            for (unique in building.getMatchingUniques(UniqueType.MaxNumberBuildable)) {
                if (city.civ.cities
                        .count {
                            it.cityConstructions.containsBuildingOrEquivalent(building.name)
                                || it.cityConstructions.isBeingConstructedOrEnqueued(building.name)
                        } >= unique.params[0].toInt()
                ) {
                    // For now, just destroy in new city. Even if constructing in own cities
                    city.cityConstructions.removeBuilding(building)
                }
            }
        }
    }

    /** Function for stuff that should happen on any capture, be it puppet, annex or liberate.
     * Stuff that should happen any time a city is moved between civs, so also when trading,
     * should go in `this.moveToCiv()`, which is called by `this.conquerCity()`.
     */
    private fun conquerCity(conqueringCiv: Civilization, conqueredCiv: Civilization, receivingCiv: Civilization) {
        city.espionage.removeAllPresentSpies(SpyFleeReason.CityCaptured)

        // Gain gold for plundering city
        val goldPlundered = getGoldForCapturingCity(conqueringCiv)
        conqueringCiv.addGold(goldPlundered)
        conqueringCiv.addNotification("Received [$goldPlundered] Gold for capturing [${city.name}]",
            city.getCenterTile().position, NotificationCategory.General, NotificationIcon.Gold)

        val reconqueredCityWhileStillInResistance = city.previousOwner == receivingCiv.civName && city.isInResistance()

        destroyBuildingsOnCapture()

        city.moveToCiv(receivingCiv)

        Battle.destroyIfDefeated(conqueredCiv, conqueringCiv, city.location)

        city.health = city.getMaxHealth() / 2 // I think that cities recover to half health when conquered?
        city.avoidGrowth = false // reset settings
        city.setCityFocus(CityFocus.NoFocus) // reset settings
        if (city.population.population > 1)
            city.population.addPopulation(-1 - city.population.population / 4) // so from 2-4 population, remove 1, from 5-8, remove 2, etc.
        city.reassignAllPopulation()

        if (!reconqueredCityWhileStillInResistance && city.foundingCivObject != receivingCiv) {
            // add resistance
            // I checked, and even if you puppet there's resistance for conquering
            city.setFlag(CityFlags.Resistance, city.population.population)
        } else {
            // reconquering or liberating city in resistance so eliminate it
            city.removeFlag(CityFlags.Resistance)
        }
        
        for (unique in conqueredCiv.getTriggeredUniques(UniqueType.TriggerUponLosingCity, GameContext(civInfo = conqueredCiv))) {
            UniqueTriggerActivation.triggerUnique(unique, civInfo = conqueredCiv)
        }
    }


    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: Civilization) {
        val oldCiv = city.civ

        // must be before moving the city to the conquering civ,
        // so the repercussions are properly checked
        diplomaticRepercussionsForConqueringCity(oldCiv, conqueringCiv)

        conquerCity(conqueringCiv, oldCiv, conqueringCiv)

        city.isPuppet = true
        city.cityStats.update()
        // The city could be producing something that puppets shouldn't, like units
        city.cityConstructions.currentConstructionIsUserSet = false
        city.cityConstructions.inProgressConstructions.clear() // undo all progress of the previous civ on units etc.
        city.cityConstructions.constructionQueue.clear()
        city.cityConstructions.chooseNextConstruction()
    }

    fun annexCity() {
        city.isPuppet = false
        if (!city.isInResistance()) city.shouldReassignPopulation = true
        city.avoidGrowth = false
        city.setCityFocus(CityFocus.NoFocus)
        city.cityStats.update()
        GUI.setUpdateWorldOnNextRender()
    }

    private fun diplomaticRepercussionsForConqueringCity(oldCiv: Civilization, conqueringCiv: Civilization) {
        val currentPopulation = city.population.population
        val percentageOfCivPopulationInThatCity = currentPopulation * 100f /
                oldCiv.cities.sumOf { it.population.population }
        val aggroGenerated = 10f + percentageOfCivPopulationInThatCity.roundToInt()

        // How can you conquer a city but not know the civ you conquered it from?!
        // I don't know either, but some of our players have managed this, and crashed their game!
        if (!conqueringCiv.knows(oldCiv))
            conqueringCiv.diplomacyFunctions.makeCivilizationsMeet(oldCiv)

        oldCiv.getDiplomacyManager(conqueringCiv)!!
                .addModifier(DiplomaticModifiers.CapturedOurCities, -aggroGenerated)

        for (thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() }) {
            val aggroGeneratedForOtherCivs = (aggroGenerated / 10).roundToInt().toFloat()
            if (thirdPartyCiv.isAtWarWith(oldCiv)) // Shared Enemies should like us more
                thirdPartyCiv.getDiplomacyManager(conqueringCiv)!!
                        .addModifier(DiplomaticModifiers.SharedEnemy, aggroGeneratedForOtherCivs) // Cool, keep at it! =D
            else thirdPartyCiv.getDiplomacyManager(conqueringCiv)!!
                    .addModifier(DiplomaticModifiers.WarMongerer, -aggroGeneratedForOtherCivs) // Uncool bro.
        }
    }

    fun liberateCity(conqueringCiv: Civilization) {
        if (city.foundingCivObject == null) { // this should never happen but just in case...
            this.puppetCity(conqueringCiv)
            this.annexCity()
            return
        }

        val foundingCiv = city.foundingCivObject!!
        if (foundingCiv.isDefeated()) // resurrected civ
            for (diploManager in foundingCiv.diplomacy.values)
                if (diploManager.diplomaticStatus == DiplomaticStatus.War)
                    diploManager.makePeace()

        val oldCiv = city.civ

        diplomaticRepercussionsForLiberatingCity(conqueringCiv, oldCiv)

        conquerCity(conqueringCiv, oldCiv, foundingCiv)

        if (foundingCiv.cities.size == 1) {
            // Resurrection!
            val capitalCityIndicator = conqueringCiv.capitalCityIndicator(city)
            if (capitalCityIndicator != null) city.cityConstructions.addBuilding(capitalCityIndicator)
            for (civ in city.civ.gameInfo.civilizations) {
                if (civ == foundingCiv || civ == conqueringCiv) continue // don't need to notify these civs
                when {
                    civ.knows(conqueringCiv) && civ.knows(foundingCiv) ->
                        civ.addNotification("[$conqueringCiv] has liberated [$foundingCiv]", NotificationCategory.Diplomacy, foundingCiv.civName, NotificationIcon.Diplomacy, conqueringCiv.civName)
                    civ.knows(conqueringCiv) && !civ.knows(foundingCiv) ->
                        civ.addNotification("[$conqueringCiv] has liberated [an unknown civilization]", NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, conqueringCiv.civName)
                    !civ.knows(conqueringCiv) && civ.knows(foundingCiv) ->
                        civ.addNotification("[An unknown civilization] has liberated [$foundingCiv]", NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, foundingCiv.civName)
                    else -> continue
                }
            }
        }
        city.isPuppet = false
        city.cityStats.update()

        // Move units out of the city when liberated
        for (unit in city.getCenterTile().getUnits().toList())
            unit.movement.teleportToClosestMoveableTile()
        for (unit in city.getTiles().flatMap { it.getUnits() }.toList())
            if (!unit.movement.canPassThrough(unit.currentTile))
                unit.movement.teleportToClosestMoveableTile()

    }


    private fun diplomaticRepercussionsForLiberatingCity(conqueringCiv: Civilization, conqueredCiv: Civilization) {
        val foundingCiv = city.foundingCivObject!!
        val percentageOfCivPopulationInThatCity = city.population.population *
                100f / (foundingCiv.cities.sumOf { it.population.population } + city.population.population)
        val respectForLiberatingOurCity = 10f + percentageOfCivPopulationInThatCity.roundToInt()

        if (foundingCiv.isMajorCiv()) {
            // In order to get "plus points" in Diplomacy, you have to establish diplomatic relations if you haven't yet
            foundingCiv.getDiplomacyManagerOrMeet(conqueringCiv)
                    .addModifier(DiplomaticModifiers.CapturedOurCities, respectForLiberatingOurCity)
            val openBordersTrade = TradeLogic(foundingCiv, conqueringCiv)
            openBordersTrade.currentTrade.ourOffers.add(TradeOffer(Constants.openBorders, TradeOfferType.Agreement, speed = conqueringCiv.gameInfo.speed))
            openBordersTrade.acceptTrade(false)
        } else {
            //Liberating a city state gives a large amount of influence, and peace
            foundingCiv.getDiplomacyManagerOrMeet(conqueringCiv).setInfluence(90f)
            if (foundingCiv.isAtWarWith(conqueringCiv)) {
                val tradeLogic = TradeLogic(foundingCiv, conqueringCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = conqueringCiv.gameInfo.speed))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = conqueringCiv.gameInfo.speed))
                tradeLogic.acceptTrade(false)
            }
        }

        val otherCivsRespectForLiberating = (respectForLiberatingOurCity / 10).roundToInt().toFloat()
        for (thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() && it != conqueredCiv }) {
            thirdPartyCiv.getDiplomacyManager(conqueringCiv)!!
                    .addModifier(DiplomaticModifiers.LiberatedCity, otherCivsRespectForLiberating) // Cool, keep at at! =D
        }
    }


    fun moveToCiv(newCiv: Civilization) {
        val oldCiv = city.civ

        // Remove/relocate palace for old Civ - need to do this BEFORE we move the cities between
        //  civs so the capitalCityIndicator recognizes the unique buildings of the conquered civ
        if (city.isCapital()) oldCiv.moveCapitalToNextLargest(city)

        oldCiv.cities = oldCiv.cities.toMutableList().apply { remove(city) }
        newCiv.cities = newCiv.cities.toMutableList().apply { add(city) }
        city.civ = newCiv
        city.state = GameContext(city)
        city.hasJustBeenConquered = false
        city.turnAcquired = city.civ.gameInfo.turns
        city.previousOwner = oldCiv.civName

        // now that the tiles have changed, we need to reassign population
        for (workedTile in city.workedTiles.filterNot { city.tiles.contains(it) }) {
            city.population.stopWorkingTile(workedTile)
            city.population.autoAssignPopulation()
        }

        // Stop WLTKD if it's still going
        city.resetWLTKD()

        // Remove their free buildings from this city and remove free buildings provided by the city from their cities
        removeBuildingsOnMoveToCiv()
        
        // Remove auto promotion from city that is being moved 
        removeAutoPromotion()

        // catch-all - should ideally not happen as we catch the individual cases with an appropriate notification
        city.espionage.removeAllPresentSpies(SpyFleeReason.Other) 
        

        // Place palace for newCiv if this is the only city they have.
        if (newCiv.cities.size == 1) newCiv.moveCapitalTo(city, null)

        // Add our free buildings to this city and add free buildings provided by the city to other cities
        city.civ.civConstructions.tryAddFreeBuildings()

        city.isBeingRazed = false

        // Transfer unique buildings
        for (building in city.cityConstructions.getBuiltBuildings()) {
            val civEquivalentBuilding = newCiv.getEquivalentBuilding(building)
            if (building != civEquivalentBuilding) {
                city.cityConstructions.removeBuilding(building)
                city.cityConstructions.addBuilding(civEquivalentBuilding)
            }
        }

        if (city.civ.gameInfo.isReligionEnabled()) city.religion.removeUnknownPantheons()

        if (newCiv.hasUnique(UniqueType.MayNotAnnexCities)) {
            city.isPuppet = true
            city.cityConstructions.currentConstructionIsUserSet = false
            city.cityConstructions.constructionQueue.clear()
            city.cityConstructions.chooseNextConstruction()
        }

        city.tryUpdateRoadStatus()
        city.cityStats.update()

        // Update proximity rankings
        city.civ.updateProximity(oldCiv, oldCiv.updateProximity(city.civ))

        // Update history
        city.getTiles().forEach { tile ->
            tile.history.recordTakeOwnership(tile)
        }

        newCiv.cache.updateOurTiles()
        oldCiv.cache.updateOurTiles()
    }

}
