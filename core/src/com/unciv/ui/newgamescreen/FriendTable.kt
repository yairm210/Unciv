package com.unciv.ui.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.civilopedia.FormattedLine.IconDisplay
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.multiplayer.FriendList
import com.unciv.ui.utils.*

class FriendTable(val friend: FriendList.Friend, width: Float, minHeight: Float)
    : Table(BaseScreen.skin) {
    val innerTable = Table()

    init {
        val innerColor = Color(1F,1F,1F,1F) //because 0xFFFFFFFF doesn't work for some reason
        val outerColor = Color(0x002042ff)
        val textBackgroundColor = Color(0x002042ff) // getBlue().lerp(Black,0.5).apply { a = 1 }
        val borderWidth = 5f
        val totalPadding = 10f + 4 * borderWidth // pad*2 + innerTable.pad*2 + borderTable.pad*2
        val internalWidth = width - totalPadding

        val titleTable = Table()
        titleTable.background = ImageGetter.getBackground(outerColor)

        val titleText = friend.name
        val friendDisplayNameMaxWidth = internalWidth - 70f // for the nation indicator with padding
        val friendDisplayLabel = WrappableLabel(titleText, friendDisplayNameMaxWidth, innerColor, Constants.headingFontSize)
        if (friendDisplayLabel.prefWidth > friendDisplayNameMaxWidth - 2f) {
            friendDisplayLabel.wrap = true
            titleTable.add(friendDisplayLabel).width(friendDisplayNameMaxWidth)
        } else {
            titleTable.add(friendDisplayLabel).align(Align.center).pad(10f,0f)
        }

        innerTable.add(titleTable).growX().fillY().row()

        innerTable.background = ImageGetter.getBackground(textBackgroundColor)
        add(innerTable).width(width).minHeight(minHeight - totalPadding)

        touchable = Touchable.enabled
        background = ImageGetter.getBackground(innerColor)
    }
}
