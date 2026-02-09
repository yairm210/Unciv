package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonReader
import com.unciv.Constants
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.logic.GameInfo
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.Counter
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Difficulty
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.nation.Personality
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
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
import com.unciv.models.stats.Stats
import com.unciv.models.stats.SubStat
import com.unciv.platform.PlatformCapabilities
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.FormattedLine
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

    private fun <T : INamed> loadNamedArray(arrayClass: Class<Array<T>>, fileHandle: FileHandle): Array<T> {
        val items = json().fromJsonFile(arrayClass, fileHandle)
        if (PlatformCapabilities.current.backgroundThreadPools) return items

        fun com.badlogic.gdx.utils.JsonValue.readStringOrNull(name: String): String? {
            val value = getString(name, "")
            return value.takeIf { it.isNotBlank() }
        }

        fun com.badlogic.gdx.utils.JsonValue.readStringArray(name: String): ArrayList<String> {
            val values = ArrayList<String>()
            var cursor = get(name)?.child
            while (cursor != null) {
                val item = cursor.asString()
                if (item.isNotBlank()) values += item
                cursor = cursor.next
            }
            return values
        }

        fun com.badlogic.gdx.utils.JsonValue.readIntArray(name: String): ArrayList<Int> {
            val values = ArrayList<Int>()
            var cursor = get(name)?.child
            while (cursor != null) {
                values += cursor.asInt()
                cursor = cursor.next
            }
            return values
        }

        fun com.badlogic.gdx.utils.JsonValue.readStats(defaultStats: Stats? = null): Stats {
            val stats = defaultStats?.clone() ?: Stats()
            stats.production = getFloat("production", stats.production)
            stats.food = getFloat("food", stats.food)
            stats.gold = getFloat("gold", stats.gold)
            stats.science = getFloat("science", stats.science)
            stats.culture = getFloat("culture", stats.culture)
            stats.happiness = getFloat("happiness", stats.happiness)
            stats.faith = getFloat("faith", stats.faith)
            return stats
        }

        fun com.badlogic.gdx.utils.JsonValue.readCounter(name: String): Counter<String>? {
            val rawCounter = get(name) ?: return null
            val values = Counter<String>()
            var cursor = rawCounter.child
            while (cursor != null) {
                val key = cursor.name ?: ""
                if (key.isNotBlank()) {
                    values[key] = if (cursor.isValue) cursor.asInt() else cursor.getInt("value", 0)
                }
                cursor = cursor.next
            }
            return values
        }

        fun com.badlogic.gdx.utils.JsonValue.readDepositAmount(name: String): TileResource.DepositAmount? {
            val rawAmount = get(name) ?: return null
            return TileResource.DepositAmount().apply {
                sparse = rawAmount.getInt("sparse", sparse)
                default = rawAmount.getInt("default", default)
                abundant = rawAmount.getInt("abundant", abundant)
            }
        }

        val rawArray = JsonReader().parse(fileHandle)
        val rawEntries = ArrayList<com.badlogic.gdx.utils.JsonValue>()
        val rawByName = LinkedHashMap<String, com.badlogic.gdx.utils.JsonValue>()
        var rawCursor = rawArray.child
        while (rawCursor != null) {
            rawEntries.add(rawCursor)
            val rawName = rawCursor.getString("name", "")
            if (rawName.isNotBlank()) rawByName[rawName] = rawCursor
            rawCursor = rawCursor.next
        }

        for (index in items.indices) {
            val item = items[index]
            val rawByIndex = rawEntries.getOrNull(index)
            val rawName = rawByIndex?.getString("name", "") ?: ""
            if (rawName.isNotBlank()) {
                // On TeaVM some entries can deserialize with missing/incorrect names.
                // Always trust the source JSON "name" field for deterministic map keys.
                item.name = rawName
            } else {
                val currentName = try { item.name } catch (_: Exception) { "" }
                if (currentName.isBlank()) {
                    val fallbackName = rawEntries.getOrNull(index)?.getString("name", "") ?: ""
                    if (fallbackName.isNotBlank()) item.name = fallbackName
                }
            }
            val effectiveName = try { item.name } catch (_: Exception) { "" }
            val raw = when {
                effectiveName.isNotBlank() && rawByName.containsKey(effectiveName) -> rawByName[effectiveName]!!
                rawEntries.getOrNull(index) != null -> rawEntries[index]
                else -> continue
            }

            if (item is RulesetObject && item.uniques.isEmpty()) {
                val rawUniques = raw.readStringArray("uniques")
                if (rawUniques.isNotEmpty()) item.uniques = rawUniques
            }

            if (item is RulesetStatsObject) {
                item.production = raw.getFloat("production", item.production)
                item.food = raw.getFloat("food", item.food)
                item.gold = raw.getFloat("gold", item.gold)
                item.science = raw.getFloat("science", item.science)
                item.culture = raw.getFloat("culture", item.culture)
                item.happiness = raw.getFloat("happiness", item.happiness)
                item.faith = raw.getFloat("faith", item.faith)
                if (item.uniques.isEmpty()) {
                    val rawUniques = raw.readStringArray("uniques")
                    if (rawUniques.isNotEmpty()) item.uniques = rawUniques
                }
            }

            if (item is UnitType) {
                val fallbackMovementType = raw.readStringOrNull("movementType")
                if (fallbackMovementType != null) item.movementType = fallbackMovementType
            }

            if (item is BaseUnit) {
                val fallbackUnitType = raw.readStringOrNull("unitType")
                if (fallbackUnitType != null) item.unitType = fallbackUnitType

                item.cost = raw.getInt("cost", item.cost)
                item.hurryCostModifier = raw.getInt("hurryCostModifier", item.hurryCostModifier)
                item.movement = raw.getInt("movement", item.movement)
                item.strength = raw.getInt("strength", item.strength)
                item.rangedStrength = raw.getInt("rangedStrength", item.rangedStrength)
                item.religiousStrength = raw.getInt("religiousStrength", item.religiousStrength)
                item.range = raw.getInt("range", item.range)
                item.interceptRange = raw.getInt("interceptRange", item.interceptRange)

                item.requiredTech = raw.readStringOrNull("requiredTech")
                item.requiredResource = raw.readStringOrNull("requiredResource")
                item.obsoleteTech = raw.readStringOrNull("obsoleteTech")
                item.upgradesTo = raw.readStringOrNull("upgradesTo")
                item.replaces = raw.readStringOrNull("replaces")
                item.uniqueTo = raw.readStringOrNull("uniqueTo")
                item.attackSound = raw.readStringOrNull("attackSound")

                val replacementText = raw.getString("replacementTextForUniques", item.replacementTextForUniques)
                if (replacementText.isNotBlank()) item.replacementTextForUniques = replacementText

                val rawPromotions = raw.readStringArray("promotions")
                if (rawPromotions.isNotEmpty()) item.promotions = rawPromotions.toHashSet()

                val rawUniques = raw.readStringArray("uniques")
                if (rawUniques.isNotEmpty()) item.uniques = rawUniques
            }
            if (item is Terrain) {
                val hasType = try {
                    item.type
                    true
                } catch (_: Exception) {
                    false
                }
                if (!hasType) {
                    val fallbackType = raw.getString("type", "")
                    if (fallbackType.isNotBlank()) {
                        runCatching { TerrainType.valueOf(fallbackType) }
                            .onSuccess { item.type = it }
                    }
                }

                item.overrideStats = raw.getBoolean("overrideStats", item.overrideStats)
                item.unbuildable = raw.getBoolean("unbuildable", item.unbuildable)
                item.turnsInto = raw.readStringOrNull("turnsInto")
                item.weight = raw.getInt("weight", item.weight)
                item.movementCost = raw.getInt("movementCost", item.movementCost)
                item.defenceBonus = raw.getFloat("defenceBonus", item.defenceBonus)
                item.impassable = raw.getBoolean("impassable", item.impassable)

                if (raw.get("occursOn") != null) {
                    item.occursOn.clear()
                    item.occursOn.addAll(raw.readStringArray("occursOn"))
                }
                if (raw.get("RGB") != null) {
                    item.RGB = raw.readIntArray("RGB")
                }
            }

            if (item is TileResource) {
                raw.readStringOrNull("resourceType")
                    ?.let { runCatching { ResourceType.valueOf(it) }.onSuccess { kind -> item.resourceType = kind } }
                if (raw.get("terrainsCanBeFoundOn") != null) {
                    item.terrainsCanBeFoundOn = raw.readStringArray("terrainsCanBeFoundOn")
                }
                item.revealedBy = raw.readStringOrNull("revealedBy")
                item.improvement = raw.readStringOrNull("improvement")
                if (raw.get("improvedBy") != null) {
                    item.improvedBy = raw.readStringArray("improvedBy")
                }
                raw.readDepositAmount("majorDepositAmount")?.let { item.majorDepositAmount = it }
                raw.readDepositAmount("minorDepositAmount")?.let { item.minorDepositAmount = it }
                raw.get("improvementStats")?.let {
                    item.improvementStats = it.readStats(item.improvementStats)
                }
            }

            if (item is TileImprovement) {
                item.replaces = raw.readStringOrNull("replaces")
                if (raw.get("terrainsCanBeBuiltOn") != null) {
                    item.terrainsCanBeBuiltOn = raw.readStringArray("terrainsCanBeBuiltOn")
                }
                item.techRequired = raw.readStringOrNull("techRequired")
                item.uniqueTo = raw.readStringOrNull("uniqueTo")
                item.turnsToBuild = raw.getInt("turnsToBuild", item.turnsToBuild)
            }

            if (item is Building) {
                item.requiredTech = raw.readStringOrNull("requiredTech")
                item.cost = raw.getInt("cost", item.cost)
                item.maintenance = raw.getInt("maintenance", item.maintenance)
                item.hurryCostModifier = raw.getInt("hurryCostModifier", item.hurryCostModifier)
                item.isWonder = raw.getBoolean("isWonder", item.isWonder)
                item.isNationalWonder = raw.getBoolean("isNationalWonder", item.isNationalWonder)
                item.requiredBuilding = raw.readStringOrNull("requiredBuilding")
                item.requiredResource = raw.readStringOrNull("requiredResource")
                if (raw.get("requiredNearbyImprovedResources") != null) {
                    item.requiredNearbyImprovedResources = raw.readStringArray("requiredNearbyImprovedResources")
                }
                item.cityStrength = raw.getFloat("cityStrength", item.cityStrength.toFloat()).toDouble()
                item.cityHealth = raw.getInt("cityHealth", item.cityHealth)
                item.replaces = raw.readStringOrNull("replaces")
                item.uniqueTo = raw.readStringOrNull("uniqueTo")
                item.quote = raw.getString("quote", item.quote)
                item.replacementTextForUniques =
                    raw.getString("replacementTextForUniques", item.replacementTextForUniques)
                raw.readCounter("specialistSlots")?.let { item.specialistSlots = it }
                raw.readCounter("greatPersonPoints")?.let { item.greatPersonPoints = it }
            }

            if (item is Promotion) {
                if (raw.get("prerequisites") != null) item.prerequisites = raw.readStringArray("prerequisites")
                if (raw.get("unitTypes") != null) item.unitTypes = raw.readStringArray("unitTypes")
                item.row = raw.getInt("row", item.row)
                item.column = raw.getInt("column", item.column)
                if (raw.get("innerColor") != null) item.innerColor = raw.readIntArray("innerColor")
                if (raw.get("outerColor") != null) item.outerColor = raw.readIntArray("outerColor")
            }

            if (item is Belief) {
                val fallbackType = raw.getString("type", "")
                if (fallbackType.isNotBlank()) {
                    runCatching { BeliefType.valueOf(fallbackType) }
                        .onSuccess { item.type = it }
                }
            }

            if (item is Nation) {
                item.leaderName = raw.getString("leaderName", item.leaderName)
                item.cityStateType = raw.readStringOrNull("cityStateType")
                item.preferredVictoryType = raw.getString("preferredVictoryType", item.preferredVictoryType)
                item.uniqueName = raw.getString("uniqueName", item.uniqueName)
                item.uniqueText = raw.getString("uniqueText", item.uniqueText)
                item.declaringWar = raw.getString("declaringWar", item.declaringWar)
                item.attacked = raw.getString("attacked", item.attacked)
                item.defeated = raw.getString("defeated", item.defeated)
                item.denounced = raw.getString("denounced", item.denounced)
                item.declaringFriendship = raw.getString("declaringFriendship", item.declaringFriendship)
                item.introduction = raw.getString("introduction", item.introduction)
                item.tradeRequest = raw.getString("tradeRequest", item.tradeRequest)
                item.neutralHello = raw.getString("neutralHello", item.neutralHello)
                item.hateHello = raw.getString("hateHello", item.hateHello)
                if (raw.get("outerColor") != null) item.outerColor = raw.readIntArray("outerColor")
                if (raw.get("innerColor") != null) item.innerColor = raw.readIntArray("innerColor")
                if (raw.get("startBias") != null) item.startBias = raw.readStringArray("startBias")
                item.personality = raw.readStringOrNull("personality")
                item.startIntroPart1 = raw.getString("startIntroPart1", item.startIntroPart1)
                item.startIntroPart2 = raw.getString("startIntroPart2", item.startIntroPart2)
                item.favoredReligion = raw.readStringOrNull("favoredReligion")
                if (raw.get("spyNames") != null) item.spyNames = raw.readStringArray("spyNames")
                if (raw.get("cities") != null) item.cities = raw.readStringArray("cities")
            }

            if (item is CityStateType) {
                if (raw.get("friendBonusUniques") != null) item.friendBonusUniques = raw.readStringArray("friendBonusUniques")
                if (raw.get("allyBonusUniques") != null) item.allyBonusUniques = raw.readStringArray("allyBonusUniques")
                if (raw.get("color") != null) item.color = raw.readIntArray("color")
            }

            if (item is UnitNameGroup && raw.get("unitNames") != null) {
                item.unitNames = raw.readStringArray("unitNames")
            }

            if (item is Tutorial) {
                item.category = raw.readStringOrNull("category")
                if (raw.get("steps") != null) item.steps = raw.readStringArray("steps")
                if (item.civilopediaText.isEmpty() && raw.get("civilopediaText") != null) {
                    val lines = ArrayList<FormattedLine>()
                    var lineRaw = raw.get("civilopediaText")?.child
                    while (lineRaw != null) {
                        val text = lineRaw.getString("text", "")
                        val separator = lineRaw.getBoolean("separator", false)
                        if (separator) lines += FormattedLine(separator = true)
                        else if (text.isNotBlank()) lines += FormattedLine(text = text)
                        lineRaw = lineRaw.next
                    }
                    if (lines.isNotEmpty()) item.civilopediaText = lines
                }
            }

            if (item is Speed && item.turns.isEmpty()) {
                var turnRow = raw.get("turns")?.child
                while (turnRow != null) {
                    val yearsPerTurn = turnRow.getFloat("yearsPerTurn", Float.NaN)
                    val untilTurn = turnRow.getFloat("untilTurn", Float.NaN)
                    if (!yearsPerTurn.isNaN() && !untilTurn.isNaN()) {
                        item.turns += hashMapOf(
                            "yearsPerTurn" to yearsPerTurn,
                            "untilTurn" to untilTurn
                        )
                    }
                    turnRow = turnRow.next
                }
            }
            if (item is Speed) {
                item.modifier = raw.getFloat("modifier", item.modifier)
                item.goldCostModifier = raw.getFloat("goldCostModifier", item.goldCostModifier)
                item.productionCostModifier = raw.getFloat("productionCostModifier", item.productionCostModifier)
                item.scienceCostModifier = raw.getFloat("scienceCostModifier", item.scienceCostModifier)
                item.cultureCostModifier = raw.getFloat("cultureCostModifier", item.cultureCostModifier)
                item.faithCostModifier = raw.getFloat("faithCostModifier", item.faithCostModifier)
                item.goldGiftModifier = raw.getFloat("goldGiftModifier", item.goldGiftModifier)
                item.cityStateTributeScalingInterval =
                    raw.getFloat("cityStateTributeScalingInterval", item.cityStateTributeScalingInterval)
                item.barbarianModifier = raw.getFloat("barbarianModifier", item.barbarianModifier)
                item.improvementBuildLengthModifier =
                    raw.getFloat("improvementBuildLengthModifier", item.improvementBuildLengthModifier)
                item.goldenAgeLengthModifier =
                    raw.getFloat("goldenAgeLengthModifier", item.goldenAgeLengthModifier)
                item.religiousPressureAdjacentCity =
                    raw.getInt("religiousPressureAdjacentCity", item.religiousPressureAdjacentCity)
                item.peaceDealDuration = raw.getInt("peaceDealDuration", item.peaceDealDuration)
                item.dealDuration = raw.getInt("dealDuration", item.dealDuration)
                item.startYear = raw.getFloat("startYear", item.startYear)
            }
            if (item is Victory) {
                if (item.milestones.isEmpty()) {
                    var milestone = raw.get("milestones")?.child
                    while (milestone != null) {
                        val milestoneText = milestone.asString()
                        if (milestoneText.isNotBlank()) item.milestones += milestoneText
                        milestone = milestone.next
                    }
                }
                if (item.requiredSpaceshipParts.isEmpty()) {
                    var spaceshipPart = raw.get("requiredSpaceshipParts")?.child
                    while (spaceshipPart != null) {
                        val partName = spaceshipPart.asString()
                        if (partName.isNotBlank()) item.requiredSpaceshipParts += partName
                        spaceshipPart = spaceshipPart.next
                    }
                }
            }
            if (item is PolicyBranch) {
                if (item.priorities.isEmpty()) {
                    var priority = raw.get("priorities")?.child
                    while (priority != null) {
                        val key = priority.name ?: ""
                        if (key.isNotBlank()) item.priorities[key] = priority.asInt()
                        priority = priority.next
                    }
                }
                if (item.policies.isEmpty()) {
                    var policyRaw = raw.get("policies")?.child
                    while (policyRaw != null) {
                        val policyName = policyRaw.getString("name", "")
                        if (policyName.isNotBlank()) {
                            val policy = Policy().apply {
                                name = policyName
                                row = policyRaw.getInt("row", 0)
                                column = policyRaw.getInt("column", 0)
                                var unique = policyRaw.get("uniques")?.child
                                while (unique != null) {
                                    val uniqueText = unique.asString()
                                    if (uniqueText.isNotBlank()) uniques += uniqueText
                                    unique = unique.next
                                }
                                val requires = ArrayList<String>()
                                var requirement = policyRaw.get("requires")?.child
                                while (requirement != null) {
                                    val requirementText = requirement.asString()
                                    if (requirementText.isNotBlank()) requires += requirementText
                                    requirement = requirement.next
                                }
                                this.requires = if (requires.isEmpty()) null else requires
                            }
                            item.policies += policy
                        }
                        policyRaw = policyRaw.next
                    }
                }
            }
        }
        return items
    }

    private fun loadTechColumns(fileHandle: FileHandle): Array<TechColumn> {
        val parsed = json().fromJsonFile(Array<TechColumn>::class.java, fileHandle)
        if (PlatformCapabilities.current.backgroundThreadPools) return parsed

        fun com.badlogic.gdx.utils.JsonValue.readStringArray(name: String): ArrayList<String> {
            val values = ArrayList<String>()
            var cursor = get(name)?.child
            while (cursor != null) {
                val value = cursor.asString()
                if (value.isNotBlank()) values += value
                cursor = cursor.next
            }
            return values
        }

        val rawRoot = JsonReader().parse(fileHandle)
        if (!rawRoot.isArray) return parsed

        val columns = ArrayList<TechColumn>()
        var rawColumn = rawRoot.child
        while (rawColumn != null) {
            val column = TechColumn().apply {
                columnNumber = rawColumn.getInt("columnNumber", 0)
                techCost = rawColumn.getInt("techCost", 0)
                buildingCost = rawColumn.getInt("buildingCost", -1)
                wonderCost = rawColumn.getInt("wonderCost", -1)
                val eraValue = rawColumn.getString("era", "")
                era = if (eraValue.isNotBlank()) eraValue else "Ancient era"
            }

            val technologies = ArrayList<Technology>()
            var rawTech = rawColumn.get("techs")?.child
            while (rawTech != null) {
                val nameValue = rawTech.getString("name", "")
                if (nameValue.isNotBlank()) {
                    val tech = Technology().apply {
                        name = nameValue
                        cost = rawTech.getInt("cost", 0)
                        row = rawTech.getInt("row", 0)
                        quote = rawTech.getString("quote", "")
                        uniques = rawTech.readStringArray("uniques")
                        prerequisites = rawTech.readStringArray("prerequisites").toHashSet()
                    }
                    technologies += tech
                }
                rawTech = rawTech.next
            }
            column.techs = technologies
            columns += column
            rawColumn = rawColumn.next
        }

        return if (columns.isEmpty()) parsed else columns.toTypedArray()
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
            val techColumns = loadTechColumns(techFile)
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
        if (buildingsFile.exists()) buildings += createHashmap(loadNamedArray(Array<Building>::class.java, buildingsFile))

        val terrainsFile = RulesetFile.Terrains.file()
        if (terrainsFile.exists()) {
            terrains += createHashmap(loadNamedArray(Array<Terrain>::class.java, terrainsFile))
            for (terrain in terrains.values) {
                terrain.originRuleset = name
                terrain.setTransients()
            }
        }

        val resourcesFile = RulesetFile.TileResources.file()
        if (resourcesFile.exists()) tileResources += createHashmap(loadNamedArray(Array<TileResource>::class.java, resourcesFile))

        val improvementsFile = RulesetFile.TileImprovements.file()
        if (improvementsFile.exists()) tileImprovements += createHashmap(loadNamedArray(Array<TileImprovement>::class.java, improvementsFile))

        val erasFile = RulesetFile.Eras.file()
        if (erasFile.exists()) eras += createHashmap(loadNamedArray(Array<Era>::class.java, erasFile))
        // While `eras.values.toList()` might seem more logical, eras.values is a MutableCollection and
        // therefore does not guarantee keeping the order of elements like a LinkedHashMap does.
        // Using map{} sidesteps this problem
        eras.map { it.value }.withIndex().forEach { it.value.eraNumber = it.index }

        val speedsFile = RulesetFile.Speeds.file()
        if (speedsFile.exists()) {
            speeds += createHashmap(loadNamedArray(Array<Speed>::class.java, speedsFile))
        }

        val unitTypesFile = RulesetFile.UnitTypes.file()
        if (unitTypesFile.exists()) unitTypes += createHashmap(loadNamedArray(Array<UnitType>::class.java, unitTypesFile))

        val unitsFile = RulesetFile.Units.file()
        if (unitsFile.exists()) units += createHashmap(loadNamedArray(Array<BaseUnit>::class.java, unitsFile))

        val promotionsFile = RulesetFile.UnitPromotions.file()
        if (promotionsFile.exists()) unitPromotions += createHashmap(loadNamedArray(Array<Promotion>::class.java, promotionsFile))

        val unitNameGroupsFile = RulesetFile.UnitNameGroup.file()
        if (unitNameGroupsFile.exists()) unitNameGroups += createHashmap(loadNamedArray(Array<UnitNameGroup>::class.java, unitNameGroupsFile))

        val questsFile = RulesetFile.Quests.file()
        if (questsFile.exists()) quests += createHashmap(loadNamedArray(Array<Quest>::class.java, questsFile))

        val specialistsFile = RulesetFile.Specialists.file()
        if (specialistsFile.exists()) specialists += createHashmap(loadNamedArray(Array<Specialist>::class.java, specialistsFile))

        val policiesFile = RulesetFile.Policies.file()
        if (policiesFile.exists()) {
            policyBranches += createHashmap(
                loadNamedArray(Array<PolicyBranch>::class.java, policiesFile)
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
                if (branch.policies.isNotEmpty()) {
                    branch.policies.last().name =
                        branch.name + Policy.branchCompleteSuffix
                } else {
                    Log.error("PolicyBranch '${branch.name}' has no policies after load (ruleset=$name)")
                }
            }
        }

        val beliefsFile = RulesetFile.Beliefs.file()
        if (beliefsFile.exists())
            beliefs += createHashmap(loadNamedArray(Array<Belief>::class.java, beliefsFile))

        val religionsFile = RulesetFile.Religions.file()
        if (religionsFile.exists())
            religions += json().fromJsonFile(Array<String>::class.java, religionsFile).toList()

        val ruinRewardsFile = RulesetFile.Ruins.file()
        if (ruinRewardsFile.exists())
            ruinRewards += createHashmap(loadNamedArray(Array<RuinReward>::class.java, ruinRewardsFile))

        val nationsFile = RulesetFile.Nations.file()
        if (nationsFile.exists()) {
            nations += createHashmap(loadNamedArray(Array<Nation>::class.java, nationsFile))
            for (nation in nations.values) nation.setTransients()
        }

        val difficultiesFile = RulesetFile.Difficulties.file()
        if (difficultiesFile.exists())
            difficulties += createHashmap(loadNamedArray(Array<Difficulty>::class.java, difficultiesFile))

        val globalUniquesFile = RulesetFile.GlobalUniques.file()
        if (globalUniquesFile.exists()) {
            globalUniques = json().fromJsonFile(GlobalUniques::class.java, globalUniquesFile)
            globalUniques.originRuleset = name
        }

        val victoryTypesFile = RulesetFile.VictoryTypes.file()
        if (victoryTypesFile.exists()) {
            victories += createHashmap(loadNamedArray(Array<Victory>::class.java, victoryTypesFile))
        }

        val cityStateTypesFile = RulesetFile.CityStateTypes.file()
        if (cityStateTypesFile.exists()) {
            cityStateTypes += createHashmap(loadNamedArray(Array<CityStateType>::class.java, cityStateTypesFile))
        }

        val personalitiesFile = RulesetFile.Personalities.file()
        if (personalitiesFile.exists()) {
            personalities += createHashmap(loadNamedArray(Array<Personality>::class.java, personalitiesFile))
        }

        val eventsFile = RulesetFile.Events.file()
        if (eventsFile.exists()) {
            events += createHashmap(loadNamedArray(Array<Event>::class.java, eventsFile))
        }

        // Tutorials exist per builtin ruleset or mod, but there's also a global file that's always loaded
        // Note we can't rely on UncivGame.Current here, so we do the same thing getBuiltinRulesetFileHandle in RulesetCache does
        if (Gdx.files != null) { // we're not running console mode
            val globalTutorialsFile = Gdx.files.internal("jsons").child(RulesetFile.Tutorials.filename)
            if (globalTutorialsFile.exists())
                tutorials += createHashmap(loadNamedArray(Array<Tutorial>::class.java, globalTutorialsFile))
        }
        val tutorialsFile = RulesetFile.Tutorials.file()
        if (tutorialsFile.exists())
            tutorials += createHashmap(loadNamedArray(Array<Tutorial>::class.java, tutorialsFile))

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

        }
        updateResourceTransients()
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
