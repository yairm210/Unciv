package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
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
import com.unciv.ui.worldscreen.optionstable.PopupTable
import kotlin.math.max

class BattleTable(val worldScreen: WorldScreen): Table() {

    private val battle = Battle(worldScreen.viewingCiv.gameInfo)
    init{
        isVisible = false
        skin = CameraStageBaseScreen.skin
        background = ImageGetter.getBackground(ImageGetter.getBlue())
        pad(5f)
        touchable = Touchable.enabled
    }

    fun hide(){
        isVisible = false
        clear()
        pack()
    }

    fun update() {
        isVisible = true
        val unitTable = worldScreen.bottomUnitTable
        val attacker : ICombatant?
        if (unitTable.selectedUnit != null
                && !unitTable.selectedUnit!!.type.isCivilian()) {
            attacker = MapUnitCombatant(unitTable.selectedUnit!!)
        } else if (unitTable.selectedCity != null) {
            attacker = CityCombatant(unitTable.selectedCity!!)
        } else {
            hide()
            return // no attacker
        }

        if (worldScreen.tileMapHolder.selectedTile == null) return
        val selectedTile = worldScreen.tileMapHolder.selectedTile!!

        val defender: ICombatant? = Battle(worldScreen.gameInfo).getMapCombatantOfTile(selectedTile)

        if(defender==null ||
                defender.getCivInfo()==worldScreen.viewingCiv
                || !(UnCivGame.Current.viewEntireMapForDebug
                        || attacker.getCivInfo().exploredTiles.contains(selectedTile.position))) {
            hide()
            return
        }

        if(defender.isInvisible()
                && !attacker.getCivInfo().viewableInvisibleUnitsTiles.contains(selectedTile)) {
            hide()
            return
        }

        simulateBattle(attacker, defender)
    }

    fun simulateBattle(attacker: ICombatant, defender: ICombatant){
        clear()
        defaults().pad(5f)

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel()
        if(attacker is MapUnitCombatant)
            attackerNameWrapper.add(UnitGroup(attacker.unit,25f)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val defenderNameWrapper = Table()
        val defenderLabel = Label(defender.getName().tr(), skin)
        if(defender is MapUnitCombatant)
            defenderNameWrapper.add(UnitGroup(defender.unit,25f)).padRight(5f)
        defenderNameWrapper.add(defenderLabel)
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)

        add("{Strength}: ".tr()+attacker.getAttackingStrength())
        add("{Strength}: ".tr()+defender.getDefendingStrength()).row()

        val attackerModifiers =
                BattleDamage().getAttackModifiers(attacker,defender).map {
                    val description = if(it.key.startsWith("vs ")) ("vs ["+it.key.replace("vs ","")+"]").tr() else it.key.tr()
                    val percentage = (if(it.value>0)"+" else "")+(it.value*100).toInt()+"%"
                    "$description: $percentage"
                }
        val defenderModifiers =
                if (defender is MapUnitCombatant)
                    BattleDamage().getDefenceModifiers(attacker, defender).map {
                        val description = if(it.key.startsWith("vs ")) ("vs ["+it.key.replace("vs ","")+"]").tr() else it.key.tr()
                        val percentage = (if(it.value>0)"+" else "")+(it.value*100).toInt()+"%"
                        "$description: $percentage"
                    }
                else listOf()

        for(i in 0..max(attackerModifiers.size,defenderModifiers.size)){
            if (attackerModifiers.size > i) add(attackerModifiers[i].toLabel(fontSize = 14)) else add()
            if (defenderModifiers.size > i) add(defenderModifiers[i].toLabel(fontSize = 14)) else add()
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
            add(if(defender.getUnitType().isCivilian()) "Captured!".tr() else "Occupied!".tr() )
        }


        else {
            add(getHealthBar(attacker.getHealth(), attacker.getMaxHealth(), damageToAttacker))
            add(getHealthBar(defender.getHealth(), defender.getMaxHealth(), damageToDefender))
        }

        row().pad(5f)
        val attackText : String = when {
            attacker is MapUnitCombatant && attacker.getUnitType().isMissileUnit() -> "NUKE"
            attacker is MapUnitCombatant -> "Attack"
            else -> "Bombard"
        }
        val attackButton = TextButton(attackText.tr(), skin).apply { color= Color.RED }

        var attackableEnemy : UnitAutomation.AttackableTile? = null

        if (attacker.canAttack()) {
            if (attacker is MapUnitCombatant) {
                attackableEnemy = UnitAutomation()
                        .getAttackableEnemies(attacker.unit, attacker.unit.movement.getDistanceToTiles())
                        .firstOrNull{ it.tileToAttack == defender.getTile()}
            }
            else if (attacker is CityCombatant)
            {
                val canBombard = UnitAutomation().getBombardTargets(attacker.city).contains(defender.getTile())
                if (canBombard) {
                    attackableEnemy = UnitAutomation.AttackableTile(attacker.getTile(), defender.getTile())
                }    
            }
        }

        if (!worldScreen.isPlayersTurn || attackableEnemy == null) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }

        else {
            attackButton.onClick {
                try {
                    battle.moveAndAttack(attacker, attackableEnemy)
                    worldScreen.tileMapHolder.unitActionOverlay?.remove() // the overlay was one of attacking
                    worldScreen.shouldUpdate = true
                }
                catch (ex:Exception){
                    val battleBugPopup = PopupTable(worldScreen)
                    battleBugPopup.addGoodSizedLabel("You've encountered a bug that I've been looking for for a while!").row()
                    battleBugPopup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
                    battleBugPopup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
                    battleBugPopup.addGoodSizedLabel("It would help me figure out what went wrong, since this isn't supposed to happen!").row()
                    battleBugPopup.addGoodSizedLabel("If you could tell me which unit was selected and which unit you tried to attack,").row()
                    battleBugPopup.addGoodSizedLabel("  that would be even better!").row()
                    battleBugPopup.open()
                }
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

    fun getHealthBar(currentHealth: Int, maxHealth: Int, expectedDamage:Int): Table {
        val healthBar = Table()
        val totalWidth = 100f
        fun addHealthToBar(image: Image, amount:Int){
            healthBar.add(image).size(amount*totalWidth/maxHealth,3f)
        }
        addHealthToBar(ImageGetter.getDot(Color.BLACK), maxHealth-currentHealth)

        val damagedHealth = ImageGetter.getDot(Color.RED)
        damagedHealth.addAction(Actions.repeat(RepeatAction.FOREVER, Actions.sequence(
                Actions.color(Color.BLACK,0.7f),
                Actions.color(Color.RED,0.7f)
        )))
        addHealthToBar(damagedHealth,expectedDamage)

        val remainingHealth = currentHealth-expectedDamage
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
