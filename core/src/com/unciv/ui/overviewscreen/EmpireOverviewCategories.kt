package com.unciv.ui.overviewscreen

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.overviewscreen.EmpireOverviewTab.EmpireOverviewTabPersistableData

private typealias FactoryType = (CivilizationInfo, EmpireOverviewScreen, EmpireOverviewTabPersistableData?) -> EmpireOverviewTab

enum class EmpireOverviewTabState { Normal, Disabled, Hidden }
private typealias StateTesterType = (CivilizationInfo) -> EmpireOverviewTabState
private fun Boolean.toState(): EmpireOverviewTabState = if (this) EmpireOverviewTabState.Disabled else EmpireOverviewTabState.Normal

/** This controls which Tabs for the [EmpireOverviewScreen] exist and their order.
 *
 *  To add a Tab, build a new [EmpireOverviewTab] subclass and fill out a new entry here, that's all.
 *  Note the enum value's name is used as Tab caption, so if you ever need a non-alphanumeric caption please redesign to include a property for the caption. 
 */
enum class EmpireOverviewCategories(
    val iconName: String,
    val shortcutKey: KeyCharAndCode,
    val factory: FactoryType,
    val stateTester: StateTesterType
) {
    Cities("OtherIcons/Cities", 'C',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?)
                = CityOverviewTab(viewingPlayer, overviewScreen, persistedData),
        fun (viewingPlayer: CivilizationInfo) = viewingPlayer.cities.isEmpty().toState()),
    Stats("StatIcons/Gold", 'S',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, _: EmpireOverviewTabPersistableData?)
                = StatsOverviewTab(viewingPlayer, overviewScreen),
        fun (_: CivilizationInfo) = EmpireOverviewTabState.Normal),
    Trades("StatIcons/Acquire", 'T',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, _: EmpireOverviewTabPersistableData?)
                = TradesOverviewTab(viewingPlayer, overviewScreen),
        fun (viewingPlayer: CivilizationInfo) = viewingPlayer.diplomacy.values.all { it.trades.isEmpty() }.toState()),
    Units("OtherIcons/Shield", 'U',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, _: EmpireOverviewTabPersistableData?)
                = UnitOverviewTab(viewingPlayer, overviewScreen),
        fun (viewingPlayer: CivilizationInfo) = viewingPlayer.getCivUnits().none().toState()),
    Diplomacy("OtherIcons/DiplomacyW", 'D',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?)
                = DiplomacyOverviewTab(viewingPlayer, overviewScreen, persistedData),
        fun (viewingPlayer: CivilizationInfo) = viewingPlayer.diplomacy.isEmpty().toState()),
    Resources("StatIcons/Happiness", 'R',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, _: EmpireOverviewTabPersistableData?)
                = ResourcesOverviewTab(viewingPlayer, overviewScreen),
        fun (viewingPlayer: CivilizationInfo) = viewingPlayer.detailedCivResources.isEmpty().toState()),
    Religion("StatIcons/Faith", 'F',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?)
                = ReligionOverviewTab(viewingPlayer, overviewScreen, persistedData),
        fun (viewingPlayer: CivilizationInfo) = when {
            !viewingPlayer.gameInfo.isReligionEnabled() -> EmpireOverviewTabState.Hidden
            viewingPlayer.gameInfo.religions.isEmpty() -> EmpireOverviewTabState.Disabled
            else -> EmpireOverviewTabState.Normal
        }),
    Wonders("OtherIcons/Wonders", 'W',
        fun (viewingPlayer: CivilizationInfo, overviewScreen: EmpireOverviewScreen, _: EmpireOverviewTabPersistableData?)
                = WonderOverviewTab(viewingPlayer, overviewScreen),
        fun (viewingPlayer: CivilizationInfo) = (viewingPlayer.naturalWonders.isEmpty() && viewingPlayer.cities.isEmpty()).toState()),
    ;

    constructor(iconName: String, shortcutChar: Char, factory: FactoryType, stateTester: StateTesterType = { _ -> EmpireOverviewTabState.Normal })
        : this(iconName, KeyCharAndCode(shortcutChar), factory, stateTester)
}

