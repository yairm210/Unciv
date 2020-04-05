package com.unciv.ui.musicmanager

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.unciv.UncivGame
import com.unciv.models.metadata.MusicDownloadGroup
import com.unciv.models.metadata.MusicDownloadInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick

class MusicManagerScreen : CameraStageBaseScreen() {
    private val music: MusicDownloadInfo = MusicDownloadInfo.load(true)

    private val screenSplit = 0.9f
    private val infoPane = MusicMgrGroupPane(skin)
    private val bottomPane = MusicMgrBottomPane(music.size > 1, skin)
    var currentPage = 0

    private val downloader = MusicMgrDownloader()

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }

        val scrollInfoPane = ScrollPane(infoPane)
        scrollInfoPane.setSize (stage.width, stage.height * screenSplit)

        bottomPane.closeButton.onClick { game.setWorldScreen(); dispose() }
        bottomPane.previousButton.onClick { changePage(-1) }
        bottomPane.pickButton.onClick { toggleSelected() }
        bottomPane.nextButton.onClick { changePage(1) }
        bottomPane.okButton.onClick {
            bottomPane.okButton.disable()
            downloader.queueDownloads(music)
            downloader.startDownload() {
                currentPage = -1
                changePage(0)
            }
        }

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
        bottomPane.pickButton.state = if (sel) MusicMgrSelectButtonState.Selected else MusicMgrSelectButtonState.Select
        if (music.anySelected()) bottomPane.okButton.enable() else bottomPane.okButton.disable()
    }

    private fun changePage(delta: Int = 0) {
        if (music.size==0) return
        if (currentPage == -1 && delta == 0) {
            val musicGroup = MusicDownloadGroup()
            musicGroup.title = "Download result"
            val messages = downloader.messages
            musicGroup.description = if (messages.size==0) "Success."
                else messages.joinToString { "\n" }
            infoPane.show (musicGroup)
        } else {
            currentPage = (currentPage + delta + music.size) % music.size
            val musicGroup = music.groups[currentPage]
            bottomPane.pickButton.state = when {
                musicGroup.isPresent() -> MusicMgrSelectButtonState.Present
                musicGroup.selected -> MusicMgrSelectButtonState.Selected
                else -> MusicMgrSelectButtonState.Select
            }
            infoPane.show (musicGroup)
        }
        infoPane.layout (stage.width, stage.height * screenSplit)
        bottomPane.layout(stage.width, stage.height * (1 - screenSplit))
    }
}

