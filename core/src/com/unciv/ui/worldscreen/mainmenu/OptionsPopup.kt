package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.MainMenuScreen
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.min
import com.badlogic.gdx.utils.Array as GdxArray
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class Language(val language:String, val percentComplete:Int){
    override fun toString(): String {
        val spaceSplitLang = language.replace("_"," ")
        return "$spaceSplitLang - $percentComplete%"
    }
}

class OptionsPopup(val previousScreen:CameraStageBaseScreen) : Popup(previousScreen) {
    private var selectedLanguage: String = "English"
    private val settings = previousScreen.game.settings
    private val optionsTable = Table(CameraStageBaseScreen.skin)
    private val resolutionArray = GdxArray(arrayOf("750x500", "900x600", "1050x700", "1200x800", "1500x1000"))

    init {
        settings.addCompletedTutorialTask("Open the options table")

        optionsTable.defaults().pad(2.5f)
        rebuildOptionsTable()

        val scrollPane = ScrollPane(optionsTable, skin)
        scrollPane.setOverscroll(false, false)
        scrollPane.fadeScrollBars = false
        scrollPane.setScrollingDisabled(true, false)
        add(scrollPane).maxHeight(screen.stage.height * 0.6f).row()

        addCloseButton {
            previousScreen.game.limitOrientationsHelper?.allowPortrait(settings.allowAndroidPortrait)
            if (previousScreen is WorldScreen)
                previousScreen.enableNextTurnButtonAfterOptions()
        }

        pack() // Needed to show the background.
        center(previousScreen.stage)
    }

    private fun addHeader(text: String) {
        optionsTable.add(text.toLabel(fontSize = 24)).colspan(2).padTop(if (optionsTable.cells.isEmpty) 0f else 20f).row()
    }

    private fun addYesNoRow(text: String, initialValue: Boolean, updateWorld: Boolean = false, action: ((Boolean) -> Unit)) {
        optionsTable.add(text.toLabel())
        val button = YesNoButton(initialValue, CameraStageBaseScreen.skin) {
            action(it)
            settings.save()
            if (updateWorld && previousScreen is WorldScreen)
                previousScreen.shouldUpdate = true
        }
        optionsTable.add(button).row()
    }

    private fun reloadWorldAndOptions() {
        settings.save()
        if (previousScreen is WorldScreen) {
            previousScreen.game.worldScreen = WorldScreen(previousScreen.gameInfo, previousScreen.viewingCiv)
            previousScreen.game.setWorldScreen()

        } else if (previousScreen is MainMenuScreen) {
            previousScreen.game.setScreen(MainMenuScreen())
        }
        (previousScreen.game.screen as CameraStageBaseScreen).openOptionsPopup()
    }

    private fun rebuildOptionsTable() {
        settings.save()
        optionsTable.clear()

        addHeader("Display options")

        addYesNoRow("Show worked tiles", settings.showWorkedTiles, true) { settings.showWorkedTiles = it }
        addYesNoRow("Show resources and improvements", settings.showResourcesAndImprovements, true) { settings.showResourcesAndImprovements = it }
        addYesNoRow("Show tile yields", settings.showTileYields, true) { settings.showTileYields = it } // JN
        addYesNoRow("Show tutorials", settings.showTutorials, true) { settings.showTutorials = it }
        addMinimapSizeSlider()

        addYesNoRow("Show pixel units", settings.showPixelUnits, true) { settings.showPixelUnits = it }
        addYesNoRow("Show pixel improvements", settings.showPixelImprovements, true) { settings.showPixelImprovements = it }

        addLanguageSelectBox()

        addResolutionSelectBox()

        addTileSetSelectBox()

        addYesNoRow("Continuous rendering", settings.continuousRendering) {
            settings.continuousRendering = it
            Gdx.graphics.isContinuousRendering = it
        }

        val continuousRenderingDescription = "When disabled, saves battery life but certain animations will be suspended"
        optionsTable.add(continuousRenderingDescription.toLabel(fontSize = 14)).colspan(2).padTop(20f).row()

        addHeader("Gameplay options")

        addYesNoRow("Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
        addYesNoRow("Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
        addYesNoRow("Auto-assign city production", settings.autoAssignCityProduction, true) {
            settings.autoAssignCityProduction = it
            if (it && previousScreen is WorldScreen &&
                    previousScreen.viewingCiv.isCurrentPlayer() && previousScreen.viewingCiv.playerType == PlayerType.Human) {
                previousScreen.gameInfo.currentPlayerCiv.cities.forEach { city ->
                    city.cityConstructions.chooseNextConstruction()
                }
            }
        }
        addYesNoRow("Auto-build roads", settings.autoBuildingRoads) { settings.autoBuildingRoads = it }
        addYesNoRow("Automated workers replace improvements", settings.automatedWorkersReplaceImprovements) { settings.automatedWorkersReplaceImprovements = it }
        addYesNoRow("Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }

        addAutosaveTurnsSelectBox()

        // at the moment the notification service only exists on Android
        addNotificationOptions()

        addHeader("Other options")


        addYesNoRow("{Show experimental world wrap for maps}\n{HIGHLY EXPERIMENTAL - YOU HAVE BEEN WARNED!}".tr(),
                settings.showExperimentalWorldWrap) {
            settings.showExperimentalWorldWrap = it
        }
        addYesNoRow("{Enable experimental religion in start games}\n{HIGHLY EXPERIMENTAL - UPDATES WILL BREAK SAVES!}".tr(),
                settings.showExperimentalReligion) {
            settings.showExperimentalReligion = it
        }


        if (previousScreen.game.limitOrientationsHelper != null) {
            addYesNoRow("Enable portrait orientation", settings.allowAndroidPortrait) {
                settings.allowAndroidPortrait = it
                // Note the following might close the options screen indirectly and delayed
                previousScreen.game.limitOrientationsHelper!!.allowPortrait(it)
            }
        }

        addSoundEffectsVolumeSlider()
        addMusicVolumeSlider()

        addTranslationGeneration()
        addModCheckerPopup()
        addSetUserId()

        optionsTable.add("Version".toLabel()).pad(10f)
        val versionLabel = previousScreen.game.version.toLabel()
        if (previousScreen.game.version[0] in '0'..'9')
            versionLabel.onClick {
                val url = "https://github.com/yairm210/Unciv/blob/master/changelog.md#" +
                        previousScreen.game.version.replace(".","")
                Gdx.net.openURI(url)
            }
        optionsTable.add(versionLabel).pad(10f).row()
    }

    private fun addMinimapSizeSlider() {
        optionsTable.add("Show minimap".tr())

        val minimapSliderLimit = (resolutionArray.indexOf(settings.resolution) + 1) *5f
        // each 1 point is effectively 10px per hexagon
        val minimapSlider = Slider(0f, minimapSliderLimit, 1f, false, skin)
        minimapSlider.value = if(settings.showMinimap) min(settings.minimapSize.toFloat(), minimapSliderLimit)
        else 0f
        minimapSlider.onChange {
            val size = minimapSlider.value.toInt()
            if (size == 0) settings.showMinimap = false
            else {
                settings.showMinimap = true
                settings.minimapSize = size
            }
            settings.save()
            Sounds.play(UncivSound.Slider)
            if (previousScreen is WorldScreen)
                previousScreen.shouldUpdate = true
        }
        optionsTable.add(minimapSlider).pad(10f).row()
    }

    private fun addSetUserId() {
        val idSetLabel = "".toLabel()
        val takeUserIdFromClipboardButton = "Take user ID from clipboard".toTextButton()
                .onClick {
                    try {
                        val clipboardContents = Gdx.app.clipboard.contents.trim()
                        UUID.fromString(clipboardContents)
                        YesNoPopup("Doing this will reset your current user ID to the clipboard contents - are you sure?",
                                {
                                    settings.userId = clipboardContents
                                    settings.save()
                                    idSetLabel.setFontColor(Color.WHITE).setText("ID successfully set!".tr())
                                }, previousScreen).open(true)
                        idSetLabel.isVisible = true
                    } catch (ex: Exception) {
                        idSetLabel.isVisible = true
                        idSetLabel.setFontColor(Color.RED).setText("Invalid ID!".tr())
                    }
                }
        optionsTable.add(takeUserIdFromClipboardButton).pad(5f).colspan(2).row()
        optionsTable.add(idSetLabel).colspan(2).row()
    }

    private fun addNotificationOptions() {
        if (Gdx.app.type == Application.ApplicationType.Android) {
            addHeader("Multiplayer options")

            addYesNoRow("Enable out-of-game turn notifications", settings.multiplayerTurnCheckerEnabled)
            { settings.multiplayerTurnCheckerEnabled = it }

            if (settings.multiplayerTurnCheckerEnabled) {
                addMultiplayerTurnCheckerDelayBox()

                addYesNoRow("Show persistent notification for turn notifier service", settings.multiplayerTurnCheckerPersistentNotificationEnabled)
                { settings.multiplayerTurnCheckerPersistentNotificationEnabled = it }
            }
        }
    }

    private fun addTranslationGeneration() {
        if (Gdx.app.type == Application.ApplicationType.Desktop) {
            val generateTranslationsButton = "Generate translation files".toTextButton()
            generateTranslationsButton.onClick {
                val translations = Translations()
                translations.readAllLanguagesTranslation()
                TranslationFileWriter.writeNewTranslationFiles(translations)
                // notify about completion
                generateTranslationsButton.setText("Translation files are generated successfully.".tr())
                generateTranslationsButton.disable()
            }
            optionsTable.add(generateTranslationsButton).colspan(2).row()
        }
    }

    private fun addModCheckerPopup() {
        //if (RulesetCache.isEmpty()) return
        val modCheckerButton = "Locate mod errors".toTextButton()
        modCheckerButton.onClick {
            val lines = ArrayList<String>()
            for (mod in RulesetCache.values) {
                val modLinks = mod.checkModLinks()
                if (modLinks.isNotOK()) {
                    lines += ""
                    lines += mod.name
                    lines += ""
                    lines += modLinks.message
                    lines += ""
                }
            }
            if (lines.isEmpty()) lines += "{No problems found}."
            val popup = Popup(screen)
            popup.name = "ModCheckerPopup"
            popup.add(ScrollPane(lines.joinToString("\n").toLabel()).apply { setOverscroll(false, false) })
                    .maxHeight(screen.stage.height / 2).row()
            popup.addCloseButton()
            popup.open(true)
        }
        optionsTable.add(modCheckerButton).colspan(2).row()
    }

    private fun addSoundEffectsVolumeSlider() {
        optionsTable.add("Sound effects volume".tr())

        val soundEffectsVolumeSlider = Slider(0f, 1.0f, 0.1f, false, skin)
        soundEffectsVolumeSlider.value = settings.soundEffectsVolume
        soundEffectsVolumeSlider.onChange {
            settings.soundEffectsVolume = soundEffectsVolumeSlider.value
            settings.save()
            Sounds.play(UncivSound.Slider)
        }
        optionsTable.add(soundEffectsVolumeSlider).pad(5f).row()
    }

    private fun addMusicVolumeSlider() {
        val musicLocation = Gdx.files.local(previousScreen.game.musicLocation)
        if (musicLocation.exists()) {
            optionsTable.add("Music volume".tr())

            val musicVolumeSlider = Slider(0f, 1.0f, 0.1f, false, skin)
            musicVolumeSlider.value = settings.musicVolume
            musicVolumeSlider.onChange {
                settings.musicVolume = musicVolumeSlider.value
                settings.save()

                val music = previousScreen.game.music
                if (music == null) // restart music, if it was off at the app start
                    thread(name = "Music") { previousScreen.game.startMusic() }

                music?.volume = 0.4f * musicVolumeSlider.value
            }
            optionsTable.add(musicVolumeSlider).pad(5f).row()
        } else {
            val downloadMusicButton = "Download music".toTextButton()
            optionsTable.add(downloadMusicButton).colspan(2).row()
            val errorTable = Table()
            optionsTable.add(errorTable).colspan(2).row()

            downloadMusicButton.onClick {
                downloadMusicButton.disable()
                errorTable.clear()
                errorTable.add("Downloading...".toLabel())

                // So the whole game doesn't get stuck while downloading the file
                thread(name = "Music") {
                    try {
                        val file = DropBox.downloadFile("/Music/thatched-villagers.mp3")
                        musicLocation.write(file, false)
                        Gdx.app.postRunnable {
                            rebuildOptionsTable()
                            previousScreen.game.startMusic()
                        }
                    } catch (ex: Exception) {
                        Gdx.app.postRunnable {
                            errorTable.clear()
                            errorTable.add("Could not download music!".toLabel(Color.RED))
                        }
                    }
                }
            }
        }
    }

    private fun addResolutionSelectBox() {
        optionsTable.add("Resolution".toLabel())

        val resolutionSelectBox = SelectBox<String>(skin)
        resolutionSelectBox.items = resolutionArray
        resolutionSelectBox.selected = settings.resolution
        optionsTable.add(resolutionSelectBox).minWidth(240f).pad(10f).row()

        resolutionSelectBox.onChange {
            settings.resolution = resolutionSelectBox.selected
            reloadWorldAndOptions()
        }
    }

    private fun addTileSetSelectBox() {
        optionsTable.add("Tileset".toLabel())

        val tileSetSelectBox = SelectBox<String>(skin)
        val tileSetArray = GdxArray<String>()
        val tileSets = ImageGetter.getAvailableTilesets()
        for (tileset in tileSets) tileSetArray.add(tileset)
        tileSetSelectBox.items = tileSetArray
        tileSetSelectBox.selected = settings.tileSet
        optionsTable.add(tileSetSelectBox).minWidth(240f).pad(10f).row()

        tileSetSelectBox.onChange {
            settings.tileSet = tileSetSelectBox.selected
            TileSetCache.assembleTileSetConfigs()
            reloadWorldAndOptions()
        }
    }

    private fun addAutosaveTurnsSelectBox() {
        optionsTable.add("Turns between autosaves".toLabel())

        val autosaveTurnsSelectBox = SelectBox<Int>(skin)
        val autosaveTurnsArray = GdxArray<Int>()
        autosaveTurnsArray.addAll(1, 2, 5, 10)
        autosaveTurnsSelectBox.items = autosaveTurnsArray
        autosaveTurnsSelectBox.selected = settings.turnsBetweenAutosaves

        optionsTable.add(autosaveTurnsSelectBox).pad(10f).row()

        autosaveTurnsSelectBox.onChange {
            settings.turnsBetweenAutosaves = autosaveTurnsSelectBox.selected
            settings.save()
        }
    }

    private fun addMultiplayerTurnCheckerDelayBox() {
        optionsTable.add("Time between turn checks out-of-game (in minutes)".toLabel())

        val checkDelaySelectBox = SelectBox<Int>(skin)
        val possibleDelaysArray = GdxArray<Int>()
        possibleDelaysArray.addAll(1, 2, 5, 15)
        checkDelaySelectBox.items = possibleDelaysArray
        checkDelaySelectBox.selected = settings.multiplayerTurnCheckerDelayInMinutes

        optionsTable.add(checkDelaySelectBox).pad(10f).row()

        checkDelaySelectBox.onChange {
            settings.multiplayerTurnCheckerDelayInMinutes = checkDelaySelectBox.selected
            settings.save()
        }
    }

    private fun addLanguageSelectBox() {
        val languageSelectBox = SelectBox<Language>(skin)
        val languageArray = GdxArray<Language>()
        previousScreen.game.translations.percentCompleteOfLanguages
                .map { Language(it.key, if (it.key == "English") 100 else it.value) }
                .sortedByDescending { it.percentComplete }
                .forEach { languageArray.add(it) }
        if (languageArray.size == 0) return

        optionsTable.add("Language".toLabel())
        languageSelectBox.items = languageArray
        val matchingLanguage = languageArray.firstOrNull { it.language == settings.language }
        languageSelectBox.selected = matchingLanguage ?: languageArray.first()
        optionsTable.add(languageSelectBox).minWidth(240f).pad(10f).row()

        languageSelectBox.onChange {
            // Sometimes the "changed" is triggered even when we didn't choose something
            selectedLanguage = languageSelectBox.selected.language

            if (selectedLanguage != settings.language)
                selectLanguage()
        }
    }

    private fun selectLanguage() {
        settings.language = selectedLanguage
        previousScreen.game.translations.tryReadTranslationForCurrentLanguage()
        reloadWorldAndOptions()
    }

}

/*
        This TextButton subclass helps to keep looks and behaviour of our Yes/No
        in one place, but it also helps keeping context for those action lambdas.

        Usage: YesNoButton(someSetting: Boolean, skin) { someSetting = it; sideEffects() }
 */
private fun Boolean.toYesNo(): String = (if (this) "Yes" else "No").tr()
private class YesNoButton(initialValue: Boolean, skin: Skin, action: (Boolean) -> Unit)
        : TextButton (initialValue.toYesNo(), skin ) {

    var value = initialValue
        private set(value) {
            field = value
            setText(value.toYesNo())
        }

    init {
        color = ImageGetter.getBlue()
        onClick {
            value = !value
            action.invoke(value)
        }
    }
}
