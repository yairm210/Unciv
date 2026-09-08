package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.battle.MapUnitCombatant
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
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.clearUndoCheckpoints
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.battleAnimationDeferred
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.getHealthBar
import com.unciv.view.AttackableTileView
import com.unciv.view.ForeignCityView
import com.unciv.view.ForeignMapUnitView
import com.unciv.view.ICombatantView
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
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
        val attackerView = tryGetAttacker() ?: return hide()
        when {
            attackerView is MapUnitView && attackerView.isNuclearWeapon() -> {
                val selectedTileView = worldScreen.mapHolder.selectedTile
                    ?: return hide() // no selected tile
                if (selectedTileView == attackerView.getTile()) return hide() // mayUseNuke would test this again, but not actually seeing the nuke-yourself table just by selecting the nuke is nicer
                simulateNuke(MapUnitCombatant(attackerView.getUnit()), selectedTileView)
            }
            attackerView is MapUnitView && attackerView.isPreparingAirSweep() -> {
                val selectedTileView = worldScreen.mapHolder.selectedTile
                    ?: return hide() // no selected tile
                simulateAirsweep(MapUnitCombatant(attackerView.getUnit()), selectedTileView)
            }
            else -> {
                val defenderView = tryGetDefender() ?: return hide()
                if (attackerView.isCity() && defenderView.isCity()) return hide()
                // This seems inefficient as the tileToAttack is already known - but the method also calculates tileToAttackFrom
                val tileToAttackFromView = if (attackerView is MapUnitView)
                    attackerView.getAttackableEnemies(attackerView.getUnit().movement.getDistanceToTiles())
                        .firstOrNull { it.getTileToAttack() == defenderView.getTile() }?.getTileToAttackFrom()
                        ?: attackerView.getTile()
                else attackerView.getTile()
                simulateBattle(attackerView, defenderView, tileToAttackFromView)
            }
        }

        isVisible = true
        pack()

        // Limit height - will force the reduction down to the modifier ScrollPanes
        // Calculating a cell maxHeight for the ScrollPanes before the pack might be faster, but this is simpler
        // Use a larget padding on top (10f) to make sure the addRoundCloseButton isn't clipped
        if (height > stage.height - 15f) {
            height = stage.height - 15f
            validate()
        }

        addBorderAllowOpacity(2f, Color.WHITE)
        addRoundCloseButton(this) {
            isVisible = false
        }

        setPosition(stage.width / 2 - width / 2, 5f)
    }

    @Readonly
    private fun tryGetAttacker(): ICombatantView? {
        val unitTable = worldScreen.bottomUnitTable
        return if (unitTable.selectedUnit != null
                && !unitTable.selectedUnit!!.isCivilian()
                && !unitTable.selectedUnit!!.hasUnique(UniqueType.CannotAttack))  // purely cosmetic - hide battle table
                    unitTable.selectedUnit
        else unitTable.selectedCity // null if no attacker
    }

    @Readonly
    private fun tryGetDefender(): ICombatantView? {
        val selectedTileView = worldScreen.mapHolder.selectedTile ?: return null // no selected tile
        return tryGetDefenderAtTile(selectedTileView, false)
    }

    @Readonly
    private fun tryGetDefenderAtTile(selectedTileView: TileView, includeFriendly: Boolean): ICombatantView? {
        val defenderView = selectedTileView.getCombatant() ?: return null // no visible combatant in tile
        val attackerCiv = worldScreen.selectedGameView.civView.getCiv()

        if (!includeFriendly && defenderView.getCivInfo().getCiv() == attackerCiv)
            return null  // no enemy combatant in tile

        return defenderView
    }

    private fun getIcon(combatantView: ICombatantView) =
        if (combatantView is ForeignMapUnitView) UnitIconGroup(combatantView.getUnit(), 25f)
        else ImageGetter.getNationPortrait(combatantView.getCivInfo().getCiv().nation, 25f)

    private val quarterScreen = worldScreen.stage.width / 4

    private fun getModifierTable(key: String, value: Int) = Table().apply {
        val description = if (key.startsWith("vs "))
            ("vs [" + key.drop(3) + "]").tr()
        else key.tr()
        val percentage = (if (value > 0) "+" else "") + value + "%"
        val upOrDownLabel = if (value > 0f) "⬆".toLabel(Color.GREEN) // U+2B06 UPWARDS BLACK ARROW
        else "⬇".toLabel(Color.RED) // U+2B07 DOWNWARDS BLACK ARROW

        add(upOrDownLabel)
        val modifierLabel = "$percentage $description".toLabel(fontSize = 14).apply { wrap = true }
        add(modifierLabel).width(quarterScreen - upOrDownLabel.minWidth)
    }

    private fun createModifiersScroll(modifiers: Iterable<Table>): ScrollPane {
        val wrapper = Table()
        wrapper.defaults().pad(2.5f)
        wrapper.top()
        for (modifier in modifiers)
            wrapper.add(modifier).row()
        return AutoScrollPane(wrapper, skin).apply {
            fadeScrollBars = false
            setScrollbarsVisible(true)
            setOverscroll(false, false)
            setScrollingDisabled(true, false)
        }
    }

    private fun simulateBattle(attacker: ICombatantView, defender: ICombatantView, tileToAttackFromView: TileView) {
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getCombatantName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val defenderNameWrapper = Table()
        val defenderLabel = Label(defender.getCombatantName().tr(hideIcons = true), skin)
        defenderNameWrapper.add(getIcon(defender)).padRight(5f)

        defenderNameWrapper.add(defenderLabel)
        add(defenderNameWrapper).row()

        addSeparator().pad(0f)

        val attackIcon = if (attacker.isRanged()) Fonts.rangedStrength else Fonts.strength
        val defenceIcon =
            if (attacker.isRanged() && defender.isRanged() && !defender.isCity() && !(defender is ForeignMapUnitView && defender.isEmbarked()))
                Fonts.rangedStrength
            else Fonts.strength // use strength icon if attacker is melee, defender is melee, defender is a city, or defender is embarked
        add(attacker.getAttackingStrength(defender).tr() + attackIcon)
        add(defender.getDefendingStrength(attacker).tr() + defenceIcon).row()

        val attackerModifiers =
                attacker.getAttackModifiers(defender, tileToAttackFromView).map {
                    getModifierTable(it.key, it.value)
                }
        val defenderModifiers =
                if (!defender.isCity())
                    defender.getDefenceModifiers(attacker, tileToAttackFromView).map {
                        getModifierTable(it.key, it.value)
                    }
                else listOf()

        add(createModifiersScroll(attackerModifiers)).pad(0f).uniformX().fillY()
        add(createModifiersScroll(defenderModifiers)).pad(0f).uniformX().fillY().row()

        if (attackerModifiers.any() || defenderModifiers.any()) {
            addSeparator()
            val attackerStrength = attacker.getFinalAttackingStrength(defender, tileToAttackFromView).roundToInt()
            val defenderStrength = defender.getFinalDefendingStrength(attacker, tileToAttackFromView).roundToInt()
            add(attackerStrength.tr() + attackIcon)
            add(defenderStrength.tr() + attackIcon).row()
        }

        // from Battle.addXp(), check for can't gain more XP from Barbarians
        if (attacker is MapUnitView && attacker.hasReachedMaxXPFromBarbarians()
                && defender.getCivInfo().getCiv().isBarbarian
        ) {
            add("Cannot gain more XP from Barbarians".toLabel(fontSize = 16).apply { wrap = true }).width(quarterScreen)
            row()
        }

        if (attacker.isMelee() &&
                (defender.isCivilian() || defender.isCity() && defender.isDefeated())) {
            add()
            val defeatedText = when {
                !defender.isCivilian() -> "Occupied!"
                (defender as ForeignMapUnitView).hasUnique(UniqueType.Uncapturable) -> ""
                else -> "Captured!"
            }
            add(defeatedText.toLabel())
        } else {
            var maxDamageToDefender = attacker.calculateDamageToDefender(defender, tileToAttackFromView, 1f)
            var minDamageToDefender = attacker.calculateDamageToDefender(defender, tileToAttackFromView, 0f)

            val maxDamageToAttacker = attacker.calculateDamageToAttacker(defender, tileToAttackFromView, 1f)
            val minDamageToAttacker = attacker.calculateDamageToAttacker(defender, tileToAttackFromView, 0f)

            if (attacker is MapUnitView && defender is ForeignMapUnitView && attacker.hasUnique(UniqueType.ExtraRangedAttack)) {
                add("Will perform an extra ranged attack".toLabel(fontSize = 16).apply { wrap = true }).width(quarterScreen)
                row()

                val (maxExtraDamageToDefender, minExtraDamageToDefender) = attacker.getExtraRangedAttackDamage(defender, tileToAttackFromView)
                maxDamageToDefender += maxExtraDamageToDefender
                minDamageToDefender += minExtraDamageToDefender
            }

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

            if (minRemainingLifeAttacker == attackerHealth)
                add(attackerHealth.toLabel())
            else if (maxRemainingLifeAttacker == minRemainingLifeAttacker)
                add("$attackerHealth → $maxRemainingLifeAttacker ($avgDamageToAttacker)".toLabel()) // U+2192 RIGHTWARDS ARROW
            else
                add("$attackerHealth → $minRemainingLifeAttacker-$maxRemainingLifeAttacker (~$avgDamageToAttacker)".toLabel())

            if (minRemainingLifeDefender == maxRemainingLifeDefender) add("$defenderHealth → $maxRemainingLifeDefender ($avgDamageToDefender)".toLabel())
            else add("$defenderHealth → $minRemainingLifeDefender-$maxRemainingLifeDefender (~$avgDamageToDefender)".toLabel())
        }

        row().pad(5f)

        if (worldScreen.canChangeState) {
            val attackText: String = if (attacker.isCity()) "Bombard" else "Attack"
            val attackButton = attackText.toTextButton().apply { color = Color.RED }

            var attackableTileView: AttackableTileView? = null

            if (attacker.canAttack()) {
                if (attacker is MapUnitView) {
                    attackableTileView = attacker
                        .getAttackableEnemies(attacker.getUnit().movement.getDistanceToTiles())
                        .firstOrNull { it.getTileToAttack() == defender.getTile() }
                } else if (attacker is ForeignCityView) {
                    val canBombard = attacker.getBombardableTiles().contains(defender.getTile())
                    if (canBombard) {
                        val gameView = worldScreen.selectedGameView
                        attackableTileView = AttackableTileView.forBombard(
                            attacker.getTile(), defender.getTile(), defender,
                            gameView.civView.getCiv(), gameView.spectatorMode, gameView
                        )
                    }
                }
            }

            if (!worldScreen.isPlayersTurn || attackableTileView == null) {
                attackButton.disable()
                attackButton.label.color = Color.GRAY
            } else {
                attackButton.onClick(UncivSound.Silent) {  // onAttackButtonClicked will do the sound
                    onAttackButtonClicked(attacker, defender, attackableTileView)
                }
            }

            add(attackButton).colspan(2)
        }
    }

    private fun onAttackButtonClicked(
        attacker: ICombatantView,
        defender: ICombatantView,
        attackableTileView: AttackableTileView
    ) {
        val canStillAttack = attacker !is MapUnitView || attacker.tryMovePreparingAttack(attackableTileView)
        worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
        // There was a direct worldScreen.update() call here, removing its 'private' but not the comment justifying the modifier.
        // My tests (desktop only) show the red-flash animations look just fine without.
        worldScreen.shouldUpdate = true
        worldScreen.clearUndoCheckpoints()
        //Gdx.graphics.requestRendering()  // Use this if immediate rendering is required

        if (!canStillAttack) return
        if (!SoundPlayer.play(UncivSound(attacker.getCombatantName())))
            SoundPlayer.play(attacker.getAttackSound())

        val (damageToDefender, damageToAttacker) = if (attacker is MapUnitView) attacker.attackOrNuke(attackableTileView)
            else (attacker as ForeignCityView).tryBombard(attackableTileView)

        worldScreen.battleAnimationDeferred(attacker, damageToAttacker, defender, damageToDefender)
        if (!attacker.canAttack()) hide()
    }


    private fun simulateNuke(attacker: MapUnitCombatant, targetTileView: TileView) {
        val attackerView = worldScreen.selectedGameView.getMapUnitView(attacker.unit)
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attackerView)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val canNuke = attackerView.mayUseNuke(targetTileView)

        val blastRadius = attacker.unit.getNukeBlastRadius()

        val defenderNameWrapper = Table()
        for (tileView in targetTileView.getVisibleTilesInDistance(blastRadius)) {
            val defender = tryGetDefenderAtTile(tileView, true) ?: continue

            val defenderLabel = defender.getCombatantName().toLabel(hideIcons = true)
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
        } else {
            attackButton.onClick(attacker.getAttackSound()) {
                attackerView.tryNuke(targetTileView)

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
                val targetTileGroup = worldScreen.mapHolder.tileGroups[targetTileView]!!
                nukeCircle.x = targetTileGroup.x
                nukeCircle.y = targetTileGroup.y
                worldScreen.mapHolder.addActorToTileGroupMap(nukeCircle)

                worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        }

        add(attackButton).colspan(2)
    }

    private fun simulateAirsweep(attacker: MapUnitCombatant, targetTileView: TileView) {
        val attackerView = worldScreen.selectedGameView.getMapUnitView(attacker.unit)
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attackerView)).padRight(5f)
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
                attackerView.getAirSweepAttackModifiers().map {
                    getModifierTable(it.key, it.value)
                }

        add(createModifiersScroll(attackerModifiers)).pad(0f).uniformX().fillY()
        add().uniformX().row()

        add(getHealthBar(attacker.getMaxHealth(), attacker.getHealth(), attacker.getHealth(), attacker.getHealth()))
        add(getHealthBar(attacker.getMaxHealth(), attacker.getMaxHealth(), attacker.getMaxHealth(), attacker.getMaxHealth()))
        row().pad(5f)

        val attackButton = "Air Sweep".toTextButton().apply { color = Color.RED }

        val canReach = attacker.unit.currentTile.getTilesInDistance(attacker.unit.getRange()).contains(targetTileView.getTile())

        if (!worldScreen.isPlayersTurn || !attacker.canAttack() || !canReach || !canAttack) {
            attackButton.disable()
            attackButton.label.color = Color.GRAY
        }
        else {
            attackButton.onClick(attacker.getAttackSound()) {
                attackerView.tryAirSweep(targetTileView)
                worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
                worldScreen.shouldUpdate = true
            }
        }

        add(attackButton).colspan(2)
    }
}
