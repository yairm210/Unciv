package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.IdChecker
import com.unciv.logic.multiplayer.FriendList
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen

class EditFriendScreen(selectedFriend: FriendList.Friend) : PickerScreen() {
    init {
        val friendNameTextField = UncivTextField("Please input a name for your friend!", selectedFriend.name)
        val pastePlayerIDButton = "Player ID from clipboard".toTextButton()
        val playerIDTextField = UncivTextField("Please input a player ID for your friend!", selectedFriend.playerID)
        val deleteFriendButton = "Delete".toTextButton()
        val friendlist = FriendList()

        topTable.add("Friend name".toLabel()).row()
        topTable.add(friendNameTextField).pad(10f).padBottom(30f).width(stage.width/2).row()

        pastePlayerIDButton.onClick {
            playerIDTextField.text = Gdx.app.clipboard.contents
        }

        topTable.add("Player ID".toLabel()).row()
        val gameIDTable = Table()
        gameIDTable.add(playerIDTextField).pad(10f).width(2*stage.width/3 - pastePlayerIDButton.width)
        gameIDTable.add(pastePlayerIDButton)
        topTable.add(gameIDTable).padBottom(30f).row()

        deleteFriendButton.onClick {
            val askPopup = ConfirmPopup(
                this,
                "Are you sure you want to delete this friend?",
                "Delete"
            ) {
                friendlist.delete(selectedFriend)
                goBack()
            }
            askPopup.open()
        }.apply { color = Color.RED }
        topTable.add(deleteFriendButton)

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            goBack()
        }

        //RightSideButton Setup
        rightSideButton.setText("Save".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            // if no edits have been made, go back to friends list
            if (selectedFriend.name == friendNameTextField.text && selectedFriend.playerID == playerIDTextField.text) {
                goBack()
            }
            if (friendlist.isFriendNameInFriendList(friendNameTextField.text) == FriendList.ErrorType.ALREADYINLIST
                    && friendlist.isFriendIDInFriendList(playerIDTextField.text) == FriendList.ErrorType.ALREADYINLIST) {
                ToastPopup("Player name already used!", this)
                return@onClick
            }
            if (friendlist.isFriendIDInFriendList(playerIDTextField.text) == FriendList.ErrorType.ALREADYINLIST
                    && friendlist.isFriendNameInFriendList(friendNameTextField.text) == FriendList.ErrorType.ALREADYINLIST) {
                ToastPopup("Player ID already used!", this)
                return@onClick
            }
            if (!IdChecker.isValidPlayerUuid(playerIDTextField.text)) {
                ToastPopup("Player ID is incorrect", this)
                return@onClick
            }
            friendlist.edit(selectedFriend, friendNameTextField.text, playerIDTextField.text)
            goBack()
        }
    }
}

fun goBack() {
    val newScreen = UncivGame.Current.popScreen()
    if (newScreen is ViewFriendsListScreen) {
        newScreen.refreshFriendsList()
    }
}
