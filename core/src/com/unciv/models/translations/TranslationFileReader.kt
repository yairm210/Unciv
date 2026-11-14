package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.models.ruleset.unique.UniqueType
import org.jetbrains.annotations.VisibleForTesting
import kotlin.collections.set

@Suppress("ConstPropertyName")
object TranslationFileReader {

    private const val templateFileLocation = "jsons/translations/template.properties"
    internal const val percentagesFileLocation = "jsons/translations/completionPercentages.properties"

    val charset: String = Charsets.UTF_8.name()

    @VisibleForTesting
    const val keyValueSeparator = " = "

    @VisibleForTesting
    fun String.isCommentLine(): Boolean =
        isBlank() || startsWith("#") || startsWith(" #") || !contains(keyValueSeparator)

    fun read(file: FileHandle): LinkedHashMap<String, String> {
        val translations = LinkedHashMap<String, String>()
        file.reader(charset).forEachLine { line ->
            if (line.isCommentLine()) return@forEachLine
            val splitLine = line.split(keyValueSeparator, limit = 2)
            if (splitLine[1].isNotEmpty()) { // if the value is empty, this means this wasn't translated yet
                val value = splitLine[1].replace("\\n","\n")
                val key = splitLine[0].replace("\\n","\n")
                translations[key] = value
            }
        }
        // TFW avoids offering the template for UniqueType.Comment, so we can hardcode this:
        translations[UniqueType.Comment.text] = "[comment]"
        return translations
    }

    fun readLanguagePercentages(): HashMap<String, CompletionPercentage> {
        val hashmap = HashMap<String, CompletionPercentage>(50)
        val percentageFile = Gdx.files.internal(percentagesFileLocation)
        if (!percentageFile.exists()) return hashmap
        percentageFile.reader(charset).forEachLine { line ->
            if (line.isCommentLine()) return@forEachLine
            val splitLine = line.split(keyValueSeparator)
            val value = if (splitLine.size > 1) CompletionPercentage.parse(splitLine[1]) else null
            if (value != null)
                hashmap[splitLine[0]] = value
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
