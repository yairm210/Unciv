package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.HexMath
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.tilegroups.TileSetStrings
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


    class AttackAnimationAction(
        val attacker: ICombatant,
        val defenderActors: List<Actor>,
        val currentTileSetStrings: TileSetStrings
    ): SequenceAction(){
        init {
            if (defenderActors.any()) {
                val attackAnimationLocation = getAttackAnimationLocation()
                if (attackAnimationLocation != null){
                    var i = 1
                    while (ImageGetter.imageExists(attackAnimationLocation+i)){
                        val image = ImageGetter.getImage(attackAnimationLocation+i)
                        addAction(Actions.run {
                            defenderActors.first().parent.addActor(image)
                        })
                        addAction(Actions.delay(0.1f))
                        addAction(Actions.removeActor(image))
                        i++
                    }
                }
            }
        }

        private fun getAttackAnimationLocation(): String?{
            if (attacker is MapUnitCombatant) {
                val unitSpecificAttackAnimationLocation =
                        currentTileSetStrings.getString(
                            currentTileSetStrings.unitsLocation,
                            attacker.getUnitType().name,
                            "-attack-"
                        )
                if (ImageGetter.imageExists(unitSpecificAttackAnimationLocation+"1")) return unitSpecificAttackAnimationLocation
            }

            val unitTypeAttackAnimationLocation =
                    currentTileSetStrings.getString(currentTileSetStrings.unitsLocation, attacker.getUnitType().name, "-attack-")

            if (ImageGetter.imageExists(unitTypeAttackAnimationLocation+"1")) return unitTypeAttackAnimationLocation
            return null
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
                        if (icon != null) yield (icon)
                    } else if (!combatant.isAirUnit()) {
                        val slot = if (combatant.isCivilian()) 0 else 1
                        yieldAll((tileGroup.layerUnitArt.getChild(slot) as Group).children)
                    }
                }

        val actorsToFlashRed =
                sequence {
                    if (damageToDefender != 0) yieldAll(getMapActorsForCombatant(defender))
                    if (damageToAttacker != 0) yieldAll(getMapActorsForCombatant(attacker))
                }.associateWith { it.color.cpy() }

        val actorsToMove = getMapActorsForCombatant(attacker).toList()

        val attackVectorHexCoords = defender.getTile().position.cpy().sub(attacker.getTile().position)
        val attackVectorWorldCoords = HexMath.hex2WorldCoords(attackVectorHexCoords)
            .nor()  // normalize vector to length of "1"
            .scl(10f) // we want 10 pixel movement

        val attackerGroup = mapHolder.tileGroups[attacker.getTile()]!!
        val defenderGroup = mapHolder.tileGroups[defender.getTile()]!!

        stage.addAction(
            Actions.sequence(
                MoveActorsAction(actorsToMove, attackVectorWorldCoords),
                Actions.run {
                    createDamageLabel(damageToAttacker, attackerGroup)
                    createDamageLabel(damageToDefender, defenderGroup)
                },
                Actions.parallel( // While the unit is moving back to its normal position, we flash the damages on both units
                    MoveActorsAction(actorsToMove, attackVectorWorldCoords.cpy().scl(-1f)),
                    AttackAnimationAction(attacker,
                        if (damageToDefender != 0) getMapActorsForCombatant(defender).toList() else listOf(),
                        mapHolder.currentTileSetStrings
                    ),
                    AttackAnimationAction(
                        defender,
                        if (damageToAttacker != 0) getMapActorsForCombatant(attacker).toList() else listOf(),
                        mapHolder.currentTileSetStrings
                    ),
                    Actions.sequence(
                        FlashRedAction(0f,1f, actorsToFlashRed),
                        FlashRedAction(1f,0f, actorsToFlashRed)
                    )
                )
        ))
    }

    private fun createDamageLabel(damage: Int, target: Actor) {
        if (damage == 0) return
        val animationDuration = 1f

        val label = (-damage).toString().toLabel(Color.RED, 40, Align.center, true)
        label.touchable = Touchable.disabled
        val container = Container(label)
        container.touchable = Touchable.disabled
        container.pack()
        val targetCenter = target.run { localToStageCoordinates(Vector2(width * 0.5f, height * 0.5f)) }
        container.setPosition(targetCenter.x, targetCenter.y, Align.bottom)

        target.stage.addActor(container)
        container.addAction(Actions.sequence(
            Actions.parallel(
                Actions.alpha(0.1f, animationDuration, Interpolation.fade),
                Actions.scaleTo(0.05f, 0.05f, animationDuration),
                Actions.moveBy(label.width * 0.95f * 0.5f, 90f, animationDuration)
            ),
            Actions.removeActor()
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
