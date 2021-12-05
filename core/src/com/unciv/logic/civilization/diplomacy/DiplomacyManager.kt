package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.civilization.*
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.ui.utils.toPercent
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
    @Deprecated("Deprecated after 3.16.13", ReplaceWith("Denunciation"))
    Denunceation,
    BorderConflict,
    SettledCitiesNearUs,
    AgreedToNotSettleNearUs,
    IgnoreThemSettlingNearUs,
    ProvideMilitaryUnit,
    EverBeenFriends,
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

class DiplomacyManager() {

    companion object {
        /** The value city-state influence can't go below */
        const val MINIMUM_INFLUENCE = -60f
    }

    @Suppress("JoinDeclarationAndAssignment")  // incorrect warning - constructor would need to be higher in scope
    @Transient
    lateinit var civInfo: CivilizationInfo

    // since this needs to be checked a lot during travel, putting it in a transient is a good performance booster
    @Transient
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
     *  Won't go below [MINIMUM_INFLUENCE]. Note this declaration leads to Major Civs getting MINIMUM_INFLUENCE serialized, but that is ignored. */
    var influence = 0f
        private set
        get() = if (civInfo.isAtWarWith(otherCiv())) MINIMUM_INFLUENCE else field

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
            influence <= -30 || civInfo.isAtWarWith(otherCiv()) -> RelationshipLevel.Unforgivable
            influence < 30 && civInfo.getTributeWillingness(otherCiv()) > 0 -> RelationshipLevel.Afraid
            influence < 0 -> RelationshipLevel.Enemy
            influence >= 60 && civInfo.getAllyCiv() == otherCivName -> RelationshipLevel.Ally
            influence >= 30 -> RelationshipLevel.Friend
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
                relationshipLevel() >= RelationshipLevel.Ally -> ceil((influence - 60f) / dropPerTurn).toInt() + 1
                relationshipLevel() >= RelationshipLevel.Friend -> ceil((influence - 30f) / dropPerTurn).toInt() + 1
                else -> 0
            }
        }
        return 0
    }

    @Suppress("unused")  //todo Finish original intent or remove
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

    // To be run from City-State DiplomacyManager, which holds the influence. Resting point for every major civ can be different.
    private fun getCityStateInfluenceRestingPoint(): Float {
        var restingPoint = 0f

        for (unique in otherCiv().getMatchingUniques("Resting point for Influence with City-States is increased by []"))
            restingPoint += unique.params[0].toInt()

        if (civInfo.cities.any()) // no capital if no cities
            for (unique in otherCiv().getMatchingUniques("Resting point for Influence with City-States following this religion []"))
                if (otherCiv().religionManager.religion?.name == civInfo.getCapital().religion.getMajorityReligionName())
                    restingPoint += unique.params[0].toInt()

        if (diplomaticStatus == DiplomaticStatus.Protector) restingPoint += 10

        if (hasFlag(DiplomacyFlags.WaryOf)) restingPoint -= 20

        return restingPoint
    }

    private fun getCityStateInfluenceDegrade(): Float {
        if (influence < getCityStateInfluenceRestingPoint())
            return 0f

        val decrement = when {
            civInfo.cityStatePersonality == CityStatePersonality.Hostile -> 1.5f
            otherCiv().isMinorCivAggressor() -> 2f
            else -> 1f
        }

        var modifierPercent = 0f
        for (unique in otherCiv().getMatchingUniques("City-State Influence degrades []% slower"))
            modifierPercent -= unique.params[0].toFloat()

        val religion = if (civInfo.cities.isEmpty()) null
            else civInfo.getCapital().religion.getMajorityReligionName()
        if (religion != null && religion == otherCiv().religionManager.religion?.name)
            modifierPercent -= 25f  // 25% slower degrade when sharing a religion

        for (civ in civInfo.gameInfo.civilizations.filter { it.isMajorCiv() && it != otherCiv()}) {
            for (unique in civ.getMatchingUniques("Influence of all other civilizations with all city-states degrades []% faster")) {
                modifierPercent += unique.params[0].toFloat()
            }
        }

        return max(0f, decrement) * max(-100f, modifierPercent).toPercent()
    }

    private fun getCityStateInfluenceRecovery(): Float {
        if (influence > getCityStateInfluenceRestingPoint())
            return 0f

        val increment = 1f  // sic: personality does not matter here

        var modifierPercent = 0f

        if (otherCiv().hasUnique("City-State Influence recovers at twice the normal rate"))
            modifierPercent += 100f

        val religion = if (civInfo.cities.isEmpty()) null
            else civInfo.getCapital().religion.getMajorityReligionName()
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
        val counter = ResourceSupplyList()
        val resourcesMap = civInfo.gameInfo.ruleSet.tileResources
        val isResourceFilter: (TradeOffer) -> Boolean = {
            (it.type == TradeType.Strategic_Resource || it.type == TradeType.Luxury_Resource)
                    && civInfo.gameInfo.ruleSet.tileResources.containsKey(it.name)
        }
        for (trade in trades) {
            for (offer in trade.ourOffers.filter(isResourceFilter))
                counter.add(resourcesMap[offer.name]!!, -offer.amount, "Trade")
            for (offer in trade.theirOffers.filter(isResourceFilter))
                counter.add(resourcesMap[offer.name]!!, offer.amount, "Trade")
        }

        for (trade in otherCiv().tradeRequests.filter { it.requestingCiv == civInfo.civName }) {
            for (offer in trade.trade.theirOffers.filter(isResourceFilter))
                counter.add(resourcesMap[offer.name]!!, -offer.amount, "Trade request")
        }

        return counter
    }

    /** Returns the [civilizations][CivilizationInfo] that know about both sides ([civInfo] and [otherCiv]) */
    fun getCommonKnownCivs(): Set<CivilizationInfo> = civInfo.getKnownCivs().intersect(otherCiv().getKnownCivs())

    /** Returns true when the [civInfo]'s territory is considered allied for [otherCiv].
     *  This includes friendly and allied city-states and the open border treaties.
     */
    fun isConsideredFriendlyTerritory(): Boolean {
        if (civInfo.isCityState() &&
            (relationshipLevel() >= RelationshipLevel.Friend || otherCiv().hasUnique("City-State territory always counts as friendly territory")))
            return true
        return hasOpenBorders
    }
    //endregion

    //region state-changing functions
    fun removeUntenableTrades() {
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
                    otherCivTrades.removeAll { it.equals(trade.reverse()) }

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
        if (civInfo.isCityState() && !otherCiv().isCityState())
            nextTurnCityStateInfluence()
        updateEverBeenFriends()
    }

    /** True when the two civs have been friends in the past */
    fun everBeenFriends(): Boolean = hasFlag(DiplomacyFlags.EverBeenFriends)

    /** Set [DiplomacyFlags.EverBeenFriends] if the two civilization are currently at least friends */
    private fun updateEverBeenFriends() {
        if (relationshipLevel() >= RelationshipLevel.Friend && !everBeenFriends())
            setFlag(DiplomacyFlags.EverBeenFriends, -1)
    }

    private fun nextTurnCityStateInfluence() {
        val initialRelationshipLevel = relationshipLevel()

        val restingPoint = getCityStateInfluenceRestingPoint()
        if (influence > restingPoint) {
            val decrement = getCityStateInfluenceDegrade()
            influence = max(restingPoint, influence - decrement)
        } else if (influence < restingPoint) {
            val increment = getCityStateInfluenceRecovery()
            influence = min(restingPoint, influence + increment)
        }
        civInfo.updateAllyCivForCityState()

        if (!civInfo.isDefeated()) { // don't display city state relationship notifications when the city state is currently defeated
            val civCapitalLocation = if (civInfo.cities.isNotEmpty()) civInfo.getCapital().location else null
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
            if (influence < 30  // We usually don't want to bully our friends
            && !hasFlag(DiplomacyFlags.NotifiedAfraid)
            && civInfo.getTributeWillingness(otherCiv()) > 0) {
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
                    civInfo.gameInfo.civilizations.filter { civInfo.isAtWarWith(it) && otherCiv().isAtWarWith(it) }.any()) {
                for (unique in civInfo.getMatchingUniques("Militaristic City-States grant units [] times as fast when you are at war with a common nation")) {
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
                    civInfo.updateDetailedCivResources() // if they were giving us resources
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
        
        val eraInfo = civInfo.getEra()

        if (relationshipLevel() < RelationshipLevel.Friend) {
            if (hasFlag(DiplomacyFlags.ProvideMilitaryUnit)) 
                removeFlag(DiplomacyFlags.ProvideMilitaryUnit)
            return
        }
        
        val variance = listOf(-1, 0, 1).random()
                
        if (eraInfo.undefinedCityStateBonuses() && otherCiv().cityStateType == CityStateType.Militaristic) {
            // Deprecated, assume Civ V values for compatibility
            if (!hasFlag(DiplomacyFlags.ProvideMilitaryUnit) && relationshipLevel() == RelationshipLevel.Friend)
                setFlag(DiplomacyFlags.ProvideMilitaryUnit, 20 + variance)

            if ((!hasFlag(DiplomacyFlags.ProvideMilitaryUnit) || getFlag(DiplomacyFlags.ProvideMilitaryUnit) > 17)
                && relationshipLevel() == RelationshipLevel.Ally)
                setFlag(DiplomacyFlags.ProvideMilitaryUnit, 17 + variance)
        }
        
        if (eraInfo.undefinedCityStateBonuses()) return

        for (bonus in eraInfo.getCityStateBonuses(otherCiv().cityStateType, relationshipLevel())) {
            // Reset the countdown if it has ended, or if we have longer to go than the current maximum (can happen when going from friend to ally)
            if (bonus.isOfType(UniqueType.CityStateMilitaryUnits) &&
               (!hasFlag(DiplomacyFlags.ProvideMilitaryUnit) || getFlag(DiplomacyFlags.ProvideMilitaryUnit) > bonus.params[0].toInt())
            ) {
                setFlag(DiplomacyFlags.ProvideMilitaryUnit, bonus.params[0].toInt() + variance)
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

        diplomaticStatus = DiplomaticStatus.War

        removeModifier(DiplomaticModifiers.YearsOfPeace)
        setFlag(DiplomacyFlags.DeclinedPeace, 10)/// AI won't propose peace for 10 turns
        setFlag(DiplomacyFlags.DeclaredWar, 10) // AI won't agree to trade for 10 turns
        removeFlag(DiplomacyFlags.BorderConflict)
    }

    fun declareWar() {
        val otherCiv = otherCiv()
        val otherCivDiplomacy = otherCivDiplomacy()

        onWarDeclared()
        otherCivDiplomacy.onWarDeclared()

        otherCiv.addNotification("[${civInfo.civName}] has declared war on us!", NotificationIcon.War, civInfo.civName)
        otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration, civInfo.civName))

        getCommonKnownCivs().forEach {
            it.addNotification("[${civInfo.civName}] has declared war on [$otherCivName]!", civInfo.civName, NotificationIcon.War, otherCivName)
        }

        otherCivDiplomacy.setModifier(DiplomaticModifiers.DeclaredWarOnUs, -20f)
        otherCivDiplomacy.removeModifier(DiplomaticModifiers.ReturnedCapturedUnits)
        if (otherCiv.isCityState()) {
            otherCivDiplomacy.setInfluence(-60f)
            civInfo.changeMinorCivsAttacked(1)
            otherCiv.cityStateFunctions.cityStateAttacked(civInfo)
        }

        for (thirdCiv in civInfo.getKnownCivs()) {
            if (thirdCiv.isAtWarWith(otherCiv)) {
                if (thirdCiv.isCityState()) thirdCiv.getDiplomacyManager(civInfo).influence += 10
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

        // Go through our city state allies.
        if (!civInfo.isCityState()) {
            for (thirdCiv in civInfo.getKnownCivs()) {
                if (thirdCiv.isCityState() && thirdCiv.getAllyCiv() == civInfo.civName) {
                    if (thirdCiv.knows(otherCiv) && !thirdCiv.isAtWarWith(otherCiv))
                        thirdCiv.getDiplomacyManager(otherCiv).declareWar()
                    else if (!thirdCiv.knows(otherCiv)) {
                        // Our city state ally has not met them yet, so they have to meet first
                        thirdCiv.makeCivilizationsMeet(otherCiv, warOnContact = true)
                        thirdCiv.getDiplomacyManager(otherCiv).declareWar()
                    }
                }
            }
        }

        // Go through their city state allies.
        if (!otherCiv.isCityState()) {
            for (thirdCiv in otherCiv.getKnownCivs()) {
                if (thirdCiv.isCityState() && thirdCiv.getAllyCiv() == otherCiv.civName) {
                    if (thirdCiv.knows(civInfo) && !thirdCiv.isAtWarWith(civInfo))
                        thirdCiv.getDiplomacyManager(civInfo).declareWar()
                    else if (!thirdCiv.knows(civInfo)) {
                        // Their city state ally has not met us yet, so we have to meet first
                        thirdCiv.makeCivilizationsMeet(civInfo, warOnContact = true)
                        thirdCiv.getDiplomacyManager(civInfo).declareWar()
                    }
                }
            }
        }
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
            // Other city states that are not our ally don't like the fact that we made peace with their enemy
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
            val otherCivRelationshipWithThirdCiv = otherCiv().getDiplomacyManager(thirdCiv).relationshipLevel()
            @Suppress("NON_EXHAUSTIVE_WHEN")  // Better readability
            when (otherCivRelationshipWithThirdCiv) {
                RelationshipLevel.Unforgivable -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies, -15f)
                RelationshipLevel.Enemy -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies, -5f)
                RelationshipLevel.Friend -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies, 5f)
                RelationshipLevel.Ally -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies, 15f)
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
            @Suppress("NON_EXHAUSTIVE_WHEN")  // Better readability
            when (thirdCivRelationshipWithOtherCiv) {
                RelationshipLevel.Unforgivable -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurEnemies, 15f)
                RelationshipLevel.Enemy -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurEnemies, 5f)
                RelationshipLevel.Friend -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurAllies, -5f)
                RelationshipLevel.Ally -> thirdCivDiplomacyManager.addModifier(DiplomaticModifiers.DenouncedOurAllies, -15f)
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
