package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import kotlin.collections.set

object TranslationFileReader {

    private const val templateFileLocation = "jsons/translations/template.properties"
    internal const val percentagesFileLocation = "jsons/translations/completionPercentages.properties"

    val charset: String = Charsets.UTF_8.name()

    fun read(file: FileHandle): LinkedHashMap<String, String> {
        val translations = LinkedHashMap<String, String>()
        file.reader(charset).forEachLine { line ->
            if (line.isBlank() || line.startsWith('#') || !line.contains(" = ")) return@forEachLine
            val splitLine = line.split(" = ", limit = 2)
            if (splitLine[1].isNotEmpty()) { // if the value is empty, this means this wasn't translated yet
                val value = splitLine[1].replace("\\n","\n")
                val key = splitLine[0].replace("\\n","\n")
                translations[key] = value
            }
        }
        return translations
    }

    fun readLanguagePercentages(): HashMap<String,Int> {
        val hashmap = HashMap<String,Int>()
        val percentageFile = Gdx.files.internal(percentagesFileLocation)
        if (!percentageFile.exists()) return hashmap
        percentageFile.reader(charset).forEachLine { line ->
            if (line.isEmpty() || line.startsWith('#')) return@forEachLine
            val splitLine = line.split(" = ")
            hashmap[splitLine[0]] = splitLine[1].toInt()
        }
        return hashmap
    }

    /**
     *  Reads the template file and feeds all lines, including empty and comment ones, to [block].
     *  Ensures closing of underlying buffers.
     *
     *  Usage:
     *  ```
     *      TranslationFileReader.readTemplates {
     *          linesToTranslate.addAll(it)
     *      }
     *  ```
     *
     *  @return The return value of [block], or null if the file is missing
     */
    fun <T> readTemplates(block: (Sequence<String>) -> T): T? {
        val templateFile = Gdx.files.internal(templateFileLocation) // read the template
        if (!templateFile.exists()) return null
        return templateFile.reader(charset).useLines(block)
    }
}
