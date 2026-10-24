package com.unciv.models.skins

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.debug

object SkinCache : HashMap<String, SkinConfig>() {
    private data class SkinAndMod(val skin: String, val mod: String)
    private val allConfigs = HashMap<SkinAndMod, SkinConfig>()

    /** Combine [SkinConfig]s for chosen mods.
     * Vanilla always active, even with a base ruleset mod active.
     * Permanent visual mods always included as long as UncivGame.Current is initialized.
     * Other active mods can be passed in parameter [ruleSetMods], if that is `null` and a game is in
     * progress, that game's mods are used instead.
     */
    fun assembleSkinConfigs(ruleSetMods: Set<String>) {
        // Needs to be a list and not a set, so subsequent mods override the previous ones
        // Otherwise you rely on hash randomness to determine override order... not good
        val mods = mutableListOf("")  // Not an emptyList - placeholder for built-in skin
        if (UncivGame.isCurrentInitialized()) {
            mods.addAll(UncivGame.Current.settings.visualMods)
        }
        mods.addAll(ruleSetMods)
        clear()
        for (mod in mods.distinct()) {
            for ((key, config) in allConfigs.filter { it.key.mod == mod } ) { // Built-in skins all have empty strings as their `.mod`, so loop through all of them.
                val skin = key.skin
                if (skin in this) this[skin]!!.updateConfig(config)
                else this[skin] = config.clone()
            }
        }
    }

    fun loadSkinConfigs(consoleMode: Boolean = false) {
        allConfigs.clear()
        var skinName = ""

        //load internal Skins
        val fileHandles: Sequence<FileHandle> =
                if (consoleMode) FileHandle("jsons/Skins").list().asSequence()
                else ImageGetter.getAvailableSkins()
                    .map { Gdx.files.internal("jsons/Skins/$it.json") }
                    .filter { it.exists() }

        for (configFile in fileHandles) {
            skinName = configFile.nameWithoutExtension().removeSuffix("Config")
            try {
                val key = SkinAndMod(skinName, "")
                assert(key !in allConfigs)
                allConfigs[key] = json().fromJsonFile(SkinConfig::class.java, configFile)
                debug("SkinConfig loaded successfully: %s", configFile.name())
            } catch (ex: Exception) {
                debug("Exception loading SkinConfig '%s':", configFile.path())
                debug("  %s", ex.localizedMessage)
                debug("  (Source file %s line %s)", ex.stackTrace[0].fileName, ex.stackTrace[0].lineNumber)
            }
        }

        //load mod Skins
        val modsHandles =
                if (consoleMode) FileHandle("mods").list().toList()
                else RulesetCache.values.mapNotNull { it.folderLocation }

        for (modFolder in modsHandles) {
            val modName = modFolder.name()
            if (modName.startsWith('.')) continue
            if (!modFolder.isDirectory) continue

            for (configFile in modFolder.child("jsons/Skins").list()) {
                try {
                    skinName = configFile.nameWithoutExtension().removeSuffix("Config")
                    val key = SkinAndMod(skinName, modName)
                    assert(key !in allConfigs)
                    allConfigs[key] = json().fromJsonFile(SkinConfig::class.java, configFile)
                    debug("Skin loaded successfully: %s", configFile.path())
                } catch (ex: Exception) {
                    debug("Exception loading Skin '%s/jsons/Skins/%s':", modFolder.name(), skinName)
                    debug("  %s", ex.localizedMessage)
                    debug("  (Source file %s line %s)", ex.stackTrace[0].fileName, ex.stackTrace[0].lineNumber)
                }
            }
        }

        assembleSkinConfigs(emptySet()) // no game is loaded, this is just the initial game setup
    }
}
