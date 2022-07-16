package com.unciv.ui.multiplayer

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.unciv.logic.multiplayer.MultiplayerGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.ConfirmPopup
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

/** Subscreen of MultiplayerScreen to edit and delete saves
 * backScreen is used for getting back to the MultiplayerScreen so it doesn't have to be created over and over again */
class EditMultiplayerGameScreen(val multiplayerGame: MultiplayerGame) : PickerScreen() {
    private var serverData = multiplayerGame.serverData
    init {
        val textField = UncivTextField.create("Game name", multiplayerGame.name)

        topTable.add("Rename".toLabel()).row()
        topTable.add(textField).pad(10f).padBottom(30f).width(stage.width / 2).row()
        topTable.add(ServerInput(::serverData).standalone()).padBottom(30f).width(stage.width / 2).row()
        topTable.add(createDeleteButton()).pad(10f).row()
        topTable.add(createGiveUpButton())

        setupCloseButton()
        setupRightSideButton(textField)
    }

    private fun setupCloseButton() {
        closeButton.setText("Back".tr())
        closeButton.onClick {
            game.popScreen()
        }
    }

    private fun setupRightSideButton(textField: TextField) {
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            val newName = textField.text.trim()
            multiplayerGame.name = newName
            multiplayerGame.serverData = serverData
            val newScreen = game.popScreen()
            if (newScreen is MultiplayerScreen) {
                newScreen.selectGame(multiplayerGame)
            }
        }
    }

    private fun createGiveUpButton(): TextButton {
        val giveUpButton = "Resign".toTextButton("negative")
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
        return giveUpButton
    }

    private fun createDeleteButton(): Button {
        val deleteButton = "Delete save".toTextButton("negative")
        deleteButton.onClick {
            val askPopup = ConfirmPopup(
                this,
                "Are you sure you want to delete this save?",
                "Delete save",
            ) {
                try {
                    game.multiplayer.deleteGame(multiplayerGame)
                    game.popScreen()
                } catch (ex: Exception) {
                    ToastPopup("Could not delete game!", this)
                }
            }
            askPopup.open()
        }
        return deleteButton
    }

    /**
     * Helper function to decrease indentation
     * Turns the current playerCiv into an AI civ and uploads the game afterwards.
     */
    private fun resign(multiplayerGame: MultiplayerGame) {
        //Create a popup
        val popup = Popup(this)
        popup.addGoodSizedLabel("Working...").row()
        popup.open()

        Concurrency.runOnNonDaemonThreadPool("Resign") {
            try {
                val resignSuccess = game.multiplayer.resign(multiplayerGame)
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
