package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import java.nio.charset.Charset
import kotlin.collections.set

class TranslationFileReader{

    private val percentagesFileLocation = "jsons/translationsByLanguage/completionPercentages.properties"
    private val charset = Charset.forName("UTF-8").name()

    fun read(translationFile: String): LinkedHashMap<String, String> {
        val translations = LinkedHashMap<String, String>()
        val text = Gdx.files.internal(translationFile)
        text.reader(charset).forEachLine { line ->
            if(!line.contains(" = ")) return@forEachLine
            val splitLine = line.split(" = ")
            if(splitLine[1]!="") { // the value is empty, this means this wasn't translated yet
                val value = splitLine[1].replace("\\n","\n")
                val key = splitLine[0].replace("\\n","\n")
                translations[key] = value
            }
        }
        return translations
    }

    private fun writeByTemplate(language:String, translations: HashMap<String, String>){
        val templateFile = Gdx.files.internal("jsons/translationsByLanguage/template.properties")
        val stringBuilder = StringBuilder()
        for(line in templateFile.reader().readLines()){
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
        Gdx.files.local("jsons/translationsByLanguage/$language.properties")
                .writeString(stringBuilder.toString(),false,charset)
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
        Gdx.files.local(percentagesFileLocation).writeString(stringBuilder.toString(),false)
    }

    fun readLanguagePercentages():HashMap<String,Int>{

        val hashmap = HashMap<String,Int>()
        val percentageFile = Gdx.files.internal(percentagesFileLocation)
        if(!percentageFile.exists()) return hashmap
        for(line in percentageFile.reader().readLines()){
            val splitLine = line.split(" = ")
            hashmap[splitLine[0]]=splitLine[1].toInt()
        }
        return hashmap
    }

}