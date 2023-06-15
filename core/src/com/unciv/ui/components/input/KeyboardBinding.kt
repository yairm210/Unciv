package com.unciv.ui.components.input

import com.badlogic.gdx.Input
import com.unciv.Constants


private val unCamelCaseRegex = Regex("([A-Z])([A-Z])([a-z])|([a-z])([A-Z])")
private fun unCamelCase(name: String) = unCamelCaseRegex.replace(name, """$1$4 $2$3$5""")

enum class KeyboardBinding(
    val category: Category,
    label: String? = null,
    key: KeyCharAndCode? = null
) {
    //region Enum Instances

    /** Used by [KeyShortcutDispatcher.KeyShortcut] to mark an old-style shortcut with a hardcoded key */
    None(Category.None, KeyCharAndCode.UNKNOWN),

    // Worldscreen
    Menu(Category.WorldScreen, KeyCharAndCode.TAB),
    NextTurn(Category.WorldScreen),
    NextTurnAlternate(Category.WorldScreen, KeyCharAndCode.SPACE),
    EmpireOverview(Category.WorldScreen),
    MusicPlayer(Category.WorldScreen, KeyCharAndCode.ctrl('m')),

    /*
     * These try to be faithful to default Civ5 key bindings as found in several places online
     * Some are a little arbitrary, e.g. Economic info, Military info
     * Some are very much so as Unciv *is* Strategic View.
     * The comments show a description like found in the mentioned sources for comparison.
     * @see http://gaming.stackexchange.com/questions/8122/ddg#8125
     */
    Civilopedia(Category.WorldScreen, Input.Keys.F1),                 // Civilopedia
    EmpireOverviewTrades(Category.WorldScreen, Input.Keys.F2),        // Economic info
    EmpireOverviewUnits(Category.WorldScreen, Input.Keys.F3),         // Military info
    EmpireOverviewPolitics(Category.WorldScreen, Input.Keys.F4),      // Diplomacy info
    SocialPolicies(Category.WorldScreen, Input.Keys.F5),              // Social Policies Screen
    TechnologyTree(Category.WorldScreen, Input.Keys.F6),              // Tech Screen
    EmpireOverviewNotifications(Category.WorldScreen, Input.Keys.F7), // Notification Log
    VictoryScreen(Category.WorldScreen, "Victory status", Input.Keys.F8),    // Victory Progress
    EmpireOverviewStats(Category.WorldScreen, Input.Keys.F9),         // Demographics
    EmpireOverviewResources(Category.WorldScreen, Input.Keys.F10),    // originally Strategic View
    QuickSave(Category.WorldScreen, Input.Keys.F11),                  // Quick Save
    QuickLoad(Category.WorldScreen, Input.Keys.F12),                  // Quick Load
    ViewCapitalCity(Category.WorldScreen, Input.Keys.HOME),           // Capital City View
    Options(Category.WorldScreen, KeyCharAndCode.ctrl('o')),    // Game Options
    SaveGame(Category.WorldScreen, KeyCharAndCode.ctrl('s')),   // Save
    LoadGame(Category.WorldScreen, KeyCharAndCode.ctrl('l')),   // Load
    ToggleResourceDisplay(Category.WorldScreen, KeyCharAndCode.ctrl('r')),  // Show Resources Icons
    ToggleYieldDisplay(Category.WorldScreen, KeyCharAndCode.ctrl('y')),  // Yield Icons, originally just "Y"
    // End of Civ5-inspired bindings

    QuitGame(Category.WorldScreen, KeyCharAndCode.ctrl('q')),
    NewGame(Category.WorldScreen, KeyCharAndCode.ctrl('n')),
    Diplomacy(Category.WorldScreen, KeyCharAndCode.UNKNOWN),
    Espionage(Category.WorldScreen, KeyCharAndCode.UNKNOWN),
    Undo(Category.WorldScreen, KeyCharAndCode.ctrl('z')),
    ToggleUI(Category.WorldScreen, "Toggle UI", KeyCharAndCode.ctrl('u')),
    ToggleWorkedTilesDisplay(Category.WorldScreen, KeyCharAndCode.UNKNOWN),
    ToggleMovementDisplay(Category.WorldScreen, KeyCharAndCode.UNKNOWN),
    ZoomIn(Category.WorldScreen, Input.Keys.NUMPAD_ADD),
    ZoomOut(Category.WorldScreen, Input.Keys.NUMPAD_SUBTRACT),

    // Map Panning - separate to get own expander. Map editor use will need to check this - it's arrows only
    PanUp(Category.MapPanning, Input.Keys.UP),
    PanLeft(Category.MapPanning, Input.Keys.LEFT),
    PanDown(Category.MapPanning, Input.Keys.DOWN),
    PanRight(Category.MapPanning, Input.Keys.RIGHT),
    PanUpAlternate(Category.MapPanning, 'W'),
    PanLeftAlternate(Category.MapPanning, 'A'),
    PanDownAlternate(Category.MapPanning, 'S'),
    PanRightAlternate(Category.MapPanning, 'D'),

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
    DisbandUnit(Category.UnitActions,"Disband unit", KeyCharAndCode.DEL),
    GiftUnit(Category.UnitActions,"Gift unit", KeyCharAndCode.UNKNOWN),
    Wait(Category.UnitActions, 'z'),
    ShowAdditionalActions(Category.UnitActions,"Show more", Input.Keys.PAGE_DOWN),
    HideAdditionalActions(Category.UnitActions,"Back", Input.Keys.PAGE_UP),
    AddInCapital(Category.UnitActions, "Add in capital", 'g'),

    // Popups
    Confirm(Category.Popups, "Confirm Dialog", 'y'),
    Cancel(Category.Popups, "Cancel Dialog", 'n'),
    ;
    //endregion

    enum class Category {
        None,
        WorldScreen {
            // Conflict checking within group plus keys assigned to UnitActions are a problem
            override fun checkConflictsIn() = sequenceOf(this, MapPanning, UnitActions)
        },
        MapPanning {
            override fun checkConflictsIn() = sequenceOf(this, WorldScreen)
        },
        UnitActions {
            // Conflict checking within group disabled, but any key assigned on WorldScreen is a problem
            override fun checkConflictsIn() = sequenceOf(WorldScreen)
        },
        Popups
        ;
        val label = unCamelCase(name)
        open fun checkConflictsIn() = sequenceOf(this)
    }

    val label: String
    val defaultKey: KeyCharAndCode
    val hidden: Boolean get() = category == Category.None

    init {
        this.label = label ?: unCamelCase(name)
        this.defaultKey = key ?: KeyCharAndCode(name[0])
    }

    //region Helper constructors
    constructor(category: Category, label: String, key: Char) : this(category, label, KeyCharAndCode(key))
    constructor(category: Category, label: String, key: Int) : this(category, label, KeyCharAndCode(key))
    constructor(category: Category, key: KeyCharAndCode) : this(category, null, key)
    constructor(category: Category, key: Char) : this(category, KeyCharAndCode(key))
    constructor(category: Category, key: Int) : this(category, KeyCharAndCode(key))
    //endregion

    /** Debug helper */
    override fun toString() = "$category.$name($defaultKey)"
}
