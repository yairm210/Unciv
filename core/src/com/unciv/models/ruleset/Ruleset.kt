package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.logic.GameInfo
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Difficulty
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.nation.Personality
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitNameGroup
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.GameResource
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.models.stats.SubStat
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import com.unciv.utils.Log
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Readonly
import kotlin.collections.set

enum class RulesetFile(
    val filename: String,
    @Readonly val getRulesetObjects: Ruleset.() -> Sequence<IRulesetObject> = { emptySequence() },
    @Readonly val getUniques: Ruleset.() -> Sequence<Unique> = { getRulesetObjects().flatMap { it.uniqueObjects } }
) {
    Beliefs("Beliefs.json", { beliefs.values.asSequence() }),
    Buildings("Buildings.json", { buildings.values.asSequence() }),
    Eras("Eras.json", { eras.values.asSequence() }),
    Religions("Religions.json"),
    Nations("Nations.json", { nations.values.asSequence() }),
    Policies("Policies.json", { policies.values.asSequence() }),
    Techs("Techs.json", { technologies.values.asSequence() }),
    Terrains("Terrains.json", { terrains.values.asSequence() }),
    Tutorials("Tutorials.json", { tutorials.values.asSequence() }),
    TileImprovements("TileImprovements.json", { tileImprovements.values.asSequence() }),
    TileResources("TileResources.json", { tileResources.values.asSequence() }),
    Specialists("Specialists.json"),
    Units("Units.json", { units.values.asSequence() }),
    UnitPromotions("UnitPromotions.json", { unitPromotions.values.asSequence() }),
    UnitNameGroup("UnitNameGroups.json", { unitNameGroups.values.asSequence() }),
    UnitTypes("UnitTypes.json", { unitTypes.values.asSequence() }),
    VictoryTypes("VictoryTypes.json"),
    CityStateTypes("CityStateTypes.json", getUniques =
        { cityStateTypes.values.asSequence().flatMap { it.allyBonusUniqueMap.getAllUniques() + it.friendBonusUniqueMap.getAllUniques() } }),
    Personalities("Personalities.json", { personalities.values.asSequence() }),
    Events("Events.json", { events.values.asSequence() + events.values.flatMap { it.choices } }),
    GlobalUniques("GlobalUniques.json", { sequenceOf(globalUniques) }),
    ModOptions("ModOptions.json", getUniques = { modOptions.uniqueObjects.asSequence() }),
    Speeds("Speeds.json", { speeds.values.asSequence() }),
    Difficulties("Difficulties.json"),
    Quests("Quests.json"),
    Ruins("Ruins.json", { ruinRewards.values.asSequence() });
}

class Ruleset {

    /** If (and only if) this Ruleset is a mod, this will be the source folder.
     *  In other words, this is `null` for built-in and combined rulesets.
     */
    var folderLocation: FileHandle? = null

    /** A Ruleset instance can represent a built-in ruleset, a mod or a combined ruleset.
     *
     *  `name` will be the built-in's fullName, the mod's name as displayed (same as folder name),
     *  or in the case of combined rulesets it will be empty.
     *
     *  @see toString
     *  @see BaseRuleset.fullName
     *  @see RulesetCache.getComplexRuleset
     */
    var name = ""

    /** The list of mods that made up this Ruleset, including the base ruleset. */
    val mods = LinkedHashSet<String>()

    //region Json fields
    val beliefs = LinkedHashMap<String, Belief>()
    val buildings = LinkedHashMap<String, Building>()
    val difficulties = LinkedHashMap<String, Difficulty>()
    val eras = LinkedHashMap<String, Era>()
    val speeds = LinkedHashMap<String, Speed>()
    /** Only [Ruleset.load], [GameInfo], [BaseUnit] and [RulesetValidator] should access this directly.
     *  All other uses should call [GameInfo.getGlobalUniques] instead. */
    internal var globalUniques = GlobalUniques()
    val nations = LinkedHashMap<String, Nation>()
    val policies = LinkedHashMap<String, Policy>()
    val policyBranches = LinkedHashMap<String, PolicyBranch>()
    val religions = ArrayList<String>()
    val ruinRewards = LinkedHashMap<String, RuinReward>()
    val quests = LinkedHashMap<String, Quest>()
    val specialists = LinkedHashMap<String, Specialist>()
    val technologies = LinkedHashMap<String, Technology>()
    val techColumns = ArrayList<TechColumn>()
    val terrains = LinkedHashMap<String, Terrain>()
    val tileImprovements = LinkedHashMap<String, TileImprovement>()
    val tileResources = LinkedHashMap<String, TileResource>()
    val tutorials = LinkedHashMap<String, Tutorial>()
    val units = LinkedHashMap<String, BaseUnit>()
    val unitPromotions = LinkedHashMap<String, Promotion>()
    val unitNameGroups = LinkedHashMap<String, UnitNameGroup>()
    val unitTypes = LinkedHashMap<String, UnitType>()
    var victories = LinkedHashMap<String, Victory>()
    var cityStateTypes = LinkedHashMap<String, CityStateType>()
    val personalities = LinkedHashMap<String, Personality>()
    val events = LinkedHashMap<String, Event>()
    var modOptions = ModOptions()
    //endregion

    //region cache fields
    val greatGeneralUnits by lazy {
        units.values.filter { it.hasUnique(UniqueType.GreatPersonFromCombat, GameContext.IgnoreConditionals) }
    }

    val tileRemovals by lazy { tileImprovements.values.filter { it.name.startsWith(Constants.remove) } }
    val nonRoadTileRemovals by lazy { tileRemovals.filter { rulesetImprovement ->
            RoadStatus.entries.toTypedArray().none { it.removeAction == rulesetImprovement.name } } }

    /** Contains all happiness levels that moving *from* them, to one *below* them, can change uniques that apply */
    val allHappinessLevelsThatAffectUniques by lazy {
        sequence {
            for (unique in this@Ruleset.allUniques())
                for (conditional in unique.modifiers){
                    if (conditional.type == UniqueType.ConditionalWhenBelowAmountStatResource
                        && conditional.params[1] == "Happiness") yield(conditional.params[0].toInt())
                    if (conditional.type == UniqueType.ConditionalWhenAboveAmountStatResource
                        && conditional.params[1] == "Happiness") yield(conditional.params[0].toInt())
                    if (conditional.type == UniqueType.ConditionalWhenBetweenStatResource
                        && conditional.params[2] == "Happiness"){
                        yield(conditional.params[0].toInt())
                        yield(conditional.params[1].toInt() + 1)
                    }
                    if (conditional.type == UniqueType.ConditionalHappy) yield(0)
                }
        }.toSet()
    }

    val roadImprovement: TileImprovement? by lazy { RoadStatus.Road.improvement(this) }
    val railroadImprovement: TileImprovement? by lazy { RoadStatus.Railroad.improvement(this) }
    //endregion

    fun clone(): Ruleset {
        val newRuleset = Ruleset()
        newRuleset.add(this)
        // Make sure the clone is recognizable - e.g. startNewGame fallback when a base mod was removed needs this
        newRuleset.name = name
        newRuleset.modOptions.isBaseRuleset = modOptions.isBaseRuleset
        return newRuleset
    }

    fun getGameResource(resourceName: String): GameResource? = Stat.safeValueOf(resourceName)
        ?: SubStat.safeValueOf(resourceName)
        ?: tileResources[resourceName]

    private inline fun <reified T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>(items.size)
        for (item in items) {
            val itemName = try { item.name }
            catch (_: Exception) {
                throw Exception("${T::class.simpleName} is missing a name!")
            }

            hashMap[itemName] = item
            (item as? IRulesetObject)?.originRuleset = name // RULESET name
        }
        return hashMap
    }

    fun add(ruleset: Ruleset) {
        beliefs.putAll(ruleset.beliefs)
        ruleset.modOptions.beliefsToRemove
            .flatMap { beliefsToRemove ->
                beliefs.filter { it.value.matchesFilter(beliefsToRemove) }.keys
            }.toSet().forEach {
                beliefs.remove(it)
            }

        ruleset.modOptions.buildingsToRemove
            .flatMap { buildingToRemove ->
                buildings.filter { it.value.matchesFilter(buildingToRemove) }.keys
            }.toSet().forEach {
                buildings.remove(it)
            }
        buildings.putAll(ruleset.buildings)
        difficulties.putAll(ruleset.difficulties)
        eras.putAll(ruleset.eras)
        speeds.putAll(ruleset.speeds)
        globalUniques = GlobalUniques.combine(globalUniques, ruleset.globalUniques)
        ruleset.modOptions.nationsToRemove
            .flatMap { nationToRemove ->
                nations.filter { it.value.matchesFilter(nationToRemove) }.keys
            }.toSet().forEach {
                nations.remove(it)
            }
        nations.putAll(ruleset.nations)
        
        /** We must remove all policies from a policy branch otherwise we have policies that cannot be picked
         *  but are still considered "available" */
        fun removePolicyBranch(policyBranch: PolicyBranch){
            policyBranches.remove(policyBranch.name)
            for (policy in policyBranch.policies)
                policies.remove(policy.name)
        }
        
        ruleset.modOptions.policyBranchesToRemove
            .flatMap { policyBranchToRemove ->
                policyBranches.filter { it.value.matchesFilter(policyBranchToRemove) }.values
            }.toSet().forEach {
                removePolicyBranch(it)
            }
        
        val overriddenPolicyBranches = policyBranches
            .filter { it.key in ruleset.policyBranches }.map { it.value }
        for (policyBranch in overriddenPolicyBranches) removePolicyBranch(policyBranch
        )
        policyBranches.putAll(ruleset.policyBranches)
        
        policies.putAll(ruleset.policies)

        // Remove the policies
        ruleset.modOptions.policiesToRemove
            .flatMap { policyToRemove ->
                policies.filter { it.value.matchesFilter(policyToRemove) }.keys
            }.toSet().forEach {
                policies.remove(it)
            }

        // Remove the policies if they exist in the policy branches too
        for (policyToRemove in ruleset.modOptions.policiesToRemove) {
            for (branch in policyBranches.values) {
                branch.policies.removeAll { it.matchesFilter(policyToRemove) }
            }
        }

        quests.putAll(ruleset.quests)

        // Remove associated Religions, including when they're favored by Nations
        religions.addAll(ruleset.religions)
        religions.removeAll(ruleset.modOptions.religionsToRemove)
        nations.filter { it.value.favoredReligion in ruleset.modOptions.religionsToRemove }
            .forEach { it.value.favoredReligion = null }

        ruinRewards.putAll(ruleset.ruinRewards)
        specialists.putAll(ruleset.specialists)
        ruleset.modOptions.techsToRemove
            .flatMap { techToRemove ->
                technologies.filter { it.value.matchesFilter(techToRemove) }.keys
            }.toSet().forEach {
                technologies.remove(it)
            }
        technologies.putAll(ruleset.technologies)
        techColumns.addAll(ruleset.techColumns)
        terrains.putAll(ruleset.terrains)
        tileImprovements.putAll(ruleset.tileImprovements)
        tileResources.putAll(ruleset.tileResources)
        tutorials.putAll(ruleset.tutorials)
        unitTypes.putAll(ruleset.unitTypes)
        victories.putAll(ruleset.victories)
        cityStateTypes.putAll(ruleset.cityStateTypes)
        ruleset.modOptions.unitsToRemove
            .flatMap { unitToRemove ->
                units.filter { it.apply { value.setRuleset(this@Ruleset) }.value.matchesFilter(unitToRemove) }.keys
            }.toSet().forEach {
                units.remove(it)
            }
        units.putAll(ruleset.units)
        personalities.putAll(ruleset.personalities)
        events.putAll(ruleset.events)
        modOptions.uniques.addAll(ruleset.modOptions.uniques)
        modOptions.constants.merge(ruleset.modOptions.constants)

        unitPromotions.putAll(ruleset.unitPromotions)
        unitNameGroups.putAll(ruleset.unitNameGroups)

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
        techColumns.clear()
        terrains.clear()
        tileImprovements.clear()
        tileResources.clear()
        tutorials.clear()
        unitPromotions.clear()
        unitNameGroups.clear()
        units.clear()
        unitTypes.clear()
        victories.clear()
        cityStateTypes.clear()
        personalities.clear()
        events.clear()
    }

    @Readonly
    fun allRulesetObjects(): Sequence<IRulesetObject> = RulesetFile.entries.asSequence().flatMap { it.getRulesetObjects(this) }
    @Readonly
    fun allUniques(): Sequence<Unique> = RulesetFile.entries.asSequence().flatMap { it.getUniques(this) }
    @Readonly fun allICivilopediaText(): Sequence<ICivilopediaText> = allRulesetObjects() + events.values.flatMap { it.choices }

    fun load(folderHandle: FileHandle) {
        fun RulesetFile.file() = folderHandle.child(filename)

        // Note: Most files are loaded using createHashmap, which sets originRuleset automatically.
        // For other files containing IRulesetObject's we'll have to remember to do so manually - e.g. Tech.
        val modOptionsFile = RulesetFile.ModOptions.file()
        if (modOptionsFile.exists()) {
            try {
                modOptions = json().fromJsonFile(ModOptions::class.java, modOptionsFile)
                modOptions.updateDeprecations()
            } catch (ex: Exception) {
                Log.error("Failed to get modOptions from json file", ex)
            }
        }

        val techFile = RulesetFile.Techs.file()
        if (techFile.exists()) {
            val techColumns = json().fromJsonFile(Array<TechColumn>::class.java, techFile)
            for (techColumn in techColumns) {
                this.techColumns.add(techColumn)
                for (tech in techColumn.techs) {
                    if (tech.cost == 0) tech.cost = techColumn.techCost
                    tech.column = techColumn
                    tech.originRuleset = name
                    technologies[tech.name] = tech
                }
            }
        }

        val buildingsFile = RulesetFile.Buildings.file()
        if (buildingsFile.exists()) buildings += createHashmap(json().fromJsonFile(Array<Building>::class.java, buildingsFile))

        val terrainsFile = RulesetFile.Terrains.file()
        if (terrainsFile.exists()) {
            terrains += createHashmap(json().fromJsonFile(Array<Terrain>::class.java, terrainsFile))
            for (terrain in terrains.values) {
                terrain.originRuleset = name
                terrain.setTransients()
            }
        }

        val resourcesFile = RulesetFile.TileResources.file()
        if (resourcesFile.exists()) tileResources += createHashmap(json().fromJsonFile(Array<TileResource>::class.java, resourcesFile))

        val improvementsFile = RulesetFile.TileImprovements.file()
        if (improvementsFile.exists()) tileImprovements += createHashmap(json().fromJsonFile(Array<TileImprovement>::class.java, improvementsFile))

        val erasFile = RulesetFile.Eras.file()
        if (erasFile.exists()) eras += createHashmap(json().fromJsonFile(Array<Era>::class.java, erasFile))
        // While `eras.values.toList()` might seem more logical, eras.values is a MutableCollection and
        // therefore does not guarantee keeping the order of elements like a LinkedHashMap does.
        // Using map{} sidesteps this problem
        eras.map { it.value }.withIndex().forEach { it.value.eraNumber = it.index }

        val speedsFile = RulesetFile.Speeds.file()
        if (speedsFile.exists()) {
            speeds += createHashmap(json().fromJsonFile(Array<Speed>::class.java, speedsFile))
        }

        val unitTypesFile = RulesetFile.UnitTypes.file()
        if (unitTypesFile.exists()) unitTypes += createHashmap(json().fromJsonFile(Array<UnitType>::class.java, unitTypesFile))

        val unitsFile = RulesetFile.Units.file()
        if (unitsFile.exists()) units += createHashmap(json().fromJsonFile(Array<BaseUnit>::class.java, unitsFile))

        val promotionsFile = RulesetFile.UnitPromotions.file()
        if (promotionsFile.exists()) unitPromotions += createHashmap(json().fromJsonFile(Array<Promotion>::class.java, promotionsFile))

        val unitNameGroupsFile = RulesetFile.UnitNameGroup.file()
        if (unitNameGroupsFile.exists()) unitNameGroups += createHashmap(json().fromJsonFile(Array<UnitNameGroup>::class.java, unitNameGroupsFile))

        val questsFile = RulesetFile.Quests.file()
        if (questsFile.exists()) quests += createHashmap(json().fromJsonFile(Array<Quest>::class.java, questsFile))

        val specialistsFile = RulesetFile.Specialists.file()
        if (specialistsFile.exists()) specialists += createHashmap(json().fromJsonFile(Array<Specialist>::class.java, specialistsFile))

        val policiesFile = RulesetFile.Policies.file()
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
                    policy.originRuleset = name
                    if (policy.requires == null) {
                        policy.requires = arrayListOf(branch.name)
                    }

                    if (policy != branch.policies.last()) {
                        // If mods override a previous policy's location, we don't want that policy to stick around,
                        // because it leads to softlocks on the policy picker screen
                        val conflictingLocationPolicy = policies.values.firstOrNull {
                            it.branch.name == policy.branch.name
                                && it.column == policy.column
                                && it.row == policy.row
                        }
                        if (conflictingLocationPolicy != null)
                            policies.remove(conflictingLocationPolicy.name)
                    }
                    policies[policy.name] = policy

                }

                // Add a finisher
                branch.policies.last().name =
                    branch.name + Policy.branchCompleteSuffix
            }
        }

        val beliefsFile = RulesetFile.Beliefs.file()
        if (beliefsFile.exists())
            beliefs += createHashmap(json().fromJsonFile(Array<Belief>::class.java, beliefsFile))

        val religionsFile = RulesetFile.Religions.file()
        if (religionsFile.exists())
            religions += json().fromJsonFile(Array<String>::class.java, religionsFile).toList()

        val ruinRewardsFile = RulesetFile.Ruins.file()
        if (ruinRewardsFile.exists())
            ruinRewards += createHashmap(json().fromJsonFile(Array<RuinReward>::class.java, ruinRewardsFile))

        val nationsFile = RulesetFile.Nations.file()
        if (nationsFile.exists()) {
            nations += createHashmap(json().fromJsonFile(Array<Nation>::class.java, nationsFile))
            for (nation in nations.values) nation.setTransients()
        }

        val difficultiesFile = RulesetFile.Difficulties.file()
        if (difficultiesFile.exists())
            difficulties += createHashmap(json().fromJsonFile(Array<Difficulty>::class.java, difficultiesFile))

        val globalUniquesFile = RulesetFile.GlobalUniques.file()
        if (globalUniquesFile.exists()) {
            globalUniques = json().fromJsonFile(GlobalUniques::class.java, globalUniquesFile)
            globalUniques.originRuleset = name
        }

        val victoryTypesFile = RulesetFile.VictoryTypes.file()
        if (victoryTypesFile.exists()) {
            victories += createHashmap(json().fromJsonFile(Array<Victory>::class.java, victoryTypesFile))
        }

        val cityStateTypesFile = RulesetFile.CityStateTypes.file()
        if (cityStateTypesFile.exists()) {
            cityStateTypes += createHashmap(json().fromJsonFile(Array<CityStateType>::class.java, cityStateTypesFile))
        }

        val personalitiesFile = RulesetFile.Personalities.file()
        if (personalitiesFile.exists()) {
            personalities += createHashmap(json().fromJsonFile(Array<Personality>::class.java, personalitiesFile))
        }

        val eventsFile = RulesetFile.Events.file()
        if (eventsFile.exists()) {
            events += createHashmap(json().fromJsonFile(Array<Event>::class.java, eventsFile))
        }

        // Tutorials exist per builtin ruleset or mod, but there's also a global file that's always loaded
        // Note we can't rely on UncivGame.Current here, so we do the same thing getBuiltinRulesetFileHandle in RulesetCache does
        if (Gdx.files != null) { // we're not running console mode
            val globalTutorialsFile = Gdx.files.internal("jsons").child(RulesetFile.Tutorials.filename)
            if (globalTutorialsFile.exists())
                tutorials += createHashmap(json().fromJsonFile(Array<Tutorial>::class.java, globalTutorialsFile))
        }
        
        val tutorialsFile = RulesetFile.Tutorials.file()
        if (tutorialsFile.exists())
            tutorials += createHashmap(json().fromJsonFile(Array<Tutorial>::class.java, tutorialsFile))

        // Add objects that might not be present in base ruleset mods, but are required
        if (modOptions.isBaseRuleset) {
            val fallbackRuleset by lazy { RulesetCache.getVanillaRuleset() } // clone at most once
            // This one should be temporary
            if (unitTypes.isEmpty()) {
                unitTypes.putAll(fallbackRuleset.unitTypes)
            }

            // These should be permanent
            if (!ruinRewardsFile.exists())
                ruinRewards.putAll(fallbackRuleset.ruinRewards)

            if (!globalUniquesFile.exists()) {
                globalUniques = fallbackRuleset.globalUniques
            }
            // If we have no victories, add all the default victories
            if (victories.isEmpty()) victories.putAll(fallbackRuleset.victories)

            if (speeds.isEmpty()) speeds.putAll(fallbackRuleset.speeds)
            if (difficulties.isEmpty()) difficulties.putAll(fallbackRuleset.difficulties)

            if (cityStateTypes.isEmpty())
                for (cityStateType in fallbackRuleset.cityStateTypes.values)
                    cityStateTypes[cityStateType.name] = CityStateType().apply {
                        name = cityStateType.name
                        color = cityStateType.color
                        friendBonusUniques = ArrayList(cityStateType.friendBonusUniques.filter {
                            UniqueValidator(this@Ruleset).checkUnique(
                                Unique(it),
                                false,
                                null,
                                true
                            ).isEmpty()
                        })
                        allyBonusUniques = ArrayList(cityStateType.allyBonusUniques.filter {
                            UniqueValidator(this@Ruleset).checkUnique(
                                Unique(it),
                                false,
                                null,
                                true
                            ).isEmpty()
                        })
                    }

            updateResourceTransients()
        }
    }

    /** Building costs are unique in that they are dependant on info in the technology part.
     *  This means that if you add a building in a mod, you want it to depend on the original tech values.
     *  Alternatively, if you edit a tech column's building costs, you want it to affect all buildings in that column.
     *  This deals with that
     *  */
    internal fun updateBuildingCosts() {
        for (building in buildings.values) {
            if (building.cost != -1) continue
            if (building.getMatchingUniques(UniqueType.Unbuildable).any { it.modifiers.isEmpty() }) continue
            val column = building.techColumn(this) ?: continue
            building.cost = if (building.isAnyWonder()) column.wonderCost else column.buildingCost
        }
    }

    /** Introduced to support UniqueType.ImprovesResources: gives a resource the chance to scan improvements */
    internal fun updateResourceTransients() {
        for (resource in tileResources.values)
            resource.setTransients(this)
    }

    @VisibleForTesting
    /** For use by class TestGame. Use only before triggering the globalUniques.uniqueObjects lazy. */
    fun addGlobalUniques(vararg uniques: String) {
        globalUniques.uniques.addAll(uniques)
    }

    /** Used for displaying a RuleSet's name */
    override fun toString() = when {
        name.isNotEmpty() -> name
        mods.size == 1 && RulesetCache[mods.first()]!!.modOptions.isBaseRuleset -> mods.first()
        else -> "Combined RuleSet ($mods)"
    }

    @Readonly
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

    fun getErrorList(tryFixUnknownUniques: Boolean = false) = RulesetValidator.create(this, tryFixUnknownUniques).getErrorList()
}
