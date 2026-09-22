package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.StretchViewport
import com.unciv.UncivGame
import com.unciv.dev.FontDesktop
import com.unciv.logic.files.UncivFiles
import com.unciv.models.ImmutableColor
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.CityView
import com.unciv.view.ForeignCivView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * The city picker used to read its own actor stage while [CityScreen.updateSync] rebuilt it.
 * An actor loses that stage as soon as it is removed, which happens during screen transitions.
 */
@RunWith(GdxTestRunner::class)
class CityScreenCityPickerTableTest {
    private val stages = ArrayList<Stage>()

    @Before
    fun setUp() {
        ensureUiInitialized()
    }

    @After
    fun disposeStages() {
        for (stage in stages) stage.dispose()
        stages.clear()
    }

    @Test
    fun updateAfterDetachRebuildsCityNameCell() {
        val picker = updatePicker(crampedPortrait = false, multipleCities = false, attached = false)
        val nameCell = cityNameCell(picker)

        assertEquals(STAGE_WIDTH / 4f, nameCell.minWidth, WIDTH_DELTA)
        assertCityNameRebuilt(nameCell)
        assertEmptySideCells(picker)
    }

    @Test
    fun cityNameCellWidthFollowsOwnerStageForEachLayout() {
        for (crampedPortrait in listOf(false, true)) {
            val expected = STAGE_WIDTH / if (crampedPortrait) 3f else 4f
            for (attached in listOf(true, false)) {
                val picker = updatePicker(crampedPortrait, multipleCities = false, attached)
                val nameCell = cityNameCell(picker)
                val where = "crampedPortrait=$crampedPortrait attached=$attached"
                assertEquals(where, expected, nameCell.minWidth, WIDTH_DELTA)
                assertEquals(where, expected, nameCell.prefWidth, WIDTH_DELTA)
                assertEquals(where, expected, nameCell.maxWidth, WIDTH_DELTA)
            }
        }
    }

    @Test
    fun singleAndMultipleCityViewsKeepSideCells() {
        for (attached in listOf(true, false)) {
            val singleCity = updatePicker(crampedPortrait = false, multipleCities = false, attached)
            assertEmptySideCells(singleCity)
            assertCityNameRebuilt(cityNameCell(singleCity))

            val multipleCities = updatePicker(crampedPortrait = true, multipleCities = true, attached)
            assertPagingArrows(multipleCities)
            assertEquals(STAGE_WIDTH / 3f, cityNameCell(multipleCities).minWidth, WIDTH_DELTA)
            assertCityNameRebuilt(cityNameCell(multipleCities))
        }
    }

    private fun updatePicker(crampedPortrait: Boolean, multipleCities: Boolean, attached: Boolean): CityScreenCityPickerTable {
        val stage = newStage()
        val screen = mockCityScreen(stage, crampedPortrait, multipleCities)
        val picker = CityScreenCityPickerTable(screen)
        stage.addActor(picker)
        if (attached) {
            assertSame(stage, picker.stage)
        } else {
            picker.remove()
            assertNull(picker.stage)
        }
        assertSame(stage, screen.stage)
        assertEquals(STAGE_WIDTH, stage.width, WIDTH_DELTA)

        picker.update()
        return picker
    }

    private fun newStage(): Stage {
        val viewport = StretchViewport(STAGE_WIDTH, STAGE_HEIGHT)
        val stage = Stage(viewport, mock(Batch::class.java))
        viewport.update(STAGE_WIDTH.toInt(), STAGE_HEIGHT.toInt(), true)
        stages.add(stage)
        return stage
    }

    private fun mockCityScreen(stage: Stage, crampedPortrait: Boolean, multipleCities: Boolean): CityScreen {
        val civ = mock(ForeignCivView::class.java)
        `when`(civ.getOuterColor()).thenReturn(ImmutableColor(Color.ROYAL))
        `when`(civ.getInnerColor()).thenReturn(ImmutableColor(Color.WHITE))

        val city = mock(CityView::class.java)
        `when`(city.owningCiv()).thenReturn(civ)
        `when`(city.isBeingRazed()).thenReturn(false)
        `when`(city.isPuppet()).thenReturn(false)
        `when`(city.isInResistance()).thenReturn(false)
        `when`(city.isCapital()).thenReturn(false)
        `when`(city.name).thenReturn(CITY_NAME)
        `when`(city.getPopulationCount()).thenReturn(POPULATION)
        `when`(city.getGarrison()).thenReturn(null)

        val screen = mock(CityScreen::class.java)
        `when`(screen.cityView).thenReturn(city)
        `when`(screen.viewableCities).thenReturn(
            if (multipleCities) listOf(city, mock(CityView::class.java)) else listOf(city)
        )
        `when`(screen.canChangeState).thenReturn(false)
        `when`(screen.isCrampedPortrait()).thenReturn(crampedPortrait)
        `when`(screen.stage).thenReturn(stage)
        return screen
    }

    private fun cityNameCell(picker: CityScreenCityPickerTable) = picker.cells[1]

    private fun assertCityNameRebuilt(nameCell: Cell<*>) {
        val nameTable = nameCell.actor as Table
        val labels = ArrayList<Label>()
        for (actor in nameTable.children) {
            if (actor is Label) labels.add(actor)
        }
        assertEquals(CITY_NAME, labels[0].text.toString())
        assertEquals(" ($POPULATION)", labels[1].text.toString())
    }

    private fun assertEmptySideCells(picker: CityScreenCityPickerTable) {
        assertEquals(3, picker.cells.size)
        assertNull(picker.cells[0].actor)
        assertNull(picker.cells[2].actor)
    }

    private fun assertPagingArrows(picker: CityScreenCityPickerTable) {
        assertEquals(3, picker.cells.size)
        assertArrow(picker.cells[0].actor as Table, rotated = false)
        assertArrow(picker.cells[2].actor as Table, rotated = true)
    }

    private fun assertArrow(button: Table, rotated: Boolean) {
        assertTrue(button.children.size == 1)
        val image = button.children.first() as Image
        assertEquals(if (rotated) 180f else 0f, image.rotation, WIDTH_DELTA)
    }

    private companion object {
        const val STAGE_WIDTH = 1200f
        const val STAGE_HEIGHT = 800f
        const val CITY_NAME = "Athens"
        const val POPULATION = 4
        const val WIDTH_DELTA = 0.001f

        private var uiInitialized = false

        private fun ensureUiInitialized() {
            if (uiInitialized) return
            val testGame = TestGame()
            UncivGame.Current.files = UncivFiles(Gdx.files)
            Fonts.fontImplementation = FontDesktop()
            ImageGetter.resetAtlases()
            ImageGetter.setNewRuleset(testGame.ruleset)
            BaseScreen.setSkin()
            uiInitialized = true
        }
    }
}
