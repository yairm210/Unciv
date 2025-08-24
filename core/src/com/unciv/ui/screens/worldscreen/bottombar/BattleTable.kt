package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.battle.AirInterception
import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.Nuke
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.addBorderAllowOpacity
import com.unciv.ui.components.extensions.addRoundCloseButton
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.clearUndoCheckpoints
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.battleAnimationDeferred
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.getHealthBar
import com.unciv.utils.DebugUtils
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.roundToInt

class BattleTable(val worldScreen: WorldScreen) : Table() {

    init {
        isVisible = false
        skin = BaseScreen.skin
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/BattleTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.apply { a = 0.8f }
        )

        defaults().pad(5f)
        pad(5f)
        touchable = Touchable.enabled
    }

    private fun hide() {
        isVisible = false
        clear()
        pack()
    }

    fun update() {
        val attacker = tryGetAttacker() ?: return hide()

        if (attacker is MapUnitCombatant && attacker.unit.isNuclearWeapon()) {
            val selectedTile = worldScreen.mapHolder.selectedTile
                ?: return hide() // no selected tile
            if (selectedTile == attacker.getTile()) return hide() // mayUseNuke would test this again, but not actually seeing the nuke-yourself table just by selecting the nuke is nicer
            simulateNuke(attacker, selectedTile)
        } else if (attacker is MapUnitCombatant && attacker.unit.isPreparingAirSweep()) {
            val selectedTile = worldScreen.mapHolder.selectedTile
                ?: return hide() // no selected tile
            simulateAirsweep(attacker, selectedTile)
        } else {
            val defender = tryGetDefender() ?: return hide()
            if (attacker is CityCombatant && defender is CityCombatant) return hide()
            val tileToAttackFrom = if (attacker is MapUnitCombatant)
                TargetHelper.getAttackableEnemies(
                    attacker.unit,
                    attacker.unit.movement.getDistanceToTiles()
                )
                    .firstOrNull { it.tileToAttack == defender.getTile() }?.tileToAttackFrom ?: attacker.getTile()
            else attacker.getTile()
            simulateBattle(attacker, defender, tileToAttackFrom)
        }

        isVisible = true
        pack()

        addBorderAllowOpacity(2f, Color.WHITE)
        addRoundCloseButton(this) {
            isVisible = false
        }

    }

    @Readonly
    private fun tryGetAttacker(): ICombatant? {
        val unitTable = worldScreen.bottomUnitTable
        return if (unitTable.selectedUnit != null
                && !unitTable.selectedUnit!!.isCivilian()
                && !unitTable.selectedUnit!!.hasUnique(UniqueType.CannotAttack))  // purely cosmetic - hide battle table
                    MapUnitCombatant(unitTable.selectedUnit!!)
        else if (unitTable.selectedCity != null)
            CityCombatant(unitTable.selectedCity!!)
        else null // no attacker
    }

    @Readonly
    private fun tryGetDefender(): ICombatant? {
        val selectedTile = worldScreen.mapHolder.selectedTile ?: return null // no selected tile
        return tryGetDefenderAtTile(selectedTile, false)
    }

    @Readonly
    private fun tryGetDefenderAtTile(selectedTile: Tile, includeFriendly: Boolean): ICombatant? {
        val attackerCiv = worldScreen.viewingCiv
        val defender: ICombatant? = Battle.getMapCombatantOfTile(selectedTile)

        if (defender == null || (!includeFriendly && defender.getCivInfo() == attackerCiv))
            return null  // no enemy combatant in tile

        val canSeeDefender = when {
            DebugUtils.VISIBLE_MAP -> true
            defender.isInvisible(attackerCiv) -> attackerCiv.viewableInvisibleUnitsTiles.contains(selectedTile)
            defender.isCity() -> attackerCiv.hasExplored(selectedTile)
            else -> attackerCiv.viewableTiles.contains(selectedTile)
        }

        if (!canSeeDefender) return null

        return defender
    }

    private fun getIcon(combatant: ICombatant) =
        if (combatant is MapUnitCombatant) UnitIconGroup(combatant.unit,25f)
        else ImageGetter.getNationPortrait(combatant.getCivInfo().nation, 25f)

    private val quarterScreen = worldScreen.stage.width / 4

    private fun getModifierTable(key: String, value: Int) = Table().apply {
        val description = if (key.startsWith("vs "))
            ("vs [" + key.drop(3) + "]").tr()
        else key.tr()
        val percentage = (if (value > 0) "+" else "") + value + "%"
        val upOrDownLabel = if (value > 0f) "⬆".toLabel(Color.GREEN)
        else "⬇".toLabel(Color.RED)

        add(upOrDownLabel)
        val modifierLabel = "$percentage $description".toLabel(fontSize = 14).apply { wrap = true }
        add(modifierLabel).width(quarterScreen - upOrDownLabel.minWidth)
    }

    private fun simulateBattle(attacker: ICombatant, defender: ICombatant, tileToAttackFrom: Tile) {
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val defenderNameWrapper = Table()
        val defenderLabel = Label(defender.getName().tr(hideIcons = true), skin)
        defenderNameWrapper.add(getIcon(defender)).padRight(5f)

        defenderNameWrapper.add(defenderLabel)
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)

        val attackIcon = if (attacker.isRanged()) Fonts.rangedStrength else Fonts.strength
        val defenceIcon =
            if (attacker.isRanged() && defender.isRanged() && !defender.isCity() && !(defender is MapUnitCombatant && defender.unit.isEmbarked()))
                Fonts.rangedStrength
            else Fonts.strength // use strength icon if attacker is melee, defender is melee, defender is a city, or defender is embarked
        add(attacker.getAttackingStrength().tr() + attackIcon)
        add(defender.getDefendingStrength(attacker.isRanged()).tr() + defenceIcon).row()

        val attackerModifiers =
                BattleDamage.getAttackModifiers(attacker, defender, tileToAttackFrom).map {
                    getModifierTable(it.key, it.value)
                }
        val defenderModifiers =
                if (defender is MapUnitCombatant)
                    BattleDamage.getDefenceModifiers(attacker, defender, tileToAttackFrom).map {
                        getModifierTable(it.key, it.value)
                    }
                else listOf()

        for (i in 0..max(attackerModifiers.size, defenderModifiers.size)) {
            if (i < attackerModifiers.size) add(attackerModifiers[i]) else add().width(quarterScreen)
            if (i < defenderModifiers.size) add(defenderModifiers[i]) else add().width(quarterScreen)
            row().pad(2f)
        }

        if (attackerModifiers.any() || defenderModifiers.any()) {
            addSeparator()
            val attackerStrength = BattleDamage.getAttackingStrength(attacker, defender, tileToAttackFrom).roundToInt()
            val defenderStrength = BattleDamage.getDefendingStrength(attacker, defender, tileToAttackFrom).roundToInt()
            add(attackerStrength.tr() + attackIcon)
            add(defenderStrength.tr() + attackIcon).row()
        }

        // from Battle.addXp(), check for can't gain more XP from Barbarians
        val maxXPFromBarbarians = attacker.getCivInfo().gameInfo.ruleset.modOptions.constants.maxXPfromBarbarians
        if (attacker is MapUnitCombatant && attacker.unit.promotions.totalXpProduced() >= maxXPFromBarbarians
                && defender.getCivInfo().isBarbarian
        ) {
            add("Cannot gain more XP from Barbarians".toLabel(fontSize = 16).apply { wrap = true }).width(quarterScreen)
            row()
        }

        if (attacker.isMelee() &&
                (defender.isCivilian() || defender is CityCombatant && defender.isDefeated())) {
            add()
            val defeatedText = when {
                !defender.isCivilian() -> "Occupied!"
                (defender as MapUnitCombatant).unit.hasUnique(UniqueType.Uncapturable) -> ""
                else -> "Captured!"
            }
            add(defeatedText.toLabel())
        } else {
            val maxDamageToDefender = BattleDamage.calculateDamageToDefender(attacker, defender, tileToAttackFrom, 1f)
            val minDamageToDefender = BattleDamage.calculateDamageToDefender(attacker, defender, tileToAttackFrom, 0f)

            val maxDamageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, defender, tileToAttackFrom, 1f)
            val minDamageToAttacker = BattleDamage.calculateDamageToAttacker(attacker, defender, tileToAttackFrom, 0f)

            val attackerHealth = attacker.getHealth()
            val minRemainingLifeAttacker = max(attackerHealth-maxDamageToAttacker, 0)
            val maxRemainingLifeAttacker = max(attackerHealth-minDamageToAttacker, 0)

            val defenderHealth = defender.getHealth()
            val minRemainingLifeDefender = max(defenderHealth-maxDamageToDefender, 0)
            val maxRemainingLifeDefender = max(defenderHealth-minDamageToDefender, 0)

            add(getHealthBar(attacker.getMaxHealth(), attacker.getHealth(), maxRemainingLifeAttacker, minRemainingLifeAttacker))
            add(getHealthBar(defender.getMaxHealth(), defender.getHealth(), maxRemainingLifeDefender, minRemainingLifeDefender, true)).row()

            fun avg(vararg values: Int) = values.average().roundToInt()
            // Don't use original damage estimates - they're raw, before clamping to 0..max
            val avgDamageToDefender = avg(defenderHealth - minRemainingLifeDefender, defenderHealth - maxRemainingLifeDefender)
            val avgDamageToAttacker = avg(attackerHealth - minRemainingLifeAttacker, attackerHealth - maxRemainingLifeAttacker)

            if (minRemainingLifeAttacker == attackerHealth) add(attackerHealth.toLabel())
            else if (maxRemainingLifeAttacker == minRemainingLifeAttacker) add("$attackerHealth → $maxRemainingLifeAttacker ($avgDamageToAttacker)".toLabel())
            else add("$attackerHealth → $minRemainingLifeAttacker-$maxRemainingLifeAttacker (~$avgDamageToAttacker)".toLabel())


            if (minRemainingLifeDefender == maxRemainingLifeDefender) add("$defenderHealth → $maxRemainingLifeDefender ($avgDamageToDefender)".toLabel())
            else add("$defenderHealth → $minRemainingLifeDefender-$maxRemainingLifeDefender (~$avgDamageToDefender)".toLabel())
        }

        row().pad(5f)

        if (worldScreen.canChangeState) {
            val attackText: String = when (attacker) {
                is CityCombatant -> "Bombard"
                else -> "Attack"
            }
            val attackButton = attackText.toTextButton().apply { color = Color.RED }

            var attackableTile: AttackableTile? = null

            if (attacker.canAttack()) {
                if (attacker is MapUnitCombatant) {
                    attackableTile = TargetHelper
                        .getAttackableEnemies(
                            attacker.unit,
                            attacker.unit.movement.getDistanceToTiles()
                        )
                        .firstOrNull { it.tileToAttack == defender.getTile() }
                } else if (attacker is CityCombatant) {
                    val canBombard =
                        TargetHelper.getBombardableTiles(attacker.city).contains(defender.getTile())
                    if (canBombard) {
                        attackableTile =
                            AttackableTile(attacker.getTile(), defender.getTile(), 0f, defender)
                    }
                }
            }

            if (!worldScreen.isPlayersTurn || attackableTile == null) {
                attackButton.disable()
                attackButton.label.color = Color.GRAY
            } else {
                attackButton.onClick(UncivSound.Silent) {  // onAttackButtonClicked will do the sound
                    onAttackButtonClicked(attacker, defender, attackableTile)
                }
            }

            add(attackButton).colspan(2)
        }


        pack()

        setPosition(worldScreen.stage.width / 2 - width / 2, 5f)
    }

    private fun onAttackButtonClicked(
        attacker: ICombatant,
        defender: ICombatant,
        attackableTile: AttackableTile
    ) {
        val canStillAttack = Battle.movePreparingAttack(attacker, attackableTile)
        worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
        // There was a direct worldScreen.update() call here, removing its 'private' but not the comment justifying the modifier.
        // My tests (desktop only) show the red-flash animations look just fine without.
        worldScreen.shouldUpdate = true
        worldScreen.clearUndoCheckpoints()
        //Gdx.graphics.requestRendering()  // Use this if immediate rendering is required

        if (!canStillAttack) return
        SoundPlayer.play(attacker.getAttackSound())
        val (damageToDefender, damageToAttacker) = Battle.attackOrNuke(attacker, attackableTile)

        worldScreen.battleAnimationDeferred(attacker, damageToAttacker, defender, damageToDefender)
        if (!attacker.canAttack()) hide()
    }


    private fun simulateNuke(attacker: MapUnitCombatant, targetTile: Tile) {
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val canNuke = Nuke.mayUseNuke(attacker, targetTile)

        val blastRadius = attacker.unit.getNukeBlastRadius()

        val defenderNameWrapper = Table()
        for (tile in targetTile.getTilesInDistance(blastRadius)) {
            val defender = tryGetDefenderAtTile(tile, true) ?: continue

            val defenderLabel = defender.getName().toLabel(hideIcons = true)
            defenderNameWrapper.add(getIcon(defender)).padRight(5f)
            defenderNameWrapper.add(defenderLabel).row()
        }
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)
        row().pad(5f)

        val attackButton = "NUKE".toTextButton().apply { color = Color.RED }

        if (!worldScreen.isPlayersTurn || !attacker.canAttack() || !canNuke) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }
        else {
            attackButton.onClick(attacker.getAttackSound()) {
                Nuke.NUKE(attacker, targetTile)

                val nukeCircle = ImageGetter.getCircle()
                nukeCircle.setSize(10f)
                nukeCircle.setOrigin(Align.center)
                nukeCircle.addAction(Actions.sequence(
                    Actions.fadeOut(0f),
                    Actions.parallel(
                        Actions.fadeIn(1f, Interpolation.pow2In),
                        Actions.scaleTo(200f, 200f, 1f, Interpolation.linear),
                    ),
                    Actions.delay(1f),
                    Actions.fadeOut(1f, Interpolation.pow2Out),
                    Actions.removeActor()
                    )
                )
                val targetTileGroup = worldScreen.mapHolder.tileGroups[targetTile]!!
                nukeCircle.x = targetTileGroup.x
                nukeCircle.y = targetTileGroup.y
                targetTileGroup.parent.addActor(nukeCircle)


                worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width / 2 - width / 2, 5f)
    }

    private fun simulateAirsweep(attacker: MapUnitCombatant, targetTile: Tile)
    {
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val canAttack = attacker.canAttack()

        val defenderLabel = Label("???", skin)
        add(defenderLabel).row()

        addSeparator().pad(0f)

        val attackIcon = Fonts.rangedStrength
        add(attacker.getAttackingStrength().tr() + attackIcon)
        add("???$attackIcon").row()

        val attackerModifiers =
                BattleDamage.getAirSweepAttackModifiers(attacker).map {
                    getModifierTable(it.key, it.value)
                }

        for (modifier in attackerModifiers) {
            add(modifier)
            add().width(quarterScreen)
            row().pad(2f)
        }

        add(getHealthBar(attacker.getMaxHealth(), attacker.getHealth(), attacker.getHealth(),attacker.getHealth()))
        add(getHealthBar(attacker.getMaxHealth(), attacker.getMaxHealth(), attacker.getMaxHealth(), attacker.getMaxHealth()))
        row().pad(5f)

        val attackButton = "Air Sweep".toTextButton().apply { color = Color.RED }

        val canReach = attacker.unit.currentTile.getTilesInDistance(attacker.unit.getRange()).contains(targetTile)

        if (!worldScreen.isPlayersTurn || !attacker.canAttack() || !canReach || !canAttack) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }
        else {
            attackButton.onClick(attacker.getAttackSound()) {
                AirInterception.airSweep(attacker, targetTile)
                worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        }

        add(attackButton).colspan(2)

        pack()

        setPosition(worldScreen.stage.width / 2 - width / 2, 5f)
    }
}
