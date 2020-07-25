package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.models.stats.Stats
import java.util.*
import kotlin.collections.HashMap

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
class Translations : LinkedHashMap<String, TranslationEntry>(){

    var percentCompleteOfLanguages = HashMap<String,Int>()
            .apply { put("English",100) } // So even if we don't manage to load the percentages, we can still pass the language screen

    private var modsWithTranslations: HashMap<String, Translations> = hashMapOf() // key == mod name

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
        activeMods?.forEach {
            modsWithTranslations[it]?.let { modTranslations ->
                val translationEntry = modTranslations[text]?.get(language)
                if (translationEntry != null) return modTranslations[text]
            }
        }

        return this[text]
    }

    /**
     * @see get
     */
    fun getText(text: String, language: String, activeMods: HashSet<String>? = null): String {
        return get(text, language, activeMods)?.get(language) ?: text
    }

    fun getLanguages(): List<String> {
        val toReturn =  mutableListOf<String>()

        for(entry in values)
            for(languageName in entry.keys)
                if(!toReturn.contains(languageName)) toReturn.add(languageName)

        return toReturn
    }


    private fun tryReadTranslationForLanguage(language: String) {
        val translationStart = System.currentTimeMillis()

        val translationFileName = "jsons/translations/$language.properties"
        if (!Gdx.files.internal(translationFileName).exists()) return

        val languageTranslations: HashMap<String, String>
        try { // On some devices we get a weird UnsupportedEncodingException
            // which is super odd because everyone should support UTF-8
            languageTranslations = TranslationFileReader.read(Gdx.files.internal(translationFileName))
        } catch (ex: Exception) {
            return
        }

        // try to load the translations from the mods
        for (modFolder in Gdx.files.local("mods").list()) {
            val modTranslationFile = modFolder.child(translationFileName)
            if (modTranslationFile.exists()) {
                val translationsForMod = Translations()
                createTranslations(language, TranslationFileReader.read(modTranslationFile), translationsForMod)

                modsWithTranslations[modFolder.name()] = translationsForMod
            }
        }

        createTranslations(language, languageTranslations)

        val translationFilesTime = System.currentTimeMillis() - translationStart
        println("Loading translation file for $language - " + translationFilesTime + "ms")
    }

    private fun createTranslations(language: String,
                                  languageTranslations: HashMap<String,String>,
                                  targetTranslations: Translations = this) {
        for (translation in languageTranslations) {
            val hashKey = if (translation.key.contains('['))
                translation.key.replace(squareBraceRegex, "[]")
            else translation.key
            if (!containsKey(hashKey))
                targetTranslations[hashKey] = TranslationEntry(translation.key)

            // why not in one line, Because there were actual crashes.
            // I'm pretty sure I solved this already, but hey double-checking doesn't cost anything.
            val entry = targetTranslations[hashKey]
            if (entry != null) entry[language] = translation.value
        }
    }

    fun tryReadTranslationForCurrentLanguage(){
        tryReadTranslationForLanguage(UncivGame.Current.settings.language)
    }

    // This function is too strange for me, however, let's keep it "as is" for now. - JackRainy
    private fun getLanguagesWithTranslationFile(): List<String> {

        val languages = ArrayList<String>()
        // So apparently the Locales don't work for everyone, which is horrendous
        // So for those players, which seem to be Android-y, we try to see what files exist directly...yeah =/
        try{
            for(file in Gdx.files.internal("jsons/translations").list())
                languages.add(file.nameWithoutExtension())
        }
        catch (ex:Exception){} // Iterating on internal files will not work when running from a .jar

        languages.addAll(Locale.getAvailableLocales() // And this should work for Desktop, meaning from a .jar
                .map { it.getDisplayName(Locale.ENGLISH) }) // Maybe THIS is the problem, that the DISPLAY locale wasn't english
        // and then languages were displayed according to the player's locale... *sweatdrop*

        // These should probably be renamed
        languages.add("Simplified_Chinese")
        languages.add("Traditional_Chinese")

        languages.remove("template")
        languages.remove("completionPercentages")
        languages.remove("Thai") // Until we figure out what to do with it

        return languages.distinct()
                .filter { it!="Thai" &&
                        Gdx.files.internal("jsons/translations/$it.properties").exists() }
    }

    fun readAllLanguagesTranslation() {
        // Apparently you can't iterate over the files in a directory when running out of a .jar...
        // https://www.badlogicgames.com/forum/viewtopic.php?f=11&t=27250
        // which means we need to list everything manually =/

        val translationStart = System.currentTimeMillis()

        for (language in getLanguagesWithTranslationFile()) {
            tryReadTranslationForLanguage(language)
        }

        val translationFilesTime = System.currentTimeMillis() - translationStart
        println("Loading translation files - "+translationFilesTime+"ms")
    }

    fun loadPercentageCompleteOfLanguages(){
        val startTime = System.currentTimeMillis()

        percentCompleteOfLanguages = TranslationFileReader.readLanguagePercentages()

        val translationFilesTime = System.currentTimeMillis() - startTime
        println("Loading percent complete of languages - "+translationFilesTime+"ms")
    }

    companion object {
        // Regex compilation is expensive, best to save it
        val bonusOrPenaltyRegex = Regex("""(Bonus|Penalty) vs (.*) (\d*)%""")
        fun translateBonusOrPenalty(unique: String): String {
            val regexResult = bonusOrPenaltyRegex.matchEntire(unique)
            if (regexResult == null) return unique.tr()
            else {
                var separatorCharacter = " "
                if (UncivGame.Current.settings.language == "Simplified_Chinese") separatorCharacter = ""
                val start = regexResult.groups[1]!!.value + " vs [" + regexResult.groups[2]!!.value + "]"
                val translatedUnique = start.tr() + separatorCharacter + regexResult.groups[3]!!.value + "%"
                return translatedUnique
            }
        }
    }
}


// We don't need to allocate different memory for these every time we .tr() - or recompile them.
// Please note: The extra \] and \} are NOT removable, despite what Android Studio might recommend -
//   they are necessary for Android Java 6 phones to parse the regex properly!

// Expect a literal [ followed by a captured () group and a literal ].
// The group may contain any number of any character except ] - pattern [^]]
val squareBraceRegex = Regex("""\[([^]]*)\]""")

// Just look for either [ or ]
val eitherSquareBraceRegex = Regex("""\[|\]""")

// Analogous as above: Expect a {} pair with any chars but } in between and capture that
val curlyBraceRegex = Regex("""\{([^}]*)\}""")

/**
 *  This function does the actual translation work,
 *      using an instance of [Translations] stored in UncivGame.Current
 *
 *  @receiver   The string to be translated, can take three forms:
 *                  plain - translated directly by hashset lookup
 *                  placeholders - contains at least one '[' - see below
 *                  sentences - contains at least one '{'
 *                  - phrases between curly braces are translated individually
 *  @return     The translated string
 *                  defaults to the input string if no translation is available,
 *                  but with placeholder or sentence brackets removed.
 */
fun String.tr(): String {
    val activeMods = if (UncivGame.Current.isGameInfoInitialized())
        UncivGame.Current.gameInfo.gameParameters.mods else null

    // There might still be optimization potential here!
    if (contains("[")) { // Placeholders!
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
        val translationStringWithSquareBracketsOnly = this.replace(squareBraceRegex, "[]")
        // That is now the key into the translation HashMap!
        val translationEntry = UncivGame.Current.translations
                .get(translationStringWithSquareBracketsOnly, UncivGame.Current.settings.language, activeMods)

        if (translationEntry == null ||
                !translationEntry.containsKey(UncivGame.Current.settings.language)) {
            // Translation placeholder doesn't exist for this language, default to English
            return this.replace(eitherSquareBraceRegex, "")
        }

        // Take the terms in the message, WITHOUT square brackets
        val termsInMessage = this.getPlaceholderParameters()
        // Take the term from the placeholder, INCLUDING the square brackets
        val termsInTranslationPlaceholder = squareBraceRegex.findAll(translationEntry.entry).map { it.value }.toList()
        if (termsInMessage.size != termsInTranslationPlaceholder.size)
            throw Exception("Message $this has a different number of terms than the placeholder $translationEntry!")

        var languageSpecificPlaceholder = translationEntry[UncivGame.Current.settings.language]!!
        for (i in termsInMessage.indices) {
            languageSpecificPlaceholder = languageSpecificPlaceholder.replace(termsInTranslationPlaceholder[i], termsInMessage[i].tr())
        }
        return languageSpecificPlaceholder      // every component is already translated
    }

    if (contains("{")) { // sentence
        return curlyBraceRegex.replace(this) { it.groups[1]!!.value.tr() }
    }

    if (Stats.isStats(this)) return Stats.parse(this).toString()

    return UncivGame.Current.translations.getText(this, UncivGame.Current.settings.language, activeMods)
}

fun String.getPlaceholderText() = this.replace(squareBraceRegex, "[]")

fun String.equalsPlaceholderText(str:String): Boolean {
    if (first() != str.first()) return false // for quick negative return 95% of the time
    return  this.getPlaceholderText() == str
}

fun String.getPlaceholderParameters() = squareBraceRegex.findAll(this).map { it.groups[1]!!.value }.toList()