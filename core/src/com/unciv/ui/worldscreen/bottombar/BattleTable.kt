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
import com.unciv.models.translations.tr
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.ui.utils.Popup
import kotlin.math.max

class BattleTable(val worldScreen: WorldScreen): Table() {

    init {
        isVisible = false
        skin = CameraStageBaseScreen.skin
        background = ImageGetter.getBackground(ImageGetter.getBlue())

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

        val attacker = tryGetAttacker()
        if(attacker==null || !worldScreen.canChangeState){ hide(); return }

        if (attacker.getUnitType()==UnitType.Missile) {
            val selectedTile = worldScreen.mapHolder.selectedTile
            if (selectedTile == null) { hide(); return } // no selected tile
            simulateNuke(attacker as MapUnitCombatant, selectedTile)
        }
        else {
            val defender = tryGetDefender()
            if (defender == null) {
                hide(); return
            }

            simulateBattle(attacker, defender)
        }
    }

    private fun tryGetAttacker(): ICombatant? {
        val unitTable = worldScreen.bottomUnitTable
        if (unitTable.selectedUnit != null
                && !unitTable.selectedUnit!!.type.isCivilian()) {
            return MapUnitCombatant(unitTable.selectedUnit!!)
        } else if (unitTable.selectedCity != null) {
            return CityCombatant(unitTable.selectedCity!!)
        } else {
            return null // no attacker
        }
    }

    private fun tryGetDefender(): ICombatant? {
        val selectedTile = worldScreen.mapHolder.selectedTile ?: return null // no selected tile
        return tryGetDefenderAtTile(selectedTile, false)
    }

    private fun tryGetDefenderAtTile(selectedTile: TileInfo, includeFriendly: Boolean): ICombatant? {
        val attackerCiv = worldScreen.viewingCiv
        val defender: ICombatant? = Battle.getMapCombatantOfTile(selectedTile)

        if(defender==null ||
                !includeFriendly && defender.getCivInfo()==attackerCiv )
            return null  // no enemy combatant in tile

        val canSeeDefender = if(UncivGame.Current.viewEntireMapForDebug) true
        else {
            when {
                defender.isInvisible() -> attackerCiv.viewableInvisibleUnitsTiles.contains(selectedTile)
                defender.getUnitType()==UnitType.City -> attackerCiv.exploredTiles.contains(selectedTile.position)
                else -> attackerCiv.viewableTiles.contains(selectedTile)
            }
        }

        if(!canSeeDefender) return null

        return defender
    }

    private fun simulateBattle(attacker: ICombatant, defender: ICombatant){
        clear()

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
                BattleDamage.getAttackModifiers(attacker,null,defender).map {
                    val description = if(it.key.startsWith("vs ")) ("vs ["+it.key.replace("vs ","")+"]").tr() else it.key.tr()
                    val percentage = (if(it.value>0)"+" else "")+(it.value*100).toInt()+"%"
                    "$description: $percentage"
                }
        val defenderModifiers =
                if (defender is MapUnitCombatant)
                    BattleDamage.getDefenceModifiers(attacker, defender).map {
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
                    attackableTile = AttackableTile(attacker.getTile(), defender.getTile())
                }
            }
        }

        if (!worldScreen.isPlayersTurn || attackableTile == null) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }

        else {
            attackButton.onClick {
                Battle.moveAndAttack(attacker, attackableTile)
                worldScreen.mapHolder.unitActionOverlay?.remove() // the overlay was one of attacking
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
        attackerNameWrapper.add(UnitGroup(attacker.unit,25f)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)
        var canNuke = true
        val defenderNameWrapper = Table()
        for (tile in targetTile.getTilesInDistance(Battle.NUKE_RADIUS)) {

            //To make sure we dont nuke civilisations we cant declare war with
            val attackerCiv = attacker.getCivInfo()
            val defenderTileCiv = tile.getCity()?.civInfo

            if(defenderTileCiv != null && defenderTileCiv.knows(attackerCiv)) {
                val canAttackDefenderCiv = attackerCiv.getDiplomacyManager(defenderTileCiv).canAttack()
                canNuke = canNuke && canAttackDefenderCiv
            }
            val defender = tryGetDefenderAtTile(tile, true)

            if (defender == null) continue
            val defenderUnitCiv = defender.getCivInfo()

            if( defenderUnitCiv.knows(attackerCiv))
            {
                val canAttackDefenderUnitCiv = attackerCiv.getDiplomacyManager(defenderUnitCiv).canAttack()
                canNuke = canNuke && canAttackDefenderUnitCiv
            }

            val defenderLabel = Label(defender.getName().tr(), skin)
            when (defender) {
                is MapUnitCombatant ->
                    defenderNameWrapper.add(UnitGroup(defender.unit, 25f)).padRight(5f)
                is CityCombatant -> {
                    val nation = defender.city.civInfo.nation
                    val nationIcon = ImageGetter.getNationIcon(nation.name)
                    nationIcon.color = nation.getInnerColor()
                    defenderNameWrapper.add(nationIcon).size(25f).padRight(5f)
                }
            }
            defenderNameWrapper.add(defenderLabel).row()
        }
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)
        row().pad(5f)

        val attackButton = "NUKE".toTextButton().apply { color= Color.RED }

        val canReach = attacker.unit.currentTile.getTilesInDistance(attacker.unit.getRange()).contains(targetTile)

        if (!worldScreen.isPlayersTurn || !attacker.canAttack() || !canReach || !canNuke) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }
        else {
            attackButton.onClick {
                Battle.nuke(attacker, targetTile)
                worldScreen.mapHolder.unitActionOverlay?.remove() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

    private fun openBugReportPopup() {
        val battleBugPopup = Popup(worldScreen)
        battleBugPopup.addGoodSizedLabel("You've encountered a bug that I've been looking for for a while!").row()
        battleBugPopup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
        battleBugPopup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
        battleBugPopup.addGoodSizedLabel("It would help me figure out what went wrong, since this isn't supposed to happen!").row()
        battleBugPopup.addGoodSizedLabel("If you could tell me which unit was selected and which unit you tried to attack,").row()
        battleBugPopup.addGoodSizedLabel("  that would be even better!").row()
        battleBugPopup.open()
    }

    private fun getHealthBar(currentHealth: Int, maxHealth: Int, expectedDamage:Int): Table {
        val healthBar = Table()
        val totalWidth = 100f
        fun addHealthToBar(image: Image, amount:Int){
            healthBar.add(image).size(amount*totalWidth/maxHealth,3f)
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
