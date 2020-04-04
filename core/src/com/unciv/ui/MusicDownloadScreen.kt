package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.metadata.MusicDownloadGroup
import com.unciv.models.metadata.MusicDownloadInfo
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import javax.swing.text.TabExpander

class MusicDownloadScreen : CameraStageBaseScreen() {
    private val music: MusicDownloadInfo = MusicDownloadInfo.load(true)

    private val screenSplit = 0.9f
    private val infoPane = MusicGroupPane(stage.width, skin)
    private val pickButton = SelectButton(SelectButtonState.Disabled, skin)
    private val bottomPane = BottomPane( stage.width, stage.height * (1 - screenSplit), music.size>1, pickButton, skin)
    var currentPage = 0

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }

        val scrollInfoPane = ScrollPane(infoPane)
        scrollInfoPane.setSize (stage.width, stage.height * screenSplit)

        pickButton.onClick { toggleSelected() }
        bottomPane.closeButton.onClick { game.setWorldScreen(); dispose() }
        bottomPane.previousButton.onClick { changePage(-1) }
        bottomPane.nextButton.onClick { changePage(1) }

        val splitPane = SplitPane(scrollInfoPane, bottomPane, true, skin)
        splitPane.splitAmount = screenSplit
        splitPane.setFillParent(true)
        stage.addActor(splitPane)

        changePage(0)
    }

    private fun toggleSelected() {
        if (music.size==0) return
        val sel = !music.groups[currentPage].selected
        music.groups[currentPage].selected = sel
        pickButton.state = if (sel) SelectButtonState.Selected else SelectButtonState.Select
    }

    private fun changePage(delta: Int = 0) {
        if (music.size==0) return
        currentPage = (currentPage + delta + music.size) % music.size
        val musicGroup = music.groups[currentPage]
        pickButton.state = when {
            musicGroup.isPresent() -> SelectButtonState.Present
            musicGroup.selected -> SelectButtonState.Selected
            else -> SelectButtonState.Select
        }
        infoPane.show(musicGroup)
    }
}

private class MusicGroupPane(width: Float, skin: Skin): Table(skin) {
    private val titleLabel = "".toLabel(Color.GOLDENROD, 24)
    private val descriptionLabel = Label("",skin)
    private val creditsLabel = Label("",skin)
    private val attributionLabel = Label("",skin)

    init {
        defaults().pad(20f)
        this.width = width
        val titleBox = Table()
        titleBox.add(titleLabel).center().growX().row()
        titleBox.width = width
        titleBox.pack()
        add(titleBox).row()
        descriptionLabel.setWrap(true)
        add(descriptionLabel).left().row()
        creditsLabel.setWrap(true)
        add(creditsLabel).row()
        attributionLabel.setWrap(true)
        val attributionBox = Table().apply { defaults().pad(5f) }
        attributionBox.add(attributionLabel).fill()
        attributionBox.pack()
        add(attributionBox.addBorder(2f, Color.GOLDENROD, true)).row()
    }

    var title: String
        get() = titleLabel.text.toString()
        set(value) { titleLabel.setText(value) }
    var description: String
        get() = descriptionLabel.text.toString()
        set(value) { descriptionLabel.setText(value) }
    var credits: String
        get() = creditsLabel.text.toString()
        set(value) { creditsLabel.setText(value) }
    var attribution: String
        get() = attributionLabel.text.toString()
        set(value) { attributionLabel.setText(value) }

    fun show(musicGroup: MusicDownloadGroup) {
        title = musicGroup.title
        description = musicGroup.description
        credits = musicGroup.credits
        attribution = musicGroup.attribution
    }
}

private class BottomPane(width: Float, height: Float, enable: Boolean, pickButton: SelectButton, skin: Skin): Table(skin) {
    val closeButton = TextButton("Close".tr(), skin)
    val previousButton = PreviousNextButton(true, enable)
    val nextButton = PreviousNextButton(false, enable)
    val okButton = TextButton("OK".tr(), skin)

    init {
        defaults().pad(10f)
        this.height = height
        this.width = width
        add (closeButton).left()
        val centerPanel = HorizontalGroup()
        centerPanel.addActor(previousButton)
        centerPanel.addActor(pickButton)
        centerPanel.addActor(nextButton)
        centerPanel.pack()
        add(centerPanel).center().fillX()
        add (okButton).right().row()
        pack()
    }
}

private class PreviousNextButton(val previous: Boolean, val enabled: Boolean): Table() {
    val image = ImageGetter.getImage("OtherIcons/BackArrow")

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

private enum class SelectButtonState { Disabled, Present, Select, Selected }

private class SelectButton(initialState: SelectButtonState, skin: Skin): TextButton("", skin) {
    private val presentText = "Present".tr()
    private val selectText = "Select".tr()
    private val selectedText = "Selected".tr()

    var state: SelectButtonState = initialState
        set(value) {
            field = value
            when (value) {
                SelectButtonState.Disabled -> {
                    setText("---")
                    color = Color.GRAY
                    isDisabled = true
                }
                SelectButtonState.Present -> {
                    setText(presentText)
                    color = Color.GREEN
                    isDisabled = true
                }
                SelectButtonState.Select -> {
                    setText(selectText)
                    color = Color.WHITE
                    isDisabled = false
                }
                SelectButtonState.Selected -> {
                    setText(selectedText)
                    color = Color.GOLDENROD
                    isDisabled = false
                }
            }
            touchable = if (isDisabled) Touchable.disabled else Touchable.enabled
        }
}