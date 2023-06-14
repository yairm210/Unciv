package com.unciv.app.desktop

import java.io.File

class UiElementDocsWriter {
    companion object {
        private const val docPath = "../../docs/Modders/Creating-a-UI-skin.md"
        private const val startMarker = "<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION -->"
        private const val endMarker = "<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION_END -->"
        private const val srcPath = "../../core/src/com/unciv/"
    }

    @Suppress("RegExpRepeatedSpace")  // IDE doesn't know about commented RegExes
    fun write() {
        val docFile = File(docPath)
        val srcFile = File(srcPath)
        if (!srcFile.exists() || !docFile.parentFile.exists()) return

        val originalLines = if (docFile.exists()) docFile.readLines() else emptyList()
        val endIndex = originalLines.indexOf(endMarker).takeIf { it != -1 } ?: (originalLines.size - 1)
        val startIndex = originalLines.indexOf(startMarker).takeIf { it != -1 } ?: (endIndex + 1)

        val elements = mutableListOf<String>()

        val backgroundRegex = Regex("""
            getUiBackground\s*\(\s*     # function call, whitespace around opening round bracket optional.
            (?:path\s*=\s*)?            # allow for named parameter
            "(?<path>[^"]*)"\s*         # captures "path", anything between double-quotes, not allowing for embedded quotes
            (?:,\s*                     # group for optional default parameter
                (?:default\s*=\s*)?     # allow for named parameter
                (?:(?:BaseScreen\.)?skinStrings\.)?     # skip qualifiers, optional
                (?<default>.*)Shape     # capture default, check required "Shape" suffix but don't capture it
            \s*)?[,)]                   # ends default parameter group and checks closing round bracket of the getUiBackground call - or check a comma and ignore tintColor parameter
            """.trimIndent(), RegexOption.COMMENTS)

        val colorRegex = Regex("""
            getUIColor\s*\(\s*          # function call, whitespace around opening round bracket optional. All \s also allow line breaks!
            (?:path\s*=\s*)?            # allow for named parameter
            "(?<path>[^"]*)"\s*         # captures "path", anything between double-quotes, not allowing for embedded quotes
            (?:,\s*                     # group for optional default parameter
                (?:default\s*=\s*)?     # allow for named parameter
                (?:Color\s*\(|colorFromRGB\s*\(|Color\.)   # recognize only Color constructor, colorFromRGB helper, or Color.* constants as argument
                (?<default>[^)]*)       # capture "default" up until a closing round bracket
            \s*)?\)                     # ends default parameter group and checks closing round bracket of the getUIColor call
            """, RegexOption.COMMENTS)

        val borderedTableRegEx = Regex("""
            (?<!class )                 # ignore the class definition itself (negative lookbehind)
            BorderedTable\s*\(\s*       # look for instance creation _or_ subclassing, thankfully similar
            (?:path\s*=\s*)?            # allow for named parameter
            "(?<path>[^"]*)"\s*         # capture string literal for path
            (?:,\s*                     # group for optional defaultBgShape parameter
                (?!defaultBgBorder\s*=)         # skip if second parameter skipped by naming the third
                (?:defaultBgShape\s*=\s*)?     # allow for named parameter
                (?:(?:BaseScreen\.)?skinStrings\.)?     # skip qualifiers, optional
                (?<defaultBgShape>.*)Shape     # capture default, check required "Shape" suffix but don't capture it
            \s*)?                     # ends defaultBgShape parameter group
            (?:,\s*                     # group for optional defaultBgBorder parameter
                (?:defaultBgBorder\s*=\s*)?     # allow for named parameter
                (?:(?:BaseScreen\.)?skinStrings\.)?     # skip qualifiers, optional
                (?<defaultBgBorder>.*)Shape     # capture default, check required "Shape" suffix but don't capture it
            \s*)?                     # ends defaultBgBorder parameter group
            \)                       # check closing bracket
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

        val newLines = sequence {
            yieldAll(originalLines.subList(0, startIndex))
            yield(startMarker)
            yield("| Directory | Name | Default shape | Image |")
            yield("|---|:---:|:---:|---|")
            yieldAll(elements.asSequence().sorted().distinct())    // FileTreeWalk guarantees no specific order as it uses File.listFiles
            yield(endMarker)
            yieldAll(originalLines.subList(endIndex + 1, originalLines.size))
        }

        docFile.writeText(newLines.joinToString("\n"))
    }
}
