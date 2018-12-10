package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.center
import com.unciv.ui.worldscreen.WorldScreen

class Language(val language:String){
    val percentComplete:Int
    init{
        val availableTranslations = GameBasics.Translations.filter { it.value.containsKey(language) }
        if(language=="English") percentComplete = 100
        else percentComplete = (availableTranslations.size*100 / GameBasics.Translations.size)
    }
    override fun toString(): String {
        return "$language - $percentComplete%"
    }
}

class WorldScreenDisplayOptionsTable : PopupTable(){
    val languageSelectBox = SelectBox<Language>(CameraStageBaseScreen.skin)

    init {
        update()
    }



    fun update() {
        val settings = UnCivGame.Current.settings
        settings.save()
        clear()

        if (settings.showWorkedTiles) addButton("{Hide} {worked tiles}") { settings.showWorkedTiles = false; update() }
        else addButton("{Show} {worked tiles}") { settings.showWorkedTiles = true; update() }

        if (settings.showResourcesAndImprovements)
            addButton("{Hide} {resources and improvements}") { settings.showResourcesAndImprovements = false; update() }
        else addButton("{Show} {resources and improvements}") { settings.showResourcesAndImprovements = true; update() }


        val languageArray = com.badlogic.gdx.utils.Array<Language>()
        GameBasics.Translations.getLanguages().map { Language(it) }.sortedByDescending { it.percentComplete }
                .forEach { languageArray.add(it) }
        languageSelectBox.items = languageArray
        languageSelectBox.selected = languageArray.first { it.language== UnCivGame.Current.settings.language}
        add(languageSelectBox).pad(10f).row()

        languageSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                val selectedLanguage = languageSelectBox.selected.language
                if (Fonts().containsFont(Fonts().getFontForLanguage(selectedLanguage)))
                    selectLanguage()
                else {
                    YesNoPopupTable("This language requires you to download fonts.\n" +
                            "Do you want to download fonts for $selectedLanguage?",
                            {

                                val downloading = PopupTable()
                                downloading.add(Label("Downloading...", CameraStageBaseScreen.skin))
                                downloading.pack()
                                downloading.center(stage)
                                stage.addActor(downloading)
                                Gdx.input.inputProcessor = null // no interaction until download is over

                                kotlin.concurrent.thread {
                                    Fonts().downloadFontForLanguage(selectedLanguage)
                                    // The language selection must be done on the render thread, because it requires a GL context.
                                    // This means that we have to tell the table to create it on render.
                                    shouldSelectLanguage=true
                                }

                            })
                }
            }
        })

        if(languageSelectBox.selected.percentComplete!=100) {
            add(Label("Missing translations:", CameraStageBaseScreen.skin)).pad(5f).row()
            val missingTextSelectBox = SelectBox<String>(CameraStageBaseScreen.skin)
            val missingTextArray = com.badlogic.gdx.utils.Array<String>()
            val currentLanguage = UnCivGame.Current.settings.language
            GameBasics.Translations.filter { !it.value.containsKey(currentLanguage) }.forEach { missingTextArray.add(it.key) }
            missingTextSelectBox.items = missingTextArray
            missingTextSelectBox.selected = "Untranslated texts"
            add(missingTextSelectBox).pad(10f).width(UnCivGame.Current.worldScreen.stage.width / 2).row()
        }

        val resolutionSelectBox= SelectBox<String>(CameraStageBaseScreen.skin)
        val resolutionArray = com.badlogic.gdx.utils.Array<String>()
        resolutionArray.addAll("900x600","1050x700","1200x800","1500x1000")
        resolutionSelectBox.items = resolutionArray
        resolutionSelectBox.selected = UnCivGame.Current.settings.resolution
        add(resolutionSelectBox).pad(10f).row()

        resolutionSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                UnCivGame.Current.settings.resolution = resolutionSelectBox.selected
                UnCivGame.Current.settings.save()
                UnCivGame.Current.worldScreen = WorldScreen()
                UnCivGame.Current.setWorldScreen()
                UnCivGame.Current.worldScreen.stage.addActor(WorldScreenDisplayOptionsTable())
            }
        })

        addButton("Close"){ remove() }

        pack() // Needed to show the background.
        center(UnCivGame.Current.worldScreen.stage)
        UnCivGame.Current.worldScreen.shouldUpdate=true
    }


    fun selectLanguage(){
        UnCivGame.Current.settings.language = languageSelectBox.selected.language
        UnCivGame.Current.settings.save()

        CameraStageBaseScreen.resetFonts()

        UnCivGame.Current.worldScreen = WorldScreen()
        UnCivGame.Current.setWorldScreen()
        UnCivGame.Current.worldScreen.stage.addActor(WorldScreenDisplayOptionsTable())
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