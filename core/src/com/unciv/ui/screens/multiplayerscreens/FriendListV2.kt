package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.multiplayer.apiv2.FriendRequestResponse
import com.unciv.logic.multiplayer.apiv2.FriendResponse
import com.unciv.ui.components.ArrowButton
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.ChatButton
import com.unciv.ui.components.CheckmarkButton
import com.unciv.ui.components.CloseButton
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.OptionsButton
import com.unciv.ui.components.SearchButton
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.multiplayerscreens.FriendListV2.Companion.showEditPopup
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import java.util.*

/**
 * A [Table] to display the user's friends as a convenient list
 *
 * Set [me] to the currently logged in user to correctly filter friend requests (if enabled).
 * Set [requests] to show friend requests with buttons to accept or deny the request at the
 * top. Use [chat] to show a button that opens a chat dialog with a single friend. Use
 * [select] to specify a callback that can be used to select a player by clicking a button
 * next to it. Use [edit] to specify a callback that can be used to edit a friend.
 * A sane default for this functionality is the [showEditPopup] function.
 * This table should be encapsulated into a [base]screen or pop-up containing one.
 */
internal class FriendListV2(
    private val base: BaseScreen,
    private val me: UUID,
    friends: List<FriendResponse> = listOf(),
    friendRequests: List<FriendRequestResponse> = listOf(),
    private val requests: Boolean = false,
    private val chat: Boolean = true,
    private val select: ((UUID) -> Unit)? = null,
    private val edit: ((UUID) -> Unit)? = null
) : Table() {
    init {
        recreate(friends, friendRequests)
    }

    /**
     * Trigger a background refresh of the friend lists and recreate the table
     *
     * Use [suppress] to avoid showing an [InfoPopup] for any failures.
     */
    fun triggerUpdate(suppress: Boolean = false) {
        Concurrency.run {
            if (suppress) {
                val friendInfo = base.game.onlineMultiplayer.api.friend.list(true)
                if (friendInfo != null) {
                    Concurrency.runOnGLThread {
                        recreate(friendInfo.first, friendInfo.second)
                    }
                }
            } else {
                InfoPopup.wrap(base.stage) {
                    val friendInfo = base.game.onlineMultiplayer.api.friend.list(false)
                    if (friendInfo != null) {
                        Concurrency.runOnGLThread {
                            recreate(friendInfo.first, friendInfo.second)
                        }
                    }
                }
            }
        }
    }

    /**
     * Recreate the table containing friends, requests and all those buttons
     */
    fun recreate(friends: List<FriendResponse>, friendRequests: List<FriendRequestResponse> = listOf()) {
        val body = Table()
        if (requests && friendRequests.isNotEmpty()) {
            body.add(getRequestTable(friendRequests)).padBottom(10f).row()
            body.addSeparatorVertical(Color.DARK_GRAY, 1f).padBottom(10f).row()
        }
        body.add(getFriendTable(friends))

        val scroll = AutoScrollPane(body, BaseScreen.skin)
        scroll.setScrollingDisabled(true, false)
        clearChildren()
        add(scroll)
        row()
    }

    /**
     * Construct the table containing friends
     */
    private fun getFriendTable(friends: List<FriendResponse>): Table {
        val table = Table(BaseScreen.skin)
        if (friends.isEmpty()) {
            table.add("You have no friends yet :/")
            return table
        }

        val width = 2 + (if (chat) 1 else 0) + (if (edit != null) 1 else 0) + (if (select != null) 1 else 0)
        table.add("Friends".toLabel(fontSize = Constants.headingFontSize)).colspan(width).padBottom(10f).row()

        for (friend in friends) {
            table.add("${friend.friend.displayName} (${friend.friend.username})").padBottom(5f)
            if (chat) {
                table.add(ChatButton().apply { onActivation { ToastPopup("Chatting is not implemented yet", stage).open(force = true) } }).padLeft(5f).padBottom(5f)
            }
            if (edit != null) {
                table.add(OptionsButton().apply { onActivation { (edit)(friend.friend.uuid ) } }).padLeft(5f).padBottom(5f)
            }
            if (select != null) {
                table.add(ArrowButton().apply { onActivation { (select)(friend.friend.uuid ) } }).padLeft(5f).padBottom(5f)
            }
            table.row()
        }
        return table
    }

    /**
     * Construct the table containing friend requests
     */
    private fun getRequestTable(friendRequests: List<FriendRequestResponse>): Table {
        val table = Table(BaseScreen.skin)
        table.add("Friend requests".toLabel(fontSize = Constants.headingFontSize)).colspan(3).padBottom(10f).row()

        val nameField = UncivTextField.create("Search player")
        val searchButton = SearchButton()
        searchButton.onActivation {
            Log.debug("Searching for player '%s'", nameField.text)
            Concurrency.run {
                val response = InfoPopup.wrap(base.stage) {
                    base.game.onlineMultiplayer.api.account.lookup(nameField.text)
                }
                if (response != null) {
                    Concurrency.runOnGLThread {
                        Log.debug("Looked up '%s' as '%s'", response.uuid, response.username)
                        ConfirmPopup(
                            base.stage,
                            "Do you want to send [${response.username}] a friend request?",
                            "Yes",
                            true
                        ) {
                            InfoPopup.load(base.stage) {
                                base.game.onlineMultiplayer.api.friend.request(response.uuid)
                                Concurrency.runOnGLThread {
                                    nameField.text = ""
                                }
                            }
                        }
                    }
                }
            }
        }

        searchButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        add(nameField).padLeft(5f).padRight(5f)
        add(searchButton).padRight(5f)
        row()

        for (request in friendRequests.filter { it.to.uuid == me }) {
            table.add("${request.from.displayName} (${request.from.username})").padBottom(5f)
            table.add(CheckmarkButton().apply { onActivation {
                InfoPopup.load(stage) {
                    base.game.onlineMultiplayer.api.friend.accept(request.uuid)
                    triggerUpdate()
                }
            } })
            table.add(CloseButton().apply { onActivation {
                InfoPopup.load(stage) {
                    base.game.onlineMultiplayer.api.friend.delete(request.uuid)
                }
            } })
            table.row()
        }
        return table
    }

    companion object {
        fun showEditPopup(friend: UUID, screen: BaseScreen) {
            val popup = ConfirmPopup(
                screen.stage,
                "Do you really want to remove [$friend] as friend?",
                "Yes",
                false
            ) {
                Log.debug("Unfriending with %s", friend)
                InfoPopup.load(screen.stage) {
                    screen.game.onlineMultiplayer.api.friend.delete(friend)
                    Concurrency.runOnGLThread {
                        ToastPopup("You removed [$friend] as friend", screen.stage)
                    }
                }
            }
            popup.open(true)
        }
    }

}
