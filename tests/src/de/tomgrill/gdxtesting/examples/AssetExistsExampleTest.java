//  Taken from https://github.com/TomGrill/gdx-testing

package de.tomgrill.gdxtesting.examples;

import com.badlogic.gdx.Gdx;
import com.unciv.models.gamebasics.GameBasics;

import org.junit.Test;
import org.junit.runner.RunWith;

import de.tomgrill.gdxtesting.GdxTestRunner;

import static org.junit.Assert.assertTrue;

@RunWith(GdxTestRunner.class)
public class AssetExistsExampleTest {

	@Test
	public void gamePngExists() {
		assertTrue("This test will only pass when the game.png exists",
				Gdx.files.local("game.png").exists());
	}

	@Test
	public void gameBasicsLoad() {
		assertTrue("This test will only pass when the game.png exists",
				GameBasics.INSTANCE.getBuildings().size() > 0);
	}

}
