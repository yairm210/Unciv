package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.utils.KeyPressDispatcher.Companion.keyboardAvailable
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class EmpireOverviewScreen(private var viewingPlayer:CivilizationInfo, defaultPage: String = "") : BaseScreen(){
    private val topTable = Table().apply { defaults().pad(10f) }
    private val centerTable = Table().apply { defaults().pad(5f) }

    internal val setCategoryActions = HashMap<String, () -> Unit>()
    private val categoryButtons = HashMap<String, Button>()

    // 50 normal button height + 2*10 topTable padding + 2 Separator + 2*5 centerTable padding
    // Since a resize recreates this screen this should be fine as a val
    internal val centerAreaHeight = stage.height - 82f

    private object ButtonDecorations {
        data class IconAndKey (val icon: String, val key: Char = Char.MIN_VALUE)
        val keyIconMap: HashMap<String,IconAndKey> = hashMapOf(
            Pair("Cities", IconAndKey("OtherIcons/Cities", 'C')),
            Pair("Stats", IconAndKey("StatIcons/Gold", 'S')),
            Pair("Trades", IconAndKey("StatIcons/Acquire", 'T')),
            Pair("Units", IconAndKey("OtherIcons/Shield", 'U')),
            Pair("Diplomacy", IconAndKey("OtherIcons/DiplomacyW", 'D')),
            Pair("Resources", IconAndKey("StatIcons/Happiness", 'R')),
            Pair("Religion", IconAndKey("StatIcons/Faith", 'F')),
            Pair("Wonders", IconAndKey("OtherIcons/Wonders", 'W'))
        )
    }

    private fun addCategory(name:String, table:Table, disabled:Boolean=false) {
        // Buttons now hold their old label plus optionally an indicator for the shortcut key.
        // Implement this templated on UnitActionsTable.getUnitActionButton()
        val iconAndKey = ButtonDecorations.keyIconMap[name] ?: return   // category without decoration entry disappears
        val setCategoryAction = {
            centerTable.clear()
            centerTable.add(ScrollPane(table).apply { setOverscroll(false, false) })
                    .height(centerAreaHeight)
                    .width(stage.width)
            centerTable.pack()
            for ((key, categoryButton) in categoryButtons.filterNot { it.value.touchable == Touchable.disabled })
                categoryButton.color = if (key == name) Color.BLUE else Color.WHITE
            if (name == "Stats")
                game.settings.addCompletedTutorialTask("See your stats breakdown")
            game.settings.lastOverviewPage = name
        }
        val icon = if (iconAndKey.icon != "") ImageGetter.getImage(iconAndKey.icon) else null
        val button = IconTextButton(name, icon)
        if (!disabled && keyboardAvailable && iconAndKey.key != Char.MIN_VALUE) {
            button.addTooltip(iconAndKey.key)
            keyPressDispatcher[iconAndKey.key] = setCategoryAction
        }
        setCategoryActions[name] = setCategoryAction
        categoryButtons[name] = button
        button.onClick(setCategoryAction)
        if (disabled) button.disable()
        topTable.add(button)
    }

    init {
        val page =
            if (defaultPage != "") {
                game.settings.lastOverviewPage = defaultPage
                defaultPage
            }
            else game.settings.lastOverviewPage

        onBackButtonClicked { game.setWorldScreen() }

        addCategory("Cities", CityOverviewTable(viewingPlayer, this), viewingPlayer.cities.isEmpty())
        addCategory("Stats", StatsOverviewTable(viewingPlayer, this))
        addCategory("Trades", TradesOverviewTable(viewingPlayer, this), viewingPlayer.diplomacy.values.all { it.trades.isEmpty() })
        addCategory("Units", UnitOverviewTable(viewingPlayer, this), viewingPlayer.getCivUnits().none())
        addCategory("Diplomacy", DiplomacyOverviewTable(viewingPlayer, this), viewingPlayer.diplomacy.isEmpty())
        addCategory("Resources", ResourcesOverviewTable(viewingPlayer, this), viewingPlayer.detailedCivResources.isEmpty())
        if (viewingPlayer.gameInfo.isReligionEnabled())
            addCategory("Religion", ReligionOverviewTable(viewingPlayer, this), viewingPlayer.gameInfo.religions.isEmpty())
        addCategory("Wonders", WonderOverviewTable(viewingPlayer, this), viewingPlayer.naturalWonders.isEmpty() && viewingPlayer.cities.isEmpty())

        val closeButton = Constants.close.toTextButton().apply {
            setColor(0.75f, 0.1f, 0.1f, 1f)
        }
        closeButton.onClick { game.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        topTable.add(closeButton)

        topTable.pack()
        val topScroll = ScrollPane(topTable).apply { setScrollingDisabled(false, true) }

        setCategoryActions[page]?.invoke()

        val table = Table()
        table.add(topScroll).row()
        table.addSeparator()
        table.add(centerTable).height(stage.height - topTable.height).expand().row()
        table.setFillParent(true)
        stage.addActor(table)
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(EmpireOverviewScreen(viewingPlayer, game.settings.lastOverviewPage))
        }
    }

    //todo this belongs in VictoryScreen as it's only ever used there
    companion object {
        fun getCivGroup(civ: CivilizationInfo, afterCivNameText:String, currentPlayer:CivilizationInfo): Table {
            val civGroup = Table()

            var labelText = civ.civName.tr()+afterCivNameText
            var labelColor=Color.WHITE
            val backgroundColor:Color

            if (civ.isDefeated()) {
                civGroup.add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
                backgroundColor = Color.LIGHT_GRAY
                labelColor = Color.BLACK
            } else if (currentPlayer==civ // game.viewEntireMapForDebug
                    || currentPlayer.knows(civ) || currentPlayer.isDefeated() || currentPlayer.victoryManager.hasWon()) {
                civGroup.add(ImageGetter.getNationIndicator(civ.nation, 30f))
                backgroundColor = civ.nation.getOuterColor()
                labelColor = civ.nation.getInnerColor()
            } else {
                civGroup.add(ImageGetter.getRandomNationIndicator(30f))
                backgroundColor = Color.DARK_GRAY
                labelText = "???"
            }

            civGroup.background = ImageGetter.getRoundedEdgeRectangle(backgroundColor)
            val label = labelText.toLabel(labelColor)
            label.setAlignment(Align.center)

            civGroup.add(label).padLeft(10f)
            civGroup.pack()
            return civGroup
        }
    }
}
