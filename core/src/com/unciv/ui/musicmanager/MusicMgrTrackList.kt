package com.unciv.ui.musicmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.MusicDownloadGroup
import com.unciv.models.metadata.MusicDownloadTrack
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.surroundWithCircle

class MusicMgrTrackList(skin: Skin): Table(skin) {
    init {
        defaults().pad(5f)
    }

    fun show (musicGroup: MusicDownloadGroup) {
        clear()
        musicGroup.tracks.forEach {
            add (TrackListRow(it, it.isPresent(), playClicked, skin)).pad(4f).row()
        }
    }

    private val playClicked: (TrackListRow, MusicDownloadTrack) -> Unit = fun (row: TrackListRow, track: MusicDownloadTrack) {
        this.cells.forEach {
            if (it.actor is TrackListRow) {
                val actor = (it.actor as TrackListRow)
                if (actor !== row && actor.playButton.state == TrackPlayButtonState.Playing)
                    actor.playButton.toggle()
            }
        }
        if (row.playButton.toggle())
            play(track.file())
        else
            stop()
    }

    // This is just a placeholder!!!! Meant to integrate into a game-wide music service
    companion object MusicController {
        private var music: Music? = null

        fun play(track: FileHandle?) {
            stop()
            if (track==null) return
            music = Gdx.audio.newMusic(track)
            music!!.volume = 0.6f
            music!!.play()
        }
        fun stop() {
            if (music !=null) {
                music!!.stop()
                music!!.dispose()
                music = null
            }
        }
    }
}

private class TrackListRow(musicTrack: MusicDownloadTrack, isPresent: Boolean, clickEvent: (TrackListRow, MusicDownloadTrack)->Unit, skin: Skin): Table(skin) {
    private val iconSize = 26f
    val playButton = TrackPlayButton(if(isPresent) TrackPlayButtonState.Stopped else TrackPlayButtonState.Disabled)

    init {
        val noteImage = ImageGetter.getImage("OtherIcons/Music Note").surroundWithCircle(iconSize)
        noteImage.circle.color = Color.SLATE
        add (noteImage).left()

        // Don't use String.toLabel() - no translation please
        var labelStyle = Label.LabelStyle(skin.get(Label.LabelStyle::class.java))
        labelStyle.fontColor = Color.LIGHT_GRAY
        add (Label(musicTrack.title, labelStyle)).pad(0f,16f,0f,16f)
        if (musicTrack.info.isNotEmpty()) {
            labelStyle = Label.LabelStyle(labelStyle)
            labelStyle.fontColor = Color.SLATE
            add (Label(musicTrack.info, labelStyle)).pad(0f,0f,0f,16f)
        }

        playButton.onClick { clickEvent(this,musicTrack) }
        add (playButton).right()
    }

}

private enum class TrackPlayButtonState (val image: String, val touchable: Touchable) {
    Uninitialized ("", Touchable.disabled),
    Disabled ("OtherIcons/Music Disabled", Touchable.disabled),
    Stopped ("OtherIcons/Music Play", Touchable.enabled),
    Playing ("OtherIcons/Music Stop", Touchable.enabled)
}

private class TrackPlayButton(initialState: TrackPlayButtonState): Table() {
    var state = initialState
        set (value) {
            lastState = field
            field = value
            setImage()
        }
    private var lastState: TrackPlayButtonState = TrackPlayButtonState.Uninitialized

    private val images = hashMapOf<TrackPlayButtonState, Image>()

    init {
        TrackPlayButtonState.values().filter { it.image.isNotEmpty() }.forEach {
            images[it] = ImageGetter.getImage(it.image)
        }
        setImage()
    }

    private fun setImage() {
        if (lastState!=state) {
            clearChildren()                 // Do not clear(): Removes listeners
            add(images[state]).size(26f)
        }
        touchable = state.touchable
    }

    fun toggle(): Boolean {
        return when (state) {
            TrackPlayButtonState.Stopped -> { state = TrackPlayButtonState.Playing; true }
            TrackPlayButtonState.Playing -> { state = TrackPlayButtonState.Stopped; false }
            else -> false
        }
    }
}

