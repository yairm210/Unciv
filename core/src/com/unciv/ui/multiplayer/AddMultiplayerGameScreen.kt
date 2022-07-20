package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.IdChecker
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
    init {
        val gameNameTextField = UncivTextField.create("Game name")
        val gameIDTextField = UncivTextField.create("GameID")
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
            game.popScreen()
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

            Concurrency.run("AddMultiplayerGame") {
                try {
                    game.onlineMultiplayer.addGame(gameIDTextField.text.trim(), gameNameTextField.text.trim())
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
}
