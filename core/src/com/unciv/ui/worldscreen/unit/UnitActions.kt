package com.unciv.ui.worldscreen.unit

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.hasOpenPopups
import com.unciv.ui.utils.extensions.toPercent
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.math.min
import kotlin.random.Random

object UnitActions {

    fun getUnitActions(unit: MapUnit, worldScreen: WorldScreen): List<UnitAction> {
        return if (unit.showAdditionalActions) getAdditionalActions(unit, worldScreen)
        else getNormalActions(unit, worldScreen)
    }

    private fun getNormalActions(unit: MapUnit, worldScreen: WorldScreen): List<UnitAction> {
        val tile = unit.getTile()
        val unitTable = worldScreen.bottomUnitTable
        val actionList = ArrayList<UnitAction>()

        if (unit.isMoving())
            actionList += UnitAction(UnitActionType.StopMovement) { unit.action = null }
        if (unit.isExploring())
            actionList += UnitAction(UnitActionType.StopExploration) { unit.action = null }
        if (unit.isAutomated())
            actionList += UnitAction(UnitActionType.StopAutomation) { unit.action = null }

        addSleepActions(actionList, unit, false)
        addFortifyActions(actionList, unit, false)

        addPromoteAction(unit, actionList)
        addUnitUpgradeAction(unit, actionList)
        addPillageAction(unit, actionList, worldScreen)
        addParadropAction(unit, actionList)
        addSetupAction(unit, actionList)
        addFoundCityAction(unit, actionList, tile)
        addBuildingImprovementsAction(unit, actionList, tile, worldScreen, unitTable)
        addCreateWaterImprovements(unit, actionList)
        addGreatPersonActions(unit, actionList, tile)
        addFoundReligionAction(unit, actionList)
        addEnhanceReligionAction(unit, actionList)
        actionList += getImprovementConstructionActions(unit, tile)
        addActionsWithLimitedUses(unit, actionList, tile)
        addExplorationActions(unit, actionList)
        addAutomateBuildingImprovementsAction(unit, actionList)
        addTriggerUniqueActions(unit, actionList)
        addAddInCapitalAction(unit, actionList, tile)

        addWaitAction(unit, actionList, worldScreen)

        addToggleActionsAction(unit, actionList, unitTable)

        return actionList
    }

    private fun getAdditionalActions(unit: MapUnit, worldScreen: WorldScreen): List<UnitAction> {
        val tile = unit.getTile()
        val unitTable = worldScreen.bottomUnitTable
        val actionList = ArrayList<UnitAction>()

        addSleepActions(actionList, unit, true)
        addFortifyActions(actionList, unit, true)

        if (unit.canUpgradeMultipleSteps())
            addUnitUpgradeAction(unit, actionList, 1)

        addSwapAction(unit, actionList, worldScreen)
        addDisbandAction(actionList, unit, worldScreen)
        addGiftAction(unit, actionList, tile)


        addToggleActionsAction(unit, actionList, unitTable)

        return actionList
    }

    private fun addSwapAction(unit: MapUnit, actionList: ArrayList<UnitAction>, worldScreen: WorldScreen) {
        // Air units cannot swap
        if (unit.baseUnit.movesLikeAirUnits()) return
        // Disable unit swapping if multiple units are selected. It would make little sense.
        // In principle, the unit swapping mode /will/ function with multiselect: it will simply
        // only consider the first selected unit, and ignore the other selections. However, it does
        // have the visual bug that the tile overlays for the eligible swap locations are drawn for
        // /all/ selected units instead of only the first one. This could be fixed, but again,
        // swapping makes little sense for multiselect anyway.
        if (worldScreen.bottomUnitTable.selectedUnits.size > 1) return
        // Only show the swap action if there is at least one possible swap movement
        if (unit.movement.getUnitSwappableTiles().none()) return
        actionList += UnitAction(
            type = UnitActionType.SwapUnits,
            isCurrentAction = worldScreen.bottomUnitTable.selectedUnitIsSwapping,
            action = {
                worldScreen.bottomUnitTable.selectedUnitIsSwapping = !worldScreen.bottomUnitTable.selectedUnitIsSwapping
                worldScreen.shouldUpdate = true
            }
        )
    }

    private fun addDisbandAction(actionList: ArrayList<UnitAction>, unit: MapUnit, worldScreen: WorldScreen) {
        actionList += UnitAction(type = UnitActionType.DisbandUnit, action = {
            if (!worldScreen.hasOpenPopups()) {
                val disbandText = if (unit.currentTile.getOwner() == unit.civInfo)
                    "Disband this unit for [${unit.baseUnit.getDisbandGold(unit.civInfo)}] gold?".tr()
                else "Do you really want to disband this unit?".tr()
                ConfirmPopup(UncivGame.Current.worldScreen!!, disbandText, "Disband unit") { unit.disband(); worldScreen.shouldUpdate = true }.open()
            }
        }.takeIf { unit.currentMovement > 0 })
    }

    private fun addCreateWaterImprovements(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        val waterImprovementAction = getWaterImprovementAction(unit)
        if (waterImprovementAction != null) actionList += waterImprovementAction
    }

    fun getWaterImprovementAction(unit: MapUnit): UnitAction? {
        val tile = unit.currentTile
        if (!tile.isWater || !unit.hasUnique(UniqueType.CreateWaterImprovements) || tile.resource == null) return null

        val improvementName = tile.tileResource.getImprovingImprovement(tile, unit.civInfo) ?: return null
        val improvement = tile.ruleset.tileImprovements[improvementName] ?: return null
        if (!tile.canBuildImprovement(improvement, unit.civInfo)) return null

        return UnitAction(UnitActionType.Create, "Create [$improvementName]",
            action = {
                tile.improvement = improvementName
                val city = tile.getCity()
                if (city != null) {
                    city.cityStats.update()
                    city.civInfo.updateDetailedCivResources()
                }
                unit.destroy()
            }.takeIf { unit.currentMovement > 0 })
    }


    private fun addFoundCityAction(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {
        val getFoundCityAction = getFoundCityAction(unit, tile)
        if (getFoundCityAction != null) actionList += getFoundCityAction
    }

    /** Produce a [UnitAction] for founding a city.
     * @param unit The unit to do the founding.
     * @param tile The tile to found a city on.
     * @return null if impossible (the unit lacks the ability to found),
     * or else a [UnitAction] 'defining' the founding.
     * The [action][UnitAction.action] field will be null if the action cannot be done here and now
     * (no movement left, too close to another city).
      */
    fun getFoundCityAction(unit: MapUnit, tile: TileInfo): UnitAction? {
        if (!unit.hasUnique(UniqueType.FoundCity)
                || tile.isWater || tile.isImpassible()) return null
        // Spain should still be able to build Conquistadors in a one city challenge - but can't settle them
        if (unit.civInfo.isOneCityChallenger() && unit.civInfo.hasEverOwnedOriginalCapital == true) return null

        if (unit.currentMovement <= 0 ||
                !tile.canBeSettled())
            return UnitAction(UnitActionType.FoundCity, action = null)

        val foundAction = {
            UncivGame.Current.settings.addCompletedTutorialTask("Found city")
            unit.civInfo.addCity(tile.position)
            if (tile.ruleset.tileImprovements.containsKey("City center"))
                tile.improvement = "City center"
            unit.destroy()
            UncivGame.Current.worldScreen!!.shouldUpdate = true
        }

        if (unit.civInfo.playerType == PlayerType.AI)
            return UnitAction(UnitActionType.FoundCity, action = foundAction)

        return UnitAction(
                type = UnitActionType.FoundCity,
                uncivSound = UncivSound.Chimes,
                action = {
                    // check if we would be breaking a promise
                    val leaders = testPromiseNotToSettle(unit.civInfo, tile)
                    if (leaders == null)
                        foundAction()
                    else {
                        // ask if we would be breaking a promise
                        val text = "Do you want to break your promise to [$leaders]?"
                        ConfirmPopup(UncivGame.Current.worldScreen!!, text, "Break promise", action = foundAction).open(force = true)
                    }
                }
            )
    }

    /**
     * Checks whether a civ founding a city on a certain tile would break a promise.
     * @param civInfo The civilization trying to found a city
     * @param tile The tile where the new city would go
     * @return null if no promises broken, else a String listing the leader(s) we would p* off.
     */
    private fun testPromiseNotToSettle(civInfo: CivilizationInfo, tile: TileInfo): String? {
        val brokenPromises = HashSet<String>()
        for (otherCiv in civInfo.getKnownCivs().filter { it.isMajorCiv() && !civInfo.isAtWarWith(it) }) {
            val diplomacyManager = otherCiv.getDiplomacyManager(civInfo)
            if (diplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs)) {
                val citiesWithin6Tiles = otherCiv.cities
                    .filter { it.getCenterTile().aerialDistanceTo(tile) <= 6 }
                    .filter { otherCiv.exploredTiles.contains(it.location) }
                if (citiesWithin6Tiles.isNotEmpty()) brokenPromises += otherCiv.getLeaderDisplayName()
            }
        }
        return if(brokenPromises.isEmpty()) null else brokenPromises.joinToString(", ")
    }

    private fun addPromoteAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.isCivilian() || !unit.promotions.canBePromoted()) return
        // promotion does not consume movement points, but is not allowed if a unit has exhausted its movement or has attacked
        actionList += UnitAction(UnitActionType.Promote,
            action = {
                UncivGame.Current.pushScreen(PromotionPickerScreen(unit))
            }.takeIf { unit.currentMovement > 0 && unit.attacksThisTurn == 0 })
    }

    private fun addSetupAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.hasUnique(UniqueType.MustSetUp) || unit.isEmbarked()) return
        val isSetUp = unit.isSetUpForSiege()
        actionList += UnitAction(UnitActionType.SetUp,
                isCurrentAction = isSetUp,
                action = {
                    unit.action = UnitActionType.SetUp.value
                    unit.useMovementPoints(1f)
                }.takeIf { unit.currentMovement > 0 && !isSetUp })
    }

    private fun addParadropAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        val paradropUniques =
            unit.getMatchingUniques(UniqueType.MayParadrop)
        if (!paradropUniques.any() || unit.isEmbarked()) return
        unit.paradropRange = paradropUniques.maxOfOrNull { it.params[0] }!!.toInt()
        actionList += UnitAction(UnitActionType.Paradrop,
            isCurrentAction = unit.isPreparingParadrop(),
            action = {
                if (unit.isPreparingParadrop()) unit.action = null
                else unit.action = UnitActionType.Paradrop.value
            }.takeIf {
                unit.currentMovement == unit.getMaxMovement().toFloat() &&
                        unit.currentTile.isFriendlyTerritory(unit.civInfo) &&
                        !unit.isEmbarked()
            })
    }

    private fun addPillageAction(unit: MapUnit, actionList: ArrayList<UnitAction>, worldScreen: WorldScreen) {
        val pillageAction = getPillageAction(unit)
            ?: return
        if (pillageAction.action == null)
            actionList += UnitAction(UnitActionType.Pillage, action = null)
        else actionList += UnitAction(type = UnitActionType.Pillage) {
            if (!worldScreen.hasOpenPopups()) {
                val pillageText = "Are you sure you want to pillage this [${unit.currentTile.improvement}]?"
                ConfirmPopup(
                    UncivGame.Current.worldScreen!!,
                    pillageText,
                    "Pillage",
                    true
                ) {
                    (pillageAction.action)()
                    worldScreen.shouldUpdate = true
                }.open()
            }
        }
    }

    fun getPillageAction(unit: MapUnit): UnitAction? {
        val tile = unit.currentTile
        if (unit.isCivilian() || tile.improvement == null || tile.getOwner() == unit.civInfo) return null

        return UnitAction(UnitActionType.Pillage,
                action = {
                    tile.getOwner()?.addNotification("An enemy [${unit.baseUnit.name}] has pillaged our [${tile.improvement}]", tile.position, "ImprovementIcons/${tile.improvement!!}", NotificationIcon.War, unit.baseUnit.name)
                    pillageLooting(tile, unit)
                    tile.setPillaged()
                    unit.civInfo.lastSeenImprovement.remove(tile.position)
                    if (tile.resource != null) tile.getOwner()?.updateDetailedCivResources()    // this might take away a resource
                    tile.getCity()?.updateCitizens = true

                    val freePillage = unit.hasUnique(UniqueType.NoMovementToPillage, checkCivInfoUniques = true)
                    if (!freePillage) unit.useMovementPoints(1f)

                    unit.healBy(25)
                }.takeIf { unit.currentMovement > 0 && canPillage(unit, tile) })
    }

    private fun pillageLooting(tile: TileInfo, unit: MapUnit) {
        // Stats objects for reporting pillage results in a notification
        val pillageYield = Stats()
        val globalPillageYield = Stats()
        val toCityPillageYield = Stats()
        val closestCity = unit.civInfo.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }
        val improvement = tile.ruleset.tileImprovements[tile.improvement]!!

        for (unique in improvement.getMatchingUniques(UniqueType.PillageYieldRandom)) {
            for (stat in unique.stats) {
                val looted = Random.nextInt((stat.value + 1).toInt()) + Random.nextInt((stat.value + 1).toInt())
                pillageYield.add(stat.key, looted.toFloat())
            }
        }
        for (unique in improvement.getMatchingUniques(UniqueType.PillageYieldFixed)) {
            for (stat in unique.stats) {
                pillageYield.add(stat.key, stat.value)
            }
        }

        for (stat in pillageYield) {
            when (stat.key) {
                in Stat.statsWithCivWideField -> {
                    unit.civInfo.addStat(stat.key, stat.value.toInt())
                    globalPillageYield[stat.key] += stat.value
                }
                else -> {
                    if (closestCity != null) {
                        closestCity.addStat(stat.key, stat.value.toInt())
                        toCityPillageYield[stat.key] += stat.value
                    }
                }
            }
        }

        if (!toCityPillageYield.isEmpty() && closestCity != null) {
            val pillagerLootLocal = "We have looted [${toCityPillageYield.toStringWithoutIcons()}] from a [${improvement.name}] which has been sent to [${closestCity.name}]"
            unit.civInfo.addNotification(pillagerLootLocal, tile.position, "ImprovementIcons/${improvement.name}", NotificationIcon.War)
        }
        if (!globalPillageYield.isEmpty()) {
            val pillagerLootGlobal = "We have looted [${globalPillageYield.toStringWithoutIcons()}] from a [${improvement.name}]"
            unit.civInfo.addNotification(pillagerLootGlobal, tile.position, "ImprovementIcons/${improvement.name}", NotificationIcon.War)
        }
    }

    private fun addExplorationActions(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (unit.baseUnit.movesLikeAirUnits()) return
        if (unit.isExploring()) return
        actionList += UnitAction(UnitActionType.Explore) {
            unit.action = UnitActionType.Explore.value
            if (unit.currentMovement > 0) UnitAutomation.automatedExplore(unit)
        }
    }

    private fun addUnitUpgradeAction(
        unit: MapUnit,
        actionList: ArrayList<UnitAction>,
        maxSteps: Int = Int.MAX_VALUE
    ) {
        val upgradeAction = getUpgradeAction(unit, maxSteps)
        if (upgradeAction != null) actionList += upgradeAction
    }

    /**  Common implementation for [getUpgradeAction], [getFreeUpgradeAction] and [getAncientRuinsUpgradeAction] */
    private fun getUpgradeAction(
        unit: MapUnit,
        maxSteps: Int,
        isFree: Boolean,
        isSpecial: Boolean
    ): UnitAction? {
        if (unit.baseUnit().upgradesTo == null && unit.baseUnit().specialUpgradesTo == null) return null // can't upgrade to anything
        val unitTile = unit.getTile()
        val civInfo = unit.civInfo
        if (!isFree && unitTile.getOwner() != civInfo) return null

        val upgradesTo = unit.baseUnit().upgradesTo
        val specialUpgradesTo = unit.baseUnit().specialUpgradesTo
        val upgradedUnit = when {
            isSpecial && specialUpgradesTo != null -> civInfo.getEquivalentUnit (specialUpgradesTo)
            isFree && upgradesTo != null -> civInfo.getEquivalentUnit(upgradesTo)  // getUnitToUpgradeTo can't ignore tech
            else -> unit.getUnitToUpgradeTo(maxSteps)
        }
        if (!unit.canUpgrade(unitToUpgradeTo = upgradedUnit, ignoreRequirements = isFree, ignoreResources = true))
            return null

        // Check _new_ resource requirements (display only - yes even for free or special upgrades)
        // Using Counter to aggregate is a bit exaggerated, but - respect the mad modder.
        val resourceRequirementsDelta = Counter<String>()
        for ((resource, amount) in unit.baseUnit().getResourceRequirements())
            resourceRequirementsDelta.add(resource, -amount)
        for ((resource, amount) in upgradedUnit.getResourceRequirements())
            resourceRequirementsDelta.add(resource, amount)
        val newResourceRequirementsString = resourceRequirementsDelta.entries
            .filter { it.value > 0 }
            .joinToString { "${it.value} {${it.key}}".tr() }

        val goldCostOfUpgrade = if (isFree) 0 else unit.getCostOfUpgrade(upgradedUnit)

        // No string for "FREE" variants, these are never shown to the user.
        // The free actions are only triggered via OneTimeUnitUpgrade or OneTimeUnitSpecialUpgrade in UniqueTriggerActivation.
        val title = if (newResourceRequirementsString.isEmpty())
                 "Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)"
            else "Upgrade to [${upgradedUnit.name}]\n([$goldCostOfUpgrade] gold, [$newResourceRequirementsString])"

        return UnitAction(UnitActionType.Upgrade,
            title = title,
            action = {
                unit.destroy()
                val newUnit = civInfo.placeUnitNearTile(unitTile.position, upgradedUnit.name)

                /** We were UNABLE to place the new unit, which means that the unit failed to upgrade!
                 * The only known cause of this currently is "land units upgrading to water units" which fail to be placed.
                 */
                if (newUnit == null) {
                    val resurrectedUnit = civInfo.placeUnitNearTile(unitTile.position, unit.name)!!
                    unit.copyStatisticsTo(resurrectedUnit)
                } else { // Managed to upgrade
                    if (!isFree) civInfo.addGold(-goldCostOfUpgrade)
                    unit.copyStatisticsTo(newUnit)
                    newUnit.currentMovement = 0f
                }
            }.takeIf {
                isFree || (
                    unit.civInfo.gold >= goldCostOfUpgrade
                    && unit.currentMovement > 0
                    && !unit.isEmbarked()
                    && unit.canUpgrade(unitToUpgradeTo = upgradedUnit)
                )
            }
        )
    }

    fun getUpgradeAction(unit: MapUnit, maxSteps: Int = Int.MAX_VALUE) =
        getUpgradeAction(unit, maxSteps, isFree = false, isSpecial = false)
    fun getFreeUpgradeAction(unit: MapUnit) =
        getUpgradeAction(unit, 1, isFree = true, isSpecial = false)
    fun getAncientRuinsUpgradeAction(unit: MapUnit) =
        getUpgradeAction(unit, 1, isFree = true, isSpecial = true)

    private fun addBuildingImprovementsAction(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo, worldScreen: WorldScreen, unitTable: UnitTable) {
        if (!unit.hasUniqueToBuildImprovements) return
        if (unit.isEmbarked()) return

        val couldConstruct = unit.currentMovement > 0
            && !tile.isCityCenter()
            && unit.civInfo.gameInfo.ruleSet.tileImprovements.values.any {
                ImprovementPickerScreen.canReport(tile.getImprovementBuildingProblems(it, unit.civInfo).toSet())
                && unit.canBuildImprovement(it)
            }

        actionList += UnitAction(UnitActionType.ConstructImprovement,
            isCurrentAction = unit.currentTile.hasImprovementInProgress(),
            action = {
                worldScreen.game.pushScreen(ImprovementPickerScreen(tile, unit) { unitTable.selectUnit() })
            }.takeIf { couldConstruct }
        )
    }

    private fun addAutomateBuildingImprovementsAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.hasUniqueToBuildImprovements) return
        if (unit.isAutomated()) return

        actionList += UnitAction(UnitActionType.Automate,
            isCurrentAction = unit.isAutomated(),
            action = {
                unit.action = UnitActionType.Automate.value
                WorkerAutomation.automateWorkerAction(unit)
            }.takeIf { unit.currentMovement > 0 }
        )
    }

    fun getAddInCapitalAction(unit: MapUnit, tile: TileInfo): UnitAction {
        return UnitAction(UnitActionType.AddInCapital,
            title = "Add to [${unit.getMatchingUniques(UniqueType.AddInCapital).first().params[0]}]",
            action = {
                unit.civInfo.victoryManager.currentsSpaceshipParts.add(unit.name, 1)
                unit.destroy()
            }.takeIf { tile.isCityCenter() && tile.getCity()!!.isCapital() && tile.getCity()!!.civInfo == unit.civInfo }
        )
    }

    private fun addAddInCapitalAction(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {
        if (!unit.hasUnique(UniqueType.AddInCapital)) return

        actionList += getAddInCapitalAction(unit, tile)
    }

    private fun addGreatPersonActions(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {

        if (unit.currentMovement > 0) for (unique in unit.getUniques()) when (unique.type) {
            UniqueType.CanHurryResearch -> {
                actionList += UnitAction(UnitActionType.HurryResearch,
                    action = {
                        unit.civInfo.tech.addScience(unit.civInfo.tech.getScienceFromGreatScientist())
                        unit.consume()
                    }.takeIf { unit.civInfo.tech.currentTechnologyName() != null
                            && !unit.civInfo.tech.currentTechnology()!!.hasUnique(UniqueType.CannotBeHurried) }
                )
            }
            UniqueType.StartGoldenAge -> {
                val turnsToGoldenAge = unique.params[0].toInt()
                actionList += UnitAction(UnitActionType.StartGoldenAge,
                    action = {
                        unit.civInfo.goldenAges.enterGoldenAge(turnsToGoldenAge)
                        unit.consume()
                    }.takeIf { unit.currentTile.getOwner() != null && unit.currentTile.getOwner() == unit.civInfo }
                )
            }
            UniqueType.CanSpeedupWonderConstruction -> {
                val canHurryWonder =
                    if (!tile.isCityCenter()) false
                    else tile.getCity()!!.cityConstructions.isBuildingWonder()
                            && tile.getCity()!!.cityConstructions.canBeHurried()

                actionList += UnitAction(UnitActionType.HurryWonder,
                    action = {
                        tile.getCity()!!.cityConstructions.apply {
                            //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                            addProductionPoints(((300 + 30 * tile.getCity()!!.population.population) * unit.civInfo.gameInfo.speed.productionCostModifier).toInt())
                            constructIfEnough()
                        }

                        unit.consume()
                    }.takeIf { canHurryWonder }
                )
            }

            UniqueType.CanSpeedupConstruction -> {
                if (!tile.isCityCenter()) {
                    actionList += UnitAction(UnitActionType.HurryBuilding, action = null)
                    continue
                }

                val cityConstructions = tile.getCity()!!.cityConstructions
                val canHurryConstruction = cityConstructions.getCurrentConstruction() is Building
                        && cityConstructions.canBeHurried()

                //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                val productionPointsToAdd = min(
                    (300 + 30 * tile.getCity()!!.population.population) * unit.civInfo.gameInfo.speed.productionCostModifier,
                    cityConstructions.getRemainingWork(cityConstructions.currentConstructionFromQueue).toFloat() - 1
                ).toInt()
                if (productionPointsToAdd <= 0) continue

                actionList += UnitAction(UnitActionType.HurryBuilding,
                    title = "Hurry Construction (+[$productionPointsToAdd]⚙)",
                    action = {
                        cityConstructions.apply {
                            addProductionPoints(productionPointsToAdd)
                            constructIfEnough()
                        }

                        unit.consume()
                    }.takeIf { canHurryConstruction }
                )
            }
            UniqueType.CanTradeWithCityStateForGoldAndInfluence -> {
                val canConductTradeMission = tile.owningCity?.civInfo?.isCityState() == true
                        && tile.owningCity?.civInfo?.isAtWarWith(unit.civInfo) == false
                val influenceEarned = unique.params[0].toFloat()
                actionList += UnitAction(UnitActionType.ConductTradeMission,
                    action = {
                        // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                        var goldEarned = (350 + 50 * unit.civInfo.getEraNumber()) * unit.civInfo.gameInfo.speed.goldCostModifier
                        for (goldUnique in unit.civInfo.getMatchingUniques(UniqueType.PercentGoldFromTradeMissions))
                            goldEarned *= goldUnique.params[0].toPercent()
                        unit.civInfo.addGold(goldEarned.toInt())
                        tile.owningCity!!.civInfo.getDiplomacyManager(unit.civInfo).addInfluence(influenceEarned)
                        unit.civInfo.addNotification("Your trade mission to [${tile.owningCity!!.civInfo}] has earned you [${goldEarned}] gold and [$influenceEarned] influence!",
                            tile.owningCity!!.civInfo.civName, NotificationIcon.Gold, NotificationIcon.Culture)
                        unit.consume()
                    }.takeIf { canConductTradeMission }
                )
            }
            else -> {}
        }
    }

    private fun addFoundReligionAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.hasUnique(UniqueType.MayFoundReligion)) return
        if (!unit.civInfo.religionManager.mayFoundReligionAtAll(unit)) return
        actionList += UnitAction(UnitActionType.FoundReligion,
            action = getFoundReligionAction(unit).takeIf { unit.civInfo.religionManager.mayFoundReligionNow(unit) }
        )
    }

    fun getFoundReligionAction(unit: MapUnit): () -> Unit {
        return {
            unit.civInfo.religionManager.useProphetForFoundingReligion(unit)
            unit.consume()
        }
    }

    private fun addEnhanceReligionAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.hasUnique(UniqueType.MayEnhanceReligion)) return
        if (!unit.civInfo.religionManager.mayEnhanceReligionAtAll(unit)) return
        actionList += UnitAction(UnitActionType.EnhanceReligion,
            title = "Enhance [${unit.civInfo.religionManager.religion!!.getReligionDisplayName()}]",
            action = getEnhanceReligionAction(unit).takeIf { unit.civInfo.religionManager.mayEnhanceReligionNow(unit) }
        )
    }

    fun getEnhanceReligionAction(unit: MapUnit): () -> Unit {
        return {
            unit.civInfo.religionManager.useProphetForEnhancingReligion(unit)
            unit.consume()
        }
    }

     fun addActionsWithLimitedUses(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {

        val actionsToAdd = unit.religiousActionsUnitCanDo()
        if (actionsToAdd.none()) return
        if (unit.religion == null || unit.civInfo.gameInfo.religions[unit.religion]!!.isPantheon()) return
        val city = tile.getCity() ?: return
        for (action in actionsToAdd) {
            if (!unit.abilityUsesLeft.containsKey(action)) continue
            if (unit.abilityUsesLeft[action]!! <= 0) continue
            when (action) {
                Constants.spreadReligionAbilityCount -> addSpreadReligionActions(unit, actionList, city)
                Constants.removeHeresyAbilityCount -> addRemoveHeresyActions(unit, actionList, city)
            }
        }
    }

    private fun useActionWithLimitedUses(unit: MapUnit, action: String) {
        unit.abilityUsesLeft[action] = unit.abilityUsesLeft[action]!! - 1
        if (unit.abilityUsesLeft[action]!! <= 0) {
            unit.consume()
        }
    }

    fun addSpreadReligionActions(unit: MapUnit, actionList: ArrayList<UnitAction>, city: CityInfo) {
        if (!unit.civInfo.gameInfo.isReligionEnabled()) return
        val blockedByInquisitor =
            city.getCenterTile()
                .getTilesInDistance(1)
                .flatMap { it.getUnits() }
                .any {
                    it.hasUnique(UniqueType.PreventSpreadingReligion)
                    && it.religion != unit.religion
                }
        actionList += UnitAction(UnitActionType.SpreadReligion,
            title = "Spread [${unit.getReligionDisplayName()!!}]",
            action = {
                val followersOfOtherReligions = city.religion.getFollowersOfOtherReligionsThan(unit.religion!!)
                for (unique in unit.getMatchingUniques(UniqueType.StatsWhenSpreading, checkCivInfoUniques = true)) {
                    unit.civInfo.addStat(Stat.valueOf(unique.params[1]), followersOfOtherReligions * unique.params[0].toInt())
                }
                city.religion.addPressure(unit.religion!!, unit.getPressureAddedFromSpread())
                if (unit.hasUnique(UniqueType.RemoveOtherReligions))
                    city.religion.removeAllPressuresExceptFor(unit.religion!!)
                unit.currentMovement = 0f
                useActionWithLimitedUses(unit, Constants.spreadReligionAbilityCount)
            }.takeIf { unit.currentMovement > 0 && !blockedByInquisitor }
        )
    }

    private fun addRemoveHeresyActions(unit: MapUnit, actionList: ArrayList<UnitAction>, city: CityInfo) {
        if (!unit.civInfo.gameInfo.isReligionEnabled()) return
        if (city.civInfo != unit.civInfo) return
        // Only allow the action if the city actually has any foreign religion
        // This will almost be always due to pressure from cities close-by
        if (city.religion.getPressures().none { it.key != unit.religion!! }) return
        actionList += UnitAction(UnitActionType.RemoveHeresy,
            title = "Remove Heresy",
            action = {
                city.religion.removeAllPressuresExceptFor(unit.religion!!)
                unit.currentMovement = 0f
                useActionWithLimitedUses(unit, Constants.removeHeresyAbilityCount)
            }.takeIf { unit.currentMovement > 0f }
        )
    }

    fun getImprovementConstructionActions(unit: MapUnit, tile: TileInfo): ArrayList<UnitAction> {
        val finalActions = ArrayList<UnitAction>()
        var uniquesToCheck = unit.getMatchingUniques(UniqueType.ConstructImprovementConsumingUnit)
        if (unit.religiousActionsUnitCanDo().all { unit.abilityUsesLeft[it] == unit.maxAbilityUses[it] })
            uniquesToCheck += unit.getMatchingUniques(UniqueType.CanConstructIfNoOtherActions)
        val civResources = unit.civInfo.getCivResourcesByName()

        for (unique in uniquesToCheck) {
            val improvementName = unique.params[0]
            val improvement = tile.ruleset.tileImprovements[improvementName]
                ?: continue

            val resourcesAvailable = improvement.uniqueObjects.none {
                it.isOfType(UniqueType.ConsumesResources) &&
                        (civResources[unique.params[1]] ?: 0) < unique.params[0].toInt()
            }

            finalActions += UnitAction(UnitActionType.Create,
                title = "Create [$improvementName]",
                action = {
                    val unitTile = unit.getTile()
                    unitTile.removeCreatesOneImprovementMarker()
                    unitTile.improvement = improvementName
                    unitTile.stopWorkingOnImprovement()
                    improvement.handleImprovementCompletion(unit)
                    unit.consume()
                }.takeIf {
                    resourcesAvailable
                    && unit.currentMovement > 0f
                    && tile.canBuildImprovement(improvement, unit.civInfo)
                    // Next test is to prevent interfering with UniqueType.CreatesOneImprovement -
                    // not pretty, but users *can* remove the building from the city queue an thus clear this:
                    && !tile.isMarkedForCreatesOneImprovement()
                    && !tile.isImpassible() // Not 100% sure that this check is necessary...
                })
        }
        return finalActions
    }

    fun takeOverTilesAround(unit: MapUnit) {
        // This method should only be called for a citadel - therefore one of the neighbour tile
        // must belong to unit's civ, so minByOrNull in the nearestCity formula should be never `null`.
        // That is, unless a mod does not specify the proper unique - then fallbackNearestCity will take over.

        fun priority(tile: TileInfo): Int { // helper calculates priority (lower is better): distance plus razing malus
            val city = tile.getCity()!!       // !! assertion is guaranteed by the outer filter selector.
            return city.getCenterTile().aerialDistanceTo(tile) +
                    (if (city.isBeingRazed) 5 else 0)
        }
        fun fallbackNearestCity(unit: MapUnit) =
            unit.civInfo.cities.minByOrNull {
               it.getCenterTile().aerialDistanceTo(unit.currentTile) +
                   (if (it.isBeingRazed) 5 else 0)
            }!!

        // In the rare case more than one city owns tiles neighboring the citadel
        // this will prioritize the nearest one not being razed
        val nearestCity = unit.currentTile.neighbors
            .filter { it.getOwner() == unit.civInfo }
            .minByOrNull { priority(it) }?.getCity()
            ?: fallbackNearestCity(unit)

        // capture all tiles which do not belong to unit's civ and are not enemy cities
        // we use getTilesInDistance here, not neighbours to include the current tile as well
        val tilesToTakeOver = unit.currentTile.getTilesInDistance(1)
                .filter { !it.isCityCenter() && it.getOwner() != unit.civInfo }

        val civsToNotify = mutableSetOf<CivilizationInfo>()
        for (tile in tilesToTakeOver) {
            val otherCiv = tile.getOwner()
            if (otherCiv != null) {
                // decrease relations for -10 pt/tile
                if (!otherCiv.knows(unit.civInfo)) otherCiv.makeCivilizationsMeet(unit.civInfo)
                otherCiv.getDiplomacyManager(unit.civInfo).addModifier(DiplomaticModifiers.StealingTerritory, -10f)
                civsToNotify.add(otherCiv)
            }
            nearestCity.expansion.takeOwnership(tile)
        }

        for (otherCiv in civsToNotify)
            otherCiv.addNotification("Your territory has been stolen by [${unit.civInfo}]!", unit.currentTile.position, unit.civInfo.civName, NotificationIcon.War)
    }

    private fun addFortifyActions(actionList: ArrayList<UnitAction>, unit: MapUnit, showingAdditionalActions: Boolean) {
        if (unit.isFortified() && !showingAdditionalActions) {
            actionList += UnitAction(
                type = if (unit.isActionUntilHealed())
                    UnitActionType.FortifyUntilHealed else
                    UnitActionType.Fortify,
                isCurrentAction = true,
                title = "${"Fortification".tr()} ${unit.getFortificationTurns() * 20}%"
            )
            return
        }

        if (!unit.canFortify()) return
        if (unit.currentMovement == 0f) return

        val isFortified = unit.isFortified()
        val isDamaged = unit.health < 100

        if (isDamaged && !showingAdditionalActions && unit.rankTileForHealing(unit.currentTile) != 0)
            actionList += UnitAction(UnitActionType.FortifyUntilHealed,
                action = { unit.fortifyUntilHealed() }.takeIf { !unit.isFortifyingUntilHealed() })
        else if (isDamaged || !showingAdditionalActions)
            actionList += UnitAction(UnitActionType.Fortify,
                action = { unit.fortify() }.takeIf { !isFortified })
    }

    private fun addSleepActions(actionList: ArrayList<UnitAction>, unit: MapUnit, showingAdditionalActions: Boolean) {
        if (unit.isFortified() || unit.canFortify() || unit.currentMovement == 0f) return
        // If this unit is working on an improvement, it cannot sleep
        if (unit.currentTile.hasImprovementInProgress()
            && unit.canBuildImprovement(unit.currentTile.getTileImprovementInProgress()!!)) return
        val isSleeping = unit.isSleeping()
        val isDamaged = unit.health < 100

        if (isDamaged && !showingAdditionalActions) {
            actionList += UnitAction(UnitActionType.SleepUntilHealed,
                action = { unit.action = UnitActionType.SleepUntilHealed.value }
                    .takeIf { !unit.isSleepingUntilHealed() }
            )
        } else if (isDamaged || !showingAdditionalActions) {
            actionList += UnitAction(UnitActionType.Sleep,
                action = { unit.action = UnitActionType.Sleep.value }.takeIf { !isSleeping }
            )
        }
    }

    fun canPillage(unit: MapUnit, tile: TileInfo): Boolean {
        val tileImprovement = tile.getTileImprovement()
        // City ruins, Ancient Ruins, Barbarian Camp, City Center marked in json
        if (tileImprovement == null || tileImprovement.hasUnique(UniqueType.Unpillagable)) return false
        val tileOwner = tile.getOwner()
        // Can't pillage friendly tiles, just like you can't attack them - it's an 'act of war' thing
        return tileOwner == null || unit.civInfo.isAtWarWith(tileOwner)
    }

    private fun addGiftAction(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: TileInfo) {
        val getGiftAction = getGiftAction(unit, tile)
        if (getGiftAction != null) actionList += getGiftAction
    }

    fun getGiftAction(unit: MapUnit, tile: TileInfo): UnitAction? {
        val recipient = tile.getOwner()
        // We need to be in another civs territory.
        if (recipient == null || recipient.isCurrentPlayer()) return null

        if (recipient.isCityState()) {
            if (recipient.isAtWarWith(unit.civInfo)) return null // No gifts to enemy CS
            // City States only take military units (and units specifically allowed by uniques)
            if (!unit.isMilitary()
                && unit.getMatchingUniques(UniqueType.GainInfluenceWithUnitGiftToCityState, checkCivInfoUniques = true)
                    .none { unit.matchesFilter(it.params[1]) }
            ) return null
        }
        // If gifting to major civ they need to be friendly
        else if (!tile.isFriendlyTerritory(unit.civInfo)) return null

        if (unit.currentMovement <= 0)
            return UnitAction(UnitActionType.GiftUnit, action = null)

        val giftAction = {
            if (recipient.isCityState()) {
                for (unique in unit.getMatchingUniques(UniqueType.GainInfluenceWithUnitGiftToCityState, checkCivInfoUniques = true)) {
                    if (unit.matchesFilter(unique.params[1])) {
                        recipient.getDiplomacyManager(unit.civInfo)
                            .addInfluence(unique.params[0].toFloat() - 5f)
                        break
                    }
                }

                recipient.getDiplomacyManager(unit.civInfo).addInfluence(5f)
            } else recipient.getDiplomacyManager(unit.civInfo)
                .addModifier(DiplomaticModifiers.GaveUsUnits, 5f)

            if (recipient.isCityState() && unit.isGreatPerson())
                unit.destroy()  // City states dont get GPs
            else
                unit.gift(recipient)
            UncivGame.Current.worldScreen!!.shouldUpdate = true
        }

        return UnitAction(UnitActionType.GiftUnit, action = giftAction)
    }

    private fun addTriggerUniqueActions(unit: MapUnit, actionList: ArrayList<UnitAction>){
        for (unique in unit.getUniques()) {
            if (!unique.conditionals.any { it.type == UniqueType.ConditionalConsumeUnit }) continue
            val unitAction = UnitAction(type = UnitActionType.TriggerUnique, unique.text){
                UniqueTriggerActivation.triggerCivwideUnique(unique, unit.civInfo)
                unit.consume()
            }
            actionList += unitAction
        }
    }

    private fun addWaitAction(unit: MapUnit, actionList: ArrayList<UnitAction>, worldScreen: WorldScreen) {
        if (!unit.isIdle()) return
        if (worldScreen.viewingCiv.getDueUnits().filter { it != unit }.none()) return
        actionList += UnitAction(
            type = UnitActionType.Wait,
            action = {
                unit.due = true
                worldScreen.switchToNextUnit()
            }
        )
    }

    private fun addToggleActionsAction(unit: MapUnit, actionList: ArrayList<UnitAction>, unitTable: UnitTable) {
        actionList += UnitAction(
            type = if (unit.showAdditionalActions) UnitActionType.HideAdditionalActions
            else UnitActionType.ShowAdditionalActions,
            action = {
                unit.showAdditionalActions = !unit.showAdditionalActions
                unitTable.update()
            }
        )
    }
}
