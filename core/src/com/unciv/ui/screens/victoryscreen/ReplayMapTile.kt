package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.HexMath
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.minimap.MinimapTile
import kotlin.math.PI
import kotlin.math.atan

// Mostly copied from MinimapTile
internal class ReplayMapTile(val tile: Tile, val tileSize: Float) {
    val image: Image = ImageGetter.getImage("OtherIcons/Hexagon")
    private var cityCircleImage: IconCircleGroup? = null
    private var neighborToBorderImage = HashMap<Tile, Image>()

    init {
        val positionalVector = HexMath.hex2WorldCoords(tile.position)
        image.setSize(tileSize, tileSize)
        image.setPosition(
            positionalVector.x * 0.5f * tileSize,
            positionalVector.y * 0.5f * tileSize
        )
    }

    fun updateColor(owningCiv: Civilization?, isCityCenter: Boolean) {
        image.color = when {
            isCityCenter && !tile.isWater && owningCiv != null -> owningCiv.nation.getInnerColor()
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

    fun updateBorders(owningCiv: Civilization?, turn: Int): ActorChange {
        val imagesBefore = neighborToBorderImage.values.toSet()
        for (neighbor in tile.neighbors) {
            val ownedByCivName = tile.history.getState(turn).ownedByCivName
            val shouldHaveBorderDisplayed = ownedByCivName != null
                    && neighbor.history.getState(turn).ownedByCivName != ownedByCivName
            if (!shouldHaveBorderDisplayed) {
                neighborToBorderImage.remove(neighbor)
                continue
            }
            if (neighbor in neighborToBorderImage &&
                    // If the owner of the city changed from the last to this turn, we still need to
                    // reprocess this image since the color of the border likely changed. I don't
                    // know how this works for the minimap, but it somehow does.
                    ownedByCivName == tile.history.getState(turn - 1).ownedByCivName
            ) continue

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

            val relativeWorldPosition =
                    tile.tileMap.getNeighborTilePositionAsWorldCoords(tile, neighbor)
            val sign = if (relativeWorldPosition.x < 0) -1 else 1
            val angle =
                    sign * (atan(sign * relativeWorldPosition.y / relativeWorldPosition.x) * 180 / PI - 90.0).toFloat()

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

    fun updateCityCircle(
        owningCiv: Civilization?,
        isCityCenter: Boolean,
        isCapital: Boolean
    ): MinimapTile.ActorChange {
        val prevCircle = cityCircleImage

        // I have no idea how the city circle gets removed in the minimap case (there this piece of
        // code is missing and the method is only invoked if the tile has a city center on it. It
        // gets removed somehow though, I tried.
        if (owningCiv == null || !isCityCenter) {
            return MinimapTile.ActorChange(
                if (prevCircle != null) setOf(prevCircle) else emptySet(),
                emptySet()
            )
        }

        val nation = owningCiv.nation
        val nationIconSize =
                (if (isCapital && owningCiv.isMajorCiv()) 1.667f else 1.25f) * image.width
        val cityCircle = ImageGetter.getCircle().apply { color = nation.getInnerColor() }
            .surroundWithCircle(nationIconSize, color = nation.getOuterColor())
        val hexCenterXPosition = image.x + image.width / 2
        cityCircle.x = hexCenterXPosition - nationIconSize / 2
        val hexCenterYPosition = image.y + image.height / 2
        cityCircle.y = hexCenterYPosition - nationIconSize / 2
        cityCircleImage = cityCircle

        return MinimapTile.ActorChange(
            if (prevCircle != null) setOf(prevCircle) else emptySet(),
            setOf(cityCircle)
        )
    }
}
