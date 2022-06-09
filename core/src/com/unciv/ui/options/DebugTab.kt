package com.unciv.ui.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.MapSaver
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.ui.utils.*

fun debugTab() = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)
    val game = UncivGame.Current

    val worldScreen = game.worldScreen
    if (worldScreen != null) {
        val simulateButton = "Simulate until turn:".toTextButton()
        val simulateTextField = TextField(game.simulateUntilTurnForDebug.toString(), BaseScreen.skin)
        val invalidInputLabel = "This is not a valid integer!".toLabel().also { it.isVisible = false }
        simulateButton.onClick {
            val simulateUntilTurns = simulateTextField.text.toIntOrNull()
            if (simulateUntilTurns == null) {
                invalidInputLabel.isVisible = true
                return@onClick
            }
            game.simulateUntilTurnForDebug = simulateUntilTurns
            invalidInputLabel.isVisible = false
            worldScreen.nextTurn()
        }
        add(simulateButton)
        add(simulateTextField).row()
        add(invalidInputLabel).colspan(2).row()
    }

    add("Supercharged".toCheckBox(game.superchargedForDebug) {
        game.superchargedForDebug = it
    }).colspan(2).row()
    add("View entire map".toCheckBox(game.viewEntireMapForDebug) {
        game.viewEntireMapForDebug = it
    }).colspan(2).row()
    val curGameInfo = game.gameInfo
    if (curGameInfo != null) {
        add("God mode (current game)".toCheckBox(curGameInfo.gameParameters.godMode) {
            curGameInfo.gameParameters.godMode = it
        }).colspan(2).row()
    }
    add("Save games compressed".toCheckBox(GameSaver.saveZipped) {
        GameSaver.saveZipped = it
    }).colspan(2).row()
    add("Save maps compressed".toCheckBox(MapSaver.saveZipped) {
        MapSaver.saveZipped = it
    }).colspan(2).row()

    add("Gdx Scene2D debug".toCheckBox(BaseScreen.enableSceneDebug) {
        BaseScreen.enableSceneDebug = it
    }).colspan(2).row()

    add("Allow untyped Uniques in mod checker".toCheckBox(RulesetCache.modCheckerAllowUntypedUniques) {
        RulesetCache.modCheckerAllowUntypedUniques = it
    }).colspan(2).row()

    add(Table().apply {
        add("Unique misspelling threshold".toLabel()).left().fillX()
        add(
            UncivSlider(0f, 0.5f, 0.05f, initial = RulesetCache.uniqueMisspellingThreshold.toFloat()) {
                RulesetCache.uniqueMisspellingThreshold = it.toDouble()
            }
        ).minWidth(120f).pad(5f)
    }).colspan(2).row()

    val unlockTechsButton = "Unlock all techs".toTextButton()
    unlockTechsButton.onClick {
        if (curGameInfo == null)
            return@onClick
        for (tech in curGameInfo.ruleSet.technologies.keys) {
            if (tech !in curGameInfo.getCurrentPlayerCivilization().tech.techsResearched) {
                curGameInfo.getCurrentPlayerCivilization().tech.addTechnology(tech)
                curGameInfo.getCurrentPlayerCivilization().popupAlerts.removeLastOrNull()
            }
        }
        curGameInfo.getCurrentPlayerCivilization().updateSightAndResources()
        if (worldScreen != null) worldScreen.shouldUpdate = true
    }
    add(unlockTechsButton).colspan(2).row()

    val giveResourcesButton = "Get all strategic resources".toTextButton()
    giveResourcesButton.onClick {
        if (curGameInfo == null)
            return@onClick
        val ownedTiles = curGameInfo.tileMap.values.asSequence().filter { it.getOwner() == curGameInfo.getCurrentPlayerCivilization() }
        val resourceTypes = curGameInfo.ruleSet.tileResources.values.asSequence().filter { it.resourceType == ResourceType.Strategic }
        for ((tile, resource) in ownedTiles zip resourceTypes) {
            tile.resource = resource.name
            tile.resourceAmount = 999
            // Debug option, so if it crashes on this that's relatively fine
            // If this becomes a problem, check if such an improvement exists and otherwise plop down a great improvement or so
            tile.improvement = resource.getImprovements().first()
        }
        curGameInfo.getCurrentPlayerCivilization().updateSightAndResources()
        if (worldScreen != null) worldScreen.shouldUpdate = true
    }
    add(giveResourcesButton).colspan(2).row()
}
