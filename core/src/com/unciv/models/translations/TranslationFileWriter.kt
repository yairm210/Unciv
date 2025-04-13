package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
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
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.Specialist
import com.unciv.models.ruleset.Speed
import com.unciv.models.ruleset.Tutorial
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Difficulty
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.utils.Log
import com.unciv.utils.debug
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object TranslationFileWriter {

    private const val specialNewLineCode = "# This is an empty line "
    const val templateFileLocation = "jsons/translations/template.properties"
    private const val languageFileLocation = "jsons/translations/%s.properties"
    private const val shortDescriptionKey = "Fastlane_short_description"
    private const val shortDescriptionFile = "short_description.txt"
    private const val fullDescriptionKey = "Fastlane_full_description"
    private const val fullDescriptionFile = "full_description.txt"
    // Current dir on desktop should be assets, so use two '..' get us to project root
    private const val fastlanePath = "../../fastlane/metadata/android/"

    //region Update translation files
    fun writeNewTranslationFiles(): String {
        try {
            val translations = Translations()
            translations.readAllLanguagesTranslation()

            var fastlaneOutput = ""
            // check to make sure we're not running from a jar since these users shouldn't need to
            // regenerate base game translation and fastlane files
            if (TranslationFileWriter.javaClass.`package`.specificationVersion == null) {
                val percentages = generateTranslationFiles(translations)
                writeLanguagePercentages(percentages)
                fastlaneOutput = "\n" + writeTranslatedFastlaneFiles(translations)
            }

            // See #5168 for some background on this
            for ((modName, modTranslations) in translations.modsWithTranslations) {
                val modFolder = UncivGame.Current.files.getModFolder(modName)
                val modPercentages = generateTranslationFiles(modTranslations, modFolder, translations)
                writeLanguagePercentages(modPercentages, modFolder)  // unused by the game but maybe helpful for the mod developer
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

    /**
     * Writes new language files per Mod or for BaseRuleset - only each language that exists in [translations].
     * @param baseTranslations For a mod, pass the base translations here so strings already existing there can be seen
     * @return a map with the percentages of translated lines per language
     */
    private fun generateTranslationFiles(
        translations: Translations,
        modFolder: FileHandle? = null,
        baseTranslations: Translations? = null
    ): HashMap<String, Int> {

        val fileNameToGeneratedStrings = LinkedHashMap<String, MutableSet<String>>()
        val linesToTranslate = mutableListOf<String>()

        if (modFolder == null) { // base game
            val templateFile = getFileHandle(null, templateFileLocation) // read the template
            if (templateFile.exists())
                linesToTranslate.addAll(templateFile.reader(TranslationFileReader.charset).readLines())

            linesToTranslate += "\n\n#################### Lines from Unique Types #######################\n"
            for (uniqueType in UniqueType.entries) {
                val deprecationAnnotation = uniqueType.getDeprecationAnnotation()
                if (deprecationAnnotation != null) continue
                if (uniqueType.flags.contains(UniqueFlag.HiddenToUsers)) continue

                linesToTranslate += "${uniqueType.getTranslatable()} = "
            }

            for (uniqueParameterType in UniqueParameterType.entries) {
                val strings = uniqueParameterType.getTranslationWriterStringsForOutput()
                if (strings.isEmpty()) continue
                linesToTranslate += "\n######### ${uniqueParameterType.displayName} ###########\n"
                linesToTranslate.addAll(strings.map { "$it = " })
            }

            for (uniqueTarget in UniqueTarget.entries)
                linesToTranslate += "$uniqueTarget = "

            linesToTranslate += "\n\n#################### Lines from Countables #######################\n"
            for (countable in Countables.entries)
                if (countable.text.isNotEmpty())
                    linesToTranslate += "${countable.text} = "

            linesToTranslate += "\n\n#################### Lines from spy actions #######################\n"
            for (spyAction in SpyAction.entries)
                linesToTranslate += "${spyAction.displayString} = "

            linesToTranslate += "\n\n#################### Lines from diplomatic modifiers #######################\n"
            for (diplomaticModifier in DiplomaticModifiers.entries)
                linesToTranslate += "${diplomaticModifier.text} = "

            linesToTranslate += "\n\n#################### Lines from key bindings #######################\n"
            for (bindingLabel in KeyboardBinding.getTranslationEntries())
                linesToTranslate += "$bindingLabel = "

            for (baseRuleset in BaseRuleset.entries) {
                val generatedStringsFromBaseRuleset =
                        GenerateStringsFromJSONs(UncivGame.Current.files.getLocalFile("jsons/${baseRuleset.fullName}"))
                for (entry in generatedStringsFromBaseRuleset)
                    fileNameToGeneratedStrings[entry.key + " from " + baseRuleset.fullName] = entry.value
            }

            // Tutorials reside one level above the base rulesets - if they were per-ruleset the following lines would be unnecessary
            val tutorialStrings = GenerateStringsFromJSONs(UncivGame.Current.files.getLocalFile("jsons")) { it.name == "Tutorials.json" }
            fileNameToGeneratedStrings["Tutorials"] = tutorialStrings.values.first()
        } else {
            fileNameToGeneratedStrings.putAll(GenerateStringsFromJSONs(modFolder.child("jsons")))
        }

        for ((key, value) in fileNameToGeneratedStrings) {
            if (value.isEmpty()) continue
            linesToTranslate += "\n#################### Lines from $key ####################\n"
            linesToTranslate.addAll(value)
        }
        fileNameToGeneratedStrings.clear()  // No longer needed

        var countOfTranslatableLines = 0
        val countOfTranslatedLines = HashMap<String, Int>()

        // iterate through all available languages
        for ((languageIndex, language) in translations.getLanguages().withIndex()) {
            var translationsOfThisLanguage = 0
            val stringBuilder = StringBuilder()

            // This is so we don't add the same keys twice if we have the same value in both Vanilla and G&K
            val existingTranslationKeys = HashSet<String>()

            for (line in linesToTranslate) {
                if (!line.contains(" = ")) {
                    // small hack to insert empty lines
                    if (line.startsWith(specialNewLineCode)) {
                        stringBuilder.appendLine()
                    } else // copy as-is
                        stringBuilder.appendLine(line)
                    continue
                }

                val translationKey = line.split(" = ")[0].replace("\\n", "\n")
                val hashMapKey =
                    if (translationKey == Translations.englishConditionalOrderingString)
                        Translations.englishConditionalOrderingString
                    else translationKey
                        .replace(pointyBraceRegex, "")
                        .replace(squareBraceRegex, "[]")

                if (existingTranslationKeys.contains(hashMapKey)) continue // don't add it twice
                existingTranslationKeys.add(hashMapKey)

                // count translatable lines only once
                if (languageIndex == 0) countOfTranslatableLines++

                val existingTranslation = translations[hashMapKey]
                var translationValue = if (existingTranslation != null && language in existingTranslation) {
                    translationsOfThisLanguage++
                    existingTranslation[language]!!
                } else if (baseTranslations?.get(hashMapKey)?.containsKey(language) == true) {
                    // String is used in the mod but also exists in base - ignore
                    continue
                } else {
                    // String is not translated either here or in base
                    stringBuilder.appendLine(" # Requires translation!")
                    ""
                }

                // THE PROBLEM
                // When we come to change params written in the TranslationFileWriter,
                //  this messes up the param name matching in existing translations.
                // Tests fail and much manual work was required.
                // SO, as a fix, for each translation where a single param is different than in the source line,
                // we try to autocorrect it.
                if (translationValue.contains('[')) {
                    val paramsOfKey = translationKey.getPlaceholderParameters()
                    val paramsOfValue = translationValue.getPlaceholderParameters()
                    val paramsOfKeyNotInValue = paramsOfKey.filterNot { it in paramsOfValue }
                    val paramsOfValueNotInKey = paramsOfValue.filterNot { it in paramsOfKey }
                    if (paramsOfKeyNotInValue.size == 1 && paramsOfValueNotInKey.size == 1)
                        translationValue = translationValue.replace(
                            "[" + paramsOfValueNotInKey.first() + "]",
                            "[" + paramsOfKeyNotInValue.first() + "]"
                        )
                }

                stringBuilder.appendTranslation(translationKey, translationValue)
            }

            countOfTranslatedLines[language] = translationsOfThisLanguage

            val fileWriter = getFileHandle(modFolder, languageFileLocation.format(language))
            // Any time you have more than 3 line breaks, make it 3
            val finalFileText = stringBuilder.toString().replace(Regex("\n{4,}"),"\n\n\n")
            fileWriter.writeString(finalFileText, false, TranslationFileReader.charset)
        }

        // Calculate the percentages of translations
        // It should be done after the loop of languages, since the countOfTranslatableLines is not known in the 1st iteration
        for (entry in countOfTranslatedLines)
            entry.setValue(if (countOfTranslatableLines <= 0) 100 else entry.value * 100 / countOfTranslatableLines)

        return countOfTranslatedLines
    }

    private fun StringBuilder.appendTranslation(key: String, value: String) {
        appendLine(key.replace("\n", "\\n") +
                " = " + value.replace("\n", "\\n"))
    }

    private fun writeLanguagePercentages(percentages: HashMap<String, Int>, modFolder: FileHandle? = null) {
        val output = percentages.asSequence()
            .sortedBy { it.key }
            .joinToString("\n", postfix = "\n") { "${it.key} = ${it.value}" }
        getFileHandle(modFolder, TranslationFileReader.percentagesFileLocation)
            .writeString(output, false)
    }

    // used for unit test only
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
        jsonsFolder: FileHandle,
        fileFilter: (File) -> Boolean = { file -> file.name.endsWith(".json", true) }
    ): LinkedHashMap<String, MutableSet<String>>() {
        // Using LinkedHashMap (instead of HashMap) is important to maintain the order of sections in the translation file

        val ruleset = RulesetCache.getVanillaRuleset()
        val startMillis = System.currentTimeMillis()

        var uniqueIndexOfNewLine = 0
        val listOfJSONFiles = jsonsFolder
            .list(fileFilter)
            .sortedBy { it.name() }       // generatedStrings maintains order, so let's feed it a predictable one

        // One set per json file, secondary loop var. Could be nicer to isolate all per-file
        // processing into another class, but then we'd have to pass uniqueIndexOfNewLine around.
        lateinit var resultStrings: MutableSet<String>

        init {
            for (jsonFile in listOfJSONFiles) {
                val filename = jsonFile.nameWithoutExtension()

                val javaClass = getJavaClassByName(filename)
                    ?: continue // unknown JSON, let's skip it

                val array = json().fromJsonFile(javaClass, jsonFile.path())

                resultStrings = mutableSetOf()
                this[filename] = resultStrings

                @Suppress("RemoveRedundantQualifierName")  // to clarify this is the kotlin way
                if (array is kotlin.Array<*>)
                    for (element in array) {
                        serializeElement(element!!) // let's serialize the strings recursively
                        // This is a small hack to insert multiple /n into the set, which can't contain identical lines
                        resultStrings.add("$specialNewLineCode ${uniqueIndexOfNewLine++}")
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
            if (unique.type != null) {
                for ((index, typeList) in unique.type.parameterTypeMap.withIndex()) {
                    if (typeList.none { it in translatableUniqueParameterTypes }) continue
                    // Unknown/Comment parameter contents better be offered to translators too
                    resultStrings.add("${unique.params[index]} = ")
                }
                resultStrings.add("${unique.type.getTranslatable()} = ")
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

        // Example: PolicyBranch inherits from Policy inherits from RulesetObject.
        // RulesetObject has the name and uniques properties and we wish to include them.
        // So we need superclass recursion to be sure not to miss stuff in the future.
        // The superclass != null check is made obsolete in theory by the Object check, but better play safe.
        fun Class<*>.allSupers(): Sequence<Class<*>> = sequence {
            if (this@allSupers == Object::class.java) return@sequence
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
                // this field can contain sub-objects, let's serialize them as well
                @Suppress("RemoveRedundantQualifierName")  // to clarify List does _not_ inherit from anything in java.util
                when {
                    // Promotion names are not uniques but since we did the "[unitName] ability"
                    // they need the "parameters" treatment too
                    // Same for victory milestones
                    (field.name in fieldsToProcessParameters)
                            && (fieldValue is java.util.AbstractCollection<*>) ->
                        for (item in fieldValue)
                            if (item is String) submitString(item, Unique(item)) else serializeElement(item!!)
                    fieldValue is java.util.AbstractCollection<*> ->
                        for (item in fieldValue)
                            if (item is String) submitString(item) else serializeElement(item!!)
                    fieldValue is kotlin.collections.List<*> ->
                        for (item in fieldValue)
                            if (item is String) submitString(item) else serializeElement(item!!)
                    element is Promotion && field.name == "name" ->  // see above
                        submitString(fieldValue.toString(), Unique(fieldValue.toString()))
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
                "Event.name" // Presently not shown anywhere
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
                "uniques", "promotions", "milestones",
            )

            private fun isFieldTypeRelevant(type: Class<*>) =
                    type == String::class.java ||
                    type == java.util.ArrayList::class.java ||
                    type == java.util.List::class.java ||        // CivilopediaText is not an ArrayList
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
                        field.name !in untranslatableFieldSet &&
                        (clazz.componentType?.simpleName ?: clazz.simpleName) + "." + field.name !in untranslatableFieldSet
            }

            private fun getJavaClassByName(name: String): Class<Any>? {
                return when (name) {
                    "Beliefs" -> emptyArray<Belief>().javaClass
                    "Buildings" -> emptyArray<Building>().javaClass
                    "CityStateTypes" -> emptyArray<CityStateType>().javaClass
                    "Difficulties" -> emptyArray<Difficulty>().javaClass
                    "Eras" -> emptyArray<Era>().javaClass
                    "Events" -> emptyArray<Event>().javaClass
                    "GlobalUniques" -> GlobalUniques().javaClass
                    "Nations" -> emptyArray<Nation>().javaClass
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
