package com.unciv.models.tilesets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.ui.utils.ImageGetter

object TileSetCache : HashMap<String, TileSetConfig>() {
    private data class TileSetAndMod(val tileSet: String, val mod: String)
    private val allConfigs = HashMap<TileSetAndMod, TileSetConfig>()

    fun assembleTileSetConfigs() {
        val mods = mutableSetOf("")     // Vanilla always active, even with base ruleset mods selected
        if (UncivGame.isCurrentInitialized()) {
            val settings = UncivGame.Current.settings
            if (settings.tileSet + " Tileset" in settings.visualMods) mods.add(settings.tileSet + " Tileset")
            if (UncivGame.Current.isGameInfoInitialized()) {
                mods.addAll(UncivGame.Current.gameInfo.ruleSet.mods)
            }
        }
        clear()
        allConfigs.filter { it.key.mod in mods }.forEach {
            if (it.key.tileSet in this) this[it.key.tileSet]!!.updateConfig(it.value)
            else this[it.key.tileSet] = it.value
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

        assembleTileSetConfigs()
    }
}