package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.IconCircleGroup
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.surroundWithCircle
import kotlin.math.max
import kotlin.math.min

class Minimap(val mapHolder: WorldMapHolder, minimapSize: Int) : Table(){
    private val allTiles = Group()
    private val tileImages = HashMap<TileInfo, Image>()
    private val scrollPositionIndicator = ClippingImage(ImageGetter.getDrawable("OtherIcons/Camera"))

    init {
        isTransform = false // don't try to resize rotate etc - this table has a LOT of children so that's valuable render time!

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

//        fun hexRow(vector2: Vector2) = vector2.x + vector2.y
//        val maxHexRow = mapHolder.tileMap.values.asSequence().map { hexRow(it.position) }.maxOrNull()!!
//        val minHexRow = mapHolder.tileMap.values.asSequence().map { hexRow(it.position) }.minOrNull()!!
//        val totalHexRows = maxHexRow - minHexRow
//        val groupSize = (minimapSize + 1) * 200f / totalHexRows
        // On hexagonal maps totalHexRows as calculated above is always 2 * radius.

        // Support rectangular maps with extreme aspect ratios by scaling to the larger coordinate with a slight weighting to make the bounding box 4:3
        val effectiveRadius = with(mapHolder.tileMap.mapParameters) {
            if (shape != MapShape.rectangular) mapSize.radius
            else max (mapSize.height, mapSize.width * 3 / 4) * MapSize.Huge.radius / MapSize.Huge.height
        }
        val mapSizePercent = if (minimapSize < 22) minimapSize + 9 else minimapSize * 5 - 75
        val smallerWorldSize = mapHolder.worldScreen.stage.let { min(it.width,it.height) }
        val groupSize = smallerWorldSize * mapSizePercent / 100 / effectiveRadius
        
        for (tileInfo in mapHolder.tileMap.values) {
            val hex = ImageGetter.getImage("OtherIcons/Hexagon")

            val positionalVector = HexMath.hex2WorldCoords(tileInfo.position)

            hex.setSize(groupSize, groupSize)
            hex.setPosition(positionalVector.x * 0.5f * groupSize,
                    positionalVector.y * 0.5f * groupSize)
            hex.onClick {
                mapHolder.setCenterPosition(tileInfo.position)
            }
            allTiles.addActor(hex)
            tileImages[tileInfo] = hex

            topX = max(topX, hex.x + groupSize)
            topY = max(topY, hex.y + groupSize)
            bottomX = min(bottomX, hex.x)
            bottomY = min(bottomY, hex.y)
        }

        for (group in allTiles.children) {
            group.moveBy(-bottomX, -bottomY)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(topX - bottomX, topY - bottomY)

        scrollPositionIndicator.touchable = Touchable.disabled
        allTiles.addActor(scrollPositionIndicator)

        add(allTiles)
        layout()
    }

    /**### Transform and set coordinates for the scrollPositionIndicator.
     *
     *  Relies on the [MiniMap][MinimapHolder.minimap]'s copy of the main [WorldMapHolder] as input.
     *
     *  Requires [scrollPositionIndicator] to be a [ClippingImage] to keep the displayed portion of the indicator within the bounds of the minimap.
     */
    fun updateScrollPosition() {
        // Only mapHolder.scrollX/Y and mapHolder.scaleX/Y change. scrollX/Y will range from 0 to mapHolder.maxX/Y,
        // with all extremes centering the corresponding map edge on screen. Y axis is 0 top, maxY bottom.
        // Visible area relative to this coordinate system seems to be mapHolder.width/2 * mapHolder.height/2.
        // Minimap coordinates are measured from the allTiles Group, which is a bounding box over the entire map, and (0,0) @ lower left.

        // Helpers for readability - each single use, but they should help explain the logic
        operator fun Rectangle.times(other:Vector2) = Rectangle(x * other.x, y * other.y, width * other.x, height * other.y)
        fun Vector2.centeredRectangle(size: Vector2) = Rectangle(x - size.x/2, y - size.y/2, size.x, size.y)
        fun Rectangle.invertY(max: Float) = Rectangle(x, max - height - y, width, height)
        fun Actor.setViewport(rect: Rectangle) { x = rect.x; y = rect.y; width = rect.width; height = rect.height }

        val worldToMiniFactor = Vector2(allTiles.width / mapHolder.maxX, allTiles.height / mapHolder.maxY)
        val worldVisibleArea = Vector2(mapHolder.width / 2 / mapHolder.scaleX, mapHolder.height / 2 / mapHolder.scaleY)
        val worldViewport = Vector2(mapHolder.scrollX, mapHolder.scrollY).centeredRectangle(worldVisibleArea)
        val miniViewport = worldViewport.invertY(mapHolder.maxY) * worldToMiniFactor
        // This _could_ place parts of the 'camera' icon outside the minimap if it were a standard Image, thus the ClippingImage helper class
        scrollPositionIndicator.setViewport(miniViewport)
    }

    private class CivAndImage(val civInfo: CivilizationInfo, val image: IconCircleGroup)
    private val cityIcons = HashMap<TileInfo, CivAndImage>()

    fun update(cloneCivilization: CivilizationInfo) {
        for((tileInfo, hex) in tileImages) {
            hex.color = when {
                !(UncivGame.Current.viewEntireMapForDebug || cloneCivilization.exploredTiles.contains(tileInfo.position)) -> Color.DARK_GRAY
                tileInfo.isCityCenter() && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getInnerColor()
                tileInfo.getCity() != null && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getOuterColor()
                else -> tileInfo.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)
            }

            if (tileInfo.isCityCenter() && cloneCivilization.exploredTiles.contains(tileInfo.position)
                    && (!cityIcons.containsKey(tileInfo) || cityIcons[tileInfo]!!.civInfo != tileInfo.getOwner())) {
                if (cityIcons.containsKey(tileInfo)) cityIcons[tileInfo]!!.image.remove() // city changed hands - remove old icon
                val nationIcon= ImageGetter.getNationIndicator(tileInfo.getOwner()!!.nation,hex.width * 3)
                nationIcon.setPosition(hex.x - nationIcon.width/3,hex.y - nationIcon.height/3)
                nationIcon.onClick {
                    mapHolder.setCenterPosition(tileInfo.position)
                }
                allTiles.addActor(nationIcon)
                cityIcons[tileInfo] = CivAndImage(tileInfo.getOwner()!!, nationIcon)
            }
        }
    }
}

class MinimapHolder(val mapHolder: WorldMapHolder): Table() {
    private val worldScreen = mapHolder.worldScreen
    private var minimapSize = Int.MIN_VALUE
    lateinit var minimap: Minimap

    private var yieldImageButton: Actor? = null
    private var populationImageButton: Actor? = null
    private var resourceImageButton: Actor? = null

    init {
        rebuildIfSizeChanged()
    }
    private fun rebuildIfSizeChanged() {
        val newMinimapSize = worldScreen.game.settings.minimapSize
        if (newMinimapSize == minimapSize) return
        minimapSize = newMinimapSize
        this.clear()
        minimap = Minimap(mapHolder, minimapSize)
        add(getToggleIcons()).align(Align.bottom)
        add(getWrappedMinimap())
        pack()
        if (stage != null) x = stage.width - width
    }

    private fun getWrappedMinimap(): Table {
        val internalMinimapWrapper = Table()
        internalMinimapWrapper.add(minimap)

        internalMinimapWrapper.background = ImageGetter.getBackground(Color.GRAY)
        internalMinimapWrapper.pack()

        val externalMinimapWrapper = Table()
        externalMinimapWrapper.add(internalMinimapWrapper).pad(5f)
        externalMinimapWrapper.background = ImageGetter.getBackground(Color.WHITE)
        externalMinimapWrapper.pack()

        return externalMinimapWrapper
    }

    private fun getToggleIcons(): Table {
        val toggleIconTable = Table()
        val settings = UncivGame.Current.settings

        val yieldImage = ImageGetter.getStatIcon("Food").surroundWithCircle(40f)
        yieldImage.circle.color = Color.BLACK
        yieldImage.actor.color.a = if (settings.showTileYields) 1f else 0.5f
        yieldImage.onClick {
            settings.showTileYields = !settings.showTileYields
            yieldImage.actor.color.a = if (settings.showTileYields) 1f else 0.5f
            worldScreen.shouldUpdate = true
        }
        toggleIconTable.add(yieldImage).row()

        val populationImage = ImageGetter.getStatIcon("Population").surroundWithCircle(40f)
        populationImage.circle.color = Color.BLACK
        populationImage.actor.color.a = if (settings.showWorkedTiles) 1f else 0.5f
        populationImage.onClick {
            settings.showWorkedTiles = !settings.showWorkedTiles
            populationImage.actor.color.a = if (settings.showWorkedTiles) 1f else 0.5f
            worldScreen.shouldUpdate = true
        }
        toggleIconTable.add(populationImage).row()

        val resourceImage = ImageGetter.getImage("ResourceIcons/Cattle")
                .surroundWithCircle(30f).apply { circle.color = Color.GREEN }
                .surroundWithCircle(40f, false).apply { circle.color = Color.BLACK }

        resourceImage.actor.color.a = if (settings.showResourcesAndImprovements) 1f else 0.5f
        resourceImage.onClick {
            settings.showResourcesAndImprovements = !settings.showResourcesAndImprovements
            resourceImage.actor.color.a = if (settings.showResourcesAndImprovements) 1f else 0.5f
            worldScreen.shouldUpdate = true
        }
        toggleIconTable.add(resourceImage)
        toggleIconTable.pack()

        yieldImageButton = yieldImage.actor
        populationImageButton = populationImage.actor
        resourceImageButton = resourceImage.actor

        return toggleIconTable
    }

    fun update(civInfo: CivilizationInfo) {
        rebuildIfSizeChanged()
        isVisible = UncivGame.Current.settings.showMinimap
        if (isVisible) {
            minimap.update(civInfo)
            with(UncivGame.Current.settings) {
                yieldImageButton?.color?.a = if (showTileYields) 1f else 0.5f
                populationImageButton?.color?.a = if (showWorkedTiles) 1f else 0.5f
                resourceImageButton?.color?.a = if (showResourcesAndImprovements) 1f else 0.5f
            }
        }
    }


    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }
}

private class ClippingImage(drawable: Drawable) : Image(drawable) {
    // https://stackoverflow.com/questions/29448099/make-actor-clip-child-image
    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.flush()
        if (clipBegin(0f,0f, parent.width, parent.height)) {
            super.draw(batch, parentAlpha)
            batch.flush()
            clipEnd()
        }
    }
}
