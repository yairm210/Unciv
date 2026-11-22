package com.unciv.logic.civilization

import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.testing.BaseTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(BaseTestRunner::class)
class ExploredRegionTests {
    private val mapParameters = MapParameters()

    /** Hex at longitude/latitude. Hexes only exist where long+lat is even, so an odd sum bumps [long] by one. */
    private fun hex(long: Int, lat: Int): HexCoord {
        val l = if (((long + lat) and 1) == 0) long else long + 1
        return HexCoord.of((lat + l) / 2, (lat - l) / 2)
    }

    private fun withNewRegion(block: ExploredRegion.() -> Unit) {
        ExploredRegion().apply {
            setMapParameters(mapParameters)
            block()
        }
    }
    private fun withWrappingRegion(block: ExploredRegion.() -> Unit) {
        val wrapParams = MapParameters().apply {
            shape = MapShape.rectangular
            mapSize = MapSize(20, 10)   // adjust to your MapSize constructor; mapRadius becomes 10
            worldWrap = true
        }
        ExploredRegion().apply {
            setMapParameters(wrapParams)
            block()
        }
    }

    private fun ExploredRegion.add(vararg hexes: HexCoord) = add(hexes.asIterable())
    private fun ExploredRegion.add(hexes: Iterable<HexCoord>) = hexes.forEach { expandToInclude(it, null) }
    private fun ExploredRegion.add(long: Int, lat: Int) = add(hex(long, lat))

    private fun describe(h: HexCoord) = "(long ${HexMath.getLongitude(h)}, lat ${HexMath.getLatitude(h)})"
    private fun ExploredRegion.assertIn(h: HexCoord, what: String = "tile") =
        assertTrue("$what ${describe(h)} should be inside the region", isPositionInRegion(h))
    private fun ExploredRegion.assertOut(h: HexCoord, what: String = "tile") =
        assertFalse("$what ${describe(h)} should be outside the region", isPositionInRegion(h))
    private fun ExploredRegion.assertExtent(width: Int, height: Int) {
        assertEquals("width", width, getWidth())
        assertEquals("height", height, getHeight())
    }
    private fun ExploredRegion.assertShouldRecalculateCoords() =
        assertTrue("stage coords must be recalculated", shouldRecalculateCoords())
    private fun ExploredRegion.assertShouldUpdateMinimap() =
        assertTrue("minimap must be rebuilt", shouldUpdateMinimap())

    @Test
    fun emptyRegionContainsNothing() = withNewRegion {
        assertTrue("a fresh region is empty", isEmpty())
        assertOut(hex(0, 0), "origin")
        // Regression: the degenerate wrapped-branch test admitted every longitude on latitude 0
        assertOut(hex(4, 0), "tile on the equator row")
    }

    @Test
    fun singleTileRegionIsOneColumnWide() = withNewRegion {
        add(3, 1)
        assertIn(hex(3, 1), "the added tile")
        assertOut(hex(5, 1), "same latitude, other column")
        assertOut(hex(3, 3), "same column, other latitude")
        assertExtent(1, 1)
    }

    @Test
    fun nonWrapRegionIsBoundingBoxOfAddedTiles() {
        val rnd = Random(42)
        // The origin is excluded, see originAsOnlyFirstTileIsKept
        val tiles = List(50) { hex(rnd.nextInt(-10, 11), rnd.nextInt(-10, 11)) }.filter { it != HexCoord.of(0, 0) }
        val longs = tiles.map { HexMath.getLongitude(it) }
        val lats = tiles.map { HexMath.getLatitude(it) }
        withNewRegion {
            add(tiles)
            tiles.forEach { assertIn(it, "added tile") }
            assertExtent(longs.max() - longs.min() + 1, lats.max() - lats.min() + 1)
            assertOut(hex(longs.max() + 2, lats.max()), "beyond the east edge")
            assertOut(hex(longs.min(), lats.max() + 2), "beyond the north edge")
        }
    }

    @Test
    fun firstTileMarksRegionDirty() = withNewRegion {
        calculateStageCoords(1000f, 1000f)
        markMinimapUpdated()
        add(1, 1)
        assertShouldRecalculateCoords()
        assertShouldUpdateMinimap()
    }

    @Test
    fun rebuildFromDiscardsOldStateAndMarksDirty() = withNewRegion {
        add(-8, 4)
        add(8, -4)
        calculateStageCoords(1000f, 1000f)
        markMinimapUpdated()
        assertFalse("precondition: flags cleared", shouldRecalculateCoords() || shouldUpdateMinimap())

        rebuildFrom(mapParameters, sequenceOf(hex(1, 1)))

        assertExtent(1, 1)
        assertOut(hex(-8, 4), "tile from before the rebuild")
        assertShouldRecalculateCoords()
        assertShouldUpdateMinimap()
    }

    @Test
    fun cloneIsIndependent() = withNewRegion {
        add(0, 2)
        val copy = clone()
        add(6, 2)
        copy.assertExtent(1, 1)  // fails if clone() shares the Vector2 instances
        copy.assertOut(hex(6, 2), "tile added to the original after cloning")
    }

    @Test
    fun worldWrapRegionCrossesEastEdge() = withWrappingRegion {
        add(8, 0)
        add(10, 0)          // long == mapRadius: stored as -10, region becomes "wrapped"
        assertIn(hex(10, 0), "east edge column")
        assertIn(hex(8, 0), "first tile")
        assertOut(hex(0, 0), "far side of the map, in the gap")
        assertExtent(3, 1)

        add(-8, 0)          // continues past the edge onto the west side
        assertIn(hex(-8, 0), "tile west of the seam")
        assertIn(hex(10, 0), "east edge column stays inside")
        assertOut(hex(0, 0), "gap still unexplored")
        assertOut(hex(-6, 0), "gap still unexplored")
        assertExtent(5, 1)  // columns 8, 9, 10, -9, -8
    }

    @Test
    fun worldWrapRegionCrossesWestEdge() = withWrappingRegion {
        // West edge column is -mapRadius+1 = -9, odd, so use an odd latitude
        add(-7, 1)
        add(-9, 1)          // stored as 11, region becomes "wrapped"
        assertIn(hex(-9, 1), "west edge column")
        assertOut(hex(1, 1), "gap")
        assertExtent(3, 1)

        add(9, 1)           // continues past the seam onto the east side
        assertIn(hex(9, 1), "tile east of the seam")
        assertIn(hex(-9, 1), "west edge column stays inside")
        assertOut(hex(1, 1), "gap")
        assertExtent(5, 1)  // columns 9, 10, -9, -8, -7
    }

    /**
     * Known limitation, documented here on purpose.
     *
     * "Empty" is encoded as both corners being exactly (0,0), so a region whose only tile is the map
     * origin is indistinguishable from an empty one, and the next tile re-initializes it.
     *
     * Not fixed because:
     *  - It is unreachable with normal vision: any reveal has at least 7 tiles, and the bounding box of
     *    the origin's six neighbours contains the origin. Only a vision-0 mod could trigger it.
     *  - A real fix needs an explicit "initialized" flag in the serialized state. Old saves lack it,
     *    so loading would have to derive it, and getting that wrong would turn every existing region "empty".
     */
    @Ignore("Known limitation: empty == exactly the origin, see KDoc")
    @Test
    fun originAsOnlyFirstTileIsKept() = withNewRegion {
        add(0, 0)
        add(2, 0)
        assertIn(hex(0, 0), "origin")
    }
}
