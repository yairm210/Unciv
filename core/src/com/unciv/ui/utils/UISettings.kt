package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.unciv.logic.GameSaver

/** Contains UI specific settings, to persist screen or popup states across instantiations.
 *
 *  Example: ExpanderTab open/close states, ScrollPane scroll positions, draggable SplitPane position...
 *
 *  Has ability to serialize to storage and retrieve.
 */

// This is a mostly empty shell for now, meant to be expanded later.
class UISettings {

    /** This is indexed by caller key.
     *  A caller key is built as `classname.methodname` from the call stack.
     */
    var expanderTabStates = HashMap<String,ExpanderStateSet>()

    /**
     * Kludge to prevent the json serializer to liberally sprinkle its output with
     * unnecessary `{class:java.lang.String,value:...}` constructs.
     * 
     * Property [titles] is indexed by the ExpanderTab's title, if found, that
     * expander should start out closed.
     */
    class ExpanderStateSet {
        var titles = HashSet<String>()
        fun add(element: String) = titles.add(element)
        fun remove(element: String) = titles.remove(element)
        operator fun contains(element: String) = titles.contains(element)
    }

    //region durable persistence
    companion object {
        const val saveName = "UISettings.json"
        private fun getSaveFile() = Gdx.files.local(saveName)
    }

    fun save() {
        GameSaver.json().toJson(this, getSaveFile())
    }

    fun load() {
        val settingsFile = getSaveFile()
        if (!settingsFile.exists()) return

        try {
            val newSettings = GameSaver.json().fromJson(UISettings::class.java, settingsFile)
            this.expanderTabStates = newSettings.expanderTabStates
            // Add copying any new containers here
        } catch (ex: Exception) {
            println("Error reading UI settings file: ${ex.localizedMessage}")
            println("  cause: ${ex.cause}")
        }
    }
    //endregion
}
