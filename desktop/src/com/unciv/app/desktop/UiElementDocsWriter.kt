package com.unciv.app.desktop

import java.io.File

class UiElementDocsWriter {
    fun write() {
        val lines = File("../../docs/Modders/Creating-a-UI-skin.md").readLines()
        val startIndex = lines.indexOf("<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION -->")
        val endIndex = lines.indexOf("<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION_END -->")

        val table = mutableListOf(
            "<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION -->",
            "| Directory | Name | Default shape |",
            "|---|:---:|:---:|"
        )

        val elements = mutableListOf<String>()

        File("../../core/src/com/unciv/").walk().forEach { file ->
            if (file.path.endsWith(".kt")) {
                val results = Regex("getUiBackground\\(\\X*?\"(?<path>.*)\"\\X*?\\)").findAll(file.readText())
                for (result in results) {
                    if ((result.groups.size) > 1) {
                        val path = result.groups[1]!!.value
                        val name = path.takeLastWhile { it != '/' }
                        if (name.isNotBlank())
                            elements.add("| ${path.dropLast(name.length)} | $name |")
                    }
                }
            }
        }

        table.addAll(elements.sorted())
        table.add("<!--- DO NOT REMOVE OR MODIFY THIS LINE UI_ELEMENT_TABLE_REGION_END -->")

        val newLines = lines.subList(0, startIndex) + table + lines.subList(endIndex + 1, lines.size)

        File("../../docs/Modders/Creating-a-UI-skin.md").writeText(newLines.joinToString("\n"))
    }
}
