package com.unciv.ui.multiplayer

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.YesNoPopup
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread

/** Subscreen of MultiplayerScreen to edit and delete saves
 * backScreen is used for getting back to the MultiplayerScreen so it doesn't have to be created over and over again */
class EditMultiplayerGameInfoScreen(val multiplayerGame: OnlineMultiplayerGame, backScreen: MultiplayerScreen) : PickerScreen() {
    init {
        val textField = TextField(multiplayerGame.name, skin)

        topTable.add("Rename".toLabel()).row()
        topTable.add(textField).pad(10f).padBottom(30f).width(stage.width / 2).row()

        val deleteButton = "Delete save".toTextButton()
        deleteButton.onClick {
            val askPopup = YesNoPopup("Are you sure you want to delete this map?", this) {
                try {
                    game.onlineMultiplayer.deleteGame(multiplayerGame)
                    game.setScreen(backScreen)
                } catch (ex: Exception) {
                    ToastPopup("Could not delete game!", this)
                }
            }
            askPopup.open()
        }.apply { color = Color.RED }

        val giveUpButton = "Resign".toTextButton()
        giveUpButton.onClick {
            val askPopup = YesNoPopup("Are you sure you want to resign?", this) {
                resign(multiplayerGame, backScreen)
            }
            askPopup.open()
        }
        giveUpButton.apply { color = Color.RED }

        topTable.add(deleteButton).pad(10f).row()
        topTable.add(giveUpButton)

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            backScreen.game.setScreen(backScreen)
        }

        //RightSideButton Setup
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            val newName = textField.text.trim()
            game.onlineMultiplayer.changeGameName(multiplayerGame, newName)
            backScreen.selectGame(newName)
            backScreen.game.setScreen(backScreen)
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
    private fun resign(multiplayerGame: OnlineMultiplayerGame, backScreen: MultiplayerScreen) {
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
                        //go back to the MultiplayerScreen
                        game.setScreen(backScreen)
                    }
                } else {
                    launchOnGLThread {
                        popup.reuseWith("You can only resign if it's your turn", true)
                    }
                }
            } catch (ex: Exception) {
                val message = MultiplayerHelpers.getLoadExceptionMessage(ex)
                launchOnGLThread {
                    popup.reuseWith(message, true)
                }
            }
        }
    }
}
