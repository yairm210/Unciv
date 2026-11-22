package com.unciv.logic

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.unciv.logic.map.OdMapImport
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The `.odmap` importer, against an archive built here rather than a fixture
 * committed to this repository -- Open Doctrines' own maps are large and are
 * not ours to ship.
 *
 * Pixmap needs the natives, hence [GdxTestRunner] rather than BaseTestRunner.
 */
@RunWith(GdxTestRunner::class)
class OdMapImportTests {
    private lateinit var ruleset: Ruleset

    @Before
    fun loadRuleset() {
        RulesetCache.loadRulesets(noMods = true)
        ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
    }

    /** Left half land (province 1), right half unpainted, which means water. */
    private fun writeOdmap(withTerrain: Boolean): FileHandle {
        val w = 64
        val h = 32
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        for (y in 0 until h) for (x in 0 until w) {
            // Province id 1 is (r,g,b) = (0,0,1); id 0 is black, meaning unpainted.
            val id = if (x < w / 2) 1 else 0
            pixmap.drawPixel(x, y, (id shl 8) or 0xFF)
        }
        val pngFile = File.createTempFile("provinces", ".png")
        PixmapIO.writePNG(FileHandle(pngFile), pixmap)
        pixmap.dispose()

        val terrain = if (withTerrain) ""","terrain":"desert"""" else ""
        val table = """{"1":{"id":1,"iso_a3":"ZZZ","name":"Testland"$terrain}}"""

        val odmap = File.createTempFile("test", ".odmap")
        ZipOutputStream(odmap.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("provinces.png"))
            zip.write(pngFile.readBytes())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("provinces.json"))
            zip.write(table.toByteArray())
            zip.closeEntry()
        }
        pngFile.delete()
        odmap.deleteOnExit()
        return FileHandle(odmap)
    }

    @Test
    fun `an odmap becomes a tile map Unciv can use`() {
        val map = OdMapImport.read(writeOdmap(withTerrain = false), ruleset, 20, 10)
        Assert.assertEquals(200, map.values.size)
        for (tile in map.values)
            Assert.assertNotNull(
                "${tile.baseTerrain} is not in the ruleset",
                ruleset.terrains[tile.baseTerrain]
            )
    }

    /**
     * Every hex must actually be painted.
     *
     * The reader walks the raster by grid INDEX and converts to a hex
     * coordinate with the same offset TileMap's constructor used. Get that
     * offset wrong and the positions fall outside the map, `contains` rejects
     * them, and every tile silently keeps the placeholder terrain the
     * constructor filled it with -- a map that loads, draws, and is all land.
     *
     * Note what is NOT asserted here: that the grid is centred on the origin.
     * It is, and it has to be -- setTransients only checks the extent from its
     * second call, which is the one GameStarter makes, so a grid numbered from
     * zero loads in the editor and throws the moment a game starts. But that
     * comes free from using TileMap(columns, rows, ruleset, worldWrap) instead
     * of laying coordinates out by hand, so an assertion about it cannot fail
     * while this reader is written this way. The second setTransients below is
     * the real guard: it is the call that would throw if that ever changed.
     */
    @Test
    fun `every hex is painted, and the map survives a second setTransients`() {
        val map = OdMapImport.read(writeOdmap(withTerrain = true), ruleset, 20, 10)

        // The fixture is half province 1 ("desert"), half unpainted ("Ocean").
        // Any tile that is neither was skipped and kept its placeholder.
        val unpainted = map.values.filter { it.baseTerrain != "Desert" && it.baseTerrain != "Ocean" }
        Assert.assertTrue(
            "${unpainted.size} of ${map.values.size} tiles were never painted, " +
                "they are ${unpainted.map { it.baseTerrain }.toSet()}",
            unpainted.isEmpty()
        )

        map.setTransients(ruleset, true)   // the call GameStarter makes
    }

    /** Unpainted pixels are open water, not a terrain guessed from latitude. */
    @Test
    fun `unpainted raster becomes ocean`() {
        val map = OdMapImport.read(writeOdmap(withTerrain = false), ruleset, 20, 10)
        val ocean = map.values.count { it.baseTerrain == "Ocean" }
        Assert.assertTrue("expected about half the map to be ocean, got $ocean of 200",
            ocean in 80..120)
    }

    /** A province that names its terrain gets that terrain, not a climate. */
    @Test
    fun `a stated terrain beats the latitude guess`() {
        val map = OdMapImport.read(writeOdmap(withTerrain = true), ruleset, 20, 10)
        val land = map.values.filter { it.baseTerrain != "Ocean" }
        Assert.assertTrue("no land at all", land.isNotEmpty())
        Assert.assertTrue(
            "expected every land tile to be Desert, saw ${land.map { it.baseTerrain }.toSet()}",
            land.all { it.baseTerrain == "Desert" }
        )
    }
}
