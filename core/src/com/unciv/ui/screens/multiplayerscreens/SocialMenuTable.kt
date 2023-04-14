package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
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

    internal val friendList = FriendListV2(base, me, requests = true, chat = { startChatting(it) }, edit = { FriendListV2.showRemoveFriendshipPopup(it, base) })
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

    private fun startChatting(selectedFriend: UUID) {
        Log.debug("Opening chat dialog with friend %s", selectedFriend)
        // TODO: The UUID is the friend account, not the chat room!
        container.actor?.dispose()
        container.actor = ChatTable(ChatMessageList(selectedFriend, base.game.onlineMultiplayer), true)
    }

}
