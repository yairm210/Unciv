package com.unciv.models.ruleset.validation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.unciv.Constants
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.getContrastRatio
import com.unciv.models.ruleset.nation.getRelativeLuminance
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.stats.Stats
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig

class RulesetValidator(val ruleset: Ruleset) {

    val uniqueValidator = UniqueValidator(ruleset)

    fun getErrorList(tryFixUnknownUniques: Boolean = false): RulesetErrorList {
        val lines = RulesetErrorList()

        /********************** Ruleset Invariant Part **********************/
        // Checks for ALL MODS - only those that can succeed without loading a base ruleset
        // When not checking the entire ruleset, we can only really detect ruleset-invariant errors in uniques

        val rulesetInvariant = UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        val rulesetSpecific = UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific

        uniqueValidator.checkUniques(ruleset.globalUniques, lines, rulesetInvariant, tryFixUnknownUniques)
        addUnitErrorsRulesetInvariant(lines, rulesetInvariant, tryFixUnknownUniques)
        addTechErrorsRulesetInvariant(lines, rulesetInvariant, tryFixUnknownUniques)
        addTechColumnErrorsRulesetInvariant(lines)
        addBuildingErrorsRulesetInvariant(lines, rulesetInvariant, tryFixUnknownUniques)
        addNationErrorsRulesetInvariant(lines, rulesetInvariant, tryFixUnknownUniques)
        addPromotionErrorsRulesetInvariant(lines, rulesetInvariant, tryFixUnknownUniques)
        addResourceErrorsRulesetInvariant(lines, rulesetInvariant, tryFixUnknownUniques)

        /********************** Tileset tests **********************/
        // e.g. json configs complete and parseable
        // Check for mod or Civ_V_GnK to avoid running the same test twice (~200ms for the builtin assets)
        if (ruleset.folderLocation != null || ruleset.name == BaseRuleset.Civ_V_GnK.fullName) {
            checkTilesetSanity(lines)
        }

        // Quit here when no base ruleset is loaded - references cannot be checked
        if (!ruleset.modOptions.isBaseRuleset) return lines

        /********************** Ruleset Specific Part **********************/

        uniqueValidator.checkUniques(ruleset.globalUniques, lines, rulesetSpecific, tryFixUnknownUniques)

        addUnitErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addBuildingErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addSpecialistErrors(lines)
        addResourceErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addImprovementErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addTerrainErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addTechErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addEraErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addSpeedErrors(lines)
        addBeliefErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addNationErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addPolicyErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addRuinsErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addPromotionErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addUnitTypeErrors(lines, rulesetSpecific, tryFixUnknownUniques)
        addVictoryTypeErrors(lines)
        addDifficutlyErrors(lines)
        addCityStateTypeErrors(tryFixUnknownUniques, rulesetSpecific, lines)

        return lines
    }

    private fun addCityStateTypeErrors(
        tryFixUnknownUniques: Boolean,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        lines: RulesetErrorList
    ) {
        for (cityStateType in ruleset.cityStateTypes.values) {
            for (unique in cityStateType.allyBonusUniqueMap.getAllUniques() + cityStateType.friendBonusUniqueMap.getAllUniques()) {
                val errors = uniqueValidator.checkUnique(
                    unique,
                    tryFixUnknownUniques,
                    cityStateType,
                    rulesetSpecific
                )
                lines.addAll(errors)
            }
        }
    }

    private fun addDifficutlyErrors(lines: RulesetErrorList) {
        for (difficulty in ruleset.difficulties.values) {
            for (unitName in difficulty.aiCityStateBonusStartingUnits + difficulty.aiMajorCivBonusStartingUnits + difficulty.playerBonusStartingUnits)
                if (unitName != Constants.eraSpecificUnit && !ruleset.units.containsKey(unitName))
                    lines += "Difficulty ${difficulty.name} contains starting unit $unitName which does not exist!"
        }
    }

    private fun addVictoryTypeErrors(lines: RulesetErrorList) {
        for (victoryType in ruleset.victories.values) {
            for (requiredUnit in victoryType.requiredSpaceshipParts)
                if (!ruleset.units.contains(requiredUnit))
                    lines.add(
                        "Victory type ${victoryType.name} requires adding the non-existant unit $requiredUnit to the capital to win!",
                        RulesetErrorSeverity.Warning
                    )
            for (milestone in victoryType.milestoneObjects)
                if (milestone.type == null)
                    lines.add(
                        "Victory type ${victoryType.name} has milestone ${milestone.uniqueDescription} that is of an unknown type!",
                        RulesetErrorSeverity.Error
                    )
            for (victory in ruleset.victories.values)
                if (victory.name != victoryType.name && victory.milestones == victoryType.milestones)
                    lines.add(
                        "Victory types ${victoryType.name} and ${victory.name} have the same requirements!",
                        RulesetErrorSeverity.Warning
                    )
        }
    }

    private fun addUnitTypeErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (unitType in ruleset.unitTypes.values) {
            uniqueValidator.checkUniques(unitType, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addPromotionErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (promotion in ruleset.unitPromotions.values) {
            // These are warning as of 3.17.5 to not break existing mods and give them time to correct, should be upgraded to error in the future
            for (prereq in promotion.prerequisites)
                if (!ruleset.unitPromotions.containsKey(prereq))
                    lines.add(
                        "${promotion.name} requires promotion $prereq which does not exist!",
                        RulesetErrorSeverity.Warning
                    )
            for (unitType in promotion.unitTypes) checkUnitType(unitType) {
                lines.add(
                    "${promotion.name} references unit type $unitType, which does not exist!",
                    RulesetErrorSeverity.Warning
                )
            }
            uniqueValidator.checkUniques(promotion, lines, rulesetSpecific, tryFixUnknownUniques)
        }
        checkPromotionCircularReferences(lines)
    }

    private fun addRuinsErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (reward in ruleset.ruinRewards.values) {
            for (difficulty in reward.excludedDifficulties)
                if (!ruleset.difficulties.containsKey(difficulty))
                    lines += "${reward.name} references difficulty ${difficulty}, which does not exist!"
            uniqueValidator.checkUniques(reward, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addPolicyErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (policy in ruleset.policies.values) {
            if (policy.requires != null)
                for (prereq in policy.requires!!)
                    if (!ruleset.policies.containsKey(prereq))
                        lines += "${policy.name} requires policy $prereq which does not exist!"
            uniqueValidator.checkUniques(policy, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (branch in ruleset.policyBranches.values)
            if (branch.era !in ruleset.eras)
                lines += "${branch.name} requires era ${branch.era} which does not exist!"


        for (policy in ruleset.policyBranches.values.flatMap { it.policies + it })
            if (policy != ruleset.policies[policy.name])
                lines += "More than one policy with the name ${policy.name} exists!"

    }

    private fun addNationErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (nation in ruleset.nations.values) {
            uniqueValidator.checkUniques(nation, lines, rulesetSpecific, tryFixUnknownUniques)

            if (nation.cityStateType != null && nation.cityStateType !in ruleset.cityStateTypes)
                lines += "${nation.name} is of city-state type ${nation.cityStateType} which does not exist!"
            if (nation.favoredReligion != null && nation.favoredReligion !in ruleset.religions)
                lines += "${nation.name} has ${nation.favoredReligion} as their favored religion, which does not exist!"
        }
    }

    private fun addBeliefErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (belief in ruleset.beliefs.values) {
            uniqueValidator.checkUniques(belief, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addSpeedErrors(lines: RulesetErrorList) {
        for (speed in ruleset.speeds.values) {
            if (speed.modifier < 0f)
                lines += "Negative speed modifier for game speed ${speed.name}"
            if (speed.yearsPerTurn.isEmpty())
                lines += "Empty turn increment list for game speed ${speed.name}"
        }
    }

    private fun addEraErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        if (ruleset.eras.isEmpty()) {
            lines += "Eras file is empty! This will likely lead to crashes. Ask the mod maker to update this mod!"
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
                    lines += "Nonexistent wonder $wonder obsoleted when starting in ${era.name}!"
            for (building in era.settlerBuildings)
                if (building !in ruleset.buildings)
                    lines += "Nonexistent building $building built by settlers when starting in ${era.name}"
            // todo the whole 'starting unit' thing needs to be redone, there's no reason we can't have a single list containing all the starting units.
            if (era.startingSettlerUnit !in ruleset.units
                && ruleset.units.values.none { it.isCityFounder() }
            )
                lines += "Nonexistent unit ${era.startingSettlerUnit} marked as starting unit when starting in ${era.name}"
            if (era.startingWorkerCount != 0 && era.startingWorkerUnit !in ruleset.units
                && ruleset.units.values.none { it.hasUnique(UniqueType.BuildImprovements) }
            )
                lines += "Nonexistent unit ${era.startingWorkerUnit} marked as starting unit when starting in ${era.name}"

            if ((era.startingMilitaryUnitCount != 0 || allDifficultiesStartingUnits.contains(
                    Constants.eraSpecificUnit
                )) && era.startingMilitaryUnit !in ruleset.units
            )
                lines += "Nonexistent unit ${era.startingMilitaryUnit} marked as starting unit when starting in ${era.name}"
            if (era.researchAgreementCost < 0 || era.startingSettlerCount < 0 || era.startingWorkerCount < 0 || era.startingMilitaryUnitCount < 0 || era.startingGold < 0 || era.startingCulture < 0)
                lines += "Unexpected negative number found while parsing era ${era.name}"
            if (era.settlerPopulation <= 0)
                lines += "Population in cities from settlers must be strictly positive! Found value ${era.settlerPopulation} for era ${era.name}"

            if (era.allyBonus.isNotEmpty())
                lines.add(
                    "Era ${era.name} contains city-state bonuses. City-state bonuses are now defined in CityStateType.json",
                    RulesetErrorSeverity.WarningOptionsOnly
                )
            if (era.friendBonus.isNotEmpty())
                lines.add(
                    "Era ${era.name} contains city-state bonuses. City-state bonuses are now defined in CityStateType.json",
                    RulesetErrorSeverity.WarningOptionsOnly
                )

            uniqueValidator.checkUniques(era, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addTechErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (tech in ruleset.technologies.values) {
            for (prereq in tech.prerequisites) {
                if (!ruleset.technologies.containsKey(prereq))
                    lines += "${tech.name} requires tech $prereq which does not exist!"


                if (tech.prerequisites.asSequence().filterNot { it == prereq }
                        .any { getPrereqTree(it).contains(prereq) }) {
                    lines.add(
                        "No need to add $prereq as a prerequisite of ${tech.name} - it is already implicit from the other prerequisites!",
                        RulesetErrorSeverity.Warning
                    )
                }

                if (getPrereqTree(prereq).contains(tech.name))
                    lines += "Techs ${tech.name} and $prereq require each other!"
            }
            if (tech.era() !in ruleset.eras)
                lines += "Unknown era ${tech.era()} referenced in column of tech ${tech.name}"
            uniqueValidator.checkUniques(tech, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addTerrainErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        if (ruleset.terrains.values.none { it.type == TerrainType.Land && !it.impassable })
            lines += "No passable land terrains exist!"
        for (terrain in ruleset.terrains.values) {
            for (baseTerrain in terrain.occursOn)
                if (!ruleset.terrains.containsKey(baseTerrain))
                    lines += "${terrain.name} occurs on terrain $baseTerrain which does not exist!"
            uniqueValidator.checkUniques(terrain, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addImprovementErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.techRequired != null && !ruleset.technologies.containsKey(improvement.techRequired!!))
                lines += "${improvement.name} requires tech ${improvement.techRequired} which does not exist!"
            for (terrain in improvement.terrainsCanBeBuiltOn)
                if (!ruleset.terrains.containsKey(terrain) && terrain != "Land" && terrain != "Water")
                    lines += "${improvement.name} can be built on terrain $terrain which does not exist!"
            if (improvement.terrainsCanBeBuiltOn.isEmpty()
                && !improvement.hasUnique(UniqueType.CanOnlyImproveResource)
                && !improvement.hasUnique(UniqueType.Unbuildable)
                && !improvement.name.startsWith(Constants.remove)
                && improvement.name !in RoadStatus.values().map { it.removeAction }
                && improvement.name != Constants.cancelImprovementOrder
            ) {
                lines.add(
                    "${improvement.name} has an empty `terrainsCanBeBuiltOn`, isn't allowed to only improve resources and isn't unbuildable! Support for this will soon end. Either give this the unique \"Unbuildable\", \"Can only be built to improve a resource\" or add \"Land\", \"Water\" or any other value to `terrainsCanBeBuiltOn`.",
                    RulesetErrorSeverity.Warning
                )
            }
            for (unique in improvement.uniqueObjects) {
                if (unique.type == UniqueType.PillageYieldRandom || unique.type == UniqueType.PillageYieldFixed) {
                    if (!Stats.isStats(unique.params[0])) continue
                    val params = Stats.parse(unique.params[0])
                    if (params.values.any { it < 0 }) lines.add(
                        "${improvement.name} cannot have a negative value for a pillage yield!",
                        RulesetErrorSeverity.Error
                    )
                }
            }
            if ((improvement.hasUnique(
                    UniqueType.PillageYieldRandom,
                    StateForConditionals.IgnoreConditionals
                )
                    || improvement.hasUnique(
                    UniqueType.PillageYieldFixed,
                    StateForConditionals.IgnoreConditionals
                ))
                && improvement.hasUnique(
                    UniqueType.Unpillagable,
                    StateForConditionals.IgnoreConditionals
                )
            ) {
                lines.add(
                    "${improvement.name} has both an `Unpillagable` unique type and a `PillageYieldRandom` or `PillageYieldFixed` unique type!",
                    RulesetErrorSeverity.Warning
                )
            }
            uniqueValidator.checkUniques(improvement, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addResourceErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (resource in ruleset.tileResources.values) {
            if (resource.revealedBy != null && !ruleset.technologies.containsKey(resource.revealedBy!!))
                lines += "${resource.name} revealed by tech ${resource.revealedBy} which does not exist!"
            if (resource.improvement != null && !ruleset.tileImprovements.containsKey(resource.improvement!!))
                lines += "${resource.name} improved by improvement ${resource.improvement} which does not exist!"
            for (improvement in resource.improvedBy)
                if (!ruleset.tileImprovements.containsKey(improvement))
                    lines += "${resource.name} improved by improvement $improvement which does not exist!"
            for (terrain in resource.terrainsCanBeFoundOn)
                if (!ruleset.terrains.containsKey(terrain))
                    lines += "${resource.name} can be found on terrain $terrain which does not exist!"
            uniqueValidator.checkUniques(resource, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addSpecialistErrors(lines: RulesetErrorList) {
        for (specialist in ruleset.specialists.values) {
            for (gpp in specialist.greatPersonPoints)
                if (gpp.key !in ruleset.units)
                    lines.add(
                        "Specialist ${specialist.name} has greatPersonPoints for ${gpp.key}, which is not a unit in the ruleset!",
                        RulesetErrorSeverity.Warning
                    )
        }
    }

    private fun addBuildingErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (building in ruleset.buildings.values) {
            if (building.requiredTech != null && !ruleset.technologies.containsKey(building.requiredTech!!))
                lines += "${building.name} requires tech ${building.requiredTech} which does not exist!"

            for (specialistName in building.specialistSlots.keys)
                if (!ruleset.specialists.containsKey(specialistName))
                    lines += "${building.name} provides specialist $specialistName which does not exist!"
            for (resource in building.getResourceRequirementsPerTurn().keys)
                if (!ruleset.tileResources.containsKey(resource))
                    lines += "${building.name} requires resource $resource which does not exist!"
            if (building.replaces != null && !ruleset.buildings.containsKey(building.replaces!!))
                lines += "${building.name} replaces ${building.replaces} which does not exist!"
            if (building.requiredBuilding != null && !ruleset.buildings.containsKey(building.requiredBuilding!!))
                lines += "${building.name} requires ${building.requiredBuilding} which does not exist!"
            uniqueValidator.checkUniques(building, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addUnitErrors(
        lines: RulesetErrorList,
        rulesetSpecific: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        if (ruleset.units.values.none { it.isCityFounder() })
            lines += "No city-founding units in ruleset!"

        for (unit in ruleset.units.values) {
            checkUnitRulesetSpecific(unit, lines)
            uniqueValidator.checkUniques(unit, lines, rulesetSpecific, tryFixUnknownUniques)
        }
    }

    private fun addResourceErrorsRulesetInvariant(
        lines: RulesetErrorList,
        rulesetInvariant: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (resource in ruleset.tileResources.values) {
            uniqueValidator.checkUniques(resource, lines, rulesetInvariant, tryFixUnknownUniques)
        }
    }

    private fun addPromotionErrorsRulesetInvariant(
        lines: RulesetErrorList,
        rulesetInvariant: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (promotion in ruleset.unitPromotions.values) {
            uniqueValidator.checkUniques(promotion, lines, rulesetInvariant, tryFixUnknownUniques)
            if (promotion.row < -1) lines += "Promotion ${promotion.name} has invalid row value: ${promotion.row}"
            if (promotion.column < 0) lines += "Promotion ${promotion.name} has invalid column value: ${promotion.column}"
            if (promotion.row == -1) continue
            for (otherPromotion in ruleset.unitPromotions.values)
                if (promotion != otherPromotion && promotion.column == otherPromotion.column && promotion.row == otherPromotion.row)
                    lines += "Promotions ${promotion.name} and ${otherPromotion.name} have the same position: ${promotion.row}/${promotion.column}"
        }
    }

    private fun addNationErrorsRulesetInvariant(
        lines: RulesetErrorList,
        rulesetInvariant: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (nation in ruleset.nations.values) {
            if (nation.cities.isEmpty() && !nation.isSpectator && !nation.isBarbarian) {
                lines += "${nation.name} can settle cities, but has no city names!"
            }

            // https://www.w3.org/TR/WCAG20/#visual-audio-contrast-contrast
            val constrastRatio = nation.getContrastRatio()
            if (constrastRatio < 3) {
                val innerColorLuminance = getRelativeLuminance(nation.getInnerColor())
                val outerColorLuminance = getRelativeLuminance(nation.getOuterColor())

                val innerLerpColor: Color
                val outerLerpColor: Color

                if (innerColorLuminance > outerColorLuminance) { // inner is brighter
                    innerLerpColor = Color.WHITE
                    outerLerpColor = Color.BLACK
                } else {
                    innerLerpColor = Color.BLACK
                    outerLerpColor = Color.WHITE
                }

                var text = "${nation.name}'s colors do not contrast enough - it is unreadable!"

                for (i in 1..10) {
                    val newInnerColor = nation.getInnerColor().cpy().lerp(innerLerpColor, 0.05f * i)
                    val newOuterColor = nation.getOuterColor().cpy().lerp(outerLerpColor, 0.05f * i)

                    if (getContrastRatio(newInnerColor, newOuterColor) > 3) {
                        text += "\nSuggested colors: "
                        text += "\n\t\t\"outerColor\": [${(newOuterColor.r * 255).toInt()}, ${(newOuterColor.g * 255).toInt()}, ${(newOuterColor.b * 255).toInt()}],"
                        text += "\n\t\t\"innerColor\": [${(newInnerColor.r * 255).toInt()}, ${(newInnerColor.g * 255).toInt()}, ${(newInnerColor.b * 255).toInt()}],"
                        break
                    }
                }

                lines.add(
                    text, RulesetErrorSeverity.WarningOptionsOnly
                )
            }

            uniqueValidator.checkUniques(nation, lines, rulesetInvariant, tryFixUnknownUniques)
        }
    }

    private fun addBuildingErrorsRulesetInvariant(
        lines: RulesetErrorList,
        rulesetInvariant: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (building in ruleset.buildings.values) {
            if (building.requiredTech == null && building.cost == -1 && !building.hasUnique(
                    UniqueType.Unbuildable
                )
            )
                lines.add(
                    "${building.name} is buildable and therefore should either have an explicit cost or reference an existing tech!",
                    RulesetErrorSeverity.Warning
                )

            for (gpp in building.greatPersonPoints)
                if (gpp.key !in ruleset.units)
                    lines.add(
                        "Building ${building.name} has greatPersonPoints for ${gpp.key}, which is not a unit in the ruleset!",
                        RulesetErrorSeverity.Warning
                    )

            uniqueValidator.checkUniques(building, lines, rulesetInvariant, tryFixUnknownUniques)

        }
    }

    private fun addTechColumnErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (techColumn in ruleset.techColumns) {
            if (techColumn.columnNumber < 0)
                lines += "Tech Column number ${techColumn.columnNumber} is negative"
            if (techColumn.buildingCost == -1)
                lines.add(
                    "Tech Column number ${techColumn.columnNumber} has no explicit building cost",
                    RulesetErrorSeverity.Warning
                )
            if (techColumn.wonderCost == -1)
                lines.add(
                    "Tech Column number ${techColumn.columnNumber} has no explicit wonder cost",
                    RulesetErrorSeverity.Warning
                )
        }
    }

    private fun addTechErrorsRulesetInvariant(
        lines: RulesetErrorList,
        rulesetInvariant: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (tech in ruleset.technologies.values) {
            for (otherTech in ruleset.technologies.values) {
                if (tech != otherTech && otherTech.column?.columnNumber == tech.column?.columnNumber && otherTech.row == tech.row)
                    lines += "${tech.name} is in the same row and column as ${otherTech.name}!"
            }

            uniqueValidator.checkUniques(tech, lines, rulesetInvariant, tryFixUnknownUniques)
        }
    }

    private fun addUnitErrorsRulesetInvariant(
        lines: RulesetErrorList,
        rulesetInvariant: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (unit in ruleset.units.values) {
            if (unit.upgradesTo == unit.name || (unit.upgradesTo != null && unit.upgradesTo == unit.replaces))
                lines += "${unit.name} upgrades to itself!"
            if (!unit.isCivilian() && unit.strength == 0)
                lines += "${unit.name} is a military unit but has no assigned strength!"
            if (unit.isRanged() && unit.rangedStrength == 0 && !unit.hasUnique(UniqueType.CannotAttack))
                lines += "${unit.name} is a ranged unit but has no assigned rangedStrength!"

            uniqueValidator.checkUniques(unit, lines, rulesetInvariant, tryFixUnknownUniques)
        }
    }

    /** Collects known technology prerequisite paths: key is the technology name,
     *  value a Set of its prerequisites including indirect ones */
    private val prereqsHashMap = HashMap<String, HashSet<String>>()
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

    /** Collects all RulesetSpecific checks for a BaseUnit */
    private fun checkUnitRulesetSpecific(unit: BaseUnit, lines: RulesetErrorList) {
        if (unit.requiredTech != null && !ruleset.technologies.containsKey(unit.requiredTech!!))
            lines += "${unit.name} requires tech ${unit.requiredTech} which does not exist!"
        if (unit.obsoleteTech != null && !ruleset.technologies.containsKey(unit.obsoleteTech!!))
            lines += "${unit.name} obsoletes at tech ${unit.obsoleteTech} which does not exist!"
        if (unit.upgradesTo != null && !ruleset.units.containsKey(unit.upgradesTo!!))
            lines += "${unit.name} upgrades to unit ${unit.upgradesTo} which does not exist!"

        // Check that we don't obsolete ourselves before we can upgrade
        if (unit.upgradesTo!=null && ruleset.units.containsKey(unit.upgradesTo!!)
            && unit.obsoleteTech!=null && ruleset.technologies.containsKey(unit.obsoleteTech!!)) {
            val upgradedUnit = ruleset.units[unit.upgradesTo!!]!!
            if (upgradedUnit.requiredTech != null && upgradedUnit.requiredTech != unit.obsoleteTech
                && !getPrereqTree(unit.obsoleteTech!!).contains(upgradedUnit.requiredTech)
            )
                lines.add(
                    "${unit.name} obsoletes at tech ${unit.obsoleteTech}," +
                        " and therefore ${upgradedUnit.requiredTech} for its upgrade ${upgradedUnit.name} may not yet be researched!",
                    RulesetErrorSeverity.Warning
                )
        }

        for (resource in unit.getResourceRequirementsPerTurn().keys)
            if (!ruleset.tileResources.containsKey(resource))
                lines += "${unit.name} requires resource $resource which does not exist!"
        if (unit.replaces != null && !ruleset.units.containsKey(unit.replaces!!))
            lines += "${unit.name} replaces ${unit.replaces} which does not exist!"
        for (promotion in unit.promotions)
            if (!ruleset.unitPromotions.containsKey(promotion))
                lines += "${unit.name} contains promotion $promotion which does not exist!"
        checkUnitType(unit.unitType) {
            lines += "${unit.name} is of type ${unit.unitType}, which does not exist!"
        }
        for (unique in unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly)) {
            val improvementName = unique.params[0]
            if (ruleset.tileImprovements[improvementName]==null) continue // this will be caught in the uniqueValidator.checkUniques
            if ((ruleset.tileImprovements[improvementName] as Stats).none() &&
                unit.isCivilian() &&
                !unit.isGreatPersonOfType("War")) {
                lines.add("${unit.name} can place improvement $improvementName which has no stats, preventing unit automation!",
                    RulesetErrorSeverity.WarningOptionsOnly)
            }
        }

        checkUnitUpgradePath(unit, lines)
    }

    // for UnitTypes fallback, used if and only if the 'Ruleset Specific Part' runs,
    // **and** a mod fails to provide the UnitTypes entirely (ruleset.unitTypes.isEmpty() is true).
    private val vanillaRuleset by lazy { RulesetCache.getVanillaRuleset() }

    /** Checks validity of one UnitType by name supporting fallback if a mod has no UnitTypes at all,
     *  and calls [reportError] if it is bad.
     */
    private fun checkUnitType(type: String, reportError: ()->Unit) {
        if (ruleset.unitTypes.containsKey(type)) return
        if (ruleset.unitTypes.isEmpty() && vanillaRuleset.unitTypes.containsKey(type)) return
        reportError()
    }

    /** Maps unit name to a set of all units naming it in its "replaces" property,
     *  only for units having such a non-empty set, for use in [checkUnitUpgradePath] */
    private val unitReplacesMap: Map<String, Set<BaseUnit>> by lazy {
        ruleset.units.values.asSequence()
            .mapNotNull { it.replaces }.distinct()
            .associateWith { base ->
                ruleset.units.values.filter { it.replaces == base }.toSet()
            }
    }

    /** Checks all possible upgrade paths of [unit], reporting to [lines].
     *  @param path used in recursion collecting the BaseUnits seen so far
     *
     *  Note: Since the units down the path will also be checked, this could log the same mistakes
     *  repeatedly, but that is mostly prevented by RulesetErrorList.add(). Each unit involved in a
     *  loop will still be flagged individually.
     */
    private fun checkUnitUpgradePath(
        unit: BaseUnit,
        lines: RulesetErrorList,
        path: Set<BaseUnit> = emptySet()
    ) {
        // This is similar to UnitUpgradeManager.getUpgradePath but without the dependency on a Civilization instance
        // It also branches over all possible nation-unique replacements in one go, since we only look for loops.
        if (unit in path) {
            lines += "Circular or self-referencing upgrade path for ${unit.name}"
            return
        }
        val upgrade = ruleset.units[unit.upgradesTo] ?: return
        val newPath = path + unit // All Set additions are new Sets - we're recursing!
        val newPathWithReplacements = unitReplacesMap[unit.name]?.let { newPath + it } ?: newPath
        checkUnitUpgradePath(upgrade, lines, newPathWithReplacements)
        val replacements = unitReplacesMap[upgrade.name] ?: return
        for (toCheck in replacements) {
            checkUnitUpgradePath(toCheck, lines, newPath)
        }
    }

    private fun checkTilesetSanity(lines: RulesetErrorList) {
        val tilesetConfigFolder = (ruleset.folderLocation ?: Gdx.files.internal("")).child("jsons\\TileSets")
        if (!tilesetConfigFolder.exists()) return

        val configTilesets = mutableSetOf<String>()
        val allFallbacks = mutableSetOf<String>()
        val folderContent = tilesetConfigFolder.list()
        var folderContentBad = false

        for (file in folderContent) {
            if (file.isDirectory || file.extension() != "json") { folderContentBad = true; continue }
            // All json files should be parseable
            try {
                val config = json().fromJsonFile(TileSetConfig::class.java, file)
                configTilesets += file.nameWithoutExtension().removeSuffix("Config")
                if (config.fallbackTileSet?.isNotEmpty() == true)
                    allFallbacks.add(config.fallbackTileSet!!)
            } catch (ex: Exception) {
                // Our fromJsonFile wrapper already intercepts Exceptions and gives them a generalized message, so go a level deeper for useful details (like "unmatched brace")
                lines.add("Tileset config '${file.name()}' cannot be loaded (${ex.cause?.message})", RulesetErrorSeverity.Warning)
            }
        }

        // Folder should not contain subdirectories, non-json files, or be empty
        if (folderContentBad)
            lines.add("The Mod tileset config folder contains non-json files or subdirectories", RulesetErrorSeverity.Warning)
        if (configTilesets.isEmpty())
            lines.add("The Mod tileset config folder contains no json files", RulesetErrorSeverity.Warning)

        // There should be atlas images corresponding to each json name
        val atlasTilesets = getTilesetNamesFromAtlases()
        val configOnlyTilesets = configTilesets - atlasTilesets
        if (configOnlyTilesets.isNotEmpty())
            lines.add("Mod has no graphics for configured tilesets: ${configOnlyTilesets.joinToString()}", RulesetErrorSeverity.Warning)

        // For all atlas images matching "TileSets/*" there should be a json
        val atlasOnlyTilesets = atlasTilesets - configTilesets
        if (atlasOnlyTilesets.isNotEmpty())
            lines.add("Mod has no configuration for tileset graphics: ${atlasOnlyTilesets.joinToString()}", RulesetErrorSeverity.Warning)

        // All fallbacks should exist (default added because TileSetCache is not loaded when running as unit test)
        val unknownFallbacks = allFallbacks - TileSetCache.keys - Constants.defaultFallbackTileset
        if (unknownFallbacks.isNotEmpty())
            lines.add("Fallback tileset invalid: ${unknownFallbacks.joinToString()}", RulesetErrorSeverity.Warning)
    }

    private fun getTilesetNamesFromAtlases(): Set<String> {
        // This partially duplicates code in ImageGetter.getAvailableTilesets, but we don't want to reload that singleton cache.

        // Our builtin rulesets have no folderLocation, in that case cheat and apply knowledge about
        // where the builtin Tileset textures are (correct would be to parse Atlases.json):
        val files = ruleset.folderLocation?.list("atlas")?.asSequence()
            ?: sequenceOf(Gdx.files.internal("Tilesets.atlas"))
        // Next, we need to cope with this running without GL context (unit test) - no TextureAtlas(file)
        return files
            .flatMap { file ->
                TextureAtlasData(file, file.parent(), false).regions.asSequence()
                    .filter { it.name.startsWith("TileSets/") && !it.name.contains("/Units/") }
            }.map { it.name.split("/")[1] }
            .toSet()
    }

    private fun checkPromotionCircularReferences(lines: RulesetErrorList) {
        fun recursiveCheck(history: HashSet<Promotion>, promotion: Promotion, level: Int) {
            if (promotion in history) {
                lines.add("Circular Reference in Promotions: ${history.joinToString("→") { it.name }}→${promotion.name}",
                    RulesetErrorSeverity.Warning)
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


}
