package com.unciv.logic.files

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.SerializationException
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.CompatibilityVersion
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameInfoSerializationVersion
import com.unciv.logic.HasGameInfoSerializationVersion
import com.unciv.logic.UncivShowableException
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.doMigrations
import com.unciv.models.metadata.isMigrationNecessary
import com.unciv.ui.screens.savescreens.Gzip
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.debug
import kotlinx.coroutines.Job
import java.io.File
import java.io.Writer

private const val SAVE_FILES_FOLDER = "SaveFiles"
private const val MULTIPLAYER_FILES_FOLDER = "MultiplayerGames"
private const val AUTOSAVE_FILE_NAME = "Autosave"
const val SETTINGS_FILE_NAME = "GameSettings.json"

class UncivFiles(
    /**
     * This is necessary because the Android turn check background worker does not hold any reference to the actual [com.badlogic.gdx.Application],
     * which is normally responsible for keeping the [Gdx] static variables from being garbage collected.
     */
    private val files: Files
) {
    init {
        debug("Creating UncivFiles, localStoragePath: %s, externalStoragePath: %s",
            files.localStoragePath, files.externalStoragePath)
    }
    //region Data

    var autoSaveJob: Job? = null

    //endregion
    //region Helpers

    fun getSave(gameName: String): FileHandle {
        return getSave(SAVE_FILES_FOLDER, gameName)
    }
    fun getMultiplayerSave(gameName: String): FileHandle {
        return getSave(MULTIPLAYER_FILES_FOLDER, gameName)
    }

    private fun getSave(saveFolder: String, gameName: String): FileHandle {
        debug("Getting save %s from folder %s, preferExternal: %s",
            gameName, saveFolder, preferExternalStorage, files.externalStoragePath)
        val location = "${saveFolder}/$gameName"
        val localFile = files.local(location)
        val externalFile = files.external(location)

        val toReturn = if (files.isExternalStorageAvailable && (
                        preferExternalStorage && (externalFile.exists() || !localFile.exists())
                        || !preferExternalStorage && (externalFile.exists() && !localFile.exists())
                ) ) {
            externalFile
        } else {
            localFile
        }

        debug("Save found: %s", toReturn.file().absolutePath)
        return toReturn
    }

    /**
     * @throws GdxRuntimeException if the [path] represents a directory
     */
    fun fileWriter(path: String, append: Boolean = false): Writer {
        val file = if (preferExternalStorage && files.isExternalStorageAvailable) {
            files.external(path)
        } else {
            files.local(path)
        }
        return file.writer(append, Charsets.UTF_8.name())
    }

    fun getMultiplayerSaves(): Sequence<FileHandle> {
        return getSaves(MULTIPLAYER_FILES_FOLDER)
    }

    fun getSaves(autoSaves: Boolean = true): Sequence<FileHandle> {
        val saves = getSaves(SAVE_FILES_FOLDER)
        val filteredSaves = if (autoSaves) { saves } else { saves.filter { !it.name().startsWith(
            AUTOSAVE_FILE_NAME
        ) }}
        return filteredSaves
    }

    private fun getSaves(saveFolder: String): Sequence<FileHandle> {
        debug("Getting saves from folder %s, externalStoragePath: %s", saveFolder, files.externalStoragePath)
        val localFiles = files.local(saveFolder).list().asSequence()

        val externalFiles = if (files.isExternalStorageAvailable && files.local("").file().absolutePath != files.external("").file().absolutePath) {
            files.external(saveFolder).list().asSequence()
        } else {
            emptySequence()
        }

        debug("Local files: %s, external files: %s",
            { localFiles.joinToString(prefix = "[", postfix = "]", transform = { it.file().absolutePath }) },
            { externalFiles.joinToString(prefix = "[", postfix = "]", transform = { it.file().absolutePath }) })
        return localFiles + externalFiles
    }

    /**
     * @return `true` if successful.
     * @throws SecurityException when delete access was denied
     */
    fun deleteSave(gameName: String): Boolean {
        return deleteSave(getSave(gameName))
    }

    /**
     * @return `true` if successful.
     * @throws SecurityException when delete access was denied
     */
    fun deleteMultiplayerSave(gameName: String): Boolean {
        return deleteSave(getMultiplayerSave(gameName))
    }

    /**
     * Only use this with a [FileHandle] obtained by one of the methods of this class!
     *
     * @return `true` if successful.
     * @throws SecurityException when delete access was denied
     */
    fun deleteSave(file: FileHandle): Boolean {
        debug("Deleting save %s", file.path())
        return file.delete()
    }

    //endregion
    //region Saving

    fun saveGame(game: GameInfo, gameName: String, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }): FileHandle {
        val file = getSave(gameName)
        saveGame(game, file, saveCompletionCallback)
        return file
    }

    /**
     * Only use this with a [FileHandle] obtained by one of the methods of this class!
     */
    fun saveGame(game: GameInfo, file: FileHandle, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }) {
        try {
            debug("Saving GameInfo %s to %s", game.gameId, file.path())
            val string = gameInfoToString(game)
            file.writeString(string, false, Charsets.UTF_8.name())
            saveCompletionCallback(null)
        } catch (ex: Exception) {
            saveCompletionCallback(ex)
        }
    }

    /**
     * Overload of function saveGame to save a GameInfoPreview in the MultiplayerGames folder
     */
    fun saveGame(game: GameInfoPreview, gameName: String, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }): FileHandle {
        val file = getMultiplayerSave(gameName)
        saveGame(game, file, saveCompletionCallback)
        return file
    }

    /**
     * Only use this with a [FileHandle] obtained by one of the methods of this class!
     */
    fun saveGame(game: GameInfoPreview, file: FileHandle, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }) {
        try {
            debug("Saving GameInfoPreview %s to %s", game.gameId, file.path())
            json().toJson(game, file)
            saveCompletionCallback(null)
        } catch (ex: Exception) {
            saveCompletionCallback(ex)
        }
    }

    /**
     * [gameName] is a suggested name for the file. If the file has already been saved to or loaded from a custom location,
     * this previous custom location will be used.
     *
     * Calls the [onSaved] on the main thread on success.
     * Calls the [onError] on the main thread with an [Exception] on error.
     */
    fun saveGameToCustomLocation(
        game: GameInfo,
        gameName: String,
        onSaved: () -> Unit,
        onError: (Exception) -> Unit) {
        val saveLocation = game.customSaveLocation ?: Gdx.files.local(gameName).path()

        try {
            val data = gameInfoToString(game)
            debug("Saving GameInfo %s to custom location %s", game.gameId, saveLocation)
            saverLoader.saveGame(data, saveLocation,
                { location ->
                    game.customSaveLocation = location
                    Concurrency.runOnGLThread { onSaved() }
                },
                {
                    Concurrency.runOnGLThread { onError(it) }
                }
            )

        } catch (ex: Exception) {
            Concurrency.runOnGLThread { onError(ex) }
        }
    }

    //endregion
    //region Loading

    fun loadGameByName(gameName: String) =
            loadGameFromFile(getSave(gameName))

    fun loadGameFromFile(gameFile: FileHandle): GameInfo {
        val gameData = gameFile.readString(Charsets.UTF_8.name())
        if (gameData.isNullOrBlank()) {
            throw emptyFile(gameFile)
        }
        return gameInfoFromString(gameData)
    }

    fun loadGamePreviewByName(gameName: String) =
            loadGamePreviewFromFile(getMultiplayerSave(gameName))

    fun loadGamePreviewFromFile(gameFile: FileHandle): GameInfoPreview {
        return json().fromJson(GameInfoPreview::class.java, gameFile) ?: throw emptyFile(gameFile)
    }

    /**
     * GDX JSON deserialization does not throw when the file is empty, it just returns `null`.
     *
     * Since we work with non-nullable types, we convert this to an actual exception here to have a nice exception message instead of a NPE.
     */
    private fun emptyFile(gameFile: FileHandle): SerializationException {
        return SerializationException("The file for the game ${gameFile.name()} is empty")
    }

    /**
     * Calls the [onLoaded] on the main thread with the [GameInfo] on success.
     * Calls the [onError] on the main thread with the [Exception] on error
     * The exception may be [IncompatibleGameInfoVersionException] if the [GameInfo] was created by a version of this game that is incompatible with the current one.
     */
    fun loadGameFromCustomLocation(
        onLoaded: (GameInfo) -> Unit,
        onError: (Exception) -> Unit
    ) {
        saverLoader.loadGame(
            { data, location ->
                try {
                    val game = gameInfoFromString(data)
                    game.customSaveLocation = location
                    Concurrency.runOnGLThread { onLoaded(game) }
                } catch (ex: Exception) {
                    Concurrency.runOnGLThread { onError(ex) }
                }
            },
            {
                Concurrency.runOnGLThread { onError(it) }
            }
        )
    }


    //endregion
    //region Settings

    private fun getGeneralSettingsFile(): FileHandle {
        return if (UncivGame.Current.isConsoleMode) FileHandle(SETTINGS_FILE_NAME)
        else files.local(SETTINGS_FILE_NAME)
    }

    fun getGeneralSettings(): GameSettings {
        val settingsFile = getGeneralSettingsFile()
        var settings: GameSettings? = null
        if (settingsFile.exists()) {
            try {
                settings = json().fromJson(GameSettings::class.java, settingsFile)
                if (settings.isMigrationNecessary()) {
                    settings.doMigrations(JsonReader().parse(settingsFile))
                }
            } catch (ex: Exception) {
                // I'm not sure of the circumstances,
                // but some people were getting null settings, even though the file existed??? Very odd.
                // ...Json broken or otherwise unreadable is the only possible reason.
                Log.error("Error reading settings file", ex)
            }
        }

        return settings ?: GameSettings().apply { isFreshlyCreated = true }
    }

    fun setGeneralSettings(gameSettings: GameSettings) {
        getGeneralSettingsFile().writeString(json().toJson(gameSettings), false, Charsets.UTF_8.name())
    }

    companion object {

        var saveZipped = false

        /**
         * If the GDX [com.badlogic.gdx.Files.getExternalStoragePath] should be preferred for this platform,
         * otherwise uses [com.badlogic.gdx.Files.getLocalStoragePath]
         */
        var preferExternalStorage = false

        /**
         * Platform dependent saver-loader to custom system locations
         */
        var saverLoader: PlatformSaverLoader = PlatformSaverLoader.None
            get() {
                if (field.javaClass.simpleName == "DesktopSaverLoader" && LinuxX11SaverLoader.isRequired())
                    field = LinuxX11SaverLoader()
                return field
            }

        /** Specialized function to access settings before Gdx is initialized.
         *
         * @param base Path to the directory where the file should be - if not set, the OS current directory is used (which is "/" on Android)
         */
        fun getSettingsForPlatformLaunchers(base: String = "."): GameSettings {
            // FileHandle is Gdx, but the class and JsonParser are not dependent on app initialization
            // In fact, at this point Gdx.app or Gdx.files are null but this still works.
            val file = FileHandle(base + File.separator + SETTINGS_FILE_NAME)
            return if (file.exists()) json().fromJsonFile(GameSettings::class.java, file)
            else GameSettings().apply { isFreshlyCreated = true }
        }

        /** @throws IncompatibleGameInfoVersionException if the [gameData] was created by a version of this game that is incompatible with the current one. */
        fun gameInfoFromString(gameData: String): GameInfo {
            val unzippedJson = try {
                Gzip.unzip(gameData.trim())
            } catch (_: Exception) {
                gameData.trim()
            }
            val gameInfo = try {
                json().fromJson(GameInfo::class.java, unzippedJson)
            } catch (ex: Exception) {
                Log.error("Exception while deserializing GameInfo JSON", ex)
                val onlyVersion = json().fromJson(GameInfoSerializationVersion::class.java, unzippedJson)
                throw IncompatibleGameInfoVersionException(onlyVersion.version, ex)
            } ?: throw UncivShowableException("The file data seems to be corrupted.")

            if (gameInfo.version > GameInfo.CURRENT_COMPATIBILITY_VERSION) {
                // this means there wasn't an immediate error while serializing, but this version will cause other errors later down the line
                throw IncompatibleGameInfoVersionException(gameInfo.version)
            }
            gameInfo.setTransients()
            return gameInfo
        }

        /**
         * Parses [gameData] as gzipped serialization of a [GameInfoPreview]
         * @throws SerializationException
         */
        fun gameInfoPreviewFromString(gameData: String): GameInfoPreview {
            return json().fromJson(GameInfoPreview::class.java, Gzip.unzip(gameData))
        }

        /**
         * Returns pretty-printed (= manually readable) serialization of [game], optionally gzipped
         */
        fun gameInfoToPrettyString(game: GameInfo, useZip: Boolean = false): String {
            val prettyJson = json().apply { setOutputType(JsonWriter.OutputType.json) }.prettyPrint(game)
            return if (useZip) Gzip.zip(prettyJson) else prettyJson
        }

        /** Returns gzipped serialization of [game], optionally gzipped ([forceZip] overrides [saveZipped]) */
        fun gameInfoToString(game: GameInfo, forceZip: Boolean? = null, updateChecksum:Boolean=false): String {
            game.version = GameInfo.CURRENT_COMPATIBILITY_VERSION

            if (updateChecksum) game.checksum = game.calculateChecksum()
            val plainJson = json().toJson(game)
            return if (forceZip ?: saveZipped) Gzip.zip(plainJson) else plainJson
        }

        /** Returns gzipped serialization of preview [game] */
        fun gameInfoToString(game: GameInfoPreview): String {
            return Gzip.zip(json().toJson(game))
        }

    }

    //endregion
    //region Autosave

    /**
     * Auto-saves a snapshot of the [gameInfo] in a new thread.
     */
    fun requestAutoSave(gameInfo: GameInfo, nextTurn: Boolean = false): Job {
        // The save takes a long time (up to a few seconds on large games!) and we can do it while the player continues his game.
        // On the other hand if we alter the game data while it's being serialized we could get a concurrent modification exception.
        // So what we do is we clone all the game data and serialize the clone.
        return requestAutoSaveUnCloned(gameInfo.clone(), nextTurn)
    }

    /**
     * In a new thread, auto-saves the [gameInfo] directly - only use this with [GameInfo] objects that are guaranteed not to be changed while the autosave is in progress!
     */
    fun requestAutoSaveUnCloned(gameInfo: GameInfo, nextTurn: Boolean = false): Job {
        val job = Concurrency.run("autoSaveUnCloned") {
            autoSave(gameInfo, nextTurn)
        }
        autoSaveJob = job
        return job
    }

    fun autoSave(gameInfo: GameInfo, nextTurn: Boolean = false) {
        try {
            saveGame(gameInfo, AUTOSAVE_FILE_NAME)
        } catch (oom: OutOfMemoryError) {
            Log.error("Ran out of memory during autosave", oom)
            return  // not much we can do here
        }

        // keep auto-saves for the last 10 turns for debugging purposes
        if (nextTurn) {
            val newAutosaveFilename =
                SAVE_FILES_FOLDER + File.separator + AUTOSAVE_FILE_NAME + "-${gameInfo.currentPlayer}-${gameInfo.turns}"
            val file =
                if (preferExternalStorage && files.isExternalStorageAvailable)
                    files.external(newAutosaveFilename)
                else
                    files.local(newAutosaveFilename)
            getSave(AUTOSAVE_FILE_NAME).copyTo(file)

            fun getAutosaves(): Sequence<FileHandle> {
                return getSaves().filter { it.name().startsWith(AUTOSAVE_FILE_NAME) }
            }
            while (getAutosaves().count() > 10) {
                val saveToDelete = getAutosaves().minByOrNull { it.lastModified() }!!
                deleteSave(saveToDelete.name())
            }
        }
    }

    fun loadLatestAutosave(): GameInfo {
        return try {
            loadGameByName(AUTOSAVE_FILE_NAME)
        } catch (_: Exception) {
            // silent fail if we can't read the autosave for any reason - try to load the last autosave by turn number first
            val autosaves = getSaves().filter { it.name() != AUTOSAVE_FILE_NAME && it.name().startsWith(
                AUTOSAVE_FILE_NAME
            ) }
            loadGameFromFile(autosaves.maxByOrNull { it.lastModified() }!!)
        }
    }

    fun autosaveExists(): Boolean {
        return getSave(AUTOSAVE_FILE_NAME).exists()
    }

    // endregion
}

class IncompatibleGameInfoVersionException(
    override val version: CompatibilityVersion,
    cause: Throwable? = null
) : UncivShowableException(
    "The save was created with an incompatible version of Unciv: [${version.createdWith.toNiceString()}]. " +
            "Please update Unciv to this version or later and try again.",
    cause
), HasGameInfoSerializationVersion
