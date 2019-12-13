package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.concurrent.thread

class Language(val language:String, ruleset: Ruleset){
    val percentComplete:Int
    init{
        val availableTranslations = ruleset.Translations.count { it.value.containsKey(language) }
        if(language=="English") percentComplete = 100
        else percentComplete = (availableTranslations*100 / ruleset.Translations.size)
    }
    override fun toString(): String {
        val spaceSplitLang = language.replace("_"," ")
        return "$spaceSplitLang- $percentComplete%"
    }
}

class WorldScreenOptionsTable(val worldScreen:WorldScreen) : PopupTable(worldScreen){
    var selectedLanguage: String = "English"

    init {
        UncivGame.Current.settings.addCompletedTutorialTask("Open the options table")
        update()
        open()
    }


    fun update() {
        val settings = UncivGame.Current.settings
        settings.save()
        clear()

        val innerTable = PopupTable(screen) // cheating, to get the old code to fit inside a Scroll =)
        innerTable.background = null

        innerTable.add("Show worked tiles".toLabel())
        innerTable.addButton(if (settings.showWorkedTiles) "Yes".tr() else "No".tr()) {
            settings.showWorkedTiles= !settings.showWorkedTiles
            update()
        }

        innerTable.add("Show resources and improvements".toLabel())
        innerTable.addButton(if (settings.showResourcesAndImprovements) "Yes".tr() else "No".tr()) {
            settings.showResourcesAndImprovements = !settings.showResourcesAndImprovements
            update()
        }

        innerTable.add("Check for idle units".toLabel())
        innerTable.addButton(if (settings.checkForDueUnits) "Yes".tr() else "No".tr()) {
            settings.checkForDueUnits = !settings.checkForDueUnits
            update()
        }

        innerTable.add("Move units with a single tap".toLabel())
        innerTable.addButton(if (settings.singleTapMove) "Yes".tr() else "No".tr()) {
            settings.singleTapMove = !settings.singleTapMove
            update()
        }

        innerTable.add("Show tutorials".toLabel())
        innerTable.addButton(if (settings.showTutorials) "Yes".tr() else "No".tr()) {
            settings.showTutorials = !settings.showTutorials
            update()
        }


        innerTable.add("Auto-assign city production".toLabel())
        innerTable.addButton(if (settings.autoAssignCityProduction) "Yes".tr() else "No".tr()) {
            settings.autoAssignCityProduction = !settings.autoAssignCityProduction
            update()
        }

        innerTable.add("Auto-build roads".toLabel())
        innerTable.addButton(if (settings.autoBuildingRoads) "Yes".tr() else "No".tr()) {
            settings.autoBuildingRoads = !settings.autoBuildingRoads
            update()
        }

        innerTable.add("Show minimap".toLabel())
        innerTable.addButton(if (settings.showMinimap) "Yes".tr() else "No".tr()) {
            settings.showMinimap = !settings.showMinimap
            update()
        }

        innerTable.add("Show pixel units".toLabel())
        innerTable.addButton(if (settings.showPixelUnits) "Yes".tr() else "No".tr()) {
            settings.showPixelUnits = !settings.showPixelUnits
            update()
        }

        innerTable.add("Show pixel improvements".toLabel())
        innerTable.addButton(if (settings.showPixelImprovements) "Yes".tr() else "No".tr()) {
            settings.showPixelImprovements = !settings.showPixelImprovements
            update()
        }

        innerTable.add("Enable nuclear weapons".toLabel())
        innerTable.addButton(if (settings.nuclearWeaponEnabled) "Yes".tr() else "No".tr()) {
            settings.nuclearWeaponEnabled = !settings.nuclearWeaponEnabled
            update()
        }

        addLanguageSelectBox(innerTable)

        addResolutionSelectBox(innerTable)

        addAutosaveTurnsSelectBox(innerTable)

        addTileSetSelectBox(innerTable)

        addSoundEffectsVolumeSlider(innerTable)
        addMusicVolumeSlider(innerTable)

        innerTable.add("Version".toLabel())
        innerTable.add(UncivGame.Current.version.toLabel()).row()


        val scrollPane = ScrollPane(innerTable, skin)
        scrollPane.setOverscroll(false, false)
        scrollPane.fadeScrollBars = false
        scrollPane.setScrollingDisabled(true, false)
        add(scrollPane).maxHeight(screen.stage.height * 0.6f).row()

        addCloseButton()

        pack() // Needed to show the background.
        center(UncivGame.Current.worldScreen.stage)
        UncivGame.Current.worldScreen.shouldUpdate = true
    }


    private fun addSoundEffectsVolumeSlider(innerTable: PopupTable) {
        innerTable.add("Sound effects volume".tr())

        val soundEffectsVolumeSlider = Slider(0f, 1.0f, 0.1f, false, skin)
        soundEffectsVolumeSlider.value = UncivGame.Current.settings.soundEffectsVolume
        soundEffectsVolumeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UncivGame.Current.settings.soundEffectsVolume = soundEffectsVolumeSlider.value
                UncivGame.Current.settings.save()
                Sounds.play("click")
            }
        })
        innerTable.add(soundEffectsVolumeSlider).row()
    }

    private fun addMusicVolumeSlider(innerTable: PopupTable) {
        val musicLocation =Gdx.files.local(UncivGame.Current.musicLocation)
        if(musicLocation.exists()) {
            innerTable.add("Music volume".tr())

            val musicVolumeSlider = Slider(0f, 1.0f, 0.1f, false, skin)
            musicVolumeSlider.value = UncivGame.Current.settings.musicVolume
            musicVolumeSlider.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    UncivGame.Current.settings.musicVolume = musicVolumeSlider.value
                    UncivGame.Current.settings.save()
                    UncivGame.Current.music?.volume = 0.4f * musicVolumeSlider.value
                }
            })
            innerTable.add(musicVolumeSlider).row()
        }
        else{
            val downloadMusicButton = TextButton("Download music".tr(),CameraStageBaseScreen.skin)
            innerTable.add(downloadMusicButton).colspan(2).row()
            val errorTable = Table()
            innerTable.add(errorTable).colspan(2).row()

            downloadMusicButton.onClick {
                // So the whole game doesn't get stuck while downloading the file
                thread {
                    try {
                        downloadMusicButton.disable()
                        errorTable.clear()
                        errorTable.add("Downloading...".toLabel())
                        val file = DropBox().downloadFile("/Music/thatched-villagers.mp3")
                        musicLocation.write(file, false)
                        update()
                        UncivGame.Current.startMusic()
                    } catch (ex: Exception) {
                        errorTable.clear()
                        errorTable.add("Could not download music!".toLabel(Color.RED))
                    }
                }
            }
        }
    }

    private fun addResolutionSelectBox(innerTable: PopupTable) {
        innerTable.add("Resolution".toLabel())

        val resolutionSelectBox = SelectBox<String>(skin)
        val resolutionArray = Array<String>()
        resolutionArray.addAll("750x500","900x600", "1050x700", "1200x800", "1500x1000")
        resolutionSelectBox.items = resolutionArray
        resolutionSelectBox.selected = UncivGame.Current.settings.resolution
        innerTable.add(resolutionSelectBox).pad(10f).row()

        resolutionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UncivGame.Current.settings.resolution = resolutionSelectBox.selected
                UncivGame.Current.settings.save()
                UncivGame.Current.worldScreen = WorldScreen(worldScreen.viewingCiv)
                UncivGame.Current.setWorldScreen()
                WorldScreenOptionsTable(UncivGame.Current.worldScreen)
            }
        })
    }

    private fun addTileSetSelectBox(innerTable: PopupTable) {
        innerTable.add("Tileset".toLabel())

        val tileSetSelectBox = SelectBox<String>(skin)
        val tileSetArray = Array<String>()
        val tileSets = ImageGetter.atlas.regions.filter { it.name.startsWith("TileSets") }
                .map { it.name.split("/")[1] }.distinct()
        for(tileset in tileSets) tileSetArray.add(tileset)
        tileSetSelectBox.items = tileSetArray
        tileSetSelectBox.selected = UncivGame.Current.settings.tileSet
        innerTable.add(tileSetSelectBox).pad(10f).row()

        tileSetSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UncivGame.Current.settings.tileSet = tileSetSelectBox.selected
                UncivGame.Current.settings.save()
                UncivGame.Current.worldScreen = WorldScreen(worldScreen.viewingCiv)
                UncivGame.Current.setWorldScreen()
                WorldScreenOptionsTable(UncivGame.Current.worldScreen)
            }
        })
    }

    private fun addAutosaveTurnsSelectBox(innerTable: PopupTable) {
        innerTable.add("Turns between autosaves".toLabel())

        val autosaveTurnsSelectBox = SelectBox<Int>(skin)
        val autosaveTurnsArray = Array<Int>()
        autosaveTurnsArray.addAll(1,2,5,10)
        autosaveTurnsSelectBox.items = autosaveTurnsArray
        autosaveTurnsSelectBox.selected = UncivGame.Current.settings.turnsBetweenAutosaves

        innerTable.add(autosaveTurnsSelectBox).pad(10f).row()

        autosaveTurnsSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UncivGame.Current.settings.turnsBetweenAutosaves= autosaveTurnsSelectBox.selected
                UncivGame.Current.settings.save()
                update()
            }
        })
    }

    private fun addLanguageSelectBox(innerTable: PopupTable) {
        innerTable.add("Language".toLabel())
        val languageSelectBox = SelectBox<Language>(skin)
        val languageArray = Array<Language>()
        val ruleSet = worldScreen.gameInfo.ruleSet
        ruleSet.Translations.getLanguages().map { Language(it, ruleSet) }
                .sortedByDescending { it.percentComplete }
                .forEach { languageArray.add(it) }
        languageSelectBox.items = languageArray
        val matchingLanguage = languageArray.firstOrNull { it.language == UncivGame.Current.settings.language }
        languageSelectBox.selected = if (matchingLanguage != null) matchingLanguage else languageArray.first()
        innerTable.add(languageSelectBox).pad(10f).row()

        languageSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                // Sometimes the "changed" is triggered even when we didn't choose something that isn't the
                selectedLanguage = languageSelectBox.selected.language

                if(selectedLanguage!=UncivGame.Current.settings.language )
                    selectLanguage()
            }
        })

        if (languageSelectBox.selected.percentComplete != 100) {
            innerTable.add("Missing translations:".toLabel()).pad(5f).colspan(2).row()
            val missingTextSelectBox = SelectBox<String>(skin)
            val missingTextArray = Array<String>()
            val currentLanguage = UncivGame.Current.settings.language
            ruleSet.Translations.filter { !it.value.containsKey(currentLanguage) }
                    .forEach { missingTextArray.add(it.key) }
            missingTextSelectBox.items = missingTextArray
            missingTextSelectBox.selected = "Untranslated texts"
            innerTable.add(missingTextSelectBox).pad(10f)
                    .width(screen.stage.width / 2).colspan(2).row()
        }
    }

    fun selectLanguage(){
        UncivGame.Current.settings.language = selectedLanguage
        UncivGame.Current.settings.save()
        CameraStageBaseScreen.resetFonts() // to load chinese characters if necessary
        UncivGame.Current.worldScreen = WorldScreen(worldScreen.viewingCiv)
        UncivGame.Current.setWorldScreen()
        WorldScreenOptionsTable(UncivGame.Current.worldScreen)
    }
}
