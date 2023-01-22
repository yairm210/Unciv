package com.unciv.ui.worldscreen.minimap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.HexMath
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import kotlin.math.PI
import kotlin.math.atan

internal class MinimapTile(val tile: Tile, tileSize: Float, val onClick: () -> Unit) {
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

    fun updateColor(isTileUnrevealed: Boolean) {
        image.isVisible = UncivGame.Current.viewEntireMapForDebug || !isTileUnrevealed
        if (!image.isVisible) return
        image.color = when {
            tile.isCityCenter() && !tile.isWater -> tile.getOwner()!!.nation.getInnerColor()
            tile.getCity() != null && !tile.isWater -> tile.getOwner()!!.nation.getOuterColor()
            else -> tile.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)
        }
    }

    class ActorChange(val removed: Set<Actor>, val added: Set<Actor>) {
        fun updateActorsIn(group: Group) {
            removed.forEach { group.removeActor(it) }
            added.forEach { group.addActor(it) }
        }
    }

    fun updateBorders(): ActorChange {
        val imagesBefore = neighborToBorderImage.values.toSet()
        for (neighbor in tile.neighbors) {
            val shouldHaveBorderDisplayed = tile.getOwner() != null
                    && neighbor.getOwner() != tile.getOwner()
            if (!shouldHaveBorderDisplayed) {
                neighborToBorderImage.remove(neighbor)
                continue
            }
            if (neighbor in neighborToBorderImage) continue

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
            borderImage.color = tile.getOwner()!!.nation.getInnerColor()
            neighborToBorderImage[neighbor] = borderImage
        }
        val imagesAfter = neighborToBorderImage.values.toSet()
        return ActorChange(imagesBefore - imagesAfter, imagesAfter - imagesBefore)
    }

    fun updateCityCircle(): ActorChange {
        val prevCircle = cityCircleImage

        val nation = tile.getOwner()!!.nation
        val nationIconSize = (if (tile.getCity()!!.isCapital() && tile.getOwner()!!.isMajorCiv()) 1.667f else 1.25f) * image.width
        val cityCircle = ImageGetter.getCircle().apply { color = nation.getInnerColor() }
            .surroundWithCircle(nationIconSize, color = nation.getOuterColor())
        val hexCenterXPosition = image.x + image.width / 2
        cityCircle.x = hexCenterXPosition - nationIconSize / 2
        val hexCenterYPosition = image.y + image.height / 2
        cityCircle.y = hexCenterYPosition - nationIconSize / 2
        cityCircle.onClick(onClick)
        cityCircleImage = cityCircle

        return ActorChange(if (prevCircle != null) setOf(prevCircle) else emptySet(), setOf(cityCircle))
    }
}
