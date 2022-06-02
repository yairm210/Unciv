package com.unciv.ui.multiplayer

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.FriendList
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick

class FriendPickerListList(
    onSelected: (String) -> Unit
) : VerticalGroup() {

    private val friendDisplays = mutableMapOf<String, FriendDisplay>()
    private val friendList = UncivGame.Current.settings.multiplayer.friendList

    init {
        padTop(10f)
        padBottom(10f)

        for (friend in friendList) {
            addFriend(friend, onSelected)
        }
    }

    private fun addFriend(friend: FriendList.Friend, onSelected: (String) -> Unit) {
        val friendDisplay = FriendDisplay(friend, onSelected)
        friendDisplays[friend.name] = friendDisplay
        addActor(friendDisplay)
        children.sort()
    }
}

private class FriendDisplay(
    friend: FriendList.Friend,
    private val onSelected: (String) -> Unit
) : Table(), Comparable<FriendDisplay> {
    var friendName: String = friend.name
        private set
    val friendButton = TextButton(friendName, BaseScreen.skin)

    init {
        padBottom(5f)

        add(friendButton)
        onClick { onSelected(friendName) }
    }

    fun changeName(newName: String) {
        friendName = newName
        friendButton.setText(newName)
    }

    override fun compareTo(other: FriendDisplay): Int = friendName.compareTo(other.friendName)
    override fun equals(other: Any?): Boolean = (other is FriendDisplay) && (friendName == other.friendName)
    override fun hashCode(): Int = friendName.hashCode()
}
