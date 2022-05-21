package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.*

class AddFriendScreen(backScreen: ViewFriendsListScreen) : PickerScreen(){
    init {
        val friendNameTextField = TextField("", skin)
        val pasteGameIDButton = "Paste playerID from clipboard".toTextButton()
        val playerIDTextField = TextField("", skin)
        val friendlist = FriendList()

        topTable.add("Friend name".toLabel()).row()
        topTable.add(friendNameTextField).pad(10f).padBottom(30f).width(stage.width/2).row()

        pasteGameIDButton.onClick {
            playerIDTextField.text = Gdx.app.clipboard.contents
        }

        topTable.add("PlayerID".toLabel()).row()
        val gameIDTable = Table()
        gameIDTable.add(playerIDTextField).pad(10f).width(2*stage.width/3 - pasteGameIDButton.width)
        gameIDTable.add(pasteGameIDButton)
        topTable.add(gameIDTable).padBottom(30f).row()

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            backScreen.game.setScreen(backScreen)
        }

        //RightSideButton Setup
        rightSideButton.setText("Add friend".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            friendlist.addNewFriend(friendNameTextField.text, playerIDTextField.text)
            if (friendlist.addFriendErrorType == "name") {
                ToastPopup("Friend name is already in your friends list!", this)
            } else if (friendlist.addFriendErrorType == "id") {
                ToastPopup("Player ID is already in your friends list!", this)
            } else if (friendlist.addFriendErrorType == "noName") {
                ToastPopup("You have to write a name for your friend!", this)
            } else if (friendlist.addFriendErrorType == "noID") {
                ToastPopup("You have to write an ID for your friend!", this)
            } else {
                backScreen.game.setScreen(backScreen)
                backScreen.refreshFriendsList()
            }
        }
    }
}
