package com.unciv.ui.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.ui.TradeScreen
import com.unciv.ui.pickerscreens.GreatPersonPickerScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.bottombar.WorldScreenBottomBar
import com.unciv.ui.worldscreen.unit.UnitActionsTable

class WorldScreen : CameraStageBaseScreen() {
    val gameInfo = game.gameInfo
    internal val civInfo: CivilizationInfo = gameInfo.getPlayerCivilization()

    val tileMapHolder: TileMapHolder  = TileMapHolder(this, gameInfo.tileMap, civInfo)
    val minimap = Minimap(tileMapHolder)

    internal var buttonScale = 0.9f
    private val topBar = WorldScreenTopBar(this)
    val bottomBar = WorldScreenBottomBar(this)
    val unitActionsTable = UnitActionsTable(this)

    private val techButton = TextButton("", CameraStageBaseScreen.skin).apply { color= Color.BLUE }
    val tradeButtons = Table()
    private val nextTurnButton = createNextTurnButton()

    private val notificationsScroll: NotificationsScroll

    init {
        topBar.setPosition(0f, stage.height - topBar.height)
        topBar.width = stage.width

        nextTurnButton.setPosition(stage.width - nextTurnButton.width - 10f,
                topBar.y - nextTurnButton.height - 10f)
        notificationsScroll = NotificationsScroll(gameInfo.notifications, this)
        notificationsScroll.width = stage.width/3
        minimap.setSize(stage.width/5,stage.height/5)
        minimap.x = stage.width - minimap.width

        tileMapHolder.addTiles()

        techButton.addClickListener {
            game.screen = TechPickerScreen(civInfo)
        }

        stage.addActor(tileMapHolder)
        stage.addActor(minimap)
        stage.addActor(topBar)
        stage.addActor(nextTurnButton)
        stage.addActor(techButton)
        stage.addActor(notificationsScroll)


        tradeButtons.defaults().pad(5f)
        stage.addActor(tradeButtons)
//        tradeButtons.isVisible=false

        bottomBar.width = stage.width
        stage.addActor(bottomBar)
        stage.addActor(unitActionsTable)

        update()

        val tileToCenterOn: Vector2 =
                when {
                    civInfo.cities.isNotEmpty() -> civInfo.getCapital().location
                    civInfo.getCivUnits().isNotEmpty() -> civInfo.getCivUnits().first().getTile().position
                    else -> Vector2.Zero
                }
        tileMapHolder.setCenterPosition(tileToCenterOn)
        createNextTurnButton() // needs civ table to be positioned
        displayTutorials("NewGame")
    }


    fun update() {
        kotlin.concurrent.thread { civInfo.happiness = civInfo.getHappinessForNextTurn().values.sum().toInt() }

        if (game.gameInfo.tutorial.contains("CityEntered")) {
            displayTutorials("AfterCityEntered")
        }

        updateTechButton()
        updateTradeButtons()

        bottomBar.update(tileMapHolder.selectedTile) // has to come before tilemapholder update because the tilemapholder actions depend on the selected unit!
        minimap.update()
        minimap.y = bottomBar.height

        unitActionsTable.update(bottomBar.unitTable.selectedUnit)
        unitActionsTable.y = bottomBar.height

        tileMapHolder.updateTiles()
        topBar.update()
        notificationsScroll.update()
        notificationsScroll.width = stage.width/3
        notificationsScroll.setPosition(stage.width - notificationsScroll.width - 5f,
                nextTurnButton.y - notificationsScroll.height - 5f)

        if(civInfo.policies.freePolicies>0) game.screen = PolicyPickerScreen(civInfo)
        else if(civInfo.greatPeople.freeGreatPeople>0) game.screen = GreatPersonPickerScreen()
    }

    private fun updateTradeButtons() {
        tradeButtons.clear()
        for(civ in gameInfo.civilizations.filterNot { it.isDefeated() || it.isPlayerCivilization() || it.isBarbarianCivilization() }){
            if(!civInfo.diplomacy.containsKey(civ.civName)) continue
            val tb = TextButton("Trade with [${civ.civName}]".tr(),skin)
            tb.addClickListener { UnCivGame.Current.screen = TradeScreen(civ) }
            tradeButtons.add(tb).row()
        }

        tradeButtons.pack()
        tradeButtons.y = techButton.y -20 - tradeButtons.height
    }

    private fun updateTechButton() {
        techButton.isVisible = civInfo.cities.isNotEmpty()

        if (civInfo.tech.currentTechnology() == null)
            techButton.setText("{Pick a tech}!".tr())
        else
            techButton.setText(civInfo.tech.currentTechnology()!!.tr() + "\r\n"
                    + civInfo.tech.turnsToTech(civInfo.tech.currentTechnology()!!) + " {turns}".tr())

        techButton.setSize(techButton.prefWidth, techButton.prefHeight)
        techButton.setPosition(10f, topBar.y - techButton.height - 5f)
    }

    private fun createNextTurnButton(): TextButton {
        val nextTurnButton = TextButton("Next turn".tr(), CameraStageBaseScreen.skin)
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

            bottomBar.unitTable.currentlyExecutingAction = null

            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            nextTurnButton.disable()
            nextTurnButton.setText("Working...".tr())

            kotlin.concurrent.thread {
                game.gameInfo.nextTurn()
                GameSaver().saveGame(game.gameInfo, "Autosave")

                // If we put this BEFORE the save game, then we try to save the game...
                // but the main thread does other stuff, including showing tutorials which guess what? Changes the game data
                // BOOM! Exception!
                // That's why this needs to be after the game is saved.
                shouldUpdate=true

                nextTurnButton.setText("Next turn".tr())
                nextTurnButton.enable()
                Gdx.input.inputProcessor = stage
            }
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

    var shouldUpdate=false
    override fun render(delta: Float) {
        if(shouldUpdate){ //  This is so that updates happen in the MAIN THREAD, where there is a GL Context,
            // otherwise images will not load properly!
            update()

            displayTutorials("NextTurn")
            if(civInfo.cities.size > 2) displayTutorials("SecondCity")
            if(civInfo.happiness<0) displayTutorials("Unhappiness")
            if(civInfo.goldenAges.isGoldenAge()) displayTutorials("GoldenAge")
            if(gameInfo.turns>=100) displayTutorials("ContactMe")
            val resources = civInfo.getCivResources()
            if(resources.keys.any { it.resourceType==ResourceType.Luxury }) displayTutorials("LuxuryResource")
            if(resources.keys.any { it.resourceType==ResourceType.Strategic}) displayTutorials("StrategicResource")
            if(civInfo.exploredTiles.map { gameInfo.tileMap[it] }.any { it.isCityCenter() && it.getOwner()!=civInfo })
                displayTutorials("EnemyCity")

            shouldUpdate=false
        }
        super.render(delta)
    }

//    override fun resume() {
//        resize(Gdx.graphics.width,Gdx.graphics.height)
//    }
}

