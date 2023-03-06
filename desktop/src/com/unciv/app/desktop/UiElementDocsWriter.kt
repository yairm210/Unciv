package com.unciv.app.desktop

import java.io.File

class UiElementDocsWriter {
    companion object {
        private const val docPath = "../../docs/Modders/Creating-a-UI-skin.md"
        private const val startMarker = "<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION -->"
        private const val endMarker = "<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION_END -->"
        private const val srcPath = "../../core/src/com/unciv/"
    }

    fun write() {
        val docFile = File(docPath)
        val srcFile = File(srcPath)
        if (!srcFile.exists() || !docFile.parentFile.exists()) return

        val originalLines = if (docFile.exists()) docFile.readLines() else emptyList()
        val endIndex = originalLines.indexOf(endMarker).takeIf { it != -1 } ?: (originalLines.size - 1)
        val startIndex = originalLines.indexOf(startMarker).takeIf { it != -1 } ?: (endIndex + 1)

        val elements = mutableListOf<String>()

        for (file in srcFile.walk()) {
            if (file.path.endsWith(".kt")) {
                val results = Regex("getUiBackground\\((\\X*?)\"(?<path>.*)\"[ ,\n\r]*((BaseScreen\\.)?skinStrings\\.(?<defaultShape>.*)Shape)?\\X*?\\)")
                    .findAll(file.readText())
                for (result in results) {
                    val path = result.groups["path"]?.value
                    val name = path?.takeLastWhile { it != '/' } ?: ""
                    val defaultShape = result.groups["defaultShape"]?.value
                    if (name.isNotBlank())
                        elements.add("| ${path!!.dropLast(name.length)} | $name | $defaultShape | |")
                }
            }
        }

        val newLines = sequence {
            yieldAll(originalLines.subList(0, startIndex))
            yield(startMarker)
            yield("| Directory | Name | Default shape | Image |")
            yield("|---|:---:|:---:|---|")
            yieldAll(elements.asSequence().sorted())    // FileTreeWalk guarantees no specific order as it uses File.listFiles
            yield(endMarker)
            yieldAll(originalLines.subList(endIndex + 1, originalLines.size))
        }

        docFile.writeText(newLines.joinToString("\n"))
    }
}
