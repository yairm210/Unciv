package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.screens.overviewscreen.EmpireOverviewTab.EmpireOverviewTabPersistableData


/** This controls which Tabs for the [EmpireOverviewScreen] exist and their order.
 *
 *  To add a Tab, build a new [EmpireOverviewTab] subclass and fill out a new entry here, that's all.
 *  Note the enum value's name is used as Tab caption, so if you ever need a non-alphanumeric caption
 *  please redesign to include a property for the caption - and don't forget GameSettings.lastOverviewPage
 *  currently looks for name when applied but uses tab caption when saving.
 */
enum class EmpireOverviewCategories(
    val iconName: String,
    val shortcutKey: KeyCharAndCode,
    val scrollAlign: Int
) {
    Cities("OtherIcons/Cities", 'C', Align.topLeft) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                CityOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: Civilization) = viewingPlayer.cities.isEmpty()
    },
    Stats("StatIcons/Gold", 'S', Align.top) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                StatsOverviewTab(viewingPlayer, overviewScreen)
        override fun showDisabled(viewingPlayer: Civilization) = viewingPlayer.isSpectator()
    },
    Trades("StatIcons/Acquire", 'T', Align.top) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                TradesOverviewTab(viewingPlayer, overviewScreen)
        override fun showDisabled(viewingPlayer: Civilization) =
                viewingPlayer.diplomacy.values.all { it.trades.isEmpty() } &&
                viewingPlayer.diplomacy.values.none { diplomacyManager ->
                        diplomacyManager.otherCiv().tradeRequests.any { it.requestingCiv == viewingPlayer.civName }
                    }
    },
    Units("OtherIcons/Shield", 'U', Align.topLeft) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                UnitOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: Civilization) = viewingPlayer.units.getCivUnits().none()
    },
    Politics("OtherIcons/Politics", 'P', Align.top) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                GlobalPoliticsOverviewTable(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: Civilization) = viewingPlayer.diplomacy.isEmpty()
    },
    Resources("StatIcons/Happiness", 'R', Align.topLeft) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                ResourcesOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: Civilization) = viewingPlayer.detailedCivResources.none { it.resource.resourceType != ResourceType.Bonus }
    },
    Religion("StatIcons/Faith", 'F', Align.top) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                ReligionOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun testState(viewingPlayer: Civilization) = when {
            !viewingPlayer.gameInfo.isReligionEnabled() -> EmpireOverviewTabState.Hidden
            viewingPlayer.gameInfo.religions.isEmpty() -> EmpireOverviewTabState.Disabled
            else -> EmpireOverviewTabState.Normal
        }
    },
    Wonders("OtherIcons/Wonders", 'W', Align.top) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                WonderOverviewTab(viewingPlayer, overviewScreen)
        override fun showDisabled(viewingPlayer: Civilization) = (viewingPlayer.naturalWonders.isEmpty() && viewingPlayer.cities.isEmpty())
    },
    Notifications("OtherIcons/Notifications", 'N', Align.top) {
        override fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                NotificationsOverviewTable(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: Civilization) = viewingPlayer.notifications.isEmpty() && viewingPlayer.notificationsLog.isEmpty()
    }

    ;

    constructor(iconName: String, shortcutChar: Char, scrollAlign: Int)
        : this(iconName, KeyCharAndCode(shortcutChar), scrollAlign)

    enum class EmpireOverviewTabState { Normal, Disabled, Hidden }

    abstract fun createTab(viewingPlayer: Civilization, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?): EmpireOverviewTab
    open fun showDisabled(viewingPlayer: Civilization) = false
    open fun testState(viewingPlayer: Civilization) =
            if (showDisabled(viewingPlayer)) EmpireOverviewTabState.Disabled
            else EmpireOverviewTabState.Normal
}
