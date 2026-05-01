package com.unciv.models

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionModifiers

interface TargetableUnitAction {
    val useFrequency: Float
    
    fun targetIsTileSpecific(): Boolean

    fun validTarget(tile: Tile): Boolean

    fun canUse(): Boolean

    fun invokeAction() : Boolean  
}

abstract class UniqueTargetableAction(val unit: MapUnit, val unique: Unique): TargetableUnitAction {
    var lastTile = unit.currentTile
    var lastContext = unit.cache.state

    override val useFrequency: Float = unique.modifiersMap[UniqueType.UnitActionPriority]!!.first().params[0].toFloat()

    override fun targetIsTileSpecific(): Boolean = unique.unitActionModifiersAreTileSpecific(unit.civ.gameInfo)

    override fun validTarget(tile: Tile): Boolean {
        if (tile != lastTile) {
            lastContext = GameContext(unit = unit, tile = tile, attackedTile = tile, ignoreFieldsBits = GameContext.CONSIDER_ONLY_TILES_MASK)
        }
        return unique.conditionalsApply(lastContext)
    }

    override fun canUse(): Boolean = UnitActionModifiers.canUse(unit, unique) && 
        UnitActionModifiers.canActivateSideEffects(unit, unique) &&
        unique.conditionalsApply(unit.cache.state.copy(ignoreFieldsBits = GameContext.IGNORE_TILE_BIT))

    abstract fun invokeUniqueOnce()
    
    override fun invokeAction(): Boolean {
        repeat(unique.getUniqueMultiplier(lastContext)) {
            invokeUniqueOnce()
        }
        UnitActionModifiers.activateSideEffects(unit, unique)
        return true
    }
}

class TriggerableTargetableAction(unit: MapUnit, unique: Unique): UniqueTargetableAction(unit, unique) {
    var lastTrigger: (()->Boolean)? = UniqueTriggerActivation.getTriggerFunction(unique, unit.civ, unit = unit, tile = unit.currentTile)

    override fun validTarget(tile: Tile): Boolean {
        val newTile = tile != lastTile
        if (!super.validTarget(tile)) return false
        if (newTile) lastTrigger = UniqueTriggerActivation.getTriggerFunction(unique, lastContext)
        return lastTrigger != null
    }
    
    override fun invokeUniqueOnce() {
        lastTrigger!!.invoke()
    }

    override fun invokeAction(): Boolean {
        if (lastTrigger == null) return false
        return super.invokeAction()
    }
}

class TransformTargetableAction(unit: MapUnit, unique: Unique): UniqueTargetableAction(unit, unique) {

    override fun validTarget(tile: Tile): Boolean {
        val unitToTransformTo = unit.civ.getEquivalentUnit(unique.params[0])
        return super.validTarget(tile) && !unitToTransformTo.getMatchingUniques(UniqueType.Unavailable, lastContext).any()
    }
    override fun canUse(): Boolean = !unit.isEmbarked() && super.canUse() 

    override fun invokeUniqueOnce() {
        val oldMovement = unit.currentMovement
        val unitToTransformTo = unit.civ.getEquivalentUnit(unique.params[0])
        unit.destroy()
        val newUnit =
            unit.civ.units.placeUnitNearTile(unit.currentTile.position, unitToTransformTo, unit.id, copiedFrom = unit)

        /** We were UNABLE to place the new unit, which means that the unit failed to upgrade!
         * The only known cause of this currently is "land units upgrading to water units" which fail to be placed.
         */
        if (newUnit == null) {
            val resurrectedUnit =
                unit.civ.units.placeUnitNearTile(unit.currentTile.position, unit.baseUnit, unit.id, copiedFrom = unit)!!

        } else { // Managed to upgrade
            // have to handle movement manually because we killed the old unit
            // a .destroy() unit has 0 movement
            // and a new one may have less Max Movement
            newUnit.currentMovement = oldMovement
            // adjust if newUnit has lower Max Movement
            if (newUnit.currentMovement.toInt() > newUnit.getMaxMovement())
                newUnit.currentMovement = newUnit.getMaxMovement().toFloat()
            // execute any side effects, Stat and Movement adjustments
        }
    }
}

fun getTargetableUnitActions(unit: MapUnit): List<TargetableUnitAction> {
    val results = mutableListOf<TargetableUnitAction>()
    // TriggerableTargetableAction
    for (unique in unit.getUniques()) {
        if (!unique.hasModifier(UniqueType.UnitActionPriority)) continue
        when (unique.type) {
            UniqueType.CanTransform -> {
                if (unit.isEmbarked() || !UnitActionModifiers.canActivateSideEffects(unit, unique))
                    continue;
                results.add(TransformTargetableAction(unit, unique))
            }
            else -> { // any other unit triggerable
                // not a unit action
                if (unique.modifiers.none { it.type?.targetTypes?.contains(UniqueTarget.UnitActionModifier) == true }) continue
                // extends an existing unit action
                if (unique.hasModifier(UniqueType.UnitActionExtraLimitedTimes)) continue
                if (!unique.isTriggerable) continue
                if (!UnitActionModifiers.canUse(unit, unique)) continue
                results.add(TriggerableTargetableAction(unit, unique))
                
            }
        }
    }
    return results
}
