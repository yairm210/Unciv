package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.HexCoord
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.metadata.ModCategories
import com.unciv.platform.PlatformCapabilities
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.utils.Concurrency
import com.unciv.utils.delayMillis
import com.unciv.utils.Display
import com.unciv.utils.isUUID
import com.unciv.utils.launchOnGLThread
import com.unciv.utils.withThreadPoolContext
import com.unciv.utils.withoutItem
import kotlinx.coroutines.CoroutineScope
import java.util.zip.Deflater
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal class AdvancedTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    override fun lateInitialize() {
        addAutosaveField()
        addSelectBox("Turns between autosaves", settings::turnsBetweenAutosaves, listOf(1,2,5,10,20,50,100,1000))

        addSeparator()

        if (Display.hasCutout())
            addCutoutCheckbox()

        if (Display.hasSystemUiVisibility())
            addHideSystemUiCheckbox()

        addFontFamilySelect()
        addFontSizeMultiplier()
        addSeparator()

        addMaxZoomSlider()

        addCheckbox("Enable Easter Eggs", settings::enableEasterEggs)

        addCheckbox("Enlarge selected notifications", settings::enlargeSelectedNotification)
        addSeparator()

        if (PlatformCapabilities.current.onlineMultiplayer)
            addSetUserId()

        addTranslationGeneration()
        if (PlatformCapabilities.current.onlineModDownloads)
            addUpdateModCategories()
        addScreenhotGeneration()

        super.lateInitialize()
    }

    private fun addCutoutCheckbox() {
        addCheckbox("Enable using display cutout areas", settings::androidCutout) {
            Display.setCutout(it)
            reopenOptions()
            GUI.setUpdateWorldOnNextRender()
        }
    }

    private fun addHideSystemUiCheckbox() {
        addCheckbox("Hide system status and navigation bars", settings::androidHideSystemUi) {
            Display.setSystemUiVisibility(hide = it)
            reopenOptions()
        }
    }

    private fun addAutosaveField() {
        add("Number of autosave files stored".toLabel()).left().fillX()

        val autoSaveTurnsTextField = UncivTextField("", settings.maxAutosavesStored.toString())
        autoSaveTurnsTextField.maxLength = 8
        autoSaveTurnsTextField.setTextFieldFilter { _, c -> c in "1234567890" }

        val autoSaveTurnsTextFieldButton = "Enter".toTextButton()
        autoSaveTurnsTextFieldButton.isEnabled = false
        autoSaveTurnsTextFieldButton.onClick {
            autosaveFieldEnterClicked(autoSaveTurnsTextField.text)
            setAutosaveButtonState(autoSaveTurnsTextFieldButton, autoSaveTurnsTextField.text)
        }
        autoSaveTurnsTextField.onChange { setAutosaveButtonState(autoSaveTurnsTextFieldButton, autoSaveTurnsTextField.text) }

        val cell = addWrapped(newRow = false) {
            add(autoSaveTurnsTextField)
            add(autoSaveTurnsTextFieldButton).padLeft(5f)
        }
        cell.minWidth(rightWidgetMinWidth).right().row()
    }

    private fun setAutosaveButtonState(button: TextButton, text: String) {
        button.isEnabled = text.toIntOrNull() != settings.maxAutosavesStored
    }

    private fun autosaveFieldEnterClicked(text: String) {
        if (text.isEmpty()) return
        val numberAutosaveTurns = text.toInt()

        if (numberAutosaveTurns <= 0) {
            val popup = Popup(stage)
            popup.addGoodSizedLabel("Autosave turns must be larger than 0!", color = Color.RED)
            popup.addCloseButton()
            popup.open(true)
            return
        }

        settings.maxAutosavesStored = numberAutosaveTurns
        if (numberAutosaveTurns < 200) return

        val popup = Popup(stage)
        popup.addGoodSizedLabel(
            "Autosave turns over 200 may take a lot of space on your device.",
            color = Color.ORANGE)
        popup.addCloseButton()
        popup.open(true)
    }

    private fun addFontFamilySelect() {
        /** Build provider for [addAsyncSelectBox]: per-mod scan */
        fun loadModFonts(mod: FileHandle) = flow {
            if (!mod.exists() || !mod.isDirectory) return@flow
            val fontsPath = mod.child("fonts")
            if (!fontsPath.exists() || !fontsPath.isDirectory) return@flow
            for (file in fontsPath.list()) {
                if (file.extension().lowercase() != "ttf") continue
                emit(FontFamilyData(
                    "${file.nameWithoutExtension()} (${mod.name()})",
                    file.nameWithoutExtension(),
                    file.path()
                ))
            }
        }

        /** Build provider for [addAsyncSelectBox]: default, mods, system */
        fun loadFonts() = flow {
            // Add default font
            emit(FontFamilyData.default)
            // List mod fonts
            val modsDir = UncivGame.Current.files.getModsFolder()
            if (modsDir.exists() && modsDir.isDirectory) {
                for (mod in modsDir.list())
                    emitAll(loadModFonts(mod))
            }
            if (PlatformCapabilities.current.systemFontEnumeration) {
                // Add system fonts
                for (font in Fonts.getSystemFonts())
                    emit(font)
            }
        }

        addAsyncSelectBox("Font family", settings::fontFamilyData, ::loadFonts) { reloadWorldAndOptions() }
    }

    private fun addFontSizeMultiplier() {
        addSlider("Font size multiplier", settings::fontSizeMultiplier, 0.7f, 1.5f, 0.05f) {
            reloadWorldAndOptions()
        }
    }

    private fun addMaxZoomSlider() {
        addSlider("Max zoom out", settings::maxWorldZoomOut, 2f, 6f, 1f) {
            if (GUI.isWorldLoaded())
                GUI.getMap().reloadMaxZoom()
        }
    }

    private fun addTranslationGeneration() {
        if (Gdx.app.type != Application.ApplicationType.Desktop) return

        val generateTranslationsButton = "Generate translation files".toTextButton()

        generateTranslationsButton.onActivation {
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
        add(generateTranslationsButton).colspan(2).row()
    }

    private fun addUpdateModCategories() {
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
        add(updateModCategoriesButton).colspan(2).row()

    }

    private fun addScreenhotGeneration() {
        if (!UncivGame.Current.files.getSave("ScreenshotGenerationGame").exists()) return

        val generateScreenshotsButton = "Generate screenshots".toTextButton()

        generateScreenshotsButton.onActivation {
            generateScreenshotsButton.setText(Constants.working.tr())
            Concurrency.run("GenerateScreenshot") {
                val extraImagesLocation = "../../extraImages"
                // I'm not sure why we need to advance the y by 2 for every screenshot... but that's the only way it remains centered
                generateScreenshots(
                    settings, arrayListOf(
                        ScreenshotConfig(630, 500, ScreenSize.Medium, "$extraImagesLocation/itch.io image.png", HexCoord(-2, 2), false),
                        ScreenshotConfig(1280, 640, ScreenSize.Medium, "$extraImagesLocation/GithubPreviewImage.png", HexCoord(-2, 4)),
                        ScreenshotConfig(1024, 500, ScreenSize.Medium, "$extraImagesLocation/Feature graphic - Google Play.png", HexCoord(-2, 6)),
                        ScreenshotConfig(1024, 500, ScreenSize.Medium, "../../fastlane/metadata/android/en-US/images/featureGraphic.png", HexCoord(-2, 8))
                    )
                )
            }
        }

        add(generateScreenshotsButton).colspan(2).row()
    }

    data class ScreenshotConfig(
        val width: Int,
        val height: Int,
        val screenSize: ScreenSize,
        var fileLocation: String,
        var centerTile: HexCoord,
        var attackCity: Boolean = true
    )

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
            withThreadPoolContext {
                delayMillis(300)
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

    private fun addSetUserId() {
        val idSetLabel = "".toLabel()
        val takeUserIdFromClipboardButton = "Take user ID from clipboard".toTextButton().onClick {
            val clipboardContents = Gdx.app.clipboard.contents.trim()
            if (clipboardContents.isUUID()) {
                ConfirmPopup(
                    stage,
                    "Doing this will reset your current user ID to the clipboard contents - are you sure?",
                    "Take user ID from clipboard"
                ) {
                    settings.multiplayer.setUserId(clipboardContents)
                    idSetLabel.setFontColor(Color.WHITE).setText("ID successfully set!".tr())
                }.open(true)
                idSetLabel.isVisible = true
            } else {
                idSetLabel.isVisible = true
                idSetLabel.setFontColor(Color.RED).setText("Invalid ID!".tr())
            }
        }
        add(takeUserIdFromClipboardButton).pad(5f).colspan(2).row()
        add(idSetLabel).colspan(2).row()
    }
}
