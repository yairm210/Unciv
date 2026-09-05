package com.unciv.ui.screens.worldscreen.worldmap

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackParticipant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.recordAttackForTesting
import com.unciv.ui.components.MapArrowType
import com.unciv.ui.components.MiscArrowTypes
import com.unciv.ui.components.UnitMovementMemoryType
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.ui.components.tilegroups.layers.OverlayMapLayer
import com.unciv.ui.components.tilegroups.layers.TileLayerTerrain
import com.unciv.ui.components.tilegroups.layers.UnitFlagMapLayer
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.unit.UnitTable
import com.unciv.view.GameView
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.anyFloat

@RunWith(BaseTestRunner::class)
class WorldMapMovementOverlayTest {
    private lateinit var game: TestGame
    private lateinit var selectedCiv: Civilization
    private lateinit var enemy: Civilization
    private lateinit var restrictedView: GameView
    private lateinit var spectatorView: GameView

    private data class Arrow(val from: HexCoord, val to: HexCoord, val type: MapArrowType)
    private data class Marker(val position: HexCoord, val isTarget: Boolean)

    @Before
    fun setUp() {
        game = TestGame()
        game.makeHexagonalMap(4)
        selectedCiv = game.addCiv(isPlayer = true)
        enemy = game.addCiv()
        val spectator = game.addCiv(game.ruleset.nations[Constants.spectator]!!, isPlayer = true)
        spectator.viewableTiles = game.tileMap.values.toSet()
        spectator.viewableInvisibleUnitsTiles = spectator.viewableTiles
        restrictedView = GameView(game.gameInfo, selectedCiv, spectatorMode = true)
        spectatorView = GameView(game.gameInfo, spectator, spectatorMode = true)
    }

    @Test
    fun foreignMovementHistoryIsHiddenOnExploredTilesOutsideCurrentVision() {
        unitWithHistory(enemy, HexCoord(-1, 0), HexCoord.Zero)
        for (tile in game.tileMap.values) tile.setExplored(selectedCiv, true)
        selectedCiv.viewableTiles = emptySet()

        assertTrue(drawOverlay(restrictedView).isEmpty())

        selectedCiv.viewableTiles = game.tileMap.values.toSet()
        assertEquals(setOf(
            Arrow(HexCoord(-1, 0), HexCoord.Zero, UnitMovementMemoryType.UnitMoved)
        ), drawOverlay(restrictedView).toSet())
    }

    @Test
    fun spectatorArrowsIncludeEndpointsUnexploredBySelectedCivilization() {
        unitWithHistory(enemy, HexCoord(-1, 0), HexCoord.Zero)
        recordAttack(HexCoord.Zero, HexCoord(1, 0))
        recordAttack(HexCoord(2, 0), HexCoord(2, 1))
        assertFalse(game.getTile(HexCoord.Zero).isExplored(selectedCiv))
        assertTrue(drawOverlay(restrictedView).isEmpty())

        assertEquals(setOf(
            Arrow(HexCoord(-1, 0), HexCoord.Zero, UnitMovementMemoryType.UnitMoved),
            Arrow(HexCoord.Zero, HexCoord(1, 0), MiscArrowTypes.UnitHasAttacked),
            Arrow(HexCoord(2, 0), HexCoord(2, 1), MiscArrowTypes.UnitHasAttacked)
        ), drawOverlay(spectatorView).toSet())
    }

    @Test
    fun selectedUnitStillFiltersHistoryWhenMapPerspectiveChanges() {
        val selectedUnit = unitWithHistory(selectedCiv, HexCoord(-1, 0), HexCoord.Zero)
        recordAttack(HexCoord.Zero, HexCoord(0, 1))
        unitWithHistory(enemy, HexCoord(2, 0), HexCoord(3, 0))
        recordAttack(HexCoord(3, 0), HexCoord(3, 1))
        recordAttack(HexCoord(3, 0), HexCoord.Zero)

        assertEquals(setOf(
            Arrow(HexCoord(-1, 0), HexCoord.Zero, UnitMovementMemoryType.UnitMoved),
            Arrow(HexCoord.Zero, HexCoord(0, 1), MiscArrowTypes.UnitHasAttacked),
            Arrow(HexCoord(3, 0), HexCoord.Zero, MiscArrowTypes.UnitHasAttacked)
        ), drawOverlay(spectatorView, restrictedView.getMapUnitView(selectedUnit)).toSet())
    }

    @Test
    fun spectatorPerspectiveKeepsPlannedRoutesForSelectedCivilization() {
        val selectedUnit = game.addUnit("Warrior", selectedCiv, game.getTile(0, 0))
        selectedUnit.action = "moveTo 1,0"
        val otherOwnUnit = game.addUnit("Warrior", selectedCiv, game.getTile(-3, 0))
        otherOwnUnit.action = "moveTo -2,0"
        val enemyUnit = game.addUnit("Warrior", enemy, game.getTile(3, 0))
        enemyUnit.action = "moveTo 3,1"

        assertEquals(setOf(
            Arrow(HexCoord.Zero, HexCoord(1, 0), MiscArrowTypes.UnitMoving),
            Arrow(HexCoord(-3, 0), HexCoord(-2, 0), MiscArrowTypes.UnitMoving)
        ), drawOverlay(spectatorView, restrictedView.getMapUnitView(selectedUnit)).toSet())
    }

    @Test
    fun recordedEndpointsDrawWithoutCurrentVisionOrExploration() {
        val source = HexCoord.Zero
        val target = HexCoord(1, 0)
        recordAttack(source, target, knowsSource = true, knowsTarget = true)
        selectedCiv.viewableTiles = emptySet()
        assertFalse(game.getTile(source).isExplored(selectedCiv))
        assertFalse(game.getTile(target).isExplored(selectedCiv))

        assertEquals(listOf(Arrow(source, target, MiscArrowTypes.UnitHasAttacked)), drawOverlay(restrictedView))
    }

    @Test
    fun partiallyObservedAttacksDrawOnlyKnownEndpointMarkers() {
        recordAttack(HexCoord.Zero, HexCoord(1, 0), knowsTarget = true)
        recordAttack(HexCoord(2, 0), HexCoord(3, 0), knowsSource = true)
        recordAttack(HexCoord(-2, 0), HexCoord(-3, 0))
        // Seeing either tile later must not turn partial observations into arrows.
        selectedCiv.viewableTiles = game.tileMap.values.toSet()
        val markers = ArrayList<Marker>()

        assertTrue(drawOverlay(restrictedView, markers = markers).isEmpty())
        assertEquals(listOf(Marker(HexCoord(1, 0), true), Marker(HexCoord(2, 0), false)), markers)
    }

    @Test
    fun selectedUnitFiltersMarkersUsingOnlyKnownEndpoints() {
        val selectedUnit = game.addUnit("Warrior", selectedCiv, game.getTile(0, 0))
        recordAttack(HexCoord(1, 0), HexCoord.Zero, knowsTarget = true)
        recordAttack(HexCoord.Zero, HexCoord(2, 0), knowsSource = true)
        recordAttack(HexCoord.Zero, HexCoord(3, 0), knowsTarget = true)
        val markers = ArrayList<Marker>()

        assertTrue(drawOverlay(restrictedView, restrictedView.getMapUnitView(selectedUnit), markers).isEmpty())
        assertEquals(listOf(Marker(HexCoord.Zero, true), Marker(HexCoord.Zero, false)), markers)
    }

    @Test
    fun selectedAttackerKeepsItsOriginalAttackArrowAfterRetreat() {
        val source = HexCoord.Zero
        val target = HexCoord(1, 0)
        val attacker = game.addUnit("Archer", selectedCiv, game.getTile(source))
        game.gameInfo.recordAttackForTesting(MapUnitCombatant(attacker), game.getTile(target))
        attacker.movement.moveToTile(game.getTile(-1, 0))

        val arrows = drawOverlay(restrictedView, restrictedView.getMapUnitView(attacker))

        assertEquals(listOf(Arrow(source, target, MiscArrowTypes.UnitHasAttacked)),
            arrows.filter { it.type == MiscArrowTypes.UnitHasAttacked })
    }

    @Test
    fun selectedDefenderKeepsItsObservedAttackAfterMoving() {
        val source = HexCoord.Zero
        val target = HexCoord(1, 0)
        val attacker = game.addUnit("Archer", enemy, game.getTile(source))
        val defender = game.addUnit("Warrior", selectedCiv, game.getTile(target))
        selectedCiv.viewableTiles = setOf(game.getTile(source), game.getTile(target))
        val attack = game.gameInfo.recordAttackForTesting(MapUnitCombatant(attacker), game.getTile(target))
        attack.targets.add(AttackParticipant(MapUnitCombatant(defender)))
        defender.movement.moveToTile(game.getTile(2, 0))

        val arrows = drawOverlay(restrictedView, restrictedView.getMapUnitView(defender))

        assertEquals(listOf(Arrow(source, target, MiscArrowTypes.UnitHasAttacked)),
            arrows.filter { it.type == MiscArrowTypes.UnitHasAttacked })
    }

    @Test
    fun selectingALaterVisibleAttackerDoesNotIdentifyAnEarlierUnseenAttacker() {
        val attacker = game.addUnit("Archer", enemy, game.getTile(0, 0))
        val target = game.getTile(1, 0)
        selectedCiv.viewableTiles = setOf(target)
        game.gameInfo.recordAttackForTesting(MapUnitCombatant(attacker), target)
        attacker.movement.moveToTile(game.getTile(-1, 0))
        selectedCiv.viewableTiles = game.tileMap.values.toSet()
        val markers = ArrayList<Marker>()
        drawOverlay(restrictedView, markers = markers)
        assertEquals(listOf(Marker(target.position, true)), markers)
        markers.clear()

        val arrows = drawOverlay(restrictedView, restrictedView.getMapUnitView(attacker), markers)

        assertTrue(arrows.none { it.type == MiscArrowTypes.UnitHasAttacked })
        assertTrue(markers.isEmpty())
    }

    @Test
    fun resettingArrowsRemovesAttackOverlaysBeforePerspectiveOrSettingsUpdates() {
        val parent = Group()
        val overlays = arrayListOf<Actor>(Actor(), Actor())
        for (overlay in overlays) parent.addActor(overlay)
        val holder = mock(WorldMapHolder::class.java)
        WorldMapHolder::class.java.getDeclaredField("tileGroups").apply {
            isAccessible = true
            set(holder, HashMap<TileView, WorldTileGroup>())
        }
        WorldMapHolder::class.java.getDeclaredField("attackOverlays").apply {
            isAccessible = true
            set(holder, overlays)
        }
        doCallRealMethod().`when`(holder).resetArrows()

        holder.resetArrows()

        assertEquals(0, parent.children.size)
        assertTrue(overlays.isEmpty())
    }

    @Test
    fun attackOverlaysStayAboveFogAndBelowUnitFlags() {
        val groups = listOf(geometryTileGroup(HexCoord(-2, 0)), geometryTileGroup(HexCoord(2, 0)))
        val map = TileGroupMap(mock(ZoomableScrollPane::class.java), groups)
        val overlay = Actor()

        map.addTileOverlay(overlay, groups.first())

        val fogLayer = map.children.first { it is OverlayMapLayer }
        val flagsLayer = map.children.first { it is UnitFlagMapLayer }
        assertTrue(overlay.zIndex > fogLayer.zIndex)
        assertTrue(overlay.zIndex < flagsLayer.zIndex)
        assertTrue(overlay.remove())
        assertFalse(map.children.contains(overlay, true))
    }

    @Test
    fun attackOverlayWrapsWithItsTileDespiteItsWithinTileOffset() {
        val groups = listOf(geometryTileGroup(HexCoord(-2, 0)), geometryTileGroup(HexCoord(2, 0)))
        val holder = mock(ZoomableScrollPane::class.java)
        `when`(holder.width).thenReturn(50f)
        val map = TileGroupMap(holder, groups, worldWrap = true)
        map.isTransform = false
        val sourceGroup = groups.minBy { it.x }
        val initialSourceX = sourceGroup.x
        val overlay = Actor().apply { setPosition(sourceGroup.x + 30f, sourceGroup.y) }
        map.addTileOverlay(overlay, sourceGroup)
        // Move just five units past the wrap boundary: the tile crosses it before the offset icon does.
        `when`(holder.scrollX).thenReturn(map.width - 20f)

        map.draw(mock(Batch::class.java), 1f)

        assertEquals(initialSourceX + map.width, sourceGroup.x, 0.001f)
        assertEquals(sourceGroup.x + 30f, overlay.x, 0.001f)
    }

    /** Real map/layer geometry with no texture loading or graphics context. */
    private fun geometryTileGroup(position: HexCoord): WorldTileGroup {
        val group = mock(WorldTileGroup::class.java, Answers.RETURNS_DEEP_STUBS)
        val offset = Vector2()
        `when`(group.tileView).thenReturn(restrictedView.getTile(game.getTile(position)))
        `when`(group.tile).thenReturn(game.getTile(position))
        val terrainLayer = TileLayerTerrain(group, 50f)
        `when`(group.layerTerrain).thenReturn(terrainLayer)
        `when`(group.x).thenAnswer { offset.x }
        `when`(group.y).thenAnswer { offset.y }
        doAnswer { offset.set(it.getArgument<Float>(0), it.getArgument<Float>(1)); null }
            .`when`(group).setPosition(anyFloat(), anyFloat())
        doAnswer { offset.add(it.getArgument<Float>(0), it.getArgument<Float>(1)); null }
            .`when`(group).moveBy(anyFloat(), anyFloat())
        doAnswer { offset.x = it.getArgument(0); null }.`when`(group).setX(anyFloat())
        return group
    }

    private fun recordAttack(source: HexCoord, target: HexCoord, knowsSource: Boolean = false, knowsTarget: Boolean = false) {
        game.gameInfo.attackEventsForTesting.add(AttackEvent().apply {
            this.source = source
            this.target = target
            if (knowsSource) this.knowsSource.add(selectedCiv.civID)
            if (knowsTarget) this.knowsTarget.add(selectedCiv.civID)
        })
    }

    private fun unitWithHistory(civ: Civilization, from: HexCoord, to: HexCoord): MapUnit {
        val unit = game.addUnit("Warrior", civ, game.getTile(to))
        unit.movementMemories = arrayListOf(MapUnit.UnitMovementMemory(from, UnitMovementMemoryType.UnitMoved))
        return unit
    }

    /** Run the real overlay method, intercepting drawing so the test needs no graphics context. */
    private fun drawOverlay(gameView: GameView, selectedUnit: MapUnitView? = null, markers: MutableList<Marker> = ArrayList()): List<Arrow> {
        val unitTable = mock(UnitTable::class.java)
        `when`(unitTable.selectedUnit).thenReturn(selectedUnit)
        val screen = mock(WorldScreen::class.java) { invocation ->
            if (invocation.method.name.startsWith("getBottomUnitTable")) unitTable
            else Answers.RETURNS_DEFAULTS.answer(invocation)
        }
        `when`(screen.selectedGameView).thenReturn(restrictedView)
        val arrows = ArrayList<Arrow>()
        val holder = mock(WorldMapHolder::class.java) { invocation ->
            if (invocation.method.name == "addArrow") {
                val from = invocation.getArgument<TileView>(0).position()
                val to = invocation.getArgument<TileView>(1).position()
                // Zero-length segments recorded when a unit has not moved have no visible arrow.
                if (from != to) arrows.add(Arrow(from, to, invocation.getArgument(2)))
                null
            } else if (invocation.method.name == "addAttackArrow") {
                arrows.add(Arrow(invocation.getArgument(0), invocation.getArgument(1), MiscArrowTypes.UnitHasAttacked))
                null
            } else if (invocation.method.name == "addAttackMarker") {
                markers.add(Marker(invocation.getArgument(0), invocation.getArgument(1)))
                null
            } else Answers.RETURNS_DEFAULTS.answer(invocation)
        }
        WorldMapHolder::class.java.getDeclaredField("worldScreen").apply {
            isAccessible = true
            set(holder, screen)
        }
        val plannedUnits = restrictedView.civView.getUnits().asSequence()
        val updateMovementOverlay = WorldMapHolder::class.java.methods.single {
            it.name.startsWith("updateMovementOverlay") && it.parameterCount == 2
        }
        updateMovementOverlay.invoke(doCallRealMethod().`when`(holder), gameView, plannedUnits)
        updateMovementOverlay.invoke(holder, gameView, plannedUnits)
        return arrows
    }
}
