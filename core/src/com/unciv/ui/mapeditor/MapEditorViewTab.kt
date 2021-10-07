package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.logic.map.TileInfo
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.civilopedia.FormattedLine.IconDisplay
import com.unciv.ui.utils.*

class MapEditorViewTab(
    private val editorScreen: MapEditorScreenV2
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    val tileDataCell: Cell<Table>

    init {
        defaults().pad(5f, 0f)

        val tileMap = editorScreen.tileMap
        val labelWidth = editorScreen.stage.width * 0.25f
        val mapParameterLabel = WrappableLabel(tileMap.mapParameters.toString(), labelWidth)
        add(mapParameterLabel.apply { wrap = true }).row()

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
        add(Constants.close.toTextButton().apply { onClick(closeAction) }).row()
    }

    override fun activated(index: Int) {
        editorScreen.tileClickHandler = this::tileClickHandler
    }
    override fun deactivated(newIndex: Int) {
        tileDataCell.setActor(null)
        editorScreen.tileClickHandler = null
    }

    fun tileClickHandler(tile: TileInfo) {
        val lines = tile.toMarkup(null)
        lines += FormattedLine()
        lines += FormattedLine("Position: ${tile.position}")
        tileDataCell.setActor(MarkupRenderer.render(lines, padding = 0f, iconDisplay = IconDisplay.NoLink))
    }
}
