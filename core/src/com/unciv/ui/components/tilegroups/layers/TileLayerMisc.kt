package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.tile.Tile
import com.unciv.models.helpers.MapArrowType
import com.unciv.models.helpers.MiscArrowTypes
import com.unciv.models.helpers.TintedMapArrow
import com.unciv.models.helpers.UnitMovementMemoryType
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.tilegroups.CityTileGroup
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.ui.components.tilegroups.YieldGroup
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.centerX
import com.unciv.ui.components.extensions.toLabel
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

class TileLayerMisc(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

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

    private var yieldsInitialized = false
    private var yields = YieldGroup().apply { isVisible = false }

    /** Array list of all arrows to draw from this tile on the next update. */
    private val arrowsToDraw = ArrayList<MapArrow>()
    private val arrows = HashMap<Tile, ArrayList<Actor>>()

    private var hexOutlineIcon: Actor? = null
    private var resourceName: String? = null
    private var resourceIcon: Actor? = null
    private var workedIcon: Actor? = null
    var improvementIcon: Actor? = null

    private val startingLocationIcons = mutableListOf<Actor>()

    private fun updateArrows() {
        for (actorList in arrows.values)
            for (actor in actorList)
                actor.remove()
        arrows.clear()

        val tileScale = 50f * 0.8f // See notes in updateRoadImages.

        for (arrowToAdd in arrowsToDraw) {
            val targetTile = arrowToAdd.targetTile
            var targetPos = Vector2(targetTile.position)
            if (tile().tileMap.mapParameters.worldWrap)
                targetPos = HexMath.getUnwrappedNearestTo(targetPos,
                    tile().position, tile().tileMap.maxLongitude)
            val targetRelative = HexMath.hex2WorldCoords(targetPos)
                .sub(HexMath.hex2WorldCoords(tile().position))

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

    private fun updateImprovementIcon(viewingCiv: Civilization?, showResourcesAndImprovements: Boolean) {

        improvementIcon?.remove()
        improvementIcon = null

        val shownImprovement = tile().getShownImprovement(viewingCiv)
        if (shownImprovement == null || !showResourcesAndImprovements)
            return

        val icon = ImageGetter.getImprovementPortrait(shownImprovement, dim = false)
        addActor(icon)

        icon.center(tileGroup)
        icon.x -= 22 // left
        icon.y -= 12 // bottom

        improvementIcon = icon
    }

    private fun updateResourceIcon(viewingCiv: Civilization?, isVisible: Boolean) {

        // If resource has changed (e.g. tech researched) - add new icon
        if (resourceName != tile().resource) {
            resourceName = tile().resource
            resourceIcon?.remove()
            if (resourceName == null)
                resourceIcon = null
            else {
                val newResourceIcon = ImageGetter.getResourcePortrait(resourceName!!, 20f,  tile().resourceAmount)
                newResourceIcon.center(tileGroup)
                newResourceIcon.x -= 22 // left
                newResourceIcon.y += 10 // top
                addActor(newResourceIcon)
                resourceIcon = newResourceIcon
            }
        }

        // This could happen on any turn, since resources need certain techs to reveal them
        resourceIcon?.isVisible = when {
            tileGroup.isForceVisible -> isVisible
            isVisible && viewingCiv == null -> true
            isVisible && tile().hasViewableResource(viewingCiv!!) -> true
            else -> false
        }
    }

    private fun updateStartingLocationIcon(isVisible: Boolean) {
        // These are visible in map editor only, but making that bit available here seems overkill

        startingLocationIcons.forEach { it.remove() }
        startingLocationIcons.clear()
        if (!isVisible || tileGroup.isForMapEditorIcon)
            return

        val tilemap = tile().tileMap

        if (tilemap.startingLocationsByNation.isEmpty())
            return

        // Allow display of up to three nations starting locations on the same tile, rest only as count.
        // Sorted so major get precedence and to make the display deterministic, otherwise you could get
        // different stacking order of the same nations in the same editing session
        val nations = tilemap.startingLocationsByNation.asSequence()
            .filter { tile() in it.value }
            .filter { it.key in tilemap.ruleset!!.nations } // Ignore missing nations
            .map { it.key to tilemap.ruleset!!.nations[it.key]!! }
            .sortedWith(compareBy({ it.second.isCityState() }, { it.first }))
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
            startingLocationIcons.add(nations.size.toString().toLabel(Color.BLACK.cpy().apply { a = 0.7f }, 14).apply {
                tileGroup.layerMisc.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(14.4f, -9f)
            })
            startingLocationIcons.add(nations.size.toString().toLabel(Color.FIREBRICK, 14).apply {
                tileGroup.layerMisc.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(14f, -8.4f)
            })
        }
    }

    // JN updating display of tile yields
    private fun updateYieldIcon(viewingCiv: Civilization?, showTileYields: Boolean) {

        if (viewingCiv == null)
            return

        // Hiding yield icons (in order to update)
        if (yieldsInitialized)
            yields.isVisible = false


        if (showTileYields) {
            // Setting up YieldGroup Icon
            if (tileGroup is CityTileGroup)
                yields.setStats(tile().stats.getTileStats(tileGroup.city, viewingCiv))
            else
                yields.setStats(tile().stats.getTileStats(viewingCiv))
            yields.setOrigin(Align.center)
            yields.setScale(0.7f)
            yields.toFront()
            yields.centerX(tileGroup)
            yields.y = tileGroup.height*0.25f - yields.height/2
            yields.isVisible = true
            yieldsInitialized = true

            // Adding YieldGroup to miscLayerGroup
            addActor(yields)
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


    fun addArrow(targetTile: Tile, type: MapArrowType) {
        if (targetTile.position != tile().position)
            arrowsToDraw.add(MapArrow(targetTile, type, strings()))
    }

    fun resetArrows() {
        arrowsToDraw.clear()
    }

    fun dimImprovement(dim: Boolean) { improvementIcon?.color?.a = if (dim) 0.5f else 1f }
    fun dimResource(dim: Boolean) { resourceIcon?.color?.a = if (dim) 0.5f else 1f }
    fun dimYields(dim: Boolean) { yields.color.a = if (dim) 0.5f else 1f }
    fun dimPopulation(dim: Boolean) { workedIcon?.color?.a = if (dim) 0.4f else 1f }

    fun setYieldVisible(isVisible: Boolean) {
        yields.isVisible = isVisible
        determineVisibility()
    }

    override fun doUpdate(viewingCiv: Civilization?) {

        var showResourcesAndImprovements = true
        var showTileYields = true

        if (tileGroup is WorldTileGroup) {
            showResourcesAndImprovements = UncivGame.Current.settings.showResourcesAndImprovements
            showTileYields = UncivGame.Current.settings.showTileYields
        }

        updateImprovementIcon(viewingCiv, showResourcesAndImprovements)
        updateYieldIcon(viewingCiv, showTileYields)
        updateResourceIcon(viewingCiv, showResourcesAndImprovements)
        updateStartingLocationIcon(showResourcesAndImprovements)
        updateArrows()
    }

    override fun determineVisibility() {
        isVisible = yields.isVisible
                || resourceIcon?.isVisible == true
                || improvementIcon != null
                || workedIcon != null
                || hexOutlineIcon != null
                || arrows.isNotEmpty()
                || startingLocationIcons.isNotEmpty()
    }

    fun reset() {
        updateImprovementIcon(null, false)
        updateResourceIcon(null, false)
        updateStartingLocationIcon(false)
    }

}
