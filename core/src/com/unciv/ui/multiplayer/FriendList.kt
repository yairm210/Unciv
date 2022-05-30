package com.unciv.ui.multiplayer

import com.unciv.UncivGame

class FriendList {
    private val settings = UncivGame.Current.settings
    var friendList = settings.multiplayer.friendList

    enum class ErrorType {
        NOERROR,
        NAME,
        ID,
        NONAME,
        NOID,
        YOURSELF,
        ALREADYINLIST;
    }

    data class Friend(val name: String, val playerID: String) {
        constructor() : this("", "")
    }

    fun addNewFriend(friendName: String, playerID: String): ErrorType {
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
        settings.save()
        return ErrorType.NOERROR
    }

    fun editFriend(friend: Friend, name: String, playerID: String) {
        friendList.remove(friend)
        val editedFriend = Friend(name,playerID)
        friendList.add(editedFriend)
        settings.save()
    }

    fun deleteFriend(friend: Friend) {
        friendList.remove(friend)
        settings.save()
    }

    fun getFriendsList(): MutableList<Friend> {
        return friendList
    }

    fun isFriendNameInFriendList(name: String): ErrorType {
        for (index in friendList.indices) {
            if (name == friendList[index].name) {
                return ErrorType.ALREADYINLIST
            }
        }
        return ErrorType.NOERROR
    }

    fun isFriendIDInFriendList(id: String): ErrorType {
        for (index in friendList.indices) {
            if (id == friendList[index].playerID) {
                return ErrorType.ALREADYINLIST
            }
        }
        return ErrorType.NOERROR
    }

    fun getFriendWithId(id: String): Friend? {
        for (index in friendList.indices) {
            if (id == friendList[index].playerID) {
                return friendList[index]
            }
        }
        return null
    }
}
