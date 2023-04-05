package com.unciv.ui.components

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class RefreshButton(size: Float = Constants.headingFontSize.toFloat()): Button(BaseScreen.skin) {
    init {
        add(ImageGetter.getImage("OtherIcons/Loading").apply {
            setOrigin(Align.center)
            setSize(size)
        })
    }
}

class SearchButton(size: Float = Constants.headingFontSize.toFloat()): Button(BaseScreen.skin) {
    init {
        add(ImageGetter.getImage("OtherIcons/Search").apply {
            setOrigin(Align.center)
            setSize(size)
        })
    }
}

class ChatButton(size: Float = Constants.headingFontSize.toFloat()): Button(BaseScreen.skin) {
    init {
        add(ImageGetter.getImage("OtherIcons/DiplomacyW").apply {
            setOrigin(Align.center)
            setSize(size)
        })
    }
}

class MultiplayerButton(size: Float = Constants.headingFontSize.toFloat()): Button(BaseScreen.skin) {
    init {
        add(ImageGetter.getImage("OtherIcons/Multiplayer").apply {
            setOrigin(Align.center)
            setSize(size)
        })
    }
}

class NewButton(size: Float = Constants.headingFontSize.toFloat()): Button(BaseScreen.skin) {
    init {
        add(ImageGetter.getImage("OtherIcons/New").apply {
            setOrigin(Align.center)
            setSize(size)
        })
    }
}
