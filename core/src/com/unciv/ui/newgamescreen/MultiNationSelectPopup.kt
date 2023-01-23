package com.unciv.ui.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.extensions.isNarrowerThan4to3
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle


class MultiNationSelectPopup(
    previousScreen: IPreviousScreen,
    val gameParameters: GameParameters,
    val majorCivs: Boolean
) : Popup(previousScreen as BaseScreen) {
    companion object {
        // These are used for the Close/OK buttons in the lower left/right corners:
        const val buttonsCircleSize = 70f
        const val buttonsIconSize = 50f
        const val buttonsOffsetFromEdge = 5f
        val buttonsBackColor: Color = Color.BLACK.cpy().apply { a = 0.67f }
    }

    private val blockWidth: Float = 0f
    private val civBlocksWidth = if(blockWidth <= 10f) previousScreen.stage.width / 3 - 5f else blockWidth

    // This Popup's body has two halves of same size, either side by side or arranged vertically
    // depending on screen proportions - determine height for one of those
    private val partHeight = stageToShowOn.height * (if (stageToShowOn.isNarrowerThan4to3()) 0.45f else 0.8f)
    private val nationListTable = Table()
    private val nationListScroll = AutoScrollPane(nationListTable)
    private val selectedNationsListTable = Table()
    private val selectedNationsListScroll = AutoScrollPane(selectedNationsListTable)
    private var selectedNations = gameParameters.randomNations
    var nations = arrayListOf<Nation>()


    init {
        var nationListScrollY = 0f
        nations += if(majorCivs) {
            previousScreen.ruleset.nations.values.asSequence()
                .filter { it.isMajorCiv() }
        } else {
            previousScreen.ruleset.nations.values.asSequence()
                .filter { it.isCityState() }
        }

        nationListScroll.setOverscroll(false, false)
        add(nationListScroll).size( civBlocksWidth + 10f, partHeight )
        // +10, because the nation table has a 5f pad, for a total of +10f
        if (stageToShowOn.isNarrowerThan4to3()) row()
        selectedNationsListScroll.setOverscroll(false, false)
        add(selectedNationsListScroll).size(civBlocksWidth + 10f, partHeight) // Same here, see above

        update()

        nationListScroll.layout()
        pack()
        if (nationListScrollY > 0f) {
            // center the selected nation vertically, getRowHeight safe because nationListScrollY > 0f ensures at least 1 row
            nationListScrollY -= (nationListScroll.height - nationListTable.getRowHeight(0)) / 2
            nationListScroll.scrollY = nationListScrollY.coerceIn(0f, nationListScroll.maxY)
        }

        val closeButton = "OtherIcons/Close".toImageButton(Color.FIREBRICK)
        closeButton.onActivation { close() }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButton.setPosition(buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(closeButton)

        val okButton = "OtherIcons/Checkmark".toImageButton(Color.LIME)
        okButton.onClick { returnSelected() }
        okButton.setPosition(innerTable.width - buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomRight)
        innerTable.addActor(okButton)

        selectedNationsListTable.touchable = Touchable.enabled
    }

    fun update() {
        nationListTable.clear()
        selectedNations = if (majorCivs) {
            gameParameters.randomNations
        } else {
            gameParameters.randomCityStates
        }
        nations -= selectedNations.toSet()
        nations = nations.sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr() }).toMutableList() as ArrayList<Nation>

        var currentY = 0f
        for (nation in nations) {
            val nationTable = NationTable(nation, civBlocksWidth, 0f) // no need for min height
            val cell = nationListTable.add(nationTable)
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
            cell.row()
            nationTable.onClick {
                addNationToPool(nation)
            }
        }

        if (selectedNations.isNotEmpty()) {
            selectedNationsListTable.clear()

            for (currentNation in selectedNations) {
                val nationTable = NationTable(currentNation, civBlocksWidth, 0f)
                nationTable.onClick { removeNationFromPool(currentNation) }
                selectedNationsListTable.add(nationTable).row()
            }
        }
    }

    private fun String.toImageButton(overColor: Color): Group {
        val style = ImageButton.ImageButtonStyle()
        val image = ImageGetter.getDrawable(this)
        style.imageUp = image
        style.imageOver = image.tint(overColor)
        val button = ImageButton(style)
        button.setSize(buttonsIconSize, buttonsIconSize)

        return button.surroundWithCircle(buttonsCircleSize, false, buttonsBackColor)
    }

    private fun updateNationListTable() {
        selectedNationsListTable.clear()

        for (currentNation in selectedNations) {
            val nationTable = NationTable(currentNation, civBlocksWidth, 0f)
            nationTable.onClick { removeNationFromPool(currentNation) }
            selectedNationsListTable.add(nationTable).row()
        }
    }

    private fun addNationToPool(nation: Nation) {
        selectedNations.add(nation)

        update()
        updateNationListTable()
    }

    private fun removeNationFromPool(nation: Nation) {
        nations.add(nation)
        selectedNations.remove(nation)

        update()
        updateNationListTable()
    }

    private fun returnSelected() {
        close()
        if (majorCivs)
            gameParameters.randomNations = selectedNations
        else
            gameParameters.randomCityStates = selectedNations
    }
}


