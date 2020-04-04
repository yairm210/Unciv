package com.unciv.ui.musicmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.MusicDownloadGroup
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class MusicMgrGroupPane(skin: Skin): Table(skin) {
    private val titleLabel = "".toLabel(Color.GOLDENROD, 24)
    private val descriptionLabel = Label("",skin)
    private val creditsLabel = Label("",skin)
    private val attributionBox = Table()
    private val attributionLabel = Label("",skin)
    private val coverHolder = Table()
    private val trackList = MusicMgrTrackList(skin)
    private var showingGroup = MusicDownloadGroup()

    init {
        defaults().pad(20f)
        val titleHolder = Table()
        titleHolder.add (titleLabel)
        add (titleHolder).left().growX().row()
        //coverHolder.background = ImageGetter.getBackground(Color.WHITE)
        add (coverHolder).left().growX().row()
        descriptionLabel.setWrap (true)
        add (descriptionLabel).left().growX().row()
        creditsLabel.setWrap (true)
        add (creditsLabel).left().growX().row()
        attributionLabel.setWrap (true)
        attributionBox.defaults().pad(16f,24f,16f,24f)
        attributionBox.left()
        attributionBox.background = ImageGetter.getBackground(Color.DARK_GRAY)
        attributionBox.add(attributionLabel).left().fill()
        add (attributionBox).left().growX().row()

        add (trackList)
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
        set(value) {
            attributionLabel.setText(value)
            attributionBox.isVisible = value.isNotEmpty()
        }

    fun show(musicGroup: MusicDownloadGroup) {
        showingGroup = musicGroup
        title = musicGroup.title
        description = musicGroup.description
        credits = musicGroup.credits
        linkToWeb(creditsLabel,extractLink(credits))
        attribution = musicGroup.attribution
        linkToWeb(attributionBox,extractLink(attribution))

        if (musicGroup.shouldDownloadCover()) {
            coverHolder.clear()
            musicGroup.downloadCover {
                ID, status, _ ->
                if (status in 200..299)
                    Gdx.app.postRunnable { setCover(ID) }
            }
        } else {
            setCover ()
        }
        trackList.show(musicGroup)
    }

    fun setCover(ID: String = "") {
        if (ID.isNotEmpty() && ID != showingGroup.coverLocal) return
        coverHolder.clear()
        if (showingGroup.isCoverCached()) {
            val coverImg = Image(showingGroup.getCachedCover())
            coverHolder.add(coverImg).size(360f).pad(5f)
        }
    }

    fun layout (width: Float, height: Float) {
        this.width = width
        this.height = height
        this.layout()
    }

    private fun extractLink (text: String): String {
        val r = Regex(""".*(?:\b|\n)(https?://[a-z0-9A-Z_+%?&=/.-]+)\b.*""", RegexOption.MULTILINE).matchEntire(text)
                ?: return ""
        return r.groups[1]?.value ?: ""
    }
    private fun linkToWeb(actor: Actor, link: String) {
        if (link.isEmpty()) {
            actor.touchable = Touchable.disabled
        } else {
            actor.onClick { Gdx.net.openURI(link) }
            actor.touchable = Touchable.enabled
        }
    }
}
