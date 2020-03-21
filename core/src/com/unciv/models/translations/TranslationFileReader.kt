package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import java.nio.charset.Charset
import kotlin.collections.set

object TranslationFileReader {

    const val percentagesFileLocation = "jsons/translations/completionPercentages.properties"
    val charset: String = Charset.forName("UTF-8").name()

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