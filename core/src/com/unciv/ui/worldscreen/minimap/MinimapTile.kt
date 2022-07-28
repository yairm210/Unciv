package com.unciv.ui.worldscreen.minimap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import kotlin.math.PI
import kotlin.math.atan

internal class MinimapTile(val tileInfo: TileInfo, tileSize: Float, val onClick: () -> Unit) {
    companion object {
        val UNREVEALED_COLOR = Color.DARK_GRAY!!
    }

    val image: Image = ImageGetter.getImage("OtherIcons/Hexagon")
    private var cityCircleImage: IconCircleGroup? = null
    var owningCiv: CivilizationInfo? = null
    private var neighborToBorderImage = HashMap<TileInfo, Image>()
    val isUnrevealed get() = image.color == UNREVEALED_COLOR

    init {
        val positionalVector = HexMath.hex2WorldCoords(tileInfo.position)

        image.color = UNREVEALED_COLOR
        image.setSize(tileSize, tileSize)
        image.setPosition(
            positionalVector.x * 0.5f * tileSize,
            positionalVector.y * 0.5f * tileSize
        )
        image.onClick(onClick)
    }

    fun updateColor(isTileUnrevealed: Boolean) {
        image.color = when {
            !UncivGame.Current.viewEntireMapForDebug && isTileUnrevealed -> UNREVEALED_COLOR
            tileInfo.isCityCenter() && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getInnerColor()
            tileInfo.getCity() != null && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getOuterColor()
            else -> tileInfo.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)
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
        for (neighbor in tileInfo.neighbors) {
            val shouldHaveBorderDisplayed = tileInfo.getOwner() != null &&
                neighbor.getOwner() != tileInfo.getOwner()
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

            val relativeWorldPosition = tileInfo.tileMap.getNeighborTilePositionAsWorldCoords(tileInfo, neighbor)
            val sign = if (relativeWorldPosition.x < 0) -1 else 1
            val angle = sign * (atan(sign * relativeWorldPosition.y / relativeWorldPosition.x) * 180 / PI - 90.0).toFloat()

            borderImage.moveBy(
                -relativeWorldPosition.x * hexagonEdgeLength / 2,
                -relativeWorldPosition.y * hexagonEdgeLength / 2
            )
            borderImage.rotateBy(angle)
            borderImage.color = tileInfo.getOwner()!!.nation.getInnerColor()
            neighborToBorderImage[neighbor] = borderImage
        }
        val imagesAfter = neighborToBorderImage.values.toSet()
        return ActorChange(imagesBefore - imagesAfter, imagesAfter - imagesBefore)
    }

    fun updateCityCircle(): ActorChange {
        val prevCircle = cityCircleImage

        val nation = tileInfo.getOwner()!!.nation
        val nationIconSize = (if (tileInfo.getCity()!!.isCapital() && tileInfo.getOwner()!!.isMajorCiv()) 1.667f else 1.25f) * image.width
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
