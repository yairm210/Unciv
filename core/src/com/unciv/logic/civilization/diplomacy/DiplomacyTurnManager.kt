package com.unciv.logic.civilization.diplomacy

import com.unciv.Constants
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.Readonly
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

object DiplomacyTurnManager {

    fun DiplomacyManager.nextTurn() {
        nextTurnTrades()
        removeUntenableTrades()
        updateHasOpenBorders()
        nextTurnDiplomaticModifiers()
        nextTurnFlags()
        if (civInfo.isCityState && otherCiv().isMajorCiv())
            nextTurnCityStateInfluence()
    }

    private fun DiplomacyManager.removeUntenableTrades() {
        for (trade in trades.toList()) {

            // Every cancelled trade can change this - if 1 resource is missing,
            // don't cancel all trades of that resource, only cancel one (the first one, as it happens, since they're added chronologically)
            val negativeCivResources = civInfo.getCivResourceSupply()
                .filter { it.amount < 0 && !it.resource.isStockpiled }.map { it.resource.name }

            for (offer in trade.ourOffers) {
                if (offer.type in listOf(TradeOfferType.Luxury_Resource, TradeOfferType.Strategic_Resource)
                    && (offer.name in negativeCivResources || !civInfo.gameInfo.ruleset.tileResources.containsKey(offer.name))
                ) {

                    trades.remove(trade)
                    val otherCivTrades = otherCiv().getDiplomacyManager(civInfo)!!.trades
                    otherCivTrades.removeAll { it.equalTrade(trade.reverse()) }

                    // Can't cut short peace treaties!
                    if (trade.theirOffers.any { it.name == Constants.peaceTreaty }) {
                        remakePeaceTreaty(trade.theirOffers.first { it.name == Constants.peaceTreaty }.duration)
                    }

                    civInfo.addNotification("One of our trades with [$otherCivName] has been cut short",
                        DiplomacyAction(otherCivName, true),
                        NotificationCategory.Trade, NotificationIcon.Trade, otherCivName)
                    otherCiv().addNotification("One of our trades with [${civInfo.civName}] has been cut short",
                        DiplomacyAction(civInfo.civName, true),
                        NotificationCategory.Trade, NotificationIcon.Trade, civInfo.civName)
                    // If you cut a trade short, we're not going to trust you with per-turn trades for a while
                    otherCivDiplomacy().setFlag(DiplomacyFlags.ResourceTradesCutShort, civInfo.gameInfo.speed.dealDuration * 2)
                    civInfo.cache.updateCivResources()
                }
            }
        }
    }

    private fun DiplomacyManager.remakePeaceTreaty(durationLeft: Int) {
        val treaty = Trade()
        treaty.ourOffers.add(
            TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, duration = durationLeft)
        )
        treaty.theirOffers.add(
            TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, duration = durationLeft)
        )
        trades.add(treaty)
        otherCiv().getDiplomacyManager(civInfo)!!.trades.add(treaty)
    }


    private fun DiplomacyManager.nextTurnCityStateInfluence() {
        val initialRelationshipLevel = relationshipIgnoreAfraid()  // Enough since only >= Friend is notified

        val restingPoint = getCityStateInfluenceRestingPoint()
        // We don't use `getInfluence()` here, as then during war with the ally of this CS,
        // our influence would be set to -59, overwriting the old value, which we want to keep
        // as it should be restored once the war ends (though we keep influence degradation from time during the war)
        if (influence > restingPoint) {
            val decrement = getCityStateInfluenceDegrade()
            setInfluence(max(restingPoint, influence - decrement))
        } else if (influence < restingPoint) {
            val increment = getCityStateInfluenceRecovery()
            setInfluence(min(restingPoint, influence + increment))
        }

        if (!civInfo.isDefeated()) { // don't display city state relationship notifications when the city state is currently defeated
            val notificationActions = civInfo.cityStateFunctions.getNotificationActions()
            if (getTurnsToRelationshipChange() == 1) {
                val text = "Your relationship with [${civInfo.civName}] is about to degrade"
                otherCiv().addNotification(text, notificationActions, NotificationCategory.Diplomacy, civInfo.civName, NotificationIcon.Diplomacy)
            }

            if (initialRelationshipLevel >= RelationshipLevel.Friend && initialRelationshipLevel != relationshipIgnoreAfraid()) {
                val text = "Your relationship with [${civInfo.civName}] degraded"
                otherCiv().addNotification(text, notificationActions, NotificationCategory.Diplomacy, civInfo.civName, NotificationIcon.Diplomacy)
            }

            // Potentially notify about afraid status
            if (getInfluence() < 30  // We usually don't want to bully our friends
                && !hasFlag(DiplomacyFlags.NotifiedAfraid)
                && civInfo.cityStateFunctions.getTributeWillingness(otherCiv()) > 0
                && otherCiv().isMajorCiv()
            ) {
                setFlag(DiplomacyFlags.NotifiedAfraid, 20)  // Wait 20 turns until next reminder
                val text = "[${civInfo.civName}] is afraid of your military power!"
                otherCiv().addNotification(text, notificationActions, NotificationCategory.Diplomacy, civInfo.civName, NotificationIcon.Diplomacy)
            }
        }
    }

    @Readonly
    private fun DiplomacyManager.getCityStateInfluenceRecovery(): Float {
        if (getInfluence() >= getCityStateInfluenceRestingPoint())
            return 0f

        val increment = 1f  // sic: personality does not matter here

        var modifierPercent = 0f

        if (otherCiv().hasUnique(UniqueType.CityStateInfluenceRecoversTwiceNormalRate))
            modifierPercent += 100f

        val religion = if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) null
        else civInfo.getCapital()!!.religion.getMajorityReligionName()
        if (religion != null && religion == otherCiv().religionManager.religion?.name)
            modifierPercent += 50f  // 50% quicker recovery when sharing a religion

        return max(0f, increment) * max(0f, modifierPercent).toPercent()
    }

    private fun DiplomacyManager.nextTurnFlags() {
        loop@ for (flag in flagsCountdown.keys.toList()) {
            // We want negative flags to keep on going negative to keep track of time
            flagsCountdown[flag] = flagsCountdown[flag]!! - 1

            // If we have uniques that make city states grant military units faster when at war with a common enemy, add higher numbers to this flag
            if (flag == DiplomacyFlags.ProvideMilitaryUnit.name && civInfo.isMajorCiv() && otherCiv().isCityState &&
                civInfo.gameInfo.civilizations.any { civInfo.isAtWarWith(it) && otherCiv().isAtWarWith(it) }) {
                for (unique in civInfo.getMatchingUniques(UniqueType.CityStateMoreGiftedUnits)) {
                    flagsCountdown[DiplomacyFlags.ProvideMilitaryUnit.name] =
                        flagsCountdown[DiplomacyFlags.ProvideMilitaryUnit.name]!! - unique.params[0].toInt() + 1
                    if (flagsCountdown[DiplomacyFlags.ProvideMilitaryUnit.name]!! <= 0) {
                        flagsCountdown[DiplomacyFlags.ProvideMilitaryUnit.name] = 0
                        break
                    }
                }
            }

            // At the end of every turn
            if (flag == DiplomacyFlags.ResearchAgreement.name)
                totalOfScienceDuringRA += civInfo.stats.statsForNextTurn.science.toInt()

            // These modifiers decrease slightly @ 50
            if (flagsCountdown[flag] == 50) {
                when (flag) {
                    DiplomacyFlags.RememberAttackedProtectedMinor.name -> {
                        addModifier(DiplomaticModifiers.AttackedProtectedMinor, 5f)
                    }
                    DiplomacyFlags.RememberBulliedProtectedMinor.name -> {
                        addModifier(DiplomaticModifiers.BulliedProtectedMinor, 5f)
                    }
                }
            }

            // Only when flag is expired
            if (flagsCountdown[flag] == 0) {
                when (flag) {
                    DiplomacyFlags.ResearchAgreement.name -> {
                        if (!otherCivDiplomacy().hasFlag(DiplomacyFlags.ResearchAgreement))
                            scienceFromResearchAgreement()
                    }
                    DiplomacyFlags.DefensivePact.name -> {
                        diplomaticStatus = DiplomaticStatus.Peace
                    }
                    // This is confusingly named - in fact, the civ that has the flag set is the MAJOR civ
                    DiplomacyFlags.ProvideMilitaryUnit.name -> {
                        // Do not unset the flag - they may return soon, and we'll continue from that point on
                        if (civInfo.cities.isEmpty() || otherCiv().cities.isEmpty())
                            continue@loop
                        else
                            otherCiv().cityStateFunctions.giveMilitaryUnitToPatron(civInfo)
                    }
                    
                    DiplomacyFlags.RecentlyAttacked.name -> {
                        civInfo.cityStateFunctions.askForUnitGifts(otherCiv())
                    }
                    // These modifiers don't tick down normally, instead there is a threshold number of turns
                    DiplomacyFlags.RememberDestroyedProtectedMinor.name -> {    // 125
                        removeModifier(DiplomaticModifiers.DestroyedProtectedMinor)
                    }
                    DiplomacyFlags.RememberAttackedProtectedMinor.name -> {     // 75
                        removeModifier(DiplomaticModifiers.AttackedProtectedMinor)
                    }
                    DiplomacyFlags.RememberBulliedProtectedMinor.name -> {      // 75
                        removeModifier(DiplomaticModifiers.BulliedProtectedMinor)
                    }
                    DiplomacyFlags.RememberSidedWithProtectedMinor.name -> {      // 25
                        removeModifier(DiplomaticModifiers.SidedWithProtectedMinor)
                    }
                    else -> {
                        for (demand in Demand.entries){
                            if (demand.agreedToDemand.name == flag) addModifier(demand.fulfilledPromiseDiplomacyModifier, 10f)
                        }
                    }
                }

                flagsCountdown.remove(flag)
            } else if (flag == DiplomacyFlags.WaryOf.name && flagsCountdown[flag]!! < -10) {
                // Used in DeclareWarTargetAutomation.declarePlannedWar to count the number of turns preparing
                // If we have been preparing for over 10 turns then cancel our attack plan
                flagsCountdown.remove(flag)
            }
        }
    }


    private fun DiplomacyManager.scienceFromResearchAgreement() {
        // https://forums.civfanatics.com/resources/research-agreements-bnw.25568/
        val scienceFromResearchAgreement = min(totalOfScienceDuringRA, otherCivDiplomacy().totalOfScienceDuringRA)
        civInfo.tech.scienceFromResearchAgreements += scienceFromResearchAgreement
        otherCiv().tech.scienceFromResearchAgreements += scienceFromResearchAgreement
        totalOfScienceDuringRA = 0
        otherCivDiplomacy().totalOfScienceDuringRA = 0
    }

    private fun DiplomacyManager.nextTurnTrades() {
        for (trade in trades.toList()) {
            for (offer in trade.ourOffers.union(trade.theirOffers).filter { it.duration > 0 }) {
                offer.duration--
            }

            if (trade.ourOffers.all { it.duration <= 0 } && trade.theirOffers.all { it.duration <= 0 }) {
                trades.remove(trade)
                for (offer in trade.ourOffers.union(trade.theirOffers).filter { it.duration == 0 }) { // this was a timed trade
                    val direction = if (offer in trade.theirOffers) "from" else "to"
                    civInfo.addNotification("[${offer.name}] $direction [$otherCivName] has ended",
                        DiplomacyAction(otherCivName, true),
                        NotificationCategory.Trade, otherCivName, NotificationIcon.Trade)

                    civInfo.updateStatsForNextTurn() // if they were bringing us gold per turn
                    if (trade.theirOffers.union(trade.ourOffers) // if resources were involved
                            .any { it.type == TradeOfferType.Luxury_Resource || it.type == TradeOfferType.Strategic_Resource })
                        civInfo.cache.updateCivResources()
                }
            }

            for (offer in trade.theirOffers.filter { it.duration <= 3 })
            {
                if (offer.duration == 3)
                    civInfo.addNotification("[${offer.name}] from [$otherCivName] will end in [3] turns",
                        DiplomacyAction(otherCivName, true),
                        NotificationCategory.Trade, otherCivName, NotificationIcon.Trade)
                else if (offer.duration == 1)
                    civInfo.addNotification("[${offer.name}] from [$otherCivName] will end next turn",
                        DiplomacyAction(otherCivName, true),
                        NotificationCategory.Trade, otherCivName, NotificationIcon.Trade)
            }
        }
    }

    private fun DiplomacyManager.nextTurnDiplomaticModifiers() {
        if (diplomaticStatus == DiplomaticStatus.Peace) {
            if (getModifier(DiplomaticModifiers.YearsOfPeace) < 30)
                addModifier(DiplomaticModifiers.YearsOfPeace, 0.5f)
        } else revertToZero(DiplomaticModifiers.YearsOfPeace, 0.5f) // war makes you forget the good ol' days

        var openBorders = 0
        if (hasOpenBorders) openBorders += 1

        if (otherCivDiplomacy().hasOpenBorders) openBorders += 1
        if (openBorders > 0) addModifier(DiplomaticModifiers.OpenBorders, openBorders / 8f) // so if we both have open borders it'll grow by 0.25 per turn
        else revertToZero(DiplomaticModifiers.OpenBorders, 1 / 8f)

        // Negatives
        revertToZero(DiplomaticModifiers.DeclaredWarOnUs, 1 / 8f) // this disappears real slow - it'll take 160 turns to really forget, this is war declaration we're talking about
        revertToZero(DiplomaticModifiers.WarMongerer, 1 / 2f) // warmongering gives a big negative boost when it happens but they're forgotten relatively quickly, like WWII amirite
        revertToZero(DiplomaticModifiers.CapturedOurCities, 1 / 4f) // if you captured our cities, though, that's harder to forget
        revertToZero(DiplomaticModifiers.BetrayedDeclarationOfFriendship, 1 / 8f) // That's a bastardly thing to do
        revertToZero(DiplomaticModifiers.BetrayedDefensivePact, 1 / 16f) // That's an outrageous thing to do
        revertToZero(DiplomaticModifiers.RefusedToNotSettleCitiesNearUs, 1 / 4f)
        for (demand in Demand.entries) {
            revertToZero(demand.betrayedPromiseDiplomacyMpodifier, 1 / 8f)
        }
        revertToZero(DiplomaticModifiers.UnacceptableDemands, 1 / 4f)
        revertToZero(DiplomaticModifiers.StealingTerritory, 1 / 4f)
        revertToZero(DiplomaticModifiers.DenouncedOurAllies, 1 / 4f)
        revertToZero(DiplomaticModifiers.DenouncedOurEnemies, 1 / 4f)
        revertToZero(DiplomaticModifiers.Denunciation, 1 / 8f) // That's personal, it'll take a long time to fade
        revertToZero(DiplomaticModifiers.SpiedOnUs, 1 / 4f)
        revertToZero(DiplomaticModifiers.StoleOurAlly, 1 / 2f) // Fair enough, don't like it but not directly against us per se

        // Positives
        revertToZero(DiplomaticModifiers.GaveUsUnits, 1 / 4f)
        revertToZero(DiplomaticModifiers.LiberatedCity, 1 / 8f)
        if (hasModifier(DiplomaticModifiers.GaveUsGifts)) {
            val giftLoss = when {
                relationshipLevel() == RelationshipLevel.Ally -> 1f
                relationshipLevel() == RelationshipLevel.Friend -> 1.5f
                relationshipLevel() == RelationshipLevel.Favorable -> 2f
                relationshipLevel() == RelationshipLevel.Neutral -> 2.5f
                relationshipLevel() == RelationshipLevel.Competitor -> 5f
                relationshipLevel() == RelationshipLevel.Enemy -> 7.5f
                relationshipLevel() == RelationshipLevel.Unforgivable -> 10f
                else -> 2.5f
            } * civInfo.gameInfo.ruleset.modOptions.constants.goldGiftDegradationMultiplier
            // We should subtract a certain amount from this balanced each turn
            // Assuming neutral relations we will subtract the higher of either:
            //  2.5% of the total amount or roughly 50 gold per turn (a value of ~.5 without inflation)
            // This ensures that the amount can be reduced to zero but scales with larger numbers
            val amountLost = (getModifier(DiplomaticModifiers.GaveUsGifts).absoluteValue * giftLoss / 100)
                .coerceAtLeast(giftLoss / 5)
            revertToZero(DiplomaticModifiers.GaveUsGifts, amountLost)
        }

        setFriendshipBasedModifier()

        setDefensivePactBasedModifier()

        setReligionBasedModifier()

        if (!hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            revertToZero(DiplomaticModifiers.DeclarationOfFriendship, 1 / 2f) //decreases slowly and will revert to full if it is declared later

        if (!hasFlag(DiplomacyFlags.DefensivePact))
            revertToZero(DiplomaticModifiers.DefensivePact, 1f)

        if (!otherCiv().isCityState) return

        if (isRelationshipLevelLT(RelationshipLevel.Friend)) {
            if (hasFlag(DiplomacyFlags.ProvideMilitaryUnit))
                removeFlag(DiplomacyFlags.ProvideMilitaryUnit)
            return
        }

        val variance = listOf(-1, 0, 1).random()

        val provideMilitaryUnitUniques = CityStateFunctions
            .getCityStateBonuses(otherCiv().cityStateType, relationshipIgnoreAfraid(), UniqueType.CityStateMilitaryUnits)
            .filter { it.conditionalsApply(civInfo.state) }.toList()
        if (provideMilitaryUnitUniques.isEmpty()) removeFlag(DiplomacyFlags.ProvideMilitaryUnit)

        for (unique in provideMilitaryUnitUniques) {
            // Reset the countdown if it has ended, or if we have longer to go than the current maximum (can happen when going from friend to ally)
            if (!hasFlag(DiplomacyFlags.ProvideMilitaryUnit) || getFlag(DiplomacyFlags.ProvideMilitaryUnit) > unique.params[0].toInt()) {
                setFlag(DiplomacyFlags.ProvideMilitaryUnit, unique.params[0].toInt() + variance)
            }
        }
    }

    /** @param amount always positive, so you don't need to think about it */
    private fun DiplomacyManager.revertToZero(modifier: DiplomaticModifiers, amount: Float) {
        if (!hasModifier(modifier)) return
        val currentAmount = getModifier(modifier)
        if (amount >= currentAmount.absoluteValue) diplomaticModifiers.remove(modifier.name)
        else if (currentAmount > 0) addModifier(modifier, -amount)
        else addModifier(modifier, amount)
    }

}
