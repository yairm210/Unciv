package com.unciv.logic.multiplayer

import com.unciv.UncivGame
import java.util.UUID

class FriendList {
    private val settings = UncivGame.Current.settings
    var listOfFriends = settings.multiplayer.friendList

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
        constructor(name: String, playerUUID: UUID) : this(name, playerUUID.toString())
    }

    fun add(friendName: String, playerID: String): ErrorType {
        for (index in listOfFriends.indices) {
            if (listOfFriends[index].name == friendName) {
                return ErrorType.NAME
            } else if (listOfFriends[index].playerID == playerID) {
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
        listOfFriends.add(Friend(friendName, playerID))
        settings.save()
        return ErrorType.NOERROR
    }

    fun edit(friend: Friend, name: String, playerID: String) {
        listOfFriends.remove(friend)
        val editedFriend = Friend(name,playerID)
        listOfFriends.add(editedFriend)
        settings.save()
    }

    fun delete(friend: Friend) {
        listOfFriends.remove(friend)
        settings.save()
    }

    fun getFriendsList() = listOfFriends

    fun isFriendNameInFriendList(name: String): ErrorType {
        return if (listOfFriends.firstOrNull { it.name == name } != null ) {
            ErrorType.ALREADYINLIST
        } else {
            ErrorType.NOERROR
        }
    }

    fun isFriendIDInFriendList(id: String): ErrorType {
        return if (listOfFriends.firstOrNull { it.playerID == id } != null ) {
            ErrorType.ALREADYINLIST
        } else {
            ErrorType.NOERROR
        }
    }

    fun getFriendById(id: String) = listOfFriends.firstOrNull { it.playerID == id }

    fun getFriendByName(name: String) = listOfFriends.firstOrNull { it.name == name }
}
