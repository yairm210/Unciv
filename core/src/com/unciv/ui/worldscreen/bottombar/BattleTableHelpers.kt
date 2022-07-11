package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.battle.ICombatant
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen

object BattleTableHelpers {

    fun WorldScreen.flashWoundedCombatants(
        attacker: ICombatant, damageToAttacker: Int,
        defender: ICombatant, damageToDefender: Int
    ) {
        fun getMapActorsForCombatant(combatant: ICombatant):Sequence<Actor> =
                sequence {
                    val tilegroups = mapHolder.tileGroups[combatant.getTile()]!!
                    when {
                        combatant.isCity() -> yieldAll(tilegroups.mapNotNull { it.icons.improvementIcon })
                        combatant.isCivilian() -> {
                            for (tileGroup in tilegroups) {
                                tileGroup.icons.civilianUnitIcon?.let { yield(it) }
                                tileGroup.update(viewingCiv)
                                yieldAll(tileGroup.pixelCivilianUnitGroup.children)
                            }
                        }
                        else -> {
                            for (tileGroup in tilegroups) {
                                tileGroup.icons.militaryUnitIcon?.let { yield(it) }
                                tileGroup.update(viewingCiv)
                                yieldAll(tileGroup.pixelMilitaryUnitGroup.children)
                            }
                        }
                    }
                }

        val actorsToFlashRed =
                sequence {
                    if (damageToDefender != 0) yieldAll(getMapActorsForCombatant(defender))
                    if (damageToAttacker != 0) yieldAll(getMapActorsForCombatant(attacker))
                }.mapTo(arrayListOf()) { it to it.color.cpy() }

        fun updateRedPercent(percent: Float) {
            for ((actor, color) in actorsToFlashRed)
                actor.color = color.cpy().lerp(Color.RED, percent)
        }

        stage.addAction(
            Actions.sequence(
            object : FloatAction(0f, 1f, 0.3f, Interpolation.sine) {
                override fun update(percent: Float) = updateRedPercent(percent)
            },
            object : FloatAction(0f, 1f, 0.3f, Interpolation.sine) {
                override fun update(percent: Float) = updateRedPercent(1 - percent)
            }
        ))
    }

    fun getHealthBar(currentHealth: Int, maxHealth: Int, expectedDamage: Int): Table {
        val healthBar = Table()
        val totalWidth = 100f
        fun addHealthToBar(image: Image, amount:Int) {
            val width = totalWidth * amount / maxHealth
            healthBar.add(image).size(width.coerceIn(0f, totalWidth),3f)
        }
        addHealthToBar(ImageGetter.getDot(Color.BLACK), maxHealth - currentHealth)

        val damagedHealth = ImageGetter.getDot(Color.FIREBRICK)
        if (UncivGame.Current.settings.continuousRendering) {
            damagedHealth.addAction(Actions.repeat(
                RepeatAction.FOREVER, Actions.sequence(
                Actions.color(Color.BLACK, 0.7f),
                Actions.color(Color.FIREBRICK, 0.7f)
            ))) }
        addHealthToBar(damagedHealth,expectedDamage)

        val remainingHealth = currentHealth - expectedDamage
        val remainingHealthDot = ImageGetter.getWhiteDot()
        remainingHealthDot.color = when {
            remainingHealth / maxHealth.toFloat() > 2 / 3f -> Color.GREEN
            remainingHealth / maxHealth.toFloat() > 1 / 3f -> Color.ORANGE
            else -> Color.RED
        }
        addHealthToBar(remainingHealthDot ,remainingHealth)

        healthBar.pack()
        return healthBar
    }
}
