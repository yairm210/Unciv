package com.unciv.app.desktop

import com.unciv.models.translations.DocStrings
import java.io.File

class UiElementDocsWriter {
    companion object {
        private const val docsDir = "../../docs/"
        private const val docFileName = "Creating-a-UI-skin.md"
        private const val startMarker = "<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION -->"
        private const val endMarker = "<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION_END -->"
        private const val srcPath = "../../core/src/com/unciv/"
    }

    fun write() {
        // Generate English first (reference)
        writeForLanguage("English", emptyMap())

        // Then all other languages
        val languages = UniqueDocsWriter.discoverLanguages().filter { it != "English" }
        for (language in languages) {
            val translations = UniqueDocsWriter.loadTranslations(language)
            writeForLanguage(language, translations)
        }
    }

    private fun writeForLanguage(language: String, translations: Map<String, String>) {
        val srcFile = File(srcPath)
        val locale = UniqueDocsWriter.languageToLocale(language)
        val targetDir = File("${docsDir}$locale/Modders/")
        if (!srcFile.exists()) return
        targetDir.mkdirs()

        // Read the English file as template (hand-written parts around the table)
        val enLocale = UniqueDocsWriter.languageToLocale("English")
        val templateFile = File("${docsDir}$enLocale/Modders/$docFileName")
        val originalLines = if (templateFile.exists()) templateFile.readLines() else emptyList()

        val endIndex = originalLines.indexOf(endMarker).takeIf { it != -1 } ?: (originalLines.size - 1)
        val startIndex = originalLines.indexOf(startMarker).takeIf { it != -1 } ?: (endIndex + 1)

        val elements = mutableListOf<String>()

        @Suppress("RegExpRepeatedSpace")
        val backgroundRegex = Regex("""
            getUiBackground\s*\(\s*
            (?:path\s*=\s*)?
            "(?<path>[^"]*)"\s*
            (?:,\s*
                (?:default\s*=\s*)?
                (?:(?:BaseScreen\.)?skinStrings\.)?
                (?<default>.*)Shape
            \s*)?[,)]
            """.trimIndent(), RegexOption.COMMENTS)

        @Suppress("RegExpRepeatedSpace")
        val colorRegex = Regex("""
            getUIColor\s*\(\s*
            (?:path\s*=\s*)?
            "(?<path>[^"]*)"\s*
            (?:,\s*
                (?:default\s*=\s*)?
                (?:Color\s*\(|colorFromRGB\s*\(|Color\.valueOf\(|Color\.)
                (?<default>[^)]*)
            \s*)?\)
            """, RegexOption.COMMENTS)

        @Suppress("RegExpRepeatedSpace")
        val borderedTableRegEx = Regex("""
            (?<!class )
            BorderedTable\s*\(\s*
            (?:path\s*=\s*)?
            "(?<path>[^"]*)"\s*
            (?:,\s*
                (?!defaultBgBorder\s*=)
                (?:defaultBgShape\s*=\s*)?
                (?:(?:BaseScreen\.)?skinStrings\.)?
                (?<defaultBgShape>.*)Shape
            \s*)?
            (?:,\s*
                (?:defaultBgBorder\s*=\s*)?
                (?:(?:BaseScreen\.)?skinStrings\.)?
                (?<defaultBgBorder>.*)Shape
            \s*)?
            \)
        """, RegexOption.COMMENTS)

        for (file in srcFile.walk()) {
            if (file.path.endsWith(".kt")) {
                val sourceText = file.readText()
                val backgroundAndColorPairs = (
                        backgroundRegex.findAll(sourceText) +
                        colorRegex.findAll(sourceText)
                    ).map {
                        it.groups["path"]?.value to it.groups["default"]?.value
                    }
                val borderedTablePairs = borderedTableRegEx.findAll(sourceText)
                    .flatMap {
                        val path = it.groups["path"]?.value
                        sequenceOf(
                            path to (it.groups["defaultBgShape"]?.value ?: "rectangleWithOutline"),
                            "${path}Border" to (it.groups["defaultBgBorder"]?.value ?: "rectangleWithOutline")
                        )
                    }
                for ((path, default) in (backgroundAndColorPairs + borderedTablePairs)) {
                    val name = path?.takeLastWhile { it != '/' } ?: ""
                    if (name.isBlank()) continue
                    val basePath = path!!.dropLast(name.length)
                    elements.add("| $basePath | $name | $default | |")
                }
            }
        }

        // Translate table headers
        val directory = translations[DocStrings.SKIN_DIRECTORY] ?: DocStrings.SKIN_DIRECTORY
        val name = translations[DocStrings.SKIN_NAME] ?: DocStrings.SKIN_NAME
        val defaultShape = translations[DocStrings.SKIN_DEFAULT_SHAPE] ?: DocStrings.SKIN_DEFAULT_SHAPE
        val image = translations[DocStrings.SKIN_IMAGE] ?: DocStrings.SKIN_IMAGE

        val newLines = sequence {
            yieldAll(originalLines.subList(0, startIndex))
            yield(startMarker)
            yield("| $directory | $name | $defaultShape | $image |")
            yield("|---|:---:|:---:|---|")
            yieldAll(elements.asSequence().sorted().distinct())
            yield(endMarker)
            yieldAll(originalLines.subList(endIndex + 1, originalLines.size))
        }

        val outputFile = File("${docsDir}$locale/Modders/$docFileName")
        outputFile.writeText(newLines.joinToString("\n"))
    }
}
