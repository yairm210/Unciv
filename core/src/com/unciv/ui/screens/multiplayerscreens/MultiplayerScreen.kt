package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.multiplayer.MultiplayerGame
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.AuthPopup
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import java.time.Duration
import java.time.Instant
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

class MultiplayerScreen : PickerScreen() {
    private var selectedGame: MultiplayerGame? = null

    private val copyGameIdButton = createCopyGameIdButton()
    private val resignButton = createResignButton()
    private val forceResignButton = createForceResignButton()
    private val deleteButton = createDeleteButton()
    private val renameButton = createRenameButton()

    private val gameSpecificButtons = listOf(copyGameIdButton, resignButton, deleteButton, renameButton)

    private val addGameButton = createAddGameButton()
    private val copyUserIdButton = createCopyUserIdButton()
    private val friendsListButton = createFriendsListButton()
    private val refreshButton = createRefreshButton()

    private val rightSideTable = createRightSideTable()
    val gameList = GameList(::selectGame)

    init {
        setDefaultCloseAction()

        scrollPane.setScrollingDisabled(false, true)

        topTable.add(createMainContent()).row()

        setupHelpButton()
        setupRightSideButton()

        game.onlineMultiplayer.requestUpdate()

        pickerPane.bottomTable.background = skinStrings.getUiBackground("MultiplayerScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.topTable.background = skinStrings.getUiBackground("MultiplayerScreen/TopTable", tintColor = skinStrings.skinConfig.clearColor)
    }

    fun onGameDeleted(gameName:String){
        if (selectedGame?.name == gameName) unselectGame()
        gameList.update()
    }

    private fun setupRightSideButton() {
        rightSideButton.setText("Join game".tr())
        rightSideButton.onClick { MultiplayerHelpers.loadMultiplayerGame(this, selectedGame!!) }
    }

    private fun createRightSideTable(): Table {
        val table = Table()
        table.defaults().uniformX()
        table.defaults().fillX()
        table.defaults().pad(10f)

        val gameSpecificActions = Table().apply { defaults().pad(10f) }
        gameSpecificActions.add(copyGameIdButton).row()
        gameSpecificActions.add(renameButton).row()
        gameSpecificActions.add(resignButton).row()
        gameSpecificActions.add(forceResignButton).row()
        gameSpecificActions.add(deleteButton).row()
        table.add(gameSpecificActions)

        val generalActions = Table().apply { defaults().pad(10f) }
        generalActions.add(copyUserIdButton).row()
        generalActions.add(addGameButton).row()
        generalActions.add(friendsListButton).row()
        generalActions.add(refreshButton).row()
        table.add(generalActions)

        return table
    }

    fun createRefreshButton(): TextButton {
        val btn = "Refresh list".toTextButton()
        btn.onClick { game.onlineMultiplayer.requestUpdate() }
        return btn
    }

    fun createAddGameButton(): TextButton {
        val btn = "Add multiplayer game".toTextButton()
        btn.onClick {
            game.pushScreen(AddMultiplayerGameScreen(this))
        }
        return btn
    }

    fun createResignButton(): TextButton {
        val negativeButtonStyle = skin.get("negative", TextButton.TextButtonStyle::class.java)
        val resignButton = "Resign".toTextButton(negativeButtonStyle).apply { disable() }
        resignButton.onClick {
            val askPopup = ConfirmPopup(
                    this,
                    "Are you sure you want to resign?",
                    "Resign",
            ) {
                resignCurrentPlayer(selectedGame!!)
            }
            askPopup.open()
        }
        return resignButton
    }

    fun createForceResignButton(): TextButton {
        val negativeButtonStyle = skin.get("negative", TextButton.TextButtonStyle::class.java)
        val resignButton = "Force current player to resign".toTextButton(negativeButtonStyle).apply { isVisible = false }
        resignButton.onClick {
            val askPopup = ConfirmPopup(
                this,
                "Are you sure you want to force the current player to resign?",
                "Yes",
            ) {
                resignCurrentPlayer(selectedGame!!)
            }
            askPopup.open()
        }
        return resignButton
    }

    /**
     * Turns the current playerCiv into an AI civ and uploads the game afterwards.
     */
    private fun resignCurrentPlayer(multiplayerGame: MultiplayerGame) {
        //Create a popup
        val popup = Popup(this)
        popup.addGoodSizedLabel(Constants.working).row()
        popup.open()

        Concurrency.runOnNonDaemonThreadPool("Resign") {
            try {
                val resignSuccess = game.onlineMultiplayer.resignCurrentPlayer(multiplayerGame)

                launchOnGLThread {
                    if (resignSuccess) {
                        popup.close()
                        game.popScreen()
                    } else {
                        popup.reuseWith("You can only resign if it's your turn", true)
                    }
                }
            } catch (ex: Exception) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex)

                if (ex is MultiplayerAuthException) {
                    launchOnGLThread {
                        AuthPopup(this@MultiplayerScreen) { success ->
                            if (success) resignCurrentPlayer(multiplayerGame)
                        }.open(true)
                    }
                    return@runOnNonDaemonThreadPool
                }

                launchOnGLThread {
                    popup.reuseWith(message, true)
                }
            }
        }
    }

    fun createDeleteButton(): TextButton {
        val negativeButtonStyle = skin.get("negative", TextButton.TextButtonStyle::class.java)
        val deleteButton = "Delete save".toTextButton(negativeButtonStyle).apply { disable() }
        deleteButton.onClick {
            val askPopup = ConfirmPopup(
                    this,
                    "Are you sure you want to delete this save?",
                    "Delete save",
            ) {
                try {
                    game.onlineMultiplayer.multiplayerFiles.deleteGame(selectedGame!!)
                    onGameDeleted(selectedGame!!.name)
                } catch (ex: Exception) {
                    Log.error("Could not delete game!", ex)
                    ToastPopup("Could not delete game!", this)
                }
            }
            askPopup.open()
        }
        return deleteButton
    }

    fun createRenameButton(): TextButton {
        val btn = "Rename".toTextButton().apply { disable() }
        btn.onClick {
            Popup(this).apply {
                val textField = UncivTextField("Game name", selectedGame!!.name)
                add(textField).width(stageToShowOn.width / 2).row()
                val saveButton = "Save".toTextButton()

                val saveNewNameFunction = {
                    val newName = textField.text.trim()
                    game.onlineMultiplayer.multiplayerFiles.changeGameName(selectedGame!!, newName) {
                        if (it != null) reuseWith("Could not save game!", true)
                    }
                    gameList.update()
                    selectGame(newName)
                    close()
                }

                saveButton.onActivation(saveNewNameFunction)
                saveButton.keyShortcuts.add(KeyCharAndCode.RETURN)
                textField.cursorPosition = textField.text.length
                this@MultiplayerScreen.stage.keyboardFocus = textField
                add(saveButton)
                open()
            }
        }
        return btn
    }

    fun createCopyGameIdButton(): TextButton {
        val btn = "Copy game ID".toTextButton().apply { disable() }
        btn.onClick {
            val gameInfo = selectedGame?.preview
            if (gameInfo != null) {
                Gdx.app.clipboard.contents = gameInfo.gameId
                ToastPopup("Game ID copied to clipboard!", this)
            }
        }
        return btn
    }

    fun createFriendsListButton(): TextButton {
        val btn = "Friends list".toTextButton()
        btn.onClick {
            game.pushScreen(ViewFriendsListScreen())
        }
        return btn
    }

    private fun createCopyUserIdButton(): TextButton {
        val btn = "Copy user ID".toTextButton()
        btn.onClick {
            Gdx.app.clipboard.contents = game.settings.multiplayer.userId
            ToastPopup("UserID copied to clipboard", this)
        }
        return btn
    }

    private fun createMainContent(): Table {
        val mainTable = Table()
        mainTable.add(ScrollPane(gameList).apply { setScrollingDisabled(true, false) }).center()
        mainTable.add(rightSideTable)
        return mainTable
    }

    private fun setupHelpButton() {
        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.").row()
            helpPopup.addGoodSizedLabel("You can assign your own user ID there easily, and other players can copy their user IDs here and send them to you for you to include them in the game.").row()
            helpPopup.row()

            helpPopup.addGoodSizedLabel("Once you've created your game, the Game ID gets automatically copied to your clipboard so you can send it to the other players.").row()
            helpPopup.addGoodSizedLabel("Players can enter your game by copying the game ID to the clipboard, and clicking on the 'Add multiplayer game' button").row()
            helpPopup.row()

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
        for (button in gameSpecificButtons)
            button.disable()
        forceResignButton.isVisible = false

        descriptionLabel.setText("")
    }

    fun selectGame(name: String) {
        val multiplayerGame = game.onlineMultiplayer.multiplayerFiles.getGameByName(name)
        if (multiplayerGame == null) {
            // Should never happen
            unselectGame()
            return
        }

        selectedGame = multiplayerGame

        for (button in gameSpecificButtons) button.enable()
        if (multiplayerGame.preview != null) {
            copyGameIdButton.enable()
        } else {
            copyGameIdButton.disable()
        }

        resignButton.isEnabled = multiplayerGame.preview?.getCurrentPlayerCiv()?.playerId == game.settings.multiplayer.userId

        if (resignButton.isEnabled || multiplayerGame.preview == null){
            forceResignButton.isVisible = false
        } else {
            val durationInactive = Duration.between(Instant.ofEpochMilli(multiplayerGame.preview!!.currentTurnStartTime), Instant.now())
            forceResignButton.isVisible =
                multiplayerGame.preview?.getPlayerCiv(game.settings.multiplayer.userId)?.civName == Constants.spectator
                    || durationInactive > Duration.ofDays(2)
        }

        rightSideButton.enable()

        descriptionLabel.setText(MultiplayerHelpers.buildDescriptionText(multiplayerGame))
    }
}
