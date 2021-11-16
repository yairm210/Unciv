package com.unciv.scripting.utils

import com.badlogic.gdx.math.Vector2


/**
 * For use in ScriptingScope. Allows interpreted scripts to make new instances of Kotlin/JVM classes.
 */
object InstanceFactories {
    fun Vector2(x: Float, y: Float) = com.badlogic.gdx.math.Vector2(x, y)
    fun MapUnit() = "NotImplemented"
}

