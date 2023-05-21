package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import kotlinx.coroutines.delay
import java.util.UUID

class SocialMenuTable(
    private val base: BaseScreen,
    me: UUID,
    initialChatRoom: Triple<UUID, ChatRoomType, String>? = null,
    private val chatHeadingFilter: List<ChatRoomType> = listOf(),
    friendRequests: Boolean = true,
    maxChatHeight: Float = 0.8f * base.stage.height
): Table(BaseScreen.skin), Disposable {
    private val friendList = FriendListV2(
        base,
        me,
        requests = friendRequests,
        chat = { _, a, c -> startChatting(c, ChatRoomType.Friend, a.displayName) },
        edit = { f, a -> FriendListV2.showRemoveFriendshipPopup(f, a, base) }
    )
    private val chatContainer = Container<ChatTable>()
    private var lastSelectedChat: UUID? = null

    init {
        add(friendList).growX().minWidth(base.stage.width * 0.45f).padRight(5f)
        add(chatContainer).minWidth(base.stage.width * 0.45f).maxHeight(maxChatHeight).growX()
        Concurrency.run {
            while (stage == null) {
                delay(10)
            }
            InfoPopup.wrap(stage) { friendList.triggerUpdate() }
        }
        if (initialChatRoom != null) {
            startChatting(initialChatRoom.first, initialChatRoom.second, initialChatRoom.third)
        }
    }

    private fun startChatting(chatRoom: UUID, chatRoomType: ChatRoomType, name: String) {
        if (lastSelectedChat == chatRoom) {
            chatContainer.actor?.dispose()
            chatContainer.actor = null
            lastSelectedChat = null
            return
        }
        lastSelectedChat = chatRoom
        chatContainer.actor?.dispose()
        chatContainer.actor = ChatTable(
            ChatMessageList(chatRoomType in chatHeadingFilter, Pair(chatRoomType, name), chatRoom, base.game.onlineMultiplayer)
        ).apply { padLeft(15f) }
    }

    override fun dispose() {
        chatContainer.actor?.dispose()
        chatContainer.actor = null
    }
}
