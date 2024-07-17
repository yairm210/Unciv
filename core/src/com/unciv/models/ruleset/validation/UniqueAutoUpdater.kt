package com.unciv.models.ruleset.validation

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetFile
import com.unciv.models.ruleset.unique.Unique
import com.unciv.utils.Log
import com.unciv.utils.debug

object UniqueAutoUpdater {

    fun autoupdateUniques(
        mod: Ruleset,
        replaceableUniques: HashMap<String, String> = getDeprecatedReplaceableUniques(mod)
    ) {
        val filesToReplace = RulesetFile.entries.map { it.filename }
        val jsonFolder = mod.folderLocation!!.child("jsons")
        for (fileName in filesToReplace) {
            val file = jsonFolder.child(fileName)
            if (!file.exists() || file.isDirectory) continue
            var newFileText = file.readString()
            for ((original, replacement) in replaceableUniques) {
                newFileText = newFileText.replace("\"$original\"", "\"$replacement\"")
                newFileText = newFileText.replace("<$original>", "<$replacement>") // For modifiers
            }
            file.writeString(newFileText, false)
        }
    }



    fun getDeprecatedReplaceableUniques(mod: Ruleset): HashMap<String, String> {
        val allUniques = mod.allUniques()
        val allDeprecatedUniques = HashSet<String>()
        val deprecatedUniquesToReplacementText = HashMap<String, String>()

        val deprecatedUniques = allUniques
            .filter { it.getDeprecationAnnotation() != null }

        val deprecatedConditionals = allUniques
            .flatMap { it.conditionals }
            .filter { it.getDeprecationAnnotation() != null }

        for (deprecatedUnique in deprecatedUniques + deprecatedConditionals) {
            if (allDeprecatedUniques.contains(deprecatedUnique.text)) continue
            allDeprecatedUniques.add(deprecatedUnique.text)

            var uniqueReplacementText = deprecatedUnique.getReplacementText(mod)
            while (Unique(uniqueReplacementText).getDeprecationAnnotation() != null)
                uniqueReplacementText = Unique(uniqueReplacementText).getReplacementText(mod)

            for (conditional in deprecatedUnique.conditionals)
                uniqueReplacementText += " <${conditional.text}>"
            val replacementUnique = Unique(uniqueReplacementText)

            val modInvariantErrors = UniqueValidator(mod).checkUnique(
                replacementUnique,
                false,
                null,
                true
            )
            for (error in modInvariantErrors)
                Log.error("ModInvariantError: %s - %s", error.text, error.errorSeverityToReport)
            if (modInvariantErrors.isNotEmpty()) continue // errors means no autoreplace

            if (mod.modOptions.isBaseRuleset) {
                val modSpecificErrors = UniqueValidator(mod).checkUnique(
                    replacementUnique,
                    false,
                    null,
                    true
                )
                for (error in modSpecificErrors)
                    Log.error("ModSpecificError: %s - %s", error.text, error.errorSeverityToReport)
                if (modSpecificErrors.isNotEmpty()) continue
            }

            deprecatedUniquesToReplacementText[deprecatedUnique.text] = uniqueReplacementText
            debug("Replace \"%s\" with \"%s\"", deprecatedUnique.text, uniqueReplacementText)
        }

        return deprecatedUniquesToReplacementText
    }
}
