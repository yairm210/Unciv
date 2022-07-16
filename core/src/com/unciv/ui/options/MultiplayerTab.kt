package com.unciv.ui.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.Multiplayer.ServerType
import com.unciv.models.UncivSound
import com.unciv.models.metadata.GameSetting
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.multiplayer.ServerInput
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.brighten
import com.unciv.ui.utils.extensions.format
import com.unciv.ui.utils.extensions.toGdxArray
import com.unciv.ui.utils.extensions.toLabel
import java.time.Duration
import java.time.temporal.ChronoUnit

fun OptionsPopup.multiplayerTab(): Table {
    val optionsPopup = this
    val tab = Table(BaseScreen.skin)
    tab.pad(10f)
    tab.defaults().pad(5f)

    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(
        tab, "Enable multiplayer status button in singleplayer games",
        settings.multiplayer::statusButtonInSinglePlayer, updateWorld = true
    )

    addSeparator(tab)

    val curRefreshSelect = RefreshSelect(
        "Update status of currently played game every:",
        createRefreshOptions(ChronoUnit.SECONDS, 3, 5),
        createRefreshOptions(ChronoUnit.SECONDS, 10, 20, 30, 60),
        GameSetting.MULTIPLAYER_CURRENT_GAME_REFRESH_DELAY,
        settings
    )
    addSelectAsSeparateTable(tab, curRefreshSelect)

    val allRefreshSelect = RefreshSelect(
        "In-game, update status of all games every:",
        createRefreshOptions(ChronoUnit.SECONDS, 15, 30),
        createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15),
        GameSetting.MULTIPLAYER_ALL_GAME_REFRESH_DELAY,
        settings
    )
    addSelectAsSeparateTable(tab, allRefreshSelect)

    addSeparator(tab)

    // at the moment the notification service only exists on Android
    val turnCheckerSelect: RefreshSelect?
    if (Gdx.app.type != Application.ApplicationType.Android) {
        turnCheckerSelect = addTurnCheckerOptions(tab, optionsPopup)
        addSeparator(tab)
    } else {
        turnCheckerSelect = null
    }

    addSelectAsSeparateTable(tab, SettingsSelect("Sound notification for when it's your turn in your currently open game:",
        createNotificationSoundOptions(),
        GameSetting.MULTIPLAYER_CURRENT_GAME_TURN_NOTIFICATION_SOUND,
        settings
    ))

    addSelectAsSeparateTable(tab, SettingsSelect("Sound notification for when it's your turn in any other game:",
        createNotificationSoundOptions(),
        GameSetting.MULTIPLAYER_OTHER_GAME_TURN_NOTIFICATION_SOUND,
        settings
    ))

    addSeparator(tab)

    val refreshSelects = listOf(curRefreshSelect, allRefreshSelect, turnCheckerSelect).filterNotNull()
    val serverInput = ServerInput(settings.multiplayer::defaultServerData) { serverData ->
        for (select in refreshSelects) select.update(serverData.type)
        UncivGame.Current.settings.save()
    }

    val serverDataTable = Table()
    val defaultServerLabel = "{Default server for all new games}:".toLabel()
    defaultServerLabel.wrap = true
    serverDataTable.add(defaultServerLabel)
        .left()
        .minWidth(100f)
        .spaceRight(10f)
        .growX()
    serverDataTable.add(serverInput.standalone(true))
        .right()
        .minWidth(200f)
        .maxWidth(600f)
        .growX()
    tab.add(serverDataTable).colspan(2).growX()

    return tab
}

private fun createNotificationSoundOptions(): List<SelectItem<UncivSound>> = listOf(
    SelectItem("None", UncivSound.Silent),
    SelectItem("Notification [1]", UncivSound.Notification1),
    SelectItem("Notification [2]", UncivSound.Notification2),
    SelectItem("Chimes", UncivSound.Chimes),
    SelectItem("Choir", UncivSound.Choir),
    SelectItem("Buy", UncivSound.Coin),
    SelectItem("Create", UncivSound.Construction),
    SelectItem("Fortify", UncivSound.Fortify),
    SelectItem("Pick a tech", UncivSound.Paper),
    SelectItem("Adopt policy", UncivSound.Policy),
    SelectItem("Promote", UncivSound.Promote),
    SelectItem("Set up", UncivSound.Setup),
    SelectItem("Swap units", UncivSound.Swap),
    SelectItem("Upgrade", UncivSound.Upgrade),
    SelectItem("Bombard", UncivSound.Bombard)
) + buildUnitAttackSoundOptions()

private fun buildUnitAttackSoundOptions(): List<SelectItem<UncivSound>> {
    return RulesetCache.getSortedBaseRulesets()
        .map(RulesetCache::get).filterNotNull()
        .map(Ruleset::units).map { it.values }
        .flatMap { it }
        .filter { it.attackSound != null }
        .filter { it.attackSound != "nuke" } // much too long for a notification
        .distinctBy { it.attackSound }
        .map { SelectItem("[${it.name}] Attack Sound", UncivSound(it.attackSound!!)) }
}

private fun addTurnCheckerOptions(
    tab: Table,
    optionsPopup: OptionsPopup
): RefreshSelect? {
    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(tab, "Enable out-of-game turn notifications", settings.multiplayer::turnCheckerEnabled)

    if (!settings.multiplayer.turnCheckerEnabled) return null

    val turnCheckerSelect = RefreshSelect(
        "Out-of-game, update status of all games every:",
        createRefreshOptions(ChronoUnit.SECONDS, 30),
        createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15),
        GameSetting.MULTIPLAYER_TURN_CHECKER_DELAY,
        settings
    )
    addSelectAsSeparateTable(tab, turnCheckerSelect)


    optionsPopup.addCheckbox(
        tab, "Show persistent notification for turn notifier service",
        settings.multiplayer::turnCheckerPersistentNotificationEnabled
    )

    return turnCheckerSelect
}

private class RefreshSelect(
    labelText: String,
    extraCustomServerOptions: List<SelectItem<Duration>>,
    dropboxOptions: List<SelectItem<Duration>>,
    setting: GameSetting,
    settings: GameSettings
) : SettingsSelect<Duration>(labelText, getInitialOptions(extraCustomServerOptions, dropboxOptions), setting, settings) {
    private val customServerItems = (extraCustomServerOptions + dropboxOptions).toGdxArray()
    private val dropboxItems = dropboxOptions.toGdxArray()

    fun update(serverType: ServerType) {
        if (serverType == ServerType.CUSTOM && items.size != customServerItems.size) {
            replaceItems(customServerItems)
        } else if (serverType == ServerType.DROPBOX && items.size != dropboxItems.size) {
            replaceItems(dropboxItems)
        }
    }
}

private fun getInitialOptions(extraCustomServerOptions: List<SelectItem<Duration>>, dropboxOptions: List<SelectItem<Duration>>): Iterable<SelectItem<Duration>> {
    val customServerItems = (extraCustomServerOptions + dropboxOptions).toGdxArray()
    val dropboxItems = dropboxOptions.toGdxArray()
    return if (UncivGame.Current.settings.multiplayer.defaultServerData.type == ServerType.CUSTOM) customServerItems else dropboxItems
}

private fun createRefreshOptions(unit: ChronoUnit, vararg options: Long): List<SelectItem<Duration>> {
    return options.map {
        val duration = Duration.of(it, unit)
        SelectItem(duration.format(), duration)
    }
}

private fun addSelectAsSeparateTable(tab: Table, settingsSelect: SettingsSelect<*>) {
    val table = Table()
    settingsSelect.addTo(table)
    tab.add(table).growX().fillX().row()
}

private fun addSeparator(tab: Table) {
    tab.addSeparator(ImageGetter.getBlue().brighten(0.1f))
}
