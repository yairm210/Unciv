package com.unciv.ui.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json

class FriendList {
    private val friendsListFileName = "FriendsList.json"
    private val friendsListFileHandle = FileHandle(friendsListFileName)
    var friendList: MutableList<Friend> = mutableListOf()

    enum class ErrorType {
        NOERROR,
        NAME,
        ID,
        NONAME,
        NOID,
        YOURSELF,
        ALREADYINLIST;
    }

    companion object {
        private const val friendsListFileName = "FriendsList.json"
        private fun friendsListFileHandle() = FileHandle(friendsListFileName)
    }

    data class Friend(val name: String, val playerID: String) {
        constructor() : this("", "")
    }

    fun addNewFriend(friendName: String, playerID: String): ErrorType {
        loadFriendsList()
        for(index in friendList.indices){
            if (friendList[index].name == friendName) {
                return ErrorType.NAME
            } else if (friendList[index].playerID == playerID) {
                return ErrorType.ID
            }
        }
        if (friendName == "") {
            return ErrorType.NONAME
        } else if (playerID == "") {
            return ErrorType.NOID
        } else if (playerID == UncivGame.Current.settings.multiplayer.userId) {
            return ErrorType.YOURSELF
        }
        friendList.add(Friend(friendName, playerID))
        saveFriendsList()
        return ErrorType.NOERROR
    }

    fun saveFriendsList() {
        json().toJson(friendList, friendsListFileHandle())
    }

    fun loadFriendsList() {
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
        loadFriendsList()
        friendList.remove(friend)
        val editedFriend = Friend(name,playerID)
        friendList.add(editedFriend)
        saveFriendsList()
    }

    fun deleteFriend(friend: Friend) {
        loadFriendsList()
        friendList.remove(friend)
        saveFriendsList()
    }

    fun getFriendsList(): MutableList<Friend> {
        loadFriendsList()
        return friendList
    }

    fun isFriendNameInFriendList(name: String): ErrorType {
        loadFriendsList()
        for (index in friendList.indices) {
            if (name == friendList[index].name) {
                return ErrorType.ALREADYINLIST
            }
        }
        return ErrorType.NOERROR
    }

    fun isFriendIDInFriendList(id: String): ErrorType {
        loadFriendsList()
        for (index in friendList.indices) {
            if (id == friendList[index].playerID) {
                return ErrorType.ALREADYINLIST
            }
        }
        return ErrorType.NOERROR
    }

    fun getFriendWithId(id: String): Friend? {
        loadFriendsList()
        for (index in friendList.indices) {
            if (id == friendList[index].playerID) {
                return friendList[index]
            }
        }
        return null
    }
}
