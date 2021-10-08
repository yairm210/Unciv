package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.TabbedPager

class MapEditorEditTab(
    private val editorScreen: MapEditorScreenV2
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    private val subTabs = TabbedPager(capacity = 8)

    init {
        top()
        pad(5f)
        subTabs.addPage("Terrain",
            MapEditorEditTerrainTab(this, editorScreen.ruleset),
            ImageGetter.getImage("OtherIcons/Terrains"), 20f)
//        subTabs.addPage("Features")
//        subTabs.addPage("Resources")
//        subTabs.addPage("Wonders")
//        subTabs.addPage("Improvements")
//        subTabs.addPage("Starting locations")
//        subTabs.addPage("Units")
        subTabs.selectPage(0)
        add(subTabs).fillX().row()
    }

    override fun activated(index: Int) {
        editorScreen.tabs.setScrollDisabled(true)
    }

    override fun deactivated(newIndex: Int) {
        editorScreen.tabs.setScrollDisabled(true)
    }
}