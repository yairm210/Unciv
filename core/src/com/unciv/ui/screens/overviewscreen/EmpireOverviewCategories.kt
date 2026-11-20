package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.screens.overviewscreen.EmpireOverviewTab.EmpireOverviewTabPersistableData
import com.unciv.view.CivView


/** This controls which Tabs for the [EmpireOverviewScreen] exist and their order.
 *
 *  To add a Tab, build a new [EmpireOverviewTab] subclass and fill out a new entry here, that's all.
 *  Note the enum value's name is used as Tab caption, so if you ever need a non-alphanumeric caption
 *  please redesign to include a property for the caption
 */
enum class EmpireOverviewCategories(
    val iconName: String,
    val shortcutKey: KeyCharAndCode,
    val scrollAlign: Int
) {
    Cities("OtherIcons/Cities", 'C', Align.topLeft) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                CityOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: CivView) = viewingPlayer.getCiv().cities.isEmpty()
        override fun getPersistDataClass() = CityOverviewTab.CityTabPersistableData::class.java
    },
    Stats("StatIcons/Gold", 'S', Align.top) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                StatsOverviewTab(viewingPlayer, overviewScreen)
        override fun showDisabled(viewingPlayer: CivView) = viewingPlayer.isSpectator()
    },
    Trades("StatIcons/Acquire", 'T', Align.top) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                TradesOverviewTab(viewingPlayer, overviewScreen)
        override fun showDisabled(viewingPlayer: CivView) =
                viewingPlayer.getCiv().diplomacy.values.all { it.trades.isEmpty() } &&
                viewingPlayer.getCiv().diplomacy.values.none { diplomacyManager ->
                        diplomacyManager.otherCiv.tradeRequests.any { it.requestingCiv == viewingPlayer.getCiv().civID }
                    }
    },
    Units("OtherIcons/Shield", 'U', Align.topLeft) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                UnitOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: CivView) = viewingPlayer.getCiv().units.getCivUnits().none()
        override fun getPersistDataClass() = UnitOverviewTab.UnitTabPersistableData::class.java
    },
    Politics("OtherIcons/Politics", 'P', Align.top) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                GlobalPoliticsOverviewTable(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: CivView) = viewingPlayer.getCiv().diplomacy.isEmpty()
        override fun getPersistDataClass() = GlobalPoliticsOverviewTable.DiplomacyTabPersistableData::class.java
    },
    Resources("StatIcons/Happiness", 'R', Align.topLeft) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                ResourcesOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: CivView) = viewingPlayer.getCiv().detailedCivResources.none { it.resource.resourceType != ResourceType.Bonus }
        override fun getPersistDataClass() = ResourcesOverviewTab.ResourcesTabPersistableData::class.java
    },
    Religion("StatIcons/Faith", 'F', Align.top) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                ReligionOverviewTab(viewingPlayer, overviewScreen, persistedData)
        override fun testState(viewingPlayer: CivView) = when {
            !viewingPlayer.getCiv().gameInfo.isReligionEnabled() -> EmpireOverviewTabState.Hidden
            viewingPlayer.getCiv().gameInfo.religions.isEmpty() -> EmpireOverviewTabState.Disabled
            else -> EmpireOverviewTabState.Normal
        }
    },
    Wonders("OtherIcons/Wonders", 'W', Align.top) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                WonderOverviewTab(viewingPlayer, overviewScreen)
        override fun showDisabled(viewingPlayer: CivView) = (viewingPlayer.getCiv().naturalWonders.isEmpty() && viewingPlayer.getCiv().cities.isEmpty())
    },
    Notifications("OtherIcons/Notifications", 'N', Align.top) {
        override fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?) =
                NotificationsOverviewTable(viewingPlayer, overviewScreen, persistedData)
        override fun showDisabled(viewingPlayer: CivView) = viewingPlayer.getCiv().notifications.isEmpty() && viewingPlayer.getCiv().notificationsLog.isEmpty()
    }

    ;

    constructor(iconName: String, shortcutChar: Char, scrollAlign: Int)
        : this(iconName, KeyCharAndCode(shortcutChar), scrollAlign)

    enum class EmpireOverviewTabState { Normal, Disabled, Hidden }

    abstract fun createTab(viewingPlayer: CivView, overviewScreen: EmpireOverviewScreen, persistedData: EmpireOverviewTabPersistableData?): EmpireOverviewTab
    open fun showDisabled(viewingPlayer: CivView) = false
    open fun testState(viewingPlayer: CivView) =
            if (showDisabled(viewingPlayer)) EmpireOverviewTabState.Disabled
            else EmpireOverviewTabState.Normal

    /** Get Java class of persistable data for Json serialization
     *  - only needed if the data should actually be saved to GameSettings.json: Leaving it at `null` means any specific state persists only through one game run
     */
    open fun getPersistDataClass(): Class<out EmpireOverviewTabPersistableData>? = null
}
