package com.unciv.logic.battle

import com.badlogic.gdx.utils.JsonReader
import com.unciv.Constants
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.recordAttackForTesting
import com.unciv.view.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackDetectionTest {
    private val testGame = TestGame().apply {
        makeHexagonalMap(7)
        for (tile in tileMap.values) setTileTerrain(tile.position, Constants.ocean)
    }
    private val game = testGame.gameInfo
    private val player = testGame.addCiv(testGame.ruleset.nations["Egypt"]!!, isPlayer = true)
    private val enemy = testGame.addCiv(testGame.ruleset.nations["Germany"]!!, isPlayer = true)
    private val source = testGame.getTile(2, 0)
    private val target = testGame.getTile(0, 0)

    init {
        testGame.addCity(player, testGame.setTileTerrain(HexCoord(-6, 0), Constants.grassland))
        testGame.addCity(enemy, testGame.setTileTerrain(HexCoord(6, 0), Constants.grassland))
        // Introductions must precede the current player to avoid the UI tutorial's settings save.
        player.diplomacyFunctions.makeCivilizationsMeet(enemy)
        enemy.getDiplomacyManager(player)!!.declareWar()
        game.currentPlayer = enemy.civID
        game.currentPlayerCiv = enemy
    }

    private fun assertKnown(event: AttackEvent, expected: Boolean) {
        assertEquals(expected, player.civID in event.knowsSource)
        assertEquals(expected, player.civID in event.attacker!!.knownBy)
    }

    private fun locations(notification: Notification): List<HexCoord> =
        JsonReader().parse(json().toJson(notification)).get("actions").map {
            val location = it.get("LocationAction").get("location")
            if (location == null) HexCoord.Zero
            else HexCoord(location.getInt("x", 0), location.getInt("y", 0))
        }

    @Test
    fun `submarine entering detector sight is identified before the observer cache refreshes`() {
        val destroyer = testGame.addUnit("Destroyer", player, target)
        destroyer.instanceName = "AH2 Detector"
        val submarine = testGame.addUnit("Submarine", enemy, testGame.getTile(3, 0))
        player.cache.updateViewableTiles()
        assertTrue(source in destroyer.viewableTiles)
        assertTrue(source in player.viewableTiles)
        assertFalse(source in player.viewableInvisibleUnitsTiles)

        submarine.movement.moveToTile(source)
        assertFalse(source in player.viewableInvisibleUnitsTiles)
        assertTrue(submarine.isInvisible(player)) // Detection, rather than adjacency, is necessary.
        Battle.attack(MapUnitCombatant(submarine), MapUnitCombatant(destroyer))

        val event = game.attackEventsForTesting.single()
        assertKnown(event, true)
        assertEquals(submarine.id, event.attacker!!.unitId)
        assertEquals(source.position, GameView(game, player).attackEventsView.getObservedAttacks().single().source)
        val notification = player.notifications.single { "[AH2 Detector]" in it.text }
        assertEquals(listOf(target.position, source.position), locations(notification))
    }

    @Test
    fun `later detection and reloading do not identify an earlier hidden attack`() {
        testGame.addUnit("Battleship", player, target)
        val submarine = testGame.addUnit("Submarine", enemy, source)
        player.cache.updateViewableTiles()
        assertTrue(source in player.viewableTiles)
        assertFalse(source in player.viewableInvisibleUnitsTiles)
        val event = game.recordAttackForTesting(MapUnitCombatant(submarine), target)
        assertKnown(event, false)

        submarine.movement.moveToTile(testGame.getTile(2, 1))
        testGame.addUnit("Destroyer", player, testGame.getTile(1, 1))
        player.cache.updateViewableTiles()
        assertTrue(submarine.isVisibleTo(player))
        val view = GameView(game, player)
        assertNull(view.attackEventsView.getObservedAttacks().single().source)
        assertTrue(view.attackEventsView.getObservedAttacks(view.getMapUnitView(submarine)).isEmpty())
        assertKnown(event, false)

        val restored = json().fromJson(GameInfo::class.java, json().toJson(game))
        restored.ruleset = game.ruleset
        val restoredPlayer = restored.civilizations.single { it.civID == player.civID }
        restoredPlayer.gameInfo = restored
        restoredPlayer.nation = player.nation
        assertKnown(restored.attackEventsForTesting.single(), false)
        assertNull(GameView(restored, restoredPlayer).attackEventsView.getObservedAttacks().single().source)
    }

    @Test
    fun `destroying the detector in combat preserves the source it identified beforehand`() {
        val destroyer = testGame.addUnit("Destroyer", player, target)
        destroyer.instanceName = "AH2 Detector"
        destroyer.health = 1
        val submarine = testGame.addUnit("Submarine", enemy, testGame.getTile(3, 0))
        player.cache.updateViewableTiles()
        submarine.movement.moveToTile(source)
        assertFalse(source in player.viewableInvisibleUnitsTiles)

        Battle.attack(MapUnitCombatant(submarine), MapUnitCombatant(destroyer))

        assertTrue(destroyer.isDestroyed)
        assertFalse(source in player.viewableTiles)
        val witnessed = game.attackEventsForTesting.single()
        assertKnown(witnessed, true)
        val notification = player.notifications.single { "[AH2 Detector]" in it.text }
        assertEquals(listOf(target.position, source.position), locations(notification))
        assertEquals(source.position, GameView(game, player).attackEventsView.getObservedAttacks().single().source)

        val unwitnessed = game.recordAttackForTesting(MapUnitCombatant(submarine), target)
        assertKnown(unwitnessed, false)
        assertKnown(witnessed, true)
    }

    @Test
    fun `a previously matching detection filter does not identify a healed invisible unit`() {
        val detector = testGame.addUnit("Battleship", player, target)
        detector.promotions.addPromotion(testGame.createUnitPromotion("Can see invisible [Wounded] units").name)
        val submarine = testGame.addUnit("Submarine", enemy, source)
        submarine.health = 50
        player.cache.updateViewableTiles()
        assertTrue(source in player.viewableInvisibleUnitsTiles)

        submarine.healBy(50)
        assertEquals(100, submarine.health)
        assertTrue(source in player.viewableInvisibleUnitsTiles)
        assertTrue(submarine.isInvisible(player))
        val event = game.recordAttackForTesting(MapUnitCombatant(submarine), target)

        assertKnown(event, false)
        assertNull(GameView(game, player).attackEventsView.getObservedAttacks().single().source)
    }

    @Test
    fun `an inactive detection conditional cannot reuse an earlier positive cache entry`() {
        val detector = testGame.addUnit("Battleship", player, target)
        detector.promotions.addPromotion(testGame.createUnitPromotion(
            "Can see invisible [Submarine] units <when above [2] movement>"
        ).name)
        val submarine = testGame.addUnit("Submarine", enemy, source)
        player.cache.updateViewableTiles()
        assertTrue(source in player.viewableInvisibleUnitsTiles)
        val witnessed = game.recordAttackForTesting(MapUnitCombatant(submarine), target)
        assertKnown(witnessed, true)

        detector.useMovementPoints(detector.currentMovement - 2f)
        assertEquals(2f, detector.currentMovement)
        assertTrue(source in detector.viewableTiles)
        assertTrue(source in player.viewableInvisibleUnitsTiles)
        val unwitnessed = game.recordAttackForTesting(MapUnitCombatant(submarine), target)

        assertKnown(unwitnessed, false)
        assertKnown(witnessed, true)
        assertNull(GameView(game, player).attackEventsView.getObservedAttacks().last().source)
    }

    @Test
    fun `a submarine defender moving into detector sight is identified in its participant record`() {
        testGame.addUnit("Destroyer", player, target)
        val battleship = testGame.addUnit("Battleship", player, testGame.getTile(0, 1))
        val submarine = testGame.addUnit("Submarine", enemy, testGame.getTile(3, 0))
        player.cache.updateViewableTiles()
        submarine.movement.moveToTile(source)
        assertTrue(source in player.viewableTiles)
        assertFalse(source in player.viewableInvisibleUnitsTiles)

        Battle.attack(MapUnitCombatant(battleship), MapUnitCombatant(submarine))

        val participant = game.attackEventsForTesting.single().targets.single()
        assertEquals(submarine.id, participant.unitId)
        assertTrue(player.civID in participant.knownBy)
    }
}
