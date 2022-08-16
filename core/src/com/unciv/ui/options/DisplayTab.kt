package com.unciv.ui.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivSlider
import com.unciv.ui.utils.WrappableLabel
import com.unciv.ui.utils.extensions.brighten
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.worldscreen.WorldScreen

private val resolutionArray = com.badlogic.gdx.utils.Array(arrayOf("750x500", "900x600", "1050x700", "1200x800", "1500x1000"))

fun displayTab(
    optionsPopup: OptionsPopup,
    onResolutionChange: () -> Unit,
    onTilesetChange: () -> Unit
) = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(2.5f)

    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(this, "Show unit movement arrows", settings.showUnitMovements, true) { settings.showUnitMovements = it }
    optionsPopup.addCheckbox(this, "Show tile yields", settings.showTileYields, true) { settings.showTileYields = it } // JN
    optionsPopup.addCheckbox(this, "Show worked tiles", settings.showWorkedTiles, true) { settings.showWorkedTiles = it }
    optionsPopup.addCheckbox(this, "Show resources and improvements", settings.showResourcesAndImprovements, true) { settings.showResourcesAndImprovements = it }
    optionsPopup.addCheckbox(this, "Show tutorials", settings.showTutorials, true) { settings.showTutorials = it }
    optionsPopup.addCheckbox(this, "Show pixel units", settings.showPixelUnits, true) { settings.showPixelUnits = it }
    optionsPopup.addCheckbox(this, "Show pixel improvements", settings.showPixelImprovements, true) { settings.showPixelImprovements = it }
    optionsPopup.addCheckbox(this, "Experimental Demographics scoreboard", settings.useDemographics, true) { settings.useDemographics = it }
    optionsPopup.addCheckbox(this, "Show zoom buttons in world screen", settings.showZoomButtons, true) { settings.showZoomButtons = it }

    addMinimapSizeSlider(this, settings, optionsPopup.selectBoxMinWidth)

    addUnitIconAlphaSlider(this, settings, optionsPopup.selectBoxMinWidth)

    addResolutionSelectBox(this, settings, optionsPopup.selectBoxMinWidth, onResolutionChange)

    addTileSetSelectBox(this, settings, optionsPopup.selectBoxMinWidth, onTilesetChange)

    optionsPopup.addCheckbox(this, "Continuous rendering", settings.continuousRendering) {
        settings.continuousRendering = it
        Gdx.graphics.isContinuousRendering = it
    }

    val continuousRenderingDescription = "When disabled, saves battery life but certain animations will be suspended"
    val continuousRenderingLabel = WrappableLabel(
        continuousRenderingDescription,
        optionsPopup.tabs.prefWidth, Color.ORANGE.brighten(0.7f), 14
    )
    continuousRenderingLabel.wrap = true
    add(continuousRenderingLabel).colspan(2).padTop(10f).row()

}

private fun addMinimapSizeSlider(table: Table, settings: GameSettings, selectBoxMinWidth: Float) {
    table.add("Minimap size".toLabel()).left().fillX()

    // The meaning of the values needs a formula to be synchronized between here and
    // [Minimap.init]. It goes off-10%-11%..29%-30%-35%-40%-45%-50% - and the percentages
    // correspond roughly to the minimap's proportion relative to screen dimensions.
    val offTranslated = "off".tr()  // translate only once and cache in closure
    val getTipText: (Float) -> String = {
        when (it) {
            0f -> offTranslated
            in 0.99f..21.01f -> "%.0f".format(it + 9) + "%"
            else -> "%.0f".format(it * 5 - 75) + "%"
        }
    }
    val minimapSlider = UncivSlider(
        0f, 25f, 1f,
        initial = if (settings.showMinimap) settings.minimapSize.toFloat() else 0f,
        getTipText = getTipText
    ) {
        val size = it.toInt()
        if (size == 0) settings.showMinimap = false
        else {
            settings.showMinimap = true
            settings.minimapSize = size
        }
        settings.save()
        val worldScreen = UncivGame.Current.getWorldScreenIfActive()
        if (worldScreen != null)
            worldScreen.shouldUpdate = true
    }
    table.add(minimapSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}

private fun addUnitIconAlphaSlider(table: Table, settings: GameSettings, selectBoxMinWidth: Float) {
    table.add("Unit icon opacity".toLabel()).left().fillX()

    val getTipText: (Float) -> String = {"%.0f".format(it*100) + "%"}

    val unitIconAlphaSlider = UncivSlider(
        0f, 1f, 0.1f, initial = settings.unitIconOpacity, getTipText = getTipText
    ) {
        settings.unitIconOpacity = it
        settings.save()

        val worldScreen = UncivGame.Current.getWorldScreenIfActive()
        if (worldScreen != null)
            worldScreen.shouldUpdate = true

    }
    table.add(unitIconAlphaSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}

private fun addResolutionSelectBox(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onResolutionChange: () -> Unit) {
    table.add("Resolution".toLabel()).left().fillX()

    val resolutionSelectBox = SelectBox<String>(table.skin)
    resolutionSelectBox.items = resolutionArray
    resolutionSelectBox.selected = settings.resolution
    table.add(resolutionSelectBox).minWidth(selectBoxMinWidth).pad(10f).row()

    resolutionSelectBox.onChange {
        settings.resolution = resolutionSelectBox.selected
        onResolutionChange()
    }
}

private fun addTileSetSelectBox(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onTilesetChange: () -> Unit) {
    table.add("Tileset".toLabel()).left().fillX()

    val tileSetSelectBox = SelectBox<String>(table.skin)
    val tileSetArray = Array<String>()
    val tileSets = ImageGetter.getAvailableTilesets()
    for (tileset in tileSets) tileSetArray.add(tileset)
    tileSetSelectBox.items = tileSetArray
    tileSetSelectBox.selected = settings.tileSet
    table.add(tileSetSelectBox).minWidth(selectBoxMinWidth).pad(10f).row()

    tileSetSelectBox.onChange {
        settings.tileSet = tileSetSelectBox.selected
        // ImageGetter ruleset should be correct no matter what screen we're on
        TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
        onTilesetChange()
    }
}
