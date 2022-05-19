package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.ui.multiplayer.FriendList
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane


class ViewFriendsListScreen(previousScreen: BaseScreen) : PickerScreen() {
    private val rightSideTable = Table()
    private val leftSideTable = Table()

    private val addFriendText = "Add friend"
    private val refreshText = "Refresh"

    private val addFriendButton = addFriendText.toTextButton()
    private val refreshButton = refreshText.toTextButton()

    val friendsList = FriendList()
    //val listOfFriends = friendsList.getFriendsList()
    var listOfFriends: MutableList<FriendList.Friend> = mutableListOf()
    var listOfFriendsButtons = arrayListOf<TextButton>()

    init {
        setDefaultCloseAction(previousScreen)

        //Help Button Setup
        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To add a friend, ask him to send you his playerID.").row()
            helpPopup.addGoodSizedLabel("Then click the 'Add friend' button.").row()
            helpPopup.addGoodSizedLabel("Insert his playerID, enter a name for him and click the 'Add friend' button").row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("After that you will see him in your friends list").row()

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
        rightSideTable.add(addFriendButton).padBottom(30f).row()

        refreshButton.onClick {
            leftSideTable.reset()
            friendsList.load()
            listOfFriends = friendsList.getFriendsList()
            for (index in listOfFriends.indices) {
                listOfFriendsButtons.add(listOfFriends[index].name.toTextButton())

                listOfFriendsButtons[index].onClick {

                }
                leftSideTable.add(listOfFriendsButtons[index]).padBottom(20f).row()
            }
        }
        rightSideTable.add(refreshButton).padBottom(30f).row()

        friendsList.load()
        listOfFriends = friendsList.getFriendsList()
        for (index in listOfFriends.indices) {
            listOfFriendsButtons.add(listOfFriends[index].name.toTextButton())

            listOfFriendsButtons[index].onClick {

            }
            leftSideTable.add(listOfFriendsButtons[index]).padBottom(20f).row()
        }
        println(listOfFriends)
//        println(listOfFriendsButtons)
    }
}
