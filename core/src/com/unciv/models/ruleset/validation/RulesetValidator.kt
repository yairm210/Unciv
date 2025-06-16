package com.unciv.models.ruleset.validation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
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
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.validation.RulesetValidator.Companion.create
import com.unciv.models.stats.INamed
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig
import com.unciv.ui.images.AtlasPreview
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait

/**
 *  Common interface for base ruleset or non-base ruleset validation.
 *
 *  There are two implementations:
 *  - [BaseRulesetValidator] is repsonsible for complete Rulesets (base rulesets or combined complex rulesets)
 *  - [NonBaseRulesetValidator] is responsible for incomplete Rulesets - extension mods
 *  Each containins all code that is specific to that role, while this parent class only contains
 *  code and data common to both.
 *
 *  @property create Use this factory to create an instance
 *  @property getErrorListInternal Override this to provide the specific implementation
 *  @property getErrorList This is the public API called from [Ruleset.getErrorList] (and special cases in `DesktopLauncher` / `Suppression`)
 */
abstract class RulesetValidator protected constructor(
    protected val ruleset: Ruleset,
    protected val tryFixUnknownUniques: Boolean
) {
    protected val uniqueValidator = UniqueValidator(ruleset)

    protected lateinit var textureNamesCache: AtlasPreview

    companion object {
        fun create(ruleset: Ruleset, tryFixUnknownUniques: Boolean = false): RulesetValidator {
            return if (ruleset.modOptions.isBaseRuleset)
                BaseRulesetValidator(ruleset, tryFixUnknownUniques)
            else
                NonBaseRulesetValidator(ruleset, tryFixUnknownUniques)
        }
    }

    protected abstract fun getErrorListInternal(): RulesetErrorList

    fun getErrorList(): RulesetErrorList {
        return try {
            getErrorListInternal()
        } catch (e: Exception) {
            RulesetErrorList(ruleset).apply {
                add("Error while validating ruleset ${ruleset.name}: ${e.message}")
            }
        }
    }

    protected fun initTextureNamesCache(lines: RulesetErrorList) {
        if (!::textureNamesCache.isInitialized)
            textureNamesCache = AtlasPreview(ruleset, lines)  // This logs Atlas list errors, and if these exist, scans for invalid png's
    }

    protected fun getPossibleMisspellings(originalText: String, possibleMisspellings: List<String>): List<String> =
        possibleMisspellings.filter {
            getRelativeTextDistance(
                it,
                originalText
            ) <= RulesetCache.uniqueMisspellingThreshold
        }

    protected fun checkFileNames(lines: RulesetErrorList) {
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

    protected fun addModOptionsErrors(lines: RulesetErrorList) {
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

    protected fun addGlobalUniqueErrors(lines: RulesetErrorList, reportRulesetSpecificErrors: Boolean) {
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

    protected fun checkContrasts(
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

    protected fun addBuildingErrorRulesetInvariant(building: Building, lines: RulesetErrorList) {
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

    protected fun addTechColumnErrorsRulesetInvariant(lines: RulesetErrorList) {
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

    protected fun checkUnitRulesetInvariant(unit: BaseUnit, lines: RulesetErrorList) {
        for (upgradesTo in unit.getUpgradeUnits(StateForConditionals.IgnoreConditionals)) {
            if (upgradesTo == unit.name || (upgradesTo == unit.replaces))
                lines.add("${unit.name} upgrades to itself!", sourceObject = unit)
        }

        if (unit.replaces != null && unit.uniqueTo == null)
            lines.add("${unit.name} should replace ${unit.replaces} but does not have uniqueTo assigned!")

        if (unit.isMilitary && unit.strength == 0)  // Should only match ranged units with 0 strength
            lines.add("${unit.name} is a military unit but has no assigned strength!", sourceObject = unit)
    }

    protected fun addPromotionErrorRulesetInvariant(promotion: Promotion, lines: RulesetErrorList) {
        if (promotion.row < -1) lines.add("Promotion ${promotion.name} has invalid row value: ${promotion.row}", sourceObject = promotion)
        if (promotion.column < 0) lines.add("Promotion ${promotion.name} has invalid column value: ${promotion.column}", sourceObject = promotion)
        if (promotion.row == -1) return
        for (otherPromotion in ruleset.unitPromotions.values)
            if (promotion != otherPromotion && promotion.column == otherPromotion.column && promotion.row == otherPromotion.row)
                lines.add("Promotions ${promotion.name} and ${otherPromotion.name} have the same position: ${promotion.row}/${promotion.column}", sourceObject = promotion)
    }

    protected fun addNationErrorRulesetInvariant(nation: Nation, lines: RulesetErrorList) {
        if (nation.cities.isEmpty() && !nation.isSpectator && !nation.isBarbarian) {
            lines.add("${nation.name} can settle cities, but has no city names!", sourceObject = nation)
        }

        checkContrasts(nation.getInnerColor(), nation.getOuterColor(), nation, lines)
    }


    protected fun checkTilesetSanity(lines: RulesetErrorList) {
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

    protected fun checkCivilopediaText(lines: RulesetErrorList) {
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

    /* This is public because `FormattedLine` does its own checking and needs the textureNamesCache test */
    fun uncachedImageExists(name: String): Boolean {
        if (ruleset.folderLocation == null) return false // Can't check in this case
        return textureNamesCache.imageExists(name)
    }
}
