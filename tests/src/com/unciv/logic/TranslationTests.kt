//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.UnitActionType
import com.unciv.ui.screens.worldscreen.unit.actions.UiUnitActionType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.MAX_CONDITIONAL_NESTING
import com.unciv.models.translations.TranslationCache
import com.unciv.models.translations.TranslationEntry
import com.unciv.models.translations.TranslationFileReader
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.Translations
import com.unciv.models.translations.Translations.Companion.conditionalOrderingKey
import com.unciv.models.translations.Translations.Companion.conditionalPlacementKey
import com.unciv.models.translations.Translations.Companion.defaultConditionalOrderingString
import com.unciv.models.translations.Translations.Companion.shouldCapitalizeKey
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getModifiers
import com.unciv.models.translations.getPartsInsideCurlies
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.models.translations.tr
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.GitInfo
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.testing.runTestParcours
import com.unciv.ui.components.fonts.DiacriticSupport
import com.unciv.ui.components.fonts.FontRulesetIcons
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.utils.Log
import java.io.Writer
import java.security.MessageDigest
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

@RunWith(BaseTestRunner::class)
class TranslationTests {
    //region !Helpers

    /** Translations to test with - all languages are loaded by [loadTranslations] with diacritic support off */
    private lateinit var translations: Translations

    /** Initialize [translations] with all languages for tests running on a complete set */
    private fun loadTranslations() {
        translations = Translations()
        translations.readAllLanguagesTranslation()
    }

    /** Initialize [RulesetCache] when needed */
    private fun setupRuleset() {
        RulesetCache.loadRulesets(noMods = true)
    }

    /** Set up empty [UncivGame] including empty translations, needed to test [String.tr] */
    private fun setupUncivGame() {
        // Needed for .tr() to work
        UncivGame.Current = UncivGame()
        UncivGame.Current.settings = GameSettings()
    }

    /** Copy all entries from [translations] to current UncivGame, so [String.tr] can be run for all languages.
     *  Requires calling [loadTranslations] and [setupUncivGame] first. */
    private fun copyTranslationsToUncivGame() {
        for ((key, value) in translations)
            UncivGame.Current.translations[key] = value
    }

    /** Add a translation entry to the current UncivGame, not [translations] - to use with [setupUncivGame], not [loadTranslations] */
    private fun addTranslation(original: String, result: String) =
        addTranslation(original.getPlaceholderText(), original, result)

    /** Add a translation entry to the current UncivGame, not [translations] - to use with [setupUncivGame], not [loadTranslations]
     *  This overload allows specifying a [key] directly instead of deriving it from [original]. */
    private fun addTranslation(key: String, original: String, result: String) {
        UncivGame.Current.translations[key] = TranslationEntry(original)
            .apply { this[Constants.english] = result }
    }
    //endregion

    @Test
    fun translationsLoad() {
        loadTranslations()
        Assert.assertTrue("This test will only pass if there are translations",
            translations.isNotEmpty()
        )
    }


    @Test
    fun allUnitActionsHaveTemplate() {
        fun String.getTemplateKey() = getPlaceholderText()
        fun String.getInnerTemplate() = getPartsInsideCurlies().firstOrNull() ?: this

        val allKeys = TranslationFileReader.readTemplates { lines ->
            lines.filterNot { it.isEmpty() || it.startsWith('#') || !it.endsWith(" = ") }
                .map { it.removeSuffix(" = ").getTemplateKey() }
                .toMutableSet()
        } ?: return

        // Include auto-templates for keyboard binding UI
        KeyboardBinding.entries.mapTo(allKeys) { it.label }

        var failures = 0
        val actionValues = UnitActionType.entries.map { "UnitActionType.$it" to it.value } +
            UiUnitActionType.entries.filter { it.type == null }.map { "UiUnitActionType.$it" to it.value }
        for ((action, value) in actionValues) {
            if (value.isEmpty()) continue
            val key = value.getInnerTemplate().getTemplateKey()
            if (key in allKeys) continue
            failures++
            println("""$action (value "$value") is missing its translation template.""")
        }
        Assert.assertEquals("This test will only pass when there is a template for all unit actions", 0, failures)
    }

    @Test
    fun translationsFromJSONsCanBeGenerated() {
        // Triggers generation of the translation's strings
        // Will output "Translation writer took...", which is suppressed unless you use the @RedirectOutput(RedirectPolicy.Show) annotation
        setupRuleset()
        val stringsSize = TranslationFileWriter.getGeneratedStringsSize()

        Assert.assertTrue("This test will only pass when all .json files are serializable",
                stringsSize > 0)
    }

    /** For every translatable string find its placeholders and check if all translations have them */
    @Test
    fun allTranslationsHaveCorrectPlaceholders() {
        loadTranslations()
        var allTranslationsHaveCorrectPlaceholders = true
        val languages = translations.getLanguages()

        for ((key, translation) in translations) {
            val translationEntry = translation.entry
            val placeholders = translationEntry.getPlaceholderParameters()
            for (language in languages) {
                val output = translations.getText(key, language)
                if (output == key) continue // the language doesn't have the required translation, so we got back the key
                for (placeholder in placeholders) {
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

    /** For every translatable string and all translations check if all translated placeholders are present in the key */
    @Test
    fun allTranslationsHaveNoExtraPlaceholders() {
        loadTranslations()
        var allTranslationsHaveNoExtraPlaceholders = true
        val languages = translations.getLanguages()

        for ((key, translation) in translations) {
            val translationEntry = translation.entry
            val placeholders = translationEntry.getPlaceholderParameters().toSet()
            for (language in languages) {
                val output = translations.getText(key, language)
                if (output == key) continue // the language doesn't have the required translation, so we got back the key
                val translatedPlaceholders = output.getPlaceholderParameters().toSet()
                val extras = translatedPlaceholders - placeholders
                for (placeholder in extras) {
                    allTranslationsHaveNoExtraPlaceholders = false
                    println("Extra placeholder `$placeholder` in `$language` for entry `$translationEntry`")
                }
            }
        }
        Assert.assertTrue(
            "This test will only pass when all placeholders in all translations are present in the key",
            allTranslationsHaveNoExtraPlaceholders
        )
    }

    @Test
    fun allPlaceholderKeysMatchEntry() {
        loadTranslations()
        var allPlaceholderKeysMatchEntry = true

        for ((key, translation) in translations) {
            if (!key.contains('[') || key.contains('<')) continue
            val translationEntry = translation.entry
            val keyFromEntry = translationEntry.getPlaceholderText()
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
    fun allTemplatesHaveUniqueKeys() {
        val keyToEntryMap = mutableMapOf<String, Pair<String, Int>>()
        var failures = 0
        TranslationFileReader.readTemplates { templateLines ->
            for ((index, template) in templateLines.withIndex()) {
                if (template.isEmpty() || template.startsWith('#')) continue
                val key = template.getPlaceholderText()
                if (key in keyToEntryMap) {
                    failures++
                    val (otherEntry, otherLine) = keyToEntryMap[key]!!
                    println("""Template "$template" (template.properties:${index + 1}) has the same key as "$otherEntry" (template.properties:$otherLine).""")
                } else {
                    keyToEntryMap[key] = template to index + 1
                }
            }
        }
        Assert.assertEquals("The template file should have no duplicate keys (keys are the template without placeholder names)", 0, failures)
    }

    @Test
    fun allTemplatesHaveUniquePlaceholders() {
        // check that the templates have unique placeholders (the translation entries are checked below)
        var noTwoPlaceholdersAreTheSame = true
        TranslationFileReader.readTemplates { templateLines ->
            for (template in templateLines) {
                if (template.isEmpty() || template.startsWith('#')) continue
                val placeholders = template.getPlaceholderParameters()

                for (placeholder in placeholders)
                    if (placeholders.count { it == placeholder } > 1) {
                        noTwoPlaceholdersAreTheSame = false
                        println("Template key $template has the parameter $placeholder more than once")
                        break
                    }
            }
        }
        Assert.assertTrue(
            "This test will only pass when no translation template keys have the same parameter twice",
            noTwoPlaceholdersAreTheSame)
    }

    @Test
    fun noTwoPlaceholdersAreTheSame() {
        loadTranslations()
        var noTwoPlaceholdersAreTheSame = true

        for (translationEntry in translations.values) {
            val placeholders = translationEntry.entry.getPlaceholderParameters()

            for (placeholder in placeholders)
                if (placeholders.count { it == placeholder } > 1) {
                    noTwoPlaceholdersAreTheSame = false
                    println("Entry $translationEntry has the parameter $placeholder more than once")
                    break
                }
        }
        Assert.assertTrue(
                "This test will only pass when no translation keys have the same parameter twice",
                noTwoPlaceholdersAreTheSame
        )
    }

    @Test
    fun allTemplatesEndWithASpace() {
        var failed = false
        TranslationFileReader.readTemplates { templateLines ->
            for (line in templateLines) {
                if (line.isEmpty() || line.startsWith('#')) continue
                if (line.endsWith(" =")) {
                    println("$line ends without a space at the end")
                    failed = true
                } else if (!line.contains(" = ")) {
                    println("$line is missing the equals sign")
                    failed = true
                } else if (!line.endsWith(" = ")) {
                    println("$line has extra characters after the equals sign and space")
                    failed = true
                }
            }
        }
        Assert.assertFalse(failed)
    }

    @Test
    fun allStringsTranslate() {
        setupUncivGame()
        loadTranslations()
        copyTranslationsToUncivGame()

        var allWordsTranslatedCorrectly = true
        for (translationEntry in translations.values) {
            for ((language, _) in translationEntry) {
                UncivGame.Current.settings.language = language
                try {
                    translationEntry.entry.tr()
                } catch (ex: Exception) {
                    allWordsTranslatedCorrectly = false
                    Log.error("Crashed when translating ${translationEntry.entry} to $language", ex)
                }
            }
        }
        Assert.assertTrue(
            "This test will only pass when all phrases properly translate to their language",
            allWordsTranslatedCorrectly
        )
    }

    @Test
    fun wordBoundaryTranslationIsFormattedCorrectly() {
        loadTranslations()
        val translationEntry = translations["\" \""]!!

        var allTranslationsCheckedOut = true
        for ((language, translation) in translationEntry) {
            if (!translation.startsWith("\"")
                || !translation.endsWith("\"")
                || translation.count { it == '\"' } != 2
            ) {
                allTranslationsCheckedOut = false
                println("Translation of the word boundary in $language was incorrectly formatted")
            }
        }

        Assert.assertTrue(
            "This test will only pass when the word boundrary translation succeeds",
            allTranslationsCheckedOut
        )
    }


    @Test
    fun translationParameterExtractionForNestedBracesWorks() {
        val superNestedString = "The brother of [[my [best friend]] and [[America]'s greatest [Dad]]]"
        runTestParcours("", { input: String -> input.getPlaceholderParameters() },
            "The city of [New [York]]", listOf("New [York]"),
            // Closing braces without a matching opening brace - 'level 0' - are ignored
            "The city of [New [York]]]", listOf("New [York]"),
            // Opening braces without a matching closing brace mean that the term is never 'closed' so there are no parameters
            "The city of [[New [York]", emptyList<String>(),
            // Supernesting
            superNestedString, listOf("[my [best friend]] and [[America]'s greatest [Dad]]"),
            superNestedString.getPlaceholderParameters()[0], listOf("my [best friend]", "[America]'s greatest [Dad]"),
            // Don't find placehoders in conditionals
            "Foo [a] <when [b]>", listOf("a")
        )

        setupUncivGame()

        addTranslation("The brother of [person]", "The sibling of [person]")
        Assert.assertEquals("The sibling of bob", "The brother of [bob]".tr())


        addTranslation("[a] and [b]", "[a] and indeed [b]")
        addTranslation("my [whatever]", "mine own [whatever]")
        addTranslation("[place]'s greatest [job]", "the greatest [job] in [place]")
        addTranslation("Dad", "Father")
        addTranslation("best friend", "closest ally")
        addTranslation("America", "The old British colonies")

        Assert.assertEquals(listOf("Dad","my [best friend]"),
            "[Dad] and [my [best friend]]".getPlaceholderParameters())
        Assert.assertEquals("Father and indeed mine own closest ally", "[Dad] and [my [best friend]]".tr())

        // Reminder: "The brother of [[my [best friend]] and [[America]'s greatest [Dad]]]"
        Assert.assertEquals("The sibling of mine own closest ally and indeed the greatest Father in The old British colonies",
            superNestedString.tr())
    }

    @Test
    fun fillPlaceHoldersWorks() {
        //setupUncivGame()
        Assert.assertEquals(
            "Gain [x] and [y]",
            "Gain [a] and [b]".fillPlaceholders("x", "y")
        )
        Assert.assertEquals(
            "[x]",
            "[a [b]]".fillPlaceholders("x")
        )
        Assert.assertThrows(
            IllegalArgumentException::class.java,
            { "Gain [a] and [b] or [c]?".fillPlaceholders("x", "y") }
        )
    }

    @Test
    fun isStatsRecognizesStatsIncludingStatCharacter() {
        setupUncivGame()

        val serializedStat = Stats(production = 1f).toStringForNotifications()
        val serializedStats = Stats(production = 1f, food = -2f).toStringForNotifications()
        Assert.assertTrue(Stats.isStats(serializedStat))
        Assert.assertTrue(Stats.isStats(serializedStats))
        Assert.assertEquals("+1 ${Stat.Production.character}Production", serializedStat.tr())
        Assert.assertEquals(
            "+1 ${Stat.Production.character}Production, -2 ${Stat.Food.character}Food",
            serializedStats.tr()
        )
        Assert.assertEquals("+1 Production", serializedStat.tr(hideStats = true))
        Assert.assertEquals("+1 Production, -2 Food", serializedStats.tr(hideStats = true))

        UncivGame.Current.translations["Production"] = TranslationEntry("Production").apply {
            this[LocaleCode.Bangla.name] = "উৎপাদন"
        }
        UncivGame.Current.translations["Food"] = TranslationEntry("Food").apply {
            this[LocaleCode.Bangla.name] = "খাদ্য"
        }
        UncivGame.Current.settings.language = LocaleCode.Bangla.name
        Assert.assertEquals("+১ ${Stat.Production.character}উৎপাদন", serializedStat.tr())
        Assert.assertEquals(
            "+১ ${Stat.Production.character}উৎপাদন, -২ ${Stat.Food.character}খাদ্য",
            serializedStats.tr()
        )
        Assert.assertEquals("+১ উৎপাদন", serializedStat.tr(hideStats = true))
        Assert.assertEquals("+১ উৎপাদন, -২ খাদ্য", serializedStats.tr(hideStats = true))
    }

    @Test
    fun iconSuppressionFlagsAffectOnlyTheirIconTypes() {
        setupUncivGame()
        val rulesetObjectName = "Test ruleset object"
        val rulesetObjectIcon = '\uE000'
        FontRulesetIcons.rulesetObjectNameToChar[rulesetObjectName] = rulesetObjectIcon
        try {
            Assert.assertEquals(Stat.Production.character + "Production", "Production".tr())
            Assert.assertEquals(Stat.Production.character + "Production", "Production".tr(hideIcons = true))
            Assert.assertEquals("Production", "Production".tr(hideStats = true))

            Assert.assertEquals(rulesetObjectIcon + rulesetObjectName, rulesetObjectName.tr())
            Assert.assertEquals(rulesetObjectName, rulesetObjectName.tr(hideIcons = true))
            Assert.assertEquals(rulesetObjectIcon + rulesetObjectName, rulesetObjectName.tr(hideStats = true))
        } finally {
            FontRulesetIcons.rulesetObjectNameToChar.remove(rulesetObjectName)
        }
    }

    @Test
    fun diacriticsSilentForEnglish() {
        loadTranslations()
        DiacriticSupport.reset()
        val english = translations.values
            .mapNotNull { entry ->
                entry["English"]?.let { entry.entry to it }
            }.toMap(HashMap())
        val diacriticSupport = DiacriticSupport(english)
        Assert.assertFalse(diacriticSupport.isEnabled())
    }

    @Test
    fun diacriticsWorkForBangla() {
        //todo This test was designed before the Bangla language was merged, and uses its own data.
        //     With the actual Bangla, we could use [loadTranslations] to load that, so there could be an additional, or a different test on the live one...

        // Here's a helper to _generate_ the expected from the listOf:
        // https://play.kotlinlang.org/#eyJ2ZXJzaW9uIjoiMi4wLjAiLCJwbGF0Zm9ybSI6ImphdmEiLCJhcmdzIjoiIiwibm9uZU1hcmtlcnMiOnRydWUsInRoZW1lIjoiaWRlYSIsImNvZGUiOiJwYWNrYWdlIHRvdWhpZHVycnJcblxuY29uc3QgdmFsIEJBTkdMQV9DSEFSU0VUX1NUQVJUID0gMHgwOTgwXG5jb25zdCB2YWwgQkFOR0xBX0NIQVJTRVRfRU5EID0gMHgwOWZmXG5cbnZhbCBCQU5HTEFfRElBQ1JJVElDUyA9IGxpc3RPZihcbiAgICAn4KaBJywgJ+CmgicsICfgpoMnLCAn4Ka8JyxcbiAgICAn4Ka+JywgJ+CmvycsICfgp4AnLCAn4KeBJyxcbiAgICAn4KeCJywgJ+CngycsICfgp4QnLCAn4KeHJyxcbiAgICAn4KeIJywgJ+CniycsICfgp4wnLCAn4KeNJyxcbiAgICAn4KeXJywgJ+CnoicsICfgp6MnLCAn4Ke+JyxcbilcblxuY29uc3QgdmFsIEJBTkdMQV9KT0lORVIgPSAn4KeNJ1xuXG5mdW4gaXNCYW5nbGFDaGFyKGNoOiBDaGFyKTogQm9vbGVhbiB7XG4gICAgcmV0dXJuIGNoLmNvZGUgPj0gQkFOR0xBX0NIQVJTRVRfU1RBUlQgJiYgY2guY29kZSA8PSBCQU5HTEFfQ0hBUlNFVF9FTkRcbn1cblxudmFsIGFsbFNlcXVlbmNlcyA9IG11dGFibGVTZXRPZjxTdHJpbmc+KClcblxuZnVuIG1haW4oKSB7XG4gICAgdmFsIGxpbmVzID0gbGlzdE9mKFxuICAgICAgICBcIuCmruCmvuCmqOCmmuCmv+CmpOCnjeCmsCDgprjgpq7gp43gpqrgpr7gpqbgppVcIiwgXCLgpqbgp4fgppbgp4HgpqhcIiwgXCLgpongp47gpqrgpqjgp43gpqgg4KaV4Kaw4KeB4KaoXCIsIFwi4KaG4KaC4Ka24Ka/4KaVXCIsIFwi4KaW4KeN4Kaw4Ka/4Ka34KeN4Kaf4Kaq4KeC4Kaw4KeN4KasXCIsIFwi4Ka44KaC4KaV4KeN4Ka34Ka/4Kaq4KeN4KakXCIsIFwi4Ka24KaV4KeN4Kak4Ka/XCIsIFwi4Ka34KeN4Kag4KeN4Kav4KeHXCJcbiAgICApXG5cbiAgICBsaW5lcy5mb3JFYWNoIHsgbGluZSAtPlxuICAgICAgICB2YWwgbGFzdFNlcXVlbmNlID0gU3RyaW5nQnVpbGRlcigpXG4gICAgICAgIGxpbmUuZm9yRWFjaCB7IGNoIC0+XG4gICAgICAgICAgICBpZiAoIWlzQmFuZ2xhQ2hhcihjaCkpIHtcbiAgICAgICAgICAgICAgICBpZiAobGFzdFNlcXVlbmNlLmlzTm90RW1wdHkoKSkge1xuICAgICAgICAgICAgICAgICAgICBhbGxTZXF1ZW5jZXMuYWRkKGxhc3RTZXF1ZW5jZS50b1N0cmluZygpKVxuICAgICAgICAgICAgICAgICAgICBsYXN0U2VxdWVuY2UuY2xlYXIoKVxuICAgICAgICAgICAgICAgIH1cbiAgICAgICAgICAgICAgICByZXR1cm5AZm9yRWFjaFxuICAgICAgICAgICAgfVxuXG4gICAgICAgICAgICBpZiAoQkFOR0xBX0RJQUNSSVRJQ1MuY29udGFpbnMoY2gpKSB7XG4gICAgICAgICAgICAgICAgbGFzdFNlcXVlbmNlLmFwcGVuZChjaClcbiAgICAgICAgICAgICAgICByZXR1cm5AZm9yRWFjaFxuICAgICAgICAgICAgfVxuXG4gICAgICAgICAgICBpZiAobGFzdFNlcXVlbmNlLmlzTm90RW1wdHkoKSAmJiBsYXN0U2VxdWVuY2UubGFzdCgpICE9IEJBTkdMQV9KT0lORVIpIHtcbiAgICAgICAgICAgICAgICBhbGxTZXF1ZW5jZXMuYWRkKGxhc3RTZXF1ZW5jZS50b1N0cmluZygpKVxuICAgICAgICAgICAgICAgIGxhc3RTZXF1ZW5jZS5jbGVhcigpXG4gICAgICAgICAgICB9XG4gICAgICAgICAgICBsYXN0U2VxdWVuY2UuYXBwZW5kKGNoKVxuICAgICAgICB9XG5cbiAgICAgICAgaWYgKGxhc3RTZXF1ZW5jZS5pc05vdEVtcHR5KCkpIHtcbiAgICAgICAgICAgIGFsbFNlcXVlbmNlcy5hZGQobGFzdFNlcXVlbmNlLnRvU3RyaW5nKCkpXG4gICAgICAgIH1cbiAgICB9XG5cbiAgICBwcmludGxuKGFsbFNlcXVlbmNlcy5maWx0ZXIgeyBpdC5sZW5ndGggPiAxIH0uam9pblRvU3RyaW5nKFwiXFxcIiwgXFxcIlwiLCBcInZhbCBleHBlY3RlZCA9IHNldE9mKFxcXCJcIiwgXCJcXFwiKVwiKSB7IGl0LmFzU2VxdWVuY2UoKS5qb2luVG9TdHJpbmcoKSB9KVxufSJ9

        DiacriticSupport.reset()
        val leftJoiningDiacritics = "ঁ ং ঃ ় া ি ী ু ূ ৃ ৄ ে ৈ ো ৌ ্ ৗ ৢ ৣ ৾".replace(" ", "")
        val leftAndRightJoiners = "্"
        val diacriticSupport = DiacriticSupport(true, Char(0x0980U)..Char(0x09FDU), leftJoiningDiacritics, "", leftAndRightJoiners)

        listOf(
            "মানচিত্র সম্পাদক", "দেখুন", "উৎপন্ন করুন", "আংশিক", "খ্রিষ্টপূর্ব", "সংক্ষিপ্ত", "শক্তি", "ষ্ঠ্যে"
        ).forEach { diacriticSupport.remapDiacritics(it) }
        val actual = diacriticSupport.getKnownCombinations()
        val expected = setOf(
                "ম, া", "চ, ি", "ত, ্, র", "ম, ্, প, া", "দ, ে", "খ, ু", "ন, ্, ন", "র, ু", "আ, ং", "শ, ি",
                "খ, ্, র, ি", "ষ, ্, ট", "প, ূ", "র, ্, ব", "স, ং", "ক, ্, ষ, ি", "প, ্, ত", "ক, ্, ত, ি",
                "ষ, ্, ঠ, ্, য, ে"
            )
            .map {
                it.split(", ").joinToString("")
            }.toSet()
        Assert.assertEquals(expected, actual)
    }


    @Test
    fun testNonBasePlaneUnicode() {
        // This tries how a translation of "Test👍" with diacritic support comes out: Should be 5 codepoints not 6 as the original string representation
        translations = Translations()
        translations.createTranslations("Test", hashMapOf("Test" to "Test\uD83D\uDC4D", "diacritics_support" to "true"))
        testRoundtrip("Test", "Test", "Test\uD83D\uDC4D") { translated ->
            val isOK = translated.startsWith("Test") && translated.length == 5 && translated.last() > DiacriticSupport.getCurrentFreeCode()
            Assert.assertTrue("Translation with one emoji should have exactly one fake alphabet codepoint", isOK)
        }
    }

    private fun testRoundtrip(language: String, term: String, input: String, additionalTest: ((String)->Unit)? = null) {
        setupUncivGame()
        copyTranslationsToUncivGame()
        UncivGame.Current.settings.language = language

        val translated = term.tr()
        if (translated == term) return // No translation present, can't test

        val output = translated.asIterable().joinToString("") { DiacriticSupport.getStringFor(it) }

        fun Char.hex() = "U+" + code.toString(16).padStart(4, '0')
        fun String.hex() = asIterable().joinToString(" ") { it.hex() }
        fun String.literalAndHex() = "\"$this\" = ${hex()}"
        val translatedHex = translated.asIterable().joinToString("; ") { it.hex() + " -> " + DiacriticSupport.getStringFor(it).literalAndHex() }
        println("Mapping '$term' in $language to fake alphabet and back:\n\tinput: ${input.literalAndHex()}\n\ttranslated: $translatedHex\n\toutput: ${output.literalAndHex()}")
        Assert.assertEquals(input, output)
        additionalTest?.invoke(translated)
    }

    @Test
    fun testNumberTr() {
        setupUncivGame()

        val testCases = arrayOf<Number>(1, -1, 0.123, -0.123)

        val expectedEnglishOutputs = arrayOf("1", "-1", "0.123", "-0.123")
        Assert.assertArrayEquals(
            "Number.tr()", expectedEnglishOutputs, testCases.map { it.tr() }.toTypedArray()
        )
        Assert.assertArrayEquals(
            "Number.tr(${LocaleCode.English.name})",
            expectedEnglishOutputs,
            testCases.map { it.tr(LocaleCode.English.name) }.toTypedArray()
        )

        val expectedBanglaOutputs = arrayOf("১", "-১", "০.১২৩", "-০.১২৩")
        Assert.assertArrayEquals(
            "Number.tr(${LocaleCode.Bangla.name})",
            expectedBanglaOutputs,
            testCases.map { it.tr(LocaleCode.Bangla.name) }.toTypedArray()
        )
    }

    @Test
    fun testStringsWithNumbers() {
        setupUncivGame()

        val tests = arrayOf("1", "+1", "-1", "1.0", "+1.0", "-1.0", "0%", "1/2", "(3/4)", "Population 1234")
        UncivGame.Current.translations["Population 1234"] = TranslationEntry("Population 1234").apply {
            this[LocaleCode.English.name] = "Citizens 1234"
            this[LocaleCode.Bangla.name] = "নাগরিক 1234"
        }

        UncivGame.Current.settings.language = LocaleCode.English.name
        Assert.assertArrayEquals(
            "English",
            arrayOf("1", "+1", "-1", "1.0", "+1.0", "-1.0", "0%", "1/2", "(3/4)", "Citizens 1,234"),
            tests.map { it.tr() }.toTypedArray()
        )

        UncivGame.Current.settings.language = LocaleCode.Bangla.name
        Assert.assertArrayEquals(
            "Bangla",
            arrayOf("১", "+১", "-১", "১.০", "+১.০", "-১.০", "০%", "১/২", "(৩/৪)", "নাগরিক ১,২৩৪"),
            tests.map { it.tr() }.toTypedArray()
        )
    }

    @Test
    fun testTranslateConditionals() {
        fun setReordered() {
            addTranslation(conditionalOrderingKey, "<when attacking> <for [mapUnitFilter] units> <with a garrison>")
            TranslationCache.invalidateLanguage()
        }
        fun setBeforeAndCapitalized() {
            addTranslation(conditionalPlacementKey, "before")
            addTranslation(shouldCapitalizeKey, "true")
            TranslationCache.invalidateLanguage()
        }
        data class TestData(val label: String, val text: String, val expected: String, val setup: ()->Unit = {})
        val testData = listOf(
            // from #14092 - code once merged all conditionals with the same placeholder and thus "ate" the first one
            TestData("multiple conditionals of same UniqueType",
                "Only available <when number of [Owned [All Road] Tiles] is more than [6]> <when number of [[in all cities connected to capital] Cities] is more than [1]>",
                "Only available when number of Owned All Road Tiles is more than 6 when number of in all cities connected to capital Cities is more than 1"
            ),
            TestData("basic multi-conditional with default ordering",
                "[+1] Strength <when attacking> <vs cities> <with a garrison>",
                "+1 Strength with a garrison vs cities when attacking"
            ),
            TestData("non-default ordering",
                "[+1] Strength <when attacking> <vs cities> <with a garrison>",
                "+1 Strength when attacking with a garrison vs cities",
                ::setReordered
            ),
            TestData("unique after capitalized conditionals",
                "Only available <when attacking> <vs cities> <with a garrison>",
                "When attacking with a garrison vs cities only available",
                ::setBeforeAndCapitalized
            ),
        )

        setupUncivGame()
        var failures = 0
        for (test in testData) {
            test.setup()
            val actual = test.text.tr()
            if (actual == test.expected) continue
            failures++
            println("""Test "${test.label}" failed: Expected="${test.expected}", actual="$actual"""")
        }
        Assert.assertEquals(0, failures)
    }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    @Ignore("Don't run on github checks, comment out annotation for a local run")
    /** Tool to determine how many of the names that come from their own cultural context have translations differing from their original */
    fun listTranslatedNames() {
        loadTranslations()
        val languages = translations.getLanguages()
        setupRuleset()
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        val parcours = listOf(
            "leader names" to ruleset.nations.values.asSequence().map { it.leaderName },
            "city names" to ruleset.nations.values.asSequence().flatMap { it.cities },
            "spy names" to ruleset.nations.values.asSequence().flatMap { it.spyNames },
            "person names" to ruleset.unitNameGroups.values.asSequence().flatMap { it.unitNames },
        )

        fun checkGroup(name: String, seq: Sequence<String>): String {
            var total = 0
            println("--- $name ---")
            val keysToCheck = seq.toSet()
            for ((key, translation) in translations) {
                val translationEntry = translation.entry
                if (translationEntry !in keysToCheck) continue
                for (language in languages) {
                    val output = translations.getText(key, language)
                    if (output == key) continue // the language doesn't have the required translation, so we got back the key
                    println("\t\"$key\" is \"$output\" in $language")
                    total++
                }
            }
            return "$total (${total * 100f / keysToCheck.size / languages.size}%) $name were translated differently than their original over ${keysToCheck.size} candidates and ${languages.size} languages"
        }

        // Print summaries after details
        parcours.map { (name, sequence) -> checkGroup(name, sequence) }.toList().forEach { println(it) }
    }

    @Test
    fun allConditionalOrderingEntriesAreValid() {
        loadTranslations()
        val translationEntry = translations[conditionalOrderingKey]
            ?: TranslationEntry(conditionalOrderingKey)
        translationEntry[Constants.english] = defaultConditionalOrderingString

        val syntaxCheck = Regex("""^<[^<>]+(> <[^<>]+)*>$""")

        var failures = 0
        for ((language, translation) in translationEntry) {
            val prefix = "$conditionalOrderingKey in $language"
            if (!syntaxCheck.matches(translation)) {
                failures++
                println("$prefix is not a list of modifiers (one or many '<conditional>' separated by a single space)")
            }

            for ((_, list) in translation.getModifiers().groupBy { it.placeholderText }) {
                for ((index, unique) in list.withIndex()) {
                    if (unique.type == null) {
                        failures++
                        println("$prefix: \"${unique.text}\" is not a valid UniqueType")
                    }
                    if (index == 0) continue
                    failures++
                    println("$prefix: \"${unique.text}\" is a duplicate of \"${list[0].text}\"")
                }
            }
        }

        Assert.assertEquals(
            "This test will only pass when each of the conditionals in $conditionalOrderingKey is typed and unique within the list",
            0, failures
        )
    }

    @Test
    fun testBracketNestingAndUnmatchedBrackets() {
        setupUncivGame()
        addTranslation("a", "A")
        addTranslation("b", "B")
        addTranslation("c", "C")
        addTranslation("x", "X")
        addTranslation("y", "Y")
        addTranslation("z", "Z")
        addTranslation("base", "Base")
        addTranslation("base [arg]", "Phase [arg]")
        addTranslation("when [arg]", "When [arg]")
        addTranslation("a [arg]", "á [arg]")
        addTranslation("b [arg]", "µ [arg]")
        addTranslation("a b", "A+B")
        addTranslation("{a", "{à")
        addTranslation("a {b", "à {B")
        addTranslation("a ", "A+1")
        addTranslation("a  ", "A+2")
        addTranslation("Foobar", "FooBar")
        addTranslation("Foo bar", "Foo+Bar")
        addTranslation(shouldCapitalizeKey, "false")

        runTestParcours(
            "Translate inputs with [], {} or <> brackets:", { input: String -> input.tr() },
            // Normal placeholders
            "[a]", "A",
            "x [a] y [b] z", "x A y B z",
            "[a][b]", "AB",
            "[]", "",                          // empty placeholder
            "[a [b]]", "á B",                  // [] in []
            "[a [b [c]]]", "á µ C",
            "[a {b}]", "a B",                  // one {} level inside []
            // Unbalanced [] should stay literal
            "a [b", "a [b",
            "a b]", "a b]",
            "[a]]", "A]",                      // stray closer after a pair is ignored
            "[[a]", "[[a]",                    // opener never closes: no terms
            "[a][b", "A[b",                    // first pair translated, dangling opener literal
            // Curly {}
            "{a}", "A",
            "x {a b} y", "x A+B y",
            "{a}{b}", "AB",
            "{a [b]}", "á B",
            "{a}}", "A}",
            "a {b", "a {b",
            "a b}", "a b}",
            "{a} [b]", "A [b]",                // leftmost type wins; the other type is untouched
            "[a] {b}", "A {b}",
            "{a} and [b] {c}", "A and [b] C",
            "[a] and {b} [c]", "A and {b} C",
            // Conditionals
            "base <a>", "Base A",
            "base <a> <b>", "Base A B",
            "base <a><b>", "Base A B",         // lenient about the separator
            "base  <a>  ", "Base A",           // removeConditionals trims and collapses
            "base [x] <when [y]>", "Phase X When Y",
            "base {a b} <when {c}>", "base A+B when C",
            "base\u00A0<a>\u00A0", "Base A",
            "a  b   <x>", "a  b X",
            "a   b   <x>", "a   b X",
            " base <a>", " base A",
            // Edge cases processed by Conditionals branch but not following the rules
            "a < b", "a < b",
            "a > b", "a > b",
            "a > b <", "a > b <",
            "[a] < [b]", "A < B",
            "<[a b]", "<A+B",
            "<[a  b]", "<a  b",
            "x   <[a]", "x   <A",
            "base <a> <b> tail", "base tail A B",
            "Foo <a> bar", "Foo+Bar A",
            "Foo <a>bar", "Foo+Bar A",
            "Foo<a> bar", "FooBar A",
            "Foo<a>bar", "FooBar A",
            "{{a}", "{{a}",                     // Unmatched brace: Not matched at all
            "{a {b} c}", "a B c",               // nested {} now supported
            "[][[]", "[[]",                     // First pair removed, second counts as no match
            "[]][[]", "][[]",                   // First pair removed, second counts as no match
        )
    }

    @Test
    fun rulesetValidationCatchesMalformedConditionals() {
        val validator = UniqueValidator(Ruleset(), false)

        fun check(input: Pair<String, List<String>>): List<String> {
            val filters = input.second.toMutableList()
            val errors = validator.checkUnique(Unique(input.first), null).mapTo(mutableListOf()) { it.text }
            if (filters.isEmpty()) return errors
            // Remove matched errors
            val iter = errors.iterator()
            for (error in iter) {
                val filter = filters.firstOrNull { it in error } ?: continue
                filters.remove(filter)
                iter.remove()
            }
            errors.addAll(filters) // return unmatched filters too
            return errors
        }

        fun nested(levels: Int) = "[+1] Sight " + "<a".repeat(levels) + ">".repeat(levels)
        // "expected" is alwas an empty list so we can output lists of _unexpected_ validation errors
        runTestParcours("Validate Uniques with malformed conditionals", ::check,
            "[+1] Sight <when at war>" to emptyList<String>(), emptyList<String>(), // control: perfectly fine
            "[+1] Sight <a>" to listOf("unknown type"), emptyList<String>(), // control: error not reported by validateConditionals
            "[+1] Sight > 9 <when at war>" to listOf("mismatched conditional braces"), emptyList<String>(),
            "[+1] Sight <for [Civilian] units<hidden from users>>" to emptyList<String>(), emptyList<String>(),
            "[+1] Sight <when at war>>" to listOf("mismatched conditional braces"), emptyList<String>(),
            "[+1] Sight <when at war><" to listOf("mismatched conditional braces"), emptyList<String>(),
            "[+1] Sight <a<a<a>>>" to listOf("contains the conditional \"a<a<a>>\", which is of an unknown type"), emptyList<String>(),
            "[+1] Sight <a> <b> <c>" to listOf("unknown type", "unknown type", "unknown type"), emptyList<String>(), // control: errors not reported by validateConditionals
            "[+1] Sight <a> <b> <c>!" to listOf("text between or after conditionals"), emptyList<String>(),
            "[+1] Sight <a>, <b>, <c>" to listOf("text between or after conditionals"), emptyList<String>(),
            nested(MAX_CONDITIONAL_NESTING) to listOf("which is of an unknown type"), emptyList<String>(),
            nested(MAX_CONDITIONAL_NESTING + 1) to listOf("excessive nesting of conditionals", "which is of an unknown type"), emptyList<String>(),
        )
    }

    /**
     *  Tester helping ensure code changes do not influence translation output.
     *
     *  Can generate files to compare using a diff tool before/after.
     *  Files are by default placed under tests/build, and are therefore delted by gradle clean.
     *  The subfolder is named "TranslationTest." plus branch name (commit short if HEAD is detached) and contains a full git description marker for idnetification.
     *
     *  Do ***NOT*** leave active in CI: Will fail every time civilopedia contents or translations change.
     *
     *  ### Usage:
     *  - Start a branch and do not rebase / merge master until done
     *  - Choose parameters at the start of the function, most importantly `writeFiles` and `outFileName`
     *  - Comment out `@Ignore` and run the test.
     *  - Copy actual hashes (ready to go in console output) to expected hashes
     *  - Implement your changes to translation code
     *  - Rerun test
     *  - When done, uncomment `@Ignore`
     */
    @Test
    @Ignore("This is meant to check local work on the translation framework only")
    @RedirectOutput(RedirectPolicy.Show)
    fun translateAndHashCivilopedia() {
        val computeHashes = true
        val writeFiles = true
        val branch = GitInfo.currentBranch?.replace("/", "_") ?: GitInfo.describe?.commitAbbrev ?: "unknown"
        /** Path for text files relative to 'assets' */
        val outPath = "../../tests/build/TranslationTest.$branch"
        fun outFileName(language: String) = "$outPath/$language.txt"

        val expectedHashes = mapOf(
            "English" to "61209d506bcf6c76d58df5b239c20a985935bb4bd84b5886bcb1ee041cd04ab0",
            "Bangla" to "104235b2045b0593795bb2aab8157ac038de5edcc2512cdd8937e281baad75f2",
            "French" to "215bdbd3c1eb9540665b53d9de45e0b849654e8281bb6d4e9ee4204b95386cc3",
            "Hungarian" to "c3d4d2c26bb74b9e263fb839f063d1a478b7f59d0db7c51244d583d3a980b0f3",
            "Japanese" to "d785720f7ccc3924396ee113527c091096e506f0131d897cb318f7c9ac3385b6",
            "Maltese" to "3ca88caee228aed32f986662c27812467b449a09003cf469ed52f8c57eb12310",
            "Russian" to "d4ac55e23bb20405a246ef2d026dca49cf62fc2a8c06b287bb0e945d66977dd8",
            "Simplified_Chinese" to "bf73ef9eb54d24a30f1dfdcfa4f4786c6a9de6b4de9f7865ab6cce8de26d925f",
            "Swedish" to "9bd56a8f06e78c94ae3cf5a1272749f545a20ac6892dd086db834532d70fb2a5",
            "Vietnamese" to "34c5cba47b42d0811b143e3d336260fc5f12733be6b3c8bbb97ad6943fca3388",
            "generated" to "7df4e96847a9e483799deee0b1e01a24886c647752e775de575f8a8fbfca8c68",
        )

        setupUncivGame()
        loadTranslations()
        copyTranslationsToUncivGame()
        setupRuleset()
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!

        val (failures, duration, hint) = translateAndHashCivilopedia(
            ruleset, computeHashes, expectedHashes, writeFiles, ::outFileName
        )

        fun describe() = GitInfo.describe?.run {
            if (result.isNotBlank()) result.replace("/", "_")
            else commitAbbrev + if (isDirty) "-dirty" else ""
        } ?: "unknown"
        Gdx.files.local("$outPath/Created by ${describe()}").writeBytes(byteArrayOf(), false)

        println("Aggregate tr() duration: $duration\n")
        if (failures > 0) println(hint.toString())
        Assert.assertEquals(0, failures)
    }

    @Test
    @Ignore("This is meant to check local work on the translation framework only")
    @RedirectOutput(RedirectPolicy.Show)
    fun measureTranslateCivilopedia() {
        val computeHashes = false
        val writeFiles = false

        val languagesToTest = mapOf(
            "English" to "",
            "Bangla" to "",
            "French" to "",
            "Hungarian" to "",
            "Japanese" to "",
            "Maltese" to "",
            "Russian" to "",
            "Swedish" to "",
            "Simplified_Chinese" to "",
            "Vietnamese" to "",
            "generated" to "",
        )

        setupUncivGame()
        loadTranslations()
        copyTranslationsToUncivGame()
        setupRuleset()
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        val measurements = mutableListOf<Duration>()

        repeat(49) {
            val (_, duration, _) = translateAndHashCivilopedia(
                ruleset, computeHashes, languagesToTest, writeFiles
            ) { _ -> "" }
            measurements += duration
        }
        val noOutliers = measurements.drop(5).sorted().drop(1).dropLast(1) // 5 warmup, ignore best and worst
        val asDbl = noOutliers.map { it.toDouble(DurationUnit.MILLISECONDS) }
        val average = asDbl.average()
        val stdDev = if (asDbl.size > 1) sqrt(asDbl.sumOf { (it - average) * (it - average) } / (asDbl.size - 1)) else 0.0
        println("\nAverage aggregate tr() duration: ${average.milliseconds} ± ${stdDev.milliseconds}\n")
    }

    @Suppress("ConstPropertyName")
    companion object {
        const val alphabetPermutations = " 1a[]."
        const val generatePermutationsLength = 6
        const val alphabetRandom = "   \u00a0\u202f-+,.12345780abcdefghijklmnop<>[]{}"
        const val generateRandomLength = 42
        const val generateRandomCount = 10_000
    }

    private fun translateAndHashCivilopedia(
        ruleset: Ruleset,
        computeHashes: Boolean,
        expectedHashes: Map<String, String>,
        writeFiles: Boolean,
        outFileName: (String) -> String
    ): Triple<Int, Duration, StringBuilder> {
        ///////////////////// Setup /////////////////////
        var failures = 0
        val hint = StringBuilder()
        var duration = 0L.nanoseconds

        fun openWriter(key: String): Writer? {
            if (!writeFiles) return null
            val file = Gdx.files.local(outFileName(key))
            file.parent().mkdirs()
            return file
                .writer(false, Charsets.UTF_8.name())
                .buffered(0x8000)
        }

        ///////////////////// Data to test /////////////////////
        fun FormattedLine.simple() = buildString {
            if (header > 0) append(" ".repeat(header - 1))
            else append("\t".repeat(1 + indent))
            append(text)
        }
        val civilopedia = sequence {
            var lastClass = ""
            for (item in ruleset.allICivilopediaText()) {
                val className = item::class.simpleName ?: "anonymous"
                if (className != lastClass) {
                    lastClass = className
                    yield(className)
                }
                item.getCivilopediaTextHeader()?.let { yield(it.simple()) }
                for (line in item.getCivilopediaTextLines(ruleset))
                    if (line.text.isNotBlank()) yield(line.simple())
            }
        }
        suspend fun SequenceScope<String>.generateRandom() {
            val chars = CharArray(generateRandomLength)
            val rng = Random(42)
            repeat(generateRandomCount) {
                for (i in chars.indices)
                    chars[i] = alphabetRandom[rng.nextInt(alphabetRandom.length)]
                yield(String(chars))
            }
        }
        suspend fun SequenceScope<String>.generatePermutations() {
            var count = 1L                      // alphabet.length ^ len
            for (len in 0..generatePermutationsLength) {
                for (k in 0 until count) {
                    val chars = CharArray(len)
                    var x = k
                    for (i in 0 until len) {
                        chars[i] = alphabetPermutations[(x % alphabetPermutations.length).toInt()]
                        x /= alphabetPermutations.length
                    }
                    yield(String(chars))
                }
                count *= alphabetPermutations.length
            }
        }
        val generated = sequence {
            generatePermutations()
            generateRandom()
        }

        ///////////////////// Run /////////////////////
        for ((key, expectedHash) in expectedHashes) {
            val language = if (key == "generated") "English" else key
            UncivGame.Current.settings.language = language
            TranslationCache.invalidateLanguage()
            val data = if (key == "generated") generated else civilopedia
            val outWriter = openWriter(key)
            var lines = 0
            var total = 0L
            val md = if (computeHashes) MessageDigest.getInstance("SHA-256") else null

            fun process(line: String, output: String) {
                md?.update(output.toByteArray(Charsets.UTF_8))
                md?.update(0.toByte())
                lines++
                total += output.length + 1
                if (outWriter == null) return
                outWriter.write(line)
                outWriter.write(" → ")
                outWriter.write(output.trimStart(' ', '\t'))
                outWriter.write("\n")
            }

            for (line in data) {
                val input = if (key == "generated") line else line.trimStart() // We indented the original text for readability
                val startTime = TimeSource.Monotonic.markNow()
                val output = try {
                    input.tr()
                } catch (ex: Throwable) {
                    "${ex::class.simpleName}: ${ex.message}"
                }
                duration += startTime.elapsedNow()
                process(line, output)
            }
            outWriter?.close()
            if (md != null) {
                val actualHash = md.digest().toHexString()
                println("$key: $lines lines, $total chars: " + if (actualHash != expectedHash) "Failed" else "OK")
                if (actualHash != expectedHash) failures++
                hint.append("\t\t\t\"$key\" to \"$actualHash\",\n")
            }
        }

        ///////////////////// Info output /////////////////////
        return Triple(failures, duration, hint)
    }
}
