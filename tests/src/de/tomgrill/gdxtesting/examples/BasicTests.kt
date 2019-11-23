//  Taken from https://github.com/TomGrill/gdx-testing

package de.tomgrill.gdxtesting.examples

import com.badlogic.gdx.Gdx
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.mapeditor.MapEditorScreen
import de.tomgrill.gdxtesting.GdxTestRunner
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class BasicTests {

    @Test
    fun gamePngExists() {
        assertTrue("This test will only pass when the game.png exists",
                Gdx.files.local("game.png").exists())
    }

    @Test
    fun gameBasicsLoad() {
        assertTrue("This test will only pass when the GameBasics can initialize, and there are buildings",
                GameBasics.Buildings.size > 0)
    }

    @Test
    fun canOpenMapEditorScreen() {
        UnCivGame.Current.setScreen(MapEditorScreen(UnCivGame.Current.gameInfo.tileMap))
        assertTrue("This test will only pass when we can open the map editor screen",
                GameBasics.Buildings.size > 0)
    }

}
