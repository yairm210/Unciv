package com.unciv.ui.components

import com.badlogic.gdx.Input
import com.unciv.Constants


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

    // Unit actions - name MUST correspond to UnitActionType.name because the shorthand constructor
    // there looks up bindings here by name - which also means we must not use UnitActionType
    // here as it will not be guaranteed to already be fully initialized.
    SwapUnits(Category.UnitActions,"Swap units", 'y'),
    Automate(Category.UnitActions, 'm'),
    StopAutomation(Category.UnitActions,"Stop automation", 'm'),
    StopMovement(Category.UnitActions,"Stop movement", '.'),
    Sleep(Category.UnitActions, 'f'),
    SleepUntilHealed(Category.UnitActions,"Sleep until healed", 'h'),
    Fortify(Category.UnitActions, 'f'),
    FortifyUntilHealed(Category.UnitActions,"Fortify until healed", 'h'),
    Explore(Category.UnitActions, 'x'),
    StopExploration(Category.UnitActions,"Stop exploration", 'x'),
    Promote(Category.UnitActions, 'o'),
    Upgrade(Category.UnitActions, 'u'),
    Transform(Category.UnitActions, 'k'),
    Pillage(Category.UnitActions, 'p'),
    Paradrop(Category.UnitActions, 'p'),
    AirSweep(Category.UnitActions, 'a'),
    SetUp(Category.UnitActions,"Set up", 't'),
    FoundCity(Category.UnitActions,"Found city", 'c'),
    ConstructImprovement(Category.UnitActions,"Construct improvement", 'i'),
    Repair(Category.UnitActions, Constants.repair, 'r'),
    Create(Category.UnitActions, 'i'),
    HurryResearch(Category.UnitActions, 'g'),
    StartGoldenAge(Category.UnitActions, 'g'),
    HurryWonder(Category.UnitActions, 'g'),
    HurryBuilding(Category.UnitActions,"Hurry Construction", 'g'),
    ConductTradeMission(Category.UnitActions, 'g'),
    FoundReligion(Category.UnitActions,"Found a Religion", 'g'),
    TriggerUnique(Category.UnitActions,"Trigger unique", 'g'),
    SpreadReligion(Category.UnitActions, 'g'),
    RemoveHeresy(Category.UnitActions, 'h'),
    EnhanceReligion(Category.UnitActions,"Enhance a Religion", 'g'),
    DisbandUnit(Category.UnitActions,"Disband unit", Input.Keys.FORWARD_DEL),
    GiftUnit(Category.UnitActions,"Gift unit", null),
    Wait(Category.UnitActions, 'z'),
    ShowAdditionalActions(Category.UnitActions,"Show more", Input.Keys.PAGE_DOWN),
    HideAdditionalActions(Category.UnitActions,"Back", Input.Keys.PAGE_UP),
    AddInCapital(Category.UnitActions, "Add in capital", 'g'),

    // Popups
    Confirm(Category.Popups, "Confirm Dialog", 'y'),
    Cancel(Category.Popups, "Cancel Dialog", 'n'),
    ;

    enum class Category {
        None, WorldScreen, UnitActions, Popups;
        val label = unCamelCase(name)
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
    constructor(category: Category, key: Char) : this(category, KeyCharAndCode(key))
    constructor(category: Category, key: Int) : this(category, KeyCharAndCode(key))

    /** Debug helper */
    override fun toString() = "$category.$name($defaultKey)"
}
