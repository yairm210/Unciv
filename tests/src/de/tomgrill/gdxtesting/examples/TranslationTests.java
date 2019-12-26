//  Taken from https://github.com/TomGrill/gdx-testing

package de.tomgrill.gdxtesting.examples;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.unciv.models.ruleset.Building;
import com.unciv.models.ruleset.Nation;
import com.unciv.models.ruleset.Ruleset;
import com.unciv.models.ruleset.tech.Technology;
import com.unciv.models.ruleset.tile.TileImprovement;
import com.unciv.models.ruleset.unit.BaseUnit;
import com.unciv.models.translations.Translations;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tomgrill.gdxtesting.GdxTestRunner;

import static org.junit.Assert.assertTrue;

// DO NOT attempt to Kotlinize until you're sure that running gradle tests:test actually checks stuff!

@RunWith(GdxTestRunner.class)
public class TranslationTests {
	
	Translations translations = new Translations();
	Ruleset ruleSet = new Ruleset(true);
	@Before
	public void loadTranslations(){
		translations.readAllLanguagesTranslation();
	}

	@Test
	public void translationsLoad() {
		assertTrue("This test will only pass there are translations",
				translations.size() > 0);
	}

	@Test
	public void allUnitsHaveTranslation() {
		Boolean allUnitsHaveTranslation = allStringAreTranslated(ruleSet.getUnits().keySet());
		assertTrue("This test will only pass when there is a translation for all units",
					allUnitsHaveTranslation);
	}


	@Test
	public void allUnitUniquesHaveTranslation() {
		Set<String> strings = new HashSet<String>();
		for (BaseUnit unit : ruleSet.getUnits().values())
			for (String unique : unit.getUniques())
				if (!unique.startsWith("Bonus")
						&& !unique.startsWith("Penalty")
						&& !unique.contains("[")) // templates
					strings.add(unique);

		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue(allStringsHaveTranslation);
	}


	@Test
	public void allBuildingsHaveTranslation() {
		Boolean allBuildingsHaveTranslation = allStringAreTranslated(ruleSet.getBuildings().keySet());
		assertTrue("This test will only pass when there is a translation for all buildings",
				allBuildingsHaveTranslation);
	}

	@Test
	public void allBuildingUniquesHaveTranslation() {
		Set<String> strings = new HashSet<String>();
		for(Building building: ruleSet.getBuildings().values()){
			strings.addAll(building.getUniques());
		}
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue(allStringsHaveTranslation);
	}


	@Test
	public void allBuildingQuotesHaveTranslation() {
		Set<String> strings = new HashSet<String>();
		for(Building building: ruleSet.getBuildings().values()){
			if(building.getQuote().equals("")) continue;
			strings.add(building.getQuote());
		}
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue(allStringsHaveTranslation);
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
	public void allImprovementUniquesHaveTranslation() {
		Set<String> strings = new HashSet<String>();
		for(TileImprovement improvement: ruleSet.getTileImprovements().values()){
			strings.addAll(improvement.getUniques());
		}
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue(allStringsHaveTranslation);
	}


	@Test
	public void allTechnologiesHaveTranslation() {
		Set<String> strings = ruleSet.getTechnologies().keySet();
		Boolean allStringsHaveTranslation = allStringAreTranslated(strings);
		assertTrue("This test will only pass when there is a translation for all technologies",
				allStringsHaveTranslation);
	}

	@Test
	public void allTechnologiesQuotesHaveTranslation() {
		Set<String> strings = new HashSet<String>();
		for(Technology tech : ruleSet.getTechnologies().values()){
			strings.add(tech.getQuote());
		}
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
			if (!translations.containsKey(unitName)) {
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

	/** For every translatable string find its placeholders and check if all translations have them */
	@Test
	public void allTranslationsHaveCorrectPlaceholders() {
	    boolean allTranslationsHaveCorrectPlaceholders = true;
		Pattern placeholderPattern = Pattern.compile("\\[[^]]*\\]");
		List<String> languages = translations.getLanguages();
		for(String key: translations.keySet()) {
			Matcher placeholdersMatcher = placeholderPattern.matcher(key);
			while (placeholdersMatcher.find()) {
				String placeholder = placeholdersMatcher.group();
				for(String language : languages) {
					if (!translations.get(key, language).contains(placeholder)) {
					    allTranslationsHaveCorrectPlaceholders = false;
					    System.out.println(String.format("Placeholder `%s` not found in `%s` language for key `%s`", placeholder, language, key));
                    }
				}
			}
		}
		assertTrue(allTranslationsHaveCorrectPlaceholders);
	}

}