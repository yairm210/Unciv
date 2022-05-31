package com.unciv.ui.multiplayer

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.logic.multiplayer.FriendList
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class ViewFriendsListScreen(previousScreen: BaseScreen) : PickerScreen() {
    private val rightSideTable = Table()
    private val leftSideTable = Table()
    private var friendsTable = Table()

    private val addFriendButton = "Add friend".toTextButton()
    private val editFriendButton = "Edit friend".toTextButton()

    private val friendsList = FriendList().getFriendsList()
    private var listOfFriendsButtons = arrayListOf<TextButton>()

    private lateinit var selectedFriend: FriendList.Friend

    init {
        setDefaultCloseAction(previousScreen)

        //Help Button Setup
        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To add a friend, ask him to send you his player ID.").row()
            helpPopup.addGoodSizedLabel("Click the 'Add friend' button.").row()
            helpPopup.addGoodSizedLabel("Insert his player ID and a name for him.").row()
            helpPopup.addGoodSizedLabel("Then click the 'Add friend' button again.").row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("After that you will see him in your friends list.").row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("A new button will appear when creating a new").row()
            helpPopup.addGoodSizedLabel("multiplayer game, which allows you to select your friend.").row()
            helpPopup.addGoodSizedLabel("").row()

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

        rightSideTable.defaults().uniformX()
        rightSideTable.defaults().fillX()
        rightSideTable.defaults().pad(20.0f)

        addFriendButton.onClick {
            game.setScreen(AddFriendScreen(this))
        }
        rightSideTable.add(addFriendButton).padBottom(10f).row()

        editFriendButton.onClick {
            game.setScreen(EditFriendScreen(selectedFriend,this))
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
            friendsTable.add(listOfFriendsButtons[index]).padBottom(20f).row()
        }
        leftSideTable.clear()
        leftSideTable.add(friendsTable)
    }
}
