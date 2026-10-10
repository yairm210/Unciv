package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a pass/fail regression test - a measurement tool.
 * Runs many real games through [GameStarter] and reports what fraction of civs actually
 * land on a tile satisfying their nation's start bias, so a change to the placement
 * algorithm can be evaluated before/after by re-running this and comparing the printed rate.
 */
@RunWith(BaseTestRunner::class)
class GameStarterStartBiasTests {

    // One nation per simple bias type actually used in the base ruleset, so each run
    // exercises a different kind of bias (single-terrain, avoid, and coast/adjacency-based).
    private val civNames = listOf(
        "Russia",   // Tundra
        "Arabia",   // Desert
        "India",    // Grassland
        "Iroquois", // Forest
        "Aztecs",   // Jungle
        "Inca",     // Hill
        "Mongolia", // Plains
        "Greece",   // Coast
    )

    private val runs = 200

    @Before
    fun loadRulesets() {
        if (RulesetCache.isEmpty())
            RulesetCache.loadRulesets(noMods = true)
        UncivGame.Current = UncivGame()
        UncivGame.Current.files = UncivFiles(Gdx.files)
        UncivGame.Current.settings = GameSettings()
    }

    private fun tileSatisfiesBias(tile: com.unciv.logic.map.tile.Tile, bias: String): Boolean {
        if (bias.equalsPlaceholderText("Avoid []")) {
            val toAvoid = bias.getPlaceholderParameters()[0]
            return !tile.getTilesInDistance(1).any { it.matchesTerrainFilter(toAvoid, null) }
        }
        return tile.getTilesInDistance(1).any { it.matchesTerrainFilter(bias, null) }
    }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun `measure start bias follow rate over 200 games`() {
        var followedBiases = 0
        var totalBiases = 0
        val perNationFollowed = HashMap<String, Int>()
        val perNationTotal = HashMap<String, Int>()

        repeat(runs) {
            val gameParameters = GameParameters().apply {
                players = ArrayList(civNames.mapIndexed { index, name ->
                    Player(name, if (index == 0) PlayerType.Human else PlayerType.AI)
                })
                numberOfCityStates = 0
                noBarbarians = true
            }
            val mapParameters = MapParameters().apply {
                mapSize = MapSize.Small
                seed = System.nanoTime()
            }
            val setup = GameSetupInfo(gameParameters, mapParameters)
            val game = GameStarter.startNewGame(setup)

            for (civ in game.civilizations.filter { it.isMajorCiv() }) {
                val tile = civ.units.getCivUnits().firstOrNull()?.getTile() ?: continue
                for (bias in civ.nation.startBias) {
                    totalBiases++
                    perNationTotal.merge(civ.civName, 1, Int::plus)
                    if (tileSatisfiesBias(tile, bias)) {
                        followedBiases++
                        perNationFollowed.merge(civ.civName, 1, Int::plus)
                    }
                }
            }
        }

        val percentage = 100.0 * followedBiases / totalBiases
        println("Start bias follow rate: $followedBiases/$totalBiases (${"%.1f".format(percentage)}%)")
        for (name in civNames) {
            val f = perNationFollowed[name] ?: 0
            val t = perNationTotal[name] ?: 0
            println("  $name: $f/$t (${"%.1f".format(100.0 * f / t)}%)")
        }
    }
}
