package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.unciv.Constants
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.models.translations.tr
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.AuthPopup
import com.unciv.utils.Log
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

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
                    (game.popScreen() as? MultiplayerScreen)?.onGameDeleted(multiplayerGame.name)
                } catch (ex: Exception) {
                    Log.error("Could not delete game!", ex)
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
            game.onlineMultiplayer.changeGameName(multiplayerGame, newName) {
                val popup = Popup(this)
                popup.addGoodSizedLabel("Could not save game!")
                popup.addCloseButton()
                popup.open()
            }
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
        popup.addGoodSizedLabel(Constants.working).row()
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

                when (ex) {
                    is MultiplayerAuthException -> {
                        launchOnGLThread {
                            AuthPopup(this@EditMultiplayerGameInfoScreen) { success ->
                                if (success) resign(multiplayerGame)
                            }.open(true)
                        }
                        return@runOnNonDaemonThreadPool
                    }
                }

                launchOnGLThread {
                    popup.reuseWith(message, true)
                }
            }

        }
    }
}
