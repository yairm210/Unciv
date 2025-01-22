package com.unciv.ui.components.input

import com.badlogic.gdx.Input
import com.unciv.Constants
import com.unciv.models.stats.Stat


private val unCamelCaseRegex = Regex("([A-Z])([A-Z])([a-z])|([a-z])([A-Z])")
private fun unCamelCase(name: String) = unCamelCaseRegex.replace(name, """$1$4 $2$3$5""")

/**
 *  This is the database of supported "bindable" keyboard shortcuts.
 *
 *  Note a label is automatically generated from the name by inserting spaces before each uppercase letter (except the initial one),
 *  and translation keys are automatically generated for all labels. This also works for [KeyboardBinding.Category].
 *
 *  [label] entries containing a placeholder need special treatment - see [getTranslationEntries] and update it when adding more.
 */
enum class KeyboardBinding(
    val category: Category,
    label: String? = null,
    key: KeyCharAndCode? = null
) {
    //region Enum Instances

    /** Used by [KeyShortcutDispatcher.KeyShortcut] to mark an old-style shortcut with a hardcoded key */
    None(Category.None, KeyCharAndCode.UNKNOWN),

    // MainMenu
    QuitMainMenu(Category.MainMenu, "Quit", KeyCharAndCode.BACK),
    Resume(Category.MainMenu),
    Quickstart(Category.MainMenu),
    StartNewGame(Category.MainMenu, "Start new game", KeyCharAndCode('N')),  // Not to be confused with NewGame (from World menu, Ctrl-N)
    MainMenuLoad(Category.MainMenu, "Load game", KeyCharAndCode('L')),
    Multiplayer(Category.MainMenu),  // Name disambiguation maybe soon, not yet necessary
    MapEditor(Category.MainMenu, "Map editor", KeyCharAndCode('E')),
    ModManager(Category.MainMenu, "Mods", KeyCharAndCode('D')),
    Scenarios(Category.MainMenu, "Scenarios", KeyCharAndCode('S')),
    MainMenuOptions(Category.MainMenu, "Options", KeyCharAndCode('O')),  // Separate binding from World where it's Ctrl-O default

    // Worldscreen
    DeselectOrQuit(Category.WorldScreen, "Deselect then Quit", KeyCharAndCode.BACK),
    Menu(Category.WorldScreen, KeyCharAndCode.TAB),
    NextTurn(Category.WorldScreen),
    NextTurnAlternate(Category.WorldScreen, KeyCharAndCode.SPACE),
    AutoPlayMenu(Category.WorldScreen, "Open AutoPlay menu", KeyCharAndCode.UNKNOWN),  // 'a' is already assigned to map panning
    AutoPlay(Category.WorldScreen, "Start AutoPlay", KeyCharAndCode.ctrl('a')),
    EmpireOverview(Category.WorldScreen),
    MusicPlayer(Category.WorldScreen, KeyCharAndCode.ctrl('m')),
    DeveloperConsole(Category.WorldScreen, '`'),
    Cycle(Category.WorldScreen, ';'),

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
    ConnectRoad(Category.UnitActions, "Connect road", 'c'),
    StopAutomation(Category.UnitActions,"Stop automation", 'm'),
    StopMovement(Category.UnitActions,"Stop movement", '.'),
    ShowUnitDestination(Category.UnitActions, "Show unit destination", 'j'),
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
    Skip(Category.UnitActions, 'z'),
    ShowAdditionalActions(Category.UnitActions,"Show more", Input.Keys.PAGE_DOWN),
    HideAdditionalActions(Category.UnitActions,"Back", Input.Keys.PAGE_UP),
    AddInCapital(Category.UnitActions, "Add in capital", 'g'),

    // The AutoPlayMenu reuses the AutoPlay binding, under Worldscreen above - otherwise clear labeling would be tricky
    AutoPlayMenuEndTurn(Category.AutoPlayMenu, "AutoPlay End Turn", 't'),
    AutoPlayMenuMilitary(Category.AutoPlayMenu, "AutoPlay Military Once", 'm'),
    AutoPlayMenuCivilians(Category.AutoPlayMenu, "AutoPlay Civilians Once", 'c'),
    AutoPlayMenuEconomy(Category.AutoPlayMenu, "AutoPlay Economy Once", 'e'),

    // NextTurnMenu
    NextTurnMenuNextTurn(Category.NextTurnMenu, "Next Turn", 'n'),
    NextTurnMenuMoveAutomatedUnits(Category.NextTurnMenu, "Move Automated Units", 'm'),

    // City Screen
    AddConstruction(Category.CityScreen, "Add to or remove from queue", KeyCharAndCode.RETURN),
    RaisePriority(Category.CityScreen, "Raise queue priority", Input.Keys.UP),
    LowerPriority(Category.CityScreen, "Lower queue priority", Input.Keys.DOWN),
    BuyConstruction(Category.CityScreen, 'b'),
    BuyTile(Category.CityScreen, 't'),
    BuildUnits(Category.CityScreen, "Buildable Units", 'u'),
    BuildBuildings(Category.CityScreen, "Buildable Buildings", 'l'),
    BuildWonders(Category.CityScreen, "Buildable Wonders", 'w'),
    BuildNationalWonders(Category.CityScreen, "Buildable National Wonders", 'n'),
    BuildOther(Category.CityScreen, "Other Constructions", 'o'),
    BuildDisabled(Category.CityScreen, "Disabled Constructions", KeyCharAndCode.ctrl('h')),
    NextCity(Category.CityScreen, Input.Keys.RIGHT),
    PreviousCity(Category.CityScreen, Input.Keys.LEFT),
    ShowStats(Category.CityScreen, 's'),
    ShowStatDetails(Category.CityScreen, "Toggle Stat Details", Input.Keys.NUMPAD_ADD),
    CitizenManagement(Category.CityScreen, 'c'),
    GreatPeopleDetail(Category.CityScreen, 'g'),
    SpecialistDetail(Category.CityScreen, 'p'),
    ReligionDetail(Category.CityScreen, 'r'),
    BuildingsDetail(Category.CityScreen, 'd'),
    ResetCitizens(Category.CityScreen, KeyCharAndCode.ctrl('r')),
    AvoidGrowth(Category.CityScreen, KeyCharAndCode.ctrl('a')),
    // The following are automatically matched by enum name to CityFocus entries - if necessary override there
    // Note on label: copied from CityFocus to ensure same translatable is used - without we'd get "Food Focus", not the same as "[Food] Focus"
    NoFocus(Category.CityScreen, "Default Focus", KeyCharAndCode.ctrl('d')),
    FoodFocus(Category.CityScreen, "[${Stat.Food.name}] Focus", KeyCharAndCode.ctrl('f')),
    ProductionFocus(Category.CityScreen, "[${Stat.Production.name}] Focus", KeyCharAndCode.ctrl('p')),
    GoldFocus(Category.CityScreen, "[${Stat.Gold.name}] Focus", KeyCharAndCode.ctrl('g')),
    ScienceFocus(Category.CityScreen, "[${Stat.Science.name}] Focus", KeyCharAndCode.ctrl('s')),
    CultureFocus(Category.CityScreen, "[${Stat.Culture.name}] Focus", KeyCharAndCode.ctrl('c')),
    FaithFocus(Category.CityScreen, "[${Stat.Faith.name}] Focus", KeyCharAndCode.UNKNOWN),

    // CityScreenConstructionMenu (not quite cleanly) reuses RaisePriority/LowerPriority, plus:
    AddConstructionTop(Category.CityScreenConstructionMenu, "Add to the top of the queue", 't'),
    AddConstructionAll(Category.CityScreenConstructionMenu, "Add to the queue in all cities", KeyCharAndCode.ctrl('a')),
    AddConstructionAllTop(Category.CityScreenConstructionMenu, "Add or move to the top in all cities", KeyCharAndCode.ctrl('t')),
    RemoveConstructionAll(Category.CityScreenConstructionMenu, "Remove from the queue in all cities", KeyCharAndCode.ctrl('r')),

    // Civilopedia
    PediaBuildings(Category.Civilopedia, "Buildings", 'b'),
    PediaWonders(Category.Civilopedia, "Wonders", 'w'),
    PediaResources(Category.Civilopedia, "Resources", 'r'),
    PediaTerrains(Category.Civilopedia, "Terrains", 't'),
    PediaImprovements(Category.Civilopedia, "Tile Improvements", 'i'),
    PediaUnits(Category.Civilopedia, "Units", 'u'),
    PediaUnitTypes(Category.Civilopedia, "Unit types", 'y'),
    PediaNations(Category.Civilopedia, "Nations", 'n'),
    PediaTechnologies(Category.Civilopedia, "Technologies", KeyCharAndCode.ctrl('t')),
    PediaPromotions(Category.Civilopedia, "Promotions", 'p'),
    PediaPolicies(Category.Civilopedia, "Policies", 'o'),
    PediaBeliefs(Category.Civilopedia, "Religions and Beliefs", 'f'),
    PediaTutorials(Category.Civilopedia, "Tutorials", Input.Keys.F1),
    PediaDifficulties(Category.Civilopedia, "Difficulty levels", 'd'),
    PediaEras(Category.Civilopedia, "Eras", 'e'),
    PediaSpeeds(Category.Civilopedia, "Speeds", 's'),
    PediaSearch(Category.Civilopedia, "Open the Search Dialog", KeyCharAndCode.ctrl('f')),

    // Popups
    Confirm(Category.Popups, "Confirm Dialog", 'y'),
    Cancel(Category.Popups, "Cancel Dialog", 'n'),
    UpgradeAll(Category.Popups, KeyCharAndCode.ctrl('a')),  // rethink? No UnitUpgradeMenu category, but CityScreenConstructionMenu gets one?
    ;
    //endregion

    enum class Category {
        None,
        MainMenu,
        WorldScreen {
            // Conflict checking within group plus keys assigned to UnitActions are a problem
            override fun checkConflictsIn() = sequenceOf(this, MapPanning, UnitActions)
        },
        AutoPlayMenu {
            override val label = "AutoPlay menu" // adapt to existing usage
        },
        NextTurnMenu {
            override val label = "NextTurn menu" // adapt to existing usage
        },
        MapPanning {
            override fun checkConflictsIn() = sequenceOf(this, WorldScreen)
        },
        UnitActions {
            // Conflict checking within group disabled, but any key assigned on WorldScreen is a problem
            override fun checkConflictsIn() = sequenceOf(WorldScreen)
        },
        CityScreen,
        CityScreenConstructionMenu, // Maybe someday a category hierarchy?
        Civilopedia,
        Popups
        ;
        open val label = unCamelCase(name)
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

    companion object {
        fun getTranslationEntries() = (
                Category.entries.asSequence().map { it.label }
                + entries.asSequence().map { it.label }.filterNot { it.contains('[') }
                + sequenceOf("[stat] Focus")
            )
    }

    /** Debug helper */
    override fun toString() = "$category.$name($defaultKey)"
}
