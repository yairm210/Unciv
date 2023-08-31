package com.unciv.models.ruleset.validation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.unciv.Constants
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.getContrastRatio
import com.unciv.models.ruleset.nation.getRelativeLuminance
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stats
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig

class RulesetValidator(val ruleset: Ruleset) {

    fun getErrorList(tryFixUnknownUniques: Boolean = false): RulesetErrorList {
        val prereqsHashMap = HashMap<String,HashSet<String>>()
        fun getPrereqTree(technologyName: String): Set<String> {
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

        val lines = RulesetErrorList()

        /********************** Ruleset Invariant Part **********************/
        // Checks for all mods - only those that can succeed without loading a base ruleset
        // When not checking the entire ruleset, we can only really detect ruleset-invariant errors in uniques

        val rulesetInvariant = UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        val rulesetSpecific = UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific

        checkUniques(ruleset.globalUniques, lines, rulesetInvariant, tryFixUnknownUniques)

        for (unit in ruleset.units.values) {
            if (unit.upgradesTo == unit.name || (unit.upgradesTo != null && unit.upgradesTo == unit.replaces))
                lines += "${unit.name} upgrades to itself!"
            if (!unit.isCivilian() && unit.strength == 0)
                lines += "${unit.name} is a military unit but has no assigned strength!"
            if (unit.isRanged() && unit.rangedStrength == 0 && !unit.hasUnique(UniqueType.CannotAttack))
                lines += "${unit.name} is a ranged unit but has no assigned rangedStrength!"

            checkUniques(unit, lines, rulesetInvariant, tryFixUnknownUniques)
        }

        for (tech in ruleset.technologies.values) {
            for (otherTech in ruleset.technologies.values) {
                if (tech != otherTech && otherTech.column?.columnNumber == tech.column?.columnNumber && otherTech.row == tech.row)
                    lines += "${tech.name} is in the same row and column as ${otherTech.name}!"
            }

            checkUniques(tech, lines, rulesetInvariant, tryFixUnknownUniques)
        }

        for (techColumn in ruleset.techColumns){
            if (techColumn.columnNumber < 0)
                lines += "Tech Column number ${techColumn.columnNumber} is negative"
            if (techColumn.buildingCost == -1)
                lines.add("Tech Column number ${techColumn.columnNumber} has no explicit building cost",
                    RulesetErrorSeverity.Warning)
            if (techColumn.wonderCost == -1)
                lines.add("Tech Column number ${techColumn.columnNumber} has no explicit wonder cost",
                    RulesetErrorSeverity.Warning)
        }

        for (building in ruleset.buildings.values) {
            if (building.requiredTech == null && building.cost == -1 && !building.hasUnique(
                        UniqueType.Unbuildable))
                lines.add("${building.name} is buildable and therefore should either have an explicit cost or reference an existing tech!",
                    RulesetErrorSeverity.Warning)

            for (gpp in building.greatPersonPoints)
                if (gpp.key !in ruleset.units)
                    lines.add("Building ${building.name} has greatPersonPoints for ${gpp.key}, which is not a unit in the ruleset!",
                        RulesetErrorSeverity.Warning)

            checkUniques(building, lines, rulesetInvariant, tryFixUnknownUniques)

        }

        for (nation in ruleset.nations.values) {
            if (nation.cities.isEmpty() && !nation.isSpectator && !nation.isBarbarian) {
                lines += "${nation.name} can settle cities, but has no city names!"
            }

            // https://www.w3.org/TR/WCAG20/#visual-audio-contrast-contrast
            val constrastRatio = nation.getContrastRatio()
            if (constrastRatio < 3) {
                val innerColorLuminance = getRelativeLuminance(nation.getInnerColor())
                val outerColorLuminance = getRelativeLuminance(nation.getOuterColor())

                val innerLerpColor:Color
                val outerLerpColor:Color

                if (innerColorLuminance > outerColorLuminance) { // inner is brighter
                    innerLerpColor = Color.WHITE
                    outerLerpColor = Color.BLACK
                }
                else {
                    innerLerpColor = Color.BLACK
                    outerLerpColor = Color.WHITE
                }

                var text = "${nation.name}'s colors do not contrast enough - it is unreadable!"

                for (i in 1..10){
                    val newInnerColor = nation.getInnerColor().cpy().lerp(innerLerpColor, 0.05f *i)
                    val newOuterColor = nation.getOuterColor().cpy().lerp(outerLerpColor, 0.05f *i)

                    if (getContrastRatio(newInnerColor, newOuterColor) > 3){
                        text += "\nSuggested colors: "
                        text += "\n\t\t\"outerColor\": [${(newOuterColor.r*255).toInt()}, ${(newOuterColor.g*255).toInt()}, ${(newOuterColor.b*255).toInt()}],"
                        text += "\n\t\t\"innerColor\": [${(newInnerColor.r*255).toInt()}, ${(newInnerColor.g*255).toInt()}, ${(newInnerColor.b*255).toInt()}],"
                        break
                    }
                }

                lines.add(
                    text, RulesetErrorSeverity.WarningOptionsOnly
                )
            }

            checkUniques(nation, lines, rulesetInvariant, tryFixUnknownUniques)
        }

        for (promotion in ruleset.unitPromotions.values) {
            checkUniques(promotion, lines, rulesetInvariant, tryFixUnknownUniques)
            if (promotion.row < -1) lines += "Promotion ${promotion.name} has invalid row value: ${promotion.row}"
            if (promotion.column < 0) lines += "Promotion ${promotion.name} has invalid column value: ${promotion.column}"
            if (promotion.row == -1) continue
            for (otherPromotion in ruleset.unitPromotions.values)
                if (promotion != otherPromotion && promotion.column == otherPromotion.column && promotion.row == otherPromotion.row)
                    lines += "Promotions ${promotion.name} and ${otherPromotion.name} have the same position: ${promotion.row}/${promotion.column}"
        }

        for (resource in ruleset.tileResources.values) {
            checkUniques(resource, lines, rulesetInvariant, tryFixUnknownUniques)
        }

        /********************** Tileset tests **********************/
        // e.g. json configs complete and parseable
        // Check for mod or Civ_V_GnK to avoid running the same test twice (~200ms for the builtin assets)
        if (ruleset.folderLocation != null || ruleset.name == BaseRuleset.Civ_V_GnK.fullName) {
            checkTilesetSanity(lines)
        }

        // Quit here when no base ruleset is loaded - references cannot be checked
        if (!ruleset.modOptions.isBaseRuleset) return lines

        /********************** Ruleset Specific Part **********************/

        val vanillaRuleset = RulesetCache.getVanillaRuleset()  // for UnitTypes fallback

        checkUniques(ruleset.globalUniques, lines, rulesetSpecific, tryFixUnknownUniques)

        if (ruleset.units.values.none { it.hasUnique(UniqueType.FoundCity, StateForConditionals.IgnoreConditionals) })
            lines += "No city-founding units in ruleset!"

        for (unit in ruleset.units.values) {
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
            if (!ruleset.unitTypes.containsKey(unit.unitType) && (ruleset.unitTypes.isNotEmpty() || !vanillaRuleset.unitTypes.containsKey(unit.unitType)))
                lines += "${unit.name} is of type ${unit.unitType}, which does not exist!"
            for (unique in unit.getMatchingUniques(UniqueType.ConstructImprovementInstantly)) {
                val improvementName = unique.params[0]
                if (ruleset.tileImprovements[improvementName]==null) continue // this will be caught in the checkUniques
                if ((ruleset.tileImprovements[improvementName] as Stats).none() &&
                        unit.isCivilian() &&
                        !unit.isGreatPersonOfType("War")) {
                    lines.add("${unit.name} can place improvement $improvementName which has no stats, preventing unit automation!",
                        RulesetErrorSeverity.Warning)
                }
            }

            checkUniques(unit, lines, rulesetSpecific, tryFixUnknownUniques)
        }

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
            checkUniques(building, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (specialist in ruleset.specialists.values){
            for (gpp in specialist.greatPersonPoints)
                if (gpp.key !in ruleset.units)
                    lines.add("Specialist ${specialist.name} has greatPersonPoints for ${gpp.key}, which is not a unit in the ruleset!",
                        RulesetErrorSeverity.Warning)
        }

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
            checkUniques(resource, lines, rulesetSpecific, tryFixUnknownUniques)
        }

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
            if ((improvement.hasUnique(UniqueType.PillageYieldRandom, StateForConditionals.IgnoreConditionals)
                            || improvement.hasUnique(UniqueType.PillageYieldFixed, StateForConditionals.IgnoreConditionals))
                    && improvement.hasUnique(UniqueType.Unpillagable, StateForConditionals.IgnoreConditionals)) {
                lines.add(
                    "${improvement.name} has both an `Unpillagable` unique type and a `PillageYieldRandom` or `PillageYieldFixed` unique type!",
                    RulesetErrorSeverity.Warning
                )
            }
            checkUniques(improvement, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        if (ruleset.terrains.values.none { it.type == TerrainType.Land && !it.impassable })
            lines += "No passable land terrains exist!"
        for (terrain in ruleset.terrains.values) {
            for (baseTerrain in terrain.occursOn)
                if (!ruleset.terrains.containsKey(baseTerrain))
                    lines += "${terrain.name} occurs on terrain $baseTerrain which does not exist!"
            checkUniques(terrain, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (tech in ruleset.technologies.values) {
            for (prereq in tech.prerequisites) {
                if (!ruleset.technologies.containsKey(prereq))
                    lines += "${tech.name} requires tech $prereq which does not exist!"


                if (tech.prerequisites.asSequence().filterNot { it == prereq }
                            .any { getPrereqTree(it).contains(prereq) }){
                    lines.add("No need to add $prereq as a prerequisite of ${tech.name} - it is already implicit from the other prerequisites!",
                        RulesetErrorSeverity.Warning)
                }

                if (getPrereqTree(prereq).contains(tech.name))
                    lines += "Techs ${tech.name} and $prereq require each other!"
            }
            if (tech.era() !in ruleset.eras)
                lines += "Unknown era ${tech.era()} referenced in column of tech ${tech.name}"
            checkUniques(tech, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        if (ruleset.eras.isEmpty()) {
            lines += "Eras file is empty! This will likely lead to crashes. Ask the mod maker to update this mod!"
        }

        val allDifficultiesStartingUnits = hashSetOf<String>()
        for (difficulty in ruleset.difficulties.values){
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
                    && ruleset.units.values.none { it.hasUnique(UniqueType.FoundCity) })
                lines += "Nonexistent unit ${era.startingSettlerUnit} marked as starting unit when starting in ${era.name}"
            if (era.startingWorkerCount != 0 && era.startingWorkerUnit !in ruleset.units
                    && ruleset.units.values.none { it.hasUnique(UniqueType.BuildImprovements) })
                lines += "Nonexistent unit ${era.startingWorkerUnit} marked as starting unit when starting in ${era.name}"

            if ((era.startingMilitaryUnitCount != 0 || allDifficultiesStartingUnits.contains(
                        Constants.eraSpecificUnit)) && era.startingMilitaryUnit !in ruleset.units)
                lines += "Nonexistent unit ${era.startingMilitaryUnit} marked as starting unit when starting in ${era.name}"
            if (era.researchAgreementCost < 0 || era.startingSettlerCount < 0 || era.startingWorkerCount < 0 || era.startingMilitaryUnitCount < 0 || era.startingGold < 0 || era.startingCulture < 0)
                lines += "Unexpected negative number found while parsing era ${era.name}"
            if (era.settlerPopulation <= 0)
                lines += "Population in cities from settlers must be strictly positive! Found value ${era.settlerPopulation} for era ${era.name}"

            if (era.allyBonus.isNotEmpty())
                lines.add("Era ${era.name} contains city-state bonuses. City-state bonuses are now defined in CityStateType.json",
                    RulesetErrorSeverity.WarningOptionsOnly)
            if (era.friendBonus.isNotEmpty())
                lines.add("Era ${era.name} contains city-state bonuses. City-state bonuses are now defined in CityStateType.json",
                    RulesetErrorSeverity.WarningOptionsOnly)

            checkUniques(era, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (speed in ruleset.speeds.values) {
            if (speed.modifier < 0f)
                lines += "Negative speed modifier for game speed ${speed.name}"
            if (speed.yearsPerTurn.isEmpty())
                lines += "Empty turn increment list for game speed ${speed.name}"
        }

        for (belief in ruleset.beliefs.values) {
            checkUniques(belief, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (nation in ruleset.nations.values) {
            checkUniques(nation, lines, rulesetSpecific, tryFixUnknownUniques)

            if (nation.cityStateType!=null && nation.cityStateType !in ruleset.cityStateTypes)
                lines += "${nation.name} is of city-state type ${nation.cityStateType} which does not exist!"
            if (nation.favoredReligion != null && nation.favoredReligion !in ruleset.religions)
                lines += "${nation.name} has ${nation.favoredReligion} as their favored religion, which does not exist!"
        }

        for (policy in ruleset.policies.values) {
            if (policy.requires != null)
                for (prereq in policy.requires!!)
                    if (!ruleset.policies.containsKey(prereq))
                        lines += "${policy.name} requires policy $prereq which does not exist!"
            checkUniques(policy, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (branch in ruleset.policyBranches.values)
            if (branch.era !in ruleset.eras)
                lines += "${branch.name} requires era ${branch.era} which does not exist!"


        for (policy in ruleset.policyBranches.values.flatMap { it.policies + it })
            if (policy != ruleset.policies[policy.name])
                lines += "More than one policy with the name ${policy.name} exists!"

        for (reward in ruleset.ruinRewards.values) {
            for (difficulty in reward.excludedDifficulties)
                if (!ruleset.difficulties.containsKey(difficulty))
                    lines += "${reward.name} references difficulty ${difficulty}, which does not exist!"
            checkUniques(reward, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (promotion in ruleset.unitPromotions.values) {
            // These are warning as of 3.17.5 to not break existing mods and give them time to correct, should be upgraded to error in the future
            for (prereq in promotion.prerequisites)
                if (!ruleset.unitPromotions.containsKey(prereq))
                    lines.add("${promotion.name} requires promotion $prereq which does not exist!",
                        RulesetErrorSeverity.Warning)
            for (unitType in promotion.unitTypes)
                if (!ruleset.unitTypes.containsKey(unitType) && (ruleset.unitTypes.isNotEmpty() || !vanillaRuleset.unitTypes.containsKey(unitType)))
                    lines.add("${promotion.name} references unit type $unitType, which does not exist!",
                        RulesetErrorSeverity.Warning)
            checkUniques(promotion, lines, rulesetSpecific, tryFixUnknownUniques)
            checkPromotionCircularReferences(lines)
        }

        for (unitType in ruleset.unitTypes.values) {
            checkUniques(unitType, lines, rulesetSpecific, tryFixUnknownUniques)
        }

        for (victoryType in ruleset.victories.values) {
            for (requiredUnit in victoryType.requiredSpaceshipParts)
                if (!ruleset.units.contains(requiredUnit))
                    lines.add("Victory type ${victoryType.name} requires adding the non-existant unit $requiredUnit to the capital to win!",
                        RulesetErrorSeverity.Warning)
            for (milestone in victoryType.milestoneObjects)
                if (milestone.type == null)
                    lines.add("Victory type ${victoryType.name} has milestone ${milestone.uniqueDescription} that is of an unknown type!",
                        RulesetErrorSeverity.Error)
            for (victory in ruleset.victories.values)
                if (victory.name != victoryType.name && victory.milestones == victoryType.milestones)
                    lines.add("Victory types ${victoryType.name} and ${victory.name} have the same requirements!",
                        RulesetErrorSeverity.Warning)
        }

        for (difficulty in ruleset.difficulties.values) {
            for (unitName in difficulty.aiCityStateBonusStartingUnits + difficulty.aiMajorCivBonusStartingUnits + difficulty.playerBonusStartingUnits)
                if (unitName != Constants.eraSpecificUnit && !ruleset.units.containsKey(unitName))
                    lines += "Difficulty ${difficulty.name} contains starting unit $unitName which does not exist!"
        }

        for (cityStateType in ruleset.cityStateTypes.values) {
            for (unique in cityStateType.allyBonusUniqueMap.getAllUniques() + cityStateType.friendBonusUniqueMap.getAllUniques()){
                val errors = checkUnique(
                    unique,
                    tryFixUnknownUniques,
                    cityStateType,
                    rulesetSpecific
                )
                lines.addAll(errors)
            }
        }

        return lines
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
        fun recursiveCheck(history: LinkedHashSet<Promotion>, promotion: Promotion, level: Int) {
            if (promotion in history) {
                lines.add("Circular Reference in Promotions: ${history.joinToString("→") { it.name }}→${promotion.name}",
                    RulesetErrorSeverity.Warning)
                return
            }
            if (level > 99) return
            history.add(promotion)
            for (prerequisiteName in promotion.prerequisites) {
                val prerequisite = ruleset.unitPromotions[prerequisiteName] ?: continue
                recursiveCheck(history.toCollection(linkedSetOf()), prerequisite, level + 1)
            }
        }
        for (promotion in ruleset.unitPromotions.values) {
            recursiveCheck(linkedSetOf(), promotion, 0)
        }
    }


    fun checkUniques(
        uniqueContainer: IHasUniques,
        lines: RulesetErrorList,
        severityToReport: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (unique in uniqueContainer.uniqueObjects) {
            val errors = checkUnique(
                unique,
                tryFixUnknownUniques,
                uniqueContainer as? INamed,
                severityToReport
            )
            lines.addAll(errors)
        }
    }

    fun checkUnique(
        unique: Unique,
        tryFixUnknownUniques: Boolean,
        namedObj: INamed?,
        severityToReport: UniqueType.UniqueComplianceErrorSeverity
    ): List<RulesetError> {
        val prefix = (if (namedObj is IRulesetObject) "${namedObj.originRuleset}: " else "") +
            (if (namedObj == null) "The" else "${namedObj.name}'s")
        if (unique.type == null) return checkUntypedUnique(unique, tryFixUnknownUniques, prefix)

        val rulesetErrors = RulesetErrorList()

        if (namedObj is IHasUniques && !unique.type.canAcceptUniqueTarget(namedObj.getUniqueTarget()))
            rulesetErrors.add(RulesetError("$prefix unique \"${unique.text}\" is not allowed on its target type", RulesetErrorSeverity.Warning))

        val typeComplianceErrors = unique.type.getComplianceErrors(unique, ruleset)
        for (complianceError in typeComplianceErrors) {
            if (complianceError.errorSeverity <= severityToReport)
                rulesetErrors.add(RulesetError("$prefix unique \"${unique.text}\" contains parameter ${complianceError.parameterName}," +
                        " which does not fit parameter type" +
                        " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !",
                    complianceError.errorSeverity.getRulesetErrorSeverity(severityToReport)
                ))
        }

        for (conditional in unique.conditionals) {
            if (conditional.type == null) {
                rulesetErrors.add(
                    "$prefix unique \"${unique.text}\" contains the conditional \"${conditional.text}\"," +
                            " which is of an unknown type!",
                    RulesetErrorSeverity.Warning
                )
            } else {
                if (conditional.type.targetTypes.none { it.modifierType != UniqueTarget.ModifierType.None })
                    rulesetErrors.add("$prefix unique \"${unique.text}\" contains the conditional \"${conditional.text}\"," +
                        " which is a Unique type not allowed as conditional or trigger.",
                        RulesetErrorSeverity.Warning)

                val conditionalComplianceErrors =
                        conditional.type.getComplianceErrors(conditional, ruleset)
                for (complianceError in conditionalComplianceErrors) {
                    if (complianceError.errorSeverity == severityToReport)
                        rulesetErrors.add(RulesetError( "$prefix unique \"${unique.text}\" contains the conditional \"${conditional.text}\"." +
                                " This contains the parameter ${complianceError.parameterName} which does not fit parameter type" +
                                " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !",
                            complianceError.errorSeverity.getRulesetErrorSeverity(severityToReport)
                        ))
                }
            }
        }


        if (severityToReport != UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific)
        // If we don't filter these messages will be listed twice as this function is called twice on most objects
        // The tests are RulesetInvariant in nature, but RulesetSpecific is called for _all_ objects, invariant is not.
            return rulesetErrors


        val deprecationAnnotation = unique.getDeprecationAnnotation()
        if (deprecationAnnotation != null) {
            val replacementUniqueText = unique.getReplacementText(ruleset)
            val deprecationText =
                    "$prefix unique \"${unique.text}\" is deprecated ${deprecationAnnotation.message}," +
                            if (deprecationAnnotation.replaceWith.expression != "") " replace with \"${replacementUniqueText}\"" else ""
            val severity = if (deprecationAnnotation.level == DeprecationLevel.WARNING)
                RulesetErrorSeverity.WarningOptionsOnly // Not user-visible
            else RulesetErrorSeverity.Warning // User visible

            rulesetErrors.add(deprecationText, severity)
        }

        return rulesetErrors
    }

    private fun checkUntypedUnique(unique: Unique, tryFixUnknownUniques: Boolean, prefix: String ): List<RulesetError> {
        // Malformed conditional is always bad
        if (unique.text.count { it == '<' } != unique.text.count { it == '>' })
            return listOf(RulesetError(
                "$prefix unique \"${unique.text}\" contains mismatched conditional braces!",
                    RulesetErrorSeverity.Warning))

        // Support purely filtering Uniques without actual implementation
        if (isFilteringUniqueAllowed(unique)) return emptyList()
        if (tryFixUnknownUniques) {
            val fixes = tryFixUnknownUnique(unique, prefix)
            if (fixes.isNotEmpty()) return fixes
        }

        if (RulesetCache.modCheckerAllowUntypedUniques) return emptyList()

        return listOf(RulesetError(
            "$prefix unique \"${unique.text}\" not found in Unciv's unique types.",
                RulesetErrorSeverity.WarningOptionsOnly))
    }

    private fun isFilteringUniqueAllowed(unique: Unique): Boolean {
        // Isolate this decision, to allow easy change of approach
        // This says: Must have no conditionals or parameters, and is contained in GlobalUniques
        if (unique.conditionals.isNotEmpty() || unique.params.isNotEmpty()) return false
        return unique.text in ruleset.globalUniques.uniqueMap
    }

    private fun tryFixUnknownUnique(unique: Unique, prefix: String): List<RulesetError> {
        val similarUniques = UniqueType.values().filter {
            getRelativeTextDistance(
                it.placeholderText,
                unique.placeholderText
            ) <= RulesetCache.uniqueMisspellingThreshold
        }
        val equalUniques =
            similarUniques.filter { it.placeholderText == unique.placeholderText }
        return when {
            // This should only ever happen if a bug is or has been introduced that prevents Unique.type from being set for a valid UniqueType, I think.\
            equalUniques.isNotEmpty() -> listOf(RulesetError(
                "$prefix unique \"${unique.text}\" looks like it should be fine, but for some reason isn't recognized.",
                    RulesetErrorSeverity.OK))

            similarUniques.isNotEmpty() -> {
                val text =
                    "$prefix unique \"${unique.text}\" looks like it may be a misspelling of:\n" +
                        similarUniques.joinToString("\n") { uniqueType ->
                            var text = "\"${uniqueType.text}"
                            if (unique.conditionals.isNotEmpty())
                                text += " " + unique.conditionals.joinToString(" ") { "<${it.text}>" }
                            text += "\""
                            if (uniqueType.getDeprecationAnnotation() != null) text += " (Deprecated)"
                            return@joinToString text
                        }.prependIndent("\t")
                listOf(RulesetError(text, RulesetErrorSeverity.OK))
            }
            else -> emptyList()
        }
    }
}
