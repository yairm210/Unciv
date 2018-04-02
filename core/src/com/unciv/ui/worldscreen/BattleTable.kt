package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.Battle
import com.unciv.logic.map.MapUnit
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable

class BattleTable(val worldScreen: WorldScreen): Table() {

    private val battle = Battle()

    fun simulateBattle(attacker: MapUnit, defender: MapUnit){
        clear()

        val attackerLabel = Label(attacker.name, CameraStageBaseScreen.skin)
        attackerLabel.style= Label.LabelStyle(attackerLabel.style)
        attackerLabel.style.fontColor=attacker.civInfo.getCivilization().getColor()
        add(attackerLabel)

        val defenderLabel = Label(attacker.name, CameraStageBaseScreen.skin)
        defenderLabel.style= Label.LabelStyle(defenderLabel.style)
        defenderLabel.style.fontColor=defender.civInfo.getCivilization().getColor()
        add(defenderLabel)

        row()

        var damageToDefender = battle.calculateDamage(attacker,defender)
        var damageToAttacker = battle.calculateDamage(defender,attacker)


        when {
            damageToAttacker>attacker.health && damageToDefender>defender.health -> // when damage exceeds health, we don't want to show negative health numbers
                // Also if both parties are supposed to die it's not indicative of who is more likely to win
                // So we "normalize" the damages until one dies
                if(damageToDefender/defender.health.toFloat() > damageToAttacker/attacker.health.toFloat()) // defender dies quicker ie first
                {
                    // Both damages *= (defender.health/damageToDefender)
                    damageToDefender = defender.health
                    damageToAttacker *= (defender.health/damageToDefender.toFloat()).toInt()
                }
                else{ // attacker dies first
                    // Both damages *= (attacker.health/damageToAttacker)
                    damageToAttacker = attacker.health
                    damageToDefender *= (attacker.health/damageToAttacker.toFloat()).toInt()
                }

            damageToAttacker>attacker.health -> damageToAttacker=attacker.health
            damageToDefender>defender.health -> damageToDefender=defender.health
        }


        val attackLabel = Label(attacker.health.toString() + " -> "
                + (attacker.health - damageToAttacker), CameraStageBaseScreen.skin)
        add(attackLabel)

        val defendLabel = Label(defender.health.toString() + " -> "
                + (defender.health - damageToDefender),
                CameraStageBaseScreen.skin)
        add(defendLabel)

        row()
        val attackButton = TextButton("Attack",CameraStageBaseScreen.skin)

        attackButton.addClickListener {
            battle.attack(attacker,defender)
            worldScreen.update()
        }

        val attackerCanReachDefender = attacker.getDistanceToTiles().containsKey(defender.getTile())
        if(attacker.currentMovement==0f || !attackerCanReachDefender)  attackButton.disable()
        add(attackButton).colspan(2)

        pack()
        setPosition(worldScreen.stage.width/2-width/2,
                5f)
    }

}