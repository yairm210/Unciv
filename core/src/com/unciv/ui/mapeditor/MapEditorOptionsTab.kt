package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.MapSaver
import com.unciv.models.translations.tr
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.TabbedPager
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toCheckBox
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton

class MapEditorOptionsTab(
    private val editorScreen: MapEditorScreen
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val seedLabel = "".toLabel(Color.GOLD)
    private val copySeedButton = "Copy to clipboard".toTextButton()
    private val tileMatchGroup = ButtonGroup<CheckBox>()
    private val copyMapButton = "Copy to clipboard".toTextButton()
    private val pasteMapButton = "Load copied data".toTextButton()

    private var seedToCopy = ""
    private var tileMatchFuzziness = TileMatchFuzziness.CompleteMatch

    enum class TileMatchFuzziness(val label: String) {
        CompleteMatch("Complete match"),
        NoImprovement("Except improvements"),
        BaseAndFeatures("Base and terrain features"),
        BaseTerrain("Base terrain only"),
        LandOrWater("Land or water only"),
    }
    init {
        top()
        defaults().pad(10f)

        add("Tile Matching Criteria".toLabel(Color.GOLD)).row()
        for (option in TileMatchFuzziness.values()) {
            val check = option.label.toCheckBox(option == tileMatchFuzziness)
            { tileMatchFuzziness = option }
            add(check).row()
            tileMatchGroup.add(check)
        }
        addSeparator(Color.GRAY)

        add(seedLabel).row()
        add(copySeedButton).row()
        copySeedButton.onClick {
            Gdx.app.clipboard.contents = seedToCopy
        }
        addSeparator(Color.GRAY)

        add("Map copy and paste".toLabel(Color.GOLD)).row()
        copyMapButton.onActivation { copyHandler() }
        copyMapButton.keyShortcuts.add(KeyCharAndCode.ctrl('c'))
        add(copyMapButton).row()
        pasteMapButton.onActivation { pasteHandler() }
        pasteMapButton.keyShortcuts.add(KeyCharAndCode.ctrl('v'))
        add(pasteMapButton).row()
    }

    private fun copyHandler() {
        Gdx.app.clipboard.contents = MapSaver.mapToSavedString(editorScreen.getMapCloneForSave())
    }

    private fun pasteHandler() {
        try {
            val clipboardContentsString = Gdx.app.clipboard.contents.trim()
            val loadedMap = MapSaver.mapFromSavedString(clipboardContentsString, checkSizeErrors = false)
            editorScreen.loadMap(loadedMap)
        } catch (ex: Exception) {
            ToastPopup("Could not load map!", editorScreen)
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        seedToCopy = editorScreen.tileMap.mapParameters.seed.toString()
        seedLabel.setText("Current map RNG seed: [$seedToCopy]".tr())
        pasteMapButton.isEnabled = Gdx.app.clipboard.hasContents()
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        editorScreen.tileMatchFuzziness = tileMatchFuzziness
    }
}
