package com.unciv.scripting.utils

import com.badlogic.gdx.math.Vector2


object InstanceFactories {
    // For use in ScriptingScope. Allows bound scripts to make new instances of Kotlin/JVM classes.
    fun Vector2(x: Float, y: Float) = com.badlogic.gdx.math.Vector2(x, y)
    fun MapUnit() = "NotImplemented"
}
