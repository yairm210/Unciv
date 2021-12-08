package com.unciv.scripting.api

import com.badlogic.gdx.Gdx

object ScriptingApiSysHelpers {
    fun printLine(msg: Any?) = println(msg.toString()) // Different name from Kotlin's is deliberate, to abstract for scripts.
    fun readLine() = kotlin.io.readLine() // Kotlin 1.6+ exposes readln(), unified name with println().
    //Return a line from the main game process's STDIN.
    fun copyToClipboard(value: Any?) {
        //Better than scripts potentially doing it themselves. In Python, for example, a way to do this would involve setting up an invisible TKinter window.
        Gdx.app.clipboard.contents = value.toString()
    }
}
