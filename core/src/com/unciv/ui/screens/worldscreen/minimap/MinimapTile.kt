package com.unciv.ui.screens.worldscreen.minimap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.TileHistory.TileHistoryState.CityCenterType
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.DebugUtils
import kotlin.math.PI
import kotlin.math.atan

class MinimapTile(val tile: Tile, tileSize: Float, val onClick: () -> Unit) {
    val image: Image = ImageGetter.getImage("OtherIcons/Hexagon")
    private var cityCircleImage: IconCircleGroup? = null
    var owningCiv: Civilization? = null
    private var neighborToBorderImage = HashMap<Tile, Image>()
    val isUnrevealed get() = !image.isVisible

    init {
        val positionalVector = HexMath.hex2WorldCoords(tile.position)

        image.isVisible = false
        image.setSize(tileSize, tileSize)
        image.setPosition(
            positionalVector.x * 0.5f * tileSize,
            positionalVector.y * 0.5f * tileSize
        )
        image.onClick(onClick)
    }

    fun updateColor(isTileUnrevealed: Boolean, turn: Int? = null) {
        image.isVisible = DebugUtils.VISIBLE_MAP || !isTileUnrevealed
        if (!image.isVisible) return
        val isCityCenter =
                if (turn == null) tile.isCityCenter() else tile.history.getState(turn).cityCenterType != CityCenterType.None
        val owningCiv = if (turn == null) tile.getOwner() else getOwningCivFromHistory(tile, turn)
        image.color = when {
            isCityCenter && !tile.isWater -> owningCiv!!.nation.getInnerColor()
            owningCiv != null && !tile.isWater -> owningCiv.nation.getOuterColor()
            else -> tile.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)
        }
    }

    class ActorChange(val removed: Set<Actor>, val added: Set<Actor>) {
        fun updateActorsIn(group: Group) {
            removed.forEach { group.removeActor(it) }
            added.forEach { group.addActor(it) }
        }
    }

    fun updateBorders(turn: Int? = null): ActorChange {
        val owningCiv = if (turn == null) tile.getOwner() else getOwningCivFromHistory(tile, turn)
        val imagesBefore = neighborToBorderImage.values.toSet()
        for (neighbor in tile.neighbors) {
            val neighborOwningCiv =
                    if (turn == null) neighbor.getOwner() else getOwningCivFromHistory(
                        neighbor,
                        turn
                    )
            val shouldHaveBorderDisplayed = owningCiv != null
                    && neighborOwningCiv != owningCiv
            if (!shouldHaveBorderDisplayed) {
                neighborToBorderImage.remove(neighbor)
                continue
            }
            if (neighbor in neighborToBorderImage) {
                neighborToBorderImage[neighbor]!!.color = owningCiv!!.nation.getInnerColor()
                continue
            }

            val borderImage = ImageGetter.getWhiteDot()

            // copied from tilegroup border logic

            val hexagonEdgeLength = image.width / 2

            borderImage.setSize(hexagonEdgeLength, hexagonEdgeLength / 4)
            borderImage.setOrigin(Align.center)
            val hexagonCenterX = image.x + image.width / 2
            borderImage.x = hexagonCenterX - borderImage.width / 2
            val hexagonCenterY = image.y + image.height / 2
            borderImage.y = hexagonCenterY - borderImage.height / 2
            // Until this point, the border image is now CENTERED on the tile it's a border for

            val relativeWorldPosition = tile.tileMap.getNeighborTilePositionAsWorldCoords(tile, neighbor)
            val sign = if (relativeWorldPosition.x < 0) -1 else 1
            val angle = sign * (atan(sign * relativeWorldPosition.y / relativeWorldPosition.x) * 180 / PI - 90.0).toFloat()

            borderImage.moveBy(
                -relativeWorldPosition.x * hexagonEdgeLength / 2,
                -relativeWorldPosition.y * hexagonEdgeLength / 2
            )
            borderImage.rotateBy(angle)
            borderImage.color = owningCiv!!.nation.getInnerColor()
            neighborToBorderImage[neighbor] = borderImage
        }
        val imagesAfter = neighborToBorderImage.values.toSet()
        return ActorChange(imagesBefore - imagesAfter, imagesAfter - imagesBefore)
    }

    fun updateCityCircle(turn: Int? = null): ActorChange {
        val prevCircle = cityCircleImage
        val owningCiv = if (turn == null) tile.getOwner() else getOwningCivFromHistory(tile, turn)
        val isCityCenter =
                if (turn == null) tile.isCityCenter() else tile.history.getState(turn).cityCenterType != CityCenterType.None

        if (owningCiv == null || !isCityCenter) {
            return ActorChange(
                if (prevCircle != null) setOf(prevCircle) else emptySet(),
                emptySet()
            )
        }

        val nation = owningCiv.nation
        val isCapital =
                if (turn == null)
                    tile.getCity()!!.isCapital()
                else
                    tile.history.getState(turn).cityCenterType ==
                            CityCenterType.Capital
        val nationIconSize = (if (isCapital && owningCiv.isMajorCiv()) 1.667f else 1.25f) * image.width
        val cityCircle = ImageGetter.getCircle(nation.getInnerColor())
            .surroundWithCircle(nationIconSize, color = nation.getOuterColor())
        val hexCenterXPosition = image.x + image.width / 2
        cityCircle.x = hexCenterXPosition - nationIconSize / 2
        val hexCenterYPosition = image.y + image.height / 2
        cityCircle.y = hexCenterYPosition - nationIconSize / 2
        cityCircle.onClick(onClick)
        cityCircleImage = cityCircle

        return ActorChange(if (prevCircle != null) setOf(prevCircle) else emptySet(), setOf(cityCircle))
    }

    fun getOwningCivFromHistory(tile: Tile, turn: Int) : Civilization? {
        val owningCivName = tile.history.getState(turn).owningCivName
        return if (owningCivName == null) null else tile.tileMap.gameInfo.getCivilization(
            owningCivName
        )
    }

}
