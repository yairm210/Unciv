package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.unciv.JsonParser
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.stats.NamedStats
import java.lang.reflect.Field

object TranslationFileWriter {

    const val templateFileLocation = "jsons/translations/template.properties"
    private val generatedStrings = mutableMapOf<String,MutableSet<String>>()

    private fun writeByTemplate(language:String, translations: HashMap<String, String>){

        val templateFile = Gdx.files.internal(templateFileLocation)
        val linesFromTemplates = mutableListOf<String>()
        linesFromTemplates.addAll(templateFile.reader().readLines())

        generateStringsFromJSONs()
        for (key in generatedStrings.keys) {
            linesFromTemplates.add("\n#################### Lines from $key.json ####################\n")
            linesFromTemplates.addAll(generatedStrings.getValue(key))
        }

        val stringBuilder = StringBuilder()
        for(line in linesFromTemplates){
            if(!line.contains(" = ")){ // copy as-is
                stringBuilder.appendln(line)
                continue
            }
            val translationKey = line.split(" = ")[0].replace("\\n","\n")
            var translationValue = ""
            if(translations.containsKey(translationKey)) translationValue = translations[translationKey]!!
            else stringBuilder.appendln(" # Requires translation!")
            val lineToWrite = translationKey.replace("\n","\\n") +
                    " = "+ translationValue.replace("\n","\\n")
            stringBuilder.appendln(lineToWrite)
        }
        Gdx.files.local("jsons/translations/$language.properties")
                .writeString(stringBuilder.toString(),false, TranslationFileReader.charset)
    }


    fun writeNewTranslationFiles(translations: Translations) {

        for (language in translations.getLanguages()) {
            val languageHashmap = HashMap<String, String>()

            for (translation in translations.values) {
                if (translation.containsKey(language))
                    languageHashmap[translation.entry] = translation[language]!!
            }
            writeByTemplate(language, languageHashmap)
        }
        writeLanguagePercentages(translations)
    }

    private fun writeLanguagePercentages(translations: Translations){
        val percentages = translations.calculatePercentageCompleteOfLanguages()
        val stringBuilder = StringBuilder()
        for(entry in percentages){
            stringBuilder.appendln(entry.key+" = "+entry.value)
        }
        Gdx.files.local(TranslationFileReader.percentagesFileLocation).writeString(stringBuilder.toString(),false)
    }

    private fun generateTutorialsStrings(): Collection<String> {

        if (generatedStrings.containsKey("Tutorials"))
            return generatedStrings.getValue("Tutorials")

        generatedStrings["Tutorials"] = mutableSetOf()
        val tutorialsStrings = generatedStrings["Tutorials"]
        val tutorials = JsonParser().getFromJson(LinkedHashMap<String, Array<String>>().javaClass, "jsons/Tutorials.json")

        for (tutorial in tutorials) {
            for (str in tutorial.value)
                if (str != "") tutorialsStrings!!.add("$str = ")
        }
        return tutorialsStrings!!
    }

    fun getGeneratedStringsSize(): Int {
        if (generatedStrings.isEmpty())
            generateStringsFromJSONs()
        return generatedStrings.values.sumBy { it.size }
    }

    private fun generateStringsFromJSONs() {

        if (generatedStrings.isNotEmpty())
            return // do not regenerate if the strings are ready

        val jsonParser = JsonParser()
        val folderHandler = Gdx.files.internal("jsons")
        val listOfJSONFiles = folderHandler.list{file -> file.name.endsWith(".json", true)}
        for (jsonFile in listOfJSONFiles)
        {
            val filename = jsonFile.nameWithoutExtension()
            // Tutorials are a bit special
            if (filename == "Tutorials") {
                generateTutorialsStrings()
                continue
            }

            val javaClass = getJavaClassByName(filename)
            if (javaClass == this.javaClass)
                continue // unknown JSON, let's skip it

            val array = jsonParser.getFromJson(javaClass, jsonFile.path())

            generatedStrings[filename] = mutableSetOf()
            val resultStrings = generatedStrings[filename]

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
                                if (item is String) resultStrings!!.add("$item = ") else serializeElement(item!!)
                        } else
                            resultStrings!!.add("$fieldValue = ")
                    }
                }
            }

            if (array is kotlin.Array<*>)
                for (element in array)
                    serializeElement(element!!) // let's serialize the strings recursively
        }
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