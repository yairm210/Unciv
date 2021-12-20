package com.unciv.scripting

import com.badlogic.gdx.Gdx
import com.unciv.JsonParser


val ScriptingConstants: _ScriptingConstantsClasses.ScriptingConstantsClass = JsonParser().getFromJson(
    _ScriptingConstantsClasses.ScriptingConstantsClass::class.java,
    _ScriptingConstantsClasses.ScriptingAssetFolders.scriptingAssets.child("ScriptingEngineConstants.json")
)


/**
 * Class defining the structure of ScriptingConstants.
 */
object _ScriptingConstantsClasses{
    // Need to define classes to deserialize the JSONs into, but really the whole file should be one singleton.
    // It would be slightly better with KotlinX, I think, since then I could at least use data classes and don't have to initialize all the properties with mutable values. LibGDX instantiates with no constructor arguments, and then assigns to properties/fields/whatever.

    /**
     * Constant values for scripting API.
     *
     * Mostly mirrors assets/scripting/ScriptingEngineConstants.json.
     * Also includes folder handles, and additional constants shared with script interpreter engines.
     */
    class ScriptingConstantsClass() {
        /**
         * Map of parameters for each script interpreter engine type.
         */
        var engines = HashMap<String, ScriptingEngineConfig>()
            // Really, these should be val:Map<>s in a data class constructor, not var:HashMap<>() in a regular class body. But GDX doesn't seem to like parsing .JSON into those, so instead let's override the accessors.
            get() = field.toMap() as HashMap<String, ScriptingEngineConfig>
            set(value) = throw UnsupportedOperationException("This property is supposed to be constant.")

        /**
         * List of filepaths that are shared by all engine types, starting from assets/scripting/sharedfiles.
         *
         * Used by SourceManager.
         * Required because internal directories can't be identified or traversed on Desktop, as all assets are put on the classpath.
         */
        var sharedfiles = ArrayList<String>()
            get() = field.toList() as ArrayList<String>
            set(value) = throw UnsupportedOperationException("This property is supposed to be constant.")

        val assetFolders = ScriptingAssetFolders

        val apiConstants = JsonParser().getFromJson(ScriptingAPIConstants::class.java, assetFolders.sharedfilesAssets.child("ScriptAPIConstants.json"))
    }

    /**
     * Configuration values for a single script interpreter engine type, as specified in assets/scripting/ScriptingEngineConstants.json.
     */
    class ScriptingEngineConfig(){
        // Not sure if these should be called "engines" or "languages". "Language" better reflects the actual distinction between the files and (not implemented) syntax highlighting REGEXs for each, but "engine" is less ambiguous with the the translation stuff.
        /**
         * Filepath strings, starting from the root folder of the engine at assets/scripting/enginefiles/{engine}, that should be copied when constructing the environment of a particular engine type.
         *
         * Used by SourceManager.
         * Required because internal directories can't be identified or traversed on Desktop, as all assets are put on the classpath.
         */
        var files = ArrayList<String>()
        // https://github.com/libgdx/libgdx/issues/4074
        // https://github.com/libgdx/libgdx/wiki/Reading-and-writing-JSON
        /**
         * Stack of Regular Expression operations that can be used to apply LibGDX Color Markup Language syntax highlighting to the output of an engine type.
         * Not yet implemented/used.
         */
        var syntaxHighlightingRegexStack = ArrayList<String>()
    }

    /**
     * Constant values mirroring assets/scripting/sharedfiles/ScriptingAPIConstants.json.
     *
     * Separate file and class from other constants because these have to be shared with each engine's interpreters.
     */
    class ScriptingAPIConstants() {
        /**
         * Prefix used to generate and identify string tokens from InstanceTokenizer.
         */
        var kotlinInstanceTokenPrefix = ""
    }

    /**
     * File handles for internal scripting asset folders.
     */
    object ScriptingAssetFolders {
        /**
         * File handle for base internal scripting asset folder.
         */
        val scriptingAssets = Gdx.files.internal("scripting/")
        /**
         * File handle for internal folder holding each engine's asset directory.
         */
        val enginefilesAssets = scriptingAssets.child("enginefiles/")
        /**
         * File handle for internal asset folder holding files shared across each engine.
         */
        val sharedfilesAssets = scriptingAssets.child("sharedfiles/")
    }
}
