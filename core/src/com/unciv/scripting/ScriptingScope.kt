package com.unciv.scripting

import com.badlogic.gdx.Gdx
//import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.scripting.utils.ScriptingApiEnums
import com.unciv.scripting.utils.InstanceFactories
import com.unciv.scripting.utils.InstanceRegistry
import com.unciv.ui.mapeditor.MapEditorScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toPixmap
import com.unciv.ui.worldscreen.WorldScreen
import java.io.ByteArrayOutputStream


// TODO: Move this, and all nested objects and classes, to api/?

// TODO: Search core code for Transient lazy init caches (E.G. natural wonders saved in TileMap apparently, and TileMap.resources), and add functions to refresh them.


/**
 * Holds references to all internal game data that scripting backends have access to.
 *
 * Also where to put any future PlayerAPI, CheatAPI, ModAPI, etc.
 *
 * For LuaScriptingBackend, UpyScriptingBackend, QjsScriptingBackend, etc, the hierarchy of data under this class definition should probably directly mirror the wrappers in the namespace exposed to running scripts.
 *
 * WorldScreen gives access to UnitTable.selectedUnit, MapHolder.selectedTile, etc. Useful for contextual operations.
 *
 * The members of this class and its nested classes should be designed for use by running scripts, not for implementing the protocol or API of scripting backends.
 * E.G.: If you need access to a file to build the scripting environment, then add it to ScriptingEngineConstants.json instead of using apiHelpers.assetFileB64. If you need access to some new type of property, then geneneralize it as much as possible and add an IPC request type for it in ScriptingProtocol.kt or add support for it in Reflection.kt.
 * In Python terms, that means that magic methods all directly send and parse IPC packets, while running scripts transparently use those magic methods to access the functions here.
 * API calls are for running scripts, and may be less stable. Building the scripting environment itself should be done directly using the IPC protocol and other lower-level constructs.
 *
 * To reduce the chance of E.G. name collisions in .apiHelpers.registeredInstances, or one misbehaving mod breaking everything by unassigning .gameInfo, different ScriptingState()s should each have their own ScriptingScope().
 */
class ScriptingScope(
        // This entire API should still be considered unstable. It may be drastically changed at any time.

        //If this is going to be exposed to downloaded mods, then every declaration here, as well as *every* declaration that is safe for scripts to have access to, should probably be whitelisted with annotations and checked or errored at the point of reflection.
        var civInfo: CivilizationInfo? = null,
        var gameInfo: GameInfo? = null,
        var uncivGame: UncivGame? = null,
        var worldScreen: WorldScreen? = null,
        var mapEditorScreen: MapEditorScreen? = null
        //val _availableNames = listOf("civInfo", "gameInfo", "uncivGame", "worldScreen", "apiHelpers") // Nope. Annotate instead.
    ) {

    val GameSaver = com.unciv.logic.GameSaver //TODO: Organize.

    val apiHelpers = ApiHelpers(this)

    class ApiHelpers(val scriptingScope: ScriptingScope) {
        // This could probably eventually include ways for scripts to create and inject their own UI elements too. Create, populate, show even popups for mods, inject buttons that execute script strings for macros.
        // TODO: The vast majority of these don't need scriptingScope access, and thus can be put on singletons.
        val isInGame: Boolean
            get() = (scriptingScope.civInfo != null && scriptingScope.gameInfo != null && scriptingScope.uncivGame != null)
        val Factories = InstanceFactories
        val Enums = ScriptingApiEnums
        val registeredInstances = InstanceRegistry()
        //Debug/dev identity function for both Kotlin and scripts. Check if value survives serialization, force something to be added to ScriptingProtocol.instanceSaver, etc.
        fun unchanged(obj: Any?) = obj
        fun printLine(msg: Any?) = println(msg) // Different name from Kotlin's is deliberate, to abstract for scripts.
        //fun readLine()
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
            // When/if letting scripts make UI elements becomes a thing, these should probably be organized together with the factories for that.
            // To test in Python:
            // import PIL.Image, io, base64; PIL.Image.open(io.BytesIO(base64.b64decode(apiHelpers.assetImage("StatIcons/Resistance")))).show()
            val fakepng = ByteArrayOutputStream()
            //Close this steam? Well, the docs say doing so "has no effect", and it should clearly get GC'd anyway.
            val pixmap = ImageGetter.getDrawable(path).getRegion().toPixmap()
            val exporter = PixmapIO.PNG() // Could be kept and "reused to encode multiple PNGs with minimal allocation", according to the docs. I don't see it as a sufficient bottleneck yet to necesarily justify the complexity and risk, though.
            exporter.setFlipY(false)
            exporter.write(fakepng, pixmap)
            pixmap.dispose() // In theory needed to avoid memory leak. Doesn't seem to actually have any impact, compared to the .dispose() inside .toPixmap(). Maybe the exporter's dispose also calls this?
            exporter.dispose() // This one should be called automatically by GC anyway.
            return String(Base64Coder.encode(fakepng.toByteArray()))
        }
//        fun applyProperties(instance: Any, properties: Map<String, Any?>) {
//        }
        //setTimeout?
        fun lambdifyScript(code: String ): () -> Unit = fun(){ scriptingScope.uncivGame!!.scriptingState.exec(code); return } // FIXME: Requires awareness of which scriptingState and which backend to use.
        //Directly invoking the resulting lambda from a running script will almost certainly break the REPL loop/IPC protocol.
    }

}
//worldScreen.bottomUnitTable.selectedCity.cityConstructions.purchaseConstruction("Missionary", -1, False, apiHelpers.Enums.Stat.statsUsableToBuy[4])
