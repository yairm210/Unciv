package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
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
import com.unciv.view.CityCombatantView
import com.unciv.view.CombatantView
import com.unciv.view.MapUnitCombatantView
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
        val attackerUnitView = tryGetAttackerUnit()
        val attackerCityView = if (attackerUnitView == null) worldScreen.bottomUnitTable.selectedCity else null
        if (attackerUnitView == null && attackerCityView == null) return hide()

        if (attackerUnitView != null) {
            val attacker = attackerUnitView.asCombatant()
            if (attackerUnitView.isNuclearWeapon()) {
                val selectedTileView = worldScreen.mapHolder.selectedTile
                    ?: return hide() // no selected tile
                if (selectedTileView == attackerUnitView.getTile()) return hide() // mayUseNuke would test this again, but not actually seeing the nuke-yourself table just by selecting the nuke is nicer
                simulateNuke(attacker, selectedTileView)
            } else if (attackerUnitView.isPreparingAirSweep()) {
                val selectedTileView = worldScreen.mapHolder.selectedTile
                    ?: return hide() // no selected tile
                simulateAirsweep(attacker, selectedTileView)
            } else {
                val defender = tryGetDefender() ?: return hide()
                // This seems inefficient as the tileToAttack is already known - but the method also calculates tileToAttackFrom
                val tileToAttackFromView = attackerUnitView.getAttackableEnemies(attackerUnitView.getUnit().movement.getDistanceToTiles())
                    .firstOrNull { it.getTileToAttack() == defender.getTile() }?.getTileToAttackFrom()
                    ?: attackerUnitView.getTile()
                simulateBattle(attacker, defender, tileToAttackFromView)
            }
        } else {
            val attacker = attackerCityView!!.asCombatant()
            val defender = tryGetDefender() ?: return hide()
            if (defender.isCity()) return hide()
            simulateBattle(attacker, defender, attacker.getTile())
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

    /** The selected unit, if it's eligible to attack (purely cosmetic checks - hide the battle table otherwise). */
    @Readonly
    private fun tryGetAttackerUnit(): MapUnitView? {
        val unit = worldScreen.bottomUnitTable.selectedUnit ?: return null
        if (unit.isCivilian() || unit.hasUnique(UniqueType.CannotAttack)) return null
        return unit
    }

    @Readonly
    private fun tryGetDefender(): CombatantView? {
        val selectedTileView = worldScreen.mapHolder.selectedTile ?: return null // no selected tile
        return tryGetDefenderAtTile(selectedTileView, false)
    }

    @Readonly
    private fun tryGetDefenderAtTile(selectedTileView: TileView, includeFriendly: Boolean): CombatantView? {
        val defenderView = selectedTileView.getCombatant() ?: return null // no visible combatant in tile
        val attackerCivView = worldScreen.selectedGameView.civView

        if (!includeFriendly && defenderView.getCivInfo() == attackerCivView)
            return null  // no enemy combatant in tile

        return defenderView
    }

    private fun getIcon(combatantView: CombatantView) =
        (combatantView as? MapUnitCombatantView)?.let { UnitIconGroup(it.getUnitView(), 25f) }
            ?: ImageGetter.getNationPortrait(combatantView.getCivInfo().getNation(), 25f)

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

    private fun simulateBattle(
        attacker: CombatantView, defender: CombatantView,
        tileToAttackFromView: TileView
    ) {
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
            if (attacker.isRanged() && defender.isRanged() && !defender.isCity() && !(defender is MapUnitCombatantView && defender.getUnitView().isEmbarked()))
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
            add(defenderStrength.tr() + defenceIcon).row()
        }

        // from Battle.addXp(), check for can't gain more XP from Barbarians
        if (attacker is MapUnitCombatantView && attacker.getUnitView().hasReachedMaxXPFromBarbarians() && defender.getCivInfo().isBarbarian()) {
            add("Cannot gain more XP from Barbarians".toLabel(fontSize = 16).apply { wrap = true }).width(quarterScreen)
            row()
        }

        val defenderIsCivilian = defender is MapUnitCombatantView && defender.getUnitView().isCivilian()
        if (!attacker.isRanged() &&
                (defenderIsCivilian || defender.isCity() && defender.isDefeated())) {
            add()
            val defeatedText = when {
                !defenderIsCivilian -> "Occupied!"
                defender.hasUnique(UniqueType.Uncapturable) -> ""
                else -> "Captured!"
            }
            add(defeatedText.toLabel())
        } else {
            var maxDamageToDefender = attacker.calculateDamageToDefender(defender, tileToAttackFromView, 1f)
            var minDamageToDefender = attacker.calculateDamageToDefender(defender, tileToAttackFromView, 0f)

            val maxDamageToAttacker = attacker.calculateDamageToAttacker(defender, tileToAttackFromView, 1f)
            val minDamageToAttacker = attacker.calculateDamageToAttacker(defender, tileToAttackFromView, 0f)

            if (!defender.isCity() && attacker is MapUnitCombatantView && attacker.hasUnique(UniqueType.ExtraRangedAttack)) {
                add("Will perform an extra ranged attack".toLabel(fontSize = 16).apply { wrap = true }).width(quarterScreen)
                row()

                val (maxExtraDamageToDefender, minExtraDamageToDefender) = attacker.getExtraRangedAttackBonusDamage(defender, tileToAttackFromView)
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
                when (attacker) {
                    is MapUnitCombatantView -> {
                        val unitView = attacker.getUnitView().tryGetMapUnitView()!!
                        attackableTileView = unitView
                            .getAttackableEnemies(unitView.getUnit().movement.getDistanceToTiles())
                            .firstOrNull { it.getTileToAttack() == defender.getTile() }
                    }
                    is CityCombatantView -> {
                        val cityView = attacker.getCityView()
                        val canBombard = cityView.getBombardableTiles().contains(defender.getTile())
                        if (canBombard) {
                            val gameView = worldScreen.selectedGameView
                            attackableTileView = attacker.buildBombardAttackableTile(
                                defender.getTile(), defender,
                                gameView.civView.getCiv(), gameView.spectatorMode, gameView
                            )
                        }
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
        attacker: CombatantView,
        defender: CombatantView,
        attackableTileView: AttackableTileView
    ) {
        val canStillAttack = if (attacker is MapUnitCombatantView) attacker.getUnitView().tryGetMapUnitView()!!.tryMovePreparingAttack(attackableTileView) else true
        worldScreen.mapHolder.removeUnitActionOverlay() // the overlay was one of attacking
        // There was a direct worldScreen.update() call here, removing its 'private' but not the comment justifying the modifier.
        // My tests (desktop only) show the red-flash animations look just fine without.
        worldScreen.shouldUpdate = true
        worldScreen.clearUndoCheckpoints()
        //Gdx.graphics.requestRendering()  // Use this if immediate rendering is required

        if (!canStillAttack) return

        if (!SoundPlayer.play(UncivSound(attacker.getCombatantName())))
            SoundPlayer.play(attacker.getAttackSound())

        val (damageToDefender, damageToAttacker) = when (attacker) {
            is MapUnitCombatantView -> attacker.getUnitView().tryGetMapUnitView()!!.attackOrNuke(attackableTileView)
            is CityCombatantView -> attacker.getCityView().tryBombard(attackableTileView)
        }

        worldScreen.battleAnimationDeferred(attacker, damageToAttacker, defender, damageToDefender)
        if (!attacker.canAttack()) hide()
    }


    private fun simulateNuke(attacker: MapUnitCombatantView, targetTileView: TileView) {
        val attackerView = attacker.getUnitView().tryGetMapUnitView()!!
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getCombatantName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val canNuke = attackerView.mayUseNuke(targetTileView)

        val blastRadius = attackerView.getNukeBlastRadius()

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

        if (!worldScreen.isPlayersTurn || !attackerView.canAttack() || !canNuke) {
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

    private fun simulateAirsweep(attacker: MapUnitCombatantView, targetTileView: TileView) {
        val attackerView = attacker.getUnitView().tryGetMapUnitView()!!
        clear()

        val attackerNameWrapper = Table()
        val attackerLabel = attacker.getCombatantName().toLabel(hideIcons = true)
        attackerNameWrapper.add(getIcon(attacker)).padRight(5f)
        attackerNameWrapper.add(attackerLabel)
        add(attackerNameWrapper)

        val canAttack = attackerView.canAttack()

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

        val canReach = attackerView.getTilesInAttackRange().contains(targetTileView)

        if (!worldScreen.isPlayersTurn || !attackerView.canAttack() || !canReach || !canAttack) {
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
