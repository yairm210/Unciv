package com.unciv.scripting.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser
import com.unciv.scripting.ScriptingConstants
//import com.unciv.scripting.ScriptingConstants
import kotlin.concurrent.thread


/**
 * Object for managing, and using (copying/instantiating) internal assets associated with each script interpreter engine type.
 */
object SourceManager {

    /**
     * Return a file handle for a specific engine type's internal assets directory.
     */
    private fun getEngineLibraries(engine: String): FileHandle {
        return ScriptingConstants.assetFolders.enginefilesAssets.child("${engine}/")
    }

    /**
     * Set up a directory tree with all known libraries and files for a scripting engine/language type.
     *
     * Creates temporary directory.
     * Copies directory tree under android/assets/scripting/sharedfiles/ into it, as specified in ScriptingEngineConstants.json
     * Copies directory tree under android/assets/scripting/enginefiles/{engine} into it, as specified in ScriptingEngineConstants.json.
     *
     * @param engine Name of the engine type, as defined in scripting constants.
     * @return FileHandle() for the temporary directory.
     */
    fun setupInterpreterEnvironment(engine: String): FileHandle {
        val enginedir = getEngineLibraries(engine)
        val outdir = FileHandle.tempDirectory("unciv-${engine}_")
        fun addfile(sourcedir: FileHandle, path: String) {
            var target = outdir.child(path)
            if (path.endsWith("/")) {
                target.mkdirs()
            } else {
                sourcedir.child(path).copyTo(target)
            }
        }
        for (fp in ScriptingConstants.sharedfiles) {
            addfile(ScriptingConstants.assetFolders.sharedfilesAssets, fp)
        }
        for (fp in ScriptingConstants.engines[engine]!!.files) {
            addfile(enginedir, fp)
        }
        Runtime.getRuntime().addShutdownHook(
            // Delete temporary directory on JVM shutdown, not on backend object destruction/termination. The copied files shouldn't be huge anyway, there's no reference to a ScriptingBackend() here, and I trust the shutdown hook to be run more reliably.
            // I guess you could wrap the outdir folder handler in something with a .finalize(), then keep it around for the duration of each backend, if you wanted to clear scripting runtimes when they're no longer in use. May become more pressing if a modding API is implemented and it involves spinning up new ScriptingStates/ScriptingBackends for every loaded game, I guess.
            thread(start = false, name = "Delete ${outdir.toString()}.") {
                outdir.deleteDirectory()
            }
        )
        return outdir
    }
}
