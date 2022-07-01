package com.unciv.ui.multiplayer

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread

/** Subscreen of MultiplayerScreen to edit and delete saves
 * backScreen is used for getting back to the MultiplayerScreen so it doesn't have to be created over and over again */
class EditMultiplayerGameInfoScreen(val multiplayerGame: OnlineMultiplayerGame) : PickerScreen() {
    init {
        val textField = UncivTextField.create("Game name", multiplayerGame.name)

        topTable.add("Rename".toLabel()).row()
        topTable.add(textField).pad(10f).padBottom(30f).width(stage.width / 2).row()

        val negativeButtonStyle = skin.get("negative", TextButtonStyle::class.java)
        val deleteButton = "Delete save".toTextButton(negativeButtonStyle)
        deleteButton.onClick {
            val askPopup = ConfirmPopup(
                this,
                "Are you sure you want to delete this save?",
                "Delete save",
            ) {
                try {
                    game.onlineMultiplayer.deleteGame(multiplayerGame)
                    game.popScreen()
                } catch (ex: Exception) {
                    ToastPopup("Could not delete game!", this)
                }
            }
            askPopup.open()
        }

        val giveUpButton = "Resign".toTextButton(negativeButtonStyle)
        giveUpButton.onClick {
            val askPopup = ConfirmPopup(
                this,
                "Are you sure you want to resign?",
                "Resign",
            ) {
                resign(multiplayerGame)
            }
            askPopup.open()
        }

        topTable.add(deleteButton).pad(10f).row()
        topTable.add(giveUpButton)

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            game.popScreen()
        }

        //RightSideButton Setup
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            val newName = textField.text.trim()
            game.onlineMultiplayer.changeGameName(multiplayerGame, newName)
            val newScreen = game.popScreen()
            if (newScreen is MultiplayerScreen) {
                newScreen.selectGame(newName)
            }
        }

        if (multiplayerGame.preview == null) {
            textField.isDisabled = true
            textField.color = Color.GRAY
            rightSideButton.disable()
            giveUpButton.disable()
        }
    }

    /**
     * Helper function to decrease indentation
     * Turns the current playerCiv into an AI civ and uploads the game afterwards.
     */
    private fun resign(multiplayerGame: OnlineMultiplayerGame) {
        //Create a popup
        val popup = Popup(this)
        popup.addGoodSizedLabel("Working...").row()
        popup.open()

        Concurrency.runOnNonDaemonThreadPool("Resign") {
            try {
                val resignSuccess = game.onlineMultiplayer.resign(multiplayerGame)
                if (resignSuccess) {
                    launchOnGLThread {
                        popup.close()
                        game.popScreen()
                    }
                } else {
                    launchOnGLThread {
                        popup.reuseWith("You can only resign if it's your turn", true)
                    }
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
