package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.MapArrowType
import com.unciv.ui.components.MiscArrowTypes
import com.unciv.ui.components.TintedMapArrow
import com.unciv.ui.components.UnitMovementMemoryType
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.centerX
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toPrettyString
import com.unciv.ui.components.tilegroups.CityTileGroup
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.ui.components.tilegroups.YieldGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.DebugUtils
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private class MapArrow(val targetTile: Tile, val arrowType: MapArrowType, val strings: TileSetStrings) {

    private fun getArrowImage(imageName: String) = ImageGetter.getImage(
        strings.orFallback { getString(tileSetLocation, "Arrows/", imageName) })


    fun getImage(): Image = when (arrowType) {
        is UnitMovementMemoryType -> getArrowImage(arrowType.name)
        is MiscArrowTypes -> getArrowImage(arrowType.name)
        is TintedMapArrow -> getArrowImage("Generic").apply { color = arrowType.color }
        else -> getArrowImage("Generic")
    }
}

class TileLayerYield(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size){
    private val yields = YieldGroup().apply {
        // Unlike resource or improvement this is created and added only once,
        // It's the contents that get updated
        isVisible = false
        setOrigin(Align.center)
        setScale(0.7f)
        y = tileGroup.height * 0.25f - height / 2
        // Adding YieldGroup to miscLayerGroup
        this@TileLayerYield.addActor(this)
    }
    
    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        val showTileYields = if (tileGroup is WorldTileGroup) UncivGame.Current.settings.showTileYields else true
        updateYieldIcon(viewingCiv, showTileYields, localUniqueCache)
    }

    // JN updating display of tile yields
    private fun updateYieldIcon(
        viewingCiv: Civilization?,
        show: Boolean,
        localUniqueCache: LocalUniqueCache
    ) {
        val effectiveVisible = show &&
                !tileGroup.isForMapEditorIcon &&  // don't have a map to calc yields
                !(viewingCiv == null && tileGroup.isForceVisible) // main menu background

        // Hiding yield icons (in order to update)
        yields.isVisible = false
        if (effectiveVisible) yields.run {
            // Update YieldGroup Icon
            if (tileGroup is CityTileGroup)
                setStats(tile.stats.getTileStats(tileGroup.city, viewingCiv, localUniqueCache))
            else
                setStats(tile.stats.getTileStats(viewingCiv, localUniqueCache))
            toFront()
            centerX(tileGroup)
            isVisible = true
        }
    }

    fun setYieldVisible(isVisible: Boolean) {
        yields.isVisible = isVisible
        this.isVisible = isVisible // don't try rendering the layer if there's nothing in it
    }
    
    fun dimYields(dim: Boolean) { yields.color.a = if (dim) 0.5f else 1f }

    fun reset(localUniqueCache: LocalUniqueCache) {
        updateYieldIcon(null, false, localUniqueCache)
    }

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

class TileLayerMisc(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    // For different unit views, we want to effectively "ignore" the terrain and color it by special view
    private var terrainOverlay: Image? = ImageGetter.getImage(strings.hexagon).setHexagonSize()

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        return if (workedIcon == null) {
            null
        } else {
            val coords = Vector2(x, y)
            workedIcon!!.parentToLocalCoordinates(coords)
            workedIcon!!.hit(coords.x, coords.y, touchable)
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

    /** Array list of all arrows to draw from this tile on the next update. */
    private val arrowsToDraw = ArrayList<MapArrow>()
    private val arrows = HashMap<Tile, ArrayList<Actor>>()

    private var hexOutlineIcon: Actor? = null

    private var resourceName: String? = null
    private var resourceAmount: Int = -1
    private var resourceIcon: Actor? = null

    private var workedIcon: Actor? = null

    private var improvementPlusPillagedID: String? = null
    var improvementIcon: Actor? = null
        private set  // Getter public for BattleTable to display as City Combatant

    private val startingLocationIcons = mutableListOf<Actor>()

    private fun clearArrows() {
        for (actorList in arrows.values)
            for (actor in actorList)
                actor.remove()
        arrows.clear()
    }

    private fun updateArrows() {
        clearArrows()
        val tileScale = 50f * 0.8f // See notes in updateRoadImages.

        for (arrowToAdd in arrowsToDraw) {
            val targetTile = arrowToAdd.targetTile
            var targetPos = Vector2(targetTile.position)
            if (tile.tileMap.mapParameters.worldWrap)
                targetPos = HexMath.getUnwrappedNearestTo(targetPos,
                    tile.position, tile.tileMap.maxLongitude)
            val targetRelative = HexMath.hex2WorldCoords(targetPos)
                .sub(HexMath.hex2WorldCoords(tile.position))

            val targetDistance = sqrt(targetRelative.x.pow(2) + targetRelative.y.pow(2))
            val targetAngle = atan2(targetRelative.y, targetRelative.x)

            if (targetTile !in arrows) {
                arrows[targetTile] = ArrayList()
            }

            val arrowImage = arrowToAdd.getImage()
            arrowImage.moveBy(25f, -5f) // Move to tile center— Y is +25f too, but subtract half the image height. Based on updateRoadImages.

            arrowImage.setSize(tileScale * targetDistance, 60f)
            arrowImage.setOrigin(0f, 30f)

            arrowImage.rotation = targetAngle / Math.PI.toFloat() * 180

            arrows[targetTile]!!.add(arrowImage)
            addActor(arrowImage)
            // FIXME: Culled when too large and panned away.
            // https://libgdx.badlogicgames.com/ci/nightlies/docs/api/com/badlogic/gdx/scenes/scene2d/utils/Cullable.html
            // .getCullingArea returns null for both miscLayerGroup and worldMapHolder. Don't know where it's happening. Somewhat rare, and fixing it may have a hefty performance cost.
        }
    }

    private fun updateImprovementIcon(viewingCiv: Civilization?, show: Boolean) {
        // If improvement has changed, force new icon next time it is needed
        val improvementToShow = tile.getShownImprovement(viewingCiv)
        val newImprovementPlusPillagedID = if (improvementToShow==null) null
        else if (tile.improvementIsPillaged) "$improvementToShow-Pillaged"
        else improvementToShow

        if (improvementPlusPillagedID != newImprovementPlusPillagedID) {
            improvementPlusPillagedID = newImprovementPlusPillagedID
            improvementIcon?.remove()
            improvementIcon = null
        }

        // Get new icon when needed
        if (improvementPlusPillagedID != null && show && improvementIcon == null) {
            val icon = ImageGetter.getImprovementPortrait(improvementToShow!!, dim = false, isPillaged = tile.improvementIsPillaged)
            icon.center(tileGroup)
            icon.x -= 22 // left
            icon.y -= 12 // bottom
            addActor(icon)
            improvementIcon = icon
        }

        improvementIcon?.isVisible = show
    }

    private fun updateResourceIcon(viewingCiv: Civilization?, show: Boolean) {
        // This could change on any turn, since resources need certain techs to reveal them
        val effectiveVisible = when {
            tileGroup.isForceVisible -> show
            show && viewingCiv == null -> true
            show && tile.hasViewableResource(viewingCiv!!) -> true
            else -> false
        }

        // If resource has changed (e.g. tech researched) - force new icon next time it's needed
        if (resourceName != tile.resource || resourceAmount != tile.resourceAmount) {
            resourceName = tile.resource
            resourceAmount = tile.resourceAmount
            resourceIcon?.remove()
            resourceIcon = null
        }

        // Get a fresh Icon if and only if necessary
        if (resourceName != null && effectiveVisible && resourceIcon == null) {
            val icon = ImageGetter.getResourcePortrait(resourceName!!, 20f, resourceAmount)
            icon.center(tileGroup)
            icon.x -= 22 // left
            icon.y += 10 // top
            addActor(icon)
            resourceIcon = icon
        }

        resourceIcon?.isVisible = effectiveVisible


        if (resourceIcon!=null){
            val isViewable = viewingCiv == null || isViewable(viewingCiv)
            dimResource(!isViewable)
        }
    }

    private fun updateStartingLocationIcon(show: Boolean) {
        // The starting location icons are visible in map editor only, but this method is abused for the
        // "Show coordinates on tiles" debug option as well. Calling code made sure this is only called
        // with isVisible=false for reset, or for non-WorldMap TileGroups, or with the debug option set.
        // Note that starting locations should always be empty on the normal WorldMap - they're cleared after use.
        // Also remember the main menu background is an EditorMapHolder which we can't distinguish from
        // The actual editor use here.

        startingLocationIcons.forEach { it.remove() }
        startingLocationIcons.clear()
        if (!show || tileGroup.isForMapEditorIcon)
            return

        if (DebugUtils.SHOW_TILE_COORDS) {
            val label = this.tile.position.toPrettyString()
            startingLocationIcons.add(label.toLabel(ImageGetter.CHARCOAL.cpy().apply { a = 0.7f }, 14).apply {
                tileGroup.layerMisc.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(15.4f, -0.6f)
            })
            startingLocationIcons.add(label.toLabel(Color.FIREBRICK, 14).apply {
                tileGroup.layerMisc.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(15f, 0f)
            })
        }

        val tilemap = tile.tileMap

        if (tilemap.startingLocationsByNation.isEmpty())
            return

        // Allow display of up to three nations starting locations on the same tile, rest only as count.
        // Sorted so major get precedence and to make the display deterministic, otherwise you could get
        // different stacking order of the same nations in the same editing session
        val nations = tilemap.startingLocationsByNation.asSequence()
            .filter { tile in it.value }
            .filter { it.key in tilemap.ruleset!!.nations } // Ignore missing nations
            .map { it.key to tilemap.ruleset!!.nations[it.key]!! }
            .sortedWith(compareBy({ it.second.isCityState }, { it.first }))
            .toList()
        if (nations.isEmpty()) return

        val displayCount = min(nations.size, 3)
        var offsetX = (displayCount - 1) * 4f
        var offsetY = (displayCount - 1) * 2f
        for (nation in nations.take(3).asReversed()) {
            val newNationIcon =
                    ImageGetter.getNationPortrait(nation.second, 20f)
            tileGroup.layerMisc.addActor(newNationIcon)
            newNationIcon.run {
                setSize(20f, 20f)
                center(tileGroup)
                moveBy(offsetX, offsetY)
                color = Color.WHITE.cpy().apply { a = 0.6f }
            }
            startingLocationIcons.add(newNationIcon)
            offsetX -= 8f
            offsetY -= 4f
        }

        // Add a Label with the total count for this tile
        if (nations.size > 3) {
            // Tons of locations for this tile - display number in red, behind the top three
            startingLocationIcons.add(nations.size.tr().toLabel(ImageGetter.CHARCOAL.cpy().apply { a = 0.7f }, 14).apply {
                tileGroup.layerMisc.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(14.4f, -9f)
            })
            startingLocationIcons.add(nations.size.tr().toLabel(Color.FIREBRICK, 14).apply {
                tileGroup.layerMisc.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(14f, -8.4f)
            })
        }
    }


    fun removeWorkedIcon() {
        workedIcon?.remove()
        workedIcon = null
        determineVisibility()
    }

    fun addWorkedIcon(icon: Actor) {
        workedIcon = icon
        addActor(workedIcon)
        determineVisibility()
    }

    fun addHexOutline(color: Color) {
        hexOutlineIcon?.remove()
        hexOutlineIcon = ImageGetter.getImage("OtherIcons/HexagonOutline").setHexagonSize(1f)
        hexOutlineIcon!!.color = color
        addActor(hexOutlineIcon)
        hexOutlineIcon!!.toBack()
        determineVisibility()
    }

    fun removeHexOutline() {
        hexOutlineIcon?.remove()
        hexOutlineIcon = null
        determineVisibility()
    }

    /** Activates a colored semitransparent overlay. [color] is cloned, brightened by 0.3f and an alpha of 0.4f applied. */
    fun overlayTerrain(color: Color) = overlayTerrainInner(color.brighten(0.3f).apply { a = 0.4f })

    /** Activates a colored semitransparent overlay. [color] is cloned and [alpha] applied. No brightening unlike the overload without explicit alpha! */
    fun overlayTerrain(color: Color, alpha: Float) = overlayTerrainInner(color.cpy().apply { a = alpha })
    
    private fun overlayTerrainInner(color: Color) {
        if (terrainOverlay == null){
            terrainOverlay = ImageGetter.getImage(strings.hexagon).setHexagonSize()
            addActor(terrainOverlay)
        }
        terrainOverlay?.color = color
        determineVisibility()
    }

    fun hideTerrainOverlay() {
        terrainOverlay?.remove()
        terrainOverlay = null
        determineVisibility()
    }


    fun addArrow(targetTile: Tile, type: MapArrowType) {
        if (targetTile.position != tile.position)
            arrowsToDraw.add(MapArrow(targetTile, type, strings))
    }

    fun resetArrows() {
        arrowsToDraw.clear()
    }

    fun dimImprovement(dim: Boolean) { improvementIcon?.color?.a = if (dim) 0.5f else 1f }
    fun dimResource(dim: Boolean) { resourceIcon?.color?.a = if (dim) 0.5f else 1f }
    fun dimPopulation(dim: Boolean) { workedIcon?.color?.a = if (dim) 0.4f else 1f }


    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {

        var showResourcesAndImprovements = true

        if (tileGroup is WorldTileGroup) {
            showResourcesAndImprovements = UncivGame.Current.settings.showResourcesAndImprovements
        }
        

        updateImprovementIcon(viewingCiv, showResourcesAndImprovements)
        updateResourceIcon(viewingCiv, showResourcesAndImprovements)
        if (tileGroup !is WorldTileGroup || DebugUtils.SHOW_TILE_COORDS)
            updateStartingLocationIcon(true)
        updateArrows()
    }

    override fun determineVisibility() {
        isVisible = resourceIcon?.isVisible == true
                || improvementIcon?.isVisible == true
                || workedIcon != null
                || hexOutlineIcon != null
                || arrows.isNotEmpty()
                || startingLocationIcons.isNotEmpty()
                || terrainOverlay != null
    }

    fun reset() {
        updateImprovementIcon(null, false)
        updateResourceIcon(null, false)
        updateStartingLocationIcon(false)
        clearArrows()
    }

}
