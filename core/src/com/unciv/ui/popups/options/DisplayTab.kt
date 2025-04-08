package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.basescreen.BaseScreen.Companion.skin
import com.unciv.ui.screens.worldscreen.NotificationsScroll
import com.unciv.utils.Display
import com.unciv.utils.ScreenMode
import com.unciv.utils.ScreenOrientation

/**
 *  @param onChange Callback for _major_ changes, OptionsPopup will rebuild itself and the WorldScreen
 */
fun displayTab(
    optionsPopup: OptionsPopup,
    onChange: () -> Unit,
) = Table(skin).apply {
    pad(10f)
    defaults().pad(2.5f)

    val settings = optionsPopup.settings

    add("Screen".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

    addScreenSizeSelectBox(this, settings, optionsPopup.selectBoxMinWidth, onChange)
    addScreenOrientationSelectBox(this, settings, optionsPopup.selectBoxMinWidth, onChange)
    addScreenModeSelectBox(this, settings, optionsPopup.selectBoxMinWidth)


    if (Gdx.app.type == Application.ApplicationType.Desktop) {
        optionsPopup.addCheckbox(this, "Map mouse auto-scroll", settings.mapAutoScroll, true) {
            settings.mapAutoScroll = it
            if (GUI.isWorldLoaded())
                GUI.getMap().isAutoScrollEnabled = settings.mapAutoScroll
        }
        addScrollSpeedSlider(this, settings, optionsPopup.selectBoxMinWidth)
    }

    addSeparator()
    add("Graphics".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

    addTileSetSelectBox(this, settings, optionsPopup.selectBoxMinWidth, onChange)
    addUnitSetSelectBox(this, settings, optionsPopup.selectBoxMinWidth, onChange)
    addSkinSelectBox(this, settings, optionsPopup.selectBoxMinWidth, onChange)

    addSeparator()
    add("UI".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

    addNotificationScrollSelect(this, settings, optionsPopup.selectBoxMinWidth)
    optionsPopup.addCheckbox(this, "Show minimap", settings.showMinimap, updateWorld = true) { settings.showMinimap = it }
    optionsPopup.addCheckbox(this, "Show tutorials", settings.showTutorials, updateWorld = true, newRow = false) { settings.showTutorials = it }
    addResetTutorials(this, settings)
    optionsPopup.addCheckbox(this, "Show zoom buttons in world screen", settings.showZoomButtons, true) { settings.showZoomButtons = it }
    optionsPopup.addCheckbox(this, "Never close popups by clicking outside", settings.forbidPopupClickBehindToClose, false) { settings.forbidPopupClickBehindToClose = it }
    optionsPopup.addCheckbox(this, "Use circles to indicate movable tiles", settings.useCirclesToIndicateMovableTiles, true) { settings.useCirclesToIndicateMovableTiles = it }
    addPediaUnitArtSizeSlider(this, settings, optionsPopup.selectBoxMinWidth)

    addSeparator()
    add("Visual Hints".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

    optionsPopup.addCheckbox(this, "Show unit movement arrows", settings.showUnitMovements, true) { settings.showUnitMovements = it }
    optionsPopup.addCheckbox(this, "Show suggested city locations for units that can found cities", settings.showSettlersSuggestedCityLocations, true) { settings.showSettlersSuggestedCityLocations = it }
    optionsPopup.addCheckbox(this, "Show tile yields", settings.showTileYields, true) { settings.showTileYields = it } // JN
    optionsPopup.addCheckbox(this, "Show worked tiles", settings.showWorkedTiles, true) { settings.showWorkedTiles = it }
    optionsPopup.addCheckbox(this, "Show resources and improvements", settings.showResourcesAndImprovements, true) { settings.showResourcesAndImprovements = it }
    optionsPopup.addCheckbox(this, "Show pixel improvements", settings.showPixelImprovements, true) { settings.showPixelImprovements = it }

    addUnitIconAlphaSlider(this, settings, optionsPopup.selectBoxMinWidth)

    addSeparator()
    add("Performance".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

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
    
    addSeparator()
    add("Experimental".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

    optionsPopup.addCheckbox(this, "Experimental Demographics scoreboard", settings.useDemographics, true) { settings.useDemographics = it }
    optionsPopup.addCheckbox(this, "Unit movement button", settings.unitMovementButtonAnimation, true) { settings.unitMovementButtonAnimation = it }
    optionsPopup.addCheckbox(this, "Unit actions menu", settings.unitActionsTableAnimation, true) { settings.unitActionsTableAnimation = it }
    }

private fun addScrollSpeedSlider(table: Table, settings: GameSettings, selectBoxMinWidth: Float) {
    table.add("Map panning speed".toLabel()).left().fillX()

    val scrollSpeedSlider = UncivSlider(
        0.2f, 25f, 0.2f, initial = settings.mapPanningSpeed
    ) {
        settings.mapPanningSpeed = it
        settings.save()
        if (GUI.isWorldLoaded())
            GUI.getMap().mapPanningSpeed = settings.mapPanningSpeed
    }
    table.add(scrollSpeedSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}

private fun addUnitIconAlphaSlider(table: Table, settings: GameSettings, selectBoxMinWidth: Float) {
    table.add("Unit icon opacity".toLabel()).left().fillX()

    val getTipText: (Float) -> String = {"%.0f".format(it*100) + "%"}

    val unitIconAlphaSlider = UncivSlider(
        0f, 1f, 0.1f, initial = settings.unitIconOpacity, getTipText = getTipText
    ) {
        settings.unitIconOpacity = it
        GUI.setUpdateWorldOnNextRender()
    }
    table.add(unitIconAlphaSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}

private fun addPediaUnitArtSizeSlider(table: Table, settings: GameSettings, selectBoxMinWidth: Float) {
    table.add("Size of Unitset art in Civilopedia".toLabel()).left().fillX()

    val unitArtSizeSlider = UncivSlider(
        0f, 360f, 1f, initial = settings.pediaUnitArtSize
    ) {
        settings.pediaUnitArtSize = it
        GUI.setUpdateWorldOnNextRender()
    }
    unitArtSizeSlider.setSnapToValues(threshold = 60f, 0f, 32f, 48f, 64f, 96f, 120f, 180f, 240f, 360f)
    table.add(unitArtSizeSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}

private fun addScreenModeSelectBox(table: Table, settings: GameSettings, selectBoxMinWidth: Float) {
    table.add("Screen Mode".toLabel()).left().fillX()

    val modes = Display.getScreenModes()
    val current: ScreenMode? = modes[settings.screenMode]

    val selectBox = SelectBox<ScreenMode>(table.skin)
    selectBox.items = Array(modes.values.toTypedArray())
    selectBox.selected = current
    selectBox.onChange {
        settings.refreshWindowSize()
        val mode = selectBox.selected
        settings.screenMode = mode.getId()
        Display.setScreenMode(mode.getId(), settings)
    }

    table.add(selectBox).minWidth(selectBoxMinWidth).pad(10f).row()
}

private fun addScreenSizeSelectBox(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onResolutionChange: () -> Unit) {
    table.add("Screen Size".toLabel()).left().fillX()

    val screenSizeSelectBox = TranslatedSelectBox(ScreenSize.entries.map { it.name }, settings.screenSize.name)
    table.add(screenSizeSelectBox).minWidth(selectBoxMinWidth).pad(10f).row()

    screenSizeSelectBox.onChange {
        settings.screenSize = ScreenSize.valueOf(screenSizeSelectBox.selected.value)
        onResolutionChange()
    }
}

private fun addScreenOrientationSelectBox(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onOrientationChange: () -> Unit){
    if (!Display.hasOrientation()) return
    
    table.add("Screen orientation".toLabel()).left().fillX()

    val selectBox = SelectBox<ScreenOrientation>(skin)
    selectBox.items = Array(ScreenOrientation.entries.toTypedArray())
    selectBox.selected = settings.displayOrientation
    selectBox.onChange {
        val orientation = selectBox.selected
        settings.displayOrientation = orientation
        Display.setOrientation(orientation)
        onOrientationChange()
    }

    table.add(selectBox).minWidth(selectBoxMinWidth).pad(10f).row()
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

    val unitSets = ImageGetter.getAvailableUnitsets()

    tileSetSelectBox.onChange {
        // Switch unitSet together with tileSet as long as one with the same name exists and both are selected
        if (settings.tileSet == settings.unitSet && unitSets.contains(tileSetSelectBox.selected)) {
            settings.unitSet = tileSetSelectBox.selected
        }
        settings.tileSet = tileSetSelectBox.selected
        // ImageGetter ruleset should be correct no matter what screen we're on
        TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
        onTilesetChange()
    }
}

private fun addUnitSetSelectBox(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onUnitsetChange: () -> Unit) {
    table.add("Unitset".toLabel()).left().fillX()

    val unitSetSelectBox = SelectBox<String>(table.skin)
    val unitSetArray = Array<String>()
    val nullValue = "None".tr()
    unitSetArray.add(nullValue)
    val unitSets = ImageGetter.getAvailableUnitsets()
    for (unitset in unitSets) unitSetArray.add(unitset)
    unitSetSelectBox.items = unitSetArray
    unitSetSelectBox.selected = settings.unitSet ?: nullValue
    table.add(unitSetSelectBox).minWidth(selectBoxMinWidth).pad(10f).row()

    unitSetSelectBox.onChange {
        settings.unitSet = if (unitSetSelectBox.selected != nullValue) unitSetSelectBox.selected else null
        // ImageGetter ruleset should be correct no matter what screen we're on
        TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
        onUnitsetChange()
    }
}

private fun addSkinSelectBox(table: Table, settings: GameSettings, selectBoxMinWidth: Float, onSkinChange: () -> Unit) {
    table.add("UI Skin".toLabel()).left().fillX()

    val skinSelectBox = SelectBox<String>(table.skin)
    val skinArray = Array<String>()
    val skins = ImageGetter.getAvailableSkins()
    for (skin in skins) skinArray.add(skin)
    skinSelectBox.items = skinArray
    skinSelectBox.selected = settings.skin
    table.add(skinSelectBox).minWidth(selectBoxMinWidth).pad(10f).row()

    skinSelectBox.onChange {
        settings.skin = skinSelectBox.selected
        // ImageGetter ruleset should be correct no matter what screen we're on
        SkinCache.assembleSkinConfigs(ImageGetter.ruleset.mods)
        onSkinChange()
    }
}

private fun addResetTutorials(table: Table, settings: GameSettings) {
    val resetTutorialsButton = "Reset tutorials".toTextButton()
    resetTutorialsButton.onClick {
            ConfirmPopup(
                table.stage,
                "Do you want to reset completed tutorials?",
                "Reset"
            ) {
                settings.tutorialsShown.clear()
                settings.tutorialTasksCompleted.clear()
                resetTutorialsButton.setText("Done!".tr())
                resetTutorialsButton.clearListeners()
            }.open(true)
    }
    table.add(resetTutorialsButton).center().row()
}

private fun addNotificationScrollSelect(table: Table, settings: GameSettings, selectBoxMinWidth: Float) {
    table.add("Notifications on world screen".toLabel()).left().fillX()

    val selectBox = TranslatedSelectBox(
        NotificationsScroll.UserSetting.entries.map { it.name },
        settings.notificationScroll
    )
    table.add(selectBox).minWidth(selectBoxMinWidth).pad(10f).row()

    selectBox.onChange {
        settings.notificationScroll = selectBox.selected.value
        GUI.setUpdateWorldOnNextRender()
    }
}
