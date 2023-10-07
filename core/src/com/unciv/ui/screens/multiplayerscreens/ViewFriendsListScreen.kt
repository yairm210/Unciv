package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.multiplayer.FriendList
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.popups.Popup
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

class ViewFriendsListScreen : PickerScreen() {
    private val rightSideTable = Table()
    private val leftSideTable = Table()
    private var friendsTable = Table()

    private val addFriendButton = "Add friend".toTextButton()
    private val editFriendButton = "Edit friend".toTextButton()

    private val friendsList = FriendList().getFriendsList()
    private var listOfFriendsButtons = arrayListOf<TextButton>()

    private lateinit var selectedFriend: FriendList.Friend

    init {
        setDefaultCloseAction()
        rightSideButton.remove()

        //Help Button Setup
        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To add a friend, ask him to send you his player ID.\nClick the 'Add friend' button.\nInsert his player ID and a name for him.\nThen click the 'Add friend' button again.\n\nAfter that you will see him in your friends list.\n\nA new button will appear when creating a new\nmultiplayer game, which allows you to select your friend.").row()

            helpPopup.addCloseButton()
            helpPopup.open()
        }
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)
        stage.addActor(tab)

        val mainTable = Table()
        mainTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).height(stage.height * 2 / 3)
        mainTable.add(rightSideTable)
        topTable.add(mainTable).row()
        scrollPane.setScrollingDisabled(false, true)

        rightSideTable.defaults().fillX()
        rightSideTable.defaults().pad(20.0f)

        addFriendButton.onClick {
            game.pushScreen(AddFriendScreen())
        }
        rightSideTable.add(addFriendButton).padBottom(10f).row()

        editFriendButton.onClick {
            game.pushScreen(EditFriendScreen(selectedFriend))
            editFriendButton.disable()
        }
        rightSideTable.add(editFriendButton).padBottom(30f).row()
        editFriendButton.disable()

        refreshFriendsList()
    }

    fun refreshFriendsList() {
        listOfFriendsButtons.clear()
        friendsTable.clear()

        for (index in friendsList.indices) {
            listOfFriendsButtons.add(friendsList[index].name.toTextButton())

            listOfFriendsButtons[index].onClick {
                selectedFriend = friendsList[index]
                editFriendButton.enable()
            }
            friendsTable.add(listOfFriendsButtons[index]).padBottom(10f).row()
        }
        leftSideTable.clear()
        leftSideTable.add(friendsTable)
    }
}
