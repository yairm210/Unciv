package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.toLabel

// This is a separate class, because it should be in use both in the New Game screen and the Map Editor screen
class MapParametersTable(val mapParameters: MapParameters): Table(){

    init {
        addMapTypeSelectBox()
        addWorldSizeSelectBox()
        addNoRuinsCheckbox()
        addNoNaturalWondersCheckbox()
    }

    private fun addMapTypeSelectBox() {
        add("{Map generation type}:".toLabel())

        val mapTypes = listOf(MapType.default, MapType.pangaea, MapType.continents, MapType.perlin)
        val mapTypeSelectBox = TranslatedSelectBox(mapTypes, mapParameters.type, CameraStageBaseScreen.skin)

        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.type=mapTypeSelectBox.selected.value
            }
        })
        add(mapTypeSelectBox).row()
    }


    private fun addWorldSizeSelectBox(){

        val worldSizeLabel = "{World size}:".toLabel()
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

        add(worldSizeLabel)
        add(worldSizeSelectBox).pad(10f).row()
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

    private fun addNoNaturalWondersCheckbox() {
        val noNaturalWondersCheckbox = CheckBox("No Natural Wonders".tr(), CameraStageBaseScreen.skin)
        noNaturalWondersCheckbox.isChecked = mapParameters.noNaturalWonders
        noNaturalWondersCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.noNaturalWonders = noNaturalWondersCheckbox.isChecked
            }
        })
        add(noNaturalWondersCheckbox).colspan(2).row()
    }
}