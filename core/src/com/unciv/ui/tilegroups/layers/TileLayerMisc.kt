package com.unciv.ui.tilegroups.layers

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
import com.unciv.ui.tilegroups.YieldGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerX
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.toLabel
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
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null

    private var yieldsInitialized = false
    private var yields = YieldGroup()

    /** Array list of all arrows to draw from this tile on the next update. */
    private val arrowsToDraw = ArrayList<MapArrow>()
    private val arrows = HashMap<Tile, ArrayList<Actor>>()

    var improvementIcon: Actor? = null
    var populationIcon: Image? = null
    var resourceIcon: Actor? = null
    var resourceName: String? = null

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

    private fun updateResourceIcon(isVisible: Boolean) {

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
            isVisible && tile().hasViewableResource(UncivGame.Current.worldScreen!!.viewingCiv) -> true
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

    fun updatePopulationIcon() {
        val icon = populationIcon
        if (icon != null) {
            icon.setSize(25f, 25f)
            icon.setPosition(width / 2 - icon.width / 2,
                height * 0.85f - icon.height / 2)
            icon.color = when {
                tile().isCityCenter() -> Color.GOLD.cpy()
                tile().providesYield() -> Color.WHITE.cpy()
                else -> Color.GRAY.cpy()
            }
            icon.toFront()
        }
    }

    fun setNewPopulationIcon(icon: Image = ImageGetter.getStatIcon("Population")
        .apply { color = Color.GREEN.darken(0.5f) }) {
        populationIcon?.remove()
        populationIcon = icon
        populationIcon!!.run {
            setSize(20f, 20f)
            center(tileGroup)
            x += 20 // right
        }
        addActor(populationIcon)
    }

    fun removePopulationIcon() {
        populationIcon?.remove()
        populationIcon = null
    }


    fun addArrow(targetTile: Tile, type: MapArrowType) {
        if (targetTile.position != tile().position)
            arrowsToDraw.add(MapArrow(targetTile, type, strings()))
    }

    fun resetArrows() {
        arrowsToDraw.clear()
    }

    fun dimImprovement(dim: Boolean) = improvementIcon?.setColor(1f, 1f, 1f, if (dim) 0.5f else 1f)
    fun dimResource(dim: Boolean) = resourceIcon?.setColor(1f, 1f, 1f, if (dim) 0.5f else 1f)
    fun dimYields(dim: Boolean) = yields.setColor(1f, 1f, 1f, if (dim) 0.5f else 1f)
    fun dimPopulation(dim: Boolean) = populationIcon?.setColor(1f, 1f, 1f, if (dim) 0.5f else 1f)

    fun setYieldVisible(isVisible: Boolean) {
        yields.isVisible = isVisible
    }

    fun update(viewingCiv: Civilization?, showResourcesAndImprovements: Boolean, showTileYields: Boolean) {
        updateImprovementIcon(viewingCiv, showResourcesAndImprovements)
        updateYieldIcon(viewingCiv, showTileYields)
        updateResourceIcon(showResourcesAndImprovements)
        updateStartingLocationIcon(showResourcesAndImprovements)
        updateArrows()
    }

    fun reset() {
        updateImprovementIcon(null, false)
        updateResourceIcon(false)
        updateStartingLocationIcon(false)
    }

}
