package com.unciv.ui.musicmanager

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.toLabel

class MusicMgrBottomPane(enable: Boolean, skin: Skin): Table(skin) {
    val closeButton = TextButton("Close".tr(), skin)
    val previousButton = PreviousNextButton(true, enable)
    val pickButton = MusicMgrSelectButton(MusicMgrSelectButtonState.Disabled, skin)
    val nextButton = PreviousNextButton(false, enable)
    val downloadButton = DownloadButton(DownloadButtonState.Disabled, skin)
    val leftLabel = LockableLabel(skin)
    val rightLabel = LockableLabel(skin)

    init {
        defaults().pad(10f)
        add (closeButton).left()
        leftLabel.setAlignment(Align.left)
        add(leftLabel).growX()
        val centerPanel = HorizontalGroup()
        centerPanel.addActor(previousButton)
        centerPanel.addActor(pickButton)
        centerPanel.addActor(nextButton)
        add(centerPanel).center()
        rightLabel.setAlignment(Align.right)
        add(rightLabel).growX()
        add (downloadButton).right()
    }

    fun layout (width: Float, height: Float) {
        this.width = width
        this.height = height
        this.layout()
    }
}

class LockableLabel(skin: Skin): Label("", skin) {
    // Subclassed Label solely to stop them from pushing the center cell out of center when their text changes
    private var lockWidth = 0f
    init {
        var labelStyle = skin.get(Label.LabelStyle::class.java)
        labelStyle = Label.LabelStyle(labelStyle) // clone this to another
        labelStyle.fontColor = Color.SLATE
        this.style = labelStyle
    }
    override fun getPrefWidth(): Float {
        if (lockWidth>0f) return lockWidth
        if (width>0f) lockWidth = width
        return super.getPrefWidth()
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

enum class MusicMgrSelectButtonState { Disabled, Present, Select, Queued }

class MusicMgrSelectButton(initialState: MusicMgrSelectButtonState, skin: Skin): TextButton("", skin) {
    private val presentText = "Available".tr()
    private val selectText = "Select".tr()
    private val selectedText = "Queued".tr()

    override fun getPrefWidth(): Float {
        return 132f
    }

    var state: MusicMgrSelectButtonState = initialState
        set(value) {
            field = value
            when (value) {
                MusicMgrSelectButtonState.Disabled -> {
                    setText("")
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
                MusicMgrSelectButtonState.Queued -> {
                    setText(selectedText)
                    color = Color.GOLDENROD
                    isDisabled = false
                }
            }
            touchable = if (isDisabled) Touchable.disabled else Touchable.enabled
        }
}

enum class DownloadButtonState (val image: String, val enabled: Boolean) {
    Uninitialized ("", false),
    Disabled ("OtherIcons/Music Download", false),
    Stopped ("OtherIcons/Music Download", true),
    Stopping ("OtherIcons/Music Stop DL", false),
    Running ("OtherIcons/Music Stop DL", true)
}

class DownloadButton(initialState: DownloadButtonState, skin: Skin): Button(skin) {
    var state = initialState
        set (value) {
            lastState = field
            field = value
            setImage()
        }
    private var lastState: DownloadButtonState = DownloadButtonState.Uninitialized

    private val images = hashMapOf<DownloadButtonState, Image>()

    init {
        DownloadButtonState.values().filter { it.image.isNotEmpty() }.forEach {
            images[it] = ImageGetter.getImage(it.image)
        }
        setImage()
    }

    private fun setImage() {
        if (lastState!=state) {
            clearChildren()
            add(images[state]).size(30f).pad(2f,12f,2f,12f)
        }
        if (state.enabled) enable() else disable()
    }

//    fun toggleDL(): Boolean {
//        return when (state) {
//            DownloadButtonState.Stopped -> { state = DownloadButtonState.Running; true }
//            DownloadButtonState.Running -> { state = DownloadButtonState.Stopped; false }
//            else -> false
//        }
//    }
}
