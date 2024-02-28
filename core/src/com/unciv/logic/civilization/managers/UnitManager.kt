package com.unciv.logic.civilization.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit

class UnitManager(val civInfo: Civilization) {

    /**
     * We never add or remove from here directly, could cause comodification problems.
     * Instead, we create a copy list with the change, and replace this list.
     * The other solution, casting toList() every "get", has a performance cost
     */
    @Transient
    private var unitList = listOf<MapUnit>()

    /**
     * Index in the unit list above of the unit that is potentially due and is next up for button "Next unit".
     */
    @Transient
    private var nextPotentiallyDueAt = 0

    fun addUnit(unitName: String, city: City? = null): MapUnit? {
        val unit = civInfo.gameInfo.ruleset.units[unitName] ?: return null
        return addUnit(unit, city)
    }

    fun addUnit(baseUnit: BaseUnit, city: City? = null): MapUnit? {
        if (civInfo.cities.isEmpty()) return null

        val unit = civInfo.getEquivalentUnit(baseUnit)
        val cityToAddTo = when {
            unit.isWaterUnit() && (city == null || !city.isCoastal()) ->
                civInfo.cities.filter { it.isCoastal() }.randomOrNull()
            city != null -> city
            else -> civInfo.cities.random()
        } ?: return null // If we got a free water unit with no coastal city to place it in
        val placedUnit = placeUnitNearTile(cityToAddTo.location, unit.name)
        // silently bail if no tile to place the unit is found
            ?: return null
        if (unit.isGreatPerson()) {
            civInfo.addNotification("A [${unit.name}] has been born in [${cityToAddTo.name}]!", placedUnit.getTile().position, NotificationCategory.General, unit.name)
        }

        if (placedUnit.hasUnique(UniqueType.ReligiousUnit) && civInfo.gameInfo.isReligionEnabled()) {
            if (!placedUnit.hasUnique(UniqueType.TakeReligionOverBirthCity)
                || civInfo.religionManager.religion?.isMajorReligion() == false) {
                placedUnit.religion = cityToAddTo.religion.getMajorityReligionName()
            }
        }

        return placedUnit
    }

    /** Tries to place the a [unitName] unit into the [Tile] closest to the given the [location]
     * @param location where to try to place the unit
     * @param unitName name of the [BaseUnit] to create and place
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(location: Vector2, unitName: String): MapUnit? {
        val unit = civInfo.gameInfo.ruleset.units[unitName]!!
        return placeUnitNearTile(location, unit)
    }

    /** Tries to place the a [baseUnit] unit into the [Tile] closest to the given the [location]
     * @param location where to try to place the unit
     * @param baseUnit [BaseUnit] to create and place
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(location: Vector2, baseUnit: BaseUnit): MapUnit? {
        val unit = civInfo.gameInfo.tileMap.placeUnitNearTile(location, baseUnit, civInfo)

        if (unit != null) {
            val triggerNotificationText = "due to gaining a [${unit.name}]"
            for (unique in unit.getUniques())
                if (!unique.hasTriggerConditional() && unique.conditionalsApply(StateForConditionals(civInfo, unit = unit)))
                    UniqueTriggerActivation.triggerUnique(unique, unit, triggerNotificationText = triggerNotificationText)
            for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponGainingUnit))
                if (unique.conditionals.any { it.type == UniqueType.TriggerUponGainingUnit &&
                        unit.matchesFilter(it.params[0]) })
                    UniqueTriggerActivation.triggerUnique(unique, unit, triggerNotificationText = triggerNotificationText)
            if (unit.getResourceRequirementsPerTurn().isNotEmpty())
                civInfo.cache.updateCivResources()

            for (unique in civInfo.getMatchingUniques(UniqueType.LandUnitsCrossTerrainAfterUnitGained)) {
                if (unit.matchesFilter(unique.params[1])) {
                    civInfo.passThroughImpassableUnlocked = true    // Update the cached Boolean
                    civInfo.passableImpassables.add(unique.params[0])   // Add to list of passable impassables
                }
            }

            if (unit.hasUnique(UniqueType.ReligiousUnit) && civInfo.gameInfo.isReligionEnabled()) {
                unit.religion = civInfo.religionManager.religion?.name
            }
        }
        return unit
    }
    fun getCivUnitsSize(): Int = unitList.size
    fun getCivUnits(): Sequence<MapUnit> = unitList.asSequence()
    fun getCivGreatPeople(): Sequence<MapUnit> = getCivUnits().filter { mapUnit -> mapUnit.isGreatPerson() }

    // Similar to getCivUnits(), but the returned list is rotated so that the
    // 'nextPotentiallyDueAt' unit is first here.
    private fun getCivUnitsStartingAtNextDue(): Sequence<MapUnit> = sequenceOf(unitList.subList(nextPotentiallyDueAt, unitList.size) + unitList.subList(0, nextPotentiallyDueAt)).flatten()

    fun addUnit(mapUnit: MapUnit, updateCivInfo: Boolean = true) {
        // Since we create a new list anyway (otherwise some concurrent modification
        // exception will happen), also rearrange existing units so that
        // 'nextPotentiallyDueAt' becomes 0.  This way new units are always last to be due
        // (can be changed as wanted, just have a predictable place).
        val newList = getCivUnitsStartingAtNextDue().toMutableList()
        newList.add(mapUnit)
        unitList = newList
        nextPotentiallyDueAt = 0

        if (updateCivInfo) {
            // Not relevant when updating Tile transients, since some info of the civ itself isn't yet available,
            // and in any case it'll be updated once civ info transients are
            civInfo.updateStatsForNextTurn() // unit upkeep
            if (mapUnit.getResourceRequirementsPerTurn().isNotEmpty())
                civInfo.cache.updateCivResources()
        }
    }

    fun removeUnit(mapUnit: MapUnit) {
        // See comment in addUnit().
        val newList = getCivUnitsStartingAtNextDue().toMutableList()
        newList.remove(mapUnit)
        unitList = newList
        nextPotentiallyDueAt = 0

        civInfo.updateStatsForNextTurn() // unit upkeep
        if (mapUnit.getResourceRequirementsPerTurn().isNotEmpty())
            civInfo.cache.updateCivResources()
    }

    fun getIdleUnits() = getCivUnits().filter { it.isIdle() }

    fun getDueUnits(): Sequence<MapUnit> = getCivUnitsStartingAtNextDue().filter { it.due && it.isIdle() }

    fun shouldGoToDueUnit() = UncivGame.Current.settings.checkForDueUnits && getDueUnits().any()

    // Return the next due unit, but preferably not 'unitToSkip': this is returned only if it is the only remaining due unit.
    fun cycleThroughDueUnits(unitToSkip: MapUnit? = null): MapUnit? {
        if (unitList.none()) return null

        var returnAt = nextPotentiallyDueAt
        var fallbackAt = -1

        do {
            if (unitList[returnAt].due && unitList[returnAt].isIdle()) {
                if (unitList[returnAt] != unitToSkip) {
                    nextPotentiallyDueAt = (returnAt + 1) % unitList.size
                    return unitList[returnAt]
                }
                else fallbackAt = returnAt
            }

            returnAt = (returnAt + 1) % unitList.size
        } while (returnAt != nextPotentiallyDueAt)

        if (fallbackAt >= 0) {
            nextPotentiallyDueAt = (fallbackAt + 1) % unitList.size
            return unitList[fallbackAt]
        }
        else return null
    }

}
