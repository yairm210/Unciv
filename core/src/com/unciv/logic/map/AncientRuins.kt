package com.unciv.logic.map

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tech.TechEra
import com.unciv.models.stats.Stat
import com.unciv.ui.pickerscreens.RuinBonusPickerScreen
import com.unciv.ui.worldscreen.unit.UnitActions
import kotlin.random.Random

class AncientRuins {

    val civInfo: CivilizationInfo
    val tile: TileInfo
    val unit: MapUnit
    val actions: ArrayList<RuinAction>
    val randomSeed: Random

    companion object {
        private const val ANCIENT_RUIN_MAP_REVEAL_OFFSET = 4
        private const val ANCIENT_RUIN_MAP_REVEAL_RANGE = 4
        private const val ANCIENT_RUIN_MAP_REVEAL_CHANCE = 0.8f
    }

    constructor(civInfo: CivilizationInfo, tile: TileInfo, unit: MapUnit) {
        this.civInfo = civInfo
        this.tile = tile
        this.unit = unit

        tile.improvement=null
        randomSeed = Random(tile.position.toString().hashCode())
        actions = ArrayList()
        var blockedActions = civInfo.updateAndGetBlockedRuinsBonus()
        if(civInfo.cities.isNotEmpty() && !blockedActions.contains(RuinBonus.POPULATION)) {
            actions.add(RuinAction(RuinBonus.POPULATION) {
                val city = civInfo.cities.random(randomSeed)
                city.population.population++
                city.population.autoAssignPopulation()
                val cityName = city.name
                civInfo.addNotification("We have found survivors in the ruins - population added to [$cityName]", tile.position, Color.GREEN)
                civInfo.recordPickRuinBonus(RuinBonus.POPULATION)
            })
        }

        val researchableAncientEraTechs = tile.tileMap.gameInfo.ruleSet.technologies.values
                .filter {
                    !civInfo.tech.isResearched(it.name)
                            && civInfo.tech.canBeResearched(it.name)
                            && it.era() == TechEra.Ancient
                }
        if(researchableAncientEraTechs.isNotEmpty() && !blockedActions.contains(RuinBonus.TECH)) {
            actions.add(RuinAction(RuinBonus.TECH) {
                val tech = researchableAncientEraTechs.random(randomSeed).name
                civInfo.tech.addTechnology(tech)
                civInfo.addNotification("We have discovered the lost technology of [$tech] in the ruins!", tile.position, Color.BLUE)
                civInfo.recordPickRuinBonus(RuinBonus.TECH)
            })
        }

        if (UncivGame.Current.gameInfo.difficulty == "Settler") {
            if ((!(civInfo.isCityState() || civInfo.isOneCityChallenger())) && !blockedActions.contains(RuinBonus.JOIN_SETTLER)) {//City states and OCC don't get settler from ruins
                actions.add(RuinAction(RuinBonus.JOIN_SETTLER) {
                    performUnitJoin(civInfo, tile, Constants.settler)
                    civInfo.recordPickRuinBonus(RuinBonus.JOIN_SETTLER)
                })
            }
            if (!blockedActions.contains(RuinBonus.JOIN_WORKER)) {
                actions.add(RuinAction(RuinBonus.JOIN_WORKER) {
                    performUnitJoin(civInfo, tile, Constants.worker)
                    civInfo.recordPickRuinBonus(RuinBonus.JOIN_WORKER)
                })
            }
        }

        if(!unit.type.isCivilian() && !blockedActions.contains(RuinBonus.PROMOTION) && unit.getUnitToUpgradeTo(true) != unit.baseUnit()) {
            actions.add(RuinAction(RuinBonus.PROMOTION) {
                UnitActions.getUpgradeAction(unit, tile, unit.getUnitToUpgradeTo(true), 0, true)?.invoke()
                val unitName = unit.name;
                civInfo.addNotification("An ancient tribe trains our [$unitName] in their ways of combat!", tile.position, Color.RED)
                civInfo.recordPickRuinBonus(RuinBonus.PROMOTION)
            })
        }

        if(!blockedActions.contains(RuinBonus.CULTURE)) {
            actions.add(RuinAction(RuinBonus.CULTURE) {
                civInfo.statsForNextTurn.add(Stat.Culture, 20f)
                civInfo.addNotification("You have found cultural artifacts which awe your citizens! You have received 20 Culture!", tile.position, Color.PINK)
                civInfo.recordPickRuinBonus(RuinBonus.CULTURE)
            })
        }

        if (!blockedActions.contains(RuinBonus.GOLD)) {
            actions.add(RuinAction(RuinBonus.GOLD) {
                val amount = listOf(25, 60, 100).random(randomSeed)
                civInfo.gold += amount
                civInfo.addNotification("We have found a stash of [$amount] gold in the ruins!", tile.position, Color.GOLD)
                civInfo.recordPickRuinBonus(RuinBonus.GOLD)
            })
        }

        // Map of the surrounding area
        if (!blockedActions.contains(RuinBonus.MAPS)) {
            actions.add(RuinAction(RuinBonus.MAPS) {
                val revealCenter = tile.getTilesAtDistance(ANCIENT_RUIN_MAP_REVEAL_OFFSET).toList().random(randomSeed)
                val tilesToReveal = revealCenter
                        .getTilesInDistance(ANCIENT_RUIN_MAP_REVEAL_RANGE)
                        .filter { Random.nextFloat() < ANCIENT_RUIN_MAP_REVEAL_CHANCE }
                        .map { it.position }
                civInfo.exploredTiles.addAll(tilesToReveal)
                civInfo.addNotification("We have found a crudely-drawn map in the ruins!", tile.position, Color.RED)
                civInfo.recordPickRuinBonus(RuinBonus.MAPS)
            })
        }

    }

    private fun performUnitJoin(civInfo: CivilizationInfo, tile: TileInfo, chosenUnit: String) {
        civInfo.placeUnitNearTile(tile.position, chosenUnit)
        civInfo.addNotification("A [$chosenUnit] has joined us!", tile.position, Color.BROWN)
    }

    fun doRuinsBonus() {
        if (unit.name == "Pathfinder") {
            UncivGame.Current.setScreen(RuinBonusPickerScreen(actions, tile))
        } else {
            (actions.map { it.action }.random(randomSeed))()
        }
    }

    class RuinAction(ruinBonus: RuinBonus, var action: () -> Unit) {
        var bonus: RuinBonus = ruinBonus
    }

    enum class RuinBonus(val desc: String) {
        POPULATION("Convince the remaining population to join one of your cities"),
        JOIN_SETTLER("Convince the lost tribe to become a Settler for your civilization"),
        JOIN_WORKER("Convince the lost tribe to become a Worker for your civilization"),
        TECH("Study their tools to discover a new technology"),
        PROMOTION("Use equipment around the tribe to upgrade your unit"),
        GOLD("Trade with the lost tribe for gold"),
        CULTURE("Use this contact with the lost tribe to enhance your culture"),
        MAPS("Have a look at their maps")
    }

}