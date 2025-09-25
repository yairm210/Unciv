package com.unciv.ui.screens.mapeditorscreen.tabs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.logic.files.FileChooser
import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen
import com.unciv.ui.screens.mapeditorscreen.MapEditorWesnothImporter
import com.unciv.utils.Log

class MapEditorOptionsTab(
    private val editorScreen: MapEditorScreen
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val seedLabel = "".toLabel(Color.GOLD)
    private val copySeedButton = "Copy to clipboard".toTextButton()
    private val tileMatchGroup = ButtonGroup<CheckBox>()
    private val copyMapButton = "Copy to clipboard".toTextButton()
    private val pasteMapButton = "Load copied data".toTextButton()
    private val worldWrapCheckBox: CheckBox
    private val overlayFileButton= TextButton(null, BaseScreen.skin)
    private val overlayAlphaSlider: UncivSlider

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
        for (option in TileMatchFuzziness.entries) {
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
        pasteMapButton.onActivation { pasteHandler() }
        pasteMapButton.keyShortcuts.add(KeyCharAndCode.ctrl('v'))
        add(Table().apply {
            add(copyMapButton).padRight(15f)
            add(pasteMapButton)
        }).row()

        add("Import a Wesnoth map".toTextButton().onActivation {
            MapEditorWesnothImporter(editorScreen).onImportButtonClicked()
        })
        addSeparator(Color.GRAY)

        worldWrapCheckBox = "Current map: World Wrap".toCheckBox(editorScreen.tileMap.mapParameters.worldWrap) {
            editorScreen.setWorldWrap(it)
        }
        add(worldWrapCheckBox).growX().row()
        addSeparator(Color.GRAY)

        add("Overlay image".toLabel(Color.GOLD)).row()
        overlayFileButton.style = TextButton.TextButtonStyle(overlayFileButton.style)
        showOverlayFileName()
        overlayFileButton.onClick {
            // TODO - to allow accessing files *outside the app scope* on Android, switch to
            //  [UncivFiles.saverLoader] and teach PlatformSaverLoader to deliver a stream or
            //  ByteArray or PixMap instead of doing a text file load using system/JVM default encoding..
            //  Then we'd need to make a *managed* PixMap-based Texture out of that, because only
            //  managed will survive GL context loss automatically. Cespenar says "could get messy".
            FileChooser.createLoadDialog(stage, "Choose an image", editorScreen.overlayFile) {
                success: Boolean, file: FileHandle ->
                if (!success) return@createLoadDialog
                editorScreen.overlayFile = file
                showOverlayFileName()
            }.apply {
                filter = FileChooser.createExtensionFilter("png", "jpg", "jpeg")
            }.open()
        }
        add(overlayFileButton).fillX().row()

        overlayAlphaSlider = UncivSlider(0f, 1f, 0.05f, initial = editorScreen.overlayAlpha) {
            editorScreen.overlayAlpha = it
        }
        add(Table().apply {
            add("Overlay opacity:".toLabel(alignment = Align.left)).left()
            add(overlayAlphaSlider).right()
        }).row()
    }

    private fun copyHandler() {
        Gdx.app.clipboard.contents = MapSaver.mapToSavedString(editorScreen.getMapCloneForSave())
    }

    private fun pasteHandler() {
        try {
            val clipboardContentsString = Gdx.app.clipboard.contents.trim()
            val loadedMap = MapSaver.mapFromSavedString(clipboardContentsString)
            editorScreen.loadMap(loadedMap)
        } catch (ex: Exception) {
            Log.error("Could not load map", ex)
            ToastPopup("Could not load map!", editorScreen)
        }
    }

    private fun showOverlayFileName() = overlayFileButton.run {
        if (editorScreen.overlayFile == null) {
            setText("Click to choose a file".tr())
            style.fontColor.a = 0.5f
        } else {
            setText(editorScreen.overlayFile!!.path())
            style.fontColor.a = 1f
        }
    }

    /** Check whether we can flip world wrap without ruining geometry */
    private fun canChangeWorldWrap(): Boolean {
        val params = editorScreen.tileMap.mapParameters
        // Can't change for hexagonal at all, as non-ww must always have an odd number of columns and ww nust have an even number of columns
        if (params.shape != MapShape.rectangular) return false
        // Too small?
        if (params.mapSize.radius < MapSize.Tiny.radius) return false
        // Even-width rectangular have no problems, but that has not necessarily been saved in mapSize!
        if (params.mapSize.width % 2 == 0) return true
        // The recorded width may have been reduced to even by the TileMap constructor.
        // In such a case we allow turning WW off, and editorScreen.setWorldWrap will fix the width.
        return (params.worldWrap)
    }

    fun update() {
        pasteMapButton.isEnabled = Gdx.app.clipboard.hasContents()
        worldWrapCheckBox.isChecked = editorScreen.tileMap.mapParameters.worldWrap
        worldWrapCheckBox.isDisabled = !canChangeWorldWrap()
        showOverlayFileName()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        seedToCopy = editorScreen.tileMap.mapParameters.seed.toString()
        seedLabel.setText("Current map RNG seed: [$seedToCopy]".tr())
        update()
        overlayAlphaSlider.value = editorScreen.overlayAlpha
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        editorScreen.tileMatchFuzziness = tileMatchFuzziness
    }
}
