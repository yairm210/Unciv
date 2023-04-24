package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.map.HexMath
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen

object BattleTableHelpers {

    class FlashRedAction(start:Float, end:Float, private val actorsToOriginalColors:Map<Actor, Color>) : FloatAction(start, end, 0.2f, Interpolation.sine){
        private fun updateRedPercent(percent: Float) {
            for ((actor, color) in actorsToOriginalColors)
                actor.color = color.cpy().lerp(Color.RED, start+percent*(end-start))
        }

        override fun update(percent: Float) = updateRedPercent(percent)
    }


    class MoveActorsAction(private val actorsToMove:List<Actor>, private val movementVector: Vector2) : RelativeTemporalAction(){
        init {
            duration = 0.3f
            interpolation = Interpolation.sine
        }
        override fun updateRelative(percentDelta: Float) {
            for (actor in actorsToMove){
                actor.moveBy(movementVector.x * percentDelta, movementVector.y * percentDelta)
            }
        }
    }

    fun WorldScreen.battleAnimation(
        attacker: ICombatant, damageToAttacker: Int,
        defender: ICombatant, damageToDefender: Int
    ) {
        fun getMapActorsForCombatant(combatant: ICombatant):Sequence<Actor> =
                sequence {
                    val tileGroup = mapHolder.tileGroups[combatant.getTile()]!!
                    if (combatant.isCity()) {
                        val icon = tileGroup.layerMisc.improvementIcon
                        if (icon != null) yield(icon)
                    }
                    else {
                        val slot = if (combatant.isCivilian()) 0 else 1
                        yieldAll((tileGroup.layerUnitArt.getChild(slot) as Group).children)
                    }
                }

        val actorsToFlashRed =
                sequence {
                    if (damageToDefender != 0) yieldAll(getMapActorsForCombatant(defender))
                    if (damageToAttacker != 0) yieldAll(getMapActorsForCombatant(attacker))
                }.mapTo(arrayListOf()) { it to it.color.cpy() }.toMap()

        val actorsToMove = getMapActorsForCombatant(attacker).toList()

        val attackVectorHexCoords = defender.getTile().position.cpy().sub(attacker.getTile().position)
        val attackVectorWorldCoords = HexMath.hex2WorldCoords(attackVectorHexCoords)
            .nor()  // normalize vector to length of "1"
            .scl(10f) // we want 10 pixel movement

        stage.addAction(
            Actions.sequence(
                MoveActorsAction(actorsToMove, attackVectorWorldCoords),
                Actions.parallel( // While the unit is moving back to its normal position, we flash the damages on both units
                    MoveActorsAction(actorsToMove, attackVectorWorldCoords.cpy().scl(-1f)),
                    Actions.sequence(
                        FlashRedAction(0f,1f, actorsToFlashRed),
                        FlashRedAction(1f,0f, actorsToFlashRed)
                    )
                )
        ))


    }

    fun getHealthBar(maxHealth: Int, currentHealth: Int, maxRemainingHealth: Int, minRemainingHealth: Int): Table {
        val healthBar = Table()
        val totalWidth = 100f
        fun addHealthToBar(image: Image, amount:Int) {
            val width = totalWidth * amount / maxHealth
            healthBar.add(image).size(width.coerceIn(0f, totalWidth),3f)
        }

        val damagedHealth = ImageGetter.getDot(Color.FIREBRICK)
        if (UncivGame.Current.settings.continuousRendering) {
            damagedHealth.addAction(Actions.repeat(
                RepeatAction.FOREVER, Actions.sequence(
                Actions.color(Color.BLACK, 0.7f),
                Actions.color(Color.FIREBRICK, 0.7f)
            ))) }

        val maybeDamagedHealth = ImageGetter.getDot(Color.ORANGE)

        val remainingHealthDot = ImageGetter.getWhiteDot()
        remainingHealthDot.color = Color.GREEN

        addHealthToBar(ImageGetter.getDot(Color.BLACK), maxHealth - currentHealth)
        addHealthToBar(damagedHealth, currentHealth - maxRemainingHealth)
        addHealthToBar(maybeDamagedHealth, maxRemainingHealth - minRemainingHealth)
        addHealthToBar(remainingHealthDot, minRemainingHealth)

        healthBar.pack()
        return healthBar
    }
}
