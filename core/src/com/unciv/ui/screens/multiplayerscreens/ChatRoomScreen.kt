package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.ChatMessage
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.*
import com.unciv.ui.components.AutoScrollPane as ScrollPane

class ChatRoomScreen(private val chatRoomID: Long) : PickerScreen() {

    private val messageTable = Table()

    private val events = EventBus.EventReceiver()  // listen for incoming chat messages in the current chat

    init {
        setDefaultCloseAction()

        scrollPane.setScrollingDisabled(false, true)
        topTable.add(ScrollPane(messageTable).apply { setScrollingDisabled(true, false) }).center()

        setupHelpButton()
        recreateMessageTable(listOf())

        rightSideButton.setText("New message".tr())
        rightSideButton.onClick {
            AskTextPopup(this, "Your new message", maxLength = 1024, actionOnOk = {
                Log.debug("Sending '$it' to room $chatRoomID")  // TODO: Implement this
            })
        }

        Concurrency.run {
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

        messageTable.add("Messages".toLabel()).center().row()
        messageTable.defaults().uniformX()
        messageTable.defaults().fillX()
        messageTable.defaults().pad(10.0f)

        messages.forEach {
            val text = "${it.sender.displayName} (${it.createdAt}):\n${it.message}"
            if (it.sender.uuid == game.onlineMultiplayer.user.uuid) {
                messageTable.add(text.toLabel()).right().row()
            } else {
                messageTable.add(text.toLabel()).left().row()
            }
        }
    }

    /**
     * Construct a help button
     */
    private fun setupHelpButton() {
        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("It would be nice if this screen was documented.").row()
            helpPopup.addCloseButton()
            helpPopup.open()
        }
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)
        stage.addActor(tab)
    }

}
