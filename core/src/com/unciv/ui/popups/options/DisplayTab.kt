package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
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
import com.unciv.ui.components.input.onClick
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
internal class DisplayTab(
    optionsPopup: OptionsPopup,
    onChange: () -> Unit,
) : Table(skin), OptionsPopupHelpers {
    override val selectBoxMinWidth by optionsPopup::selectBoxMinWidth

    // Only temporarily used instead of a local var so we can get a reference
    private lateinit var currentScreenMode: ScreenMode

    init {
        pad(10f)
        defaults().pad(2.5f)

        val settings = optionsPopup.settings

        add("Screen".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

        addScreenSizeSelectBox(onChange)
        addScreenOrientationSelectBox(onChange)
        addScreenModeSelectBox()


        if (Gdx.app.type == Application.ApplicationType.Desktop) {
            addCheckbox("Map mouse auto-scroll", settings.mapAutoScroll, true) {
                settings.mapAutoScroll = it
                if (GUI.isWorldLoaded())
                    GUI.getMap().isAutoScrollEnabled = settings.mapAutoScroll
            }
            addScrollSpeedSlider(this, settings, selectBoxMinWidth)
        }

        addSeparator()
        add("Graphics".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

        addTileSetSelectBox(onChange)
        addUnitSetSelectBox(onChange)
        addSkinSelectBox(onChange)

        addSeparator()
        add("UI".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

        addNotificationScrollSelect()
        addCheckbox("Show minimap", settings::showMinimap, updateWorld = true)
        addCheckbox("Show tutorials", settings.showTutorials, updateWorld = true, newRow = false) { settings.showTutorials = it }
        addResetTutorials(this, settings)
        addCheckbox("Show zoom buttons in world screen", settings.showZoomButtons, true) { settings.showZoomButtons = it }
        addCheckbox(
            "Never close popups by clicking outside",
            settings.forbidPopupClickBehindToClose,
            false
        ) { settings.forbidPopupClickBehindToClose = it }
        addCheckbox(
            "Use circles to indicate movable tiles",
            settings.useCirclesToIndicateMovableTiles,
            true
        ) { settings.useCirclesToIndicateMovableTiles = it }
        addPediaUnitArtSizeSlider(this, settings, selectBoxMinWidth)

        addSeparator()
        add("Visual Hints".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

        addCheckbox("Show unit movement arrows", settings.showUnitMovements, true) { settings.showUnitMovements = it }
        addCheckbox(
            "Show suggested city locations for units that can found cities",
            settings.showSettlersSuggestedCityLocations,
            true
        ) { settings.showSettlersSuggestedCityLocations = it }
        addCheckbox("Show tile yields", settings.showTileYields, true) { settings.showTileYields = it } // JN
        addCheckbox("Show worked tiles", settings.showWorkedTiles, true) { settings.showWorkedTiles = it }
        addCheckbox("Show resources and improvements", settings.showResourcesAndImprovements, true) {
            settings.showResourcesAndImprovements = it
        }
        addCheckbox("Show pixel improvements", settings.showPixelImprovements, true) { settings.showPixelImprovements = it }

        addUnitIconAlphaSlider(this, settings, selectBoxMinWidth)

        addSeparator()
        add("Performance".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

        addCheckbox("Continuous rendering", settings.continuousRendering) {
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

        addCheckbox("Experimental Demographics scoreboard", settings.useDemographics, true) { settings.useDemographics = it }
        addCheckbox("Unit movement button", settings.unitMovementButtonAnimation, true) { settings.unitMovementButtonAnimation = it }
        addCheckbox("Unit actions menu", settings.unitActionsTableAnimation, true) { settings.unitActionsTableAnimation = it }
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

        val getTipText: (Float) -> String = { "%.0f".format(it * 100) + "%" }

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

    private fun addScreenModeSelectBox() {
        val modes = Display.getScreenModes()
        currentScreenMode = modes[settings.screenMode] ?: modes.values.first()
        addSelectBox("Screen Mode", ::currentScreenMode, modes.values) { mode ->
            settings.refreshWindowSize()
            settings.screenMode = mode.getId()
            Display.setScreenMode(mode.getId(), settings)
        }
    }

    private fun addScreenSizeSelectBox(onResolutionChange: () -> Unit) {
        addSelectBox("UI Scale", settings::screenSize, ScreenSize.entries) {
            onResolutionChange()
        }
    }

    private fun addScreenOrientationSelectBox(onOrientationChange: () -> Unit) {
        if (!Display.hasOrientation()) return
        addSelectBox("Screen orientation", settings::displayOrientation, ScreenOrientation.entries) { orientation -> 
            Display.setOrientation(orientation)
            onOrientationChange()
        }
    }

    private fun addTileSetSelectBox(onTilesetChange: () -> Unit) {
        val unitSets = ImageGetter.getAvailableUnitsets()
        addSelectBox("Tileset", settings::tileSet, ImageGetter.getAvailableTilesets().asIterable()) { value ->
            // Switch unitSet together with tileSet as long as one with the same name exists and both are selected
            if (settings.tileSet == settings.unitSet && unitSets.contains(value)) {
                settings.unitSet = value
            }
            // ImageGetter ruleset should be correct no matter what screen we're on
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            onTilesetChange()
        }
    }

    private fun addUnitSetSelectBox(onUnitsetChange: () -> Unit) {
        val tileSets = ImageGetter.getAvailableTilesets()
        addSelectBox("Unitset", settings::unitSet, ImageGetter.getAvailableUnitsets().asIterable()) { value ->
            // Switch unitSet together with tileSet as long as one with the same name exists and both are selected
            if (value != null && settings.tileSet == settings.unitSet && tileSets.contains(value)) {
                settings.tileSet = value
            }
            // ImageGetter ruleset should be correct no matter what screen we're on
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            onUnitsetChange()
        }
    }

    private fun addSkinSelectBox(onSkinChange: () -> Unit) {
        addSelectBox("UI Skin", settings::skin, ImageGetter.getAvailableSkins().asIterable()) {
            settings.skin = it // addSelectbox will do it after the callback
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

    private fun addNotificationScrollSelect() {
        addEnumAsStringSelectBox("Notifications on world screen", settings::notificationScroll, NotificationsScroll.UserSetting.entries) {
            GUI.setUpdateWorldOnNextRender()
        }
    }
}
