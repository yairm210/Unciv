package com.unciv.ui.mapeditor

import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.TabbedPager

class MapEditorMainTabs(
    editorScreen: MapEditorScreen
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
        prefWidth = editorScreen.getToolsWidth()

        addPage("View", view,
            ImageGetter.getImage("OtherIcons/Search"), 25f,
            shortcutKey = KeyCharAndCode.ctrl('i'))
        addPage("Generate", generate,
            ImageGetter.getImage("OtherIcons/New"), 25f,
            shortcutKey = KeyCharAndCode.ctrl('n'))
        addPage("Edit", edit,
            ImageGetter.getImage("OtherIcons/Terrains"), 25f,
            shortcutKey = KeyCharAndCode.ctrl('e'))
        addPage("Load", load,
            ImageGetter.getImage("OtherIcons/Load"), 25f,
            shortcutKey = KeyCharAndCode.ctrl('l'),
            disabled = load.noMapsAvailable())
        addPage("Save", save,
            ImageGetter.getImage("OtherIcons/Checkmark"), 25f,
            shortcutKey = KeyCharAndCode.ctrl('s'))
        addPage("Mods", mods,
            ImageGetter.getImage("OtherIcons/Mods"), 25f,
            shortcutKey = KeyCharAndCode.ctrl('d'))
        addPage("Options", options,
            ImageGetter.getImage("OtherIcons/Settings"), 25f,
            shortcutKey = KeyCharAndCode.ctrl('o'))
        selectPage(0)
    }
}
