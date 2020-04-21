package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

/** Table for editing [mapParameters]
 *
 *  This is a separate class, because it should be in use both in the New Game screen and the Map Editor screen
 *
 *  @param isEmptyMapAllowed whether the [MapType.empty] option should be present. Is used by the Map Editor, but should **never** be used with the New Game
 * */
class MapParametersTable(val mapParameters: MapParameters, val isEmptyMapAllowed: Boolean = false):
    Table() {
    lateinit var mapTypeSelectBox: TranslatedSelectBox
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
        addAdvancedSettings()
    }

    private fun addMapShapeSelectBox() {
        val mapShapes = listOfNotNull(
                MapShape.hexagonal,
                MapShape.rectangular
        )
        val mapShapeSelectBox =
                TranslatedSelectBox(mapShapes, mapParameters.shape, skin)
        mapShapeSelectBox.onChange {
                mapParameters.shape = mapShapeSelectBox.selected.value
            }

        add ("{Map shape}:".toLabel()).left()
        add(mapShapeSelectBox).fillX().row()
    }

    private fun addMapTypeSelectBox() {

        val mapTypes = listOfNotNull(
            MapType.default,
            MapType.pangaea,
            MapType.continents,
            MapType.perlin,
            MapType.archipelago,
            if (isEmptyMapAllowed) MapType.empty else null
        )

        mapTypeSelectBox = TranslatedSelectBox(mapTypes, mapParameters.type, skin)

        mapTypeSelectBox.onChange {
                mapParameters.type = mapTypeSelectBox.selected.value

                // If the map won't be generated, these options are irrelevant and are hidden
                noRuinsCheckbox.isVisible = mapParameters.type != MapType.empty
                noNaturalWondersCheckbox.isVisible = mapParameters.type != MapType.empty
            }

        add("{Map generation type}:".toLabel()).left()
        add(mapTypeSelectBox).fillX().row()
    }


    private fun addWorldSizeSelectBox() {
        val worldSizeSelectBox = TranslatedSelectBox(
            MapSize.values().map { it.name },
            mapParameters.size.name,
            skin
        )

        worldSizeSelectBox.onChange {
                mapParameters.size = MapSize.valueOf(worldSizeSelectBox.selected.value)
            }

        add("{World size}:".toLabel()).left()
        add(worldSizeSelectBox).fillX().row()
    }

    private fun addNoRuinsCheckbox() {
        noRuinsCheckbox = CheckBox("No ancient ruins".tr(), skin)
        noRuinsCheckbox.isChecked = mapParameters.noRuins
        noRuinsCheckbox.onChange { mapParameters.noRuins = noRuinsCheckbox.isChecked }
        add(noRuinsCheckbox).colspan(2).row()
    }

    private fun addNoNaturalWondersCheckbox() {
        noNaturalWondersCheckbox = CheckBox("No Natural Wonders".tr(), skin)
        noNaturalWondersCheckbox.isChecked = mapParameters.noNaturalWonders
        noNaturalWondersCheckbox.onChange {
            mapParameters.noNaturalWonders = noNaturalWondersCheckbox.isChecked
        }
        add(noNaturalWondersCheckbox).colspan(2).row()
    }

    private fun addAdvancedSettings() {
        val button = "Show advanced settings".toTextButton()
        val advancedSettingsTable = Table()
                .apply {isVisible = false; defaults().pad(5f)}

        add(button).colspan(2).row()
        val advancedSettingsCell = add(Table()).colspan(2)
        row()

        button.onClick {
            advancedSettingsTable.isVisible = !advancedSettingsTable.isVisible

            if (advancedSettingsTable.isVisible) {
                button.setText("Hide advanced settings".tr())
                advancedSettingsCell.setActor(advancedSettingsTable)
            } else {
                button.setText("Show advanced settings".tr())
                advancedSettingsCell.setActor(Table())
            }
        }

        val sliders = HashMap<Slider, ()->Float>()

        fun addSlider(text:String, getValue:()->Float, min:Float, max:Float, onChange: (value:Float)->Unit): Slider {
            val slider = Slider(min, max, (max - min) / 20, false, skin)
            slider.value = getValue()
            slider.onChange { onChange(slider.value) }
            advancedSettingsTable.add(text.toLabel()).left()
            advancedSettingsTable.add(slider).fillX().row()
            sliders[slider] = getValue
            return slider
        }

        addSlider("Map Height", {mapParameters.elevationExponent}, 0.6f,0.8f)
            {mapParameters.elevationExponent=it}

        addSlider("Temperature extremeness", {mapParameters.temperatureExtremeness}, 0.4f,0.8f)
            { mapParameters.temperatureExtremeness = it}

        addSlider("Resource richness", {mapParameters.resourceRichness},0f,0.5f)
            { mapParameters.resourceRichness=it }

        addSlider("Vegetation richness", {mapParameters.vegetationRichness}, 0f, 1f)
            { mapParameters.vegetationRichness=it }

        addSlider("Rare features richness", {mapParameters.rareFeaturesRichness}, 0f, 0.5f)
            { mapParameters.rareFeaturesRichness = it }

        addSlider("Max Coast extension", {mapParameters.maxCoastExtension.toFloat()}, 0f, 5f)
            { mapParameters.maxCoastExtension =it.toInt() }.apply { stepSize=1f }

        addSlider("Biome areas extension", {mapParameters.tilesPerBiomeArea.toFloat()}, 1f, 15f)
            { mapParameters.tilesPerBiomeArea = it.toInt() }.apply { stepSize=1f }

        addSlider("Water level", {mapParameters.waterThreshold}, -0.1f, 0.1f)
            { mapParameters.waterThreshold = it }

        val resetToDefaultButton = "Reset to default".toTextButton()
        resetToDefaultButton.onClick {
            mapParameters.resetAdvancedSettings()
            for(entry in sliders)
                entry.key.value = entry.value()
        }
        advancedSettingsTable.add(resetToDefaultButton).colspan(2).row()
    }
}