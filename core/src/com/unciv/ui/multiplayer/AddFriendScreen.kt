package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.IdChecker
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.*
import java.util.*

class AddFriendScreen(backScreen: ViewFriendsListScreen) : PickerScreen(){
    init {
        val friendNameTextField = TextField("", skin)
        val gameIDTextField = TextField("", skin)
        val pasteGameIDButton = "Paste playerID from clipboard".toTextButton()
        pasteGameIDButton.onClick {
            gameIDTextField.text = Gdx.app.clipboard.contents
        }

        topTable.add("PlayerID".toLabel()).row()
        val gameIDTable = Table()
        gameIDTable.add(gameIDTextField).pad(10f).width(2*stage.width/3 - pasteGameIDButton.width)
        gameIDTable.add(pasteGameIDButton)
        topTable.add(gameIDTable).padBottom(30f).row()

        topTable.add("Friend name".toLabel()).row()
        topTable.add(friendNameTextField).pad(10f).padBottom(30f).width(stage.width/2).row()

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            backScreen.game.setScreen(backScreen)
        }

        //RightSideButton Setup
        rightSideButton.setText("Add friend".tr())
        rightSideButton.enable()
        rightSideButton.onClick {

        }
    }
}
