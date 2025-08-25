package com.unciv.logic

import com.unciv.Constants
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.managers.TechManager
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.Ruleset
import yairm210.purity.annotations.Readonly

/**
 * Container for all temporarily used code managing transitions from deprecated elements to their replacements.
 *
 * Please place ***all*** such code here and call  it _only_ from [GameInfo.setTransients].
 * Functions are allowed to remain once no longer used if you think they might serve as template for
 * similar usecases in the future. Please comment sufficiently :)
 */
@Suppress("unused")  // as mentioned above
object BackwardCompatibility {

    /**
     * Mods can change, leading to things on the map that are no longer defined in the mod.
     * This function removes them so the game doesn't crash when it tries to access them.
     */
    fun GameInfo.removeMissingModReferences() {
        tileMap.removeMissingTerrainModReferences(ruleset)

        removeUnitsAndPromotions()
        removeMissingGreatPersonPoints()

        // Mod decided you can't repair things anymore - get rid of old pillaged improvements
        removeOldPillagedImprovements()
        removeMissingLastSeenImprovements()

        handleMissingReferencesForEachCity()

        removeTechAndPolicies()
        updateMissingStartingEra()
    }

    private fun GameInfo.updateMissingStartingEra() {
        if (gameParameters.startingEra in ruleset.eras) return
        gameParameters.startingEra = ruleset.eras.keys.first()
    }

    fun GameInfo.migrateGreatGeneralPools() {
        for (civ in civilizations) civ.greatPeople.run {
            if (pointsForNextGreatGeneral >= pointsForNextGreatGeneralCounter["Great General"]) {
                pointsForNextGreatGeneralCounter["Great General"] = pointsForNextGreatGeneral
            } else pointsForNextGreatGeneral = pointsForNextGreatGeneralCounter["Great General"]
        }
    }

    private fun GameInfo.removeUnitsAndPromotions() {
        for (tile in tileMap.values) {
            for (unit in tile.getUnits().toList()) {
                if (!ruleset.units.containsKey(unit.name)) tile.removeUnit(unit)

                for (promotion in unit.promotions.promotions.toList())
                    if (!ruleset.unitPromotions.containsKey(promotion))
                        unit.promotions.promotions.remove(promotion)
            }
        }
    }

    private fun GameInfo.removeMissingGreatPersonPoints() {
        for (civ in civilizations) {
            // Don't remove the 'points to next' counters, since pools do not necessarily correspond to unit names
            for (key in civ.greatPeople.greatGeneralPointsCounter.keys.toList())
                if (!ruleset.units.containsKey(key))
                    civ.greatPeople.greatGeneralPointsCounter.remove(key)
            for (key in civ.greatPeople.greatPersonPointsCounter.keys.toList())
                if (!ruleset.units.containsKey(key))
                    civ.greatPeople.greatPersonPointsCounter.remove(key)
        }
    }

    private fun GameInfo.removeOldPillagedImprovements() {
        if (!ruleset.tileImprovements.containsKey(Constants.repair))
            for (tile in tileMap.values) {
                if (tile.roadIsPillaged) {
                    tile.removeRoad()
                }
                if (tile.improvementIsPillaged) {
                    tile.improvement = null
                    tile.improvementIsPillaged = false
                }
            }
    }

    private fun GameInfo.removeMissingLastSeenImprovements() {
        for (civ in civilizations)
            for ((vector,improvementName) in civ.lastSeenImprovement.toList())
                if (!ruleset.tileImprovements.containsKey(improvementName))
                    civ.lastSeenImprovement.remove(vector)
    }

    private fun GameInfo.handleMissingReferencesForEachCity() {
        for (city in civilizations.asSequence().flatMap { it.cities.asSequence() }) {

            for (building in city.cityConstructions.builtBuildings.toList()) {
                if (!ruleset.buildings.containsKey(building))
                    city.cityConstructions.builtBuildings.remove(building)
            }

            @Readonly
            fun isInvalidConstruction(construction: String) =
                !ruleset.buildings.containsKey(construction)
                    && !ruleset.units.containsKey(construction)
                    && !PerpetualConstruction.perpetualConstructionsMap.containsKey(construction)

            // Remove invalid buildings or units from the queue - don't just check buildings and units because it might be a special construction as well
            for (construction in city.cityConstructions.constructionQueue.toList()) {
                if (isInvalidConstruction(construction))
                    city.cityConstructions.constructionQueue.remove(construction)
            }
            // And from being in progress
            for (construction in city.cityConstructions.inProgressConstructions.keys.toList())
                if (isInvalidConstruction(construction))
                    city.cityConstructions.inProgressConstructions.remove(construction)
        }
    }

    private fun GameInfo.removeTechAndPolicies() {
        for (civInfo in civilizations) {
            for (tech in civInfo.tech.techsResearched.toList())
                if (!ruleset.technologies.containsKey(tech))
                    civInfo.tech.techsResearched.remove(tech)
            
            for (tech in civInfo.tech.techsToResearch.toList())
                if (!ruleset.technologies.containsKey(tech))
                    civInfo.tech.techsToResearch.remove(tech)
            
            for (policy in civInfo.policies.adoptedPolicies.toList())
                if (!ruleset.policies.containsKey(policy))
                    civInfo.policies.adoptedPolicies.remove(policy)
        }
    }

    /**
     * Replaces all occurrences of [oldBuildingName] in [cityConstructions] with [newBuildingName]
     * if the former is not contained in the ruleset.
     */
    private fun changeBuildingNameIfNotInRuleset(
        ruleSet: Ruleset,
        cityConstructions: CityConstructions,
        oldBuildingName: String,
        newBuildingName: String
    ) {
        if (ruleSet.buildings.containsKey(oldBuildingName))
            return
        // Replace in built buildings
        if (cityConstructions.isBuilt(oldBuildingName)) {
            cityConstructions.removeBuilding(oldBuildingName)
            cityConstructions.addBuilding(newBuildingName)
        }
        // Replace in construction queue
        if (!cityConstructions.isBuilt(newBuildingName) && !cityConstructions.constructionQueue.contains(newBuildingName))
            cityConstructions.constructionQueue = cityConstructions.constructionQueue
                .map { if (it == oldBuildingName) newBuildingName else it }
                .toMutableList()
        else
            cityConstructions.constructionQueue.remove(oldBuildingName)
        // Replace in in-progress constructions
        if (cityConstructions.inProgressConstructions.containsKey(oldBuildingName)) {
            if (!cityConstructions.isBuilt(newBuildingName) && !cityConstructions.inProgressConstructions.containsKey(newBuildingName))
                cityConstructions.inProgressConstructions[newBuildingName] = cityConstructions.inProgressConstructions[oldBuildingName]!!
            cityConstructions.inProgressConstructions.remove(oldBuildingName)
        }
    }

    /** Replace a changed tech name */
    private fun TechManager.replaceUpdatedTechName(oldTechName: String, newTechName: String) {
        if (oldTechName in techsResearched) {
            techsResearched.remove(oldTechName)
            techsResearched.add(newTechName)
        }
        val index = techsToResearch.indexOf(oldTechName)
        if (index >= 0) {
            techsToResearch[index] = newTechName
        }
        if (oldTechName in techsInProgress) {
            techsInProgress[newTechName] = researchOfTech(oldTechName)
            techsInProgress.remove(oldTechName)
        }
    }

    /** Replace a deprecated DiplomacyFlags instance */
    fun GameInfo.replaceDiplomacyFlag(old: DiplomacyFlags, new: DiplomacyFlags) {
        fun DiplomacyManager.replaceFlag() {
            if (hasFlag(old)) {
                val value = getFlag(old)
                removeFlag(old)
                setFlag(new, value)
            }
        }
        civilizations.flatMap { civ -> civ.diplomacy.values }.forEach { it.replaceFlag() }
    }

    /** Make sure all MapUnits have the starting promotions that they're supposed to. */
    fun GameInfo.guaranteeUnitPromotions() {
        for (tileInfo in tileMap.values) for (unit in tileInfo.getUnits())
            for (startingPromo in unit.baseUnit.promotions)
                unit.promotions.addPromotion(startingPromo, true)
    }

    /** Move max XP from barbarians to new home */
    @Suppress("DEPRECATION", "EmptyFunctionBlock")
    fun ModOptions.updateDeprecations() { }

    /** Convert from Fortify X to Fortify and save off X */
    fun GameInfo.convertFortify() {
        val reg = Regex("""^Fortify\s+(\d+)([\w\s]*)""")
        for (civInfo in civilizations) {
            for (unit in civInfo.units.getCivUnits()) {
                if (unit.action != null && reg.matches(unit.action!!)) {
                    val (turns, heal) = reg.find(unit.action!!)!!.destructured
                    unit.turnsFortified = turns.toInt()
                    unit.action = "Fortify$heal"
                }
            }
        }
    }

    fun GameInfo.migrateToTileHistory() {
        if (historyStartTurn >= 0) return
        for (tile in getCities().flatMap { it.getTiles() }) {
            tile.history.recordTakeOwnership(tile)
        }
        historyStartTurn = turns
    }

    fun GameInfo.ensureUnitIds() {
        if (lastUnitId == 0) lastUnitId = tileMap.values.asSequence()
            .flatMap { it.getUnits() }.maxOfOrNull { it.id }?.coerceAtLeast(0) ?: 0
        for (unit in tileMap.values.flatMap { it.getUnits() }) {
            if (unit.id == Constants.NO_ID) unit.id = ++lastUnitId
        }
    }
}
