package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapType
import com.unciv.models.gamebasics.Ruleset
import com.unciv.models.gamebasics.VictoryType
import com.unciv.models.gamebasics.tech.TechEra
import com.unciv.models.gamebasics.tr
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSpeed
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.toLabel

class NewGameScreenOptionsTable(val newGameParameters: GameParameters,
                                val mapParameters: MapParameters,
                                val ruleset: Ruleset, val onMultiplayerToggled:()->Unit)
    : Table(CameraStageBaseScreen.skin){
    init{
        addMapTypeSizeAndFile()
        addDifficultySelectBox()
        addGameSpeedSelectBox()
        addEraSelectBox()
        addCityStatesSelectBox()
        addVictoryTypeCheckboxes()
        addBarbariansCheckbox()
        addOneCityChallengeCheckbox()
        addNoRuinsCheckbox()
        addIsOnlineMultiplayerCheckbox()

        // addModCheckboxes()

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

    private fun addNoRuinsCheckbox() {
        val noRuinsCheckbox = CheckBox("No ancient ruins".tr(), CameraStageBaseScreen.skin)
        noRuinsCheckbox.isChecked = mapParameters.noRuins
        noRuinsCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.noRuins = noRuinsCheckbox.isChecked
            }
        })
        add(noRuinsCheckbox).colspan(2).row()
    }    

    private fun addIsOnlineMultiplayerCheckbox() {

        val isOnlineMultiplayerCheckbox = CheckBox("Online Multiplayer".tr(), CameraStageBaseScreen.skin)
        isOnlineMultiplayerCheckbox.isChecked = newGameParameters.isOnlineMultiplayer
        isOnlineMultiplayerCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.isOnlineMultiplayer = isOnlineMultiplayerCheckbox.isChecked
                onMultiplayerToggled()
            }
        })
        add(isOnlineMultiplayerCheckbox).colspan(2).row()
    }

    private fun addMapTypeSizeAndFile() {
        add("{Map type}:".tr())
        val mapTypes = arrayListOf(MapType.default,MapType.continents,MapType.perlin,MapType.pangaea)
        if(MapSaver().getMaps().isNotEmpty()) mapTypes.add(MapType.custom)

        val mapFileLabel = "{Map file}:".toLabel()
        val mapFileSelectBox = getMapFileSelectBox()
        mapFileLabel.isVisible = false
        mapFileSelectBox.isVisible = false

        val mapTypeSelectBox = TranslatedSelectBox(mapTypes, mapParameters.type, CameraStageBaseScreen.skin)

        val worldSizeSelectBox = getWorldSizeSelectBox()
        val worldSizeLabel = "{World size}:".toLabel()

        fun updateOnMapTypeChange(){
            mapParameters.type = mapTypeSelectBox.selected.value
            if (mapParameters.type == MapType.custom) {
                worldSizeSelectBox.isVisible = false
                worldSizeLabel.isVisible = false
                mapFileSelectBox.isVisible = true
                mapFileLabel.isVisible = true
                mapParameters.name = mapFileSelectBox.selected
            } else {
                worldSizeSelectBox.isVisible = true
                worldSizeLabel.isVisible = true
                mapFileSelectBox.isVisible = false
                mapFileLabel.isVisible = false
                mapParameters.name = ""
            }
        }

        updateOnMapTypeChange() // activate once, so when we had a file map before we'll have the right things set for another one

        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                updateOnMapTypeChange()
            }
        })

        add(mapTypeSelectBox).pad(10f).row()


        add(worldSizeLabel)
        add(worldSizeSelectBox).pad(10f).row()

        add(mapFileLabel)
        add(mapFileSelectBox).pad(10f).row()
    }

    private fun addCityStatesSelectBox() {
        add("{Number of city-states}:".tr())
        val cityStatesSelectBox = SelectBox<Int>(CameraStageBaseScreen.skin)
        val cityStatesArray = Array<Int>()

        (0..ruleset.Nations.filter { it.value.isCityState() }.size).forEach { cityStatesArray.add(it) }
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
        val difficultySelectBox = TranslatedSelectBox(ruleset.Difficulties.keys, newGameParameters.difficulty, CameraStageBaseScreen.skin)
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

    private fun addEraSelectBox() {
        add("{Starting Era}:".tr())
        // The eras enum values are "Medieval" etc. but are shown to the player as "Medieval era".tr()
        // because in other languages "Medieval era" is one word
        val eraSelectBox = TranslatedSelectBox(TechEra.values().map { it.name+" era" }, newGameParameters.startingEra.name+" era", CameraStageBaseScreen.skin)
        eraSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.startingEra = TechEra.valueOf(eraSelectBox.selected.value.replace(" era",""))
            }
        })
        add(eraSelectBox).pad(10f).row()
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
        if (mapParameters.name in mapNames) mapFileSelectBox.selected = mapParameters.name

        mapFileSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.name = mapFileSelectBox.selected!!
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

        val currentWorldSizeName = worldSizeToRadius.entries
                .first { it.value == mapParameters.radius }.key
        val worldSizeSelectBox = TranslatedSelectBox(worldSizeToRadius.keys, currentWorldSizeName, CameraStageBaseScreen.skin)

        worldSizeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.radius = worldSizeToRadius[worldSizeSelectBox.selected.value]!!
            }
        })
        return worldSizeSelectBox
    }


    fun addModCheckboxes(){

        add("{Victory conditions}:".tr()).colspan(2).row()

        // Create a checkbox for each VictoryType existing
        val modCheckboxTable = Table().apply { defaults().pad(10f) }

        val mods = Gdx.files.local("mods")

        for(modFolder in mods.list()){
                if(modFolder.list().any { it.name()=="jsons" }) {
                    val ruleSet = Ruleset(false)

                    try {
                        val modRuleset = ruleSet.load(modFolder.path() + "/jsons")

                    }catch (ex:Exception){}
            }
        }

        add(modCheckboxTable).colspan(2).row()
    }

}

//
//class Mod(val name:String){
//    val ruleSet=Ruleset(false)
//
//    fun tryLoadRuleset(){
//        val folderPath="mods/$name"
//        val jsonsFolderLocation = folderPath+"/jsons"
//        if(Gdx.files.local(jsonsFolderLocation).exists())
//
//    }
//}