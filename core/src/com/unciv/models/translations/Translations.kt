package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.fonts.DiacriticSupport
import com.unciv.utils.Log
import com.unciv.utils.debug
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Readonly

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
    @Readonly
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
     * Returns a specific translation, or the original [text] if no translation for [language] exists
     * @see get
     */
    @Readonly
    fun getText(text: String, language: String, activeMods: HashSet<String>? = null): String {
        return get(text, language, activeMods)?.get(language) ?: text
    }

    /**
     * Returns a specific translation, or [default] if no translation for [language] exists
     */
    @Readonly
    fun getText(text: String, language: String, activeMods: HashSet<String>? = null, default: String): String {
        return get(text, language, activeMods)?.get(language) ?: default
    }

    /** Get all languages present in `this`, used for [TranslationFileWriter] and `TranslationTests`
     *  * Note: No deterministic order. If a client needs that, remap to LanguageCode order or something.
     */
    fun getLanguages(): Set<String> = hashSetOf<String>().apply {
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
            if (!modTranslationFile.exists()) continue
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


    fun tryReadTranslationForCurrentLanguage() =
        tryReadTranslationForSpecificLanguage(UncivGame.Current.settings.language)

    fun tryReadTranslationForSpecificLanguage(language: String) {
        DiacriticSupport.reset()
        tryReadTranslationForLanguage(language)
        DiacriticSupport.freeTranslationData()
    }

    /** Get a list of supported languages for [readAllLanguagesTranslation] */
    private fun getLanguagesWithTranslationFile(): List<String> {
        val languages = LocaleCode.getSupportedLanguages()
        return languages.filter { Gdx.files.internal("jsons/translations/$it.properties").exists() }.toList()
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

    @Readonly
    fun getConditionalOrder(language: String) =
        getText(conditionalOrderingKey, language, null, defaultConditionalOrderingString)

    @Readonly
    fun placeConditionalsAfterUnique(language: String) =
        getText(conditionalPlacementKey, language, null, "") != "before"

    /** Returns the equivalent of a space in the given language
     * Defaults to a space if no translation is provided
     */
    @Readonly
    fun getSpaceEquivalent(language: String) =
        getText("\" \"", language, null).removeSurrounding("\"")

    @Readonly
    fun shouldCapitalize(language: String) =
        getText(shouldCapitalizeKey, language, null, "true").toBoolean()

    @Readonly
    fun triggerNotificationEffectBeforeCause(language: String) =
        getText(effectBeforeCauseKey, language, null, "true").toBoolean()

    companion object {
        @VisibleForTesting
        const val conditionalOrderingKey = "ConditionalsOrder"
        @VisibleForTesting
        const val defaultConditionalOrderingString =
            "<with a garrison> <for [mapUnitFilter] units> <when above [amount] HP> <when below [amount] HP> <vs cities> <vs [mapUnitFilter] units> <when fighting in [tileFilter] tiles> <when attacking> <when defending> <when at war> <when not at war> <while the empire is happy> <during a Golden Age> <during the [era]> <starting from the [era]> <before the [era]> <with [techOrPolicy]> <without [techOrPolicy]>"
        @VisibleForTesting
        const val conditionalPlacementKey = "ConditionalsPlacement"
        @VisibleForTesting
        const val shouldCapitalizeKey = "StartWithCapitalLetter"
        @VisibleForTesting
        const val effectBeforeCauseKey = "EffectBeforeCause"
    }
}
