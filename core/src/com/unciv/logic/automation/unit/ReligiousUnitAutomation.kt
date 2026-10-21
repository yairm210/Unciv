package com.unciv.logic.automation.unit

import com.unciv.view.GameView
import com.unciv.view.MapUnitView
import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.city.City
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

object ReligiousUnitAutomation {

    fun automateMissionary(unitView: MapUnitView) {
        val unit = unitView.getUnit()
        if (unitView.religion != unitView.civ().getReligion()?.name || unitView.religion == null) {
            unitView.tryDisband()
            return
        }

        val ourCitiesWithoutReligion = unit.civ.cities.filter {
            it.religion.getMajorityReligion() != unitView.civ().getReligion()
        }
        
        @Readonly
        fun isValidSpreadReligionTarget(city: City): Boolean {
            val diplomacyManager = unit.civ.getDiplomacyManager(city.civ)
            if (diplomacyManager?.hasFlag(DiplomacyFlags.AgreedToNotSpreadReligion) == true){
                // See NextTurnAutomation - these are the conditions under which AI agrees to religious demands
                // If they still hold, keep the agreement, otherwise we can renege
                if (diplomacyManager.relationshipLevel() == RelationshipLevel.Ally) return false
                if (Automation.threatAssessment(unitView.civ(), GameView(unit.civ.gameInfo, unit.civ).getForeignCivView(city.civ)) >= ThreatLevel.High) return false
            }
            return true
        }

        /** Lowest value will be chosen */
        @Readonly
        fun rankCityForReligionSpread(city: City): Int {
            var rank = city.getCenterTile().aerialDistanceTo(unit.getTile())
            
            val diplomacyManager = unit.civ.getDiplomacyManager(city.civ)
            if (diplomacyManager?.hasFlag(DiplomacyFlags.AgreedToNotSpreadReligion) == true){
                rank += 10 // Greatly discourage, but if the other options are too far away we'll take it anyway
            }
                
            return rank
        }
        
        
        val city =
            if (ourCitiesWithoutReligion.any())
                ourCitiesWithoutReligion.minByOrNull { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            else unit.civ.gameInfo.getCities()
                .filter { it.religion.getMajorityReligion() != unitView.civ().getReligion() }
                .filter { it.civ.knows(unit.civ) && !it.civ.isAtWarWith(unit.civ) }
                .filterNot { it.religion.isProtectedByInquisitor(unitView.religion) }
                .filter { isValidSpreadReligionTarget(it) }
                .minByOrNull { rankCityForReligionSpread(it) }

        if (city == null) return
        val destination = city.getTiles()
            .filter { unit.movement.canMoveTo(it) || it == unit.getTile() }
            .sortedBy { it.aerialDistanceTo(unit.getTile()) }
            .firstOrNull { unit.movement.canReach(it) } ?: return

        unit.movement.headTowards(destination)

        if (unit.getTile() in city.getTiles() && unit.civ.religionManager.maySpreadReligionNow(unit)) {
            unitView.tryInvokeUnitAction(UnitActionType.SpreadReligion)
        }
    }

    fun automateInquisitor(unitView: MapUnitView) {
        val unit = unitView.getUnit()
        val civReligion = unitView.civ().getReligion()

        if (unitView.religion != civReligion?.name || unitView.religion == null) {
            // No need to keep a unit we can't use, as it only blocks religion spreads of religions other that its own
            unitView.tryDisband()
            return
        }

        val holyCity = unit.civ.religionManager.getHolyCity()
        val cityToConvert = determineBestInquisitorCityToConvert(unitView) // Also returns null if the inquisitor can't convert cities
        val pressureDeficit =
            if (cityToConvert == null) 0
            else cityToConvert.religion.getPressureDeficit(civReligion?.name)

        val citiesToProtect = unit.civ.cities.asSequence()
            .filter { it.religion.getMajorityReligion() == civReligion }
            // We only look at cities that are not currently protected or are protected by us
            .filter { !it.religion.isProtectedByInquisitor() || unit.getTile() in it.getCenterTile().neighbors }

        // cities with most populations will be prioritized by the AI
        val cityToProtect = citiesToProtect.maxByOrNull { it.population.population }

        val destinationCity: City? = when {
            cityToConvert != null
                && (cityToConvert == holyCity
                || pressureDeficit > Constants.aiPreferInquisitorOverMissionaryPressureDifference
                || cityToConvert.religion.isBlockedHolyCity && cityToConvert.religion.religionThisIsTheHolyCityOf == civReligion?.name
                ) && unitView.hasUnique(UniqueType.CanRemoveHeresy) -> {
                cityToConvert
            }
            cityToProtect != null && unitView.hasUnique(UniqueType.PreventSpreadingReligion) -> {
                if (holyCity != null && !holyCity.religion.isProtectedByInquisitor())
                    holyCity
                else cityToProtect
            }
            cityToConvert != null -> cityToConvert
            else -> null
        }

        if (destinationCity == null) return
        val destinationTile = destinationCity.getCenterTile().neighbors
                .filter { unit.movement.canMoveTo(it) || it == unit.getTile() }
                .sortedBy { it.aerialDistanceTo(unit.currentTile) }
                .firstOrNull { unit.movement.canReach(it) }
                ?: return

        unit.movement.headTowards(destinationTile)

        if (cityToConvert != null && unit.getTile().getCity() == destinationCity) {
            unitView.tryInvokeUnitAction(UnitActionType.RemoveHeresy)
        }
    }

    @Readonly
    private fun determineBestInquisitorCityToConvert(
        unitView: MapUnitView,
    ): City? {
        val unit = unitView.getUnit()
        if (unitView.religion != unitView.civ().getReligion()?.name || !unitView.hasUnique(UniqueType.CanRemoveHeresy))
            return null

        val holyCity = unit.civ.religionManager.getHolyCity()
        // Our own holy city was taken over!
        if (holyCity != null && holyCity.civ == unit.civ
            && holyCity.religion.getMajorityReligion() != unitView.civ().getReligion()!!)
            return holyCity

        val blockedHolyCity = unit.civ.cities.firstOrNull { it.religion.isBlockedHolyCity && it.religion.religionThisIsTheHolyCityOf == unitView.religion }
        if (blockedHolyCity != null)
            return blockedHolyCity

        // Find cities 
        val relevantCities = unit.civ.cities.asSequence()
            .filter { it.getCenterTile().isExplored(unit.civ) } // Cities we know about
            // Someone else is controlling this city
            .filter { 
                val majorityReligion = it.religion.getMajorityReligion()
                majorityReligion != null && majorityReligion != unitView.civ().getReligion()
            }
        
        val closeCity = relevantCities
            .filter { it.getCenterTile().aerialDistanceTo(unit.currentTile) <= 20 }
            // Find the city that we're the closest to converting
            .minByOrNull { it.religion.getPressureDeficit(unitView.civ().getReligion()?.name) }
        if (closeCity != null) return closeCity
        
        return relevantCities.minByOrNull { it.religion.getPressureDeficit(unitView.civ().getReligion()?.name) }
    }


    fun foundReligion(unitView: MapUnitView) {
        val unit = unitView.getUnit()
        val cityToFoundReligionAt =
            if (unit.getTile().isCityCenter() && !unit.getTile().owningCity!!.isHolyCity()) unit.getTile().owningCity
            else unit.civ.cities.firstOrNull {
                !it.isHolyCity()
                    && unit.movement.canMoveTo(it.getCenterTile())
                    && unit.movement.canReach(it.getCenterTile())
            }
        if (cityToFoundReligionAt == null) return
        if (unit.getTile() != cityToFoundReligionAt.getCenterTile()) {
            unit.movement.headTowards(cityToFoundReligionAt.getCenterTile())
            return
        }

        unitView.tryInvokeUnitAction(UnitActionType.FoundReligion)
    }

    fun enhanceReligion(unitView: MapUnitView) {
        // Try go to a nearby city
        if (!unitView.getTile().isCityCenter())
            UnitAutomation.tryEnterOwnClosestCity(unitView)

        // If we were unable to go there this turn, unable to do anything else
        if (!unitView.getTile().isCityCenter())
            return

        unitView.tryInvokeUnitAction(UnitActionType.EnhanceReligion)
    }

}
