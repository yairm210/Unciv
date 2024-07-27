package com.unciv.models.tilesets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.images.ImageGetter

object TileSetCache : HashMap<String, TileSet>() {

    fun getCurrent() : TileSet {
        return get(UncivGame.Current.settings.tileSet)!!
    }

    /** Combine [TileSetConfig]s for chosen mods.
     * Vanilla always active, even with a base ruleset mod active.
     * Permanent visual mods always included as long as UncivGame.Current is initialized.
     * Other active mods can be passed in parameter [ruleSetMods], if that is `null` and a game is in
     * progress, that game's mods are used instead.
     */
    fun assembleTileSetConfigs(ruleSetMods: Set<String>) {
        // Needs to be a list and not a set, so subsequent mods override the previous ones
        // Otherwise you rely on hash randomness to determine override order... not good
        val mods = ArrayList<String>()
        mods.add(TileSet.DEFAULT)
        if (UncivGame.isCurrentInitialized())
            mods.addAll(UncivGame.Current.settings.visualMods)
        mods.addAll(ruleSetMods)

        values.forEach { it.resetConfig() }

        for (mod in mods.distinct()) {
            for (tileset in values) {
                tileset.mergeModConfig(mod)
            }
        }
    }

    /** Load the json config files and do an initial [assembleTileSetConfigs] run without explicit mods.
     *
     *  Runs from UncivGame.create without exception handling, therefore should not be vulnerable to broken mods.
     *  (The critical point is json parsing in loadConfigFiles, which is now hardened)
     *  Also runs from ModManagementScreen after downloading a new mod or deleting one.
     */
    fun loadTileSetConfigs(consoleMode: Boolean = false) {

        clear()

        // Load internal TileSets
        val internalFiles: List<FileHandle> =
            if (consoleMode)
                FileHandle("jsons/TileSets").list().toList()
            else
                ImageGetter.getAvailableTilesets()
                .map { Gdx.files.internal("jsons/TileSets/$it.json") }
                .filter { it.exists() }
                .toList()

        loadConfigFiles(internalFiles, TileSet.DEFAULT)

        // Load mod TileSets
        val modsHandles =
            if (consoleMode) FileHandle("mods").list().toList()
            else RulesetCache.values.mapNotNull { it.folderLocation }

        for (modFolder in modsHandles) {
            val modName = modFolder.name()
            if (!modFolder.isDirectory || modName.startsWith('.'))
                continue
            val modFiles = modFolder.child("jsons/TileSets").list().toList()
            loadConfigFiles(modFiles, modName)
        }

        values.forEach { it.fallback = get(it.config.fallbackTileSet) }

        assembleTileSetConfigs(hashSetOf()) // no game is loaded, this is just the initial game setup
    }

    private fun loadConfigFiles(files: List<FileHandle>, configId: String) {
        for (configFile in files) {
            // jsons/TileSets shouldn't have subfolders, but if a mad modder has one, don't crash (file.readString would throw):
            if (configFile.isDirectory) continue
            // `files` is an unfiltered directory listing - ignore chaff
            if (configFile.extension() != "json") continue

            val config = try {
                json().fromJsonFile(TileSetConfig::class.java, configFile)
            } catch (_: Exception) {
                continue  // Ignore jsons that don't load
            }

            val name = configFile.nameWithoutExtension().removeSuffix("Config")
            val tileset = get(name) ?: TileSet(name)
            tileset.cacheConfigFromMod(configId, config)
            set(name, tileset)
        }
    }

    /** Determines potentially available TileSets - by scanning for TileSet jsons.
     *  Available before initialization finishes.
     */
    fun getAvailableTilesets(imageGetterTilesets: Sequence<String>): Set<String> {
        val modTilesetConfigFiles = UncivGame.Current.files.getModsFolder().list().asSequence()
            .filter { it.isDirectory && !it.name().startsWith('.') }
            .flatMap { it.child("jsons/TileSets").list().asSequence() }

        val builtinTilesetConfigFiles = imageGetterTilesets
            .map { Gdx.files.internal("jsons/TileSets/$it.json") }

        return (builtinTilesetConfigFiles + modTilesetConfigFiles)
            .filter { it.exists() }
            .map { it.nameWithoutExtension().removeSuffix("Config") }
            .toSet()
    }
}
