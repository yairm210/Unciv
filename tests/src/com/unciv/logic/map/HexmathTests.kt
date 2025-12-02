package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.json.json
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
            val coords = HexMath.getHexCoordsAtDistance(HexCoord.Zero, ring, 100, false)
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
    
    @Test
    fun testHexCoordConversions(){
        for (x in -2..2)
            for (y in -2..2){
                val hexCoord = HexCoord.of(x, y)
                Assert.assertEquals(hexCoord.x, x)
                Assert.assertEquals(hexCoord.y, y)
            }
    }

    
    @Test
    fun testHexCoordVector2SerDeser(){
        val json = json()
        
        for (x in -2..2)
            for (y in -2..2){
                val hexCoord = HexCoord(x, y)
                val vector2 = Vector2(x.toFloat(), y.toFloat())
                
                // hexcoord -> json -> vector2
                val hexCoordSerialized = json.toJson(hexCoord)
                val vector2Deserialized = json.fromJson(Vector2::class.java, hexCoordSerialized)
                Assert.assertEquals(vector2, vector2Deserialized)
                
                // vector2 -> json -> hexcoord
                val vector2Serialized = json.toJson(vector2)
                val hexCoordDeserialized = json.fromJson(HexCoord::class.java, vector2Serialized)
                Assert.assertEquals(hexCoord, hexCoordDeserialized)
            }
    }

}
