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
import com.unciv.ui.utils.*
import kotlin.math.PI
import kotlin.math.atan
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.surroundWithCircle
import kotlin.math.max
import kotlin.math.min

class Minimap(val mapHolder: WorldMapHolder, minimapSize: Int) : Group(){
    private val allTiles = Group()
    class MinimapTileImages(val tileHexagonImage:Image) {
        var cityCircleImage: IconCircleGroup? = null
        var owningCiv: CivilizationInfo? = null
        var neighborToBorderImage = HashMap<TileInfo,Image>()
    }
    private val tileinfoToImages = HashMap<TileInfo, MinimapTileImages>()
    private val scrollPositionIndicators = ArrayList<ClippingImage>()
    
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
            tileinfoToImages[tileInfo] = MinimapTileImages(hex)

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

        scrollPositionIndicators.add(ClippingImage(ImageGetter.getDrawable("OtherIcons/Camera")))
        // If we are continuous scrolling (world wrap), add another 2 scrollPositionIndicators which
        // get drawn at proper offsets to simulate looping
        if (mapHolder.continuousScrollingX) {
          scrollPositionIndicators.add(ClippingImage(ImageGetter.getDrawable("OtherIcons/Camera")))
          scrollPositionIndicators.add(ClippingImage(ImageGetter.getDrawable("OtherIcons/Camera")))
        }
        for (indicator in scrollPositionIndicators) {
          indicator.touchable = Touchable.disabled
          allTiles.addActor(indicator)
        }

        setSize(allTiles.width, allTiles.height)
        addActor(allTiles)
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
        scrollPositionIndicators[0].setViewport(miniViewport)
        
        // If world wrap enabled, draw another 2 viewports at proper offset to simulate wrapping
        if (scrollPositionIndicators.size != 1) {
          miniViewport.x -= allTiles.width
          scrollPositionIndicators[1].setViewport(miniViewport)
          miniViewport.x += allTiles.width * 2
          scrollPositionIndicators[2].setViewport(miniViewport)
        }
    }

    fun update(cloneCivilization: CivilizationInfo) {
        for ((tileInfo, tileImages) in tileinfoToImages) {
            tileImages.tileHexagonImage.color = when {
                !(UncivGame.Current.viewEntireMapForDebug || cloneCivilization.exploredTiles.contains(tileInfo.position)) -> Color.DARK_GRAY
                tileInfo.isCityCenter() && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getInnerColor()
                tileInfo.getCity() != null && !tileInfo.isWater -> tileInfo.getOwner()!!.nation.getOuterColor()
                else -> tileInfo.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)
            }
            
            if (!cloneCivilization.exploredTiles.contains(tileInfo.position)) continue
            
            if (tileInfo.isCityCenter() && tileImages.owningCiv != tileInfo.getOwner()) {
                tileImages.cityCircleImage?.remove()
                val nation = tileInfo.getOwner()!!.nation
                val hex = tileImages.tileHexagonImage                
                val nationIconSize = (if (tileInfo.getCity()!!.isCapital() && tileInfo.getOwner()!!.isMajorCiv()) 1.667f else 1.25f)* hex.width
                val nationIcon= ImageGetter.getCircle().apply { color = nation.getInnerColor() }
                    .surroundWithCircle(nationIconSize, color = nation.getOuterColor())
                val hexCenterXPosition = hex.x + hex.width/2
                nationIcon.x = hexCenterXPosition - nationIconSize/2
                val hexCenterYPosition = hex.y + hex.height/2
                nationIcon.y = hexCenterYPosition - nationIconSize/2
                nationIcon.onClick {
                    mapHolder.setCenterPosition(tileInfo.position)
                }
                tileImages.cityCircleImage = nationIcon
                addActor(nationIcon)
            }
            
            if (tileImages.owningCiv != tileInfo.getOwner()){
                tileImages.neighborToBorderImage.values.forEach { it.remove() }
                tileImages.owningCiv = tileInfo.getOwner()
            }
            
            for (neighbor in tileInfo.neighbors){
                val shouldHaveBorderDisplayed = tileInfo.getOwner() != null &&
                        neighbor.getOwner() != tileInfo.getOwner() 
                if (!shouldHaveBorderDisplayed) {
                    tileImages.neighborToBorderImage[neighbor]?.remove()
                    tileImages.neighborToBorderImage.remove(neighbor)
                    continue
                }
                if (tileImages.neighborToBorderImage.containsKey(neighbor)) continue
                val borderImage = ImageGetter.getWhiteDot()
                
                // copied from tilegroup border logic
                
                val hexagonEdgeLength = tileImages.tileHexagonImage.width/2

                borderImage.setSize(hexagonEdgeLength, hexagonEdgeLength/4)
                borderImage.setOrigin(Align.center)
                val hexagonCenterX = tileImages.tileHexagonImage.x + tileImages.tileHexagonImage.width/2
                borderImage.x = hexagonCenterX - borderImage.width/2
                val hexagonCenterY = tileImages.tileHexagonImage.y + tileImages.tileHexagonImage.height/2
                borderImage.y = hexagonCenterY - borderImage.height/2
                // Until this point, the border image is now CENTERED on the tile it's a border for

                val relativeWorldPosition = tileInfo.tileMap.getNeighborTilePositionAsWorldCoords(tileInfo, neighbor)
                val sign = if (relativeWorldPosition.x < 0) -1 else 1
                val angle = sign * (atan(sign * relativeWorldPosition.y / relativeWorldPosition.x) * 180 / PI - 90.0).toFloat()

                borderImage.moveBy(-relativeWorldPosition.x * hexagonEdgeLength/2,
                    -relativeWorldPosition.y * hexagonEdgeLength/2)
                borderImage.rotateBy(angle)
                borderImage.color = tileInfo.getOwner()!!.nation.getInnerColor()
                addActor(borderImage)
            }
        }
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

class MinimapHolder(val mapHolder: WorldMapHolder): Table() {
    private val worldScreen = mapHolder.worldScreen
    private var minimapSize = Int.MIN_VALUE
    lateinit var minimap: Minimap

    /**
     * Class that unifies the behaviour of the little green map overlay toggle buttons shown next to the minimap.
     *
     * @param icon An [Image] to display.
     * @property getter A function that returns the current backing state of the toggle.
     * @property setter A function for setting the backing state of the toggle.
     * @param backgroundColor If non-null, a background colour to show behind the image.
     */
    class MapOverlayToggleButton(
        icon: Image,
        private val getter: () -> Boolean,
        private val setter: (Boolean) -> Unit,
        backgroundColor: Color? = null
    ) {
        /** [Actor] of the button. Add this to whatever layout. */
        val actor: IconCircleGroup by lazy {
            var innerActor: Actor = icon
            if (backgroundColor != null) {
                innerActor = innerActor
                    .surroundWithCircle(30f)
                    .apply { circle.color = backgroundColor }
            }
            // So, the "Food" and "Population" stat icons have green as part of their image, but the "Cattle" icon needs a background colour, which is… An interesting mixture/reuse of texture data and render-time processing.
            innerActor.surroundWithCircle(40f).apply { circle.color = Color.BLACK }
        }

        init {
            actor.onClick(::toggle)
        }

        /** Toggle overlay. Called on click. */
        fun toggle() {
            setter(!getter())
            UncivGame.Current.worldScreen.shouldUpdate = true
            // Setting worldScreen.shouldUpdate implicitly causes this.update() to be called by the WorldScreen on the next update.
        }

        /** Update. Called via [WorldScreen.shouldUpdate] on toggle. */
        fun update() {
            actor.actor.color.a = if (getter()) 1f else 0.5f
        }
    }

    /** Button, next to the minimap, to toggle the unit movement map overlay. */
    val movementsImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("StatIcons/Movement").apply { setColor(0f, 0f, 0f, 1f) },
        getter = { UncivGame.Current.settings.showUnitMovements },
        setter = { UncivGame.Current.settings.showUnitMovements = it },
        backgroundColor = Color.GREEN
    )
    /** Button, next to the minimap, to toggle the tile yield map overlay. */
    val yieldImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("StatIcons/Food"),
        // This is a use in the UI that has little to do with the stat… These buttons have more in common with each other than they do with other uses of getStatIcon().
        getter = { UncivGame.Current.settings.showTileYields },
        setter = { UncivGame.Current.settings.showTileYields = it }
    )
    /** Button, next to the minimap, to toggle the worked tiles map overlay. */
    val populationImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("StatIcons/Population"),
        getter = { UncivGame.Current.settings.showWorkedTiles },
        setter = { UncivGame.Current.settings.showWorkedTiles = it }
    )
    /** Button, next to the minimap, to toggle the resource icons map overlay. */
    val resourceImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("ResourceIcons/Cattle"),
        getter = { UncivGame.Current.settings.showResourcesAndImprovements },
        setter = { UncivGame.Current.settings.showResourcesAndImprovements = it },
        backgroundColor = Color.GREEN
    )

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

    /** @return Layout table for the little green map overlay toggle buttons, show to the left of the minimap. */
    private fun getToggleIcons(): Table {
        val toggleIconTable = Table()

        toggleIconTable.add(movementsImageButton.actor).row()
        toggleIconTable.add(yieldImageButton.actor).row()
        toggleIconTable.add(populationImageButton.actor).row()
        toggleIconTable.add(resourceImageButton.actor).row()

        return toggleIconTable
    }

    fun update(civInfo: CivilizationInfo) {
        rebuildIfSizeChanged()
        isVisible = UncivGame.Current.settings.showMinimap
        if (isVisible) {
            minimap.update(civInfo)
            movementsImageButton.update()
            yieldImageButton.update()
            populationImageButton.update()
            resourceImageButton.update()
        }
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
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
