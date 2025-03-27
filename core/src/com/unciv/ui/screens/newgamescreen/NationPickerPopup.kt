package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings.NationPickerListMode
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.extensions.isNarrowerThan4to3
import com.unciv.ui.components.extensions.toImageButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.components.widgets.AutoScrollPane
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
        // Note - innerTable has pad(20f) and defaults().pad(5f), so content bottomLeft is at x=25/y=25
        // These are used for the Close/OK buttons in the lower left/right corners:
        const val buttonsCircleSize = 70f
        const val buttonsIconSize = 50f
        const val buttonsOffsetFromEdge = 5f
        val buttonsBackColor: Color = ImageGetter.CHARCOAL.cpy().apply { a = 0.67f }
        // Icon view sizing
        const val iconViewIconSize = 50f  // Portrait lies and will be bigger than asked for (55f)
        const val iconViewCellSize = 60f  // Difference to the above is used for selection highlight
        const val iconViewSpacing = 5f    // Extra spacing between icons
        const val iconViewPadTop = 18f    // align top row with nation icon in detail pane - empiric
        // Allow scrolling the bottom left icons _out_ from under the close/toggle view buttons
        const val iconViewPadBottom = buttonsCircleSize + buttonsOffsetFromEdge - 25f + iconViewSpacing
        const val iconViewPadHorz = iconViewSpacing / 2  // a little empiric
    }

    private val previousScreen = playerPicker.previousScreen
    private val ruleset = previousScreen.ruleset
    private val settings = GUI.getSettings()

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
    private var listMode: NationPickerListMode = settings.nationPickerListMode
    private var selection: SelectInfo? = null
    private val keySelectMap = mutableMapOf<Char, MutableList<SelectInfo>>()
    private var lastKeyPressed = Char.MIN_VALUE
    private var keyRoundRobin = 0

    init {
        nationListScroll.setOverscroll(false, false)
        add(nationListScroll).size( civBlocksWidth + 10f, partHeight )
        // +10, because the nation table has a 5f pad, for a total of +10f
        if (stageToShowOn.isNarrowerThan4to3()) row()
        nationDetailsScroll.setOverscroll(false, false)
        add(nationDetailsScroll).size(civBlocksWidth + 10f, partHeight) // Same here, see above

        updateNationListTable()

        clickBehindToClose = true
        addActionIcons()

        nationDetailsTable.touchable = Touchable.enabled
        nationDetailsTable.onClick { returnSelected() }
    }

    /** Note - [newMode]==null toggles, but this is prepared for key shortcuts _setting_ a mode.
     *  Unused due to our key input stack not supporting Ctrl-Numbers yet, postponed.
     */
    private fun toggleListMode(newMode: NationPickerListMode? = null) {
        fun NationPickerListMode.toggle() = when (this) {
            NationPickerListMode.Icons -> NationPickerListMode.List
            NationPickerListMode.List -> NationPickerListMode.Icons
        }
        listMode = newMode ?: listMode.toggle()
        settings.nationPickerListMode = listMode
        updateNationListTable()
        nationListScroll.updateVisualScroll()
    }

    private fun String.toImageButton(overColor: Color) =
        toImageButton(buttonsIconSize, buttonsCircleSize, buttonsBackColor, overColor)

    private fun addActionIcons() {
        // Despite being a Popup we use our own buttons - floating circular ones
        val closeButton = getCloseButton(buttonsCircleSize, buttonsIconSize, buttonsBackColor, Color.FIREBRICK) { close() }
        closeButton.setPosition(buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(closeButton)

        val okButton = "OtherIcons/Checkmark".toImageButton(Color.LIME)
        okButton.onActivation { returnSelected() }
        okButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        okButton.setPosition(innerTable.width - buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomRight)
        innerTable.addActor(okButton)

        val switchViewButton = "OtherIcons/NationSwap".toImageButton(Color.ROYAL)
        switchViewButton.onActivation { toggleListMode() }
        // No keyboard support yet - file manager conventions: Ctrl-1 Icons, Ctrl-2 List
        switchViewButton.setPosition(2 * buttonsOffsetFromEdge + buttonsCircleSize, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(switchViewButton)
    }

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
        val translatedName: String = nation.name.tr(hideIcons = true)
    )

    private fun updateNationListTable() {
        nationListTable.clear()
        keySelectMap.clear()
        nationListTable.keyShortcuts.clear()

        // As for background... In List mode, the NationTable blocks come with a 5f horizontal padding,
        // so the Icon mode background "jumps" to 5f wider - haven't found a fix!
        if (listMode == NationPickerListMode.List) {
            nationListTable.background = null
            nationListTable.defaults().space(0f)
            nationListTable.pad(0f)
        } else {
            nationListTable.background = BaseScreen.skinStrings.getUiBackground(
                "NewGameScreen/NationTable/Background",
                tintColor = Color.DARK_GRAY.cpy().apply { a = 0.75f }
            )
            nationListTable.defaults().space(iconViewSpacing)
            nationListTable.pad(iconViewPadTop, iconViewPadHorz, iconViewPadBottom, iconViewPadHorz)
        }

        // These are available as closures to the factories below
        var currentX = 0f
        var currentY = 0f

        // Decide by listMode how each block is built -
        // for each a factory producing an Actor and info on how to select it
        fun getListModeNationActor(element: NationIterationElement): Pair<NationTable, SelectInfo> {
            val currentSelectInfo = SelectInfo(element.nation, currentY)
            val nationTable = NationTable(element.nation, civBlocksWidth, 0f) // no need for min height
            val cell = nationListTable.add(nationTable)
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
            cell.row()
            return nationTable to currentSelectInfo
        }

        fun getIconsModeNationActor(element: NationIterationElement): Pair<WidgetGroup, SelectInfo> {
            val nationIcon = ImageGetter.getNationPortrait(element.nation, iconViewIconSize)
            nationIcon.addTooltip(element.translatedName, tipAlign = Align.center, hideIcons = true)
            val nationGroup = Container(nationIcon).apply {
                isTransform = false
                touchable = Touchable.enabled
                setRound(false)
                center()
            }
            val currentSelectInfo = SelectInfo(element.nation, currentY, nationGroup)
            if (currentX + iconViewCellSize > civBlocksWidth) {
                nationListTable.row()
                currentX = 0f
                currentY += iconViewCellSize
            }
            nationListTable.add(nationGroup).size(iconViewCellSize)
            currentX += iconViewCellSize + iconViewSpacing
            return nationGroup to currentSelectInfo
        }

        val nationActorFactory = when (listMode) {
            NationPickerListMode.Icons -> ::getIconsModeNationActor
            NationPickerListMode.List -> ::getListModeNationActor
        }

        selection = null
        var selectInfo: SelectInfo? = null

        for (element in getSortedNations()) {
            val (nationActor, currentSelectInfo) = nationActorFactory(element)
            
            nationActor.onClick {
                highlightNation(currentSelectInfo)
            }
            nationActor.onDoubleClick {
                selection = currentSelectInfo
                returnSelected()
            }

            if (player.chosenCiv == element.nation.name) {
                selectInfo = currentSelectInfo
            }

            // Keyboard: Fist letter of each "word" - "The Ottomans" get T _and_ O
            val keys = element.translatedName.split(' ').mapNotNull { it.firstOrNull() }.toSet()
            for (key in keys) {
                if (key in keySelectMap) {
                    keySelectMap[key]!! += currentSelectInfo
                } else {
                    keySelectMap[key] = mutableListOf(currentSelectInfo)
                    nationListTable.keyShortcuts.add(key) { onKeyPress(key) }
                }
            }
        }    

        nationListScroll.layout()
        pack()
        if (selectInfo != null) highlightNation(selectInfo)
    }

    private fun getSortedNations(): Sequence<NationIterationElement> {
        // Random and Spectator come first, both optional
        val part1 = sequence {
            if (!noRandom) {
                val random = Nation().apply {
                    name = Constants.random
                    innerColor = listOf(255, 255, 255)
                    outerColor = listOf(0, 0, 0)
                    setTransients()
                }
                yield(NationIterationElement(random))
            }
            val spectator = previousScreen.ruleset.nations[Constants.spectator]
            if (spectator != null && player.playerType != PlayerType.AI)  // only humans can spectate, sorry robots
                yield(NationIterationElement(spectator))
        }
        // Then what PlayerPickerTable says we should display - see its doc
        val part2 = playerPicker.getAvailablePlayerCivs(player.chosenCiv)
            .map { NationIterationElement(it) }
        // Combine and Sort
        return part1 +
            part2.sortedWith(
                compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.translatedName }
            )
    }

    private fun onKeyPress(key: Char) {
        // Keyboard is handled for the entire Table, not per Nation Actor to allow round-robin
        // That is, "Germany, Greece, Gremlins" -> press "G" repeatedly to cycle through them.
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

        // Because in Icons mode it's much less clear _where_ the selected Nation is in the Grid -
        // the scrollY centering is enough in List mode - the selection gets a thin border
        // oscillating between the Nation's colours:
        selectInfo.widget?.addAction(HighlightAction(selectInfo))
    }

    @Suppress("UsePropertyAccessSyntax")  // setColor _is_ a field-by-field copy not a reference set
    private class HighlightAction(selectInfo: SelectInfo) : TemporalAction(1.5f) {
        private val innerColor = selectInfo.nation.getInnerColor()
        private val outerColor = selectInfo.nation.getOuterColor()
        private val widget = selectInfo.widget!!
        private val tempColor = Color()

        override fun begin() {
            widget.background = ImageGetter.getCircleDrawable()
                .apply { setMinSize(iconViewCellSize, iconViewCellSize) }
        }
        override fun update(percent: Float) {
            val t = (1.0 - cos(percent * PI * 2)) / 2
            tempColor.set(outerColor).lerp(innerColor, t.toFloat())
            widget.setColor(tempColor)  // Luckily only affects background
        }
        override fun end() {
            restart()
        }
    }
}
