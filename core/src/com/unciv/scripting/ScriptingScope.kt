package com.unciv.scripting

import com.badlogic.gdx.Gdx
//import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.scripting.utils.ScriptingApiEnums
import com.unciv.scripting.utils.InstanceFactories
import com.unciv.scripting.utils.InstanceRegistry
import com.unciv.ui.mapeditor.MapEditorScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toPixmap
import com.unciv.ui.worldscreen.WorldScreen
import java.io.ByteArrayOutputStream


/**
 * Holds references to all internal game data that scripting backends have access to.
 *
 * Also where to put any future PlayerAPI, CheatAPI, ModAPI, etc.
 *
 * For LuaScriptingBackend, UpyScriptingBackend, QjsScriptingBackend, etc, the hierarchy of data under this class definition should probably directly mirror the wrappers in the namespace exposed to running scripts.
 *
 * WorldScreen gives access to UnitTable.selectedUnit, MapHolder.selectedTile, etc. Useful for contextual operations.
 *
 * The members of this class and its subclasses should not be used in implementing the protocol level of scripting backends.
 * E.G.: If you need access to a file to build the scripting environment, then add it to ScriptingEngineConstants.json instead of using apiHelpers.assetFileB64. If you need access to some new type of property, then geneneralize it as much as possible and add an IPC request type for it in ScriptingProtocol.kt.
 * API calls are for running scripts, and may be less stable. Building the scripting environment itself should be done directly using the IPC protocol and other lower-level constructs.
 */
class ScriptingScope(
        //If this is going to be exposed to downloaded mods, then every declaration here, as well as *every* declaration that is safe for scripts to have access to, should probably be whitelisted with annotations and checked or errored at the point of reflection.
        var civInfo: CivilizationInfo? = null,
        var gameInfo: GameInfo? = null,
        var uncivGame: UncivGame? = null,
        var worldScreen: WorldScreen? = null,
        var mapEditorScreen: MapEditorScreen? = null
        //val _availableNames = listOf("civInfo", "gameInfo", "uncivGame", "worldScreen", "apiHelpers") // Nope. Annotate instead.
    ) {

    val apiHelpers = ApiHelpers(this)

    class ApiHelpers(val scriptingScope: ScriptingScope) {
        // This could probably eventually include ways for scripts to create and inject their own UI elements too. Create, populate, show even popups for mods, inject buttons that execute script strings for macros.
        val isInGame: Boolean
            get() = (scriptingScope.civInfo != null && scriptingScope.gameInfo != null && scriptingScope.uncivGame != null)
        val Factories = InstanceFactories
        val Enums = ScriptingApiEnums
        val registeredInstances = InstanceRegistry()
        //Debug/dev identity function for both Kotlin and scripts. Check if value survives serialization, force something to be added to ScriptingProtocol.instanceSaver, etc.
        fun unchanged(obj: Any?) = obj
        fun printLn(msg: Any?) = println(msg)
        //fun readLn()
        //Return a line from the main game process's STDIN.
        fun toString(obj: Any?) = obj.toString()
        fun copyToClipboard(value: Any?) {
            //Better than scripts potentially doing it themselves. In Python, for example, a way to do this would involve setting up an invisible TKinter window.
            Gdx.app.clipboard.contents = value.toString();
        }
        //fun typeOf(obj: Any?) = obj::class.simpleName
        //fun typeOfQualified(obj: Any?) = obj::class.qualifiedName
        // @param path Path of an internal file as exposed in Gdx.files.internal.
        // @return The contents of the internal file read as a text string.
        fun assetFileString(path: String) = Gdx.files.internal(path).readString()
        // @param path Path of an internal file as exposed in Gdx.files.internal.
        // @return The contents of the internal file encoded as a Base64 string.
        fun assetFileB64(path: String) = String(Base64Coder.encode(Gdx.files.internal(path).readBytes()))
        // @param path Path of an internal image as exposed in ImageGetter as a TextureRegionDrawable from an atlas.
        // @return The image encoded as a PNG file encoded as a Base64 string.
        fun assetImageB64(path: String): String {
            // To test in Python:
            // import PIL.Image, io, base64; PIL.Image.open(io.BytesIO(base64.b64decode(apiHelpers.assetImage("StatIcons/Resistance")))).show()
            val fakepng = ByteArrayOutputStream()
            //Close this? Well, the docs say doing so "has no effect", and it should clearly get GC'd anyway.
            val pixmap = ImageGetter.getDrawable(path).getRegion().toPixmap()
            val exporter = PixmapIO.PNG()
            exporter.setFlipY(false)
            exporter.write(fakepng, pixmap)
            pixmap.dispose() // Doesn't seem to help with the memory leak.
            exporter.dispose() // Both of these should be called automatically by GC anyway.
            return String(Base64Coder.encode(fakepng.toByteArray()))

//            return String(Base64Coder.encode((ImageGetter.getDrawable(path).getRegion().getTexture().getTextureData() as FileTextureData).getFileHandle().readBytes())) // Cool. This works. But it freezes everything for several dozen seconds on the first run, and it returns the massive packed file.
        }
    }

}
//worldScreen.bottomUnitTable.selectedCity.cityConstructions.purchaseConstruction("Missionary", -1, False, apiHelpers.Enums.Stat.statsUsableToBuy[4])
