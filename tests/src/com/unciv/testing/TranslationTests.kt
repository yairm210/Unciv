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

    private fun allStringAreTranslated(strings: Set<String>): Boolean {
        val squareBraceRegex = Regex("""\[([^]]*)]""")
        var allStringsHaveTranslation = true
        for (entry in strings) {
            val key = if(entry.contains('[')) entry.replace(squareBraceRegex,"[]") else entry
            if (!translations.containsKey(key)) {
                allStringsHaveTranslation = false
                println(entry)
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
            val translationEntry = translations[key]!!.entry
            val placeholders = placeholderPattern.findAll(translationEntry).map { it.value }.toList()
            for (language in languages) {
                for (placeholder in placeholders) {
                    val output = translations.getText(translationEntry, language)
                    if (!output.contains(placeholder)) {
                        allTranslationsHaveCorrectPlaceholders = false
                        println("Placeholder `$placeholder` not found in `$language` for entry `$translationEntry`")
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
    fun allPlaceholderKeysMatchEntry() {
        val squareBraceRegex = Regex("""\[([^]]*)]""")
        var allPlaceholderKeysMatchEntry = true
        for (key in translations.keys) {
            if ( !key.contains('[') ) continue
            val translationEntry = translations[key]!!.entry
            val keyFromEntry = translationEntry.replace(squareBraceRegex,"[]")
            if (key != keyFromEntry) {
                allPlaceholderKeysMatchEntry = false
                println("Entry $translationEntry found under bad key $key")
                break
            }
        }
        Assert.assertTrue(
                "This test will only pass when all placeholder translations'keys match their entry with shortened placeholders",
                allPlaceholderKeysMatchEntry
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
