package com.unciv.models.ruleset.validation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.RulesetFile
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.nation.getContrastRatio
import com.unciv.models.ruleset.nation.getRelativeLuminance
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.models.ruleset.validation.RulesetValidator.Companion.create
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stats
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig
import com.unciv.ui.images.AtlasPreview
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait
import com.unciv.ui.images.PortraitPromotion

/**
 *  Class mananging ruleset validation.
 *
 *  - This class manages code flow and checks that do not require the ruleset to be complete
 *    (it works on extension mods).
 *  - The [BaseRulesetValidator] subclass on the other hand does detailed checks that require the
 *    Ruleset to be complete, that is, all references to other objects should resolve.
 *  - Instantiation **must** occur using the [create] factory, it decides which class to deliver.
 *  - Individual methods per [RulesetObject] type are open, and [BaseRulesetValidator] adds checks
 *    by overriding them.
 *
 *  Repeat to be clear: **All** RulesetInvariant checks should happen here, and **only** those.
 *  [BaseRulesetValidator] adds the RulesetSpecific checks and only those via overrides.
 *
 *  @param ruleset The Ruleset to check - can be a base ruleset, an extension mod,
 *                 or a complex Ruleset assembled from a base ruleset and one or more mods.
 *                 A complex Ruleset should have modOptions.isBaseRuleset ON, so the detailed
 *                 checks are applied.
 *  @param tryFixUnknownUniques If on, the result will include hints on possible misspellings
 *  @property getErrorList This is the public API called from [Ruleset.getErrorList] (and special cases in `DesktopLauncher` / `Suppression`)
 */
open class RulesetValidator protected constructor(
    protected val ruleset: Ruleset,
    private val tryFixUnknownUniques: Boolean
) {
    /** `true` for a [BaseRulesetValidator] instance, `false` for a [RulesetValidator] instance. */
    private val reportRulesetSpecificErrors = ruleset.modOptions.isBaseRuleset

    protected val uniqueValidator = UniqueValidator(ruleset)

    private lateinit var textureNamesCache: AtlasPreview

    companion object {
        fun create(ruleset: Ruleset, tryFixUnknownUniques: Boolean = false): RulesetValidator {
            return if (ruleset.modOptions.isBaseRuleset)
                BaseRulesetValidator(ruleset, tryFixUnknownUniques)
            else
                RulesetValidator(ruleset, tryFixUnknownUniques)
        }
    }

    fun getErrorList(): RulesetErrorList {
        return try {
            getErrorListInternal()
        } catch (e: Exception) {
            RulesetErrorList(ruleset).apply {
                add("Error while validating ruleset ${ruleset.name}: ${e.message}")
                add(e.stackTraceToString())
            }
        }
    }

    private fun getErrorListInternal(): RulesetErrorList {
        val lines = RulesetErrorList(ruleset)

        addModOptionsErrors(lines)
        addGlobalUniqueErrors(lines)

        addUnitErrors(lines)
        addBuildingErrors(lines)
        addSpecialistErrors(lines)
        addResourceErrors(lines)
        addImprovementErrors(lines)
        addTerrainErrors(lines)
        addTechErrors(lines)
        addTechColumnErrors(lines)
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
        addEventErrors(lines)
        addCityStateTypeErrors(lines)

        initTextureNamesCache(lines)

        // Tileset tests - e.g. json configs complete and parseable
        checkTilesetSanity(lines)  // relies on textureNamesCache
        checkCivilopediaText(lines)  // relies on textureNamesCache
        checkFileNames(lines)

        return lines
    }

    //region RulesetObject-specific handlers

    protected open fun addBeliefErrors(lines: RulesetErrorList) {
        for (belief in ruleset.beliefs.values) {
            if (belief.type == BeliefType.Any || belief.type == BeliefType.None)
                lines.add("${belief.name} type is ${belief.type}, which is not allowed!", sourceObject = belief)
            uniqueValidator.checkUniques(belief, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addBuildingErrors(lines: RulesetErrorList) {
        for (building in ruleset.buildings.values) {
            checkBuilding(building, lines)
            uniqueValidator.checkUniques(building, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun checkBuilding(building: Building, lines: RulesetErrorList) {
        if (building.requiredTechs().none() && building.cost == -1 && !building.hasUnique(UniqueType.Unbuildable))
            lines.add(
                "${building.name} is buildable and therefore should either have an explicit cost or reference an existing tech!",
                RulesetErrorSeverity.Warning, building
            )

        if (building.replaces != null && building.uniqueTo == null)
            lines.add("${building.name} should replace ${building.replaces} but does not have uniqueTo assigned!")
    }

    protected open fun addCityStateTypeErrors(lines: RulesetErrorList) {
        for (cityStateType in ruleset.cityStateTypes.values) {
            for (unique in cityStateType.allyBonusUniqueMap.getAllUniques() + cityStateType.friendBonusUniqueMap.getAllUniques()) {
                val errors = uniqueValidator.checkUnique(unique, tryFixUnknownUniques, null, reportRulesetSpecificErrors)
                lines.addAll(errors)
            }
        }
    }

    protected open fun addDifficultyErrors(lines: RulesetErrorList) {
        for (difficulty in ruleset.difficulties.values) {
            if (difficulty.aiBuildingCostModifier < 0 || difficulty.aiBuildingMaintenanceModifier < 0 || difficulty.aiCityGrowthModifier < 0 ||
                difficulty.aiUnhappinessModifier < 0 || difficulty.aiUnitCostModifier < 0 || difficulty.aiUnitMaintenanceModifier < 0 ||
                difficulty.aiUnitSupplyModifier < 0 || difficulty.aiWonderCostModifier < 0 ||
                difficulty.buildingCostModifier < 0 || difficulty.policyCostModifier < 0 || difficulty.researchCostModifier < 0 ||
                difficulty.unhappinessModifier < 0 || difficulty.unitCostModifier < 0)
                lines.add("Difficulty ${difficulty.name} contains one or more negative modifier(s)!", sourceObject = null)
            if (difficulty.turnBarbariansCanEnterPlayerTiles < 0)
                lines.add("Difficulty ${difficulty.name} has a negative turnBarbariansCanEnterPlayerTiles!",
                    RulesetErrorSeverity.Warning, sourceObject = null)
            uniqueValidator.checkUniques(difficulty, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addEraErrors(lines: RulesetErrorList) {
        for (era in ruleset.eras.values) {
            if (era.researchAgreementCost < 0 || era.startingSettlerCount < 0 || era.startingWorkerCount < 0 || 
                era.startingMilitaryUnitCount < 0 || era.startingGold < 0 || era.startingCulture < 0)
                lines.add("Unexpected negative number found while parsing era ${era.name}", sourceObject = era)
            if (era.settlerPopulation <= 0)
                lines.add("Population in cities from settlers must be strictly positive! Found value ${era.settlerPopulation} for era ${era.name}", sourceObject = era)

            if (era.allyBonus.isNotEmpty() || era.friendBonus.isNotEmpty())
                lines.add(
                    "Era ${era.name} contains city-state bonuses. City-state bonuses are now defined in CityStateType.json",
                    RulesetErrorSeverity.WarningOptionsOnly, era
                )

            uniqueValidator.checkUniques(era, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addEventErrors(lines: RulesetErrorList) {
        // An Event is not a IHasUniques, so not suitable as sourceObject
        for (event in ruleset.events.values) {
            for (choice in event.choices) {
                uniqueValidator.checkUniques(choice, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
            }
            uniqueValidator.checkUniques(event, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addGlobalUniqueErrors(lines: RulesetErrorList) {
        uniqueValidator.checkUniques(ruleset.globalUniques, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)

        val fakeUniqueContainer = object : IHasUniques {
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

    protected open fun addImprovementErrors(lines: RulesetErrorList) {
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.replaces != null && improvement.uniqueTo == null)
                lines.add("${improvement.name} should replace ${improvement.replaces} but does not have uniqueTo assigned!")
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

            val hasPillageUnique = improvement.hasUnique(UniqueType.PillageYieldRandom, GameContext.IgnoreConditionals)
                || improvement.hasUnique(UniqueType.PillageYieldFixed, GameContext.IgnoreConditionals)
            if (hasPillageUnique && improvement.hasUnique(UniqueType.Unpillagable, GameContext.IgnoreConditionals)) {
                lines.add(
                    "${improvement.name} has both an `Unpillagable` unique type and a `PillageYieldRandom` or `PillageYieldFixed` unique type!",
                    RulesetErrorSeverity.Warning, improvement
                )
            }

            uniqueValidator.checkUniques(improvement, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addModOptionsErrors(lines: RulesetErrorList) {
        // Basic Unique validation (type, target, parameters) should always run.
        // Using reportRulesetSpecificErrors=true as ModOptions never should use Uniques depending on objects from a base ruleset anyway.
        uniqueValidator.checkUniques(ruleset.modOptions, lines, reportRulesetSpecificErrors = true, tryFixUnknownUniques)

        //TODO: More thorough checks. Here I picked just those where bad values might endanger stability.
        val constants = ruleset.modOptions.constants
        if (constants.cityExpandRange !in 1..100)
            lines.add("Invalid ModConstant 'cityExpandRange'.", sourceObject = null)
        if (constants.cityWorkRange !in 1..100)
            lines.add("Invalid ModConstant 'cityWorkRange'.", sourceObject = null)
        if (constants.minimalCityDistance < 1)
            lines.add("Invalid ModConstant 'minimalCityDistance'.", sourceObject = null)
        if (constants.minimalCityDistanceOnDifferentContinents < 1)
            lines.add("Invalid ModConstant 'minimalCityDistanceOnDifferentContinents'.", sourceObject = null)
        if (constants.baseCityBombardRange < 1)
            lines.add("Invalid ModConstant 'baseCityBombardRange'.", sourceObject = null)

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
    }

    protected open fun addNationErrors(lines: RulesetErrorList) {
        for (nation in ruleset.nations.values) {
            checkNation(nation, lines)
            uniqueValidator.checkUniques(nation, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun checkNation(nation: Nation, lines: RulesetErrorList) {
        if (nation.cities.isEmpty() && !nation.isSpectator && !nation.isBarbarian) {
            lines.add("${nation.name} can settle cities, but has no city names!", sourceObject = nation)
        }

        fun isColorFaulty(rgb: List<Int>?) = when {
            rgb == null -> false
            rgb.size != 3 -> true
            rgb.any { it !in 0..255 } -> true
            else -> false
        }
        val badInner = isColorFaulty(nation.innerColor)
        val badOuter = isColorFaulty(nation.outerColor)
        if (badInner)
            lines.add("${nation.name}'s innerColor is not an array of three integers in the 0..255 range", sourceObject = nation)
        if (badOuter)
            lines.add("${nation.name}'s outerColor is not an array of three integers in the 0..255 range", sourceObject = nation)

        if (!badInner && !badOuter)
            checkContrasts(nation.getInnerColor(), nation.getOuterColor(), nation, lines)
    }

    protected open fun addPersonalityErrors(lines: RulesetErrorList) {
        for (personality in ruleset.personalities.values) {
            if (personality.uniques.isNotEmpty())
                lines.add("Personality Uniques are not supported", RulesetErrorSeverity.Warning, personality)
        }
    }

    protected open fun addPolicyErrors(lines: RulesetErrorList) {
        for (policy in ruleset.policies.values) {
            uniqueValidator.checkUniques(policy, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addPromotionErrors(lines: RulesetErrorList) {
        for (promotion in ruleset.unitPromotions.values) {
            uniqueValidator.checkUniques(promotion, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
            checkContrasts(promotion.innerColorObject ?: PortraitPromotion.defaultInnerColor,
                promotion.outerColorObject ?: PortraitPromotion.defaultOuterColor, promotion, lines)
            checkPromotion(promotion, lines)
        }
    }

    protected open fun checkPromotion(promotion: Promotion, lines: RulesetErrorList) {
        if (promotion.row < -1) lines.add("Promotion ${promotion.name} has invalid row value: ${promotion.row}", sourceObject = promotion)
        if (promotion.column < 0) lines.add("Promotion ${promotion.name} has invalid column value: ${promotion.column}", sourceObject = promotion)
        if (promotion.row == -1) return
        for (otherPromotion in ruleset.unitPromotions.values)
            if (promotion != otherPromotion && promotion.column == otherPromotion.column && promotion.row == otherPromotion.row)
                lines.add("Promotions ${promotion.name} and ${otherPromotion.name} have the same position: ${promotion.row}/${promotion.column}", sourceObject = promotion)
    }

    protected open fun addResourceErrors(lines: RulesetErrorList) {
        for (resource in ruleset.tileResources.values) {
            uniqueValidator.checkUniques(resource, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addRuinsErrors(lines: RulesetErrorList) {
        for (reward in ruleset.ruinRewards.values) {
            @Suppress("KotlinConstantConditions") // data is read from json, so any assumptions may be wrong
            if (reward.weight < 0) lines.add("${reward.name} has a negative weight, which is not allowed!", sourceObject = reward)
            uniqueValidator.checkUniques(reward, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addSpecialistErrors(lines: RulesetErrorList) {}

    protected open fun addSpeedErrors(lines: RulesetErrorList) {
        for (speed in ruleset.speeds.values) {
            if (speed.modifier < 0f || speed.barbarianModifier < 0f || speed.cultureCostModifier < 0f || speed.faithCostModifier < 0f ||
                speed.goldCostModifier < 0f || speed.goldGiftModifier < 0f || speed.goldenAgeLengthModifier < 0f ||
                speed.improvementBuildLengthModifier < 0f || speed.productionCostModifier < 0f || speed.scienceCostModifier < 0f)
                lines.add("One or more negative speed modifier(s) for game speed ${speed.name}", sourceObject = speed)
            if (speed.dealDuration < 1 || speed.peaceDealDuration < 1)
                lines.add("Deal durations must be positive", sourceObject = speed)
            if (speed.religiousPressureAdjacentCity < 0)
                lines.add("'religiousPressureAdjacentCity' must not be negative", sourceObject = speed)
            if (speed.yearsPerTurn.isEmpty())
                lines.add("Empty turn increment list for game speed ${speed.name}", sourceObject = speed)
            var lastTurn = 0
            for ((yearInterval, untilTurn) in speed.yearsPerTurn) {
                if (yearInterval <= 0f)
                    lines.add("Negative year interval $yearInterval in turn increment list", sourceObject = speed)
                if (untilTurn <= lastTurn)
                    lines.add("The 'untilTurn' field in the turn increment list must be monotonously increasing, but $untilTurn is <= $lastTurn", sourceObject = speed)
                lastTurn = untilTurn
            }
            uniqueValidator.checkUniques(speed, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addTechErrors(lines: RulesetErrorList) {
        for (tech in ruleset.technologies.values) {
            if (tech.row < 1) lines.add("Tech ${tech.name} has a row value below 1: ${tech.row}", sourceObject = tech)
            uniqueValidator.checkUniques(tech, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addTechColumnErrors(lines: RulesetErrorList) {
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
    }

    protected open fun addTerrainErrors(lines: RulesetErrorList) {
        for (terrain in ruleset.terrains.values) {
            uniqueValidator.checkUniques(terrain, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addUnitErrors(lines: RulesetErrorList) {
        for (unit in ruleset.units.values) {
            checkUnit(unit, lines)
            uniqueValidator.checkUniques(unit, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun checkUnit(unit: BaseUnit, lines: RulesetErrorList) {
        for (upgradesTo in unit.getUpgradeUnits(GameContext.IgnoreConditionals)) {
            if (upgradesTo == unit.name || upgradesTo == unit.replaces)
                lines.add("${unit.name} upgrades to itself!", sourceObject = unit)
        }

        if (unit.replaces != null && unit.uniqueTo == null)
            lines.add("${unit.name} should replace ${unit.replaces} but does not have uniqueTo assigned!")

        if (unit.isMilitary && unit.strength == 0)  // Should only match ranged units with 0 strength
            lines.add("${unit.name} is a military unit but has no assigned strength!", sourceObject = unit)
    }

    protected open fun addUnitTypeErrors(lines: RulesetErrorList) {
        val unitMovementTypes = UnitMovementType.entries.map { it.name }.toSet()
        for (unitType in ruleset.unitTypes.values) {
            if (unitType.movementType !in unitMovementTypes)
                lines.add("Unit type ${unitType.name} has an invalid movement type ${unitType.movementType}", sourceObject = unitType)
            uniqueValidator.checkUniques(unitType, lines, reportRulesetSpecificErrors, tryFixUnknownUniques)
        }
    }

    protected open fun addVictoryTypeErrors(lines: RulesetErrorList) {
        // Victory and Milestone aren't IHasUniques and are unsuitable as sourceObject
        for (victoryType in ruleset.victories.values) {
            for (milestone in victoryType.milestoneObjects) {
                if (milestone.type == null)
                    lines.add(
                        "Victory type ${victoryType.name} has milestone \"${milestone.uniqueDescription}\" that is of an unknown type!",
                        RulesetErrorSeverity.Error, sourceObject = null
                    )
            }

            for (otherVictory in ruleset.victories.values)
                if (otherVictory.name > victoryType.name && otherVictory.milestones == victoryType.milestones)
                    lines.add(
                        "Victory types ${victoryType.name} and ${otherVictory.name} have the same requirements!",
                        RulesetErrorSeverity.Warning, sourceObject = null
                    )
        }
    }

    //endregion
    //region General helpers

    protected fun getPossibleMisspellings(originalText: String, possibleMisspellings: List<String>): List<String> =
        possibleMisspellings.filter {
            getRelativeTextDistance(
                it,
                originalText
            ) <= RulesetCache.uniqueMisspellingThreshold
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

    private data class SuggestedColors(val innerColor: Color, val outerColor: Color)

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

    //endregion
    //region File and Texture related checks

    private fun initTextureNamesCache(lines: RulesetErrorList) {
        if (!::textureNamesCache.isInitialized)
            textureNamesCache = AtlasPreview(ruleset, lines)  // This logs Atlas list errors, and if these exist, scans for invalid png's
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

    /* This is public because `FormattedLine` does its own checking and needs the textureNamesCache test */
    fun uncachedImageExists(name: String) = textureNamesCache.imageExists(name)

    //endregion

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

}
