package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.HashMapVector2
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.UncivShowableException
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.WorkerAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.RuinsManager.RuinsManager
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitMovementAlgorithms
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeRequest
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Difficulty
import com.unciv.models.ruleset.Era
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.TemporaryUnique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.utils.MayaCalendar
import com.unciv.ui.utils.extensions.toPercent
import com.unciv.ui.utils.extensions.withItem
import com.unciv.ui.victoryscreen.RankingType
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Proximity : IsPartOfGameInfoSerialization {
    None, // ie no cities
    Neighbors,
    Close,
    Far,
    Distant
}

class CivilizationInfo : IsPartOfGameInfoSerialization {

    @Transient
    private var workerAutomationCache: WorkerAutomation? = null
    /** Returns an instance of WorkerAutomation valid for the duration of the current turn
     * This instance carries cached data common for all Workers of this civ */
    fun getWorkerAutomation(): WorkerAutomation {
        val currentTurn = if (UncivGame.Current.isInitialized && UncivGame.Current.gameInfo != null) {
            UncivGame.Current.gameInfo!!.turns
        } else {
            0
        }
        if (workerAutomationCache == null || workerAutomationCache!!.cachedForTurn != currentTurn)
            workerAutomationCache = WorkerAutomation(this, currentTurn)
        return workerAutomationCache!!
    }

    @Transient
    lateinit var gameInfo: GameInfo

    @Transient
    lateinit var nation: Nation

    /**
     * We never add or remove from here directly, could cause comodification problems.
     * Instead, we create a copy list with the change, and replace this list.
     * The other solution, casting toList() every "get", has a performance cost
     */
    @Transient
    private var units = listOf<MapUnit>()

    /**
     * Index in the unit list above of the unit that is potentially due and is next up for button "Next unit".
     */
    @Transient
    private var nextPotentiallyDueAt = 0

    @Transient
    var viewableTiles = setOf<TileInfo>()

    @Transient
    var viewableInvisibleUnitsTiles = setOf<TileInfo>()

    /** Contains mapping of cities to travel mediums from ALL civilizations connected by trade routes to the capital */
    @Transient
    var citiesConnectedToCapitalToMediums = mapOf<CityInfo, Set<String>>()

    /** This is for performance since every movement calculation depends on this, see MapUnit comment */
    @Transient
    var hasActiveGreatWall = false

    @Transient
    var statsForNextTurn = Stats()

    @Transient
    var happinessForNextTurn = 0

    @Transient
    var detailedCivResources = ResourceSupplyList()

    @Transient
    var summarizedCivResources = ResourceSupplyList()

    @Transient
    val cityStateFunctions = CityStateFunctions(this)

    @Transient
    private var cachedMilitaryMight = -1

    @Transient
    var passThroughImpassableUnlocked = false   // Cached Boolean equal to passableImpassables.isNotEmpty()

    @Transient
    var nonStandardTerrainDamage = false

    @Transient
    var lastEraResourceUsedForBuilding = HashMap<String, Int>()

    @Transient
    val lastEraResourceUsedForUnit = HashMap<String, Int>()

    @Transient
    var thingsToFocusOnForVictory = setOf<Victory.Focus>()

    var playerType = PlayerType.AI

    /** Used in online multiplayer for human players */
    var playerId = ""
    /** The Civ's gold reserves. Public get, private set - please use [addGold] method to modify. */
    var gold = 0
        private set
    var civName = ""
    var tech = TechManager()
    var policies = PolicyManager()
    var civConstructions = CivConstructions()
    var questManager = QuestManager()
    var religionManager = ReligionManager()
    var goldenAges = GoldenAgeManager()
    var greatPeople = GreatPersonManager()
    var espionageManager = EspionageManager()
    var victoryManager = VictoryManager()
    var ruinsManager = RuinsManager()
    var diplomacy = HashMap<String, DiplomacyManager>()
    var proximity = HashMap<String, Proximity>()
    val popupAlerts = ArrayList<PopupAlert>()
    private var allyCivName: String? = null
    var naturalWonders = ArrayList<String>()

    var notifications = ArrayList<Notification>()

    var notificationsLog = ArrayList<NotificationsLog>()
    class NotificationsLog(val turn: Int = 0) {
        var notifications = ArrayList<Notification>()
    }

    /** for trades here, ourOffers is the current civ's offers, and theirOffers is what the requesting civ offers  */
    val tradeRequests = ArrayList<TradeRequest>()

    /** See DiplomacyManager.flagsCountdown for why this does not map Enums to ints */
    private var flagsCountdown = HashMap<String, Int>()

    /** Arraylist instead of HashMap as the same unique might appear multiple times
     * We don't use pairs, as these cannot be serialized due to having no no-arg constructor
     * This can also contain NON-temporary uniques but I can't be bothered to do the deprecation dance with this one
     */
    val temporaryUniques = ArrayList<TemporaryUnique>()

    // if we only use lists, and change the list each time the cities are changed,
    // we won't get concurrent modification exceptions.
    // This is basically a way to ensure our lists are immutable.
    var cities = listOf<CityInfo>()
    var citiesCreated = 0
    var exploredTiles = HashSet<Vector2>()

    @Deprecated("Only for backward compatibility, will have no values after GameInfo.setTransients",
        ReplaceWith("lastSeenImprovement"))
    var lastSeenImprovementSaved = HashMap<String, String>()

    var lastSeenImprovement = HashMapVector2<String>()

    // To correctly determine "game over" condition as clarified in #4707
    // Nullable type meant to be deprecated and converted to non-nullable,
    // default false once we no longer want legacy save-game compatibility
    // This parameter means they owned THEIR OWN capital btw, not other civs'.
    var hasEverOwnedOriginalCapital: Boolean? = null

    val passableImpassables = HashSet<String>() // For Carthage-like uniques

    // For Aggressor, Warmonger status
    private var numMinorCivsAttacked = 0

    var totalCultureForContests = 0
    var totalFaithForContests = 0

    /**
     * Container class to represent a historical attack recently performed by this civilization.
     *
     * @property attackingUnit Name key of [BaseUnit] type that performed the attack, or null (E.G. for city bombardments).
     * @property source Position of the tile from which the attack was made.
     * @property target Position of the tile targeted by the attack.
     * @see [MapUnit.UnitMovementMemory], [attacksSinceTurnStart]
     */
    class HistoricalAttackMemory() : IsPartOfGameInfoSerialization {
        constructor(attackingUnit: String?, source: Vector2, target: Vector2): this() {
            this.attackingUnit = attackingUnit
            this.source = source
            this.target = target
        }
        var attackingUnit: String? = null
        lateinit var source: Vector2
        lateinit var target: Vector2
        fun clone() = HistoricalAttackMemory(attackingUnit, Vector2(source), Vector2(target))
    }
    /** Deep clone an ArrayList of [HistoricalAttackMemory]s. */
    private fun ArrayList<HistoricalAttackMemory>.copy() = ArrayList(this.map { it.clone() })
    /**
     * List of attacks that this civilization has performed since the start of its most recent turn. Does not include attacks already tracked in [MapUnit.attacksSinceTurnStart] of living units. Used in movement arrow overlay.
     * @see [MapUnit.attacksSinceTurnStart]
     */
    var attacksSinceTurnStart = ArrayList<HistoricalAttackMemory>()

    var hasMovedAutomatedUnits = false

    @Transient
    var hasLongCountDisplayUnique = false

    constructor()

    constructor(civName: String) {
        this.civName = civName
    }

    fun clone(): CivilizationInfo {
        val toReturn = CivilizationInfo()
        toReturn.gold = gold
        toReturn.playerType = playerType
        toReturn.playerId = playerId
        toReturn.civName = civName
        toReturn.tech = tech.clone()
        toReturn.policies = policies.clone()
        toReturn.civConstructions = civConstructions.clone()
        toReturn.religionManager = religionManager.clone()
        toReturn.questManager = questManager.clone()
        toReturn.goldenAges = goldenAges.clone()
        toReturn.greatPeople = greatPeople.clone()
        toReturn.ruinsManager = ruinsManager.clone()
        toReturn.espionageManager = espionageManager.clone()
        toReturn.victoryManager = victoryManager.clone()
        toReturn.allyCivName = allyCivName
        for (diplomacyManager in diplomacy.values.map { it.clone() })
            toReturn.diplomacy[diplomacyManager.otherCivName] = diplomacyManager
        toReturn.proximity.putAll(proximity)
        toReturn.cities = cities.map { it.clone() }

        // This is the only thing that is NOT switched out, which makes it a source of ConcurrentModification errors.
        // Cloning it by-pointer is a horrific move, since the serialization would go over it ANYWAY and still lead to concurrency problems.
        // Cloning it by iterating on the tilemap values may seem ridiculous, but it's a perfectly thread-safe way to go about it, unlike the other solutions.
        toReturn.exploredTiles.addAll(gameInfo.tileMap.values.asSequence().map { it.position }.filter { it in exploredTiles })
        toReturn.lastSeenImprovement.putAll(lastSeenImprovement)
        toReturn.notifications.addAll(notifications)
        toReturn.notificationsLog.addAll(notificationsLog)
        toReturn.citiesCreated = citiesCreated
        toReturn.popupAlerts.addAll(popupAlerts)
        toReturn.tradeRequests.addAll(tradeRequests)
        toReturn.naturalWonders.addAll(naturalWonders)
        toReturn.cityStatePersonality = cityStatePersonality
        toReturn.cityStateResource = cityStateResource
        toReturn.cityStateUniqueUnit = cityStateUniqueUnit
        toReturn.flagsCountdown.putAll(flagsCountdown)
        toReturn.temporaryUniques.addAll(temporaryUniques)
        toReturn.hasEverOwnedOriginalCapital = hasEverOwnedOriginalCapital
        toReturn.passableImpassables.addAll(passableImpassables)
        toReturn.numMinorCivsAttacked = numMinorCivsAttacked
        toReturn.totalCultureForContests = totalCultureForContests
        toReturn.totalFaithForContests = totalFaithForContests
        toReturn.attacksSinceTurnStart = attacksSinceTurnStart.copy()
        toReturn.hasMovedAutomatedUnits = hasMovedAutomatedUnits
        return toReturn
    }

    //region pure functions
    fun getDifficulty(): Difficulty {
        if (isPlayerCivilization()) return gameInfo.getDifficulty()
        // TODO We should be able to mark a difficulty as 'default AI difficulty' somehow
        val chieftainDifficulty = gameInfo.ruleSet.difficulties["Chieftain"]
        if (chieftainDifficulty != null) return chieftainDifficulty
        return gameInfo.ruleSet.difficulties.values.first()
    }

    fun getDiplomacyManager(civInfo: CivilizationInfo) = getDiplomacyManager(civInfo.civName)
    fun getDiplomacyManager(civName: String) = diplomacy[civName]!!

    fun getProximity(civInfo: CivilizationInfo) = getProximity(civInfo.civName)
    @Suppress("MemberVisibilityCanBePrivate")  // same visibility for overloads
    fun getProximity(civName: String) = proximity[civName] ?: Proximity.None

    /** Returns only undefeated civs, aka the ones we care about */
    fun getKnownCivs() = diplomacy.values.map { it.otherCiv() }.filter { !it.isDefeated() }
    fun knows(otherCivName: String) = diplomacy.containsKey(otherCivName)
    fun knows(otherCiv: CivilizationInfo) = knows(otherCiv.civName)

    /** A sorted Sequence of all other civs we know (excluding barbarians and spectators) */
    fun getKnownCivsSorted(includeCityStates: Boolean = true, includeDefeated: Boolean = false) =
        gameInfo.civilizations.asSequence()
        .filterNot {
            it == this ||
                it.isBarbarian() || it.isSpectator() ||
                !this.knows(it) ||
                (!includeDefeated && it.isDefeated()) ||
                (!includeCityStates && it.isCityState())
        }
        .sortedWith(
            compareByDescending<CivilizationInfo> { it.isMajorCiv() }
                .thenBy (UncivGame.Current.settings.getCollatorFromLocale()) { it.civName.tr() }
        )
    fun getCapital() = cities.firstOrNull { it.isCapital() }
    fun isPlayerCivilization() = playerType == PlayerType.Human
    fun isOneCityChallenger() = (
            playerType == PlayerType.Human &&
                    gameInfo.gameParameters.oneCityChallenge)

    fun isCurrentPlayer() = gameInfo.currentPlayerCiv == this
    fun isBarbarian() = nation.isBarbarian()
    fun isSpectator() = nation.isSpectator()
    fun isCityState(): Boolean = nation.isCityState()
    val cityStateType: CityStateType get() = nation.cityStateType!!
    var cityStatePersonality: CityStatePersonality = CityStatePersonality.Neutral
    var cityStateResource: String? = null
    var cityStateUniqueUnit: String? = null // Unique unit for militaristic city state. Might still be null if there are no appropriate units
    fun isMajorCiv() = nation.isMajorCiv()
    fun isAlive(): Boolean = !isDefeated()

    @Suppress("unused")  //TODO remove if future use unlikely, including DiplomacyFlags.EverBeenFriends and 2 DiplomacyManager methods - see #3183
    // I'm willing to call this deprecated after so long
    fun hasEverBeenFriendWith(otherCiv: CivilizationInfo): Boolean = getDiplomacyManager(otherCiv).everBeenFriends()

    fun hasMetCivTerritory(otherCiv: CivilizationInfo): Boolean = otherCiv.getCivTerritory().any { it in exploredTiles }
    fun getCompletedPolicyBranchesCount(): Int = policies.adoptedPolicies.count { Policy.isBranchCompleteByName(it) }
    fun originalMajorCapitalsOwned(): Int = cities.count { it.isOriginalCapital && it.foundingCiv != "" && gameInfo.getCivilization(it.foundingCiv).isMajorCiv() }
    private fun getCivTerritory() = cities.asSequence().flatMap { it.tiles.asSequence() }

    fun getPreferredVictoryType(): String {
        val victoryTypes = gameInfo.gameParameters.victoryTypes
        if (victoryTypes.size == 1)
            return victoryTypes.first() // That is the most relevant one
        val victoryType = nation.preferredVictoryType
        return if (victoryType in gameInfo.ruleSet.victories) victoryType
               else Constants.neutralVictoryType
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getPreferredVictoryTypeObject(): Victory? {
        val preferredVictoryType = getPreferredVictoryType()
        return if (preferredVictoryType == Constants.neutralVictoryType) null
               else gameInfo.ruleSet.victories[getPreferredVictoryType()]!!
    }

    fun wantsToFocusOn(focus: Victory.Focus): Boolean {
        return thingsToFocusOnForVictory.contains(focus)
    }

    @Transient
    private val civInfoStats = CivInfoStats(this)
    fun stats() = civInfoStats

    @Transient
    private val civInfoTransientUpdater = CivInfoTransientUpdater(this)
    fun transients() = civInfoTransientUpdater

    fun updateStatsForNextTurn() {
        happinessForNextTurn = stats().getHappinessBreakdown().values.sum().roundToInt()
        statsForNextTurn = stats().getStatMapForNextTurn().values.reduce { a, b -> a + b }
    }

    fun getHappiness() = happinessForNextTurn


    fun getCivResources(): ResourceSupplyList = summarizedCivResources

    // Preserves some origins for resources so we can separate them for trades
    fun getCivResourcesWithOriginsForTrade(): ResourceSupplyList {
        val newResourceSupplyList = ResourceSupplyList(keepZeroAmounts = true)
        for (resourceSupply in detailedCivResources) {
            // If we got it from another trade or from a CS, preserve the origin
            if (resourceSupply.isCityStateOrTradeOrigin()) {
                newResourceSupplyList.add(resourceSupply.copy())
                newResourceSupplyList.add(resourceSupply.resource, Constants.tradable, 0) // Still add an empty "tradable" entry so it shows up in the list
            }
            else
                newResourceSupplyList.add(resourceSupply.resource, Constants.tradable, resourceSupply.amount)
        }
        return newResourceSupplyList
    }

    fun isCapitalConnectedToCity(city: CityInfo): Boolean = citiesConnectedToCapitalToMediums.keys.contains(city)


    /**
     * Returns a dictionary of ALL resource names, and the amount that the civ has of each
     */
    fun getCivResourcesByName(): HashMap<String, Int> {
        val hashMap = HashMap<String, Int>(gameInfo.ruleSet.tileResources.size)
        for (resource in gameInfo.ruleSet.tileResources.keys) hashMap[resource] = 0
        for (entry in getCivResources())
            hashMap[entry.resource.name] = entry.amount
        return hashMap
    }

    fun getResourceModifier(resource: TileResource): Int {
        var resourceModifier = 1f
        for (unique in getMatchingUniques(UniqueType.DoubleResourceProduced))
            if (unique.params[0] == resource.name)
                resourceModifier *= 2f
        if (resource.resourceType == ResourceType.Strategic) {
            resourceModifier *= 1f + getMatchingUniques(UniqueType.StrategicResourcesIncrease)
                .map { it.params[0].toFloat() / 100f }.sum()

        }
        return resourceModifier.toInt()
    }

    fun hasResource(resourceName: String): Boolean = getCivResourcesByName()[resourceName]!! > 0

    fun hasUnique(uniqueType: UniqueType, stateForConditionals: StateForConditionals =
        StateForConditionals(this)) = getMatchingUniques(uniqueType, stateForConditionals).any()

    // Does not return local uniques, only global ones.
    /** Destined to replace getMatchingUniques, gradually, as we fill the enum */
    fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = StateForConditionals(this), cityToIgnore: CityInfo? = null) = sequence {
        yieldAll(nation.getMatchingUniques(uniqueType, stateForConditionals))
        yieldAll(cities.asSequence()
            .filter { it != cityToIgnore }
            .flatMap { city -> city.getMatchingUniquesWithNonLocalEffects(uniqueType, stateForConditionals) }
        )
        yieldAll(policies.policyUniques.getMatchingUniques(uniqueType, stateForConditionals))
        yieldAll(tech.techUniques.getMatchingUniques(uniqueType, stateForConditionals))
        if (temporaryUniques.isNotEmpty())
            yieldAll(temporaryUniques.asSequence()
                .map { it.uniqueObject }
                .filter { it.isOfType(uniqueType) && it.conditionalsApply(stateForConditionals) }
            )
        yieldAll(getEra().getMatchingUniques(uniqueType, stateForConditionals))
        if (religionManager.religion != null)
            yieldAll(religionManager.religion!!.getFounderUniques()
                .filter { it.isOfType(uniqueType) && it.conditionalsApply(stateForConditionals) })

        yieldAll(getCivResources().asSequence()
            .filter { it.amount > 0 }
            .flatMap { it.resource.getMatchingUniques(uniqueType, stateForConditionals) }
        )

        yieldAll(gameInfo.ruleSet.globalUniques.getMatchingUniques(uniqueType, stateForConditionals))
    }


    //region Units
    fun getCivUnitsSize(): Int = units.size
    fun getCivUnits(): Sequence<MapUnit> = units.asSequence()
    fun getCivGreatPeople(): Sequence<MapUnit> = getCivUnits().filter { mapUnit -> mapUnit.isGreatPerson() }

    // Similar to getCivUnits(), but the returned list is rotated so that the
    // 'nextPotentiallyDueAt' unit is first here.
    private fun getCivUnitsStartingAtNextDue(): Sequence<MapUnit> = sequenceOf(units.subList(nextPotentiallyDueAt, units.size) + units.subList(0, nextPotentiallyDueAt)).flatten()

    fun addUnit(mapUnit: MapUnit, updateCivInfo: Boolean = true) {
        // Since we create a new list anyway (otherwise some concurrent modification
        // exception will happen), also rearrange existing units so that
        // 'nextPotentiallyDueAt' becomes 0.  This way new units are always last to be due
        // (can be changed as wanted, just have a predictable place).
        val newList = getCivUnitsStartingAtNextDue().toMutableList()
        newList.add(mapUnit)
        units = newList
        nextPotentiallyDueAt = 0

        if (updateCivInfo) {
            // Not relevant when updating TileInfo transients, since some info of the civ itself isn't yet available,
            // and in any case it'll be updated once civ info transients are
            updateStatsForNextTurn() // unit upkeep
            if (mapUnit.baseUnit.getResourceRequirements().isNotEmpty())
                updateDetailedCivResources()
        }
    }

    fun removeUnit(mapUnit: MapUnit) {
        // See comment in addUnit().
        val newList = getCivUnitsStartingAtNextDue().toMutableList()
        newList.remove(mapUnit)
        units = newList
        nextPotentiallyDueAt = 0

        updateStatsForNextTurn() // unit upkeep
        if (mapUnit.baseUnit.getResourceRequirements().isNotEmpty())
            updateDetailedCivResources()
    }

    fun getIdleUnits() = getCivUnits().filter { it.isIdle() }

    fun getDueUnits(): Sequence<MapUnit> = getCivUnitsStartingAtNextDue().filter { it.due && it.isIdle() }

    fun shouldGoToDueUnit() = UncivGame.Current.settings.checkForDueUnits && getDueUnits().any()

    // Return the next due unit, but preferably not 'unitToSkip': this is returned only if it is the only remaining due unit.
    fun cycleThroughDueUnits(unitToSkip: MapUnit? = null): MapUnit? {
        if (units.none()) return null

        var returnAt = nextPotentiallyDueAt
        var fallbackAt = -1

        do {
            if (units[returnAt].due && units[returnAt].isIdle()) {
                if (units[returnAt] != unitToSkip) {
                    nextPotentiallyDueAt = (returnAt + 1) % units.size
                    return units[returnAt]
                }
                else fallbackAt = returnAt
            }

            returnAt = (returnAt + 1) % units.size
        } while (returnAt != nextPotentiallyDueAt)

        if (fallbackAt >= 0) {
            nextPotentiallyDueAt = (fallbackAt + 1) % units.size
            return units[fallbackAt]
        }
        else return null
    }
    //endregion

    fun shouldOpenTechPicker(): Boolean {
        if (!tech.canResearchTech()) return false
        if (tech.freeTechs != 0) return true
        return tech.currentTechnology() == null && cities.isNotEmpty()
    }

    fun getEquivalentBuilding(buildingName: String) = getEquivalentBuilding(gameInfo.ruleSet.buildings[buildingName]!!)
    fun getEquivalentBuilding(baseBuilding: Building): Building {
        if (baseBuilding.replaces != null)
            return getEquivalentBuilding(baseBuilding.replaces!!)

        for (building in gameInfo.ruleSet.buildings.values)
            if (building.replaces == baseBuilding.name && building.uniqueTo == civName)
                return building
        return baseBuilding
    }

    fun getEquivalentUnit(baseUnitName: String): BaseUnit {
        val baseUnit = gameInfo.ruleSet.units[baseUnitName]
            ?: throw UncivShowableException("Unit $baseUnitName doesn't seem to exist!")
        return getEquivalentUnit(baseUnit)
    }
    fun getEquivalentUnit(baseUnit: BaseUnit): BaseUnit {
        if (baseUnit.replaces != null)
            return getEquivalentUnit(baseUnit.replaces!!) // Equivalent of unique unit is the equivalent of the replaced unit

        for (unit in gameInfo.ruleSet.units.values)
            if (unit.replaces == baseUnit.name && unit.uniqueTo == civName)
                return unit
        return baseUnit
    }

    fun makeCivilizationsMeet(otherCiv: CivilizationInfo, warOnContact: Boolean = false) {
        meetCiv(otherCiv, warOnContact)
        otherCiv.meetCiv(this, warOnContact)
    }

    private fun meetCiv(otherCiv: CivilizationInfo, warOnContact: Boolean = false) {
        diplomacy[otherCiv.civName] = DiplomacyManager(this, otherCiv.civName)
            .apply { diplomaticStatus = DiplomaticStatus.Peace }

        if (!otherCiv.isSpectator())
            otherCiv.popupAlerts.add(PopupAlert(AlertType.FirstContact, civName))

        if (isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Meet another civilization")

        if (!(isCityState() && otherCiv.isMajorCiv())) return
        if (warOnContact || otherCiv.isMinorCivAggressor()) return // No gift if they are bad people, or we are just about to be at war

        val cityStateLocation = if (cities.isEmpty()) null else getCapital()!!.location

        val giftAmount = Stats(gold = 15f)
        val faithAmount = Stats(faith = 4f)
        // Later, religious city-states will also gift gold, making this the better implementation
        // For now, it might be overkill though.
        var meetString = "[${civName}] has given us [${giftAmount}] as a token of goodwill for meeting us"
        val religionMeetString = "[${civName}] has also given us [${faithAmount}]"
        if (diplomacy.filter { it.value.otherCiv().isMajorCiv() }.size == 1) {
            giftAmount.timesInPlace(2f)
            meetString = "[${civName}] has given us [${giftAmount}] as we are the first major civ to meet them"
        }
        if (cityStateLocation != null)
            otherCiv.addNotification(meetString, cityStateLocation, NotificationIcon.Gold)
        else
            otherCiv.addNotification(meetString, NotificationIcon.Gold)

        if (otherCiv.isCityState() && otherCiv.canGiveStat(Stat.Faith)){
            otherCiv.addNotification(religionMeetString, NotificationIcon.Faith)

            for ((key, value) in faithAmount)
                otherCiv.addStat(key, value.toInt())
        }
        for ((key, value) in giftAmount)
            otherCiv.addStat(key, value.toInt())

        if (cities.isNotEmpty())
            otherCiv.exploredTiles = otherCiv.exploredTiles.withItem(getCapital()!!.location)

        questManager.justMet(otherCiv) // Include them in war with major pseudo-quest
    }

    fun discoverNaturalWonder(naturalWonderName: String) {
        naturalWonders.add(naturalWonderName)
    }

    override fun toString(): String {
        return civName
    } // for debug

    /**
     *  Determine loss conditions.
     *
     *  If the civ has never controlled an original capital, it stays 'alive' as long as it has units (irrespective of non-original-capitals owned)
     *  Otherwise, it stays 'alive' as long as it has cities (irrespective of settlers owned)
     */
    fun isDefeated() = when {
        isBarbarian() || isSpectator() -> false     // Barbarians and voyeurs can't lose
        hasEverOwnedOriginalCapital == true -> cities.isEmpty()
        else -> getCivUnits().none()
    }

    fun getEra(): Era = tech.era

    fun getEraNumber(): Int = getEra().eraNumber

    fun isAtWarWith(otherCiv: CivilizationInfo): Boolean {
        return when {
            otherCiv == this -> false
            otherCiv.isBarbarian() || isBarbarian() -> true
            else -> {
                val diplomacyManager = diplomacy[otherCiv.civName]
                    ?: return false // not encountered yet
                return diplomacyManager.diplomaticStatus == DiplomaticStatus.War
            }
        }
    }

    fun isAtWar() = diplomacy.values.any { it.diplomaticStatus == DiplomaticStatus.War && !it.otherCiv().isDefeated() }

    fun canEnterBordersOf(otherCiv: CivilizationInfo): Boolean {
        if (otherCiv == this) return true // own borders are always open
        if (otherCiv.isBarbarian() || isBarbarian()) return false // barbarians blocks the routes
        val diplomacyManager = diplomacy[otherCiv.civName]
            ?: return false // not encountered yet
        if (otherCiv.isCityState() && diplomacyManager.diplomaticStatus != DiplomaticStatus.War) return true
        return diplomacyManager.hasOpenBorders
    }

    /**
     * Returns a civilization caption suitable for greetings including player type info:
     * Like "Milan" if the nation is a city state, "Caesar of Rome" otherwise, with an added
     * " (AI)", " (Human - Hotseat)", or " (Human - Multiplayer)" if the game is multiplayer.
     */
    fun getLeaderDisplayName(): String {
        val severalHumans = gameInfo.civilizations.count { it.playerType == PlayerType.Human } > 1
        val online = gameInfo.gameParameters.isOnlineMultiplayer
        return nation.getLeaderDisplayName().tr() +
            when {
                !online && !severalHumans ->
                    ""                      // offline single player will know everybody else is AI
                playerType == PlayerType.AI ->
                    " (" + "AI".tr() + ")"
                online ->
                    " (" + "Human".tr() + " - " + "Multiplayer".tr() + ")"
                else ->
                    " (" + "Human".tr() + " - " + "Hotseat".tr() + ")"
            }
    }

    fun canSignResearchAgreement(): Boolean {
        if (!isMajorCiv()) return false
        if (!hasUnique(UniqueType.EnablesResearchAgreements)) return false
        if (gameInfo.ruleSet.technologies.values
                        .none { tech.canBeResearched(it.name) && !tech.isResearched(it.name) }) return false
        return true
    }

    fun canSignResearchAgreementsWith(otherCiv: CivilizationInfo): Boolean {
        val diplomacyManager = getDiplomacyManager(otherCiv)
        val cost = getResearchAgreementCost()
        return canSignResearchAgreement() && otherCiv.canSignResearchAgreement()
                && diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
                && !diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement)
                && !diplomacyManager.otherCivDiplomacy().hasFlag(DiplomacyFlags.ResearchAgreement)
                && gold >= cost && otherCiv.gold >= cost
    }

    fun getStatForRanking(category: RankingType): Int {
        return if (isDefeated()) 0
        else when (category) {
                RankingType.Score -> calculateTotalScore().toInt()
                RankingType.Population -> cities.sumOf { it.population.population }
                RankingType.Crop_Yield -> statsForNextTurn.food.roundToInt()
                RankingType.Production -> statsForNextTurn.production.roundToInt()
                RankingType.Gold -> gold
                RankingType.Territory -> cities.sumOf { it.tiles.size }
                RankingType.Force -> getMilitaryMight()
                RankingType.Happiness -> getHappiness()
                RankingType.Technologies -> tech.researchedTechnologies.size
                RankingType.Culture -> policies.adoptedPolicies.count { !Policy.isBranchCompleteByName(it) }
        }
    }

    private fun getMilitaryMight(): Int {
        if (cachedMilitaryMight < 0)
            cachedMilitaryMight = calculateMilitaryMight()
        return  cachedMilitaryMight
    }

    private fun calculateMilitaryMight(): Int {
        var sum = 1 // minimum value, so we never end up with 0
        for (unit in units) {
            sum += if (unit.baseUnit.isWaterUnit())
                unit.getForceEvaluation() / 2   // Really don't value water units highly
            else
                unit.getForceEvaluation()
        }
        val goldBonus = sqrt(max(0f, gold.toFloat())).toPercent()  // 2f if gold == 10000
        sum = (sum * min(goldBonus, 2f)).toInt()    // 2f is max bonus
        return sum
    }


    fun getGreatPeople(): HashSet<BaseUnit> {
        val greatPeople = gameInfo.ruleSet.units.values.asSequence()
            .filter { it.isGreatPerson() }
            .map { getEquivalentUnit(it.name) }
        return if (!gameInfo.isReligionEnabled())
            greatPeople.filter { !it.hasUnique(UniqueType.HiddenWithoutReligion) }.toHashSet()
        else greatPeople.toHashSet()
    }

    fun hasTechOrPolicy(techOrPolicyName: String) =
        tech.isResearched(techOrPolicyName) || policies.isAdopted(techOrPolicyName)

    fun isMinorCivAggressor() = numMinorCivsAttacked >= 2
    fun isMinorCivWarmonger() = numMinorCivsAttacked >= 4

    private fun isLongCountActive(): Boolean {
        val unique = getMatchingUniques(UniqueType.MayanGainGreatPerson).firstOrNull()
            ?: return false
        return tech.isResearched(unique.params[1])
    }
    fun isLongCountDisplay() = hasLongCountDisplayUnique && isLongCountActive()

    fun calculateScoreBreakdown(): HashMap<String,Double> {
        val scoreBreakdown = hashMapOf<String,Double>()
        // 1276 is the number of tiles in a medium sized map. The original uses 4160 for this,
        // but they have bigger maps
        var mapSizeModifier = 1276 / gameInfo.tileMap.mapParameters.numberOfTiles().toDouble()
        if (mapSizeModifier > 1)
            mapSizeModifier = (mapSizeModifier - 1) / 3 + 1

        scoreBreakdown["Cities"] = cities.size * 10 * mapSizeModifier
        scoreBreakdown["Population"] = cities.sumOf { it.population.population } * 3 * mapSizeModifier
        scoreBreakdown["Tiles"] = cities.sumOf { city -> city.getTiles().filter { !it.isWater}.count() } * 1 * mapSizeModifier
        scoreBreakdown["Wonders"] = 40 * cities
            .sumOf { city -> city.cityConstructions.builtBuildings
                .filter { gameInfo.ruleSet.buildings[it]!!.isWonder }.size
            }.toDouble()
        scoreBreakdown["Technologies"] = tech.getNumberOfTechsResearched() * 4.toDouble()
        scoreBreakdown["Future Tech"] = tech.repeatingTechsResearched * 10.toDouble()

        return scoreBreakdown
    }

    fun calculateTotalScore() = calculateScoreBreakdown().values.sum()

    //endregion

    //region state-changing functions

    /** This is separate because the REGULAR setTransients updates the viewable ties,
     *  and updateVisibleTiles tries to meet civs...
     *  And if the civs don't yet know who they are then they don't know if they're barbarians =\
     *  */
    fun setNationTransient() {
        nation = gameInfo.ruleSet.nations[civName]
                ?: throw UncivShowableException("Nation $civName is not found!")
    }

    fun setTransients() {
        goldenAges.civInfo = this

        civConstructions.setTransients(civInfo = this)

        policies.civInfo = this
        if (policies.adoptedPolicies.size > 0 && policies.numberOfAdoptedPolicies == 0)
            policies.numberOfAdoptedPolicies = policies.adoptedPolicies.count { !Policy.isBranchCompleteByName(it) }
        policies.setTransients()

        questManager.civInfo = this
        questManager.setTransients()

        if (citiesCreated == 0 && cities.any())
            citiesCreated = cities.filter { it.name in nation.cities }.size

        religionManager.civInfo = this // needs to be before tech, since tech setTransients looks at all uniques
        religionManager.setTransients()

        tech.civInfo = this
        tech.setTransients()

        ruinsManager.setTransients(this)

        for (diplomacyManager in diplomacy.values) {
            diplomacyManager.civInfo = this
            diplomacyManager.updateHasOpenBorders()
        }

        espionageManager.setTransients(this)

        victoryManager.civInfo = this

        thingsToFocusOnForVictory = getPreferredVictoryTypeObject()?.getThingsToFocus(this) ?: setOf()

        for (cityInfo in cities) {
            cityInfo.civInfo = this // must be before the city's setTransients because it depends on the tilemap, that comes from the currentPlayerCivInfo
            cityInfo.setTransients()
        }

        passThroughImpassableUnlocked = passableImpassables.isNotEmpty()
        // Cache whether this civ gets nonstandard terrain damage for performance reasons.
        nonStandardTerrainDamage = getMatchingUniques(UniqueType.DamagesContainingUnits)
            .any { gameInfo.ruleSet.terrains[it.params[0]]!!.damagePerTurn != it.params[1].toInt() }

        // Cache the last era each resource is used for buildings or units respectively for AI building evaluation
        for (resource in gameInfo.ruleSet.tileResources.values.asSequence().filter { it.resourceType == ResourceType.Strategic }.map { it.name }) {
            val applicableBuildings = gameInfo.ruleSet.buildings.values.filter { it.requiresResource(resource) && getEquivalentBuilding(it) == it }
            val applicableUnits = gameInfo.ruleSet.units.values.filter { it.requiresResource(resource) && getEquivalentUnit(it) == it }

            val lastEraForBuilding = applicableBuildings.map { gameInfo.ruleSet.eras[gameInfo.ruleSet.technologies[it.requiredTech]?.era()]?.eraNumber ?: 0 }.maxOrNull()
            val lastEraForUnit = applicableUnits.map { gameInfo.ruleSet.eras[gameInfo.ruleSet.technologies[it.requiredTech]?.era()]?.eraNumber ?: 0 }.maxOrNull()

            if (lastEraForBuilding != null)
                lastEraResourceUsedForBuilding[resource] = lastEraForBuilding
            if (lastEraForUnit != null)
                lastEraResourceUsedForUnit[resource] = lastEraForUnit
        }

        hasLongCountDisplayUnique = hasUnique(UniqueType.MayanCalendarDisplay)

    }

    fun updateSightAndResources() {
        updateViewableTiles()
        updateHasActiveGreatWall()
        updateDetailedCivResources()
    }

    fun changeMinorCivsAttacked(count: Int) {
        numMinorCivsAttacked += count
    }

    // implementation in a separate class, to not clog up CivInfo
    fun initialSetCitiesConnectedToCapitalTransients() = transients().updateCitiesConnectedToCapital(true)
    fun updateHasActiveGreatWall() = transients().updateHasActiveGreatWall()
    fun updateViewableTiles() = transients().updateViewableTiles()
    fun updateDetailedCivResources() = transients().updateCivResources()

    fun startTurn() {
        civConstructions.startTurn()
        attacksSinceTurnStart.clear()
        updateStatsForNextTurn() // for things that change when turn passes e.g. golden age, city state influence

        // Do this after updateStatsForNextTurn but before cities.startTurn
        if (playerType == PlayerType.AI && gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.convertGoldToScience))
            NextTurnAutomation.automateGoldToSciencePercentage(this)

        // Generate great people at the start of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        if (cities.isNotEmpty()) { //if no city available, addGreatPerson will throw exception
            val greatPerson = greatPeople.getNewGreatPerson()
            if (greatPerson != null && gameInfo.ruleSet.units.containsKey(greatPerson)) addUnit(greatPerson)
            religionManager.startTurn()
            if (isLongCountActive())
                MayaCalendar.startTurnForMaya(this)
        }

        updateViewableTiles() // adds explored tiles so that the units will be able to perform automated actions better
        transients().updateCitiesConnectedToCapital()
        startTurnFlags()
        updateRevolts()
        for (city in cities) city.startTurn()  // Most expensive part of startTurn

        for (unit in getCivUnits()) unit.startTurn()
        hasMovedAutomatedUnits = false

        updateDetailedCivResources() // If you offered a trade last turn, this turn it will have been accepted/declined

        for (tradeRequest in tradeRequests.toList()) { // remove trade requests where one of the sides can no longer supply
            val offeringCiv = gameInfo.getCivilization(tradeRequest.requestingCiv)
            if (offeringCiv.isDefeated() || !TradeEvaluation().isTradeValid(tradeRequest.trade, this, offeringCiv)) {
                tradeRequests.remove(tradeRequest)
                // Yes, this is the right direction. I checked.
                offeringCiv.addNotification("Our proposed trade is no longer relevant!", NotificationIcon.Trade)
            }
        }
    }

    fun endTurn() {
        val notificationsThisTurn = NotificationsLog(gameInfo.turns)
        notificationsThisTurn.notifications.addAll(notifications)

        while (notificationsLog.size >= UncivGame.Current.settings.notificationsLogMaxTurns) {
            notificationsLog.removeFirst()
        }

        if (notificationsThisTurn.notifications.isNotEmpty())
            notificationsLog.add(notificationsThisTurn)

        notifications.clear()
        updateStatsForNextTurn()
        val nextTurnStats = statsForNextTurn

        policies.endTurn(nextTurnStats.culture.toInt())
        totalCultureForContests += nextTurnStats.culture.toInt()

        if (isCityState())
            questManager.endTurn()

        // disband units until there are none left OR the gold values are normal
        if (!isBarbarian() && gold < -100 && nextTurnStats.gold.toInt() < 0) {
            for (i in 1 until (gold / -100)) {
                var civMilitaryUnits = getCivUnits().filter { it.baseUnit.isMilitary() }
                if (civMilitaryUnits.any()) {
                    val unitToDisband = civMilitaryUnits.first()
                    unitToDisband.disband()
                    civMilitaryUnits -= unitToDisband
                    val unitName = unitToDisband.shortDisplayName()
                    addNotification("Cannot provide unit upkeep for $unitName - unit has been disbanded!", unitName, NotificationIcon.Death)
                }
            }
        }

        addGold( nextTurnStats.gold.toInt() )

        if (cities.isNotEmpty() && gameInfo.ruleSet.technologies.isNotEmpty())
            tech.endTurn(nextTurnStats.science.toInt())

        religionManager.endTurn(nextTurnStats.faith.toInt())
        totalFaithForContests += nextTurnStats.faith.toInt()

        if (isMajorCiv()) greatPeople.addGreatPersonPoints(getGreatPersonPointsForNextTurn()) // City-states don't get great people!

        for (city in cities.toList()) { // a city can be removed while iterating (if it's being razed) so we need to iterate over a copy
            city.endTurn()
        }

        // Update turn counter for temporary uniques
        for (unique in temporaryUniques) {
            if (unique.turnsLeft >= 0)
                unique.turnsLeft -= 1
        }
        temporaryUniques.removeAll { it.turnsLeft == 0 }

        goldenAges.endTurn(getHappiness())
        getCivUnits().forEach { it.endTurn() }  // This is the most expensive part of endTurn
        diplomacy.values.toList().forEach { it.nextTurn() } // we copy the diplomacy values so if it changes in-loop we won't crash
        updateHasActiveGreatWall()

        cachedMilitaryMight = -1    // Reset so we don't use a value from a previous turn
    }

    private fun startTurnFlags() {
        for (flag in flagsCountdown.keys.toList()) {
            // In case we remove flags while iterating
            if (!flagsCountdown.containsKey(flag)) continue

            if (flag == CivFlags.CityStateGreatPersonGift.name) {
                val cityStateAllies: List<CivilizationInfo> =
                    getKnownCivs().filter { it.isCityState() && it.getAllyCiv() == civName }
                val givingCityState = cityStateAllies.filter { it.cities.isNotEmpty() }.randomOrNull()

                if (cityStateAllies.isNotEmpty()) flagsCountdown[flag] = flagsCountdown[flag]!! - 1

                if (flagsCountdown[flag]!! < min(cityStateAllies.size, 10) && cities.isNotEmpty()
                    && givingCityState != null
                ) {
                    givingCityState.cityStateFunctions.giveGreatPersonToPatron(this)
                    flagsCountdown[flag] = turnsForGreatPersonFromCityState()
                }

                continue
            }

            if (flagsCountdown[flag]!! > 0)
                flagsCountdown[flag] = flagsCountdown[flag]!! - 1

            if (flagsCountdown[flag] != 0) continue

            when (flag) {
                CivFlags.RevoltSpawning.name -> doRevoltSpawn()
            }
        }
        handleDiplomaticVictoryFlags()
    }

    private fun handleDiplomaticVictoryFlags() {
        if (flagsCountdown[CivFlags.ShouldResetDiplomaticVotes.name] == 0) {
            gameInfo.diplomaticVictoryVotesCast.clear()
            removeFlag(CivFlags.ShowDiplomaticVotingResults.name)
            removeFlag(CivFlags.ShouldResetDiplomaticVotes.name)
        }

        if (flagsCountdown[CivFlags.ShowDiplomaticVotingResults.name] == 0) {
            gameInfo.processDiplomaticVictory()
            if (gameInfo.civilizations.any { it.victoryManager.hasWon() } ) {
                removeFlag(CivFlags.TurnsTillNextDiplomaticVote.name)
            } else {
                addFlag(CivFlags.ShouldResetDiplomaticVotes.name, 1)
                addFlag(CivFlags.TurnsTillNextDiplomaticVote.name, getTurnsBetweenDiplomaticVotes())
            }
        }

        if (flagsCountdown[CivFlags.TurnsTillNextDiplomaticVote.name] == 0) {
            addFlag(CivFlags.ShowDiplomaticVotingResults.name, 1)
        }
    }

    fun addFlag(flag: String, count: Int) = flagsCountdown.set(flag, count)
    fun removeFlag(flag: String) = flagsCountdown.remove(flag)
    fun hasFlag(flag: String) = flagsCountdown.contains(flag)

    fun getTurnsBetweenDiplomaticVotes() = (15 * gameInfo.speed.modifier).toInt() // Dunno the exact calculation, hidden in Lua files

    fun getTurnsTillNextDiplomaticVote() = flagsCountdown[CivFlags.TurnsTillNextDiplomaticVote.name]

    fun getRecentBullyingCountdown() = flagsCountdown[CivFlags.RecentlyBullied.name]
    fun getTurnsTillCallForBarbHelp() = flagsCountdown[CivFlags.TurnsTillCallForBarbHelp.name]

    fun mayVoteForDiplomaticVictory() =
        getTurnsTillNextDiplomaticVote() == 0
        && civName !in gameInfo.diplomaticVictoryVotesCast.keys
        // Only vote if there is someone to vote for, may happen in one-more-turn mode
        && gameInfo.civilizations.any { it.isMajorCiv() && !it.isDefeated() && it != this }

    fun diplomaticVoteForCiv(chosenCivName: String?) {
        if (chosenCivName != null) gameInfo.diplomaticVictoryVotesCast[civName] = chosenCivName
    }

    fun shouldShowDiplomaticVotingResults() =
         flagsCountdown[CivFlags.ShowDiplomaticVotingResults.name] == 0
         && gameInfo.civilizations.any { it.isMajorCiv() && !it.isDefeated() && it != this }


    private fun updateRevolts() {
        if (gameInfo.civilizations.none { it.isBarbarian() }) {
            // Can't spawn revolts without barbarians ¯\_(ツ)_/¯
            return
        }

        if (!hasUnique(UniqueType.SpawnRebels)) {
            removeFlag(CivFlags.RevoltSpawning.name)
            return
        }

        if (!hasFlag(CivFlags.RevoltSpawning.name)) {
            addFlag(CivFlags.RevoltSpawning.name, max(getTurnsBeforeRevolt(),1))
            return
        }
    }

    private fun doRevoltSpawn() {
        val barbarians = try {
            // The first test in `updateRevolts` should prevent getting here in a no-barbarians game, but it has been shown to still occur
            gameInfo.getBarbarianCivilization()
        } catch (ex: NoSuchElementException) {
            removeFlag(CivFlags.RevoltSpawning.name)
            return
        }

        val random = Random()
        val rebelCount = 1 + random.nextInt(100 + 20 * (cities.size - 1)) / 100
        val spawnCity = cities.maxByOrNull { random.nextInt(it.population.population + 10) } ?: return
        val spawnTile = spawnCity.getTiles().maxByOrNull { rateTileForRevoltSpawn(it) } ?: return
        val unitToSpawn = gameInfo.ruleSet.units.values.asSequence().filter {
            it.uniqueTo == null && it.isMelee() && it.isLandUnit()
            && !it.hasUnique(UniqueType.CannotAttack) && it.isBuildable(this)
        }.maxByOrNull {
            random.nextInt(1000)
        } ?: return

        repeat(rebelCount) {
            gameInfo.tileMap.placeUnitNearTile(
                spawnTile.position,
                unitToSpawn.name,
                barbarians
            )
        }

        // Will be automatically added again as long as unhappiness is still low enough
        removeFlag(CivFlags.RevoltSpawning.name)

        addNotification("Your citizens are revolting due to very high unhappiness!", spawnTile.position, unitToSpawn.name, "StatIcons/Malcontent")
    }

    // Higher is better
    private fun rateTileForRevoltSpawn(tile: TileInfo): Int {
        if (tile.isWater || tile.militaryUnit != null || tile.civilianUnit != null || tile.isCityCenter() || tile.isImpassible())
            return -1
        var score = 10
        if (tile.improvement == null) {
            score += 4
            if (tile.resource != null) {
                score += 3
            }
        }
        if (tile.getDefensiveBonus() > 0)
            score += 4
        return score
    }

    private fun getTurnsBeforeRevolt() =
        ((4 + Random().nextInt(3)) * max(gameInfo.speed.modifier, 1f)).toInt()

    /** Modify gold by a given amount making sure it does neither overflow nor underflow.
     * @param delta the amount to add (can be negative)
     */
    fun addGold(delta: Int) {
        // not using Long.coerceIn - this stays in 32 bits
        gold = when {
            delta > 0 && gold > Int.MAX_VALUE - delta -> Int.MAX_VALUE
            delta < 0 && gold < Int.MIN_VALUE - delta -> Int.MIN_VALUE
            else -> gold + delta
        }
    }

    fun addStat(stat: Stat, amount: Int) {
        when (stat) {
            Stat.Culture -> { policies.addCulture(amount)
                              if(amount > 0) totalCultureForContests += amount }
            Stat.Science -> tech.addScience(amount)
            Stat.Gold -> addGold(amount)
            Stat.Faith -> { religionManager.storedFaith += amount
                            if(amount > 0) totalFaithForContests += amount }
            else -> {}
            // Food and Production wouldn't make sense to be added nationwide
            // Happiness cannot be added as it is recalculated again, use a unique instead
        }
    }

    fun getStatReserve(stat: Stat): Int {
        return when (stat) {
            Stat.Culture -> policies.storedCulture
            Stat.Science -> {
                if (tech.currentTechnology() == null) 0
                else tech.remainingScienceToTech(tech.currentTechnology()!!.name)
            }
            Stat.Gold -> gold
            Stat.Faith -> religionManager.storedFaith
            else -> 0
        }
    }

    fun getGreatPersonPointsForNextTurn(): Counter<String> {
        val greatPersonPoints = Counter<String>()
        for (city in cities) greatPersonPoints.add(city.getGreatPersonPoints())
        return greatPersonPoints
    }

    /**
     * @returns whether units of this civilization can pass through the tiles owned by [otherCiv],
     * considering only civ-wide filters.
     * Use [TileInfo.canCivPassThrough] to check whether units of a civilization can pass through
     * a specific tile, considering only civ-wide filters.
     * Use [UnitMovementAlgorithms.canPassThrough] to check whether a specific unit can pass through
     * a specific tile.
     */
    fun canPassThroughTiles(otherCiv: CivilizationInfo): Boolean {
        if (otherCiv == this) return true
        if (otherCiv.isBarbarian()) return true
        if (nation.isBarbarian() && gameInfo.turns >= gameInfo.difficultyObject.turnBarbariansCanEnterPlayerTiles)
            return true
        val diplomacyManager = diplomacy[otherCiv.civName]
        if (diplomacyManager != null && (diplomacyManager.hasOpenBorders || diplomacyManager.diplomaticStatus == DiplomaticStatus.War))
            return true
        // Players can always pass through city-state tiles
        if (isPlayerCivilization() && otherCiv.isCityState()) return true
        return false
    }


    fun addNotification(text: String, location: Vector2, vararg notificationIcons: String) {
        addNotification(text, LocationAction(location), *notificationIcons)
    }

    fun addNotification(text: String, vararg notificationIcons: String) = addNotification(text, null, *notificationIcons)

    fun addNotification(text: String, action: NotificationAction?, vararg notificationIcons: String) {
        if (playerType == PlayerType.AI) return // no point in lengthening the saved game info if no one will read it
        val arrayList = notificationIcons.toCollection(ArrayList())
        notifications.add(Notification(text, arrayList,
                if (action is LocationAction && action.locations.isEmpty()) null else action))
    }

    fun addUnit(unitName: String, city: CityInfo? = null): MapUnit? {
        if (cities.isEmpty()) return null
        if (!gameInfo.ruleSet.units.containsKey(unitName)) return null

        val cityToAddTo = city ?: cities.random()
        val unit = getEquivalentUnit(unitName)
        val placedUnit = placeUnitNearTile(cityToAddTo.location, unit.name)
        // silently bail if no tile to place the unit is found
            ?: return null
        if (unit.isGreatPerson()) {
            addNotification("A [${unit.name}] has been born in [${cityToAddTo.name}]!", placedUnit.getTile().position, unit.name)
        }

        if (placedUnit.hasUnique(UniqueType.ReligiousUnit) && gameInfo.isReligionEnabled()) {
            placedUnit.religion =
                when {
                    placedUnit.hasUnique(UniqueType.TakeReligionOverBirthCity)
                    && religionManager.religion?.isMajorReligion() == true ->
                        religionManager.religion!!.name
                    city != null -> city.cityConstructions.cityInfo.religion.getMajorityReligionName()
                    else -> religionManager.religion?.name
                }
            placedUnit.setupAbilityUses(cityToAddTo)
        }

        for (unique in getMatchingUniques(UniqueType.LandUnitsCrossTerrainAfterUnitGained)) {
            if (unit.matchesFilter(unique.params[1])) {
                passThroughImpassableUnlocked = true    // Update the cached Boolean
                passableImpassables.add(unique.params[0])   // Add to list of passable impassables
            }
        }

        return placedUnit
    }

    /** Tries to place the a [unitName] unit into the [TileInfo] closest to the given the [location]
     * @param location where to try to place the unit
     * @param unitName name of the [BaseUnit] to create and place
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(location: Vector2, unitName: String): MapUnit? {
        return gameInfo.tileMap.placeUnitNearTile(location, unitName, this)
    }

    fun addCity(location: Vector2) {
        val newCity = CityInfo(this, location)
        newCity.cityConstructions.chooseNextConstruction()

    }

    fun destroy() {
        val destructionText = if (isMajorCiv()) "The civilization of [$civName] has been destroyed!"
        else "The City-State of [$civName] has been destroyed!"
        for (civ in gameInfo.civilizations)
            civ.addNotification(destructionText, civName, NotificationIcon.Death)
        getCivUnits().forEach { it.destroy() }
        tradeRequests.clear() // if we don't do this then there could be resources taken by "pending" trades forever
        for (diplomacyManager in diplomacy.values) {
            diplomacyManager.trades.clear()
            diplomacyManager.otherCiv().getDiplomacyManager(this).trades.clear()
            for (tradeRequest in diplomacyManager.otherCiv().tradeRequests.filter { it.requestingCiv == civName })
                diplomacyManager.otherCiv().tradeRequests.remove(tradeRequest) // it  would be really weird to get a trade request from a dead civ
        }
    }

    fun getResearchAgreementCost(): Int {
        // https://forums.civfanatics.com/resources/research-agreements-bnw.25568/
        return (
            getEra().researchAgreementCost * gameInfo.speed.goldCostModifier
        ).toInt()
    }

    fun updateProximity(otherCiv: CivilizationInfo, preCalculated: Proximity? = null): Proximity {
        if (otherCiv == this)   return Proximity.None
        if (preCalculated != null) {
            // We usually want to update this for a pair of civs at the same time
            // Since this function *should* be symmetrical for both civs, we can just do it once
            this.proximity[otherCiv.civName] = preCalculated
            return preCalculated
        }
        if (cities.isEmpty() || otherCiv.cities.isEmpty()) {
            proximity[otherCiv.civName] = Proximity.None
            return Proximity.None
        }

        val mapParams = gameInfo.tileMap.mapParameters
        var minDistance = 100000 // a long distance
        var totalDistance = 0
        var connections = 0

        var proximity = Proximity.None

        for (ourCity in cities) {
            for (theirCity in otherCiv.cities) {
                val distance = ourCity.getCenterTile().aerialDistanceTo(theirCity.getCenterTile())
                totalDistance += distance
                connections++
                if (minDistance > distance) minDistance = distance
            }
        }

        if (minDistance <= 7) {
            proximity = Proximity.Neighbors
        } else if (connections > 0) {
            val averageDistance = totalDistance / connections
            val mapFactor = if (mapParams.shape == MapShape.rectangular)
                (mapParams.mapSize.height + mapParams.mapSize.width) / 2
                else  (mapParams.mapSize.radius * 3) / 2 // slightly less area than equal size rect

            val closeDistance = ((mapFactor * 25) / 100).coerceIn(10, 20)
            val farDistance = ((mapFactor * 45) / 100).coerceIn(20, 50)

            proximity = if (minDistance <= 11 && averageDistance <= closeDistance)
                Proximity.Close
            else if (averageDistance <= farDistance)
                Proximity.Far
            else
                Proximity.Distant
        }

        // Check if different continents (unless already max distance, or water map)
        if (connections > 0 && proximity != Proximity.Distant && !gameInfo.tileMap.isWaterMap()
            && getCapital()!!.getCenterTile().getContinent() != otherCiv.getCapital()!!.getCenterTile().getContinent()
        ) {
            // Different continents - increase separation by one step
            proximity = when (proximity) {
                Proximity.Far -> Proximity.Distant
                Proximity.Close -> Proximity.Far
                Proximity.Neighbors -> Proximity.Close
                else -> proximity
            }
        }

        // If there aren't many players (left) we can't be that far
        val numMajors = gameInfo.getAliveMajorCivs().size
        if (numMajors <= 2 && proximity > Proximity.Close)
            proximity = Proximity.Close
        if (numMajors <= 4 && proximity > Proximity.Far)
            proximity = Proximity.Far

        this.proximity[otherCiv.civName] = proximity

        return proximity
    }

    /**
     * Removes current capital then moves capital to argument city if not null
     */
    fun moveCapitalTo(city: CityInfo?) {
        if (cities.isNotEmpty() && getCapital() != null) {
            val oldCapital = getCapital()!!
            oldCapital.cityConstructions.removeBuilding(oldCapital.capitalCityIndicator())
        }

        if (city == null) return // can't move a non-existent city but we can always remove our old capital
        // move new capital
        city.cityConstructions.addBuilding(city.capitalCityIndicator())
        city.isBeingRazed = false // stop razing the new capital if it was being razed
    }

    fun moveCapitalToNextLargest() {
        val availableCities = cities.filterNot { it.isCapital() }
        if (availableCities.none()) return
        var newCapital = availableCities.filterNot { it.isPuppet }.maxByOrNull { it.population.population }

        if (newCapital == null) { // No non-puppets, take largest puppet and annex
            newCapital = availableCities.maxByOrNull { it.population.population }!!
            newCapital.annexCity()
        }
        moveCapitalTo(newCapital)
    }

    //////////////////////// City State wrapper functions ////////////////////////

    fun receiveGoldGift(donorCiv: CivilizationInfo, giftAmount: Int) =
        cityStateFunctions.receiveGoldGift(donorCiv, giftAmount)
    fun turnsForGreatPersonFromCityState(): Int = ((37 + Random().nextInt(7)) * gameInfo.speed.modifier).toInt()

    fun getProtectorCivs() = cityStateFunctions.getProtectorCivs()
    fun addProtectorCiv(otherCiv: CivilizationInfo) = cityStateFunctions.addProtectorCiv(otherCiv)
    fun removeProtectorCiv(otherCiv: CivilizationInfo, forced: Boolean = false) =
        cityStateFunctions.removeProtectorCiv(otherCiv, forced)
    fun otherCivCanPledgeProtection(otherCiv: CivilizationInfo) = cityStateFunctions.otherCivCanPledgeProtection(otherCiv)
    fun otherCivCanWithdrawProtection(otherCiv: CivilizationInfo) = cityStateFunctions.otherCivCanWithdrawProtection(otherCiv)

    fun updateAllyCivForCityState() = cityStateFunctions.updateAllyCivForCityState()
    fun getTributeWillingness(demandingCiv: CivilizationInfo, demandingWorker: Boolean = false)
        = cityStateFunctions.getTributeWillingness(demandingCiv, demandingWorker)
    fun canGiveStat(statType: Stat) = cityStateFunctions.canGiveStat(statType)

    fun getAllyCiv() = allyCivName
    fun setAllyCiv(newAllyName: String?) { allyCivName = newAllyName }

    //endregion

    fun asPreview() = CivilizationInfoPreview(this)
}

/**
 * Reduced variant of CivilizationInfo used for load preview.
 */
class CivilizationInfoPreview() {
    var civName = ""
    var playerType = PlayerType.AI
    var playerId = ""
    fun isPlayerCivilization() = playerType == PlayerType.Human

    /**
     * Converts a CivilizationInfo object (can be uninitialized) into a CivilizationInfoPreview object.
     */
    constructor(civilizationInfo: CivilizationInfo) : this() {
        civName = civilizationInfo.civName
        playerType = civilizationInfo.playerType
        playerId = civilizationInfo.playerId
    }
}

enum class CivFlags {
    CityStateGreatPersonGift,
    TurnsTillNextDiplomaticVote,
    ShowDiplomaticVotingResults,
    ShouldResetDiplomaticVotes,
    RecentlyBullied,
    TurnsTillCallForBarbHelp,
    RevoltSpawning,
}
