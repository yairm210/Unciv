package com.unciv.scripting.api

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.scripting.utils.FakeMap
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toPixmap
import java.io.ByteArrayOutputStream

// TODO: Search core code for Transient lazy init caches (E.G. natural wonders saved in TileMap apparently, and TileMap.resources), and add functions to refresh them?

class ScriptingApiHelpers(val scriptingScope: ScriptingScope) {
    // This, and the classes of its members, should try to implement only the minimum number of helper functions that are needed for each type of functionality otherwise not possible in scripts. E.G. Don't add special "loadGame" functions or whatever here, but do expose the existing methods of UncivGame. E.G. Don't add factories to speed up making alert popups, because all the required constructors can already be called through constructorByQualname anyway. Let the rest of the codebase and the scripts themselves do the work— Maintenance of the API itself will be easier if all it does is expose existing Kotlin code to dynamic Python/JS/Lua code.
    val isInGame: Boolean
        get() = (scriptingScope.civInfo != null && scriptingScope.gameInfo != null && scriptingScope.uncivGame != null)

    val App = ScriptingApiAppHelpers

    val Sys = ScriptingApiSysHelpers

    val Jvm = ScriptingApiJvmHelpers

    val Mappers = ScriptingApiMappers

    val registeredInstances = ScriptingApiInstanceRegistry()
    val instancesAsInstances = FakeMap{obj: Any? -> obj} // TODO: Rename this, and singleton it.
    /// Scripting language bindings work by keeping track of the paths to values, and making Kotlin/the JVM resolve them only when needed.
    // This creates a dilemma: Resolving a path into a Kotlin value too early means that no further paths (E.G. attribute, keys, calls) can be built on top of it. But resolving it late means that expected side effects may not happen (E.G. function calls probably shouldn't be deferred). And values that *must* be resolved, like the results of function calls, cannot have their own members and method accessed until they themselves are assigned to a path, because they're just kinda floating around as far as the scripting-exposed semantics are concerned.
    // So this fake Map works around that, by providing a way for any random object to appear to have a path.


    //setTimeout?
    //fun lambdifyScript(code: String ): () -> Unit = fun(){ scriptingScope.uncivGame!!.scriptingState.exec(code); return } // FIXME: Requires awareness of which scriptingState and which backend to use.
    // TODO: Move to modApiHelpers.
    //Directly invoking the resulting lambda from a running script will almost certainly break the REPL loop/IPC protocol.
}
