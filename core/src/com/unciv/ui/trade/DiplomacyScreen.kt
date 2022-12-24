package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.AssignedQuest
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers.*
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.tilegroups.CityButton
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.RecreateOnResize
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setFontSize
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import kotlin.math.floor
import kotlin.math.roundToInt
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

/**
 * Creates the diplomacy screen for [viewingCiv].
 *
 * When [selectCiv] is given and [selectTrade] is not, that Civilization is selected as if clicked on the left side.
 * When [selectCiv] and [selectTrade] are supplied, that Trade for that Civilization is selected, used for the counter-offer option from `TradePopup`.
 */
@Suppress("KDocUnresolvedReference")  // Mentioning non-field parameters is flagged, but they work anyway
class DiplomacyScreen(
    val viewingCiv: CivilizationInfo,
    private val selectCiv: CivilizationInfo? = null,
    private val selectTrade: Trade? = null
): BaseScreen(), RecreateOnResize {
    companion object {
        private const val nationIconSize = 100f
        private const val nationIconPad = 10f
    }

    private val leftSideTable = Table().apply { defaults().pad(nationIconPad) }
    private val leftSideScroll = ScrollPane(leftSideTable)
    private val rightSideTable = Table()
    private val closeButton = Constants.close.toTextButton()

    private fun isNotPlayersTurn() = !UncivGame.Current.worldScreen!!.canChangeState

    init {
        val splitPane = SplitPane(leftSideScroll, rightSideTable, false, skin)
        splitPane.splitAmount = 0.2f

        updateLeftSideTable(selectCiv)

        splitPane.setFillParent(true)
        stage.addActor(splitPane)

        closeButton.onActivation { UncivGame.Current.popScreen() }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButton.label.setFontSize(Constants.headingFontSize)
        closeButton.labelCell.pad(10f)
        closeButton.pack()
        positionCloseButton()
        stage.addActor(closeButton) // This must come after the split pane so it will be above, that the button will be clickable

        if (selectCiv != null) {
            if (selectTrade != null) {
                val tradeTable = setTrade(selectCiv)
                tradeTable.tradeLogic.currentTrade.set(selectTrade)
                tradeTable.offerColumnsTable.update()
            } else
                updateRightSide(selectCiv)
        }
    }

    private fun positionCloseButton() {
        closeButton.setPosition(stage.width * 0.1f, stage.height - 10f, Align.top)
    }

    private fun updateLeftSideTable(selectCiv: CivilizationInfo?) {
        leftSideTable.clear()
        leftSideTable.add().padBottom(60f).row()  // room so the close button does not cover the first

        var selectCivY = 0f

        for (civ in viewingCiv.getKnownCivsSorted()) {
            if (civ == selectCiv) {
                selectCivY = leftSideTable.prefHeight
            }

            val civIndicator = ImageGetter.getNationIndicator(civ.nation, nationIconSize)

            val relationLevel = civ.getDiplomacyManager(viewingCiv).relationshipLevel()
            val relationshipIcon = if (civ.isCityState() && relationLevel == RelationshipLevel.Ally)
                ImageGetter.getImage("OtherIcons/Star")
                    .surroundWithCircle(size = 30f, color = relationLevel.color).apply {
                        actor.color = Color.GOLD
                    }
            else
                ImageGetter.getCircle().apply {
                    color = if (viewingCiv.isAtWarWith(civ)) Color.RED else relationLevel.color
                    setSize(30f, 30f)
                }
            civIndicator.addActor(relationshipIcon)

            if (civ.isCityState()) {
                val innerColor = civ.gameInfo.ruleSet.nations[civ.civName]!!.getInnerColor()
                val typeIcon = ImageGetter.getImage("CityStateIcons/"+civ.cityStateType.name)
                    .surroundWithCircle(size = 35f, color = innerColor).apply {
                        actor.color = Color.BLACK
                    }
                civIndicator.addActor(typeIcon)
                typeIcon.y = floor(civIndicator.height - typeIcon.height)
                typeIcon.x = floor(civIndicator.width - typeIcon.width)
            }

            if (civ.isCityState() && civ.questManager.haveQuestsFor(viewingCiv)) {
                val questIcon = ImageGetter.getImage("OtherIcons/Quest")
                    .surroundWithCircle(size = 30f, color = Color.GOLDENROD)
                civIndicator.addActor(questIcon)
                questIcon.x = floor(civIndicator.width - questIcon.width)
            }

            val civNameLabel = civ.civName.toLabel()
            leftSideTable.add(civIndicator).row()
            leftSideTable.add(civNameLabel).padBottom(20f).row()

            civIndicator.onClick { updateRightSide(civ) }
            civNameLabel.onClick { updateRightSide(civ) }
        }

        if (selectCivY != 0f) {
            leftSideScroll.layout()
            leftSideScroll.scrollY = selectCivY + (nationIconSize + 2 * nationIconPad - stage.height) / 2
            leftSideScroll.updateVisualScroll()
        }
    }

    private fun updateRightSide(otherCiv: CivilizationInfo) {
        rightSideTable.clear()
        if (otherCiv.isCityState()) rightSideTable.add(
            ScrollPane(getCityStateDiplomacyTable(otherCiv))
        )
        else rightSideTable.add(ScrollPane(getMajorCivDiplomacyTable(otherCiv)))
            .height(stage.height)
    }

    private fun setTrade(civ: CivilizationInfo): TradeTable {
        rightSideTable.clear()
        val tradeTable = TradeTable(civ, this)
        rightSideTable.add(tradeTable)
        return tradeTable
    }

    private fun getCityStateDiplomacyTableHeader(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

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
                val image = ImageGetter.getResourceImage(name, 30f)
                wrapper.add(image).padRight(5f)
                wrapper.add(supplyList.amount.toLabel())
                resourcesTable.add(wrapper).padRight(20f)
                wrapper.addTooltip(name, 18f)
                wrapper.onClick {
                    UncivGame.Current.pushScreen(CivilopediaScreen(UncivGame.Current.gameInfo!!.ruleSet, link = "Resource/$name"))
                }
            }
            diplomacyTable.add(resourcesTable).row()
        }
        diplomacyTable.row().padTop(15f)

        otherCiv.updateAllyCivForCityState()
        var ally = otherCiv.getAllyCiv()
        if (ally != null) {
            if (!viewingCiv.knows(ally))
                ally = "Unknown civilization"
            val allyInfluence = otherCiv.getDiplomacyManager(ally).getInfluence().toInt()
            diplomacyTable
                .add("Ally: [$ally] with [$allyInfluence] Influence".toLabel())
                .row()
        }

        val protectors = otherCiv.getProtectorCivs()
        if (protectors.isNotEmpty()) {
            val newProtectors = listOf<String>()
            for (protector in protectors) {
                if (!viewingCiv.knows(protector))
                    newProtectors.plus("Unknown civilization".tr())
                else
                    newProtectors.plus(protector.civName.tr())
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
        diplomacyTable.add(getRelationshipTable(otherCivDiplomacyManager)).row()
        if (nextLevelString.isNotEmpty()) {
            diplomacyTable.add(nextLevelString.toLabel()).row()
        }
        diplomacyTable.row().padTop(15f)

        var friendBonusText = "When Friends:".tr()+"\n"
        val friendBonusObjects = viewingCiv.cityStateFunctions.getCityStateBonuses(otherCiv.cityStateType, RelationshipLevel.Friend)
        friendBonusText += friendBonusObjects.joinToString(separator = "\n") { it.text.tr() }

        var allyBonusText = "When Allies:".tr()+"\n"
        val allyBonusObjects = viewingCiv.cityStateFunctions.getCityStateBonuses(otherCiv.cityStateType, RelationshipLevel.Ally)
        allyBonusText += allyBonusObjects.joinToString(separator = "\n") { it.text.tr() }

        val relationLevel = otherCivDiplomacyManager.relationshipLevel()
        if (relationLevel >= RelationshipLevel.Friend) {
            // RelationshipChange = Ally -> Friend or Friend -> Favorable
            val turnsToRelationshipChange = otherCivDiplomacyManager.getTurnsToRelationshipChange()
            if (turnsToRelationshipChange != 0)
                diplomacyTable.add("Relationship changes in another [$turnsToRelationshipChange] turns".toLabel())
                    .row()
        }

        val friendBonusLabelColor = if (relationLevel == RelationshipLevel.Friend) Color.GREEN else Color.GRAY
        val friendBonusLabel = friendBonusText.toLabel(friendBonusLabelColor)
            .apply { setAlignment(Align.center) }
        diplomacyTable.add(friendBonusLabel).row()

        val allyBonusLabelColor = if (relationLevel == RelationshipLevel.Ally) Color.GREEN else Color.GRAY
        val allyBonusLabel = allyBonusText.toLabel(allyBonusLabelColor)
            .apply { setAlignment(Align.center) }
        diplomacyTable.add(allyBonusLabel).row()

        if (otherCiv.cityStateUniqueUnit != null) {
            val unitName = otherCiv.cityStateUniqueUnit
            val techName = viewingCiv.gameInfo.ruleSet.units[otherCiv.cityStateUniqueUnit]!!.requiredTech
            diplomacyTable.add("[${otherCiv.civName}] is able to provide [${unitName}] once [${techName}] is researched.".toLabel(fontSize = Constants.defaultFontSize)).row()
        }

        return diplomacyTable
    }

    fun fillUniquePlaceholders(unique:Unique, vararg strings: String):String {
        return unique.placeholderText.fillPlaceholders(*strings) + unique.conditionals.map { " <${it.text}>" }
            .joinToString("")
    }

    private fun getCityStateDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)

        diplomacyTable.addSeparator()

        val giveGiftButton = "Give a Gift".toTextButton()
        giveGiftButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getGoldGiftTable(otherCiv)))
        }
        diplomacyTable.add(giveGiftButton).row()
        if (isNotPlayersTurn() || viewingCiv.isAtWarWith(otherCiv)) giveGiftButton.disable()

        val improveTileButton = getImproveTilesButton(otherCiv, otherCivDiplomacyManager)
        if (improveTileButton != null) diplomacyTable.add(improveTileButton).row()

        if (otherCivDiplomacyManager.diplomaticStatus != DiplomaticStatus.Protector)
            diplomacyTable.add(getPledgeToProtectButton(otherCiv)).row()
        else
            diplomacyTable.add(getRevokeProtectionButton(otherCiv)).row()

        val demandTributeButton = "Demand Tribute".toTextButton()
        demandTributeButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getDemandTributeTable(otherCiv)))
        }
        diplomacyTable.add(demandTributeButton).row()
        if (isNotPlayersTurn() || viewingCiv.isAtWarWith(otherCiv)) demandTributeButton.disable()

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)
        if (!viewingCiv.gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.diplomaticRelationshipsCannotChange)) {
            if (viewingCiv.isAtWarWith(otherCiv))
                diplomacyTable.add(getNegotiatePeaceCityStateButton(otherCiv, diplomacyManager)).row()
            else diplomacyTable.add(getDeclareWarButton(diplomacyManager, otherCiv)).row()
        }

        if (otherCiv.cities.isNotEmpty() && otherCiv.getCapital() != null && viewingCiv.hasExplored(otherCiv.getCapital()!!.location))
            diplomacyTable.add(getGoToOnMapButton(otherCiv)).row()

        val diplomaticMarriageButton = getDiplomaticMarriageButton(otherCiv)
        if (diplomaticMarriageButton != null) diplomacyTable.add(diplomaticMarriageButton).row()

        for (assignedQuest in otherCiv.questManager.assignedQuests.filter { it.assignee == viewingCiv.civName }) {
            diplomacyTable.addSeparator()
            diplomacyTable.add(getQuestTable(assignedQuest)).row()
        }

        for (target in otherCiv.getKnownCivs().filter { otherCiv.questManager.warWithMajorActive(it) }) {
            diplomacyTable.addSeparator()
            diplomacyTable.add(getWarWithMajorTable(target, otherCiv)).row()
        }

        return diplomacyTable
    }

    private fun getRevokeProtectionButton(otherCiv: CivilizationInfo): TextButton {
        val revokeProtectionButton = "Revoke Protection".toTextButton()
        revokeProtectionButton.onClick {
            ConfirmPopup(this, "Revoke protection for [${otherCiv.civName}]?", "Revoke Protection") {
                otherCiv.removeProtectorCiv(viewingCiv)
                updateLeftSideTable(otherCiv)
                updateRightSide(otherCiv)
            }.open()
        }
        if (isNotPlayersTurn() || !otherCiv.otherCivCanWithdrawProtection(viewingCiv)) revokeProtectionButton.disable()
        return revokeProtectionButton
    }

    private fun getPledgeToProtectButton(otherCiv: CivilizationInfo): TextButton {
        val protectionButton = "Pledge to protect".toTextButton()
        protectionButton.onClick {
            ConfirmPopup(
                this,
                "Declare Protection of [${otherCiv.civName}]?",
                "Pledge to protect",
                true
            ) {
                otherCiv.addProtectorCiv(viewingCiv)
                updateLeftSideTable(otherCiv)
                updateRightSide(otherCiv)
            }.open()
        }
        if (isNotPlayersTurn() || !otherCiv.otherCivCanPledgeProtection(viewingCiv)) protectionButton.disable()
        return protectionButton
    }

    private fun getNegotiatePeaceCityStateButton(
        otherCiv: CivilizationInfo,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton {
        val peaceButton = "Negotiate Peace".toTextButton()
        peaceButton.onClick {
            ConfirmPopup(
                this,
                "Peace with [${otherCiv.civName}]?",
                "Negotiate Peace",
                true
            ) {
                val tradeLogic = TradeLogic(viewingCiv, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(
                    TradeOffer(
                        Constants.peaceTreaty,
                        TradeType.Treaty
                    )
                )
                tradeLogic.currentTrade.theirOffers.add(
                    TradeOffer(
                        Constants.peaceTreaty,
                        TradeType.Treaty
                    )
                )
                tradeLogic.acceptTrade()
                updateLeftSideTable(otherCiv)
                updateRightSide(otherCiv)
            }.open()
        }
        val cityStatesAlly = otherCiv.getAllyCiv()
        val atWarWithItsAlly = viewingCiv.getKnownCivs()
            .any { it.civName == cityStatesAlly && it.isAtWarWith(viewingCiv) }
        if (isNotPlayersTurn() || atWarWithItsAlly) peaceButton.disable()

        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar)) {
            peaceButton.disable() // Can't trade for 10 turns after war was declared
            val turnsLeft = otherCivDiplomacyManager.getFlag(DiplomacyFlags.DeclaredWar)
            peaceButton.setText(peaceButton.text.toString() + "\n$turnsLeft" + Fonts.turn)
        }

        return peaceButton
    }

    private fun getImproveTilesButton(
        otherCiv: CivilizationInfo,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton? {
        if (otherCiv.cities.isEmpty()) return null
        val improvableResourceTiles = getImprovableResourceTiles(otherCiv)
        val improvements =
            otherCiv.gameInfo.ruleSet.tileImprovements.filter { it.value.turnsToBuild != 0 }
        var needsImprovements = false

        for (improvableTile in improvableResourceTiles)
            for (tileImprovement in improvements.values)
                if (improvableTile.tileResource.isImprovedBy(tileImprovement.name)
                    && improvableTile.canBuildImprovement(tileImprovement, otherCiv)
                )
                    needsImprovements = true

        if (!needsImprovements) return null


        val improveTileButton = "Gift Improvement".toTextButton()
        improveTileButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getImprovementGiftTable(otherCiv)))
        }


        if (isNotPlayersTurn() || otherCivDiplomacyManager.getInfluence() < 60 || !needsImprovements)
            improveTileButton.disable()
        return improveTileButton
    }

    private fun getDiplomaticMarriageButton(otherCiv: CivilizationInfo): TextButton? {
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
        if (isNotPlayersTurn() || !otherCiv.cityStateFunctions.canBeMarriedBy(viewingCiv)) diplomaticMarriageButton.disable()
        return diplomaticMarriageButton
    }

    private fun getGoldGiftTable(otherCiv: CivilizationInfo): Table {
        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)
        diplomacyTable.addSeparator()

        for (giftAmount in listOf(250, 500, 1000)) {
            val influenceAmount = otherCiv.cityStateFunctions.influenceGainedByGift(viewingCiv, giftAmount)
            val giftButton =
                "Gift [$giftAmount] gold (+[$influenceAmount] influence)".toTextButton()
            giftButton.onClick {
                otherCiv.receiveGoldGift(viewingCiv, giftAmount)
                updateLeftSideTable(otherCiv)
                updateRightSide(otherCiv)
            }
            diplomacyTable.add(giftButton).row()
            if (viewingCiv.gold < giftAmount || isNotPlayersTurn()) giftButton.disable()
        }

        val backButton = "Back".toTextButton()
        backButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(backButton)
        return diplomacyTable
    }

    fun getImprovableResourceTiles(otherCiv:CivilizationInfo) = otherCiv.getCapital()!!.getTiles().filter {
        it.hasViewableResource(otherCiv)
        && it.tileResource.resourceType != ResourceType.Bonus
        && (it.improvement == null || !it.tileResource.isImprovedBy(it.improvement!!))
    }

    private fun getImprovementGiftTable(otherCiv: CivilizationInfo): Table {
        val improvementGiftTable = getCityStateDiplomacyTableHeader(otherCiv)
        improvementGiftTable.addSeparator()

        val improvableResourceTiles = getImprovableResourceTiles(otherCiv)
        val tileImprovements =
            otherCiv.gameInfo.ruleSet.tileImprovements

        for (improvableTile in improvableResourceTiles) {
            for (tileImprovement in tileImprovements.values) {
                if (improvableTile.tileResource.isImprovedBy(tileImprovement.name)
                    && improvableTile.canBuildImprovement(tileImprovement, otherCiv)
                ) {
                    val improveTileButton =
                        "Build [${tileImprovement}] on [${improvableTile.tileResource}] (200 Gold)".toTextButton()
                    improveTileButton.onClick {
                        viewingCiv.addGold(-200)
                        improvableTile.stopWorkingOnImprovement()
                        improvableTile.changeImprovement(tileImprovement.name)
                        otherCiv.updateDetailedCivResources()
                        rightSideTable.clear()
                        rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
                    }
                    if (viewingCiv.gold < 200)
                        improveTileButton.disable()
                    improvementGiftTable.add(improveTileButton).row()
                }
            }
        }

        val backButton = "Back".toTextButton()
        backButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        improvementGiftTable.add(backButton)
        return improvementGiftTable

    }

    private fun getDemandTributeTable(otherCiv: CivilizationInfo): Table {
        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)
        diplomacyTable.addSeparator()
        diplomacyTable.add("Tribute Willingness".toLabel()).row()
        val modifierTable = Table()
        val tributeModifiers = otherCiv.cityStateFunctions.getTributeModifiers(viewingCiv, requireWholeList = true)
        for (item in tributeModifiers) {
            val color = if (item.value >= 0) Color.GREEN else Color.RED
            modifierTable.add(item.key.toLabel(color))
            modifierTable.add(item.value.toString().toLabel(color)).row()
        }
        modifierTable.add("Sum:".toLabel())
        modifierTable.add(tributeModifiers.values.sum().toLabel()).row()
        diplomacyTable.add(modifierTable).row()
        diplomacyTable.add("At least 0 to take gold, at least 30 and size 4 city for worker".toLabel()).row()
        diplomacyTable.addSeparator()

        val demandGoldButton = "Take [${otherCiv.cityStateFunctions.goldGainedByTribute()}] gold (-15 Influence)".toTextButton()
        demandGoldButton.onClick {
            otherCiv.cityStateFunctions.tributeGold(viewingCiv)
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandGoldButton).row()
        if (otherCiv.getTributeWillingness(viewingCiv, demandingWorker = false) < 0)   demandGoldButton.disable()

        val demandWorkerButton = "Take worker (-50 Influence)".toTextButton()
        demandWorkerButton.onClick {
            otherCiv.cityStateFunctions.tributeWorker(viewingCiv)
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandWorkerButton).row()
        if (otherCiv.getTributeWillingness(viewingCiv, demandingWorker = true) < 0)    demandWorkerButton.disable()

        val backButton = "Back".toTextButton()
        backButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(backButton)
        return diplomacyTable
    }

    private fun getQuestTable(assignedQuest: AssignedQuest): Table {
        val questTable = Table()
        questTable.defaults().pad(10f)

        val quest: Quest = viewingCiv.gameInfo.ruleSet.quests[assignedQuest.questName]!!
        val remainingTurns: Int = assignedQuest.getRemainingTurns()
        val title = if (quest.influence > 0)
            "[${quest.name}] (+[${quest.influence.toInt()}] influence)"
        else
            quest.name
        val description = assignedQuest.getDescription()

        questTable.add(title.toLabel(fontSize = Constants.headingFontSize)).row()
        questTable.add(description.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(stage.width / 2).row()
        if (quest.duration > 0)
            questTable.add("[${remainingTurns}] turns remaining".toLabel()).row()
        if (quest.isGlobal()) {
            val leaderString = viewingCiv.gameInfo.getCivilization(assignedQuest.assigner).questManager.getLeaderStringForQuest(assignedQuest.questName)
            if (leaderString != "")
                questTable.add(leaderString.toLabel()).row()
        }

        questTable.onClick {
            assignedQuest.onClickAction()
        }
        return questTable
    }

    private fun getWarWithMajorTable(target: CivilizationInfo, otherCiv: CivilizationInfo): Table {
        val warTable = Table()
        warTable.defaults().pad(10f)

        val title = "War against [${target.civName}]"
        val description = "We need you to help us defend against [${target.civName}]. Killing [${otherCiv.questManager.unitsToKill(target)}] of their military units would slow their offensive."
        val progress = if (viewingCiv.knows(target)) "Currently you have killed [${otherCiv.questManager.unitsKilledSoFar(target, viewingCiv)}] of their military units."
            else "You need to find them first!"

        warTable.add(title.toLabel(fontSize = Constants.headingFontSize)).row()
        warTable.add(description.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(stage.width / 2).row()
        warTable.add(progress.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(stage.width / 2).row()

        return warTable
    }


    private fun getMajorCivDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)

        val helloText = if (otherCivDiplomacyManager.relationshipLevel() <= RelationshipLevel.Enemy)
            otherCiv.nation.hateHello
        else otherCiv.nation.neutralHello
        val leaderIntroTable = LeaderIntroTable(otherCiv, helloText)
        diplomacyTable.add(leaderIntroTable).row()
        diplomacyTable.addSeparator()

        val diplomaticRelationshipsCanChange =
            !viewingCiv.gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.diplomaticRelationshipsCannotChange)

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)

        if (!viewingCiv.isAtWarWith(otherCiv)) {
            diplomacyTable.add(getTradeButton(otherCiv)).row()


            if (!diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
                diplomacyTable.add(getDeclareFriendshipButton(otherCiv)).row()


            if (viewingCiv.canSignResearchAgreementsWith(otherCiv))
                diplomacyTable.add(getResearchAgreementButton(otherCiv)).row()

            if (!diplomacyManager.hasFlag(DiplomacyFlags.Denunciation)
                    && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            ) diplomacyTable.add(getDenounceButton(otherCiv, diplomacyManager)).row()

            if (diplomaticRelationshipsCanChange)
                diplomacyTable.add(getDeclareWarButton(diplomacyManager, otherCiv)).row()

        } else if (diplomaticRelationshipsCanChange) {
            val negotiatePeaceButton =
                    getNegotiatePeaceMajorCivButton(otherCiv, otherCivDiplomacyManager)

            diplomacyTable.add(negotiatePeaceButton).row()
        }


        val demandsButton = "Demands".toTextButton()
        demandsButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(getDemandsTable(viewingCiv, otherCiv))
        }
        diplomacyTable.add(demandsButton).row()
        if (isNotPlayersTurn()) demandsButton.disable()

        if (otherCiv.cities.isNotEmpty() && otherCiv.getCapital() != null && viewingCiv.hasExplored(otherCiv.getCapital()!!.location))
            diplomacyTable.add(getGoToOnMapButton(otherCiv)).row()

        if (!otherCiv.isPlayerCivilization()) { // human players make their own choices
            diplomacyTable.add(getRelationshipTable(otherCivDiplomacyManager)).row()
            diplomacyTable.add(getDiplomacyModifiersTable(otherCivDiplomacyManager)).row()
            val promisesTable = getPromisesTable(diplomacyManager, otherCivDiplomacyManager)
            if (promisesTable != null) diplomacyTable.add(promisesTable).row()
        }

        UncivGame.Current.musicController.chooseTrack(otherCiv.civName,
            MusicMood.peaceOrWar(viewingCiv.isAtWarWith(otherCiv)), MusicTrackChooserFlags.setSelectNation)

        return diplomacyTable
    }

    private fun getNegotiatePeaceMajorCivButton(
        otherCiv: CivilizationInfo,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton {
        val negotiatePeaceButton = "Negotiate Peace".toTextButton()
        negotiatePeaceButton.onClick {
            val tradeTable = setTrade(otherCiv)
            val peaceTreaty = TradeOffer(Constants.peaceTreaty, TradeType.Treaty)
            tradeTable.tradeLogic.currentTrade.theirOffers.add(peaceTreaty)
            tradeTable.tradeLogic.currentTrade.ourOffers.add(peaceTreaty)
            tradeTable.offerColumnsTable.update()
        }

        if (isNotPlayersTurn()) negotiatePeaceButton.disable()

        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar)) {
            negotiatePeaceButton.disable() // Can't trade for 10 turns after war was declared
            val turnsLeft = otherCivDiplomacyManager.getFlag(DiplomacyFlags.DeclaredWar)
            negotiatePeaceButton.setText(negotiatePeaceButton.text.toString() + "\n$turnsLeft" + Fonts.turn)
        }
        return negotiatePeaceButton
    }

    private fun getDenounceButton(
        otherCiv: CivilizationInfo,
        diplomacyManager: DiplomacyManager
    ): TextButton {
        val denounceButton = "Denounce ([30] turns)".toTextButton()
        denounceButton.onClick {
            ConfirmPopup(this, "Denounce [${otherCiv.civName}]?", "Denounce ([30] turns)") {
                diplomacyManager.denounce()
                updateLeftSideTable(otherCiv)
                setRightSideFlavorText(otherCiv, "We will remember this.", "Very well.")
            }.open()
        }
        if (isNotPlayersTurn()) denounceButton.disable()
        return denounceButton
    }

    private fun getResearchAgreementButton(otherCiv: CivilizationInfo): TextButton {
        val researchAgreementButton = "Research Agreement".toTextButton()

        val requiredGold = viewingCiv.getResearchAgreementCost()
        researchAgreementButton.onClick {
            val tradeTable = setTrade(otherCiv)
            val researchAgreement =
                    TradeOffer(Constants.researchAgreement, TradeType.Treaty, requiredGold)
            val goldCostOfSignResearchAgreement =
                    TradeOffer("Gold".tr(), TradeType.Gold, -requiredGold)
            tradeTable.tradeLogic.currentTrade.theirOffers.add(researchAgreement)
            tradeTable.tradeLogic.ourAvailableOffers.add(researchAgreement)
            tradeTable.tradeLogic.ourAvailableOffers.add(goldCostOfSignResearchAgreement)
            tradeTable.tradeLogic.currentTrade.ourOffers.add(researchAgreement)
            tradeTable.tradeLogic.theirAvailableOffers.add(researchAgreement)
            tradeTable.tradeLogic.theirAvailableOffers.add(goldCostOfSignResearchAgreement)
            tradeTable.offerColumnsTable.update()
        }
        if (isNotPlayersTurn()) researchAgreementButton.disable()
        return researchAgreementButton
    }

    private fun getDeclareFriendshipButton(otherCiv: CivilizationInfo): TextButton {
        val declareFriendshipButton =
                "Offer Declaration of Friendship ([30] turns)".toTextButton()
        declareFriendshipButton.onClick {
            otherCiv.popupAlerts.add(
                PopupAlert(
                    AlertType.DeclarationOfFriendship,
                    viewingCiv.civName
                )
            )
            declareFriendshipButton.disable()
        }
        if (isNotPlayersTurn() || otherCiv.popupAlerts
                    .any { it.type == AlertType.DeclarationOfFriendship && it.value == viewingCiv.civName }
        )
            declareFriendshipButton.disable()
        return declareFriendshipButton
    }

    private fun getTradeButton(otherCiv: CivilizationInfo): TextButton {
        val tradeButton = "Trade".toTextButton()
        tradeButton.onClick {
            setTrade(otherCiv).apply {
                tradeLogic.ourAvailableOffers.apply { remove(firstOrNull { it.type == TradeType.Treaty }) }
                tradeLogic.theirAvailableOffers.apply { remove(firstOrNull { it.type == TradeType.Treaty }) }
                offerColumnsTable.update()
            }
        }
        if (isNotPlayersTurn()) tradeButton.disable()
        return tradeButton
    }

    private fun getPromisesTable(
        diplomacyManager: DiplomacyManager,
        otherCivDiplomacyManager: DiplomacyManager
    ): Table? {
        val promisesTable = Table()

        // Not for (flag in DiplomacyFlags.values()) - all other flags should result in DiplomaticModifiers or stay internal?
        val flag = DiplomacyFlags.AgreedToNotSettleNearUs
        if (otherCivDiplomacyManager.hasFlag(flag)) {
            val text =
                "We promised not to settle near them ([${otherCivDiplomacyManager.getFlag(flag)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }
        if (diplomacyManager.hasFlag(flag)) {
            val text =
                "They promised not to settle near us ([${diplomacyManager.getFlag(flag)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }

        return if (promisesTable.cells.isEmpty) null else promisesTable
    }

    private fun getDiplomacyModifiersTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val diplomacyModifiersTable = Table()
        for (modifier in otherCivDiplomacyManager.diplomaticModifiers) {
            // Angry about attacked CS and destroyed CS do not stack
            if (modifier.key == AttackedProtectedMinor.name
                && otherCivDiplomacyManager.hasModifier(DestroyedProtectedMinor))
                continue

            var text = when (DiplomaticModifiers.valueOf(modifier.key)) {
                DeclaredWarOnUs -> "You declared war on us!"
                WarMongerer -> "Your warmongering ways are unacceptable to us."
                LiberatedCity -> "We applaud your liberation of conquered cities!"
                CapturedOurCities -> "You have captured our cities!"
                YearsOfPeace -> "Years of peace have strengthened our relations."
                SharedEnemy -> "Our mutual military struggle brings us closer together."
                DeclarationOfFriendship -> "We have signed a public declaration of friendship"
                DeclaredFriendshipWithOurEnemies -> "You have declared friendship with our enemies!"
                DeclaredFriendshipWithOurAllies -> "You have declared friendship with our allies"
                OpenBorders -> "Our open borders have brought us closer together."
                BetrayedDeclarationOfFriendship -> "Your so-called 'friendship' is worth nothing."
                Denunciation -> "You have publicly denounced us!"
                DenouncedOurAllies -> "You have denounced our allies"
                DenouncedOurEnemies -> "You have denounced our enemies"
                BetrayedPromiseToNotSettleCitiesNearUs -> "You betrayed your promise to not settle cities near us"
                RefusedToNotSettleCitiesNearUs -> "You refused to stop settling cities near us"
                FulfilledPromiseToNotSettleCitiesNearUs -> "You fulfilled your promise to stop settling cities near us!"
                UnacceptableDemands -> "Your arrogant demands are in bad taste"
                UsedNuclearWeapons -> "Your use of nuclear weapons is disgusting!"
                StealingTerritory -> "You have stolen our lands!"
                GaveUsUnits -> "You gave us units!"
                DestroyedProtectedMinor -> "You destroyed City-States that were under our protection!"
                AttackedProtectedMinor -> "You attacked City-States that were under our protection!"
                BulliedProtectedMinor -> "You demanded tribute from City-States that were under our protection!"
                SidedWithProtectedMinor -> "You sided with a City-State over us"
                ReturnedCapturedUnits -> "You returned captured units to us"
            }
            text = text.tr() + " "
            if (modifier.value > 0) text += "+"
            text += modifier.value.roundToInt()
            val color = if (modifier.value < 0) Color.RED else Color.GREEN
            diplomacyModifiersTable.add(text.toLabel(color)).row()
        }
        return diplomacyModifiersTable
    }

    private fun getDemandsTable(viewingCiv: CivilizationInfo, otherCiv: CivilizationInfo): Table {
        val demandsTable = Table()
        demandsTable.defaults().pad(10f)

        val dontSettleCitiesButton = "Please don't settle new cities near us.".toTextButton()
        if (otherCiv.popupAlerts.any { it.type == AlertType.DemandToStopSettlingCitiesNear && it.value == viewingCiv.civName })
            dontSettleCitiesButton.disable()
        dontSettleCitiesButton.onClick {
            otherCiv.popupAlerts.add(
                PopupAlert(
                    AlertType.DemandToStopSettlingCitiesNear,
                    viewingCiv.civName
                )
            )
            dontSettleCitiesButton.disable()
        }
        demandsTable.add(dontSettleCitiesButton).row()

        demandsTable.add(Constants.close.toTextButton().onClick { updateRightSide(otherCiv) })
        return demandsTable
    }

    private fun getRelationshipTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val relationshipTable = Table()

        val opinionOfUs =
            if (otherCivDiplomacyManager.civInfo.isCityState()) otherCivDiplomacyManager.getInfluence().toInt()
            else otherCivDiplomacyManager.opinionOfOtherCiv().toInt()

        relationshipTable.add("{Our relationship}: ".toLabel())
        val relationshipLevel = otherCivDiplomacyManager.relationshipLevel()
        val relationshipText = relationshipLevel.name.tr() + " ($opinionOfUs)"
        val relationshipColor = when (relationshipLevel) {
            RelationshipLevel.Neutral -> Color.WHITE
            RelationshipLevel.Favorable, RelationshipLevel.Friend,
            RelationshipLevel.Ally -> Color.GREEN
            RelationshipLevel.Afraid -> Color.YELLOW
            else -> Color.RED
        }

        relationshipTable.add(relationshipText.toLabel(relationshipColor)).row()
        if (otherCivDiplomacyManager.civInfo.isCityState())
            relationshipTable.add(
                CityButton.getInfluenceBar(
                    otherCivDiplomacyManager.getInfluence(),
                    relationshipLevel,
                    200f, 10f
                )
            ).colspan(2).pad(5f)
        return relationshipTable
    }

    private fun getDeclareWarButton(
        diplomacyManager: DiplomacyManager,
        otherCiv: CivilizationInfo
    ): TextButton {
        val declareWarButton = "Declare war".toTextButton(skin.get("negative", TextButton.TextButtonStyle::class.java))
        val turnsToPeaceTreaty = diplomacyManager.turnsToPeaceTreaty()
        if (turnsToPeaceTreaty > 0) {
            declareWarButton.disable()
            declareWarButton.setText(declareWarButton.text.toString() + " ($turnsToPeaceTreaty${Fonts.turn})")
        }
        declareWarButton.onClick {
            ConfirmPopup(this, "Declare war on [${otherCiv.civName}]?", "Declare war") {
                diplomacyManager.declareWar()
                setRightSideFlavorText(otherCiv, otherCiv.nation.attacked, "Very well.")
                updateLeftSideTable(otherCiv)
                UncivGame.Current.musicController.chooseTrack(otherCiv.civName, MusicMood.War, MusicTrackChooserFlags.setSpecific)
            }.open()
        }
        if (isNotPlayersTurn()) declareWarButton.disable()
        return declareWarButton
    }

    // response currently always gets "Very Well.", but that may expand in the future.
    @Suppress("SameParameterValue")
    private fun setRightSideFlavorText(
        otherCiv: CivilizationInfo,
        flavorText: String,
        response: String
    ) {
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        diplomacyTable.add(LeaderIntroTable(otherCiv))
        diplomacyTable.addSeparator()
        diplomacyTable.add(flavorText.toLabel()).row()

        val responseButton = response.toTextButton()
        responseButton.onActivation { updateRightSide(otherCiv) }
        responseButton.keyShortcuts.add(KeyCharAndCode.SPACE)
        diplomacyTable.add(responseButton)

        rightSideTable.clear()
        rightSideTable.add(diplomacyTable)
    }

    private fun getGoToOnMapButton(civilization: CivilizationInfo): TextButton {
        val goToOnMapButton = "Go to on map".toTextButton()
        goToOnMapButton.onClick {
            val worldScreen = UncivGame.Current.resetToWorldScreen()
            worldScreen.mapHolder.setCenterPosition(civilization.getCapital()!!.location, selectUnit = false)
        }
        return goToOnMapButton
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        positionCloseButton()
    }

    override fun recreate(): BaseScreen = DiplomacyScreen(viewingCiv, selectCiv, selectTrade)
}
