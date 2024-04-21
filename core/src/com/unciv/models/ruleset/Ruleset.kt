package com.unciv.models.ruleset

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
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
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import com.unciv.utils.Log
import kotlin.collections.set

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
    var globalUniques = GlobalUniques()
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
    val units = LinkedHashMap<String, BaseUnit>()
    val unitPromotions = LinkedHashMap<String, Promotion>()
    val unitTypes = LinkedHashMap<String, UnitType>()
    var victories = LinkedHashMap<String, Victory>()
    var cityStateTypes = LinkedHashMap<String, CityStateType>()
    val personalities = LinkedHashMap<String, Personality>()
    val events = LinkedHashMap<String, Event>()
    var modOptions = ModOptions()
    //endregion

    //region cache fields
    val greatGeneralUnits by lazy {
        units.values.filter { it.hasUnique(UniqueType.GreatPersonFromCombat, StateForConditionals.IgnoreConditionals) }
    }

    val tileRemovals by lazy { tileImprovements.values.filter { it.name.startsWith(Constants.remove) } }

    /** Contains all happiness levels that moving *from* them, to one *below* them, can change uniques that apply */
    val allHappinessLevelsThatAffectUniques by lazy {
        sequence {
            for (rulesetObject in (units.values + terrains.values + buildings.values + technologies.values + eras.values
                + beliefs.values + unitPromotions.values + tileResources.values + policies.values + tileImprovements.values + globalUniques))
                for (unique in rulesetObject.uniqueObjects)
                    for (conditional in unique.conditionals){
                        if (conditional.type == UniqueType.ConditionalBelowHappiness) yield(conditional.params[0].toInt())
                        if (conditional.type == UniqueType.ConditionalBetweenHappiness){
                            yield(conditional.params[0].toInt())
                            yield(conditional.params[1].toInt() + 1)
                        }
                        if (conditional.type == UniqueType.ConditionalHappy) yield(0)
                    }
        }.toSet()
    }

    val roadImprovement by lazy { RoadStatus.Road.improvement(this) }
    val railroadImprovement by lazy { RoadStatus.Railroad.improvement(this) }
    //endregion

    fun clone(): Ruleset {
        val newRuleset = Ruleset()
        newRuleset.add(this)
        return newRuleset
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>(items.size)
        for (item in items) {
            hashMap[item.name] = item
            (item as? IRulesetObject)?.originRuleset = name
        }
        return hashMap
    }

    fun add(ruleset: Ruleset) {
        beliefs.putAll(ruleset.beliefs)
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
        globalUniques = GlobalUniques().apply {
            uniques.addAll(globalUniques.uniques)
            uniques.addAll(ruleset.globalUniques.uniques)
        }
        ruleset.modOptions.nationsToRemove
            .flatMap { nationToRemove ->
                nations.filter { it.value.matchesFilter(nationToRemove) }.keys
            }.toSet().forEach {
                nations.remove(it)
            }
        nations.putAll(ruleset.nations)
        policyBranches.putAll(ruleset.policyBranches)
        policies.putAll(ruleset.policies)
        quests.putAll(ruleset.quests)
        religions.addAll(ruleset.religions)
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
        unitTypes.putAll(ruleset.unitTypes)
        victories.putAll(ruleset.victories)
        cityStateTypes.putAll(ruleset.cityStateTypes)
        ruleset.modOptions.unitsToRemove
            .flatMap { unitToRemove ->
                units.filter { it.apply { value.ruleset = this@Ruleset }.value.matchesFilter(unitToRemove) }.keys
            }.toSet().forEach {
                units.remove(it)
            }
        units.putAll(ruleset.units)
        personalities.putAll(ruleset.personalities)
        events.putAll(ruleset.events)
        modOptions.uniques.addAll(ruleset.modOptions.uniques)
        modOptions.constants.merge(ruleset.modOptions.constants)

        unitPromotions.putAll(ruleset.unitPromotions)

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
        unitPromotions.clear()
        units.clear()
        unitTypes.clear()
        victories.clear()
        cityStateTypes.clear()
        personalities.clear()
        events.clear()
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
            unitTypes.values.asSequence() +
            personalities.values.asSequence()
            // Victories is only INamed
    fun allIHasUniques(): Sequence<IHasUniques> =
            allRulesetObjects() + sequenceOf(modOptions)
    fun allICivilopediaText(): Sequence<ICivilopediaText> =
            allRulesetObjects() + events.values + events.values.flatMap { it.choices }

    fun load(folderHandle: FileHandle) {
        // Note: Most files are loaded using createHashmap, which sets originRuleset automatically.
        // For other files containing IRulesetObject's we'll have to remember to do so manually - e.g. Tech.
        val modOptionsFile = folderHandle.child("ModOptions.json")
        if (modOptionsFile.exists()) {
            try {
                modOptions = json().fromJsonFile(ModOptions::class.java, modOptionsFile)
                modOptions.updateDeprecations()
            } catch (ex: Exception) {
                Log.error("Failed to get modOptions from json file", ex)
            }
        }

        val techFile = folderHandle.child("Techs.json")
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

        val buildingsFile = folderHandle.child("Buildings.json")
        if (buildingsFile.exists()) buildings += createHashmap(json().fromJsonFile(Array<Building>::class.java, buildingsFile))

        val terrainsFile = folderHandle.child("Terrains.json")
        if (terrainsFile.exists()) {
            terrains += createHashmap(json().fromJsonFile(Array<Terrain>::class.java, terrainsFile))
            for (terrain in terrains.values) {
                terrain.originRuleset = name
                terrain.setTransients()
            }
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
            globalUniques.originRuleset = name
        }

        val victoryTypesFile = folderHandle.child("VictoryTypes.json")
        if (victoryTypesFile.exists()) {
            victories += createHashmap(json().fromJsonFile(Array<Victory>::class.java, victoryTypesFile))
        }

        val cityStateTypesFile = folderHandle.child("CityStateTypes.json")
        if (cityStateTypesFile.exists()) {
            cityStateTypes += createHashmap(json().fromJsonFile(Array<CityStateType>::class.java, cityStateTypesFile))
        }

        val personalitiesFile = folderHandle.child("Personalities.json")
        if (personalitiesFile.exists()) {
            personalities += createHashmap(json().fromJsonFile(Array<Personality>::class.java, personalitiesFile))
        }

        val eventsFile = folderHandle.child("Events.json")
        if (eventsFile.exists()) {
            events += createHashmap(json().fromJsonFile(Array<Event>::class.java, eventsFile))
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
        }
    }

    /** Building costs are unique in that they are dependant on info in the technology part.
     *  This means that if you add a building in a mod, you want it to depend on the original tech values.
     *  Alternatively, if you edit a tech column's building costs, you want it to affect all buildings in that column.
     *  This deals with that
     *  */
    fun updateBuildingCosts() {
        for (building in buildings.values) {
            if (building.cost == -1 && building.getMatchingUniques(UniqueType.Unbuildable).none { it.conditionals.isEmpty() }) {
                val column = building.techColumn(this)
                if (column != null) {
                    building.cost = if (building.isAnyWonder()) column.wonderCost else column.buildingCost
                }
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
