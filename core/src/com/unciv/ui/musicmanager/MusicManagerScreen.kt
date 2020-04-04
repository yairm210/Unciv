package com.unciv.ui.musicmanager

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.unciv.UncivGame
import com.unciv.models.metadata.MusicDownloadInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick

class MusicDownloadScreen : CameraStageBaseScreen() {
    private val music: MusicDownloadInfo = MusicDownloadInfo.load(true)

    private val screenSplit = 0.9f
    private val infoPane = MusicMgrGroupPane(skin)
    private val pickButton = MusicMgrSelectButton(MusicMgrSelectButtonState.Disabled, skin)
    private val bottomPane = MusicMgrBottomPane(music.size > 1, pickButton, skin)
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
        pickButton.state = if (sel) MusicMgrSelectButtonState.Selected else MusicMgrSelectButtonState.Select
    }

    private fun changePage(delta: Int = 0) {
        if (music.size==0) return
        currentPage = (currentPage + delta + music.size) % music.size
        val musicGroup = music.groups[currentPage]
        pickButton.state = when {
            musicGroup.isPresent() -> MusicMgrSelectButtonState.Present
            musicGroup.selected -> MusicMgrSelectButtonState.Selected
            else -> MusicMgrSelectButtonState.Select
        }
        infoPane.show (musicGroup)
        infoPane.layout (stage.width, stage.height * screenSplit)
        bottomPane.layout(stage.width, stage.height * (1 - screenSplit))
    }
}

