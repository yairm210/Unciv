package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.IdChecker
import com.unciv.logic.multiplayer.FriendList
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import java.util.*

class AddFriendScreen(backScreen: ViewFriendsListScreen) : PickerScreen() {
    init {
        val friendNameTextField = TextField("", skin)
        val pastePlayerIDButton = "Paste player ID from clipboard".toTextButton()
        val playerIDTextField = TextField("", skin)
        val friendlist = FriendList()

        topTable.add("Friend name".toLabel()).row()
        friendNameTextField.messageText = "Please input a name for your friend!".tr()
        topTable.add(friendNameTextField).pad(10f).padBottom(30f).width(stage.width/2).row()

        pastePlayerIDButton.onClick {
            playerIDTextField.text = Gdx.app.clipboard.contents
        }

        topTable.add("Player ID".toLabel()).row()
        val gameIDTable = Table()
        playerIDTextField.messageText = "Please input a player ID for your friend!".tr()
        gameIDTable.add(playerIDTextField).pad(10f).width(2*stage.width/3 - pastePlayerIDButton.width)
        gameIDTable.add(pastePlayerIDButton)
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
            try {
                UUID.fromString(IdChecker.checkAndReturnPlayerUuid(playerIDTextField.text))
            } catch (ex: Exception) {
                ToastPopup("Player ID is incorrect", this)
                return@onClick
            }

            when (friendlist.add(friendNameTextField.text, playerIDTextField.text)) {
                FriendList.ErrorType.NAME -> ToastPopup("Friend name is already in your friends list!", this)

                FriendList.ErrorType.ID -> ToastPopup("Player ID is already in your friends list!", this)

                FriendList.ErrorType.NONAME -> ToastPopup("You have to write a name for your friend!", this)

                FriendList.ErrorType.NOID -> ToastPopup("You have to write an ID for your friend!", this)

                FriendList.ErrorType.YOURSELF -> ToastPopup("You cannot add your own player ID in your friend list!", this)

                else -> {
                    backScreen.game.setScreen(backScreen)
                    backScreen.refreshFriendsList()
                }
            }
        }
    }
}
