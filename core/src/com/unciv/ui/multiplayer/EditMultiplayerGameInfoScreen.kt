package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerGameSaver
import com.unciv.ui.crashhandling.crashHandlingThread
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.YesNoPopup

/** Subscreen of MultiplayerScreen to edit and delete saves
* backScreen is used for getting back to the MultiplayerScreen so it doesn't have to be created over and over again */
class EditMultiplayerGameInfoScreen(val gameInfo: GameInfoPreview?, gameName: String, backScreen: MultiplayerScreen): PickerScreen(){
    init {
        val textField = TextField(gameName, skin)

        topTable.add("Rename".toLabel()).row()
        topTable.add(textField).pad(10f).padBottom(30f).width(stage.width/2).row()

        val deleteButton = "Delete save".toTextButton()
        deleteButton.onClick {
            val askPopup = YesNoPopup("Are you sure you want to delete this map?", {
                backScreen.removeMultiplayerGame(gameInfo, gameName)
                backScreen.game.setScreen(backScreen)
                backScreen.reloadGameListUI()
            }, this)
            askPopup.open()
        }.apply { color = Color.RED }

        val giveUpButton = "Resign".toTextButton()
        giveUpButton.onClick {
            val askPopup = YesNoPopup("Are you sure you want to resign?", {
                resign(gameInfo!!.gameId, gameName, backScreen)
            }, this)
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
            //remove the old game file
            backScreen.removeMultiplayerGame(gameInfo, gameName)
            //using addMultiplayerGame will download the game from Dropbox so the descriptionLabel displays the right things
            backScreen.addMultiplayerGame(gameInfo!!.gameId, textField.text)
            backScreen.game.setScreen(backScreen)
            backScreen.reloadGameListUI()
        }

        if (gameInfo == null){
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
    private fun resign(gameId: String, gameName: String, backScreen: MultiplayerScreen){
        //Create a popup
        val popup = Popup(this)
        popup.addGoodSizedLabel("Working...").row()
        popup.open()

        crashHandlingThread {
            try {
                //download to work with newest game state
                val gameInfo = OnlineMultiplayerGameSaver().tryDownloadGame(gameId)
                val playerCiv = gameInfo.currentPlayerCiv

                //only give up if it's the users turn
                //this ensures that no one can upload a newer game state while we try to give up
                if (playerCiv.playerId == game.settings.userId) {
                    //Set own civ info to AI
                    playerCiv.playerType = PlayerType.AI
                    playerCiv.playerId = ""

                    //call next turn so turn gets simulated by AI
                    gameInfo.nextTurn()

                    //Add notification so everyone knows what happened
                    //call for every civ cause AI players are skipped anyway
                    for (civ in gameInfo.civilizations) {
                        civ.addNotification("[${playerCiv.civName}] resigned and is now controlled by AI", playerCiv.civName)
                    }

                    //save game so multiplayer list stays up to date but do not override multiplayer settings
                    val updatedSave = this.gameInfo!!.updateCurrentTurn(gameInfo)
                    GameSaver.saveGame(updatedSave, gameName)
                    OnlineMultiplayerGameSaver().tryUploadGame(gameInfo, withPreview = true)

                    postCrashHandlingRunnable {
                        popup.close()
                        //go back to the MultiplayerScreen
                        backScreen.game.setScreen(backScreen)
                        backScreen.reloadGameListUI()
                    }
                } else {
                    postCrashHandlingRunnable {
                        popup.reuseWith("You can only resign if it's your turn", true)
                    }
                }
            } catch (ex: FileStorageRateLimitReached) {
                postCrashHandlingRunnable {
                    popup.reuseWith("Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds", true)
                }
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    popup.reuseWith("Could not upload game!", true)
                }
            }
        }
    }
}
