package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.MapSaver
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.TabbedPager

class MapEditorMainTabs(
    editorScreen: MapEditorScreenV2
) : TabbedPager(
    minimumHeight = editorScreen.stage.height,
    maximumHeight = editorScreen.stage.height,
    headerFontSize = 24,
    capacity = 7
) {
    val view = MapEditorViewTab(editorScreen)
    val generate = MapEditorGenerateTab(editorScreen)
    val edit = MapEditorEditTab(editorScreen, headerHeight)
    val load = MapEditorLoadTab(editorScreen, headerHeight)
    val save = MapEditorSaveTab(editorScreen, headerHeight)
    val mods = MapEditorModsTab(editorScreen)
    val options = MapEditorOptionsTab(editorScreen)

    init {
        prefWidth = 0.4f * editorScreen.stage.width

        addPage("View", view,
            ImageGetter.getImage("OtherIcons/Search"), 25f)
        addPage("Generate", generate,
            ImageGetter.getImage("OtherIcons/New"), 25f)
        addPage("Edit", edit,
            ImageGetter.getImage("OtherIcons/Terrains"), 25f)
        addPage("Load", load,
            ImageGetter.getImage("OtherIcons/Load"), 25f,
            disabled = MapSaver.getMaps().isEmpty())
        addPage("Save", save,
            ImageGetter.getImage("OtherIcons/Checkmark"), 25f)
        addPage("Mods", mods,
            ImageGetter.getImage("OtherIcons/Mods"), 25f)
        addPage("Options", options,
            ImageGetter.getImage("OtherIcons/Settings"), 25f)
        selectPage(0)
    }
}
