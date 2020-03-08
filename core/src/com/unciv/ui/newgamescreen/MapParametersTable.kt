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

        mapTypeSelectBox = TranslatedSelectBox(mapTypes, mapParameters.type, skin)

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

    private fun addAdvancedSettings() {
        val button = TextButton("Show advanced settings".tr(), skin)
        val advancedSettingsTable = Table().apply {isVisible = false; defaults().pad(5f)}

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


        val averageHeightSlider = Slider(0f,1f,0.01f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.mountainProbability = this@apply.value
                }
            })
        }
        averageHeightSlider.value = mapParameters.mountainProbability
        advancedSettingsTable.add("Map Height".toLabel()).left()
        advancedSettingsTable.add(averageHeightSlider).fillX().row()


        val tempExtremeSlider = Slider(0f,1f,0.01f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.temperatureExtremeness = this@apply.value
                }
            })
        }
        tempExtremeSlider.value = mapParameters.temperatureExtremeness
        advancedSettingsTable.add("Temperature extremeness".toLabel()).left()
        advancedSettingsTable.add(tempExtremeSlider).fillX().row()


        val resourceRichnessSlider = Slider(0f,0.2f,0.01f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.resourceRichness = this@apply.value
                }
            })
        }
        resourceRichnessSlider.value = mapParameters.resourceRichness
        advancedSettingsTable.add("Resource richness".toLabel()).left()
        advancedSettingsTable.add(resourceRichnessSlider).fillX().row()

        val strategicResourceRichnessSlider = Slider(0f,0.2f,0.01f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.strategicResourceRichness = this@apply.value
                }
            })
        }
        strategicResourceRichnessSlider.value = mapParameters.strategicResourceRichness
        advancedSettingsTable.add("Strategic Resource richness".toLabel()).left()
        advancedSettingsTable.add(strategicResourceRichnessSlider).fillX().row()


        val terrainFeatureRichnessSlider = Slider(0f,1f,0.01f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.terrainFeatureRichness = this@apply.value
                }
            })
        }
        terrainFeatureRichnessSlider.value = mapParameters.terrainFeatureRichness
        advancedSettingsTable.add("Terrain Features richness".toLabel()).left()
        advancedSettingsTable.add(terrainFeatureRichnessSlider).fillX().row()


        val ruinsRichnessSlider = Slider(0f,10f,1f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.ruinsRichness = this@apply.value
                }
            })
        }
        ruinsRichnessSlider.value = mapParameters.ruinsRichness
        advancedSettingsTable.add("Ancient Ruin density".toLabel()).left()
        advancedSettingsTable.add(ruinsRichnessSlider).fillX().row()


        val maxCoastExtensionSlider = Slider(0f,5f,1f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.maxCoastExtension = this@apply.value.toInt()
                }
            })
        }
        maxCoastExtensionSlider.value = mapParameters.maxCoastExtension.toFloat()
        advancedSettingsTable.add("Max Coast extension".toLabel()).left()
        advancedSettingsTable.add(maxCoastExtensionSlider).fillX().row()


        val tilesPerBiomeAreaSlider = Slider(0f,15f,1f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.tilesPerBiomeArea = this@apply.value.toInt()
                }
            })
        }
        tilesPerBiomeAreaSlider.value = mapParameters.tilesPerBiomeArea.toFloat()
        advancedSettingsTable.add("Biome areas extension".toLabel()).left()
        advancedSettingsTable.add(tilesPerBiomeAreaSlider).fillX().row()


        val waterPercentSlider = Slider(0f,1f,0.01f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.waterProbability = this@apply.value
                }
            })
        }
        waterPercentSlider.value = mapParameters.waterProbability
        advancedSettingsTable.add("Water percent".toLabel()).left()
        advancedSettingsTable.add(waterPercentSlider).fillX().row()


        val landPercentSlider = Slider(0f,1f,0.01f, false, skin).apply {
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    mapParameters.landProbability = this@apply.value
                }
            })
        }
        landPercentSlider.value = mapParameters.landProbability
        advancedSettingsTable.add("Land percent".toLabel()).left()
        advancedSettingsTable.add(landPercentSlider).fillX().row()

        val resetToDefaultButton = TextButton("Reset to default".tr(), skin)
        resetToDefaultButton.onClick {
            mapParameters.resetAdvancedSettings()
            averageHeightSlider.value = mapParameters.mountainProbability
            tempExtremeSlider.value = mapParameters.temperatureExtremeness
            resourceRichnessSlider.value = mapParameters.resourceRichness
            strategicResourceRichnessSlider.value = mapParameters.strategicResourceRichness
            terrainFeatureRichnessSlider.value = mapParameters.terrainFeatureRichness
            ruinsRichnessSlider.value = mapParameters.ruinsRichness
            maxCoastExtensionSlider.value = mapParameters.maxCoastExtension.toFloat()
            tilesPerBiomeAreaSlider.value = mapParameters.tilesPerBiomeArea.toFloat()
            waterPercentSlider.value = mapParameters.waterProbability
            landPercentSlider.value = mapParameters.landProbability
        }
        advancedSettingsTable.add(resetToDefaultButton).colspan(2).row()
    }
}