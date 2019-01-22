package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.tr
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.round

class CityScreen(internal val city: CityInfo) : CameraStageBaseScreen() {
    private var selectedTile: TileInfo? = null

    private var tileTable = Table()
    private var cityInfoTable = CityInfoTable(this)
    private var constructionsTable = ConstructionsTable(this)
    private var cityPickerTable = Table()
    private var goToWorldButton = TextButton("Exit city".tr(), CameraStageBaseScreen.skin)
    private var tileGroups = ArrayList<CityTileGroup>()
    var topCityStatsTable=Table()

    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen(); dispose() }
        addTiles()

        val tableBackgroundColor = ImageGetter.getBlue().lerp(Color.BLACK,0.5f)


        var buildingsTableContainer = Table()
        buildingsTableContainer.pad(3f)
        buildingsTableContainer.background = ImageGetter.getBackground(tableBackgroundColor)
        cityInfoTable.update()
        val buildingsScroll = ScrollPane(cityInfoTable)
        buildingsTableContainer.add(buildingsScroll)
                .height(stage.height / 2)

        buildingsTableContainer = buildingsTableContainer.addBorder(2f, Color.WHITE)
        buildingsTableContainer.setPosition(stage.width - buildingsTableContainer.width-5,
                stage.height - buildingsTableContainer.height-5)

        stage.addActor(constructionsTable)
        stage.addActor(goToWorldButton)
        stage.addActor(cityPickerTable)
        stage.addActor(buildingsTableContainer)

        update()
        displayTutorials("CityEntered")
    }

    internal fun update() {
        cityInfoTable.update()
        updateCityPickerTable()
        constructionsTable.update()
        updateGoToWorldButton()
        updateTileTable()
        updateTileGroups()

        topCityStatsTable.remove()
        topCityStatsTable = getCityStatsTable()
        topCityStatsTable.setPosition(5f, stage.height-5-topCityStatsTable.height)
        stage.addActor(topCityStatsTable)

        if (city.getCenterTile().getTilesAtDistance(4).isNotEmpty()){
            displayTutorials("CityRange")
        }
    }

    private fun updateTileGroups() {
        val nextTile = city.expansion.chooseNewTileToOwn()
        for (tileGroup in tileGroups) {

            tileGroup.update()
            if(tileGroup.tileInfo == nextTile){
                tileGroup.showCircle(Color.PURPLE)
                tileGroup.setColor(0f,0f,0f,0.7f)
            }
        }

    }

    fun getCityStatsTable(): Table {
        val table=Table().pad(10f)
        table.defaults().pad(5f)
        table.background=ImageGetter.getBackground(Color.BLACK.cpy().apply { a=0.8f })
        val columns = Stats().toHashMap().size
        table.add(Label("{Free population}:".tr()
                +city.population.getFreePopulation().toString() + "/" + city.population.population,skin))
                .colspan(columns).row()

        val turnsToExpansionString : String
        if (city.cityStats.currentCityStats.culture > 0) {
            val turnsToExpansion = ceil((city.expansion.getCultureToNextTile() - city.expansion.cultureStored)
                    / city.cityStats.currentCityStats.culture).toInt()
            turnsToExpansionString = "[$turnsToExpansion] turns to expansion".tr()
        } else {
            turnsToExpansionString = "Stopped expansion"
        }
        table.add(Label(turnsToExpansionString + " (" + city.expansion.cultureStored + "/" + city.expansion.getCultureToNextTile() + ")",
                skin)).colspan(columns).row()

        val turnsToPopString : String
        if (city.cityStats.currentCityStats.food > 0) {
            val turnsToPopulation = ceil((city.population.getFoodToNextPopulation()-city.population.foodStored)
                    / city.cityStats.currentCityStats.food).toInt()
            turnsToPopString = "[$turnsToPopulation] turns to new population".tr()
        } else if (city.cityStats.currentCityStats.food < 0) {
            val turnsToStarvation = ceil(city.population.foodStored / -city.cityStats.currentCityStats.food).toInt()
            turnsToPopString = "[$turnsToStarvation] turns to lose population"
        } else {
            turnsToPopString = "Stopped population growth".tr()
        }
        table.add(Label(turnsToPopString + " (" + city.population.foodStored + "/" + city.population.getFoodToNextPopulation() + ")"
                ,skin)).colspan(columns).row()

        if (city.resistanceCounter > 0) {
            table.add(Label("In resistance for another [${city.resistanceCounter}] turns".tr(),skin)).colspan(columns).row()
        }

        table.addSeparator()
        val beige = colorFromRGB(194,180,131)
        for(stat in city.cityStats.currentCityStats.toHashMap()) {
            if(stat.key==Stat.Happiness) continue
            val minitable=Table().padRight(5f).padLeft(5f)
            minitable.add(ImageGetter.getStatIcon(stat.key.name)).size(20f).padRight(3f)
            minitable.add(Label(round(stat.value).toInt().toString(), CameraStageBaseScreen.skin))
            table.add(minitable)
        }
        return table.addBorder(2f, beige)
    }

    private fun updateCityPickerTable() {
        cityPickerTable.clear()

        val civInfo = city.civInfo
        if (civInfo.cities.size > 1) {
            val prevCityButton = TextButton("<", CameraStageBaseScreen.skin)
            prevCityButton.onClick {
                    val indexOfCity = civInfo.cities.indexOf(city)
                    val indexOfNextCity = if (indexOfCity == 0) civInfo.cities.size - 1 else indexOfCity - 1
                    game.screen = CityScreen(civInfo.cities[indexOfNextCity])
                    dispose()
                }
            cityPickerTable.add(prevCityButton).pad(20f)
        } else cityPickerTable.add()

        val cityNameTable = Table()
        if(city.isBeingRazed){
            val fireImage = ImageGetter.getImage("OtherIcons/Fire.png")
            cityNameTable.add(fireImage).size(20f).padRight(5f)
        }

        if(city.isCapital()){
            val starImage = Image(ImageGetter.getDrawable("OtherIcons/Star.png").tint(Color.LIGHT_GRAY))
            cityNameTable.add(starImage).size(20f).padRight(5f)
        }

        val currentCityLabel = Label(city.name+" ("+city.population.population+")", CameraStageBaseScreen.skin)
        currentCityLabel.setFontSize(25)
        cityNameTable.add(currentCityLabel)

        cityPickerTable.add(cityNameTable)


        if (civInfo.cities.size > 1) {
            val nextCityButton = TextButton(">", CameraStageBaseScreen.skin)
            nextCityButton.onClick {
                    val indexOfCity = civInfo.cities.indexOf(city)
                    val indexOfNextCity = if (indexOfCity == civInfo.cities.size - 1) 0 else indexOfCity + 1
                    game.screen = CityScreen(civInfo.cities[indexOfNextCity])
                    dispose()
                }
            cityPickerTable.add(nextCityButton).pad(20f)
        } else cityPickerTable.add()
        cityPickerTable.row()

        if(!city.isBeingRazed) {
            val razeCityButton = TextButton("Raze city".tr(), skin)
            razeCityButton.onClick { city.isBeingRazed=true; update() }
            cityPickerTable.add(razeCityButton).colspan(cityPickerTable.columns)
        }
        else{
            val stopRazingCityButton = TextButton("Stop razing city".tr(), skin)
            stopRazingCityButton.onClick { city.isBeingRazed=false; update() }
            cityPickerTable.add(stopRazingCityButton).colspan(cityPickerTable.columns)
        }

        cityPickerTable.pack()
        cityPickerTable.centerX(stage)
        stage.addActor(cityPickerTable)
    }

    private fun updateGoToWorldButton() {
        goToWorldButton.clearListeners()
        goToWorldButton.onClick {
            game.setWorldScreen()
            game.worldScreen.tileMapHolder.setCenterPosition(city.location)
            game.worldScreen.bottomBar.unitTable.selectedUnit=null
            dispose()
        }

        goToWorldButton.pad(5f)
        //goToWorldButton.setSize(goToWorldButton.prefWidth, goToWorldButton.prefHeight)
        goToWorldButton.centerX(stage)
        goToWorldButton.y = stage.height - goToWorldButton.height - 20
    }

    private fun addTiles() {
        val cityInfo = city

        val allTiles = Group()

        for (tileInfo in cityInfo.getCenterTile().getTilesInDistance(5)) {
            if (!city.civInfo.exploredTiles.contains(tileInfo.position)) continue // Don't even bother to display it.
            val tileGroup = CityTileGroup(cityInfo, tileInfo)
            val tilesInRange = city.getTilesInRange()

            // this needs to happen on update, because we can buy tiles, which changes the definition of the bought tiles...
            var shouldToggleTilesWorked = false
            when {
                tileInfo.getCity()!=city -> // outside of city
                    if(city.canAcquireTile(tileInfo)){
                        tileGroup.addAcquirableIcon()
                        tileGroup.yieldGroup.isVisible = false
                    } else {
                        tileGroup.setColor(0f, 0f, 0f, 0.3f)
                        tileGroup.yieldGroup.isVisible = false
                    }

                tileInfo !in tilesInRange -> // within city but not close enough to be workable
                    tileGroup.yieldGroup.isVisible = false

                !tileInfo.isCityCenter() && tileGroup.populationImage==null -> { // workable
                    tileGroup.addPopulationIcon()
                    shouldToggleTilesWorked=true
                }
            }

            tileGroup.onClick {
                selectedTile = tileInfo
                if (shouldToggleTilesWorked) {
                    if (!tileInfo.isWorked() && city.population.getFreePopulation() > 0)
                        city.workedTiles.add(tileInfo.position)
                    else if (tileInfo.isWorked()) city.workedTiles.remove(tileInfo.position)
                    city.cityStats.update()
                }
                update()
            }


            val positionalVector = HexMath().hex2WorldCoords(tileInfo.position.cpy().sub(cityInfo.location))
            val groupSize = 50
            tileGroup.setPosition(stage.width / 2 + positionalVector.x * 0.8f * groupSize.toFloat(),
                    stage.height / 2 + positionalVector.y * 0.8f * groupSize.toFloat())
            tileGroups.add(tileGroup)
            allTiles.addActor(tileGroup)
        }

        val scrollPane = ScrollPane(allTiles)
        scrollPane.setFillParent(true)
        scrollPane.setPosition(cityTilesX, cityTilesY)
        scrollPane.setOrigin(stage.width / 2, stage.height / 2)
        scrollPane.addListener(object : ActorGestureListener() {
            var lastScale = 1f
            var lastInitialDistance = 0f

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
                cityTilesX = scrollPane.x
                cityTilesY = scrollPane.y
            }
        })
        stage.addActor(scrollPane)
    }

    private fun updateTileTable() {
        tileTable.remove()
        if (selectedTile == null) return
        val tile = selectedTile!!
        tileTable.clearChildren()

        val stats = tile.getTileStats(city, city.civInfo)
        tileTable.pad(20f)

        tileTable.add(Label(tile.toString(), CameraStageBaseScreen.skin)).colspan(2)
        tileTable.row()

        val statsTable = Table()
        statsTable.defaults().pad(2f)
        for (entry in stats.toHashMap().filterNot { it.value==0f }) {
            statsTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
            statsTable.add(Label(Math.round(entry.value).toString(), CameraStageBaseScreen.skin))
            statsTable.row()
        }
        tileTable.add(statsTable).row()

        if(tile.getOwner()==null && tile.neighbors.any{it.getCity()==city}){
            val goldCostOfTile = city.expansion.getGoldCostOfTile(tile)
            val buyTileButton = TextButton("Buy for [$goldCostOfTile] gold".tr(),skin)
            buyTileButton.onClick("coin") { city.expansion.buyTile(tile); game.screen = CityScreen(city); dispose() }
            if(goldCostOfTile>city.civInfo.gold) buyTileButton.disable()
            tileTable.add(buyTileButton)
        }
        if(city.canAcquireTile(tile)){
            val acquireTileButton = TextButton("Acquire".tr(),skin)
            acquireTileButton.onClick { city.expansion.takeOwnership(tile); game.screen = CityScreen(city); dispose() }
            tileTable.add(acquireTileButton)
        }

        tileTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK,0.5f))
        tileTable=tileTable.addBorder(2f, Color.WHITE)
        tileTable.setPosition(stage.width - 5f - tileTable.width, 5f)
        stage.addActor(tileTable)
    }

    companion object {
        @Transient var cityTilesX = 0f
        @Transient var cityTilesY = 0f
    }
}