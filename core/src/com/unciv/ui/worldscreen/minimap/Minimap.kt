package com.unciv.ui.worldscreen.minimap

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.ui.images.ClippingImage
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.*
import com.unciv.ui.utils.extensions.*
import com.unciv.ui.worldscreen.WorldMapHolder
import kotlin.math.max
import kotlin.math.min

class Minimap(val mapHolder: WorldMapHolder, minimapSize: Int) : Group() {
    private val tileLayer = Group()
    private val minimapTiles: List<MinimapTile>
    private val scrollPositionIndicators: List<ClippingImage>
    private var lastViewingCiv: CivilizationInfo? = null

    init {
        // don't try to resize rotate etc - this table has a LOT of children so that's valuable render time!
        isTransform = false

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        val tileSize = calcTileSize(minimapSize)
        minimapTiles = createMinimapTiles(tileSize)
        for (image in minimapTiles.map { it.image }) {
            tileLayer.addActor(image)

            // keeps track of the current top/bottom/left/rightmost tiles to size and position the minimap correctly
            topX = max(topX, image.x + tileSize)
            topY = max(topY, image.y + tileSize)
            bottomX = min(bottomX, image.x)
            bottomY = min(bottomY, image.y)
        }

        for (group in tileLayer.children) {
            group.moveBy(-bottomX, -bottomY)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        tileLayer.setSize(topX - bottomX, topY - bottomY)

        scrollPositionIndicators = createScrollPositionIndicators()
        scrollPositionIndicators.forEach(tileLayer::addActor)

        setSize(tileLayer.width, tileLayer.height)
        addActor(tileLayer)

        mapHolder.onViewportChangedListener = ::updateScrollPosition
    }

    private fun calcTileSize(minimapSize: Int): Float {
        // Support rectangular maps with extreme aspect ratios by scaling to the larger coordinate with a slight weighting to make the bounding box 4:3
        val effectiveRadius = with(mapHolder.tileMap.mapParameters) {
            if (shape != MapShape.rectangular) mapSize.radius
            else max(
                mapSize.height,
                mapSize.width * 3 / 4
            ) * MapSize.Huge.radius / MapSize.Huge.height
        }
        val mapSizePercent = if (minimapSize < 22) minimapSize + 9 else minimapSize * 5 - 75
        val smallerWorldDimension = mapHolder.worldScreen.stage.let { min(it.width, it.height) }
        return smallerWorldDimension * mapSizePercent / 100 / effectiveRadius
    }

    private fun createScrollPositionIndicators(): List<ClippingImage> {
        // If we are continuous scrolling (world wrap), add another 2 scrollPositionIndicators which
        // get drawn at proper offsets to simulate looping
        val indicatorAmount = if (mapHolder.continuousScrollingX) 3 else 1

        return List(indicatorAmount, init = {
            val indicator = ClippingImage(ImageGetter.getDrawable("OtherIcons/Camera"))
            indicator.touchable = Touchable.disabled
            return@List indicator
        })
    }

    private fun createMinimapTiles(tileSize: Float): List<MinimapTile> {
        val tiles = ArrayList<MinimapTile>()
        for (tileInfo in mapHolder.tileMap.values) {
            val minimapTile = MinimapTile(tileInfo, tileSize, onClick = {
                mapHolder.setCenterPosition(tileInfo.position)
            })
            tiles.add(minimapTile)
        }
        return tiles
    }

    /**### Transform and set coordinates for the scrollPositionIndicator.
     *
     *  Requires [scrollPositionIndicator] to be a [ClippingImage] to keep the displayed portion of the indicator within the bounds of the minimap.
     */
    private fun updateScrollPosition(worldWidth: Float, worldHeight: Float, worldViewport: Rectangle) {
        operator fun Rectangle.times(other: Vector2) = Rectangle(x * other.x, y * other.y, width * other.x, height * other.y)
        fun Actor.setViewport(rect: Rectangle) {
            x = rect.x;
            y = rect.y;
            width = rect.width;
            height = rect.height
        }

        val worldToMiniFactor = Vector2(tileLayer.width / worldWidth, tileLayer.height / worldHeight)
        val miniViewport = worldViewport * worldToMiniFactor
        // This _could_ place parts of the 'camera' icon outside the minimap if it were a standard Image, thus the ClippingImage helper class
        scrollPositionIndicators[0].setViewport(miniViewport)

        // If world wrap enabled, draw another 2 viewports at proper offset to simulate wrapping
        if (scrollPositionIndicators.size != 1) {
            miniViewport.x -= tileLayer.width
            scrollPositionIndicators[1].setViewport(miniViewport)
            miniViewport.x += tileLayer.width * 2
            scrollPositionIndicators[2].setViewport(miniViewport)
        }
    }

    fun update(viewingCiv: CivilizationInfo) {
        for (minimapTile in minimapTiles) {
            val tileInfo = minimapTile.tileInfo
            val ownerChanged = minimapTile.owningCiv != tileInfo.getOwner()
            if (ownerChanged) {
                minimapTile.owningCiv = tileInfo.getOwner()
            }

            val shouldBeUnrevealed = tileInfo.position !in viewingCiv.exploredTiles && !viewingCiv.isSpectator()
            val revealStatusChanged = minimapTile.isUnrevealed != shouldBeUnrevealed
            if (revealStatusChanged || ownerChanged) {
                minimapTile.updateColor(shouldBeUnrevealed)
            }

            // If owner didn't change, neither city circle nor borders can have changed
            if (shouldBeUnrevealed || !ownerChanged) continue

            if (tileInfo.isCityCenter()) {
                minimapTile.updateCityCircle().updateActorsIn(this)
            }

            minimapTile.updateBorders().updateActorsIn(this)
        }
        lastViewingCiv = viewingCiv
    }


    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

