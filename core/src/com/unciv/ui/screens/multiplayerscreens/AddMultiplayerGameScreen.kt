package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.IdChecker
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.isUUID
import com.unciv.utils.launchOnGLThread

class AddMultiplayerGameScreen(multiplayerScreen: MultiplayerScreen) : PickerScreen() {
    init {
        val gameNameTextField = UncivTextField("Game name")
        val gameIDTextField = UncivTextField("GameID")
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
        setDefaultCloseAction()

        //RightSideButton Setup
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        rightSideButton.onActivation {
            if (!IdChecker.checkAndReturnGameUuid(gameIDTextField.text).isUUID()) {
                ToastPopup("Invalid game ID!", this)
                return@onActivation
            }

            val popup = Popup(this)
            popup.addGoodSizedLabel(Constants.working)
            popup.open()

            Concurrency.run("AddMultiplayerGame") {
                try {
                    game.onlineMultiplayer.addGame(gameIDTextField.text.trim(), gameNameTextField.text.trim())
                    launchOnGLThread {
                        popup.close()
                        game.popScreen()
                        multiplayerScreen.gameList.update()
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
