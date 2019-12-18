package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.set

class TranslationFileReader(){
    fun read(translationFile: String): LinkedHashMap<String, String> {
        val translations = LinkedHashMap<String, String>()
        val text = Gdx.files.internal(translationFile)
        text.reader(Charsets.UTF_8.toString()).forEachLine {
            val line=it
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

    fun writeByTemplate(language:String, translations: HashMap<String, String>){
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
                .writeString(stringBuilder.toString(),false,Charsets.UTF_8.name())
    }
}