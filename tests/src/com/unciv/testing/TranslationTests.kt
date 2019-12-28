//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.Translations
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(GdxTestRunner::class)
class TranslationTests {
    var translations = Translations()
    var ruleSet = Ruleset(true)

    @Before
    fun loadTranslations() {
        translations.readAllLanguagesTranslation()
    }

    @Test
    fun translationsLoad() {
        Assert.assertTrue("This test will only pass there are translations",
                translations.size > 0)
    }

    @Test
    fun allUnitsHaveTranslation() {
        val allUnitsHaveTranslation = allStringAreTranslated(ruleSet.units.keys)
        Assert.assertTrue("This test will only pass when there is a translation for all units",
                allUnitsHaveTranslation)
    }

    @Test
    fun allUnitUniquesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (unit in ruleSet.units.values) for (unique in unit.uniques) if (!unique.startsWith("Bonus")
                && !unique.startsWith("Penalty")
                && !unique.contains("[")) // templates
            strings.add(unique)
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue(allStringsHaveTranslation)
    }

    @Test
    fun allBuildingsHaveTranslation() {
        val allBuildingsHaveTranslation = allStringAreTranslated(ruleSet.buildings.keys)
        Assert.assertTrue("This test will only pass when there is a translation for all buildings",
                allBuildingsHaveTranslation)
    }

    @Test
    fun allBuildingUniquesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (building in ruleSet.buildings.values) {
            strings.addAll(building.uniques)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue(allStringsHaveTranslation)
    }

    @Test
    fun allBuildingQuotesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (building in ruleSet.buildings.values) {
            if (building.quote == "") continue
            strings.add(building.quote)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue(allStringsHaveTranslation)
    }

    @Test
    fun allTerrainsHaveTranslation() {
        val strings: Set<String> = ruleSet.terrains.keys
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all buildings",
                allStringsHaveTranslation)
    }

    @Test
    fun allTerrainUniquesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (terrain in ruleSet.terrains.values) {
            strings.addAll(terrain.uniques)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all terrain uniques", allStringsHaveTranslation)
    }

    @Test
    fun allImprovementsHaveTranslation() {
        val strings: Set<String> = ruleSet.tileImprovements.keys
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all improvements",
                allStringsHaveTranslation)
    }

    @Test
    fun allImprovementUniquesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (improvement in ruleSet.tileImprovements.values) {
            strings.addAll(improvement.uniques)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue(allStringsHaveTranslation)
    }

    @Test
    fun allTechnologiesHaveTranslation() {
        val strings: Set<String> = ruleSet.technologies.keys
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all technologies",
                allStringsHaveTranslation)
    }

    @Test
    fun allTechnologiesQuotesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (tech in ruleSet.technologies.values) {
            strings.add(tech.quote)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all technologies",
                allStringsHaveTranslation)
    }

    @Test
    fun allPromotionsHaveTranslation() {
        val strings: Set<String> = ruleSet.unitPromotions.keys
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all promotions",
                allStringsHaveTranslation)
    }

    private fun allStringAreTranslated(strings: Set<String>): Boolean {
        var allBuildingsHaveTranslation = true
        for (unitName in strings) {
            if (!translations.containsKey(unitName)) {
                allBuildingsHaveTranslation = false
                println(unitName)
            }
        }
        return allBuildingsHaveTranslation
    }

    @Test
    fun allTranslatedNationsFilesAreSerializable() {
        for (file in Gdx.files.internal("jsons/Nations").list()) {
            ruleSet.getFromJson(Array<Nation>().javaClass, file.path())
        }
        Assert.assertTrue("This test will only pass when there is a translation for all promotions",
                true)
    }

    /** For every translatable string find its placeholders and check if all translations have them */
    @Test
    fun allTranslationsHaveCorrectPlaceholders() {
        val placeholderPattern = """\[[^]]*]""".toRegex()
        var allTranslationsHaveCorrectPlaceholders = true
        val languages = translations.getLanguages()
        for (key in translations.keys) {
            val placeholders = placeholderPattern.findAll(key).map { it.value }.toList()
            for (language in languages) {
                placeholders.forEach { placeholder ->
                    if(!translations.get(key, language).contains(placeholder)) {
                        allTranslationsHaveCorrectPlaceholders = false
                        println("Placeholder `$placeholder` not found in `$language` for key `$key`")
                    }
                }

            }
        }
        Assert.assertTrue(
            "This test will only pass when all translations' placeholders match those of the key",
            allTranslationsHaveCorrectPlaceholders
        )
    }

}