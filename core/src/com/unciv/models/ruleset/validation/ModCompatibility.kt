package com.unciv.models.ruleset.validation

import com.badlogic.gdx.files.FileHandle
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.ModCompatibility.meetsAllRequirements
import com.unciv.models.ruleset.validation.ModCompatibility.meetsBaseRequirements
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/**
 *  Helper collection dealing with declarative Mod compatibility
 *
 *  Implements:
 *  - [UniqueType.ModRequires]
 *  - [UniqueType.ModIncompatibleWith]
 *  - [UniqueType.ModIsAudioVisual]
 *  - [UniqueType.ModIsNotAudioVisual]
 *  - [UniqueType.ModIsAudioVisualOnly]
 *
 *  Methods:
 *  - [meetsBaseRequirements] - to build a checkbox list of Extension mods
 *  - [meetsAllRequirements] - to see if a mod is allowed in the context of a complete mod selection
 */
object ModCompatibility {
    /**
     *  Should the "Permanent Audiovisual Mod" checkbox be shown for [mod]?
     *
     *  Note: The guessing part may potentially be deprecated and removed if we get our Modders to complete declarative coverage.
     */
    @Readonly fun isAudioVisualMod(mod: Ruleset) = isAudioVisualDeclared(mod) ?: isAudioVisualGuessed(mod)

    @Readonly
    private fun isAudioVisualDeclared(mod: Ruleset): Boolean? {
        if (mod.modOptions.hasUnique(UniqueType.ModIsAudioVisualOnly)) return true
        if (mod.modOptions.hasUnique(UniqueType.ModIsAudioVisual)) return true
        if (mod.modOptions.hasUnique(UniqueType.ModIsNotAudioVisual)) return false
        return null
    }

    // If there's media (audio folders or any atlas), show the PAV choice...
    @Readonly
    private fun isAudioVisualGuessed(mod: Ruleset): Boolean {
        val folder = mod.folderLocation ?: return false  // Also catches isBuiltin
        @Readonly
        fun isSubFolderNotEmpty(modFolder: FileHandle, name: String): Boolean {
            val file = modFolder.child(name)
            if (!file.exists()) return false
            if (!file.isDirectory) return false
            return file.list().isNotEmpty()
        }
        if (isSubFolderNotEmpty(folder, "music")) return true
        if (isSubFolderNotEmpty(folder, "sounds")) return true
        if (isSubFolderNotEmpty(folder, "voices")) return true
        return folder.list("atlas").isNotEmpty()
    }

    @Readonly
    fun isExtensionMod(mod: Ruleset) =
        !mod.modOptions.isBaseRuleset
            && mod.name.isNotBlank()
            && !mod.modOptions.hasUnique(UniqueType.ModIsAudioVisualOnly)

    @Readonly
    fun isConstantsOnly(mod: Ruleset): Boolean {
        val folder = mod.folderLocation ?: return false
        if (folder.list("atlas").isNotEmpty()) return false
        val jsonFolder = folder.child("jsons")
        if (!jsonFolder.exists() || !jsonFolder.isDirectory) return false
        return jsonFolder.list().map { it.name() } == listOf("ModOptions.json")
    }

    @Pure
    fun modNameFilter(modName: String, filter: String): Boolean {
        if (modName == filter) return true
        if (filter.length < 3 || !filter.startsWith('*') || !filter.endsWith('*')) return false
        val partialName = filter.substring(1, filter.length - 1).lowercase()
        return partialName in modName.lowercase()
    }

    @Readonly
    private fun isIncompatibleWith(mod: Ruleset, otherMod: Ruleset) =
        mod.modOptions.getMatchingUniques(UniqueType.ModIncompatibleWith)
            .any { modNameFilter(otherMod.name, it.params[0]) }

    @Readonly
    private fun isIncompatible(mod: Ruleset, otherMod: Ruleset) =
        isIncompatibleWith(mod, otherMod) || isIncompatibleWith(otherMod, mod)

    /** Implement [UniqueType.ModRequires] and [UniqueType.ModIncompatibleWith]
     *  for selecting extension mods to show - after a [baseRuleset] was chosen.
     *
     *  - Extension mod is incompatible with [baseRuleset] -> Nope
     *  - Extension mod has no ModRequires unique -> OK
     *  - For each ModRequires: Not ([baseRuleset] meets filter OR any other cached _extension_ mod meets filter) -> Nope
     *  - All ModRequires tested -> OK
     */
    @Readonly
    fun meetsBaseRequirements(mod: Ruleset, baseRuleset: Ruleset): Boolean {
        if (isIncompatible(mod, baseRuleset)) return false

        val allOtherExtensionModNames = RulesetCache.values.asSequence()
            .filter { it != mod && !it.modOptions.isBaseRuleset && it.name.isNotEmpty() }
            .map { it.name }
            .toList()

        for (unique in mod.modOptions.getMatchingUniques(UniqueType.ModRequires)) {
            val filter = unique.params[0]
            if (modNameFilter(baseRuleset.name, filter)) continue
            if (allOtherExtensionModNames.none { modNameFilter(it, filter) }) return false
        }
        return true
    }

    /** Implement [UniqueType.ModRequires] and [UniqueType.ModIncompatibleWith]
     *  for _enabling_ shown extension mods depending on other extension choices
     *
     *  @param selectedExtensionMods all "active" mods for the compatibility tests - including the testee [mod] itself in this is allowed, it will be ignored. Will be iterated only once.
     *
     *  - No need to test: Extension mod is incompatible with [baseRuleset] - we expect [meetsBaseRequirements] did exclude it from the UI entirely
     *  - Extension mod is incompatible with any _other_ **selected** extension mod -> Nope
     *  - Extension mod has no ModRequires unique -> OK
     *  - For each ModRequires: Not([baseRuleset] meets filter OR any other **selected** extension mod meets filter) -> Nope
     *  - All ModRequires tested -> OK
     */
    @Readonly
    fun meetsAllRequirements(mod: Ruleset, baseRuleset: Ruleset, selectedExtensionMods: Iterable<Ruleset>): Boolean {
        val otherSelectedExtensionMods = selectedExtensionMods.filterNot { it == mod }.toList()
        if (otherSelectedExtensionMods.any { isIncompatible(mod, it) }) return false

        for (unique in mod.modOptions.getMatchingUniques(UniqueType.ModRequires)) {
            val filter = unique.params[0]
            if (modNameFilter(baseRuleset.name, filter)) continue
            if (otherSelectedExtensionMods.none { modNameFilter(it.name, filter) }) return false
        }
        return true
    }

    data class AllDeclaredDependenciesResult(val base: String?, val mods: List<String>, val errors: RulesetErrorList)

    /**
     *  Build a _recursive_ "set" of requirements, enough to build a "Complex Ruleset" for mod-checking.
     *
     *  The returned [AllDeclaredDependenciesResult] has the following fields:
     *  * [base][AllDeclaredDependenciesResult.base]: The determined base ruleset. Can be the mod itself. `null` means no requirement found a base ruleset
     *    (mods not caring about declaring requirements or no filter matched any - in which case `errors` might show the problem)
     *  * [mods][AllDeclaredDependenciesResult.mods]: The mod itself if it is an extension, plus all found extension mods matching any requirement filter
     *  * [errors][AllDeclaredDependenciesResult.errors]: Lists problems such as loops, infinite recursion, missing mods, no base or too many bases
     *
     *  @param forOptions If `true` raises the severity of the error message "No base ruleset declared" to `Error`, otherwise it's `OK` to accommodate non-declaring mods.
     *      Additionally, a message is appended indicating all found mods (ModCheckTab will use these to check against)
     */
    fun getAllDeclaredPrerequisites(mod: Ruleset, forOptions: Boolean): AllDeclaredDependenciesResult {
        val bases = mutableSetOf<Ruleset>()
        val mods = mutableSetOf<Ruleset>()
        val errors = RulesetErrorList() // Not passing [mod] means no suppression checks

        fun scanRecursive(current: Ruleset, level: Int) {
            when {
                current.modOptions.isBaseRuleset -> bases += current
                isExtensionMod(current) -> mods += current
            }

            if (level > 42) {
                errors.add("Dependencies for ${mod.name} recurse too deeply", RulesetErrorSeverity.ErrorOptionsOnly)
                return
            }

            for (unique in current.modOptions.getMatchingUniques(UniqueType.ModRequires)) {
                val filter = unique.params[0]
                var found = false
                for ((otherName, otherMod) in RulesetCache) {
                    when {
                        !modNameFilter(otherName, filter) -> continue
                        otherMod == mod -> {
                            if (level == 0)
                                errors.add("Mod ${mod.name} depends on itself", RulesetErrorSeverity.ErrorOptionsOnly)
                            else
                                errors.add("Mod ${mod.name} depends on itself via ${current.name}", RulesetErrorSeverity.ErrorOptionsOnly)
                            continue
                        }
                        else -> {
                            scanRecursive(otherMod, level + 1)
                            found = forOptions
                        }
                    }
                }
                if (!found) errors.add("Missing mod: \"$filter\"", RulesetErrorSeverity.ErrorOptionsOnly)
            }
        }

        scanRecursive(mod, 0)

        when {
            bases.isEmpty() -> errors.add("No base ruleset declared for ${mod.name}", if (forOptions) RulesetErrorSeverity.Error else RulesetErrorSeverity.OK)
            bases.size > 1 -> errors.add("Multiple base rulesets declared for ${mod.name}", RulesetErrorSeverity.WarningOptionsOnly)
        }

        val baseName = bases.firstOrNull()?.name
        val modNames = mods.map { it.name }
        if (forOptions) {
            errors.add("Checking combination of: ${listOfNotNull(baseName) + modNames}", RulesetErrorSeverity.OK)
        }

        return AllDeclaredDependenciesResult(baseName, modNames, errors)
    }
}
