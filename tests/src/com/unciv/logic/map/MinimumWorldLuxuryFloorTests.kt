package com.unciv.logic.map

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.logic.map.mapgenerator.resourceplacement.LuxuryResourcePlacementLogic
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.testing.GdxTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spot-checks optional ModConstants world luxury floor resolution and soft target math. */
class MinimumWorldLuxuryFloorTests {

    @Test
    fun zeroConstantsDisableFloor() {
        val ruleset = Ruleset()
        assertEquals(
            0,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize.Large, MapResourceSetting.default
            )
        )
    }

    @Test
    fun largeDefaultUsesConfiguredFloor() {
        val ruleset = Ruleset()
        ruleset.modOptions.constants.minimumWorldLuxuriesLarge = 88
        assertEquals(
            88,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize.Large, MapResourceSetting.default
            )
        )
    }

    @Test
    fun abundantScalesByRandomLuxuriesPercent() {
        val ruleset = Ruleset()
        ruleset.modOptions.constants.minimumWorldLuxuriesLarge = 88
        // Abundant.randomLuxuriesPercent = 133 → 88 * 133 / 100 = 117
        assertEquals(
            117,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize.Large, MapResourceSetting.abundant
            )
        )
    }

    @Test
    fun customRadiusMapsToNextSmallerPredefined() {
        val ruleset = Ruleset()
        ruleset.modOptions.constants.minimumWorldLuxuriesLarge = 88
        ruleset.modOptions.constants.minimumWorldLuxuriesHuge = 112
        // radius 35 → Large (30), not Huge (40)
        assertEquals(
            88,
            LuxuryResourcePlacementLogic.resolveMinimumWorldLuxuryFloor(
                ruleset, MapSize(35), MapResourceSetting.default
            )
        )
    }

    @Test
    fun nonBindingFloorKeepsLegacyTarget() {
        // alreadyPlaced high enough that deficit is 0 → same target as floor=0 (no second RNG needed).
        val legacyBase = 40
        val civVariance = 3
        assertEquals(
            LuxuryResourcePlacementLogic.resolveRandomLuxuryPlacementTarget(legacyBase, civVariance, 0, 50),
            LuxuryResourcePlacementLogic.resolveRandomLuxuryPlacementTarget(legacyBase, civVariance, 10, 50)
        )
        assertEquals(43, LuxuryResourcePlacementLogic.resolveRandomLuxuryPlacementTarget(legacyBase, civVariance, 10, 50))
    }

    @Test
    fun bindingFloorRaisesTargetAboveLegacy() {
        val legacyBase = 30
        val civVariance = 2
        val floor = 100
        val already = 40
        val withFloor = LuxuryResourcePlacementLogic.resolveRandomLuxuryPlacementTarget(
            legacyBase, civVariance, floor, already
        )
        val without = LuxuryResourcePlacementLogic.resolveRandomLuxuryPlacementTarget(
            legacyBase, civVariance, 0, already
        )
        assertEquals(32, without)
        // deficit = 100 + 2 - 40 = 62
        assertEquals(62, withFloor)
        assertTrue(withFloor > without)
    }
}

/**
 * Smoke: floor runs on the GameStarter / MapRegions path (not map-editor spreadResources).
 * Full bit-identity across two startNewGame calls is not asserted — each game gets a new gameId RNG stream.
 */
@RunWith(GdxTestRunner::class)
class MinimumWorldLuxuryFloorGameStarterTests {

    @Before
    fun setup() {
        UncivGame.Current = UncivGame()
        UncivGame.Current.files = UncivFiles(Gdx.files)
        UncivGame.Current.settings = GameSettings()
        if (RulesetCache.isEmpty())
            RulesetCache.loadRulesets(noMods = true)
    }

    @Test
    fun gameStarterWithFloorCompletes() {
        val ruleset = RulesetCache.getVanillaRuleset()
        val previous = ruleset.modOptions.constants.minimumWorldLuxuriesLarge
        ruleset.modOptions.constants.minimumWorldLuxuriesLarge = 88
        try {
            val gameParameters = GameParameters().apply {
                numberOfCityStates = 8
                noBarbarians = true
                players.clear()
                players.add(Player(playerType = PlayerType.Human))
                repeat(5) { players.add(Player()) }
            }
            val mapParameters = MapParameters().apply {
                type = MapType.pangaea
                shape = MapShape.hexagonal
                mapSize = MapSize.Large
                seed = 1L
            }
            val game = GameStarter.startNewGame(GameSetupInfo(gameParameters, mapParameters))
            val luxuries = game.tileMap.tileList.count {
                it.tileResource?.resourceType == ResourceType.Luxury
            }
            assertTrue("expected some luxuries, got $luxuries", luxuries > 0)
        } finally {
            ruleset.modOptions.constants.minimumWorldLuxuriesLarge = previous
        }
    }
}
