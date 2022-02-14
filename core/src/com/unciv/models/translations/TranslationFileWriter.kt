package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import com.unciv.JsonParser
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.*
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object TranslationFileWriter {

    private const val specialNewLineCode = "# This is an empty line "
    const val templateFileLocation = "jsons/translations/template.properties"
    private const val languageFileLocation = "jsons/translations/%s.properties"

    fun writeNewTranslationFiles(): String {
        try {
            val translations = Translations()
            translations.readAllLanguagesTranslation()

            val percentages = generateTranslationFiles(translations)
            writeLanguagePercentages(percentages)

            // See #5168 for some background on this
            for ((modName, modTranslations) in translations.modsWithTranslations) {
                val modFolder = Gdx.files.local("mods").child(modName)
                val modPercentages = generateTranslationFiles(modTranslations, modFolder, translations)
                writeLanguagePercentages(modPercentages, modFolder)  // unused by the game but maybe helpful for the mod developer
            }

            return "Translation files are generated successfully."
        } catch (ex: Throwable) {
            return ex.localizedMessage
        }
    }

    private fun getFileHandle(modFolder: FileHandle?, fileLocation: String) =
            if (modFolder != null) modFolder.child(fileLocation)
            else Gdx.files.local(fileLocation)

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
            val templateFile = getFileHandle(modFolder, templateFileLocation) // read the template
            if (templateFile.exists())
                linesToTranslate.addAll(templateFile.reader(TranslationFileReader.charset).readLines())

            linesToTranslate += "\n######### City filters ###########\n"
            linesToTranslate.addAll(UniqueParameterType.cityFilterStrings.map { "$it = " })

            for (baseRuleset in BaseRuleset.values()) {
                val generatedStringsFromBaseRuleset =
                        generateStringsFromJSONs(Gdx.files.local("jsons/${baseRuleset.fullName}"))
                for (entry in generatedStringsFromBaseRuleset)
                    fileNameToGeneratedStrings[entry.key + " from " + baseRuleset.fullName] = entry.value
            }

            fileNameToGeneratedStrings["Tutorials"] = generateTutorialsStrings()
        } else {
            fileNameToGeneratedStrings.putAll(generateStringsFromJSONs(modFolder.child("jsons")))
        }

        for (key in fileNameToGeneratedStrings.keys) {
            linesToTranslate.add("\n#################### Lines from $key ####################\n")
            linesToTranslate.addAll(fileNameToGeneratedStrings.getValue(key))
        }


        linesToTranslate.add("\n\n#################### Lines from Unique Types #######################\n")
        for (unique in UniqueType.values()) {
            val deprecationAnnotation = unique.getDeprecationAnnotation()
            if (deprecationAnnotation != null) continue
            if (unique.flags.contains(UniqueFlag.HiddenToUsers)) continue

            // to get rid of multiple equal parameters, like "[amount] [amount]", don't use the unique.text directly
            //  instead fill the placeholders with incremented values if the previous one exists
            val newPlaceholders = ArrayList<String>()
            for (placeholderText in unique.text.getPlaceholderParameters()) {
                if (!newPlaceholders.contains(placeholderText))
                    newPlaceholders += placeholderText
                else {
                    var i = 2
                    while (newPlaceholders.contains(placeholderText + i)) i++
                    newPlaceholders += placeholderText + i
                }
            }
            val finalText = unique.text.fillPlaceholders(*newPlaceholders.toTypedArray())
            linesToTranslate.add("$finalText = ")
        }

        for (uniqueTarget in UniqueTarget.values())
            linesToTranslate.add("$uniqueTarget = ")

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
                        if (!stringBuilder.endsWith("\r\n\r\n")) // don't double-add line breaks -
                        // this stops lots of line breaks between removed translations in G&K
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
                var translationValue = if (existingTranslation != null && language in existingTranslation){
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

                val lineToWrite = translationKey.replace("\n", "\\n") +
                        " = " + translationValue.replace("\n", "\\n")
                stringBuilder.appendLine(lineToWrite)
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

    private fun writeLanguagePercentages(percentages: HashMap<String, Int>, modFolder: FileHandle? = null) {
        val output = percentages.asSequence()
            .joinToString("\n", postfix = "\n") { "${it.key} = ${it.value}" }
        getFileHandle(modFolder, TranslationFileReader.percentagesFileLocation)
            .writeString(output, false)
    }

    private fun generateTutorialsStrings(): MutableSet<String> {

        val tutorialsStrings = mutableSetOf<String>()
        val tutorials = JsonParser().getFromJson(LinkedHashMap<String, Array<String>>().javaClass, "jsons/Tutorials.json")

        var uniqueIndexOfNewLine = 0
        for (tutorial in tutorials) {
            if (!tutorial.key.startsWith('_'))
                tutorialsStrings.add("${tutorial.key.replace('_', ' ')} = ")
            for (str in tutorial.value)
                if (str != "") tutorialsStrings.add("$str = ")
            // This is a small hack to insert multiple /n into the set, which can't contain identical lines
            tutorialsStrings.add("$specialNewLineCode ${uniqueIndexOfNewLine++}")
        }
        return tutorialsStrings
    }

    // used for unit test only
    fun getGeneratedStringsSize(): Int {
        return generateStringsFromJSONs(Gdx.files.local("jsons/Civ V - Vanilla")).values.sumOf { // exclude empty lines
            it.count { line: String -> !line.startsWith(specialNewLineCode) }
        }
    }

    private fun generateStringsFromJSONs(jsonsFolder: FileHandle): LinkedHashMap<String, MutableSet<String>> {
        // build maps identifying parameters as certain types of filters - unitFilter etc
        val ruleset = RulesetCache.getVanillaRuleset()
        val tileFilterMap = ruleset.terrains.keys.toMutableSet().apply { addAll(sequenceOf(
            "Friendly Land",
            "Foreign Land",
            "Fresh water",
            "non-fresh water",
            "Open Terrain",
            "Rough Terrain",
            "Natural Wonder",
            "unimproved"
        )) }
        val tileImprovementMap = ruleset.tileImprovements.keys.toMutableSet().apply { add("Great Improvement") }
        val buildingMap = ruleset.buildings.keys.toMutableSet().apply { addAll(sequenceOf(
            "Wonders",
            "Wonder",
            "National Wonder",
            "World Wonder",
            "Buildings",
            "Building"
        )) }
        val unitTypeMap = ruleset.unitTypes.keys.toMutableSet().apply { addAll(UniqueParameterType.unitTypeStrings) }

        val startMillis = System.currentTimeMillis()

        // Using LinkedHashMap (instead of HashMap) is important to maintain the order of sections in the translation file
        val generatedStrings = LinkedHashMap<String, MutableSet<String>>()

        var uniqueIndexOfNewLine = 0
        val jsonParser = JsonParser()
        val listOfJSONFiles = jsonsFolder
                .list { file -> file.name.endsWith(".json", true) }
                .sortedBy { it.name() }       // generatedStrings maintains order, so let's feed it a predictable one

        for (jsonFile in listOfJSONFiles) {
            val filename = jsonFile.nameWithoutExtension()

            val javaClass = getJavaClassByName(filename)
            if (javaClass == this.javaClass)
                continue // unknown JSON, let's skip it

            val array = jsonParser.getFromJson(javaClass, jsonFile.path())

            generatedStrings[filename] = mutableSetOf()
            val resultStrings = generatedStrings[filename]!!

            fun submitString(string: String) {
                val unique = Unique(string)
                if (unique.hasFlag(UniqueFlag.HiddenToUsers))
                    return // We don't need to translate this at all, not user-visible
                var stringToTranslate = string.removeConditionals()

                val existingParameterNames = HashSet<String>()
                if (unique.params.isNotEmpty()) {
                    for ((index,parameter) in unique.params.withIndex()) {
                        var parameterName = when {
                            unique.type != null -> {
                                val possibleParameterTypes = unique.type.parameterTypeMap[index]
                                // for multiple types. will look like "[unitName/buildingName]"
                                possibleParameterTypes.joinToString("/") { it.parameterName }
                            }
                            parameter.toFloatOrNull() != null -> "amount"
                            Stat.values().any { it.name == parameter } -> "stat"
                            parameter in tileFilterMap -> "tileFilter"
                            ruleset.units.containsKey(parameter) -> "unit"
                            parameter in tileImprovementMap -> "tileImprovement"
                            ruleset.tileResources.containsKey(parameter) -> "resource"
                            ruleset.technologies.containsKey(parameter) -> "tech"
                            ruleset.unitPromotions.containsKey(parameter) -> "promotion"
                            parameter in buildingMap -> "building"
                            parameter in unitTypeMap -> "unitType"
                            Stats.isStats(parameter) -> "stats"
                            parameter in UniqueParameterType.cityFilterStrings -> "cityFilter"
                            else -> "param"
                        }
                        if (parameterName in existingParameterNames) {
                            var i = 2
                            while (parameterName + i in existingParameterNames) i++
                            parameterName += i
                        }
                        existingParameterNames += parameterName

                        stringToTranslate = stringToTranslate.replaceFirst(parameter, parameterName)
                    }
                } else if (string.contains('{')) {
                    val matches = curlyBraceRegex.findAll(string)
                    if (matches.any()) {
                        // Ignore outer string, only translate the parts within `{}`
                        matches.forEach { submitString(it.groups[1]!!.value) }
                        return
                    }
                }
                resultStrings.add("$stringToTranslate = ")
                return
            }

            fun serializeElement(element: Any) {
                if (element is String) {
                    submitString(element)
                    return
                }
                val allFields = (
                            element.javaClass.declaredFields
                            + element.javaClass.fields
                            // Include superclass so the main PolicyBranch, which inherits from Policy,
                            // will recognize its Uniques and have them translated
                            + element.javaClass.superclass.declaredFields
                        ).filter {
                            it.type == String::class.java ||
                            it.type == java.util.ArrayList::class.java ||
                            it.type == java.util.List::class.java ||        // CivilopediaText is not an ArrayList
                            it.type == java.util.HashSet::class.java ||
                            it.type.isEnum  // allow scanning Enum names
                        }
                for (field in allFields) {
                    field.isAccessible = true
                    val fieldValue = field.get(element)
                    if (isFieldTranslatable(javaClass, field, fieldValue)) { // skip fields which must not be translated
                        // this field can contain sub-objects, let's serialize them as well
                        @Suppress("RemoveRedundantQualifierName")  // to clarify List does _not_ inherit from anything in java.util
                        when (fieldValue) {
                            is java.util.AbstractCollection<*> ->
                                for (item in fieldValue)
                                    if (item is String) submitString(item) else serializeElement(item!!)
                            is kotlin.collections.List<*> ->
                                for (item in fieldValue)
                                    if (item is String) submitString(item) else serializeElement(item!!)
                            else -> submitString(fieldValue.toString())
                        }
                    }
                }
            }

            if (array is kotlin.Array<*>)
                for (element in array) {
                    serializeElement(element!!) // let's serialize the strings recursively
                    // This is a small hack to insert multiple /n into the set, which can't contain identical lines
                    resultStrings.add("$specialNewLineCode ${uniqueIndexOfNewLine++}")
                }
        }
        println("Translation writer took ${System.currentTimeMillis()-startMillis}ms for ${jsonsFolder.name()}")

        return generatedStrings
    }

    /** Exclude fields by name that contain references to items defined elsewhere
     * or are otherwise Strings but not user-displayed.
     *
     * An exclusion applies either over _all_ json files and all classes contained in them
     * or Class-specific by using a "Class.Field" notation.
     */
    private val untranslatableFieldSet = setOf(
            "aiFreeTechs", "aiFreeUnits", "attackSound", "building",
            "cannotBeBuiltWith", "cultureBuildings", "improvement", "improvingTech",
            "obsoleteTech", "occursOn", "prerequisites", "promotions",
            "providesFreeBuilding", "replaces", "requiredBuilding", "requiredBuildingInAllCities",
            "requiredNearbyImprovedResources", "requiredResource", "requiredTech", "requires",
            "resourceTerrainAllow", "revealedBy", "startBias", "techRequired",
            "terrainsCanBeBuiltOn", "terrainsCanBeFoundOn", "turnsInto", "uniqueTo", "upgradesTo",
            "link", "icon", "extraImage", "color",  // FormattedLine
            "excludedDifficulties", "RuinReward.uniques"      // RuinReward
    )
    /** Specifies Enums where the name property _is_ translatable, by Class name */
    private val translatableEnumsSet = setOf("BeliefType")

    /** Checks whether a field's value should be included in the translation templates.
     * Applies explicit field exclusions from [untranslatableFieldSet].
     * The Modifier.STATIC exclusion removes fields from e.g. companion objects.
     * Fields of enum types need that type explicitly allowed in [translatableEnumsSet]
     */
    private fun isFieldTranslatable(clazz: Class<*>, field: Field, fieldValue: Any?): Boolean {
        return fieldValue != null &&
                fieldValue != "" &&
                (field.modifiers and Modifier.STATIC) == 0 &&
                (!field.type.isEnum || field.type.simpleName in translatableEnumsSet) &&
                field.name !in untranslatableFieldSet &&
                (clazz.componentType?.simpleName ?: clazz.simpleName) + "." + field.name !in untranslatableFieldSet
    }

    private fun getJavaClassByName(name: String): Class<Any> {
        return when (name) {
            "Beliefs" -> emptyArray<Belief>().javaClass
            "Buildings" -> emptyArray<Building>().javaClass
            "Difficulties" -> emptyArray<Difficulty>().javaClass
            "Eras" -> emptyArray<Era>().javaClass
            "GlobalUniques" -> GlobalUniques().javaClass
            "Nations" -> emptyArray<Nation>().javaClass
            "Policies" -> emptyArray<PolicyBranch>().javaClass
            "Quests" -> emptyArray<Quest>().javaClass
            "Religions" -> emptyArray<String>().javaClass
            "Ruins" -> emptyArray<RuinReward>().javaClass
            "Specialists" -> emptyArray<Specialist>().javaClass
            "Techs" -> emptyArray<TechColumn>().javaClass
            "Terrains" -> emptyArray<Terrain>().javaClass
            "TileImprovements" -> emptyArray<TileImprovement>().javaClass
            "TileResources" -> emptyArray<TileResource>().javaClass
            "Tutorials" -> this.javaClass // dummy value
            "UnitPromotions" -> emptyArray<Promotion>().javaClass
            "Units" -> emptyArray<BaseUnit>().javaClass
            "UnitTypes" -> emptyArray<UnitType>().javaClass
            else -> this.javaClass // dummy value
        }
    }

}
