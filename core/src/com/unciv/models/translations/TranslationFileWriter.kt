package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.unciv.JsonParser
import com.unciv.models.ruleset.Nation

object TranslationFileWriter {

    const val templateFileLocation = "jsons/translations/template.properties"

    private fun writeByTemplate(language:String, translations: HashMap<String, String>){

        val templateFile = Gdx.files.internal(templateFileLocation)
        val linesFromTemplates = mutableListOf<String>()
        linesFromTemplates.addAll(templateFile.reader().readLines())
        linesFromTemplates.add("\n#################### Lines from Nations.json ####################\n")
        linesFromTemplates.addAll(generateNationsStrings())
        linesFromTemplates.add("\n#################### Lines from Tutorials.json ####################\n")
        linesFromTemplates.addAll(generateTutorialsStrings())

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

    fun generateNationsStrings(): Collection<String> {

        val nations = JsonParser().getFromJson(emptyArray<Nation>().javaClass, "jsons/Nations.json")
        val strings = mutableSetOf<String>() // using set to avoid duplicates

        for (nation in nations) {
            for (field in nation.javaClass.declaredFields
                    .filter { it.type == String::class.java || it.type == java.util.ArrayList::class.java }) {
                field.isAccessible = true
                val fieldValue = field.get(nation)
                if (field.name != "startBias" && // skip fields which must not be translated
                        fieldValue != null && fieldValue != "") {

                    if (fieldValue is ArrayList<*>) {
                        for (item in fieldValue)
                            strings.add("$item = ")
                    } else
                        strings.add("$fieldValue = ")
                }
            }
        }
        return strings
    }

    fun generateTutorialsStrings(): Collection<String> {

        val tutorials = JsonParser().getFromJson(LinkedHashMap<String, Array<String>>().javaClass, "jsons/Tutorials.json")
        val strings = mutableSetOf<String>() // using set to avoid duplicates

        for (tutorial in tutorials) {
            for (str in tutorial.value)
                if (str != "") strings.add("$str = ")
        }
        return strings
    }

}