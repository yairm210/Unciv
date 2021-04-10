package com.unciv.models.tilesets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser

object TileSetCache : HashMap<String, TileSetConfig>(){
    fun loadTileSetConfigs(consoleMode: Boolean = false, printOutput: Boolean = false){
        clear()
        var tileSetName = ""

        //load default TileSets
        for (configFile in Gdx.files.local("jsons/TileSets").list()){
            tileSetName = configFile.nameWithoutExtension().removeSuffix("Config")
            try {
                if (this[tileSetName] == null)
                    this[tileSetName] = JsonParser().getFromJson(TileSetConfig::class.java, configFile)
                else
                    this[tileSetName]!!.updateConfig(JsonParser().getFromJson(TileSetConfig::class.java, configFile))
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
        val modsHandles = if (consoleMode) FileHandle("mods").list()
        else Gdx.files.local("mods").list()

        for (modFolder in modsHandles) {
            if (modFolder.name().startsWith('.')) continue
            if (!modFolder.isDirectory) continue

            try {
                for (configFile in modFolder.child("jsons/TileSets").list()){
                    tileSetName = configFile.nameWithoutExtension().removeSuffix("Config")
                    if (this[tileSetName] == null)
                        this[tileSetName] = JsonParser().getFromJson(TileSetConfig::class.java, configFile)
                    else
                        this[tileSetName]!!.updateConfig(JsonParser().getFromJson(TileSetConfig::class.java, configFile))
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
    }
}