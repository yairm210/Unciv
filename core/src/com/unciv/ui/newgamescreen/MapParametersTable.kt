package com.unciv.ui.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter.DigitsOnlyFilter
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapResources
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapGenerationRandomness
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UncivSlider
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.extensions.isNarrowerThan4to3
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toCheckBox
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton

/** Table for editing [mapParameters]
 *
 *  This is a separate class, because it should be in use both in the New Game screen and the Map Editor screen
 *
 *  @param forMapEditor whether the [MapType.empty] option should be present. Is used by the Map Editor, but should **never** be used with the New Game
 * */
class MapParametersTable(
    private val previousScreen: IPreviousScreen? = null,
    private val mapParameters: MapParameters,
    private val mapGeneratedMainType: String,
    private val forMapEditor: Boolean = false,
    private val sizeChangedCallback: (()->Unit)? = null
) : Table() {
    // These are accessed fom outside the class to read _and_ write values,
    // namely from MapOptionsTable, NewMapScreen and NewGameScreen
    lateinit var mapTypeSelectBox: TranslatedSelectBox
    lateinit var customMapSizeRadius: TextField
    lateinit var customMapWidth: TextField
    lateinit var customMapHeight: TextField

    private val randomness = MapGenerationRandomness()

    private lateinit var worldSizeSelectBox: TranslatedSelectBox
    private var customWorldSizeTable = Table ()
    private var hexagonalSizeTable = Table()
    private var rectangularSizeTable = Table()
    lateinit var resourceSelectBox: TranslatedSelectBox
    private lateinit var noRuinsCheckbox: CheckBox
    private lateinit var noNaturalWondersCheckbox: CheckBox
    private lateinit var worldWrapCheckbox: CheckBox
    private lateinit var seedTextField: TextField

    private lateinit var mapShapesOptionsValues: HashSet<String>
    private lateinit var mapTypesOptionsValues: HashSet<String>
    private lateinit var mapSizesOptionsValues: HashSet<String>
    private lateinit var mapResourcesOptionsValues: HashSet<String>

    // Keep references (in the key) and settings value getters (in the value) of the 'advanced' sliders
    // in a HashMap for reuse later - in the reset to defaults button. Better here as field than as closure.
    // A HashMap indexed on a Widget is problematic, as it does not define its own hashCode and equals
    // overrides nor is a Widget a data class. Seems to work anyway.
    private val advancedSliders = HashMap<UncivSlider, ()->Float>()

    init {
        update()
    }

    fun update() {
        clear()

        skin = BaseScreen.skin
        defaults().pad(5f, 10f)
        if (mapGeneratedMainType == MapGeneratedMainType.randomGenerated) {
            add("{Which options should be available to the random selection?}".toLabel()).colspan(2).grow().row()
        }
        addMapShapeSelectBox()
        addMapTypeSelectBox()
        addWorldSizeTable()
        addResourceSelectBox()
        addWrappedCheckBoxes()
        addAdvancedSettings()
    }

    fun reseed() {
        mapParameters.reseed()
        seedTextField.text = mapParameters.seed.toString()
    }

    private fun addMapShapeSelectBox() {
        val mapShapes = listOfNotNull(
            MapShape.hexagonal,
            MapShape.flatEarth,
            MapShape.rectangular
        )

        if (mapGeneratedMainType == MapGeneratedMainType.randomGenerated) {
            mapShapesOptionsValues = mapShapes.toHashSet()
            val optionsTable = MultiCheckboxTable("{Enabled Map Shapes}", "NewGameMapShapes", mapShapesOptionsValues) {
                if (mapShapesOptionsValues.isEmpty()) {
                    mapParameters.shape = mapShapes.random(randomness.RNG)
                } else {
                    mapParameters.shape = mapShapesOptionsValues.random(randomness.RNG)
                }
            }
            add(optionsTable).colspan(2).grow().row()
        } else {
            val mapShapeSelectBox =
                    TranslatedSelectBox(mapShapes, mapParameters.shape, skin)
            mapShapeSelectBox.onChange {
                mapParameters.shape = mapShapeSelectBox.selected.value
                updateWorldSizeTable()
            }

            add ("{Map Shape}:".toLabel()).left()
            add(mapShapeSelectBox).fillX().row()
        }
    }

    private fun addMapTypeSelectBox() {
        // MapType is not an enum so we can't simply enumerate. //todo: make it so!
        val mapTypes = listOfNotNull(
            MapType.pangaea,
            MapType.continentAndIslands,
            MapType.twoContinents,
            MapType.threeContinents,
            MapType.fourCorners,
            MapType.archipelago,
            MapType.innerSea,
            if (forMapEditor && mapGeneratedMainType != MapGeneratedMainType.randomGenerated) MapType.empty else null
        )

        if (mapGeneratedMainType == MapGeneratedMainType.randomGenerated) {
            mapTypesOptionsValues = mapTypes.toHashSet()
            val optionsTable = MultiCheckboxTable("{Enabled Map Generation Types}", "NewGameMapGenerationTypes", mapTypesOptionsValues) {
                if (mapTypesOptionsValues.isEmpty()) {
                    mapParameters.type = mapTypes.random(randomness.RNG)
                } else {
                    mapParameters.type = mapTypesOptionsValues.random(randomness.RNG)
                }
            }
            add(optionsTable).colspan(2).grow().row()
        } else {
            mapTypeSelectBox = TranslatedSelectBox(mapTypes, mapParameters.type, skin)

            mapTypeSelectBox.onChange {
                mapParameters.type = mapTypeSelectBox.selected.value

                // If the map won't be generated, these options are irrelevant and are hidden
                noRuinsCheckbox.isVisible = mapParameters.type != MapType.empty
                noNaturalWondersCheckbox.isVisible = mapParameters.type != MapType.empty
            }

            add("{Map Generation Type}:".toLabel()).left()
            add(mapTypeSelectBox).fillX().row()
        }
    }

    private fun addWorldSizeTable() {
        if (mapGeneratedMainType == MapGeneratedMainType.randomGenerated) {
            val mapSizes = MapSize.values().map { it.name }
            mapSizesOptionsValues = mapSizes.toHashSet()
            val optionsTable = MultiCheckboxTable("{Enabled World Sizes}", "NewGameWorldSizes", mapSizesOptionsValues) {
                if (mapSizesOptionsValues.isEmpty()) {
                    mapParameters.mapSize = MapSizeNew(mapSizes.random(randomness.RNG))
                } else {
                    mapParameters.mapSize = MapSizeNew(mapSizesOptionsValues.random(randomness.RNG))
                }
            }
            add(optionsTable).colspan(2).grow().row()
        } else {
            val mapSizes = MapSize.values().map { it.name } + listOf(MapSize.custom)
            worldSizeSelectBox = TranslatedSelectBox(mapSizes, mapParameters.mapSize.name, skin)
            worldSizeSelectBox.onChange { updateWorldSizeTable() }

            addHexagonalSizeTable()
            addRectangularSizeTable()

            add("{World Size}:".toLabel()).left()
            add(worldSizeSelectBox).fillX().row()
            add(customWorldSizeTable).colspan(2).grow().row()

            updateWorldSizeTable()
        }
    }

    private fun addHexagonalSizeTable() {
        val defaultRadius = mapParameters.mapSize.radius.toString()
        customMapSizeRadius = UncivTextField.create("Radius", defaultRadius).apply {
            textFieldFilter = DigitsOnlyFilter()
        }
        customMapSizeRadius.onChange {
            mapParameters.mapSize = MapSizeNew(customMapSizeRadius.text.toIntOrNull() ?: 0 )
        }
        hexagonalSizeTable.add("{Radius}:".toLabel()).grow().left()
        hexagonalSizeTable.add(customMapSizeRadius).right().row()
        hexagonalSizeTable.add("Anything above 40 may work very slowly on Android!".toLabel(Color.RED)
            .apply { wrap=true }).width(prefWidth).colspan(hexagonalSizeTable.columns)
    }

    private fun addRectangularSizeTable() {
        val defaultWidth = mapParameters.mapSize.width.toString()
        customMapWidth = UncivTextField.create("Width", defaultWidth).apply {
            textFieldFilter = DigitsOnlyFilter()
        }

        val defaultHeight = mapParameters.mapSize.height.toString()
        customMapHeight = UncivTextField.create("Height", defaultHeight).apply {
            textFieldFilter = DigitsOnlyFilter()
        }

        customMapWidth.onChange {
            mapParameters.mapSize = MapSizeNew(customMapWidth.text.toIntOrNull() ?: 0, customMapHeight.text.toIntOrNull() ?: 0)
        }
        customMapHeight.onChange {
            mapParameters.mapSize = MapSizeNew(customMapWidth.text.toIntOrNull() ?: 0, customMapHeight.text.toIntOrNull() ?: 0)
        }

        rectangularSizeTable.defaults().pad(5f)
        rectangularSizeTable.add("{Width}:".toLabel()).grow().left()
        rectangularSizeTable.add(customMapWidth).right().row()
        rectangularSizeTable.add("{Height}:".toLabel()).grow().left()
        rectangularSizeTable.add(customMapHeight).right().row()
        rectangularSizeTable.add("Anything above 80 by 50 may work very slowly on Android!".toLabel(Color.RED)
            .apply { wrap = true }).width(prefWidth).colspan(hexagonalSizeTable.columns)
    }

    private fun updateWorldSizeTable() {
        customWorldSizeTable.clear()

        if ((mapParameters.shape == MapShape.hexagonal || mapParameters.shape == MapShape.flatEarth) && worldSizeSelectBox.selected.value == MapSize.custom)
            customWorldSizeTable.add(hexagonalSizeTable).grow().row()
        else if (mapParameters.shape == MapShape.rectangular && worldSizeSelectBox.selected.value == MapSize.custom)
            customWorldSizeTable.add(rectangularSizeTable).grow().row()
        else
            mapParameters.mapSize = MapSizeNew(worldSizeSelectBox.selected.value)

        sizeChangedCallback?.invoke()
    }

    private fun addResourceSelectBox() {
        val mapResources = if (forMapEditor) listOf(
            MapResources.sparse,
            MapResources.default,
            MapResources.abundant,
        ) else listOf(
            MapResources.sparse,
            MapResources.default,
            MapResources.abundant,
            MapResources.strategicBalance,
            MapResources.legendaryStart
        )

        if (mapGeneratedMainType == MapGeneratedMainType.randomGenerated) {
            mapResourcesOptionsValues = mapResources.toHashSet()
            val optionsTable = MultiCheckboxTable("{Enabled Resource Settings}", "NewGameResourceSettings", mapResourcesOptionsValues) {
                if (mapResourcesOptionsValues.isEmpty()) {
                    mapParameters.mapResources = mapResources.random(randomness.RNG)
                } else {
                    mapParameters.mapResources = mapResourcesOptionsValues.random(randomness.RNG)
                }
            }
            add(optionsTable).colspan(2).grow().row()
        } else {
            resourceSelectBox = TranslatedSelectBox(mapResources, mapParameters.mapResources, skin)

            resourceSelectBox.onChange {
                mapParameters.mapResources = resourceSelectBox.selected.value
            }

            add("{Resource Setting}:".toLabel()).left()
            add(resourceSelectBox).fillX().row()
        }
    }

    private fun Table.addNoRuinsCheckbox() {
        noRuinsCheckbox = "No Ancient Ruins".toCheckBox(mapParameters.noRuins) {
            mapParameters.noRuins = it
        }
        add(noRuinsCheckbox).row()
    }

    private fun Table.addNoNaturalWondersCheckbox() {
        noNaturalWondersCheckbox = "No Natural Wonders".toCheckBox(mapParameters.noNaturalWonders) {
            mapParameters.noNaturalWonders = it
        }
        add(noNaturalWondersCheckbox).row()
    }

    private fun Table.addWorldWrapCheckbox() {
        worldWrapCheckbox = "World Wrap".toCheckBox(mapParameters.worldWrap) {
            mapParameters.worldWrap = it
        }
        add(worldWrapCheckbox).row()
    }

    private fun addWrappedCheckBoxes() {
        val worldWrapWarning = "World wrap maps are very memory intensive - creating large world wrap maps on Android can lead to crashes!"
        if (mapGeneratedMainType == MapGeneratedMainType.randomGenerated) {
            add(ExpanderTab("{Other Settings}", persistenceID = "NewGameOtherSettings", startsOutOpened = false) {
                it.defaults().pad(5f,0f)
                it.addNoRuinsCheckbox()
                it.addNoNaturalWondersCheckbox()
                it.addWorldWrapCheckbox()
                it.add(worldWrapWarning.toLabel(fontSize = 14).apply { wrap=true }).colspan(2).fillX().row()
            }).pad(10f).padTop(10f).colspan(2).growX().row()
        } else {
            add(Table(skin).apply {
                defaults().left().pad(2.5f)
                addNoRuinsCheckbox()
                addNoNaturalWondersCheckbox()
                addWorldWrapCheckbox()
            }).colspan(2).center().row()
            add(worldWrapWarning.toLabel(fontSize = 14).apply { wrap=true }).colspan(2).fillX().row()
        }
    }

    private fun addAdvancedSettings() {
        val expander = ExpanderTab("Advanced Settings", startsOutOpened = false) {
            addAdvancedControls(it)
        }
        add(expander).pad(10f).padTop(10f).colspan(2).growX().row()
    }

    private fun addAdvancedControls(table: Table) {
        table.defaults().pad(5f)

        seedTextField = UncivTextField.create("RNG Seed", mapParameters.seed.toString())
        seedTextField.textFieldFilter = DigitsOnlyFilter()

        // If the field is empty, fallback seed value to 0
        seedTextField.onChange {
            mapParameters.seed = try {
                seedTextField.text.toLong()
            } catch (e: Exception) {
                0L
            }
        }

        table.add("RNG Seed".toLabel()).left()
        table.add(seedTextField).fillX().padBottom(10f).row()

        fun addSlider(text: String, getValue:()->Float, min: Float, max: Float, onChange: (value: Float)->Unit): UncivSlider {
            val slider = UncivSlider(min, max, (max - min) / 20, onChange = onChange, initial = getValue())
            table.add(text.toLabel()).left()
            table.add(slider).fillX().row()
            advancedSliders[slider] = getValue
            return slider
        }

        fun addSlider(text: String, getValue:()->Float, min: Float, max: Float, step: Float, onChange: (value: Float)->Unit): UncivSlider {
            val slider = UncivSlider(min, max, step, onChange = onChange, initial = getValue())
            table.add(text.toLabel()).left()
            table.add(slider).fillX().row()
            advancedSliders[slider] = getValue
            return slider
        }

        fun addCheckbox(
            text: String,
            startsOutChecked: Boolean = false,
            changeAction: ((Boolean) -> Unit)): CheckBox {
            val checkbox = text.toCheckBox(startsOutChecked) {
                changeAction.invoke(true)
            }
            return checkbox
        }

        addSlider("Map Elevation", {mapParameters.elevationExponent}, 0.6f, 0.8f)
        { mapParameters.elevationExponent = it }

        addSlider("Temperature extremeness", {mapParameters.temperatureExtremeness}, 0.4f, 0.8f)
        { mapParameters.temperatureExtremeness = it }

        addSlider("Temperature shift", {mapParameters.temperatureShift}, -0.4f, 0.4f, 0.1f)
        { mapParameters.temperatureShift = it }

        addSlider("Resource richness", {mapParameters.resourceRichness},0f, 0.5f)
        { mapParameters.resourceRichness = it }

        addSlider("Vegetation richness", {mapParameters.vegetationRichness}, 0f, 1f)
        { mapParameters.vegetationRichness = it }

        addSlider("Rare features richness", {mapParameters.rareFeaturesRichness}, 0f, 0.5f)
        { mapParameters.rareFeaturesRichness = it }

        addSlider("Max Coast extension", {mapParameters.maxCoastExtension.toFloat()}, 0f, 5f)
        { mapParameters.maxCoastExtension = it.toInt() }.apply { stepSize = 1f }

        addSlider("Biome areas extension", {mapParameters.tilesPerBiomeArea.toFloat()}, 1f, 15f)
        { mapParameters.tilesPerBiomeArea = it.toInt() }.apply { stepSize = 1f }

        addSlider("Water level", {mapParameters.waterThreshold}, -0.1f, 0.1f)
        { mapParameters.waterThreshold = it }

        table.add(addCheckbox("Random nations pool", mapParameters.enableRandomNationsPool) {
            mapParameters.enableRandomNationsPool = it
            update()
        }).row()

        if (mapParameters.enableRandomNationsPool) {
            table.add(addCheckbox("Blacklist random nations pool", mapParameters.blacklistRandomNationsPool) {
                mapParameters.blacklistRandomNationsPool = it
            }).row()
        }

        val randomPoolButton = "Select nations".toTextButton()
        randomPoolButton.onClick {
            if (previousScreen != null) {
                val popup = RandomNationPickerPopup(previousScreen, mapParameters)
                popup.open()
                popup.update()
            }
        }
        if (mapParameters.enableRandomNationsPool)
            table.add(randomPoolButton).colspan(2).padTop(10f).row()

        val resetToDefaultButton = "Reset to defaults".toTextButton()
        resetToDefaultButton.onClick {
            mapParameters.resetAdvancedSettings()
            seedTextField.text = mapParameters.seed.toString()
            for (entry in advancedSliders)
                entry.key.value = entry.value()
        }
        table.add(resetToDefaultButton).colspan(2).padTop(10f).row()
    }
}

private class RandomNationPickerPopup(
    previousScreen: IPreviousScreen,
    val mapParameters: MapParameters
) : Popup(previousScreen as BaseScreen) {
    companion object {
        // These are used for the Close/OK buttons in the lower left/right corners:
        const val buttonsCircleSize = 70f
        const val buttonsIconSize = 50f
        const val buttonsOffsetFromEdge = 5f
        val buttonsBackColor: Color = Color.BLACK.cpy().apply { a = 0.67f }
    }

    val blockWidth: Float = 0f
    val civBlocksWidth = if(blockWidth <= 10f) previousScreen.stage.width / 3 - 5f else blockWidth

    // This Popup's body has two halves of same size, either side by side or arranged vertically
    // depending on screen proportions - determine height for one of those
    private val partHeight = stageToShowOn.height * (if (stageToShowOn.isNarrowerThan4to3()) 0.45f else 0.8f)
    private val nationListTable = Table()
    private val nationListScroll = AutoScrollPane(nationListTable)
    private val selectedNationsListTable = Table()
    private val selectedNationsListScroll = AutoScrollPane(selectedNationsListTable)
    private var selectedNations = mapParameters.randomNations
    var nations = arrayListOf<Nation>()


    init {
        var nationListScrollY = 0f
        nations += previousScreen.ruleset.nations.values.asSequence()
            .filter { it.isMajorCiv() }
        nationListScroll.setOverscroll(false, false)
        add(nationListScroll).size( civBlocksWidth + 10f, partHeight )
        // +10, because the nation table has a 5f pad, for a total of +10f
        if (stageToShowOn.isNarrowerThan4to3()) row()
        selectedNationsListScroll.setOverscroll(false, false)
        add(selectedNationsListScroll).size(civBlocksWidth + 10f, partHeight) // Same here, see above

        update()

        nationListScroll.layout()
        pack()
        if (nationListScrollY > 0f) {
            // center the selected nation vertically, getRowHeight safe because nationListScrollY > 0f ensures at least 1 row
            nationListScrollY -= (nationListScroll.height - nationListTable.getRowHeight(0)) / 2
            nationListScroll.scrollY = nationListScrollY.coerceIn(0f, nationListScroll.maxY)
        }

        val closeButton = "OtherIcons/Close".toImageButton(Color.FIREBRICK)
        closeButton.onActivation { close() }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButton.setPosition(buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(closeButton)

        val okButton = "OtherIcons/Checkmark".toImageButton(Color.LIME)
        okButton.onClick { returnSelected() }
        okButton.setPosition(innerTable.width - buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomRight)
        innerTable.addActor(okButton)

        selectedNationsListTable.touchable = Touchable.enabled
    }

    fun update() {
        nationListTable.clear()
        selectedNations = mapParameters.randomNations
        nations -= selectedNations.toSet()
        nations = nations.sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr() }).toMutableList() as ArrayList<Nation>

        var currentY = 0f
        for (nation in nations) {
            val nationTable = NationTable(nation, civBlocksWidth, 0f) // no need for min height
            val cell = nationListTable.add(nationTable)
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
            cell.row()
            nationTable.onClick {
                addNationToPool(nation)
            }
        }

        if (selectedNations.isNotEmpty()) {
            selectedNationsListTable.clear()

            for (currentNation in selectedNations) {
                val nationTable = NationTable(currentNation, civBlocksWidth, 0f)
                nationTable.onClick { removeNationFromPool(currentNation) }
                selectedNationsListTable.add(nationTable).row()
            }
        }
    }

    private fun String.toImageButton(overColor: Color): Group {
        val style = ImageButton.ImageButtonStyle()
        val image = ImageGetter.getDrawable(this)
        style.imageUp = image
        style.imageOver = image.tint(overColor)
        val button = ImageButton(style)
        button.setSize(buttonsIconSize, buttonsIconSize)

        return button.surroundWithCircle(buttonsCircleSize, false, buttonsBackColor)
    }

    private fun updateNationListTable() {
        selectedNationsListTable.clear()

        for (currentNation in selectedNations) {
            val nationTable = NationTable(currentNation, civBlocksWidth, 0f)
            nationTable.onClick { removeNationFromPool(currentNation) }
            selectedNationsListTable.add(nationTable).row()
        }
    }

    private fun addNationToPool(nation: Nation) {
        selectedNations.add(nation)

        update()
        updateNationListTable()
    }

    private fun removeNationFromPool(nation: Nation) {
        nations.add(nation)
        selectedNations.remove(nation)

        update()
        updateNationListTable()
    }

    private fun returnSelected() {
        close()
        mapParameters.randomNations = selectedNations
    }
}
