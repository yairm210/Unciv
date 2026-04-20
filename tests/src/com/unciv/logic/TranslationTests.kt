//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.UnitActionType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.stats.Stats
import com.unciv.models.translations.TranslationEntry
import com.unciv.models.translations.TranslationFileReader
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.Translations
import com.unciv.models.translations.Translations.Companion.conditionalOrderingKey
import com.unciv.models.translations.Translations.Companion.conditionalPlacementKey
import com.unciv.models.translations.Translations.Companion.defaultConditionalOrderingString
import com.unciv.models.translations.Translations.Companion.shouldCapitalizeKey
import com.unciv.models.translations.curlyBraceRegex
import com.unciv.models.translations.getModifiers
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.ui.components.fonts.DiacriticSupport
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.utils.Log
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
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
        fun String.getTemplateKey() = if (contains('[')) replace(squareBraceRegex, "[]") else this
        fun String.getInnerTemplate() = (if (contains('{')) curlyBraceRegex.find(this)?.groupValues[1] else null) ?: this

        val allKeys = TranslationFileReader.readTemplates { lines ->
            lines.filterNot { it.isEmpty() || it.startsWith('#') || !it.endsWith(" = ") }
                .map { it.removeSuffix(" = ").getTemplateKey() }
                .toMutableSet()
        } ?: return

        // Include auto-templates for keyboard binding UI
        KeyboardBinding.entries.mapTo(allKeys) { it.label }

        var failures = 0
        for (action in UnitActionType.entries) {
            if (action.value.isEmpty()) continue
            val key = action.value.getInnerTemplate().getTemplateKey()
            if (key in allKeys) continue
            failures++
            println("""UnitActionType.$action (value "${action.value}") is missing its translation template.""")
        }
        Assert.assertEquals("This test will only pass when there is a template for all unit actions", 0, failures)
    }

    @Test
    fun translationsFromJSONsCanBeGenerated() {
        if (!com.unciv.platform.PlatformCapabilities.current.backgroundThreadPools) return
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
            val placeholders = squareBraceRegex.findAll(translationEntry)
                    .map { it.value }.toList()
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
            val placeholders = squareBraceRegex.findAll(translationEntry)
                .map { it.value }.toSet()
            for (language in languages) {
                val output = translations.getText(key, language)
                if (output == key) continue // the language doesn't have the required translation, so we got back the key
                val translatedPlaceholders = squareBraceRegex.findAll(output)
                    .map { it.value }.toSet()
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
            val keyFromEntry = translationEntry.replace(squareBraceRegex, "[]")
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
                val key = template.replace(squareBraceRegex, "[]")
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
                val placeholders = squareBraceRegex.findAll(template)
                    .map { it.value }.toList()

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
            val placeholders = squareBraceRegex.findAll(translationEntry.entry)
                    .map { it.value }.toList()

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
        Assert.assertEquals(listOf("New [York]"),
            "The city of [New [York]]".getPlaceholderParameters())

        // Closing braces without a matching opening brace - 'level 0' - are ignored
        Assert.assertEquals(listOf("New [York]"),
            "The city of [New [York]]]".getPlaceholderParameters())

        // Opening braces without a matching closing brace mean that the term is never 'closed'
        // so there are no parameters
        Assert.assertEquals(listOf<String>(),
            "The city of [[New [York]".getPlaceholderParameters())

        // Supernesting
        val superNestedString = "The brother of [[my [best friend]] and [[America]'s greatest [Dad]]]"
        Assert.assertEquals(listOf("[my [best friend]] and [[America]'s greatest [Dad]]"),
                superNestedString.getPlaceholderParameters())
        Assert.assertEquals(listOf("my [best friend]", "[America]'s greatest [Dad]"),
        superNestedString.getPlaceholderParameters()[0]
            .getPlaceholderParameters())

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
    fun isStatsRecognizesStatsIncludingStatCharacter() {
        setupUncivGame()

        Assert.assertTrue(Stats.isStats(Stats(1f,2f,3f).toStringForNotifications()))
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

        val tests = arrayOf("1", "+1", "-1", "1.0", "+1.0", "-1.0", "0%", "1/2", "(3/4)")

        UncivGame.Current.settings.language = LocaleCode.English.name
        Assert.assertArrayEquals(
            "English", tests, // assume unchanged
            tests.map { it.tr() }.toTypedArray()
        )

        UncivGame.Current.settings.language = LocaleCode.Bangla.name
        Assert.assertArrayEquals(
            "Bangla",
            arrayOf("১", "+১", "-১", "১.০", "+১.০", "-১.০", "০%", "১/২", "(৩/৪)"),
            tests.map { it.tr() }.toTypedArray()
        )
    }

    @Test
    fun testTranslateConditionals() {
        fun setReordered() {
            addTranslation(conditionalOrderingKey, "<when attacking> <for [mapUnitFilter] units> <with a garrison>")
        }
        fun setBeforeAndCapitalized() {
            addTranslation(conditionalPlacementKey, "before")
            addTranslation(shouldCapitalizeKey, "true")
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
}
