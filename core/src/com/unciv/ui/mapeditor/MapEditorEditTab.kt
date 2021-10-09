package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.*

class MapEditorEditTab(
    private val editorScreen: MapEditorScreenV2,
    headerHeight: Float
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    private val subTabs: TabbedPager
    private val brushTable = Table(skin)
    private val brushLabel = "Brush ([1]):".toLabel()
    private val brushCell: Cell<Actor>

    /** Display name of the current brush */
    var brushName = ""
        private set
    /** This applies the current brush to one tile **without** validation or transient updates */
    var brushAction: (TileInfo)->Unit = {}
        private set
    /** Brush size: 1..5 means hexagon of radius x, -1 means floodfill */
    var brushSize = 1
        private set

    init {
        top()
        pad(5f)

        brushTable.apply {
            defaults().pad(10f).left()
            add(brushLabel)
            brushCell = add().padLeft(0f)
            add(UncivSlider(1f,6f,1f, getTipText = ::getBrushTip) {
                brushSize = if (it > 5f) -1 else it.toInt()
                brushLabel.setText("Brush ([${getBrushTip(it)}]):".tr())
            }).padLeft(0f)
        }

        val subTabsHeight = editorScreen.stage.height - headerHeight - brushTable.prefHeight
        subTabs = TabbedPager(minimumWidth = editorScreen.stage.width * 0.33f, minimumHeight = subTabsHeight, capacity = 8)
        val ruleset = editorScreen.ruleset

        subTabs.addPage("Terrain",
            MapEditorEditTerrainTab(this, ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f)
        subTabs.addPage("Features",
            MapEditorEditFeaturesTab(this, ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f,
            disabled = ruleset.terrains.values.none { it.type == TerrainType.TerrainFeature })
        subTabs.addPage("Wonders",
            MapEditorEditWondersTab(this, ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f,
            disabled = ruleset.terrains.values.none { it.type == TerrainType.NaturalWonder })
        subTabs.addPage("Resources",
            MapEditorEditResourcesTab(this, ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f,
            disabled = ruleset.tileResources.values.isEmpty())
        subTabs.addPage("Improvements",
            MapEditorEditImprovementsTab(this, ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f)
        subTabs.addPage("Starting locations",
            MapEditorEditStartsTab(this, ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f)
        subTabs.addPage("Units",
            MapEditorEditUnitsTab(this, ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f)
        subTabs.selectPage(0)

        add(brushTable).fillX().row()
        addSeparator(Color.GRAY)
        add(subTabs).left().fillX().row()
    }

    fun setBrush(name: String, icon: String, isRemove: Boolean = false, applyAction: (TileInfo)->Unit) {
        brushName = name
        brushCell.setActor(FormattedLine(name, icon = icon, iconCrossed = isRemove).render(0f))
        brushAction = applyAction
    }

    override fun activated(index: Int) {
        editorScreen.tabs.setScrollDisabled(true)
        //subTabs.width = editorScreen.tabs.width
    }

    override fun deactivated(newIndex: Int) {
        editorScreen.tabs.setScrollDisabled(true)
    }

    companion object {
        private fun getBrushTip(value: Float) = if (value > 5f) "Floodfill" else value.toString()
    }
}
