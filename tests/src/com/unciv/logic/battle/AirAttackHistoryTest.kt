package com.unciv.logic.battle

import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.view.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AirAttackHistoryTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv()
    private val defendingCiv = testGame.addCiv()
    private val attackerBase = testGame.addCity(attackingCiv, testGame.getTile(-5, 0)).getCenterTile()
    private val interceptorBase = testGame.addCity(defendingCiv, testGame.getTile(5, 0)).getCenterTile()
    private val target = testGame.getTile(1, 0)

    init {
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        attackingCiv.getDiplomacyManager(defendingCiv)!!.declareWar()
    }

    @Test
    fun `normal interception identifies which unit damaged the bomber independently of its target`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, interceptorBase)
        interceptor.instanceName = "Air guard"

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        val event = game.attackEventsForTesting.single()
        val interception = event.interceptions.single()
        val record = interception.interceptor!!
        assertTrue(interception.intercepted)
        assertEquals(interceptor.id, record.unitId)
        assertEquals("Air guard", record.instanceName)
        assertEquals(defendingCiv.civID, record.civId)
        assertEquals(interceptorBase.position, record.position)
        assertEquals(100, record.healthBefore)
        assertEquals(100, record.healthAfter)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
        assertEquals(0, record.damageReceived)
        assertEquals(0, interception.damageToInterceptor)
        assertTrue(interception.damageToAttacker > 0)
        assertEquals(100 - bomber.health, event.attacker!!.damageReceived)
        assertTrue(event.attacker!!.damageReceived >= interception.damageToAttacker)
        assertEquals(defender.id, event.targets.single().unitId)
        assertEquals(100 - defender.health, event.targets.single().damageReceived)
        assertEquals(1, interceptor.attacksThisTurn)
    }

    @Test
    fun `fatal interception records the interceptor and leaves the intended target unharmed`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase).apply { health = 1 }
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, interceptorBase)

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        val event = game.attackEventsForTesting.single()
        assertEquals(AttackResolution.Intercepted, event.resolution)
        assertEquals(AttackParticipantOutcome.Destroyed, event.attacker!!.outcome)
        assertEquals(interceptor.id, event.interceptions.single().interceptor!!.unitId)
        assertEquals(1, event.interceptions.single().damageToAttacker)
        assertEquals(1, event.attacker!!.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, event.targets.single().outcome)
        assertEquals(0, event.targets.single().damageReceived)
        assertEquals(100, defender.health)
    }

    @Test
    fun `missed interception still records the consumed attempt without claiming damage`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, interceptorBase).apply {
            promotions.addPromotion(testGame.createUnitPromotion("[-99]% chance to intercept air attacks").name)
        }
        assertEquals(1, interceptor.interceptChance())
        // Choose a real combat state whose deterministic roll misses; do not mock interception.
        val context = GameContext(MapUnitCombatant(bomber), MapUnitCombatant(defender), target, CombatAction.Intercept)
        while (context.stateBasedRandom("AirInterception.tryInterceptAirAttack").nextFloat() <= 0.01f)
            game.turns++

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        val event = game.attackEventsForTesting.single()
        val interception = event.interceptions.single()
        assertFalse(interception.intercepted)
        assertEquals(0, interception.damageToAttacker)
        assertEquals(0, interception.damageToInterceptor)
        assertEquals(interceptor.id, interception.interceptor!!.unitId)
        assertEquals(AttackParticipantOutcome.Survived, interception.interceptor!!.outcome)
        assertEquals(1, interceptor.attacksThisTurn)
        // The intended target may still damage the bomber during its normal attack.
        assertEquals(100 - bomber.health, event.attacker!!.damageReceived)
        assertEquals(AttackResolution.Completed, event.resolution)
    }

    @Test
    fun `sweep without an interceptor records its completed mission and no incidental target`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val groundUnit = testGame.addUnit("Warrior", defendingCiv, target)

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        val event = game.attackEventsForTesting.single()
        assertEquals(AttackKind.AirSweep, event.kind)
        assertEquals(AttackResolution.Completed, event.resolution)
        assertEquals(attackerBase.position, event.source)
        assertEquals(target.position, event.target)
        assertEquals(sweeper.id, event.attacker!!.unitId)
        assertEquals(AttackParticipantOutcome.Survived, event.attacker!!.outcome)
        assertEquals(0, event.attacker!!.damageReceived)
        assertTrue(event.interceptions.isEmpty())
        assertTrue(event.targets.isEmpty())
        assertEquals(100, groundUnit.health)
        assertEquals(1, sweeper.attacksThisTurn)
        assertEquals(0f, sweeper.currentMovement)
    }

    @Test
    fun `ground interception of a sweep records both consumed attacks and zero damage`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val interceptor = testGame.addUnit("Anti-Aircraft Gun", defendingCiv, testGame.getTile(3, 0))

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        val event = game.attackEventsForTesting.single()
        val interception = event.interceptions.single()
        assertEquals(AttackKind.AirSweep, event.kind)
        assertEquals(AttackResolution.Completed, event.resolution)
        assertTrue(interception.intercepted)
        assertEquals(interceptor.id, interception.interceptor!!.unitId)
        assertEquals(0, interception.damageToAttacker)
        assertEquals(0, interception.damageToInterceptor)
        assertEquals(0, interception.interceptor!!.damageReceived)
        assertEquals(0, event.attacker!!.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, event.attacker!!.outcome)
        assertEquals(AttackParticipantOutcome.Survived, interception.interceptor!!.outcome)
        assertEquals(1, sweeper.attacksThisTurn)
        assertEquals(1, interceptor.attacksThisTurn)
        assertTrue(event.targets.isEmpty())
    }

    @Test
    fun `air sweep records damage in both directions and either participant's death`() {
        for ((sweeperHealth, interceptorHealth) in listOf(100 to 100, 1 to 100, 100 to 1)) {
            val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase).apply { health = sweeperHealth }
            val interceptor = testGame.addUnit("Fighter", defendingCiv, interceptorBase).apply { health = interceptorHealth }

            AirInterception.airSweep(MapUnitCombatant(sweeper), target)

            val event = game.attackEventsForTesting.last()
            val interception = event.interceptions.single()
            assertEquals(AttackKind.AirSweep, event.kind)
            assertEquals(AttackResolution.Completed, event.resolution)
            assertEquals(sweeperHealth - sweeper.health, event.attacker!!.damageReceived)
            assertEquals(event.attacker!!.damageReceived, interception.damageToAttacker)
            assertEquals(interceptorHealth - interceptor.health, interception.interceptor!!.damageReceived)
            assertEquals(interception.interceptor!!.damageReceived, interception.damageToInterceptor)
            assertEquals(if (sweeper.isDestroyed) AttackParticipantOutcome.Destroyed else AttackParticipantOutcome.Survived,
                event.attacker!!.outcome)
            assertEquals(if (interceptor.isDestroyed) AttackParticipantOutcome.Destroyed else AttackParticipantOutcome.Survived,
                interception.interceptor!!.outcome)
            if (sweeperHealth == 1) assertTrue(sweeper.isDestroyed)
            if (interceptorHealth == 1) assertTrue(interceptor.isDestroyed)
            if (!sweeper.isDestroyed) sweeper.destroy()
            if (!interceptor.isDestroyed) interceptor.destroy()
        }
    }

    @Test
    fun `interceptor snapshots survive save and clone without sharing mutable identities`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, interceptorBase)
        interceptor.instanceName = "Air guard"
        AirInterception.airSweep(MapUnitCombatant(sweeper), target)
        val original = game.attackEventsForTesting.single().interceptions.single()
        val attackerOutcome = original.attackerOutcome
        val interceptorOutcome = original.interceptorOutcome
        val knowsTarget = original.knowsTarget!!.toSet()

        val cloned = game.clone().attackEventsForTesting.single().interceptions.single()
        val saved = json().fromJson(GameInfo::class.java, json().toJson(game))
            .attackEventsForTesting.single().interceptions.single()
        assertNotSame(original, cloned)
        assertNotSame(original.interceptor, cloned.interceptor)
        assertNotSame(original.interceptor!!.knownBy, cloned.interceptor!!.knownBy)
        interceptor.instanceName = "Renamed guard"
        original.interceptor!!.knownBy.clear()
        original.interceptor!!.damageReceived = -1
        original.intercepted = false
        original.damageToAttacker = -1
        original.damageToInterceptor = -1
        original.attackerOutcome = null
        original.interceptorOutcome = null
        original.knowsTarget!!.clear()

        for (copy in listOf(cloned, saved)) {
            assertEquals("Air guard", copy.interceptor!!.instanceName)
            assertEquals(interceptor.id, copy.interceptor!!.unitId)
            assertTrue(defendingCiv.civID in copy.interceptor!!.knownBy)
            assertEquals(100 - interceptor.health, copy.interceptor!!.damageReceived)
            assertEquals(100 - interceptor.health, copy.damageToInterceptor)
            assertTrue(copy.intercepted)
            assertEquals(100 - sweeper.health, copy.damageToAttacker)
            assertEquals(attackerOutcome, copy.attackerOutcome)
            assertEquals(interceptorOutcome, copy.interceptorOutcome)
            assertEquals(knowsTarget, copy.knowsTarget)
        }
    }

    @Test
    fun `nuclear blast can kill its interceptor without changing interception damage attribution`() {
        val bomb = testGame.addUnit("Atomic Bomb", attackingCiv, attackerBase).apply {
            promotions.addPromotion(testGame.createUnitPromotion("Damage taken from interception reduced by [100]%").name)
        }
        testGame.addCity(defendingCiv, target)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, target).apply { health = 1 }

        Nuke.NUKE(MapUnitCombatant(bomb), target)

        assertTrue(interceptor.isDestroyed)
        val event = game.attackEventsForTesting.single()
        val interception = event.interceptions.single()
        assertTrue(interception.intercepted)
        assertEquals(0, interception.damageToAttacker)
        assertEquals(0, interception.damageToInterceptor)
        assertEquals(AttackParticipantOutcome.Destroyed, interception.interceptor!!.outcome)
        assertEquals(AttackParticipantOutcome.Survived, interception.attackerOutcome)
        assertEquals(AttackParticipantOutcome.Survived, interception.interceptorOutcome)
        assertEquals(1, interception.interceptor!!.damageReceived)
        assertEquals(0, interception.interceptor!!.healthAfter)
        val blastVictim = event.targets.single { it.unitId == interceptor.id }
        assertEquals(interception.interceptor!!.damageReceived, blastVictim.damageReceived)
        assertEquals(interception.interceptor!!.outcome, blastVictim.outcome)
    }

    @Test
    fun `sweep history never turns an unseen interceptor base into a known endpoint`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, interceptorBase)
        attackingCiv.viewableTiles = setOf(attackerBase, target)
        defendingCiv.viewableTiles = setOf(interceptorBase, target)

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        val attackView = GameView(game, attackingCiv)
        val defenseView = GameView(game, defendingCiv)
        val attackingObservation = attackView.attackEventsView.getObservedAttacks().single()
        assertEquals(attackerBase.position, attackingObservation.source)
        assertEquals(target.position, attackingObservation.target)
        val defensiveObservation = defenseView.attackEventsView.getObservedAttacks().single()
        assertNull(defensiveObservation.source)
        assertEquals(target.position, defensiveObservation.target)
        assertEquals(listOf(defensiveObservation),
            defenseView.attackEventsView.getObservedAttacks(defenseView.getMapUnitView(interceptor)))

        // Later discovery neither reveals the attacker's origin nor identifies the old interceptor.
        attackingCiv.viewableTiles = setOf(attackerBase, target, interceptorBase)
        defendingCiv.viewableTiles = attackingCiv.viewableTiles
        assertTrue(attackView.attackEventsView.getObservedAttacks(attackView.getMapUnitView(interceptor)).isEmpty())
        assertEquals(listOf(defensiveObservation), defenseView.attackEventsView.getObservedAttacks())
        assertEquals(listOf(attackingObservation), attackView.attackEventsView.getObservedAttacks())
    }
}
