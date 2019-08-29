package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.concurrent.thread

class Language(val language:String){
    val percentComplete:Int
    init{
        val availableTranslations = GameBasics.Translations.filter { it.value.containsKey(language) }
        if(language=="English") percentComplete = 100
        else percentComplete = (availableTranslations.size*100 / GameBasics.Translations.size)
    }
    override fun toString(): String {
        val spaceSplitLang = language.replace("_"," ")
        return "$spaceSplitLang- $percentComplete%"
    }
}

class WorldScreenOptionsTable(val worldScreen:WorldScreen) : PopupTable(worldScreen){
    var selectedLanguage: String = "English"

    init {
        update()
        open()
    }


    fun update() {
        val settings = UnCivGame.Current.settings
        settings.save()
        clear()

        val innerTable = PopupTable(screen) // cheating, to get the old code to fit inside a Scroll =)
        innerTable.background=null
        innerTable.add("Worked tiles".toLabel())
        if (settings.showWorkedTiles) innerTable.addButton("Hide") { settings.showWorkedTiles = false; update() }
        else innerTable.addButton("Show") { settings.showWorkedTiles = true; update() }

        innerTable.add("Resources and improvements".toLabel())
        if (settings.showResourcesAndImprovements)
            innerTable.addButton("Hide") { settings.showResourcesAndImprovements = false; update() }
        else innerTable.addButton("Show") { settings.showResourcesAndImprovements = true; update() }

        innerTable.add("Check for idle units".toLabel())
        innerTable.addButton(if(settings.checkForDueUnits) "Yes".tr() else "No".tr()) {
            settings.checkForDueUnits = !settings.checkForDueUnits
            update()
        }

        innerTable.add("Move units with a single tap".toLabel())
        innerTable.addButton(if(settings.singleTapMove) "Yes".tr() else "No".tr()) {
            settings.singleTapMove = !settings.singleTapMove
            update()
        }

        innerTable.add("Show tutorials".toLabel())
        innerTable.addButton(if(settings.showTutorials) "Yes".tr() else "No".tr()) {
            settings.showTutorials= !settings.showTutorials
            update()
        }

        innerTable.add("Auto-assign city production".toLabel())
        innerTable.addButton(if(settings.autoAssignCityProduction) "Yes".tr() else "No".tr()) {
            settings.autoAssignCityProduction= !settings.autoAssignCityProduction
            update()
        }

        addLanguageSelectBox(innerTable)

        addResolutionSelectBox(innerTable)

        addAutosaveTurnsSelectBox(innerTable)

        addTileSetSelectBox(innerTable)

        addSoundEffectsVolumeSlider(innerTable)

        innerTable.add("Version".toLabel())
        innerTable.add(UnCivGame.Current.version.toLabel()).row()

        addUsernameAndId(innerTable)

        val scrollPane = ScrollPane(innerTable,skin)
        scrollPane.setOverscroll(false,false)
        scrollPane.fadeScrollBars=false
        scrollPane.setScrollingDisabled(true,false)
        add(scrollPane).maxHeight(screen.stage.height*0.6f).row()

        addCloseButton()

        pack() // Needed to show the background.
        center(UnCivGame.Current.worldScreen.stage)
        UnCivGame.Current.worldScreen.shouldUpdate=true
    }

    private fun addUsernameAndId(innerTable: PopupTable) {
        innerTable.add("Username".toLabel())
        val userNameTextField = TextField(UnCivGame.Current.settings.userName, skin)
        userNameTextField.addListener {
            UnCivGame.Current.settings.userName = userNameTextField.text
            UnCivGame.Current.settings.save()
            true
        }
        innerTable.add(userNameTextField).row()


        innerTable.add("User Id".toLabel())
        val userIdButton = TextButton("Click to copy".tr(),skin)
        userIdButton.onClick { Gdx.app.clipboard.contents = UnCivGame.Current.settings.userId }
        innerTable.add(userIdButton).row()
    }

    private fun addSoundEffectsVolumeSlider(innerTable: PopupTable) {
        innerTable.add("Sound effects volume".tr())

        val soundEffectsVolumeSlider = Slider(0f, 1.0f, 0.1f, false, skin)
        soundEffectsVolumeSlider.value = UnCivGame.Current.settings.soundEffectsVolume
        soundEffectsVolumeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UnCivGame.Current.settings.soundEffectsVolume = soundEffectsVolumeSlider.value
                UnCivGame.Current.settings.save()
                Sounds.play("click")
            }
        })
        innerTable.add(soundEffectsVolumeSlider).row()
    }

    private fun addResolutionSelectBox(innerTable: PopupTable) {
        innerTable.add("Resolution".toLabel())

        val resolutionSelectBox = SelectBox<String>(skin)
        val resolutionArray = Array<String>()
        resolutionArray.addAll("900x600", "1050x700", "1200x800", "1500x1000")
        resolutionSelectBox.items = resolutionArray
        resolutionSelectBox.selected = UnCivGame.Current.settings.resolution
        innerTable.add(resolutionSelectBox).pad(10f).row()

        resolutionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UnCivGame.Current.settings.resolution = resolutionSelectBox.selected
                UnCivGame.Current.settings.save()
                UnCivGame.Current.worldScreen = WorldScreen(worldScreen.viewingCiv)
                UnCivGame.Current.setWorldScreen()
                WorldScreenOptionsTable(UnCivGame.Current.worldScreen)
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
        tileSetSelectBox.selected = UnCivGame.Current.settings.tileSet
        innerTable.add(tileSetSelectBox).pad(10f).row()

        tileSetSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UnCivGame.Current.settings.tileSet = tileSetSelectBox.selected
                UnCivGame.Current.settings.save()
                UnCivGame.Current.worldScreen = WorldScreen(worldScreen.viewingCiv)
                UnCivGame.Current.setWorldScreen()
                WorldScreenOptionsTable(UnCivGame.Current.worldScreen)
            }
        })
    }

    private fun addAutosaveTurnsSelectBox(innerTable: PopupTable) {
        innerTable.add("Turns between autosaves".toLabel())

        val autosaveTurnsSelectBox = SelectBox<Int>(skin)
        val autosaveTurnsArray = Array<Int>()
        autosaveTurnsArray.addAll(1,2,5,10)
        autosaveTurnsSelectBox.items = autosaveTurnsArray
        autosaveTurnsSelectBox.selected = UnCivGame.Current.settings.turnsBetweenAutosaves

        innerTable.add(autosaveTurnsSelectBox).pad(10f).row()

        autosaveTurnsSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UnCivGame.Current.settings.turnsBetweenAutosaves= autosaveTurnsSelectBox.selected
                UnCivGame.Current.settings.save()
                update()
            }
        })
    }

    private fun addLanguageSelectBox(innerTable: PopupTable) {
        innerTable.add("Language".toLabel())
        val languageSelectBox = SelectBox<Language>(skin)
        val languageArray = Array<Language>()
        GameBasics.Translations.getLanguages().map { Language(it) }.sortedByDescending { it.percentComplete }
                .forEach { languageArray.add(it) }
        languageSelectBox.items = languageArray
        languageSelectBox.selected = languageArray.first { it.language == UnCivGame.Current.settings.language }
        innerTable.add(languageSelectBox).pad(10f).row()

        languageSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                selectedLanguage = languageSelectBox.selected.language
                if (Fonts().containsFont(Fonts().getFontForLanguage(selectedLanguage)))
                    selectLanguage()
                else {
                    val spaceSplitLang = selectedLanguage.replace("_", " ")
                    YesNoPopupTable("This language requires you to download fonts.\n" +
                            "Do you want to download fonts for $spaceSplitLang?",
                            {

                                val downloading = PopupTable(screen)
                                downloading.add("Downloading...".toLabel())
                                downloading.open()
                                Gdx.input.inputProcessor = null // no interaction until download is over

                                thread {
                                    Fonts().downloadFontForLanguage(selectedLanguage)
                                    // The language selection must be done on the render thread, because it requires a GL context.
                                    // This means that we have to tell the table to create it on render.
                                    shouldSelectLanguage = true
                                }
                            })
                }
            }
        })

        if (languageSelectBox.selected.percentComplete != 100) {
            innerTable.add("Missing translations:".toLabel()).pad(5f).colspan(2).row()
            val missingTextSelectBox = SelectBox<String>(skin)
            val missingTextArray = Array<String>()
            val currentLanguage = UnCivGame.Current.settings.language
            GameBasics.Translations.filter { !it.value.containsKey(currentLanguage) }.forEach { missingTextArray.add(it.key) }
            missingTextSelectBox.items = missingTextArray
            missingTextSelectBox.selected = "Untranslated texts"
            innerTable.add(missingTextSelectBox).pad(10f).width(UnCivGame.Current.worldScreen.stage.width / 2).colspan(2).row()
        }
    }


    fun selectLanguage(){
        UnCivGame.Current.settings.language = selectedLanguage
        UnCivGame.Current.settings.save()

        CameraStageBaseScreen.resetFonts()

        UnCivGame.Current.worldScreen = WorldScreen(worldScreen.viewingCiv)
        UnCivGame.Current.setWorldScreen()
        WorldScreenOptionsTable(UnCivGame.Current.worldScreen)
    }

    var shouldSelectLanguage = false
    override fun draw(batch: Batch?, parentAlpha: Float) {
        if(shouldSelectLanguage){
            shouldSelectLanguage=false
            selectLanguage()
        }
        super.draw(batch, parentAlpha)
    }
}

