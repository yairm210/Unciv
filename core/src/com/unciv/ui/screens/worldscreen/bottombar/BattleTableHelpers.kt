package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.HexMath
import com.unciv.models.translations.tr
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.widgets.ShadowedLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency


object BattleTableHelpers {
    /** Duration of the red-tint transition, used once per direction */
    private const val flashRedDuration = 0.2f
    /** Duration of the attacker displacement, used once per direction */
    private const val moveActorsDuration = 0.3f
    /** Max distance of the attacker displacement, in world coords */
    private const val moveActorsDisplacement = 10f
    /** If a mod provides attack animations (e.g. swinging a sword), they're played with this duration per frame */
    private const val attackAnimationFrameDuration = 0.1f
    /** Duration a damage number label is visible */
    private const val damageLabelDuration = 1.2f
    /** Size of a damage number label - currently fixed independednt of map zoom */
    private const val damageLabelFontSize = 40
    /** Total distance a damage number label is displaced upwards during that time in world coords */
    private const val damageLabelDisplacement = 90f


    class FlashRedAction(
        start: Float, end: Float,
        private val actorsToOriginalColors: Map<Actor, Color>
    ) : FloatAction(start, end, flashRedDuration, Interpolation.sine) {
        private fun updateRedPercent(percent: Float) {
            for ((actor, color) in actorsToOriginalColors)
                actor.color = color.cpy().lerp(Color.RED, start + percent * (end - start))
        }

        override fun update(percent: Float) = updateRedPercent(percent)
    }


    class MoveActorsAction(
        private val actorsToMove: List<Actor>,
        private val movementVector: Vector2
    ) : RelativeTemporalAction() {
        init {
            duration = moveActorsDuration
            interpolation = Interpolation.sine
        }
        override fun updateRelative(percentDelta: Float) {
            for (actor in actorsToMove) {
                actor.moveBy(movementVector.x * percentDelta, movementVector.y * percentDelta)
            }
        }
    }


    class AttackAnimationAction(
        private val attacker: ICombatant,
        defenderActors: List<Actor>,
        private val currentTileSetStrings: TileSetStrings
    ): SequenceAction() {
        init {
            if (defenderActors.any()) {
                val attackAnimationLocation = getAttackAnimationLocation()
                if (attackAnimationLocation != null) {
                    var i = 1
                    while (ImageGetter.imageExists(attackAnimationLocation + i)) {
                        val image = ImageGetter.getImage(attackAnimationLocation + i)

                        val defenderParentGroup = defenderActors.first().parent
                        addAction(Actions.run {
                            defenderParentGroup.addActor(image)
                        })
                        addAction(Actions.delay(attackAnimationFrameDuration))
                        addAction(Actions.removeActor(image))
                        i++
                    }
                }
            }
        }

        private fun getAttackAnimationLocation(): String? {
            fun TileSetStrings.getLocation(name: String) = getString(unitsLocation, name, "-attack-")

            if (attacker is MapUnitCombatant) {
                val unitSpecificAttackAnimationLocation = currentTileSetStrings.getLocation(attacker.getName())
                if (ImageGetter.imageExists(unitSpecificAttackAnimationLocation + "1"))
                    return unitSpecificAttackAnimationLocation
            }

            val unitTypeAttackAnimationLocation = currentTileSetStrings.getLocation(attacker.getUnitType().name)
            if (ImageGetter.imageExists(unitTypeAttackAnimationLocation + "1"))
                return unitTypeAttackAnimationLocation
            return null
        }
    }


    /** The animation for the Damage labels */
    private class DamageLabelAnimation(actor: WidgetGroup) : TemporalAction(damageLabelDuration) {
        val startX = actor.x
        val startY = actor.y

        /* A tested version with smooth scale-out in addition to the alpha fade
        val width = actor.width
        val height = actor.height
        init {
            actor.isTransform = true
        }
        override fun update(percent: Float) {
            actor.color.a = Interpolation.fade.apply(1f - percent)
            val scale = Interpolation.smooth.apply(1f - percent)
            actor.setScale(scale)
            val x = startX + (1f - scale) * width / 2
            val y = startY + (1f - scale) * height / 2 +
                    Interpolation.smooth.apply(percent) * damageLabelDisplacement
            actor.setPosition(x, y)
        }
        */

        override fun update(percent: Float) {
            actor.color.a = Interpolation.fade.apply(1f - percent)
            actor.setPosition(startX, startY + percent * damageLabelDisplacement)
        }
        override fun end() {
            actor.remove()
        }
    }

    fun WorldScreen.battleAnimationDeferred(
        attacker: ICombatant, damageToAttacker: Int,
        defender: ICombatant, damageToDefender: Int
    ){
        // This ensures that we schedule the animation to happen AFTER the worldscreen.update(), 
        //    where the spriteGroup of the attacker is created on the tile it moves to 
        Concurrency.runOnGLThread { battleAnimation(attacker, damageToAttacker, defender, damageToDefender) }
    }

    private fun WorldScreen.battleAnimation(
        attacker: ICombatant, damageToAttacker: Int,
        defender: ICombatant, damageToDefender: Int
    ) {
        fun getMapActorsForCombatant(combatant: ICombatant): Sequence<Actor> =
            sequence {
                val tileGroup = mapHolder.tileGroups[combatant.getTile()]!!
                if (combatant.isCity()) {
                    val icon = tileGroup.layerImprovement.improvementIcon
                    if (icon != null) yield (icon)
                } else if (!combatant.isAirUnit()) {
                    val slot = tileGroup.layerUnitArt.getSpriteSlot((combatant as MapUnitCombatant).unit)
                    if (slot != null) yieldAll(slot.spriteGroup.children)
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
            .scl(moveActorsDisplacement)

        val attackerGroup = mapHolder.tileGroups[attacker.getTile()]!!
        val defenderGroup = mapHolder.tileGroups[defender.getTile()]!!
        val hideDefenderDamage = defender.isDefeated() &&
                attacker.getTile().position == defender.getTile().position

        stage.addAction(
            Actions.sequence(
                MoveActorsAction(actorsToMove, attackVectorWorldCoords),
                Actions.run {
                    createDamageLabel(damageToAttacker, attackerGroup)
                    if (!hideDefenderDamage)
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

        val container = ShadowedLabel((-damage).tr(), damageLabelFontSize, Color.RED)
        val targetRight = target.run { localToStageCoordinates(Vector2(width, height * 0.5f)) }
        container.setPosition(targetRight.x, targetRight.y, Align.center)
        target.stage.addActor(container)

        container.addAction(DamageLabelAnimation(container))
    }

    fun getHealthBar(maxHealth: Int, currentHealth: Int, maxRemainingHealth: Int, minRemainingHealth: Int, forDefender: Boolean = false): Table {
        val healthBar = Table()
        val totalWidth = 120f
        fun addHealthToBar(image: Image, amount: Int) {
            val width = totalWidth * amount / maxHealth
            healthBar.add(image).size(width.coerceIn(0f, totalWidth),4f)
        }

        fun animateHealth(health: Image, fat: Float, move: Float) {
            health.addAction(Actions.sequence(
                Actions.sizeBy(fat, 0f),
                Actions.sizeBy(-fat, 0f, 0.5f)
            ))
            health.addAction(Actions.sequence(
                Actions.moveBy(-move, 0f),
                Actions.moveBy(move, 0f, 0.5f)
            ))
        }
        
        val damagedHealth = ImageGetter.getDot(Color.FIREBRICK)
        val remainingHealthDot = ImageGetter.getDot(Color.GREEN)
        val maybeDamagedHealth = ImageGetter.getDot(Color.ORANGE)
        val missingHealth = ImageGetter.getDot(ImageGetter.CHARCOAL)
        if (UncivGame.Current.settings.continuousRendering) {
            maybeDamagedHealth.addAction(Actions.forever(Actions.sequence(
                Actions.color(Color.FIREBRICK, 0.7f),
                Actions.color(Color.ORANGE, 0.7f)
            )))
        }
        
        val healthDecreaseWidth = (currentHealth - minRemainingHealth) * totalWidth / 100 // Used for animation only
        if (forDefender) {
            addHealthToBar(missingHealth, maxHealth - currentHealth)
            addHealthToBar(damagedHealth, currentHealth - maxRemainingHealth)
            addHealthToBar(maybeDamagedHealth, maxRemainingHealth - minRemainingHealth)
            addHealthToBar(remainingHealthDot, minRemainingHealth)

            remainingHealthDot.toFront()
            animateHealth(remainingHealthDot, healthDecreaseWidth, healthDecreaseWidth)
        }
        else {
            addHealthToBar(remainingHealthDot, minRemainingHealth)
            addHealthToBar(maybeDamagedHealth, maxRemainingHealth - minRemainingHealth)
            addHealthToBar(damagedHealth, currentHealth - maxRemainingHealth)
            addHealthToBar(missingHealth, maxHealth - currentHealth)

            remainingHealthDot.toFront()
            animateHealth(remainingHealthDot, healthDecreaseWidth, 0f)
        }
        healthBar.pack()
        return healthBar
    }
}
