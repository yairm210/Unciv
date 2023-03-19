package com.unciv.ui.components

import com.badlogic.gdx.Input


private val unCamelCaseRegex = Regex("([A-Z])([A-Z])([a-z])|([a-z])([A-Z])")
private fun unCamelCase(name: String) = unCamelCaseRegex.replace(name, """$1$4 $2$3$5""")

enum class KeyboardBinding(
    val category: Category,
    label: String? = null,
    key: KeyCharAndCode? = null
) {
    // Used by [KeyShortcutDispatcher.KeyShortcut] to mark an old-style shortcut with a hardcoded key
    None(Category.None, KeyCharAndCode.UNKNOWN),
    // Worldscreen
    NextTurn(Category.WorldScreen),
    NextTurnAlternate(Category.WorldScreen, KeyCharAndCode.SPACE),
    Civilopedia(Category.WorldScreen, Input.Keys.F1),
    EmpireOverview(Category.WorldScreen),
    Wait(Category.None, 'z'),  // Used but excluded from UI because UnitActionType.Wait needs to be done too
    // Popups
    Confirm(Category.Popups, "Confirm Dialog", 'y'),
    Cancel(Category.Popups, "Cancel Dialog", 'n'),
    ;

    enum class Category {
        None, WorldScreen, Popups
    }

    val label: String
    val defaultKey: KeyCharAndCode
    val hidden: Boolean get() = category == Category.None

    init {
        this.label = label ?: unCamelCase(name)
        this.defaultKey = key ?: KeyCharAndCode(name[0])
    }

    // Helpers to make enum instance initializations shorter
    constructor(category: Category, label: String, key: Char) : this(category, label, KeyCharAndCode(key))
    constructor(category: Category, label: String, key: Int) : this(category, label, KeyCharAndCode(key))
    constructor(category: Category, key: KeyCharAndCode) : this(category, null, key)
    constructor(category: Category, key: Char) : this(category, null, KeyCharAndCode(key))
    constructor(category: Category, key: Int) : this(category, null, KeyCharAndCode(key))

    /** Debug helper */
    override fun toString() = "$category.$name($defaultKey)"
}
