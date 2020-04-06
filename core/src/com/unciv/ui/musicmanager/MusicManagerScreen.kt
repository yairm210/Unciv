package com.unciv.ui.musicmanager

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.UncivGame
import com.unciv.models.metadata.MusicDownloadGroup
import com.unciv.models.metadata.MusicDownloadInfo
import com.unciv.ui.utils.CameraStageBaseScreen
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
        bottomPane.previousButton.onClick { updatePage(-1) }
        bottomPane.pickButton.onClick (this::toggleSelected)
        bottomPane.pickButton.addListener ( object : ActorGestureListener() {
            override fun longPress(actor: Actor?, x: Float, y: Float): Boolean {
                pickerLongClick()
                return true
            }
        } )
        bottomPane.nextButton.onClick { updatePage(1) }
        bottomPane.downloadButton.onClick (this::downloadButtonClick)

        val splitPane = SplitPane(scrollInfoPane, bottomPane, true, skin)
        splitPane.splitAmount = screenSplit
        splitPane.setFillParent(true)
        stage.addActor(splitPane)

        updatePage(0)
    }

    private fun downloadButtonClick() {
        when (bottomPane.downloadButton.state) {
            DownloadButtonState.Disabled -> Unit
            DownloadButtonState.Stopped -> {
                bottomPane.downloadButton.state = DownloadButtonState.Running
                bottomPane.leftLabel.setText("")
                bottomPane.rightLabel.setText("")
                downloader.queueDownloads(music)
                downloader.startDownload ({
                    if (it.failureCount>0)
                        bottomPane.leftLabel.setText("Downloads failed: ${it.failureCount}")
                    if (it.successCount>0)
                        bottomPane.rightLabel.setText("Downloads succeded: ${it.successCount}")
                    updatePage()
                }, {
                    music.setSelected(false)
                    currentPage = -1
                    bottomPane.downloadButton.state = DownloadButtonState.Disabled
                    updatePage()
                })
            }
            DownloadButtonState.Running -> {
                bottomPane.downloadButton.state = DownloadButtonState.Stopping
                bottomPane.downloadButton.clickListener.exit(InputEvent(),0f,0f,-1, Actor())
                downloader.stopDownload()
                updatePage()
            }
            else -> Unit
        }
    }

    private fun pickerLongClick() {
        if (music.size==0 || downloader.isRunning) return
        when (bottomPane.pickButton.state) {
            MusicMgrSelectButtonState.Select -> music.setSelected(true)
            MusicMgrSelectButtonState.Queued -> music.setSelected(false)
            else -> return
        }
        toggleSelected()
    }
    private fun toggleSelected() {
        if (music.size==0 || downloader.isRunning) return
        val sel = !music.groups[currentPage].selected
        music.groups[currentPage].selected = sel
        bottomPane.pickButton.state = if (sel) MusicMgrSelectButtonState.Queued else MusicMgrSelectButtonState.Select
        bottomPane.downloadButton.state = if (music.anySelected()) DownloadButtonState.Stopped else DownloadButtonState.Disabled
    }

    private fun addLongPress() {

    }

    private fun updatePage(delta: Int = 0) {
        if (music.size==0) return
        if (currentPage == -1 && delta == 0) {
            val musicGroup = MusicDownloadGroup()
            musicGroup.title = "Download result"
            val messages = downloader.messages
            musicGroup.description = messages.joinToString( "\n" )
            bottomPane.pickButton.state = MusicMgrSelectButtonState.Disabled
            // ugly hack as the page switch did move the button from under the mouse and the isOver() state stuck
            // the dummy InputEvent and Actor are not used by the implementation.
            bottomPane.downloadButton.clickListener.exit(InputEvent(),0f,0f,-1, Actor())
            infoPane.show (musicGroup)
        } else {
            currentPage = ((if (currentPage==-1) 0 else currentPage) + delta + music.size) % music.size
            val musicGroup = music.groups[currentPage]
            bottomPane.pickButton.state = when {
                downloader.isRunning -> MusicMgrSelectButtonState.Disabled
                musicGroup.isPresent() -> MusicMgrSelectButtonState.Present
                musicGroup.selected -> MusicMgrSelectButtonState.Queued
                else -> MusicMgrSelectButtonState.Select
            }
            infoPane.show (musicGroup)
        }
        infoPane.layout (stage.width, stage.height * screenSplit)
        bottomPane.layout(stage.width, stage.height * (1 - screenSplit))
    }
}

