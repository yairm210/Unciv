package com.unciv.scripting.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser
import kotlin.concurrent.thread


object SourceManager {

    val scriptingAssets = Gdx.files.internal("scripting/")
    val enginedataAssets = scriptingAssets.child("enginedata/")
    
    val shareddataAssets = scriptingAssets.child("shareddata/")
    val shareddataAssetsList = scriptingAssets.child("SharedData.json")
    
    val shareddataFilelist = JsonParser().getFromJson(Array<String>::class.java, shareddataAssetsList)

    private fun getEngineFilelistPath(engine: String): FileHandle {
        return scriptingAssets.child("${engine}.json")
    }

    private fun getEngineFilelist(engine: String): Array<String> {
        // https://github.com/libgdx/libgdx/issues/4074
        // https://github.com/libgdx/libgdx/wiki/Reading-and-writing-JSON
        // Apparently identifying and listing internal directories doesn't work on Desktop, as all assets are put on the classpath.
        // So all the files for an engine should be listed in a flat .JSON instead.
        return JsonParser().getFromJson(
            Array<String>::class.java,
            getEngineFilelistPath(engine)
        )
    }

    private fun getEngineLibraries(engine: String): FileHandle {
        return enginedataAssets.child("${engine}/")
    }

    fun setupInterpreterEnvironment(engine: String): FileHandle {
        // Creates temporary directory.
        // Copies directory tree under `android/assets/scripting/shareddata/` into it, as specified by `SharedData.json` 
        // Copies directory tree under `android/assets/scripting/enginedata/{engine}` into it, as specified by `{engine}.json`.
        // Returns `FileHandle()` for the temporary directory.
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
        for (fp in shareddataFilelist) {
            addfile(shareddataAssets, fp)
        }
        for (fp in getEngineFilelist(engine)) {
            addfile(enginedir, fp)
        }
        Runtime.getRuntime().addShutdownHook(
            // Delete temporary directory on JVM shutdown, not on backend object destruction/termination. The copied files shouldn't be huge anyway, there's no reference to a `ScriptingBackend()` here, and I trust the shutdown hook to be run more reliably.
            thread(start = false, name = "Delete ${outdir.toString()}.") {
                outdir.deleteDirectory()
            }
        )
        return outdir
    }
}
