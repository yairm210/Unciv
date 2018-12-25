package com.unciv.ui.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.DiplomaticStatus
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tr
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.VictoryScreen
import com.unciv.ui.pickerscreens.GreatPersonPickerScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechButton
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.bottombar.WorldScreenBottomBar
import com.unciv.ui.worldscreen.unit.UnitActionsTable

class WorldScreen : CameraStageBaseScreen() {
    val gameInfo = game.gameInfo
    internal val civInfo: CivilizationInfo = gameInfo.getPlayerCivilization()

    val tileMapHolder: TileMapHolder  = TileMapHolder(this, gameInfo.tileMap)
    val minimapWrapper = MinimapHolder(tileMapHolder)

    private val topBar = WorldScreenTopBar(this)
    val bottomBar = WorldScreenBottomBar(this)
    val unitActionsTable = UnitActionsTable(this)

    private val techButton = Table()
    private val diplomacyButtonWrapper = Table()
    private val nextTurnButton = createNextTurnButton()

    private val notificationsScroll: NotificationsScroll

    init {
        topBar.setPosition(0f, stage.height - topBar.height)
        topBar.width = stage.width

        nextTurnButton.setPosition(stage.width - nextTurnButton.width - 10f,
                topBar.y - nextTurnButton.height - 10f)
        notificationsScroll = NotificationsScroll(this)
        notificationsScroll.width = stage.width/3

        minimapWrapper.x = stage.width - minimapWrapper.width

        tileMapHolder.addTiles()

        techButton.touchable=Touchable.enabled
        techButton.onClick("paper") {
            game.screen = TechPickerScreen(civInfo)
        }

        stage.addActor(tileMapHolder)
        stage.addActor(minimapWrapper)
        stage.addActor(topBar)
        stage.addActor(nextTurnButton)
        stage.addActor(techButton)
        stage.addActor(notificationsScroll)


        diplomacyButtonWrapper.defaults().pad(5f)
        stage.addActor(diplomacyButtonWrapper)

        bottomBar.width = stage.width
        stage.addActor(bottomBar)
        stage.addActor(unitActionsTable)

        displayTutorials("NewGame")
        displayTutorials("TileLayout")
        update()

        val tileToCenterOn: Vector2 =
                when {
                    civInfo.cities.isNotEmpty() -> civInfo.getCapital().location
                    civInfo.getCivUnits().isNotEmpty() -> civInfo.getCivUnits().first().getTile().position
                    else -> Vector2.Zero
                }
        tileMapHolder.setCenterPosition(tileToCenterOn)
        createNextTurnButton() // needs civ table to be positioned
    }

    // This is private so that we will set the shouldUpdate to true instead.
    // That way, not only do we save a lot of unneccesary updates, we also ensure that all updates are called from the main GL thread
    // and we don't get any silly concurrency problems!
    private fun update() {
        // many of the display functions will be called with the game clone and not the actual game,
        // because that's guaranteed to stay the exact same and so we won't get any concurrent modification exceptions

        val gameClone = gameInfo.clone()
        gameClone.setTransients()
        val cloneCivilization = gameClone.getPlayerCivilization()
        kotlin.concurrent.thread {
            civInfo.happiness = gameClone.getPlayerCivilization().getHappinessForNextTurn().values.sum().toInt()
            gameInfo.civilizations.forEach { it.setCitiesConnectedToCapitalTransients() }
        }

        if(bottomBar.unitTable.selectedUnit!=null){
            displayTutorials("UnitSelected")
        }

        if(UnCivGame.Current.settings.hasCrashedRecently){
            displayTutorials("GameCrashed")
            UnCivGame.Current.settings.tutorialsShown.remove("GameCrashed")
            UnCivGame.Current.settings.hasCrashedRecently=false
            UnCivGame.Current.settings.save()
        }

        if (UnCivGame.Current.settings.tutorialsShown.contains("CityEntered")) {
            displayTutorials("AfterCityEntered")
        }

        if(!UnCivGame.Current.settings.tutorialsShown.contains("EnemyCityNeedsConqueringWithMeleeUnit")) {
            for (enemyCity in cloneCivilization.diplomacy.values.filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() }.flatMap { it.cities }) {
                if (enemyCity.health == 1 && enemyCity.getCenterTile().getTilesInDistance(2)
                                .any { it.getUnits().any { unit -> unit.civInfo == cloneCivilization } })
                    displayTutorials("EnemyCityNeedsConqueringWithMeleeUnit")
            }
        }

        if(gameClone.getPlayerCivilization().getCivUnits().any { it.health<100 })
            displayTutorials("InjuredUnits")

        if(gameClone.getPlayerCivilization().getCivUnits().any { it.name=="Worker" })
            displayTutorials("WorkerTrained")

        updateTechButton(cloneCivilization)
        updateDiplomacyButton(cloneCivilization)

        bottomBar.update(tileMapHolder.selectedTile) // has to come before tilemapholder update because the tilemapholder actions depend on the selected unit!
        minimapWrapper.update(cloneCivilization)
        minimapWrapper.y = bottomBar.height // couldn't be bothered to create a separate val for minimap wrapper

        unitActionsTable.update(bottomBar.unitTable.selectedUnit)
        unitActionsTable.y = bottomBar.height

        // if we use the clone, then when we update viewable tiles
        // it doesn't update the explored tiles of the civ... need to think about that harder
        // it causes a bug when we move a unit to an unexplored tile (for instance a cavalry unit which can move far)
        tileMapHolder.updateTiles(civInfo)

        topBar.update(cloneCivilization)
        notificationsScroll.update(civInfo.notifications)
        notificationsScroll.width = stage.width/3
        notificationsScroll.setPosition(stage.width - notificationsScroll.width - 5f,
                nextTurnButton.y - notificationsScroll.height - 5f)

        if(!gameInfo.oneMoreTurnMode && civInfo.victoryManager.hasWon()) game.screen = VictoryScreen()
        else if(civInfo.policies.freePolicies>0) game.screen = PolicyPickerScreen(civInfo)
        else if(civInfo.greatPeople.freeGreatPeople>0) game.screen = GreatPersonPickerScreen()
    }

    private fun updateDiplomacyButton(civInfo: CivilizationInfo) {
        diplomacyButtonWrapper.clear()
        if(civInfo.diplomacy.values.map { it.otherCiv() }
                        .filterNot { it.isDefeated() || it.isPlayerCivilization() || it.isBarbarianCivilization() }
                        .any()) {
            displayTutorials("OtherCivEncountered")
            val btn = TextButton("Diplomacy".tr(), skin)
            btn.onClick { UnCivGame.Current.screen = DiplomacyScreen() }
            diplomacyButtonWrapper.add(btn)
        }
        diplomacyButtonWrapper.pack()
        diplomacyButtonWrapper.y = techButton.y -20 - diplomacyButtonWrapper.height
    }

    private fun updateTechButton(civInfo: CivilizationInfo) {
        techButton.isVisible = civInfo.cities.isNotEmpty()

        techButton.clearChildren()

        if (civInfo.tech.currentTechnology() == null) {
            val buttonPic = Table().apply { background = ImageGetter.getDrawable("OtherIcons/civTableBackground.png").tint(colorFromRGB(7, 46, 43)); defaults().pad(10f) }
            buttonPic.add(Label("{Pick a tech}!".tr(), skin).setFontColor(Color.WHITE).setFontSize(22))
            techButton.add(buttonPic)
        }
        else {
            val currentTech = civInfo.tech.currentTechnology()!!
            val innerButton = TechButton(currentTech,civInfo.tech)
            innerButton.color = colorFromRGB(7, 46, 43)
            techButton.add(innerButton)
            val turnsToTech = civInfo.tech.turnsToTech(currentTech)
            innerButton.text.setText(currentTech.tr() + "\r\n" + turnsToTech
                    + (if(turnsToTech>1) " {turns}".tr() else " {turn}".tr()))
        }

        techButton.setSize(techButton.prefWidth, techButton.prefHeight)
        techButton.setPosition(10f, topBar.y - techButton.height - 5f)
    }

    private fun createNextTurnButton(): TextButton {
        val nextTurnButton = TextButton("Next turn".tr(), CameraStageBaseScreen.skin)
        nextTurnButton.onClick {
            if (civInfo.tech.freeTechs != 0) {
                game.screen = TechPickerScreen(true, civInfo)
                return@onClick
            } else if (civInfo.policies.shouldOpenPolicyPicker) {
                game.screen = PolicyPickerScreen(civInfo)
                civInfo.policies.shouldOpenPolicyPicker = false
                return@onClick
            }
            else if (civInfo.tech.currentTechnology() == null && civInfo.cities.isNotEmpty()) {
                game.screen = TechPickerScreen(civInfo)
                return@onClick
            }

            bottomBar.unitTable.currentlyExecutingAction = null

            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            nextTurnButton.disable()
            nextTurnButton.setText("Working...".tr())

            kotlin.concurrent.thread {
                try {
                    gameInfo.nextTurn()
                }
                catch (ex:Exception){
                    game.settings.hasCrashedRecently=true
                    game.settings.save()
                    throw ex
                }

                val gameInfoClone = gameInfo.clone()
                kotlin.concurrent.thread {
                    // the save takes a long time( up to a second!) and we can do it while the player continues his game.
                    // On the other hand if we alter the game data while it's being serialized we could get a concurrent modification exception.
                    // So what we do is we clone all the game data and serialize the clone.
                    GameSaver().saveGame(gameInfoClone, "Autosave")
                    nextTurnButton.enable() // only enable the user to next turn once we've saved the current one
                }

                // If we put this BEFORE the save game, then we try to save the game...
                // but the main thread does other stuff, including showing tutorials which guess what? Changes the game data
                // BOOM! Exception!
                // That's why this needs to be after the game is saved.
                shouldUpdate=true

                nextTurnButton.setText("Next turn".tr())
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

            val shownTutorials = UnCivGame.Current.settings.tutorialsShown
            displayTutorials("NextTurn")
            if("BarbarianEncountered" !in shownTutorials
                    && civInfo.viewableTiles.any { it.getUnits().any { unit -> unit.civInfo.isBarbarianCivilization() } })
                displayTutorials("BarbarianEncountered")
            if(civInfo.cities.size > 2) displayTutorials("SecondCity")
            if(civInfo.happiness < 0) displayTutorials("Unhappiness")
            if(civInfo.goldenAges.isGoldenAge()) displayTutorials("GoldenAge")
            if(gameInfo.turns >= 100) displayTutorials("ContactMe")
            val resources = civInfo.getCivResources()
            if(resources.keys.any { it.resourceType==ResourceType.Luxury }) displayTutorials("LuxuryResource")
            if(resources.keys.any { it.resourceType==ResourceType.Strategic}) displayTutorials("StrategicResource")
            if("EnemyCity" !in shownTutorials
                    && civInfo.exploredTiles.asSequence().map { gameInfo.tileMap[it] }
                            .any { it.isCityCenter() && it.getOwner()!=civInfo })
                displayTutorials("EnemyCity")
            if("Enables construction of Spaceship parts" in civInfo.getBuildingUniques())
                displayTutorials("ApolloProgram")
            if(civInfo.getCivUnits().any { it.type == UnitType.Siege })
                displayTutorials("SiegeUnitTrained")
            if(civInfo.tech.getUniques().contains("Enables embarkation for land units"))
                displayTutorials("CanEmbark")

            shouldUpdate=false
        }
        super.render(delta)
    }

}

