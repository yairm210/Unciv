package com.unciv.models.ruleset.validation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.unciv.Constants
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.nation.getContrastRatio
import com.unciv.models.ruleset.nation.getRelativeLuminance
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.validation.RulesetValidator.Suppression.removeSuppressions
import com.unciv.models.stats.Stats
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig
import com.unciv.models.translations.fillPlaceholders

class RulesetValidator(val ruleset: Ruleset) {

    private val uniqueValidator = UniqueValidator(ruleset)

    fun getErrorList(tryFixUnknownUniques: Boolean = false): RulesetErrorList {
        val unfilteredResult =
            if (ruleset.modOptions.isBaseRuleset)
                getBaseRulesetErrorList(tryFixUnknownUniques)
            else
                // When no base ruleset is loaded - references cannot be checked
                getNonBaseRulesetErrorList(tryFixUnknownUniques)
        return unfilteredResult.removeSuppressions(ruleset.modOptions)
    }

    private fun getNonBaseRulesetErrorList(tryFixUnknownUniques: Boolean): RulesetErrorList {
        val lines = RulesetErrorList()

        // When not checking the entire ruleset, we can only really detect ruleset-invariant errors in uniques
        addModOptionsErrors(lines, tryFixUnknownUniques)
        uniqueValidator.checkUniques(ruleset.globalUniques, lines, false, tryFixUnknownUniques)
        addUnitErrorsRulesetInvariant(lines, tryFixUnknownUniques)
        addTechErrorsRulesetInvariant(lines, tryFixUnknownUniques)
        addTechColumnErrorsRulesetInvariant(lines)
        addBuildingErrorsRulesetInvariant(lines, tryFixUnknownUniques)
        addNationErrorsRulesetInvariant(lines, tryFixUnknownUniques)
        addPromotionErrorsRulesetInvariant(lines, tryFixUnknownUniques)
        addResourceErrorsRulesetInvariant(lines, tryFixUnknownUniques)

        /**********************  **********************/
        // e.g. json configs complete and parseable
        // Check for mod or Civ_V_GnK to avoid running the same test twice (~200ms for the builtin assets)
        if (ruleset.folderLocation != null) checkTilesetSanity(lines)

        return lines
    }


    private fun getBaseRulesetErrorList(tryFixUnknownUniques: Boolean): RulesetErrorList {

        uniqueValidator.populateFilteringUniqueHashsets()

        val lines = RulesetErrorList()
        addModOptionsErrors(lines, tryFixUnknownUniques)
        uniqueValidator.checkUniques(ruleset.globalUniques, lines, true, tryFixUnknownUniques)

        addUnitErrorsBaseRuleset(lines, tryFixUnknownUniques)
        addBuildingErrors(lines, tryFixUnknownUniques)
        addSpecialistErrors(lines)
        addResourceErrors(lines, tryFixUnknownUniques)
        addImprovementErrors(lines, tryFixUnknownUniques)
        addTerrainErrors(lines, tryFixUnknownUniques)
        addTechErrors(lines, tryFixUnknownUniques)
        addTechColumnErrorsRulesetInvariant(lines)
        addEraErrors(lines, tryFixUnknownUniques)
        addSpeedErrors(lines)
        addBeliefErrors(lines, tryFixUnknownUniques)
        addNationErrors(lines, tryFixUnknownUniques)
        addPolicyErrors(lines, tryFixUnknownUniques)
        addRuinsErrors(lines, tryFixUnknownUniques)
        addPromotionErrors(lines, tryFixUnknownUniques)
        addUnitTypeErrors(lines, tryFixUnknownUniques)
        addVictoryTypeErrors(lines)
        addDifficultyErrors(lines)
        addCityStateTypeErrors(tryFixUnknownUniques, lines)

        // Check for mod or Civ_V_GnK to avoid running the same test twice (~200ms for the builtin assets)
        if (ruleset.folderLocation != null || ruleset.name == BaseRuleset.Civ_V_GnK.fullName) {
            checkTilesetSanity(lines)
        }

        return lines
    }

    private fun addModOptionsErrors(lines: RulesetErrorList, tryFixUnknownUniques: Boolean) {
        // Basic Unique validation (type, target, parameters) should always run.
        // Using reportRulesetSpecificErrors=true as ModOptions never should use Uniques depending on objects from a base ruleset anyway.
        uniqueValidator.checkUniques(ruleset.modOptions, lines, reportRulesetSpecificErrors = true, tryFixUnknownUniques)

        if (ruleset.name.isBlank()) return // The rest of these tests don't make sense for combined rulesets

        val audioVisualUniqueTypes = setOf(
            UniqueType.ModIsAudioVisual,
            UniqueType.ModIsAudioVisualOnly,
            UniqueType.ModIsNotAudioVisual
        )
        if (ruleset.modOptions.uniqueObjects.count { it.type in audioVisualUniqueTypes } > 1)
            lines += "A mod should only specify one of the 'can/should/cannot be used as permanent audiovisual mod' options."
        if (!ruleset.modOptions.isBaseRuleset) return
        for (unique in ruleset.modOptions.getMatchingUniques(UniqueType.ModRequires)) {
            lines += "Mod option '${unique.text}' is invalid for a base ruleset."
        }
    }

    private fun addCityStateTypeErrors(
        tryFixUnknownUniques: Boolean,
        lines: RulesetErrorList
    ) {
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

    private fun addDifficultyErrors(lines: RulesetErrorList) {
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
        tryFixUnknownUniques: Boolean
    ) {
        for (unitType in ruleset.unitTypes.values) {
            uniqueValidator.checkUniques(unitType, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addPromotionErrors(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (promotion in ruleset.unitPromotions.values) {
            addPromotionErrorRulesetInvariant(promotion, lines)

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
            uniqueValidator.checkUniques(promotion, lines, true, tryFixUnknownUniques)
        }
        checkPromotionCircularReferences(lines)
    }

    private fun addRuinsErrors(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (reward in ruleset.ruinRewards.values) {
            @Suppress("KotlinConstantConditions") // data is read from json, so any assumptions may be wrong
            if (reward.weight < 0) lines += "${reward.name} has a negative weight, which is not allowed!"
            for (difficulty in reward.excludedDifficulties)
                if (!ruleset.difficulties.containsKey(difficulty))
                    lines += "${reward.name} references difficulty ${difficulty}, which does not exist!"
            uniqueValidator.checkUniques(reward, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addPolicyErrors(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (policy in ruleset.policies.values) {
            if (policy.requires != null)
                for (prereq in policy.requires!!)
                    if (!ruleset.policies.containsKey(prereq))
                        lines += "${policy.name} requires policy $prereq which does not exist!"
            uniqueValidator.checkUniques(policy, lines, true, tryFixUnknownUniques)
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
        tryFixUnknownUniques: Boolean
    ) {
        for (nation in ruleset.nations.values) {
            addNationErrorRulesetInvariant(nation, lines)

            uniqueValidator.checkUniques(nation, lines, true, tryFixUnknownUniques)

            if (nation.cityStateType != null && nation.cityStateType !in ruleset.cityStateTypes)
                lines += "${nation.name} is of city-state type ${nation.cityStateType} which does not exist!"
            if (nation.favoredReligion != null && nation.favoredReligion !in ruleset.religions)
                lines += "${nation.name} has ${nation.favoredReligion} as their favored religion, which does not exist!"
        }
    }

    private fun addBeliefErrors(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (belief in ruleset.beliefs.values) {
            if (belief.type == BeliefType.Any || belief.type == BeliefType.None)
                lines += "${belief.name} type is {belief.type}, which is not allowed!"
            uniqueValidator.checkUniques(belief, lines, true, tryFixUnknownUniques)
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

            val grantsStartingMilitaryUnit = era.startingMilitaryUnitCount != 0
                || allDifficultiesStartingUnits.contains(Constants.eraSpecificUnit)
            if (grantsStartingMilitaryUnit && era.startingMilitaryUnit !in ruleset.units)
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

            uniqueValidator.checkUniques(era, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addTechErrors(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (tech in ruleset.technologies.values) {
            for (prereq in tech.prerequisites) {
                if (!ruleset.technologies.containsKey(prereq))
                    lines += "${tech.name} requires tech $prereq which does not exist!"

                if (tech.prerequisites.any { it != prereq && getPrereqTree(it).contains(prereq) }) {
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
            uniqueValidator.checkUniques(tech, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addTerrainErrors(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        if (ruleset.terrains.values.none { it.type == TerrainType.Land && !it.impassable })
            lines += "No passable land terrains exist!"

        for (terrain in ruleset.terrains.values) {
            for (baseTerrainName in terrain.occursOn) {
                val baseTerrain = ruleset.terrains[baseTerrainName]
                if (baseTerrain == null)
                    lines += "${terrain.name} occurs on terrain $baseTerrainName which does not exist!"
                else if (baseTerrain.type == TerrainType.NaturalWonder)
                    lines.add("${terrain.name} occurs on natural wonder $baseTerrainName: Unsupported.", RulesetErrorSeverity.WarningOptionsOnly)
            }
            if (terrain.type == TerrainType.NaturalWonder) {
                if (terrain.turnsInto == null)
                    lines += "Natural Wonder ${terrain.name} is missing the turnsInto attribute!"
                val baseTerrain = ruleset.terrains[terrain.turnsInto]
                if (baseTerrain == null)
                    lines += "${terrain.name} turns into terrain ${terrain.turnsInto} which does not exist!"
                else if (!baseTerrain.type.isBaseTerrain)
                    // See https://github.com/hackedpassword/Z2/blob/main/HybridTileTech.md for a clever exploit
                    lines.add("${terrain.name} turns into terrain ${terrain.turnsInto} which is not a base terrain!", RulesetErrorSeverity.Warning)
            }
            uniqueValidator.checkUniques(terrain, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addImprovementErrors(
        lines: RulesetErrorList,
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
            for (unique in improvement.uniqueObjects
                    .filter { it.type == UniqueType.PillageYieldRandom || it.type == UniqueType.PillageYieldFixed }) {
                if (!Stats.isStats(unique.params[0])) continue
                val params = Stats.parse(unique.params[0])
                if (params.values.any { it < 0 }) lines.add(
                    "${improvement.name} cannot have a negative value for a pillage yield!",
                    RulesetErrorSeverity.Error
                )
            }

            val hasPillageUnique = improvement.hasUnique(UniqueType.PillageYieldRandom, StateForConditionals.IgnoreConditionals)
                || improvement.hasUnique(UniqueType.PillageYieldFixed, StateForConditionals.IgnoreConditionals)
            if (hasPillageUnique && improvement.hasUnique(UniqueType.Unpillagable, StateForConditionals.IgnoreConditionals)) {
                lines.add(
                    "${improvement.name} has both an `Unpillagable` unique type and a `PillageYieldRandom` or `PillageYieldFixed` unique type!",
                    RulesetErrorSeverity.Warning
                )
            }
            uniqueValidator.checkUniques(improvement, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addResourceErrors(
        lines: RulesetErrorList,
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
            uniqueValidator.checkUniques(resource, lines, true, tryFixUnknownUniques)
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
        tryFixUnknownUniques: Boolean
    ) {
        for (building in ruleset.buildings.values) {
            addBuildingErrorRulesetInvariant(building, lines)

            for (requiredTech: String in building.requiredTechs())
                if (!ruleset.technologies.containsKey(requiredTech))
                    lines += "${building.name} requires tech $requiredTech which does not exist!"
            for (specialistName in building.specialistSlots.keys)
                if (!ruleset.specialists.containsKey(specialistName))
                    lines += "${building.name} provides specialist $specialistName which does not exist!"
            for (resource in building.getResourceRequirementsPerTurn(StateForConditionals.IgnoreConditionals).keys)
                if (!ruleset.tileResources.containsKey(resource))
                    lines += "${building.name} requires resource $resource which does not exist!"
            if (building.replaces != null && !ruleset.buildings.containsKey(building.replaces!!))
                lines += "${building.name} replaces ${building.replaces} which does not exist!"
            if (building.requiredBuilding != null && !ruleset.buildings.containsKey(building.requiredBuilding!!))
                lines += "${building.name} requires ${building.requiredBuilding} which does not exist!"
            uniqueValidator.checkUniques(building, lines, true, tryFixUnknownUniques)
        }
    }


    private fun addUnitErrorsRulesetInvariant(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (unit in ruleset.units.values) {
            checkUnitRulesetInvariant(unit, lines)
            uniqueValidator.checkUniques(unit, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addUnitErrorsBaseRuleset(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        if (ruleset.units.values.none { it.isCityFounder() })
            lines += "No city-founding units in ruleset!"

        for (unit in ruleset.units.values) {
            checkUnitRulesetInvariant(unit, lines)
            checkUnitRulesetSpecific(unit, lines)
            uniqueValidator.checkUniques(unit, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addResourceErrorsRulesetInvariant(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (resource in ruleset.tileResources.values) {
            uniqueValidator.checkUniques(resource, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addPromotionErrorsRulesetInvariant(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (promotion in ruleset.unitPromotions.values) {
            uniqueValidator.checkUniques(promotion, lines, false, tryFixUnknownUniques)

            addPromotionErrorRulesetInvariant(promotion, lines)
        }
    }

    private fun addPromotionErrorRulesetInvariant(promotion: Promotion, lines: RulesetErrorList) {
        if (promotion.row < -1) lines += "Promotion ${promotion.name} has invalid row value: ${promotion.row}"
        if (promotion.column < 0) lines += "Promotion ${promotion.name} has invalid column value: ${promotion.column}"
        if (promotion.row == -1) return
        for (otherPromotion in ruleset.unitPromotions.values)
            if (promotion != otherPromotion && promotion.column == otherPromotion.column && promotion.row == otherPromotion.row)
                lines += "Promotions ${promotion.name} and ${otherPromotion.name} have the same position: ${promotion.row}/${promotion.column}"
    }

    private fun addNationErrorsRulesetInvariant(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (nation in ruleset.nations.values) {
            addNationErrorRulesetInvariant(nation, lines)
            uniqueValidator.checkUniques(nation, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addNationErrorRulesetInvariant(nation: Nation, lines: RulesetErrorList) {
        if (nation.cities.isEmpty() && !nation.isSpectator && !nation.isBarbarian) {
            lines += "${nation.name} can settle cities, but has no city names!"
        }

        // https://www.w3.org/TR/WCAG20/#visual-audio-contrast-contrast
        val constrastRatio = nation.getContrastRatio()
        if (constrastRatio < 3) {
            val suggestedColors = getSuggestedColors(nation)
            val newOuterColor = suggestedColors.outerColor
            val newInnerColor = suggestedColors.innerColor

            var text = "${nation.name}'s colors do not contrast enough - it is unreadable!"
            text += "\nSuggested colors: "
            text += "\n\t\t\"outerColor\": [${(newOuterColor.r * 255).toInt()}, ${(newOuterColor.g * 255).toInt()}, ${(newOuterColor.b * 255).toInt()}],"
            text += "\n\t\t\"innerColor\": [${(newInnerColor.r * 255).toInt()}, ${(newInnerColor.g * 255).toInt()}, ${(newInnerColor.b * 255).toInt()}],"

            lines.add(text, RulesetErrorSeverity.WarningOptionsOnly)
            lines.add(text, RulesetErrorSeverity.WarningOptionsOnly)
        }
    }

    data class SuggestedColors(val innerColor: Color, val outerColor:Color)

    private fun getSuggestedColors(nation: Nation): SuggestedColors {
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


        for (i in 1..10) {
            val newInnerColor = nation.getInnerColor().cpy().lerp(innerLerpColor, 0.05f * i)
            val newOuterColor = nation.getOuterColor().cpy().lerp(outerLerpColor, 0.05f * i)

            if (getContrastRatio(newInnerColor, newOuterColor) > 3) return SuggestedColors(newInnerColor, newOuterColor)
        }
        throw Exception("Error getting suggested colors for nation "+nation.name)
    }

    private fun addBuildingErrorsRulesetInvariant(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (building in ruleset.buildings.values) {
            addBuildingErrorRulesetInvariant(building, lines)

            uniqueValidator.checkUniques(building, lines, false, tryFixUnknownUniques)

        }
    }

    private fun addBuildingErrorRulesetInvariant(building: Building, lines: RulesetErrorList) {
        if (building.requiredTechs().none() && building.cost == -1 && !building.hasUnique(
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

        for (tech in ruleset.technologies.values) {
            for (otherTech in ruleset.technologies.values) {
                if (tech != otherTech && otherTech.column?.columnNumber == tech.column?.columnNumber && otherTech.row == tech.row)
                    lines += "${tech.name} is in the same row and column as ${otherTech.name}!"
            }
        }
    }

    private fun addTechErrorsRulesetInvariant(
        lines: RulesetErrorList,
        tryFixUnknownUniques: Boolean
    ) {
        for (tech in ruleset.technologies.values) {
            uniqueValidator.checkUniques(tech, lines, false, tryFixUnknownUniques)
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

    private fun checkUnitRulesetInvariant(unit: BaseUnit, lines: RulesetErrorList) {
        for (upgradesTo in unit.getUpgradeUnits(StateForConditionals.IgnoreConditionals)) {
            if (upgradesTo == unit.name || (upgradesTo == unit.replaces))
                lines += "${unit.name} upgrades to itself!"
        }
        if (unit.isMilitary() && unit.strength == 0)  // Should only match ranged units with 0 strength
            lines += "${unit.name} is a military unit but has no assigned strength!"
    }

    /** Collects all RulesetSpecific checks for a BaseUnit */
    private fun checkUnitRulesetSpecific(unit: BaseUnit, lines: RulesetErrorList) {
        for (requiredTech: String in unit.requiredTechs())
            if (!ruleset.technologies.containsKey(requiredTech))
                lines += "${unit.name} requires tech $requiredTech which does not exist!"
        for (obsoleteTech: String in unit.techsAtWhichNoLongerAvailable())
            if (!ruleset.technologies.containsKey(obsoleteTech))
                lines += "${unit.name} obsoletes at tech $obsoleteTech which does not exist!"
        for (upgradesTo in unit.getUpgradeUnits(StateForConditionals.IgnoreConditionals))
            if (!ruleset.units.containsKey(upgradesTo))
                lines += "${unit.name} upgrades to unit $upgradesTo which does not exist!"

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
                            RulesetErrorSeverity.Warning
                        )
            }

        for (resource in unit.getResourceRequirementsPerTurn(StateForConditionals.IgnoreConditionals).keys)
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

        // We should ignore conditionals here - there are condition implementations on this out there that require a game state (and will test false without)
        for (unique in unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly, StateForConditionals.IgnoreConditionals)) {
            val improvementName = unique.params[0]
            if (ruleset.tileImprovements[improvementName] == null) continue // this will be caught in the uniqueValidator.checkUniques
            if ((ruleset.tileImprovements[improvementName] as Stats).isEmpty() &&
                unit.isCivilian() &&
                !unit.isGreatPersonOfType("War")) {
                lines.add("${unit.name} can place improvement $improvementName which has no stats, preventing unit automation!",
                    RulesetErrorSeverity.WarningOptionsOnly)
            }
        }
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

    /**
     *  All methods dealing with how Mod authors can suppress RulesetValidator output are here.
     *
     *  This allows the outside code to be agnostic about how each entry operates, it's all here,
     *  and can easily be expanded, e.g. to some wildcard type.
     *  The [docDescription], [isErrorSuppressed] and [isValidFilter] need to agree on the rules!
     *
     *  Current decisions:
     *  * You cannot suppress [RulesetErrorSeverity.Error] level messages.
     *  * Each suppression entry is compared verbatim and case-sensitive.
     *  * Validation of the suppression entries themselves is rudimentary.
     */
    object Suppression {
        /** Delegated from [UniqueParameterType.ValidationWarning] */
        const val docDescription = "Suppresses one specific Ruleset validation warning. Needs to specify the full text verbatim including correct upper/lower case."

        /** Delegated from [UniqueParameterType.ValidationWarning] */
        const val docExample = "Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched!"

        private val deprecationWarningRegex by lazy { Regex("""^.*unique ".*" is deprecated as of [0-9.]+, replace with.*$""") }
        private val untypedWarningRegex by lazy { Regex("""^.*unique ".*" not found in Unciv's unique types, and is not used as a filtering unique.$""") }

        /** Determine whether [parameterText] is a valid Suppression filter as implemented by [isErrorSuppressed] */
        fun isValidFilter(parameterText: String) = when {
            // Cannot contain {} or <>
            '{' in parameterText || '<' in parameterText -> false
            // Must not be a deprecation - these should be implemented by their replacement not suppressed
            deprecationWarningRegex.matches(parameterText) -> false
            // Must not be a untyped/nonfiltering warning (a case for the Comment UniqueType instead)
            untypedWarningRegex.matches(parameterText) -> false
            // More rules here???
            else -> true
        }

        /** Determine if [error] matches any entry in [suppressionFilters] */
        private fun isErrorSuppressed(error: RulesetError, suppressionFilters: Set<String>): Boolean {
            if (error.errorSeverityToReport >= RulesetErrorSeverity.Error) return false
            return error.text in suppressionFilters
        }

        /** Removes suppressed messages as declared in ModOptions using UniqueType.SuppressWarnings, allows chaining */
        fun RulesetErrorList.removeSuppressions(modOptions: ModOptions): RulesetErrorList {
            // Cache suppressions
            val suppressionFilters = modOptions.uniqueMap.getUniques(UniqueType.SuppressWarnings)
                .map { it.params[0] }.toSet()
            if (suppressionFilters.isEmpty()) return this

            // Check every error for suppression and if so, remove
            val iterator = iterator()
            while (iterator.hasNext()) {
                val error = iterator.next()
                if (isErrorSuppressed(error, suppressionFilters))
                    iterator.remove()  // This is the one Collection removal not throwing those pesky CCM Exceptions
            }

            return this
        }

        /** Whosoever maketh this here methyd available to evil Mod whytches shall forever be cursed and damned to all Hell's tortures */
        @Suppress("unused")  // Debug tool
        fun autoSuppressAllWarnings(ruleset: Ruleset, toModOptions: ModOptions) {
            if (ruleset.folderLocation == null)
                throw UncivShowableException("autoSuppressAllWarnings needs Ruleset.folderLocation")
            for (error in RulesetValidator(ruleset).getErrorList()) {
                if (error.errorSeverityToReport >= RulesetErrorSeverity.Error) continue
                toModOptions.uniques += UniqueType.SuppressWarnings.text.fillPlaceholders(error.text)
            }
            json().toJson(toModOptions, ruleset.folderLocation!!.child("jsons/ModOptions.json"))
        }
    }
}
