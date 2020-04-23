package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import com.unciv.JsonParser
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.map.MapUnit
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.ui.worldscreen.unit.UnitActions
import java.lang.reflect.Field

object TranslationFileWriter {

    private const val specialNewLineCode = "# This is an empty line "
    const val templateFileLocation = "jsons/translations/template.properties"
    private const val languageFileLocation = "jsons/translations/%s.properties"

    fun writeNewTranslationFiles(translations: Translations) {

        val percentages = generateTranslationFiles(translations)
        writeLanguagePercentages(percentages)

        // try to do the same for the mods
        for(modFolder in Gdx.files.local("mods").list().filter { it.isDirectory })
            generateTranslationFiles(translations, modFolder)
            // write percentages is not needed: for an individual mod it makes no sense

    }

    private fun getFileHandle(modFolder: FileHandle?, fileLocation: String) =
            if (modFolder != null) modFolder.child(fileLocation)
            else Gdx.files.local(fileLocation)

    private fun generateTranslationFiles(translations: Translations, modFolder: FileHandle? = null): HashMap<String, Int> {
        // read the template
        val templateFile = getFileHandle(modFolder, templateFileLocation)
        val linesFromTemplates = mutableListOf<String>()
        if (templateFile.exists())
            linesFromTemplates.addAll(templateFile.reader().readLines())
        // read the JSON files
        val generatedStrings = generateStringsFromJSONs(modFolder)
        for (key in generatedStrings.keys) {
            linesFromTemplates.add("\n#################### Lines from $key.json ####################\n")
            linesFromTemplates.addAll(generatedStrings.getValue(key))
        }

        var countOfTranslatableLines = 0
        val countOfTranslatedLines = HashMap<String, Int>()
        // iterate through all available languages
        for (language in translations.getLanguages()) {
            var translationsOfThisLanguage = 0
            val stringBuilder = StringBuilder()

            for (line in linesFromTemplates) {
                if (line.contains(" = ")) {
                    // count translatable lines only once (e.g. for English)
                    if (language == "English") countOfTranslatableLines++
                } else {
                    // small hack to insert empty lines
                    if (line.startsWith(specialNewLineCode))
                        stringBuilder.appendln()
                    else // copy as-is
                        stringBuilder.appendln(line)
                    continue
                }

                val translationKey = line.split(" = ")[0].replace("\\n", "\n")
                var translationValue = ""

                val translationEntry = translations[translationKey]
                if (translationEntry != null && translationEntry.containsKey(language)) {
                    translationValue = translationEntry[language]!!
                    translationsOfThisLanguage++
                } else stringBuilder.appendln(" # Requires translation!")

                val lineToWrite = translationKey.replace("\n", "\\n") +
                        " = " + translationValue.replace("\n", "\\n")
                stringBuilder.appendln(lineToWrite)
            }

            countOfTranslatedLines[language] = translationsOfThisLanguage

            val fileWriter = getFileHandle(modFolder, languageFileLocation.format(language))
            fileWriter.writeString(stringBuilder.toString(), false, TranslationFileReader.charset)
        }

        // Calculate the percentages of translations
        // It should be done after the loop of languages, since the countOfTranslatableLines is not known in the 1st iteration
        for (key in countOfTranslatedLines.keys)
            countOfTranslatedLines[key] = if (countOfTranslatableLines > 0) countOfTranslatedLines.getValue(key) * 100 / countOfTranslatableLines
            else 100

        return countOfTranslatedLines
    }

    private fun writeLanguagePercentages(percentages: HashMap<String,Int>){
        val stringBuilder = StringBuilder()
        for(entry in percentages){
            stringBuilder.appendln(entry.key+" = "+entry.value)
        }
        Gdx.files.local(TranslationFileReader.percentagesFileLocation).writeString(stringBuilder.toString(),false)
    }



    private fun generateTutorialsStrings(): MutableSet<String> {

        val tutorialsStrings = mutableSetOf<String>()
        val tutorials = JsonParser().getFromJson(LinkedHashMap<String, Array<String>>().javaClass, "jsons/Tutorials.json")

        var uniqueIndexOfNewLine = 0
        for (tutorial in tutorials) {
            for (str in tutorial.value)
                if (str != "") tutorialsStrings.add("$str = ")
            // This is a small hack to insert multiple /n into the set, which can't contain identical lines
            tutorialsStrings.add("$specialNewLineCode ${uniqueIndexOfNewLine++}")
        }
        return tutorialsStrings
    }

    // used for unit test only
    fun getGeneratedStringsSize(): Int {
        return generateStringsFromJSONs().values.sumBy { // exclude empty lines
            it.count{ line: String -> !line.startsWith(specialNewLineCode) } }
    }

    private fun generateStringsFromJSONs(modFolder: FileHandle? = null): Map<String, MutableSet<String>> {

        // Using LinkedHashMap (instead of HashMap) is important to maintain the order of sections in the translation file
        val generatedStrings = LinkedHashMap<String, MutableSet<String>>()

        var uniqueIndexOfNewLine = 0
        val jsonParser = JsonParser()
        val folderHandler = getFileHandle(modFolder,"jsons")
        val listOfJSONFiles = folderHandler.list{file -> file.name.endsWith(".json", true)}
        for (jsonFile in listOfJSONFiles)
        {
            val filename = jsonFile.nameWithoutExtension()
            // Tutorials are a bit special
            if (filename == "Tutorials") {
                generatedStrings[filename] = generateTutorialsStrings()
                continue
            }

            val javaClass = getJavaClassByName(filename)
            if (javaClass == this.javaClass)
                continue // unknown JSON, let's skip it

            val array = jsonParser.getFromJson(javaClass, jsonFile.path())

            generatedStrings[filename] = mutableSetOf()
            val resultStrings = generatedStrings[filename]

            fun submitString(item: Any) {
                val string = item.toString()
                // substitute the regex for "Bonus/Penalty vs ..."
                val match = Regex(BattleDamage.BONUS_VS_UNIT_TYPE).matchEntire(string)
                when {
                    match != null ->
                        resultStrings!!.add("${match.groupValues[1]} vs [unitType] = ")

                    // substitute the regex for the bonuses, etc.
                    string.startsWith(MapUnit.BONUS_WHEN_INTERCEPTING)
                            || string.startsWith(UnitActions.CAN_UNDERTAKE)
                            || string.endsWith(MapUnit.CHANCE_TO_INTERCEPT_AIR_ATTACKS)
                            || Regex(BattleDamage.BONUS_AS_ATTACKER).matchEntire(string) != null
                            || Regex(BattleDamage.HEAL_WHEN_KILL).matchEntire(string) != null ->
                    {
                        val updatedString = string.replace("\\[\\d+(?=])]".toRegex(),"[amount]")
                        resultStrings!!.add("$updatedString = ")
                    }
                    else ->
                        resultStrings!!.add("$string = ")
                }
            }

            fun serializeElement(element: Any) {
                val allFields = (element.javaClass.declaredFields + element.javaClass.fields).
                                    filter { it.type == String::class.java ||
                                             it.type == java.util.ArrayList::class.java ||
                                             it.type == java.util.HashSet::class.java }
                for (field in allFields) {
                    field.isAccessible = true
                    val fieldValue = field.get(element)
                    if (isFieldTranslatable(field, fieldValue)) { // skip fields which must not be translated
                        // this field can contain sub-objects, let's serialize them as well
                        if (fieldValue is java.util.AbstractCollection<*>) {
                            for (item in fieldValue)
                                if (item is String) submitString(item) else serializeElement(item!!)
                        } else
                            submitString(fieldValue)
                    }
                }
            }

            if (array is kotlin.Array<*>)
                for (element in array) {
                    serializeElement(element!!) // let's serialize the strings recursively
                    // This is a small hack to insert multiple /n into the set, which can't contain identical lines
                    resultStrings!!.add("$specialNewLineCode ${uniqueIndexOfNewLine++}")
                }
        }
        return generatedStrings
    }

    private fun isFieldTranslatable(field: Field, fieldValue: Any?): Boolean {
        return  fieldValue != null &&
                fieldValue != "" &&
                field.name !in setOf("startBias", "requiredTech", "uniqueTo",
                "aiFreeTechs", "aiFreeUnits", "techRequired", "improvingTech", "promotions",
                "building", "revealedBy", "attackSound", "requiredResource", "obsoleteTech")
    }

    private fun getJavaClassByName(name: String): Class<Any> {
        return when (name) {
            "Buildings" -> emptyArray<Building>().javaClass
            "Difficulties" -> emptyArray<Difficulty>().javaClass
            "GreatPeopleNames" -> this.javaClass // dummy value
            "Nations" -> emptyArray<Nation>().javaClass
            "Policies" -> emptyArray<PolicyBranch>().javaClass
            "Techs" -> emptyArray<TechColumn>().javaClass
            "Terrains" -> emptyArray<Terrain>().javaClass
            "TileImprovements" -> emptyArray<TileImprovement>().javaClass
            "TileResources" -> emptyArray<TileResource>().javaClass
            "Tutorials" -> this.javaClass // dummy value
            "UnitPromotions" -> emptyArray<Promotion>().javaClass
            "Units" -> emptyArray<BaseUnit>().javaClass
            else -> this.javaClass // dummy value
        }
    }

}