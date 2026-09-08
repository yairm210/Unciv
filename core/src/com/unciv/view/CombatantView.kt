package com.unciv.view

import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.UnitType
import yairm210.purity.annotations.Readonly

/**
 * View of an [ICombatant] from the perspective of [viewer] via [gameView]. Get one via
 * [ForeignMapUnitView.asCombatant] or [ForeignCityView.asCombatant] - which return the richer
 * [MapUnitCombatantView]/[CityCombatantView] subclasses when the underlying view carries enough
 * information to also dispatch battle actions (movement, nuke, air sweep, bombard).
 */
sealed class CombatantView protected constructor(private val combatant: ICombatant, viewer: Civilization, spectatorMode: Boolean = false,
                    gameView: GameView) : GameBasedView<ICombatant>(combatant, viewer, spectatorMode, gameView) {

    @Readonly fun getCivInfo(): ForeignCivView = gameView.getForeignCivView(combatant.getCivInfo())
    @Readonly fun getTile(): TileView = gameView.getTile(combatant.getTile())
    /** The underlying unit for rendering purposes (icons, sprites) - `null` for cities. Deliberately not [ICombatant]. */
    @Readonly fun getMapUnitOrNull(): MapUnit? = (combatant as? MapUnitCombatant)?.unit

    // Named to avoid clashing with the JVM getter of the `name` property some view classes already expose
    @Readonly fun getCombatantName(): String = combatant.getName()
    @Readonly fun getHealth(): Int = combatant.getHealth()
    @Readonly fun getMaxHealth(): Int = combatant.getMaxHealth()
    @Readonly fun getUnitType(): UnitType = combatant.getUnitType()
    @Readonly fun getAttackingStrength(defender: CombatantView? = null): Int = combatant.getAttackingStrength(defender?.unwrap())
    @Readonly fun getDefendingStrength(attacker: CombatantView? = null): Int = combatant.getDefendingStrength(attacker?.unwrap())
    @Readonly fun isDefeated(): Boolean = combatant.isDefeated()
    @Readonly fun canAttack(): Boolean = combatant.canAttack()
    @Readonly fun getAttackSound(): UncivSound = combatant.getAttackSound()

    @Readonly fun isRanged(): Boolean = combatant.isRanged()
    @Readonly fun isAirUnit(): Boolean = combatant.isAirUnit()
    @Readonly fun isCity(): Boolean = combatant.isCity()
    @Readonly fun isCivilian(): Boolean = combatant.isCivilian()
    @Readonly fun isEmbarked(): Boolean = combatant is MapUnitCombatant && combatant.unit.isEmbarked()
    @Readonly fun hasUnique(uniqueType: UniqueType): Boolean = combatant is MapUnitCombatant && combatant.hasUnique(uniqueType)
    @Readonly fun hasReachedMaxXPFromBarbarians(): Boolean =
        combatant is MapUnitCombatant && combatant.unit.promotions.totalXpProduced() >= combatant.unit.civ.gameInfo.ruleset.modOptions.constants.maxXPfromBarbarians

    // Battle math - identical for unit and city combatants
    @Readonly fun getAttackModifiers(defender: CombatantView, tileToAttackFrom: TileView): Map<String, Int> =
        BattleDamage.getAttackModifiers(combatant, defender.unwrap(), tileToAttackFrom.getTile())
    @Readonly fun getDefenceModifiers(attacker: CombatantView, tileToAttackFrom: TileView): Map<String, Int> =
        BattleDamage.getDefenceModifiers(attacker.unwrap(), combatant, tileToAttackFrom.getTile())
    /** Attacking strength including modifiers - as opposed to [getAttackingStrength], which is the base strength. */
    @Readonly fun getFinalAttackingStrength(defender: CombatantView, tileToAttackFrom: TileView): Float =
        BattleDamage.getAttackingStrength(combatant, defender.unwrap(), tileToAttackFrom.getTile())
    /** Defending strength including modifiers - as opposed to [getDefendingStrength], which is the base strength. */
    @Readonly fun getFinalDefendingStrength(attacker: CombatantView, tileToAttackFrom: TileView): Float =
        BattleDamage.getDefendingStrength(attacker.unwrap(), combatant, tileToAttackFrom.getTile())
    @Readonly fun calculateDamageToDefender(defender: CombatantView, tileToAttackFrom: TileView, randomnessFactor: Float): Int =
        BattleDamage.calculateDamageToDefender(combatant, defender.unwrap(), tileToAttackFrom.getTile(), randomnessFactor)
    @Readonly fun calculateDamageToAttacker(defender: CombatantView, tileToAttackFrom: TileView, randomnessFactor: Float): Int =
        BattleDamage.calculateDamageToAttacker(combatant, defender.unwrap(), tileToAttackFrom.getTile(), randomnessFactor)
    /** (max, min) bonus damage dealt to [defender] from an additional [UniqueType.ExtraRangedAttack] - `(0, 0)` if not applicable. */
    @Readonly fun getExtraRangedAttackBonusDamage(defender: CombatantView, tileToAttackFrom: TileView): Pair<Int, Int> {
        if (combatant !is MapUnitCombatant) return 0 to 0
        val defenderCombatant = defender.unwrap()
        var maxExtra = 0
        var minExtra = 0
        for (unique in combatant.unit.getMatchingUniques(UniqueType.ExtraRangedAttack)) {
            val baseRangedStrengthForExtraAttack = (combatant.unit.baseUnit.strength * unique.params[0].toFloat() / 100).toInt()
            val fakeAttacker = Battle.FakeUnitForExtraRangedAttack(combatant, baseRangedStrengthForExtraAttack)
            maxExtra += BattleDamage.calculateDamageToDefender(fakeAttacker, defenderCombatant, tileToAttackFrom.getTile(), 1f)
            minExtra += BattleDamage.calculateDamageToDefender(fakeAttacker, defenderCombatant, tileToAttackFrom.getTile(), 0f)
        }
        return maxExtra to minExtra
    }

    /** Builds the [AttackableTileView] a city bombard needs (no movement, so no tileToAttackFrom calculation required). */
    @Readonly fun buildBombardAttackableTile(
        fromTile: TileView, toTile: TileView, defender: CombatantView,
        viewer: Civilization, spectatorMode: Boolean, gameView: GameView
    ): AttackableTileView =
        AttackableTileView(AttackableTile(fromTile.getTile(), toTile.getTile(), 0f, defender.unwrap()), viewer, spectatorMode, gameView)
}

/** A [CombatantView] of a unit - carries the full [MapUnitView], so battle actions (movement, attack, nuke, air sweep) can be dispatched directly. */
class MapUnitCombatantView internal constructor(
    private val unitView: MapUnitView, viewer: Civilization, spectatorMode: Boolean = false, gameView: GameView
) : CombatantView(MapUnitCombatant(unitView.getUnit()), viewer, spectatorMode, gameView) {
    @Readonly fun getUnitView(): MapUnitView = unitView
}

/** A [CombatantView] of a city - carries the full [ForeignCityView], so battle actions (bombard) can be dispatched directly. */
class CityCombatantView internal constructor(
    private val cityView: ForeignCityView, viewer: Civilization, spectatorMode: Boolean = false, gameView: GameView
) : CombatantView(CityCombatant(cityView.getCity()), viewer, spectatorMode, gameView) {
    @Readonly fun getCityView(): ForeignCityView = cityView
}
