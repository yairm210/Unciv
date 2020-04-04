package com.unciv.ui.musicmanager

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter

class MusicMgrBottomPane(enable: Boolean, pickButton: MusicMgrSelectButton, skin: Skin): Table(skin) {
    val closeButton = TextButton("Close".tr(), skin)
    val previousButton = PreviousNextButton(true, enable)
    val nextButton = PreviousNextButton(false, enable)
    val okButton = Button(skin)

    init {
        defaults().pad(10f)
        add (closeButton).left()
        val centerPanel = HorizontalGroup()
        centerPanel.addActor(previousButton)
        centerPanel.addActor(pickButton)
        centerPanel.addActor(nextButton)
        centerPanel.pack()
        add(centerPanel).center()
        val okImage = ImageGetter.getImage("OtherIcons/Music Download")
        okButton.add(okImage).size(28f).pad(2f,12f,2f,12f)
        add (okButton).right()
    }

    fun layout (width: Float, height: Float) {
        this.width = width
        this.height = height
        //this.setFillParent(true)
        this.layout()
    }
}

class PreviousNextButton(val previous: Boolean, val enabled: Boolean): Table() {
    private val image = ImageGetter.getImage("OtherIcons/BackArrow")

    init {
        val imageSize = 25f
        if (!previous) {
            image.setSize(imageSize, imageSize)
            image.setOrigin(Align.center)
            image.rotateBy(180f)
        }
        add(image).size(imageSize).pad(5f, 20f, 5f, 20f)
        if (enabled) enable() else disable()
    }
    fun enable(){
        image.color = Color.WHITE
        touchable = Touchable.enabled
    }
    fun disable(){
        image.color = Color.GRAY
        touchable = Touchable.disabled
    }
}

enum class MusicMgrSelectButtonState { Disabled, Present, Select, Selected }

class MusicMgrSelectButton(initialState: MusicMgrSelectButtonState, skin: Skin): TextButton("", skin) {
    private val presentText = "Available".tr()
    private val selectText = "Select".tr()
    private val selectedText = "Queued".tr()

    var state: MusicMgrSelectButtonState = initialState
        set(value) {
            field = value
            when (value) {
                MusicMgrSelectButtonState.Disabled -> {
                    setText("---")
                    color = Color.GRAY
                    isDisabled = true
                }
                MusicMgrSelectButtonState.Present -> {
                    setText(presentText)
                    color = Color.GREEN
                    isDisabled = true
                }
                MusicMgrSelectButtonState.Select -> {
                    setText(selectText)
                    color = Color.WHITE
                    isDisabled = false
                }
                MusicMgrSelectButtonState.Selected -> {
                    setText(selectedText)
                    color = Color.GOLDENROD
                    isDisabled = false
                }
            }
            touchable = if (isDisabled) Touchable.disabled else Touchable.enabled
        }
}
