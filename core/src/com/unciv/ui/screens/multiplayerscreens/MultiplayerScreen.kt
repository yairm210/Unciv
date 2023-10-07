package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.MultiplayerGameDeleted
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.models.translations.tr
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

class MultiplayerScreen : PickerScreen() {
    private var selectedGame: OnlineMultiplayerGame? = null

    private val editButtonText = "Game settings"
    private val editButton = createEditButton()

    private val addGameText = "Add multiplayer game"
    private val addGameButton = createAddGameButton()

    private val copyGameIdText = "Copy game ID"
    private val copyGameIdButton = createCopyGameIdButton()

    private val copyUserIdText = "Copy user ID"
    private val copyUserIdButton = createCopyUserIdButton()

    private val friendsListText = "Friends list"
    private val friendsListButton = createFriendsListButton()

    private val refreshText = "Refresh list"
    private val refreshButton = createRefreshButton()

    private val rightSideTable = createRightSideTable()
    private val leftSideTable = GameList(::selectGame)

    private val events = EventBus.EventReceiver()

    init {
        setDefaultCloseAction()

        scrollPane.setScrollingDisabled(false, true)

        topTable.add(createMainContent()).row()

        setupHelpButton()

        setupRightSideButton()

        events.receive(MultiplayerGameDeleted::class, { it.name == selectedGame?.name }) {
            unselectGame()
        }

        game.onlineMultiplayer.requestUpdate()

        pickerPane.bottomTable.background = skinStrings.getUiBackground("MultiplayerScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.topTable.background = skinStrings.getUiBackground("MultiplayerScreen/TopTable", tintColor = skinStrings.skinConfig.clearColor)
    }

    private fun setupRightSideButton() {
        rightSideButton.setText("Join game".tr())
        rightSideButton.onClick { MultiplayerHelpers.loadMultiplayerGame(this, selectedGame!!) }
    }

    private fun createRightSideTable(): Table {
        val table = Table()
        table.defaults().uniformX()
        table.defaults().fillX()
        table.defaults().pad(10.0f)
        table.add(copyUserIdButton).padBottom(30f).row()
        table.add(copyGameIdButton).row()
        table.add(editButton).row()
        table.add(addGameButton).padBottom(30f).row()
        table.add(friendsListButton).padBottom(30f).row()
        table.add(refreshButton).row()
        return table
    }

    fun createRefreshButton(): TextButton {
        val btn = refreshText.toTextButton()
        btn.onClick { game.onlineMultiplayer.requestUpdate() }
        return btn
    }

    fun createAddGameButton(): TextButton {
        val btn = addGameText.toTextButton()
        btn.onClick {
            game.pushScreen(AddMultiplayerGameScreen())
        }
        return btn
    }

    fun createEditButton(): TextButton {
        val btn = editButtonText.toTextButton().apply { disable() }
        btn.onClick {
            game.pushScreen(EditMultiplayerGameInfoScreen(selectedGame!!))
        }
        return btn
    }

    fun createCopyGameIdButton(): TextButton {
        val btn = copyGameIdText.toTextButton().apply { disable() }
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
        val btn = friendsListText.toTextButton()
        btn.onClick {
            game.pushScreen(ViewFriendsListScreen())
        }
        return btn
    }

    private fun createCopyUserIdButton(): TextButton {
        val btn = copyUserIdText.toTextButton()
        btn.onClick {
            Gdx.app.clipboard.contents = game.settings.multiplayer.userId
            ToastPopup("UserID copied to clipboard", this)
        }
        return btn
    }

    private fun createMainContent(): Table {
        val mainTable = Table()
        mainTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).center()
        mainTable.add(rightSideTable)
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

        editButton.disable()
        copyGameIdButton.disable()
        rightSideButton.disable()
        descriptionLabel.setText("")
    }

    fun selectGame(name: String) {
        val multiplayerGame = game.onlineMultiplayer.getGameByName(name)
        if (multiplayerGame == null) {
            // Should never happen
            unselectGame()
            return
        }

        selectedGame = multiplayerGame

        if (multiplayerGame.preview != null) {
            copyGameIdButton.enable()
        } else {
            copyGameIdButton.disable()
        }
        editButton.enable()
        rightSideButton.enable()

        descriptionLabel.setText(MultiplayerHelpers.buildDescriptionText(multiplayerGame))
    }
}
