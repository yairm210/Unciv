package com.unciv.ui.screens.worldscreen.minimap

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.ui.images.ClippingImage
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.*
import com.unciv.ui.components.extensions.*
import com.unciv.ui.screens.worldscreen.WorldMapHolder
import kotlin.math.max
import kotlin.math.min

class Minimap(val mapHolder: WorldMapHolder, minimapSize: Int, private val civInfo: Civilization? = null) : Group() {
    private val tileLayer = Group()
    private val minimapTiles: List<MinimapTile>
    private val scrollPositionIndicators: List<ClippingImage>
    private var lastViewingCiv: Civilization? = null

    init {
        // don't try to resize rotate etc - this table has a LOT of children so that's valuable render time!
        isTransform = false

        var topX = -Float.MAX_VALUE
        var topY = -Float.MAX_VALUE
        var bottomX = Float.MAX_VALUE
        var bottomY = Float.MAX_VALUE

        // Set fixed minimap size
        val stageMinimapSize = calcMinimapSize(minimapSize)
        setSize(stageMinimapSize.x, stageMinimapSize.y)

        // Calculate max tileSize to fit in mimimap
        val tileSize = calcTileSize(stageMinimapSize)
        minimapTiles = createMinimapTiles(tileSize)
        for (image in minimapTiles.map { it.image }) {
            tileLayer.addActor(image)

            // keeps track of the current top/bottom/left/rightmost tiles to size and position the minimap correctly
            topX = max(topX, image.x + tileSize)
            topY = max(topY, image.y + tileSize)
            bottomX = min(bottomX, image.x)
            bottomY = min(bottomY, image.y)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        tileLayer.setSize(topX - bottomX, topY - bottomY)

        // Center tiles in minimap holder
        val padX = (stageMinimapSize.x - tileLayer.width) / 2 - bottomX
        val padY = (stageMinimapSize.y - tileLayer.height) / 2 - bottomY
        for (group in tileLayer.children) {
            group.moveBy(padX, padY)
        }

        scrollPositionIndicators = createScrollPositionIndicators()
        //scrollPositionIndicators.forEach(tileLayer::addActor)

        addActor(tileLayer)

        //mapHolder.onViewportChangedListener = ::updateScrollPosition
    }

    private fun calcTileSize(minimapSize: Vector2): Float {
        val height: Float
        val width: Float
        val mapParameters = mapHolder.tileMap.mapParameters

        if (civInfo != null) {
            height = civInfo.exploredRegion.getHeight().toFloat()
            width = civInfo.exploredRegion.getWidth().toFloat()
        } else {
            if (mapParameters.shape != MapShape.rectangular) {
                val diameter = mapParameters.mapSize.radius * 2 + 1
                height = diameter.toFloat()
                width = diameter.toFloat()
            } else {
                height = mapParameters.mapSize.height.toFloat()
                width = mapParameters.mapSize.width.toFloat()
            }
        }

        val result =
                min(
                    minimapSize.y / (height + 1.5f) / 1.732f * 4, // 1.5 - padding, hex height = 1.732 / 2 * d / 2 -> d = height / 1.732 * 2 * 2
                    minimapSize.x / (width + 0.5f) / 0.75f // 0.5 - padding, hex width = 0.75 * d -> d = width / 0.75
                )

        return result
    }

    private fun calcMinTileSize(minimapSize: Int): Float {
        // Support rectangular maps with extreme aspect ratios by scaling to the larger coordinate with a slight weighting to make the bounding box 4:3
        val effectiveRadius =
            with(mapHolder.tileMap.mapParameters) {
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

    private fun calcMinimapSize(minimapSize: Int): Vector2 {
        val minimapTileSize = calcMinTileSize(minimapSize)
        var height: Float
        var width: Float
        val mapParameters = mapHolder.tileMap.mapParameters

        if (mapParameters.shape != MapShape.rectangular) {
            val diameter = mapParameters.mapSize.radius * 2 + 1
            height = diameter.toFloat()
            width = diameter.toFloat()
        } else {
            height = mapParameters.mapSize.height.toFloat()
            width = mapParameters.mapSize.width.toFloat()
        }

        // hex height = 1.732 / 2 * d / 2, number of rows = mapDiameter * 2
        height *= minimapTileSize * 1.732f / 2
        // hex width = 0.75 * d
        width =
                if (mapParameters.worldWrap)
                    (width - 1) * minimapTileSize * 0.75f
                else
                    width * minimapTileSize * 0.75f

        return Vector2(width, height)
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
        val pad = if(mapHolder.tileMap.mapParameters.shape != MapShape.rectangular)
                mapHolder.tileMap.mapParameters.mapSize.radius * tileSize * 1.5f
            else
                (mapHolder.tileMap.mapParameters.mapSize.width - 1) * tileSize * 0.75f
        val leftSide = if(civInfo != null) civInfo.exploredRegion.getMinimapLeft(tileSize) else -Float.MAX_VALUE
        for (tileInfo in mapHolder.tileMap.values) {
            if (civInfo?.exploredRegion?.isPositionInRegion(tileInfo.position) == false) continue
            val minimapTile = MinimapTile(tileInfo, tileSize, onClick = {
                mapHolder.setCenterPosition(tileInfo.position)
            })
            if(minimapTile.image.x < leftSide)
                minimapTile.image.x += pad
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

    fun update(viewingCiv: Civilization) {
        for (minimapTile in minimapTiles) {
            val tileInfo = minimapTile.tile
            val ownerChanged = minimapTile.owningCiv != tileInfo.getOwner()
            if (ownerChanged) {
                minimapTile.owningCiv = tileInfo.getOwner()
            }

            val shouldBeUnrevealed = !viewingCiv.hasExplored(tileInfo) && !viewingCiv.isSpectator()
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

