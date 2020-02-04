package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.tech.TechEra
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toLabel

class NewGameScreenOptionsTable(val newGameScreen: NewGameScreen, val updatePlayerPickerTable:()->Unit)
    : Table(CameraStageBaseScreen.skin) {
    val newGameParameters = newGameScreen.newGameParameters
    val mapParameters = newGameScreen.mapParameters
    val ruleset = newGameScreen.ruleset

    private var mapTypeSpecificTable = Table()
    private val generatedMapOptionsTable = MapParametersTable(mapParameters)
    private val savedMapOptionsTable = Table()

    init {
        pad(10f)
        top()
        defaults().pad(5f)
        add("Map options".toLabel(fontSize = 24)).colspan(2).row()
        addMapTypeSelection()

        add("Game options".toLabel(fontSize = 24)).padTop(20f).colspan(2).row()
        addDifficultySelectBox()
        addGameSpeedSelectBox()
        addEraSelectBox()
        addCityStatesSelectBox()
        addVictoryTypeCheckboxes()
        addBarbariansCheckbox()
        addOneCityChallengeCheckbox()
        addIsOnlineMultiplayerCheckbox()
        addModCheckboxes()

        pack()
    }

    private fun addMapTypeSelection() {
        add("{Map type}:".toLabel())
        val mapTypes = arrayListOf("Generated")
        if (MapSaver().getMaps().isNotEmpty()) mapTypes.add(MapType.custom)
        val mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", CameraStageBaseScreen.skin)

        val mapFileSelectBox = getMapFileSelectBox()
        savedMapOptionsTable.defaults().pad(5f)
        savedMapOptionsTable.add("{Map file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        savedMapOptionsTable.add(mapFileSelectBox).maxWidth(newGameScreen.stage.width / 2)
                .right().row()

        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            if (mapTypeSelectBox.selected.value == MapType.custom) {
                mapParameters.type = MapType.custom
                mapParameters.name = mapFileSelectBox.selected
                mapTypeSpecificTable.add(savedMapOptionsTable)
            } else {
                mapParameters.name = ""
                mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                mapTypeSpecificTable.add(generatedMapOptionsTable)
            }
        }

        // activate once, so when we had a file map before we'll have the right things set for another one
        updateOnMapTypeChange()

        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateOnMapTypeChange()
            }
        })

        add(mapTypeSelectBox).row()
        add(mapTypeSpecificTable).colspan(2).row()
    }


    private fun getMapFileSelectBox(): SelectBox<String> {
        val mapFileSelectBox = SelectBox<String>(CameraStageBaseScreen.skin)
        val mapNames = Array<String>()
        for (mapName in MapSaver().getMaps()) mapNames.add(mapName)
        mapFileSelectBox.items = mapNames
        if (mapParameters.name in mapNames) mapFileSelectBox.selected = mapParameters.name

        mapFileSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.name = mapFileSelectBox.selected!!
            }
        })
        return mapFileSelectBox
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

    private fun addOneCityChallengeCheckbox() {
        val oneCityChallengeCheckbox = CheckBox("One City Challenge".tr(), CameraStageBaseScreen.skin)
        oneCityChallengeCheckbox.isChecked = newGameParameters.oneCityChallenge
        oneCityChallengeCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.oneCityChallenge = oneCityChallengeCheckbox.isChecked
            }
        })
        add(oneCityChallengeCheckbox).colspan(2).row()
    }

    private fun addIsOnlineMultiplayerCheckbox() {

        val isOnlineMultiplayerCheckbox = CheckBox("Online Multiplayer".tr(), CameraStageBaseScreen.skin)
        isOnlineMultiplayerCheckbox.isChecked = newGameParameters.isOnlineMultiplayer
        isOnlineMultiplayerCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.isOnlineMultiplayer = isOnlineMultiplayerCheckbox.isChecked
                updatePlayerPickerTable()
            }
        })
        add(isOnlineMultiplayerCheckbox).colspan(2).row()
    }

    private fun addCityStatesSelectBox() {
        add("{Number of city-states}:".tr())
        val cityStatesSelectBox = SelectBox<Int>(CameraStageBaseScreen.skin)
        val cityStatesArray = Array<Int>()

        (0..ruleset.nations.filter { it.value.isCityState() }.size).forEach { cityStatesArray.add(it) }
        cityStatesSelectBox.items = cityStatesArray
        cityStatesSelectBox.selected = newGameParameters.numberOfCityStates
        add(cityStatesSelectBox).row()
        cityStatesSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfCityStates = cityStatesSelectBox.selected
            }
        })
    }

    private fun addDifficultySelectBox() {
        add("{Difficulty}:".tr())
        val difficultySelectBox = TranslatedSelectBox(ruleset.difficulties.keys, newGameParameters.difficulty, CameraStageBaseScreen.skin)
        difficultySelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.difficulty = difficultySelectBox.selected.value
            }
        })
        add(difficultySelectBox).fillX().row()
    }

    private fun addGameSpeedSelectBox() {
        add("{Game Speed}:".tr())
        val gameSpeedSelectBox = TranslatedSelectBox(GameSpeed.values().map { it.name }, newGameParameters.gameSpeed.name, CameraStageBaseScreen.skin)
        gameSpeedSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.gameSpeed = GameSpeed.valueOf(gameSpeedSelectBox.selected.value)
            }
        })
        add(gameSpeedSelectBox).fillX().row()
    }

    private fun addEraSelectBox() {
        add("{Starting Era}:".tr())
        // The eras enum values are "Medieval" etc. but are shown to the player as "Medieval era".tr()
        // because in other languages "Medieval era" is one word
        val eraSelectBox = TranslatedSelectBox(TechEra.values().map { it.name + " era" }, newGameParameters.startingEra.name + " era", CameraStageBaseScreen.skin)
        eraSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.startingEra = TechEra.valueOf(eraSelectBox.selected.value.replace(" era", ""))
            }
        })
        add(eraSelectBox).fillX().row()
    }


    private fun addVictoryTypeCheckboxes() {
        add("{Victory conditions}:".toLabel()).colspan(2).row()

        // Create a checkbox for each VictoryType existing
        var i = 0
        val victoryConditionsTable = Table().apply { defaults().pad(5f) }
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
            victoryConditionsTable.add(victoryCheckbox).left()
            if (++i % 2 == 0) victoryConditionsTable.row()
        }
        add(victoryConditionsTable).colspan(2).row()
    }


    fun addModCheckboxes() {
        val modRulesets = RulesetCache.filter { it.key!="" }.values
        if(modRulesets.isEmpty()) return

        fun reloadMods(){
            ruleset.clear()
            ruleset.add(RulesetCache.getComplexRuleset(newGameParameters.mods))
            ruleset.mods+=newGameParameters.mods

            ImageGetter.ruleset=ruleset
            ImageGetter.setTextureRegionDrawables()
        }

        add("{Mods}:".tr()).colspan(2).row()
        val modCheckboxTable = Table().apply { defaults().pad(5f) }
        for(mod in modRulesets){
            val checkBox = CheckBox(mod.name,CameraStageBaseScreen.skin)
            checkBox.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    if(checkBox.isChecked) newGameParameters.mods.add(mod.name)
                    else newGameParameters.mods.remove(mod.name)
                    reloadMods()
                    updatePlayerPickerTable()
                }
            })
            modCheckboxTable.add(checkBox).row()
        }

        add(modCheckboxTable).colspan(2).row()
    }

}
