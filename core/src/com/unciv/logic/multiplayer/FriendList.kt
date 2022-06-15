package com.unciv.logic.multiplayer

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

    fun add(friendName: String, playerID: String): ErrorType {
        for (index in friendList.indices) {
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

    fun edit(friend: Friend, name: String, playerID: String) {
        friendList.remove(friend)
        val editedFriend = Friend(name,playerID)
        friendList.add(editedFriend)
        settings.save()
    }

    fun delete(friend: Friend) {
        friendList.remove(friend)
        settings.save()
    }

    fun getFriendsList() = friendList

    fun isFriendNameInFriendList(name: String): ErrorType {
        return if (friendList.firstOrNull { it.name == name } != null ) {
            ErrorType.ALREADYINLIST
        } else {
            ErrorType.NOERROR
        }
    }

    fun isFriendIDInFriendList(id: String): ErrorType {
        return if (friendList.firstOrNull { it.playerID == id } != null ) {
            ErrorType.ALREADYINLIST
        } else {
            ErrorType.NOERROR
        }
    }

    fun getFriendById(id: String) = friendList.firstOrNull { it.playerID == id }

    fun getFriendByName(name: String) = friendList.firstOrNull { it.name == name }
}
