package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Json
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class LoadScreen : PickerScreen() {
    lateinit var selectedSave:String

    init {
        val saveTable = Table()


        val deleteSaveButton = TextButton("Delete save".tr(), CameraStageBaseScreen.skin)
        deleteSaveButton .addClickListener {
            GameSaver().deleteSave(selectedSave)
            UnCivGame.Current.screen = LoadScreen()
        }
        deleteSaveButton.disable()
        rightSideGroup.addActor(deleteSaveButton)

        topTable.add(saveTable)
        val saves = GameSaver().getSaves()
        rightSideButton.setText("Load game".tr())
        saves.forEach {
            val textButton = TextButton(it,skin)
            textButton.addClickListener {
                selectedSave=it

                var textToSet = it

                val savedAt = Date(GameSaver().getSave(it).lastModified())
                textToSet+="\n{Saved at}: ".tr()+ SimpleDateFormat("dd-MM-yy HH.mm").format(savedAt)
                try{
                    val game = GameSaver().loadGame(it)
                    textToSet+="\n"+game.getPlayerCivilization()+", {Turn} ".tr()+game.turns
                }catch (ex:Exception){
                    textToSet+="\n{Could not load game}!".tr()
                }
                descriptionLabel.setText(textToSet)
                rightSideButton.setText("Load [$it]".tr())
                rightSideButton.enable()
                deleteSaveButton.enable()
                deleteSaveButton.color= Color.RED
            }
            saveTable.add(textButton).pad(5f).row()
        }

        val loadFromClipboardTable = Table()
        val loadFromClipboardButton = TextButton("Load copied data".tr(),skin)
        val errorLabel = Label("",skin).setFontColor(Color.RED)
        loadFromClipboardButton.addClickListener {
            try{
                val clipboardContentsString = Gdx.app.clipboard.contents
                val loadedGame = Json().fromJson(GameInfo::class.java, clipboardContentsString)
                loadedGame.setTransients()
                UnCivGame.Current.loadGame(loadedGame)
            }catch (ex:Exception){
                errorLabel.setText("Could not load game from clipboard!")
            }
        }

        loadFromClipboardTable.add(loadFromClipboardButton).row()
        loadFromClipboardTable.add(errorLabel)
        topTable.add(loadFromClipboardTable)

        rightSideButton.addClickListener {
            UnCivGame.Current.loadGame(selectedSave)
        }



    }

}

class EmpireOverviewScreen : CameraStageBaseScreen(){
    init {
        val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()

        val closeButton =TextButton("Close".tr(),skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton)

        addCityInfoTable(civInfo)
        addHappinessTable(civInfo)
        addGoldTable(civInfo)
    }

    private fun addHappinessTable(civInfo: CivilizationInfo) {
        val happinessTable = Table(skin)
        happinessTable.defaults().pad(5f)
        happinessTable.add(Label("Happiness", skin).setFont(24)).colspan(2).row()
        for (entry in civInfo.getHappinessForNextTurn()) {
            happinessTable.add(entry.key)
            happinessTable.add(entry.value.toString()).row()
        }
        happinessTable.add("Total")
        happinessTable.add(civInfo.getHappinessForNextTurn().values.sum().toString())
        happinessTable.pack()
        stage.addActor(happinessTable)
    }

    private fun addGoldTable(civInfo: CivilizationInfo) {
        val goldTable = Table(skin)
        goldTable.defaults().pad(5f)
        goldTable.add(Label("Gold", skin).setFont(24)).colspan(2).row()
        var total=0f
        for (entry in civInfo.getStatsForNextTurn()) {
            if(entry.value.gold==0f) continue
            goldTable.add(entry.key)
            goldTable.add(entry.value.gold.toString()).row()
            total += entry.value.gold
        }
        goldTable.add("Total")
        goldTable.add(total.toString())
        goldTable.pack()
        goldTable.y = stage.height/2
        stage.addActor(goldTable)
    }

    private fun addCityInfoTable(civInfo: CivilizationInfo) {
        val cityInfotable = Table()
        cityInfotable.skin = skin
        cityInfotable.defaults().pad(5f)
        cityInfotable.add(Label("Cities", skin).setFont(24)).colspan(8).row()
        cityInfotable.add()
        cityInfotable.add(ImageGetter.getStatIcon("Population")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Food")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Gold")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Science")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Production")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Culture")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Happiness")).size(20f).row()

        for (city in civInfo.cities) {
            cityInfotable.add(city.name)
            cityInfotable.add(city.population.population.toString())
            cityInfotable.add(city.cityStats.currentCityStats.food.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.gold.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.science.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.production.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.culture.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.happiness.roundToInt().toString()).row()
        }
        cityInfotable.add("Total")
        cityInfotable.add(civInfo.cities.sumBy { it.population.population }.toString())
        cityInfotable.add("")
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.gold.toInt() }.toString())
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.science.toInt() }.toString())
        cityInfotable.add("")
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.culture.toInt() }.toString())
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.happiness.toInt() }.toString())

        cityInfotable.pack()
        cityInfotable.setPosition(stage.width / 2, stage.height / 3)
        stage.addActor(cityInfotable)
    }
}