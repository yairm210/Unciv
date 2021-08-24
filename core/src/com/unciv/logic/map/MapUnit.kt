package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
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

    @Transient
    var ignoresZoneOfControl = false

    @Transient
    var allTilesCosts1 = false

    @Transient
    var canPassThroughImpassableTiles = false

    @Transient
    var roughTerrainPenalty = false

    @Transient
    var doubleMovementInCoast = false

    @Transient
    var doubleMovementInForestAndJungle = false

    @Transient
    var doubleMovementInSnowTundraAndHills = false

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

    lateinit var owner: String

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
        val name = if (instanceName == null) name
                   else "$instanceName ({${name}})"
        return if (religion != null) "[$name] ([$religion])"
               else name
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
    
    var abilityUsedCount: HashMap<String, Int> = hashMapOf()
    var religion: String? = null
    var religiousStrengthLost = 0

    //region pure functions
    fun clone(): MapUnit {
        val toReturn = MapUnit()
        toReturn.baseUnit = baseUnit
        toReturn.name = name
        toReturn.civInfo = civInfo
        toReturn.owner = owner
        toReturn.instanceName = instanceName
        toReturn.currentMovement = currentMovement
        toReturn.health = health
        toReturn.action = action
        toReturn.attacksThisTurn = attacksThisTurn
        toReturn.promotions = promotions.clone()
        toReturn.isTransported = isTransported
        toReturn.abilityUsedCount.putAll(abilityUsedCount)
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
    fun getMaxMovement(): Int {
        if (isEmbarked()) return getEmbarkedMovement()

        var movement = baseUnit.movement
        movement += getMatchingUniques("[] Movement").sumBy { it.params[0].toInt() }
        
        // Deprecated since 3.15.6
            movement += getUniques().count { it.text == "+1 Movement" }
        //
        
        for (unique in civInfo.getMatchingUniques("+[] Movement for all [] units"))
            if (matchesFilter(unique.params[1]))
                movement += unique.params[0].toInt()

        if (civInfo.goldenAges.isGoldenAge() &&
            civInfo.hasUnique("+1 Movement for all units during Golden Age")
        )
            movement += 1

        return movement
    }


    // This SHOULD NOT be a HashSet, because if it is, then promotions with the same text (e.g. barrage I, barrage II)
    //  will not get counted twice!
    @Transient
    private var tempUniques = ArrayList<Unique>()

    fun getUniques(): ArrayList<Unique> = tempUniques

    fun getMatchingUniques(placeholderText: String): Sequence<Unique> =
        tempUniques.asSequence().filter { it.placeholderText == placeholderText }

    fun hasUnique(unique: String): Boolean {
        return getUniques().any { it.placeholderText == unique }
    }

    fun updateUniques() {
        val uniques = ArrayList<Unique>()
        val baseUnit = baseUnit()
        uniques.addAll(baseUnit.uniqueObjects)
        uniques.addAll(type.uniqueObjects)
        
        for (promotion in promotions.promotions) {
            uniques.addAll(currentTile.tileMap.gameInfo.ruleSet.unitPromotions[promotion]!!.uniqueObjects)
        }

        tempUniques = uniques

        //todo: parameterize [terrainFilter] in 5 to 7 of the following:

        // "All tiles costs 1" obsoleted in 3.11.18 - keyword: Deprecate
        allTilesCosts1 = hasUnique("All tiles cost 1 movement") || hasUnique("All tiles costs 1")
        canPassThroughImpassableTiles = hasUnique("Can pass through impassable tiles")
        ignoresTerrainCost = hasUnique("Ignores terrain cost")
        ignoresZoneOfControl = hasUnique("Ignores Zone of Control")
        roughTerrainPenalty = hasUnique("Rough terrain penalty")
        doubleMovementInCoast = hasUnique("Double movement in coast")
        doubleMovementInForestAndJungle = hasUnique("Double movement rate through Forest and Jungle")
        doubleMovementInSnowTundraAndHills = hasUnique("Double movement in Snow, Tundra and Hills")
        canEnterIceTiles = hasUnique("Can enter ice tiles")
        cannotEnterOceanTiles = hasUnique("Cannot enter ocean tiles")
        cannotEnterOceanTilesUntilAstronomy = hasUnique("Cannot enter ocean tiles until Astronomy")
        // Constants.workerUnique deprecated since 3.15.5
        hasUniqueToBuildImprovements = hasUnique(Constants.canBuildImprovements) || hasUnique(Constants.workerUnique)
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
        
        newUnit.updateUniques()
        newUnit.updateVisibleTiles()
    }
    
    /**
     * Determines this (land or sea) unit's current maximum vision range from unit properties, civ uniques and terrain.
     * @return Maximum distance of tiles this unit may possibly see
     */
    private fun getVisibilityRange(): Int {
        var visibilityRange = 2
        for (unique in civInfo.getMatchingUniques("+[] Sight for all [] units"))
            if (matchesFilter(unique.params[1]))
                visibilityRange += unique.params[0].toInt()
        visibilityRange += getMatchingUniques("[] Visibility Range").sumBy { it.params[0].toInt() }
        
        if (hasUnique("Limited Visibility")) visibilityRange -= 1

        // Deprecated since 3.15.1
            if (civInfo.hasUnique("+1 Sight for all land military units") && baseUnit.isMilitary() && baseUnit.isLandUnit())
                visibilityRange += 1
        //


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
        // Constants.workerUnique deprecated since 3.15.5
        if (getTile().improvementInProgress != null 
            && canBuildImprovement(getTile().getTileImprovementInProgress()!!)) 
                return false
        // unique "Can construct roads" deprecated since 3.15.5
            if (hasUnique("Can construct roads") && currentTile.improvementInProgress == RoadStatus.Road.name) return false
        //
        return !(isFortified() || isExploring() || isSleeping() || isAutomated() || isMoving())
    }

    fun maxAttacksPerTurn(): Int {
        var maxAttacksPerTurn = 1 + getMatchingUniques("[] additional attacks per turn").sumBy { it.params[0].toInt() }
        // Deprecated since 3.15.6
        if (hasUnique("1 additional attack per turn"))
            maxAttacksPerTurn++
        //
        return maxAttacksPerTurn
    }
    
    fun canAttack(): Boolean {
        if (currentMovement == 0f) return false
        return attacksThisTurn < maxAttacksPerTurn()
    }

    fun getRange(): Int {
        if (baseUnit.isMelee()) return 1
        var range = baseUnit().range
        // Deprecated since 3.15.6
            if (hasUnique("+1 Range")) range++
            if (hasUnique("+2 Range")) range += 2
        //
        range += getMatchingUniques("[] Range").sumBy { it.params[0].toInt() }
        return range
    }


    fun isEmbarked(): Boolean {
        if (!baseUnit.isLandUnit()) return false
        return currentTile.isWater
    }

    fun isInvisible(): Boolean {
        if (hasUnique("Invisible to others"))
            return true
        return false
    }

    fun getEmbarkedMovement(): Int {
        var movement = 2
        movement += civInfo.getMatchingUniques("Increases embarked movement +1").count()
        if (civInfo.hasUnique("+1 Movement for all embarked units")) movement += 1
        return movement
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
        return getMatchingUniques("All adjacent units heal [] HP when healing").sumBy { it.params[0].toInt() }
    }

    fun canGarrison() = baseUnit.isMilitary() && baseUnit.isLandUnit()

    fun isGreatPerson() = baseUnit.isGreatPerson()

    //endregion

    //region state-changing functions
    fun setTransients(ruleset: Ruleset) {
        promotions.setTransients(this)
        baseUnit = ruleset.units[name]
            ?: throw java.lang.Exception("Unit $name is not found!")
        updateUniques()
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
                    if (removedFeatureObject != null && removedFeatureObject.uniques
                            .contains("Provides a one-time Production bonus to the closest city when cut down")
                    ) {
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

        amountToHealBy += getMatchingUniques("[] HP when healing").sumBy { it.params[0].toInt() }
        
        val maxAdjacentHealingBonus = currentTile.getTilesInDistance(1)
            .flatMap { it.getUnits().asSequence() }.map { it.adjacentHealingBonus() }.maxOrNull()
        if (maxAdjacentHealingBonus != null)
            amountToHealBy += maxAdjacentHealingBonus
        if (hasUnique("All healing effects doubled"))
            amountToHealBy *= 2
        healBy(amountToHealBy)
    }

    fun healBy(amount: Int) {
        health += amount
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
        doAction()

        if (currentMovement > 0 && 
            // Constants.workerUnique deprecated since 3.15.5
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

        getCitadelDamage()
        getTerrainDamage()
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
            && !tile.improvement!!.startsWith("StartingLocation ")
            && tile.getTileImprovement()!!.isAncientRuinsEquivalent()
        )
            getAncientRuinBonus(tile)
        if (tile.improvement == Constants.barbarianEncampment && !civInfo.isBarbarian())
            clearEncampment(tile)

        if (!hasUnique("All healing effects doubled") && baseUnit.isLandUnit() && baseUnit.isMilitary()) {
            //todo: Grants [promotion] to adjacent [unitFilter] units for the rest of the game
            val gainDoubleHealPromotion = tile.neighbors
                .any { it.hasUnique("Grants Rejuvenation (all healing effects doubled) to adjacent military land units for the rest of the game") }
            if (gainDoubleHealPromotion && civInfo.gameInfo.ruleSet.unitPromotions.containsKey("Rejuvenation"))
                promotions.addPromotion("Rejuvenation", true)
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
        for (unit in currentTile.getUnits().filter { it.isTransported && isTransportTypeOf(it) }
            .toList()) {
            // if we disbanded a unit carrying other units in a city, the carried units can still stay in the city
            if (currentTile.isCityCenter() && unit.movement.canMoveTo(currentTile)) continue
            // if no "fuel" to escape, should be disbanded as well
            if (unit.currentMovement < 0.1)
                unit.disband()
            // let's find closest city or another carrier where it can be evacuated
            val tileCanMoveTo = unit.currentTile.getTilesInDistance(unit.getRange() * 2)
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

    fun canIntercept(attackedTile: TileInfo): Boolean {
        if (!canIntercept()) return false
        if (currentTile.aerialDistanceTo(attackedTile) > baseUnit.interceptRange) return false
        return true
    }
    
    fun canIntercept(): Boolean {
        if (interceptChance() == 0) return false
        val maxAttacksPerTurn = 1 +
            getMatchingUniques("[] extra interceptions may be made per turn").sumBy { it.params[0].toInt() } +
            // Deprecated since 3.15.7
                getMatchingUniques("1 extra interception may be made per turn").count()
            //
        if (attacksThisTurn >= maxAttacksPerTurn) return false
        return true
    }

    fun interceptChance(): Int {
        return getMatchingUniques("[]% chance to intercept air attacks").sumBy { it.params[0].toInt() }
    }

    fun isTransportTypeOf(mapUnit: MapUnit): Boolean {
        // Currently, only missiles and airplanes can be carried
        if (!mapUnit.baseUnit.movesLikeAirUnits()) return false
        return getMatchingUniques("Can carry [] [] units").any { mapUnit.matchesFilter(it.params[1]) }
    }
    
    fun carryCapacity(unit: MapUnit): Int {
        var capacity = getMatchingUniques("Can carry [] [] units").filter { unit.matchesFilter(it.params[1]) }.sumBy { it.params[0].toInt() }
        capacity += getMatchingUniques("Can carry [] extra [] units").filter { unit.matchesFilter(it.params[1]) }.sumBy { it.params[0].toInt() }
        // Deprecated since 3.15.5
        capacity += getMatchingUniques("Can carry 2 aircraft").filter { unit.matchesFilter("Air") }.sumBy { 2 }
        capacity += getMatchingUniques("Can carry 1 extra aircraft").filter { unit.matchesFilter("Air") }.sumBy { 1 }
        return capacity
    }

    fun canTransport(unit: MapUnit): Boolean {
        if (owner != unit.owner) return false
        if (!isTransportTypeOf(unit)) return false
        if (unit.getMatchingUniques("Cannot be carried by [] units").any{matchesFilter(it.params[0])}) return false
        if (currentTile.airUnits.count { it.isTransported } >= carryCapacity(unit)) return false
        return true
    }

    fun interceptDamagePercentBonus(): Int {
        // "Bonus when intercepting []%" deprecated since 3.15.7
        return getUniques().filter { it.placeholderText == "Bonus when intercepting []%" || it.placeholderText == "[]% Damage when intercepting"}
            .sumBy { it.params[0].toInt() }
    }

    fun receivedInterceptDamageFactor(): Float {
        var damageFactor = 1f
        for (unique in getMatchingUniques("Damage taken from interception reduced by []%"))
            damageFactor *= 1f - unique.params[0].toFloat() / 100f
        return damageFactor
    }

    private fun getTerrainDamage() {
        // hard coded mountain damage for now
        if (getTile().baseTerrain == Constants.mountain) {
            val tileDamage = 50
            health -= tileDamage

            if (health <= 0) {
                civInfo.addNotification(
                    "Our [$name] took [$tileDamage] tile damage and was destroyed",
                    currentTile.position,
                    name,
                    NotificationIcon.Death
                )
                destroy()
            } else civInfo.addNotification(
                "Our [$name] took [$tileDamage] tile damage",
                currentTile.position,
                name
            )
        }

    }

    private fun getCitadelDamage() {
        // Check for Citadel damage - note: 'Damage does not stack with other Citadels'
        val citadelTile = currentTile.neighbors
            .firstOrNull {
                it.getOwner() != null && civInfo.isAtWarWith(it.getOwner()!!) &&
                        with(it.getTileImprovement()) {
                            this != null && this.hasUnique("Deal 30 damage to adjacent enemy units")
                        }
            }

        if (citadelTile != null) {
            health -= 30
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
    }

    fun matchesFilter(filter: String): Boolean {
        if (filter.contains('{')) // multiple types at once - AND logic. Looks like:"{Military} {Land}"
            return filter.removePrefix("{").removeSuffix("}").split("} {")
                .all { matchesFilter(it) }
        return when (filter) {
            "Wounded", "wounded units" -> health < 100
            "Barbarians", "Barbarian" -> civInfo.isBarbarian()
            "Embarked" -> isEmbarked()
            else -> {
                if (baseUnit.matchesFilter(filter)) return true
                if (hasUnique(filter)) return true
                return false
            }
        }
    }

    fun canBuildImprovement(improvement: TileImprovement, tile: TileInfo = currentTile): Boolean {
        // Constants.workerUnique deprecated since 3.15.5
        if (hasUnique(Constants.workerUnique)) return true
        val matchingUniques = getMatchingUniques(Constants.canBuildImprovements)
        return matchingUniques.any { improvement.matchesFilter(it.params[0]) || tile.matchesTerrainFilter(it.params[0]) }
    }
    
    fun maxReligionSpreads(): Int {
        return getMatchingUniques("Can spread religion [] times").sumBy { it.params[0].toInt() }
    }
    
    fun canSpreadReligion(): Boolean {
        return hasUnique("Can spread religion [] times")
    }

    fun getPressureAddedFromSpread(): Int {
        return baseUnit.religiousStrength
    }

    fun getReligionString(): String {
        val maxSpreads = maxReligionSpreads()
        if (abilityUsedCount["Religion Spread"] == null) return "" // That is, either the key doesn't exist, or it does exist and the value is null.
        return "${maxSpreads - abilityUsedCount["Religion Spread"]!!}/${maxSpreads}"
    }

    fun actionsOnDeselect() {
        showAdditionalActions = false
        if (isPreparingParadrop()) action = null
    }

    fun getPower(): Int {
        val promotionBonus = (promotions.numberOfPromotions + 1).toFloat().pow(0.3f)
        var power = (baseUnit.getPower() * promotionBonus).toInt()
        power *= health
        power /= 100
        return power
    }

    //endregion
}
