package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.event.EventBus
import com.unciv.models.translations.tr
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Log
import java.util.*
import kotlin.math.max

class ChatRoomScreen(private val chatRoomUUID: UUID) : PickerScreen() {

    private val messageTable = ChatMessageList(chatRoomUUID, game.onlineMultiplayer)

    private val events = EventBus.EventReceiver()  // listen for incoming chat messages in the current chat

    init {
        setDefaultCloseAction()

        scrollPane.setScrollingDisabled(false, true)
        topTable.add(AutoScrollPane(messageTable, skin).apply { setScrollingDisabled(true, false) }).center()

        setupTopButtons()

        rightSideButton.setText("New message".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            val ask = AskTextPopup(this, "Your new message", maxLength = 1024, actionOnOk = {
                Log.debug("Sending '$it' to room $chatRoomUUID")  // TODO: Implement this
            })
            ask.width = max(ask.width, stage.width / 1.5f)
            ask.open()
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

    override fun dispose() {
        messageTable.dispose()
        super.dispose()
    }

}
