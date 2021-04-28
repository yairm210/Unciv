package com.unciv.models.tilesets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser

object TileSetCache : HashMap<String, TileSetConfig>(){
    fun loadTileSetConfigs(consoleMode: Boolean = false, printOutput: Boolean = false){
        clear()
        var tileSetName = ""

        //load internal TileSets
        val fileHandles = if (consoleMode) FileHandle("jsons/TileSets").list()
        else Gdx.files.internal("jsons/TileSets").list()

        for (configFile in fileHandles){
            tileSetName = configFile.nameWithoutExtension().removeSuffix("Config")
            loadConfig(tileSetName, configFile, printOutput)
        }

        //load mod TileSets
        val modsHandles = if (consoleMode) FileHandle("mods").list()
        else Gdx.files.local("mods").list()

        for (modFolder in modsHandles) {
            if (modFolder.name().startsWith('.')) continue
            if (!modFolder.isDirectory) continue

            for (configFile in modFolder.child("jsons/TileSets").list()){
                tileSetName = configFile.nameWithoutExtension().removeSuffix("Config")
                loadConfig(tileSetName, configFile, printOutput)
            }
        }
    }

    private fun loadConfig(tileSetName: String, configFile: FileHandle, printOutput: Boolean){
        try {
            if (this[tileSetName] == null)
                this[tileSetName] = JsonParser().getFromJson(TileSetConfig::class.java, configFile)
            else
                this[tileSetName]!!.updateConfig(JsonParser().getFromJson(TileSetConfig::class.java, configFile))
            this[tileSetName]!!.setTransients()

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
}