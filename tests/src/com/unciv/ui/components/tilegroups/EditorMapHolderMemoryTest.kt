package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.dev.FontDesktop
import com.unciv.models.tilesets.TileSetCache
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.MeasureMemory
import com.unciv.testing.MeasureMemory.toMB
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.testing.TestGame
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Memory regression test tracking TileGroup allocation for large maps.
 * The OOM in the issue tracker occurs in TileGroup.<init> when creating TileGroups
 * for a large map on Android's 512MB heap.
 * Run across versions and compare printed output to track regressions.
 */
@RunWith(GdxTestRunner::class)
class EditorMapHolderMemoryTest {

    private lateinit var testGame: TestGame

    @Before
    fun setUp() {
        testGame = TestGame()
        Fonts.fontImplementation = FontDesktop()
        ImageGetter.setNewRuleset(testGame.ruleset)
        TileSetCache.loadTileSetConfigs()
    }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun tileGroupMemoryFor100x100Map() {
        testGame.makeRectangularMap(100, 100)
        val tileMap = testGame.tileMap
        val tileSetStrings = TileSetStrings()

        val (tileGroups, allocatedBytes) = MeasureMemory.measure {
            tileMap.values.map { TileGroup(it, tileSetStrings) }
        }

        val tileCount = tileGroups.size
        val bytesPerTile = if (tileCount > 0) allocatedBytes / tileCount else 0L
        println("TileGroup memory: ${allocatedBytes.toMB()} for $tileCount tiles ($bytesPerTile bytes/tile)")

        // On Android with a 512MB heap, exceeding ~200MB for 10k tiles pushes close to OOM.
        Assert.assertTrue(
            "TileGroup allocation of ${allocatedBytes.toMB()} exceeds 200 MB for $tileCount tiles",
            allocatedBytes < 200L * 1024 * 1024
        )
    }

    @Test
    @Ignore("Cannot fail, it's a measurement tool to run locally.")
    @RedirectOutput(RedirectPolicy.Show)
    fun tileGroupMemoryBreakdown() {
        testGame.makeRectangularMap(100, 100)
        val tiles = testGame.tileMap.values.toList()
        val n = tiles.size
        val tileSetStrings = TileSetStrings()

        // Baseline: full TileGroup
        val (_, total) = MeasureMemory.measure { tiles.map { TileGroup(it, tileSetStrings) } }
        val bpt = total / n  // bytes per tile

        // Reference: cost of 12 Group objects per tile (TileGroup + the 11 TileLayer Groups that
        // existed BEFORE the flat-layer refactor). After the refactor, TileLayers are plain Kotlin
        // classes, so only TileGroup itself contributes Group overhead.
        val (_, groups12) = MeasureMemory.measure { (1..n * 12).map { Group() } }

        // Reference: cost of a YieldGroup per tile.
        // After the flat-layer refactor, TileLayerYield.yields is lazy — only created for tiles
        // that actually display yields.
        val (_, yieldGroups) = MeasureMemory.measure { (1..n).map { YieldGroup() } }

        // Reference: cost of one Image per tile.
        // After the flat-layer refactor, TileLayerMisc.terrainOverlay is lazy — only created
        // when an overlay color is applied. whiteDot (getImage(null)) equals any image's size.
        val (_, images) = MeasureMemory.measure { (1..n).map { ImageGetter.getImage(null) } }

        // Empty collections created at construction:
        //   TileLayerFeatures.roadImages (HashMap), TileLayerBorders.borderSegments (HashMap),
        //   TileLayerMisc.arrows (HashMap) → 3 HashMaps
        val (_, hashMaps) = MeasureMemory.measure { (1..n * 3).map { HashMap<Any, Any>() } }

        //   TileLayerMisc.arrowsToDraw (ArrayList), TileLayerMisc.startingLocationIcons (ArrayList),
        //   TileLayerTerrain.tileBaseImages (ArrayList) → 3 ArrayLists
        val (_, arrayLists) = MeasureMemory.measure { (1..n * 3).map { ArrayList<Any>() } }

        // neighborEdgeDataList in TileLayerTerrain: isolated by comparing per-tile cost on
        // a 1×1 map (0 neighbors → empty sequence, no NeighborEdgeData objects)
        // vs 100×100 map (interior tiles have 6 neighbors → 6 NeighborEdgeData objects each).
        testGame.makeRectangularMap(1, 1)
        val tile1x1 = testGame.tileMap.values.toList()
        val (_, total1x1) = MeasureMemory.measure { tile1x1.map { TileGroup(it, tileSetStrings) } }
        val neighborEdgeCost = (total / n) - (total1x1 / tile1x1.size)

        // Reference: cost of ~10 ownedActors ArrayLists per tile (one per non-terrain layer).
        // After lazy-init, only layerTerrain.ownedActors is created at construction; the other 10
        // layers never add actors during init, so their lists are deferred to first gameplay update.
        val (_, ownedActorLists10) = MeasureMemory.measure { (1..n * 10).map { ArrayList<Any>() } }

        fun Long.pct() = "(${"%4.1f".format(this * 100.0 / bpt)}%)"

        println("=== TileGroup memory breakdown ($n tiles) ===")
        println("Total:              ${total.toMB()}  =  $bpt bytes/tile")
        println()
        println("Reference components (each measured independently; costs saved by flat-layer refactor):")
        println("  12 Group objects: %6d b/t  %s  ← now only TileGroup is a Group (11 saved)".format(groups12 / n, (groups12 / n).pct()))
        println("  YieldGroup:       %6d b/t  %s  ← now lazy (saved for most tiles)".format(yieldGroups / n, (yieldGroups / n).pct()))
        println("  terrainOverlay:   %6d b/t  %s  ← now lazy (saved for most tiles)".format(images / n, (images / n).pct()))
        println("  3 HashMaps:       %6d b/t  %s  ← roadImages/borderSegments/arrows (empty)".format(hashMaps / n, (hashMaps / n).pct()))
        println("  3 ArrayLists:     %6d b/t  %s  ← arrowsToDraw/startingLocs/tileBaseImages".format(arrayLists / n, (arrayLists / n).pct()))
        println("  neighborEdgeData: %6d b/t  %s  ← ≈6 NeighborEdgeData objects (100×100 vs 1×1)".format(neighborEdgeCost, neighborEdgeCost.pct()))
        println("  10 ownedActors:   %6d b/t  %s  ← non-terrain layers now lazy (saved at construction)".format(ownedActorLists10 / n, (ownedActorLists10 / n).pct()))
        val accounted = groups12 / n + yieldGroups / n + images / n + hashMaps / n + arrayLists / n + neighborEdgeCost + ownedActorLists10 / n
        println()
        println("  Subtotal above:   %6d b/t  %s".format(accounted, accounted.pct()))
        println("  Remainder:        %6d b/t  %s  ← terrain Images, TileGroup fields, etc.".format(bpt - accounted, (bpt - accounted).pct()))
    }
}
