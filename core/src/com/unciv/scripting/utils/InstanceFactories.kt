package com.unciv.scripting.utils

import com.unciv.MainMenuScreen
import com.unciv.ui.mapeditor.MapEditorScreen
import com.unciv.logic.map.TileMap
import com.badlogic.gdx.math.Vector2


//TODO: Rename to ScriptingApiFactories? Move to separate package with other API-focused things?

/**
 * For use in ScriptingScope. Allows interpreted scripts to make new instances of Kotlin/JVM classes.
 */
object InstanceFactories {
    //This, and possible ApiHelpers itself, need better nested namespaces.
    val Game = object {
    }
    val Math = object {
    }
    val Rulesets = object {
    }
    val Kotlin = object {
    }
    val Gui = object {
        fun MainMenuScreen() = com.unciv.MainMenuScreen()
        fun MapEditorScreen(map: TileMap) = com.unciv.ui.mapeditor.MapEditorScreen(map)
    }
    fun arrayOf(elements: Collection<Any?>): Array<*> = elements.toTypedArray()
    fun arrayOfAny(elements: Collection<Any>): Array<Any> = elements.toTypedArray()
    fun arrayOfString(elements: Collection<String>): Array<String> = elements.toTypedArray()
    fun Vector2(x: Number, y: Number) = com.badlogic.gdx.math.Vector2(x.toFloat(), y.toFloat())
    fun MapUnit() = "NotImplemented"
    fun Technology() = "NotImplemented"
}

