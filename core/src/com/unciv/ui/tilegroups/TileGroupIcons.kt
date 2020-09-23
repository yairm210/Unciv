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

    fun update(showResourcesAndImprovements: Boolean, tileIsViewable: Boolean, showMilitaryUnit: Boolean, viewingCiv:CivilizationInfo?) {
        updateResourceIcon(showResourcesAndImprovements)
        updateImprovementIcon(showResourcesAndImprovements)

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

    private fun aircraftTable(): IconCircleGroup {
        val aircraftTable = Table()
        val militaryUnit = tileGroup.tileInfo.militaryUnit
        val ownedNation = tileGroup.tileInfo.owningCity!!.civInfo.nation
        if (tileGroup.tileInfo.isCityCenter()) {
            aircraftTable.add(ImageGetter.getImage("OtherIcons/Aircraft")
                    .apply { color = ownedNation.getInnerColor() }).size(12f).row()
            aircraftTable.add("${tileGroup.tileInfo.airUnits.size}".toLabel(ownedNation.getInnerColor(), 10))
        }
        else if (militaryUnit!=null) {
            val unitCapacity = 2 + militaryUnit.getUniques().count { it.text == "Can carry 1 extra air unit" }
            aircraftTable.add("${tileGroup.tileInfo.airUnits.size}/$unitCapacity".toLabel(ownedNation.getInnerColor(),10))
        }
        if (tileGroup.tileInfo.airUnits.none { it.canAttack() })
            aircraftTable.color.a = 0.5f
        return aircraftTable.surroundWithCircle(25f,false).apply {
            circle.color=ownedNation.getOuterColor()
            if (militaryUnit!=null) x += 30f
        }
    }

    fun newUnitIcon(unit: MapUnit?, oldUnitGroup: UnitGroup?, isViewable: Boolean, yFromCenter: Float, viewingCiv: CivilizationInfo?): UnitGroup? {
        var newImage: UnitGroup? = null
        // The unit can change within one update - for instance, when attacking, the attacker replaces the defender!
        oldUnitGroup?.remove()

        if (tileGroup.tileInfo.militaryUnit==null && isViewable && tileGroup.tileInfo.isCityCenter()) {
            if (tileGroup.tileInfo.airUnits.isEmpty())
                tileGroup.unitLayerGroup.clear()
            else tileGroup.unitLayerGroup.addActor(aircraftTable().apply { setPosition(15f,35f) })
        }

        if (unit != null && isViewable) { // Tile is visible
            newImage = UnitGroup(unit, 25f)
            if (UncivGame.Current.settings.continuousRendering && oldUnitGroup?.blackSpinningCircle != null) {
                newImage.blackSpinningCircle = ImageGetter.getCircle()
                        .apply { rotation = oldUnitGroup.blackSpinningCircle!!.rotation }
            }
            tileGroup.unitLayerGroup.clear()
            tileGroup.unitLayerGroup.addActor(newImage)
            newImage.center(tileGroup)
            newImage.y += yFromCenter
            if (unit.type.isMilitary() && tileGroup.tileInfo.airUnits.isNotEmpty()){
                newImage.x -= 15
                newImage.addActor(aircraftTable())
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