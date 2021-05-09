package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.IdChecker
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.ToastPopup
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import java.util.*

class AddMultiplayerGameScreen(backScreen: MultiplayerScreen) : PickerScreen(){
    init {
        val gameNameTextField = TextField("", skin)
        val gameIDTextField = TextField("", skin)
        val pasteGameIDButton = TextButton("Paste gameID from clipboard", skin)
        pasteGameIDButton.onClick {
            gameIDTextField.text = Gdx.app.clipboard.contents
        }

        topTable.add("GameID".toLabel()).row()
        val gameIDTable = Table()
        gameIDTable.add(gameIDTextField).pad(10f).width(2*stage.width/3 - pasteGameIDButton.width)
        gameIDTable.add(pasteGameIDButton)
        topTable.add(gameIDTable).padBottom(30f).row()

        topTable.add("Game name".toLabel()).row()
        topTable.add(gameNameTextField).pad(10f).padBottom(30f).width(stage.width/2).row()

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
            }catch (ex: Exception){
                ToastPopup("Invalid game ID!", this)
                return@onClick
            }

            backScreen.addMultiplayerGame(gameIDTextField.text.trim(), gameNameTextField.text.trim())
            backScreen.game.setScreen(backScreen)
        }
    }
}