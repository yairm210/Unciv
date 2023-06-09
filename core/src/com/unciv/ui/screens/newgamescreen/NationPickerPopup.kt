package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings.NationPickerListMode
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.isNarrowerThan4to3
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.onDoubleClick
import com.unciv.ui.components.extensions.toImageButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.PI
import kotlin.math.cos

internal class NationPickerPopup(
    private val playerPicker: PlayerPickerTable,
    private val player: Player,
    private val noRandom: Boolean
) : Popup(playerPicker.previousScreen as BaseScreen, Scrollability.None) {
    companion object {
        // These are used for the Close/OK buttons in the lower left/right corners:
        const val buttonsCircleSize = 70f
        const val buttonsIconSize = 50f
        const val buttonsOffsetFromEdge = 5f
        val buttonsBackColor: Color = Color.BLACK.cpy().apply { a = 0.67f }
        // Icon view sizing
        const val iconViewIconSize = 50f  // Portrait lies and will be bigger than asked for (55f)
        const val iconViewCellSize = 60f  // Difference to the above is used for selection highlight
    }

    private val previousScreen = playerPicker.previousScreen
    private val ruleset = previousScreen.ruleset

    // This Popup's body has two halves of same size, either side by side or arranged vertically
    // depending on screen proportions - determine height for one of those
    private val partHeight = stageToShowOn.height * (if (stageToShowOn.isNarrowerThan4to3()) 0.45f else 0.8f)
    private val civBlocksWidth = playerPicker.civBlocksWidth

    private val nationListTable = Table()
    private val nationListScroll = AutoScrollPane(nationListTable)
    private val nationDetailsTable = Table()
    private val nationDetailsScroll = AutoScrollPane(nationDetailsTable)

    private class SelectInfo(
        val nation: Nation,
        val scrollY: Float,
        val widget: Container<Portrait>? = null  // null = unused in List mode
    )
    private var listMode: NationPickerListMode
    private var selection: SelectInfo? = null
    private val keySelectMap = mutableMapOf<Char, MutableList<SelectInfo>>()
    private var lastKeyPressed = Char.MIN_VALUE
    private var keyRoundRobin = 0

    init {
        val settings = GUI.getSettings()
        listMode = settings.nationPickerListMode

        nationListScroll.setOverscroll(false, false)
        add(nationListScroll).size( civBlocksWidth + 10f, partHeight )
        // +10, because the nation table has a 5f pad, for a total of +10f
        if (stageToShowOn.isNarrowerThan4to3()) row()
        nationDetailsScroll.setOverscroll(false, false)
        add(nationDetailsScroll).size(civBlocksWidth + 10f, partHeight) // Same here, see above

        updateNationListTable()

        val closeButton = "OtherIcons/Close".toImageButton(Color.FIREBRICK)
        closeButton.onActivation { close() }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButton.setPosition(buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(closeButton)
        clickBehindToClose = true

        val okButton = "OtherIcons/Checkmark".toImageButton(Color.LIME)
        okButton.onActivation { returnSelected() }
        okButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        okButton.setPosition(innerTable.width - buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomRight)
        innerTable.addActor(okButton)

        val switchViewButton = "OtherIcons/NationSwap".toImageButton(Color.ROYAL)
        switchViewButton.onActivation {
            listMode = if (listMode == NationPickerListMode.Icons)
                    NationPickerListMode.List
                else NationPickerListMode.Icons
            settings.nationPickerListMode = listMode
            updateNationListTable()
        }
        switchViewButton.setPosition(2 * buttonsOffsetFromEdge + buttonsCircleSize, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(switchViewButton)

        nationDetailsTable.touchable = Touchable.enabled
        nationDetailsTable.onClick { returnSelected() }
    }

    private fun String.toImageButton(overColor: Color) =
        toImageButton(buttonsIconSize, buttonsCircleSize, buttonsBackColor, overColor)

    private fun returnSelected() {
        val selectedNation = selection?.nation?.name
            ?: return

        UncivGame.Current.musicController.chooseTrack(selectedNation, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSelectNation)

        player.chosenCiv = selectedNation
        close()
        playerPicker.update()
    }

    private data class NationIterationElement(
        val nation: Nation,
        val sort: Int,
        val translatedName: String = nation.name.tr(hideIcons = true)
    )

    private fun updateNationListTable() {
        nationListTable.clear()
        keySelectMap.clear()
        nationListTable.keyShortcuts.clear()
        nationListTable.background = if (listMode == NationPickerListMode.List) null
            else BaseScreen.skinStrings.getUiBackground(
                "NewGameScreen/NationTable/Background",
                tintColor = Color.DARK_GRAY.cpy().apply { a = 0.75f }
            )
        selection = null

        val part1 = sequence {
            if (!noRandom) {
                val random = Nation().apply {
                    name = Constants.random
                    innerColor = listOf(255, 255, 255)
                    outerColor = listOf(0, 0, 0)
                    setTransients()
                }
                yield(NationIterationElement(random, 0))
            }
            val spectator = previousScreen.ruleset.nations[Constants.spectator]
            if (spectator != null && player.playerType != PlayerType.AI)  // only humans can spectate, sorry robots
                yield(NationIterationElement(spectator, 1))
        }
        val part2 = playerPicker.getAvailablePlayerCivs(player.chosenCiv)
            .map { NationIterationElement(it, 2) }
        val nationSequence = (part1 + part2)
            .sortedWith(
                compareBy<NationIterationElement> { it.sort }
                .thenBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.translatedName }
            )

        var selectInfo: SelectInfo? = null
        var currentY = 0f
        var currentX = 0f
        for ((nation, _, translatedName) in nationSequence) {
            val key = if (' ' in translatedName) translatedName.split(' ')[1][0] else translatedName[0]
            val currentSelectInfo: SelectInfo

            val nationActor = if (listMode == NationPickerListMode.List) {
                currentSelectInfo = SelectInfo(nation, currentY)
                val nationTable = NationTable(nation, civBlocksWidth, 0f) // no need for min height
                val cell = nationListTable.add(nationTable)
                currentY += cell.padBottom + cell.prefHeight + cell.padTop
                cell.row()
                nationTable
            } else {
                val nationIcon = ImageGetter.getNationPortrait(nation, iconViewIconSize)
                nationIcon.addTooltip(translatedName, tipAlign = Align.center, hideIcons = true)
                val nationGroup = Container(nationIcon).apply {
                    isTransform = false
                    touchable = Touchable.enabled
                    center()
                }
                currentSelectInfo = SelectInfo(nation, currentY, nationGroup)
                if (currentX + iconViewCellSize > civBlocksWidth) {
                    nationListTable.row()
                    currentX = 0f
                    currentY += iconViewCellSize
                }
                nationListTable.add(nationGroup).size(iconViewCellSize)
                currentX += iconViewCellSize
                nationGroup
            }

            nationActor.onClick {
                highlightNation(currentSelectInfo)
            }
            nationActor.onDoubleClick {
                selection = currentSelectInfo
                returnSelected()
            }
            if (player.chosenCiv == nation.name) {
                selectInfo = currentSelectInfo
            }
            if (key in keySelectMap) {
                keySelectMap[key]!! += currentSelectInfo
            } else {
                keySelectMap[key] = mutableListOf(currentSelectInfo)
                nationListTable.keyShortcuts.add(key) { onKeyPress(key) }
            }
        }

        nationListScroll.layout()
        pack()
        if (selectInfo != null) highlightNation(selectInfo)
    }

    private fun onKeyPress(key: Char) {
        val entries = keySelectMap[key] ?: return
        keyRoundRobin = if (key != lastKeyPressed) 0 else (keyRoundRobin + 1) % entries.size
        lastKeyPressed = key
        highlightNation(entries[keyRoundRobin])
    }

    private fun highlightNation(selectInfo: SelectInfo) {
        selection?.widget?.run {
            clearActions()
            background = null
        }

        nationDetailsTable.clearChildren()  // .clear() also clears listeners!
        nationDetailsTable.add(NationTable(selectInfo.nation, civBlocksWidth, partHeight, ruleset))
        selection = selectInfo

        nationListScroll.scrollY = selectInfo.scrollY -
            (nationListScroll.height - nationListTable.getRowHeight(0)) / 2

        selectInfo.widget?.addAction(HighlightAction(selectInfo))
    }

    private class HighlightAction(selectInfo: SelectInfo) : TemporalAction(1.5f) {
        private val innerColor = selectInfo.nation.getInnerColor()
        private val outerColor = selectInfo.nation.getOuterColor()
        private val widget = selectInfo.widget!!
        private val tempColor = Color()

        override fun begin() {
            widget.background = ImageGetter.getDrawable("OtherIcons/Circle")
                .apply { setMinSize(iconViewCellSize, iconViewCellSize) }
        }
        override fun update(percent: Float) {
            val t = (1.0 - cos(percent * PI * 2)) / 2
            tempColor.set(outerColor).lerp(innerColor, t.toFloat())
            Suppress("UsePropertyAccessSyntax")  // it _is_ a field-by-field copy not a reference set
            widget.setColor(tempColor)  // Luckily only affects background
        }
        override fun end() {
            restart()
        }
    }
}
