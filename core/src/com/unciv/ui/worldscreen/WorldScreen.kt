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
    private val civTable = CivStatsTable()
    private val techButton = TextButton("", CameraStageBaseScreen.skin)


    internal val optionsTable: WorldScreenOptionsTable
    private val notificationsScroll: NotificationsScroll
    internal val unitTable = UnitTable(this)

    init {
        val gameInfo = game.gameInfo
        this.civInfo = gameInfo.getPlayerCivilization()
        tileMapHolder = TileMapHolder(this, gameInfo.tileMap, civInfo)
        tileInfoTable = TileInfoTable(this, civInfo)
        notificationsScroll = NotificationsScroll(gameInfo.notifications, this)
        optionsTable = WorldScreenOptionsTable(this, civInfo)
        Label("", CameraStageBaseScreen.skin).style.font.data.setScale(game.settings.labelScale)

        tileMapHolder.addTiles()
        stage.addActor(tileMapHolder)
        stage.addActor(tileInfoTable)
        stage.addActor(civTable)
        stage.addActor(techButton)
        stage.addActor(notificationsScroll)
        stage.addActor(unitTable)
        update()

        tileMapHolder.setCenterPosition(Vector2.Zero)
        createNextTurnButton() // needs civ table to be positioned
        stage.addActor(optionsTable)

        val beginningTutorial = mutableListOf<String>()
        beginningTutorial.add("Hello, and welcome to Unciv!" +
                "\r\nCivilization games can be complex, so we'll" +
                "\r\n  be guiding you along your first journey." +
                "\r\nBefore we begin, let's review some basic game concepts.")
        beginningTutorial.add("This is the world map, which is made up of multiple tiles." +
                "\r\nEach tile can contain units, as well as resources" +
                "\r\n  and improvements, which we'll get to later")
        beginningTutorial.add("You start out with two units -" +
                "\r\n  a Settler - who can found a city," +
                "\r\n  and a scout, for exploring the area." +
                "\r\n  Click on a tile to assign orders the unit!")

        displayTutorials("NewGame", beginningTutorial)
    }


    fun update() {
        if (game.gameInfo.tutorial.contains("CityEntered")) {
            val tutorial = ArrayList<String>()
            tutorial.add("Once you've done everything you can, " + "\r\nclick the next turn button on the top right to continue.")
            tutorial.add("Each turn, science, culture and gold are added" +
                    "\r\n to your civilization, your cities' construction" +
                    "\r\n continues, and they may grow in population or area.")
            displayTutorials("NextTurn", tutorial)
        }

        updateTechButton()
        if (tileMapHolder.selectedTile != null) tileInfoTable.updateTileTable(tileMapHolder.selectedTile!!)
        tileMapHolder.updateTiles()
        civTable.update(this)
        notificationsScroll.update()
        unitTable.update()
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
                    + civInfo.turnsToTech(civInfo.tech.currentTechnology()!!) + " turns")

        techButton.setSize(techButton.prefWidth, techButton.prefHeight)
        techButton.setPosition(10f, civTable.y - techButton.height - 5f)
    }

    private fun createNextTurnButton() {
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
            unitTable.selectedUnitTile = null
            unitTable.currentlyExecutingAction = null
            GameSaver.SaveGame(game, "Autosave")
            update()

            val tutorial = ArrayList<String>()
            tutorial.add("In your first couple of turns," +
                    "\r\n  you will have very little options," +
                    "\r\n  but as your civilization grows, so do the " +
                    "\r\n  number of things requiring your attention")
            displayTutorials("NextTurn", tutorial)
        }

        nextTurnButton.setPosition(stage.width - nextTurnButton.width - 10f,
                civTable.y - nextTurnButton.height - 10f)
        stage.addActor(nextTurnButton)
    }

    override fun resize(width: Int, height: Int) {

        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            super.resize(width, height)
            game.worldScreen = WorldScreen() // start over.
            game.setWorldScreen()
        }
    }
}

