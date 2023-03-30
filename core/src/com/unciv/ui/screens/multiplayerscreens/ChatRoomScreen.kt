package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.ChatMessage
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import com.unciv.ui.components.AutoScrollPane as ScrollPane

class ChatRoomScreen(private val chatRoomUUID: UUID) : PickerScreen() {

    private val messageTable = Table()

    private val events = EventBus.EventReceiver()  // listen for incoming chat messages in the current chat

    init {
        setDefaultCloseAction()

        scrollPane.setScrollingDisabled(false, true)
        topTable.add(ScrollPane(messageTable).apply { setScrollingDisabled(true, false) }).center()

        setupTopButtons()
        recreateMessageTable(listOf())

        rightSideButton.setText("New message".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            val ask = AskTextPopup(this, "Your new message", maxLength = 1024, actionOnOk = {
                Log.debug("Sending '$it' to room $chatRoomUUID")  // TODO: Implement this
            })
            ask.open()
        }

        Concurrency.run {
            // TODO: Remove this workaround fix by implementing a serious API handler
            game.onlineMultiplayer.api.user = game.onlineMultiplayer.api.account.get()
            updateMessages()
        }
    }

    /**
     * Update the messages of the chat room by querying the server, then recreate the message table
     */
    private suspend fun updateMessages() {
        // TODO: Implement querying the server
        Concurrency.runOnGLThread {
            recreateMessageTable(listOf())
        }
    }

    /**
     * Recreate the central table of all available messages
     */
    private fun recreateMessageTable(messages: List<ChatMessage>) {
        messageTable.clear()
        if (messages.isEmpty()) {
            messageTable.add("No messages here yet".toLabel()).center().row()
            return
        }

        messageTable.add().minWidth(0.96f * stage.width).expandX().fillX().row()  // empty cell to make the table span the whole X screen
        messageTable.add("Messages".toLabel()).center().row()
        messageTable.defaults().uniformX()
        messageTable.defaults().pad(10.0f)

        messages.forEach {
            // This block splits the message by spaces to make it fit on the screen.
            // It might be inefficient but at least it works reliably for any amount of text.
            val msgList = ArrayList<String>()
            var currentLine = ""
            for (word in it.message.split(" ")) {
                currentLine = if (Label("$currentLine $word", skin).width < 0.7f * stage.width) {
                    if (currentLine == "") {
                        word
                    } else {
                        "$currentLine $word"
                    }
                } else {
                    msgList.add(currentLine)
                    word
                }
            }
            msgList.add(currentLine)

            val label = "${it.sender.displayName} [${it.sender.username}] (${it.createdAt}):\n${msgList.joinToString("\n")}".toLabel()
            // TODO: Maybe add a border around each label to differentiate between messages visually clearer
            if (it.sender.uuid == game.onlineMultiplayer.api.user!!.uuid) {
                messageTable.add(label).maxWidth(label.width).prefWidth(label.width).right().row()
            } else {
                messageTable.add(label).maxWidth(label.width).prefWidth(label.width).left().row()
            }
        }
    }

    /**
     * Construct two buttons for chat members and help
     */
    private fun setupTopButtons() {
        val padding = 8.0f
        val tab = Table()
        val membersButton = "Chat members".toTextButton()
        membersButton.onClick {
            ToastPopup("Chat member list is not implemented yet.", stage)  // TODO
        }
        membersButton.padRight(padding)
        tab.add(membersButton)

        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("It would be nice if this screen was documented.").row()
            helpPopup.addCloseButton()
            helpPopup.open()
        }
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width - membersButton.width)
        tab.y = (stage.height - helpButton.height)
        stage.addActor(tab)
    }

}
