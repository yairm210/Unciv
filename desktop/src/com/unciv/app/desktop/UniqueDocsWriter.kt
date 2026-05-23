package com.unciv.app.desktop

import com.unciv.logic.map.mapunit.MapUnitCache
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.DocStrings
import com.unciv.models.translations.fillPlaceholders
import com.unciv.utils.Log
import java.io.File

class UniqueDocsWriter {
    companion object {
        private const val docsDir = "../../docs/"
        private const val uniqueTypesFileName = "uniques.md"
        private const val countablesFileName = "Unique-parameters.md"

        /** Where in the documentation file the Countables are to be inserted, start marker. */
        private const val countablesBeginMarker = "[//]: # (Countables automatically generated BEGIN)"
        /** Where in the documentation file the Countables are to be inserted, end marker. */
        private const val countablesEndMarker = "[//]: # (Countables automatically generated END)"

        /**
         * Switch from each Unique shown once under one UniqueTarget heading chosen from targetTypes (`true`)
         * to showing each Unique repeatedly under each UniqueTarget heading it applies to (`false`).
         */
        private const val showUniqueOnOneTarget = false

        /** Switch **on** the display of _inherited_ UniqueTargets in "Applicable to:" */
        private const val showInheritedTargets = false

        private fun UniqueTarget.allTargets(): Sequence<UniqueTarget> = sequence {
            if (showInheritedTargets && inheritsFrom != null) yieldAll(inheritsFrom!!.allTargets())
            yield(this@allTargets)
        }
        private fun UniqueType.allTargets(): Sequence<UniqueTarget> =
            targetTypes.asSequence().flatMap { it.allTargets() }.distinct()
        private fun UniqueTarget.allUniqueTypes(): Sequence<UniqueType> =
            UniqueType.entries.asSequence().filter {
                this in it.targetTypes
            }

        /** Map language names (from .properties files) to ISO 639-1 locale codes. */
        fun languageToLocale(language: String): String = when (language) {
            "English" -> "en"
            "Afrikaans" -> "af"
            "Bangla" -> "bn"
            "Belarusian" -> "be"
            "Bosnian" -> "bs"
            "Brazilian_Portuguese" -> "pt-BR"
            "Bulgarian" -> "bg"
            "Catalan" -> "ca"
            "Croatian" -> "hr"
            "Czech" -> "cs"
            "Dutch" -> "nl"
            "Filipino" -> "tl"
            "Finnish" -> "fi"
            "French" -> "fr"
            "Galician" -> "gl"
            "German" -> "de"
            "Greek" -> "el"
            "Hindi" -> "hi"
            "Hungarian" -> "hu"
            "Indonesian" -> "id"
            "Italian" -> "it"
            "Japanese" -> "ja"
            "Korean" -> "ko"
            "Latin" -> "la"
            "Lithuanian" -> "lt"
            "Malay" -> "ms"
            "Maltese" -> "mt"
            "Norwegian" -> "no"
            "Persian_(Pinglish-DIN)" -> "fa-DIN"
            "Persian_(Pinglish-UN)" -> "fa-UN"
            "Polish" -> "pl"
            "Portuguese" -> "pt"
            "Romanian" -> "ro"
            "Russian" -> "ru"
            "Rusyn" -> "rue"
            "Simplified_Chinese" -> "zh"
            "Spanish" -> "es"
            "Swedish" -> "sv"
            "Thai" -> "th"
            "Traditional_Chinese" -> "zh-TW"
            "Turkish" -> "tr"
            "Ukrainian" -> "uk"
            "Vietnamese" -> "vi"
            "Zulu" -> "zu"
            else -> language  // fallback: use the name as-is
        }

        /** Discover available languages from translation file directory. */
        fun discoverLanguages(): List<String> {
            val dir = File("jsons/translations/")
            if (!dir.exists()) return emptyList()
            return dir.listFiles { f ->
                f.extension == "properties"
                    && f.nameWithoutExtension != "template"
                    && f.nameWithoutExtension != "completionPercentages"
            }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                ?: emptyList()
        }

        /** Load translations from a .properties file. Returns empty map if file not found. */
        fun loadTranslations(language: String): Map<String, String> {
            val file = File("jsons/translations/$language.properties")
            if (!file.exists()) return emptyMap()
            val translations = LinkedHashMap<String, String>()
            file.forEachLine { line ->
                if (line.isBlank() || line.startsWith('#') || !line.contains(" = ")) return@forEachLine
                val splitLine = line.split(" = ", limit = 2)
                if (splitLine.size < 2 || splitLine[1].isEmpty()) return@forEachLine
                val value = splitLine[1].replace("\\n", "\n")
                val key = splitLine[0].replace("\\n", "\n").replace("\\= ", " = ")
                translations[key] = value
            }
            return translations
        }

        // Thanks https://github.com/ktorio/ktor/blob/d89d41ef6dc91479e6c13c25eb306abc15040b8e/ktor-utils/common/src/io/ktor/util/Text.kt#L7-L28
        /** An escaping routine specifically for mkdocs input: `<>` are escaped **unless** within backticks, and newlines are doubled */
        private fun String.escapeHtml(indent: Int = 0) = buildString(capacity = length + indent + 6) {
            var inCodeBlock = false
            for (char in this@escapeHtml) {
                when(char) {
                    '`' -> {
                        inCodeBlock = !inCodeBlock
                        append('`')
                    }
                    '&' -> append("&amp;")
                    '<' -> append(if (inCodeBlock) "<" else "&lt;")
                    '>' -> append(if (inCodeBlock) ">" else "&gt;")
                    '\n' -> append("\n\n" + "\t".repeat(indent))
                    else -> append(char)
                }
            }
        }
    }

    fun write() {
        // Generate English first — needed as template for other languages' writeCountables
        writeForLanguage("English")

        val languages = discoverLanguages().filter { it != "English" }
        for (language in languages) {
            writeForLanguage(language)
        }
    }

    private fun writeForLanguage(language: String) {
        val translations = if (language == "English") emptyMap()
            else loadTranslations(language)
        val locale = languageToLocale(language)

        val targetDir = File("${docsDir}$locale/Modders/")
        targetDir.mkdirs()
        writeUniqueTypes(language, translations, locale)
        writeCountables(language, translations, locale)
    }

    /** Look up a translation, falling back to English (the key itself). */
    private fun tr(text: String, translations: Map<String, String>): String =
        translations[text] ?: text

    private fun writeUniqueTypes(language: String, translations: Map<String, String>, locale: String) {
        val targetTypesToUniques =
            if (showUniqueOnOneTarget)
                UniqueType.entries
                    .groupBy { it.targetTypes.minOrNull()!! }
                    .toSortedMap()
            else
                UniqueTarget.entries.asSequence().associateWith { target ->
                    target.allTargets().flatMap { inheritedTarget ->
                        inheritedTarget.allUniqueTypes()
                    }.distinct().toList()
                }

        val capacity = 25 + targetTypesToUniques.size + UniqueType.entries.size * (if (showUniqueOnOneTarget) 3 else 16)
        val lines = ArrayList<String>(capacity)

        // Title and intro
        fun addLine(text: String) { lines.add(text) }
        val isEnglish = language == "English"

        addLine(tr(DocStrings.UNIQUES_TITLE, translations))
        addLine(tr(DocStrings.UNIQUES_OVERVIEW, translations))
        addLine("\n" + tr(DocStrings.UNIQUES_INTRO, translations))
        addLine("")

        for ((targetType, uniqueTypes) in targetTypesToUniques) {
            if (uniqueTypes.isEmpty()) continue

            // Section header: "## Global uniques（全局）" etc.
            val englishHeader = targetType.name + " uniques"
            val translatedName = if (!isEnglish) translations[targetType.name] else null
            val header = if (translatedName != null && translatedName != targetType.name)
                "## $englishHeader（$translatedName）"
            else
                "## $englishHeader"
            addLine(header)

            if (targetType.documentationString.isNotEmpty()) {
                val docString = tr(targetType.documentationString, translations)
                addLine("!!! note \"\"\n\n    $docString\n")
            }

            for (uniqueType in uniqueTypes) {
                if (uniqueType.getDeprecationAnnotation() != null) continue

                val uniqueText = if (targetType.modifierType != UniqueTarget.ModifierType.None)
                    "&lt;${uniqueType.text}&gt;"
                else uniqueType.text
                addLine("??? example  \"$uniqueText\"")

                // Show translation of the unique text on a separate line
                if (!isEnglish) {
                    val translatedUniqueText = translations[uniqueType.text]
                    if (translatedUniqueText != null && translatedUniqueText != uniqueType.text) {
                        addLine("\t/ $translatedUniqueText\n")
                    }
                }

                if (uniqueType.docDescription != null) {
                    val docDesc = tr(uniqueType.docDescription!!, translations)
                    addLine("\t${docDesc.escapeHtml(1)}\n")
                }
                if (uniqueType.parameterTypeMap.isNotEmpty()) {
                    val paramExamples = uniqueType.parameterTypeMap.map { it.first().docExample }.toTypedArray()
                    val examplePrefix = tr(DocStrings.DOC_EXAMPLE, translations)
                    addLine("\t$examplePrefix \"${uniqueText.fillPlaceholders(*paramExamples)}\"\n")
                }
                if (uniqueType.flags.contains(UniqueFlag.AcceptsSpeedModifier)) {
                    val modifierIntro = tr(DocStrings.DOC_MODIFIER_INTRO, translations)
                    addLine("\t$modifierIntro &lt;${UniqueType.ModifiedByGameSpeed.text}&gt;\n")
                }
                if (uniqueType.flags.contains(UniqueFlag.AcceptsGameProgressModifier)) {
                    val modifierIntro = tr(DocStrings.DOC_MODIFIER_INTRO, translations)
                    addLine("\t$modifierIntro &lt;${UniqueType.ModifiedByGameProgress.text}&gt;\n")
                }
                if (uniqueType in MapUnitCache.UnitMovementUniques) {
                    addLine("\t${tr(DocStrings.DOC_CACHED_UNIQUE, translations)}\n")
                }
                if (uniqueType.flags.contains(UniqueFlag.NoConditionals)) {
                    addLine("\t${tr(DocStrings.DOC_NO_CONDITIONALS, translations)}\n")
                }
                if (uniqueType.flags.contains(UniqueFlag.HiddenToUsers)) {
                    addLine("\t${tr(DocStrings.DOC_HIDDEN_TO_USERS, translations)}\n")
                }
                val applicableTo = tr(DocStrings.DOC_APPLICABLE_TO, translations)
                addLine("\t$applicableTo " + uniqueType.allTargets().sorted().joinToString())
                addLine("")
            }
        }

        // Abbreviations
        addLine("")
        for (paramType in UniqueParameterType.entries.asSequence().sortedBy { it.parameterName }) {
            if (paramType.docDescription == null) continue
            val description = tr(paramType.docDescription!!, translations)
            val punctuation = if (description.last().category == '.'.category) "" else "."
            addLine("*[${paramType.parameterName}]: $description$punctuation")
        }

        val file = File("${docsDir}$locale/Modders/$uniqueTypesFileName")
        file.writeText(lines.joinToString("\n"))
    }

    private fun writeCountables(language: String, translations: Map<String, String>, locale: String) {
        val enLocale = languageToLocale("English")
        val sourceFile = File("${docsDir}$enLocale/Modders/$countablesFileName")
        val oldContent = try {
            sourceFile.readText(Charsets.UTF_8)
        } catch (ex: Throwable) {
            Log.error("Can't read ${sourceFile.path}", ex)
            return
        }
        val truncateBegin = oldContent.indexOf(countablesBeginMarker)
        if (truncateBegin < 0) {
            Log.error("Can't find `%s` in %s", countablesBeginMarker, sourceFile.path)
            return
        }
        val truncateEnd = oldContent.indexOf(countablesEndMarker)
        if (truncateEnd < 0) {
            Log.error("Can't find `%s` in %s", countablesEndMarker, sourceFile.path)
            return
        }
        if (truncateEnd < truncateBegin) {
            Log.error("Inverted Countables markers in %s", countablesEndMarker, sourceFile.path)
            return
        }

        val newContent = StringBuilder(oldContent.length)
        newContent.append(oldContent, 0, truncateBegin + countablesBeginMarker.length)
        newContent.appendLine()

        for (countable in Countables.entries) {
            if (countable.getDeprecationAnnotation() != null) continue
            val header = tr(countable.documentationHeader, translations)
            newContent.appendLine("-   $header")
            newContent.appendLine("    - Example: `Only available <when number of [${countable.example}] is more than [0]>`")
            for (extraLine in countable.documentationStrings) {
                newContent.append("    - ")
                newContent.appendLine(tr(extraLine, translations))
            }
        }

        newContent.appendLine()
        newContent.append(oldContent, truncateEnd, oldContent.length)

        val file = File("${docsDir}$locale/Modders/$countablesFileName")
        file.writeText(newContent.toString(), Charsets.UTF_8)
    }
}
