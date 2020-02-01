package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.ResourceLevel
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.toLabel

/** Table for editing [mapParameters]
 *
 *  This is a separate class, because it should be in use both in the New Game screen and the Map Editor screen
 *
 *  @param isEmptyMapAllowed whether the [MapType.empty] option should be present. Is used by the Map Editor, but should **never** be used with the New Game
 * */
class MapParametersTable(val mapParameters: MapParameters, val isEmptyMapAllowed: Boolean = false) :
    Table() {

    lateinit var noRuinsCheckbox: CheckBox
    lateinit var noNaturalWondersCheckbox: CheckBox

    init {
        addMapTypeSelectBox()
        addResourceLevelSelectBox()
        addWorldSizeSelectBox()
        addNoRuinsCheckbox()
        addNoNaturalWondersCheckbox()
    }

    private fun addResourceLevelSelectBox() {
        add("{Resource Level}:".toLabel())

        val resourceLevelSelectBox = TranslatedSelectBox(
                        ResourceLevel.values().map { it.name },
                        mapParameters.resourceLevel.name,
                        CameraStageBaseScreen.skin)

        resourceLevelSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.resourceLevel = ResourceLevel.valueOf(resourceLevelSelectBox.selected.value)
            }
        })

        add(resourceLevelSelectBox).pad(10f).row()
    }

    private fun addMapTypeSelectBox() {
        add("{Map generation type}:".toLabel())

        val mapTypes = listOfNotNull(
            MapType.default,
            MapType.pangaea,
            MapType.continents,
            MapType.perlin,
            if (isEmptyMapAllowed) MapType.empty else null
        )
        val mapTypeSelectBox =
            TranslatedSelectBox(mapTypes, mapParameters.type, CameraStageBaseScreen.skin)

        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.type = mapTypeSelectBox.selected.value

                // If the map won't be generated, these options are irrelevant and are hidden
                noRuinsCheckbox.isVisible = mapParameters.type != MapType.empty
                noNaturalWondersCheckbox.isVisible = mapParameters.type != MapType.empty
            }
        })
        add(mapTypeSelectBox).pad(10f).row()
    }


    private fun addWorldSizeSelectBox() {

        val worldSizeLabel = "{World size}:".toLabel()
        val worldSizeSelectBox = TranslatedSelectBox(
            MapSize.values().map { it.name },
            mapParameters.size.name,
            CameraStageBaseScreen.skin
        )

        worldSizeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.size = MapSize.valueOf(worldSizeSelectBox.selected.value)
            }
        })

        add(worldSizeLabel)
        add(worldSizeSelectBox).pad(10f).row()
    }

    private fun addNoRuinsCheckbox() {
        noRuinsCheckbox = CheckBox("No ancient ruins".tr(), CameraStageBaseScreen.skin)
        noRuinsCheckbox.isChecked = mapParameters.noRuins
        noRuinsCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.noRuins = noRuinsCheckbox.isChecked
            }
        })
        add(noRuinsCheckbox).colspan(2).row()
    }

    private fun addNoNaturalWondersCheckbox() {
        noNaturalWondersCheckbox = CheckBox("No Natural Wonders".tr(), CameraStageBaseScreen.skin)
        noNaturalWondersCheckbox.isChecked = mapParameters.noNaturalWonders
        noNaturalWondersCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.noNaturalWonders = noNaturalWondersCheckbox.isChecked
            }
        })
        add(noNaturalWondersCheckbox).colspan(2).row()
    }
}