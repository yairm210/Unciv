package com.unciv.ui.components

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

open class SpecificButton(private val size: Float, private val path: String): Button(BaseScreen.skin) {
    init { create() }

    private fun create() {
        add(ImageGetter.getImage(path).apply {
            setOrigin(Align.center)
            setSize(size)
        })
    }
}

class RefreshButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Loading")
class SearchButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Search")
class ChatButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/DiplomacyW")
class CloseButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Close")
class MultiplayerButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Multiplayer")
class PencilButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Pencil")
class NewButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/New")
class ArrowButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/ArrowRight")
class CheckmarkButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Checkmark")
class OptionsButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Options")
class LockButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/LockSmall")
class SettingsButton(size: Float = Constants.headingFontSize.toFloat()): SpecificButton(size, "OtherIcons/Settings")
