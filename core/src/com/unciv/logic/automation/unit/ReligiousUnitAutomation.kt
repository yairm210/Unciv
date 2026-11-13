package com.unciv.logic.automation.unit

import com.unciv.view.GameView
import com.unciv.view.MapUnitView
import com.unciv.view.ForeignCityView
import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

object ReligiousUnitAutomation {

    fun automateMissionary(unitView: MapUnitView) {
        if (unitView.religion != unitView.civ().getReligion()?.name || unitView.religion == null) {
            unitView.tryDisband()
            return
        }

        val civ = unitView.civ()
        val ourCitiesWithoutReligion = civ.cities().filter { it.getMajorityReligion() != civ.getReligion() }

        @Readonly
        fun isValidSpreadReligionTarget(city: ForeignCityView): Boolean {
            val diplomacyManager = civ.getDiplomacyManagerWith(city.owningCiv())
            if (diplomacyManager?.hasFlag(DiplomacyFlags.AgreedToNotSpreadReligion) == true){
                // See NextTurnAutomation - these are the conditions under which AI agrees to religious demands
                // If they still hold, keep the agreement, otherwise we can renege
                if (diplomacyManager.relationshipLevel() == RelationshipLevel.Ally) return false
                if (Automation.threatAssessment(civ, city.owningCiv()) >= ThreatLevel.High) return false
            }
            return true
        }

        /** Lowest value will be chosen */
        @Readonly
        fun rankCityForReligionSpread(city: ForeignCityView): Int {
            var rank = city.getCenterTile().aerialDistanceTo(unitView.getTile())

            val diplomacyManager = civ.getDiplomacyManagerWith(city.owningCiv())
            if (diplomacyManager?.hasFlag(DiplomacyFlags.AgreedToNotSpreadReligion) == true){
                rank += 10 // Greatly discourage, but if the other options are too far away we'll take it anyway
            }

            return rank
        }


        val city: ForeignCityView? =
            if (ourCitiesWithoutReligion.any())
                ourCitiesWithoutReligion.minByOrNull { it.getCenterTile().aerialDistanceTo(unitView.getTile()) }
            else civ.getKnownCivs().asSequence()
                .filterNot { it.isAtWarWith(civ) }
                .flatMap { it.cities() }
                .filter { it.getMajorityReligion() != civ.getReligion() }
                .filterNot { isProbablyProtectedByInquisitor(it, unitView.religion) }
                .filter { isValidSpreadReligionTarget(it) }
                .minByOrNull { rankCityForReligionSpread(it) }

        if (city == null) return
        val unitTile = unitView.getTile()
        val destination = city.getTiles()
            .filter { unitView.canMoveTo(it) || it == unitTile }
            .sortedBy { it.aerialDistanceTo(unitTile) }
            .firstOrNull { unitView.canReach(it) } ?: return

        unitView.tryHeadTowards(destination)

        // The action is only enabled if the unit may spread religion in the city it's standing in
        if (unitView.getTile() in city.getTiles()) {
            unitView.tryInvokeUnitAction(UnitActionType.SpreadReligion)
        }
    }

    /** Estimate of [com.unciv.logic.city.managers.CityReligionManager.isProtectedByInquisitor] using data available */
    @Readonly
    private fun isProbablyProtectedByInquisitor(city: ForeignCityView, fromReligion: String?): Boolean =
        city.getCenterTile().getExploredTilesInDistance(1)
            .flatMap { tile -> tile.getVisibleUnits() }
            .any { unit ->
                unit.religion != null
                    && (fromReligion == null || unit.religion != fromReligion)
                    && unit.hasUnique(UniqueType.PreventSpreadingReligion)
            }

    fun automateInquisitor(unitView: MapUnitView) {
        val civ = unitView.civ()
        val civReligion = civ.getReligion()

        if (unitView.religion != civReligion?.name || unitView.religion == null) {
            // No need to keep a unit we can't use, as it only blocks religion spreads of religions other that its own
            unitView.tryDisband()
            return
        }

        val holyCity = civ.getHolyCity()
        val cityToConvert = determineBestInquisitorCityToConvert(unitView) // Also returns null if the inquisitor can't convert cities
        val pressureDeficit =
            if (cityToConvert == null) 0
            else cityToConvert.getPressureDeficit(civReligion?.name)

        val citiesToProtect = civ.cities().asSequence()
            .filter { it.getMajorityReligion() == civReligion }
            // We only look at cities that are not currently protected or are protected by us
            .filter { !it.isProtectedByInquisitor() || unitView.getTile() in it.getCenterTile().getNeighbors() }

        // cities with most populations will be prioritized by the AI
        val cityToProtect = citiesToProtect.maxByOrNull { it.getPopulationCount() }

        val destinationCity: ForeignCityView? = when {
            cityToConvert != null
                && (cityToConvert == holyCity
                || pressureDeficit > Constants.aiPreferInquisitorOverMissionaryPressureDifference
                || cityToConvert.isBlockedHolyCity() && cityToConvert.getReligionThisIsTheHolyCityOf() == civReligion?.name
                ) && unitView.hasUnique(UniqueType.CanRemoveHeresy) -> {
                cityToConvert
            }
            cityToProtect != null && unitView.hasUnique(UniqueType.PreventSpreadingReligion) -> {
                if (holyCity != null && !holyCity.isProtectedByInquisitor())
                    holyCity
                else cityToProtect
            }
            cityToConvert != null -> cityToConvert
            else -> null
        }

        if (destinationCity == null) return
        val unitTile = unitView.getTile()
        val destinationTile = destinationCity.getCenterTile().getNeighbors()
                .filter { unitView.canMoveTo(it) || it == unitTile }
                .sortedBy { it.aerialDistanceTo(unitTile) }
                .firstOrNull { unitView.canReach(it) }
                ?: return

        unitView.tryHeadTowards(destinationTile)

        if (cityToConvert != null && unitView.getTile().getCity() == destinationCity) {
            unitView.tryInvokeUnitAction(UnitActionType.RemoveHeresy)
        }
    }

    @Readonly
    private fun determineBestInquisitorCityToConvert(
        unitView: MapUnitView,
    ): ForeignCityView? {
        val civ = unitView.civ()
        val civReligion = civ.getReligion()
        if (unitView.religion != civReligion?.name || !unitView.hasUnique(UniqueType.CanRemoveHeresy))
            return null

        val holyCity = civ.getHolyCity()
        // Our own holy city was taken over!
        if (holyCity != null && holyCity.owningCiv() == civ
            && holyCity.getMajorityReligion() != civReligion!!)
            return holyCity

        val blockedHolyCity = civ.cities().firstOrNull { it.isBlockedHolyCity() && it.getReligionThisIsTheHolyCityOf() == unitView.religion }
        if (blockedHolyCity != null)
            return blockedHolyCity

        // Find cities 
        val relevantCities = civ.cities().asSequence()
            // Someone else is controlling this city
            .filter { 
                val majorityReligion = it.getMajorityReligion()
                majorityReligion != null && majorityReligion != civReligion
            }
        
        val unitTile = unitView.getTile()
        val closeCity = relevantCities
            .filter { it.getCenterTile().aerialDistanceTo(unitTile) <= 20 }
            // Find the city that we're the closest to converting
            .minByOrNull { it.getPressureDeficit(civReligion?.name) }
        if (closeCity != null) return closeCity
        
        return relevantCities.minByOrNull { it.getPressureDeficit(civReligion?.name) }
    }


    fun foundReligion(unitView: MapUnitView) {
        val unitTile = unitView.getTile()
        val cityToFoundReligionAt =
            if (unitTile.isCityCenter() && !unitTile.owningCity()!!.isHolyCity()) unitTile.owningCity()
            else unitView.civ().cities().firstOrNull {
                !it.isHolyCity()
                    && unitView.canMoveTo(it.getCenterTile())
                    && unitView.canReach(it.getCenterTile())
            }
        if (cityToFoundReligionAt == null) return
        if (unitTile != cityToFoundReligionAt.getCenterTile()) {
            unitView.tryHeadTowards(cityToFoundReligionAt.getCenterTile())
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
