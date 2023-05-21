package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.GameOverviewResponse
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.multiplayerscreens.ChatMessageList
import com.unciv.ui.screens.multiplayerscreens.ChatRoomType
import com.unciv.ui.screens.multiplayerscreens.ChatTable
import com.unciv.ui.screens.multiplayerscreens.FriendListV2
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.runBlocking
import java.util.UUID

class MultiplayerOverviewTable(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class MultiplayerTabPersistableData(
        // If the game is no APIv2 and no online multiplayer game, this values are just null
        private val chatRoomUUID: UUID? = null,
        val gameName: String,
        val chatMessageList: ChatMessageList? =
                if (chatRoomUUID == null) null
                else ChatMessageList(true, Pair(ChatRoomType.Game, gameName), chatRoomUUID, UncivGame.Current.onlineMultiplayer),
        val disposables: MutableList<Disposable> = mutableListOf()
    ) : EmpireOverviewTabPersistableData() {
        constructor(overview: GameOverviewResponse) : this(overview.chatRoomUUID, overview.name)
        override fun isEmpty() = false
    }

    override val persistableData = (persistedData as? MultiplayerTabPersistableData) ?: MultiplayerTabPersistableData(
        runBlocking {
            // This operation shouldn't fail; however, this wraps into an InfoPopup before crashing the game
            InfoPopup.wrap(overviewScreen.stage) {
                overviewScreen.game.onlineMultiplayer.api.game.head(UUID.fromString(gameInfo.gameId))
            }!!
        }
    )

    private val chatTable = if (persistableData.chatMessageList != null) ChatTable(
        persistableData.chatMessageList,
        useInputPopup = true
    ) else null

    private var friendContainer:  Container<FriendListV2>? = null
    private var chatContainer: Container<ChatTable>? = null
    private var friendCell: Cell<Container<FriendListV2>?>? = null
    private var chatCell: Cell<Container<ChatTable>?>? = null

    init {
        val tablePadding = 30f
        defaults().pad(tablePadding).top()

        friendContainer = Container()
        chatContainer = Container()
        chatContainer?.actor = chatTable
        persistableData.disposables.forEach { it.dispose() }
        persistableData.disposables.clear()

        friendCell = add(friendContainer).grow()
        chatCell = add(chatContainer).padLeft(15f).growX()

        // Detaching the creation of the friend list as well as the resizing of the cells
        // provides two advantages: the surrounding stage is known and the UI delay is reduced.
        // This assumes that networking is slower than the UI, otherwise it crashes with NPE.
        Concurrency.run {
            val me = overviewScreen.game.onlineMultiplayer.api.account.get()!!
            Concurrency.runOnGLThread {
                val friendList = FriendListV2(
                    overviewScreen,
                    me.uuid,
                    requests = true,
                    chat = { _, a, c -> startChatting(a, c) },
                    edit = { f, a -> FriendListV2.showRemoveFriendshipPopup(f, a, overviewScreen) }
                )
                friendList.triggerUpdate(true)
                friendContainer?.actor = friendList
                if (stage != null) {
                    friendCell?.prefWidth(stage.width * 0.3f)
                    chatCell?.prefWidth(stage.width * 0.7f)
                }
            }
        }
    }

    private fun startChatting(friend: AccountResponse, chatRoom: UUID) {
        Log.debug("Opening chat dialog with friend %s (room %s)", friend, chatRoom)
        val chatList = ChatMessageList(true, Pair(ChatRoomType.Friend, friend.displayName), chatRoom, overviewScreen.game.onlineMultiplayer)
        persistableData.disposables.add(chatList)
        chatContainer?.actor = ChatTable(
            chatList,
            useInputPopup = true
        )
    }
}
