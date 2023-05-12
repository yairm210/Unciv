package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.utils.Disposable
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import kotlinx.coroutines.runBlocking
import java.util.*

class SocialMenuScreen(me: UUID? = null, initialChatRoom: Triple<UUID, ChatRoomType, String>? = null) : PickerScreen(horizontally = true), Disposable {
    private val socialTable = SocialMenuTable(
        this,
        me ?: runBlocking { game.onlineMultiplayer.api.account.get()!!.uuid },
        initialChatRoom,
        listOf(ChatRoomType.Game)
    )
    private val helpButton = "Help".toTextButton().onClick {
        val helpPopup = Popup(this)
        helpPopup.addGoodSizedLabel("It would be nice if this screen was documented.").row()
        helpPopup.addCloseButton()
        helpPopup.open()
    }

    init {
        setDefaultCloseAction()
        topTable.add(socialTable)
        rightSideButton.setText("Members".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            InfoPopup(stage, "It would be nice to show the members of this chat room").open()
        }
        rightSideGroup.addActor(Container(helpButton).padRight(5f).padLeft(5f))
    }

    override fun dispose() {
        socialTable.dispose()
        super.dispose()
    }
}
