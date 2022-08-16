package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.automation.unit.WorkerAutomation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.RejectionReason
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.UnitActionType
import com.unciv.models.helpers.UnitMovementMemoryType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.extensions.filterAndLogic
import com.unciv.ui.utils.extensions.toPercent
import java.text.DecimalFormat
import kotlin.math.pow


/**
 * The immutable properties and mutable game state of an individual unit present on the map
 */
class MapUnit : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: CivilizationInfo

    @Transient
    lateinit var baseUnit: BaseUnit

    @Transient
    lateinit var currentTile: TileInfo

    @Transient
    val movement = UnitMovementAlgorithms(this)

    @Transient
    var isDestroyed = false

    // This is saved per each unit because if we need to recalculate viewable tiles every time a unit moves,
    //  and we need to go over ALL the units, that's a lot of time spent on updating information we should already know!
    // About 10% of total NextTurn performance time, at the time of this change!
    @Transient
    var viewableTiles = HashSet<TileInfo>()

    // These are for performance improvements to getMovementCostBetweenAdjacentTiles,
    // a major component of getDistanceToTilesWithinTurn,
    // which in turn is a component of getShortestPath and canReach
    @Transient
    var ignoresTerrainCost = false
        private set

    @Transient
    var ignoresZoneOfControl = false
        private set

    @Transient
    var allTilesCosts1 = false
        private set

    @Transient
    var canPassThroughImpassableTiles = false
        private set

    @Transient
    var roughTerrainPenalty = false
        private set

    /** If set causes an early exit in getMovementCostBetweenAdjacentTiles
     *  - means no double movement uniques, roughTerrainPenalty or ignoreHillMovementCost */
    @Transient
    var noTerrainMovementUniques = false
        private set

    /** If set causes a second early exit in getMovementCostBetweenAdjacentTiles */
    @Transient
    var noBaseTerrainOrHillDoubleMovementUniques = false
        private set

    /** If set skips tile.matchesFilter tests for double movement in getMovementCostBetweenAdjacentTiles */
    @Transient
    var noFilteredDoubleMovementUniques = false
        private set

    /** Used for getMovementCostBetweenAdjacentTiles only, based on order of testing */
    enum class DoubleMovementTerrainTarget { Feature, Base, Hill, Filter }
    /** Mod-friendly cache of double-movement terrains */
    @Transient
    val doubleMovementInTerrain = HashMap<String, DoubleMovementTerrainTarget>()

    @Transient
    var canEnterIceTiles = false

    @Transient
    var cannotEnterOceanTiles = false

    @Transient
    var canEnterForeignTerrain: Boolean = false

    @Transient
    var costToDisembark: Float? = null

    @Transient
    var costToEmbark: Float? = null

    @Transient
    var paradropRange = 0

    @Transient
    var hasUniqueToBuildImprovements = false    // not canBuildImprovements to avoid confusion

    @Transient
    var hasStrengthBonusInRadiusUnique = false
    @Transient
    var hasCitadelPlacementUnique = false

    /** civName owning the unit */
    lateinit var owner: String

    /** civName of original owner - relevant for returning captured workers from barbarians */
    var originalOwner: String? = null

    /**
     * Name key of the unit, used for serialization
     */
    lateinit var name: String

    /**
     *  Name of this individual unit, usually resulting from promotion
     */
    var instanceName: String? = null

    /**
     * Name which should be displayed in UI
     *
     * Note this is translated after being returned from this function, so let's pay
     * attention to combined names (renamed units, religion).
     */
    fun displayName(): String {
        val baseName =
            if (instanceName == null) "[$name]"
            else "$instanceName ([$name])"

        return if (religion == null) baseName
        else "$baseName ([${getReligionDisplayName()}])"
    }

    fun shortDisplayName(): String {
        return if (instanceName != null) "[$instanceName]"
            else "[$name]"
    }

    var currentMovement: Float = 0f
    var health: Int = 100

    var action: String? = null // work, automation, fortifying, I dunno what.
    @Transient
    var showAdditionalActions: Boolean = false

    var attacksThisTurn = 0
    var promotions = UnitPromotions()
    var due: Boolean = true
    var isTransported: Boolean = false
    var turnsFortified = 0

    var abilityUsesLeft: HashMap<String, Int> = hashMapOf()
    var maxAbilityUses: HashMap<String, Int> = hashMapOf()

    var religion: String? = null
    var religiousStrengthLost = 0

    /**
     * Container class to represent a single instant in a [MapUnit]'s recent movement history.
     *
     * @property position Position on the map at this instant, cloned on instantiation.
     * @property type Category of the last change in position that brought the unit to this position.
     * @see [movementMemories]
     * */
    class UnitMovementMemory(position: Vector2, val type: UnitMovementMemoryType) : IsPartOfGameInfoSerialization {
        @Suppress("unused") // needed because this is part of a save and gets deserialized
        constructor(): this(Vector2.Zero, UnitMovementMemoryType.UnitMoved)
        val position = Vector2(position)

        fun clone() = UnitMovementMemory(position, type)
        override fun toString() = "${this::class.simpleName}($position, $type)"
    }

    /** Deep clone an ArrayList of [UnitMovementMemory]s. */
    private fun ArrayList<UnitMovementMemory>.copy() = ArrayList(this.map { it.clone() })

    /** FIFO list of this unit's past positions. Should never exceed two items in length. New item added once at end of turn and once at start, to allow rare between-turn movements like melee withdrawal to be distinguished. Used in movement arrow overlay. */
    var movementMemories = ArrayList<UnitMovementMemory>()

    /** Add the current position and the most recent movement type to [movementMemories]. Called once at end and once at start of turn, and at unit creation. */
    fun addMovementMemory() {
        movementMemories.add(UnitMovementMemory(getTile().position, mostRecentMoveType))
        while (movementMemories.size > 2) { // O(n) but n == 2.
            // Keep at most one arrow segment— A lot of the time even that won't be rendered because the two positions will be the same.
            // When in the unit's turn— I.E. For a player unit— The last two entries will be from .endTurn() followed by from .startTurn(), so the segment from .movementMemories will have zero length. Instead, what gets seen will be the segment from the end of .movementMemories to the unit's current position.
            // When not in the unit's turn— I.E. For a foreign unit— The segment from the end of .movementMemories to the unit's current position will have zero length, while the last two entries here will be from .startTurn() followed by .endTurn(), so the segment here will be what gets shown.
            // The exception is when a unit changes position when not in its turn, such as by melee withdrawal or foreign territory expulsion. Then the segment here and the segment from the end of here to the current position can both be shown.
            movementMemories.removeFirst()
        }
    }

    /** The most recent type of position change this unit has experienced. Used in movement arrow overlay.*/
    var mostRecentMoveType = UnitMovementMemoryType.UnitMoved

    /** Array list of all the tiles that this unit has attacked since the start of its most recent turn. Used in movement arrow overlay. */
    var attacksSinceTurnStart = ArrayList<Vector2>()

    //region pure functions
    fun clone(): MapUnit {
        val toReturn = MapUnit()
        toReturn.baseUnit = baseUnit
        toReturn.name = name
        toReturn.civInfo = civInfo
        toReturn.owner = owner
        toReturn.originalOwner = originalOwner
        toReturn.instanceName = instanceName
        toReturn.currentMovement = currentMovement
        toReturn.health = health
        toReturn.action = action
        toReturn.attacksThisTurn = attacksThisTurn
        toReturn.turnsFortified = turnsFortified
        toReturn.promotions = promotions.clone()
        toReturn.isTransported = isTransported
        toReturn.abilityUsesLeft.putAll(abilityUsesLeft)
        toReturn.maxAbilityUses.putAll(maxAbilityUses)
        toReturn.religion = religion
        toReturn.religiousStrengthLost = religiousStrengthLost
        toReturn.movementMemories = movementMemories.copy()
        toReturn.mostRecentMoveType = mostRecentMoveType
        toReturn.attacksSinceTurnStart = ArrayList(attacksSinceTurnStart.map { Vector2(it) })
        return toReturn
    }

    val type: UnitType
        get() = baseUnit.getType()

    fun baseUnit(): BaseUnit = baseUnit
    fun getMovementString(): String =
        DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + getMaxMovement()

    fun getTile(): TileInfo = currentTile


    // This SHOULD NOT be a HashSet, because if it is, then e.g. promotions with the same uniques
    //  (e.g. barrage I, barrage II) will not get counted twice!
    @Transient
    private var tempUniques = ArrayList<Unique>()

    @Transient
    private var tempUniquesMap = UniqueMap()

    fun getUniques(): ArrayList<Unique> = tempUniques

    fun getMatchingUniques(
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = StateForConditionals(civInfo, unit=this),
        checkCivInfoUniques: Boolean = false
    ) = sequence {
            yieldAll(
                tempUniquesMap.getMatchingUniques(uniqueType, stateForConditionals)
            )
        if (checkCivInfoUniques)
            yieldAll(civInfo.getMatchingUniques(uniqueType, stateForConditionals))
    }

    // TODO typify usages and remove this function
    @Deprecated("as of 4.0.15", ReplaceWith("hasUnique(uniqueType: UniqueType, ...)"))
    fun hasUnique(unique: String): Boolean {
        return tempUniquesMap.getUniques(unique).any()
    }

    fun hasUnique(
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = StateForConditionals(civInfo, unit=this),
        checkCivInfoUniques: Boolean = false
    ): Boolean {
        return getMatchingUniques(uniqueType, stateForConditionals, checkCivInfoUniques).any()
    }

    fun updateUniques(ruleset: Ruleset) {
        val uniques = ArrayList<Unique>()
        val baseUnit = baseUnit()
        uniques.addAll(baseUnit.uniqueObjects)
        uniques.addAll(type.uniqueObjects)

        for (promotion in promotions.getPromotions()) {
            uniques.addAll(promotion.uniqueObjects)
        }

        tempUniques = uniques
        tempUniquesMap = UniqueMap().apply {
            addUniques(uniques)
        }

        allTilesCosts1 = hasUnique(UniqueType.AllTilesCost1Move)
        canPassThroughImpassableTiles = hasUnique(UniqueType.CanPassImpassable)
        ignoresTerrainCost = hasUnique(UniqueType.IgnoresTerrainCost)
        ignoresZoneOfControl = hasUnique(UniqueType.IgnoresZOC)
        roughTerrainPenalty = hasUnique(UniqueType.RoughTerrainPenalty)

        doubleMovementInTerrain.clear()
        for (unique in getMatchingUniques(UniqueType.DoubleMovementOnTerrain)) {
            val param = unique.params[0]
            val terrain = ruleset.terrains[param]
            doubleMovementInTerrain[param] = when {
                terrain == null -> DoubleMovementTerrainTarget.Filter
                terrain.name == Constants.hill -> DoubleMovementTerrainTarget.Hill
                terrain.type == TerrainType.TerrainFeature -> DoubleMovementTerrainTarget.Feature
                terrain.type.isBaseTerrain -> DoubleMovementTerrainTarget.Base
                else -> DoubleMovementTerrainTarget.Filter
            }
        }
        // Init shortcut flags
        noTerrainMovementUniques = doubleMovementInTerrain.isEmpty() &&
                !roughTerrainPenalty && !civInfo.nation.ignoreHillMovementCost
        noBaseTerrainOrHillDoubleMovementUniques = doubleMovementInTerrain
            .none { it.value != DoubleMovementTerrainTarget.Feature }
        noFilteredDoubleMovementUniques = doubleMovementInTerrain
            .none { it.value == DoubleMovementTerrainTarget.Filter }
        costToDisembark = (getMatchingUniques(UniqueType.ReducedDisembarkCost, checkCivInfoUniques = true))
            .minOfOrNull { it.params[0].toFloat() }
        costToEmbark = getMatchingUniques(UniqueType.ReducedEmbarkCost, checkCivInfoUniques = true)
            .minOfOrNull { it.params[0].toFloat() }

        //todo: consider parameterizing [terrainFilter] in some of the following:
        canEnterIceTiles = hasUnique(UniqueType.CanEnterIceTiles)
        cannotEnterOceanTiles = hasUnique(UniqueType.CannotEnterOcean, StateForConditionals(civInfo=civInfo, unit=this))

        hasUniqueToBuildImprovements = hasUnique(UniqueType.BuildImprovements)
        canEnterForeignTerrain = hasUnique(UniqueType.CanEnterForeignTiles)
            || hasUnique(UniqueType.CanEnterForeignTilesButLosesReligiousStrength)

        hasStrengthBonusInRadiusUnique = hasUnique(UniqueType.StrengthBonusInRadius)
        hasCitadelPlacementUnique = getMatchingUniques(UniqueType.ConstructImprovementConsumingUnit)
            .mapNotNull { civInfo.gameInfo.ruleSet.tileImprovements[it.params[0]] }
            .any { it.hasUnique(UniqueType.TakeOverTilesAroundWhenBuilt) }
    }

    fun copyStatisticsTo(newUnit: MapUnit) {
        newUnit.health = health
        newUnit.instanceName = instanceName
        newUnit.currentMovement = currentMovement
        newUnit.attacksThisTurn = attacksThisTurn
        newUnit.isTransported = isTransported
        for (promotion in newUnit.promotions.promotions)
            if (promotion !in promotions.promotions)
                promotions.addPromotion(promotion, isFree = true)

        newUnit.promotions = promotions.clone()

        newUnit.updateUniques(civInfo.gameInfo.ruleSet)
        newUnit.updateVisibleTiles()
    }


    fun getMaxMovement(): Int {
        var movement =
            if (isEmbarked()) 2
            else baseUnit.movement

        movement += getMatchingUniques(UniqueType.Movement, checkCivInfoUniques = true)
            .sumOf { it.params[0].toInt() }


        if (movement < 1) movement = 1

        return movement
    }

    /**
     * Determines this (land or sea) unit's current maximum vision range from unit properties, civ uniques and terrain.
     * @return Maximum distance of tiles this unit may possibly see
     */
    private fun getVisibilityRange(): Int {
        var visibilityRange = 2

        val conditionalState = StateForConditionals(civInfo = civInfo, unit = this)

        if (isEmbarked() && !hasUnique(UniqueType.NormalVisionWhenEmbarked, conditionalState, checkCivInfoUniques = true)) {
            return 1
        }

        visibilityRange += getMatchingUniques(UniqueType.Sight, conditionalState, checkCivInfoUniques = true)
            .sumOf { it.params[0].toInt() }

        visibilityRange += getTile().getMatchingUniques(UniqueType.Sight, conditionalState)
            .sumOf { it.params[0].toInt() }

        if (visibilityRange < 1) visibilityRange = 1

        return visibilityRange
    }

    /**
     * Update this unit's cache of viewable tiles and its civ's as well.
     */
    fun updateVisibleTiles(updateCivViewableTiles:Boolean = true) {
        val oldViewableTiles = viewableTiles

        viewableTiles = when {
            hasUnique(UniqueType.NoSight) -> hashSetOf()
            hasUnique(UniqueType.CanSeeOverObstacles) ->
                getTile().getTilesInDistance(getVisibilityRange()).toHashSet() // it's that simple
            else -> getTile().getViewableTilesList(getVisibilityRange()).toHashSet()
        }

        // Set equality automatically determines if anything changed - https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-abstract-set/equals.html
        if (updateCivViewableTiles && oldViewableTiles != viewableTiles)
            civInfo.updateViewableTiles() // for the civ
    }

    fun isActionUntilHealed() = action?.endsWith("until healed") == true

    fun isFortified() = action?.startsWith(UnitActionType.Fortify.value) == true
    fun isFortifyingUntilHealed() = isFortified() && isActionUntilHealed()

    fun isSleeping() = action?.startsWith(UnitActionType.Sleep.value) == true
    fun isSleepingUntilHealed() = isSleeping() && isActionUntilHealed()

    fun isMoving() = action?.startsWith("moveTo") == true

    fun isAutomated() = action == UnitActionType.Automate.value
    fun isExploring() = action == UnitActionType.Explore.value
    fun isPreparingParadrop() = action == UnitActionType.Paradrop.value
    fun isPreparingAirSweep() = action == UnitActionType.AirSweep.value
    fun isSetUpForSiege() = action == UnitActionType.SetUp.value

    /** For display in Unit Overview */
    fun getActionLabel() = if (action == null) "" else if (isFortified()) UnitActionType.Fortify.value else if (isMoving()) "Moving" else action!!

    fun isMilitary() = baseUnit.isMilitary()
    fun isCivilian() = baseUnit.isCivilian()

    fun getFortificationTurns(): Int {
        if (!isFortified()) return 0
        return turnsFortified
    }

    // debug helper (please update comment if you see some "$unit" using this)
    override fun toString() = "$name - $owner"


    fun isIdle(): Boolean {
        if (currentMovement == 0f) return false
        val tile = getTile()
        if (tile.improvementInProgress != null &&
                canBuildImprovement(tile.getTileImprovementInProgress()!!) &&
                !tile.isMarkedForCreatesOneImprovement()
            ) return false
        return !(isFortified() || isExploring() || isSleeping() || isAutomated() || isMoving())
    }

    fun maxAttacksPerTurn(): Int {
        return 1 + getMatchingUniques(UniqueType.AdditionalAttacks, checkCivInfoUniques = true)
            .sumOf { it.params[0].toInt() }
    }

    fun canAttack(): Boolean {
        if (currentMovement == 0f) return false
        return attacksThisTurn < maxAttacksPerTurn()
    }

    fun getRange(): Int {
        if (baseUnit.isMelee()) return 1
        var range = baseUnit().range
        range += getMatchingUniques(UniqueType.Range, checkCivInfoUniques = true)
            .sumOf { it.params[0].toInt() }
        return range
    }

    fun getMaxMovementForAirUnits(): Int {
        return getRange() * 2
    }

    fun isEmbarked(): Boolean {
        if (!baseUnit.isLandUnit()) return false
        return currentTile.isWater
    }

    fun isInvisible(to: CivilizationInfo): Boolean {
        if (hasUnique(UniqueType.Invisible))
            return true
        if (hasUnique(UniqueType.InvisibleToNonAdjacent))
            return getTile().getTilesInDistance(1).none {
                it.getUnits().any { unit -> unit.owner == to.civName }
            }
        return false
    }

    /**
     *  Follow the upgrade chain, stopping when there is no [BaseUnit.upgradesTo] or a tech is not researched.
     *  @param  [actionAllowStep] Will be called for each upgrade allowed by tech and has a double purpose:
     *          Side effects, e.g. for aggregation, are allowed, and
     *          returning `false` will abort the upgrade chain and not include the step in the final count.
     *  @return Number of allowed upgrade steps
     */
    private fun followUpgradePath(
        maxSteps: Int = Int.MAX_VALUE,
        actionAllowStep: (oldUnit: BaseUnit, newUnit: BaseUnit)->Boolean
    ): Int {
        var unit = baseUnit()
        var steps = 0

        // Go up the upgrade tree until you find the last one which is buildable
        while(steps < maxSteps) {
            if (unit.upgradesTo == null) break
            val newUnit = unit.getDirectUpgradeUnit(civInfo)
            val techName = newUnit.requiredTech
            if (techName != null && !civInfo.tech.isResearched(techName)) break
            if (!actionAllowStep(unit, newUnit)) break
            unit = newUnit
            steps++
        }
        return steps
    }

    /** Get the base unit this map unit could upgrade to, respecting researched tech and nation uniques only.
     *  Note that if the unit can't upgrade, the current BaseUnit is returned.
     *  @param maxSteps follow the upgrade chain only this far. Useful values are default (directly upgrade to what tech ultimately allows) or 1 (Civ5 behaviour)
     */
    // Used from UnitAutomation, UI action, canUpgrade
    fun getUnitToUpgradeTo(maxSteps: Int = Int.MAX_VALUE): BaseUnit {
        var unit = baseUnit()
        followUpgradePath(maxSteps) { _, newUnit ->
            unit = newUnit
            true
        }
        return unit
    }

    /** Check if the default upgrade would do more than one step
     *  - to avoid showing both the single step and normal upgrades in UnitActions */
    fun canUpgradeMultipleSteps(): Boolean {
        return 1 < followUpgradePath(2) { _, _ -> true }
    }

    /** Check whether this unit can upgrade to [unitToUpgradeTo]. This does not check or follow the
     *  normal upgrade chain defined by [BaseUnit.upgradesTo], unless [unitToUpgradeTo] is left at default.
     *  @param maxSteps only used for default of [unitToUpgradeTo], ignored otherwise.
     *  @param ignoreRequirements Ignore possible tech/policy/building requirements (e.g. resource requirements still count).
     *          Used for upgrading units via ancient ruins.
     *  @param ignoreResources Ignore resource requirements (tech still counts)
     *          Used to display disabled Upgrade button
     */
    fun canUpgrade(
        maxSteps: Int = Int.MAX_VALUE,
        unitToUpgradeTo: BaseUnit = getUnitToUpgradeTo(maxSteps),
        ignoreRequirements: Boolean = false,
        ignoreResources: Boolean = false
    ): Boolean {
        if (name == unitToUpgradeTo.name) return false
        val rejectionReasons = unitToUpgradeTo.getRejectionReasons(civInfo)
        if (rejectionReasons.isOKIgnoringRequirements(ignoreRequirements, ignoreResources)) return true

        // The resource requirements check above did not consider that the resources
        // this unit currently "consumes" are available for an upgrade too - if that's one of the
        // reasons, repeat the check with those resources in the pool.
        if (!rejectionReasons.contains(RejectionReason.ConsumesResources))
            return false

        //TODO redesign without kludge: Inform getRejectionReasons about 'virtually available' resources somehow

        // We need to remove the unit from the civ for this check,
        // because if the unit requires, say, horses, and so does its upgrade,
        // and the civ currently has 0 horses, we need to see if the upgrade will be buildable
        // WHEN THE CURRENT UNIT IS NOT HERE
        civInfo.removeUnit(this)
        val canUpgrade = unitToUpgradeTo.getRejectionReasons(civInfo)
            .isOKIgnoringRequirements(ignoreTechPolicyEraWonderRequirements = ignoreRequirements)
        civInfo.addUnit(this)
        return canUpgrade
    }

    /** Determine gold cost of a Unit Upgrade, potentially over several steps.
     *  @param unitToUpgradeTo the final BaseUnit. Must be reachable via normal upgrades or else
     *         the function will return the cost to upgrade to the last possible and researched normal upgrade.
     *  @return Gold cost in increments of 5, never negative. Will return 0 for invalid inputs (unit can't upgrade or is is already a [unitToUpgradeTo])
     *  @see   <a href="https://github.com/dmnd/CvGameCoreSource/blob/6501d2398113a5100ffa854c146fb6f113992898/CvGameCoreDLL_Expansion1/CvUnit.cpp#L7728">CvUnit::upgradePrice</a>
     */
    // Only one use from getUpgradeAction at the moment, so AI-specific rules omitted
    //todo Does the AI never buy upgrades???
    fun getCostOfUpgrade(unitToUpgradeTo: BaseUnit): Int {
        // Source rounds to int every step, we don't
        //TODO From the source, this should apply _Production_ modifiers (Temple of Artemis? GameSpeed! StartEra!), at the moment it doesn't

        var goldCostOfUpgrade = 0

        val ruleset = civInfo.gameInfo.ruleSet
        val constants = ruleset.modOptions.constants.unitUpgradeCost
        // apply modifiers: Wonders (Pentagon), Policies (Professional Army). Cached outside loop despite
        // the UniqueType being allowed on a BaseUnit - we don't have a MapUnit in the loop.
        // Actually instantiating every intermediate to support such mods: todo
        var civModifier = 1f
        val stateForConditionals = StateForConditionals(civInfo, unit = this)
        for (unique in civInfo.getMatchingUniques(UniqueType.UnitUpgradeCost, stateForConditionals))
            civModifier *= unique.params[0].toPercent()

        followUpgradePath(actionAllowStep = fun(oldUnit: BaseUnit, newUnit: BaseUnit): Boolean {
            // do clamping and rounding here so upgrading stepwise costs the same as upgrading far down the chain
            var stepCost = constants.base
            stepCost += (constants.perProduction * (newUnit.cost - oldUnit.cost)).coerceAtLeast(0f)
            val era = ruleset.eras[ruleset.technologies[newUnit.requiredTech]?.era()]
            if (era != null)
                stepCost *= (1f + era.eraNumber * constants.eraMultiplier)
            stepCost = (stepCost * civModifier).pow(constants.exponent)
            goldCostOfUpgrade += (stepCost / constants.roundTo).toInt() * constants.roundTo
            return newUnit != unitToUpgradeTo  // stop at requested BaseUnit to upgrade to
        })

        return goldCostOfUpgrade
    }


    fun canFortify(): Boolean {
        if (baseUnit.isWaterUnit()) return false
        if (isCivilian()) return false
        if (baseUnit.movesLikeAirUnits()) return false
        if (isEmbarked()) return false
        if (hasUnique(UniqueType.NoDefensiveTerrainBonus)) return false
        if (isFortified()) return false
        return true
    }

    fun fortify() {
        action = "Fortify"
    }

    fun fortifyUntilHealed() {
        action = "Fortify until healed"
    }

    fun fortifyIfCan() {
        if (canFortify()) fortify()
    }

    private fun adjacentHealingBonus(): Int {
        return getMatchingUniques(UniqueType.HealAdjacentUnits).sumOf { it.params[0].toInt() }
    }

    // Only military land units can truly "garrison"
    fun canGarrison() = isMilitary() && baseUnit.isLandUnit()

    fun isGreatPerson() = baseUnit.isGreatPerson()
    fun isGreatPersonOfType(type: String) = baseUnit.isGreatPersonOfType(type)

    //endregion

    //region state-changing functions
    fun setTransients(ruleset: Ruleset) {
        promotions.setTransients(this)
        baseUnit = ruleset.units[name]
            ?: throw java.lang.Exception("Unit $name is not found!")

        updateUniques(ruleset)
    }

    fun useMovementPoints(amount: Float) {
        turnsFortified = 0
        currentMovement -= amount
        if (currentMovement < 0) currentMovement = 0f
    }

    fun getMovementDestination(): TileInfo {
        val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }
        val destinationVector = Vector2(destination[0].toFloat(), destination[1].toFloat())
        return currentTile.tileMap[destinationVector]
    }

    fun doAction() {
        if (action == null) return
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff

        val enemyUnitsInWalkingDistance = movement.getDistanceToTiles().keys
            .filter { it.militaryUnit != null && civInfo.isAtWarWith(it.militaryUnit!!.civInfo) }
        if (enemyUnitsInWalkingDistance.isNotEmpty()) {
            if (isMoving()) // stop on enemy in sight
                action = null
            return  // Don't you dare move.
        }

        val currentTile = getTile()
        if (isMoving()) {
            val destinationTile = getMovementDestination()
            if (!movement.canReach(destinationTile)) { // That tile that we were moving towards is now unreachable -
                // for instance we headed towards an unknown tile and it's apparently unreachable
                action = null
                return
            }
            val gotTo = movement.headTowards(destinationTile)
            if (gotTo == currentTile) // We didn't move at all
                return
            if (gotTo.position == destinationTile.position) action = null
            if (currentMovement > 0) doAction()
            return
        }

        if (isAutomated()) WorkerAutomation.automateWorkerAction(this)

        if (isExploring()) UnitAutomation.automatedExplore(this)
    }


    private fun workOnImprovement() {
        val tile = getTile()
        if (tile.isMarkedForCreatesOneImprovement()) return
        tile.turnsToImprovement -= 1
        if (tile.turnsToImprovement != 0) return

        if (civInfo.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Construct an improvement")

        when {
            tile.improvementInProgress!!.startsWith(Constants.remove) -> {
                val removedFeatureName = tile.improvementInProgress!!.removePrefix(Constants.remove)
                val tileImprovement = tile.getTileImprovement()
                if (tileImprovement != null
                    && tile.terrainFeatures.any {
                        tileImprovement.terrainsCanBeBuiltOn.contains(it) && it == removedFeatureName
                    }
                    && !tileImprovement.terrainsCanBeBuiltOn.contains(tile.baseTerrain)
                ) {
                    // We removed a terrain (e.g. Forest) and the improvement (e.g. Lumber mill) requires it!
                    tile.improvement = null
                    if (tile.resource != null) civInfo.updateDetailedCivResources() // unlikely, but maybe a mod makes a resource improvement dependent on a terrain feature
                }
                if (RoadStatus.values().any { tile.improvementInProgress == it.removeAction })
                    tile.roadStatus = RoadStatus.None
                else {
                    val removedFeatureObject = tile.ruleset.terrains[removedFeatureName]
                    if (removedFeatureObject != null && removedFeatureObject.hasUnique(UniqueType.ProductionBonusWhenRemoved)) {
                        tryProvideProductionToClosestCity(removedFeatureName)
                    }
                    tile.removeTerrainFeature(removedFeatureName)
                }
            }
            tile.improvementInProgress == RoadStatus.Road.name -> tile.roadStatus = RoadStatus.Road
            tile.improvementInProgress == RoadStatus.Railroad.name -> tile.roadStatus = RoadStatus.Railroad
            else -> {
                val improvement = civInfo.gameInfo.ruleSet.tileImprovements[tile.improvementInProgress]!!
                improvement.handleImprovementCompletion(this)
                tile.improvement = tile.improvementInProgress
            }
        }

        tile.improvementInProgress = null
        tile.getCity()?.updateCitizens = true
    }


    private fun tryProvideProductionToClosestCity(removedTerrainFeature: String) {
        val tile = getTile()
        val closestCity = civInfo.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }
        @Suppress("FoldInitializerAndIfToElvis")
        if (closestCity == null) return
        val distance = closestCity.getCenterTile().aerialDistanceTo(tile)
        var productionPointsToAdd = if (distance == 1) 20 else 20 - (distance - 2) * 5
        if (tile.owningCity == null || tile.owningCity!!.civInfo != civInfo) productionPointsToAdd =
            productionPointsToAdd * 2 / 3
        if (productionPointsToAdd > 0) {
            closestCity.cityConstructions.addProductionPoints(productionPointsToAdd)
            val locations = LocationAction(tile.position, closestCity.location)
            civInfo.addNotification(
                "Clearing a [$removedTerrainFeature] has created [$productionPointsToAdd] Production for [${closestCity.name}]",
                locations, NotificationIcon.Construction
            )
        }
    }

    private fun heal() {
        if (isEmbarked()) return // embarked units can't heal
        if (health >= 100) return // No need to heal if at max health
        if (hasUnique(UniqueType.HealOnlyByPillaging, checkCivInfoUniques = true)) return

        val amountToHealBy = rankTileForHealing(getTile())
        if (amountToHealBy == 0) return

        healBy(amountToHealBy)
    }

    fun healBy(amount: Int) {
        health += amount *
            if (hasUnique(UniqueType.HealingEffectsDoubled, checkCivInfoUniques = true)) 2
            else 1
        if (health > 100) health = 100
    }

    /** Returns the health points [MapUnit] will receive if healing on [tileInfo] */
    fun rankTileForHealing(tileInfo: TileInfo): Int {
        val isFriendlyTerritory = tileInfo.isFriendlyTerritory(civInfo)

        var healing = when {
            tileInfo.isCityCenter() -> 25
            tileInfo.isWater && isFriendlyTerritory && (baseUnit.isWaterUnit() || isTransported) -> 20 // Water unit on friendly water
            tileInfo.isWater -> 0 // All other water cases
            isFriendlyTerritory -> 20 // Allied territory
            tileInfo.getOwner() == null -> 10 // Neutral territory
            else -> 10 // Enemy territory
        }

        val mayHeal = healing > 0 || (tileInfo.isWater && hasUnique(UniqueType.HealsOutsideFriendlyTerritory, checkCivInfoUniques = true))
        if (!mayHeal) return healing

        healing += getMatchingUniques(UniqueType.Heal, checkCivInfoUniques = true).sumOf { it.params[0].toInt() }

        val healingCity = tileInfo.getTilesInDistance(1).firstOrNull {
            it.isCityCenter() && it.getCity()!!.getMatchingUniques(UniqueType.CityHealingUnits).any()
        }?.getCity()
        if (healingCity != null) {
            for (unique in healingCity.getMatchingUniques(UniqueType.CityHealingUnits)) {
                if (!matchesFilter(unique.params[0])) continue
                healing += unique.params[1].toInt()
            }
        }

        val maxAdjacentHealingBonus = currentTile.neighbors
            .flatMap { it.getUnits().asSequence() }.filter { it.civInfo == civInfo }
            .map { it.adjacentHealingBonus() }.maxOrNull()
        if (maxAdjacentHealingBonus != null)
            healing += maxAdjacentHealingBonus

        return healing
    }

    fun endTurn() {
        movement.clearPathfindingCache()
        if (currentMovement > 0
            && getTile().improvementInProgress != null
            && canBuildImprovement(getTile().getTileImprovementInProgress()!!)
        ) workOnImprovement()
        if (currentMovement == getMaxMovement().toFloat() && isFortified() && turnsFortified < 2) {
            turnsFortified++
        }
        if (!isFortified())
            turnsFortified = 0

        if (currentMovement == getMaxMovement().toFloat() // didn't move this turn
            || hasUnique(UniqueType.HealsEvenAfterAction)
        ) heal()

        if (action != null && health > 99)
            if (isActionUntilHealed()) {
                action = null // wake up when healed
            }

        if (isPreparingParadrop() || isPreparingAirSweep())
            action = null

        if (hasUnique(UniqueType.ReligiousUnit)
            && getTile().getOwner() != null
            && !getTile().getOwner()!!.isCityState()
            && !civInfo.canPassThroughTiles(getTile().getOwner()!!)
        ) {
            val lostReligiousStrength =
                getMatchingUniques(UniqueType.CanEnterForeignTilesButLosesReligiousStrength)
                    .map { it.params[0].toInt() }
                    .minOrNull()
            if (lostReligiousStrength != null)
                religiousStrengthLost += lostReligiousStrength
            if (religiousStrengthLost >= baseUnit.religiousStrength) {
                civInfo.addNotification("Your [${name}] lost its faith after spending too long inside enemy territory!", getTile().position, name)
                destroy()
            }
        }

        doCitadelDamage()
        doTerrainDamage()

        addMovementMemory()
    }

    fun startTurn() {
        movement.clearPathfindingCache()
        currentMovement = getMaxMovement().toFloat()
        attacksThisTurn = 0
        due = true

        // Hakkapeliitta movement boost
        if (getTile().getUnits().count() > 1)
        {
            // For every double-stacked tile, check if our cohabitant can boost our speed
            for (unit in getTile().getUnits())
            {
                if (unit == this)
                    continue

                if (unit.getMatchingUniques(UniqueType.TransferMovement)
                        .any { matchesFilter(it.params[0]) } )
                    currentMovement = maxOf(getMaxMovement().toFloat(), unit.getMaxMovement().toFloat())
            }
        }

        // Wake sleeping units if there's an enemy in vision range:
        // Military units always but civilians only if not protected.
        if (isSleeping() && (isMilitary() || (currentTile.militaryUnit == null && !currentTile.isCityCenter())) &&
            this.viewableTiles.any {
                it.militaryUnit != null && it.militaryUnit!!.civInfo.isAtWarWith(civInfo)
            }
        )
            action = null

        val tileOwner = getTile().getOwner()
        if (tileOwner != null && !canEnterForeignTerrain && !civInfo.canPassThroughTiles(tileOwner) && !tileOwner.isCityState()) // if an enemy city expanded onto this tile while I was in it
            movement.teleportToClosestMoveableTile()

        addMovementMemory()
        attacksSinceTurnStart.clear()
    }

    fun destroy() {
        val currentPosition = Vector2(getTile().position)
        civInfo.attacksSinceTurnStart.addAll(attacksSinceTurnStart.asSequence().map { CivilizationInfo.HistoricalAttackMemory(this.name, currentPosition, it) })
        currentMovement = 0f
        removeFromTile()
        civInfo.removeUnit(this)
        civInfo.updateViewableTiles()
        // all transported units should be destroyed as well
        currentTile.getUnits().filter { it.isTransported && isTransportTypeOf(it) }
            .toList() // because we're changing the list
            .forEach { unit -> unit.destroy() }
        isDestroyed = true
    }

    fun gift(recipient: CivilizationInfo) {
        civInfo.removeUnit(this)
        civInfo.updateViewableTiles()
        // all transported units should be destroyed as well
        currentTile.getUnits().filter { it.isTransported && isTransportTypeOf(it) }
            .toList() // because we're changing the list
            .forEach { unit -> unit.destroy() }
        assignOwner(recipient)
        recipient.updateViewableTiles()
    }

    /** Destroys the unit and gives stats if its a great person */
    fun consume() {
        addStatsPerGreatPersonUsage()
        destroy()
    }

    private fun addStatsPerGreatPersonUsage() {
        if (!isGreatPerson()) return

        val gainedStats = Stats()
        for (unique in civInfo.getMatchingUniques(UniqueType.ProvidesGoldWheneverGreatPersonExpended)) {
            gainedStats.gold += (100 * civInfo.gameInfo.speed.goldCostModifier).toInt()
        }
        for (unique in civInfo.getMatchingUniques(UniqueType.ProvidesStatsWheneverGreatPersonExpended)) {
            val uniqueStats = unique.stats
            val speedModifiers = civInfo.gameInfo.speed.statCostModifiers
            for (stat in uniqueStats) {
                uniqueStats[stat.key] = stat.value * speedModifiers[stat.key]!!
            }
            gainedStats.add(uniqueStats)
        }

        if (gainedStats.isEmpty()) return

        for (stat in gainedStats)
            civInfo.addStat(stat.key, stat.value.toInt())
        civInfo.addNotification("By expending your [$name] you gained [${gainedStats}]!", getTile().position, name)
    }

    fun removeFromTile() = currentTile.removeUnit(this)

    fun moveThroughTile(tile: TileInfo) {
        // addPromotion requires currentTile to be valid because it accesses ruleset through it.
        // getAncientRuinBonus, if it places a new unit, does too
        currentTile = tile

        if (civInfo.isMajorCiv()
            && tile.improvement != null
            && tile.getTileImprovement()!!.isAncientRuinsEquivalent()
        ) {
            getAncientRuinBonus(tile)
        }
        if (tile.improvement == Constants.barbarianEncampment && !civInfo.isBarbarian())
            clearEncampment(tile)
        // Check whether any civilians without military units are there.
        // Keep in mind that putInTile(), which calls this method,
        // might have already placed your military unit in this tile.
        val unguardedCivilian = tile.getUnguardedCivilian(this)
        // Capture Enemy Civilian Unit if you move on top of it
        if (isMilitary() && unguardedCivilian != null && civInfo.isAtWarWith(unguardedCivilian.civInfo)) {
            Battle.captureCivilianUnit(MapUnitCombatant(this), MapUnitCombatant(tile.civilianUnit!!))
        }

        val promotionUniques = tile.neighbors
            .flatMap { it.allTerrains }
            .flatMap { it.getMatchingUniques(UniqueType.TerrainGrantsPromotion) }
        for (unique in promotionUniques) {
            if (!this.matchesFilter(unique.params[2])) continue
            val promotion = unique.params[0]
            if (promotion in promotions.promotions) continue
            promotions.addPromotion(promotion, true)
        }

        updateVisibleTiles()
    }

    fun putInTile(tile: TileInfo) {
        when {
            !movement.canMoveTo(tile) ->
                throw Exception("Unit $name at $currentTile can't be put in tile ${tile.position}!")
            baseUnit.movesLikeAirUnits() -> tile.airUnits.add(this)
            isCivilian() -> tile.civilianUnit = this
            else -> tile.militaryUnit = this
        }
        // this check is here in order to not load the fresh built unit into carrier right after the build
        isTransported = !tile.isCityCenter() && baseUnit.movesLikeAirUnits()  // not moving civilians
        moveThroughTile(tile)
    }

    private fun clearEncampment(tile: TileInfo) {
        tile.improvement = null

        // Notify City-States that this unit cleared a Barbarian Encampment, required for quests
        civInfo.gameInfo.getAliveCityStates()
            .forEach { it.questManager.barbarianCampCleared(civInfo, tile.position) }

        var goldGained =
            civInfo.getDifficulty().clearBarbarianCampReward * civInfo.gameInfo.speed.goldCostModifier
        if (civInfo.hasUnique(UniqueType.TripleGoldFromEncampmentsAndCities))
            goldGained *= 3f

        civInfo.addGold(goldGained.toInt())
        civInfo.addNotification(
            "We have captured a barbarian encampment and recovered [${goldGained.toInt()}] gold!",
            tile.position,
            NotificationIcon.Gold
        )
    }

    fun disband() {
        // evacuation of transported units before disbanding, if possible. toListed because we're modifying the unit list.
        for (unit in currentTile.getUnits()
            .filter { it.isTransported && isTransportTypeOf(it) }
            .toList()
        ) {
            // if we disbanded a unit carrying other units in a city, the carried units can still stay in the city
            if (currentTile.isCityCenter() && unit.movement.canMoveTo(currentTile)) {
                unit.isTransported = false
                continue
            }
            // if no "fuel" to escape, should be disbanded as well
            if (unit.currentMovement < Constants.minimumMovementEpsilon)
                unit.disband()
            // let's find closest city or another carrier where it can be evacuated
            val tileCanMoveTo = unit.currentTile.getTilesInDistance(unit.getMaxMovementForAirUnits())
                .filterNot { it == currentTile }.firstOrNull { unit.movement.canMoveTo(it) }

            if (tileCanMoveTo != null)
                unit.movement.moveToTile(tileCanMoveTo)
            else
                unit.disband()
        }

        destroy()
        if (currentTile.getOwner() == civInfo)
            civInfo.addGold(baseUnit.getDisbandGold(civInfo))
        if (civInfo.isDefeated()) civInfo.destroy()
    }

    private fun getAncientRuinBonus(tile: TileInfo) {
        tile.improvement = null
        civInfo.ruinsManager.selectNextRuinsReward(this)
    }

    fun assignOwner(civInfo: CivilizationInfo, updateCivInfo: Boolean = true) {
        owner = civInfo.civName
        this.civInfo = civInfo
        civInfo.addUnit(this, updateCivInfo)
    }

    fun capturedBy(captor: CivilizationInfo) {
        civInfo.removeUnit(this)
        assignOwner(captor)
        currentMovement = 0f
        // It's possible that the unit can no longer stand on the tile it was captured on.
        // For example, because it's embarked and the capturing civ cannot embark units yet.
        if (!movement.canPassThrough(getTile())) {
            movement.teleportToClosestMoveableTile()
        }
    }

    fun canIntercept(attackedTile: TileInfo): Boolean {
        if (!canIntercept()) return false
        if (currentTile.aerialDistanceTo(attackedTile) > baseUnit.interceptRange) return false
        return true
    }

    fun canIntercept(): Boolean {
        if (interceptChance() == 0) return false
        // Air Units can only Intercept if they didn't move this turn
        if (baseUnit.isAirUnit() && currentMovement == 0f) return false
        val maxAttacksPerTurn = 1 +
            getMatchingUniques(UniqueType.ExtraInterceptionsPerTurn)
                .sumOf { it.params[0].toInt() }
        if (attacksThisTurn >= maxAttacksPerTurn) return false
        return true
    }

    fun interceptChance(): Int {
        return getMatchingUniques(UniqueType.ChanceInterceptAirAttacks).sumOf { it.params[0].toInt() }
    }

    fun isTransportTypeOf(mapUnit: MapUnit): Boolean {
        // Currently, only missiles and airplanes can be carried
        if (!mapUnit.baseUnit.movesLikeAirUnits()) return false
        return getMatchingUniques(UniqueType.CarryAirUnits).any { mapUnit.matchesFilter(it.params[1]) }
    }

    private fun carryCapacity(unit: MapUnit): Int {
        return (getMatchingUniques(UniqueType.CarryAirUnits)
                + getMatchingUniques(UniqueType.CarryExtraAirUnits))
            .filter { unit.matchesFilter(it.params[1]) }
            .sumOf { it.params[0].toInt() }
    }

    fun canTransport(unit: MapUnit): Boolean {
        if (owner != unit.owner) return false
        if (!isTransportTypeOf(unit)) return false
        if (unit.getMatchingUniques(UniqueType.CannotBeCarriedBy).any { matchesFilter(it.params[0]) }) return false
        if (currentTile.airUnits.count { it.isTransported } >= carryCapacity(unit)) return false
        return true
    }

    fun interceptDamagePercentBonus(): Int {
        return getMatchingUniques(UniqueType.DamageWhenIntercepting)
            .sumOf { it.params[0].toInt() }
    }

    fun receivedInterceptDamageFactor(): Float {
        var damageFactor = 1f
        for (unique in getMatchingUniques(UniqueType.DamageFromInterceptionReduced))
            damageFactor *= 1f - unique.params[0].toFloat() / 100f
        return damageFactor
    }

    private fun doTerrainDamage() {
        val tileDamage = getDamageFromTerrain()
        health -= tileDamage

        if (health <= 0) {
            civInfo.addNotification(
                "Our [$name] took [$tileDamage] tile damage and was destroyed",
                currentTile.position,
                name,
                NotificationIcon.Death
            )
            destroy()
        } else if (tileDamage > 0) civInfo.addNotification(
            "Our [$name] took [$tileDamage] tile damage",
            currentTile.position,
            name
        )
    }

    fun getDamageFromTerrain(tile: TileInfo = currentTile): Int {
        if (civInfo.nonStandardTerrainDamage) {
            for (unique in getMatchingUniques(UniqueType.DamagesContainingUnits)) {
                if (unique.params[0] in tile.allTerrains.map { it.name }) {
                    return unique.params[1].toInt() // Use the damage from the unique
                }
            }
        }
        // Otherwise fall back to the defined standard damage
        return  tile.allTerrains.sumOf { it.damagePerTurn }
    }

    private fun doCitadelDamage() {
        // Check for Citadel damage - note: 'Damage does not stack with other Citadels'
        val (citadelTile, damage) = currentTile.neighbors
            .filter {
                it.getOwner() != null
                && it.improvement != null
                && civInfo.isAtWarWith(it.getOwner()!!)
            }.map { tile ->
                tile to tile.getTileImprovement()!!.getMatchingUniques(UniqueType.DamagesAdjacentEnemyUnits)
                    .sumOf { it.params[0].toInt() }
            }.maxByOrNull { it.second }
            ?: return
        if (damage == 0) return
        health -= damage
        val locations = LocationAction(citadelTile.position, currentTile.position)
        if (health <= 0) {
            civInfo.addNotification(
                "An enemy [Citadel] has destroyed our [$name]",
                locations,
                NotificationIcon.Citadel, NotificationIcon.Death, name
            )
            citadelTile.getOwner()?.addNotification(
                "Your [Citadel] has destroyed an enemy [$name]",
                locations,
                NotificationIcon.Citadel, NotificationIcon.Death, name
            )
            destroy()
        } else civInfo.addNotification(
            "An enemy [Citadel] has attacked our [$name]",
            locations,
            NotificationIcon.Citadel, NotificationIcon.War, name
        )
    }

    /** Implements [UniqueParameterType.MapUnitFilter][com.unciv.models.ruleset.unique.UniqueParameterType.MapUnitFilter] */
    fun matchesFilter(filter: String): Boolean {
        return filter.filterAndLogic { matchesFilter(it) } // multiple types at once - AND logic. Looks like:"{Military} {Land}"
            ?: when (filter) {

            // todo: unit filters should be adjectives, fitting "[filterType] units"
            // This means converting "wounded units" to "Wounded", "Barbarians" to "Barbarian"
            "Wounded", "wounded units" -> health < 100
            Constants.barbarians, "Barbarian" -> civInfo.isBarbarian()
            "City-State" -> civInfo.isCityState()
            "Embarked" -> isEmbarked()
            "Non-City" -> true
            else -> {
                if (baseUnit.matchesFilter(filter)) return true
                if (hasUnique(filter)) return true
                return false
            }
        }
    }

    fun canBuildImprovement(improvement: TileImprovement, tile: TileInfo = currentTile): Boolean {
        // Workers (and similar) should never be able to (instantly) construct things, only build them
        // HOWEVER, they should be able to repair such things if they are pillaged
        if (improvement.turnsToBuild == 0
            && improvement.name != Constants.cancelImprovementOrder
            && tile.improvementInProgress != improvement.name
        ) return false

        return getMatchingUniques(UniqueType.BuildImprovements)
            .any { improvement.matchesFilter(it.params[0]) || tile.matchesTerrainFilter(it.params[0]) }
    }

    fun getReligionDisplayName(): String? {
        if (religion == null) return null
        return civInfo.gameInfo.religions[religion]!!.getReligionDisplayName()
    }

    fun religiousActionsUnitCanDo(): Sequence<String> {
        return getMatchingUniques(UniqueType.CanActionSeveralTimes)
            .map { it.params[0] }
    }

    fun canDoReligiousAction(action: String): Boolean {
        return getMatchingUniques(UniqueType.CanActionSeveralTimes).any { it.params[0] == action }
    }

    /** For the actual value, check the member variable [maxAbilityUses]
     */
    fun getBaseMaxActionUses(action: String): Int {
        return getMatchingUniques(UniqueType.CanActionSeveralTimes)
            .filter { it.params[0] == action }
            .sumOf { it.params[1].toInt() }
    }

    fun setupAbilityUses(buildCity: CityInfo? = null) {
        for (action in religiousActionsUnitCanDo()) {
            val baseAmount = getBaseMaxActionUses(action)
            val additional =
                if (buildCity == null) 0
                else buildCity.getMatchingUniques(UniqueType.UnitStartingActions)
                    .filter { matchesFilter(it.params[0]) && buildCity.matchesFilter(it.params[1]) && it.params[2] == action }
                    .sumOf { it.params[3].toInt() }

            maxAbilityUses[action] = baseAmount + additional
            abilityUsesLeft[action] = baseAmount + additional
        }
    }


    fun getPressureAddedFromSpread(): Int {
        var pressureAdded = baseUnit.religiousStrength.toFloat()

        for (unique in getMatchingUniques(UniqueType.SpreadReligionStrength, checkCivInfoUniques = true))
            pressureAdded *= unique.params[0].toPercent()

        return pressureAdded.toInt()
    }

    fun getActionString(action: String): String {
        val maxActionUses = maxAbilityUses[action]
        if (abilityUsesLeft[action] == null) return "0/0" // Something went wrong
        return "${abilityUsesLeft[action]!!}/${maxActionUses}"
    }

    fun actionsOnDeselect() {
        showAdditionalActions = false
        if (isPreparingParadrop() || isPreparingAirSweep()) action = null
    }

    fun getForceEvaluation(): Int {
        val promotionBonus = (promotions.numberOfPromotions + 1).toFloat().pow(0.3f)
        var power = (baseUnit.getForceEvaluation() * promotionBonus).toInt()
        power *= health
        power /= 100
        return power
    }

    fun threatensCiv(civInfo: CivilizationInfo): Boolean {
        if (getTile().getOwner() == civInfo)
            return true
        return getTile().neighbors.any { it.getOwner() == civInfo }
    }

    //endregion
}
