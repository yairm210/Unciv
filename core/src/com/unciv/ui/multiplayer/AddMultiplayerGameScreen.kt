package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.IdChecker
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.Multiplayer.ServerData
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import java.util.*

class AddMultiplayerGameScreen : PickerScreen() {
    var serverData: ServerData = ServerData.default

    init {
        topTable.add("GameID".toLabel()).row()
        val gameIDTextField = UncivTextField.create("GameID")
        val gameIDTable = createGameIdTable(gameIDTextField)
        topTable.add(gameIDTable).padBottom(30f).row()

        topTable.add("Game name".toLabel()).row()
        val gameNameTextField = UncivTextField.create("Game name")
        topTable.add(gameNameTextField).pad(10f).padBottom(30f).width(stage.width / 2).row()

        topTable.add(ServerInput(::serverData).standalone()).width(stage.width / 2).row()

        setupCloseButton()
        setupRightSideButton(gameIDTextField, gameNameTextField)
    }

    private fun createGameIdTable(
        gameIDTextField: TextField
    ): Table {
        val pasteGameIDButton = createPasteButton(gameIDTextField)
        val gameIDTable = Table()
        gameIDTable.add(gameIDTextField).pad(10f).width(2 * stage.width / 3 - pasteGameIDButton.width)
        gameIDTable.add(pasteGameIDButton)
        return gameIDTable
    }

    private fun createPasteButton(gameIDTextField: TextField): TextButton {
        val pasteGameIDButton = "Paste gameID from clipboard".toTextButton()
        pasteGameIDButton.onClick {
            gameIDTextField.text = Gdx.app.clipboard.contents
        }
        return pasteGameIDButton
    }

    private fun setupRightSideButton(gameIDTextField: TextField, gameNameTextField: TextField) {
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            try {
                UUID.fromString(IdChecker.checkAndReturnGameUuid(gameIDTextField.text))
            } catch (ex: Exception) {
                ToastPopup("Invalid game ID!", this)
                return@onClick
            }

            val popup = Popup(this)
            popup.addGoodSizedLabel("Working...")
            popup.open()

            Concurrency.run("AddMultiplayerGame") {
                try {
                    val gameName = gameNameTextField.text.trim()
                    game.multiplayer.addGame(serverData, gameIDTextField.text.trim(), if (gameName.isBlank()) null else gameName)
                    launchOnGLThread {
                        popup.close()
                        game.popScreen()
                    }
                } catch (ex: Exception) {
                    val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                    launchOnGLThread {
                        popup.reuseWith(message, true)
                    }
                }
            }
        }
    }

    private fun setupCloseButton() {
        closeButton.setText("Back".tr())
        closeButton.onClick {
            game.popScreen()
        }
    }
}
