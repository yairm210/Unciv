package com.unciv.ui.screens.mapeditorscreen.tabs

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.TileDescription
import com.unciv.models.Counter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen

class MapEditorViewTab(
    private val editorScreen: MapEditorScreen
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private var tileDataCell: Cell<Table>? = null
    private val mockCiv = createMockCiv(editorScreen.ruleset)
    private val naturalWonders = Counter<String>()
    /** Click-locating items with several instances: round robin, for simplicity only a global one */
    private var roundRobinIndex = 0
    private val collator = UncivGame.Current.settings.getCollatorFromLocale()
    private val labelWidth = editorScreen.getToolsWidth() - 40f

    init {
        top()
        // Note on width: max expander content width + 2 * expander.defaultPad + 2 * the following horizontal pad
        // should not exceed editorScreen.getToolsWidth() or the page will scroll horizontally!
        defaults().pad(5f, 20f)
        update()
    }

    private fun createMockCiv(ruleset: Ruleset) = Civilization().apply {
        // This crappy construct exists only to allow us to call Tile.TileStatFunctions.getTileStats
        nation = Nation()
        nation.name = "Test"
        gameInfo = GameInfo()
        gameInfo.ruleset = ruleset
        cache.updateState()
        
        // show yields of strategic resources too
        tech.techsResearched.addAll(ruleset.technologies.keys)
    }

    private fun Civilization.updateMockCiv(ruleset: Ruleset) {
        if (gameInfo.ruleset === ruleset) return
        gameInfo.ruleset = ruleset
        tech.techsResearched.addAll(ruleset.technologies.keys)
    }

    private fun update() {
        clear()
        mockCiv.updateMockCiv(editorScreen.ruleset)

        val tileMap = editorScreen.tileMap

        val headerText = tileMap.mapParameters.name.ifEmpty { "New map" }
        add(ExpanderTab(
            headerText,
            startsOutOpened = false,
            defaultPad = 0f  // See note in init
        ) {
            val mapParameterText = tileMap.mapParameters.toString()
                .replace("\"${tileMap.mapParameters.name}\" ", "")
            val mapParameterLabel = WrappableLabel(mapParameterText, labelWidth)
            it.add(mapParameterLabel.apply { wrap = true }).row()
        }).row()

        try {
            tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)
        } catch (ex: Exception) {
            ToastPopup("Error assigning continents: ${ex.message}", editorScreen)
        }

        val area = tileMap.values.size
        val waterPercent = (tileMap.values.count { it.isWater } * 100f / area).toInt()
        val impassablePercent = (tileMap.values.count { it.isImpassible() } * 100f / area).toInt()
        val continents = tileMap.continentSizes.size
        val statsText = "Area: [$area] tiles, [$waterPercent]% water, [$impassablePercent]% impassable, [$continents] continents/islands"
        val statsLabel = WrappableLabel(statsText, labelWidth)
        add(statsLabel.apply { wrap = true }).row()

        add(editorScreen.descriptionTextField).growX().row()

        // Map editor must not touch tileMap.naturalWonders as it is a by lazy immutable list,
        // and we wouldn't be able to fix it when the natural wonders change
        if (editorScreen.naturalWondersNeedRefresh) {
            naturalWonders.clear()
            tileMap.values.asSequence()
                .mapNotNull { it.naturalWonder }
                .sortedWith(compareBy(collator) { it.tr(hideIcons = true) })
                .forEach {
                    naturalWonders.add(it, 1)
                }
            editorScreen.naturalWondersNeedRefresh = false
        }
        if (naturalWonders.isNotEmpty()) {
            val lines = naturalWonders.map {
                FormattedLine(if (it.value == 1) it.key else "{${it.key}} (${it.value})", it.key, "Terrain/${it.key}")
            }
            add(ExpanderTab(
                "{Natural Wonders} (${naturalWonders.size})",
                fontSize = 21,
                startsOutOpened = false,
                headerPad = 5f
            ) {
                it.add(MarkupRenderer.render(lines, iconDisplay = IconDisplay.NoLink) { name->
                    scrollToWonder(name)
                })
            }).row()
        }

        // Starting locations not cached like natural wonders - storage is already compact
        if (tileMap.startingLocationsByNation.isNotEmpty()) {
            val lines = tileMap.getStartingLocationSummary()
                .map { FormattedLine(if (it.second == 1) it.first else "{${it.first}} (${it.second})", it.first, "Nation/${it.first}") }
            add(ExpanderTab(
                "{Starting locations} (${tileMap.startingLocationsByNation.size})",
                fontSize = 21,
                startsOutOpened = false,
                headerPad = 5f
            ) {
                it.add(MarkupRenderer.render(lines.asIterable(), iconDisplay = IconDisplay.NoLink) { name ->
                    scrollToStartOfNation(name)
                })
            }).row()
        }

        addSeparator()

        tileDataCell = add(Table()).fillX()
        row()

        addSeparator()
        add("Exit map editor".toTextButton().apply { onClick(editorScreen::closeEditor) }).row()

        invalidateHierarchy()  //todo - unsure this helps
        validate()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        editorScreen.tileClickHandler = this::tileClickHandler
        update()
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        editorScreen.hideSelection()
        tileDataCell?.setActor(null)
        editorScreen.tileClickHandler = null
    }

    private fun tileClickHandler(tile: Tile) {
        if (tileDataCell == null) return

        val lines = ArrayList<FormattedLine>()

        lines += FormattedLine("Position: [${tile.position.toString().replace(".0","")}]")
        lines += FormattedLine()

        lines.addAll(TileDescription.toMarkup(tile, null))

        val stats = try {
            tile.stats.getTileStats(null, mockCiv)
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

        val nations = tile.tileMap.getTileStartingLocationSummary(tile)
        if (nations.isNotEmpty()) {
            lines += FormattedLine()
            lines += FormattedLine("Starting location(s): [$nations]")
        }

        val continent = tile.getContinent()
        if (continent >= 0) {
            lines += FormattedLine()
            lines += FormattedLine("Continent: [$continent] ([${tile.tileMap.continentSizes[continent]}] tiles)", link = "continent")
        }

        val renderedInfo = MarkupRenderer.render(lines, labelWidth) {
            if (it == "continent") {
                // Visualize the continent this tile is on
                editorScreen.hideSelection()
                val color = Color.BROWN.darken(0.5f)
                for (markTile in tile.tileMap.values) {
                    if (markTile.getContinent() == continent)
                        editorScreen.highlightTile(markTile, color)
                }
            } else {
                // This needs CivilopediaScreen to be able to work without a GameInfo!
                editorScreen.openCivilopedia(it)
            }
        }

        if (tile.resource != null && (tile.resourceAmount > 0 || tile.tileResource.resourceType == ResourceType.Strategic)) {
            renderedInfo.addSeparator(Color.GRAY)
            renderedInfo.add(Table().apply {
                add("Resource abundance".toLabel(alignment = Align.left)).left().growX()
                val slider = UncivSlider(0f, 42f, 1f,
                    initial = tile.resourceAmount.toFloat()
                ) {
                    tile.resourceAmount = it.toInt()
                    editorScreen.updateTile(tile)
                    editorScreen.isDirty = true
                }
                slider.setSnapToValues(threshold = 5f, 0f,1f,2f,3f,4f,5f,6f,7f,8f,9f,10f,12f,15f,20f,30f,40f)
                add(slider).right().minWidth(80f).fillX().padTop(15f)
            }).fillX()
        }

        tileDataCell?.setActor(renderedInfo)

        editorScreen.hideSelection()
        editorScreen.highlightTile(tile, Color.CORAL)
    }

    private fun scrollToWonder(name: String) {
        scrollToNextTileOf(editorScreen.tileMap.values.filter { it.naturalWonder == name })
    }
    private fun scrollToStartOfNation(name: String) {
        val tiles = editorScreen.tileMap.startingLocationsByNation[name]
            ?: return
        scrollToNextTileOf(tiles.toList())
    }
    private fun scrollToNextTileOf(tiles: List<Tile>) {
        if (tiles.isEmpty()) return
        if (roundRobinIndex >= tiles.size) roundRobinIndex = 0
        val tile = tiles[roundRobinIndex++]
        editorScreen.mapHolder.setCenterPosition(tile.position, blink = true)
        tileClickHandler(tile)
    }

    private fun TileMap.getTileStartingLocationSummary(tile: Tile) =
        startingLocations.asSequence()
            .filter { it.position == tile.position }
            .mapNotNull { if (it.nation in ruleset!!.nations) ruleset!!.nations[it.nation]!! to it.usage else null }
            .sortedWith(compareBy<Pair<Nation,TileMap.StartingLocation.Usage>>{ it.first.isCityState }.thenBy(collator) { it.first.name.tr(hideIcons = true) })
            .joinToString { "{${it.first.name}} ({${it.second.label}})".tr() }

    private fun TileMap.getStartingLocationSummary() =
        startingLocationsByNation.asSequence()
        .mapNotNull { if (it.key in ruleset!!.nations) ruleset!!.nations[it.key]!! to it.value.size else null }
        .sortedWith(compareBy<Pair<Nation,Int>>{ it.first.isCityState }.thenBy(collator) { it.first.name.tr(hideIcons = true) })
        .map { it.first.name to it.second }
}
