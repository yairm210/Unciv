package com.unciv.scripting.api

class ScriptingModApiHelpers(val scriptingScope: ScriptingScope) {
    // var handlerContext: NamedTuple?
    // Why not just use a map? String keys will be clearer in scripts than integers anyway.
    // Collection that gets replaced with any contextual parameters when running script handlers. E.G. Unit moved, city founded, tech researched, construction finished.
//fun showScriptedChoicePopup(title: String, body: String, options: List<String>, callback: String?) // Actually, no. Popup() seems to work on its own.
    // TODO: Mods blacklist, for security threats.
    //fun lambdifyScript(code: String ): () -> Unit = fun(){ scriptingScope.uncivGame!!.scriptingState.exec(code); return } // FIXME: Requires awareness of which scriptingState and which backend to use.
    //Directly invoking the resulting lambda from a running script will almost certainly break the REPL loop/IPC protocol.
    //setTimeout?
}
