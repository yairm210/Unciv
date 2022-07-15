package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.UnitGroup
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerX
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import kotlin.math.min

/** Helper class for TileGroup, which was getting too full */
class TileGroupIcons(val tileGroup: TileGroup) {

    var improvementIcon: Actor? = null
    var populationIcon: Image? = null //reuse for acquire icon
    val startingLocationIcons = mutableListOf<Actor>()

    var civilianUnitIcon: UnitGroup? = null
    var militaryUnitIcon: UnitGroup? = null

    fun update(showResourcesAndImprovements: Boolean, showTileYields: Boolean, tileIsViewable: Boolean, showMilitaryUnit: Boolean, viewingCiv: CivilizationInfo?) {
        updateResourceIcon(showResourcesAndImprovements)
        updateImprovementIcon(showResourcesAndImprovements, viewingCiv)
        updateStartingLocationIcon(showResourcesAndImprovements)

        if (viewingCiv != null) updateYieldIcon(showTileYields, viewingCiv)

        civilianUnitIcon = newUnitIcon(tileGroup.tileInfo.civilianUnit, civilianUnitIcon,
                tileIsViewable, -20f, viewingCiv)
        militaryUnitIcon = newUnitIcon(tileGroup.tileInfo.militaryUnit, militaryUnitIcon,
                tileIsViewable && showMilitaryUnit, 20f, viewingCiv)
    }

    fun addPopulationIcon(icon: Image = ImageGetter.getStatIcon("Population")
            .apply { color = Color.GREEN.darken(0.5f) }) {
        populationIcon?.remove()
        populationIcon = icon
        populationIcon!!.run {
            setSize(20f, 20f)
            center(tileGroup)
            x += 20 // right
        }
        tileGroup.miscLayerGroup.addActor(populationIcon)
    }

    fun removePopulationIcon() {
        populationIcon?.remove()
        populationIcon = null
    }


    private fun newUnitIcon(unit: MapUnit?, oldUnitGroup: UnitGroup?, isViewable: Boolean, yFromCenter: Float, viewingCiv: CivilizationInfo?): UnitGroup? {
        var newImage: UnitGroup? = null
        // The unit can change within one update - for instance, when attacking, the attacker replaces the defender!
        oldUnitGroup?.unitBaseImage?.remove()
        oldUnitGroup?.remove()

        if (unit != null && isViewable) { // Tile is visible
            newImage = UnitGroup(unit, 25f)

            if (UncivGame.Current.settings.continuousRendering && oldUnitGroup?.blackSpinningCircle != null) {
                newImage.blackSpinningCircle = ImageGetter.getCircle()
                        .apply { rotation = oldUnitGroup.blackSpinningCircle!!.rotation }
            }
            tileGroup.unitLayerGroup.addActor(newImage)
            newImage.center(tileGroup)
            newImage.y += yFromCenter

            // We "steal" the unit image so that all backgrounds are rendered next to each other
            // to save texture swapping and improve framerate
            tileGroup.unitImageLayerGroup.addActor(newImage.unitBaseImage)
            newImage.unitBaseImage.center(tileGroup)
            newImage.unitBaseImage.y += yFromCenter

            // Display number of carried air units
            if (unit.getTile().airUnits.any { unit.isTransportTypeOf(it) } && !unit.getTile().isCityCenter()) {
                val holder = Table()
                val secondaryColor = unit.civInfo.nation.getInnerColor()
                val airUnitTable = Table().apply { defaults().pad(3f) }
                airUnitTable.background = ImageGetter.getBackground(unit.civInfo.nation.getOuterColor())
                val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
                aircraftImage.color = secondaryColor
                airUnitTable.add(aircraftImage).size(10f)
                airUnitTable.add(unit.getTile().airUnits.size.toString().toLabel(secondaryColor, 10))
                val surroundedWithCircle = airUnitTable.surroundWithCircle(20f,false, unit.civInfo.nation.getOuterColor())
                surroundedWithCircle.circle.width *= 1.5f
                surroundedWithCircle.circle.centerX(surroundedWithCircle)
                holder.add(surroundedWithCircle).row()
                holder.setOrigin(Align.center)
                holder.center(tileGroup)
                newImage.addActor(holder)
            }

            newImage.unitBaseImage.color.a = UncivGame.Current.settings.unitIconOpacity //0f (invisible) to 1f (fully opaque)
            newImage.actionGroup?.color?.a = UncivGame.Current.settings.unitIconOpacity //0f (invisible) to 1f (fully opaque)

            // Instead of fading out the entire unit with its background, we just fade out its central icon,
            // that way it remains much more visible on the map
            if (!unit.isIdle() && unit.civInfo == viewingCiv) {
                newImage.unitBaseImage.color.a *= 0.5f
                newImage.actionGroup?.color?.a = 0.5f * UncivGame.Current.settings.unitIconOpacity
            }

        }

        return newImage
    }


    private fun updateImprovementIcon(showResourcesAndImprovements: Boolean, viewingCiv: CivilizationInfo?) {
        improvementIcon?.remove()
        improvementIcon = null
        val shownImprovement = tileGroup.tileInfo.getShownImprovement(viewingCiv)
        if (shownImprovement == null || !showResourcesAndImprovements) return

        val newImprovementImage = ImageGetter.getImprovementIcon(shownImprovement)
        tileGroup.miscLayerGroup.addActor(newImprovementImage)
        newImprovementImage.run {
            setSize(20f, 20f)
            center(tileGroup)
            this.x -= 22 // left
            this.y -= 10 // bottom
            color = Color.WHITE.cpy().apply { a = 0.7f }
        }
        improvementIcon = newImprovementImage
    }

    // JN updating display of tile yields
    private fun updateYieldIcon(showTileYields: Boolean, viewingCiv: CivilizationInfo) {

        // Hiding yield icons (in order to update)
        if (tileGroup.tileYieldGroupInitialized)
            tileGroup.tileYieldGroup.isVisible = false


        if (showTileYields) {
            // Setting up YieldGroup Icon
            tileGroup.tileYieldGroup.setStats(tileGroup.tileInfo.getTileStats(viewingCiv))
            tileGroup.tileYieldGroup.setOrigin(Align.center)
            tileGroup.tileYieldGroup.setScale(0.7f)
            tileGroup.tileYieldGroup.toFront()
            tileGroup.tileYieldGroup.centerX(tileGroup)
            tileGroup.tileYieldGroup.y = tileGroup.height * 0.25f - tileGroup.tileYieldGroup.height / 2
            tileGroup.tileYieldGroup.isVisible = true
            tileGroup.tileYieldGroupInitialized = true

            // Adding YieldGroup to miscLayerGroup
            tileGroup.miscLayerGroup.addActor(tileGroup.tileYieldGroup)
        }
    }


    private fun updateResourceIcon(showResourcesAndImprovements: Boolean) {
        if (tileGroup.resource != tileGroup.tileInfo.resource) {
            tileGroup.resource = tileGroup.tileInfo.resource
            tileGroup.resourceImage?.remove()
            if (tileGroup.resource == null) tileGroup.resourceImage = null
            else {
                val newResourceIcon = ImageGetter.getResourceImage(tileGroup.tileInfo.resource!!, 20f)
                newResourceIcon.center(tileGroup)
                newResourceIcon.x = newResourceIcon.x - 22 // left
                newResourceIcon.y = newResourceIcon.y + 10 // top
                tileGroup.miscLayerGroup.addActor(newResourceIcon)
                tileGroup.resourceImage = newResourceIcon
            }
        }

        if (tileGroup.resourceImage != null) { // This could happen on any turn, since resources need certain techs to reveal them
            val shouldDisplayResource =
                    if (tileGroup.showEntireMap) showResourcesAndImprovements
                    else showResourcesAndImprovements
                            && tileGroup.tileInfo.hasViewableResource(UncivGame.Current.worldScreen!!.viewingCiv)
            tileGroup.resourceImage!!.isVisible = shouldDisplayResource
        }
    }


    private fun updateStartingLocationIcon(showResourcesAndImprovements: Boolean) {
        // these are visible in map editor only, but making that bit available here seems overkill

        startingLocationIcons.forEach { it.remove() }
        startingLocationIcons.clear()
        if (!showResourcesAndImprovements) return
        if (tileGroup.forMapEditorIcon) return  // the editor options for terrain do not bother to fully initialize, so tileInfo.tileMap would be an uninitialized lateinit
        val tileInfo = tileGroup.tileInfo
        if (tileInfo.tileMap.startingLocationsByNation.isEmpty()) return

        // Allow display of up to three nations starting locations on the same tile, rest only as count.
        // Sorted so major get precedence and to make the display deterministic, otherwise you could get
        // different stacking order of the same nations in the same editing session
        val nations = tileInfo.tileMap.startingLocationsByNation.asSequence()
            .filter { tileInfo in it.value }
            .filter { it.key in tileInfo.tileMap.ruleset!!.nations } // Ignore missing nations
            .map { it.key to tileInfo.tileMap.ruleset!!.nations[it.key]!! }
            .sortedWith(compareBy({ it.second.isCityState() }, { it.first }))
            .toList()
        if (nations.isEmpty()) return

        val displayCount = min(nations.size, 3)
        var offsetX = (displayCount - 1) * 4f
        var offsetY = (displayCount - 1) * 2f
        for (nation in nations.take(3).asReversed()) {
            val newNationIcon =
                ImageGetter.getNationIndicator(nation.second, 20f)
            tileGroup.miscLayerGroup.addActor(newNationIcon)
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
                tileGroup.miscLayerGroup.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(14.4f, -9f)
            })
            startingLocationIcons.add(nations.size.toString().toLabel(Color.FIREBRICK, 14).apply {
                tileGroup.miscLayerGroup.addActor(this)
                setOrigin(Align.center)
                center(tileGroup)
                moveBy(14f, -8.4f)
            })
        }
    }
}
