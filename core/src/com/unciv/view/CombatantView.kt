package com.unciv.view

import com.unciv.logic.battle.ICombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.UnitType
import yairm210.purity.annotations.Readonly

/** View of an [ICombatant] from the perspective of [viewer] via [gameView]. */
class CombatantView(private val combatant: ICombatant, viewer: Civilization, spectatorMode: Boolean = false,
                    private val gameView: GameView) : GameBasedView<ICombatant>(combatant, viewer, spectatorMode) {

    // Navigation
    @Readonly fun getCivInfo(): ForeignCivView = ForeignCivView(combatant.getCivInfo(), viewer, spectatorMode)
    @Readonly fun getTile(): TileView = gameView.getTile(combatant.getTile())

    // Data retrieval
    @Readonly fun getName(): String = combatant.getName()
    @Readonly fun getHealth(): Int = combatant.getHealth()
    @Readonly fun getMaxHealth(): Int = combatant.getMaxHealth()
    @Readonly fun getUnitType(): UnitType = combatant.getUnitType()
    @Readonly fun getAttackingStrength(defender: CombatantView? = null): Int = combatant.getAttackingStrength(defender?.unwrap())
    @Readonly fun getDefendingStrength(attacker: CombatantView? = null): Int = combatant.getDefendingStrength(attacker?.unwrap())
    @Readonly fun isDefeated(): Boolean = combatant.isDefeated()
    @Readonly fun isInvisible(to: ForeignCivView): Boolean = combatant.isInvisible(to.unwrap())
    @Readonly fun canAttack(): Boolean = combatant.canAttack()
    @Readonly fun matchesFilter(filter: String, multiFilter: Boolean = true): Boolean = combatant.matchesFilter(filter, multiFilter)
    fun getAttackSound(): UncivSound = combatant.getAttackSound()
    fun getNotificationDisplay(leadingText: String = ""): String = combatant.getNotificationDisplay(leadingText)

    @Readonly fun isMelee(): Boolean = combatant.isMelee()
    @Readonly fun isRanged(): Boolean = combatant.isRanged()
    @Readonly fun isAirUnit(): Boolean = combatant.isAirUnit()
    @Readonly fun isWaterUnit(): Boolean = combatant.isWaterUnit()
    @Readonly fun isLandUnit(): Boolean = combatant.isLandUnit()
    @Readonly fun isCity(): Boolean = combatant.isCity()
    @Readonly fun isCivilian(): Boolean = combatant.isCivilian()

    // TEMP - should be removed once migration ends
    @Readonly fun getCombatant(): ICombatant = combatant
}
