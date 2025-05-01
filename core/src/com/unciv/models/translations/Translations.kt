package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.fonts.DiacriticSupport
import com.unciv.ui.components.fonts.FontRulesetIcons
import com.unciv.utils.Log
import com.unciv.utils.debug
import java.util.Locale
import org.jetbrains.annotations.VisibleForTesting

/**
 *  This collection holds all translations for the game.
 *
 *  The collection may be instantiated loading any number of languages, but normally it contains
 *  either one (player-selected) language or all of them for the translation file writer.
 *
 *  Translatables have two forms: normal or containing placeholders (delimited by square brackets).
 *  Translation _requests_ can have a third form: containing {subsentences} - these are split into their
 *  components and passed individually back into the translator, therefore they need no entry in this collection
 *
 *  @property keys:     The key is a copy of the translatable string in the normal case.
 *                       For the placeholder case, the key is modified: placeholders reduced to empty [] pairs.
 *  @property values:   Each a [TranslationEntry] containing the original translatable and 0..n translations.
 *  @property percentCompleteOfLanguages:   Holds the completion percentage for each language,
 *                      read from its file "completionPercentages.properties" which is generated
 *                      by the [TranslationFileWriter] utility.
 *
 *  @see    String.tr   for more explanations (below)
 */
class Translations : LinkedHashMap<String, TranslationEntry>() {

    var percentCompleteOfLanguages = HashMap<String,Int>()
            .apply { put(Constants.english, 100) } // So even if we don't manage to load the percentages, we can still pass the language screen

    internal var modsWithTranslations: HashMap<String, Translations> = hashMapOf() // key == mod name

    // used by tr() whenever GameInfo not initialized (allowing new game screen to use mod translations)
    var translationActiveMods = LinkedHashSet<String>()

    /**
     * Searches for the translation entry of a given [text] for a given [language].
     * This includes translations provided by mods from [activeMods]
     *
     * @param text the input text for the translate entry
     * @param language the inquired language
     * @param activeMods set of the active mods that should include in the search
     *
     * @return the translation entry or null when not available
     */
    fun get(text: String, language: String, activeMods: HashSet<String>? = null): TranslationEntry? {
        if (activeMods != null)
            for (activeMod in activeMods) {
                val modTranslations = modsWithTranslations[activeMod] ?: continue
                val translationEntry = modTranslations[text]
                if (translationEntry?.get(language) != null)
                    return translationEntry
            }

        return this[text]
    }

    /**
     * @see get
     */
    fun getText(text: String, language: String, activeMods: HashSet<String>? = null): String {
        return get(text, language, activeMods)?.get(language) ?: text
    }

    /** Get all languages present in `this`, used for [TranslationFileWriter] and `TranslationTests` */
    fun getLanguages() = linkedSetOf<String>().apply {
            for (entry in values)
                for (languageName in entry.keys)
                    add(languageName)
        }

    /** This reads all translations for a specific language, including _all_ installed mods.
     *  Vanilla translations go into `this` instance, mod translations into [modsWithTranslations].
     */
    private fun tryReadTranslationForLanguage(language: String, noDiacritics: Boolean = false) {
        val translationStart = System.currentTimeMillis()

        val translationFileName = "jsons/translations/$language.properties"
        if (!Gdx.files.internal(translationFileName).exists()) return

        val languageTranslations: HashMap<String, String>
        try { // On some devices we get a weird UnsupportedEncodingException
            // which is super odd because everyone should support UTF-8
            languageTranslations = TranslationFileReader.read(Gdx.files.internal(translationFileName))
        } catch (ex: Exception) {
            Log.error("Exception reading translations for $language", ex)
            return
        }

        // try to load the translations from the mods
        for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
            val modTranslationFile = modFolder.child(translationFileName)
            if (modTranslationFile.exists()) {
                var translationsForMod = modsWithTranslations[modFolder.name()]
                if (translationsForMod == null) {
                    translationsForMod = Translations()
                    modsWithTranslations[modFolder.name()] = translationsForMod
                }
                try {
                    translationsForMod.createTranslations(language, TranslationFileReader.read(modTranslationFile), noDiacritics)
                } catch (ex: Exception) {
                    Log.error("Exception reading translations for ${modFolder.name()} $language", ex)
                }
            }
        }

        createTranslations(language, languageTranslations, noDiacritics)

        debug("Loading translation file for %s - %sms", language, System.currentTimeMillis() - translationStart)
    }

    @VisibleForTesting
    fun createTranslations(language: String, languageTranslations: HashMap<String, String>, noDiacritics: Boolean = false) {
        val diacriticSupport = if (noDiacritics) null
            else DiacriticSupport(languageTranslations).takeIf { it.isEnabled() }
        for ((key, value) in languageTranslations) {
            val hashKey = if (key.contains('[') && !key.contains('<'))
                key.getPlaceholderText()
            else key
            var entry = this[hashKey]
            if (entry == null) {
                entry = TranslationEntry(key)
                this[hashKey] = entry
            }
            entry[language] = diacriticSupport?.remapDiacritics(value) ?: value
        }
    }


    fun tryReadTranslationForCurrentLanguage() {
        DiacriticSupport.reset()
        tryReadTranslationForLanguage(UncivGame.Current.settings.language)
        DiacriticSupport.freeTranslationData()
    }

    /** Get a list of supported languages for [readAllLanguagesTranslation] */
    // This function is too strange for me, however, let's keep it "as is" for now. - JackRainy
    private fun getLanguagesWithTranslationFile(): List<String> {

        val languages = HashSet<String>()
        // So apparently the Locales don't work for everyone, which is horrendous
        // So for those players, which seem to be Android-y, we try to see what files exist directly...yeah =/
        try{
            for (file in Gdx.files.internal("jsons/translations").list())
                languages.add(file.nameWithoutExtension())
        }
        catch (ex: Exception) {
            Log.error("Failed to add languages", ex)
        } // Iterating on internal files will not work when running from a .jar

        languages.addAll(Locale.getAvailableLocales() // And this should work for Desktop, meaning from a .jar
                .map { it.getDisplayName(Locale.ENGLISH) }) // Maybe THIS is the problem, that the DISPLAY locale wasn't english
        // and then languages were displayed according to the player's locale... *sweatdrop*

        // These should probably be renamed
        languages.add("Simplified_Chinese")
        languages.add("Traditional_Chinese")

        languages.remove("template")
        languages.remove("completionPercentages")

        return languages.filter { Gdx.files.internal("jsons/translations/$it.properties").exists() }
    }

    /** Ensure _all_ languages are loaded, used by [TranslationFileWriter] and `TranslationTests` only.
     *
     *  #### Notes:
     *  -  Expects to run on a newly created instance.
     *  -  Loads the translations with no diacritic mapping, so what we read will be what we write
     *     (otherwise we would write out the fake alphabet-conversions, a one-way destructive mistake).
     *  -  Relies on usage by TFW and tests only, if the result is ever meant to support translations that are actually displayed, a refactor will be needed.
     *  -  Does not clear the fake alphabet possibly present in DiacriticSupport, but will not use it either.
     */
    fun readAllLanguagesTranslation() {
        // Apparently you can't iterate over the files in a directory when running out of a .jar...
        // https://www.badlogicgames.com/forum/viewtopic.php?f=11&t=27250
        // which means we need to list everything manually =/

        val translationStart = System.currentTimeMillis()

        for (language in getLanguagesWithTranslationFile()) {
            tryReadTranslationForLanguage(language, noDiacritics = true)
        }

        debug("Loading translation files - %sms", System.currentTimeMillis() - translationStart)
    }

    fun loadPercentageCompleteOfLanguages() {
        val startTime = System.currentTimeMillis()

        percentCompleteOfLanguages = TranslationFileReader.readLanguagePercentages()

        debug("Loading percent complete of languages - %sms", System.currentTimeMillis() - startTime)
    }

    fun getConditionalOrder(language: String): String {
        return getText(englishConditionalOrderingString, language, null)
    }

    fun placeConditionalsAfterUnique(language: String) =
        get(conditionalUniqueOrderString, language, null)?.get(language) != "before"

    /** Returns the equivalent of a space in the given language
     * Defaults to a space if no translation is provided
     */
    fun getSpaceEquivalent(language: String): String {
        val translation = getText("\" \"", language, null)
        return translation.substring(1, translation.length-1)
    }

    fun shouldCapitalize(language: String): Boolean {
        return get(shouldCapitalizeString, language, null)?.get(language)?.toBoolean() ?: true
    }

    fun triggerNotificationEffectBeforeCause(language: String): Boolean{
        return get(effectBeforeCause, language, null)?.get(language)?.toBoolean() ?: true
    }

    companion object {
        // Whenever this string is changed, it should also be changed in the translation files!
        // It is mostly used as the template for translating the order of conditionals
        const val englishConditionalOrderingString =
            "<with a garrison> <for [mapUnitFilter] units> <above [amount] HP> <below [amount] HP> <vs cities> <vs [mapUnitFilter] units> <when fighting in [tileFilter] tiles> <when attacking> <when defending> <if this city has at least [amount] specialists> <when at war> <when not at war> <while the empire is happy> <during a Golden Age> <during the [era]> <starting from the [era]> <before the [era]> <with [techOrPolicy]> <without [techOrPolicy]>"
        const val conditionalUniqueOrderString = "ConditionalsPlacement"
        const val shouldCapitalizeString = "StartWithCapitalLetter"
        const val effectBeforeCause = "EffectBeforeCause"
    }
}


// We don't need to allocate different memory for these every time we .tr() - or recompile them.
// Please note: The extra \] and \} are NOT removable, despite what Android Studio might recommend -
//   they are necessary for Android Java 6 phones to parse the regex properly!

// Expect a literal [ followed by a captured () group and a literal ].
// The group may contain any number of any character except ] - pattern [^]]
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
val squareBraceRegex = Regex("""\[([^]]*)\]""")

// Analogous as above: Expect a {} pair with any chars but } in between and capture that
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
val curlyBraceRegex = Regex("""\{([^}]*)\}""")

// Analogous as above: Expect a <> pair with any chars but > in between and capture that
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
val pointyBraceRegex = Regex("""\<([^>]*)\>""")

// Used to match continous digits 0, 12, 1232 etc
@Suppress("RegExpRedundantEscape") // Some Android versions need ]}) escaped
val digitsRegex = Regex("""\d""")

object TranslationActiveModsCache {
    private var cachedHash = Int.MIN_VALUE

    var activeMods: HashSet<String> = hashSetOf()
        get() {
            val hash = getCurrentHash()
            if (hash != cachedHash) {
                cachedHash = hash
                field = getCurrentSet()
            }
            return field
        }
        private set

    private fun getCurrentHash(): Int {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null) {
            gameInfo.gameParameters.mods.hashCode() + gameInfo.gameParameters.baseRuleset.hashCode() * 31
        } else {
            UncivGame.Current.translations.translationActiveMods.hashCode() * 31 * 31
        }
    }

    private fun getCurrentSet(): LinkedHashSet<String> {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null) {
            val par = gameInfo.gameParameters
            // This is equivalent to (par.mods + par.baseRuleset) without the cast down to `Set`
            LinkedHashSet<String>(par.mods.size + 1).apply {
                addAll(par.mods)
                add(par.baseRuleset)
            }
        } else {
            UncivGame.Current.translations.translationActiveMods
        }
    }
}

/**
 *  This function does the actual translation work,
 *      using an instance of [Translations] stored in UncivGame.Current
 *
 *  @receiver   The string to be translated, can take three forms:
 *                  plain - translated directly by hashset lookup
 *                  placeholders - contains at least one '[' - see below
 *                  sentences - contains at least one '{'
 *                  - phrases between curly braces are translated individually
 *                  Additionally, they may contain conditionals between '<' and '>'
 *  @param      hideIcons disables auto-inserting icons for ruleset objects (but not Stats)
 *  @param      hideStats disables auto-inserting icons for Stats (but not rulset objects)
 *  @return     The translated string
 *                  defaults to the input string if no translation is available,
 *                  but with placeholder or sentence brackets removed.
 */
fun String.tr(hideIcons: Boolean = false, hideStats: Boolean = false): String {
    val language: String = UncivGame.Current.settings.language

    // '<' and '>' checks for quick 'no' answer, regex to ensure that no one accidentally put '><' and ruined things
    if (contains('<') && contains('>') && pointyBraceRegex.containsMatchIn(this)) {
        return translateConditionals(hideIcons, language)
    }

    // curly and square brackets can be nested inside of each other so find the leftmost curly/square
    // bracket then process that first
    val indexSquare = this.indexOf('[')
    val indexCurly = this.indexOf('{')

    val squareBracketsEncounteredFirst = indexSquare >= 0 && (indexCurly < 0 || indexSquare < indexCurly)
    val curlyBracketsEncounteredFirst =  indexCurly >= 0 && (indexSquare < 0 || indexCurly < indexSquare)

    if (squareBracketsEncounteredFirst)
        return translatePlaceholders(language, hideIcons)

    if (curlyBracketsEncounteredFirst) // Translating partial sentences
        return curlyBraceRegex.replace(this) { it.groups[1]!!.value.tr(hideIcons) }

    return translateIndividualWord(language, hideIcons, hideStats)
}


private fun String.translateConditionals(hideIcons: Boolean, language: String): String {
    /**
     * So conditionals can contain placeholders, such as <vs [unitFilter] units>, which themselves
     * can contain multiple filters, such as <vs [{Military} {Water}] units>.
     * Moreover, we can have any amount of conditionals in any order, and translations
     * can reorder these conditionals in any way they like, even putting them in front
     * of the rest of the translatable string.
     * All of this nesting makes it quite difficult to translate, and is the reason we check
     * for these first.
     *
     * The plan: First translate each of the conditionals on its own, and then combine them
     * together into the final fully translated string.
     */

    var translatedBaseUnique = this.removeConditionals().tr(hideIcons)

    val conditionals = this.getModifiers().map { it.placeholderText }
    val conditionsWithTranslation: LinkedHashMap<String, String> = linkedMapOf()

    for (conditional in this.getModifiers())
        conditionsWithTranslation[conditional.placeholderText] = conditional.text.tr(hideIcons)

    val translatedConditionals: MutableList<String> = mutableListOf()

    // Somewhere, we asked the translators to reorder all possible conditionals in a way that
    // makes sense in their language. We get this ordering, and than extract each of the
    // translated conditionals, removing the <> surrounding them, and removing param values
    // where it exists.
    val conditionalOrdering = UncivGame.Current.translations.getConditionalOrder(language)
    for (placedConditional in pointyBraceRegex.findAll(conditionalOrdering)
        .map { it.value.substring(1, it.value.length - 1).getPlaceholderText() }) {
        if (placedConditional in conditionals) {
            translatedConditionals.add(conditionsWithTranslation[placedConditional]!!)
            conditionsWithTranslation.remove(placedConditional)
        }
    }

    // If the translated string that should contain all conditionals doesn't contain
    // a few conditionals used here, just add the translations of these to the end.
    // We do test for this, but just in case.
    translatedConditionals.addAll(conditionsWithTranslation.values)

    // After that, add the translation of the base unique either before or after these conditionals
    if (UncivGame.Current.translations.placeConditionalsAfterUnique(language)) {
        translatedConditionals.add(0, translatedBaseUnique)
    } else {
        if (UncivGame.Current.translations.shouldCapitalize(language) && translatedBaseUnique[0].isUpperCase())
            translatedBaseUnique = translatedBaseUnique.replaceFirstChar { it.lowercase() }
        translatedConditionals.add(translatedBaseUnique)
    }

    var fullyTranslatedString = translatedConditionals.joinToString(
        UncivGame.Current.translations.getSpaceEquivalent(language)
    )
    if (UncivGame.Current.translations.shouldCapitalize(language))
        fullyTranslatedString = fullyTranslatedString.replaceFirstChar { it.uppercase() }
    return fullyTranslatedString
}

private fun String.translatePlaceholders(language: String, hideIcons: Boolean): String {
    /**
     * I'm SURE there's an easier way to do this but I can't think of it =\
     * So what's all this then?
     * Well, not all languages are like English. So say I want to say "work on Library has completed in Akkad",
     * but in a completely different language like Japanese or German,
     * It could come out "Akkad hast die worken onner Library gerfinishen" or whatever,
     * basically, the order of the words in the sentence is not guaranteed.
     * So to translate this, I give a sentence like "work on [building] has completed in [city]"
     * and the german can put those placeholders where he wants, so  "[city] hast die worken onner [building] gerfinishen"
     * The string on which we call tr() will look like "work on [library] has completed in [Akkad]"
     * We will find the german placeholder text, and replace the placeholders with what was filled in the text we got!
     */

    // Convert "work on [building] has completed in [city]" to "work on [] has completed in []"
    val translationStringWithSquareBracketsOnly = this.getPlaceholderText()
    // That is now the key into the translation HashMap!
    val translationEntry = UncivGame.Current.translations
        .get(translationStringWithSquareBracketsOnly, language, TranslationActiveModsCache.activeMods)

    var languageSpecificPlaceholder: String
    val originalEntry: String
    if (translationEntry == null || !translationEntry.containsKey(language)) {
        // Translation placeholder doesn't exist for this language, default to English
        languageSpecificPlaceholder = this
        originalEntry = this
    } else {
        languageSpecificPlaceholder = translationEntry[language]!!
        originalEntry = translationEntry.entry
    }

    // Take the terms in the message, WITHOUT square brackets
    val termsInMessage = this.getPlaceholderParameters()
    // Take the terms from the placeholder
    val termsInTranslationPlaceholder = originalEntry.getPlaceholderParameters()
    if (termsInMessage.size != termsInTranslationPlaceholder.size)
        throw Exception("Message $this has a different number of terms than the placeholder $translationEntry!")

    for (i in termsInMessage.indices) {
        languageSpecificPlaceholder = languageSpecificPlaceholder.replace(
            "[${termsInTranslationPlaceholder[i]}]", // re-add square brackets to placeholder terms
            termsInMessage[i].tr(hideIcons)
        )
    }
    return languageSpecificPlaceholder      // every component is already translated
}


/** No brackets of any kind, just a single word */
private fun String.translateIndividualWord(language: String, hideIcons: Boolean, hideStats: Boolean): String {
    if (Stats.isStats(this)) return Stats.parse(this).toString()

    val translation = UncivGame.Current.translations.getText(
        this, language, TranslationActiveModsCache.activeMods
    ).replace(digitsRegex) {
        it.value.toLong().tr(language)
    }

    val stat = Stat.safeValueOf(this)
    if (!hideStats && stat != null) return stat.character + translation

    if (!hideIcons && FontRulesetIcons.rulesetObjectNameToChar.containsKey(this))
        return FontRulesetIcons.rulesetObjectNameToChar[this]!! + translation

    return translation
}


/**
 * Finds the parameters in a string while IGNORING the lower-leveled braces.
 * For example, a string like 'The city of [New [York]]' will return ['New [York]'],
 * allowing us to have nested translations!
 */
fun String.getPlaceholderParameters(): List<String> {
    if (!this.contains('[')) return emptyList()

    val stringToParse = this.removeConditionals()
    val parameters = ArrayList<String>()
    var depthOfBraces = 0
    var startOfCurrentParameter = -1
    for (i in stringToParse.indices) {
        if (stringToParse[i] == '[') {
            if (depthOfBraces == 0) startOfCurrentParameter = i+1
            depthOfBraces++
        }
        if (stringToParse[i] == ']' && depthOfBraces > 0) {
            depthOfBraces--
            if (depthOfBraces == 0) parameters.add(substring(startOfCurrentParameter,i))
        }
    }
    return parameters
}

fun String.getPlaceholderText(): String {
    var stringToReturn = this.removeConditionals()
    val placeholderParameters = stringToReturn.getPlaceholderParameters()
    for (placeholderParameter in placeholderParameters)
        stringToReturn = stringToReturn.replaceFirst("[$placeholderParameter]", "[]")
    return stringToReturn
}

fun String.equalsPlaceholderText(str: String): Boolean {
    if (isEmpty()) return str.isEmpty()
    if (str.isEmpty()) return false // Empty strings have no .first()
    if (first() != str.first()) return false // for quick negative return 95% of the time
    return this.getPlaceholderText() == str
}

fun String.hasPlaceholderParameters(): Boolean {
    if (!this.contains('[')) return false
    return squareBraceRegex.containsMatchIn(this.removeConditionals())
}

/** Substitutes placeholders with [strings], respecting order of appearance. */
fun String.fillPlaceholders(vararg strings: String): String {
    val keys = this.getPlaceholderParameters()
    if (keys.size > strings.size)
        throw Exception("String $this has a different number of placeholders ${keys.joinToString()} (${keys.size}) than the substitutive strings ${strings.joinToString()} (${strings.size})!")

    var filledString = this.replace(squareBraceRegex, "[]")
    for (i in keys.indices)
        filledString = filledString.replaceFirst("[]", "[${strings[i]}]")
    return filledString
}

fun String.getModifiers(): List<Unique> {
    if (!this.contains('<')) return emptyList()
    return pointyBraceRegex.findAll(this).map { Unique(it.groups[1]!!.value) }.toList()
}

fun String.removeConditionals(): String {
    if (!this.contains('<')) return this // no need to regex search
    return this
        .replace(pointyBraceRegex, "")
        // So, this is a quick hack, but it works as long as nobody uses word separators different from " " (space) and "" (none),
        // and no translations start or end with a space.
        // According to https://linguistics.stackexchange.com/questions/6131/is-there-a-long-list-of-languages-whose-writing-systems-dont-use-spaces
        // This is a reasonable but not fully correct assumption to make.
        // By doing it like this, we exclude languages such as Tibetan, Dzongkha (Bhutan), and Ethiopian.
        // If we ever start getting translations for these, we'll work something out then.
        .replace("  ", " ")
        .trim()
}

// formats number according to current language
fun Number.tr(): String {
    return UncivGame.Current.settings.getCurrentNumberFormat().format(this)
}

// formats number according to given language
fun Number.tr(language: String): String {
    return LocaleCode.getNumberFormatFromLanguage(language).format(this)
}
