package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.MultiplayerGameDeleted
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.logic.multiplayer.apiv2.ApiException
import com.unciv.logic.multiplayer.apiv2.GameOverviewResponse
import com.unciv.models.translations.tr
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.popups.Popup
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.InfoPopup
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import com.unciv.ui.components.AutoScrollPane as ScrollPane

class MultiplayerScreenV2 : PickerScreen() {
    private var selectedGame: Pair<String, OnlineMultiplayerGame>? = null // pair of game UUID to file handle
    private var cachedGames: Map<String, GameOverviewResponse> = mutableMapOf()

    private val leftSideTable = Table() // list friend requests, then online friends, then offline friends, see recreateLeftSideTable()
    private val rightSideTable = Table() // this should be used for game details and buttons ™ // GameList(::selectGame)

    private val updateFriendListButton = "Update friend list".toTextButton()
    private val requestFriendshipButton = "Request friendship".toTextButton()
    private val updateGameListButton = "Update games".toTextButton()
    // TODO: Align lobby button horizontally to the join game button, if possible
    private val lobbyBrowserButton = "Browse open lobbies".toTextButton()

    private val events = EventBus.EventReceiver()

    init {
        lobbyBrowserButton.onClick {
            game.pushScreen(LobbyBrowserScreen())
        }
        updateFriendListButton.onClick {
            Concurrency.run {
                reloadFriendList()
            }
        }
        updateGameListButton.onClick {
            Concurrency.run {
                reloadGameList()
            }
        }

        setDefaultCloseAction()
        recreateLeftSideTable()

        scrollPane.setScrollingDisabled(false, true)
        topTable.add(createMainContent()).row()

        setupHelpButton()

        rightSideGroup.addActor(lobbyBrowserButton)
        rightSideButton.setText("Join game".tr())
        rightSideButton.onClick {
            if (selectedGame != null) {
                MultiplayerHelpers.loadMultiplayerGame(this, selectedGame!!.second)
            }
        }

        events.receive(MultiplayerGameDeleted::class, { it.name == selectedGame?.first }) {
            unselectGame()
        }

        pickerPane.bottomTable.background = skinStrings.getUiBackground("MultiplayerScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.topTable.background = skinStrings.getUiBackground("MultiplayerScreen/TopTable", tintColor = skinStrings.skinConfig.clearColor)

        Concurrency.run {
            reloadGameList()
        }
    }

    /**
     * Reload the list of friends and friend requests from the server
     */
    private suspend fun reloadFriendList() {
        reloadFriendList()
    }

    /**
     * Reload the list of open games from the server, disabling the button if it's not available anymore
     */
    private suspend fun reloadGameList() {
        try {
            // Map of game UUID to game overview
            val newCachedGames = game.onlineMultiplayer.api.games.list().associateBy({ it.gameUUID.toString() }, { it })
            Concurrency.runOnGLThread {
                if (selectedGame != null && !newCachedGames.containsKey(selectedGame!!.first)) {
                    unselectGame()
                }
                cachedGames = newCachedGames
                recreateRightSideTable()
            }
        } catch (e: ApiException) {
            Concurrency.runOnGLThread {
                InfoPopup(stage, e.localizedMessage)
            }
        }
    }

    /**
     * Recreate a scrollable table of all friend requests and friends, sorted by their online status
     */
    // TODO: This method is a stub at the moment and needs expansion
    private fun recreateLeftSideTable() {
        leftSideTable.clear()
        leftSideTable.add("Friends".toLabel()).colspan(2).row()

        leftSideTable.defaults().uniformX()
        leftSideTable.defaults().fillX()
        leftSideTable.defaults().pad(10.0f)

        leftSideTable.add("label A".toLabel()).colspan(2).row()
        leftSideTable.add("label B".toLabel()).colspan(2).row()
        leftSideTable.add("label C".toLabel()).colspan(2).row()
        leftSideTable.add("label D".toLabel()).colspan(2).row()

        for (y in 0..24) {
            leftSideTable.add("Friend $y ${if (y % 2 == 0) "ONLINE" else "OFFLINE"}".toLabel()).row()
        }

        leftSideTable.add(updateFriendListButton)
        leftSideTable.add(requestFriendshipButton).row()
    }

    /**
     * Recreate a list of all games stored on the server
     */
    private fun recreateRightSideTable() {
        rightSideTable.clear()
        rightSideTable.add("Games".toLabel()).row()

        rightSideTable.defaults().uniformX()
        rightSideTable.defaults().fillX()
        rightSideTable.defaults().pad(10.0f)

        for (y in 0..24) {
            rightSideTable.add("Game $y ${if (y % 2 == 0) "ONLINE" else "OFFLINE"}".toLabel()).row()
        }

        cachedGames.forEach {
            val btn = "Game ${it.key}".toTextButton()
            btn.onClick {
                selectGame(it.key)
            }
            rightSideTable.add(btn).row()
        }

        rightSideTable.add(updateGameListButton).row()
    }

    private fun createMainContent(): Table {
        val mainTable = Table()
        mainTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).center()
        mainTable.add(ScrollPane(rightSideTable).apply { setScrollingDisabled(true, false) }).center()
        return mainTable
    }

    private fun setupHelpButton() {
        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.")
                .row()
            helpPopup.addGoodSizedLabel("You can assign your own user ID there easily, and other players can copy their user IDs here and send them to you for you to include them in the game.")
                .row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("Once you've created your game, the Game ID gets automatically copied to your clipboard so you can send it to the other players.")
                .row()
            helpPopup.addGoodSizedLabel("Players can enter your game by copying the game ID to the clipboard, and clicking on the 'Add multiplayer game' button")
                .row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("The symbol of your nation will appear next to the game when it's your turn").row()

            helpPopup.addCloseButton()
            helpPopup.open()
        }
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)

        stage.addActor(tab)
    }

    private fun unselectGame() {
        selectedGame = null
        rightSideButton.disable()
        descriptionLabel.setText("")
    }

    private fun selectGame(name: String) {
        if (!cachedGames.containsKey(name)) {
            Log.error("UI game cache key '$name' doesn't exist")
            unselectGame()
            return
        }

        val storedMultiplayerGame = game.onlineMultiplayer.getGameByName(name)
        if (storedMultiplayerGame == null) {
            InfoPopup(stage, "The game $name was not downloaded yet.")  // TODO
        } else {
            selectedGame = Pair(name, storedMultiplayerGame)
        }

        rightSideButton.enable()
        descriptionLabel.setText(describeGame(cachedGames[name]!!, storedMultiplayerGame))
    }

    private fun describeGame(cachedGame: GameOverviewResponse, storedMultiplayerGame: OnlineMultiplayerGame?): String {
        var details = "More details are being loaded ..."
        if (storedMultiplayerGame != null) {
            val preview = storedMultiplayerGame.preview
            if (preview != null) {
                details = "Turns: ${preview.turns}\nDifficulty: ${preview.difficulty}\nCivilizations: ${preview.civilizations}"
            }
        }
        return "${cachedGame.name}\nGame ID: ${cachedGame.gameUUID}\nData version: ${cachedGame.gameDataID}\nLast activity: ${cachedGame.lastActivity}\nLast player: ${cachedGame.lastPlayer.displayName}\n$details"
    }
}

