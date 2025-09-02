package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Test


class HexmathTests {
    // Looks like our current movement is actually unoptimized, since it fails this test :)
    @Test
    fun zeroIndexed(){
        Assert.assertEquals(0, HexMath.getZeroBasedIndex(0,0))
    }

    @Test
    fun testMappingIsOneToOne(){
        val seenCoordsMapping = hashSetOf<Int>()
        for (ring in 1..100) {
            val coords = HexMath.getVectorsAtDistance(Vector2.Zero, ring, 100, false)
            val ringStartCoordinate = 1 + 6 * ring * (ring - 1) / 2
            for (coord in coords) {
                val mapping = HexMath.getZeroBasedIndex(coord.x.toInt(), coord.y.toInt())
                Assert.assertFalse("Duplicate coords found: $coord", seenCoordsMapping.contains(mapping))
                Assert.assertTrue("Coords $coord should be in ring $ring, actual mapping $mapping", mapping in ringStartCoordinate .. (ringStartCoordinate + 6 * ring - 1))

                seenCoordsMapping.add(mapping)
            }
        }
    }
    
    @Test
    fun testTileNeighborMappingIsNonConflicting(){
        val ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
        val tileMap = TileMap(100, ruleset, false)
        val seenCoordsMapping = hashSetOf<Int>()
        
        for (tile in tileMap.values){
            for (neighbor in tile.neighbors){
                val index = HexMath.tilesAndNeighborUniqueIndex(tile, neighbor)
                Assert.assertFalse("Duplicate coords found: $neighbor", seenCoordsMapping.contains(index))

                seenCoordsMapping.add(index)
            }
        }
    }
}
