package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapParameters
import com.unciv.models.Counter
import com.unciv.models.ModConstants
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Difficulty
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.NamedStats
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.utils.Log
import com.unciv.utils.debug
import kotlin.collections.set

object ModOptionsConstants {
    const val diplomaticRelationshipsCannotChange = "Diplomatic relationships cannot change"
    const val convertGoldToScience = "Can convert gold to science with sliders"
    const val allowCityStatesSpawnUnits = "Allow City States to spawn with additional units"
    const val tradeCivIntroductions = "Can trade civilization introductions for [] Gold"
    const val disableReligion = "Disable religion"
    const val allowRazeCapital = "Allow raze capital"
    const val allowRazeHolyCity = "Allow raze holy city"
}

class ModOptions : IHasUniques {
    var isBaseRuleset = false
    var techsToRemove = HashSet<String>()
    var buildingsToRemove = HashSet<String>()
    var unitsToRemove = HashSet<String>()
    var nationsToRemove = HashSet<String>()


    var lastUpdated = ""
    var modUrl = ""
    var author = ""
    var modSize = 0
    var topics = mutableListOf<String>()

    @Deprecated("As of 3.18.15")
    var maxXPfromBarbarians = 30

    override var uniques = ArrayList<String>()

    // If these two are delegated with "by lazy", the mod download process crashes and burns
    // Instead, Ruleset.load sets them, which is preferable in this case anyway
    override var uniqueObjects: List<Unique> = listOf()
    override var uniqueMap: Map<String, List<Unique>> = mapOf()

    override fun getUniqueTarget() = UniqueTarget.ModOptions

    val constants = ModConstants()
}

class Ruleset {

    var folderLocation:FileHandle?=null

    var name = ""
    val beliefs = LinkedHashMap<String, Belief>()
    val buildings = LinkedHashMap<String, Building>()
    val difficulties = LinkedHashMap<String, Difficulty>()
    val eras = LinkedHashMap<String, Era>()
    val speeds = LinkedHashMap<String, Speed>()
    var globalUniques = GlobalUniques()
    val nations = LinkedHashMap<String, Nation>()
    val policies = LinkedHashMap<String, Policy>()
    val policyBranches = LinkedHashMap<String, PolicyBranch>()
    val religions = ArrayList<String>()
    val ruinRewards = LinkedHashMap<String, RuinReward>()
    val quests = LinkedHashMap<String, Quest>()
    val specialists = LinkedHashMap<String, Specialist>()
    val technologies = LinkedHashMap<String, Technology>()
    val terrains = LinkedHashMap<String, Terrain>()
    val tileImprovements = LinkedHashMap<String, TileImprovement>()
    val tileResources = LinkedHashMap<String, TileResource>()
    val units = LinkedHashMap<String, BaseUnit>()
    val unitPromotions = LinkedHashMap<String, Promotion>()
    val unitTypes = LinkedHashMap<String, UnitType>()
    var victories = LinkedHashMap<String, Victory>()
    var cityStateTypes = LinkedHashMap<String, CityStateType>()

    val mods = LinkedHashSet<String>()
    var modOptions = ModOptions()

    fun clone(): Ruleset {
        val newRuleset = Ruleset()
        newRuleset.add(this)
        return newRuleset
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    fun add(ruleset: Ruleset) {
        beliefs.putAll(ruleset.beliefs)
        buildings.putAll(ruleset.buildings)
        for (buildingToRemove in ruleset.modOptions.buildingsToRemove) buildings.remove(
            buildingToRemove
        )
        difficulties.putAll(ruleset.difficulties)
        eras.putAll(ruleset.eras)
        speeds.putAll(ruleset.speeds)
        globalUniques = GlobalUniques().apply {
            uniques.addAll(globalUniques.uniques); uniques.addAll(ruleset.globalUniques.uniques)
        }
        nations.putAll(ruleset.nations)
        for (nationToRemove in ruleset.modOptions.nationsToRemove) nations.remove(nationToRemove)
        policyBranches.putAll(ruleset.policyBranches)
        policies.putAll(ruleset.policies)
        quests.putAll(ruleset.quests)
        religions.addAll(ruleset.religions)
        ruinRewards.putAll(ruleset.ruinRewards)
        specialists.putAll(ruleset.specialists)
        technologies.putAll(ruleset.technologies)
        for (techToRemove in ruleset.modOptions.techsToRemove) technologies.remove(techToRemove)
        terrains.putAll(ruleset.terrains)
        tileImprovements.putAll(ruleset.tileImprovements)
        tileResources.putAll(ruleset.tileResources)
        units.putAll(ruleset.units)
        unitTypes.putAll(ruleset.unitTypes)
        victories.putAll(ruleset.victories)
        cityStateTypes.putAll(ruleset.cityStateTypes)
        for (unitToRemove in ruleset.modOptions.unitsToRemove) units.remove(unitToRemove)
        modOptions.uniques.addAll(ruleset.modOptions.uniques)
        modOptions.constants.merge(ruleset.modOptions.constants)

        // Allow each mod to define their own columns, and if there's a conflict, later mods will be shifted right
        // We should never be editing the original ruleset objects, only copies
        val addRulesetUnitPromotionClones = ruleset.unitPromotions.values.map { it.clone() }
        val existingPromotionLocations =
                unitPromotions.values.map { "${it.row}/${it.column}" }.toHashSet()
        val promotionsWithConflictingLocations = addRulesetUnitPromotionClones.filter {
            existingPromotionLocations.contains("${it.row}/${it.column}")
        }
        val columnsWithConflictingLocations =
                promotionsWithConflictingLocations.map { it.column }.distinct()

        if (columnsWithConflictingLocations.isNotEmpty()) {
            var highestExistingColumn = unitPromotions.values.maxOf { it.column }
            for (conflictingColumn in columnsWithConflictingLocations) {
                highestExistingColumn += 1
                val newColumn = highestExistingColumn
                for (promotion in addRulesetUnitPromotionClones)
                    if (promotion.column == conflictingColumn)
                        promotion.column = newColumn
            }
        }
        val finalModUnitPromotionsMap = addRulesetUnitPromotionClones.associateBy { it.name }

        unitPromotions.putAll(finalModUnitPromotionsMap)

        mods += ruleset.mods
    }

    fun clear() {
        beliefs.clear()
        buildings.clear()
        difficulties.clear()
        eras.clear()
        speeds.clear()
        globalUniques = GlobalUniques()
        mods.clear()
        nations.clear()
        policies.clear()
        policyBranches.clear()
        quests.clear()
        religions.clear()
        ruinRewards.clear()
        specialists.clear()
        technologies.clear()
        terrains.clear()
        tileImprovements.clear()
        tileResources.clear()
        unitPromotions.clear()
        units.clear()
        unitTypes.clear()
        victories.clear()
        cityStateTypes.clear()
    }

    fun allRulesetObjects(): Sequence<IRulesetObject> =
            beliefs.values.asSequence() +
            buildings.values.asSequence() +
            //difficulties is only INamed
            eras.values.asSequence() +
            speeds.values.asSequence() +
            sequenceOf(globalUniques) +
            nations.values.asSequence() +
            policies.values.asSequence() +
            policyBranches.values.asSequence() +
            // quests is only INamed
            // religions is just Strings
            ruinRewards.values.asSequence() +
            // specialists is only NamedStats
            technologies.values.asSequence() +
            terrains.values.asSequence() +
            tileImprovements.values.asSequence() +
            tileResources.values.asSequence() +
            unitPromotions.values.asSequence() +
            units.values.asSequence() +
            unitTypes.values.asSequence()
            // Victories is only INamed
    fun allIHasUniques(): Sequence<IHasUniques> =
            allRulesetObjects() + sequenceOf(modOptions)

    fun load(folderHandle: FileHandle) {
        val modOptionsFile = folderHandle.child("ModOptions.json")
        if (modOptionsFile.exists()) {
            try {
                modOptions = json().fromJsonFile(ModOptions::class.java, modOptionsFile)
                modOptions.updateDeprecations()
            } catch (ex: Exception) {}
            modOptions.uniqueObjects = modOptions.uniques.map { Unique(it, UniqueTarget.ModOptions) }
            modOptions.uniqueMap = modOptions.uniqueObjects.groupBy { it.placeholderText }
        }

        val techFile = folderHandle.child("Techs.json")
        if (techFile.exists()) {
            val techColumns = json().fromJsonFile(Array<TechColumn>::class.java, techFile)
            for (techColumn in techColumns) {
                for (tech in techColumn.techs) {
                    if (tech.cost == 0) tech.cost = techColumn.techCost
                    tech.column = techColumn
                    technologies[tech.name] = tech
                }
            }
        }

        val buildingsFile = folderHandle.child("Buildings.json")
        if (buildingsFile.exists()) buildings += createHashmap(json().fromJsonFile(Array<Building>::class.java, buildingsFile))

        val terrainsFile = folderHandle.child("Terrains.json")
        if (terrainsFile.exists()) {
            terrains += createHashmap(json().fromJsonFile(Array<Terrain>::class.java, terrainsFile))
            for (terrain in terrains.values) terrain.setTransients()
        }

        val resourcesFile = folderHandle.child("TileResources.json")
        if (resourcesFile.exists()) tileResources += createHashmap(json().fromJsonFile(Array<TileResource>::class.java, resourcesFile))

        val improvementsFile = folderHandle.child("TileImprovements.json")
        if (improvementsFile.exists()) tileImprovements += createHashmap(json().fromJsonFile(Array<TileImprovement>::class.java, improvementsFile))

        val erasFile = folderHandle.child("Eras.json")
        if (erasFile.exists()) eras += createHashmap(json().fromJsonFile(Array<Era>::class.java, erasFile))
        // While `eras.values.toList()` might seem more logical, eras.values is a MutableCollection and
        // therefore does not guarantee keeping the order of elements like a LinkedHashMap does.
        // Using map{} sidesteps this problem
        eras.map { it.value }.withIndex().forEach { it.value.eraNumber = it.index }

        val speedsFile = folderHandle.child("Speeds.json")
        if (speedsFile.exists()) {
            speeds += createHashmap(json().fromJsonFile(Array<Speed>::class.java, speedsFile))
        }

        val unitTypesFile = folderHandle.child("UnitTypes.json")
        if (unitTypesFile.exists()) unitTypes += createHashmap(json().fromJsonFile(Array<UnitType>::class.java, unitTypesFile))

        val unitsFile = folderHandle.child("Units.json")
        if (unitsFile.exists()) units += createHashmap(json().fromJsonFile(Array<BaseUnit>::class.java, unitsFile))

        val promotionsFile = folderHandle.child("UnitPromotions.json")
        if (promotionsFile.exists()) unitPromotions += createHashmap(json().fromJsonFile(Array<Promotion>::class.java, promotionsFile))

        var topRow = unitPromotions.values.filter { it.column == 0 }.maxOfOrNull { it.row } ?: -1
        for (promotion in unitPromotions.values)
            if (promotion.row == -1){
                promotion.column = 0
                topRow += 1
                promotion.row = topRow
            }

        val questsFile = folderHandle.child("Quests.json")
        if (questsFile.exists()) quests += createHashmap(json().fromJsonFile(Array<Quest>::class.java, questsFile))

        val specialistsFile = folderHandle.child("Specialists.json")
        if (specialistsFile.exists()) specialists += createHashmap(json().fromJsonFile(Array<Specialist>::class.java, specialistsFile))

        val policiesFile = folderHandle.child("Policies.json")
        if (policiesFile.exists()) {
            policyBranches += createHashmap(
                json().fromJsonFile(Array<PolicyBranch>::class.java, policiesFile)
            )
            for (branch in policyBranches.values) {
                // Setup this branch
                branch.requires = ArrayList()
                branch.branch = branch
                for (victoryType in victories.values) {
                    if (victoryType.name !in branch.priorities.keys) {
                        branch.priorities[victoryType.name] = 0
                    }
                }
                policies[branch.name] = branch

                // Append child policies of this branch
                for (policy in branch.policies) {
                    policy.branch = branch
                    if (policy.requires == null) {
                        policy.requires = arrayListOf(branch.name)
                    }
                    policies[policy.name] = policy
                }

                // Add a finisher
                branch.policies.last().name =
                    branch.name + Policy.branchCompleteSuffix
            }
        }

        val beliefsFile = folderHandle.child("Beliefs.json")
        if (beliefsFile.exists())
            beliefs += createHashmap(json().fromJsonFile(Array<Belief>::class.java, beliefsFile))

        val religionsFile = folderHandle.child("Religions.json")
        if (religionsFile.exists())
            religions += json().fromJsonFile(Array<String>::class.java, religionsFile).toList()

        val ruinRewardsFile = folderHandle.child("Ruins.json")
        if (ruinRewardsFile.exists())
            ruinRewards += createHashmap(json().fromJsonFile(Array<RuinReward>::class.java, ruinRewardsFile))

        val nationsFile = folderHandle.child("Nations.json")
        if (nationsFile.exists()) {
            nations += createHashmap(json().fromJsonFile(Array<Nation>::class.java, nationsFile))
            for (nation in nations.values) nation.setTransients()
        }

        val difficultiesFile = folderHandle.child("Difficulties.json")
        if (difficultiesFile.exists())
            difficulties += createHashmap(json().fromJsonFile(Array<Difficulty>::class.java, difficultiesFile))

        val globalUniquesFile = folderHandle.child("GlobalUniques.json")
        if (globalUniquesFile.exists()) {
            globalUniques = json().fromJsonFile(GlobalUniques::class.java, globalUniquesFile)
        }

        val victoryTypesFile = folderHandle.child("VictoryTypes.json")
        if (victoryTypesFile.exists()) {
            victories += createHashmap(json().fromJsonFile(Array<Victory>::class.java, victoryTypesFile))
        }

        val cityStateTypesFile = folderHandle.child("CityStateTypes.json")
        if (cityStateTypesFile.exists()) {
            cityStateTypes += createHashmap(json().fromJsonFile(Array<CityStateType>::class.java, cityStateTypesFile))
        }



        // Add objects that might not be present in base ruleset mods, but are required
        if (modOptions.isBaseRuleset) {
            // This one should be temporary
            if (unitTypes.isEmpty()) {
                unitTypes.putAll(RulesetCache.getVanillaRuleset().unitTypes)
            }

            // These should be permanent
            if (ruinRewards.isEmpty())
                ruinRewards.putAll(RulesetCache.getVanillaRuleset().ruinRewards)

            if (globalUniques.uniques.isEmpty()) {
                globalUniques = RulesetCache.getVanillaRuleset().globalUniques
            }
            // If we have no victories, add all the default victories
            if (victories.isEmpty()) victories.putAll(RulesetCache.getVanillaRuleset().victories)

            if (speeds.isEmpty()) speeds.putAll(RulesetCache.getVanillaRuleset().speeds)

            if (cityStateTypes.isEmpty())
                for (cityStateType in RulesetCache.getVanillaRuleset().cityStateTypes.values)
                    cityStateTypes[cityStateType.name] = CityStateType().apply {
                        name = cityStateType.name
                        color = cityStateType.color
                        friendBonusUniques = ArrayList(cityStateType.friendBonusUniques.filter {
                            RulesetValidator(this@Ruleset).checkUnique(Unique(it),false,"",UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific,UniqueTarget.CityState).isEmpty()
                        })
                        allyBonusUniques = ArrayList(cityStateType.allyBonusUniques.filter {
                            RulesetValidator(this@Ruleset).checkUnique(Unique(it),false,"",UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific,UniqueTarget.CityState).isEmpty()
                        })
                    }
        }
    }

    /** Building costs are unique in that they are dependant on info in the technology part.
     *  This means that if you add a building in a mod, you want it to depend on the original tech values.
     *  Alternatively, if you edit a tech column's building costs, you want it to affect all buildings in that column.
     *  This deals with that
     *  */
    fun updateBuildingCosts() {
        for (building in buildings.values) {
            if (building.cost == 0 && building.getMatchingUniques(UniqueType.Unbuildable).none { it.conditionals.isEmpty() }) {
                val column = technologies[building.requiredTech]?.column
                        ?: throw UncivShowableException("Building '[${building.name}]' is buildable and therefore must either have an explicit cost or reference an existing tech.")
                building.cost = if (building.isAnyWonder()) column.wonderCost else column.buildingCost
            }
        }
    }

    /** Used for displaying a RuleSet's name */
    override fun toString() = when {
        name.isNotEmpty() -> name
        mods.size == 1 && RulesetCache[mods.first()]!!.modOptions.isBaseRuleset -> mods.first()
        else -> "Combined RuleSet"
    }

    fun getSummary(): String {
        val stringList = ArrayList<String>()
        if (modOptions.isBaseRuleset) stringList += "Base Ruleset"
        if (technologies.isNotEmpty()) stringList += "[${technologies.size}] Techs"
        if (nations.isNotEmpty()) stringList += "[${nations.size}] Nations"
        if (units.isNotEmpty()) stringList += "[${units.size}] Units"
        if (buildings.isNotEmpty()) stringList += "[${buildings.size}] Buildings"
        if (tileResources.isNotEmpty()) stringList += "[${tileResources.size}] Resources"
        if (tileImprovements.isNotEmpty()) stringList += "[${tileImprovements.size}] Improvements"
        if (religions.isNotEmpty()) stringList += "[${religions.size}] Religions"
        if (beliefs.isNotEmpty()) stringList += "[${beliefs.size}] Beliefs"
        return stringList.joinToString { it.tr() }
    }

    fun checkModLinks(tryFixUnknownUniques: Boolean = false) = RulesetValidator(this).getErrorList(tryFixUnknownUniques)
}

/** Loading mods is expensive, so let's only do it once and
 * save all of the loaded rulesets somewhere for later use
 *  */
object RulesetCache : HashMap<String,Ruleset>() {
    /** Whether mod checking allows untyped uniques - set to `false` once all vanilla uniques are converted! */
    var modCheckerAllowUntypedUniques = true

    /** Similarity below which an untyped unique can be considered a potential misspelling.
     * Roughly corresponds to the fraction of the Unique placeholder text that can be different/misspelled, but with some extra room for [getRelativeTextDistance] idiosyncrasies. */
    var uniqueMisspellingThreshold = 0.15 // Tweak as needed. Simple misspellings seem to be around 0.025, so would mostly be caught by 0.05. IMO 0.1 would be good, but raising to 0.15 also seemed to catch what may be an outdated Unique.


    /** Returns error lines from loading the rulesets, so we can display the errors to users */
    fun loadRulesets(consoleMode: Boolean = false, noMods: Boolean = false) :List<String> {
        clear()
        for (ruleset in BaseRuleset.values()) {
            val fileName = "jsons/${ruleset.fullName}"
            val fileHandle =
                if (consoleMode) FileHandle(fileName)
                else Gdx.files.internal(fileName)
            this[ruleset.fullName] = Ruleset().apply {
                load(fileHandle)
                name = ruleset.fullName
            }
        }

        if (noMods) return listOf()

        val modsHandles = if (consoleMode) FileHandle("mods").list()
        else Gdx.files.local("mods").list()

        val errorLines = ArrayList<String>()
        for (modFolder in modsHandles) {
            if (modFolder.name().startsWith('.')) continue
            if (!modFolder.isDirectory) continue
            try {
                val modRuleset = Ruleset()
                modRuleset.load(modFolder.child("jsons"))
                modRuleset.name = modFolder.name()
                modRuleset.folderLocation = modFolder
                this[modRuleset.name] = modRuleset
                debug("Mod loaded successfully: %s", modRuleset.name)
                if (Log.shouldLog()) {
                    val modLinksErrors = modRuleset.checkModLinks()
                    if (modLinksErrors.any()) {
                        debug("checkModLinks errors: %s", modLinksErrors.getErrorText())
                    }
                }
            } catch (ex: Exception) {
                errorLines += "Exception loading mod '${modFolder.name()}':"
                errorLines += "  ${ex.localizedMessage}"
                errorLines += "  ${ex.cause?.localizedMessage}"
            }
        }
        if (Log.shouldLog()) for (line in errorLines) debug(line)
        return errorLines
    }


    fun getVanillaRuleset() = this[BaseRuleset.Civ_V_Vanilla.fullName]!!.clone() // safeguard, so no-one edits the base ruleset by mistake

    fun getSortedBaseRulesets(): List<String> {
        val baseRulesets = values
            .filter { it.modOptions.isBaseRuleset }
            .map { it.name }
            .distinct()
        if (baseRulesets.size < 2) return baseRulesets

        // We sort the base rulesets such that the ones unciv provides are on the top,
        // and the rest is alphabetically ordered.
        return baseRulesets.sortedWith(
            compareBy(
                { ruleset ->
                    BaseRuleset.values()
                        .firstOrNull { br -> br.fullName == ruleset }?.ordinal
                        ?: BaseRuleset.values().size
                },
                { it }
            )
        )
    }

    /** Creates a combined [Ruleset] from a list of mods contained in [parameters]. */
    fun getComplexRuleset(parameters: MapParameters) =
        getComplexRuleset(parameters.mods, parameters.baseRuleset)

    /** Creates a combined [Ruleset] from a list of mods contained in [parameters]. */
    fun getComplexRuleset(parameters: GameParameters) =
        getComplexRuleset(parameters.mods, parameters.baseRuleset)

    /**
     * Creates a combined [Ruleset] from a list of mods.
     * If no baseRuleset is passed in [optionalBaseRuleset] (or a non-existing one), then the vanilla Ruleset is included automatically.
     * Any mods in the [mods] parameter marked as base ruleset (or not loaded in [RulesetCache]) are ignored.
     */
    fun getComplexRuleset(mods: LinkedHashSet<String>, optionalBaseRuleset: String? = null): Ruleset {
        val baseRuleset =
                if (containsKey(optionalBaseRuleset) && this[optionalBaseRuleset]!!.modOptions.isBaseRuleset)
                    this[optionalBaseRuleset]!!
                else getVanillaRuleset()

        val loadedMods = mods.asSequence()
            .filter { containsKey(it) }
            .map { this[it]!! }
            .filter { !it.modOptions.isBaseRuleset }

        return getComplexRuleset(baseRuleset, loadedMods.asIterable())
    }

    /**
     * Creates a combined [Ruleset] from [baseRuleset] and [extensionRulesets] which must only contain non-base rulesets.
     */
    fun getComplexRuleset(baseRuleset: Ruleset, extensionRulesets: Iterable<Ruleset>): Ruleset {
        val newRuleset = Ruleset()

        val loadedMods = extensionRulesets.asSequence() + baseRuleset

        for (mod in loadedMods.sortedByDescending { it.modOptions.isBaseRuleset }) {
            if (mod.modOptions.isBaseRuleset) {
                // This is so we don't keep using the base ruleset's unqiues *by reference* and add to in ad infinitum
                newRuleset.modOptions.uniques = ArrayList()
                newRuleset.modOptions.isBaseRuleset = true
            }
            newRuleset.add(mod)
            newRuleset.mods += mod.name
        }
        newRuleset.updateBuildingCosts() // only after we've added all the mods can we calculate the building costs

        return newRuleset
    }

    /**
     * Runs [Ruleset.checkModLinks] on a temporary [combined Ruleset][getComplexRuleset] for a list of [mods]
     */
    fun checkCombinedModLinks(
        mods: LinkedHashSet<String>,
        baseRuleset: String? = null,
        tryFixUnknownUniques: Boolean = false
    ): RulesetErrorList {
        return try {
            val newRuleset = getComplexRuleset(mods, baseRuleset)
            newRuleset.modOptions.isBaseRuleset = true // This is so the checkModLinks finds all connections
            newRuleset.checkModLinks(tryFixUnknownUniques)
        } catch (ex: UncivShowableException) {
            // This happens if a building is dependent on a tech not in the base ruleset
            //  because newRuleset.updateBuildingCosts() in getComplexRuleset() throws an error
            RulesetErrorList()
                .apply { add(ex.message, RulesetErrorSeverity.Error) }
        }
    }

}

class Specialist: NamedStats() {
    var color = ArrayList<Int>()
    val colorObject by lazy { colorFromRGB(color) }
    var greatPersonPoints = Counter<String>()
}
