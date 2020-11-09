package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.ui.utils.*

/** Helper class for TileGroup, which was getting too full */
class TileGroupIcons(val tileGroup: TileGroup){

    var improvementIcon: Actor? = null
    var populationIcon: Image? = null //reuse for acquire icon

    var civilianUnitIcon: UnitGroup? = null
    var militaryUnitIcon: UnitGroup? = null

    fun update(showResourcesAndImprovements: Boolean, showTileYields: Boolean, tileIsViewable: Boolean, showMilitaryUnit: Boolean, viewingCiv:CivilizationInfo?) {
        updateResourceIcon(showResourcesAndImprovements)
        updateImprovementIcon(showResourcesAndImprovements)

        // JN
        updateYieldIcon(showTileYields)

        civilianUnitIcon = newUnitIcon(tileGroup.tileInfo.civilianUnit, civilianUnitIcon,
                tileIsViewable, -20f, viewingCiv)
        militaryUnitIcon = newUnitIcon(tileGroup.tileInfo.militaryUnit, militaryUnitIcon,
                tileIsViewable && showMilitaryUnit, 20f, viewingCiv)
    }

    fun addPopulationIcon(icon: Image = ImageGetter.getStatIcon("Population")
            .apply { color = Color.GREEN.cpy().lerp(Color.BLACK, 0.5f) }) {
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


    fun newUnitIcon(unit: MapUnit?, oldUnitGroup: UnitGroup?, isViewable: Boolean, yFromCenter: Float, viewingCiv: CivilizationInfo?): UnitGroup? {
        var newImage: UnitGroup? = null
        // The unit can change within one update - for instance, when attacking, the attacker replaces the defender!
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

            // Display number of carried air units
            if (unit.getTile().airUnits.any { unit.isTransportTypeOf(it) } && !unit.getTile().isCityCenter()) {
                val holder = Table()
                val secondarycolor = unit.civInfo.nation.getInnerColor()
                val airUnitTable = Table().apply { defaults().pad(5f) }
                airUnitTable.background = ImageGetter.getRoundedEdgeTableBackground(unit.civInfo.nation.getOuterColor())
                val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
                aircraftImage.color = secondarycolor
                airUnitTable.add(aircraftImage).size(15f)
                airUnitTable.add(unit.getTile().airUnits.size.toString().toLabel(secondarycolor, 14))
                holder.add(airUnitTable).row()
                holder.setOrigin(Align.center)
                holder.center(tileGroup)
                newImage.addActor(holder)
            }

            // Instead of fading out the entire unit with its background, we just fade out its central icon,
            // that way it remains much more visible on the map
            if (!unit.isIdle() && unit.civInfo == viewingCiv)
                newImage.unitBaseImage.color.a = 0.5f
        }
        return newImage
    }


    fun updateImprovementIcon(showResourcesAndImprovements: Boolean) {
        improvementIcon?.remove()
        improvementIcon = null

        if (tileGroup.tileInfo.improvement != null && showResourcesAndImprovements) {
            val newImprovementImage = ImageGetter.getImprovementIcon(tileGroup.tileInfo.improvement!!)
            tileGroup.miscLayerGroup.addActor(newImprovementImage)
            newImprovementImage .run {
                setSize(20f, 20f)
                center(tileGroup)
                this.x -= 22 // left
                this.y -= 10 // bottom
            }
            improvementIcon = newImprovementImage
        }
        if (improvementIcon != null) {
            improvementIcon!!.color = Color.WHITE.cpy().apply { a = 0.7f }
        }
    }

    // JN updating display of tile yields
    private fun updateYieldIcon(showTileYields: Boolean) {

        // Removing yield icons (in order to update)
        tileGroup.tileYieldGroup.clear()



        if (showTileYields) {
            tileGroup.tileYieldGroup.setStats(tileGroup.tileInfo.getTileStats(CivilizationInfo()))
            tileGroup.tileYieldGroup.setOrigin(Align.center)
            tileGroup.tileYieldGroup.setScale(0.7f)
            tileGroup.tileYieldGroup.toFront()
            tileGroup.tileYieldGroup.centerX(tileGroup)
            tileGroup.tileYieldGroup.y = tileGroup.height * 0.25f - tileGroup.tileYieldGroup.height / 2
            /*if (tileGroup.tileInfo.isWorked()) {
                tileGroup.tileYieldGroup.color = Color.WHITE
            } else if (!tileInfo.isCityCenter()) {
                tileGroup.tileYieldGroup.color = Color.GRAY.cpy().apply { a = 0.5f }
            }*/
        }
    }


/*
    fun updateYieldIcon(showTileYields: Boolean){

        // Removing yield icons (in order to then update)
        tileGroup.yieldImageGroup.clear()

        if (showTileYields){
            var tileStats = tileGroup.tileInfo.getTileStats(CivilizationInfo())

            // Location of yield icons within tiles
            val yieldPositions = mapOf(
                    "Food" to listOf(22,10,tileStats.food.toInt()),
                    "Production" to listOf(9,10,tileStats.production.toInt()),
                    "Gold" to listOf(22,-3,tileStats.gold.toInt()),
                    "Science" to listOf(9,-3,tileStats.science.toInt()),
                    "Culture" to listOf(17,-13,tileStats.culture.toInt()))

            // If yield > 0 then an icon will be collected and displayed
            for (yieldType in yieldPositions) {
                val yieldTypeName = yieldType.key
                val yieldNumber = yieldType.value.get(2).toString()
                val yieldIconString = "$yieldTypeName$yieldNumber"
                val newYieldIcon = ImageGetter.getYieldImage(yieldIconString,14f)

                if (yieldNumber  != "0") {
                    newYieldIcon.center(tileGroup)
                    newYieldIcon.x = newYieldIcon.x + yieldType.value.get(0) // right
                    newYieldIcon.y = newYieldIcon.y - yieldType.value.get(1) // bottom
                    tileGroup.yieldImageGroup.addActor(newYieldIcon)
                }
            }


                    //tileGroup.miscLayerGroup.addActor(newYieldIcon)}}
                    //tileGroup.yieldImage = newYieldIcon}}
        }
    }
*/


    fun updateResourceIcon(showResourcesAndImprovements: Boolean) {
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
                    if (tileGroup.showEntireMap) tileGroup.tileInfo.resource != null
                    else showResourcesAndImprovements
                            && tileGroup.tileInfo.hasViewableResource(UncivGame.Current.worldScreen.viewingCiv)
            tileGroup.resourceImage!!.isVisible = shouldDisplayResource
        }
    }


}