package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.multiplayer.apiv2.GameOverviewResponse
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.MultiplayerButton
import com.unciv.ui.components.NewButton
import com.unciv.ui.components.RefreshButton
import com.unciv.ui.components.SettingsButton
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.CreateLobbyPopup
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Concurrency.runBlocking
import com.unciv.utils.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.unciv.ui.components.AutoScrollPane as ScrollPane

/**
 * Screen that should list all open lobbies on the left side, with buttons to interact with them and a list of recently opened games on the right
 */
class LobbyBrowserScreen : BaseScreen() {
    private val lobbyBrowserTable = LobbyBrowserTable(this) { updateJob.cancel() }
    private val gameList = GameListV2(this, ::onSelect)
    private var updateJob = startUpdateJob(false)

    private val me
        get() = runBlocking { game.onlineMultiplayer.api.account.get() }!!

    private val table = Table()  // main table including all content of this screen
    private val bottomTable = Table()  // bottom bar including the cancel and help buttons

    private val newLobbyButton = NewButton()
    private val socialButton = MultiplayerButton()
    private val serverSettingsButton = SettingsButton()
    private val helpButton = "Help".toTextButton()
    private val updateButton = RefreshButton()
    private val closeButton = Constants.close.toTextButton()

    init {
        table.add("Lobby browser".toLabel(fontSize = Constants.headingFontSize)).padTop(20f).padBottom(10f)
        table.add().colspan(2)  // layout purposes only
        table.add("Currently open games".toLabel(fontSize = Constants.headingFontSize)).padTop(20f).padBottom(10f)
        table.row()

        val lobbyButtons = Table()
        newLobbyButton.onClick {
            CreateLobbyPopup(this as BaseScreen, me)
        }
        updateButton.onClick {
            lobbyBrowserTable.triggerUpdate()
        }
        lobbyButtons.add(newLobbyButton).padBottom(5f).row()
        lobbyButtons.add("F".toTextButton().apply {
            label = "F".toLabel(fontSize = Constants.headingFontSize)
            label.setAlignment(Align.center)
            onClick { ToastPopup("Filtering is not implemented yet", stage) }
        }).padBottom(5f).row()
        lobbyButtons.add(updateButton).row()

        table.add(ScrollPane(lobbyBrowserTable).apply { setScrollingDisabled(true, false) }).growX().growY().padRight(10f)
        table.add(lobbyButtons).padLeft(10f).growY()
        table.addSeparatorVertical(Color.DARK_GRAY, 1f).height(0.75f * stage.height).padLeft(10f).padRight(10f).growY()
        table.add(ScrollPane(gameList).apply { setScrollingDisabled(true, false) }).growX()
        table.row()

        closeButton.keyShortcuts.add(KeyCharAndCode.ESC)
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButton.onActivation {
            game.popScreen()
        }
        socialButton.onClick {
            val popup = Popup(stage)
            popup.add(SocialMenuTable(this as BaseScreen, me.uuid)).center().minWidth(0.5f * stage.width).fillX().fillY().row()
            popup.addCloseButton()
            popup.open()
        }
        serverSettingsButton.onClick {
            ToastPopup("The server settings feature is not implemented yet. A server list should be added here as well.", this).open()
        }
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("This should become a lobby browser.").row()  // TODO
            helpPopup.addCloseButton()
            helpPopup.open()
        }
        bottomTable.add(closeButton).pad(20f)
        bottomTable.add().growX()  // layout purposes only
        bottomTable.add(socialButton).pad(5f)
        bottomTable.add(serverSettingsButton).padRight(5f)
        bottomTable.add(helpButton).padRight(20f)

        table.addSeparator(skinStrings.skinConfig.baseColor.brighten(0.1f), height = 1f).width(stage.width * 0.85f).padTop(15f).row()
        table.row().bottom().fillX().maxHeight(stage.height / 8)
        table.add(bottomTable).colspan(4).fillX()

        table.setFillParent(true)
        stage.addActor(table)
    }

    private fun onSelect(gameOverview: GameOverviewResponse) {
        Log.debug("Loading game '%s' (%s)", gameOverview.name, gameOverview.gameUUID)
        val gameInfo = InfoPopup.load(stage) {
            game.onlineMultiplayer.downloadGame(gameOverview.gameUUID.toString())
        }
        if (gameInfo != null) {
            Concurrency.runOnNonDaemonThreadPool {
                game.loadGame(gameInfo)
            }
        }
    }

    private fun startUpdateJob(updateNow: Boolean): Job {
        return Concurrency.run {
            if (updateNow) {
                lobbyBrowserTable.triggerUpdate()
            }
            while (true) {
                delay(30 * 1000)
                lobbyBrowserTable.triggerUpdate()
            }
        }
    }

    override fun resume() {
        Log.debug("Resuming LobbyBrowserScreen")
        updateJob.cancel()
        updateJob = startUpdateJob(true)
        super.resume()
    }

    override fun dispose() {
        updateJob.cancel()
        gameList.dispose()
        super.dispose()
    }

}
