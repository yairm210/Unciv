package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.HexMath
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.disable
import java.util.*

class CityScreen(internal val city: CityInfo) : CameraStageBaseScreen() {
    private var selectedTile: TileInfo? = null

    private var buttonScale = game.settings.buttonScale
    private var tileTable = Table()
    private var buildingsTable = BuildingsTable(this)
    private var cityStatsTable = Table()
    private var cityPickerTable = Table()
    private var goToWorldButton = TextButton("Exit city", CameraStageBaseScreen.skin)
    private var tileGroups = ArrayList<TileGroup>()

    init {
        Label("", CameraStageBaseScreen.skin).style.font.data.setScale(game.settings.labelScale)

        addTiles()
        stage.addActor(tileTable)

        val tileTableBackground = com.unciv.ui.utils.ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(Color(0x0040804f))
        tileTableBackground.minHeight = 0f
        tileTableBackground.minWidth = 0f
        tileTable.background = tileTableBackground

        val buildingsTableContainer = Table()
        buildingsTableContainer.pad(20f)
        buildingsTableContainer.background = tileTableBackground
        //BuildingsTableContainer.add(new Label("Buildings",skin)).row();
        buildingsTable.update()
        val buildingsScroll = ScrollPane(buildingsTable)
        buildingsTableContainer.add(buildingsScroll).height(stage.height / 2)

        buildingsTableContainer.pack()
        buildingsTableContainer.setPosition(stage.width - buildingsTableContainer.width,
                stage.height - buildingsTableContainer.height)

        cityStatsTable.background = tileTableBackground
        stage.addActor(cityStatsTable)
        stage.addActor(goToWorldButton)
        stage.addActor(cityPickerTable)
        stage.addActor(buildingsTableContainer)
        update()
        displayTutorials("CityEntered")
    }

    internal fun update() {
        buildingsTable.update()
        updateCityPickerTable()
        updateCityTable()
        updateGoToWorldButton()
        updateTileTable()
        updateTileGroups()
    }


    private fun updateTileGroups() {
        for (HG in tileGroups) {
            HG.update()
        }
    }

    private fun updateCityPickerTable() {
        cityPickerTable.clear()
        cityPickerTable.row().pad(20f)

        val civInfo = city.civInfo
        if (civInfo.cities.size > 1) {
            val prevCityButton = TextButton("<", CameraStageBaseScreen.skin)
            prevCityButton.addClickListener {
                    val indexOfCity = civInfo.cities.indexOf(city)
                    val indexOfNextCity = if (indexOfCity == 0) civInfo.cities.size - 1 else indexOfCity - 1
                    game.screen = CityScreen(civInfo.cities[indexOfNextCity])
                    dispose()
                }
            cityPickerTable.add(prevCityButton)
        }

        val currentCityLabel = Label(city.name, CameraStageBaseScreen.skin)
        currentCityLabel.setFontScale(2f)
        cityPickerTable.add(currentCityLabel)

        if (civInfo.cities.size > 1) {
            val nextCityButton = TextButton(">", CameraStageBaseScreen.skin)
            nextCityButton.addClickListener {
                    val indexOfCity = civInfo.cities.indexOf(city)
                    val indexOfNextCity = if (indexOfCity == civInfo.cities.size - 1) 0 else indexOfCity + 1
                    game.screen = CityScreen(civInfo.cities[indexOfNextCity])
                    dispose()
                }
            cityPickerTable.add(nextCityButton)
        }
        cityPickerTable.pack()
        cityPickerTable.setPosition(stage.width / 2 - cityPickerTable.width / 2, 0f)
        stage.addActor(cityPickerTable)
    }

    private fun updateGoToWorldButton() {
        goToWorldButton.clearListeners()
        goToWorldButton.addClickListener {
                game.setWorldScreen()
                game.worldScreen!!.tileMapHolder.setCenterPosition(city.cityLocation)
                dispose()
            }

        goToWorldButton.setSize(goToWorldButton.prefWidth, goToWorldButton.prefHeight)
        goToWorldButton.setPosition(10f, stage.height - goToWorldButton.height - 5f)
    }

    private fun addTiles() {
        val cityInfo = city

        val allTiles = Group()

        for (tileInfo in game.gameInfo.tileMap.getTilesInDistance(cityInfo.cityLocation, 5)) {
            if (!tileInfo.explored) continue // Don't even bother to display it.
            val group = CityTileGroup(cityInfo, tileInfo)
            group.addClickListener {
                    selectedTile = tileInfo
                    update()
                }

            if (!cityInfo.tilesInRange.contains(tileInfo) || tileInfo.workingCity != null && tileInfo.workingCity != cityInfo.name) {
                group.setColor(0f, 0f, 0f, 0.3f)
                group.yieldGroup.isVisible = false
            } else if (!tileInfo.isCityCenter) {
                group.addPopulationIcon()
                group.populationImage!!.addClickListener {
                        if (tileInfo.workingCity == null && cityInfo.population.freePopulation > 0)
                            tileInfo.workingCity = cityInfo.name
                        else if (cityInfo.name == tileInfo.workingCity) tileInfo.workingCity = null
                        cityInfo.cityStats.update()
                        update()
                    }
            }

            val positionalVector = HexMath.Hex2WorldCoords(tileInfo.position.cpy().sub(cityInfo.cityLocation))
            val groupSize = 50
            group.setPosition(stage.width / 2 + positionalVector.x * 0.8f * groupSize.toFloat(),
                    stage.height / 2 + positionalVector.y * 0.8f * groupSize.toFloat())
            tileGroups.add(group)
            allTiles.addActor(group)
        }

        val scrollPane = ScrollPane(allTiles)
        scrollPane.setFillParent(true)
        scrollPane.setPosition(game.settings.cityTilesX, game.settings.cityTilesY)
        scrollPane.setOrigin(stage.width / 2, stage.height / 2)
        scrollPane.addListener(object : ActorGestureListener() {
            var lastScale = 1f
            internal var lastInitialDistance = 0f

            override fun zoom(event: InputEvent?, initialDistance: Float, distance: Float) {
                if (lastInitialDistance != initialDistance) {
                    lastInitialDistance = initialDistance
                    lastScale = scrollPane.scaleX
                }
                val scale = Math.sqrt((distance / initialDistance).toDouble()).toFloat() * lastScale
                scrollPane.setScale(scale)
            }

            override fun pan(event: InputEvent?, x: Float, y: Float, deltaX: Float, deltaY: Float) {
                scrollPane.moveBy(deltaX * scrollPane.scaleX, deltaY * scrollPane.scaleX)
                game.settings.cityTilesX = scrollPane.x
                game.settings.cityTilesY = scrollPane.y
            }
        })
        stage.addActor(scrollPane)
    }

    private fun updateCityTable() {
        val stats = city.cityStats.currentCityStats
        cityStatsTable.pad(20f)
        cityStatsTable.columnDefaults(0).padRight(10f)
        cityStatsTable.clear()

        val cityStatsHeader = Label("City Stats", CameraStageBaseScreen.skin)

        cityStatsHeader.setFontScale(2f)
        cityStatsTable.add(cityStatsHeader).colspan(2).pad(10f)
        cityStatsTable.row()

        val cityStatsValues = LinkedHashMap<String, String>()
        cityStatsValues["Production"] = Math.round(stats.production).toString() + city.cityConstructions.getAmountConstructedText()
        cityStatsValues["Food"] = (Math.round(stats.food).toString()
                + " (" + city.population.foodStored + "/" + city.population.foodToNextPopulation + ")")
        cityStatsValues["Gold"] = Math.round(stats.gold).toString() + ""
        cityStatsValues["Science"] = Math.round(stats.science).toString() + ""
        cityStatsValues["Culture"] = (Math.round(stats.culture).toString()
                + " (" + city.expansion.cultureStored + "/" + city.expansion.cultureToNextTile + ")")
        cityStatsValues["Population"] = city.population.freePopulation.toString() + "/" + city.population.population

        for (key in cityStatsValues.keys) {
            cityStatsTable.add<Image>(com.unciv.ui.utils.ImageGetter.getStatIcon(key)).align(Align.right)
            cityStatsTable.add(Label(cityStatsValues[key], CameraStageBaseScreen.skin)).align(Align.left)
            cityStatsTable.row()
        }

        val buildingText = city.cityConstructions.getCityProductionTextForCityButton()
        val buildingPickButton = TextButton(buildingText, CameraStageBaseScreen.skin)
        buildingPickButton.addClickListener {
                game.screen = com.unciv.ui.pickerscreens.ConstructionPickerScreen(city)
                dispose()
            }

        buildingPickButton.label.setFontScale(buttonScale)
        cityStatsTable.add(buildingPickButton).colspan(2).pad(10f)
                .size(buildingPickButton.width * buttonScale, buildingPickButton.height * buttonScale)


        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        val construction = city.cityConstructions.getCurrentConstruction()
        if (!(construction is Building && construction.isWonder)) {
            cityStatsTable.row()
            val buildingGoldCost = construction.getGoldCost(city.civInfo.policies.getAdoptedPolicies())
            val buildingBuyButton = TextButton("Buy for \r\n$buildingGoldCost gold", CameraStageBaseScreen.skin)
            buildingBuyButton.addClickListener {
                    city.cityConstructions.purchaseBuilding(city.cityConstructions.currentConstruction)
                    update()
                }
            if (buildingGoldCost > city.civInfo.gold) {
                buildingBuyButton.disable()
            }
            buildingBuyButton.label.setFontScale(buttonScale)
            cityStatsTable.add(buildingBuyButton).colspan(2).pad(10f)
                    .size(buildingBuyButton.width * buttonScale, buildingBuyButton.height * buttonScale)
        }

        cityStatsTable.setPosition(10f, 10f)
        cityStatsTable.pack()
    }

    private fun updateTileTable() {
        if (selectedTile == null) return
        tileTable.clearChildren()

        val stats = selectedTile!!.getTileStats(city, city.civInfo)
        tileTable.pad(20f)
        tileTable.columnDefaults(0).padRight(10f)

        val cityStatsHeader = Label("Tile Stats", CameraStageBaseScreen.skin)
        cityStatsHeader.setFontScale(2f)
        tileTable.add(cityStatsHeader).colspan(2).pad(10f)
        tileTable.row()

        tileTable.add(Label(selectedTile!!.toString(), CameraStageBaseScreen.skin)).colspan(2)
        tileTable.row()

        for (entry in stats.toHashMap().filterNot { it.value==0f }) {
            tileTable.add<Image>(ImageGetter.getStatIcon(entry.key.toString())).align(Align.right)
            tileTable.add(Label(Math.round(entry.value).toString() + "", CameraStageBaseScreen.skin)).align(Align.left)
            tileTable.row()
        }


        tileTable.pack()

        tileTable.setPosition(stage.width - 10f - tileTable.width, 10f)
    }

}

