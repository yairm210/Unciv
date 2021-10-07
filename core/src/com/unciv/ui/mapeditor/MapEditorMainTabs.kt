package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.TabbedPager

class MapEditorMainTabs(editorScreen: MapEditorScreenV2) : TabbedPager(capacity = 7) {
    init {
        addPage("View", MapEditorViewTab(editorScreen), ImageGetter.getImage("OtherIcons/Search"), 25f)
        addPage("Generate", Table(), ImageGetter.getImage("OtherIcons/New"), 25f)
        addPage("Edit", Table(), ImageGetter.getImage("OtherIcons/Terrains"), 25f)
        addPage("Load", Table(), ImageGetter.getImage("OtherIcons/Load"), 25f)
        addPage("Save", Table(), ImageGetter.getImage("OtherIcons/Checkmark"), 25f)
        addPage("Mods", Table(), ImageGetter.getImage("OtherIcons/Mods"), 25f)
        addPage("Options", Table(), ImageGetter.getImage("OtherIcons/Settings"), 25f)
    }
}
