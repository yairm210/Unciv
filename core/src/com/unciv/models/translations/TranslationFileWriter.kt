package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.civilization.diplomacy.Demand
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.models.SpyAction
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Event
import com.unciv.models.ruleset.GlobalUniques
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.RuinReward
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.Specialist
import com.unciv.models.ruleset.Speed
import com.unciv.models.ruleset.Tutorial
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Difficulty
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.nation.Personality
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.DeprecatedUniqueType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitNameGroup
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.utils.Log
import com.unciv.utils.debug
import com.unciv.utils.isRunFromJar
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

object TranslationFileWriter {

    private const val specialNewLineCode = "# This is an empty line "
    private const val languageFileLocation = "jsons/translations/%s.properties"
    private const val shortDescriptionKey = "Fastlane_short_description"
    private const val shortDescriptionFile = "short_description.txt"
    private const val fullDescriptionKey = "Fastlane_full_description"
    private const val fullDescriptionFile = "full_description.txt"
    // Current dir on desktop should be assets, so use two '..' get us to project root
    private const val fastlanePath = "../../fastlane/metadata/android/"

    // For preallocating collections only:
    /** Relation all lines including comments and duplidates to actual translation keys */
    private const val empiricLinesToKeysFactor = 1.86
    /** Relation all resulting lines including comments to actual translation keys */
    private const val empiricParsedToKeysFactor = 1.29

    private fun BaseRuleset.jsonFolderName() = "jsons/$fullName"
    private fun BaseRuleset.jsonFolder() = if (UncivGame.isCurrentInitialized())
            UncivGame.Current.files.getLocalFile(jsonFolderName())
        else Gdx.app.files.local(jsonFolderName())
    private fun defaultFileFilter(file: File) = file.name.endsWith(".json", true)

    //region Update translation files
    fun writeNewTranslationFiles(modSelection: String): String {
        val allSelected = modSelection == "All mods"
        val baseSelected = allSelected || BaseRuleset.entries.any { it.fullName == modSelection }

        try {
            val translations = Translations()
            translations.readAllLanguagesTranslation()
            val modSelected = !baseSelected && modSelection in translations.modsWithTranslations

            var fastlaneOutput = ""
            // check to make sure we're not running from a jar since these users shouldn't need to
            // regenerate base game translation and fastlane files
            if (baseSelected && !isRunFromJar(this)) {
                val percentages = generateTranslationFiles(translations)
                writeLanguagePercentages(percentages)
                fastlaneOutput = "\n" + writeTranslatedFastlaneFiles(translations)
            }

            fun processMod(modName: String, modTranslations: Translations) {
                val modFolder = UncivGame.Current.files.getModFolder(modName)
                val modPercentages = generateTranslationFiles(modTranslations, modFolder, translations)
                writeLanguagePercentages(modPercentages, modFolder)  // unused by the game but maybe helpful for the mod developer
            }
            if (allSelected) {
                // See #5168 for some background on this
                for ((modName, modTranslations) in translations.modsWithTranslations)
                    processMod(modName, modTranslations)
            } else if (modSelected) {
                processMod(modSelection, translations.modsWithTranslations[modSelection]!!)
            }

            return "Translation files are generated successfully.".tr() + fastlaneOutput
        } catch (ex: Throwable) {
            Log.error("Failed to generate translation files", ex)
            return ex.localizedMessage ?: ex.javaClass.simpleName
        }
    }

    private fun getFileHandle(modFolder: FileHandle?, fileLocation: String) =
            if (modFolder != null) modFolder.child(fileLocation)
            else UncivGame.Current.files.getLocalFile(fileLocation)

    /** One line from the merged translation source, parsed once and reused for every language. */
    private data class ParsedLine(
        val raw: String,
        val isTranslatable: Boolean,
        val translationKey: String = "",
        val hashMapKey: String = "",
        val defaultValue: String = ""
    )

    /** Result of resolving a single [ParsedLine] against one language's translations. */
    private data class LineResolution(
        val value: String,
        val isTranslated: Boolean,
        /** Whether this line should count towards the "total translatable lines" denominator. */
        val countsTowardTotal: Boolean
    )

    private val multipleNewlinesRegex = Regex("\n{4,}")

    /**
     * Writes new language files per Mod or for BaseRuleset - only each language that exists in [translations].
     * @param translations Result of Translations().readAllLanguagesTranslation(), base or mod-specific
     * @param modFolder Points to the mod root folder when processing a mod
     * @param baseTranslations For a mod, pass the base translations here so strings already existing there can be seen
     * @return A map with the percentages of translated lines per language
     */
    private fun generateTranslationFiles(
        translations: Translations,
        modFolder: FileHandle? = null,
        baseTranslations: Translations? = null
    ): HashMap<String, Int> {
        val linesToTranslate = ArrayList<String>((translations.size * empiricLinesToKeysFactor).roundToInt())
        val fileNameToGeneratedStrings: Map<String, Set<String>>

        if (modFolder == null) {
            linesToTranslate.collectTemplateLines()
            linesToTranslate.collectUniqueSystemLines()
            linesToTranslate.collectGameplayDataLines()
            fileNameToGeneratedStrings = collectBaseGameJsonStrings()
        } else {
            fileNameToGeneratedStrings = GenerateStringsFromJSONs(modFolder.child("jsons"))
        }
        linesToTranslate.appendGeneratedStringsSections(fileNameToGeneratedStrings)

        val parsedLines = parseLines(linesToTranslate)
        val languages = translations.getLanguages()

        // NOTE: The "total translatable lines" denominator is derived from a random language's
        // resolution state - but that's OK since countsTowardTotal isn't dependent on the language.
        val countOfTranslatableLines = if (languages.isEmpty()) 0
        else countTranslatableLines(parsedLines, languages.first(), translations, baseTranslations)

        val countOfTranslatedLines = HashMap<String, Int>()
        for (language in languages) {
            countOfTranslatedLines[language] =
                writeLanguageFile(language, parsedLines, translations, baseTranslations, modFolder)
        }

        // Calculate the percentages of translations
        for (entry in countOfTranslatedLines)
            entry.setValue(if (countOfTranslatableLines <= 0) 100 else entry.value * 100 / countOfTranslatableLines)

        return countOfTranslatedLines
    }

    private fun MutableList<String>.collectTemplateLines() {
        TranslationFileReader.readTemplates { addAll(it) }
    }

    /** Only for Base ruleset scanning */
    private fun MutableList<String>.collectUniqueSystemLines() {
        add("\n\n#################### Lines from Unique Types #######################\n")
        for (uniqueType in UniqueType.entries) {
            val deprecationAnnotation = uniqueType.getDeprecationAnnotation()
            if (deprecationAnnotation != null) continue
            if (uniqueType.flags.contains(UniqueFlag.HiddenToUsers)) continue
            add("${uniqueType.getTranslatable()} = ")
        }

        for (uniqueParameterType in UniqueParameterType.entries) {
            val strings = uniqueParameterType.getTranslationWriterStringsForOutput()
            if (strings.isEmpty()) continue
            add("\n######### ${uniqueParameterType.displayName} ###########\n")
            addAll(strings.map { "$it = " })
        }

        for (uniqueTarget in UniqueTarget.entries)
            add("$uniqueTarget = ")
    }

    private fun MutableList<String>.collectGameplayDataLines() {
        add("\n\n#################### Lines from Countables #######################\n")
        for (countable in Countables.entries)
            if (countable.text.isNotEmpty())
                add("${countable.text} = ")

        add("\n\n#################### Lines from spy actions #######################\n")
        for (spyAction in SpyAction.entries)
            add("${spyAction.displayString} = ")

        add("\n\n#################### Lines from diplomatic modifiers #######################\n")
        for (diplomaticModifier in DiplomaticModifiers.entries)
            add("${diplomaticModifier.text} = ")

        add("\n\n#################### Lines from demands #######################\n")
        for (demand in Demand.entries) {
            add("\n### ${demand.name} \n")
            val uiTexts = listOf(demand.demandText, demand.acceptDemandText, demand.refuseDemandText,
                demand.violationNoticedText, demand.agreedToDemandText, demand.refusedDemandText,
                demand.wePromisedText, demand.theyPromisedText)
            for (text in uiTexts)
                add("$text = ")
        }

        add("\n\n#################### Lines from personality biases #######################\n")
        for (focus in PersonalityValue.entries)
            add("${focus.description} = ")

        add("\n\n#################### Lines from key bindings #######################\n")
        for (bindingLabel in KeyboardBinding.getTranslationEntries())
            add("$bindingLabel = ")
    }

    /** @return A map of section headers to sets of translatables */
    private fun collectBaseGameJsonStrings(): LinkedHashMap<String, MutableSet<String>> {
        val fileNameToGeneratedStrings = LinkedHashMap<String, MutableSet<String>>()

        for (baseRuleset in BaseRuleset.entries) {
            val generatedStringsFromBaseRuleset = GenerateStringsFromJSONs(baseRuleset)
            for (entry in generatedStringsFromBaseRuleset)
                fileNameToGeneratedStrings[entry.key + " from " + baseRuleset.fullName] = entry.value
        }

        // Global Tutorials reside one level above the base rulesets - if we had only per-ruleset tutorials the following lines would be unnecessary
        val tutorialStrings = GenerateStringsFromJSONs(UncivGame.Current.files.getLocalFile("jsons")) { it.name == "Tutorials.json" }
        fileNameToGeneratedStrings["Global Tutorials"] = tutorialStrings.values.first()

        return fileNameToGeneratedStrings
    }

    private fun MutableList<String>.appendGeneratedStringsSections(
        fileNameToGeneratedStrings: Map<String, Set<String>>
    ) {
        for ((key, value) in fileNameToGeneratedStrings) {
            if (value.isEmpty()) continue
            add("\n#################### Lines from $key ####################\n")
            addAll(value)
        }
    }

    @Pure private fun String.toHashKey() =
        replace(pointyBraceRegex, "")
        .replace(squareBraceRegex, "[]")
    @Pure private fun String.unEscapeNewLines() =
        replace("\\n", "\n")
    @Pure private fun String.escapeNewLines() =
        replace("\n", "\\n")

        /**
     * Parses the flat [linesToTranslate] into structured, deduplicated [ParsedLine]s exactly once
     */
    private fun parseLines(linesToTranslate: List<String>): List<ParsedLine> {
        val expectedCount = (linesToTranslate.size / empiricLinesToKeysFactor * empiricParsedToKeysFactor).roundToInt()
        val existingTranslationKeys = HashSet<String>(expectedCount)
        val result = ArrayList<ParsedLine>(expectedCount)

        for (line in linesToTranslate) {
            if (!line.contains(" = ")) {
                result += ParsedLine(raw = line, isTranslatable = false)
                continue
            }

            val eqIndex = line.indexOf(" = ")
            val translationKey = line.substring(0, eqIndex).unEscapeNewLines()
            val hashMapKey = translationKey.toHashKey()
            if (!existingTranslationKeys.add(hashMapKey)) continue // don't add it twice

            val defaultValue = line.substring(eqIndex + 3)
            result += ParsedLine(line, isTranslatable = true, translationKey, hashMapKey, defaultValue)
        }

        return result
    }

    /** Resolves what value (if any) [parsedLine] should get for [language]. Null means "skip entirely". */
    private fun resolveLine(
        parsedLine: ParsedLine,
        language: String,
        translations: Translations,
        baseTranslations: Translations?
    ): LineResolution? {
        val countsTowardTotal = parsedLine.translationKey != Translations.conditionalOrderingKey

        val existingTranslation = translations[parsedLine.hashMapKey]
        if (existingTranslation != null && language in existingTranslation) {
            return LineResolution(existingTranslation[language]!!, isTranslated = true, countsTowardTotal)
        }

        if (baseTranslations?.get(parsedLine.hashMapKey)?.containsKey(language) == true) {
            // String is used in the mod but also exists in base - ignore
            return null
        }

        if (parsedLine.defaultValue.isNotEmpty()) {
            // We could treat this as not translated/not counting, so pre-translatables would not
            // count towards completion percentages at all
            return LineResolution(parsedLine.defaultValue, isTranslated = true, countsTowardTotal)
        }

        // String is not translated either here or in base
        return LineResolution("", isTranslated = false, countsTowardTotal)
    }

    private fun countTranslatableLines(
        parsedLines: List<ParsedLine>,
        referenceLanguage: String,
        translations: Translations,
        baseTranslations: Translations?
    ): Int {
        var count = 0
        for (parsedLine in parsedLines) {
            if (!parsedLine.isTranslatable) continue
            val resolution = resolveLine(parsedLine, referenceLanguage, translations, baseTranslations) ?: continue
            if (resolution.countsTowardTotal) count++
        }
        return count
    }

    private fun writeLanguageFile(
        language: String,
        parsedLines: List<ParsedLine>,
        translations: Translations,
        baseTranslations: Translations?,
        modFolder: FileHandle?
    ): Int {
        var translationsOfThisLanguage = 0
        val stringBuilder = StringBuilder(parsedLines.size * 40) // rough average line length, avoids resizing too often

        // When treating a Mod, ensure we don't delete their work for missing json-generated keys
        val oldTranslationsForLanguage = mutableSetOf<String>()
        if (baseTranslations != null)
            for (entry in translations)
                if (language in entry.value) oldTranslationsForLanguage.add(entry.key)

        for (parsedLine in parsedLines) {
            if (!parsedLine.isTranslatable) {
                // small hack to insert empty lines
                if (parsedLine.raw.startsWith(specialNewLineCode))
                    stringBuilder.appendLine()
                else // copy as-is
                    stringBuilder.appendLine(parsedLine.raw)
                continue
            }

            oldTranslationsForLanguage.remove(parsedLine.hashMapKey)

            val resolution = resolveLine(parsedLine, language, translations, baseTranslations) ?: continue

            if (resolution.isTranslated) {
                translationsOfThisLanguage++
            } else if (resolution.countsTowardTotal) {
                stringBuilder.appendLine(" # Requires translation!")
            }

            val translationValue = autocorrectSingleParamMismatch(parsedLine.translationKey, resolution.value)
            stringBuilder.appendTranslation(parsedLine.translationKey, translationValue)
        }

        val file = getFileHandle(modFolder, languageFileLocation.format(language))

        // Ensure we don't lose any modder's work that is missing templates
        if (oldTranslationsForLanguage.isNotEmpty())
            appendUnusedOldTranslations(stringBuilder, file, oldTranslationsForLanguage)

        // Any time you have more than 3 line breaks, make it 3
        val finalFileText = stringBuilder.toString().replace(multipleNewlinesRegex, "\n\n\n")
        file.writeString(finalFileText, false, TranslationFileReader.charset)

        return translationsOfThisLanguage
    }

    /**
     * Fixes [translationValue] when a placeholder name mistmatches the one in [translationKey].
     *
     * ##### THE PROBLEM
     * When we come to change params written in the TranslationFileWriter,
     * this messes up the param name matching in existing translations.
     * Tests fail and much manual work was required.
     *
     * SO, as a fix, for each translation where a single param is different than in the source line,
     * we try to autocorrect it.
     *
     * @return Usually, [translationValue] unchanged, but if necessary, with one placeholder name replaced
     */
    private fun autocorrectSingleParamMismatch(translationKey: String, translationValue: String): String {
        if (!translationValue.contains('[')) return translationValue

        val paramsOfKey = translationKey.getPlaceholderParameters()
        val paramsOfValue = translationValue.getPlaceholderParameters()
        val paramsOfKeyNotInValue = paramsOfKey.filterNot { it in paramsOfValue }
        val paramsOfValueNotInKey = paramsOfValue.filterNot { it in paramsOfKey }

        if (paramsOfKeyNotInValue.size != 1 || paramsOfValueNotInKey.size != 1) return translationValue

        return translationValue.replace(
            "[" + paramsOfValueNotInKey.first() + "]",
            "[" + paramsOfKeyNotInValue.first() + "]"
        )
    }

    private fun appendUnusedOldTranslations(
        stringBuilder: StringBuilder,
        file: FileHandle,
        keysToPreserve: Set<String>
    ) {
        stringBuilder.appendLine()
        stringBuilder.appendLine("#################### Possibly unused ####################")
        stringBuilder.appendLine("# These were found in the original translation file, but not as required translation from the json scan.")
        stringBuilder.appendLine()

        val oldTranslations = TranslationFileReader.read(file)
        val oldEntriesWithHashKey = oldTranslations.entries
            .associateBy { it.key.toHashKey() }
        for ((hashKey, entry) in oldEntriesWithHashKey) {
            if (hashKey !in keysToPreserve) continue
            stringBuilder.appendTranslation(entry.key, entry.value)
        }
    }

    @Pure
    private fun StringBuilder.appendTranslation(key: String, value: String) {
        appendLine(key.escapeNewLines() + " = " + value.escapeNewLines())
    }

    private fun writeLanguagePercentages(percentages: HashMap<String, Int>, modFolder: FileHandle? = null) {
        val comment = "# Automatically generated - ${if (modFolder == null) "do not edit manually" else "for your info only"}\n\n"
        val output = percentages.asSequence()
            .sortedBy { it.key }
            .joinToString("\n", prefix = comment, postfix = "\n") { "${it.key} = ${it.value}" }
        getFileHandle(modFolder, TranslationFileReader.percentagesFileLocation)
            .writeString(output, false)
    }

    @VisibleForTesting
    fun getGeneratedStringsSize(): Int {
        return GenerateStringsFromJSONs(Gdx.files.local("jsons/Civ V - Vanilla")).values.sumOf {
            // exclude empty lines
            it.count { line: String -> !line.startsWith(specialNewLineCode) }
        }
    }

    private fun UniqueType.getTranslatable(): String {
        // to get rid of multiple equal parameters, like "[amount] [amount]", don't use the unique.text directly
        //  instead fill the placeholders with incremented values if the previous one exists
        val newPlaceholders = ArrayList<String>()
        for (placeholderText in text.getPlaceholderParameters()) {
            newPlaceholders.addNumberedParameter(placeholderText)
        }
        return text.fillPlaceholders(*newPlaceholders.toTypedArray())
    }

    private fun DeprecatedUniqueType.getTranslatable(): String {
        val newPlaceholders = ArrayList<String>()
        for (placeholderText in text.getPlaceholderParameters()) {
            newPlaceholders.addNumberedParameter(placeholderText)
        }
        return text.fillPlaceholders(*newPlaceholders.toTypedArray())
    }

    private fun ArrayList<String>.addNumberedParameter(name: String) {
        if (name !in this) {
            this += name
            return
        }
        var i = 2
        while (name + i in this) i++
        this += name + i
    }

    /** This scans one folder for json files and generates lines ***to translate*** (left side).
     *  All work is done right on instantiation.
      */
    private class GenerateStringsFromJSONs(
        /** Used only to call UniqueParameterType.guessTypeForTranslationWriter */
        private val ruleset: Ruleset,
        jsonsFolder: FileHandle,
        fileFilter: (File) -> Boolean
    ): LinkedHashMap<String, MutableSet<String>>() {
        // Using LinkedHashMap (instead of HashMap) is important to maintain the order of sections in the translation file

        constructor(baseRuleset: BaseRuleset) : this(
            RulesetCache[baseRuleset.fullName]!!,
            baseRuleset.jsonFolder(),
            ::defaultFileFilter
        )
        constructor(jsonsFolder: FileHandle, fileFilter: (File) -> Boolean = ::defaultFileFilter) : this(
            RulesetCache[jsonsFolder.parent().name()]?.takeIf { it.modOptions.isBaseRuleset } ?: RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!,
            jsonsFolder, fileFilter
        )

        // One set per json file, secondary loop var. Could be nicer to isolate all per-file
        // processing into another class, but then we'd have to pass uniqueIndexOfNewLine around.
        lateinit var resultStrings: MutableSet<String>

        init {
            val startMillis = System.currentTimeMillis()

            var uniqueIndexOfNewLine = 0
            fun addNewLine() {
                // This is a small hack to insert multiple /n into the set, which can't contain identical lines
                resultStrings.add("$specialNewLineCode ${uniqueIndexOfNewLine++}")
            }

            val listOfJSONFiles = jsonsFolder
                .list(fileFilter)
                .sortedBy { it.name() }       // generatedStrings maintains order, so let's feed it a predictable one

            for (jsonFile in listOfJSONFiles) {
                val filename = jsonFile.nameWithoutExtension()

                val javaClass = getJavaClassByName(filename)
                    ?: continue // unknown JSON, let's skip it

                val data = json().fromJsonFile(javaClass, jsonFile.path())

                resultStrings = mutableSetOf()
                this[filename] = resultStrings

                @Suppress("RemoveRedundantQualifierName")  // to clarify this is the kotlin way
                if (data is kotlin.Array<*>) {
                    for (element in data) {
                        serializeElement(element!!) // let's serialize the strings recursively
                        addNewLine()
                    }
                } else {
                    serializeElement(data)
                    addNewLine()
                }
            }
            val displayName = if (jsonsFolder.name() != "jsons") jsonsFolder.name()
                else jsonsFolder.parent().name().ifEmpty { "Tutorials" }  // Show mod name - or special case
            debug("Translation writer took %sms for %s", System.currentTimeMillis() - startMillis, displayName)
        }

        fun submitString(string: String) {
            if (string.isEmpty()) return  // entries in Collection<String> do not pass isFieldTranslatable
            if ('{' in string) {
                val matches = curlyBraceRegex.findAll(string)
                if (matches.any()) {
                    // Ignore outer string, only translate the parts within `{}`
                    matches.forEach { submitString(it.groups[1]!!.value) }
                    return
                }
            }
            resultStrings.add("$string = ")
        }

        fun submitPretranslatableString(string: String) {
            if (string.isEmpty()) return
            resultStrings.add("$string = $string")
        }

        fun submitString(string: String, unique: Unique) {
            if (unique.isHiddenToUsers())
                return // We don't need to translate this at all, not user-visible

            val stringToTranslate = string.removeConditionals()
            for (conditional in unique.modifiers) {
                submitString(conditional.text, conditional)
            }

            if (unique.params.isEmpty()) {
                submitString(stringToTranslate)
                return
            }

            // Do simpler parameter numbering when typed, as the code below is susceptible to problems with nested brackets - UniqueTypes don't have them (yet)!
            if (unique.type != null)
                return submitTypedUnique(unique)

            if (unique.deprecatedType != null) {
                resultStrings.add("${unique.deprecatedType.getTranslatable()} = ")
                return
            }

            val parameterNames = ArrayList<String>()
            for (parameter in unique.params) {
                val parameterName = UniqueParameterType.guessTypeForTranslationWriter(parameter, ruleset).parameterName
                parameterNames.addNumberedParameter(parameterName)
                if (translatableUniqueParameterTypes.none { it.parameterName == parameterName }) continue
                resultStrings.add("$parameter = ")
            }
            resultStrings.add("${stringToTranslate.fillPlaceholders(*parameterNames.toTypedArray())} = ")
        }

        fun submitTypedUnique(unique: Unique) {
            require(unique.type != null)
            for ((index, typeList) in unique.type.parameterTypeMap.withIndex()) {
                if (typeList.none { it in translatableUniqueParameterTypes }) continue
                // Unknown/Comment parameter contents better be offered to translators too
                if (unique.type == UniqueType.Comment) {
                    val subUnique = Unique(unique.params[index])
                    if (subUnique.type != null)
                        return submitTypedUnique(subUnique)
                }
                resultStrings.add("${unique.params[index]} = ")
            }
            resultStrings.add("${unique.type.getTranslatable()} = ")
        }

        // Example: PolicyBranch inherits from Policy inherits from RulesetObject.
        // RulesetObject has the name and uniques properties and we wish to include them.
        // So we need superclass recursion to be sure not to miss stuff in the future.
        // The superclass != null check is made obsolete in theory by the Any check, but better play safe.
        fun Class<*>.allSupers(): Sequence<Class<*>> = sequence {
            if (this@allSupers == Any::class.java) return@sequence
            yield(this@allSupers)
            if (superclass != null)
                yieldAll(superclass.allSupers())
        }

        fun serializeElement(element: Any) {
            if (element is String) {
                submitString(element)
                return
            }

            // Including `fields` is dubious. AFAIK it is an incomplete view of what we're getting
            // anyway via superclass recursion. But since we now do a distinct() it won't hurt.
            val allFields = element.javaClass.fields.asSequence() +
                    element.javaClass.allSupers().flatMap { it.declaredFields.asSequence() }
            // Filter by classes we can and want to process, avoid Companion fields
            // Note lazies are Modifier.TRANSIENT and type == kotlin.Lazy
            val relevantFields = allFields.filter {
                    (it.modifiers and (Modifier.STATIC or Modifier.TRANSIENT)) == 0 &&
                    isFieldTypeRelevant(it.type)
                    // it.type != element.javaClass  // avoid following infinite loops - redundant as isFieldTypeRelevant won't match
                }.distinct()  // We do get duplicates even without `fields`, no need to do double work, even if submitString operates on a set

            for (field in relevantFields) {
                field.isAccessible = true
                val fieldValue = field.get(element)
                // skip fields which must not be translated
                if (!isFieldTranslatable(element.javaClass, field, fieldValue)) continue
                // Some Tutorial names need no translation
                if (element is Tutorial && field.name == "name"
                        && UniqueType.HiddenFromCivilopedia.placeholderText in element.uniques)
                    continue
                val isPreTranslatable = isFieldPreTranslatable(element.javaClass, field)
                val isParameterized = isFieldParameterized(element.javaClass, field)
                fun submitCollectionItem(item: Any?) {
                    if (item === null) return
                    if (item !is String) serializeElement(item)
                    else if (isPreTranslatable) submitPretranslatableString(item)
                    else if (isParameterized) submitString(item, Unique(item))
                    else submitString(item)
                }
                // this field can contain sub-objects, let's serialize them as well
                @Suppress("RemoveRedundantQualifierName")  // to clarify List does _not_ inherit from anything in java.util
                when {
                    fieldValue is java.util.AbstractCollection<*> ->
                        for (item in fieldValue)
                            submitCollectionItem(item)
                    fieldValue is kotlin.collections.List<*> ->
                        for (item in fieldValue)
                            submitCollectionItem(item)
                    isParameterized && (fieldValue is String) ->
                        submitString(fieldValue, Unique(fieldValue))
                    else -> submitString(fieldValue.toString())
                }
            }
        }

        companion object {
            /** Exclude fields by name that contain references to items defined elsewhere
             * or are otherwise Strings but not user-displayed.
             *
             * An exclusion applies either over _all_ json files and all classes contained in them
             * or Class-specific by using a "Class.Field" notation.
             */
            private val untranslatableFieldSet = setOf(
                "aiFreeTechs", "aiFreeUnits", "attackSound", "building", "cannotBeBuiltWith",
                "cultureBuildings", "excludedDifficulties", "improvement", "improvingTech",
                "obsoleteTech", "occursOn", "prerequisites", "promotions",
                "providesFreeBuilding", "replaces", "requiredBuilding", "requiredBuildingInAllCities",
                "requiredNearbyImprovedResources", "requiredResource", "requiredTech", "requires",
                "revealedBy", "startBias", "techRequired", "terrainsCanBeBuiltOn",
                "terrainsCanBeFoundOn", "turnsInto", "uniqueTo", "upgradesTo",
                "link", "icon", "extraImage", "color",  // FormattedLine
                "RuinReward.uniques", "TerrainType.name",
                "CityStateType.friendBonusUniques", "CityStateType.allyBonusUniques",
                "Era.citySound",
                "keyShortcut",
                "originRuleset"
            )

            /** Fields that are collections of simple strings that will be offered pre-translated.
             *  Matching rules as in [untranslatableFieldSet].
             *
             *  Note This is determined in [GenerateStringsFromJSONs], then communicated to [generateTranslationFiles]
             *  by emitting e.g. 'Paris = Paris' instead of 'Paris = '. [generateTranslationFiles] then detects that and ensures
             *  it's written out like that and not commented with " # Requires translation!".
             */
            private val preTranslatableFieldSet = setOf(
                "Nation.cities",
                "Nation.spyNames",
                "UnitNameGroup.unitNames"
            )

            /** Specifies Enums where the name property _is_ translatable, by Class name */
            private val translatableEnumsSet = setOf("BeliefType")

            /** Only these Unique parameter types will be offered as translatables - for all others it is expected their content
             *  corresponds with a ruleset object name which will be made translatable by their actual json definition */
            private val translatableUniqueParameterTypes = setOf(
                UniqueParameterType.Unknown,
                UniqueParameterType.Comment
            )

            private val fieldsToProcessParameters = setOf(
                "uniques",
                // Promotion names are not uniques but since we did the "[unitName] ability"
                // they need the "parameters" treatment too
                "Promotion.name",
                "promotions",
                // Same for victory milestones
                "milestones",
                // e.g. "See also: [rulesetobject]" in civilopediaText
                "FormattedLine.text",
            )

            @Readonly
            private fun isFieldTypeRelevant(type: Class<*>) =
                    type == String::class.java ||
                    type == java.util.ArrayList::class.java ||
                    type == List::class.java ||        // CivilopediaText is not an ArrayList
                    type == java.util.HashSet::class.java ||
                    type.isEnum  // allow scanning Enum names

            /** Checks whether a field's value should be included in the translation templates.
             * Applies explicit field exclusions from [untranslatableFieldSet].
             * The Modifier.STATIC exclusion removes fields from e.g. companion objects.
             * Fields of enum types need that type explicitly allowed in [translatableEnumsSet]
             */
            private fun isFieldTranslatable(clazz: Class<*>, field: Field, fieldValue: Any?): Boolean {
                return fieldValue != null &&
                        fieldValue != "" &&
                        (!field.type.isEnum || field.type.simpleName in translatableEnumsSet) &&
                        !containsPotentiallyQualifiedName(untranslatableFieldSet, clazz, field)
            }

            /** Checks whether a field's content should be marked as "pre-translatable", meaning if it's not yet translated for a language,
             *  then `thing = thing` gets written instead of `# requires translation`...
             *  ONLY applies to collections of simple strings which can't contain {} placeholders.
             */
            private fun isFieldPreTranslatable(clazz: Class<*>, field: Field) =
                containsPotentiallyQualifiedName(preTranslatableFieldSet, clazz, field)

            /** Checks whether a field content should be treated as translatable as-is, or whether to potentially expect parameters */
            private fun isFieldParameterized(clazz: Class<*>, field: Field) =
                containsPotentiallyQualifiedName(fieldsToProcessParameters, clazz, field)

            private fun containsPotentiallyQualifiedName(set: Set<String>, clazz: Class<*>, field: Field) =
                field.name in set || (clazz.componentType?.simpleName ?: clazz.simpleName) + "." + field.name in set

            private fun getJavaClassByName(name: String): Class<Any>? {
                return when (name) {
                    "Beliefs" -> emptyArray<Belief>().javaClass
                    "Buildings" -> emptyArray<Building>().javaClass
                    "CityStateTypes" -> emptyArray<CityStateType>().javaClass
                    "Difficulties" -> emptyArray<Difficulty>().javaClass
                    "Eras" -> emptyArray<Era>().javaClass
                    "Events" -> emptyArray<Event>().javaClass
                    "GlobalUniques" -> GlobalUniques().javaClass
                    "UnitNameGroups" -> emptyArray<UnitNameGroup>().javaClass
                    "Nations" -> emptyArray<Nation>().javaClass
                    "Personalities" -> emptyArray<Personality>().javaClass
                    "Policies" -> emptyArray<PolicyBranch>().javaClass
                    "Quests" -> emptyArray<Quest>().javaClass
                    "Religions" -> emptyArray<String>().javaClass
                    "Ruins" -> emptyArray<RuinReward>().javaClass
                    "Specialists" -> emptyArray<Specialist>().javaClass
                    "Speeds" -> emptyArray<Speed>().javaClass
                    "Techs" -> emptyArray<TechColumn>().javaClass
                    "Terrains" -> emptyArray<Terrain>().javaClass
                    "TileImprovements" -> emptyArray<TileImprovement>().javaClass
                    "TileResources" -> emptyArray<TileResource>().javaClass
                    "Tutorials" -> emptyArray<Tutorial>().javaClass
                    "UnitPromotions" -> emptyArray<Promotion>().javaClass
                    "Units" -> emptyArray<BaseUnit>().javaClass
                    "UnitTypes" -> emptyArray<UnitType>().javaClass
                    "VictoryTypes" -> emptyArray<Victory>().javaClass
                    else -> null // dummy value
                }
            }
        }
    }

    //endregion
    //region Fastlane

    /** This writes translated short_description.txt and full_description.txt files into the Fastlane structure.
     *  @param [translations] A [Translations] instance with all languages loaded.
     *  @return Success or error message.
     */
    private fun writeTranslatedFastlaneFiles(translations: Translations): String {
        @Suppress("LiftReturnOrAssignment")  // clearer with explicit returns
        try {
            writeFastlaneFiles(shortDescriptionFile, translations[shortDescriptionKey], false)
            writeFastlaneFiles(fullDescriptionFile, translations[fullDescriptionKey], true)
            updateFastlaneChangelog()

            return "Fastlane files are generated successfully.".tr()
        } catch (ex: Throwable) {
            Log.error("Failed to generate fastlane files", ex)
            return ex.localizedMessage ?: ex.javaClass.simpleName
        }
    }

    private fun writeFastlaneFiles(fileName: String, translationEntry: TranslationEntry?, endWithNewline: Boolean) {
        if (translationEntry == null) return
        for ((language, translated) in translationEntry) {
            val fileContent = when {
                endWithNewline && !translated.endsWith('\n') -> translated + '\n'
                !endWithNewline && translated.endsWith('\n') -> translated.removeSuffix("\n")
                else -> translated
            }
            val path = fastlanePath + LocaleCode.fastlaneFolder(language)
            File(path).mkdirs()
            File(path + File.separator + fileName).writeText(fileContent)
        }
    }

    // Original changelog entry, written by incrementVersionAndChangelog, is often changed manually for readability.
    // This updates the fastlane changelog entry to match the latest one in changelog.md
    private fun updateFastlaneChangelog() {
        // Relative path since we're in android/assets
        val changelogFile = File("../../changelog.md").readText()
        //  changelogs by definition do not have #'s in them so we can use it as a delimiter
        val latestVersionRegexGroup = Regex("## \\S*([^#]*)").find(changelogFile)
        val versionChangelog = latestVersionRegexGroup!!.groups[1]!!.value.trim()

        val buildConfigFile = File("../../buildSrc/src/main/kotlin/BuildConfig.kt").readText()
        val versionNumber =
            Regex("appCodeNumber = (\\d*)").find(buildConfigFile)!!.groups[1]!!.value

        val fileName = "$fastlanePath/en-US/changelogs/$versionNumber.txt"
        File(fileName).writeText(versionChangelog)
    }
    //endregion
}
