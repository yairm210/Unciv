package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

/** Table for editing [mapParameters]
 *
 *  This is a separate class, because it should be in use both in the New Game screen and the Map Editor screen
 *
 *  @param isEmptyMapAllowed whether the [MapType.empty] option should be present. Is used by the Map Editor, but should **never** be used with the New Game
 * */
class MapParametersTable(val mapParameters: MapParameters, val isEmptyMapAllowed: Boolean = false):
    Table() {

    lateinit var noRuinsCheckbox: CheckBox
    lateinit var noNaturalWondersCheckbox: CheckBox

    init {
        skin = CameraStageBaseScreen.skin
        defaults().pad(5f)
        addMapShapeSelectBox()
        addMapTypeSelectBox()
        addWorldSizeSelectBox()
        addNoRuinsCheckbox()
        addNoNaturalWondersCheckbox()
    }

    private fun addMapShapeSelectBox() {
        val mapShapes = listOfNotNull(
                MapShape.hexagonal,
                MapShape.rectangular
        )
        val mapShapeSelectBox =
                TranslatedSelectBox(mapShapes, mapParameters.shape, skin)
        mapShapeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.shape = mapShapeSelectBox.selected.value
            }
        })

        add ("{Map shape}:".toLabel()).left()
        add(mapShapeSelectBox).fillX().row()
    }

    private fun addMapTypeSelectBox() {

        val mapTypes = listOfNotNull(
            MapType.default,
            MapType.pangaea,
            MapType.continents,
            MapType.perlin,
            if (isEmptyMapAllowed) MapType.empty else null
        )
        val mapTypeSelectBox =
            TranslatedSelectBox(mapTypes, mapParameters.type, skin)

        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.type = mapTypeSelectBox.selected.value

                // If the map won't be generated, these options are irrelevant and are hidden
                noRuinsCheckbox.isVisible = mapParameters.type != MapType.empty
                noNaturalWondersCheckbox.isVisible = mapParameters.type != MapType.empty
            }
        })

        add("{Map generation type}:".toLabel()).left()
        add(mapTypeSelectBox).fillX().row()
    }


    private fun addWorldSizeSelectBox() {
        val worldSizeSelectBox = TranslatedSelectBox(
            MapSize.values().map { it.name },
            mapParameters.size.name,
            skin
        )

        worldSizeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.size = MapSize.valueOf(worldSizeSelectBox.selected.value)
            }
        })

        add("{World size}:".toLabel()).left()
        add(worldSizeSelectBox).fillX().row()
    }

    private fun addNoRuinsCheckbox() {
        noRuinsCheckbox = CheckBox("No ancient ruins".tr(), skin)
        noRuinsCheckbox.isChecked = mapParameters.noRuins
        noRuinsCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.noRuins = noRuinsCheckbox.isChecked
            }
        })
        add(noRuinsCheckbox).colspan(2).row()
    }

    private fun addNoNaturalWondersCheckbox() {
        noNaturalWondersCheckbox = CheckBox("No Natural Wonders".tr(), skin)
        noNaturalWondersCheckbox.isChecked = mapParameters.noNaturalWonders
        noNaturalWondersCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                mapParameters.noNaturalWonders = noNaturalWondersCheckbox.isChecked
            }
        })
        add(noNaturalWondersCheckbox).colspan(2).row()
    }
}