package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Nation
import com.unciv.models.stats.Stats
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
        top()
        defaults().pad(5f, 20f)
        update()
    }

    private fun CivilizationInfo.updateMockCiv() {
        // This crappy construct exists only to allow us to call TileInfo.getTileStats
        try {
            val dummy = nation
        } catch (ex: UninitializedPropertyAccessException) {
            nation = Nation()
            nation.name = "Test"
        }
        try {
            val dummy = gameInfo
        } catch (ex: UninitializedPropertyAccessException) {
            gameInfo = GameInfo()
        }
        gameInfo.ruleSet = editorScreen.ruleset
        if (tech.techsResearched.isEmpty()) {
            // show yields of strategic resources too
            tech.techsResearched.addAll(editorScreen.ruleset.technologies.keys)
        }
    }

    private fun update() {
        clear()
        mockCiv.updateMockCiv()

        val tileMap = editorScreen.tileMap
        val labelWidth = editorScreen.stage.width * 0.33f

        if (tileMap.mapParameters.name.isNotEmpty()) {
            val mapNameLabel = "${tileMap.mapParameters.name}{}".toLabel(Color.SKY, 24)
            add(mapNameLabel).padBottom(15f).row()
        }

        val mapParameterText = tileMap.mapParameters.toString()
            .replace("\"${tileMap.mapParameters.name}\" ", "")
        val mapParameterLabel = WrappableLabel(mapParameterText, labelWidth)
        add(mapParameterLabel.apply { wrap = true }).row()

        tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)
        val statsText = "Area: ${tileMap.values.size} tiles, ${tileMap.continentSizes.size} continents/islands"
        val statsLabel = WrappableLabel(statsText, labelWidth)
        add(statsLabel.apply { wrap = true }).row()

        if (tileMap.naturalWonders.isNotEmpty()) {
            val collator = UncivGame.Current.settings.getCollatorFromLocale()
            val naturalWonders = sequenceOf(FormattedLine("Natural Wonders", header = 3, color = "#228b22")) +
                tileMap.naturalWonders.asSequence()
                .sortedWith(compareBy(collator, { it.tr() }))
                .map { FormattedLine(it, link = "Terrain/$it") }
            add(MarkupRenderer.render(naturalWonders.toList(), iconDisplay = IconDisplay.NoLink) {
                scrollToWonder(it)
            }).row()
        }

        addSeparator()

        tileDataCell = add(Table()).fillX()
        row()

        addSeparator()
        add("Exit map editor".toTextButton().apply { onClick(editorScreen::closeEditor) }).row()
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

        lines += FormattedLine("Position: ${tile.position.toString().replace(".0","")}")
        lines += FormattedLine()

        lines.addAll(tile.toMarkup(null))

        val stats = try {
            tile.getTileStats(null, mockCiv)
        } catch (ex: Exception) {
            // Maps aren't always fixed to remove dead references... like resource "Gold"
            if (ex.message != null)
                ToastPopup(ex.message!!, editorScreen)
            Stats()
        }
        if (!stats.isEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine(stats.toString())
        }

        val nations = tile.tileMap.startingLocationsByNation.asSequence()
            .filter { tile in it.value }
            .filter { it.key in tile.tileMap.ruleset!!.nations } // Ignore missing nations
            .map { it.key to tile.tileMap.ruleset!!.nations[it.key]!! }
            .sortedWith(compareBy({ it.second.isCityState() }, { it.first }))
            .joinToString { it.first.tr() }
        if (nations.isNotEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine("Starting location(s): [$nations]")
        }

        tileDataCell?.setActor(MarkupRenderer.render(lines, iconDisplay = IconDisplay.NoLink))
    }

    private fun scrollToWonder(link: String) {
        val name = link.split('/')[1]
        val tile = editorScreen.tileMap.values.filter {
            it.naturalWonder == name
        }.randomOrNull() ?: return
        editorScreen.mapHolder.setCenterPosition(tile.position)
    }
}
