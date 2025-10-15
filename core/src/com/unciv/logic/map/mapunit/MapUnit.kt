package com.unciv.logic.map.mapunit

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.MultiFilter
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.battle.BattleUnitCapture
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.movement.UnitMovement
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.translations.tr
import com.unciv.ui.components.UnitMovementMemoryType
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import java.text.DecimalFormat
import kotlin.math.pow
import kotlin.math.ulp


/**
 * The immutable properties and mutable game state of an individual unit present on the map
 */
class MapUnit : IsPartOfGameInfoSerialization {

    //region Persisted fields

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

    /** Should not be changed directly - instead use [useMovementPoints] */
    var currentMovement: Float = 0f
    var health: Int = 100
    var id: Int = Constants.NO_ID

    // work, automation, fortifying, ...
    // Connect roads implies automated is true. It is specified by the action type.
    var action: String? = null
    var automated: Boolean = false

    // We can infer who we are escorting based on our tile
    @Cache private var escorting: Boolean = false

    var automatedRoadConnectionDestination: Vector2? = null
    // Temp disable, since this data broke saves
    @Transient
    var automatedRoadConnectionPath: List<Vector2>? = null

    var attacksThisTurn = 0
    var promotions = UnitPromotions()

    /** Indicates if unit should be located with 'next unit' action */
    var due: Boolean = true

    var isTransported: Boolean = false
    var turnsFortified = 0

    // New - track only *how many have been used*, derive max from uniques, left = max - used
    var abilityToTimesUsed: HashMap<String, Int> = hashMapOf()

    var religion: String? = null
    var religiousStrengthLost = 0

    /** FIFO list of this unit's past positions. Should never exceed two items in length. New item added once at end of turn and once at start, to allow rare between-turn movements like melee withdrawal to be distinguished. Used in movement arrow overlay. */
    var movementMemories = ArrayList<UnitMovementMemory>()

    /** The most recent type of position change this unit has experienced. Used in movement arrow overlay.*/
    var mostRecentMoveType = UnitMovementMemoryType.UnitMoved

    /** Array list of all the tiles that this unit has attacked since the start of its most recent turn. Used in movement arrow overlay. */
    var attacksSinceTurnStart = ArrayList<Vector2>()
    
    class UnitStatus {
        var name:String = ""
        /** Decreses at *start on next turn* so defensive statuses persist on enemy turns */
        var turnsLeft = 1
        
        @Transient
        lateinit var uniques: List<Unique>
        
        fun setTransients(unit: MapUnit) {
            uniques = unit.civ.gameInfo.ruleset.unitPromotions[name]?.uniqueObjects ?: emptyList()
        }
    }
    
    var statusMap = HashMap<String, UnitStatus>()

    //endregion
    //region Transient fields

    @Transient
    lateinit var civ: Civilization

    @Transient
    lateinit var baseUnit: BaseUnit

    @Transient
    lateinit var currentTile: Tile

    fun hasTile() = ::currentTile.isInitialized

    @Transient
    private var tempUniquesMap = UniqueMap()

    @Transient
    /** Temp map excluding unit uniques*/
    private var nonUnitUniquesMap = UniqueMap()

    @Transient
    val movement = UnitMovement(this)

    @Transient
    val upgrade = UnitUpgradeManager(this)

    @Transient
    var isDestroyed = false

    @Transient
    var cache = MapUnitCache(this)

    // This is saved per each unit because if we need to recalculate viewable tiles every time a unit moves,
    //  and we need to go over ALL the units, that's a lot of time spent on updating information we should already know!
    // About 10% of total NextTurn performance time, at the time of this change!
    @Transient
    var viewableTiles = HashSet<Tile>()

    //endregion

    /**
     * Container class to represent a single instant in a [MapUnit]'s recent movement history.
     *
     * @property position Position on the map at this instant, cloned on instantiation.
     * @property type Category of the last change in position that brought the unit to this position.
     * @see [movementMemories]
     * */
    class UnitMovementMemory(position: Vector2, val type: UnitMovementMemoryType) : IsPartOfGameInfoSerialization {
        @Suppress("unused") // needed because this is part of a save and gets deserialized
        constructor() : this(Vector2.Zero, UnitMovementMemoryType.UnitMoved)

        val position = Vector2(position)

        @Readonly fun clone() = UnitMovementMemory(position, type)
        override fun toString() = "${this::class.simpleName}($position, $type)"
    }

    //region pure functions

    // debug helper (please update comment if you see some "$unit" using this - MapUnit.label in WorkerAutomation is console log only as well)
    override fun toString() = "$name - $owner"

    /**
     * Name which should be displayed in UI
     *
     * Note this is translated after being returned from this function, so let's pay
     * attention to combined names (renamed units, religion).
     */
    @Readonly
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

    @Readonly
    fun clone(): MapUnit {
        @LocalState val toReturn = MapUnit()
        toReturn.baseUnit = baseUnit
        toReturn.name = name
        toReturn.civ = civ
        toReturn.id = id
        toReturn.owner = owner
        toReturn.originalOwner = originalOwner
        toReturn.instanceName = instanceName
        toReturn.currentMovement = currentMovement
        toReturn.health = health
        toReturn.action = action
        toReturn.automated = automated
        toReturn.escorting = escorting
        toReturn.automatedRoadConnectionDestination = automatedRoadConnectionDestination
        toReturn.automatedRoadConnectionPath = automatedRoadConnectionPath
        toReturn.attacksThisTurn = attacksThisTurn
        toReturn.turnsFortified = turnsFortified
        toReturn.promotions = promotions.clone()
        toReturn.isTransported = isTransported
        toReturn.abilityToTimesUsed = HashMap(abilityToTimesUsed)
        toReturn.religion = religion
        toReturn.religiousStrengthLost = religiousStrengthLost
        toReturn.movementMemories = movementMemories.copy()
        toReturn.statusMap = HashMap(statusMap)
        toReturn.mostRecentMoveType = mostRecentMoveType
        toReturn.attacksSinceTurnStart = ArrayList(attacksSinceTurnStart.map { Vector2(it) })
        return toReturn
    }

    val type: UnitType
        get() = baseUnit.type

    @Readonly fun getMovementString(): String =
        (DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + getMaxMovement()).tr()

    
    @Readonly fun getTile(): Tile = currentTile

    @Readonly
    fun getClosestCity(): City? = civ.cities.minByOrNull {
        it.getCenterTile().aerialDistanceTo(currentTile)
    }

    @Readonly fun isMilitary() = baseUnit.isMilitary
    @Readonly fun isCivilian() = baseUnit.isCivilian()

    @Readonly fun isActionUntilHealed() = action?.endsWith("until healed") == true

    @Readonly fun isFortified() = action?.startsWith(UnitActionType.Fortify.value) == true
    @Readonly fun isGuarding() = action?.equals(UnitActionType.Guard.value) == true
    @Readonly fun isFortifyingUntilHealed() = isFortified() && isActionUntilHealed()
    @Readonly
    fun getFortificationTurns(): Int {
        if (!(isFortified() || isGuarding())) return 0
        return turnsFortified
    }

    @Readonly fun isSleeping() = action?.startsWith(UnitActionType.Sleep.value) == true
    @Readonly fun isSleepingUntilHealed() = isSleeping() && isActionUntilHealed()

    @Readonly fun isMoving() = action?.startsWith("moveTo") == true
    @Readonly
    fun getMovementDestination(): Tile {
        val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }
        val destinationVector = Vector2(destination[0].toFloat(), destination[1].toFloat())
        return currentTile.tileMap[destinationVector]
    }

    @Readonly fun isAutomated() = automated

    @Readonly fun isAutomatingRoadConnection() = action == UnitActionType.ConnectRoad.value
    @Readonly fun isExploring() = action == UnitActionType.Explore.value
    @Readonly fun isPreparingParadrop() = action == UnitActionType.Paradrop.value
    @Readonly fun isPreparingAirSweep() = action == UnitActionType.AirSweep.value
    @Readonly fun isSetUpForSiege() = action == UnitActionType.SetUp.value

    /**
     * @param includeOtherEscortUnit determines whether this method will also check if it's other escort unit is idle if it has one
     * Leave it as default unless you know what [isIdle] does.
     */
    @Readonly
    fun isIdle(includeOtherEscortUnit: Boolean = true): Boolean {
        if (!hasMovement()) return false
        val tile = getTile()
        if (tile.improvementInProgress != null &&
                canBuildImprovement(tile.getTileImprovementInProgress()!!) &&
                !tile.isMarkedForCreatesOneImprovement()
        ) return false
        if (includeOtherEscortUnit && isEscorting() && !getOtherEscortUnit()!!.isIdle(false)) return false
        return !(isFortified() || isExploring() || isSleeping() || isAutomated() || isMoving() || isGuarding())
    }

    @Readonly fun getUniques(): Sequence<Unique> = tempUniquesMap.getAllUniques()

    @Readonly
    fun getMatchingUniques(
        uniqueType: UniqueType,
        gameContext: GameContext = cache.state,
        checkCivInfoUniques: Boolean = false
    ) = sequence {
        yieldAll(
                tempUniquesMap.getMatchingUniques(uniqueType, gameContext)
        )
        if (checkCivInfoUniques)
            yieldAll(civ.getMatchingUniques(uniqueType, gameContext))
    }

    @Readonly
    fun hasUnique(
        uniqueType: UniqueType,
        gameContext: GameContext = cache.state,
        checkCivInfoUniques: Boolean = false
    ): Boolean {
        return getMatchingUniques(uniqueType, gameContext, checkCivInfoUniques).any()
    }

    @Readonly
    fun getTriggeredUniques(
        trigger: UniqueType,
        gameContext: GameContext = cache.state,
        triggerFilter: (Unique) -> Boolean = { true }
    ): Sequence<Unique> {
        return tempUniquesMap.getTriggeredUniques(trigger, gameContext, triggerFilter)
    }
    

    /** Gets *per turn* resource requirements - does not include immediate costs for stockpiled resources.
     * StateForConditionals is assumed to regarding this mapUnit*/
    @Readonly
    fun getResourceRequirementsPerTurn(): Counter<String> {
        val resourceRequirements = Counter<String>()
        if (baseUnit.requiredResource != null) resourceRequirements[baseUnit.requiredResource!!] = 1
        for (unique in getMatchingUniques(UniqueType.ConsumesResources, cache.state))
            resourceRequirements.add(unique.params[1], unique.params[0].toInt())
        return resourceRequirements
    }

    @Readonly
    fun requiresResource(resource: String): Boolean {
        if (getResourceRequirementsPerTurn().contains(resource)) return true
        for (unique in getMatchingUniques(UniqueType.CostsResources, cache.state)) {
            if (unique.params[1] == resource) return true
        }
        return false
    }

    @Readonly fun hasMovement() = currentMovement > 0

    @Readonly
    fun getMaxMovement(ignoreOtherUnit: Boolean = false): Int {
        var movement =
                if (isEmbarked()) 2
                else baseUnit.movement

        movement += getMatchingUniques(UniqueType.Movement, checkCivInfoUniques = true)
                .sumOf { it.params[0].toInt() }

        if (movement < 1) movement = 1

        // Hakkapeliitta movement boost
        // For every double-stacked tile, check if our cohabitant can boost our speed
        // (a test `count() > 1` is no optimization - two iterations of a sequence instead of one)
        if (!ignoreOtherUnit) { // if both units can boost the other, we avoid an endless loop
            for (boostingUnit in currentTile.getUnits()) {
                if (boostingUnit == this) continue
                if (boostingUnit.getMatchingUniques(UniqueType.TransferMovement)
                        .none { matchesFilter(it.params[0]) }
                ) continue
                movement = movement.coerceAtLeast(boostingUnit.getMaxMovement(true))
            }
        }

        return movement
    }

    @Readonly
    fun hasUnitMovedThisTurn(): Boolean {
        val max = getMaxMovement().toFloat()
        return currentMovement < max - max.ulp
    }

    /**
     * Determines this (land or sea) unit's current maximum vision range from unit properties, civ uniques and terrain.
     * @return Maximum distance of tiles this unit may possibly see
     */
    @Readonly
    fun getVisibilityRange(): Int {
        var visibilityRange = 2

        val conditionalState = cache.state

        val relevantUniques = getMatchingUniques(UniqueType.Sight, conditionalState, checkCivInfoUniques = true) +
                getTile().getMatchingUniques(UniqueType.Sight, conditionalState)
        visibilityRange += relevantUniques.sumOf { it.params[0].toInt() }

        if (visibilityRange < 1) visibilityRange = 1

        return visibilityRange
    }

    @Readonly
    fun maxAttacksPerTurn(): Int {
        return 1 + getMatchingUniques(UniqueType.AdditionalAttacks, checkCivInfoUniques = true)
                .sumOf { it.params[0].toInt() }
    }

    @Readonly
    fun canAttack(): Boolean {
        if (!hasMovement()) return false
        if (isCivilian()) return false
        return attacksThisTurn < maxAttacksPerTurn()
    }

    @Readonly
    fun getRange(): Int {
        if (baseUnit.isMelee()) return 1
        var range = baseUnit.range
        range += getMatchingUniques(UniqueType.Range, checkCivInfoUniques = true)
                .sumOf { it.params[0].toInt() }
        return range
    }

    @Readonly fun getMaxMovementForAirUnits(): Int = getRange() * 2

    @Readonly
    fun isEmbarked(): Boolean {
        if (!baseUnit.isLandUnit) return false
        if (cache.canMoveOnWater) return false
        return currentTile.isWater
    }

    @Readonly
    fun isInvisible(to: Civilization): Boolean {
        if (hasUnique(UniqueType.Invisible) && !to.isSpectator())
            return true
        if (hasUnique(UniqueType.InvisibleToNonAdjacent) && !to.isSpectator())
            return getTile().getTilesInDistance(1).none {
                it.getUnits().any { unit -> unit.owner == to.civName }
            }
        return false
    }

    @Readonly
    fun canFortify(ignoreAlreadyFortified: Boolean = false) = when {
        baseUnit.isWaterUnit -> false
        isCivilian() -> false
        baseUnit.movesLikeAirUnits -> false
        isEmbarked() -> false
        hasUnique(UniqueType.NoDefensiveTerrainBonus, checkCivInfoUniques = true) -> false
        ignoreAlreadyFortified -> true
        isFortified() -> false
        else -> true
    }

    @Readonly
    private fun adjacentHealingBonus(): Int {
        return getMatchingUniques(UniqueType.HealAdjacentUnits).sumOf { it.params[0].toInt() }
    }
    
    @Readonly
    fun getHealAmountForCurrentTile() = when {
        isEmbarked() -> 0 // embarked units can't heal
        health >= 100 -> 0 // No need to heal if at max health
        hasUnique(UniqueType.HealOnlyByPillaging, checkCivInfoUniques = true) -> 0
        else -> rankTileForHealing(getTile())
    }

    @Readonly fun canHealInCurrentTile() = getHealAmountForCurrentTile() > 0

    /** Returns the health points [MapUnit] will receive if healing on [tile] */
    @Readonly
    fun rankTileForHealing(tile: Tile): Int {
        val isFriendlyTerritory = tile.isFriendlyTerritory(civ)

        var healing = when {
            tile.isCityCenter() -> 25
            tile.isWater && isFriendlyTerritory && (baseUnit.isWaterUnit || isTransported) -> 20 // Water unit on friendly water
            tile.isWater && isFriendlyTerritory && cache.canMoveOnWater -> 20 // Treated as a water unit on friendly water
            tile.isWater -> 0 // All other water cases
            isFriendlyTerritory -> 20 // Allied territory
            tile.getOwner() == null -> 10 // Neutral territory
            else -> 10 // Enemy territory
        }

        @Suppress("KotlinConstantConditions") // Wrong warning. isWater **is** dynamic, set in Tile.setTerrainTransients
        val mayHeal = healing > 0 || (tile.isWater && hasUnique(UniqueType.HealsOutsideFriendlyTerritory, checkCivInfoUniques = true))
        @Suppress("KotlinConstantConditions") // Warning is right, but `return healing` reads nicer than `return 0`
        if (!mayHeal) return healing

        healing += getMatchingUniques(UniqueType.Heal, checkCivInfoUniques = true).sumOf { it.params[0].toInt() }

        val healingCity = tile.getTilesInDistance(1).firstOrNull {
            it.isCityCenter() && it.getCity()!!.getMatchingUniques(UniqueType.CityHealingUnits).any()
        }?.getCity()
        if (healingCity != null) {
            for (unique in healingCity.getMatchingUniques(UniqueType.CityHealingUnits)) {
                if (!matchesFilter(unique.params[0]) || !isAlly(healingCity.civ)) continue // only heal our units or allied units
                healing += unique.params[1].toInt()
            }
        }

        val maxAdjacentHealingBonus = currentTile.neighbors
                .flatMap { it.getUnits() }.filter { it.civ == civ }
                .map { it.adjacentHealingBonus() }.maxOrNull()
        if (maxAdjacentHealingBonus != null)
            healing += maxAdjacentHealingBonus
        
        healing -= getDamageFromTerrain(tile)

        return healing
    }

    // Only military land units can truly "garrison"
    @Readonly fun canGarrison() = isMilitary() && baseUnit.isLandUnit

    @Readonly fun isGreatPerson() = baseUnit.isGreatPerson
    @Readonly fun isGreatPersonOfType(type: String) = baseUnit.isGreatPersonOfType(type)

    @Readonly
    fun canIntercept(attackedTile: Tile): Boolean {
        if (!canIntercept()) return false
        if (currentTile.aerialDistanceTo(attackedTile) > getInterceptionRange()) return false
        return true
    }

    @Readonly
    fun getInterceptionRange(): Int {
        val rangeFromUniques = getMatchingUniques(UniqueType.AirInterceptionRange, checkCivInfoUniques = true)
                .sumOf { it.params[0].toInt() }
        return baseUnit.interceptRange + rangeFromUniques
    }

    @Readonly
    fun canIntercept(): Boolean {
        if (interceptChance() == 0) return false
        // Air Units can only Intercept if they didn't move this turn
        if (baseUnit.isAirUnit() && !hasMovement()) return false
        val maxAttacksPerTurn = 1 +
                getMatchingUniques(UniqueType.ExtraInterceptionsPerTurn)
                        .sumOf { it.params[0].toInt() }
        if (attacksThisTurn >= maxAttacksPerTurn) return false
        return true
    }

    @Readonly
    fun interceptChance(): Int {
        return getMatchingUniques(UniqueType.ChanceInterceptAirAttacks).sumOf { it.params[0].toInt() }
    }

    @Readonly
    fun interceptDamagePercentBonus(): Int {
        return getMatchingUniques(UniqueType.DamageWhenIntercepting)
                .sumOf { it.params[0].toInt() }
    }

    @Readonly
    fun receivedInterceptDamageFactor(): Float {
        var damageFactor = 1f
        for (unique in getMatchingUniques(UniqueType.DamageFromInterceptionReduced))
            damageFactor *= 1f - unique.params[0].toFloat() / 100f
        return damageFactor
    }

    @Readonly
    fun getDamageFromTerrain(tile: Tile = currentTile): Int {
        return tile.allTerrains.sumOf { it.damagePerTurn }
    }

    @Readonly
    fun isTransportTypeOf(mapUnit: MapUnit): Boolean {
        // Currently, only missiles and airplanes can be carried
        if (!mapUnit.baseUnit.movesLikeAirUnits) return false
        return getMatchingUniques(UniqueType.CarryAirUnits).any { mapUnit.matchesFilter(it.params[1]) }
    }

    @Readonly
    private fun carryCapacity(unit: MapUnit): Int {
        return (getMatchingUniques(UniqueType.CarryAirUnits)
                + getMatchingUniques(UniqueType.CarryExtraAirUnits))
                .filter { unit.matchesFilter(it.params[1]) }
                .sumOf { it.params[0].toInt() }
    }

    @Readonly
    fun canTransport(unit: MapUnit): Boolean {
        if (owner != unit.owner) return false
        if (!isTransportTypeOf(unit)) return false
        if (unit.getMatchingUniques(UniqueType.CannotBeCarriedBy).any { matchesFilter(it.params[0]) }) return false
        if (currentTile.airUnits.count { it.isTransported } >= carryCapacity(unit)) return false
        return true
    }

    /** Gets a Nuke's blast radius from the BlastRadius unique, defaulting to 2. No check whether the unit actually is a Nuke. */
    @Readonly
    fun getNukeBlastRadius() = getMatchingUniques(UniqueType.BlastRadius)
            // Don't check conditionals as these are not supported
            .firstOrNull()?.params?.get(0)?.toInt() ?: 2

    @Readonly
    private fun isAlly(otherCiv: Civilization): Boolean {
        return otherCiv == civ
                || (otherCiv.isCityState && otherCiv.getAllyCivName() == civ.civName)
                || (civ.isCityState && civ.getAllyCivName() == otherCiv.civName)
    }

    /** Implements [UniqueParameterType.MapUnitFilter][com.unciv.models.ruleset.unique.UniqueParameterType.MapUnitFilter] */
    @Readonly
    fun matchesFilter(filter: String, multiFilter: Boolean = true): Boolean {
        return if (multiFilter) MultiFilter.multiFilter(filter, ::matchesSingleFilter) else matchesSingleFilter(filter)
    }

    @Readonly
    private fun matchesSingleFilter(filter: String): Boolean {
        return when (filter) {
            Constants.wounded, "wounded units" -> health < 100
            Constants.barbarians, "Barbarian" -> civ.isBarbarian
            "City-State" -> civ.isCityState
            Constants.embarked -> isEmbarked()
            "Non-City" -> true
            else -> {
                if (baseUnit.matchesFilter(filter, cache.state, false)) return true
                if (civ.matchesFilter(filter, cache.state, false)) return true
                if (nonUnitUniquesMap.hasUnique(filter, cache.state)) return true
                if (promotions.promotions.contains(filter)) return true
                if (hasStatus(filter)) return true 
                return false
            }
        }
    }

    @Readonly
    fun canBuildImprovement(improvement: TileImprovement, tile: Tile = currentTile): Boolean {
        if (civ.isBarbarian) return false
        
        // Workers (and similar) should never be able to (instantly) construct things, only build them
        // HOWEVER, they should be able to repair such things if they are pillaged
        if (improvement.turnsToBuild == -1
                && improvement.name != Constants.cancelImprovementOrder
                && tile.improvementInProgress != improvement.name
        ) return false
        val buildImprovementUniques = getMatchingUniques(UniqueType.BuildImprovements)
        if (tile.improvementInProgress == Constants.repair) {
            if (tile.isEnemyTerritory(civ)) return false
            return buildImprovementUniques.any()
        }
        return buildImprovementUniques
                .any { improvement.matchesFilter(it.params[0], cache.state) || tile.matchesTerrainFilter(it.params[0], civ) }
    }

    @Readonly
    fun getReligionDisplayName(): String? {
        if (religion == null) return null
        return civ.gameInfo.religions[religion]!!.getReligionDisplayName()
    }

    @Readonly
    fun getForceEvaluation(): Int {
        val promotionBonus = (promotions.numberOfPromotions + 1).toFloat().pow(0.3f)
        var power = (baseUnit.getForceEvaluation() * promotionBonus).toInt()
        power *= health
        power /= 100
        return power
    }

    @Readonly
    fun getOtherEscortUnit(): MapUnit? {
        if (!::currentTile.isInitialized) return null // In some cases we might not have the unit placed on the map yet
        if (isCivilian()) return getTile().militaryUnit
        if (isMilitary()) return getTile().civilianUnit
        return null
    }

    @Readonly
    fun isEscorting(): Boolean {
        if (escorting) { 
            if (getOtherEscortUnit() != null) return true
            escorting = false
        }
        return false
    }

    @Readonly
    fun threatensCiv(civInfo: Civilization): Boolean {
        if (getTile().getOwner() == civInfo)
            return true
        return getTile().neighbors.any { it.getOwner() == civInfo }
    }

    /** Deep clone an ArrayList of [UnitMovementMemory]s. */
    @Readonly private fun ArrayList<UnitMovementMemory>.copy() = ArrayList(this.map { it.clone() })

    //endregion
    //region state-changing functions

    fun setTransients(ruleset: Ruleset) {
        promotions.setTransients(this)
        baseUnit = ruleset.units[name]
                ?: throw java.lang.Exception("Unit $name is not found!")
        
        for (status in statusMap.values) status.setTransients(this)
        updateUniques()
        if (action == UnitActionType.Automate.value){
            automated = true
            action = null
        }
    }

    fun updateUniques() {
        val otherUniqueSources = promotions.getPromotions().flatMap { it.uniqueObjects } +
            statusMap.values.flatMap { it.uniques }
        val uniqueSources = baseUnit.rulesetUniqueObjects.asSequence() + otherUniqueSources
        
        tempUniquesMap = UniqueMap(uniqueSources)
        nonUnitUniquesMap = UniqueMap(otherUniqueSources)
        cache.updateUniques()
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
        newUnit.automated = automated
        newUnit.action = action // Needed too for Unit Overview action column

        newUnit.updateUniques()
        newUnit.updateVisibleTiles()
    }

    /**
     * Update this unit's cache of viewable tiles and its civ's as well.
     */
    fun updateVisibleTiles(updateCivViewableTiles: Boolean = true, explorerPosition: Vector2? = null) {
        val oldViewableTiles = viewableTiles

        viewableTiles = when {
            hasUnique(UniqueType.NoSight) -> hashSetOf(getTile()) // 0 sight distance still means we can see the Tile we're in
            hasUnique(UniqueType.CanSeeOverObstacles) ->
                getTile().getTilesInDistance(getVisibilityRange()).toHashSet() // it's that simple
            else -> getTile().getViewableTilesList(getVisibilityRange()).toHashSet()
        }

        // Set equality automatically determines if anything changed - https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-abstract-set/equals.html
        
        val shouldUpdateTiles = updateCivViewableTiles && oldViewableTiles != viewableTiles
                // Don't bother updating if all previous and current viewable tiles are within our borders
                && (oldViewableTiles.any { it !in civ.cache.ourTilesAndNeighboringTiles } 
                || viewableTiles.any { it !in civ.cache.ourTilesAndNeighboringTiles })

        if (!shouldUpdateTiles) return
        
        val unfilteredTriggeredUniques = getTriggeredUniques(UniqueType.TriggerUponDiscoveringTile, GameContext.IgnoreConditionals).toList()
        if (unfilteredTriggeredUniques.isNotEmpty()) {
            val newlyExploredTiles = viewableTiles.filter { !it.isExplored(civ) }
            for (tile in newlyExploredTiles) {
                val state = cache.state
                for (unique in unfilteredTriggeredUniques) {
                    if (unique.getModifiers(UniqueType.TriggerUponDiscoveringTile)
                            .any { tile.matchesFilter(it.params[0], civ) }
                        && unique.conditionalsApply(state)
                    )
                        UniqueTriggerActivation.triggerUnique(unique, this)
                }
            }
        }

        civ.cache.updateViewableTiles(explorerPosition)
    }

    /** Can accept a negative number to gain movement points */
    fun useMovementPoints(amount: Float) {
        turnsFortified = 0
        currentMovement -= amount
        if (currentMovement < 0) currentMovement = 0f
        if (amount < 0) {
            val maxMovement = getMaxMovement().toFloat()
            if (currentMovement > maxMovement) currentMovement = maxMovement
        }
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

    fun doAction() {
        if (action == null && !isAutomated()) return
        if (!hasMovement()) return  // We've already done stuff this turn, and can't do any more stuff
        if (isEscorting() && !getOtherEscortUnit()!!.hasMovement()) return

        val enemyUnitsInWalkingDistance = movement.getDistanceToTiles().keys
                .filter { it.militaryUnit != null && civ.isAtWarWith(it.militaryUnit!!.civ) }
        if (enemyUnitsInWalkingDistance.isNotEmpty()) {
            if (isMoving()) // stop on enemy in sight
                action = null
            if (!(isExploring() || isAutomated()))  // have fleeing code
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
            if (gotTo == currentTile) { // We didn't move at all
                // pathway blocked? Are we still at the same spot as start of turn?
                if (movementMemories.last().position == currentTile.position)
                    action = null
                return
            }
            if (gotTo.position == destinationTile.position) action = null
            if (hasMovement()) doAction()
            return
        }

        if (isAutomated()) UnitAutomation.automateUnitMoves(this)

        if (isExploring()) UnitAutomation.automatedExplore(this)
    }

    fun healBy(amount: Int) {
        health += amount *
                if (hasUnique(UniqueType.HealingEffectsDoubled, checkCivInfoUniques = true)) 2
                else 1
        if (health > 100) health = 100
        cache.updateUniques()
    }

    fun takeDamage(amount: Int) {
        health -= amount
        if (health > 100) health = 100 // For cheating modders, e.g. negative tile damage
        if (health < 0) health = 0
        if (health == 0) destroy()
        else cache.updateUniques()
    }

    fun destroy(destroyTransportedUnit: Boolean = true) {
        stopEscorting()
        currentMovement = 0f
        civ.units.removeUnit(this)
        if (::currentTile.isInitialized) {
            val currentPosition = Vector2(getTile().position)
            civ.attacksSinceTurnStart.addAll(attacksSinceTurnStart.asSequence().map { Civilization.HistoricalAttackMemory(this.name, currentPosition, it) })
            removeFromTile()
            civ.cache.updateViewableTiles()
            if (destroyTransportedUnit) {
                // all transported units should be destroyed as well
                currentTile.getUnits().filter { it.isTransported && isTransportTypeOf(it) }
                        .toList() // because we're changing the list
                        .forEach { unit -> unit.destroy() }
            }
        }

        isDestroyed = true
    }

    fun gift(recipient: Civilization) {
        stopEscorting()
        civ.units.removeUnit(this)
        civ.cache.updateViewableTiles()
        // all transported units should be gift as well
        currentTile.getUnits().filter { it.isTransported && isTransportTypeOf(it) }
                .forEach { unit -> unit.gift(recipient) }
        assignOwner(recipient)
        recipient.cache.updateViewableTiles()
    }

    /** Destroys the unit and gives stats if its a great person */
    fun consume() {
        for (unique in civ.getTriggeredUniques(UniqueType.TriggerUponExpendingUnit){ matchesFilter(it.params[0]) })
            UniqueTriggerActivation.triggerUnique(unique, this,
                triggerNotificationText = "due to expending our [${this.name}]")
        destroy()
    }

    fun removeFromTile() = currentTile.removeUnit(this)


    /** Return null if military on tile, or no civilian */
    // Could be local to moveThroughTile, therefore left in the state-changing region
    private fun Tile.getUnguardedCivilian(attacker: MapUnit): MapUnit? {
        return when {
            militaryUnit != null && militaryUnit != attacker -> null
            civilianUnit != null -> civilianUnit!!
            else -> null
        }
    }

    fun moveThroughTile(tile: Tile) {
        // addPromotion requires currentTile to be valid because it accesses ruleset through it.
        // getAncientRuinBonus, if it places a new unit, does too
        currentTile = tile
        // The state also needs to be valid for uniques to see the cached version
        cache.state = GameContext(this)
        // The improvement may get removed if it has ruins effects or is a barbarian camp, and will still be needed if removed
        val improvement = tile.improvement


        // To allow triggering on barb camps and ruins, must happen before entering them
        val triggeredUniques = getTriggeredUniques(UniqueType.TriggerUponEnteringTile) { tile.matchesFilter(it.params[0]) }
        for (triggeredUnique in triggeredUniques)
            UniqueTriggerActivation.triggerUnique(triggeredUnique, this)

        if (civ.isMajorCiv() && tile.getTileImprovement()?.isAncientRuinsEquivalent() == true) {
            getAncientRuinBonus(tile)
        }
        if (improvement == Constants.barbarianEncampment && !civ.isBarbarian)
            clearEncampment(tile)
        // Check whether any civilians without military units are there.
        // Keep in mind that putInTile(), which calls this method,
        // might have already placed your military unit in this tile.
        val unguardedCivilian = tile.getUnguardedCivilian(this)
        // Capture Enemy Civilian Unit if you move on top of it
        if (isMilitary() && unguardedCivilian != null && civ.isAtWarWith(unguardedCivilian.civ)) {
            BattleUnitCapture.captureCivilianUnit(MapUnitCombatant(this), MapUnitCombatant(tile.civilianUnit!!))
        }

        val promotionUniques = tile.neighbors
                .flatMap { it.allTerrains }
                .flatMap { it.getMatchingUniques(UniqueType.TerrainGrantsPromotion) }
        for (unique in promotionUniques) {
            if (!this.matchesFilter(unique.params[2])) continue
            val promotion = unique.params[0]
            promotions.addPromotion(promotion, true)
        }
            
        updateVisibleTiles(true, currentTile.position)
    }

    fun putInTile(tile: Tile) {
        when {
            !movement.canMoveTo(tile) ->
                throw Exception("Unit $name of ${civ.civName} at $currentTile can't be put in tile $tile!")

            baseUnit.movesLikeAirUnits -> tile.airUnits.add(this)
            isCivilian() -> tile.civilianUnit = this
            else -> tile.militaryUnit = this
        }
        // this check is here in order to not load the fresh built unit into carrier right after the build
        if (baseUnit.movesLikeAirUnits){
            if (!tile.isCityCenter()) isTransported = true
            else {
                val currentUntransportedUnits = tile.getUnits().count { it.type.isAirUnit() && !it.isTransported }
                // Tile units includes us, we were just added
                isTransported = currentUntransportedUnits > tile.getCity()!!.getMaxAirUnits()
            }
        }
        moveThroughTile(tile)
        cache.updateUniques()
    }

    fun startEscorting() {
        if (getOtherEscortUnit() != null) {
            escorting = true
            getOtherEscortUnit()!!.escorting = true
        } else {
            escorting = false
        }
        movement.clearPathfindingCache()
    }

    fun stopEscorting() {
        getOtherEscortUnit()?.escorting = false
        escorting = false
        if (::currentTile.isInitialized) // Can cause an error if the unit has not been placed on the map yet.
            movement.clearPathfindingCache()
    }

    private fun clearEncampment(tile: Tile) {
        tile.removeImprovement()

        // Notify City-States that this unit cleared a Barbarian Encampment, required for quests
        civ.gameInfo.getAliveCityStates()
                .forEach { it.questManager.barbarianCampCleared(civ, tile.position) }

        var goldGained =
                civ.getDifficulty().clearBarbarianCampReward * civ.gameInfo.speed.goldCostModifier
        if (civ.hasUnique(UniqueType.TripleGoldFromEncampmentsAndCities))
            goldGained *= 3f

        civ.addGold(goldGained.toInt())
        civ.addNotification(
                "We have captured a barbarian encampment and recovered [${goldGained.toInt()}] gold!",
                tile.position,
                NotificationCategory.War,
                NotificationIcon.Gold
        )
    }

    fun disband() {
        // Safeguard against running on already destroyed instances
        if (isDestroyed) return

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
        if (currentTile.getOwner() == civ)
            civ.addGold(baseUnit.getDisbandGold(civ))
        if (civ.isDefeated()) civ.destroy()
    }

    private fun getAncientRuinBonus(tile: Tile) {
        tile.removeImprovement()
        civ.ruinsManager.selectNextRuinsReward(this)
    }

    /** Assigns ownership to [civInfo], updating its [unit manager][Civilization.units] and our [cache].
     *
     *  Used during game load to rebuild the transient `UnitManager.unitList` from Tile data.
     *  Cannot be used to reassign from one civ to another - doesn't remove from old owner.
     */
    fun assignOwner(civInfo: Civilization, updateCivInfo: Boolean = true) {
        owner = civInfo.civName
        this.civ = civInfo
        civInfo.units.addUnit(this, updateCivInfo)
        // commit named "Fixed game load": GameInfo.setTransients code flow and dependency requirements
        // may lead to this being called before our own setTransients
        if (::baseUnit.isInitialized)
            cache.updateUniques()
    }

    fun capturedBy(captor: Civilization) {
        civ.units.removeUnit(this)
        assignOwner(captor)
        currentMovement = 0f
        // It's possible that the unit can no longer stand on the tile it was captured on.
        // For example, because it's embarked and the capturing civ cannot embark units yet.
        if (!movement.canPassThrough(getTile())) {
            movement.teleportToClosestMoveableTile()
        }
    }

    fun actionsOnDeselect() {
        if (isPreparingParadrop() || isPreparingAirSweep()) action = null
    }

    /** Add the current position and the most recent movement type to [movementMemories]. Called once at end and once at start of turn, and at unit creation. */
    fun addMovementMemory() {
        movementMemories.add(UnitMovementMemory(getTile().position, mostRecentMoveType))
        while (movementMemories.size > 2) { // O(n) but n == 2.
            // Keep at most one arrow segment— A lot of the time even that won't be rendered because the two positions will be the same.
            // When in the unit's turn— I.E. For a player unit— The last two entries will be from .endTurn() followed by from .startTurn(), so the segment from .movementMemories will have zero length. Instead, what gets seen will be the segment from the end of .movementMemories to the unit's current position.
            // When not in the unit's turn— I.E. For a foreign unit— The segment from the end of .movementMemories to the unit's current position will have zero length, while the last two entries here will be from .startTurn() followed by .endTurn(), so the segment here will be what gets shown.
            // The exception is when a unit changes position when not in its turn, such as by melee withdrawal or foreign territory expulsion. Then the segment here and the segment from the end of here to the current position can both be shown.
            movementMemories.removeAt(0)
        }
    }

    @Readonly fun getStatus(name:String): UnitStatus? = statusMap[name]
    @Readonly fun hasStatus(name:String): Boolean = getStatus(name) != null
    
    fun setStatus(name:String, turns:Int){
        val existingStatus = getStatus(name)
        if (existingStatus != null){
            if (turns > existingStatus.turnsLeft) existingStatus.turnsLeft = turns
            return
        }
        
        val status = UnitStatus()
        status.name = name
        status.turnsLeft = turns
        status.setTransients(this)
        statusMap[status.name] = status
        updateUniques()

        for (unique in getTriggeredUniques(UniqueType.TriggerUponStatusGain){ it.params[0] == name })
            UniqueTriggerActivation.triggerUnique(unique, this)
    }
    
    fun removeStatus(name:String){
        statusMap.remove(name) ?: return

        updateUniques()

        for (unique in getTriggeredUniques(UniqueType.TriggerUponStatusLoss){ it.params[0] == name })
            UniqueTriggerActivation.triggerUnique(unique, this)
    }

    /**
     * Sets the instance name should the unit be needing a historical figure name.
     */
    fun setHistoricalFigureName() {
        if (hasUnique(UniqueType.CanBeAHistoricalFigure, cache.state)) {
            // Get both the name and associated unique for the historical figure
            val pair = getMatchingUniques(UniqueType.CanBeAHistoricalFigure, cache.state)
                .map { it.params[0] }.distinct()
                .flatMap {
                    // Get all historical figure groups that match the name or a tag
                    civ.gameInfo.ruleset.historicalFigures.values.filter {
                        hf -> hf.name == it || hf.hasTagUnique(it, cache.state)
                    }
                }
                .distinct()
                .filter {
                    // Validate that it's available
                    it.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                        .none { unique -> !unique.conditionalsApply(cache.state) } &&
                    it.getMatchingUniques(UniqueType.Unavailable, GameContext.IgnoreConditionals)
                        .none { unique -> unique.conditionalsApply(cache.state) }
                }
                .flatMap { hf -> 
                    // Grab only names that haven't been taken
                    hf.names.filter {
                        name -> name !in civ.gameInfo.historicalFiguresTaken
                    }
                    // Make a pair of the name and the historical figure instance
                    .map { it to hf }
                }
                .shuffled().firstOrNull()
            if (pair != null) {
                instanceName = pair.first
                civ.gameInfo.historicalFiguresTaken.add(pair.first)
                promotions.addPromotion(pair.first, true)
                for (unique in pair.second.uniqueObjects) {
                    if (unique.isTriggerable && !unique.hasTriggerConditional() && unique.conditionalsApply(cache.state)) {
                        UniqueTriggerActivation.triggerUnique(unique, this)
                    }
                }
            }
        }
    }

    fun isNuclearWeapon() = hasUnique(UniqueType.NuclearWeapon)
    //endregion
}
