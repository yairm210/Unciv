package com.unciv.ui.screens.worldscreen.mainmenu

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.ui.audio.MusicTrackInfo
import com.unciv.ui.components.extensions.MusicControls.addMusicControls
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import yairm210.purity.annotations.Readonly

class WorldScreenMusicPopup(
    worldScreen: WorldScreen
) : Popup(worldScreen, maxSizePercentage = calcSize(worldScreen)) {

    companion object {
        // 3/4 of the screen is just a bit too small on small screen settings
        @Readonly
        private fun calcSize(worldScreen: WorldScreen) = when(worldScreen.game.settings.screenSize) {
            ScreenSize.Tiny -> 0.95f
            ScreenSize.Small -> 0.85f
            else -> 0.75f
        }
    }

    private val musicController = UncivGame.Current.musicController
    private val trackStyle: TextButton.TextButtonStyle
    private val historyExpander: ExpanderTab
    private val visualMods = worldScreen.game.settings.visualMods
    private val mods = worldScreen.gameInfo.gameParameters.mods

    init {
        val settings = UncivGame.Current.settings

        getScrollPane()!!.setScrollingDisabled(true, false)
        defaults().fillX()

        val sk = BaseScreen.skinStrings

        // Make the list flat but with mouse-over highlighting and visual click-down feedback
        // by making them buttons instead of labels, but with a style that has no NinePatches
        // as backgrounds, just tinted whiteDot. As default, but skinnable.
        val up = sk.getUiBackground("WorldScreenMusicPopup/TrackList/Up", tintColor = skin.getColor("color"))
        val down = sk.getUiBackground("WorldScreenMusicPopup/TrackList/Down", tintColor = skin.getColor("positive"))
        val over = sk.getUiBackground("WorldScreenMusicPopup/TrackList/Over", tintColor = skin.getColor("highlight"))
        trackStyle = TextButton.TextButtonStyle(up, down, null, Fonts.font)
        trackStyle.over = over
        trackStyle.disabled = up
        trackStyle.disabledFontColor = Color.LIGHT_GRAY

        addMusicMods(settings)
        historyExpander = addHistory()
        bottomTable.addMusicControls(settings, musicController)
        addCloseButton().colspan(2)

        musicController.onChange {
            historyExpander.innerTable.clear()
            historyExpander.innerTable.updateTrackList(musicController.getHistory())
        }
    }

    private fun String.toSmallUntranslatedButton(rightSide: Boolean = false) =
        TextButton(this, trackStyle).apply {
            label.setFontScale(14 / Fonts.ORIGINAL_FONT_SIZE)
            label.setAlignment(if (rightSide) Align.right else Align.left)
            labelCell.pad(5f)
            isDisabled = rightSide
        }

    private fun addMusicMods(settings: GameSettings) {
        val modsToTracks = musicController.getAllMusicFileInfo().groupBy { it.mod }
        val collator = settings.getCollatorFromLocale()
        val modsSorted = modsToTracks.entries.asSequence()
            .sortedWith(compareBy(collator) { it.key })
        for ((modLabel, trackList) in modsSorted) {
            val tracksSorted = trackList.asSequence()
                .sortedWith(compareBy(collator) { it.track })
            addTrackList(modLabel.ifEmpty { "—Default—" }, tracksSorted)
        }
    }

    private fun addHistory() = addTrackList("—History—", musicController.getHistory())

    private fun addTrackList(title: String, tracks: Sequence<MusicTrackInfo>): ExpanderTab {
        // Note title is either a mod name or something that cannot be a mod name (thanks to the em-dashes)
        val icon = when (title) {
            in mods -> "OtherIcons/Mods"
            in visualMods -> "UnitPromotionIcons/Scouting"
            else -> null
        }?.let { ImageGetter.getImage(it).apply { setSize(18f) } }
        val expander = ExpanderTab(title, Constants.defaultFontSize, icon,
            startsOutOpened = false, defaultPad = 0f, headerPad = 5f,
            persistenceID = "MusicPopup.$title",
        ) {
            it.updateTrackList(tracks)
        }
        add(expander).colspan(2).growX().row()

        return expander
    }

    private fun Table.updateTrackList(tracks: Sequence<MusicTrackInfo>) {
        for (entry in tracks) {
            val trackLabel = entry.track.toSmallUntranslatedButton()
            trackLabel.onClick { musicController.startTrack(entry) }
            add(trackLabel).fillX()
            add(entry.type.toSmallUntranslatedButton(true)).right().row()
            // Note - displaying the file extension is meant as modder help, and could possibly
            // be extended to a modder tool - maybe display eligibility for known triggers?
            // Might also be gated by a setting so casual users won't see it?
        }
    }

}
