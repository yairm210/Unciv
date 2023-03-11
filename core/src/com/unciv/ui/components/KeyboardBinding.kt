package com.unciv.ui.components

import com.badlogic.gdx.Input

private val unCamelCaseRegex = Regex("([A-Z])([A-Z])([a-z])|([a-z])([A-Z])")
private fun unCamelCase(name: String) = unCamelCaseRegex.replace(name, """$1$4 $2$3$5""")

enum class KeyboardBinding(
    label: String? = null,
    key: KeyCharAndCode? = null
) {
    // Worldscreen
    NextTurn,
    NextTurnAlternate(key = KeyCharAndCode.SPACE),
    Civilopedia(key = KeyCharAndCode(Input.Keys.F1)),
    EmpireOverview,
    // Popups
    Confirm("Confirm Dialog", KeyCharAndCode('y')),
    Cancel("Cancel Dialog", KeyCharAndCode('n')),
    ;

    val label: String
    val defaultKey: KeyCharAndCode

    init {
        this.label = label ?: unCamelCase(name)
        this.defaultKey = key ?: KeyCharAndCode(name[0])
    }
}
