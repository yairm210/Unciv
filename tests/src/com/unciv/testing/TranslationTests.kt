//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.Translations
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(GdxTestRunner::class)
class TranslationTests {
    private var translations = Translations()
    private var ruleset = Ruleset()

    @Before
    fun loadTranslations() {
        translations.readAllLanguagesTranslation()
        RulesetCache.loadRulesets()
        ruleset = RulesetCache.getBaseRuleset()
    }

    @Test
    fun translationsLoad() {
        Assert.assertTrue("This test will only pass there are translations",
                translations.size > 0)
    }

    @Test
    fun allUnitActionsHaveTranslation() {
        val actions: MutableSet<String> = HashSet()
        for (action in UnitActionType.values()) {
            if (action == UnitActionType.Upgrade)
                actions.add("Upgrade to [unitType] ([goldCost] gold)")
            else
                actions.add(action.value)
        }
        val allUnitActionsHaveTranslation = allStringAreTranslated(actions)
        Assert.assertTrue("This test will only pass when there is a translation for all unit actions",
                allUnitActionsHaveTranslation)
    }

    @Test
    fun allTerrainsHaveTranslation() {
        val strings: Set<String> = ruleset.terrains.keys
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all buildings",
                allStringsHaveTranslation)
    }

    @Test
    fun allTerrainUniquesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (terrain in ruleset.terrains.values) {
            strings.addAll(terrain.uniques)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all terrain uniques",
                allStringsHaveTranslation)
    }

    @Test
    fun allImprovementsHaveTranslation() {
        val strings: Set<String> = ruleset.tileImprovements.keys
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all improvements",
                allStringsHaveTranslation)
    }

    @Test
    fun allImprovementUniquesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (improvement in ruleset.tileImprovements.values) {
            strings.addAll(improvement.uniques)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all improvements uniques",
                allStringsHaveTranslation)
    }

    @Test
    fun allTechnologiesHaveTranslation() {
        val strings: Set<String> = ruleset.technologies.keys
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all technologies",
                allStringsHaveTranslation)
    }

    @Test
    fun allTechnologiesQuotesHaveTranslation() {
        val strings: MutableSet<String> = HashSet()
        for (tech in ruleset.technologies.values) {
            strings.add(tech.quote)
        }
        val allStringsHaveTranslation = allStringAreTranslated(strings)
        Assert.assertTrue("This test will only pass when there is a translation for all technologies quotes",
                allStringsHaveTranslation)
    }


    private fun allStringAreTranslated(strings: Set<String>): Boolean {
        var allStringsHaveTranslation = true
        for (key in strings) {
            if (!translations.containsKey(key)) {
                allStringsHaveTranslation = false
                println(key)
            }
        }
        return allStringsHaveTranslation
    }

    @Test
    fun translationsFromJSONsCanBeGenerated() {
        // it triggers generation of the translation's strings
        val stringsSize = TranslationFileWriter.getGeneratedStringsSize()

        Assert.assertTrue("This test will only pass when all .json files are serializable",
                stringsSize > 0)
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
                for (placeholder in placeholders) {
                    if (!translations.get(key, language).contains(placeholder)) {
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

    @Test
    fun allTranslationsEndWithASpace() {
        val templateLines = Gdx.files.internal(TranslationFileWriter.templateFileLocation).reader().readLines()
        var failed = false
        for (line in templateLines) {
            if (line.endsWith(" =")) {
                println("$line ends without a space at the end")
                failed=true
            }
        }
        Assert.assertFalse(failed)
    }
}