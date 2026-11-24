package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.unciv.dev.FontDesktop
import com.unciv.logic.map.HexCoord
import com.unciv.models.tilesets.TileSetCache
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.view.GameView
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TileLayerTerrainTest {

    private lateinit var testGame: TestGame

    @Before
    fun setUp() {
        testGame = TestGame()
        Fonts.fontImplementation = FontDesktop()
        ImageGetter.setNewRuleset(testGame.ruleset) // Requires GdxTestRunner
        TileSetCache.loadTileSetConfigs()
    }

    /** Adds an image named [name] to the loaded images, reusing the drawable of [existing] */
    @Suppress("UNCHECKED_CAST")
    private fun addImage(name: String, existing: String) {
        val field = ImageGetter::class.java.getDeclaredField("textureRegionDrawables")
        field.isAccessible = true
        val drawables = field.get(ImageGetter) as HashMap<String, TextureRegionDrawable>
        drawables[name] = drawables[existing]!!
    }

    @Test
    fun buyingATileShowsTheOwnersTileStyle() {
        testGame.makeHexagonalMap(3)
        val civ = testGame.addCiv()
        val city = testGame.addCity(civ, testGame.getTile(HexCoord.Zero))
        val tile = testGame.getTile(HexCoord(2, 0))
        Assert.assertNull(tile.getOwner())

        val gameView = GameView(testGame.gameInfo, civ)
        val tileGroup = TileGroup(gameView.tileMapView.getTile(tile), TileSetStrings())
        tileGroup.update(gameView.civView)
        val unownedImages = tileGroup.layerTerrain.tileBaseImages.map { it.name }
        Assert.assertTrue(unownedImages.isNotEmpty())
        // Without the numbered variant suffix, like "Grassland2"
        val baseLocations = unownedImages.map { it.trimEnd { char -> char.isDigit() } }

        // A tileset image for this terrain in the owner's style, like the civ styled tiles of a mod
        val style = civ.nation.getStyleOrCivName()
        for ((base, name) in baseLocations.zip(unownedImages)) addImage("$base-$style", name)

        city.expansion.takeOwnership(tile)
        tileGroup.update(gameView.civView)

        Assert.assertEquals(baseLocations.map { "$it-$style" }, tileGroup.layerTerrain.tileBaseImages.map { it.name })
    }
}
