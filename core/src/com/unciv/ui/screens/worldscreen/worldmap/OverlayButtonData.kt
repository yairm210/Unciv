package com.unciv.ui.screens.worldscreen.worldmap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.Align
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Spy
import com.unciv.models.UncivSound
import com.unciv.models.UnitActionType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.*
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.overviewscreen.EspionageOverviewScreen

/** Interface for creating floating "action" buttons on tiles */
interface OverlayButtonData{
    fun createButton(worldMapHolder: WorldMapHolder): Actor
}

const val buttonSize = 60f
const val smallerCircleSizes = 25f

class MoveHereOverlayButtonData(val unitToTurnsToDestination: HashMap<MapUnit, Int>, val tile: Tile) :
    OverlayButtonData {
    override fun createButton(worldMapHolder: WorldMapHolder): Actor {
        return getMoveHereButton(worldMapHolder)
    }

    private fun getMoveHereButton(worldMapHolder: WorldMapHolder): Group {
        val isParadrop = unitToTurnsToDestination.keys.all { it.isPreparingParadrop() }
        val image = if (isParadrop)
            ImageGetter.getUnitActionPortrait("Paradrop", buttonSize / 2)
        else ImageGetter.getStatIcon("Movement")
            .apply { color = ImageGetter.CHARCOAL; width = buttonSize / 2; height = buttonSize / 2 }
        val moveHereButton = image
            .surroundWithCircle(buttonSize - 2, false)
            .surroundWithCircle(buttonSize, false, ImageGetter.CHARCOAL)

        if (!isParadrop) {
            val numberCircle = unitToTurnsToDestination.values.maxOrNull()!!.tr().toLabel(fontSize = 14)
                .apply { setAlignment(Align.center) }
                .surroundWithCircle(smallerCircleSizes - 2, color = BaseScreen.skin.getColor("base-40"))
                .surroundWithCircle(smallerCircleSizes, false)
            moveHereButton.addActor(numberCircle)
        }

        val firstUnit = unitToTurnsToDestination.keys.first()
        val unitIcon = if (unitToTurnsToDestination.size == 1) UnitIconGroup(firstUnit, smallerCircleSizes)
        else unitToTurnsToDestination.size.tr().toLabel(fontColor = firstUnit.civ.nation.getInnerColor()).apply { setAlignment(
            Align.center) }
            .surroundWithCircle(smallerCircleSizes).apply { circle.color = firstUnit.civ.nation.getOuterColor() }
        unitIcon.y = buttonSize - unitIcon.height
        moveHereButton.addActor(unitIcon)

        val unitsThatCanMove = unitToTurnsToDestination.keys.filter { it.hasMovement() }
        if (unitsThatCanMove.isEmpty()) moveHereButton.color.a = 0.5f
        else {
            moveHereButton.onActivation(UncivSound.Silent) {
                worldMapHolder.moveUnitToTargetTile(unitsThatCanMove, tile)
            }
            moveHereButton.keyShortcuts.add(KeyCharAndCode.TAB)
        }
        return moveHereButton
    }
}

// Contains the data required to draw a "swap with" button
class SwapWithOverlayButtonData(val unit: MapUnit, val tile: Tile) : OverlayButtonData {
    override fun createButton(worldMapHolder: WorldMapHolder): Actor {
        return getSwapWithButton(worldMapHolder)
    }

    fun getSwapWithButton(worldMapHolder: WorldMapHolder): Group {
        val swapWithButton = Group()
        swapWithButton.setSize(buttonSize, buttonSize)
        swapWithButton.addActor(ImageGetter.getCircle(size = buttonSize))
        swapWithButton.addActor(
            ImageGetter.getImage("OtherIcons/Swap").apply {
                color = ImageGetter.CHARCOAL
                setSize(buttonSize / 2)
                center(swapWithButton)
            }
        )

        val unitIcon = UnitIconGroup(unit, smallerCircleSizes)
        unitIcon.y = buttonSize - unitIcon.height
        swapWithButton.addActor(unitIcon)

        swapWithButton.onActivation(UncivSound.Silent) {
            worldMapHolder.swapMoveUnitToTargetTile(unit, tile)
        }
        swapWithButton.keyShortcuts.add(KeyCharAndCode.TAB)

        return swapWithButton
    }
}

// Contains the data required to draw a "connect road" button
class ConnectRoadOverlayButtonData(val unit: MapUnit, val tile: Tile) : OverlayButtonData {
    override fun createButton(worldMapHolder: WorldMapHolder): Actor {
        return getConnectRoadButton(worldMapHolder)
    }

    private fun getConnectRoadButton(worldMapHolder: WorldMapHolder): Group {
        val connectRoadButton = Group()
        connectRoadButton.setSize(buttonSize, buttonSize)
        connectRoadButton.addActor(ImageGetter.getUnitActionPortrait("RoadConnection", buttonSize * 0.8f).apply {
            center(connectRoadButton)
        }
        )

        val unitIcon = UnitIconGroup(unit, smallerCircleSizes)
        unitIcon.y = buttonSize - unitIcon.height
        connectRoadButton.addActor(unitIcon)

        connectRoadButton.onActivation(UncivSound.Silent) {
            connectRoadToTargetTile(worldMapHolder, unit, tile)
        }
        connectRoadButton.keyShortcuts.add(KeyboardBinding.ConnectRoad)

        return connectRoadButton
    }

    private fun connectRoadToTargetTile(worldMapHolder: WorldMapHolder, selectedUnit: MapUnit, targetTile: Tile) {
        selectedUnit.automatedRoadConnectionDestination = targetTile.position
        selectedUnit.automatedRoadConnectionPath = null
        selectedUnit.action = UnitActionType.ConnectRoad.value
        selectedUnit.automated = true
        UnitAutomation.automateUnitMoves(selectedUnit)

        SoundPlayer.play(UncivSound("wagon"))

        worldMapHolder.worldScreen.shouldUpdate = true
        worldMapHolder.removeUnitActionOverlay()

        // Make highlighting go away
        worldMapHolder.worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad = false
    }
}

// Contains the data required to draw a "move spy" button
class MoveSpyOverlayButtonData(val spy: Spy, val city: City?) : OverlayButtonData {
    override fun createButton(worldMapHolder: WorldMapHolder): Actor {
        return getMoveSpyButton(worldMapHolder)
    }

    private fun getMoveSpyButton(worldMapHolder: WorldMapHolder): Group {
        val spyActionButton = Group()
        spyActionButton.setSize(buttonSize, buttonSize)
        spyActionButton.addActor(ImageGetter.getCircle(size = buttonSize))
        if (city != null) {
            spyActionButton.addActor(
                ImageGetter.getStatIcon("Movement").apply {
                    name = "Button"
                    color = ImageGetter.CHARCOAL
                    setSize(buttonSize / 2)
                    center(spyActionButton)
                }
            )
        } else {
            spyActionButton.addActor(
                ImageGetter.getImage("OtherIcons/Close").apply {
                    name = "Button"
                    color = Color.RED
                    setSize(buttonSize / 2)
                    center(spyActionButton)
                }
            )
        }

        val worldScreen = worldMapHolder.worldScreen
        spyActionButton.onActivation(UncivSound.Silent) {
            if (city != null) {
                spy.moveTo(city)
                worldScreen.game.pushScreen(EspionageOverviewScreen(worldScreen.selectedCiv, worldScreen))
            } else {
                worldScreen.game.pushScreen(EspionageOverviewScreen(worldScreen.selectedCiv, worldScreen))
                worldScreen.bottomUnitTable.selectedSpy = null
            }
            worldMapHolder.removeUnitActionOverlay()
            worldMapHolder.selectedTile = null
            worldScreen.shouldUpdate = true
            worldScreen.bottomUnitTable.selectSpy(null)
        }
        spyActionButton.keyShortcuts.add(KeyCharAndCode.TAB)
        spyActionButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        spyActionButton.keyShortcuts.add(KeyCharAndCode.NUMPAD_ENTER)

        return spyActionButton
    }
}
