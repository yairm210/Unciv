package com.unciv.ui.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tr
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.VictoryScreen
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.pickerscreens.GreatPersonPickerScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.pickerscreens.TechButton
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.bottombar.BattleTable
import com.unciv.ui.worldscreen.bottombar.WorldScreenBottomBar
import com.unciv.ui.worldscreen.optionstable.OnlineMultiplayer
import com.unciv.ui.worldscreen.optionstable.PopupTable
import com.unciv.ui.worldscreen.unit.UnitActionsTable
import kotlin.concurrent.thread

class WorldScreen(val viewingCiv:CivilizationInfo) : CameraStageBaseScreen() {
    val gameInfo = game.gameInfo
    var isPlayersTurn = viewingCiv == gameInfo.currentPlayerCiv // todo this should be updated when passing turns

    val tileMapHolder: TileMapHolder  = TileMapHolder(this, gameInfo.tileMap)
    val minimapWrapper = MinimapHolder(tileMapHolder)

    private val topBar = WorldScreenTopBar(this)
    val bottomBar = WorldScreenBottomBar(this)
    val battleTable = BattleTable(this)
    val unitActionsTable = UnitActionsTable(this)

    private val techPolicyandVictoryHolder = Table()
    private val techButtonHolder = Table()
    private val diplomacyButtonWrapper = Table()
    private val nextTurnButton = createNextTurnButton()

    private val notificationsScroll: NotificationsScroll
    var alertPopupIsOpen = false // if we have an alert popup and then we changed screens, the old one shouldn't affect us

    init {
        topBar.setPosition(0f, stage.height - topBar.height)
        topBar.width = stage.width

        notificationsScroll = NotificationsScroll(this)
        // notifications are right-aligned, they take up only as much space as necessary.
        notificationsScroll.width = stage.width/2

        minimapWrapper.x = stage.width - minimapWrapper.width

        tileMapHolder.addTiles()

        techButtonHolder.touchable=Touchable.enabled
        techButtonHolder.onClick("paper") {
            game.screen = TechPickerScreen(viewingCiv)
        }
        techPolicyandVictoryHolder.add(techButtonHolder)

        // Don't show policies until they become relevant
        if(viewingCiv.policies.adoptedPolicies.isNotEmpty() || viewingCiv.policies.canAdoptPolicy()) {
            val policyScreenButton = Button(skin)
            policyScreenButton.add(ImageGetter.getImage("PolicyIcons/Constitution")).size(30f).pad(15f)
            policyScreenButton.onClick { game.screen = PolicyPickerScreen(this) }
            techPolicyandVictoryHolder.add(policyScreenButton).pad(10f)
        }

        stage.addActor(tileMapHolder)
        stage.addActor(minimapWrapper)
        stage.addActor(topBar)
        stage.addActor(nextTurnButton)
        stage.addActor(techPolicyandVictoryHolder)
        stage.addActor(notificationsScroll)


        diplomacyButtonWrapper.defaults().pad(5f)
        stage.addActor(diplomacyButtonWrapper)

        bottomBar.width = stage.width
        stage.addActor(bottomBar)

        battleTable.width = stage.width/3
        battleTable.x = stage.width/3
        stage.addActor(battleTable)

        stage.addActor(unitActionsTable)

        displayTutorials("NewGame")
        displayTutorials("TileLayout")

        createNextTurnButton() // needs civ table to be positioned

        val tileToCenterOn: Vector2 =
                when {
                    viewingCiv.cities.isNotEmpty() -> viewingCiv.getCapital().location
                    viewingCiv.getCivUnits().isNotEmpty() -> viewingCiv.getCivUnits().first().getTile().position
                    else -> Vector2.Zero
                }
        tileMapHolder.setCenterPosition(tileToCenterOn,true)

        update()

        if(gameInfo.gameParameters.isOnlineMultiplayer && !gameInfo.isUpToDate)
            isPlayersTurn = false // until we're up to date, don't let the player do anything
        if(gameInfo.gameParameters.isOnlineMultiplayer && !isPlayersTurn) {
            stage.addAction(Actions.forever(Actions.sequence(Actions.run {
                loadLatestMultiplayerState()
            }, Actions.delay(10f)))) // delay is in seconds
        }
    }

    fun loadLatestMultiplayerState(){
        val loadingGamePopup = PopupTable(this)
        loadingGamePopup.add("Loading latest game state...")
        loadingGamePopup.open()
        thread {
            try {
                val latestGame = OnlineMultiplayer().tryDownloadGame(gameInfo.gameId)
                if(gameInfo.isUpToDate && gameInfo.currentPlayer==latestGame.currentPlayer) { // we were trying to download this to see when it's our turn...nothing changed
                    loadingGamePopup.close()
                    return@thread
                }
                latestGame.isUpToDate=true
                // Since we're making a screen this needs to run from the man thread which has a GL context
                Gdx.app.postRunnable { game.loadGame(latestGame) }

            } catch (ex: Exception) {
                loadingGamePopup.close()
                val couldntDownloadLatestGame = PopupTable(this)
                couldntDownloadLatestGame.addGoodSizedLabel("Couldn't download the latest game state!").row()
                couldntDownloadLatestGame.addCloseButton()
                couldntDownloadLatestGame.addAction(Actions.delay(5f, Actions.run { couldntDownloadLatestGame.close() }))
                couldntDownloadLatestGame.open()
            }
        }
    }

    // This is private so that we will set the shouldUpdate to true instead.
    // That way, not only do we save a lot of unnecessary updates, we also ensure that all updates are called from the main GL thread
    // and we don't get any silly concurrency problems!
    private fun update() {

        displayTutorialsOnUpdate(viewingCiv, gameInfo)

        bottomBar.update(tileMapHolder.selectedTile) // has to come before tilemapholder update because the tilemapholder actions depend on the selected unit!
        battleTable.update()

        minimapWrapper.update(viewingCiv)
        minimapWrapper.y = bottomBar.height // couldn't be bothered to create a separate val for minimap wrapper

        unitActionsTable.update(bottomBar.unitTable.selectedUnit)
        unitActionsTable.y = bottomBar.unitTable.height

        // if we use the clone, then when we update viewable tiles
        // it doesn't update the explored tiles of the civ... need to think about that harder
        // it causes a bug when we move a unit to an unexplored tile (for instance a cavalry unit which can move far)
        tileMapHolder.updateTiles(viewingCiv)

        topBar.update(viewingCiv)

        updateTechButton(viewingCiv)
        techPolicyandVictoryHolder.pack()
        techPolicyandVictoryHolder.setPosition(10f, topBar.y - techPolicyandVictoryHolder.height - 5f)
        updateDiplomacyButton(viewingCiv)

        notificationsScroll.update(viewingCiv.notifications)
        notificationsScroll.setPosition(stage.width - notificationsScroll.width - 5f,
                nextTurnButton.y - notificationsScroll.height - 5f)

        val isSomethingOpen = tutorials.isTutorialShowing || stage.actors.any { it is TradePopup }
                || alertPopupIsOpen
        if(!isSomethingOpen) {
            when {
                !gameInfo.oneMoreTurnMode && gameInfo.civilizations.any { it.victoryManager.hasWon() } -> game.screen = VictoryScreen()
                viewingCiv.policies.freePolicies > 0 -> game.screen = PolicyPickerScreen(this)
                viewingCiv.greatPeople.freeGreatPeople > 0 -> game.screen = GreatPersonPickerScreen()
                viewingCiv.tradeRequests.isNotEmpty() -> TradePopup(this)
                viewingCiv.popupAlerts.any() -> AlertPopup(this, viewingCiv.popupAlerts.first())
            }
        }
        updateNextTurnButton()
    }

    private fun displayTutorialsOnUpdate(cloneCivilization: CivilizationInfo, gameClone: GameInfo) {
        if (UnCivGame.Current.settings.hasCrashedRecently) {
            displayTutorials("GameCrashed")
            UnCivGame.Current.settings.tutorialsShown.remove("GameCrashed")
            UnCivGame.Current.settings.hasCrashedRecently = false
            UnCivGame.Current.settings.save()
        }

        if (bottomBar.unitTable.selectedUnit != null) displayTutorials("UnitSelected")
        if (viewingCiv.cities.isNotEmpty()) displayTutorials("CityFounded")
        if (UnCivGame.Current.settings.tutorialsShown.contains("CityEntered")) displayTutorials("AfterCityEntered")


        if (!UnCivGame.Current.settings.tutorialsShown.contains("EnemyCityNeedsConqueringWithMeleeUnit")) {
            for (enemyCity in cloneCivilization.diplomacy.values.filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() }.flatMap { it.cities }) {
                if (enemyCity.health == 1 && enemyCity.getCenterTile().getTilesInDistance(2)
                                .any { it.getUnits().any { unit -> unit.civInfo == cloneCivilization } })
                    displayTutorials("EnemyCityNeedsConqueringWithMeleeUnit")
            }
        }

        if (gameClone.getCurrentPlayerCivilization().getCivUnits().any { it.health < 100 })
            displayTutorials("InjuredUnits")

        if (gameClone.getCurrentPlayerCivilization().getCivUnits().any { it.name == Constants.worker })
            displayTutorials("WorkerTrained")
    }

    private fun updateDiplomacyButton(civInfo: CivilizationInfo) {
        diplomacyButtonWrapper.clear()
        if(civInfo.getKnownCivs()
                        .filterNot { it.isDefeated() || it==viewingCiv || it.isBarbarian() }
                        .any()) {
            displayTutorials("OtherCivEncountered")
            val btn = TextButton("Diplomacy".tr(), skin)
            btn.onClick { UnCivGame.Current.screen = DiplomacyScreen(viewingCiv) }
            btn.label.setFontSize(30)
            btn.labelCell.pad(10f)
            diplomacyButtonWrapper.add(btn)
        }
        diplomacyButtonWrapper.pack()
        diplomacyButtonWrapper.y = techPolicyandVictoryHolder.y -20 - diplomacyButtonWrapper.height
    }

    private fun updateTechButton(civInfo: CivilizationInfo) {
        techButtonHolder.isVisible = civInfo.cities.isNotEmpty()
        techButtonHolder.clearChildren()

        val researchableTechs = GameBasics.Technologies.values.filter { !civInfo.tech.isResearched(it.name) && civInfo.tech.canBeResearched(it.name) }
        if (civInfo.tech.currentTechnology() == null && researchableTechs.isEmpty())
            civInfo.tech.techsToResearch.add("Future Tech")

        if (civInfo.tech.currentTechnology() == null) {
            val buttonPic = Table()
            buttonPic.background = ImageGetter.getDrawable("OtherIcons/civTableBackground")
                    .tint(colorFromRGB(7, 46, 43))
            buttonPic.defaults().pad(10f)
            buttonPic.add("{Pick a tech}!".toLabel().setFontColor(Color.WHITE).setFontSize(22))
            techButtonHolder.add(buttonPic)
        }
        else {
            val currentTech = civInfo.tech.currentTechnologyName()!!
            val innerButton = TechButton(currentTech,civInfo.tech)
            innerButton.color = colorFromRGB(7, 46, 43)
            techButtonHolder.add(innerButton)
            val turnsToTech = civInfo.tech.turnsToTech(currentTech)
            innerButton.text.setText(currentTech.tr() + "\r\n" + turnsToTech
                    + (if(turnsToTech>1) " {turns}".tr() else " {turn}".tr()))
        }

        techButtonHolder.pack() //setSize(techButtonHolder.prefWidth, techButtonHolder.prefHeight)
    }

    private fun createNextTurnButton(): TextButton {

        val nextTurnButton = TextButton("", skin) // text is set in update()
        nextTurnButton.label.setFontSize(30)
        nextTurnButton.labelCell.pad(10f)

        nextTurnButton.onClick {
            // cycle through units not yet done
            if (viewingCiv.shouldGoToDueUnit()) {
                val nextDueUnit = viewingCiv.getNextDueUnit()
                if(nextDueUnit!=null) {
                    tileMapHolder.setCenterPosition(nextDueUnit.currentTile.position, false, false)
                    bottomBar.unitTable.selectedUnit = nextDueUnit
                    shouldUpdate=true
                }
                return@onClick
            }

            val cityWithNoProductionSet = viewingCiv.cities
                    .firstOrNull{it.cityConstructions.currentConstruction==""}
            if(cityWithNoProductionSet!=null){
                game.screen = CityScreen(cityWithNoProductionSet)
                return@onClick
            }

            if (viewingCiv.shouldOpenTechPicker()) {
                game.screen = TechPickerScreen(viewingCiv.tech.freeTechs != 0, viewingCiv)
                return@onClick
            } else if (viewingCiv.policies.shouldOpenPolicyPicker) {
                game.screen = PolicyPickerScreen(this)
                viewingCiv.policies.shouldOpenPolicyPicker = false
                return@onClick
            }

            nextTurn() // If none of the above
        }

        return nextTurnButton
    }

    private fun nextTurn() {
        isPlayersTurn = false
        shouldUpdate = true


        thread { // on a separate thread so the user can explore their world while we're passing the turn
            val gameInfoClone = gameInfo.clone()
            gameInfoClone.setTransients()
            try {
                gameInfoClone.nextTurn()

                if(gameInfo.gameParameters.isOnlineMultiplayer) {
                    try {
                        OnlineMultiplayer().tryUploadGame(gameInfoClone)
                    } catch (ex: Exception) {
                        val cantUploadNewGamePopup = PopupTable(this)
                        cantUploadNewGamePopup.addGoodSizedLabel("Could not upload game!").row()
                        cantUploadNewGamePopup.addCloseButton()
                        cantUploadNewGamePopup.open()
                        isPlayersTurn = true // Since we couldn't push the new game clone, then it's like we never clicked the "next turn" button
                        shouldUpdate = true
                        return@thread
                    }
                }
            } catch (ex: Exception) {
                game.settings.hasCrashedRecently = true
                game.settings.save()
                throw ex
            }

            game.gameInfo = gameInfoClone

            val shouldAutoSave = gameInfoClone.turns % game.settings.turnsBetweenAutosaves == 0

            // create a new worldscreen to show the new stuff we've changed, and switch out the current screen.
            // do this on main thread - it's the only one that has a GL context to create images from
            Gdx.app.postRunnable {

                fun createNewWorldScreen(){
                    val newWorldScreen = WorldScreen(gameInfoClone.getPlayerToViewAs())
                    newWorldScreen.tileMapHolder.scrollX = tileMapHolder.scrollX
                    newWorldScreen.tileMapHolder.scrollY = tileMapHolder.scrollY
                    newWorldScreen.tileMapHolder.scaleX = tileMapHolder.scaleX
                    newWorldScreen.tileMapHolder.scaleY = tileMapHolder.scaleY
                    newWorldScreen.tileMapHolder.updateVisualScroll()
                    game.worldScreen = newWorldScreen
                    game.setWorldScreen()
                }

                if (gameInfoClone.currentPlayerCiv.civName != viewingCiv.civName
                        && !gameInfoClone.gameParameters.isOnlineMultiplayer)
                    UnCivGame.Current.screen = PlayerReadyScreen(gameInfoClone.getCurrentPlayerCivilization())
                else {
                    createNewWorldScreen()
                }

                if(shouldAutoSave) {
                    game.worldScreen.nextTurnButton.disable()
                    GameSaver().autoSave(gameInfoClone) {
                        createNewWorldScreen()  // only enable the user to next turn once we've saved the current one
                    }
                }
            }
        }
    }

    fun updateNextTurnButton() {
        val text = when {
            !isPlayersTurn -> "Waiting for other players..."
            viewingCiv.shouldGoToDueUnit() -> "Next unit"
            viewingCiv.cities.any { it.cityConstructions.currentConstruction == "" } -> "Pick construction"
            viewingCiv.shouldOpenTechPicker() -> "Pick a tech"
            viewingCiv.policies.shouldOpenPolicyPicker -> "Pick a policy"
            else -> "Next turn"
        }
        nextTurnButton.setText(text.tr())
        nextTurnButton.color = if (text == "Next turn") Color.WHITE else Color.GRAY
        nextTurnButton.pack()
        if (alertPopupIsOpen || !isPlayersTurn) nextTurnButton.disable()
        else nextTurnButton.enable()
        nextTurnButton.setPosition(stage.width - nextTurnButton.width - 10f, topBar.y - nextTurnButton.height - 10f)
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            super.resize(width, height)
            game.worldScreen = WorldScreen(viewingCiv) // start over.
            game.setWorldScreen()
        }
    }

    var shouldUpdate=false


    override fun render(delta: Float) {
        //  This is so that updates happen in the MAIN THREAD, where there is a GL Context,
        //    otherwise images will not load properly!
        if (shouldUpdate) {
            shouldUpdate = false

            update()
            showTutorialsOnNextTurn()
        }

        super.render(delta)
    }

    private fun showTutorialsOnNextTurn(){
        val shownTutorials = UnCivGame.Current.settings.tutorialsShown
        displayTutorials("NextTurn")
        if("BarbarianEncountered" !in shownTutorials
                && viewingCiv.viewableTiles.any { it.getUnits().any { unit -> unit.civInfo.isBarbarian() } })
            displayTutorials("BarbarianEncountered")
        if(viewingCiv.cities.size > 2) displayTutorials("SecondCity")
        if(viewingCiv.getHappiness() < 5) displayTutorials("HappinessGettingLow")
        if(viewingCiv.getHappiness() < 0) displayTutorials("Unhappiness")
        if(viewingCiv.goldenAges.isGoldenAge()) displayTutorials("GoldenAge")
        if(gameInfo.turns >= 50 && UnCivGame.Current.settings.checkForDueUnits) displayTutorials("Idle_Units")
        if(gameInfo.turns >= 100) displayTutorials("ContactMe")
        val resources = viewingCiv.getCivResources()
        if(resources.any { it.resource.resourceType==ResourceType.Luxury }) displayTutorials("LuxuryResource")
        if(resources.any { it.resource.resourceType==ResourceType.Strategic}) displayTutorials("StrategicResource")
        if("EnemyCity" !in shownTutorials
                && gameInfo.civilizations.filter { it!=viewingCiv }
                        .flatMap { it.cities }.any { viewingCiv.exploredTiles.contains(it.location) })
            displayTutorials("EnemyCity")
        if(viewingCiv.containsBuildingUnique("Enables construction of Spaceship parts"))
            displayTutorials("ApolloProgram")
        if(viewingCiv.getCivUnits().any { it.type == UnitType.Siege })
            displayTutorials("SiegeUnitTrained")
        if(viewingCiv.tech.getTechUniques().contains("Enables embarkation for land units"))
            displayTutorials("CanEmbark")
    }

}