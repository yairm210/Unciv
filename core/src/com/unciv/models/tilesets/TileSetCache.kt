package com.unciv.models.tilesets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.ui.utils.ImageGetter

object TileSetCache : HashMap<String, TileSetConfig>() {
    private data class TileSetAndMod(val tileSet: String, val mod: String)
    private val allConfigs = HashMap<TileSetAndMod, TileSetConfig>()

    /** Combine [TileSetConfig]s for chosen mods.
     * Vanilla always active, even with a base ruleset mod active.
     * Permanent visual mods always included as long as UncivGame.Current is initialized.
     * Other active mods can be passed in parameter [ruleSetMods], if that is `null` and a game is in
     * progress, that game's mods are used instead.
     */
    fun assembleTileSetConfigs(ruleSetMods: Set<String>) {
        // Needs to be a list and not a set, so subsequent mods override the previous ones
        // Otherwise you rely on hash randomness to determine override order... not good
        val mods = mutableListOf("")
        if (UncivGame.isCurrentInitialized()) {
            mods.addAll(UncivGame.Current.settings.visualMods)
        }
        mods.addAll(ruleSetMods)
        clear()
        for (mod in mods.distinct()) {
            val entry = allConfigs.entries.firstOrNull { it.key.mod == mod } ?: continue

            val tileSet = entry.key.tileSet
            if (tileSet in this) this[tileSet]!!.updateConfig(entry.value)
            else this[tileSet] = entry.value
        }
    }

    fun loadTileSetConfigs(consoleMode: Boolean = false, printOutput: Boolean = false){
        allConfigs.clear()
        var tileSetName = ""

        //load internal TileSets
        val fileHandles: Sequence<FileHandle> =
            if (consoleMode) FileHandle("jsons/TileSets").list().asSequence()
            else ImageGetter.getAvailableTilesets().map { Gdx.files.internal("jsons/TileSets/$it.json")}.filter { it.exists() }

        for (configFile in fileHandles){
            tileSetName = configFile.nameWithoutExtension().removeSuffix("Config")
            try {
                val key = TileSetAndMod(tileSetName, "")
                assert(key !in allConfigs)
                allConfigs[key] = JsonParser().getFromJson(TileSetConfig::class.java, configFile)
                if (printOutput) {
                    println("TileSetConfig loaded successfully: ${configFile.name()}")
                    println()
                }
            } catch (ex: Exception){
                if (printOutput){
                    println("Exception loading TileSetConfig '${configFile.path()}':")
                    println("  ${ex.localizedMessage}")
                    println("  (Source file ${ex.stackTrace[0].fileName} line ${ex.stackTrace[0].lineNumber})")
                }
            }
        }

        //load mod TileSets
        val modsHandles = 
            if (consoleMode) FileHandle("mods").list()
            else Gdx.files.local("mods").list()

        for (modFolder in modsHandles) {
            val modName = modFolder.name()
            if (modName.startsWith('.')) continue
            if (!modFolder.isDirectory) continue

            try {
                for (configFile in modFolder.child("jsons/TileSets").list()){
                    tileSetName = configFile.nameWithoutExtension().removeSuffix("Config")
                    val key = TileSetAndMod(tileSetName, modName)
                    assert(key !in allConfigs)
                    allConfigs[key] = JsonParser().getFromJson(TileSetConfig::class.java, configFile)
                    if (printOutput) {
                        println("TileSetConfig loaded successfully: ${configFile.path()}")
                        println()
                    }
                }
            } catch (ex: Exception){
                if (printOutput) {
                    println("Exception loading TileSetConfig '${modFolder.name()}/jsons/TileSets/${tileSetName}':")
                    println("  ${ex.localizedMessage}")
                    println("  (Source file ${ex.stackTrace[0].fileName} line ${ex.stackTrace[0].lineNumber})")
                }
            }
        }

        assembleTileSetConfigs(hashSetOf()) // no game is loaded, this is just the initial game setup
    }
}
