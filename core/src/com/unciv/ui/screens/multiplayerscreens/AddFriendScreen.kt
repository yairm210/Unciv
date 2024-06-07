package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.IdChecker
import com.unciv.logic.multiplayer.FriendList
import com.unciv.models.translations.tr
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import java.util.UUID

class AddFriendScreen : PickerScreen() {
    init {
        val friendNameTextField = UncivTextField("Please input a name for your friend!")
        val pastePlayerIDButton = "Paste player ID from clipboard".toTextButton()
        val playerIDTextField = UncivTextField("Please input a player ID for your friend!")
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

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            UncivGame.Current.popScreen()
        }

        //RightSideButton Setup
        rightSideButton.setText("Add friend".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            try {
                UUID.fromString(IdChecker.checkAndReturnPlayerUuid(playerIDTextField.text))
            } catch (_: Exception) {
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
                    val newScreen = UncivGame.Current.popScreen()
                    if (newScreen is ViewFriendsListScreen) {
                        newScreen.refreshFriendsList()
                    }
                }
            }
        }
    }
}
