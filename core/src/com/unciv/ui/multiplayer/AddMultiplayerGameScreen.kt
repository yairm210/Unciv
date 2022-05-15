package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.IdChecker
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.*
import java.util.*

class AddMultiplayerGameScreen(backScreen: MultiplayerScreen) : PickerScreen() {
    init {
        val gameNameTextField = TextField("", skin)
        val gameIDTextField = TextField("", skin)
        val pasteGameIDButton = "Paste gameID from clipboard".toTextButton()
        pasteGameIDButton.onClick {
            gameIDTextField.text = Gdx.app.clipboard.contents
        }

        topTable.add("GameID".toLabel()).row()
        val gameIDTable = Table()
        gameIDTable.add(gameIDTextField).pad(10f).width(2 * stage.width / 3 - pasteGameIDButton.width)
        gameIDTable.add(pasteGameIDButton)
        topTable.add(gameIDTable).padBottom(30f).row()

        topTable.add("Game name".toLabel()).row()
        topTable.add(gameNameTextField).pad(10f).padBottom(30f).width(stage.width / 2).row()

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            backScreen.game.setScreen(backScreen)
        }

        //RightSideButton Setup
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

            launchCrashHandling("AddMultiplayerGame") {
                try {
                    game.onlineMultiplayer.addGame(gameIDTextField.text.trim(), gameNameTextField.text.trim())
                    postCrashHandlingRunnable {
                        popup.close()
                        game.setScreen(backScreen)
                    }
                } catch (ex: Exception) {
                    val message = backScreen.getLoadExceptionMessage(ex)
                    postCrashHandlingRunnable {
                        popup.reuseWith(message, true)
                    }
                }
            }
        }
    }
}
