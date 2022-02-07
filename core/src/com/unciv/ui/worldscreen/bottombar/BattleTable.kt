package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.automation.BattleHelper
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.battle.*
import com.unciv.logic.map.TileInfo
import com.unciv.models.AttackableTile
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.math.max

class BattleTable(val worldScreen: WorldScreen): Table() {

    init {
        isVisible = false
        skin = BaseScreen.skin
        background = ImageGetter.getBackground(ImageGetter.getBlue().apply { a=0.8f })

        defaults().pad(5f)
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
        if (!worldScreen.canChangeState) { hide(); return }

        val attacker = tryGetAttacker()
        if (attacker == null) { hide(); return }

        if (attacker is MapUnitCombatant && attacker.unit.baseUnit.isNuclearWeapon()) {
            val selectedTile = worldScreen.mapHolder.selectedTile
            if (selectedTile == null) { hide(); return } // no selected tile
            simulateNuke(attacker, selectedTile)
        } else {
            val defender = tryGetDefender()
            if (defender == null) { hide(); return }
            simulateBattle(attacker, defender)
        }
        pack()
        addBorderAllowOpacity(1f, Color.WHITE)
    }

    private fun tryGetAttacker(): ICombatant? {
        val unitTable = worldScreen.bottomUnitTable
        return if (unitTable.selectedUnit != null
                && !unitTable.selectedUnit!!.isCivilian()
                && !unitTable.selectedUnit!!.hasUnique(UniqueType.CannotAttack))
                    MapUnitCombatant(unitTable.selectedUnit!!)
        else if (unitTable.selectedCity != null)
            CityCombatant(unitTable.selectedCity!!)
        else null // no attacker
    }

    private fun tryGetDefender(): ICombatant? {
        val selectedTile = worldScreen.mapHolder.selectedTile ?: return null // no selected tile
        return tryGetDefenderAtTile(selectedTile, false)
    }

    private fun tryGetDefenderAtTile(selectedTile: TileInfo, includeFriendly: Boolean): ICombatant? {
        val attackerCiv = worldScreen.viewingCiv
        val defender: ICombatant? = Battle.getMapCombatantOfTile(selectedTile)

        if (defender == null || (!includeFriendly && defender.getCivInfo() == attackerCiv))
            return null  // no enemy combatant in tile

        val canSeeDefender = 
            if (UncivGame.Current.viewEntireMapForDebug) true
            else {
                when {
                    defender.isInvisible(attackerCiv) -> attackerCiv.viewableInvisibleUnitsTiles.contains(selectedTile)
                    defender.isCity() -> attackerCiv.exploredTiles.contains(selectedTile.position)
                    else -> attackerCiv.viewableTiles.contains(selectedTile)
                }
            }

        if (!canSeeDefender) return null

        return defender
    }

    private fun getIcon(combatant:ICombatant) =
        if (combatant is MapUnitCombatant) UnitGroup(combatant.unit,25f)
        else ImageGetter.getNationIndicator(combatant.getCivInfo().nation, 25f)

    private fun simulateBattle(attacker: ICombatant, defender: ICombatant){
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel()
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val defenderNameWrapper = Table()
        val defenderLabel = Label(defender.getName().tr(), skin)
        defenderNameWrapper.add(getIcon(defender)).padRight(5f)

        defenderNameWrapper.add(defenderLabel)
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)

        add(attacker.getAttackingStrength().toString() + Fonts.strength)
        add(defender.getDefendingStrength().toString() + Fonts.strength).row()


        val quarterScreen = worldScreen.stage.width/4

        val attackerModifiers =
                BattleDamage.getAttackModifiers(attacker, defender).map {
                    val description = if (it.key.startsWith("vs "))
                        ("vs [" + it.key.replace("vs ", "") + "]").tr()
                    else it.key.tr()
                    val percentage = (if (it.value > 0) "+" else "") + it.value + "%"

                    val upOrDownLabel = if (it.value > 0f) "⬆".toLabel(Color.GREEN) else "⬇".toLabel(
                        Color.RED)
                    val modifierTable = Table()
                    modifierTable.add(upOrDownLabel)
                    val modifierLabel = "$percentage $description".toLabel(fontSize = 14).apply { wrap=true }
                    modifierTable.add(modifierLabel).width(quarterScreen)
                    modifierTable
                }
        val defenderModifiers =
                if (defender is MapUnitCombatant)
                    BattleDamage.getDefenceModifiers(attacker, defender).map {
                        val description = if(it.key.startsWith("vs ")) ("vs ["+it.key.replace("vs ","")+"]").tr() else it.key.tr()
                        val percentage = (if(it.value>0)"+" else "")+ it.value +"%"
                        val upOrDownLabel = if (it.value > 0f) "⬆".toLabel(Color.GREEN) else "⬇".toLabel(
                            Color.RED)
                        val modifierTable = Table()
                        modifierTable.add(upOrDownLabel)
                        val modifierLabel = "$percentage $description".toLabel(fontSize = 14).apply { wrap=true }
                        modifierTable.add(modifierLabel).width(quarterScreen)
                        modifierTable
                    }
                else listOf()

        for (i in 0..max(attackerModifiers.size,defenderModifiers.size)) {
            if (attackerModifiers.size > i)
                add(attackerModifiers[i])
            else add().width(quarterScreen)
            if (defenderModifiers.size > i)
                add(defenderModifiers[i])
            else add().width(quarterScreen)
            row().pad(2f)
        }

        // from Battle.addXp(), check for can't gain more XP from Barbarians
        val modConstants = attacker.getCivInfo().gameInfo.ruleSet.modOptions.constants
        if (attacker.getXP() >= modConstants.maxXPfromBarbarians
                && defender.getCivInfo().isBarbarian()){
            add("Cannot gain more XP from Barbarians".tr().toLabel(fontSize = 16).apply { wrap=true }).width(quarterScreen)
            row()
        }

        var damageToDefender = BattleDamage.calculateDamageToDefender(attacker,null,defender)
        var damageToAttacker = BattleDamage.calculateDamageToAttacker(attacker,null,defender)


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


        if(attacker.isMelee() && (defender.isCivilian()
                        || defender is CityCombatant && defender.isDefeated())) {
            add("")
            add(
                if (defender.isCivilian()
                    && (defender as MapUnitCombatant).unit.hasUnique(UniqueType.Uncapturable)
                ) ""
                else if (defender.isCivilian()) "Captured!".tr()
                else "Occupied!".tr()
            )
        }


        else {
            add(getHealthBar(attacker.getHealth(), attacker.getMaxHealth(), damageToAttacker))
            add(getHealthBar(defender.getHealth(), defender.getMaxHealth(), damageToDefender))
        }

        row().pad(5f)
        val attackText : String = when (attacker) {
            is CityCombatant -> "Bombard"
            else -> "Attack"
        }
        val attackButton = attackText.toTextButton().apply { color= Color.RED }

        var attackableTile : AttackableTile? = null

        if (attacker.canAttack()) {
            if (attacker is MapUnitCombatant) {
                attackableTile = BattleHelper
                        .getAttackableEnemies(attacker.unit, attacker.unit.movement.getDistanceToTiles())
                        .firstOrNull{ it.tileToAttack == defender.getTile()}
            }
            else if (attacker is CityCombatant)
            {
                val canBombard = UnitAutomation.getBombardTargets(attacker.city).contains(defender.getTile())
                if (canBombard) {
                    attackableTile = AttackableTile(attacker.getTile(), defender.getTile(), 0f)
                }
            }
        }

        if (!worldScreen.isPlayersTurn || attackableTile == null) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }

        else {
            attackButton.onClick(attacker.getAttackSound()) {
                Battle.moveAndAttack(attacker, attackableTile)
                worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

    private fun simulateNuke(attacker: MapUnitCombatant, targetTile: TileInfo){
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel()
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)
        
        val canNuke = Battle.mayUseNuke(attacker, targetTile)

        val blastRadius =
            if (!attacker.unit.hasUnique(UniqueType.BlastRadius)) 2
            else attacker.unit.getMatchingUniques(UniqueType.BlastRadius).first().params[0].toInt()
        
        val defenderNameWrapper = Table()
        for (tile in targetTile.getTilesInDistance(blastRadius)) {
            val defender = tryGetDefenderAtTile(tile, true) ?: continue
            
            val defenderLabel = Label(defender.getName().tr(), skin)
            defenderNameWrapper.add(getIcon(defender)).padRight(5f)
            defenderNameWrapper.add(defenderLabel).row()
        }
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)
        row().pad(5f)

        val attackButton = "NUKE".toTextButton().apply { color = Color.RED }

        val canReach = attacker.unit.currentTile.getTilesInDistance(attacker.unit.getRange()).contains(targetTile)

        if (!worldScreen.isPlayersTurn || !attacker.canAttack() || !canReach || !canNuke) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }
        else {
            attackButton.onClick(attacker.getAttackSound()) {
                Battle.NUKE(attacker, targetTile)
                worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

    private fun getHealthBar(currentHealth: Int, maxHealth: Int, expectedDamage:Int): Table {
        val healthBar = Table()
        val totalWidth = 100f
        fun addHealthToBar(image: Image, amount:Int) {
            val width = totalWidth * amount/maxHealth
            healthBar.add(image).size(width.coerceIn(0f,totalWidth),3f)
        }
        addHealthToBar(ImageGetter.getDot(Color.BLACK), maxHealth-currentHealth)

        val damagedHealth = ImageGetter.getDot(Color.FIREBRICK)
        if (UncivGame.Current.settings.continuousRendering) {
            damagedHealth.addAction(Actions.repeat(RepeatAction.FOREVER, Actions.sequence(
                    Actions.color(Color.BLACK,0.7f),
                    Actions.color(Color.FIREBRICK,0.7f)
            ))) }
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
