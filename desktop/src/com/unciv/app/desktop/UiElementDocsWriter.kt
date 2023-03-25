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
        val backgroundRegex = Regex("""getUiBackground\((\X*?)"(?<path>.*)"[ ,\n\r]*((BaseScreen\.)?skinStrings\.(?<default>.*)Shape)?\X*?\)""")
        val colorRegex = Regex("""
            getUIColor\s*\(\s*          # function call, whitespace around opening round bracket optional. All \s also allow line breaks!
            "(?<path>[^"]*)"\s*         # captures "path", anything between double-quotes, not allowing for embedded quotes
            (?:,\s*                     # group for optional default parameter
                (?:default\s*=\s*)?     # allow for named parameter
                (?:Colors\s*\(|colorFromRGB\s*\(|Color\.)   # recognize only Color constructor, colorFromRGB helper, or Color.* constants as argument
                (?<default>[^)]*)       # capture "default" up until a closing round bracket
            )\s*\)                      # ends default parameter group and checks closing round bracket of the getUIColor call
            """, RegexOption.COMMENTS)

        for (file in srcFile.walk()) {
            if (file.path.endsWith(".kt")) {
                val sourceText = file.readText()
                val matches: Sequence<MatchResult> =
                        backgroundRegex.findAll(sourceText) + colorRegex.findAll(sourceText)
                for (result in matches) {
                    val path = result.groups["path"]?.value
                    val name = path?.takeLastWhile { it != '/' } ?: ""
                    val default = result.groups["default"]?.value
                    if (name.isNotBlank())
                        elements.add("| ${path!!.dropLast(name.length)} | $name | $default | |")
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
