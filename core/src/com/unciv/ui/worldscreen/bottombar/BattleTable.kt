package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.math.max

class BattleTable(val worldScreen: WorldScreen): Table() {

    private val battle = Battle(worldScreen.civInfo.gameInfo)
    init{
        skin = CameraStageBaseScreen.skin
        background = ImageGetter.getDrawable(ImageGetter.WhiteDot)
                .tint(ImageGetter.getBlue())
        pad(10f)
    }

    fun hide(){
        clear()
    }

    fun update() {
        val unitTable = worldScreen.bottomBar.unitTable
        if (unitTable.selectedUnit == null
                || unitTable.selectedUnit!!.getBaseUnit().unitType == UnitType.Civilian){
            hide()
            return
        } // no attacker

        val attacker = MapUnitCombatant(unitTable.selectedUnit!!)

        if (worldScreen.tileMapHolder.selectedTile == null) return
        val selectedTile = worldScreen.tileMapHolder.selectedTile!!

        val defender: ICombatant? = Battle().getMapCombatantOfTile(selectedTile)

        if(defender==null || defender.getCivilization()==worldScreen.civInfo
                || !attacker.getCivilization().exploredTiles.contains(selectedTile.position)) {
            hide()
            return
        }
        simulateBattle(attacker, defender)
    }

    fun simulateBattle(attacker: MapUnitCombatant, defender: ICombatant){
        clear()
        row().pad(5f)
        val attackerLabel = Label(attacker.getName(), skin)
                .setFontColor(attacker.getCivilization().getCivilization().getColor())
        add(attackerLabel)

        val defenderLabel = Label(defender.getName(), skin)
                .setFontColor(defender.getCivilization().getCivilization().getColor())
        add(defenderLabel)

        row().pad(5f)

        add("Strength: "+attacker.getAttackingStrength(defender))
        add("Strength: "+defender.getDefendingStrength(attacker))
        row().pad(5f)

        val attackerModifiers = battle.getAttackModifiers(attacker,defender)  .map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }
        val defenderModifiers = battle.getDefenceModifiers(attacker, defender).map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }

        for(i in 0..max(attackerModifiers.size,defenderModifiers.size)){
            if (attackerModifiers.size > i) add(attackerModifiers[i]) else add()
            if (defenderModifiers.size > i) add(defenderModifiers[i]) else add()
            row().pad(5f)
        }

        var damageToDefender = battle.calculateDamageToDefender(attacker,defender)
        var damageToAttacker = battle.calculateDamageToAttacker(attacker,defender)


        if (damageToAttacker>attacker.getHealth() && damageToDefender>defender.getHealth()) // when damage exceeds health, we don't want to show negative health numbers
        // Also if both parties are supposed to die it's not indicative of who is more likely to win
        // So we "normalize" the damages until one dies
        {
            if(damageToDefender/defender.getHealth().toFloat() > damageToAttacker/attacker.getHealth().toFloat()) // defender dies quicker ie first
            {
                // Both damages *= (defender.health/damageToDefender)
                damageToDefender = defender.getHealth()
                damageToAttacker *= (defender.getHealth()/damageToDefender.toFloat()).toInt()
            } else{ // attacker dies first
                // Both damages *= (attacker.health/damageToAttacker)
                damageToAttacker = attacker.getHealth()
                damageToDefender *= (attacker.getHealth()/damageToAttacker.toFloat()).toInt()
            }
        }
        else if (damageToAttacker>attacker.getHealth()) damageToAttacker=attacker.getHealth()
        else if (damageToDefender>defender.getHealth()) damageToDefender=defender.getHealth()


        if(attacker.isMelee() && (defender.getUnitType()==UnitType.Civilian || defender.getUnitType()==UnitType.City && defender.isDefeated())) {
            add("")
            add("Captured!")
        }

        else {
            add("Health: " + attacker.getHealth().toString() + " -> "
                    + (attacker.getHealth() - damageToAttacker))

            add("Health: " + defender.getHealth().toString() + " -> "
                    + (defender.getHealth() - damageToDefender))
        }

        row().pad(5f)
        val attackButton = TextButton("Attack", skin)

        attacker.unit.getDistanceToTiles()

        val attackerCanReachDefender = UnitAutomation().getAttackableEnemies(attacker.unit)
                .contains(defender.getTile())

        if(!attackerCanReachDefender || !attacker.unit.canAttack()) attackButton.disable()
        else {
            attackButton.addClickListener {
                if(attacker.isMelee())
                    attacker.unit.movementAlgs().headTowards(defender.getTile())
                battle.attack(attacker, defender)
                worldScreen.update()
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

}