package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.toHexCoord
import com.unciv.models.translations.tr
import com.unciv.ui.components.*
import com.unciv.ui.components.extensions.*
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
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
    // Lazily created to avoid allocating YieldGroup for tiles that never display yields.
    private var yields: YieldGroup? = null

    private fun getOrCreateYields(): YieldGroup {
        if (yields == null) {
            yields = YieldGroup().apply {
                isVisible = false
                setOrigin(Align.center)
                setScale(0.7f)
            }
            addOwnedActor(yields!!)
            // y is tile-local before attachment (tileY == 0) or absolute after; either way correct.
            yields!!.y = tileY + tileGroup.height * 0.25f - yields!!.height / 2
        }
        return yields!!
    }

    override fun doUpdate(viewingCiv: Civilization?) {
        val showTileYields = if (tileGroup is WorldTileGroup) UncivGame.Current.settings.showTileYields else true
        updateYieldIcon(viewingCiv, showTileYields)
    }

    // JN updating display of tile yields
    private fun updateYieldIcon(
        viewingCiv: Civilization?,
        show: Boolean,
    ) {
        val effectiveVisible = show &&
                !tileGroup.isForMapEditorIcon &&  // don't have a map to calc yields
                !(viewingCiv == null && tileGroup.isForceVisible) // main menu background

        if (!effectiveVisible) {
            yields?.isVisible = false
            return
        }

        val y = getOrCreateYields()
        y.isVisible = false
        y.run {
            // Update YieldGroup Icon
            if (tileGroup is CityTileGroup)
                setStats(tile.stats.getTileStats(tileGroup.city, viewingCiv))
            else
                setStats(tile.stats.getTileStats(viewingCiv))
            toFront()
            // Centre horizontally on the tile (absolute position)
            x = tileX + (tileGroup.width - width) / 2
            isVisible = true
        }
    }

    fun setYieldVisible(isVisible: Boolean) {
        yields?.isVisible = isVisible
        this.isVisible = isVisible // don't try rendering the layer if there's nothing in it
    }

    fun dimYields(dim: Boolean) { yields?.color?.a = if (dim) 0.5f else 1f }

    fun reset() {
        updateYieldIcon(null, false)
    }

    override fun determineVisibility() {
        isVisible = yields?.isVisible == true
    }
}


class TileLayerResource(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size){

    private var resourceName: String? = null
    private var resourceAmount: Int = -1
    private var resourceIcon: Actor? = null
    private var resourceProvidedIcon: Actor? = null

    private fun updateResourceIcon(viewingCiv: Civilization?, show: Boolean) {
        // This could change on any turn, since resources need certain techs to reveal them
        val effectiveVisible = when {
            tileGroup.isForceVisible -> show
            show && viewingCiv == null -> true
            show && viewingCiv?.canSeeResource(tile.tileResource) == true -> true
            else -> false
        }

        // If resource has changed (e.g. tech researched) - force new icon next time it's needed
        if (resourceName != tile.resource || resourceAmount != tile.resourceAmount) {
            resourceName = tile.resource
            resourceAmount = tile.resourceAmount
            resourceIcon?.let { removeOwnedActor(it) }
            resourceIcon = null
        }

        // Get a fresh Icon if and only if necessary
        if (resourceName != null && effectiveVisible && resourceIcon == null) {
            val icon = ImageGetter.getResourcePortrait(resourceName!!, 20f, resourceAmount)
            // Centre on tile, offset left and up
            icon.x = tileX + (tileGroup.width - icon.width) / 2 - 22f
            icon.y = tileY + (tileGroup.height - icon.height) / 2 + 10f
            addOwnedActor(icon)
            resourceIcon = icon
        }

        resourceIcon?.isVisible = effectiveVisible


        if (resourceIcon != null){
            val isViewable = viewingCiv == null || isViewable(viewingCiv)
            dimResource(!isViewable)

            val shouldResourceProvidedBeDisplayed =
                viewingCiv != null && tile.getOwner() == viewingCiv
                        && tile.providesResources(viewingCiv)
            if (shouldResourceProvidedBeDisplayed && resourceProvidedIcon == null){
                val group = NonTransformGroup()
                group.setSize(12f,12f)

                val blackStar = ImageGetter.getImage("OtherIcons/Star")
                blackStar.setSize(12f)
                blackStar.color = Color.BLACK
                blackStar.center(group)
                group.addActor(blackStar)

                val goldStar = ImageGetter.getImage("OtherIcons/Star")
                goldStar.setSize(10f)
                goldStar.color = Color.GOLD
                goldStar.center(group)
                group.addActor(goldStar)

                // Slightly extruding out from the resource icon
                group.setPosition(resourceIcon!!.right + 3f, resourceIcon!!.top + 3f, Align.topRight)
                addOwnedActor(group)

                resourceProvidedIcon = group
            }

            if (!shouldResourceProvidedBeDisplayed && resourceProvidedIcon != null){
                removeOwnedActor(resourceProvidedIcon!!)
                resourceProvidedIcon = null
            }
            resourceProvidedIcon?.toFront()
            resourceProvidedIcon?.isVisible = effectiveVisible
        }
    }

    fun reset() {
        updateResourceIcon(null, false)
    }

    fun dimResource(dim: Boolean) { resourceIcon?.color?.a = if (dim) 0.5f else 1f }

    override fun doUpdate(viewingCiv: Civilization?) {
        val showResourcesAndImprovements = if (tileGroup is WorldTileGroup)
            UncivGame.Current.settings.showResourcesAndImprovements else true

        updateResourceIcon(viewingCiv, showResourcesAndImprovements)
    }

    override fun determineVisibility() {
        isVisible = resourceIcon?.isVisible == true
    }
}

class TileLayerImprovement(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size){
    private var improvementPlusPillagedID: String? = null
    var improvementIcon: Actor? = null
        private set  // Getter public for BattleTable to display as City Combatant


    override fun doUpdate(viewingCiv: Civilization?) {
        val showResourcesAndImprovements = if (tileGroup is WorldTileGroup)
            UncivGame.Current.settings.showResourcesAndImprovements else true

        updateImprovementIcon(viewingCiv, showResourcesAndImprovements)
    }

    fun dimImprovement(dim: Boolean) { improvementIcon?.color?.a = if (dim) 0.5f else 1f }

    private fun updateImprovementIcon(viewingCiv: Civilization?, show: Boolean) {
        // If improvement has changed, force new icon next time it is needed
        val improvementToShow = tile.getShownImprovement(viewingCiv)
        val newImprovementPlusPillagedID = if (improvementToShow==null) null
        else if (tile.improvementIsPillaged) "$improvementToShow-Pillaged"
        else improvementToShow

        if (improvementPlusPillagedID != newImprovementPlusPillagedID) {
            improvementPlusPillagedID = newImprovementPlusPillagedID
            improvementIcon?.let { removeOwnedActor(it) }
            improvementIcon = null
        }

        // Get new icon when needed
        if (improvementPlusPillagedID != null && show && improvementIcon == null) {
            val icon = ImageGetter.getImprovementPortrait(improvementToShow!!, isPillaged = tile.improvementIsPillaged)
            // Centre on tile, offset left and down
            icon.x = tileX + (tileGroup.width - icon.width) / 2 - 22f
            icon.y = tileY + (tileGroup.height - icon.height) / 2 - 12f
            addOwnedActor(icon)
            improvementIcon = icon
        }

        improvementIcon?.isVisible = show
    }

    override fun determineVisibility() {
        isVisible = improvementIcon?.isVisible == true
    }

    fun reset() {
        updateImprovementIcon(null, false)
    }
}

class TileLayerMisc(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    // Lazily created — only allocated when an overlay color is applied to this tile.
    private var terrainOverlay: Image? = null

    /** Array list of all arrows to draw from this tile on the next update. */
    private val arrowsToDraw = ArrayList<MapArrow>()
    private val arrows = HashMap<Tile, ArrayList<Actor>>()

    private var hexOutlineIcon: Actor? = null

    private var workedIcon: Actor? = null

    /** Optional click handler attached to the workedIcon when it is added (used by CityScreen). */
    var onWorkedIconClick: (() -> Unit)? = null
    /** Optional double-click handler attached to the workedIcon when it is added (used by CityScreen). */
    var onWorkedIconDoubleClick: (() -> Unit)? = null

    private val startingLocationIcons = mutableListOf<Actor>()

    private fun clearArrows() {
        for (actorList in arrows.values)
            for (actor in actorList)
                removeOwnedActor(actor)
        arrows.clear()
    }

    private fun updateArrows() {
        clearArrows()
        val tileScale = 50f * 0.8f // See notes in updateRoadImages.

        for (arrowToAdd in arrowsToDraw) {
            val targetTile = arrowToAdd.targetTile
            var targetPos = Vector2(targetTile.position.toVector2())
            if (tile.tileMap.mapParameters.worldWrap)
                targetPos = HexMath.getUnwrappedNearestTo(targetPos.toHexCoord(),
                    tile.position, tile.tileMap.maxLongitude)
            val targetRelative = HexMath.hex2WorldCoords(targetPos.toHexCoord())
                .sub(HexMath.hex2WorldCoords(tile.position))

            val targetDistance = sqrt(targetRelative.x.pow(2) + targetRelative.y.pow(2))
            val targetAngle = atan2(targetRelative.y, targetRelative.x)

            if (targetTile !in arrows) {
                arrows[targetTile] = ArrayList()
            }

            val arrowImage = arrowToAdd.getImage()
            arrowImage.touchable = Touchable.disabled
            // Arrows originate at tile centre (25, -5 in tile-local); offset by tile origin for absolute.
            arrowImage.setPosition(tileX + 25f, tileY - 5f)

            arrowImage.setSize(tileScale * targetDistance, 60f)
            arrowImage.setOrigin(0f, 30f)

            arrowImage.rotation = targetAngle / Math.PI.toFloat() * 180

            arrows[targetTile]!!.add(arrowImage)
            addOwnedActor(arrowImage)
            // FIXME: Culled when too large and panned away.
            // https://libgdx.badlogicgames.com/ci/nightlies/docs/api/com/badlogic/gdx/scenes/scene2d/utils/Cullable.html
            // .getCullingArea returns null for both miscLayerGroup and worldMapHolder. Don't know where it's happening. Somewhat rare, and fixing it may have a hefty performance cost.
        }
    }

    private fun updateStartingLocationIcon(show: Boolean) {
        // The starting location icons are visible in map editor only, but this method is abused for the
        // "Show coordinates on tiles" debug option as well. Calling code made sure this is only called
        // with isVisible=false for reset, or for non-WorldMap TileGroups, or with the debug option set.
        // Note that starting locations should always be empty on the normal WorldMap - they're cleared after use.
        // Also remember the main menu background is an EditorMapHolder which we can't distinguish from
        // The actual editor use here.

        startingLocationIcons.forEach { removeOwnedActor(it) }
        startingLocationIcons.clear()
        if (!show || tileGroup.isForMapEditorIcon)
            return

        if (DebugUtils.SHOW_TILE_COORDS) {
            val label = this.tile.position.toVector2().toPrettyString()
            val tileW = tileGroup.width
            val tileH = tileGroup.height
            startingLocationIcons.add(label.toLabel(ImageGetter.CHARCOAL.cpy().apply { a = 0.7f }, 14).apply {
                touchable = Touchable.disabled
                setOrigin(Align.center)
                x = tileX + (tileW - width) / 2 + 15.4f
                y = tileY + (tileH - height) / 2 - 0.6f
                tileGroup.layerMisc.addOwnedActor(this)
            })
            startingLocationIcons.add(label.toLabel(Color.FIREBRICK, 14).apply {
                touchable = Touchable.disabled
                setOrigin(Align.center)
                x = tileX + (tileW - width) / 2 + 15f
                y = tileY + (tileH - height) / 2
                tileGroup.layerMisc.addOwnedActor(this)
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
            newNationIcon.run {
                touchable = Touchable.disabled
                setSize(20f, 20f)
                x = tileX + (tileGroup.width - 20f) / 2 + offsetX
                y = tileY + (tileGroup.height - 20f) / 2 + offsetY
                color = Color.WHITE.cpy().apply { a = 0.6f }
            }
            tileGroup.layerMisc.addOwnedActor(newNationIcon)
            startingLocationIcons.add(newNationIcon)
            offsetX -= 8f
            offsetY -= 4f
        }

        // Add a Label with the total count for this tile
        if (nations.size > 3) {
            // Tons of locations for this tile - display number in red, behind the top three
            startingLocationIcons.add(nations.size.tr().toLabel(ImageGetter.CHARCOAL.cpy().apply { a = 0.7f }, 14).apply {
                touchable = Touchable.disabled
                setOrigin(Align.center)
                x = tileX + (tileGroup.width - width) / 2 + 14.4f
                y = tileY + (tileGroup.height - height) / 2 - 9f
                tileGroup.layerMisc.addOwnedActor(this)
            })
            startingLocationIcons.add(nations.size.tr().toLabel(Color.FIREBRICK, 14).apply {
                touchable = Touchable.disabled
                setOrigin(Align.center)
                x = tileX + (tileGroup.width - width) / 2 + 14f
                y = tileY + (tileGroup.height - height) / 2 - 8.4f
                tileGroup.layerMisc.addOwnedActor(this)
            })
        }
    }


    fun removeWorkedIcon() {
        workedIcon?.let { removeOwnedActor(it) }
        workedIcon = null
        determineVisibility()
    }

    fun addWorkedIcon(icon: Actor) {
        workedIcon = icon
        onWorkedIconClick?.let { handler -> icon.onClick { handler() } }
        onWorkedIconDoubleClick?.let { handler -> icon.onDoubleClick(action = { handler() }) }
        addOwnedActor(workedIcon!!)
        determineVisibility()
    }

    fun addHexOutline(color: Color) {
        hexOutlineIcon?.let { removeOwnedActor(it) }
        hexOutlineIcon = ImageGetter.getImage("OtherIcons/HexagonOutline").apply {
            touchable = Touchable.disabled
            setHexagonSize(1f)
        }
        hexOutlineIcon!!.color = color
        addOwnedActor(hexOutlineIcon!!)
        hexOutlineIcon!!.toBack()
        determineVisibility()
    }

    fun removeHexOutline() {
        hexOutlineIcon?.let { removeOwnedActor(it) }
        hexOutlineIcon = null
        determineVisibility()
    }

    /** Activates a colored semitransparent overlay. [color] is cloned, brightened by 0.3f and an alpha of 0.4f applied. */
    fun overlayTerrain(color: Color) = overlayTerrainInner(color.brighten(0.3f).apply { a = 0.4f })

    /** Activates a colored semitransparent overlay. [color] is cloned and [alpha] applied. No brightening unlike the overload without explicit alpha! */
    fun overlayTerrain(color: Color, alpha: Float) = overlayTerrainInner(color.cpy().apply { a = alpha })

    private fun overlayTerrainInner(color: Color) {
        if (terrainOverlay == null) {
            terrainOverlay = ImageGetter.getImage(strings.hexagon).apply {
                touchable = Touchable.disabled
                setHexagonSize()
            }
            addOwnedActor(terrainOverlay!!)
        }
        terrainOverlay!!.color = color
        determineVisibility()
    }

    fun hideTerrainOverlay() {
        terrainOverlay?.let { removeOwnedActor(it) }
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

    fun dimPopulation(dim: Boolean) { workedIcon?.color?.a = if (dim) 0.4f else 1f }


    override fun doUpdate(viewingCiv: Civilization?) {
        if (tileGroup !is WorldTileGroup || DebugUtils.SHOW_TILE_COORDS)
            updateStartingLocationIcon(true)
        updateArrows()
    }

    override fun determineVisibility() {
        isVisible = workedIcon != null
                || hexOutlineIcon != null
                || arrows.isNotEmpty()
                || startingLocationIcons.isNotEmpty()
                || terrainOverlay != null
    }

    fun reset() {
        updateStartingLocationIcon(false)
        clearArrows()
    }

}
