package com.unciv.ui.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.json.fromJsonFile
import com.unciv.json.json

class FriendList {
    private val friendsListFileName = "FriendsList.json"
    private val friendsListFileHandle = FileHandle(friendsListFileName)
    var friendList: MutableList<Friend> = mutableListOf()
    var addFriendErrorType = ""

    companion object {
        private const val friendsListFileName = "FriendsList.json"
        private fun friendsListFileHandle() = FileHandle(friendsListFileName)
    }

    data class Friend(val name: String, val playerID: String) {
        constructor() : this("", "")
    }

    fun addNewFriend(friendName: String, playerID: String) {
        load()
        addFriendErrorType = ""
        for(index in friendList.indices){
            if (friendList[index].name == friendName) {
                addFriendErrorType = "name"
            } else if (friendList[index].playerID == playerID) {
                addFriendErrorType = "id"
            } else if (friendName == "") {
                addFriendErrorType = "noName"
            } else if (playerID == "") {
                addFriendErrorType = "noID"
            }
        }
        if (addFriendErrorType == ""){
            friendList.add(Friend(friendName, playerID))
        }
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
            friendList.clear()
            friendList.addAll(newFriends)
        }
    }

    fun editFriend(friend: Friend, name: String, playerID: String) {
        load()
        friendList.remove(friend)
        val editedFriend = Friend(name,playerID)
        friendList.add(editedFriend)
        save()
    }

    fun deleteFriend(friend: Friend) {
        load()
        friendList.remove(friend)
        save()
    }

    fun getFriendsList(): MutableList<Friend> {
        load()
        return friendList
    }
}
