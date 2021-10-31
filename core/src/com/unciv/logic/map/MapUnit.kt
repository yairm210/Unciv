package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.MultiHashMap
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueMapTyped
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.utils.toPercent
import java.text.DecimalFormat
import kotlin.math.pow

/**
 * The immutable properties and mutable game state of an individual unit present on the map
 */
class MapUnit {

    @Transient
    lateinit var civInfo: CivilizationInfo

    @Transient
    lateinit var baseUnit: BaseUnit

    @Transient
    internal lateinit var currentTile: TileInfo

    @Transient
    val movement = UnitMovementAlgorithms(this)

    @Transient
    var isDestroyed = false

    // This is saved per each unit because if we need to recalculate viewable tiles every time a unit moves,
    //  and we need to go over ALL the units, that's a lot of time spent on updating information we should already know!
    // About 10% of total NextTurn performance time, at the time of this change!
    @Transient
    var viewableTiles = listOf<TileInfo>()

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
    var cannotEnterOceanTilesUntilAstronomy = false

    @Transient
    var canEnterForeignTerrain: Boolean = false

    @Transient
    var paradropRange = 0

    @Transient
    var hasUniqueToBuildImprovements = false    // not canBuildImprovements to avoid confusion

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

    var abilityUsesLeft: HashMap<String, Int> = hashMapOf()
    var maxAbilityUses: HashMap<String, Int> = hashMapOf()

    var religion: String? = null
    var religiousStrengthLost = 0

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
        toReturn.promotions = promotions.clone()
        toReturn.isTransported = isTransported
        toReturn.abilityUsesLeft.putAll(abilityUsesLeft)
        toReturn.maxAbilityUses.putAll(maxAbilityUses)
        toReturn.religion = religion
        toReturn.religiousStrengthLost = religiousStrengthLost
        return toReturn
    }

    val type: UnitType
        get() = baseUnit.getType()

    fun baseUnit(): BaseUnit = baseUnit
    fun getMovementString(): String =
        DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + getMaxMovement()

    fun getTile(): TileInfo = currentTile
    

    // This SHOULD NOT be a HashSet, because if it is, then promotions with the same text (e.g. barrage I, barrage II)
    //  will not get counted twice!
    @Transient
    private var tempUniques = ArrayList<Unique>()

    @Transient
    private var tempUniquesMap = UniqueMapTyped()

    fun getUniques(): ArrayList<Unique> = tempUniques

    fun getMatchingUniques(placeholderText: String): Sequence<Unique> =
        tempUniques.asSequence().filter { it.placeholderText == placeholderText }

    fun getMatchingUniques(
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = StateForConditionals(civInfo, unit=this),
        checkCivInfoUniques:Boolean = false
    ) = sequence {
        val tempUniques = tempUniquesMap[uniqueType]
        if (tempUniques != null)
            yieldAll(
                tempUniques.filter { it.conditionalsApply(stateForConditionals) }
            )
        if (checkCivInfoUniques)
            yieldAll(civInfo.getMatchingUniques(uniqueType, stateForConditionals))
    }

    fun hasUnique(unique: String): Boolean {
        return tempUniques.any { it.placeholderText == unique }
    }

    fun hasUnique(uniqueType: UniqueType, stateForConditionals: StateForConditionals
            = StateForConditionals(civInfo, unit=this)): Boolean {
        return getMatchingUniques(uniqueType, stateForConditionals).any()
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
        val newUniquesMap = UniqueMapTyped()
        for (unique in uniques)
            if (unique.type != null)
                newUniquesMap.addUnique(unique)
        tempUniquesMap = newUniquesMap

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

        //todo: consider parameterizing [terrainFilter] in some of the following:
        canEnterIceTiles = hasUnique(UniqueType.CanEnterIceTiles)
        cannotEnterOceanTiles = hasUnique(UniqueType.CannotEnterOcean)
        cannotEnterOceanTilesUntilAstronomy = hasUnique(UniqueType.CannotEnterOceanUntilAstronomy)

        hasUniqueToBuildImprovements = hasUnique(UniqueType.BuildImprovements)
        canEnterForeignTerrain =
            hasUnique("May enter foreign tiles without open borders, but loses [] religious strength each turn it ends there")
                    || hasUnique("May enter foreign tiles without open borders")
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

        // Deprecated since 3.17.5
            for (unique in getMatchingUniques(UniqueType.MovementUnits, checkCivInfoUniques = true))
                if (matchesFilter(unique.params[1]))
                    movement += unique.params[0].toInt()
    
            if (civInfo.goldenAges.isGoldenAge() &&
                civInfo.hasUnique(UniqueType.MovementGoldenAge)
            )
                movement += 1
        //

        
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

        if (isEmbarked() && !hasUnique(UniqueType.NormalVisionWhenEmbarked, conditionalState)
            && !civInfo.hasUnique(UniqueType.NormalVisionWhenEmbarked, conditionalState)) {
            return 1
        }
        
        visibilityRange += getMatchingUniques(UniqueType.Sight, checkCivInfoUniques = true).sumOf { it.params[0].toInt() }

        // Deprecated since 3.17.5
            for (unique in getMatchingUniques(UniqueType.SightUnits))
                if (matchesFilter(unique.params[1]))
                    visibilityRange += unique.params[0].toInt()
        
        
            visibilityRange += getMatchingUniques(UniqueType.VisibilityRange).sumOf { it.params[0].toInt() }
        
            if (hasUnique(UniqueType.LimitedVisibility)) visibilityRange -= 1
        //

        // Maybe add the uniques of the tile a unit is standing on to the tempUniques of the unit?
        for (unique in getTile().getAllTerrains().flatMap { it.uniqueObjects })
            if (unique.placeholderText == "[] Sight for [] units" && matchesFilter(unique.params[1]))
                visibilityRange += unique.params[0].toInt()

        if (visibilityRange < 1) visibilityRange = 1

        return visibilityRange
    }
    
    /**
     * Update this unit's cache of viewable tiles and its civ's as well.
     */
    fun updateVisibleTiles() {
        if (baseUnit.isAirUnit()) {
            viewableTiles = if (hasUnique("6 tiles in every direction always visible"))
                getTile().getTilesInDistance(6).toList()  // it's that simple
            else listOf() // bomber units don't do recon
            civInfo.updateViewableTiles() // for the civ
            return
        }
        viewableTiles = getTile().getViewableTilesList(getVisibilityRange())
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
    fun isSetUpForSiege() = action == UnitActionType.SetUp.value

    /** For display in Unit Overview */
    fun getActionLabel() = if (action == null) "" else if (isFortified()) UnitActionType.Fortify.value else action!!

    fun isCivilian() = baseUnit.isCivilian()

    fun getFortificationTurns(): Int {
        if (!isFortified()) return 0
        return action!!.split(" ")[1].toInt()
    }

    // debug helper (please update comment if you see some "$unit" using this)
    override fun toString() = "$name - $owner"


    fun isIdle(): Boolean {
        if (currentMovement == 0f) return false
        if (getTile().improvementInProgress != null 
            && canBuildImprovement(getTile().getTileImprovementInProgress()!!)) 
                return false
        return !(isFortified() || isExploring() || isSleeping() || isAutomated() || isMoving())
    }

    fun maxAttacksPerTurn(): Int {
        return 1 + getMatchingUniques("[] additional attacks per turn").sumOf { it.params[0].toInt() }
    }

    fun canAttack(): Boolean {
        if (currentMovement == 0f) return false
        return attacksThisTurn < maxAttacksPerTurn()
    }

    fun getRange(): Int {
        if (baseUnit.isMelee()) return 1
        var range = baseUnit().range
        range += getMatchingUniques("[] Range").sumOf { it.params[0].toInt() }
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
        if (hasUnique("Invisible to others"))
            return true
        if (hasUnique("Invisible to non-adjacent units"))
            return getTile().getTilesInDistance(1).none {
                it.getOwner() == to || it.getUnits().any { unit -> unit.owner == to.civName }
            }
        return false
    }

    fun getUnitToUpgradeTo(): BaseUnit {
        var unit = baseUnit()

        // Go up the upgrade tree until you find the last one which is buildable
        while (unit.upgradesTo != null && unit.getDirectUpgradeUnit(civInfo).requiredTech
                .let { it == null || civInfo.tech.isResearched(it) }
        )
            unit = unit.getDirectUpgradeUnit(civInfo)
        return unit
    }

    /** @param ignoreRequired: Ignore possible tech/policy/building requirements. 
     * Used for upgrading units via ancient ruins.
     */
    fun canUpgrade(unitToUpgradeTo: BaseUnit = getUnitToUpgradeTo(), ignoreRequired: Boolean = false): Boolean {
        // We need to remove the unit from the civ for this check,
        // because if the unit requires, say, horses, and so does its upgrade,
        // and the civ currently has 0 horses,
        // if we don't remove the unit before the check it's return false!

        if (name == unitToUpgradeTo.name) return false
        civInfo.removeUnit(this)
        val canUpgrade = 
            if (ignoreRequired) unitToUpgradeTo.isBuildableIgnoringTechs(civInfo)
            else unitToUpgradeTo.isBuildable(civInfo)
        civInfo.addUnit(this)
        return canUpgrade
    }

    fun getCostOfUpgrade(): Int {
        val unitToUpgradeTo = getUnitToUpgradeTo()
        var goldCostOfUpgrade = (unitToUpgradeTo.cost - baseUnit().cost) * 2f + 10f
        for (unique in civInfo.getMatchingUniques("Gold cost of upgrading [] units reduced by []%")) {
            if (matchesFilter(unique.params[0]))
                goldCostOfUpgrade *= (1 - unique.params[1].toFloat() / 100f)
        }

        if (goldCostOfUpgrade < 0) return 0 // For instance, Landsknecht costs less than Spearman, so upgrading would cost negative gold
        return goldCostOfUpgrade.toInt()
    }


    fun canFortify(): Boolean {
        if (baseUnit.isWaterUnit()) return false
        if (isCivilian()) return false
        if (baseUnit.movesLikeAirUnits()) return false
        if (isEmbarked()) return false
        if (hasUnique("No defensive terrain bonus")) return false
        if (isFortified()) return false
        return true
    }

    fun fortify() {
        action = "Fortify 0"
    }

    fun fortifyUntilHealed() {
        action = "Fortify 0 until healed"
    }

    fun fortifyIfCan() {
        if (canFortify()) fortify()
    }

    private fun adjacentHealingBonus(): Int {
        return getMatchingUniques("All adjacent units heal [] HP when healing").sumOf { it.params[0].toInt() }
    }

    fun canGarrison() = baseUnit.isMilitary() && baseUnit.isLandUnit()

    fun isGreatPerson() = baseUnit.isGreatPerson()

    //endregion

    //region state-changing functions
    fun setTransients(ruleset: Ruleset) {
        promotions.setTransients(this)
        baseUnit = ruleset.units[name]
            ?: throw java.lang.Exception("Unit $name is not found!")

        updateUniques(ruleset)
    }

    fun useMovementPoints(amount: Float) {
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
        tile.turnsToImprovement -= 1
        if (tile.turnsToImprovement != 0) return

        if (civInfo.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Construct an improvement")

        when {
            tile.improvementInProgress!!.startsWith("Remove ") -> {
                val removedFeatureName = tile.improvementInProgress!!.removePrefix("Remove ")
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
                    tile.terrainFeatures.remove(removedFeatureName)
                }
            }
            tile.improvementInProgress == RoadStatus.Road.name -> tile.roadStatus = RoadStatus.Road
            tile.improvementInProgress == RoadStatus.Railroad.name -> tile.roadStatus = RoadStatus.Railroad
            else -> {
                tile.improvement = tile.improvementInProgress
                if (tile.resource != null) civInfo.updateDetailedCivResources()
            }
        }
        tile.improvementInProgress = null
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
            val locations = LocationAction(listOf(tile.position, closestCity.location))
            civInfo.addNotification(
                "Clearing a [$removedTerrainFeature] has created [$productionPointsToAdd] Production for [${closestCity.name}]",
                locations, NotificationIcon.Construction
            )
        }
    }

    private fun heal() {
        if (isEmbarked()) return // embarked units can't heal
        if (civInfo.hasUnique("Can only heal by pillaging")) return

        var amountToHealBy = rankTileForHealing(getTile())
        if (amountToHealBy == 0 && !(hasUnique("May heal outside of friendly territory") && !getTile().isFriendlyTerritory(civInfo))) return

        amountToHealBy += getMatchingUniques("[] HP when healing").sumOf { it.params[0].toInt() }

        val maxAdjacentHealingBonus = currentTile.neighbors
            .flatMap { it.getUnits().asSequence() }.map { it.adjacentHealingBonus() }.maxOrNull()
        if (maxAdjacentHealingBonus != null)
            amountToHealBy += maxAdjacentHealingBonus

        healBy(amountToHealBy)
    }

    fun healBy(amount: Int) {
        health += if (hasUnique("All healing effects doubled"))
                amount * 2
            else
                amount
        if (health > 100) health = 100
    }

    /** Returns the health points [MapUnit] will receive if healing on [tileInfo] */
    fun rankTileForHealing(tileInfo: TileInfo): Int {
        val isFriendlyTerritory = tileInfo.isFriendlyTerritory(civInfo)

        var healing = when {
            tileInfo.isCityCenter() -> 20
            tileInfo.isWater && isFriendlyTerritory && (baseUnit.isWaterUnit() || isTransported) -> 15 // Water unit on friendly water
            tileInfo.isWater -> 0 // All other water cases
            tileInfo.getOwner() == null -> 10 // Neutral territory
            isFriendlyTerritory -> 15 // Allied territory
            else -> 5 // Enemy territory
        }

        val mayHeal = healing > 0 || (tileInfo.isWater && hasUnique("May heal outside of friendly territory"))
        if (!mayHeal) return healing


        for (unique in getMatchingUniques("[] HP when healing in [] tiles")) {
            if (tileInfo.matchesFilter(unique.params[1], civInfo)) {
                healing += unique.params[0].toInt()
            }
        }

        val healingCity = tileInfo.getTilesInDistance(1).firstOrNull {
            it.isCityCenter() && it.getCity()!!.getMatchingUniques("[] Units adjacent to this city heal [] HP per turn when healing").any()
        }?.getCity()
        if (healingCity != null) {
            for (unique in healingCity.getMatchingUniques("[] Units adjacent to this city heal [] HP per turn when healing")) {
                if (!matchesFilter(unique.params[0])) continue
                healing += unique.params[1].toInt()
            }
        }

        return healing
    }

    fun endTurn() {
        if (currentMovement > 0 &&
            getTile().improvementInProgress != null
            && canBuildImprovement(getTile().getTileImprovementInProgress()!!)
        ) workOnImprovement()
        if (currentMovement == getMaxMovement().toFloat() && isFortified()) {
            val currentTurnsFortified = getFortificationTurns()
            if (currentTurnsFortified < 2)
                action = action!!.replace(
                    currentTurnsFortified.toString(),
                    (currentTurnsFortified + 1).toString(),
                    true
                )
        }
        if (hasUnique("Heal adjacent units for an additional 15 HP per turn"))
            currentTile.neighbors.flatMap { it.getUnits() }.forEach { it.healBy(15) }

        if (currentMovement == getMaxMovement().toFloat() // didn't move this turn
            || hasUnique("Unit will heal every turn, even if it performs an action")
        ) heal()

        if (action != null && health > 99)
            if (isActionUntilHealed()) {
                action = null // wake up when healed
            }

        if (isPreparingParadrop())
            action = null

        if (hasUnique("Religious Unit")
            && getTile().getOwner() != null
            && !getTile().getOwner()!!.isCityState()
            && !civInfo.canPassThroughTiles(getTile().getOwner()!!)
        ) {
            val lostReligiousStrength =
                getMatchingUniques("May enter foreign tiles without open borders, but loses [] religious strength each turn it ends there")
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
    }

    fun startTurn() {
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

                if (unit.getMatchingUniques("Transfer Movement to []").any { matchesFilter(it.params[0]) } )
                    currentMovement = maxOf(getMaxMovement().toFloat(), unit.getMaxMovement().toFloat())
            }
        }

        // Wake sleeping units if there's an enemy in vision range:
        // Military units always but civilians only if not protected.
        if (isSleeping() && (baseUnit.isMilitary() || currentTile.militaryUnit == null) &&
            this.viewableTiles.any {
                it.militaryUnit != null && it.militaryUnit!!.civInfo.isAtWarWith(civInfo)
            }
        )
            action = null

        val tileOwner = getTile().getOwner()
        if (tileOwner != null && !canEnterForeignTerrain && !civInfo.canPassThroughTiles(tileOwner) && !tileOwner.isCityState()) // if an enemy city expanded onto this tile while I was in it
            movement.teleportToClosestMoveableTile()
    }

    fun destroy() {
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

        val promotionUniques = tile.neighbors
            .flatMap { it.getAllTerrains() }
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
                throw Exception("I can't go there!")
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

        // Notify city states that this unit cleared a Barbarian Encampment, required for quests
        civInfo.gameInfo.getAliveCityStates()
            .forEach { it.questManager.barbarianCampCleared(civInfo, tile.position) }

        var goldGained =
            civInfo.getDifficulty().clearBarbarianCampReward * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        if (civInfo.hasUnique("Receive triple Gold from Barbarian encampments and pillaging Cities"))
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
        val maxAttacksPerTurn = 1 +
            getMatchingUniques("[] extra interceptions may be made per turn").sumOf { it.params[0].toInt() }
        if (attacksThisTurn >= maxAttacksPerTurn) return false
        return true
    }

    fun interceptChance(): Int {
        return getMatchingUniques("[]% chance to intercept air attacks").sumOf { it.params[0].toInt() }
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
        if (unit.getMatchingUniques(UniqueType.CannotBeCarriedBy).any{matchesFilter(it.params[0])}) return false
        if (currentTile.airUnits.count { it.isTransported } >= carryCapacity(unit)) return false
        return true
    }

    fun interceptDamagePercentBonus(): Int {
        return getMatchingUniques("[]% Damage when intercepting")
            .sumOf { it.params[0].toInt() }
    }

    fun receivedInterceptDamageFactor(): Float {
        var damageFactor = 1f
        for (unique in getMatchingUniques("Damage taken from interception reduced by []%"))
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
                if (unique.params[0] in tile.getAllTerrains().map { it.name }) {
                    return unique.params[1].toInt() // Use the damage from the unique
                }
            }
        }
        // Otherwise fall back to the defined standard damage
        return  tile.getAllTerrains().sumOf { it.damagePerTurn }
    }

    private fun doCitadelDamage() {
        // Check for Citadel damage - note: 'Damage does not stack with other Citadels'
        val (citadelTile, damage) = currentTile.neighbors
            .filter {
                it.getOwner() != null
                && it.improvement != null
                && civInfo.isAtWarWith(it.getOwner()!!)
            }.map { tile ->
                tile to tile.getTileImprovement()!!
                    .getMatchingUniques(UniqueType.DamagesAdjacentEnemyUnits)
                    .sumOf { it.params[0].toInt() }
            }.maxByOrNull { it.second }
            ?: return
        if (damage == 0) return
        health -= damage
        val locations = LocationAction(listOf(citadelTile.position, currentTile.position))
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

    fun matchesFilter(filter: String): Boolean {
        if (filter.contains('{')) // multiple types at once - AND logic. Looks like:"{Military} {Land}"
            return filter.removePrefix("{").removeSuffix("}").split("} {")
                .all { matchesFilter(it) }
        
        return when (filter) {
            // todo: unit filters should be adjectives, fitting "[filterType] units"
            // This means converting "wounded units" to "Wounded", "Barbarians" to "Barbarian"
            "Wounded", "wounded units" -> health < 100
            "Barbarians", "Barbarian" -> civInfo.isBarbarian()
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
        val matchingUniques = getMatchingUniques(UniqueType.BuildImprovements)
        return matchingUniques.any { improvement.matchesFilter(it.params[0]) || tile.matchesTerrainFilter(it.params[0]) }
    }

    fun getReligionDisplayName(): String? {
        if (religion == null) return null
        return civInfo.gameInfo.religions[religion]!!.getReligionDisplayName()
    }

    fun religiousActionsUnitCanDo(): Sequence<String> {
        return getMatchingUniques("Can [] [] times")
            .map { it.params[0] }
    }

    fun canDoReligiousAction(action: String): Boolean {
        return getMatchingUniques("Can [] [] times").any { it.params[0] == action }
    }

    /** For the actual value, check the member variable [maxAbilityUses]
     */
    fun getBaseMaxActionUses(action: String): Int {
        return getMatchingUniques("Can [] [] times")
            .filter { it.params[0] == action }
            .sumOf { it.params[1].toInt() }
    }

    fun setupAbilityUses(buildCity: CityInfo? = null) {
        for (action in religiousActionsUnitCanDo()) {
            val baseAmount = getBaseMaxActionUses(action)
            val additional =
                if (buildCity == null) 0
                else buildCity.getMatchingUniques("[] units built [] can [] [] extra times")
                    .filter { matchesFilter(it.params[0]) && buildCity.matchesFilter(it.params[1]) && it.params[2] == action }
                    .sumOf { it.params[3].toInt() }

            maxAbilityUses[action] = baseAmount + additional
            abilityUsesLeft[action] = baseAmount + additional
        }
    }


    fun getPressureAddedFromSpread(): Int {
        var pressureAdded = baseUnit.religiousStrength.toFloat()

        // Deprecated since 3.17.5
            for (unique in civInfo.getMatchingUniques(UniqueType.SpreadReligionStrengthUnits))
                if (matchesFilter(unique.params[0]))
                    pressureAdded *= unique.params[0].toPercent()
        //

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
        if (isPreparingParadrop()) action = null
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
