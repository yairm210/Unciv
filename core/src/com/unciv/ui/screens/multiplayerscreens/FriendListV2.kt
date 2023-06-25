package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.ApiException
import com.unciv.logic.multiplayer.apiv2.ApiStatusCode
import com.unciv.logic.multiplayer.apiv2.FriendRequestResponse
import com.unciv.logic.multiplayer.apiv2.FriendResponse
import com.unciv.ui.components.ArrowButton
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.ChatButton
import com.unciv.ui.components.CheckmarkButton
import com.unciv.ui.components.CloseButton
import com.unciv.ui.components.OptionsButton
import com.unciv.ui.components.SearchButton
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.setFontSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.multiplayerscreens.FriendListV2.Companion.showRemoveFriendshipPopup
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import java.util.UUID

/**
 * A [Table] to display the user's friends as a convenient list
 *
 * Set [me] to the currently logged in user to correctly filter friend requests (if enabled).
 * Set [requests] to show friend requests with buttons to accept or deny the request at the
 * top. Use [chat] to specify a callback when a user wants to chat with someone. Use
 * [select] to specify a callback that can be used to select a player by clicking a button
 * next to it. Use [edit] to specify a callback that can be used to edit a friend.
 * [chat], [select] and [edit] receive the friendship [UUID] as well as the [AccountResponse]
 * of the friend. Only [chat] gets called with the UUID of the chat room as well.
 *
 * A sane default for the [edit] functionality is the [showRemoveFriendshipPopup] function.
 * This table should be encapsulated into a [base]screen or pop-up containing one.
 */
internal class FriendListV2(
    private val base: BaseScreen,
    private val me: UUID,
    friends: List<FriendResponse> = listOf(),
    friendRequests: List<FriendRequestResponse> = listOf(),
    val requests: Boolean = false,
    val chat: ((UUID, AccountResponse, UUID) -> Unit)? = null,
    val select: ((UUID, AccountResponse) -> Unit)? = null,
    val edit: ((UUID, AccountResponse) -> Unit)? = null
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
        if (requests) {
            body.add(getRequestTable(friendRequests)).padBottom(10f).growX().row()
            body.addSeparatorVertical(Color.DARK_GRAY, 1f).padBottom(10f).row()
        }
        body.add(getFriendTable(friends)).growX()

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

        val width = 2 + (if (chat != null) 1 else 0) + (if (edit != null) 1 else 0) + (if (select != null) 1 else 0)
        table.add("Friends".toLabel(fontSize = Constants.headingFontSize)).colspan(width).padBottom(10f).row()

        for (friend in friends) {
            table.add("${friend.friend.displayName} (${friend.friend.username})").padBottom(5f)
            if (chat != null) {
                table.add(ChatButton().apply { onActivation { (chat)(friend.uuid, friend.friend.to(), friend.chatUUID) } }).padLeft(5f).padBottom(5f)
            }
            if (edit != null) {
                table.add(OptionsButton().apply { onActivation { (edit)(friend.uuid, friend.friend.to()) } }).padLeft(5f).padBottom(5f)
            }
            if (select != null) {
                table.add(ArrowButton().apply { onActivation { (select)(friend.uuid, friend.friend.to()) } }).padLeft(5f).padBottom(5f)
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
            val searchString = nameField.text
            if (searchString == "") {
                return@onActivation
            }

            Log.debug("Searching for player '%s'", searchString)
            Concurrency.run {
                val response = InfoPopup.wrap(base.stage) {
                    try {
                        base.game.onlineMultiplayer.api.account.lookup(searchString)
                    } catch (exc: ApiException) {
                        if (exc.error.statusCode == ApiStatusCode.InvalidUsername) {
                            Concurrency.runOnGLThread {
                                ToastPopup("No player [$searchString] found", stage).open(force = true)
                            }
                            null
                        } else {
                            throw exc
                        }
                    }
                }
                if (response != null) {
                    Concurrency.runOnGLThread {
                        Log.debug("Looked up '%s' as '%s'", response.username, response.uuid)
                        if (response.uuid == me) {
                            InfoPopup(base.stage, "You can't request a friendship from yourself!").open()
                            return@runOnGLThread
                        }
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
                        }.open(force = true)
                    }
                }
            }
        }

        searchButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        val nameCell = table.add(nameField).padLeft(5f).padRight(5f).padBottom(15f).growX()
        if (friendRequests.isNotEmpty()) {
            nameCell.colspan(2)
        }
        table.add(searchButton).padBottom(15f)
        table.row()

        for (request in friendRequests.filter { it.to.uuid == me }) {
            table.add("${request.from.displayName} (${request.from.username})").padBottom(5f)
            table.add(CheckmarkButton().apply { onActivation {
                InfoPopup.load(stage) {
                    base.game.onlineMultiplayer.api.friend.accept(request.uuid)
                    triggerUpdate()
                }
            } }).padBottom(5f).padLeft(5f)
            table.add(CloseButton().apply { onActivation {
                InfoPopup.load(stage) {
                    base.game.onlineMultiplayer.api.friend.delete(request.uuid)
                    triggerUpdate()
                }
            } }).padBottom(5f).padLeft(5f)
            table.row()
        }

        if (friendRequests.any { it.from.uuid == me }) {
            table.addSeparator(color = Color.LIGHT_GRAY).pad(15f).row()
            val infoLine = Label("Awaiting response:", BaseScreen.skin)
            infoLine.setFontColor(Color.GRAY)
            infoLine.setFontSize(Constants.smallFontSize)
            table.add(infoLine).colspan(3).padBottom(5f).row()
        }
        for (request in friendRequests.filter { it.from.uuid == me }) {
            table.add("${request.to.displayName} (${request.to.username})").colspan(3).padBottom(5f).row()
        }
        return table
    }

    companion object {
        fun showRemoveFriendshipPopup(friendship: UUID, friend: AccountResponse, screen: BaseScreen) {
            val popup = ConfirmPopup(
                screen.stage,
                "Do you really want to remove [${friend.username}] as friend?",
                "Yes",
                false
            ) {
                Log.debug("Unfriending with %s (friendship UUID: %s)", friend.username, friendship)
                InfoPopup.load(screen.stage) {
                    screen.game.onlineMultiplayer.api.friend.delete(friendship)
                    Concurrency.runOnGLThread {
                        ToastPopup("You removed [${friend.username}] as friend", screen.stage)
                    }
                }
            }
            popup.open(true)
        }
    }

}
