package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.ConfirmPopup
import kotlin.math.roundToInt

class MajorCivDiplomacyTable(private val diplomacyScreen: DiplomacyScreen) {
    val viewingCiv = diplomacyScreen.viewingCiv

    fun getMajorCivDiplomacyTable(otherCiv: Civilization): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)!!

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)

        val helloText: String
        val helloVoice: String
        if (otherCivDiplomacyManager.isRelationshipLevelLE(RelationshipLevel.Enemy)) {
            helloText = otherCiv.nation.hateHello
            helloVoice = "${otherCiv.civName}.hateHello"
        } else {
            helloText = otherCiv.nation.neutralHello
            helloVoice = "${otherCiv.civName}.neutralHello"
        }
        val leaderIntroTable = LeaderIntroTable(otherCiv, helloText)
        diplomacyTable.add(leaderIntroTable).row()
        diplomacyTable.addSeparator()

        val diplomaticRelationshipsCanChange =
            !viewingCiv.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)!!

        if (!viewingCiv.isAtWarWith(otherCiv)) {
            diplomacyTable.add(getTradeButton(otherCiv)).row()


            if (!diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
                diplomacyTable.add(getDeclareFriendshipButton(otherCiv)).row()

            if (!diplomacyManager.hasFlag(DiplomacyFlags.Denunciation)
                && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            ) diplomacyTable.add(getDenounceButton(otherCiv, diplomacyManager)).row()

            if (diplomaticRelationshipsCanChange)
                diplomacyTable.add(diplomacyScreen.getDeclareWarButton(diplomacyManager, otherCiv)).row()

        } else if (diplomaticRelationshipsCanChange) {
            val negotiatePeaceButton =
                getNegotiatePeaceMajorCivButton(otherCiv, otherCivDiplomacyManager)

            diplomacyTable.add(negotiatePeaceButton).row()
        }


        val demandsButton = "Demands".toTextButton()
        demandsButton.onClick {
            diplomacyScreen.rightSideTable.clear()
            diplomacyScreen.rightSideTable.add(getDemandsTable(viewingCiv, otherCiv))
        }
        diplomacyTable.add(demandsButton).row()
        if (diplomacyScreen.isNotPlayersTurn()) demandsButton.disable()

        if (otherCiv.getCapital() != null && viewingCiv.hasExplored(otherCiv.getCapital()!!.getCenterTile()))
            diplomacyTable.add(diplomacyScreen.getGoToOnMapButton(otherCiv)).row()

        if (otherCiv.isHuman())
            diplomacyTable.add(diplomacyScreen.getHumanRelationshipTable(otherCivDiplomacyManager)).row()
        else { 
            diplomacyTable.add(diplomacyScreen.getRelationshipTable(otherCivDiplomacyManager)).row()
            diplomacyTable.add(getDiplomacyModifiersTable(otherCivDiplomacyManager)).row()
            val promisesTable = getPromisesTable(diplomacyManager, otherCivDiplomacyManager)
            if (promisesTable != null) diplomacyTable.add(promisesTable).row()
        }

        // Starting playback here assumes the MajorCivDiplomacyTable is shown immediately
        UncivGame.Current.musicController.playVoice(helloVoice)

        return diplomacyTable
    }


    private fun getNegotiatePeaceMajorCivButton(
        otherCiv: Civilization,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton {
        val negotiatePeaceButton = "Negotiate Peace".toTextButton()
        negotiatePeaceButton.onClick {
            val tradeTable = diplomacyScreen.setTrade(otherCiv)
            val peaceTreaty = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = viewingCiv.gameInfo.speed)
            tradeTable.tradeLogic.currentTrade.theirOffers.add(peaceTreaty)
            tradeTable.tradeLogic.currentTrade.ourOffers.add(peaceTreaty)
            tradeTable.offerColumnsTable.update()
            tradeTable.enableOfferButton(true)
        }

        if (diplomacyScreen.isNotPlayersTurn()) negotiatePeaceButton.disable()

        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar)) {
            negotiatePeaceButton.disable() // Can't trade for 10 turns after war was declared
            val turnsLeft = otherCivDiplomacyManager.getFlag(DiplomacyFlags.DeclaredWar)
            negotiatePeaceButton.setText(negotiatePeaceButton.text.toString() + "\n${turnsLeft.tr()}" + Fonts.turn)
        }
        return negotiatePeaceButton
    }

    private fun getDenounceButton(
        otherCiv: Civilization,
        diplomacyManager: DiplomacyManager
    ): TextButton {
        val denounceButton = "Denounce ([30] turns)".toTextButton()
        denounceButton.onClick {
            ConfirmPopup(diplomacyScreen, "Denounce [${otherCiv.civName}]?", "Denounce ([30] turns)") {
                diplomacyManager.denounce()
                diplomacyScreen.updateLeftSideTable(otherCiv)
                diplomacyScreen.setRightSideFlavorText(
                    otherCiv,
                    if (otherCiv.nation.denounced.isNotEmpty()) otherCiv.nation.denounced else "We will remember this.",
                    "Very well."
                )

                val music = UncivGame.Current.musicController
                music.playVoice("${otherCiv.nation.name}.denounced")
            }.open()
        }
        if (diplomacyScreen.isNotPlayersTurn()) denounceButton.disable()
        return denounceButton
    }

    private fun getDeclareFriendshipButton(otherCiv: Civilization): TextButton {
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
        if (diplomacyScreen.isNotPlayersTurn() || otherCiv.popupAlerts
                .any { it.type == AlertType.DeclarationOfFriendship && it.value == viewingCiv.civName }
        )
            declareFriendshipButton.disable()
        return declareFriendshipButton
    }

    private fun getTradeButton(otherCiv: Civilization): TextButton {
        val tradeButton = "Trade".toTextButton()
        tradeButton.onClick {
            diplomacyScreen.setTrade(otherCiv).apply {
                offerColumnsTable.update()
            }
        }
        if (diplomacyScreen.isNotPlayersTurn()) tradeButton.disable()
        return tradeButton
    }

    private fun getPromisesTable(
        diplomacyManager: DiplomacyManager,
        otherCivDiplomacyManager: DiplomacyManager
    ): Table? {
        val promisesTable = Table()

        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs)) {
            val text =
                "We promised not to settle near them ([${otherCivDiplomacyManager.getFlag(DiplomacyFlags.AgreedToNotSettleNearUs)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }
        if (diplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs)) {
            val text =
                "They promised not to settle near us ([${diplomacyManager.getFlag(DiplomacyFlags.AgreedToNotSettleNearUs)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }

        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSpreadReligion)) {
            val text =
                "We promised not to spread religion to them ([${otherCivDiplomacyManager.getFlag(DiplomacyFlags.AgreedToNotSpreadReligion)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }
        if (diplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSpreadReligion)) {
            val text =
                "They promised not to spread religion to us ([${diplomacyManager.getFlag(DiplomacyFlags.AgreedToNotSpreadReligion)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }
        if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSendSpies)) {
            val text =
                "We promised not to send spies to them ([${otherCivDiplomacyManager.getFlag(DiplomacyFlags.AgreedToNotSendSpies)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }
        if (diplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSendSpies)) {
            val text =
                "They promised not to send spies to us ([${diplomacyManager.getFlag(DiplomacyFlags.AgreedToNotSendSpies)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }

        return if (promisesTable.cells.isEmpty) null else promisesTable
    }

    private fun getDiplomacyModifiersTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val diplomacyModifiersTable = Table()
        for (modifier in otherCivDiplomacyManager.diplomaticModifiers) {
            // Angry about attacked CS and destroyed CS do not stack
            if (modifier.key == DiplomaticModifiers.AttackedProtectedMinor.name
                && otherCivDiplomacyManager.hasModifier(DiplomaticModifiers.DestroyedProtectedMinor))
                continue

            val diplomaticModifier = DiplomaticModifiers.safeValueOf(modifier.key)
                ?: continue // This modifier is from the future, you cannot understand it yet
            var text = diplomaticModifier.text.tr() + " "
            if (modifier.value > 0) text += "+"
            text += modifier.value.roundToInt()
            val color = if (modifier.value < 0) Color.RED else Color.GREEN
            diplomacyModifiersTable.add(text.toLabel(color)).row()
        }
        return diplomacyModifiersTable
    }

    private fun getDemandsTable(viewingCiv: Civilization, otherCiv: Civilization): Table {
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

        val dontSpreadReligionButton = "Please don't spread your religion to us.".toTextButton()
        if (otherCiv.popupAlerts.any { it.type == AlertType.DemandToStopSpreadingReligion && it.value == viewingCiv.civName })
            dontSpreadReligionButton.disable()
        dontSpreadReligionButton.onClick {
            otherCiv.popupAlerts.add(
                PopupAlert(
                    AlertType.DemandToStopSpreadingReligion,
                    viewingCiv.civName
                )
            )
            dontSpreadReligionButton.disable()
        }
        demandsTable.add(dontSpreadReligionButton).row()
        
        if (viewingCiv.gameInfo.gameParameters.espionageEnabled) {
            val dontSpyButton = "Stop spying on us.".toTextButton()
            val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)!!
            if (otherCiv.popupAlerts.any { it.type == AlertType.DemandToStopSpyingOnUs && it.value == viewingCiv.civName} ||
                diplomacyManager.hasFlag(DiplomacyFlags.AgreedToNotSendSpies))
                dontSpyButton.disable()
            dontSpyButton.onClick {
                otherCiv.popupAlerts.add(
                    PopupAlert(
                        AlertType.DemandToStopSpyingOnUs,
                        viewingCiv.civName
                    )
                )
                dontSpyButton.disable()
            }
            demandsTable.add(dontSpyButton).row()
        }

        demandsTable.add(Constants.close.toTextButton().onClick { diplomacyScreen.updateRightSide(otherCiv) })
        return demandsTable
    }
}
