package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.battle.*
import com.unciv.models.gamebasics.tr
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.math.max

class BattleTable(val worldScreen: WorldScreen): Table() {

    private val battle = Battle(worldScreen.currentPlayerCiv.gameInfo)
    init{
        skin = CameraStageBaseScreen.skin
        background = ImageGetter.getBackground(ImageGetter.getBlue())
        pad(5f)
    }

    fun hide(){
        clear()
    }

    fun update() {
        val unitTable = worldScreen.bottomBar.unitTable
        val attacker : ICombatant?
        if (unitTable.selectedUnit != null
                && !unitTable.selectedUnit!!.type.isCivilian()) {
            attacker = MapUnitCombatant(unitTable.selectedUnit!!)
        } else if (unitTable.selectedCity != null) {
            attacker = CityCombatant(unitTable.selectedCity!!)
        } else {
            return // no attacker
        }

        if (worldScreen.tileMapHolder.selectedTile == null) return
        val selectedTile = worldScreen.tileMapHolder.selectedTile!!

        val defender: ICombatant? = Battle(worldScreen.gameInfo).getMapCombatantOfTile(selectedTile)

        if(defender==null ||
                defender.getCivilization()==worldScreen.currentPlayerCiv
                || !(UnCivGame.Current.viewEntireMapForDebug
                        || attacker.getCivilization().exploredTiles.contains(selectedTile.position))) {
            hide()
            return
        }
        simulateBattle(attacker, defender)
    }

    fun simulateBattle(attacker: ICombatant, defender: ICombatant){
        clear()
        defaults().pad(5f)
        println("debuglog simulateBattle")

        val attackerNameWrapper = Table()
        val attackerLabel = Label(attacker.getName(), skin)
        if(attacker is MapUnitCombatant)
            attackerNameWrapper.add(UnitGroup(attacker.unit,25f)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val defenderNameWrapper = Table()
        val defenderLabel = Label(defender.getName(), skin)
        if(defender is MapUnitCombatant)
            defenderNameWrapper.add(UnitGroup(defender.unit,25f)).padRight(5f)
        defenderNameWrapper.add(defenderLabel)
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)

        add("{Strength}: ".tr()+attacker.getAttackingStrength())
        add("{Strength}: ".tr()+defender.getDefendingStrength()).row()

        val attackerModifiers = BattleDamage().getAttackModifiers(attacker,defender)  .map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }
        val defenderModifiers = if (defender is MapUnitCombatant)
                                    BattleDamage().getDefenceModifiers(attacker, defender).map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }
                                else listOf()

        for(i in 0..max(attackerModifiers.size,defenderModifiers.size)){
            if (attackerModifiers.size > i) add(attackerModifiers[i]).actor.setFontSize(14) else add()
            if (defenderModifiers.size > i) add(defenderModifiers[i]).actor.setFontSize(14) else add()
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


        if(attacker.isMelee() && (defender.getUnitType().isCivilian()
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
        val attackText : String = if (attacker is MapUnitCombatant) "Attack" else "Bombard"
        val attackButton = TextButton(attackText.tr(), skin).apply { color= Color.RED }

        var attackableEnemy : UnitAutomation.AttackableTile? = null
        var canAttack : Boolean = attacker.canAttack()

        if (canAttack) {
            if (attacker is MapUnitCombatant) {
                attacker.unit.getDistanceToTiles()
                attackableEnemy = UnitAutomation().getAttackableEnemies(attacker.unit, attacker.unit.getDistanceToTiles())
                        .firstOrNull{ it.tileToAttack == defender.getTile()}
                canAttack = attackableEnemy != null
            }
            else if (attacker is CityCombatant)
            {
                canAttack = UnitAutomation().getBombardTargets(attacker.city).contains(defender.getTile())
            }
        }

        if(!canAttack) attackButton.disable()
        else {
            //var attackSound = attacker.unit.baseUnit.attackSound
            //if(attackSound==null) attackSound="click"`
            //attackButton.onClick(attackSound) {
            attackButton.onClick {
                if (attacker is MapUnitCombatant) {
                    attacker.unit.moveToTile(attackableEnemy!!.tileToAttackFrom)
                }
                battle.attack(attacker, defender)
                worldScreen.shouldUpdate=true
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

}