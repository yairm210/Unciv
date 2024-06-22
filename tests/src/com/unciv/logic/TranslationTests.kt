//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.stats.Stats
import com.unciv.models.translations.TranslationEntry
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.Translations
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.ui.components.fonts.DiacriticSupport
import com.unciv.utils.Log
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TranslationTests {
    private var translations = Translations()
    private var ruleset = Ruleset()

    @Before
    // Since the ruleset and translation loader have their own output,
    // We 'disable' the output stream for their outputs, and only enable it for the test itself.
    @RedirectOutput(RedirectPolicy.Discard)
    fun loadTranslations() {
        translations.readAllLanguagesTranslation()
        RulesetCache.loadRulesets(noMods = true)
        ruleset = RulesetCache.getVanillaRuleset()
    }

    @Test
    fun translationsLoad() {
        Assert.assertTrue("This test will only pass if there are translations",
                translations.size > 0)
    }


    // This test is incorrectly defined: it should read from the template.properties file and not from the final translation files.
//    @Test
//    fun allUnitActionsHaveTranslation() {
//        val actions: MutableSet<String> = HashSet()
//        for (action in UnitActionType.values()) {
//            actions.add(
//                when(action) {
//                    UnitActionType.Upgrade -> "Upgrade to [unitType] ([goldCost] gold)"
//                    UnitActionType.Create -> "Create [improvement]"
//                    UnitActionType.SpreadReligion -> "Spread [religionName]"
//                    else -> action.value
//                }
//            )
//        }
//        val allUnitActionsHaveTranslation = allStringAreTranslated(actions)
//        Assert.assertTrue("This test will only pass when there is a translation for all unit actions",
//                allUnitActionsHaveTranslation)
//    }
//
//    private fun allStringAreTranslated(strings: Set<String>): Boolean {
//        var allStringsHaveTranslation = true
//        for (entry in strings) {
//            val key = if (entry.contains('[')) entry.replace(squareBraceRegex, "[]") else entry
//            if (!translations.containsKey(key)) {
//                allStringsHaveTranslation = false
//                println("$entry not translated!")
//            }
//        }
//        return allStringsHaveTranslation
//    }

    @Test
    fun translationsFromJSONsCanBeGenerated() {
        // Triggers generation of the translation's strings
        // Will output "Translation writer took...", which is suppressed unless you use the @RedirectOutput(RedirectPolicy.Show) annotation
        val stringsSize = TranslationFileWriter.getGeneratedStringsSize()

        Assert.assertTrue("This test will only pass when all .json files are serializable",
                stringsSize > 0)
    }

    /** For every translatable string find its placeholders and check if all translations have them */
    @Test
    fun allTranslationsHaveCorrectPlaceholders() {
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
    fun allTranslationsHaveUniquePlaceholders() {
        // check that the templates have unique placeholders (the translation entries are checked below)
        val templateLines = Gdx.files.internal(TranslationFileWriter.templateFileLocation).reader().readLines()
        var noTwoPlaceholdersAreTheSame = true
        for (template in templateLines) {
            if (template.startsWith("#")) continue
            val placeholders = squareBraceRegex.findAll(template)
                .map { it.value }.toList()

            for (placeholder in placeholders)
                if (placeholders.count { it == placeholder } > 1) {
                    noTwoPlaceholdersAreTheSame = false
                    println("Template key $template has the parameter $placeholder more than once")
                    break
                }
        }
        Assert.assertTrue(
            "This test will only pass when no translation template keys have the same parameter twice",
            noTwoPlaceholdersAreTheSame)
    }

    @Test
    fun noTwoPlaceholdersAreTheSame() {
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
    fun allTranslationsEndWithASpace() {
        val templateLines = Gdx.files.internal(TranslationFileWriter.templateFileLocation).reader().readLines()
        var failed = false
        for (line in templateLines) {
            if (line.endsWith(" =")) {
                println("$line ends without a space at the end")
                failed = true
            }
        }
        Assert.assertFalse(failed)
    }


    @Test
    fun allStringsTranslate() {
        // Needed for .tr() to work
        UncivGame.Current = UncivGame()
        UncivGame.Current.settings = GameSettings()

        for ((key, value) in translations)
            UncivGame.Current.translations[key] = value

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

        UncivGame.Current = UncivGame()
        UncivGame.Current.settings = GameSettings()

        fun addTranslation(original: String, result: String) {
            UncivGame.Current.translations[original.getPlaceholderText()] = TranslationEntry(original)
                .apply { this[Constants.english] = result }
        }
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
        UncivGame.Current = UncivGame()
        UncivGame.Current.settings = GameSettings()

        Assert.assertTrue(Stats.isStats(Stats(1f,2f,3f).toStringForNotifications()))
    }

    @Test
    fun diacriticsSilentForEnglish() {
        DiacriticSupport.reset()
        val english = translations.values
            .mapNotNull { entry ->
                entry["English"]?.let { entry.entry to it }
            }.toMap(HashMap())
        DiacriticSupport.prepareTranslationData(english)
        Assert.assertTrue(DiacriticSupport.noDiacritics())
    }

    @Test
    fun diacriticsWorkForBangla() {
        //todo This test was designed before the Bangla language was merged, and uses its own data.
        //     With the actual Bangla, the @before will already have loaded that, so there could be an additional, or a different test on the live one...
        DiacriticSupport.reset()
        val leftJoiningDiacritics = "ঁ ং ঃ ় া ি ী ু ূ ৃ ৄ ে ৈ ো ৌ ্ ৗ ৢ ৣ ৾".replace(" ", "")
        val leftAndRightJoiners = "্"
        DiacriticSupport.prepareTranslationData(Char(0x0980U)..Char(0x09FDU), leftJoiningDiacritics, "", leftAndRightJoiners)

        listOf(
            "মানচিত্র সম্পাদক", "দেখুন", "উৎপন্ন করুন", "আংশিক"
        ).forEach { DiacriticSupport.remapDiacritics(it) }
        val actual = DiacriticSupport.getKnownCombinations()
        val expected = setOf("ম, া", "চ, ি", "ত, ্, র", "ম, ্, প, া", "দ, ে", "খ, ু", "ন, ্, ন", "র, ু", "আ, ং", "শ, ি")
            .map {
                it.split(", ").joinToString("")
            }.toSet()
        Assert.assertEquals(expected, actual)
    }

//    @Test
//    fun allConditionalsAreContainedInConditionalOrderTranslation() {
//        val orderedConditionals = Translations.englishConditionalOrderingString
//        val orderedConditionalsSet = orderedConditionals.getConditionals().map { it.placeholderText }
//        val translationEntry = translations[orderedConditionals]!!
//
//        var allTranslationsCheckedOut = true
//        for ((language, translation) in translationEntry) {
//            val translationConditionals = translation.getConditionals().map { it.placeholderText }
//            if (translationConditionals.toHashSet() != orderedConditionalsSet.toHashSet()
//                || translationConditionals.count() != translationConditionals.distinct().count()
//            ) {
//                allTranslationsCheckedOut = false
//                println("Not all or double parameters found in the conditional ordering for $language")
//            }
//        }
//
//        Assert.assertTrue(
//            "This test will only pass when each of the conditionals exists exactly once in the translations for the conditional ordering",
//            allTranslationsCheckedOut
//        )
//    }
}
