package com.unciv.ui.worldscreen

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.worldscreen.unit.UnitActionsTable

class WorldScreen : CameraStageBaseScreen() {
    val gameInfo = game.gameInfo
    internal val civInfo: CivilizationInfo = gameInfo.getPlayerCivilization()

    val tileMapHolder: TileMapHolder  = TileMapHolder(this, gameInfo.tileMap, civInfo)

    internal var buttonScale = 0.9f
    private val topBar = WorldScreenTopBar(this)
    val bottomBar = WorldScreenBottomBar(this)
    val unitActionsTable = UnitActionsTable(this)

    private val techButton = TextButton("", CameraStageBaseScreen.skin)
    private val nextTurnButton = createNextTurnButton()

    private val notificationsScroll: NotificationsScroll

    init {
        topBar.setPosition(0f, stage.height - topBar.height)
        topBar.width = stage.width

        nextTurnButton.setPosition(stage.width - nextTurnButton.width - 10f,
                topBar.y - nextTurnButton.height - 10f)
        notificationsScroll = NotificationsScroll(gameInfo.notifications, this)
        notificationsScroll.width = stage.width/3
        Label("", skin).style.font.data.setScale(1.5f)


        tileMapHolder.addTiles()

        stage.addActor(tileMapHolder)
        stage.addActor(topBar)
        stage.addActor(nextTurnButton)
        stage.addActor(techButton)
        stage.addActor(notificationsScroll)

        bottomBar.width = stage.width
        stage.addActor(bottomBar)
        stage.addActor(unitActionsTable)

        update()

        tileMapHolder.setCenterPosition(Vector2.Zero)
        createNextTurnButton() // needs civ table to be positioned
        displayTutorials("NewGame")
    }


    fun update() {
        if (game.gameInfo.tutorial.contains("CityEntered")) {
            displayTutorials("AfterCityEntered")
        }

        updateTechButton()
        bottomBar.update(tileMapHolder.selectedTile) // has to come before tilemapholder update because the tilemapholder actions depend on the selected unit!

        unitActionsTable.update(bottomBar.unitTable.selectedUnit)
        unitActionsTable.y = bottomBar.height

        tileMapHolder.updateTiles()
        topBar.update()
        notificationsScroll.update()
        notificationsScroll.width = stage.width/3
        notificationsScroll.setPosition(stage.width - notificationsScroll.width - 5f,
                nextTurnButton.y - notificationsScroll.height - 5f)
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
        techButton.setPosition(10f, topBar.y - techButton.height - 5f)
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
            bottomBar.unitTable.currentlyExecutingAction = null
            kotlin.concurrent.thread { GameSaver().saveGame(game.gameInfo, "Autosave") }
            update()
            displayTutorials("NextTurn")

            if(gameInfo.turns>=100)
                displayTutorials("ContactMe")
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

