package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CityStatePersonality
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.extensions.toPercent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class RelationshipLevel(val color: Color) {
    // War is tested separately for the Diplomacy Screen. Colored RED.
    Unforgivable(Color.FIREBRICK),
    Afraid(Color(0x5300ffff)),     // HSV(260,100,100)
    Enemy(Color.YELLOW),
    Competitor(Color(0x1f998fff)), // HSV(175,80,60)
    Neutral(Color(0x1bb371ff)),    // HSV(154,85,70)
    Favorable(Color(0x14cc3cff)),  // HSV(133,90,80)
    Friend(Color(0x2ce60bff)),     // HSV(111,95,90)
    Ally(Color.CHARTREUSE)           // HSV(90,100,100)
}

enum class DiplomacyFlags {
    DeclinedLuxExchange,
    DeclinedPeace,
    DeclinedResearchAgreement,
    DeclaredWar,
    DeclarationOfFriendship,
    ResearchAgreement,
    BorderConflict,
    SettledCitiesNearUs,
    AgreedToNotSettleNearUs,
    IgnoreThemSettlingNearUs,
    ProvideMilitaryUnit,
    MarriageCooldown,
    NotifiedAfraid,
    RecentlyPledgedProtection,
    RecentlyWithdrewProtection,
    AngerFreeIntrusion,
    RememberDestroyedProtectedMinor,
    RememberAttackedProtectedMinor,
    RememberBulliedProtectedMinor,
    RememberSidedWithProtectedMinor,
    Denunciation,
    WaryOf,
    Bullied,
    RecentlyAttacked,
}

enum class DiplomaticModifiers {
    DeclaredWarOnUs,
    WarMongerer,
    CapturedOurCities,
    DeclaredFriendshipWithOurEnemies,
    BetrayedDeclarationOfFriendship,
    Denunciation,
    DenouncedOurAllies,
    RefusedToNotSettleCitiesNearUs,
    BetrayedPromiseToNotSettleCitiesNearUs,
    UnacceptableDemands,
    UsedNuclearWeapons,
    StealingTerritory,

    YearsOfPeace,
    SharedEnemy,
    LiberatedCity,
    DeclarationOfFriendship,
    DeclaredFriendshipWithOurAllies,
    DenouncedOurEnemies,
    OpenBorders,
    FulfilledPromiseToNotSettleCitiesNearUs,
    GaveUsUnits,
    DestroyedProtectedMinor,
    AttackedProtectedMinor,
    BulliedProtectedMinor,
    SidedWithProtectedMinor,
    ReturnedCapturedUnits,
}

class DiplomacyManager() : IsPartOfGameInfoSerialization {

    companion object {
        /** The value city-state influence can't go below */
        const val MINIMUM_INFLUENCE = -60f
    }

    @Suppress("JoinDeclarationAndAssignment")  // incorrect warning - constructor would need to be higher in scope
    @Transient
    lateinit var civInfo: CivilizationInfo

    // since this needs to be checked a lot during travel, putting it in a transient is a good performance booster
    @Transient
    /** Can civInfo enter otherCivInfo's tiles? */
    var hasOpenBorders = false

    lateinit var otherCivName: String
    var trades = ArrayList<Trade>()
    var diplomaticStatus = DiplomaticStatus.War

    /** Contains various flags (declared war, promised to not settle, declined luxury trade) and the number of turns in which they will expire.
     *  The JSON serialize/deserialize REFUSES to deserialize hashmap keys as Enums, so I'm forced to use strings instead =(
     *  This is so sad Alexa play Despacito */
    private var flagsCountdown = HashMap<String, Int>()

    /** For AI. Positive is good relations, negative is bad.
     * Baseline is 1 point for each turn of peace - so declaring a war upends 40 years of peace, and e.g. capturing a city can be another 30 or 40.
     * As for why it's String and not DiplomaticModifier see FlagsCountdown comment */
    var diplomaticModifiers = HashMap<String, Float>()

    /** For city-states. Influence is saved in the CITY STATE -> major civ Diplomacy, NOT in the major civ -> city state diplomacy.
     * Access via getInfluence() and setInfluence() unless you know what you're doing.
     * Note that not using the setter skips recalculating the ally and bounds checks,
     * and skipping the getter bypasses the modified value when at war */
    private var influence = 0f

    /** Total of each turn Science during Research Agreement */
    private var totalOfScienceDuringRA = 0

    fun clone(): DiplomacyManager {
        val toReturn = DiplomacyManager()
        toReturn.otherCivName = otherCivName
        toReturn.diplomaticStatus = diplomaticStatus
        toReturn.trades.addAll(trades.map { it.clone() })
        toReturn.influence = influence
        toReturn.flagsCountdown.putAll(flagsCountdown)
        toReturn.diplomaticModifiers.putAll(diplomaticModifiers)
        toReturn.totalOfScienceDuringRA = totalOfScienceDuringRA
        return toReturn
    }

    constructor(civilizationInfo: CivilizationInfo, OtherCivName: String) : this() {
        civInfo = civilizationInfo
        otherCivName = OtherCivName
        updateHasOpenBorders()
    }

    //region pure functions
    fun otherCiv() = civInfo.gameInfo.getCivilization(otherCivName)
    fun otherCivDiplomacy() = otherCiv().getDiplomacyManager(civInfo)

    fun turnsToPeaceTreaty(): Int {
        for (trade in trades)
            for (offer in trade.ourOffers)
                if (offer.name == Constants.peaceTreaty && offer.duration > 0) return offer.duration
        return 0
    }

    fun opinionOfOtherCiv(): Float {
        var modifierSum = diplomaticModifiers.values.sum()
        // Angry about attacked CS and destroyed CS do not stack
        if (hasModifier(DiplomaticModifiers.DestroyedProtectedMinor) && hasModifier(DiplomaticModifiers.AttackedProtectedMinor))
            modifierSum -= getModifier(DiplomaticModifiers.AttackedProtectedMinor)
        return modifierSum
    }

    fun relationshipLevel(): RelationshipLevel {
        if (civInfo.isPlayerCivilization() && otherCiv().isPlayerCivilization())
            return RelationshipLevel.Neutral // People make their own choices.

        if (civInfo.isPlayerCivilization())
            return otherCiv().getDiplomacyManager(civInfo).relationshipLevel()

        if (civInfo.isCityState()) return when {
            getInfluence() <= -30 || civInfo.isAtWarWith(otherCiv()) -> RelationshipLevel.Unforgivable
            getInfluence() < 0 -> RelationshipLevel.Enemy
            getInfluence() < 30 && civInfo.getTributeWillingness(otherCiv()) > 0 -> RelationshipLevel.Afraid
            getInfluence() >= 60 && civInfo.getAllyCiv() == otherCivName -> RelationshipLevel.Ally
            getInfluence() >= 30 -> RelationshipLevel.Friend
            else -> RelationshipLevel.Neutral
        }

        // not entirely sure what to do between AI civs, because they probably have different views of each other,
        // maybe we need to average their views of each other? That makes sense to me.

        val opinion = opinionOfOtherCiv()
        return when {
            opinion <= -80 -> RelationshipLevel.Unforgivable
            opinion <= -40 || civInfo.isAtWarWith(otherCiv()) -> RelationshipLevel.Enemy  /* During wartime, the estimation in which you are held may be enemy OR unforgivable */
            opinion <= -15 -> RelationshipLevel.Competitor

            opinion >= 80 -> RelationshipLevel.Ally
            opinion >= 40 -> RelationshipLevel.Friend
            opinion >= 15 -> RelationshipLevel.Favorable
            else -> RelationshipLevel.Neutral
        }
    }

    /** Returns the number of turns to degrade from Ally or from Friend */
    fun getTurnsToRelationshipChange(): Int {
        if (otherCiv().isCityState())
            return otherCivDiplomacy().getTurnsToRelationshipChange()

        if (civInfo.isCityState() && !otherCiv().isCityState()) {
            val dropPerTurn = getCityStateInfluenceDegrade()
            return when {
                dropPerTurn == 0f -> 0
                relationshipLevel() >= RelationshipLevel.Ally -> ceil((getInfluence() - 60f) / dropPerTurn).toInt() + 1
                relationshipLevel() >= RelationshipLevel.Friend -> ceil((getInfluence() - 30f) / dropPerTurn).toInt() + 1
                else -> 0
            }
        }
        return 0
    }

    @Suppress("unused")  //todo Finish original intent (usage in uniques) or remove
    fun matchesCityStateRelationshipFilter(filter: String): Boolean {
        val relationshipLevel = relationshipLevel()
        return when (filter) {
            "Allied" -> relationshipLevel == RelationshipLevel.Ally
            "Friendly" -> relationshipLevel == RelationshipLevel.Friend
            "Enemy" -> relationshipLevel == RelationshipLevel.Enemy
            "Unforgiving" -> relationshipLevel == RelationshipLevel.Unforgivable
            "Neutral" -> relationshipLevel == RelationshipLevel.Neutral
            else -> false
        }
    }

    fun addInfluence(amount: Float) {
        setInfluence(influence + amount)
    }

    fun setInfluence(amount: Float) {
        influence = max(amount, MINIMUM_INFLUENCE)
        civInfo.updateAllyCivForCityState()
    }

    fun getInfluence() = if (civInfo.isAtWarWith(otherCiv())) MINIMUM_INFLUENCE else influence

    // To be run from City-State DiplomacyManager, which holds the influence. Resting point for every major civ can be different.
    private fun getCityStateInfluenceRestingPoint(): Float {
        var restingPoint = 0f

        for (unique in otherCiv().getMatchingUniques(UniqueType.CityStateRestingPoint))
            restingPoint += unique.params[0].toInt()

        if (civInfo.cities.any() && civInfo.getCapital() != null)
            for (unique in otherCiv().getMatchingUniques(UniqueType.RestingPointOfCityStatesFollowingReligionChange))
                if (otherCiv().religionManager.religion?.name == civInfo.getCapital()!!.religion.getMajorityReligionName())
                    restingPoint += unique.params[0].toInt()

        if (diplomaticStatus == DiplomaticStatus.Protector) restingPoint += 10

        if (hasFlag(DiplomacyFlags.WaryOf)) restingPoint -= 20

        return restingPoint
    }

    private fun getCityStateInfluenceDegrade(): Float {
        if (getInfluence() <= getCityStateInfluenceRestingPoint())
            return 0f

        val decrement = when {
            civInfo.cityStatePersonality == CityStatePersonality.Hostile -> 1.5f
            otherCiv().isMinorCivAggressor() -> 2f
            else -> 1f
        }

        var modifierPercent = 0f
        for (unique in otherCiv().getMatchingUniques(UniqueType.CityStateInfluenceDegradation))
            modifierPercent += unique.params[0].toFloat()

        val religion = if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) null
            else civInfo.getCapital()!!.religion.getMajorityReligionName()
        if (religion != null && religion == otherCiv().religionManager.religion?.name)
            modifierPercent -= 25f  // 25% slower degrade when sharing a religion

        for (civ in civInfo.gameInfo.civilizations.filter { it.isMajorCiv() && it != otherCiv()}) {
            for (unique in civ.getMatchingUniques(UniqueType.OtherCivsCityStateRelationsDegradeFaster)) {
                modifierPercent += unique.params[0].toFloat()
            }
        }

        return max(0f, decrement) * max(-100f, modifierPercent).toPercent()
    }

    private fun getCityStateInfluenceRecovery(): Float {
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

    fun canDeclareWar() = turnsToPeaceTreaty() == 0 && diplomaticStatus != DiplomaticStatus.War

    //Used for nuke
    fun canAttack() = turnsToPeaceTreaty() == 0

    fun goldPerTurn(): Int {
        var goldPerTurnForUs = 0
        for (trade in trades) {
            for (offer in trade.ourOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs -= offer.amount
            for (offer in trade.theirOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs += offer.amount
        }
        return goldPerTurnForUs
    }

    private fun scienceFromResearchAgreement() {
        // https://forums.civfanatics.com/resources/research-agreements-bnw.25568/
        val scienceFromResearchAgreement = min(totalOfScienceDuringRA, otherCivDiplomacy().totalOfScienceDuringRA)
        civInfo.tech.scienceFromResearchAgreements += scienceFromResearchAgreement
        otherCiv().tech.scienceFromResearchAgreements += scienceFromResearchAgreement
        totalOfScienceDuringRA = 0
        otherCivDiplomacy().totalOfScienceDuringRA = 0
    }

    fun resourcesFromTrade(): ResourceSupplyList {
        val newResourceSupplyList = ResourceSupplyList()
        val resourcesMap = civInfo.gameInfo.ruleSet.tileResources
        val isResourceFilter: (TradeOffer) -> Boolean = {
            (it.type == TradeType.Strategic_Resource || it.type == TradeType.Luxury_Resource)
                    && resourcesMap.containsKey(it.name)
        }
        for (trade in trades) {
            for (offer in trade.ourOffers.filter(isResourceFilter))
                newResourceSupplyList.add(resourcesMap[offer.name]!!, "Trade", -offer.amount)
            for (offer in trade.theirOffers.filter(isResourceFilter))
                newResourceSupplyList.add(resourcesMap[offer.name]!!, "Trade", offer.amount)
        }

        for (trade in otherCiv().tradeRequests.filter { it.requestingCiv == civInfo.civName }) {
            for (offer in trade.trade.theirOffers.filter(isResourceFilter))
                newResourceSupplyList.add(resourcesMap[offer.name]!!, "Trade request", -offer.amount)
        }

        return newResourceSupplyList
    }

    /** Returns the [civilizations][CivilizationInfo] that know about both sides ([civInfo] and [otherCiv]) */
    fun getCommonKnownCivs(): Set<CivilizationInfo> = civInfo.getKnownCivs().intersect(otherCiv().getKnownCivs().toSet())

    /** Returns true when the [civInfo]'s territory is considered allied for [otherCiv].
     *  This includes friendly and allied city-states and the open border treaties.
     */
    fun isConsideredFriendlyTerritory(): Boolean {
        if (civInfo.isCityState() &&
            (relationshipLevel() >= RelationshipLevel.Friend || otherCiv().hasUnique(UniqueType.CityStateTerritoryAlwaysFriendly)))
            return true

        return otherCivDiplomacy().hasOpenBorders // if THEY can enter US then WE are considered friendly territory for THEM
    }
    //endregion

    //region state-changing functions
    private fun removeUntenableTrades() {
        for (trade in trades.toList()) {

            // Every cancelled trade can change this - if 1 resource is missing,
            // don't cancel all trades of that resource, only cancel one (the first one, as it happens, since they're added chronologically)
            val negativeCivResources = civInfo.getCivResources()
                .filter { it.amount < 0 }.map { it.resource.name }

            for (offer in trade.ourOffers) {
                if (offer.type in listOf(TradeType.Luxury_Resource, TradeType.Strategic_Resource)
                    && (offer.name in negativeCivResources || !civInfo.gameInfo.ruleSet.tileResources.containsKey(offer.name))
                ) {

                    trades.remove(trade)
                    val otherCivTrades = otherCiv().getDiplomacyManager(civInfo).trades
                    otherCivTrades.removeAll { it.equalTrade(trade.reverse()) }

                    // Can't cut short peace treaties!
                    if (trade.theirOffers.any { it.name == Constants.peaceTreaty }) {
                        remakePeaceTreaty(trade.theirOffers.first { it.name == Constants.peaceTreaty }.duration)
                    }

                    civInfo.addNotification("One of our trades with [$otherCivName] has been cut short", NotificationIcon.Trade, otherCivName)
                    otherCiv().addNotification("One of our trades with [${civInfo.civName}] has been cut short", NotificationIcon.Trade, civInfo.civName)
                    civInfo.updateDetailedCivResources()
                }
            }
        }
    }

    private fun remakePeaceTreaty(durationLeft: Int) {
        val treaty = Trade()
        treaty.ourOffers.add(
            TradeOffer(Constants.peaceTreaty, TradeType.Treaty, duration = durationLeft)
        )
        treaty.theirOffers.add(
            TradeOffer(Constants.peaceTreaty, TradeType.Treaty, duration = durationLeft)
        )
        trades.add(treaty)
        otherCiv().getDiplomacyManager(civInfo).trades.add(treaty)
    }

    // for performance reasons we don't want to call this every time we want to see if a unit can move through a tile
    fun updateHasOpenBorders() {
        // City-states can enter ally's territory (the opposite is true anyway even without open borders)
        val newHasOpenBorders = civInfo.getAllyCiv() == otherCivName
                || trades.flatMap { it.theirOffers }.any { it.name == Constants.openBorders && it.duration > 0 }

        val bordersWereClosed = hasOpenBorders && !newHasOpenBorders
        hasOpenBorders = newHasOpenBorders

        if (bordersWereClosed) { // borders were closed, get out!
            for (unit in civInfo.getCivUnits()
                .filter { it.currentTile.getOwner()?.civName == otherCivName }.toList()) {
                unit.movement.teleportToClosestMoveableTile()
            }
        }
    }

    fun nextTurn() {
        nextTurnTrades()
        removeUntenableTrades()
        updateHasOpenBorders()
        nextTurnDiplomaticModifiers()
        nextTurnFlags()
        if (civInfo.isCityState() && otherCiv().isMajorCiv())
            nextTurnCityStateInfluence()
    }

    private fun nextTurnCityStateInfluence() {
        val initialRelationshipLevel = relationshipLevel()

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
            val civCapitalLocation = if (civInfo.cities.isNotEmpty() || civInfo.getCapital() != null) civInfo.getCapital()!!.location else null
            if (getTurnsToRelationshipChange() == 1) {
                val text = "Your relationship with [${civInfo.civName}] is about to degrade"
                if (civCapitalLocation != null) otherCiv().addNotification(text, civCapitalLocation, civInfo.civName, NotificationIcon.Diplomacy)
                else otherCiv().addNotification(text, civInfo.civName, NotificationIcon.Diplomacy)
            }

            if (initialRelationshipLevel >= RelationshipLevel.Friend && initialRelationshipLevel != relationshipLevel()) {
                val text = "Your relationship with [${civInfo.civName}] degraded"
                if (civCapitalLocation != null) otherCiv().addNotification(text, civCapitalLocation, civInfo.civName, NotificationIcon.Diplomacy)
                else otherCiv().addNotification(text, civInfo.civName, NotificationIcon.Diplomacy)
            }

            // Potentially notify about afraid status
            if (getInfluence() < 30  // We usually don't want to bully our friends
                && !hasFlag(DiplomacyFlags.NotifiedAfraid)
                && civInfo.getTributeWillingness(otherCiv()) > 0
                && otherCiv().isMajorCiv()
            ) {
                setFlag(DiplomacyFlags.NotifiedAfraid, 20)  // Wait 20 turns until next reminder
                val text = "[${civInfo.civName}] is afraid of your military power!"
                if (civCapitalLocation != null) otherCiv().addNotification(text, civCapitalLocation, civInfo.civName, NotificationIcon.Diplomacy)
                else otherCiv().addNotification(text, civInfo.civName, NotificationIcon.Diplomacy)
            }
        }
    }

    private fun nextTurnFlags() {
        loop@ for (flag in flagsCountdown.keys.toList()) {
            // No need to decrement negative countdown flags: they do not expire
            if (flagsCountdown[flag]!! > 0)
                flagsCountdown[flag] = flagsCountdown[flag]!! - 1

            // If we have uniques that make city states grant military units faster when at war with a common enemy, add higher numbers to this flag
            if (flag == DiplomacyFlags.ProvideMilitaryUnit.name && civInfo.isMajorCiv() && otherCiv().isCityState() &&
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
                totalOfScienceDuringRA += civInfo.statsForNextTurn.science.toInt()

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
                    // This is confusingly named - in fact, the civ that has the flag set is the MAJOR civ
                    DiplomacyFlags.ProvideMilitaryUnit.name -> {
                        // Do not unset the flag - they may return soon, and we'll continue from that point on
                        if (civInfo.cities.isEmpty() || otherCiv().cities.isEmpty())
                            continue@loop
                        else
                            otherCiv().cityStateFunctions.giveMilitaryUnitToPatron(civInfo)
                    }
                    DiplomacyFlags.AgreedToNotSettleNearUs.name -> {
                        addModifier(DiplomaticModifiers.FulfilledPromiseToNotSettleCitiesNearUs, 10f)
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
                }

                flagsCountdown.remove(flag)
            }
        }
    }

    private fun nextTurnTrades() {
        for (trade in trades.toList()) {
            for (offer in trade.ourOffers.union(trade.theirOffers).filter { it.duration > 0 }) {
                offer.duration--
            }

            if (trade.ourOffers.all { it.duration <= 0 } && trade.theirOffers.all { it.duration <= 0 }) {
                trades.remove(trade)
                for (offer in trade.ourOffers.union(trade.theirOffers).filter { it.duration == 0 }) { // this was a timed trade
                    if (offer in trade.theirOffers)
                        civInfo.addNotification("[${offer.name}] from [$otherCivName] has ended", otherCivName, NotificationIcon.Trade)
                    else civInfo.addNotification("[${offer.name}] to [$otherCivName] has ended", otherCivName, NotificationIcon.Trade)

                    civInfo.updateStatsForNextTurn() // if they were bringing us gold per turn
                    if (trade.theirOffers.union(trade.ourOffers) // if resources were involved
                                .any { it.type == TradeType.Luxury_Resource || it.type == TradeType.Strategic_Resource })
                        civInfo.updateDetailedCivResources()
                }
            }
        }
    }

    private fun nextTurnDiplomaticModifiers() {
        if (diplomaticStatus == DiplomaticStatus.Peace) {
            if (getModifier(DiplomaticModifiers.YearsOfPeace) < 30)
                addModifier(DiplomaticModifiers.YearsOfPeace, 0.5f)
        } else revertToZero(DiplomaticModifiers.YearsOfPeace, 0.5f) // war makes you forget the good ol' days

        var openBorders = 0
        if (hasOpenBorders) openBorders += 1

        if (otherCivDiplomacy().hasOpenBorders) openBorders += 1
        if (openBorders > 0) addModifier(DiplomaticModifiers.OpenBorders, openBorders / 8f) // so if we both have open borders it'll grow by 0.25 per turn
        else revertToZero(DiplomaticModifiers.OpenBorders, 1 / 8f)

        revertToZero(DiplomaticModifiers.DeclaredWarOnUs, 1 / 8f) // this disappears real slow - it'll take 160 turns to really forget, this is war declaration we're talking about
        revertToZero(DiplomaticModifiers.WarMongerer, 1 / 2f) // warmongering gives a big negative boost when it happens but they're forgotten relatively quickly, like WWII amirite
        revertToZero(DiplomaticModifiers.CapturedOurCities, 1 / 4f) // if you captured our cities, though, that's harder to forget
        revertToZero(DiplomaticModifiers.BetrayedDeclarationOfFriendship, 1 / 8f) // That's a bastardly thing to do
        revertToZero(DiplomaticModifiers.RefusedToNotSettleCitiesNearUs, 1 / 4f)
        revertToZero(DiplomaticModifiers.BetrayedPromiseToNotSettleCitiesNearUs, 1 / 8f) // That's a bastardly thing to do
        revertToZero(DiplomaticModifiers.UnacceptableDemands, 1 / 4f)
        revertToZero(DiplomaticModifiers.LiberatedCity, 1 / 8f)
        revertToZero(DiplomaticModifiers.StealingTerritory, 1 / 4f)
        revertToZero(DiplomaticModifiers.DenouncedOurAllies, 1 / 4f)
        revertToZero(DiplomaticModifiers.DenouncedOurEnemies, 1 / 4f)
        revertToZero(DiplomaticModifiers.Denunciation, 1 / 8f) // That's personal, it'll take a long time to fade
        revertToZero(DiplomaticModifiers.GaveUsUnits, 1 / 4f)

        setFriendshipBasedModifier()

        if (!hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            revertToZero(DiplomaticModifiers.DeclarationOfFriendship, 1 / 2f) //decreases slowly and will revert to full if it is declared later

        if (!otherCiv().isCityState()) return

        if (relationshipLevel() < RelationshipLevel.Friend) {
            if (hasFlag(DiplomacyFlags.ProvideMilitaryUnit))
                removeFlag(DiplomacyFlags.ProvideMilitaryUnit)
            return
        }

        val variance = listOf(-1, 0, 1).random()

        val provideMilitaryUnitUniques = civInfo.cityStateFunctions.getCityStateBonuses(otherCiv().cityStateType, relationshipLevel(), UniqueType.CityStateMilitaryUnits)
            .filter { it.conditionalsApply(civInfo) }.toList()
        if (provideMilitaryUnitUniques.isEmpty()) removeFlag(DiplomacyFlags.ProvideMilitaryUnit)

        for (unique in provideMilitaryUnitUniques) {
            // Reset the countdown if it has ended, or if we have longer to go than the current maximum (can happen when going from friend to ally)
            if (!hasFlag(DiplomacyFlags.ProvideMilitaryUnit) || getFlag(DiplomacyFlags.ProvideMilitaryUnit) > unique.params[0].toInt()) {
                setFlag(DiplomacyFlags.ProvideMilitaryUnit, unique.params[0].toInt() + variance)
            }
        }
    }

    /** Everything that happens to both sides equally when war is declared by one side on the other */
    private fun onWarDeclared() {
        // Cancel all trades.
        for (trade in trades)
            for (offer in trade.theirOffers.filter { it.duration > 0 })
                civInfo.addNotification("[" + offer.name + "] from [$otherCivName] has ended", otherCivName, NotificationIcon.Trade)
        trades.clear()
        updateHasOpenBorders()

        val civAtWarWith = otherCiv()

        if (civInfo.isCityState() && civInfo.getProtectorCivs().contains(civAtWarWith)) {
            civInfo.removeProtectorCiv(civAtWarWith, forced = true)
        }

        diplomaticStatus = DiplomaticStatus.War

        removeModifier(DiplomaticModifiers.YearsOfPeace)
        setFlag(DiplomacyFlags.DeclinedPeace, 10)/// AI won't propose peace for 10 turns
        setFlag(DiplomacyFlags.DeclaredWar, 10) // AI won't agree to trade for 10 turns
        removeFlag(DiplomacyFlags.BorderConflict)


        // Go through city state allies.
        if (!civInfo.isCityState()) {
            for (thirdCiv in civInfo.getKnownCivs()
                .filter { it.isCityState() && it.getAllyCiv() == civInfo.civName }) {

                if (thirdCiv.knows(civAtWarWith) && !thirdCiv.isAtWarWith(civAtWarWith))
                    thirdCiv.getDiplomacyManager(civAtWarWith).declareWar(true)
                else if (!thirdCiv.knows(civAtWarWith)) {
                    // Our city state ally has not met them yet, so they have to meet first
                    thirdCiv.makeCivilizationsMeet(civAtWarWith, warOnContact = true)
                    thirdCiv.getDiplomacyManager(civAtWarWith).declareWar(true)
                }
            }
        }
    }

    /** Declares war with the other civ in this diplomacy manager.
     * Handles all war effects and diplomatic changes with other civs and such.
     *
     * @param indirectCityStateAttack Influence with city states should only be set to -60
     * when they are attacked directly, not when their ally is attacked.
     * When @indirectCityStateAttack is set to true, we thus don't reset the influence with this city state.
     * Should only ever be set to true for calls originating from within this function.
     */
    fun declareWar(indirectCityStateAttack: Boolean = false) {
        val otherCiv = otherCiv()
        val otherCivDiplomacy = otherCivDiplomacy()

        if (otherCiv.isCityState() && !indirectCityStateAttack) {
            otherCivDiplomacy.setInfluence(-60f)
            civInfo.changeMinorCivsAttacked(1)
            otherCiv.cityStateFunctions.cityStateAttacked(civInfo)

            // You attacked your own ally, you're a right bastard
            if (otherCiv.getAllyCiv() == civInfo.civName) {
                otherCiv.updateAllyCivForCityState()
                otherCivDiplomacy.setInfluence(-120f)
                for (knownCiv in civInfo.getKnownCivs()) {
                    knownCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, -10f)
                }
            }
        }

        onWarDeclared()
        otherCivDiplomacy.onWarDeclared()

        otherCiv.addNotification("[${civInfo.civName}] has declared war on us!", NotificationIcon.War, civInfo.civName)
        otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration, civInfo.civName))

        getCommonKnownCivs().forEach {
            it.addNotification("[${civInfo.civName}] has declared war on [$otherCivName]!", civInfo.civName, NotificationIcon.War, otherCivName)
        }

        otherCivDiplomacy.setModifier(DiplomaticModifiers.DeclaredWarOnUs, -20f)
        otherCivDiplomacy.removeModifier(DiplomaticModifiers.ReturnedCapturedUnits)

        for (thirdCiv in civInfo.getKnownCivs()) {
            if (thirdCiv.isAtWarWith(otherCiv)) {
                if (thirdCiv.isCityState()) thirdCiv.getDiplomacyManager(civInfo).addInfluence(10f)
                else thirdCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.SharedEnemy, 5f)
            } else thirdCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.WarMongerer, -5f)
        }

        if (hasFlag(DiplomacyFlags.DeclarationOfFriendship)) {
            removeFlag(DiplomacyFlags.DeclarationOfFriendship)
            otherCivDiplomacy.removeModifier(DiplomaticModifiers.DeclarationOfFriendship)
            for (knownCiv in civInfo.getKnownCivs()) {
                val amount = if (knownCiv == otherCiv) -40f else -20f
                val diploManager = knownCiv.getDiplomacyManager(civInfo)
                diploManager.addModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, amount)
                diploManager.removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies) // obviously this guy's declarations of friendship aren't worth much.
            }
        }
        otherCivDiplomacy.removeFlag(DiplomacyFlags.DeclarationOfFriendship)

        if (hasFlag(DiplomacyFlags.ResearchAgreement)) {
            removeFlag(DiplomacyFlags.ResearchAgreement)
            totalOfScienceDuringRA = 0
            otherCivDiplomacy.totalOfScienceDuringRA = 0
        }
        otherCivDiplomacy.removeFlag(DiplomacyFlags.ResearchAgreement)
    }

    /** Should only be called from makePeace */
    private fun makePeaceOneSide() {
        diplomaticStatus = DiplomaticStatus.Peace
        val otherCiv = otherCiv()
        // Get out of others' territory
        for (unit in civInfo.getCivUnits().filter { it.getTile().getOwner() == otherCiv }.toList())
            unit.movement.teleportToClosestMoveableTile()

        for (thirdCiv in civInfo.getKnownCivs()) {
            // Our ally city states make peace with us
            if (thirdCiv.getAllyCiv() == civInfo.civName && thirdCiv.isAtWarWith(otherCiv))
                thirdCiv.getDiplomacyManager(otherCiv).makePeace()
            // Other City-States that are not our ally don't like the fact that we made peace with their enemy
            if (thirdCiv.getAllyCiv() != civInfo.civName && thirdCiv.isAtWarWith(otherCiv))
                thirdCiv.getDiplomacyManager(civInfo).addInfluence(-10f)
        }
    }


    fun makePeace() {
        makePeaceOneSide()
        otherCivDiplomacy().makePeaceOneSide()

        for (civ in getCommonKnownCivs()) {
            civ.addNotification(
                    "[${civInfo.civName}] and [$otherCivName] have signed a Peace Treaty!",
                    civInfo.civName, NotificationIcon.Diplomacy, otherCivName
            )
        }
    }

    fun hasFlag(flag: DiplomacyFlags) = flagsCountdown.containsKey(flag.name)
    fun setFlag(flag: DiplomacyFlags, amount: Int) {
        flagsCountdown[flag.name] = amount
    }

    fun getFlag(flag: DiplomacyFlags) = flagsCountdown[flag.name]!!
    fun removeFlag(flag: DiplomacyFlags) {
        flagsCountdown.remove(flag.name)
    }

    fun addModifier(modifier: DiplomaticModifiers, amount: Float) {
        val modifierString = modifier.name
        if (!hasModifier(modifier)) setModifier(modifier, 0f)
        diplomaticModifiers[modifierString] = diplomaticModifiers[modifierString]!! + amount
        if (diplomaticModifiers[modifierString] == 0f) diplomaticModifiers.remove(modifierString)
    }

    fun setModifier(modifier: DiplomaticModifiers, amount: Float) {
        diplomaticModifiers[modifier.name] = amount
    }

    private fun getModifier(modifier: DiplomaticModifiers): Float {
        if (!hasModifier(modifier)) return 0f
        return diplomaticModifiers[modifier.name]!!
    }

    private fun removeModifier(modifier: DiplomaticModifiers) = diplomaticModifiers.remove(modifier.name)
    fun hasModifier(modifier: DiplomaticModifiers) = diplomaticModifiers.containsKey(modifier.name)

    /** @param amount always positive, so you don't need to think about it */
    private fun revertToZero(modifier: DiplomaticModifiers, amount: Float) {
        if (!hasModifier(modifier)) return
        val currentAmount = getModifier(modifier)
        if (currentAmount > 0) addModifier(modifier, -amount)
        else addModifier(modifier, amount)
    }

    fun signDeclarationOfFriendship() {
        setModifier(DiplomaticModifiers.DeclarationOfFriendship, 35f)
        otherCivDiplomacy().setModifier(DiplomaticModifiers.DeclarationOfFriendship, 35f)
        setFlag(DiplomacyFlags.DeclarationOfFriendship, 30)
        otherCivDiplomacy().setFlag(DiplomacyFlags.DeclarationOfFriendship, 30)

        if (otherCiv().playerType == PlayerType.Human)
            otherCiv().addNotification("[${civInfo.civName}] and [$otherCivName] have signed the Declaration of Friendship!",
                    civInfo.civName, NotificationIcon.Diplomacy, otherCivName)

        for (thirdCiv in getCommonKnownCivs().filter { it.isMajorCiv() }) {
            thirdCiv.addNotification("[${civInfo.civName}] and [$otherCivName] have signed the Declaration of Friendship!",
                    civInfo.civName, NotificationIcon.Diplomacy, otherCivName)
            thirdCiv.getDiplomacyManager(civInfo).setFriendshipBasedModifier()
        }
    }

    private fun setFriendshipBasedModifier() {
        removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies)
        removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies)
        for (thirdCiv in getCommonKnownCivs()
                .filter { it.getDiplomacyManager(civInfo).hasFlag(DiplomacyFlags.DeclarationOfFriendship) }) {
            when (otherCiv().getDiplomacyManager(thirdCiv).relationshipLevel()) {
                RelationshipLevel.Unforgivable -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies, -15f)
                RelationshipLevel.Enemy -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies, -5f)
                RelationshipLevel.Friend -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies, 5f)
                RelationshipLevel.Ally -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies, 15f)
                else -> {}
            }
        }
    }

    fun denounce() {
        setModifier(DiplomaticModifiers.Denunciation, -35f)
        otherCivDiplomacy().setModifier(DiplomaticModifiers.Denunciation, -35f)
        setFlag(DiplomacyFlags.Denunciation, 30)
        otherCivDiplomacy().setFlag(DiplomacyFlags.Denunciation, 30)

        otherCiv().addNotification("[${civInfo.civName}] has denounced us!", NotificationIcon.Diplomacy, civInfo.civName)

        // We, A, are denouncing B. What do other major civs (C,D, etc) think of this?
        getCommonKnownCivs().filter { it.isMajorCiv() }.forEach { thirdCiv ->
            thirdCiv.addNotification("[${civInfo.civName}] has denounced [$otherCivName]!", civInfo.civName, NotificationIcon.Diplomacy, otherCivName)
            val thirdCivRelationshipWithOtherCiv = thirdCiv.getDiplomacyManager(otherCiv()).relationshipLevel()
            val thirdCivDiplomacyManager = thirdCiv.getDiplomacyManager(civInfo)
            when (thirdCivRelationshipWithOtherCiv) {
                RelationshipLevel.Unforgivable -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurEnemies, 15f)
                RelationshipLevel.Enemy -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurEnemies, 5f)
                RelationshipLevel.Friend -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurAllies, -5f)
                RelationshipLevel.Ally -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurAllies, -15f)
                else -> {}
            }
        }
    }

    fun agreeNotToSettleNear() {
        otherCivDiplomacy().setFlag(DiplomacyFlags.AgreedToNotSettleNearUs, 100)
        addModifier(DiplomaticModifiers.UnacceptableDemands, -10f)
        otherCiv().addNotification("[${civInfo.civName}] agreed to stop settling cities near us!", NotificationIcon.Diplomacy, civInfo.civName)
    }

    fun refuseDemandNotToSettleNear() {
        addModifier(DiplomaticModifiers.UnacceptableDemands, -20f)
        otherCivDiplomacy().setFlag(DiplomacyFlags.IgnoreThemSettlingNearUs, 100)
        otherCivDiplomacy().addModifier(DiplomaticModifiers.RefusedToNotSettleCitiesNearUs, -15f)
        otherCiv().addNotification("[${civInfo.civName}] refused to stop settling cities near us!", NotificationIcon.Diplomacy, civInfo.civName)
    }

    fun sideWithCityState() {
        otherCivDiplomacy().setModifier(DiplomaticModifiers.SidedWithProtectedMinor, -5f)
        otherCivDiplomacy().setFlag(DiplomacyFlags.RememberSidedWithProtectedMinor, 25)
    }

    fun becomeWary() {
        if (hasFlag(DiplomacyFlags.WaryOf)) return // once is enough
        setFlag(DiplomacyFlags.WaryOf, -1) // Never expires
        otherCiv().addNotification("City-States grow wary of your aggression. The resting point for Influence has decreased by [20] for [${civInfo.civName}].", civInfo.civName)
    }

    //endregion
}
