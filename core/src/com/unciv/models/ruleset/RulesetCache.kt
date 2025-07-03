package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapParameters
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.validation.RulesetError
import com.unciv.models.ruleset.validation.RulesetErrorList
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.ruleset.validation.getRelativeTextDistance
import com.unciv.utils.Log
import com.unciv.utils.Tag

/** Loading mods is expensive, so let's only do it once and
 * save all of the loaded rulesets somewhere for later use
 *  */
object RulesetCache : HashMap<String, Ruleset>() {
    /** Similarity below which an untyped unique can be considered a potential misspelling.
     * Roughly corresponds to the fraction of the Unique placeholder text that can be different/misspelled, but with some extra room for [getRelativeTextDistance] idiosyncrasies. */
    var uniqueMisspellingThreshold = 0.15 // Tweak as needed. Simple misspellings seem to be around 0.025, so would mostly be caught by 0.05. IMO 0.1 would be good, but raising to 0.15 also seemed to catch what may be an outdated Unique.


    /** Returns error lines from loading the rulesets, so we can display the errors to users */
    fun loadRulesets(consoleMode: Boolean = false, noMods: Boolean = false): List<String> {
        val newRulesets = HashMap<String, Ruleset>()

        for (ruleset in BaseRuleset.entries) {
            val fileName = "jsons/${ruleset.fullName}"
            val fileHandle =
                if (consoleMode) FileHandle(fileName)
                else Gdx.files.internal(fileName)
            newRulesets[ruleset.fullName] = Ruleset().apply {
                name = ruleset.fullName
                load(fileHandle)
            }
        }
        this.putAll(newRulesets)

        val errorLines = ArrayList<String>()
        if (!noMods) {
            val modsHandles = if (consoleMode) FileHandle("mods").list()
                else UncivGame.Current.files.getModsFolder().list()

            for (modFolder in modsHandles) {
                if (modFolder.name().startsWith('.')) continue
                if (!modFolder.isDirectory) continue
                try {
                    val modRuleset = Ruleset()
                    modRuleset.name = modFolder.name()
                    modRuleset.load(modFolder.child("jsons"))
                    modRuleset.folderLocation = modFolder
                    newRulesets[modRuleset.name] = modRuleset
                    Log.debug("Mod loaded successfully: %s", modRuleset.name)
                } catch (ex: Exception) {
                    errorLines += "Exception loading mod '${modFolder.name()}':"
                    errorLines += "  ${ex.localizedMessage}"
                    errorLines += "  ${ex.cause?.localizedMessage}"
                }
            }
            if (Log.shouldLog()) for (line in errorLines) Log.debug(line)
        }

        // We save the 'old' cache values until we're ready to replace everything, so that the cache isn't empty while we try to load ruleset files
        // - this previously lead to "can't find Vanilla ruleset" if the user had a lot of mods and downloaded a new one
        this.clear()
        this.putAll(newRulesets)

        return errorLines
    }


    fun getVanillaRuleset() = this[BaseRuleset.Civ_V_Vanilla.fullName]!!.clone() // safeguard, so no-one edits the base ruleset by mistake

    fun getSortedBaseRulesets(): List<String> {
        val baseRulesets = values
            .filter { it.modOptions.isBaseRuleset }
            .map { it.name }
            .distinct()
        if (baseRulesets.size < 2) return baseRulesets

        // We sort the base rulesets such that the ones unciv provides are on the top,
        // and the rest is alphabetically ordered.
        return baseRulesets.sortedWith(
            compareBy(
                { ruleset ->
                    BaseRuleset.entries
                        .firstOrNull { br -> br.fullName == ruleset }?.ordinal
                        ?: BaseRuleset.entries.size
                },
                { it }
            )
        )
    }

    /** Creates a combined [Ruleset] from a list of mods contained in [parameters]. */
    fun getComplexRuleset(parameters: MapParameters) =
        getComplexRuleset(parameters.mods, parameters.baseRuleset)

    /** Creates a combined [Ruleset] from a list of mods contained in [parameters]. */
    fun getComplexRuleset(parameters: GameParameters) =
        getComplexRuleset(parameters.mods, parameters.baseRuleset)

    /**
     * Creates a combined [Ruleset] from a list of mods.
     * If no baseRuleset is passed in [optionalBaseRuleset] (or a non-existing one), then the vanilla Ruleset is included automatically.
     * Any mods in the [mods] parameter marked as base ruleset (or not loaded in [RulesetCache]) are ignored.
     */
    fun getComplexRuleset(mods: LinkedHashSet<String>, optionalBaseRuleset: String? = null): Ruleset {
        val baseRuleset =
                if (containsKey(optionalBaseRuleset) && this[optionalBaseRuleset]!!.modOptions.isBaseRuleset)
                    this[optionalBaseRuleset]!!
                else getVanillaRuleset()

        val loadedMods = mods.asSequence()
            .filter { containsKey(it) }
            .map { this[it]!! }
            .filter { !it.modOptions.isBaseRuleset }

        return getComplexRuleset(baseRuleset, loadedMods.asIterable())
    }

    /**
     * Creates a combined [Ruleset] from [baseRuleset] and [extensionRulesets] which must only contain non-base rulesets.
     */
    fun getComplexRuleset(baseRuleset: Ruleset, extensionRulesets: Iterable<Ruleset>): Ruleset {
        val newRuleset = Ruleset()

        val loadedMods = extensionRulesets.asSequence() + baseRuleset

        for (mod in loadedMods.sortedByDescending { it.modOptions.isBaseRuleset }) {
            if (mod.modOptions.isBaseRuleset) {
                // This is so we don't keep using the base ruleset's uniques *by reference* and add to in ad infinitum
                newRuleset.modOptions.uniques = ArrayList()
                newRuleset.modOptions.isBaseRuleset = true
                // Default tileset and unitset are according to base ruleset
                newRuleset.modOptions.tileset = mod.modOptions.tileset
                newRuleset.modOptions.unitset = mod.modOptions.unitset
            }
            newRuleset.add(mod)
            newRuleset.mods += mod.name
        }
        newRuleset.updateBuildingCosts() // only after we've added all the mods can we calculate the building costs
        newRuleset.updateResourceTransients()

        return newRuleset
    }

    /**
     * Runs [Ruleset.getErrorList] on a temporary [combined Ruleset][getComplexRuleset] for a list of [mods]
     */
    fun checkCombinedModLinks(
        mods: LinkedHashSet<String>,
        baseRuleset: String? = null,
        tryFixUnknownUniques: Boolean = false
    ): RulesetErrorList {
        return try {
            val newRuleset = getComplexRuleset(mods, baseRuleset)
            newRuleset.modOptions.isBaseRuleset = true // This is so the checkModLinks finds all connections
            newRuleset.getErrorList(tryFixUnknownUniques)
        } catch (ex: UncivShowableException) {
            // This happens if a building is dependent on a tech not in the base ruleset
            //  because newRuleset.updateBuildingCosts() in getComplexRuleset() throws an error
            RulesetErrorList.of(ex.message, RulesetErrorSeverity.Error)
        }
    }
}
