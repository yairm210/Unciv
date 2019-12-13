//  Taken from https://github.com/TomGrill/gdx-testing

package de.tomgrill.gdxtesting.examples;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.unciv.models.ruleset.Nation;
import com.unciv.models.ruleset.Ruleset;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import de.tomgrill.gdxtesting.GdxTestRunner;

import static org.junit.Assert.assertTrue;

// DO NOT attempt to Kotlinize until you're sure that running gradle tests:test actually checks stuff!

@RunWith(GdxTestRunner.class)
public class TranslationTests {
	
	Ruleset ruleSet = new Ruleset(true);

	@Test
	public void translationsLoad() {
		assertTrue("This test will only pass there are translations",
				ruleSet.getTranslations().size() > 0);
	}

	@Test
	public void allUnitsHaveTranslation() {
		Boolean allUnitsHaveTranslation = allStringAreTranslated(ruleSet.getUnits().keySet());
		assertTrue("This test will only pass when there is a translation for all units",
					allUnitsHaveTranslation);
	}

	@Test
	public void allBuildingsHaveTranslation() {
		Boolean allBuildingsHaveTranslation = allStringAreTranslated(ruleSet.getBuildings().keySet());
		assertTrue("This test will only pass when there is a translation for all buildings",
				allBuildingsHaveTranslation);
	}

	@Test
	public void allTerrainsHaveTranslation() {
		Set<String> strings = ruleSet.getTerrains().keySet();
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue("This test will only pass when there is a translation for all buildings",
				allStringsHaveTranslation);
	}

	@Test
	public void allImprovementsHaveTranslation() {
		Set<String> strings = ruleSet.getTileImprovements().keySet();
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue("This test will only pass when there is a translation for all improvements",
				allStringsHaveTranslation);
	}

	@Test
	public void allTechnologiesHaveTranslation() {
		Set<String> strings = ruleSet.getTechnologies().keySet();
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue("This test will only pass when there is a translation for all technologies",
				allStringsHaveTranslation);
	}

	@Test
	public void allPromotionsHaveTranslation() {
		Set<String> strings = ruleSet.getUnitPromotions().keySet();
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue("This test will only pass when there is a translation for all promotions",
				allStringsHaveTranslation);
	}

	private Boolean allStringAreTranslated(Set<String> strings) {
		boolean allBuildingsHaveTranslation = true;
		for (String unitName : strings) {
			if (!ruleSet.getTranslations().containsKey(unitName)) {
				allBuildingsHaveTranslation = false;
				System.out.println(unitName);
			}
		}
		return allBuildingsHaveTranslation;
	}

	@Test
	public void allTranslatedNationsFilesAreSerializable() {
		for(FileHandle file : Gdx.files.internal("jsons/Nations").list()){
			ruleSet.getFromJson(new Array<Nation>().getClass(), file.path());
		}
		assertTrue("This test will only pass when there is a translation for all promotions",
				true);
	}

}