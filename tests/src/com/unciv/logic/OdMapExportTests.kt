package com.unciv.logic

import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.OdMapExport
import com.unciv.logic.map.OdMapImport
import com.unciv.logic.map.OdTerrain
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/** Writing an `.odmap`, and reading back what was written. */
@RunWith(GdxTestRunner::class)
class OdMapExportTests {
    private lateinit var ruleset: Ruleset

    @Before
    fun loadRuleset() {
        RulesetCache.loadRulesets(noMods = true)
        ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
    }

    /** A map with land, sea and several terrains, so the comparison has something to say. */
    private fun sampleMap(columns: Int = 20, rows: Int = 10): TileMap {
        val map = TileMap(columns, rows, ruleset, true)
        map.mapParameters.apply {
            shape = MapShape.rectangular
            mapSize = MapSize(columns, rows)
        }
        val terrains = listOf("Grassland", "Plains", "Desert", "Tundra", "Snow")
        for ((index, tile) in map.values.withIndex()) {
            tile.baseTerrain = if (index % 3 == 0) "Ocean" else terrains[index % terrains.size]
            tile.setTerrainFeatures(emptyList())
        }
        map.setTransients(ruleset, true)
        return map
    }

    private fun tempOdmap(): FileHandle =
        FileHandle(File.createTempFile("export", ".odmap").apply { deleteOnExit() })

    @Test
    fun `the archive holds what an odmap must hold`() {
        val file = tempOdmap()
        OdMapExport.write(sampleMap(), file, "Test map")
        ZipFile(file.file()).use { zip ->
            for (member in listOf("provinces.png", "land_sea.png",
                                  "provinces.json", "countries.json", "metadata.json"))
                Assert.assertNotNull("$member is missing", zip.getEntry(member))
        }
    }

    /**
     * The exporter paints each grid cell as a solid rectangle and the importer
     * samples the centre of each cell, so the GEOMETRY is an identity rather
     * than an approximation. If that stops holding, the two have drifted apart.
     *
     * The TERRAIN is an identity everywhere the model has a token for it, with
     * one documented exception: the model's thirteen tokens include no plain
     * grassland, so a Grassland hex is written `plains` and comes back Plains.
     * That is dragoman's limitation too, and changing it here would put a
     * token in the file that nothing else in either project understands.
     */
    @Test
    fun `a map survives the round trip hex for hex`() {
        val original = sampleMap()
        val file = tempOdmap()
        OdMapExport.write(original, file, "Test map")
        val back = OdMapImport.read(file, ruleset, 20, 10)

        Assert.assertEquals(original.values.size, back.values.size)
        val changed = ArrayList<String>()
        var grasslandBecamePlains = 0
        for (tile in original.values) {
            val other = back[tile.position]
            if (other.baseTerrain == tile.baseTerrain) continue
            if (tile.baseTerrain == "Grassland" && other.baseTerrain == "Plains") {
                grasslandBecamePlains++
                continue
            }
            changed.add("${tile.position}: ${tile.baseTerrain} -> ${other.baseTerrain}")
        }
        Assert.assertEquals(
            "hexes changed that should not have: ${changed.take(6)}", 0, changed.size)
        // And the known exception really is the known exception, not zero
        // because the test stopped exercising it.
        Assert.assertEquals(
            original.values.count { it.baseTerrain == "Grassland" }, grasslandBecamePlains)
    }

    /**
     * Every terrain the two sides share is one the ruleset has.
     *
     * Iterating the enum rather than a list written out here is the point: a
     * token added to [OdTerrain] is covered by this the moment it exists, and
     * there is no second list to forget to update. This test used to carry its
     * own copy of all thirteen.
     */
    @Test
    fun `every shared terrain exists in the ruleset`() {
        for (terrain in OdTerrain.entries) {
            Assert.assertNotNull(
                "${terrain.token} maps to ${terrain.baseTerrain}, which the ruleset has not got",
                ruleset.terrains[terrain.baseTerrain])
            if (terrain.feature != null)
                Assert.assertNotNull(
                    "${terrain.token} implies the feature ${terrain.feature}, which the ruleset has not got",
                    ruleset.terrains[terrain.feature])
            Assert.assertSame("forToken(${terrain.token}) is not itself",
                terrain, OdTerrain.forToken(terrain.token))
        }
        Assert.assertNull("a token nothing defines should be null",
            OdTerrain.forToken("not_a_terrain"))
    }

    /** Water hexes must leave the raster unpainted, which that game reads as sea. */
    @Test
    fun `water becomes unpainted raster`() {
        val original = sampleMap()
        val file = tempOdmap()
        OdMapExport.write(original, file, "Test map")

        val expectedLand = original.values.count { !it.isWater }
        val provinces = ZipFile(file.file()).use { zip ->
            zip.getInputStream(zip.getEntry("provinces.json")).use { it.readBytes() }
                .toString(Charsets.UTF_8)
        }
        // One province per land hex, and none for water.
        val count = Regex(""""id":\d+""").findAll(provinces).count()
        Assert.assertEquals(expectedLand, count)
    }

    @Test
    fun `an all-water map is refused rather than written empty`() {
        val map = sampleMap()
        for (tile in map.values) tile.baseTerrain = "Ocean"
        map.setTransients(ruleset, true)   // isWater is a transient of the terrain
        try {
            OdMapExport.write(map, tempOdmap(), "All sea")
            Assert.fail("expected an all-water map to be refused")
        } catch (expected: UncivShowableException) {
            Assert.assertTrue(expected.message, expected.message.contains("all water"))
        }
    }
}
