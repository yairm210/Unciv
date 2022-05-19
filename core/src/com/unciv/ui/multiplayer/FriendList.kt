package com.unciv.ui.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.json.fromJsonFile
import com.unciv.json.json


class FriendList {
    private val friendsListFileName = "FriendsList.json"
    private val friendsListFileHandle = FileHandle(friendsListFileName)
    var friendList: MutableList<Friend> = mutableListOf()

    companion object {
        private const val friendsListFileName = "FriendsList.json"
        private fun friendsListFileHandle() = FileHandle(friendsListFileName)
    }

    data class Friend(val name: String, val playerID: String) {
        constructor() : this("", "")
    }

    fun addNewFriend(friendName: String, playerID: String) {
        load()
        friendList.add(Friend(friendName, playerID))
        println(friendList)
        save()
    }

    fun save() {
        json().toJson(friendList, friendsListFileHandle())
    }

    fun load() {
        if(friendsListFileHandle.exists()){
            if (json().fromJsonFile(Array<Friend>::class.java, friendsListFileName) == null) {
                friendsListFileHandle.writeString("[]", false)
            }
            val newFriends = json().fromJsonFile(Array<Friend>::class.java, friendsListFileName)

            friendList.addAll(newFriends)
        }
    }

    fun getFriendsList(): MutableList<Friend> {
        return friendList
    }
}