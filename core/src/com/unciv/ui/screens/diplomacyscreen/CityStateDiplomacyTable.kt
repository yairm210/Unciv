package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.civilization.managers.AssignedQuest
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup

class CityStateDiplomacyTable(private val diplomacyScreen: DiplomacyScreen) {
    val viewingCiv = diplomacyScreen.viewingCiv

    fun getCityStateDiplomacyTable(otherCiv: Civilization): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)!!

        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)

        diplomacyTable.addSeparator()

        val giveGiftButton = "Give a Gift".toTextButton()
        giveGiftButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getGoldGiftTable(otherCiv)))
        }
        diplomacyTable.add(giveGiftButton).row()
        if (diplomacyScreen.isNotPlayersTurn() || viewingCiv.isAtWarWith(otherCiv)) giveGiftButton.disable()

        val improveTileButton = getImproveTilesButton(otherCiv, otherCivDiplomacyManager)
        if (improveTileButton != null) diplomacyTable.add(improveTileButton).row()

        if (otherCivDiplomacyManager.diplomaticStatus != DiplomaticStatus.Protector)
            diplomacyTable.add(getPledgeToProtectButton(otherCiv)).row()
        else
            diplomacyTable.add(getRevokeProtectionButton(otherCiv)).row()

        val demandTributeButton = "Demand Tribute".toTextButton()
        demandTributeButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getDemandTributeTable(otherCiv)))
        }
        diplomacyTable.add(demandTributeButton).row()
        if (diplomacyScreen.isNotPlayersTurn() || viewingCiv.isAtWarWith(otherCiv)) demandTributeButton.disable()

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)!!
        if (!viewingCiv.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)) {
            if (viewingCiv.isAtWarWith(otherCiv))
                diplomacyTable.add(getNegotiatePeaceCityStateButton(otherCiv, diplomacyManager)).row()
            else diplomacyTable.add(diplomacyScreen.getDeclareWarButton(diplomacyManager, otherCiv)).row()
        }

        if (otherCiv.getCapital() != null && viewingCiv.hasExplored(otherCiv.getCapital()!!.getCenterTile()))
            diplomacyTable.add(diplomacyScreen.getGoToOnMapButton(otherCiv)).row()

        val diplomaticMarriageButton = getDiplomaticMarriageButton(otherCiv)
        if (diplomaticMarriageButton != null) diplomacyTable.add(diplomaticMarriageButton).row()

        for (assignedQuest in otherCiv.questManager.getAssignedQuestsFor(viewingCiv.civName)) {
            diplomacyTable.addSeparator()
            diplomacyTable.add(getQuestTable(assignedQuest)).row()
        }

        for (target in otherCiv.getKnownCivs().filter { otherCiv.questManager.warWithMajorActive(it) && viewingCiv != it }) {
            diplomacyTable.addSeparator()
            diplomacyTable.add(getWarWithMajorTable(target, otherCiv)).row()
        }

        return diplomacyTable
    }


    private fun getCityStateDiplomacyTableHeader(otherCiv: Civilization): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)!!

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(2.5f)

        diplomacyTable.add(LeaderIntroTable(otherCiv)).padBottom(15f).row()

        diplomacyTable.add("{Type}:  {${otherCiv.cityStateType.name}}".toLabel()).row()
        diplomacyTable.add("{Personality}:  {${otherCiv.cityStatePersonality}}".toLabel()).row()

        if (otherCiv.detailedCivResources.any { it.resource.resourceType != ResourceType.Bonus }) {
            val resourcesTable = Table()
            resourcesTable.add("{Resources}:  ".toLabel()).padRight(10f)
            val cityStateResources = otherCiv.cityStateFunctions.getCityStateResourcesForAlly()
            for (supplyList in cityStateResources) {
                if (supplyList.resource.resourceType == ResourceType.Bonus)
                    continue
                val name = supplyList.resource.name
                val wrapper = Table()
                val image = ImageGetter.getResourcePortrait(name, 30f)
                wrapper.add(image).padRight(5f)
                wrapper.add(supplyList.amount.toLabel())
                resourcesTable.add(wrapper).padRight(20f)
                wrapper.addTooltip(name, 18f)
                wrapper.onClick {
                    diplomacyScreen.openCivilopedia(supplyList.resource.makeLink())
                }
            }
            diplomacyTable.add(resourcesTable).row()
        }
        diplomacyTable.row().padTop(15f)

        otherCiv.cityStateFunctions.updateAllyCivForCityState()
        var ally = otherCiv.getAllyCivName()
        if (ally != null) {
            val allyInfluence = otherCiv.getDiplomacyManager(ally)!!.getInfluence().toInt()
            if (!viewingCiv.knows(ally) && ally != viewingCiv.civName)
                ally = "Unknown civilization"
            diplomacyTable
                .add("Ally: [$ally] with [$allyInfluence] Influence".toLabel())
                .row()
        }

        val protectors = otherCiv.cityStateFunctions.getProtectorCivs()
        if (protectors.isNotEmpty()) {
            val newProtectors = arrayListOf<String>()
            for (protector in protectors) {
                if (!viewingCiv.knows(protector) && protector.civName != viewingCiv.civName)
                    newProtectors.add("Unknown civilization".tr())
                else
                    newProtectors.add(protector.civName.tr())
            }
            val protectorString = "{Protected by}: " + newProtectors.joinToString(", ")
            diplomacyTable.add(protectorString.toLabel()).row()
        }

        val atWar = otherCiv.isAtWarWith(viewingCiv)

        val nextLevelString = when {
            atWar -> ""
            otherCivDiplomacyManager.getInfluence().toInt() < 30 -> "Reach 30 for friendship."
            ally == viewingCiv.civName -> ""
            else -> "Reach highest influence above 60 for alliance."
        }
        diplomacyTable.add(diplomacyScreen.getRelationshipTable(otherCivDiplomacyManager)).row()
        if (nextLevelString.isNotEmpty()) {
            diplomacyTable.add(nextLevelString.toLabel()).row()
        }
        diplomacyTable.row().padTop(15f)

        val relationLevel = otherCivDiplomacyManager.relationshipIgnoreAfraid()
        if (relationLevel >= RelationshipLevel.Friend) {
            // RelationshipChange = Ally -> Friend or Friend -> Favorable
            val turnsToRelationshipChange = otherCivDiplomacyManager.getTurnsToRelationshipChange()
            if (turnsToRelationshipChange != 0)
                diplomacyTable.add("Relationship changes in another [$turnsToRelationshipChange] turns".toLabel())
                    .row()
        }

        fun addBonusLabels(header: String, bonusLevel: RelationshipLevel, currentRelationLevel: RelationshipLevel) {

            val bonuses = viewingCiv.cityStateFunctions
                .getCityStateBonuses(otherCiv.cityStateType, bonusLevel)
                .filterNot { it.isHiddenToUsers() }
            if (bonuses.none()) return
            
            val headerColor = if (currentRelationLevel == bonusLevel) Color.GREEN else Color.WHITE
            diplomacyTable.add(header.toLabel(fontColor = headerColor).apply { setAlignment(Align.center) }).row()
            val stateForConditionals = StateForConditionals(viewingCiv)
            for (bonus in bonuses) {
                val bonusLabelColor = if (currentRelationLevel == bonusLevel && bonus.conditionalsApply(stateForConditionals))
                    Color.GREEN else Color.GRAY
                val bonusLabel = ColorMarkupLabel(bonus.getDisplayText(), bonusLabelColor)
                    .apply { setAlignment(Align.center) }
                diplomacyTable.add(bonusLabel).row()
            }
        }
        addBonusLabels("When Friends:", RelationshipLevel.Friend, relationLevel)
        addBonusLabels("When Allies:", RelationshipLevel.Ally, relationLevel)

        if (otherCiv.cityStateUniqueUnit != null) {
            val unitName = otherCiv.cityStateUniqueUnit
            val techNames = viewingCiv.gameInfo.ruleset.units[otherCiv.cityStateUniqueUnit]!!.requiredTechs()
            val techAndTech = techNames.joinToString(" and ")
            val isOrAre = if (techNames.count() == 1) "is" else "are"
            diplomacyTable.add("[${otherCiv.civName}] is able to provide [${unitName}] once [${techAndTech}] [${isOrAre}] researched.".toLabel(fontSize = Constants.defaultFontSize)).row()
        }

        return diplomacyTable
    }


    private fun getRevokeProtectionButton(otherCiv: Civilization): TextButton {
        val revokeProtectionButton = "Revoke Protection".toTextButton()
        revokeProtectionButton.onClick {
            ConfirmPopup(diplomacyScreen, "Revoke protection for [${otherCiv.civName}]?", "Revoke Protection") {
                otherCiv.cityStateFunctions.removeProtectorCiv(viewingCiv)
                diplomacyScreen.updateLeftSideTable(otherCiv)
                diplomacyScreen.updateRightSide(otherCiv)
            }.open()
        }
        if (diplomacyScreen.isNotPlayersTurn() || !otherCiv.cityStateFunctions.otherCivCanWithdrawProtection(viewingCiv))
            revokeProtectionButton.disable()
        return revokeProtectionButton
    }

    private fun getPledgeToProtectButton(otherCiv: Civilization): TextButton {
        val protectionButton = "Pledge to protect".toTextButton()
        protectionButton.onClick {
            ConfirmPopup(
                diplomacyScreen,
                "Declare Protection of [${otherCiv.civName}]?",
                "Pledge to protect",
                true
            ) {
                otherCiv.cityStateFunctions.addProtectorCiv(viewingCiv)
                diplomacyScreen.updateLeftSideTable(otherCiv)
                diplomacyScreen.updateRightSide(otherCiv)
            }.open()
        }
        if (diplomacyScreen.isNotPlayersTurn() || !otherCiv.cityStateFunctions.otherCivCanPledgeProtection(viewingCiv))
            protectionButton.disable()
        return protectionButton
    }

    private fun getNegotiatePeaceCityStateButton(
        otherCiv: Civilization,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton {
        val peaceButton = "Negotiate Peace".toTextButton()
        peaceButton.onClick {
            ConfirmPopup(
                diplomacyScreen,
                "Peace with [${otherCiv.civName}]?",
                "Negotiate Peace",
                true
            ) {
                val tradeLogic = TradeLogic(viewingCiv, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(
                    TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = viewingCiv.gameInfo.speed)
                )
                tradeLogic.currentTrade.theirOffers.add(
                    TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = viewingCiv.gameInfo.speed)
                )
                tradeLogic.acceptTrade()
                diplomacyScreen.updateLeftSideTable(otherCiv)
                diplomacyScreen.updateRightSide(otherCiv)
            }.open()
        }
        val cityStatesAlly = otherCiv.getAllyCivName()
        val atWarWithItsAlly = viewingCiv.getKnownCivs()
            .any { it.civName == cityStatesAlly && it.isAtWarWith(viewingCiv) }
        if (diplomacyScreen.isNotPlayersTurn() || atWarWithItsAlly) peaceButton.disable()

        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar)) {
            peaceButton.disable() // Can't trade for 10 turns after war was declared
            val turnsLeft = otherCivDiplomacyManager.getFlag(DiplomacyFlags.DeclaredWar)
            peaceButton.setText(peaceButton.text.toString() + "\n${turnsLeft.tr()}" + Fonts.turn)
        }

        return peaceButton
    }

    private fun getImproveTilesButton(
        otherCiv: Civilization,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton? {
        if (otherCiv.cities.isEmpty()) return null
        val improvableResourceTiles = getImprovableResourceTiles(otherCiv)
        val improvements =
            otherCiv.gameInfo.ruleset.tileImprovements.filter { it.value.turnsToBuild != -1 }
        var needsImprovements = false

        for (improvableTile in improvableResourceTiles)
            for (tileImprovement in improvements.values)
                if (improvableTile.tileResource.isImprovedBy(tileImprovement.name)
                    && improvableTile.improvementFunctions.canBuildImprovement(tileImprovement, otherCiv.state)
                )
                    needsImprovements = true

        if (!needsImprovements) return null


        val improveTileButton = "Gift Improvement".toTextButton()
        improveTileButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getImprovementGiftTable(otherCiv)))
        }


        if (diplomacyScreen.isNotPlayersTurn() || otherCivDiplomacyManager.getInfluence() < 60)
            improveTileButton.disable()
        return improveTileButton
    }

    private fun getDiplomaticMarriageButton(otherCiv: Civilization): TextButton? {
        if (!viewingCiv.hasUnique(UniqueType.CityStateCanBeBoughtForGold))
            return null

        val diplomaticMarriageButton =
            "Diplomatic Marriage ([${otherCiv.cityStateFunctions.getDiplomaticMarriageCost()}] Gold)".toTextButton()
        diplomaticMarriageButton.onClick {
            val newCities = otherCiv.cities
            otherCiv.cityStateFunctions.diplomaticMarriage(viewingCiv)
            UncivGame.Current.popScreen() // The other civ will no longer exist
            for (city in newCities)
                viewingCiv.popupAlerts.add(PopupAlert(AlertType.DiplomaticMarriage, city.id))   // Player gets to choose between annex and puppet
        }
        if (diplomacyScreen.isNotPlayersTurn() || !otherCiv.cityStateFunctions.canBeMarriedBy(viewingCiv))
            diplomaticMarriageButton.disable()
        return diplomaticMarriageButton
    }

    private fun getGoldGiftTable(otherCiv: Civilization): Table {
        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)
        diplomacyTable.addSeparator()

        for (giftAmount in listOf(250, 500, 1000)) {
            val influenceAmount = otherCiv.cityStateFunctions.influenceGainedByGift(viewingCiv, giftAmount)
            val giftButton =
                "Gift [$giftAmount] gold (+[$influenceAmount] influence)".toTextButton()
            giftButton.onClick {
                otherCiv.cityStateFunctions.receiveGoldGift(viewingCiv, giftAmount)
                diplomacyScreen.updateLeftSideTable(otherCiv)
                diplomacyScreen.updateRightSide(otherCiv)
            }
            diplomacyTable.add(giftButton).row()
            if (viewingCiv.gold < giftAmount || diplomacyScreen.isNotPlayersTurn()) giftButton.disable()
        }

        val backButton = "Back".toTextButton()
        backButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(backButton)
        return diplomacyTable
    }

    private fun getImprovableResourceTiles(otherCiv:Civilization) = otherCiv.cities.flatMap { it.getTiles() }.filter {
        it.hasViewableResource(otherCiv)
            && it.tileResource.resourceType != ResourceType.Bonus
            && (it.improvement == null || !it.tileResource.isImprovedBy(it.improvement!!))
    }

    private fun getImprovementGiftTable(otherCiv: Civilization): Table {
        val improvementGiftTable = getCityStateDiplomacyTableHeader(otherCiv)
        improvementGiftTable.addSeparator()

        val improvableResourceTiles = getImprovableResourceTiles(otherCiv)
        val tileImprovements =
            otherCiv.gameInfo.ruleset.tileImprovements

        for (improvableTile in improvableResourceTiles) {
            for (tileImprovement in tileImprovements.values) {
                if (improvableTile.tileResource.isImprovedBy(tileImprovement.name)
                    && improvableTile.improvementFunctions.canBuildImprovement(tileImprovement, otherCiv.state)
                ) {
                    val improveTileButton =
                        "Build [${tileImprovement}] on [${improvableTile.tileResource}] (200 Gold)".toTextButton()
                    improveTileButton.onClick {
                        viewingCiv.addGold(-200)
                        improvableTile.stopWorkingOnImprovement()
                        improvableTile.setImprovement(tileImprovement.name)
                        otherCiv.cache.updateCivResources()
                        diplomacyScreen.rightSideTable.clear()
                        diplomacyScreen.rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
                    }
                    if (viewingCiv.gold < 200)
                        improveTileButton.disable()
                    improvementGiftTable.add(improveTileButton).row()
                }
            }
        }

        val backButton = "Back".toTextButton()
        backButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        improvementGiftTable.add(backButton)
        return improvementGiftTable

    }

    private fun getDemandTributeTable(otherCiv: Civilization): Table {
        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)
        diplomacyTable.addSeparator()
        diplomacyTable.add("Tribute Willingness".toLabel()).row()
        val modifierTable = Table()
        val tributeModifiers = otherCiv.cityStateFunctions.getTributeModifiers(viewingCiv, requireWholeList = true)
        for (item in tributeModifiers) {
            val color = if (item.value >= 0) Color.GREEN else Color.RED
            modifierTable.add(item.key.toLabel(color))
            modifierTable.add(item.value.tr().toLabel(color)).row()
        }
        modifierTable.add("Sum:".toLabel())
        modifierTable.add(tributeModifiers.values.sum().toLabel()).row()
        diplomacyTable.add(modifierTable).row()
        diplomacyTable.add("At least 0 to take gold, at least 30 and size 4 city for worker".toLabel()).row()
        diplomacyTable.addSeparator()

        val demandGoldButton = "Take [${otherCiv.cityStateFunctions.goldGainedByTribute()}] gold (-15 Influence)".toTextButton()
        demandGoldButton.onClick {
            otherCiv.cityStateFunctions.tributeGold(viewingCiv)
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandGoldButton).row()
        if (otherCiv.cityStateFunctions.getTributeWillingness(viewingCiv, demandingWorker = false) < 0)   demandGoldButton.disable()

        val demandWorkerButton = "Take worker (-50 Influence)".toTextButton()
        demandWorkerButton.onClick {
            otherCiv.cityStateFunctions.tributeWorker(viewingCiv)
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandWorkerButton).row()
        if (otherCiv.cityStateFunctions.getTributeWillingness(viewingCiv, demandingWorker = true) < 0)    demandWorkerButton.disable()

        val backButton = "Back".toTextButton()
        backButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(backButton)
        return diplomacyTable
    }

    private fun getQuestTable(assignedQuest: AssignedQuest): Table {
        val questTable = Table()
        questTable.defaults().pad(10f)

        val quest: Quest = viewingCiv.gameInfo.ruleset.quests[assignedQuest.questName]!!
        val remainingTurns: Int = assignedQuest.getRemainingTurns()
        val title = if (quest.influence > 0)
            "[${quest.name}] (+[${quest.influence.toInt()}] influence)"
        else
            quest.name
        val description = assignedQuest.getDescription()

        questTable.add(title.toLabel(fontSize = Constants.headingFontSize)).row()
        questTable.add(description.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(diplomacyScreen.stage.width / 2).row()
        if (quest.duration > 0)
            questTable.add("[${remainingTurns}] turns remaining".toLabel()).row()
        if (quest.isGlobal()) {
            val leaderString = viewingCiv.gameInfo.getCivilization(assignedQuest.assigner).questManager.getScoreStringForGlobalQuest(assignedQuest)
            if (leaderString.isNotEmpty())
                questTable.add(leaderString.toLabel()).row()
        }

        questTable.onClick {
            assignedQuest.onClickAction()
        }
        return questTable
    }

    private fun getWarWithMajorTable(target: Civilization, otherCiv: Civilization): Table {
        val warTable = Table()
        warTable.defaults().pad(10f)

        val title = "War against [${target.civName}]"
        val description = "We need you to help us defend against [${target.civName}]. Killing [${otherCiv.questManager.unitsToKill(target)}] of their military units would slow their offensive."
        val progress = if (viewingCiv.knows(target)) "Currently you have killed [${otherCiv.questManager.unitsKilledSoFar(target, viewingCiv)}] of their military units."
        else "You need to find them first!"

        warTable.add(title.toLabel(fontSize = Constants.headingFontSize)).row()
        warTable.add(description.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(diplomacyScreen.stage.width / 2).row()
        warTable.add(progress.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(diplomacyScreen.stage.width / 2).row()

        return warTable
    }
}
