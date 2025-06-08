package com.unciv.models.ruleset.validation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.nation.getContrastRatio
import com.unciv.models.ruleset.nation.getRelativeLuminance
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.*
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stats
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig
import com.unciv.ui.images.AtlasPreview
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait
import com.unciv.ui.images.PortraitPromotion

class RulesetValidator(val ruleset: Ruleset, private val tryFixUnknownUniques: Boolean = false) {

    private val uniqueValidator = UniqueValidator(ruleset)

    private lateinit var textureNamesCache: AtlasPreview

    fun getErrorList(): RulesetErrorList {
        try {
            // When no base ruleset is loaded - references cannot be checked
            if (!ruleset.modOptions.isBaseRuleset) return getNonBaseRulesetErrorList()

            return getBaseRulesetErrorList()
        } catch (e: Exception) {
            return RulesetErrorList(ruleset).apply { 
                add("Error while validating ruleset ${ruleset.name}: ${e.message}")
            }
        }
    }

    private fun getNonBaseRulesetErrorList(): RulesetErrorList {
        val lines = RulesetErrorList(ruleset)

        // When not checking the entire ruleset, we can only really detect ruleset-invariant errors in uniques
        addModOptionsErrors(lines)
        
        addGlobalUniqueErrors(lines, false)
        
        addUnitErrorsRulesetInvariant(lines)
        addTechErrorsRulesetInvariant(lines)
        addTechColumnErrorsRulesetInvariant(lines)
        addBuildingErrorsRulesetInvariant(lines)
        addNationErrorsRulesetInvariant(lines)
        addPromotionErrorsRulesetInvariant(lines)
        addResourceErrorsRulesetInvariant(lines)

        if (!::textureNamesCache.isInitialized)
            textureNamesCache = AtlasPreview(ruleset, lines)  // This logs Atlas list errors, and if these exist, scans for invalid png's

        // Tileset tests - e.g. json configs complete and parseable
        checkTilesetSanity(lines)  // relies on textureNamesCache
        checkCivilopediaText(lines)  // relies on textureNamesCache
        checkFileNames(lines)

        return lines
    }


    private fun getBaseRulesetErrorList(): RulesetErrorList {

        uniqueValidator.populateFilteringUniqueHashsets()

        val lines = RulesetErrorList(ruleset)
        addModOptionsErrors(lines)
        addGlobalUniqueErrors(lines, true)

        addUnitErrorsBaseRuleset(lines)
        addBuildingErrors(lines)
        addSpecialistErrors(lines)
        addResourceErrors(lines)
        addImprovementErrors(lines)
        addTerrainErrors(lines)
        addTechErrors(lines)
        addTechColumnErrorsRulesetInvariant(lines)
        addEraErrors(lines)
        addSpeedErrors(lines)
        addPersonalityErrors(lines)
        addBeliefErrors(lines)
        addNationErrors(lines)
        addPolicyErrors(lines)
        addRuinsErrors(lines)
        addPromotionErrors(lines)
        addUnitTypeErrors(lines)
        addVictoryTypeErrors(lines)
        addDifficultyErrors(lines)
        addEventErrors(lines, tryFixUnknownUniques)
        addCityStateTypeErrors(tryFixUnknownUniques, lines)

        if (!::textureNamesCache.isInitialized)
            textureNamesCache = AtlasPreview(ruleset, lines)  // This logs Atlas list errors, and if these exist, scans for invalid png's

        // Tileset tests - e.g. json configs complete and parseable
        // Check for mod or Civ_V_GnK to avoid running the same test twice (~200ms for the builtin assets)
        if (ruleset.folderLocation != null || ruleset.name == BaseRuleset.Civ_V_GnK.fullName) {
            checkTilesetSanity(lines)
        }

        checkCivilopediaText(lines)
        checkFileNames(lines)

        return lines
    }


    private fun getPossibleMisspellings(originalText: String, possibleMisspellings: List<String>): List<String> =
        possibleMisspellings.filter {
            getRelativeTextDistance(
                it,
                originalText
            ) <= RulesetCache.uniqueMisspellingThreshold
        }
    
    private fun checkFileNames(lines: RulesetErrorList) {
        val folder = ruleset.folderLocation ?: return

        checkMisplacedJsonFiles(folder, lines)
        checkMisspelledFolders(folder, lines)
        checkImagesFolders(folder, lines)
        checkUnknownJsonFilenames(folder, lines)
        warnSplitAtlasesPerformanceDegradation(folder, lines)
    }

    private fun checkMisspelledFolders(
        folder: FileHandle,
        lines: RulesetErrorList
    ) {
        val knownFolderNames = listOf("jsons", "maps", "sounds", "Images", "fonts")
        for (child in folder.list()) {
            if (!child.isDirectory || child.name() in knownFolderNames) continue

            val possibleMisspellings = getPossibleMisspellings(child.name(), knownFolderNames)
            if (possibleMisspellings.isNotEmpty())
                lines.add(
                    "Folder \"${child.name()}\" is probably a misspelling of " + possibleMisspellings.joinToString("/"),
                    RulesetErrorSeverity.OK
                )
        }
    }

    private fun checkMisplacedJsonFiles(
        folder: FileHandle,
        lines: RulesetErrorList
    ) {
        for (child in folder.list()) {
            if (child.name().endsWith("json") && !child.name().startsWith("Atlas"))
                lines.add("File ${child.name()} is located in the root folder - it should be moved to a 'jsons' folder")
        }
    }

    private fun checkImagesFolders(
        folder: FileHandle,
        lines: RulesetErrorList
    ) {
        val knownImageFolders =
            Portrait.Type.entries.map { it.directory }.flatMap { listOf(it + "Icons", it + "Portraits") } +
                    listOf("CityStateIcons", "PolicyBranchIcons", "PolicyIcons", "OtherIcons", "EmojiIcons", "StatIcons", "TileIcons", "TileSets")  // Not portrait-able (yet?)

        val imageFolders = folder.list().filter { it.name().startsWith("Images") }
        for (imageFolder in imageFolders) {
            for (child in imageFolder.list()) {
                if (!child.isDirectory) {
                    lines.add(
                        "File \"$imageFolder/${child.name()}\" is misplaced - Images folders should not contain any files directly - only subfolders",
                        RulesetErrorSeverity.OK
                    )
                } else if (child.name() !in knownImageFolders) {
                    val possibleMisspellings = getPossibleMisspellings(child.name(), knownImageFolders)
                    if (possibleMisspellings.isNotEmpty())
                        lines.add(
                            "Folder \"$imageFolder/${child.name()}\" is probably a misspelling of " +
                                    possibleMisspellings.joinToString("/"),
                            RulesetErrorSeverity.OK
                        )
                }
            }
        }
    }

    private fun checkUnknownJsonFilenames(
        folder: FileHandle,
        lines: RulesetErrorList
    ) {
        val jsonFolder = folder.child("jsons")
        if (!jsonFolder.exists()) return

        for (file in jsonFolder.list("json")) {
            if (file.name() in RulesetFile.entries.map { it.filename }) continue

            var text = "File ${file.name()} is in the jsons folder but is not a recognized ruleset file"
            val possibleMisspellings = getPossibleMisspellings(file.name(), RulesetFile.entries.map { it.filename })
            if (possibleMisspellings.isNotEmpty())
                text += "\nPossible misspelling of: " + possibleMisspellings.joinToString("/")
            lines.add(text, RulesetErrorSeverity.OK)
        }
    }

    private fun warnSplitAtlasesPerformanceDegradation(
        folder: FileHandle,
        lines: RulesetErrorList
    ) {
        if (folder.child("game2.png").exists()){
            lines.add(
                "Your images are being generated into multiple atlas files - this can cause lag for players. " +
                        "Please consult https://yairm210.github.io/Unciv/Modders/Images-and-Audio/#rendering-performance on how to improve rendering performance.",
                RulesetErrorSeverity.WarningOptionsOnly, sourceObject = null
            )
        }
    }


    private fun addModOptionsErrors(lines: RulesetErrorList) {
        // Basic Unique validation (type, target, parameters) should always run.
        // Using reportRulesetSpecificErrors=true as ModOptions never should use Uniques depending on objects from a base ruleset anyway.
        uniqueValidator.checkUniques(ruleset.modOptions, lines, reportRulesetSpecificErrors = true, tryFixUnknownUniques)

        if (ruleset.name.isBlank()) return // The rest of these tests don't make sense for combined rulesets

        val audioVisualUniqueTypes = setOf(
            UniqueType.ModIsAudioVisual,
            UniqueType.ModIsAudioVisualOnly,
            UniqueType.ModIsNotAudioVisual
        )
        // modOptions is a valid sourceObject, but unnecessary
        if (ruleset.modOptions.uniqueObjects.count { it.type in audioVisualUniqueTypes } > 1)
            lines.add("A mod should only specify one of the 'can/should/cannot be used as permanent audiovisual mod' options.", sourceObject = null)

        val mapSelectUniques = ruleset.modOptions.getMatchingUniques(UniqueType.ModMapPreselection).toList()
        if (mapSelectUniques.size > 1)
            lines.add("Specifying more than one map as preselection makes no sense", RulesetErrorSeverity.WarningOptionsOnly, sourceObject = null)
        if (mapSelectUniques.isNotEmpty()) {
            val mapsFolder = UncivGame.Current.files.getModFolder(ruleset.name).child("maps")
            if (mapsFolder.exists()) {
                val maps = mapsFolder.list().map { it.name().lowercase() }
                for (unique in mapSelectUniques) {
                    if (unique.params[0].lowercase() in maps) continue
                    lines.add("Mod names map '${unique.params[0]}' as preselection, which does not exist.", RulesetErrorSeverity.WarningOptionsOnly, sourceObject = null)
                }
            } else {
                lines.add("Mod option for map preselection exists but Mod has no 'maps' folder.", RulesetErrorSeverity.WarningOptionsOnly, sourceObject = null)
            }
        }

        if (!ruleset.modOptions.isBaseRuleset) return
        for (unique in ruleset.modOptions.getMatchingUniques(UniqueType.ModRequires)) {
            lines.add("Mod option '${unique.text}' is invalid for a base ruleset.", sourceObject = null)
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
        // A Difficulty is not a IHasUniques, so not suitable as sourceObject
        for (difficulty in ruleset.difficulties.values) {
            for (unitName in difficulty.aiCityStateBonusStartingUnits + difficulty.aiMajorCivBonusStartingUnits + difficulty.playerBonusStartingUnits)
                if (unitName != Constants.eraSpecificUnit && !ruleset.units.containsKey(unitName))
                    lines.add("Difficulty ${difficulty.name} contains starting unit $unitName which does not exist!", sourceObject = null)
        }
    }

    private fun addEventErrors(lines: RulesetErrorList,
                               tryFixUnknownUniques: Boolean) {
        // An Event is not a IHasUniques, so not suitable as sourceObject
        for (event in ruleset.events.values) {
            for (choice in event.choices) {
                uniqueValidator.checkUniques(choice, lines, true, tryFixUnknownUniques)
            }
            uniqueValidator.checkUniques(event, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addVictoryTypeErrors(lines: RulesetErrorList) {
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

    private val unitMovementTypes = UnitMovementType.entries.map { it.name }.toSet()
    private fun addUnitTypeErrors(lines: RulesetErrorList) {
        for (unitType in ruleset.unitTypes.values) {
            if (unitType.movementType !in unitMovementTypes)
                lines.add("Unit type ${unitType.name} has an invalid movement type ${unitType.movementType}", sourceObject = unitType)
            uniqueValidator.checkUniques(unitType, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addPromotionErrors(lines: RulesetErrorList) {
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

    private fun addRuinsErrors(lines: RulesetErrorList) {
        for (reward in ruleset.ruinRewards.values) {
            @Suppress("KotlinConstantConditions") // data is read from json, so any assumptions may be wrong
            if (reward.weight < 0) lines.add("${reward.name} has a negative weight, which is not allowed!", sourceObject = reward)
            for (difficulty in reward.excludedDifficulties)
                if (!ruleset.difficulties.containsKey(difficulty))
                    lines.add("${reward.name} references difficulty ${difficulty}, which does not exist!", sourceObject = reward)
            uniqueValidator.checkUniques(reward, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addPolicyErrors(lines: RulesetErrorList) {
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

    private fun addNationErrors(lines: RulesetErrorList) {
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

    private fun addBeliefErrors(lines: RulesetErrorList) {
        for (belief in ruleset.beliefs.values) {
            if (belief.type == BeliefType.Any || belief.type == BeliefType.None)
                lines.add("${belief.name} type is ${belief.type}, which is not allowed!", sourceObject = belief)
            uniqueValidator.checkUniques(belief, lines, true, tryFixUnknownUniques)
        }
    }

    private fun addSpeedErrors(lines: RulesetErrorList) {
        for (speed in ruleset.speeds.values) {
            if (speed.modifier < 0f)
                lines.add("Negative speed modifier for game speed ${speed.name}", sourceObject = speed)
            if (speed.yearsPerTurn.isEmpty())
                lines.add("Empty turn increment list for game speed ${speed.name}", sourceObject = speed)
        }
    }
    
    private fun addPersonalityErrors(lines: RulesetErrorList) {
        for (personality in ruleset.personalities.values) {
            if (personality.preferredVictoryType != Constants.neutralVictoryType
                    && personality.preferredVictoryType !in ruleset.victories) {
                lines.add("Preferred victory type ${personality.preferredVictoryType} does not exist in ruleset",
                    RulesetErrorSeverity.Warning, sourceObject = personality,)
            }
        }
    }

    private fun addEraErrors(lines: RulesetErrorList) {
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

    private fun addTechErrors(lines: RulesetErrorList) {
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

    private fun addTerrainErrors(lines: RulesetErrorList) {
        if (ruleset.terrains.values.none { it.type == TerrainType.Land && !it.impassable && !it.hasUnique(UniqueType.NoNaturalGeneration) })
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

    private fun addImprovementErrors(lines: RulesetErrorList) {
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

    private fun addResourceErrors(lines: RulesetErrorList) {
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

    private fun addSpecialistErrors(lines: RulesetErrorList) {
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

    private fun addBuildingErrors(lines: RulesetErrorList) {
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
    
    private fun addGlobalUniqueErrors(lines: RulesetErrorList, reportRulesetSpecificErrors: Boolean) {
        uniqueValidator.checkUniques(ruleset.globalUniques, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)

        val fakeUniqueContainer = object : IHasUniques{
            override var uniques: ArrayList<String> = ArrayList()
            override val uniqueObjects: List<Unique> = emptyList()
            override val uniqueMap: UniqueMap = UniqueMap.EMPTY
            override fun getUniqueTarget() = UniqueTarget.Unit
            override var name = "Global unit uniques"
        }
        
        for (uniqueText in ruleset.globalUniques.unitUniques) {
            val unique = Unique(uniqueText)
            val errors = uniqueValidator.checkUnique(
                unique,
                tryFixUnknownUniques,
                fakeUniqueContainer,
                reportRulesetSpecificErrors
            )
            lines.addAll(errors)
        }
        
    }


    private fun addUnitErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (unit in ruleset.units.values) {
            checkUnitRulesetInvariant(unit, lines)
            uniqueValidator.checkUniques(unit, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addUnitErrorsBaseRuleset(lines: RulesetErrorList) {
        if (ruleset.units.values.none { it.isCityFounder() })
            lines.add("No city-founding units in ruleset!", sourceObject = null)

        for (unit in ruleset.units.values) {
            checkUnitRulesetInvariant(unit, lines)
            checkUnitRulesetSpecific(unit, lines)
            uniqueValidator.checkUniques(unit, lines, true, tryFixUnknownUniques)
            checkUniqueToMisspelling(unit, unit.uniqueTo, lines)
        }
    }

    private fun addResourceErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (resource in ruleset.tileResources.values) {
            uniqueValidator.checkUniques(resource, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addPromotionErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (promotion in ruleset.unitPromotions.values) {
            uniqueValidator.checkUniques(promotion, lines, false, tryFixUnknownUniques)
            checkContrasts(promotion.innerColorObject ?: PortraitPromotion.defaultInnerColor,
                promotion.outerColorObject ?: PortraitPromotion.defaultOuterColor, promotion, lines)
            addPromotionErrorRulesetInvariant(promotion, lines)
        }
    }

    private fun addPromotionErrorRulesetInvariant(promotion: Promotion, lines: RulesetErrorList) {
        if (promotion.row < -1) lines.add("Promotion ${promotion.name} has invalid row value: ${promotion.row}", sourceObject = promotion)
        if (promotion.column < 0) lines.add("Promotion ${promotion.name} has invalid column value: ${promotion.column}", sourceObject = promotion)
        if (promotion.row == -1) return
        for (otherPromotion in ruleset.unitPromotions.values)
            if (promotion != otherPromotion && promotion.column == otherPromotion.column && promotion.row == otherPromotion.row)
                lines.add("Promotions ${promotion.name} and ${otherPromotion.name} have the same position: ${promotion.row}/${promotion.column}", sourceObject = promotion)
    }

    private fun addNationErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (nation in ruleset.nations.values) {
            addNationErrorRulesetInvariant(nation, lines)
            uniqueValidator.checkUniques(nation, lines, false, tryFixUnknownUniques)
        }
    }

    private fun addNationErrorRulesetInvariant(nation: Nation, lines: RulesetErrorList) {
        if (nation.cities.isEmpty() && !nation.isSpectator && !nation.isBarbarian) {
            lines.add("${nation.name} can settle cities, but has no city names!", sourceObject = nation)
        }

        checkContrasts(nation.getInnerColor(), nation.getOuterColor(), nation, lines)
    }
    
    

    private fun checkContrasts(
        innerColor: Color,
        outerColor: Color,
        nation: RulesetObject,
        lines: RulesetErrorList
    ) {
        val constrastRatio = getContrastRatio(innerColor, outerColor)
        if (constrastRatio < 3) { // https://www.w3.org/TR/WCAG20/#visual-audio-contrast-contrast
            val (newInnerColor, newOuterColor) = getSuggestedColors(innerColor, outerColor)

            var text = "${nation.name}'s colors do not contrast enough - it is unreadable!"
            text += "\nSuggested colors: "
            text += "\n\t\t\"outerColor\": [${(newOuterColor.r * 255).toInt()}, ${(newOuterColor.g * 255).toInt()}, ${(newOuterColor.b * 255).toInt()}],"
            text += "\n\t\t\"innerColor\": [${(newInnerColor.r * 255).toInt()}, ${(newInnerColor.g * 255).toInt()}, ${(newInnerColor.b * 255).toInt()}],"

            lines.add(text, RulesetErrorSeverity.WarningOptionsOnly, nation)
        }
    }


    data class SuggestedColors(val innerColor: Color, val outerColor: Color)

    private fun getSuggestedColors(innerColor: Color, outerColor: Color): SuggestedColors {
        val innerColorLuminance = getRelativeLuminance(innerColor)
        val outerColorLuminance = getRelativeLuminance(outerColor)

        val innerLerpColor: Color
        val outerLerpColor: Color

        if (innerColorLuminance > outerColorLuminance) { // inner is brighter
            innerLerpColor = Color.WHITE
            outerLerpColor = ImageGetter.CHARCOAL
        } else {
            innerLerpColor = ImageGetter.CHARCOAL
            outerLerpColor = Color.WHITE
        }


        for (i in 1..10) {
            val newInnerColor = innerColor.cpy().lerp(innerLerpColor, 0.05f * i)
            val newOuterColor = outerColor.cpy().lerp(outerLerpColor, 0.05f * i)

            if (getContrastRatio(newInnerColor, newOuterColor) > 3) return SuggestedColors(newInnerColor, newOuterColor)
        }
        throw Exception("Error getting suggested colors")
    }

    private fun addBuildingErrorsRulesetInvariant(lines: RulesetErrorList) {
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
                RulesetErrorSeverity.Warning, building
            )

        for (gpp in building.greatPersonPoints)
            if (gpp.key !in ruleset.units)
                lines.add(
                    "Building ${building.name} has greatPersonPoints for ${gpp.key}, which is not a unit in the ruleset!",
                    RulesetErrorSeverity.Warning, building
                )

        if (building.replaces != null && building.uniqueTo == null)
            lines.add("${building.name} should replace ${building.replaces} but does not have uniqueTo assigned!")
    }

    private fun addTechColumnErrorsRulesetInvariant(lines: RulesetErrorList) {
        // TechColumn is not a IHasUniques and unsuitable as sourceObject
        for (techColumn in ruleset.techColumns) {
            if (techColumn.columnNumber < 0)
                lines.add("Tech Column number ${techColumn.columnNumber} is negative", sourceObject = null)

            val buildingsWithoutAssignedCost = ruleset.buildings.values.filter { building ->
                building.cost == -1 && techColumn.techs.map { it.name }.contains(building.requiredTech) }.toList()


            val nonWondersWithoutAssignedCost = buildingsWithoutAssignedCost.filter { !it.isAnyWonder() }
            if (techColumn.buildingCost == -1 && nonWondersWithoutAssignedCost.any())
                lines.add(
                    "Tech Column number ${techColumn.columnNumber} has no explicit building cost leaving "+nonWondersWithoutAssignedCost.joinToString()+" unassigned",
                    RulesetErrorSeverity.Warning, sourceObject = null
                )

            val wondersWithoutAssignedCost = buildingsWithoutAssignedCost.filter { it.isAnyWonder() }
            if (techColumn.wonderCost == -1 && wondersWithoutAssignedCost.any())
                lines.add(
                    "Tech Column number ${techColumn.columnNumber} has no explicit wonder cost leaving "+wondersWithoutAssignedCost.joinToString()+" unassigned",
                    RulesetErrorSeverity.Warning, sourceObject = null
                )
        }

        for (tech in ruleset.technologies.values) {
            for (otherTech in ruleset.technologies.values) {
                if (tech != otherTech && otherTech.column?.columnNumber == tech.column?.columnNumber && otherTech.row == tech.row)
                    lines.add("${tech.name} is in the same row and column as ${otherTech.name}!", sourceObject = tech)
            }
        }
    }

    private fun addTechErrorsRulesetInvariant(lines: RulesetErrorList) {
        for (tech in ruleset.technologies.values) {
            if (tech.row < 1) lines.add("Tech ${tech.name} has a row value below 1: ${tech.row}", sourceObject = tech)
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
                lines.add("${unit.name} upgrades to itself!", sourceObject = unit)
        }
        
        if (unit.replaces != null && unit.uniqueTo == null)
            lines.add("${unit.name} should replace ${unit.replaces} but does not have uniqueTo assigned!")
        
        if (unit.isMilitary && unit.strength == 0)  // Should only match ranged units with 0 strength
            lines.add("${unit.name} is a military unit but has no assigned strength!", sourceObject = unit)
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

    /** Collects all RulesetSpecific checks for a BaseUnit */
    private fun checkUnitRulesetSpecific(unit: BaseUnit, lines: RulesetErrorList) {
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
        // If running from a jar *and* checking a builtin ruleset, skip this check.
        // - We can't list() the jsons, and the unit test before release is sufficient, the tileset config can't have changed since then.
        if (ruleset.folderLocation == null && this::class.java.`package`?.specificationVersion != null)
            return

        val tilesetConfigFolder = (ruleset.folderLocation ?: Gdx.files.internal("")).child("jsons/TileSets")
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
                lines.add("Tileset config '${file.name()}' cannot be loaded (${ex.cause?.message})", RulesetErrorSeverity.Warning, sourceObject = null)
            }
        }

        // Folder should not contain subdirectories, non-json files, or be empty
        if (folderContentBad)
            lines.add("The Mod tileset config folder contains non-json files or subdirectories", RulesetErrorSeverity.Warning, sourceObject = null)
        if (configTilesets.isEmpty())
            lines.add("The Mod tileset config folder contains no json files", RulesetErrorSeverity.Warning, sourceObject = null)

        // There should be atlas images corresponding to each json name
        val atlasTilesets = getTilesetNamesFromAtlases()
        val configOnlyTilesets = configTilesets - atlasTilesets
        if (configOnlyTilesets.isNotEmpty())
            lines.add("Mod has no graphics for configured tilesets: ${configOnlyTilesets.joinToString()}", RulesetErrorSeverity.Warning, sourceObject = null)

        // For all atlas images matching "TileSets/*" there should be a json
        val atlasOnlyTilesets = atlasTilesets - configTilesets
        if (atlasOnlyTilesets.isNotEmpty())
            lines.add("Mod has no configuration for tileset graphics: ${atlasOnlyTilesets.joinToString()}", RulesetErrorSeverity.Warning, sourceObject = null)

        // All fallbacks should exist (default added because TileSetCache is not loaded when running as unit test)
        val unknownFallbacks = allFallbacks - TileSetCache.keys - Constants.defaultFallbackTileset
        if (unknownFallbacks.isNotEmpty())
            lines.add("Fallback tileset invalid: ${unknownFallbacks.joinToString()}", RulesetErrorSeverity.Warning, sourceObject = null)
    }

    private fun getTilesetNamesFromAtlases() =
        textureNamesCache
            .filter { it.startsWith("TileSets/") && !it.contains("/Units/") }
            .map { it.split("/")[1] }
            .toSet()

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

    private fun checkCivilopediaText(lines: RulesetErrorList) {
        for (sourceObject in ruleset.allICivilopediaText()) {
            for ((index, line) in sourceObject.civilopediaText.withIndex()) {
                for (error in line.unsupportedReasons(this)) {
                    val nameText = (sourceObject as? INamed)?.name?.plus("'s ") ?: ""
                    val text = "(${sourceObject::class.java.simpleName}) ${nameText}civilopediaText line ${index + 1}: $error"
                    lines.add(text, RulesetErrorSeverity.WarningOptionsOnly, sourceObject as? IRulesetObject, null)
                }
            }
        }
    }

    fun uncachedImageExists(name: String): Boolean {
        if (ruleset.folderLocation == null) return false // Can't check in this case
        return textureNamesCache.imageExists(name)
    }
}
