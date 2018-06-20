package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
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
        pad(5f)
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
                || !(attacker.getCivilization().exploredTiles.contains(selectedTile.position) || UnCivGame.Current.viewEntireMapForDebug)) {
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

        add("{Strength}: ".tr()+attacker.getAttackingStrength(defender))
        add("{Strength}: ".tr()+defender.getDefendingStrength(attacker))
        row().pad(5f)

        val attackerModifiers = BattleDamage().getAttackModifiers(attacker,defender)  .map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }
        val defenderModifiers = if (defender is MapUnitCombatant)
                                    BattleDamage().getDefenceModifiers(attacker, defender).map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }
                                else listOf()

        for(i in 0..max(attackerModifiers.size,defenderModifiers.size)){
            if (attackerModifiers.size > i) add(attackerModifiers[i]).actor.setFont(14) else add()
            if (defenderModifiers.size > i) add(defenderModifiers[i]).actor.setFont(14) else add()
            row().pad(2f)
        }

        var damageToDefender = BattleDamage().calculateDamageToDefender(attacker,defender)
        var damageToAttacker = BattleDamage().calculateDamageToAttacker(attacker,defender)


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


        if(attacker.isMelee() && (defender.getUnitType()==UnitType.Civilian
                        || defender.getUnitType()==UnitType.City && defender.isDefeated())) {
            add("")
            add("{Captured!}".tr())
        }

        else {
            add("{Health}: ".tr() + attacker.getHealth().toString() + " -> "
                    + (attacker.getHealth() - damageToAttacker))

            add("{Health}: ".tr() + defender.getHealth().toString() + " -> "
                    + (defender.getHealth() - damageToDefender))
        }

        row().pad(5f)
        val attackButton = TextButton("Attack", skin)

        attacker.unit.getDistanceToTiles()

        val attackableEnemy = UnitAutomation().getAttackableEnemies(attacker.unit)
                .firstOrNull{ it.tileToAttack == defender.getTile()}

        if(attackableEnemy==null || !attacker.unit.canAttack()) attackButton.disable()
        else {
            attackButton.addClickListener {
                attacker.unit.moveToTile(attackableEnemy.tileToAttackFrom)
                battle.attack(attacker, defender)
                worldScreen.update()
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

}