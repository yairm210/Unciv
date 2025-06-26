package com.unciv.models.ruleset.validation

import com.badlogic.gdx.files.FileHandle
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.ModCompatibility.meetsAllRequirements
import com.unciv.models.ruleset.validation.ModCompatibility.meetsBaseRequirements

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
    fun isAudioVisualMod(mod: Ruleset) = isAudioVisualDeclared(mod) ?: isAudioVisualGuessed(mod)

    private fun isAudioVisualDeclared(mod: Ruleset): Boolean? {
        if (mod.modOptions.hasUnique(UniqueType.ModIsAudioVisualOnly)) return true
        if (mod.modOptions.hasUnique(UniqueType.ModIsAudioVisual)) return true
        if (mod.modOptions.hasUnique(UniqueType.ModIsNotAudioVisual)) return false
        return null
    }

    // If there's media (audio folders or any atlas), show the PAV choice...
    private fun isAudioVisualGuessed(mod: Ruleset): Boolean {
        val folder = mod.folderLocation ?: return false  // Also catches isBuiltin
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

    fun isExtensionMod(mod: Ruleset) =
        !mod.modOptions.isBaseRuleset
            && mod.name.isNotBlank()
            && !mod.modOptions.hasUnique(UniqueType.ModIsAudioVisualOnly)

    fun isConstantsOnly(mod: Ruleset): Boolean {
        val folder = mod.folderLocation ?: return false
        if (folder.list("atlas").isNotEmpty()) return false
        val jsonFolder = folder.child("jsons")
        if (!jsonFolder.exists() || !jsonFolder.isDirectory) return false
        return jsonFolder.list().map { it.name() } == listOf("ModOptions.json")
    }

    fun modNameFilter(modName: String, filter: String): Boolean {
        if (modName == filter) return true
        if (filter.length < 3 || !filter.startsWith('*') || !filter.endsWith('*')) return false
        val partialName = filter.substring(1, filter.length - 1).lowercase()
        return partialName in modName.lowercase()
    }

    private fun isIncompatibleWith(mod: Ruleset, otherMod: Ruleset) =
        mod.modOptions.getMatchingUniques(UniqueType.ModIncompatibleWith)
            .any { modNameFilter(otherMod.name, it.params[0]) }

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
}
