package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.GameParameters
import com.unciv.GameSpeed
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.VictoryType
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.toLabel
import kotlin.math.min

class NewGameScreenOptionsTable(val newGameParameters: GameParameters, val updateNationTables: ()->Unit): Table(CameraStageBaseScreen.skin){
    init{
        addMapTypeSizeAndFile()
        addNumberOfHumansAndEnemies()
        addDifficultySelectBox()
        addGameSpeedSelectBox()
        addVictoryTypeCheckboxes()
        addBarbariansCheckbox()

        pack()
    }

    private fun addBarbariansCheckbox() {
        val noBarbariansCheckbox = CheckBox("No barbarians".tr(), CameraStageBaseScreen.skin)
        noBarbariansCheckbox.isChecked = newGameParameters.noBarbarians
        noBarbariansCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.noBarbarians = noBarbariansCheckbox.isChecked
            }
        })
        add(noBarbariansCheckbox).colspan(2).row()
    }

    private fun addMapTypeSizeAndFile() {
        add("{Map type}:".tr())
        val mapTypes = LinkedHashMap<String, MapType>()
        for (type in MapType.values()) {
            if (type == MapType.File && MapSaver().getMaps().isEmpty()) continue
            mapTypes[type.toString()] = type
        }

        val mapFileLabel = "{Map file}:".toLabel()
        val mapFileSelectBox = getMapFileSelectBox()
        mapFileLabel.isVisible = false
        mapFileSelectBox.isVisible = false

        val mapTypeSelectBox = TranslatedSelectBox(mapTypes.keys, newGameParameters.mapType.toString(), CameraStageBaseScreen.skin)

        val worldSizeSelectBox = getWorldSizeSelectBox()
        val worldSizeLabel = "{World size}:".toLabel()

        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapType = mapTypes[mapTypeSelectBox.selected.value]!!
                if (newGameParameters.mapType == MapType.File) {
                    worldSizeSelectBox.isVisible = false
                    worldSizeLabel.isVisible = false
                    mapFileSelectBox.isVisible = true
                    mapFileLabel.isVisible = true
                    newGameParameters.mapFileName = mapFileSelectBox.selected
                } else {
                    worldSizeSelectBox.isVisible = true
                    worldSizeLabel.isVisible = true
                    mapFileSelectBox.isVisible = false
                    mapFileLabel.isVisible = false
                    newGameParameters.mapFileName = null
                }
            }
        })
        add(mapTypeSelectBox).pad(10f).row()


        add(worldSizeLabel)
        add(worldSizeSelectBox).pad(10f).row()

        add(mapFileLabel)
        add(mapFileSelectBox).pad(10f).row()
    }

    private fun addNumberOfHumansAndEnemies() {
        add("{Number of human players}:".tr())
        val humanPlayers = SelectBox<Int>(CameraStageBaseScreen.skin)
        val humanPlayersArray = Array<Int>()
        (1..GameBasics.Nations.filter{ !it.value.isCityState() }.size).forEach { humanPlayersArray.add(it) }
        humanPlayers.items = humanPlayersArray
        humanPlayers.selected = newGameParameters.numberOfHumanPlayers
        add(humanPlayers).pad(10f).row()


        add("{Number of enemies}:".tr())
        val enemiesSelectBox = SelectBox<Int>(CameraStageBaseScreen.skin)
        val enemiesArray = Array<Int>()
        for (enemyNumber in 0 until GameBasics.Nations.filter{ !it.value.isCityState() }.size) {
            enemiesArray.add(enemyNumber)
        }
        enemiesSelectBox.items = enemiesArray
        enemiesSelectBox.selected = newGameParameters.numberOfEnemies
        add(enemiesSelectBox).pad(10f).row()

        addCityStatesSelectBox()

        humanPlayers.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfHumanPlayers = humanPlayers.selected
                removeExtraHumanNations(humanPlayers)

                val maxNumberOfEnemies = GameBasics.Nations.size - newGameParameters.numberOfHumanPlayers
                newGameParameters.numberOfEnemies = min(newGameParameters.numberOfEnemies, maxNumberOfEnemies)
                enemiesSelectBox.selected = newGameParameters.numberOfEnemies
            }
        })

        enemiesSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfEnemies = enemiesSelectBox.selected
                removeExtraHumanNations(humanPlayers)
            }
        })

    }

    private fun addCityStatesSelectBox() {
        add("{Number of city-states}:".tr())
        val cityStatesSelectBox = SelectBox<Int>(CameraStageBaseScreen.skin)
        val cityStatesArray = Array<Int>()
        (0..GameBasics.Nations.filter { it.value.isCityState() }.size).forEach { cityStatesArray.add(it) }
        cityStatesSelectBox.items = cityStatesArray
        cityStatesSelectBox.selected = newGameParameters.numberOfCityStates
        add(cityStatesSelectBox).pad(10f).row()
        cityStatesSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfCityStates = cityStatesSelectBox.selected
            }
        })
    }

    private fun addDifficultySelectBox() {
        add("{Difficulty}:".tr())
        val difficultySelectBox = TranslatedSelectBox(GameBasics.Difficulties.keys, newGameParameters.difficulty, CameraStageBaseScreen.skin)
        difficultySelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.difficulty = difficultySelectBox.selected.value
            }
        })
        add(difficultySelectBox).pad(10f).row()
    }

    private fun addGameSpeedSelectBox() {
        add("{Game Speed}:".tr())
        val gameSpeedSelectBox = TranslatedSelectBox(GameSpeed.values().map { it.name }, newGameParameters.gameSpeed.name, CameraStageBaseScreen.skin)
        gameSpeedSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.gameSpeed = GameSpeed.valueOf(gameSpeedSelectBox.selected.value)
            }
        })
        add(gameSpeedSelectBox).pad(10f).row()
    }


    private fun addVictoryTypeCheckboxes() {
        add("{Victory conditions}:".tr()).colspan(2).row()

        // Create a checkbox for each VictoryType existing
        var i = 0
        val victoryConditionsTable = Table().apply { defaults().pad(10f) }
        for (victoryType in VictoryType.values()) {
            if (victoryType == VictoryType.Neutral) continue
            val victoryCheckbox = CheckBox(victoryType.name.tr(), CameraStageBaseScreen.skin)
            victoryCheckbox.name = victoryType.name
            victoryCheckbox.isChecked = newGameParameters.victoryTypes.contains(victoryType)
            victoryCheckbox.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    // If the checkbox is checked, adds the victoryTypes else remove it
                    if (victoryCheckbox.isChecked) {
                        newGameParameters.victoryTypes.add(victoryType)
                    } else {
                        newGameParameters.victoryTypes.remove(victoryType)
                    }
                }
            })
            victoryConditionsTable.add(victoryCheckbox)
            if (++i % 2 == 0) victoryConditionsTable.row()
        }
        add(victoryConditionsTable).colspan(2).row()
    }

    private fun getMapFileSelectBox(): SelectBox<String> {
        val mapFileSelectBox = SelectBox<String>(CameraStageBaseScreen.skin)
        val mapNames = Array<String>()
        for (mapName in MapSaver().getMaps()) mapNames.add(mapName)
        mapFileSelectBox.items = mapNames

        mapFileSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapFileName = mapFileSelectBox.selected!!
            }
        })
        return mapFileSelectBox
    }

    private fun getWorldSizeSelectBox(): TranslatedSelectBox {
        val worldSizeToRadius = LinkedHashMap<String, Int>()
        worldSizeToRadius["Tiny"] = 10
        worldSizeToRadius["Small"] = 15
        worldSizeToRadius["Medium"] = 20
        worldSizeToRadius["Large"] = 30
        worldSizeToRadius["Huge"] = 40

        val currentWorldSizeName = worldSizeToRadius.entries.first { it.value == newGameParameters.mapRadius }.key
        val worldSizeSelectBox = TranslatedSelectBox(worldSizeToRadius.keys, currentWorldSizeName, CameraStageBaseScreen.skin)

        worldSizeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapRadius = worldSizeToRadius[worldSizeSelectBox.selected.value]!!
            }
        })
        return worldSizeSelectBox
    }


    fun removeExtraHumanNations(humanPlayers: SelectBox<Int>) {
        val maxNumberOfHumanPlayers = GameBasics.Nations.size - newGameParameters.numberOfEnemies
        if(newGameParameters.numberOfHumanPlayers>maxNumberOfHumanPlayers){
            newGameParameters.numberOfHumanPlayers=maxNumberOfHumanPlayers
            humanPlayers.selected=maxNumberOfHumanPlayers
        }
        if(newGameParameters.humanNations.size>newGameParameters.numberOfHumanPlayers) {
            val nationsOverAllowed = newGameParameters.humanNations.size - newGameParameters.numberOfHumanPlayers
            newGameParameters.humanNations.removeAll(newGameParameters.humanNations.take(nationsOverAllowed))
            updateNationTables()
        }
    }

}