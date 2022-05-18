package com.unciv.ui.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json


class FriendList {
    private val friendsListFileName = "FriendsList.json"
    private val friendsListFileHandle = FileHandle(friendsListFileName)

    fun addNewFriend(friendName: String, playerID: String) {
        val friend = Friend(friendName, playerID)

        val json = Json()

        var friendList: List<*>

        friendList = json.fromJson(MutableList::class.java, Friend::class.java, friendsListFileHandle)

        friendList.plus(friend)

        val serializedFriendListWithNewFriendAdded: String = json.prettyPrint(friendList)

        friendsListFileHandle.writeString(serializedFriendListWithNewFriendAdded, false)
    }
}

class Friend(val name: String, val playerID: String)