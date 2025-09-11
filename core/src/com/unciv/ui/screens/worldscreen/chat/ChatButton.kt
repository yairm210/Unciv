package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.chat.Message
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ChatButton(val worldScreen: WorldScreen) : IconTextButton(
    "Chat", ImageGetter.getImage("OtherIcons/Chat"), 23
), Disposable {
    val chat = ChatStore.getChatByGameId(worldScreen.gameInfo.gameId)

    init {
        width = 95f
        iconCell.pad(3f).center()

        if (!chat.read || ChatStore.hasGlobalMessage) {
            startFlashing()
        }

        onClick {
            stopFlashing()
            ChatPopup(chat, worldScreen).open()
        }

        refreshVisibility()
    }

    /**
     * Toggles [ChatButton] if needed and also starts or stops [ChatWebSocket] as required.
     */
    fun refreshVisibility() {
        isVisible = if (
            worldScreen.gameInfo.gameParameters.isOnlineMultiplayer &&
            UncivGame.Current.onlineMultiplayer.multiplayerServer.getFeatureSet().chatVersion > 0
        ) {
            ChatWebSocket.requestMessageSend(
                Message.Join(listOf(worldScreen.gameInfo.gameId)),
            )
            updatePosition()
            true
        } else {
            ChatWebSocket.stop()
            false
        }
    }

    fun updatePosition() = setPosition(
        worldScreen.techPolicyAndDiplomacy.x.coerceAtLeast(1f),
        worldScreen.techPolicyAndDiplomacy.y - height - 1f
    )

    private var flashJob: Job? = null

    fun startFlashing(targetColor: Color = Color.ORANGE, interval: Duration = 500.milliseconds) {
        flashJob?.cancel()
        flashJob = Concurrency.run("ChatButton color flash") {
            var isAlternatingColor = true
            while (true) {
                Gdx.app.postRunnable {
                    if (isAlternatingColor) {
                        icon?.color = targetColor
                        label.color = targetColor
                    } else {
                        icon?.color = fontColor
                        label.color = fontColor
                    }

                    isAlternatingColor = !isAlternatingColor
                }

                delay(interval)
            }
        }
    }

    private fun stopFlashing() {
        flashJob?.cancel()
        icon?.color = fontColor
        label.color = fontColor
    }

    override fun dispose() {
        flashJob?.cancel()
    }
}
