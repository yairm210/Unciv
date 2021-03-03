package com.unciv.logic.map

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import java.text.DecimalFormat
import kotlin.random.Random

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
     */
    fun displayName(): String {
        return if(instanceName == null) {
            name
        }
        else {
            "$instanceName ($name)"
        }
    }

    var currentMovement: Float = 0f
    var health: Int = 100

    var action: String? = null // work, automation, fortifying, I dunno what.

    var attacksThisTurn = 0
    var promotions = UnitPromotions()
    var due: Boolean = true
    var isTransported: Boolean = false

    companion object {
        private const val ANCIENT_RUIN_MAP_REVEAL_OFFSET = 4
        private const val ANCIENT_RUIN_MAP_REVEAL_RANGE = 4
        private const val ANCIENT_RUIN_MAP_REVEAL_CHANCE = 0.8f
    }

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
        return toReturn
    }

    val type: UnitType
        get() = baseUnit.unitType

    fun baseUnit(): BaseUnit = baseUnit
    fun getMovementString(): String = DecimalFormat("0.#").format(currentMovement.toDouble()) + "/" + getMaxMovement()
    fun getTile(): TileInfo = currentTile
    fun getMaxMovement(): Int {
        if (isEmbarked()) return getEmbarkedMovement()

        var movement = baseUnit.movement
        movement += getUniques().count { it.text == "+1 Movement" }

        if (type.isWaterUnit() && !type.isCivilian()
                && civInfo.hasUnique("All military naval units receive +1 movement and +1 sight"))
            movement += 1

        for (unique in civInfo.getMatchingUniques("+[] Movement for all [] units"))
            if (matchesFilter(unique.params[1]))
                movement += unique.params[0].toInt()

        if (civInfo.goldenAges.isGoldenAge() &&
                civInfo.hasUnique("+1 Movement for all units during Golden Age"))
            movement += 1

        return movement
    }

    // This SHOULD NOT be a hashset, because if it is, then promotions with the same text (e.g. barrage I, barrage II)
    //  will not get counted twice!
    @Transient
    var tempUniques = ArrayList<Unique>()

    fun getUniques(): ArrayList<Unique> = tempUniques

    fun getMatchingUniques(placeholderText: String): Sequence<Unique> = tempUniques.asSequence().filter { it.placeholderText == placeholderText }

    fun updateUniques() {
        val uniques = ArrayList<Unique>()
        val baseUnit = baseUnit()
        uniques.addAll(baseUnit.uniqueObjects)

        for (promotion in promotions.promotions) {
            uniques.addAll(currentTile.tileMap.gameInfo.ruleSet.unitPromotions[promotion]!!.uniqueObjects)
        }

        tempUniques = uniques

        // "All tiles costs 1" obsoleted in 3.11.18
        allTilesCosts1 = hasUnique("All tiles cost 1 movement") || hasUnique("All tiles costs 1")
        canPassThroughImpassableTiles = hasUnique("Can pass through impassable tiles")
        ignoresTerrainCost = hasUnique("Ignores terrain cost")
        roughTerrainPenalty = hasUnique("Rough terrain penalty")
        doubleMovementInCoast = hasUnique("Double movement in coast")
        doubleMovementInForestAndJungle = hasUnique("Double movement rate through Forest and Jungle")
        doubleMovementInSnowTundraAndHills = hasUnique("Double movement in Snow, Tundra and Hills")
        canEnterIceTiles = hasUnique("Can enter ice tiles")
        cannotEnterOceanTiles = hasUnique("Cannot enter ocean tiles")
        cannotEnterOceanTilesUntilAstronomy = hasUnique("Cannot enter ocean tiles until Astronomy")
    }

    fun hasUnique(unique: String): Boolean {
        return getUniques().any { it.placeholderText == unique }
    }

    fun updateVisibleTiles() {
        if (type.isAirUnit()) {
            viewableTiles = if (hasUnique("6 tiles in every direction always visible"))
                getTile().getTilesInDistance(6).toList()  // it's that simple
            else listOf() // bomber units don't do recon
        } else {
            var visibilityRange = 2
            visibilityRange += getUniques().count { it.text == "+1 Visibility Range" }
            if (hasUnique("+2 Visibility Range")) visibilityRange += 2 // This shouldn't be stackable
            if (hasUnique("Limited Visibility")) visibilityRange -= 1
            if (civInfo.hasUnique("+1 Sight for all land military units") && type.isMilitary() && type.isLandUnit())
                visibilityRange += 1
            if (type.isWaterUnit() && !type.isCivilian()
                    && civInfo.hasUnique("All military naval units receive +1 movement and +1 sight"))
                visibilityRange += 1
            if (isEmbarked() && civInfo.hasUnique("+1 Sight when embarked"))
                visibilityRange += 1
            val tile = getTile()
            if (tile.isHill() && type.isLandUnit()) visibilityRange += 1

            viewableTiles = tile.getViewableTilesList(visibilityRange)
        }
        civInfo.updateViewableTiles() // for the civ
    }

    fun isFortified() = action?.startsWith("Fortify") == true

    fun isSleeping() = action?.startsWith("Sleep") == true

    fun isMoving() = action?.startsWith("moveTo") == true

    fun getFortificationTurns(): Int {
        if (!isFortified()) return 0
        return action!!.split(" ")[1].toInt()
    }

    override fun toString() = "$name - $owner"


    fun isIdle(): Boolean {
        if (currentMovement == 0f) return false
        if (hasUnique(Constants.workerUnique) && getTile().improvementInProgress != null) return false
        if (hasUnique("Can construct roads") && currentTile.improvementInProgress == "Road") return false
        if (isFortified()) return false
        if (action == Constants.unitActionExplore || isSleeping()
                || action == Constants.unitActionAutomation || isMoving()) return false
        return true
    }

    fun canAttack(): Boolean {
        if (currentMovement == 0f) return false
        if (attacksThisTurn > 0 && !hasUnique("1 additional attack per turn")) return false
        if (attacksThisTurn > 1) return false
        return true
    }

    fun getRange(): Int {
        if (type.isMelee()) return 1
        var range = baseUnit().range
        if (hasUnique("+1 Range")) range++
        if (hasUnique("+2 Range")) range += 2
        return range
    }


    fun isEmbarked(): Boolean {
        if (!type.isLandUnit()) return false
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
                        .let { it == null || civInfo.tech.isResearched(it) })
            unit = unit.getDirectUpgradeUnit(civInfo)
        return unit
    }

    fun canUpgrade(): Boolean {
        // We need to remove the unit from the civ for this check,
        // because if the unit requires, say, horses, and so does its upgrade,
        // and the civ currently has 0 horses,
        // if we don't remove the unit before the check it's return false!

        val unitToUpgradeTo = getUnitToUpgradeTo()
        if (name == unitToUpgradeTo.name) return false
        civInfo.removeUnit(this)
        val canUpgrade = unitToUpgradeTo.isBuildable(civInfo)
        civInfo.addUnit(this)
        return canUpgrade
    }

    fun getCostOfUpgrade(): Int {
        val unitToUpgradeTo = getUnitToUpgradeTo()
        var goldCostOfUpgrade = (unitToUpgradeTo.cost - baseUnit().cost) * 2 + 10
        for (unique in civInfo.getMatchingUniques("Gold cost of upgrading military units reduced by 33%"))
            goldCostOfUpgrade = (goldCostOfUpgrade * 0.66f).toInt()
        if (goldCostOfUpgrade < 0) return 0 // For instance, Landsknecht costs less than Spearman, so upgrading would cost negative gold
        return goldCostOfUpgrade
    }


    fun canFortify(): Boolean {
        if (type.isWaterUnit()) return false
        if (type.isCivilian()) return false
        if (type.isAirUnit()) return false
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
        var healingBonus = 0
        if (hasUnique("This unit and all others in adjacent tiles heal 5 additional HP per turn")) healingBonus += 5
        if (hasUnique("This unit and all others in adjacent tiles heal 5 additional HP. This unit heals 5 additional HP outside of friendly territory.")) healingBonus += 5
        return healingBonus
    }

    fun canGarrison() = type.isMilitary() && type.isLandUnit()

    fun isGreatPerson() = baseUnit.isGreatPerson()

    //endregion

    //region state-changing functions
    fun setTransients(ruleset: Ruleset) {
        promotions.unit = this
        baseUnit = ruleset.units[name]
                ?: throw java.lang.Exception("Unit $name is not found!")
        updateUniques()
    }

    fun useMovementPoints(amount: Float) {
        currentMovement -= amount
        if (currentMovement < 0) currentMovement = 0f
    }

    fun doAction() {
        if (action == null) return
        if (currentMovement == 0f) return  // We've already done stuff this turn, and can't do any more stuff

        val enemyUnitsInWalkingDistance = movement.getDistanceToTiles().keys
                .filter { it.militaryUnit != null && civInfo.isAtWarWith(it.militaryUnit!!.civInfo) }
        if (enemyUnitsInWalkingDistance.isNotEmpty()) {
            if (action?.startsWith("moveTo") == true) // stop on enemy in sight
                action = null
            return  // Don't you dare move.
        }

        val currentTile = getTile()
        if (isMoving()) {
            val destination = action!!.replace("moveTo ", "").split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val destinationVector = Vector2(destination[0].toFloat(), destination[1].toFloat())
            val destinationTile = currentTile.tileMap[destinationVector]
            if (!movement.canReach(destinationTile)){ // That tile that we were moving towards is now unreachable -
                // for instance we headed towards an unknown tile and it's apparently unreachable
                action = null
                return
            }
            val gotTo = movement.headTowards(destinationTile)
            if (gotTo == currentTile) // We didn't move at all
                return
            if (gotTo.position == destinationVector) action = null
            if (currentMovement > 0) doAction()
            return
        }

        if (action == Constants.unitActionAutomation) WorkerAutomation(this).automateWorkerAction()

        if (action == Constants.unitActionExplore) UnitAutomation.automatedExplore(this)
    }


    private fun workOnImprovement() {
        val tile = getTile()
        tile.turnsToImprovement -= 1
        if (tile.turnsToImprovement != 0) return

        if (civInfo.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Construct an improvement")
        when {
            tile.improvementInProgress!!.startsWith("Remove") -> {
                val tileImprovement = tile.getTileImprovement()
                if (tileImprovement != null
                        && tileImprovement.terrainsCanBeBuiltOn.contains(tile.terrainFeature)
                        && !tileImprovement.terrainsCanBeBuiltOn.contains(tile.baseTerrain)) {
                    tile.improvement = null // We removed a terrain (e.g. Forest) and the improvement (e.g. Lumber mill) requires it!
                    if (tile.resource != null) civInfo.updateDetailedCivResources()        // unlikely, but maybe a mod makes a resource improvement dependent on a terrain feature
                }
                if (tile.improvementInProgress == "Remove Road" || tile.improvementInProgress == "Remove Railroad")
                    tile.roadStatus = RoadStatus.None
                else {
                    // We put "tile.terrainFeature!=null" because of a strange edge case that SHOULD be solved from 3.11.11+, so we should remove it then and see
                    if (tile.terrainFeature != null && tile.tileMap.gameInfo.ruleSet.terrains[tile.terrainFeature!!]!!.uniques
                                    .contains("Provides a one-time Production bonus to the closest city when cut down"))
                        tryProvideProductionToClosestCity()
                    tile.terrainFeature = null
                }
            }
            tile.improvementInProgress == "Road" -> tile.roadStatus = RoadStatus.Road
            tile.improvementInProgress == "Railroad" -> tile.roadStatus = RoadStatus.Railroad
            else -> {
                tile.improvement = tile.improvementInProgress
                if (tile.resource != null) civInfo.updateDetailedCivResources()
            }
        }
        tile.improvementInProgress = null
    }

    private fun tryProvideProductionToClosestCity() {
        val tile = getTile()
        val closestCity = civInfo.cities.minBy { it.getCenterTile().aerialDistanceTo(tile) }
        if (closestCity == null) return
        val distance = closestCity.getCenterTile().aerialDistanceTo(tile)
        var productionPointsToAdd = if (distance == 1) 20 else 20 - (distance - 2) * 5
        if (tile.owningCity == null || tile.owningCity!!.civInfo != civInfo) productionPointsToAdd = productionPointsToAdd * 2 / 3
        if (productionPointsToAdd > 0) {
            closestCity.cityConstructions.addProductionPoints(productionPointsToAdd)
            civInfo.addNotification("Clearing a [${tile.terrainFeature}] has created [$productionPointsToAdd] Production for [${closestCity.name}]", closestCity.location, Color.BROWN)
        }

    }

    private fun heal() {
        if (isEmbarked()) return // embarked units can't heal
        var amountToHealBy = rankTileForHealing(getTile())
        if (amountToHealBy == 0) return

        if (hasUnique("+10 HP when healing")) amountToHealBy += 10
        val maxAdjacentHealingBonus = currentTile.getTilesInDistance(1)
                .flatMap { it.getUnits().asSequence() }.map { it.adjacentHealingBonus() }.max()
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
            tileInfo.isWater && isFriendlyTerritory && (type.isWaterUnit() || isTransported) -> 15 // Water unit on friendly water
            tileInfo.isWater -> 0 // All other water cases
            tileInfo.getOwner() == null -> 10 // Neutral territory
            isFriendlyTerritory -> 15 // Allied territory
            else -> 5 // Enemy territory
        }

        if (hasUnique("This unit and all others in adjacent tiles heal 5 additional HP. This unit heals 5 additional HP outside of friendly territory.")
                && !isFriendlyTerritory
                && healing > 0)// Additional healing from medic is only applied when the unit is able to heal
            healing += 5

        return healing
    }

    fun endTurn() {
        doAction()

        if (currentMovement > 0 && hasUnique(Constants.workerUnique)
                && getTile().improvementInProgress != null) workOnImprovement()
        if (currentMovement > 0 && hasUnique("Can construct roads")
                && currentTile.improvementInProgress == "Road") workOnImprovement()
        if (currentMovement == getMaxMovement().toFloat() && isFortified()) {
            val currentTurnsFortified = getFortificationTurns()
            if (currentTurnsFortified < 2)
                action = action!!.replace(currentTurnsFortified.toString(), (currentTurnsFortified + 1).toString(), true)
        }
        if (hasUnique("Heal adjacent units for an additional 15 HP per turn"))
            currentTile.neighbors.flatMap { it.getUnits() }.forEach { it.healBy(15) }

        if (currentMovement == getMaxMovement().toFloat() // didn't move this turn
                || hasUnique("Unit will heal every turn, even if it performs an action")) heal()

        if (action != null && health > 99)
            if (action!!.endsWith(" until healed")) {
                action = null // wake up when healed
            }

        getCitadelDamage()
        getTerrainDamage()
    }

    fun startTurn() {
        currentMovement = getMaxMovement().toFloat()
        attacksThisTurn = 0
        due = true

        // Wake sleeping units if there's an enemy nearby and civilian is not protected
        if (isSleeping() && (!type.isCivilian() || currentTile.militaryUnit == null) &&
                currentTile.getTilesInDistance(2).any {
                    it.militaryUnit != null && it.militaryUnit!!.civInfo.isAtWarWith(civInfo)
                })
            action = null

        val tileOwner = getTile().getOwner()
        if (tileOwner != null && !civInfo.canEnterTiles(tileOwner) && !tileOwner.isCityState()) // if an enemy city expanded onto this tile while I was in it
            movement.teleportToClosestMoveableTile()
    }

    fun destroy() {
        removeFromTile()
        civInfo.removeUnit(this)
        civInfo.updateViewableTiles()
        // all transported units should be destroyed as well
        currentTile.getUnits().filter { it.isTransported && isTransportTypeOf(it) }.toList() // because we're changing the list
                .forEach { unit -> unit.destroy() }
    }

    fun removeFromTile() = currentTile.removeUnit(this)

    fun moveThroughTile(tile: TileInfo) {
        if (tile.improvement == Constants.ancientRuins && civInfo.isMajorCiv())
            getAncientRuinBonus(tile)
        if (tile.improvement == Constants.barbarianEncampment && !civInfo.isBarbarian())
            clearEncampment(tile)

        // addPromotion requires currentTile to be valid because it accesses ruleset through it
        currentTile = tile

        if (!hasUnique("All healing effects doubled") && type.isLandUnit() && type.isMilitary()) {
            val gainDoubleHealPromotion = tile.neighbors
                    .any { it.containsUnique("Grants Rejuvenation (all healing effects doubled) to adjacent military land units for the rest of the game") }
            if (gainDoubleHealPromotion && civInfo.gameInfo.ruleSet.unitPromotions.containsKey("Rejuvenation"))
                promotions.addPromotion("Rejuvenation", true)
        }

        updateVisibleTiles()
    }

    fun putInTile(tile: TileInfo) {
        when {
            !movement.canMoveTo(tile) -> throw Exception("I can't go there!")
            type.isAirUnit() -> tile.airUnits.add(this)
            type.isCivilian() -> tile.civilianUnit = this
            else -> tile.militaryUnit = this
        }
        // this check is here in order to not load the fresh built unit into carrier right after the build
        isTransported = !tile.isCityCenter() &&
                type.isAirUnit() // not moving civilians
        moveThroughTile(tile)
    }

    private fun clearEncampment(tile: TileInfo) {
        tile.improvement = null

        // Notify city states that this unit cleared a Barbarian Encampment, required for quests
        civInfo.gameInfo.getAliveCityStates().forEach { it.questManager.barbarianCampCleared(civInfo, tile.position) }

        var goldGained = civInfo.getDifficulty().clearBarbarianCampReward * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        if (civInfo.hasUnique("Receive triple Gold from Barbarian encampments and pillaging Cities"))
            goldGained *= 3f

        civInfo.gold += goldGained.toInt()
        civInfo.addNotification("We have captured a barbarian encampment and recovered [${goldGained.toInt()}] gold!", tile.position, Color.RED)
    }

    fun disband() {
        // evacuation of transported units before disbanding, if possible. toListed because we're modifying the unit list.
        for (unit in currentTile.getUnits().filter { it.isTransported && isTransportTypeOf(it) }.toList()) {
            // we disbanded a carrier in a city, it can still stay in the city
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
            civInfo.gold += baseUnit.getDisbandGold()
        if (civInfo.isDefeated()) civInfo.destroy()
    }

    private fun getAncientRuinBonus(tile: TileInfo) {
        tile.improvement = null
        val tileBasedRandom = Random(tile.position.toString().hashCode())
        val actions: ArrayList<() -> Unit> = ArrayList()
        if (civInfo.cities.isNotEmpty()) actions.add {
            val city = civInfo.cities.random(tileBasedRandom)
            city.population.population++
            city.population.autoAssignPopulation()
            civInfo.addNotification("We have found survivors in the ruins - population added to [" + city.name + "]", tile.position, Color.GREEN)
        }
        val researchableAncientEraTechs = tile.tileMap.gameInfo.ruleSet.technologies.values
                .filter {
                    !civInfo.tech.isResearched(it.name)
                            && civInfo.tech.canBeResearched(it.name)
                            && it.era() == Constants.ancientEra
                }
        if (researchableAncientEraTechs.isNotEmpty())
            actions.add {
                val tech = researchableAncientEraTechs.random(tileBasedRandom).name
                civInfo.tech.addTechnology(tech)
                civInfo.addNotification("We have discovered the lost technology of [$tech] in the ruins!", tile.position, Color.BLUE)
            }


        val possibleUnits = listOf(Constants.settler, Constants.worker, "Warrior")
                .filter { civInfo.gameInfo.ruleSet.units.containsKey(it) }
        if (possibleUnits.isNotEmpty())
            actions.add {
                val chosenUnit = possibleUnits.random(tileBasedRandom)
                if (!(civInfo.isCityState() || civInfo.isOneCityChallenger()) || chosenUnit != Constants.settler) { //City-States and OCC don't get settler from ruins
                    civInfo.placeUnitNearTile(tile.position, chosenUnit)
                    civInfo.addNotification("A [$chosenUnit] has joined us!", tile.position, Color.BROWN)
                }
            }

        if (!type.isCivilian())
            actions.add {
                promotions.XP += 10
                civInfo.addNotification("An ancient tribe trains our [$name] in their ways of combat!", tile.position, Color.RED)
            }

        actions.add {
            val amount = listOf(25, 60, 100).random(tileBasedRandom)
            civInfo.gold += amount
            civInfo.addNotification("We have found a stash of [$amount] gold in the ruins!", tile.position, Color.GOLD)
        }

        actions.add {
            civInfo.policies.addCulture(20)
            civInfo.addNotification("We have discovered cultural artifacts in the ruins! (+20 Culture)", tile.position, Color.GOLD)
        }

        // Map of the surrounding area
        actions.add {
            val revealCenter = tile.getTilesAtDistance(ANCIENT_RUIN_MAP_REVEAL_OFFSET).toList().random(tileBasedRandom)
            val tilesToReveal = revealCenter
                    .getTilesInDistance(ANCIENT_RUIN_MAP_REVEAL_RANGE)
                    .filter { Random.nextFloat() < ANCIENT_RUIN_MAP_REVEAL_CHANCE }
                    .map { it.position }
            civInfo.exploredTiles.addAll(tilesToReveal)
            civInfo.updateViewableTiles()
            civInfo.addNotification("We have found a crudely-drawn map in the ruins!", tile.position, Color.RED)
        }

        (actions.random(tileBasedRandom))()
    }

    fun assignOwner(civInfo: CivilizationInfo, updateCivInfo: Boolean = true) {
        owner = civInfo.civName
        this.civInfo = civInfo
        civInfo.addUnit(this, updateCivInfo)
    }

    fun canIntercept(attackedTile: TileInfo): Boolean {
        if (attacksThisTurn > 1) return false
        if (interceptChance() == 0) return false
        if (attacksThisTurn > 0 && !hasUnique("1 extra Interception may be made per turn")) return false
        if (currentTile.aerialDistanceTo(attackedTile) > baseUnit.interceptRange) return false
        return true
    }

    fun interceptChance(): Int {
        return getMatchingUniques("[]% chance to intercept air attacks").sumBy { it.params[0].toInt() }
    }

    fun isTransportTypeOf(mapUnit: MapUnit): Boolean {
        val isAircraftCarrier = hasUnique("Can carry 2 aircraft")
        val isMissileCarrier = hasUnique("Can carry 2 missiles")
        if (!isMissileCarrier && !isAircraftCarrier)
            return false
        if (!mapUnit.type.isAirUnit()) return false
        if (isMissileCarrier && mapUnit.type != UnitType.Missile)
            return false
        if (isAircraftCarrier && mapUnit.type == UnitType.Missile)
            return false
        return true
    }

    fun canTransport(mapUnit: MapUnit): Boolean {
        if (!isTransportTypeOf(mapUnit)) return false
        if (owner != mapUnit.owner) return false

        var unitCapacity = 2
        unitCapacity += getUniques().count { it.text == "Can carry 1 extra air unit" }

        if (currentTile.airUnits.count { it.isTransported } >= unitCapacity) return false

        return true
    }

    fun interceptDamagePercentBonus(): Int {
        return getUniques().filter { it.placeholderText == "Bonus when intercepting []%" }
                .sumBy { it.params[0].toInt() }
    }

    private fun getTerrainDamage() {
        // hard coded mountain damage for now
        if (getTile().baseTerrain == Constants.mountain) {
            val tileDamage = 50
            health -= tileDamage

            if (health <= 0) {
                civInfo.addNotification("Our [$name] took [$tileDamage] tile damage and was destroyed", currentTile.position, Color.RED)
                destroy()
            } else civInfo.addNotification("Our [$name] took [$tileDamage] tile damage", currentTile.position, Color.RED)
        }

    }

    private fun getCitadelDamage() {
        // Check for Citadel damage
        val applyCitadelDamage = currentTile.neighbors
                .filter { it.getOwner() != null && civInfo.isAtWarWith(it.getOwner()!!) }
                .map { it.getTileImprovement() }
                .filter { it != null && it.hasUnique("Deal 30 damage to adjacent enemy units") }
                .any()

        if (applyCitadelDamage) {
            health -= 30

            if (health <= 0) {
                civInfo.addNotification("An enemy [Citadel] has destroyed our [$name]", currentTile.position, Color.RED)
                // todo - add notification for attacking civ
                destroy()
            } else civInfo.addNotification("An enemy [Citadel] has attacked our [$name]", currentTile.position, Color.RED)
        }
    }

    fun matchesFilter(filter: String): Boolean {
        if (baseUnit.matchesFilter(filter)) return true
        if ((filter == "Wounded" || filter == "wounded units") && health < 100) return true
        if (hasUnique(filter)) return true
        if ((filter == "Barbarians" || filter == "Barbarian") && civInfo.isBarbarian()) return true

        return false
    }

    //endregion
}