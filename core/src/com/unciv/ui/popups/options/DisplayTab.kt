package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.worldscreen.NotificationsScroll
import com.unciv.utils.Display
import com.unciv.utils.ScreenMode
import com.unciv.utils.ScreenOrientation

/**
 *  @param onChange Callback for _major_ changes, OptionsPopup will rebuild itself and the WorldScreen
 */
internal class DisplayTab(
    optionsPopup: OptionsPopup,
    onChange: () -> Unit
): OptionsPopupTab(optionsPopup) {
    init {
        defaults().pad(2.5f)

        addHeader("Screen")

        addScreenSizeSelectBox(onChange)
        addScreenOrientationSelectBox(onChange)
        addScreenModeSelectBox()


        if (Gdx.app.type == Application.ApplicationType.Desktop) {
            addCheckbox("Map mouse auto-scroll", settings::mapAutoScroll, updateWorld = true) {
                if (GUI.isWorldLoaded())
                    GUI.getMap().isAutoScrollEnabled = it
            }
            addScrollSpeedSlider(this, settings, selectBoxMinWidth)
        }

        addHeader("Graphics")

        addTileSetSelectBox(onChange)
        addUnitSetSelectBox(onChange)
        addSkinSelectBox(onChange)

        addHeader("UI")

        addNotificationScrollSelect()
        addCheckbox("Show minimap", settings::showMinimap, updateWorld = true)
        addCheckbox("Show tutorials", settings.showTutorials, updateWorld = true, newRow = false) { settings.showTutorials = it }
        addResetTutorials(this, settings)
        addCheckbox("Show zoom buttons in world screen", settings::showZoomButtons, true)
        addCheckbox("Never close popups by clicking outside", settings::forbidPopupClickBehindToClose)
        addCheckbox("Use circles to indicate movable tiles", settings::useCirclesToIndicateMovableTiles, updateWorld = true)
        addPediaUnitArtSizeSlider(this, settings, selectBoxMinWidth)

        addHeader("Visual Hints")

        addCheckbox("Show unit movement arrows", settings::showUnitMovements, updateWorld = true)
        addCheckbox("Show suggested city locations for units that can found cities", settings::showSettlersSuggestedCityLocations, updateWorld = true)
        addCheckbox("Show tile yields", settings::showTileYields, updateWorld = true)
        addCheckbox("Show worked tiles", settings::showWorkedTiles, updateWorld = true)
        addCheckbox("Show resources and improvements", settings::showResourcesAndImprovements, updateWorld = true)
        addCheckbox("Show pixel improvements", settings::showPixelImprovements, updateWorld = true)

        addUnitIconAlphaSlider(this, settings, selectBoxMinWidth)

        addHeader("Performance")

        addCheckbox("Continuous rendering", settings::continuousRendering) {
            Gdx.graphics.isContinuousRendering = it
        }

        val continuousRenderingDescription = "When disabled, saves battery life but certain animations will be suspended"
        val continuousRenderingLabel = WrappableLabel(
            continuousRenderingDescription,
            optionsPopup.tabs.prefWidth, Color.ORANGE.brighten(0.7f), 14
        )
        continuousRenderingLabel.wrap = true
        add(continuousRenderingLabel).colspan(2).padTop(10f).row()

        addHeader("Experimental")

        addCheckbox("Experimental Demographics scoreboard", settings::useDemographics)
        addCheckbox("Unit movement button", settings::unitMovementButtonAnimation)
        addCheckbox("Unit actions menu", settings::unitActionsTableAnimation)
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
        val proxy = object {
            var value: ScreenMode
                get() = modes[settings.screenMode] ?: modes.values.first()
                set(value) { settings.screenMode = value.getId() }
        }
        addSelectBox("Screen Mode", proxy::value, modes.values) { mode, _ ->
            settings.refreshWindowSize()
            Display.setScreenMode(mode.getId(), settings)
        }
    }

    private fun addScreenSizeSelectBox(onResolutionChange: () -> Unit) {
        addSelectBox("UI Scale", settings::screenSize, ScreenSize.entries) { _, _ ->
            onResolutionChange()
        }
    }

    private fun addScreenOrientationSelectBox(onOrientationChange: () -> Unit) {
        if (!Display.hasOrientation()) return
        addSelectBox("Screen orientation", settings::displayOrientation, ScreenOrientation.entries) { orientation, _ ->
            Display.setOrientation(orientation)
            onOrientationChange()
        }
    }

    private fun addTileSetSelectBox(onTilesetChange: () -> Unit) {
        val unitSets = ImageGetter.getAvailableUnitsets()
        addSelectBox("Tileset", settings::tileSet, ImageGetter.getAvailableTilesets().asIterable()) { newValue, oldValue ->
            // Switch unitSet together with tileSet as long as one with the same name exists and both are selected
            if (oldValue == settings.unitSet && unitSets.contains(newValue)) {
                settings.unitSet = newValue
            }
            // ImageGetter ruleset should be correct no matter what screen we're on
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            onTilesetChange()
        }
    }

    private fun addUnitSetSelectBox(onUnitsetChange: () -> Unit) {
        val nullValue = "None".tr()
        addSelectBox("Unitset", settings::unitSet, ImageGetter.getAvailableUnitsets().asIterable()) { value, _ ->
            if (value == nullValue) settings.unitSet = null
            // ImageGetter ruleset should be correct no matter what screen we're on
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            onUnitsetChange()
        }
    }

    private fun addSkinSelectBox(onSkinChange: () -> Unit) {
        addSelectBox("UI Skin", settings::skin, ImageGetter.getAvailableSkins().asIterable()) { _, _ ->
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
