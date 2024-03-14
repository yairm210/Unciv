package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.metadata.ModCategories
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.extensions.withoutItem
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Display
import com.unciv.utils.ScreenOrientation
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.zip.Deflater

fun advancedTab(
    optionsPopup: OptionsPopup,
    onFontChange: () -> Unit
) = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings

    addAutosaveTurnsSelectBox(this, settings)
    addSeparator(Color.GRAY)

    if (Display.hasOrientation())
        addOrientationSelectBox(this, optionsPopup)

    if (Display.hasCutout())
        addCutoutCheckbox(this, optionsPopup)

    if (Display.hasSystemUiVisibility())
        addHideSystemUiCheckbox(this, optionsPopup)

    addFontFamilySelect(this, settings, optionsPopup.selectBoxMinWidth, onFontChange)
    addFontSizeMultiplier(this, settings, onFontChange)
    addSeparator(Color.GRAY)

    addMaxZoomSlider(this, settings)

    addEasterEggsCheckBox(this, settings)

    addEnlargeNotificationsCheckBox(this, settings)
    addSeparator(Color.GRAY)

    addSetUserId(this, settings)

    addTranslationGeneration(this, optionsPopup)
}

private fun addCutoutCheckbox(table: Table, optionsPopup: OptionsPopup) {
    optionsPopup.addCheckbox(table, "Enable using display cutout areas", optionsPopup.settings.androidCutout)
    {
        optionsPopup.settings.androidCutout = it
        Display.setCutout(it)
        optionsPopup.reopenAfterDisplayLayoutChange()
    }
}

private fun addHideSystemUiCheckbox(table: Table, optionsPopup: OptionsPopup) {
    optionsPopup.addCheckbox(table, "Hide system status and navigation bars", optionsPopup.settings.androidHideSystemUi)
    {
        optionsPopup.settings.androidHideSystemUi = it
        Display.setSystemUiVisibility(hide = it)
        optionsPopup.reopenAfterDisplayLayoutChange()
    }
}

private fun addOrientationSelectBox(table: Table, optionsPopup: OptionsPopup) {

    val settings = optionsPopup.settings

    table.add("Screen orientation".toLabel()).left().fillX()

    val selectBox = SelectBox<ScreenOrientation>(table.skin)
    selectBox.items = Array(ScreenOrientation.values())
    selectBox.selected = settings.displayOrientation
    selectBox.onChange {
        val orientation = selectBox.selected
        settings.displayOrientation = orientation
        Display.setOrientation(orientation)
        optionsPopup.reopenAfterDisplayLayoutChange()
    }

    table.add(selectBox).minWidth(optionsPopup.selectBoxMinWidth).pad(10f).row()
}

private fun addAutosaveTurnsSelectBox(table: Table, settings: GameSettings) {
    table.add("Turns between autosaves".toLabel()).left().fillX()

    val autosaveTurnsSelectBox = SelectBox<Int>(table.skin)
    val autosaveTurnsArray = Array<Int>()
    autosaveTurnsArray.addAll(1, 2, 5, 10)
    autosaveTurnsSelectBox.items = autosaveTurnsArray
    autosaveTurnsSelectBox.selected = settings.turnsBetweenAutosaves

    table.add(autosaveTurnsSelectBox).pad(10f).row()

    autosaveTurnsSelectBox.onChange {
        settings.turnsBetweenAutosaves = autosaveTurnsSelectBox.selected
    }
}

private fun addFontFamilySelect(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onFontChange: () -> Unit) {
    table.add("Font family".toLabel()).left().fillX()
    val selectCell = table.add()
    table.row()

    fun loadFontSelect(fonts: Array<FontFamilyData>, selectCell: Cell<Actor>) {
        if (fonts.isEmpty) return

        val fontSelectBox = SelectBox<FontFamilyData>(table.skin)
        fontSelectBox.items = fonts

        // `FontFamilyData` implements kotlin equality contract such that _only_ the invariantName field is compared.
        // The Gdx SelectBox should honor that - but it doesn't, as it is a _kotlin_ thing to implement
        // `==` by calling `equals`, and there's precompiled _Java_ `==` in the widget code.
        // `setSelected` first calls a `contains` which can switch between using `==` and `equals` (set to `equals`)
        // but just one step later (where it re-checks whether the new selection is equal to the old one)
        // it does a hard `==`. Also, setSelection copies its argument to the selection var, it doesn't pull a match from `items`.
        // Therefore, _selecting_ an item in a `SelectBox` by an instance of `FontFamilyData` where only the `invariantName` is valid won't work properly.
        //
        // This is why it's _not_ `fontSelectBox.selected = FontFamilyData(settings.fontFamily)`
        val fontToSelect = settings.fontFamilyData
        fontSelectBox.selected = fonts.firstOrNull { it.invariantName == fontToSelect.invariantName } // will default to first entry if `null` is passed

        selectCell.setActor(fontSelectBox).minWidth(selectBoxMinWidth).pad(10f)

        fontSelectBox.onChange {
            settings.fontFamilyData = fontSelectBox.selected
            onFontChange()
        }
    }

    // This is a heavy operation and causes ANRs
    Concurrency.run("Add Font Select") {

        val fonts = Array<FontFamilyData>()

        // Add default font
        fonts.add(FontFamilyData.default)

        // Add mods fonts
        val modsDir = Gdx.files.local("mods/")
        for (mod in modsDir.list()) {

            // Not a dir, continue
            if (!mod.isDirectory)
                continue

            val modFontsDir = mod.child("fonts")

            // Mod doesn't have fonts, continue
            if (!modFontsDir.exists())
                continue

            // Find .ttf files and add construct FontFamilyData
            for (fontFile in modFontsDir.list()) {
                if (fontFile.extension().lowercase() == "ttf") {
                    fonts.add(
                        FontFamilyData(
                            "${fontFile.nameWithoutExtension()} (${mod.name()})",
                            fontFile.nameWithoutExtension(),
                            fontFile.path())
                    )
                }
            }

        }

        // Add system fonts
        for (font in Fonts.getSystemFonts())
            fonts.add(font)

        launchOnGLThread { loadFontSelect(fonts, selectCell) }
    }
}

private fun addFontSizeMultiplier(
    table: Table,
    settings: GameSettings,
    onFontChange: () -> Unit
) {
    table.add("Font size multiplier".toLabel()).left().fillX().padTop(5f)

    val fontSizeSlider = UncivSlider(
        0.7f, 1.5f, 0.05f,
        initial = settings.fontSizeMultiplier
    ) {
        settings.fontSizeMultiplier = it
    }
    fontSizeSlider.onChange {
        if (!fontSizeSlider.isDragging)
            onFontChange()
    }
    table.add(fontSizeSlider).pad(5f).padTop(10f).row()
}

private fun addMaxZoomSlider(table: Table, settings: GameSettings) {
    table.add("Max zoom out".tr()).left().fillX().padTop(5f)
    val maxZoomSlider = UncivSlider(
        2f, 6f, 1f,
        initial = settings.maxWorldZoomOut
    ) {
        settings.maxWorldZoomOut = it
        if (GUI.isWorldLoaded())
            GUI.getMap().reloadMaxZoom()
    }
    table.add(maxZoomSlider).pad(5f).padTop(10f).row()
}

private fun addTranslationGeneration(table: Table, optionsPopup: OptionsPopup) {
    if (Gdx.app.type != Application.ApplicationType.Desktop) return

    val generateTranslationsButton = "Generate translation files".toTextButton()

    generateTranslationsButton.onActivation {
        optionsPopup.tabs.selectPage("Advanced")  // only because key F12 works from any page
        generateTranslationsButton.setText(Constants.working.tr())
        Concurrency.run("WriteTranslations") {
            val result = TranslationFileWriter.writeNewTranslationFiles()
            launchOnGLThread {
                // notify about completion
                generateTranslationsButton.setText(result.tr())
                generateTranslationsButton.disable()
            }
        }
    }

    generateTranslationsButton.keyShortcuts.add(Input.Keys.F12)
    generateTranslationsButton.addTooltip("F12", 18f)
    table.add(generateTranslationsButton).colspan(2).row()

    val updateModCategoriesButton = "Update Mod categories".toTextButton()
    updateModCategoriesButton.onActivation {
        updateModCategoriesButton.setText(Constants.working.tr())
        Concurrency.run("GithubTopicQuery") {
            val result = ModCategories.mergeOnline()
            launchOnGLThread {
                updateModCategoriesButton.setText(result)
            }
        }
    }
    table.add(updateModCategoriesButton).colspan(2).row()

    if (!UncivGame.Current.files.getSave("ScreenshotGenerationGame").exists()) return

    val generateScreenshotsButton = "Generate screenshots".toTextButton()

    generateScreenshotsButton.onActivation {
        generateScreenshotsButton.setText(Constants.working.tr())
        Concurrency.run("GenerateScreenshot") {
            val extraImagesLocation = "../../extraImages"
            // I'm not sure why we need to advance the y by 2 for every screenshot... but that's the only way it remains centered
            generateScreenshots(optionsPopup.settings, arrayListOf(
                ScreenshotConfig(630, 500, ScreenSize.Medium, "$extraImagesLocation/itch.io image.png", Vector2(-2f, 2f),false),
                ScreenshotConfig(1280, 640, ScreenSize.Medium, "$extraImagesLocation/GithubPreviewImage.png", Vector2(-2f, 4f)),
                ScreenshotConfig(1024, 500, ScreenSize.Medium, "$extraImagesLocation/Feature graphic - Google Play.png",Vector2(-2f, 6f)),
                ScreenshotConfig(1024, 500, ScreenSize.Medium, "../../fastlane/metadata/android/en-US/images/featureGraphic.png",Vector2(-2f, 8f))
            ))
        }
    }

    table.add(generateScreenshotsButton).colspan(2).row()
}

data class ScreenshotConfig(val width: Int, val height: Int, val screenSize: ScreenSize, var fileLocation: String, var centerTile: Vector2, var attackCity: Boolean = true)

private fun CoroutineScope.generateScreenshots(settings: GameSettings, configs: ArrayList<ScreenshotConfig>) {
    val currentConfig = configs.first()
    launchOnGLThread {
        val screenshotGame =
                UncivGame.Current.files.loadGameByName("ScreenshotGenerationGame")
        settings.screenSize = currentConfig.screenSize
        val newScreen = UncivGame.Current.loadGame(screenshotGame)
        newScreen.stage.viewport.update(currentConfig.width, currentConfig.height, true)

        // Reposition mapholder and minimap whose position was based on the previous stage size...
        newScreen.mapHolder.setSize(newScreen.stage.width, newScreen.stage.height)
        newScreen.mapHolder.layout()
        newScreen.minimapWrapper.x = newScreen.stage.width - newScreen.minimapWrapper.width

        newScreen.mapHolder.setCenterPosition( // Center on the city
            currentConfig.centerTile,
            immediately = true,
            selectUnit = true
        )

        newScreen.mapHolder.onTileClicked(newScreen.mapHolder.tileMap[-2, 3]) // Then click on Keshik
        if (currentConfig.attackCity)
            newScreen.mapHolder.onTileClicked(newScreen.mapHolder.tileMap[-2, 2]) // Then click city again for attack table
        newScreen.mapHolder.zoomIn(true)
        withContext(Dispatchers.IO) {
            Thread.sleep(300)
            launchOnGLThread {
                val pixmap = Pixmap.createFromFrameBuffer(
                    0, 0,
                    currentConfig.width, currentConfig.height
                )
                PixmapIO.writePNG(
                    FileHandle(currentConfig.fileLocation),
                    pixmap,
                    Deflater.DEFAULT_COMPRESSION,
                    true
                )
                pixmap.dispose()
                val newConfigs = configs.withoutItem(currentConfig)
                if (newConfigs.isNotEmpty()) generateScreenshots(settings, newConfigs)
            }
        }
    }
}

private fun addSetUserId(table: Table, settings: GameSettings) {
    val idSetLabel = "".toLabel()
    val takeUserIdFromClipboardButton = "Take user ID from clipboard".toTextButton()
        .onClick {
            try {
                val clipboardContents = Gdx.app.clipboard.contents.trim()
                UUID.fromString(clipboardContents)
                ConfirmPopup(
                    table.stage,
                    "Doing this will reset your current user ID to the clipboard contents - are you sure?",
                    "Take user ID from clipboard"
                ) {
                    settings.multiplayer.userId = clipboardContents
                    idSetLabel.setFontColor(Color.WHITE).setText("ID successfully set!".tr())
                }.open(true)
                idSetLabel.isVisible = true
            } catch (_: Exception) {
                idSetLabel.isVisible = true
                idSetLabel.setFontColor(Color.RED).setText("Invalid ID!".tr())
            }
        }
    table.add(takeUserIdFromClipboardButton).pad(5f).colspan(2).row()
    table.add(idSetLabel).colspan(2).row()
}

private fun addEasterEggsCheckBox(table: Table, settings: GameSettings) {
    val checkbox = "Enable Easter Eggs".toCheckBox(settings.enableEasterEggs) { settings.enableEasterEggs = it }
    table.add(checkbox).colspan(2).row()
}

private fun addEnlargeNotificationsCheckBox(table: Table, settings: GameSettings) {
    val checkbox = "Enlarge selected notifications"
        .toCheckBox(settings.enlargeSelectedNotification) { settings.enlargeSelectedNotification = it }
    table.add(checkbox).colspan(2).row()
}
