package com.unciv.scripting.utils

import com.badlogic.gdx.math.Vector2


//TODO: Rename to ScriptingApiFactories? Move to separate package with other API-focused things?

/**
 * For use in ScriptingScope. Allows interpreted scripts to make new instances of Kotlin/JVM classes.
 */
object InstanceFactories {
    //This, and possible ApiHelpers itself, need better nested namespaces.
    object Math {
    }
    object Rulesets {
    }
    object Kotlin {
    }
    object GUI {
    }
    fun Array() = "NotImplemented"
    fun Vector2(x: Float, y: Float) = com.badlogic.gdx.math.Vector2(x, y)
    fun MapUnit() = "NotImplemented"
    fun Technology() = "NotImplemented"
}

