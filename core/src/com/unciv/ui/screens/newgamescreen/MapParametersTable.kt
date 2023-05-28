package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter.DigitsOnlyFilter
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapResources
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapGenerationRandomness
import com.unciv.models.translations.tr
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.UncivSlider
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.WrappableLabel
import com.unciv.ui.components.extensions.onChange
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toGdxArray
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.basescreen.BaseScreen

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
    // A HashMap indexed on a Widget without its own hashCode and equals overrides means the map uses
    // identity equality, which is fine here.
    private val advancedSliders = HashMap<UncivSlider, ()->Float>()

    init {
        update()
    }

    fun update() {
        clear()

        skin = BaseScreen.skin
        defaults().pad(5f, 10f)
        if (mapGeneratedMainType == MapGeneratedMainType.randomGenerated) {
            val prompt = "Which options should be available to the random selection?"
            val width = (previousScreen as? NewGameScreen)?.getColumnWidth() ?: 200f
            val label = WrappableLabel(prompt, width - 20f)  // 20 is the defaults() padding
            label.setAlignment(Align.center)
            label.wrap = true
            add(label).colspan(2).grow().row()
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
            MapType.perlin,
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

            if (forMapEditor) {
                val comment = "This is used for painting resources, not in map generator steps:"
                val expectedWidth = (UncivGame.Current.screen?.stage?.width ?: 1200f) * 0.4f
                val label = WrappableLabel(comment, expectedWidth, Color.GOLD, 14)
                label.setAlignment(Align.center)
                label.wrap = true
                add(label).colspan(2).row()
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
            } catch (_: Exception) {
                0L
            }
        }

        table.add("RNG Seed".toLabel()).left()
        table.add(seedTextField).fillX().padBottom(10f).row()

        fun addSlider(text: String, getValue: ()->Float, min: Float, max: Float, step: Float, onChange: (value: Float)->Unit): UncivSlider {
            val slider = UncivSlider(min, max, step, onChange = onChange, initial = getValue())
            table.add(text.toLabel()).left()
            table.add(slider).fillX().row()
            advancedSliders[slider] = getValue
            return slider
        }
        fun addSlider(text: String, getValue: ()->Float, min: Float, max: Float, onChange: (value: Float)->Unit): UncivSlider =
            addSlider(text, getValue, min, max, (max - min) / 20, onChange)

        addSlider("Map Elevation", { mapParameters.elevationExponent }, 0.6f, 0.8f)
            { mapParameters.elevationExponent = it }

        addSlider("Temperature extremeness", { mapParameters.temperatureExtremeness }, 0.4f, 0.8f)
            { mapParameters.temperatureExtremeness = it }

        addSlider("Temperature shift", { mapParameters.temperatureShift }, -0.4f, 0.4f, 0.1f)
            { mapParameters.temperatureShift = it }

        if (forMapEditor) {
            addSlider("Resource richness", { mapParameters.resourceRichness }, 0f, 0.5f)
                { mapParameters.resourceRichness = it }
        }

        addSlider("Vegetation richness", { mapParameters.vegetationRichness }, 0f, 1f)
            { mapParameters.vegetationRichness = it }

        addSlider("Rare features richness", { mapParameters.rareFeaturesRichness }, 0f, 0.5f)
            { mapParameters.rareFeaturesRichness = it }

        addSlider("Max Coast extension", { mapParameters.maxCoastExtension.toFloat() }, 0f, 5f)
            { mapParameters.maxCoastExtension = it.toInt() }.apply { stepSize = 1f }

        addSlider("Biome areas extension", { mapParameters.tilesPerBiomeArea.toFloat() }, 1f, 15f)
            { mapParameters.tilesPerBiomeArea = it.toInt() }.apply { stepSize = 1f }

        addSlider("Water level", { mapParameters.waterThreshold }, -0.1f, 0.1f)
            { mapParameters.waterThreshold = it }

        table.add(AdvancedSettingsMenu(mapParameters, ::loadAdvancedSettings))
            .colspan(table.columns).fill(false).center().row()
    }

    private class AdvancedSettingsMenu(
        mapParameters: MapParameters,
        loadAdvancedSettings: (MapParameters.Advanced) -> Unit
    ) : SelectBox<AdvancedSettingsMenu.MenuEntry>(BaseScreen.skin) {

        class MenuEntry(val label: String, val checkDisabled: ()->Boolean, val action: ()->Unit) {
            var disabled = false  // used as cache set from a checkDisabled call
            override fun toString() = label.tr()
        }

        init {
            // Fix Gdx bug https://github.com/libgdx/libgdx/pull/6930
            val fld = javaClass.superclass.getDeclaredField("scrollPane")
            fld.isAccessible = true
            fld.set(this, newScrollPane())

            val lastSetup = GUI.getSettings().lastGameSetup

            items = listOf(
                MenuEntry("―Choose―", { true }, {}),  // that's 'em dash' U+2014
                MenuEntry("Reset to defaults",
                    checkDisabled = { mapParameters.advanced.isDefault() },
                    action = {
                        loadAdvancedSettings(MapParameters.Advanced.default)
                    }),
                MenuEntry("Last new game",
                    checkDisabled = {
                        lastSetup == null || lastSetup.mapParameters.advanced == mapParameters.advanced
                    },
                    action = {
                        loadAdvancedSettings(lastSetup!!.mapParameters.advanced)
                    }),
                MenuEntry("Copy to clipboard", { false }, { mapParameters.advanced.toClipboard() }),
                MenuEntry("Paste from clipboard",
                    checkDisabled = { !Gdx.app.clipboard.contents.startsWith("{") },
                    action = {
                        try {
                            val pasted = MapParameters.Advanced.fromClipboard()
                            loadAdvancedSettings(pasted)
                        } catch (_: Throwable) {}
                    }),
            ).toGdxArray()

            onChange {
                if (!selected.checkDisabled())
                    selected.action.invoke()
                selectedIndex = 0
            }
        }

        override fun onShow(scrollPane: Actor?, below: Boolean) {
            // Cache checkDisabled results while dropdown is open, otherwise those lambdas are all
            // permanently hit at the game's refresh rate. Cute when it reacts immediately to a
            // clipboard change, but better save those cycles.
            for (item in items)
                item.disabled = item.checkDisabled()
            super.onShow(scrollPane, below)
        }

        val saveColor = Color()
        @Suppress("UsePropertyAccessSyntax")
        /** This draws on the "closed" part of the SelectBox - gray it out */
        override fun drawItem(batch: Batch, font: BitmapFont, item: MenuEntry, x: Float, y: Float, width: Float): GlyphLayout {
            // Notes: font is the same instance as style.font, but font.color is *not* the same
            // instance as style.fontColor. To modify on-the-fly, only copy the rgba values around,
            // no new instance creation please here.
            // Also note, simply setting to GRAY including alpha will hinder fade actions -
            // see List subclass below, where we need to respect premultiplied alpha.
            saveColor.set(font.color)
            font.setColor(Color.GRAY)
            val layout = super.drawItem(batch, font, item, x, y, width)
            font.setColor(saveColor)
            return layout
        }

        override fun newScrollPane() = Pane(this)

        private class Pane(box: SelectBox<MenuEntry>) : SelectBoxScrollPane<MenuEntry>(box) {
            override fun newList() = Drop()
        }

        private class Drop : List<MenuEntry>(BaseScreen.skin) {
            var selectedDrawable: Drawable? = null
            val selectedRect = Rectangle()
            val saveColor = Color()
            override fun drawSelection(batch: Batch, drawable: Drawable?, x: Float, y: Float, width: Float, height: Float) {
                selectedDrawable = drawable
                selectedRect.set(x, y, width, height)
            }
            /** This draws the items on the dropdown - hide index 0 entirely, draw disabled as needed */
            override fun drawItem(batch: Batch, font: BitmapFont, index: Int, item: MenuEntry, x: Float, y: Float, width: Float): GlyphLayout {
                if (!item.disabled)
                    selectedRect.also { super.drawSelection(batch, selectedDrawable, it.x, it.y, it.width, it.height) }
                saveColor.set(font.color)
                val alpha = if (index == 0) 0f else if (item.disabled) 0.5f else 1f
                font.setColor(saveColor.r, saveColor.g, saveColor.b, saveColor.a * alpha )
                val layout = super.drawItem(batch, font, index, item, x, y, width)
                @Suppress("UsePropertyAccessSyntax")
                font.setColor(saveColor)
                return layout
            }
        }
    }

    private fun loadAdvancedSettings(newValue: MapParameters.Advanced) {
        mapParameters.reseed()
        seedTextField.text = mapParameters.seed.toString()

        mapParameters.advanced = newValue.clone()
        for (entry in advancedSliders)
            entry.key.value = entry.value()
    }
}
