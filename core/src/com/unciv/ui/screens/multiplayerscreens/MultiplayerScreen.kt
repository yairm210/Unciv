package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.MultiplayerGamePreview
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.models.ruleset.RulesetCache
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
    private var selectedGame: MultiplayerGamePreview? = null

    private val copyGameIdButton = createCopyGameIdButton()
    private val resignButton = createResignButton()
    private val forceResignButton = createForceResignButton()
    private val skipTurnButton = createSkipTurnButton()
    private val deleteButton = createDeleteButton()
    private val renameButton = createRenameButton()

    private val gameSpecificButtons = listOf(copyGameIdButton, resignButton, deleteButton, renameButton)

    private val addGameButton = createAddGameButton()
    private val copyUserIdButton = createCopyUserIdButton()
    private val friendsListButton = createFriendsListButton()
    private val refreshButton = createRefreshButton()

    val gameList = GameList(::selectGame)

    init {
        setDefaultCloseAction()

        scrollPane.setScrollingDisabled(false, true)

        topTable.add(createMainContent()).row()

        setupHelpButton()
        setupRightSideButton()
        
        Concurrency.run("Update all multiplayer games") {
            game.onlineMultiplayer.requestUpdate()
        }

        pickerPane.bottomTable.background = skinStrings.getUiBackground("MultiplayerScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.topTable.background = skinStrings.getUiBackground("MultiplayerScreen/TopTable", tintColor = skinStrings.skinConfig.clearColor)
    }

    private fun onGameDeleted(gameName:String){
        if (selectedGame?.name == gameName) unselectGame()
        gameList.update()
    }

    private fun setupRightSideButton() {
        rightSideButton.setText("Join game".tr())
        rightSideButton.onClick {
            val missingMods = selectedGame!!.preview!!.gameParameters.getModsAndBaseRuleset()
                .filter { !RulesetCache.containsKey(it) }
            if (missingMods.isEmpty()) return@onClick MultiplayerHelpers.loadMultiplayerGame(this, selectedGame!!)

            // Download missing mods
            Concurrency.runOnNonDaemonThreadPool(LoadGameScreen.downloadMissingMods) {
                try {
                    LoadGameScreen.loadMissingMods(missingMods, onModDownloaded = {
                        Concurrency.runOnGLThread { ToastPopup("[$it] Downloaded!", this@MultiplayerScreen) }
                    },
                    onCompleted = {
                        RulesetCache.loadRulesets()
                        Concurrency.runOnGLThread { MultiplayerHelpers.loadMultiplayerGame(this@MultiplayerScreen, selectedGame!!) }
                    })
                } catch (ex: Exception) {
                    val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                    launchOnGLThread { ToastPopup(message, this@MultiplayerScreen) }
                }
            }
        }
    }

    private fun getGeneralActionsTable(): Table {
        val generalActions = Table().apply { defaults().pad(10f) }
        generalActions.add(copyUserIdButton).row()
        generalActions.add(addGameButton).row()
        generalActions.add(friendsListButton).row()
        generalActions.add(refreshButton).row()
        return generalActions
    }

    private fun getGameSpecificActionsTable(): Table {
        val gameSpecificActions = Table().apply { defaults().pad(10f) }
        gameSpecificActions.add(copyGameIdButton).row()
        gameSpecificActions.add(renameButton).row()
        gameSpecificActions.add(skipTurnButton).row()
        gameSpecificActions.add(resignButton).row()
        gameSpecificActions.add(forceResignButton).row()
        gameSpecificActions.add(deleteButton).row()
        return gameSpecificActions
    }

    private fun createRefreshButton(): TextButton {
        val btn = "Refresh list".toTextButton()
        btn.onClick {
            Concurrency.run("Update all multiplayer games") {
                game.onlineMultiplayer.requestUpdate()
            }
        }
        return btn
    }

    private fun createAddGameButton(): TextButton {
        val btn = "Add multiplayer game".toTextButton()
        btn.onClick {
            game.pushScreen(AddMultiplayerGameScreen(this))
        }
        return btn
    }

    private fun createResignButton(): TextButton {
        val negativeButtonStyle = skin.get("negative", TextButton.TextButtonStyle::class.java)
        val resignButton = "Resign".toTextButton(negativeButtonStyle).apply { disable() }
        resignButton.onClick {
            val civName = selectedGame!!.preview!!.currentPlayer
            val askPopup = ConfirmPopup(
                    this,
                    "Are you sure you ([$civName]) want to resign?",
                    "Resign",
            ) {
                resignPlayer(selectedGame!!, civName)
            }
            askPopup.open()
        }
        return resignButton
    }

    private fun createForceResignButton(): TextButton {
        val negativeButtonStyle = skin.get("negative", TextButton.TextButtonStyle::class.java)
        val resignButton = "Force current player to resign".toTextButton(negativeButtonStyle).apply { isVisible = false }
        resignButton.onClick {
            val currentPlayer = selectedGame!!.preview!!.currentPlayer
            val askPopup = ConfirmPopup(
                this,
                "Are you sure you want to force the current player ([$currentPlayer]) to resign?",
                "Yes",
            ) {
                resignPlayer(selectedGame!!, currentPlayer)
            }
            askPopup.open()
        }
        return resignButton
    }

    private fun createSkipTurnButton(): TextButton {
        val negativeButtonStyle = skin.get("negative", TextButton.TextButtonStyle::class.java)
        val skipTurnButton = "Skip turn of current player".toTextButton(negativeButtonStyle).apply { isVisible = false }
        skipTurnButton.onClick {
            val civName = selectedGame!!.preview!!.currentPlayer
            val askPopup = ConfirmPopup(
                this,
                "Are you sure you want to skip the turn of [$civName]?",
                "Yes",
            ) {
                skipCurrentPlayerTurn(selectedGame!!, civName)
            }
            askPopup.open()
        }
        return skipTurnButton
    }

    /**
     * Turns the current playerCiv into an AI civ and uploads the game afterwards.
     */
    private fun resignPlayer(multiplayerGamePreview: MultiplayerGamePreview, playerCiv: String) {
        //Create a popup
        val popup = Popup(this)
        popup.addGoodSizedLabel(Constants.working).row()
        popup.open()

        Concurrency.runOnNonDaemonThreadPool("Resign") {
            try {
                val errorMessage = game.onlineMultiplayer.resignPlayer(multiplayerGamePreview, playerCiv)

                launchOnGLThread {
                    if (errorMessage.isEmpty()) {
                        popup.close()
                    } else {
                        popup.reuseWith(errorMessage, true)
                    }
                }
            } catch (ex: Exception) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex)

                if (ex is MultiplayerAuthException) {
                    launchOnGLThread {
                        AuthPopup(this@MultiplayerScreen) { success ->
                            if (success) resignPlayer(multiplayerGamePreview, playerCiv)
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

    /**
     * Turns the current playerCiv into an AI civ and uploads the game afterwards.
     */
    private fun skipCurrentPlayerTurn(multiplayerGamePreview: MultiplayerGamePreview, playerToSkip: String) {
        //Create a popup
        val popup = Popup(this)
        popup.addGoodSizedLabel(Constants.working).row()
        popup.open()

        Concurrency.runOnNonDaemonThreadPool("Skip turn") {
            try {
                val skipTurnErrorMessage = game.onlineMultiplayer.skipCurrentPlayerTurn(multiplayerGamePreview, playerToSkip)

                launchOnGLThread {
                    if (skipTurnErrorMessage == null) {
                        popup.close()
                    } else {
                        popup.reuseWith(skipTurnErrorMessage, true)
                    }
                    gameList.update()
                }
            } catch (ex: Exception) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex)

                if (ex is MultiplayerAuthException) {
                    launchOnGLThread {
                        AuthPopup(this@MultiplayerScreen) { success ->
                            if (success) skipCurrentPlayerTurn(multiplayerGamePreview, playerToSkip)
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

    private fun createDeleteButton(): TextButton {
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

    private fun createRenameButton(): TextButton {
        val btn = "Rename".toTextButton().apply { disable() }
        btn.onClick {
            Popup(this).apply {
                val textField = UncivTextField("Game name", selectedGame!!.name)
                // slashes in mp names are interpreted as directory separators, so we don't allow them
                textField.textFieldFilter = UncivFiles.fileNameTextFieldFilter()
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

    private fun createCopyGameIdButton(): TextButton {
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

    private fun createFriendsListButton(): TextButton {
        val btn = "Friends list".toTextButton()
        btn.onClick {
            game.pushScreen(ViewFriendsListScreen())
        }
        return btn
    }

    private fun createCopyUserIdButton(): TextButton {
        val btn = "Copy user ID".toTextButton()
        btn.onClick {
            Gdx.app.clipboard.contents = game.settings.multiplayer.getUserId()
            ToastPopup("UserID copied to clipboard", this)
        }
        return btn
    }

    private fun createMainContent(): Table {
        val mainTable = Table()
        mainTable.add(ScrollPane(gameList).apply { setScrollingDisabled(true, false) }).center()
        mainTable.add(getGameSpecificActionsTable())
        mainTable.add(getGeneralActionsTable())
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
        skipTurnButton.isVisible = false
        forceResignButton.isVisible = false

        descriptionLabel.setText("")
    }

    private fun selectGame(name: String) {
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
            rightSideButton.enable()
        } else {
            copyGameIdButton.disable()
            rightSideButton.disable()
        }

        // is it our turn?
        resignButton.isEnabled = multiplayerGame.preview?.getCurrentPlayerCiv()?.playerId == game.settings.multiplayer.getUserId()

        val preview = multiplayerGame.preview
        // the latter checks if we are on the first turn on a new game, where the start time has not yet been set (default 0L)
        if (resignButton.isEnabled || preview == null || preview.currentTurnStartTime == 0L){
            skipTurnButton.isVisible = false
            forceResignButton.isVisible = false
        } else {
            val durationInactive = Duration.between(Instant.ofEpochMilli(preview.currentTurnStartTime), Instant.now())
            val weAreAPlayer = game.settings.multiplayer.getUserId() in preview.civilizations.map { it.playerId }
            skipTurnButton.isVisible = weAreAPlayer && durationInactive > Duration.ofMinutes(preview.gameParameters.minutesUntilSkipTurn.toLong())
            forceResignButton.isVisible = weAreAPlayer && durationInactive > Duration.ofDays(2)
        }
        
        descriptionLabel.setText(MultiplayerHelpers.buildDescriptionText(multiplayerGame))
    }
}
