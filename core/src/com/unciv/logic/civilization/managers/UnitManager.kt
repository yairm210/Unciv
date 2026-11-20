package com.unciv.logic.civilization.managers

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.transients.CivInfoTransientCache
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import yairm210.purity.annotations.Readonly

class UnitManager(val civInfo: Civilization) {

    /**
     *  All units of [civInfo], _ordered_.
     *
     *  When loading a save, this is entirely rebuilt from Tile.*Unit.
     *  * GameInfo.setTransients -> TileMap.setTransients -> Tile.setUnitTransients -> MapUnit.assignOwner -> [addUnit] (the MapUnit overload)
     *
     *  We never add or remove from here directly, could cause comodification problems.
     *  Instead, we create a copy list with the change, and replace this list.
     *  The other solution, casting toList() every "get", has a performance cost
     */
    private var unitList = listOf<MapUnit>()

    /** Creates a new [MapUnit] and places it on the map.
     *  @param  unitName The [BaseUnit] name to create a MapUnit instance of - auto-mapped to a nation equivalent if one exists
     *  @param  city     The City to place the new unit in or near
     *  @return          The new unit or `null` if unsuccessful (invalid unitName, no tile found where it could be placed, or civ has no cities)
     */
    fun addUnit(unitName: String, city: City? = null): MapUnit? {
        val unit = civInfo.gameInfo.ruleset.units[unitName] ?: return null
        return addUnit(unit, city)
    }

    /** Creates a new [MapUnit] and places it on the map.
     *  @param  baseUnit The [BaseUnit] to create a MapUnit instance of - auto-mapped to a nation equivalent if one exists
     *  @param  city     The City to place the new unit in or near
     *  @return          The new unit or `null` if unsuccessful (no tile found where it could be placed, or civ has no cities)
     */
    fun addUnit(baseUnit: BaseUnit, city: City? = null): MapUnit? {
        if (civInfo.cities.isEmpty()) return null
        val rng = (city?.state ?: civInfo.state).stateBasedRandom("UnitManager.addUnit")

        val unit = civInfo.getEquivalentUnit(baseUnit)
        val citiesNotInResistance = civInfo.cities.filterNot { it.isInResistance() }

        // To allow cities on water tiles to be able to build water units.
        val canSpawnUnitOnWater = city?.isNaval() == true
        val cityToAddTo = when {
            unit.isWaterUnit && canSpawnUnitOnWater -> city
            unit.isWaterUnit ->
                citiesNotInResistance.filter { it.isNaval() }.randomOrNull(rng) ?:
                civInfo.cities.filter { it.isNaval() }.randomOrNull(rng)
            city != null -> city
            else -> citiesNotInResistance.randomOrNull(rng) ?: civInfo.cities.random(rng)
        } ?: return null // If we got a free water unit with no coastal city to place it in
        val placedUnit = placeUnitNearTile(cityToAddTo.location.toHexCoord(), unit.name)
        // silently bail if no tile to place the unit is found
            ?: return null
        if (unit.isGreatPerson) {
            civInfo.addNotification("A [${unit.name}] has been born in [${cityToAddTo.name}]!", MapUnitAction(placedUnit), NotificationCategory.General, unit.name)
            civInfo.gameInfo.getAliveCityStates().forEach {
                it.questManager.handleIndividualQuests() // Maybe there's an "acquire great person" quest active
            }
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
    fun placeUnitNearTile(location: HexCoord, unitName: String): MapUnit? {
        val unit = civInfo.gameInfo.ruleset.units[unitName]!!
        return placeUnitNearTile(location, unit)
    }

    /** Tries to place the a [baseUnit] unit into the [Tile] closest to the given the [location]
     * @param location where to try to place the unit
     * @param baseUnit [BaseUnit] to create and place
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(location: HexCoord, baseUnit: BaseUnit, unitId: Int? = null, copiedFrom: MapUnit? = null): MapUnit? {
        val unit = civInfo.gameInfo.tileMap.placeUnitNearTile(location, baseUnit, civInfo, unitId)

        if (unit == null) return unit
        
        // Must happen before the triggers or else things like "[This Unit] loses the [promotionName] promotion"
        //    will trigger *before* the unit actually has the promotion, and then will get the promotion anyway.
        copiedFrom?.copyStatisticsTo(unit)

        val triggerNotificationText = "due to gaining a [${unit.name}]"
        for (unique in unit.getUniques().toList()) {
            // Promotion triggerables are ALREADY handled in placeUnitNearTile -> addPromotion, where they were added
            if (unique.hasTriggerConditional() ||
                unique.sourceObjectType == UniqueTarget.Promotion ||
                !unique.conditionalsApply(unit.cache.state)
            ) continue
            repeat(unique.getUniqueMultiplier(unit.cache.state)) {
                UniqueTriggerActivation.triggerUnique(unique, unit, triggerNotificationText = triggerNotificationText)
            }
        }

        civInfo.forEachTriggeredUnique(UniqueType.TriggerUponGainingUnit, unit.cache.state,
                { unit.matchesFilter(it.params[0]) }) { unique ->
            UniqueTriggerActivation.triggerUnique(unique, unit, triggerNotificationText = triggerNotificationText)
        }

        if (unit.getResourceRequirementsPerTurn().isNotEmpty())
            civInfo.cache.updateCivResources()

        civInfo.forEachMatchingUnique(UniqueType.LandUnitsCrossTerrainAfterUnitGained, unit.cache.state) { unique ->
            if (unit.matchesFilter(unique.params[1])) {
                civInfo.passThroughImpassableUnlocked = true    // Update the cached Boolean
                civInfo.passableImpassables.add(unique.params[0])   // Add to list of passable impassables
            }
        }

        if (unit.hasUnique(UniqueType.ReligiousUnit) && civInfo.gameInfo.isReligionEnabled()) {
            unit.religion = civInfo.religionManager.religion?.name
        }
        return unit
    }

    @Readonly fun getCivUnitsSize(): Int = unitList.size
    @Readonly fun getCivUnits(): Sequence<MapUnit> = unitList.asSequence()
    @Readonly fun getCivGreatPeople(): Sequence<MapUnit> = getCivUnits().filter { mapUnit -> mapUnit.isGreatPerson() }

    /** Assigns an existing [mapUnit] to this manager.
     *
     *  Used during load game via setTransients to regenerate a Civilization's list from the serialized Tile fields.
     *  @param updateCivInfo When `true`, calls [updateStatsForNextTurn][Civilization.updateStatsForNextTurn] and possibly [updateCivResources][CivInfoTransientCache]
     */
    fun addUnit(mapUnit: MapUnit, updateCivInfo: Boolean = true) {
        // We create a new list (otherwise some concurrent modification exception will happen)
        val newList = unitList.toMutableList()
        newList.add(mapUnit)
        unitList = newList

        if (updateCivInfo) {
            // Not relevant when updating Tile transients, since some info of the civ itself isn't yet available,
            // and in any case it'll be updated once civ info transients are
            civInfo.updateStatsForNextTurn() // unit upkeep
            if (mapUnit.getResourceRequirementsPerTurn().isNotEmpty())
                civInfo.cache.updateCivResources()
        }
    }

    fun removeUnit(mapUnit: MapUnit, updateCivInfo: Boolean = true) {
        // See comment in addUnit().
        val newList = unitList.toMutableList()
        newList.remove(mapUnit)
        unitList = newList
        if (!updateCivInfo) return

        civInfo.updateStatsForNextTurn() // unit upkeep
        if (mapUnit.getResourceRequirementsPerTurn().isNotEmpty())
            civInfo.cache.updateCivResources()

        civInfo.forEachTriggeredUnique(UniqueType.TriggerUponLosingUnit, mapUnit.cache.state,
                { mapUnit.matchesFilter(it.params[0]) }) { unique ->
            UniqueTriggerActivation.triggerUnique(unique, mapUnit)
        }
    }

    @Readonly fun getIdleUnits() = getCivUnits().filter { it.isIdle() }

    @Readonly fun getUnitById(id: Int) = getCivUnits().firstOrNull { it.id == id }
}
