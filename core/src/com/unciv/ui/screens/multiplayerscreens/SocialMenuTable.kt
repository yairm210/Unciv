package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.delay
import java.util.*

class SocialMenuTable(
    private val base: BaseScreen,
    private val me: UUID
): Table(BaseScreen.skin) {

    internal val friendList = FriendListV2(base, me, requests = true, chat = { _, a, c -> startChatting(a, c) }, edit = { f, a -> FriendListV2.showRemoveFriendshipPopup(f, a, base) })
    private val container = Container<ChatTable>()

    init {
        add(friendList)
        add(container)
        Concurrency.run {
            while (stage == null) {
                delay(10)
            }
            InfoPopup.wrap(stage) { friendList.triggerUpdate() }
        }
    }

    private fun startChatting(friend: AccountResponse, chatRoom: UUID) {
        Log.debug("Opening chat dialog with friend %s (room %s)", friend, chatRoom)
        container.actor?.dispose()
        container.actor = ChatTable(ChatMessageList(chatRoom, base.game.onlineMultiplayer), true)
    }

}
