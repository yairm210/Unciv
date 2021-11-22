package com.unciv.app.desktop

import com.unciv.models.ruleset.unique.UniqueType
import java.io.File

class UniqueDocsWriter {
    fun write() {
        val lines = ArrayList<String>()
        for (targetType in UniqueType.values().groupBy { it.targetTypes.first() }) {
            lines += "## " + targetType.key.name + " uniques"
            for (unique in targetType.value) {
                lines += "#### " + unique.text
                lines += "Applicable to: " + unique.targetTypes.joinToString()
                lines += ""
            }
        }
        File("../../docs/uniques.md").writeText(lines.joinToString("\n"))
    }
}
