package com.unciv.models.ruleset.validation

import com.unciv.Constants
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.models.stats.Stats

/**
 *  This implementation of [RulesetValidator] can rely on [ruleset] being complete.
 */
internal class BaseRulesetValidator(
    ruleset: Ruleset,
    tryFixUnknownUniques: Boolean
) : RulesetValidator(ruleset, tryFixUnknownUniques) {

    // for UnitTypes fallback, used only if a mod fails to provide
    // the UnitTypes entirely (ruleset.unitTypes.isEmpty() is true).
    private val vanillaRuleset by lazy { RulesetCache.getVanillaRuleset() }

    /** Collects known technology prerequisite paths: key is the technology name,
     *  value a Set of its prerequisites including indirect ones */
    private val prereqsHashMap = HashMap<String, HashSet<String>>()

    override fun addBeliefErrors(lines: RulesetErrorList) {
        super.addBeliefErrors(lines)

        for (belief in ruleset.beliefs.values) {
            if (belief.type == BeliefType.Any || belief.type == BeliefType.None)
                lines.add("${belief.name} type is ${belief.type}, which is not allowed!", sourceObject = belief)
            uniqueValidator.checkUniques(belief, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addBuildingErrors(lines: RulesetErrorList) {
        // No super.addBuildingErrors(lines): included in the loop below

        for (building in ruleset.buildings.values) {
            addBuildingErrorRulesetInvariant(building, lines)

            for (requiredTech: String in building.requiredTechs())
                if (!ruleset.technologies.containsKey(requiredTech))
                    lines.add("${building.name} requires tech $requiredTech which does not exist!", sourceObject = building)
            for (specialistName in building.specialistSlots.keys)
                if (!ruleset.specialists.containsKey(specialistName))
                    lines.add("${building.name} provides specialist $specialistName which does not exist!", sourceObject = building)
            for (resource in building.getResourceRequirementsPerTurn(StateForConditionals.IgnoreConditionals).keys)
                if (!ruleset.tileResources.containsKey(resource))
                    lines.add("${building.name} requires resource $resource which does not exist!", sourceObject = building)
            if (building.replaces != null && !ruleset.buildings.containsKey(building.replaces!!))
                lines.add("${building.name} replaces ${building.replaces} which does not exist!", sourceObject = building)
            if (building.requiredBuilding != null && !ruleset.buildings.containsKey(building.requiredBuilding!!))
                lines.add("${building.name} requires ${building.requiredBuilding} which does not exist!", sourceObject = building)

            checkUniqueToMisspelling(building, building.uniqueTo, lines)
            uniqueValidator.checkUniques(building, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addCityStateTypeErrors(lines: RulesetErrorList) {
        super.addCityStateTypeErrors(lines)

        for (cityStateType in ruleset.cityStateTypes.values) {
            for (unique in cityStateType.allyBonusUniqueMap.getAllUniques() + cityStateType.friendBonusUniqueMap.getAllUniques()) {
                val errors = uniqueValidator.checkUnique(
                    unique,
                    tryFixUnknownUniques,
                    null,
                    true
                )
                lines.addAll(errors)
            }
        }
    }

    override fun addDifficultyErrors(lines: RulesetErrorList) {
        super.addDifficultyErrors(lines)

        // A Difficulty is not a IHasUniques, so not suitable as sourceObject
        for (difficulty in ruleset.difficulties.values) {
            for (unitName in difficulty.aiCityStateBonusStartingUnits + difficulty.aiMajorCivBonusStartingUnits + difficulty.playerBonusStartingUnits)
                if (unitName != Constants.eraSpecificUnit && !ruleset.units.containsKey(unitName))
                    lines.add("Difficulty ${difficulty.name} contains starting unit $unitName which does not exist!", sourceObject = null)
        }
    }

    override fun addEraErrors(lines: RulesetErrorList) {
        super.addEraErrors(lines)

        if (ruleset.eras.isEmpty()) {
            lines.add("Eras file is empty! This will likely lead to crashes. Ask the mod maker to update this mod!", sourceObject = null)
        }

        val allDifficultiesStartingUnits = hashSetOf<String>()
        for (difficulty in ruleset.difficulties.values) {
            allDifficultiesStartingUnits.addAll(difficulty.aiCityStateBonusStartingUnits)
            allDifficultiesStartingUnits.addAll(difficulty.aiMajorCivBonusStartingUnits)
            allDifficultiesStartingUnits.addAll(difficulty.playerBonusStartingUnits)
        }

        for (era in ruleset.eras.values) {
            for (wonder in era.startingObsoleteWonders)
                if (wonder !in ruleset.buildings)
                    lines.add("Nonexistent wonder $wonder obsoleted when starting in ${era.name}!", sourceObject = era)
            for (building in era.settlerBuildings)
                if (building !in ruleset.buildings)
                    lines.add("Nonexistent building $building built by settlers when starting in ${era.name}", sourceObject = era)
            // todo the whole 'starting unit' thing needs to be redone, there's no reason we can't have a single list containing all the starting units.
            if (era.startingSettlerUnit !in ruleset.units
                && ruleset.units.values.none { it.isCityFounder() }
            )
                lines.add("Nonexistent unit ${era.startingSettlerUnit} marked as starting unit when starting in ${era.name}", sourceObject = era)
            if (era.startingWorkerCount != 0 && era.startingWorkerUnit !in ruleset.units
                && ruleset.units.values.none { it.hasUnique(UniqueType.BuildImprovements) }
            )
                lines.add("Nonexistent unit ${era.startingWorkerUnit} marked as starting unit when starting in ${era.name}", sourceObject = era)

            val grantsStartingMilitaryUnit = era.startingMilitaryUnitCount != 0
                || allDifficultiesStartingUnits.contains(Constants.eraSpecificUnit)
            if (grantsStartingMilitaryUnit && era.startingMilitaryUnit !in ruleset.units)
                lines.add("Nonexistent unit ${era.startingMilitaryUnit} marked as starting unit when starting in ${era.name}", sourceObject = era)
            if (era.researchAgreementCost < 0 || era.startingSettlerCount < 0 || era.startingWorkerCount < 0 || era.startingMilitaryUnitCount < 0 || era.startingGold < 0 || era.startingCulture < 0)
                lines.add("Unexpected negative number found while parsing era ${era.name}", sourceObject = era)
            if (era.settlerPopulation <= 0)
                lines.add("Population in cities from settlers must be strictly positive! Found value ${era.settlerPopulation} for era ${era.name}", sourceObject = era)

            if (era.allyBonus.isNotEmpty())
                lines.add(
                    "Era ${era.name} contains city-state bonuses. City-state bonuses are now defined in CityStateType.json",
                    RulesetErrorSeverity.WarningOptionsOnly, era
                )
            if (era.friendBonus.isNotEmpty())
                lines.add(
                    "Era ${era.name} contains city-state bonuses. City-state bonuses are now defined in CityStateType.json",
                    RulesetErrorSeverity.WarningOptionsOnly, era
                )

            uniqueValidator.checkUniques(era, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addEventErrors(lines: RulesetErrorList) {
        super.addEventErrors(lines)

        // An Event is not a IHasUniques, so not suitable as sourceObject
        for (event in ruleset.events.values) {
            for (choice in event.choices) {
                uniqueValidator.checkUniques(choice, lines, true, tryFixUnknownUniques)
            }
            uniqueValidator.checkUniques(event, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addImprovementErrors(lines: RulesetErrorList) {
        super.addImprovementErrors(lines)

        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.techRequired != null && !ruleset.technologies.containsKey(improvement.techRequired!!))
                lines.add("${improvement.name} requires tech ${improvement.techRequired} which does not exist!", sourceObject = improvement)
            if (improvement.replaces != null && !ruleset.tileImprovements.containsKey(improvement.replaces))
                lines.add("${improvement.name} replaces ${improvement.replaces} which does not exist!", sourceObject = improvement)
            if (improvement.replaces != null && improvement.uniqueTo == null)
                lines.add("${improvement.name} should replace ${improvement.replaces} but does not have uniqueTo assigned!")
            checkUniqueToMisspelling(improvement, improvement.uniqueTo, lines)
            for (terrain in improvement.terrainsCanBeBuiltOn)
                if (!ruleset.terrains.containsKey(terrain) && terrain != "Land" && terrain != "Water")
                    lines.add("${improvement.name} can be built on terrain $terrain which does not exist!", sourceObject = improvement)
            if (improvement.terrainsCanBeBuiltOn.isEmpty()
                && !improvement.hasUnique(UniqueType.CanOnlyImproveResource)
                && !improvement.hasUnique(UniqueType.Unbuildable)
                && !improvement.name.startsWith(Constants.remove)
                && improvement.name !in RoadStatus.entries.map { it.removeAction }
                && improvement.name != Constants.cancelImprovementOrder
            ) {
                lines.add(
                    "${improvement.name} has an empty `terrainsCanBeBuiltOn`, isn't allowed to only improve resources. As such it isn't buildable! Either give this the unique \"Unbuildable\", \"Can only be built to improve a resource\", or add \"Land\", \"Water\" or any other value to `terrainsCanBeBuiltOn`.",
                    RulesetErrorSeverity.Warning, improvement
                )
            }
            for (unique in improvement.uniqueObjects
                .filter { it.type == UniqueType.PillageYieldRandom || it.type == UniqueType.PillageYieldFixed }) {
                if (!Stats.isStats(unique.params[0])) continue
                val params = Stats.parse(unique.params[0])
                if (params.values.any { it < 0 }) lines.add(
                    "${improvement.name} cannot have a negative value for a pillage yield!",
                    RulesetErrorSeverity.Error, improvement
                )
            }

            val hasPillageUnique = improvement.hasUnique(UniqueType.PillageYieldRandom, StateForConditionals.IgnoreConditionals)
                || improvement.hasUnique(UniqueType.PillageYieldFixed, StateForConditionals.IgnoreConditionals)
            if (hasPillageUnique && improvement.hasUnique(UniqueType.Unpillagable, StateForConditionals.IgnoreConditionals)) {
                lines.add(
                    "${improvement.name} has both an `Unpillagable` unique type and a `PillageYieldRandom` or `PillageYieldFixed` unique type!",
                    RulesetErrorSeverity.Warning, improvement
                )
            }
            uniqueValidator.checkUniques(improvement, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addModOptionsErrors(lines: RulesetErrorList) {
        super.addModOptionsErrors(lines)

        for (unique in ruleset.modOptions.getMatchingUniques(UniqueType.ModRequires)) {
            lines.add("Mod option '${unique.text}' is invalid for a base ruleset.", sourceObject = null)
        }
    }

    override fun addNationErrors(lines: RulesetErrorList) {
        // No super.addNationErrors(lines), included in loop below
        for (nation in ruleset.nations.values) {
            addNationErrorRulesetInvariant(nation, lines)

            uniqueValidator.checkUniques(nation, lines, true, tryFixUnknownUniques)

            if (nation.preferredVictoryType != Constants.neutralVictoryType && nation.preferredVictoryType !in ruleset.victories)
                lines.add("${nation.name}'s preferredVictoryType is ${nation.preferredVictoryType} which does not exist!", sourceObject = nation)
            if (nation.cityStateType != null && nation.cityStateType !in ruleset.cityStateTypes)
                lines.add("${nation.name} is of city-state type ${nation.cityStateType} which does not exist!", sourceObject = nation)
            if (nation.favoredReligion != null && nation.favoredReligion !in ruleset.religions)
                lines.add("${nation.name} has ${nation.favoredReligion} as their favored religion, which does not exist!", sourceObject = nation)
        }
    }

    override fun addPersonalityErrors(lines: RulesetErrorList) {
        super.addPersonalityErrors(lines)

        for (personality in ruleset.personalities.values) {
            if (personality.preferredVictoryType != Constants.neutralVictoryType
                && personality.preferredVictoryType !in ruleset.victories) {
                lines.add("Preferred victory type ${personality.preferredVictoryType} does not exist in ruleset",
                    RulesetErrorSeverity.Warning, sourceObject = personality,)
            }
        }
    }

    override fun addPolicyErrors(lines: RulesetErrorList) {
        super.addPolicyErrors(lines)

        for (policy in ruleset.policies.values) {
            for (prereq in policy.requires ?: emptyList())
                if (!ruleset.policies.containsKey(prereq))
                    lines.add("${policy.name} requires policy $prereq which does not exist!", sourceObject = policy)

            uniqueValidator.checkUniques(policy, lines, true, tryFixUnknownUniques)
        }

        for (branch in ruleset.policyBranches.values) {
            if (branch.era !in ruleset.eras)
                lines.add("${branch.name} requires era ${branch.era} which does not exist!", sourceObject = branch)

            val policyLocations = HashMap<String, Policy>()
            for (policy in branch.policies) {
                val policyLocation = "${policy.row}/${policy.column}"
                val existingPolicyInLocation = policyLocations[policyLocation]

                if (existingPolicyInLocation == null) policyLocations[policyLocation] = policy
                else lines.add("Policies ${policy.name} and ${existingPolicyInLocation.name} in branch ${branch.name}" +
                    " are both located at column ${policy.column} row ${policy.row}!", sourceObject = policy)
            }
        }


        for (policy in ruleset.policyBranches.values.flatMap { it.policies + it })
            if (policy != ruleset.policies[policy.name])
                lines.add("More than one policy with the name ${policy.name} exists!", sourceObject = policy)

    }

    override fun addPromotionErrors(lines: RulesetErrorList) {
        // No super.addPromotionErrors(lines): included below
        //TODO except the contrast check
        for (promotion in ruleset.unitPromotions.values) {
            addPromotionErrorRulesetInvariant(promotion, lines)

            // These are warning as of 3.17.5 to not break existing mods and give them time to correct, should be upgraded to error in the future
            for (prereq in promotion.prerequisites)
                if (!ruleset.unitPromotions.containsKey(prereq))
                    lines.add(
                        "${promotion.name} requires promotion $prereq which does not exist!",
                        RulesetErrorSeverity.Warning, promotion
                    )
            for (unitType in promotion.unitTypes) checkUnitType(unitType) {
                lines.add(
                    "${promotion.name} references unit type $unitType, which does not exist!",
                    RulesetErrorSeverity.Warning, promotion
                )
            }
            uniqueValidator.checkUniques(promotion, lines, true, tryFixUnknownUniques)
        }
        checkPromotionCircularReferences(lines)
    }

    private fun checkPromotionCircularReferences(lines: RulesetErrorList) {
        fun recursiveCheck(history: HashSet<Promotion>, promotion: Promotion, level: Int) {
            if (promotion in history) {
                lines.add("Circular Reference in Promotions: ${history.joinToString("→") { it.name }}→${promotion.name}",
                    RulesetErrorSeverity.Warning, promotion)
                return
            }
            if (level > 99) return
            history.add(promotion)
            for (prerequisiteName in promotion.prerequisites) {
                val prerequisite = ruleset.unitPromotions[prerequisiteName] ?: continue
                // Performance - if there's only one prerequisite, we can send this linked set as-is, since no one else will be using it
                val linkedSetToPass =
                    if (promotion.prerequisites.size == 1) history
                    else history.toCollection(hashSetOf())
                recursiveCheck(linkedSetToPass, prerequisite, level + 1)
            }
        }
        for (promotion in ruleset.unitPromotions.values) {
            if (promotion.prerequisites.isEmpty()) continue
            recursiveCheck(hashSetOf(), promotion, 0)
        }
    }

    override fun addResourceErrors(lines: RulesetErrorList) {
        // No super.addResourceErrors(lines), included below

        for (resource in ruleset.tileResources.values) {
            if (resource.revealedBy != null && !ruleset.technologies.containsKey(resource.revealedBy!!))
                lines.add("${resource.name} revealed by tech ${resource.revealedBy} which does not exist!", sourceObject = resource)
            if (resource.improvement != null && !ruleset.tileImprovements.containsKey(resource.improvement!!))
                lines.add("${resource.name} improved by improvement ${resource.improvement} which does not exist!", sourceObject = resource)
            for (improvement in resource.improvedBy)
                if (!ruleset.tileImprovements.containsKey(improvement))
                    lines.add("${resource.name} improved by improvement $improvement which does not exist!", sourceObject = resource)
            for (terrain in resource.terrainsCanBeFoundOn)
                if (!ruleset.terrains.containsKey(terrain))
                    lines.add("${resource.name} can be found on terrain $terrain which does not exist!", sourceObject = resource)
            uniqueValidator.checkUniques(resource, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addRuinsErrors(lines: RulesetErrorList) {
        super.addRuinsErrors(lines)

        for (reward in ruleset.ruinRewards.values) {
            @Suppress("KotlinConstantConditions") // data is read from json, so any assumptions may be wrong
            if (reward.weight < 0) lines.add("${reward.name} has a negative weight, which is not allowed!", sourceObject = reward)
            for (difficulty in reward.excludedDifficulties)
                if (!ruleset.difficulties.containsKey(difficulty))
                    lines.add("${reward.name} references difficulty ${difficulty}, which does not exist!", sourceObject = reward)
            uniqueValidator.checkUniques(reward, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addSpecialistErrors(lines: RulesetErrorList) {
        super.addSpecialistErrors(lines)

        // Specialist is not a IHasUniques and unsuitable as sourceObject
        for (specialist in ruleset.specialists.values) {
            for (gpp in specialist.greatPersonPoints)
                if (gpp.key !in ruleset.units)
                    lines.add(
                        "Specialist ${specialist.name} has greatPersonPoints for ${gpp.key}, which is not a unit in the ruleset!",
                        RulesetErrorSeverity.Warning, sourceObject = null
                    )
        }
    }

    override fun addSpeedErrors(lines: RulesetErrorList) {
        super.addSpeedErrors(lines)

        for (speed in ruleset.speeds.values) {
            if (speed.modifier < 0f)
                lines.add("Negative speed modifier for game speed ${speed.name}", sourceObject = speed)
            if (speed.yearsPerTurn.isEmpty())
                lines.add("Empty turn increment list for game speed ${speed.name}", sourceObject = speed)
        }
    }

    override fun addTechErrors(lines: RulesetErrorList) {
        // No super.addTechErrors(lines) or we would duplicate the checkUniques
        //TODO missing `row < 1` check -> unify

        for (tech in ruleset.technologies.values) {
            for (prereq in tech.prerequisites) {
                if (!ruleset.technologies.containsKey(prereq))
                    lines.add("${tech.name} requires tech $prereq which does not exist!", sourceObject = tech)

                if (tech.prerequisites.any { it != prereq && getPrereqTree(it).contains(prereq) }) {
                    lines.add(
                        "No need to add $prereq as a prerequisite of ${tech.name} - it is already implicit from the other prerequisites!",
                        RulesetErrorSeverity.Warning, tech
                    )
                }

                if (getPrereqTree(prereq).contains(tech.name))
                    lines.add("Techs ${tech.name} and $prereq require each other!", sourceObject = tech)
            }
            if (tech.era() !in ruleset.eras)
                lines.add("Unknown era ${tech.era()} referenced in column of tech ${tech.name}", sourceObject = tech)
            uniqueValidator.checkUniques(tech, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addTerrainErrors(lines: RulesetErrorList) {
        super.addTerrainErrors(lines)

        if (ruleset.terrains.values.none { it.type == TerrainType.Land && !it.impassable && !it.hasUnique(
                UniqueType.NoNaturalGeneration) })
            lines.add("No passable land terrains exist!", sourceObject = null)

        for (terrain in ruleset.terrains.values) {
            for (baseTerrainName in terrain.occursOn) {
                val baseTerrain = ruleset.terrains[baseTerrainName]
                if (baseTerrain == null)
                    lines.add("${terrain.name} occurs on terrain $baseTerrainName which does not exist!", sourceObject = terrain)
                else if (baseTerrain.type == TerrainType.NaturalWonder)
                    lines.add("${terrain.name} occurs on natural wonder $baseTerrainName: Unsupported.", RulesetErrorSeverity.WarningOptionsOnly, terrain)
            }
            if (terrain.type == TerrainType.NaturalWonder && terrain.turnsInto != null) {
                val baseTerrain = ruleset.terrains[terrain.turnsInto]
                if (baseTerrain == null)
                    lines.add("${terrain.name} turns into terrain ${terrain.turnsInto} which does not exist!", sourceObject = terrain)
                else if (!baseTerrain.type.isBaseTerrain)
                // See https://github.com/hackedpassword/Z2/blob/main/HybridTileTech.md for a clever exploit
                    lines.add("${terrain.name} turns into terrain ${terrain.turnsInto} which is not a base terrain!", RulesetErrorSeverity.Warning, terrain)
            }
            uniqueValidator.checkUniques(terrain, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addUnitErrors(lines: RulesetErrorList) {
        // No super.addUnitErrors(lines), included below

        if (ruleset.units.values.none { it.isCityFounder() })
            lines.add("No city-founding units in ruleset!", sourceObject = null)

        for (unit in ruleset.units.values) {
            checkUnitRulesetInvariant(unit, lines)
            checkUnit(unit, lines)
            uniqueValidator.checkUniques(unit, lines, true, tryFixUnknownUniques)
            checkUniqueToMisspelling(unit, unit.uniqueTo, lines)
        }
    }

    /** Collects all RulesetSpecific checks for a BaseUnit */
    private fun checkUnit(unit: BaseUnit, lines: RulesetErrorList) {
        for (requiredTech: String in unit.requiredTechs())
            if (!ruleset.technologies.containsKey(requiredTech))
                lines.add("${unit.name} requires tech $requiredTech which does not exist!", sourceObject = unit)
        for (obsoleteTech: String in unit.techsAtWhichNoLongerAvailable())
            if (!ruleset.technologies.containsKey(obsoleteTech))
                lines.add("${unit.name} obsoletes at tech $obsoleteTech which does not exist!", sourceObject = unit)
        for (upgradesTo in unit.getUpgradeUnits(StateForConditionals.IgnoreConditionals))
            if (!ruleset.units.containsKey(upgradesTo))
                lines.add("${unit.name} upgrades to unit $upgradesTo which does not exist!", sourceObject = unit)

        // Check that we don't obsolete ourselves before we can upgrade
        for (obsoleteTech: String in unit.techsAtWhichAutoUpgradeInProduction())
            for (upgradesTo in unit.getUpgradeUnits(StateForConditionals.IgnoreConditionals)) {
                if (!ruleset.units.containsKey(upgradesTo)) continue
                if (!ruleset.technologies.containsKey(obsoleteTech)) continue
                val upgradedUnit = ruleset.units[upgradesTo]!!
                for (requiredTech: String in upgradedUnit.requiredTechs())
                    if (requiredTech != obsoleteTech && !getPrereqTree(obsoleteTech).contains(requiredTech))
                        lines.add(
                            "${unit.name} is supposed to automatically upgrade at tech ${obsoleteTech}," +
                                " and therefore $requiredTech for its upgrade ${upgradedUnit.name} may not yet be researched!",
                            RulesetErrorSeverity.Warning, unit
                        )
            }

        for (resource in unit.getResourceRequirementsPerTurn(StateForConditionals.IgnoreConditionals).keys)
            if (!ruleset.tileResources.containsKey(resource))
                lines.add("${unit.name} requires resource $resource which does not exist!", sourceObject = unit)
        if (unit.replaces != null && !ruleset.units.containsKey(unit.replaces!!))
            lines.add("${unit.name} replaces ${unit.replaces} which does not exist!", sourceObject = unit)
        for (promotion in unit.promotions)
            if (!ruleset.unitPromotions.containsKey(promotion))
                lines.add("${unit.name} contains promotion $promotion which does not exist!", sourceObject = unit)
        checkUnitType(unit.unitType) {
            lines.add("${unit.name} is of type ${unit.unitType}, which does not exist!", sourceObject = unit)
        }

        // We should ignore conditionals here - there are condition implementations on this out there that require a game state (and will test false without)
        for (unique in unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly, StateForConditionals.IgnoreConditionals)) {
            val improvementName = unique.params[0]
            if (ruleset.tileImprovements[improvementName] == null) continue // this will be caught in the uniqueValidator.checkUniques
            if ((ruleset.tileImprovements[improvementName] as Stats).isEmpty() &&
                unit.isCivilian() &&
                !unit.isGreatPersonOfType("War")) {
                lines.add("${unit.name} can place improvement $improvementName which has no stats, preventing unit automation!",
                    RulesetErrorSeverity.WarningOptionsOnly, unit)
            }
        }
    }

    override fun addUnitTypeErrors(lines: RulesetErrorList) {
        super.addUnitTypeErrors(lines)

        val unitMovementTypes = UnitMovementType.entries.map { it.name }.toSet()
        for (unitType in ruleset.unitTypes.values) {
            if (unitType.movementType !in unitMovementTypes)
                lines.add("Unit type ${unitType.name} has an invalid movement type ${unitType.movementType}", sourceObject = unitType)
            uniqueValidator.checkUniques(unitType, lines, true, tryFixUnknownUniques)
        }
    }

    override fun addVictoryTypeErrors(lines: RulesetErrorList) {
        super.addVictoryTypeErrors(lines)

        // Victory and Milestone aren't IHasUniques and are unsuitable as sourceObject
        for (victoryType in ruleset.victories.values) {
            for (requiredUnit in victoryType.requiredSpaceshipParts)
                if (!ruleset.units.contains(requiredUnit))
                    lines.add(
                        "Victory type ${victoryType.name} requires adding the non-existant unit $requiredUnit to the capital to win!",
                        RulesetErrorSeverity.Warning, sourceObject = null
                    )

            for (milestone in victoryType.milestoneObjects) {
                if (milestone.type == null)
                    lines.add(
                        "Victory type ${victoryType.name} has milestone \"${milestone.uniqueDescription}\" that is of an unknown type!",
                        RulesetErrorSeverity.Error, sourceObject = null
                    )
                if (milestone.type in listOf(MilestoneType.BuiltBuilding, MilestoneType.BuildingBuiltGlobally)
                    && milestone.params[0] !in ruleset.buildings)
                    lines.add(
                        "Victory type ${victoryType.name} has milestone \"${milestone.uniqueDescription}\" that references an unknown building ${milestone.params[0]}!",
                        RulesetErrorSeverity.Error,
                    )
            }

            for (victory in ruleset.victories.values)
                if (victory.name != victoryType.name && victory.milestones == victoryType.milestones)
                    lines.add(
                        "Victory types ${victoryType.name} and ${victory.name} have the same requirements!",
                        RulesetErrorSeverity.Warning, sourceObject = null
                    )
        }
    }

    private fun checkUniqueToMisspelling(obj: IHasUniques, uniqueTo: String?, lines: RulesetErrorList){
        if (uniqueTo == null || uniqueTo in ruleset.nations) return

        val civUniques = ruleset.nations.values.flatMap { it.uniques }.toSet()
        if (uniqueTo in civUniques) return

        val possibleMisspellings = getPossibleMisspellings(uniqueTo,
            civUniques.toList() + ruleset.nations.keys)
        if (possibleMisspellings.isEmpty()) return
        lines.add(
            "${obj.name} has uniqueTo \"$uniqueTo\" does not match any nation/unique - may be a misspelling of " +
                possibleMisspellings.joinToString(" OR ") { "\"$it\"" }, sourceObject = obj,
            errorSeverityToReport = RulesetErrorSeverity.OK
        )
    }

    /** Checks validity of one UnitType by name supporting fallback if a mod has no UnitTypes at all,
     *  and calls [reportError] if it is bad.
     */
    private fun checkUnitType(type: String, reportError: ()->Unit) {
        if (ruleset.unitTypes.containsKey(type)) return
        if (ruleset.unitTypes.isEmpty() && vanillaRuleset.unitTypes.containsKey(type)) return
        reportError()
    }

    /** @return The Set of direct and indirect prerequisites of a Technology by names */
    private fun getPrereqTree(technologyName: String): Set<String> {
        if (prereqsHashMap.containsKey(technologyName)) return prereqsHashMap[technologyName]!!
        val technology = ruleset.technologies[technologyName]
            ?: return emptySet()
        val techHashSet = HashSet<String>()
        techHashSet += technology.prerequisites
        prereqsHashMap[technologyName] = techHashSet
        for (prerequisite in technology.prerequisites)
            techHashSet += getPrereqTree(prerequisite)
        return techHashSet
    }

}
