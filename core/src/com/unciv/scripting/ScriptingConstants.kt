package com.unciv.scripting

import com.badlogic.gdx.Gdx
import com.unciv.JsonParser


val ScriptingConstants: _ScriptingConstantsClasses.ScriptingConstantsClass = JsonParser().getFromJson(
    _ScriptingConstantsClasses.ScriptingConstantsClass::class.java,
    _ScriptingConstantsClasses.ScriptingAssetFolders.scriptingAssets.child("ScriptingEngineConstants.json")
)


object _ScriptingConstantsClasses{
    // Need to define classes to deserialize the JSONs into, but really this whole file should be one singleton.
    // It would be better slightly with KotlinX, I think, since then I could at least use data classes and don't have to initialize all the properties.

    class ScriptingConstantsClass() {
        var engines = HashMap<String, ScriptingEngineConfig>()
            // Really, these should be `val: Map<>`s in a data class constructor, not `var: HashMap<>()` in a regular class body. But GDX doesn't seem to like parsing .JSON into those, so instead let's override the accessors.
            get() = field.toMap() as HashMap<String, ScriptingEngineConfig>
            set(value) = throw UnsupportedOperationException("This property is supposed to be constant.")
        
        var sharedfiles = ArrayList<String>()
            get() = field.toList() as ArrayList<String>
            set(value) = throw UnsupportedOperationException("This property is supposed to be constant.")
        
        val apiConstants = JsonParser().getFromJson(ScriptingAPIConstants::class.java, assetFolders.sharedfilesAssets.child("ScriptAPIConstants.json"))
        
        val assetFolders
            get() = ScriptingAssetFolders
    }

    class ScriptingEngineConfig(){
        // Not sure if these should be called "engines" or "languages". "Language" better reflects the actual distinction between the files and (not implemented) syntax highlighting REGEXs for each, but "engine" is less ambiguous with the the translation stuff.
        var files = ArrayList<String>()
        // https://github.com/libgdx/libgdx/issues/4074
        // https://github.com/libgdx/libgdx/wiki/Reading-and-writing-JSON
        // Apparently identifying and listing internal directories doesn't work on Desktop, as all assets are put on the classpath.
        // So all the files for each engine engine have to be manually listed in a .JSON instead.
        var syntaxHighlightingRegexStack = ArrayList<String>()
    }

    class ScriptingAPIConstants() {
        var kotlinObjectTokenPrefix = ""
    }

    object ScriptingAssetFolders {
        val scriptingAssets = Gdx.files.internal("scripting/")
        val enginefilesAssets = scriptingAssets.child("enginefiles/")
        val sharedfilesAssets = scriptingAssets.child("sharedfiles/")
    }
}
