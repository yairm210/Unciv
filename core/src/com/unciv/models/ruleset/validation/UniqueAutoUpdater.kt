package com.unciv.models.ruleset.validation

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetFile
import com.unciv.models.ruleset.unique.Unique
import com.unciv.utils.Log

object UniqueAutoUpdater {

    /** Apply Unique replacements.
     *  @param mod The Ruleset to process. It must have a `folderLocation`.
     *  @param replaceableUniques A map old to new. The default is only for use with the 'mod-ci' command line argument from `DeskTopLauncher`.
     *                            The ModCheck UI will have produced this map using a combined Ruleset for replacement validation when appropriate.
     */
    fun autoupdateUniques(
        mod: Ruleset,
        replaceableUniques: HashMap<String, String> = getDeprecatedReplaceableUniques(mod, mod)
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

    /** Determine possible auto-corrections from Deprecation annotations.
     *  @param mod The Ruleset to scan
     *  @param rulesetForValidation The Ruleset to test potential fixed Uniques against. Can be same as [mod],
     *                              otherwise it should be a complex Ruleset combining [mod] with an appropriate base ruleset.
     *  @return A map of old to new. Will not include 'new' values that don't pass Unique validation against [rulesetForValidation].
     */
    fun getDeprecatedReplaceableUniques(mod: Ruleset, rulesetForValidation: Ruleset): HashMap<String, String> {
        val allUniques = mod.allUniques()
        val allDeprecatedUniques = HashSet<String>()
        val deprecatedUniquesToReplacementText = HashMap<String, String>()
        val validator = UniqueValidator(rulesetForValidation)
        val reportRulesetSpecificErrors = rulesetForValidation.modOptions.isBaseRuleset

        val deprecatedUniques = allUniques
            .filter { it.getDeprecationAnnotation() != null }

        val deprecatedConditionals = allUniques
            .flatMap { it.modifiers }
            .filter { it.getDeprecationAnnotation() != null }

        for (deprecatedUnique in deprecatedUniques + deprecatedConditionals) {
            if (allDeprecatedUniques.contains(deprecatedUnique.text)) continue
            allDeprecatedUniques.add(deprecatedUnique.text)

            var uniqueReplacementText = deprecatedUnique.getReplacementText(mod)
            while (Unique(uniqueReplacementText).getDeprecationAnnotation() != null)
                uniqueReplacementText = Unique(uniqueReplacementText).getReplacementText(mod)

            for (conditional in deprecatedUnique.modifiers)
                uniqueReplacementText += " <${conditional.text}>"
            val replacementUnique = Unique(uniqueReplacementText)

            val modErrors = validator.checkUnique(replacementUnique, false, null, reportRulesetSpecificErrors)
            for (error in modErrors)
                Log.error("ModError: %s - %s", error.text, error.errorSeverityToReport)
            if (modErrors.isNotEmpty()) continue // errors means no autoreplace

            deprecatedUniquesToReplacementText[deprecatedUnique.text] = uniqueReplacementText
            Log.debug("Replace \"%s\" with \"%s\"", deprecatedUnique.text, uniqueReplacementText)
        }

        return deprecatedUniquesToReplacementText
    }
}
