package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.GUI
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.worldscreen.NotificationsScroll
import com.unciv.utils.Display
import com.unciv.utils.ScreenMode
import com.unciv.utils.ScreenOrientation

internal class DisplayTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    override fun lateInitialize() {
        defaults().pad(2.5f)

        addHeader("Screen")

        addScreenSizeSelectBox()
        addScreenOrientationSelectBox()
        addScreenModeSelectBox()


        if (Gdx.app.type == Application.ApplicationType.Desktop) {
            addCheckbox("Map mouse auto-scroll", settings::mapAutoScroll, updateWorld = true) {
                if (GUI.isWorldLoaded())
                    GUI.getMap().isAutoScrollEnabled = it
            }
            addScrollSpeedSlider()
        }

        addHeader("Graphics")

        addTileSetSelectBox()
        addUnitSetSelectBox()
        addSkinSelectBox()

        addHeader("UI")

        addNotificationScrollSelect()
        addCheckbox("Show minimap", settings::showMinimap, updateWorld = true)
        addCheckbox("Show tutorials", settings.showTutorials, updateWorld = true, newRow = false) { settings.showTutorials = it }
        addResetTutorials()
        addCheckbox("Show zoom buttons in world screen", settings::showZoomButtons, true)
        addCheckbox("Never close popups by clicking outside", settings::forbidPopupClickBehindToClose)
        addCheckbox("Use circles to indicate movable tiles", settings::useCirclesToIndicateMovableTiles, updateWorld = true)
        addPediaUnitArtSizeSlider()

        addHeader("Visual Hints")

        addCheckbox("Show unit movement arrows", settings::showUnitMovements, updateWorld = true)
        addCheckbox("Show suggested city locations for units that can found cities", settings::showSettlersSuggestedCityLocations, updateWorld = true)
        addCheckbox("Show tile yields", settings::showTileYields, updateWorld = true)
        addCheckbox("Show worked tiles", settings::showWorkedTiles, updateWorld = true)
        addCheckbox("Show resources and improvements", settings::showResourcesAndImprovements, updateWorld = true)
        addCheckbox("Show pixel improvements", settings::showPixelImprovements, updateWorld = true)

        addUnitIconAlphaSlider()

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

        super.lateInitialize()
    }

    private fun addScrollSpeedSlider() {
        addSlider("Map panning speed", settings::mapPanningSpeed, 0.2f, 25f, 0.2f) {
            settings.save()
            if (GUI.isWorldLoaded())
                GUI.getMap().mapPanningSpeed = settings.mapPanningSpeed
        }
    }

    private fun addUnitIconAlphaSlider() {
        val getTipText: (Float) -> String = { "%.0f".format(it * 100) + "%" }
        addSlider("Unit icon opacity", settings::unitIconOpacity, 0f, 1f, 0.1f, getTipText) {
            GUI.setUpdateWorldOnNextRender()
        }
    }

    private fun addPediaUnitArtSizeSlider() {
        addSlider("Size of Unitset art in Civilopedia", settings::pediaUnitArtSize, 0f, 360f) {
            GUI.setUpdateWorldOnNextRender() // TODO: I doubt that helps, the setting has only influence on CivilopediaScreen
        }.actor.apply {
            setSnapToValues(threshold = 60f, 0f, 32f, 48f, 64f, 96f, 120f, 180f, 240f, 360f)
        }
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

    private fun addScreenSizeSelectBox() {
        addSelectBox("UI Scale", settings::screenSize, ScreenSize.entries) { _, _ ->
            reloadWorldAndOptions()
        }
    }

    private fun addScreenOrientationSelectBox() {
        if (!Display.hasOrientation()) return
        addSelectBox("Screen orientation", settings::displayOrientation, ScreenOrientation.entries) { orientation, _ ->
            Display.setOrientation(orientation)
            reloadWorldAndOptions()
        }
    }

    private fun addTileSetSelectBox() {
        val unitSets = ImageGetter.getAvailableUnitsets()
        addSelectBox("Tileset", settings::tileSet, ImageGetter.getAvailableTilesets().asIterable()) { newValue, oldValue ->
            // Switch unitSet together with tileSet as long as one with the same name exists and both are selected
            if (oldValue == settings.unitSet && unitSets.contains(newValue)) {
                settings.unitSet = newValue
            }
            // ImageGetter ruleset should be correct no matter what screen we're on
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            reloadWorldAndOptions()
        }
    }

    private fun addUnitSetSelectBox() {
        val nullValue = "None".tr()
        addSelectBox("Unitset", settings::unitSet, ImageGetter.getAvailableUnitsets().asIterable()) { value, _ ->
            if (value == nullValue) settings.unitSet = null
            // ImageGetter ruleset should be correct no matter what screen we're on
            TileSetCache.assembleTileSetConfigs(ImageGetter.ruleset.mods)
            reloadWorldAndOptions()
        }
    }

    private fun addSkinSelectBox() {
        addSelectBox("UI Skin", settings::skin, ImageGetter.getAvailableSkins().asIterable()) { _, _ ->
            // ImageGetter ruleset should be correct no matter what screen we're on
            SkinCache.assembleSkinConfigs(ImageGetter.ruleset.mods)
            reloadWorldAndOptions()
        }
    }

    private fun addResetTutorials() {
        val resetTutorialsButton = "Reset tutorials".toTextButton()
        resetTutorialsButton.onClick {
            ConfirmPopup(
                stage,
                "Do you want to reset completed tutorials?",
                "Reset"
            ) {
                settings.tutorialsShown.clear()
                settings.tutorialTasksCompleted.clear()
                resetTutorialsButton.setText("Done!".tr())
                resetTutorialsButton.clearListeners()
            }.open(true)
        }
        add(resetTutorialsButton).center().row()
    }

    private fun addNotificationScrollSelect() {
        addEnumAsStringSelectBox("Notifications on world screen", settings::notificationScroll, NotificationsScroll.UserSetting.entries) {
            GUI.setUpdateWorldOnNextRender()
        }
    }
}
