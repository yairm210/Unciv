package com.unciv.ui.worldscreen

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.GameSaver
import com.unciv.ui.worldscreen.unit.UnitTable

class WorldScreen : CameraStageBaseScreen() {
    internal val civInfo: CivilizationInfo

    val tileMapHolder: TileMapHolder

    internal var buttonScale = game.settings.buttonScale
    private val tileInfoTable: TileInfoTable
    private val civTable = CivStatsTable(this)
    private val techButton = TextButton("", CameraStageBaseScreen.skin)
    private val nextTurnButton = createNextTurnButton()

    internal val optionsTable: WorldScreenOptionsTable
    private val notificationsScroll: NotificationsScroll
    internal val unitTable = UnitTable(this)
    private val battleTable:BattleTable

    init {
        val gameInfo = game.gameInfo
        this.civInfo = gameInfo.getPlayerCivilization()

        battleTable = BattleTable(this)
        unitTable.setPosition(5f, 5f)
        tileMapHolder = TileMapHolder(this, gameInfo.tileMap, civInfo)
        tileInfoTable = TileInfoTable(this, civInfo)
        civTable.setPosition(10f, stage.height - civTable.height - 10f )
        nextTurnButton.setPosition(stage.width - nextTurnButton.width - 10f,
                civTable.y - nextTurnButton.height - 10f)
        notificationsScroll = NotificationsScroll(gameInfo.notifications, this)
        notificationsScroll.width = stage.width/3
        optionsTable = WorldScreenOptionsTable(this, civInfo)
        Label("", CameraStageBaseScreen.skin).style.font.data.setScale(game.settings.labelScale)


        tileMapHolder.addTiles()

        stage.addActor(tileMapHolder)
        stage.addActor(tileInfoTable)
        stage.addActor(civTable)
        stage.addActor(nextTurnButton)
        stage.addActor(techButton)
        stage.addActor(notificationsScroll)
        stage.addActor(unitTable)
        stage.addActor(battleTable)

        update()

        tileMapHolder.setCenterPosition(Vector2.Zero)
        createNextTurnButton() // needs civ table to be positioned
        stage.addActor(optionsTable)
        displayTutorials("NewGame")
    }


    fun update() {
        if (game.gameInfo.tutorial.contains("CityEntered")) {
            displayTutorials("AfterCityEntered")
        }

        updateTechButton()
        if (tileMapHolder.selectedTile != null)
            tileInfoTable.updateTileTable(tileMapHolder.selectedTile!!)

        unitTable.update() // has to come before tilemapholder update because the tilemapholder actions depend on the selected unit!
        tileMapHolder.updateTiles()
        civTable.update(this)
        notificationsScroll.update()
        notificationsScroll.width = stage.width/3
        notificationsScroll.setPosition(stage.width - notificationsScroll.width - 5f,
                nextTurnButton.y - notificationsScroll.height - 5f)


        battleTable.update()
    }

    private fun updateTechButton() {
        techButton.isVisible = civInfo.cities.isNotEmpty()
        techButton.clearListeners()
        techButton.addClickListener {
            game.screen = TechPickerScreen(civInfo)
        }

        if (civInfo.tech.currentTechnology() == null)
            techButton.setText("Choose a tech!")
        else
            techButton.setText(civInfo.tech.currentTechnology() + "\r\n"
                    + civInfo.tech.turnsToTech(civInfo.tech.currentTechnology()!!) + " turns")

        techButton.setSize(techButton.prefWidth, techButton.prefHeight)
        techButton.setPosition(10f, civTable.y - techButton.height - 5f)
    }

    private fun createNextTurnButton(): TextButton {
        val nextTurnButton = TextButton("Next turn", CameraStageBaseScreen.skin)
        nextTurnButton.addClickListener {
            if (civInfo.tech.freeTechs != 0) {
                game.screen = TechPickerScreen(true, civInfo)
                return@addClickListener
            } else if (civInfo.policies.shouldOpenPolicyPicker) {
                game.screen = PolicyPickerScreen(civInfo)
                civInfo.policies.shouldOpenPolicyPicker = false
                return@addClickListener
            }
            else if (civInfo.tech.currentTechnology() == null && civInfo.cities.isNotEmpty()) {
                game.screen = TechPickerScreen(civInfo)
                return@addClickListener
            }

            game.gameInfo.nextTurn()
            unitTable.currentlyExecutingAction = null
            GameSaver.saveGame(game, "Autosave")
            update()
            displayTutorials("NextTurn")
        }

        return nextTurnButton
    }

    override fun resize(width: Int, height: Int) {

        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            super.resize(width, height)
            game.worldScreen = WorldScreen() // start over.
            game.setWorldScreen()
        }
    }
}