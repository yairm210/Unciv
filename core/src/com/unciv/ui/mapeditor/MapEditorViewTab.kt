package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.MainMenuScreen
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Nation
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.civilopedia.FormattedLine.IconDisplay
import com.unciv.ui.utils.*

class MapEditorViewTab(
    private val editorScreen: MapEditorScreenV2
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    private var tileDataCell: Cell<Table>? = null
    private val mockCiv = CivilizationInfo()

    init {
        mockCiv.nation = Nation().apply { name = "Test" }
        defaults().pad(5f, 20f)
        update()
    }

    private fun update() {
        clear()

        val tileMap = editorScreen.tileMap
        val labelWidth = editorScreen.stage.width * 0.33f

        if (tileMap.mapParameters.name.isNotEmpty()) {
            val mapNameLabel = "${tileMap.mapParameters.name}{}".toLabel(Color.SKY, 24)
            add(mapNameLabel).padBottom(15f).row()
        }

        val mapParameterLabel = WrappableLabel(tileMap.mapParameters.toString(), labelWidth)
        add(mapParameterLabel.apply { wrap = true }).row()

        tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)
        val statsText = "Area: ${tileMap.values.size} tiles, ${tileMap.continentSizes.size} continents, ${tileMap.naturalWonders.size} Natural Wonders"
        val statsLabel = WrappableLabel(statsText, labelWidth)
        add(statsLabel.apply { wrap = true }).row()
        addSeparator()

        tileDataCell = add(Table()).fillX()
        row()

        addSeparator()
        val closeAction = {
            editorScreen.game.setScreen(MainMenuScreen())
        }
        editorScreen.keyPressDispatcher[KeyCharAndCode.BACK] = closeAction
        add("Exit map editor".toTextButton().apply { onClick(closeAction) }).row()
    }

    override fun activated(index: Int) {
        editorScreen.tileClickHandler = this::tileClickHandler
        update()
    }
    override fun deactivated(newIndex: Int) {
        tileDataCell?.setActor(null)
        editorScreen.tileClickHandler = null
    }

    fun tileClickHandler(tile: TileInfo) {
        if (tileDataCell == null) return

        val lines = ArrayList<FormattedLine>()

        lines += FormattedLine("Position: ${tile.position.toString().replace(".0","")}", centered = true)
        lines += FormattedLine()

        lines.addAll(tile.toMarkup(null))

        val stats = tile.getTileStats(null, mockCiv)
        if (!stats.isEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine(stats.toString(), centered = true)
        }

        val nations = tile.tileMap.startingLocationsByNation.asSequence()
            .filter { tile in it.value }
            .filter { it.key in tile.tileMap.ruleset!!.nations } // Ignore missing nations
            .map { it.key to tile.tileMap.ruleset!!.nations[it.key]!! }
            .sortedWith(compareBy({ it.second.isCityState() }, { it.first }))
            .joinToString { it.first.tr() }
        if (nations.isNotEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine("Starting location(s): [$nations]", centered = true)
        }

        tileDataCell?.setActor(MarkupRenderer.render(lines, iconDisplay = IconDisplay.NoLink))
    }
}
