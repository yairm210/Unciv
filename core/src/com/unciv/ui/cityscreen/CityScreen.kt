package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.HexMath
import com.unciv.ui.utils.ImageGetter
import java.util.*

class CityScreen(internal val city: CityInfo) : CameraStageBaseScreen() {
    private var selectedTile: TileInfo? = null

    private var tileTable = Table()
    private var buildingsTable = BuildingsTable(this)
    private var cityStatsTable = CityStatsTable(this)
    private var statExplainer = Table(skin)
    private var cityPickerTable = Table()
    private var goToWorldButton = TextButton("Exit city", CameraStageBaseScreen.skin)
    private var tileGroups = ArrayList<CityTileGroup>()

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
        stage.addActor(statExplainer)
        stage.addActor(buildingsTableContainer)
        update()
        displayTutorials("CityEntered")
    }

    internal fun update() {
        buildingsTable.update()
        updateCityPickerTable()
        cityStatsTable.update()
        updateStatExplainer()
        updateGoToWorldButton()
        updateTileTable()
        updateTileGroups()
    }

    private fun updateStatExplainer() {
        statExplainer.defaults().pad(5f)
        statExplainer.clear()
        statExplainer.add()
        statExplainer.add("Production")
        statExplainer.add("Food")
        statExplainer.add("Science")
        statExplainer.add("Gold")
        statExplainer.add("Culture")

        for (entry in city.cityStats.baseStatList){
            if(entry.value.toHashMap().values.all { it==0f }) continue //irrelevant!
            statExplainer.row()
            statExplainer.add(entry.key)
            statExplainer.add(entry.value.production.toInt().toString())
            statExplainer.add(entry.value.food.toInt().toString())
            statExplainer.add(entry.value.science.toInt().toString())
            statExplainer.add(entry.value.gold.toInt().toString())
            statExplainer.add(entry.value.culture.toInt().toString())
        }
        statExplainer.pack()
        statExplainer.isTransform=true
        statExplainer.setScale(0.8f)
        statExplainer.setPosition(5f,cityStatsTable.top + 10)
    }


    private fun updateTileGroups() {
        val nextTile = city.expansion.getNewTile()
        for (HG in tileGroups) {
            HG.update()
            if(HG.tileInfo == nextTile){
                HG.showCircle(Color.PURPLE)
                HG.setColor(0f,0f,0f,0.7f)
            }
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
                game.worldScreen!!.tileMapHolder.setCenterPosition(city.location)
                dispose()
            }

        goToWorldButton.setSize(goToWorldButton.prefWidth, goToWorldButton.prefHeight)
        goToWorldButton.setPosition(10f, stage.height - goToWorldButton.height - 5f)
    }

    private fun addTiles() {
        val cityInfo = city

        val allTiles = Group()

        for (tileInfo in cityInfo.getCenterTile().getTilesInDistance(5)) {
            if (!city.civInfo.exploredTiles.contains(tileInfo.position)) continue // Don't even bother to display it.
            val group = CityTileGroup(cityInfo, tileInfo)
            group.addClickListener {
                    selectedTile = tileInfo
                    update()
                }

            if (tileInfo.getCity()!=city) {
                group.setColor(0f, 0f, 0f, 0.3f)
                group.yieldGroup.isVisible = false
            } else if (!tileInfo.isCityCenter()) {
                group.addPopulationIcon()
                group.populationImage!!.addClickListener {
                        if (!tileInfo.isWorked() && cityInfo.population.getFreePopulation() > 0)
                            cityInfo.workedTiles.add(tileInfo.position)
                        else if (tileInfo.isWorked()) cityInfo.workedTiles.remove(tileInfo.position)
                        cityInfo.cityStats.update()
                        update()
                    }
            }

            val positionalVector = HexMath.Hex2WorldCoords(tileInfo.position.cpy().sub(cityInfo.location))
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

