package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UniqueAbility
import com.unciv.logic.civilization.*
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class RelationshipLevel{
    Unforgivable,
    Enemy,
    Competitor,
    Neutral,
    Favorable,
    Friend,
    Ally
}

enum class DiplomacyFlags{
    DeclinedLuxExchange,
    DeclinedPeace,
    DeclinedResearchAgreement,
    DeclaredWar,
    DeclarationOfFriendship,
    ResearchAgreement,
    Denunceation,
    BorderConflict,
    SettledCitiesNearUs,
    AgreedToNotSettleNearUs,
    IgnoreThemSettlingNearUs,
    ProvideMilitaryUnit
}

enum class DiplomaticModifiers{
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
    FulfilledPromiseToNotSettleCitiesNearUs
}

class DiplomacyManager() {
    
    companion object {
        /** The value city-state influence can't go below */
        const val MINIMUM_INFLUENCE = -60f
    }
    
    @Transient lateinit var civInfo: CivilizationInfo
    // since this needs to be checked a lot during travel, putting it in a transient is a good performance booster
    @Transient var hasOpenBorders=false

    lateinit var otherCivName:String
    var trades = ArrayList<Trade>()
    var diplomaticStatus = DiplomaticStatus.War

    /** Contains various flags (declared war, promised to not settle, declined luxury trade) and the number of turns in which they will expire.
     *  The JSON serialize/deserialize REFUSES to deserialize hashmap keys as Enums, so I'm forced to use strings instead =(
     *  This is so sad Alexa play Despacito */
    private var flagsCountdown = HashMap<String,Int>()

    /** For AI. Positive is good relations, negative is bad.
     * Baseline is 1 point for each turn of peace - so declaring a war upends 40 years of peace, and e.g. capturing a city can be another 30 or 40.
     * As for why it's String and not DiplomaticModifier see FlagsCountdown comment */
    var diplomaticModifiers = HashMap<String,Float>()

    /** For city-states. Influence is saved in the CITY STATE -> major civ Diplomacy, NOT in the major civ -> cty state diplomacy.
     *  Won't go below [MINIMUM_INFLUENCE] */
    var influence = 0f
        set(value) { field = max(value, MINIMUM_INFLUENCE) }

    /** For city-states. Resting point is the value of Influence at which it ceases changing by itself */
    var restingPoint = 0f

    /** Total of each turn Science during Research Agreement */
    var totalOfScienceDuringRA = 0

    fun clone(): DiplomacyManager {
        val toReturn = DiplomacyManager()
        toReturn.otherCivName=otherCivName
        toReturn.diplomaticStatus=diplomaticStatus
        toReturn.trades.addAll(trades.map { it.clone() })
        toReturn.influence = influence
        toReturn.restingPoint = restingPoint
        toReturn.flagsCountdown.putAll(flagsCountdown)
        toReturn.diplomaticModifiers.putAll(diplomaticModifiers)
        toReturn.totalOfScienceDuringRA=totalOfScienceDuringRA
        return toReturn
    }

    constructor(civilizationInfo: CivilizationInfo, OtherCivName:String) : this() {
        civInfo=civilizationInfo
        otherCivName=OtherCivName
        updateHasOpenBorders()
    }

    //region pure functions
    fun otherCiv() = civInfo.gameInfo.getCivilization(otherCivName)
    fun otherCivDiplomacy() = otherCiv().getDiplomacyManager(civInfo)

    fun turnsToPeaceTreaty(): Int {
        for(trade in trades)
            for(offer in trade.ourOffers)
                if(offer.name == Constants.peaceTreaty && offer.duration > 0) return offer.duration
        return 0
    }

    fun opinionOfOtherCiv() = diplomaticModifiers.values.sum()

    fun relationshipLevel(): RelationshipLevel {
        if(civInfo.isPlayerCivilization() && otherCiv().isPlayerCivilization())
            return RelationshipLevel.Neutral // People make their own choices.

        if(civInfo.isPlayerCivilization())
            return otherCiv().getDiplomacyManager(civInfo).relationshipLevel()

        if(civInfo.isCityState()) {
            if(influence <= -60) return RelationshipLevel.Unforgivable
            if(influence <= -30 || civInfo.isAtWarWith(otherCiv())) return RelationshipLevel.Enemy

            if(influence >= 60) return RelationshipLevel.Ally
            if(influence >= 30) return RelationshipLevel.Friend
            return RelationshipLevel.Neutral
        }

        // not entirely sure what to do between AI civs, because they probably have different views of each other,
        // maybe we need to average their views of each other? That makes sense to me.

        val opinion = opinionOfOtherCiv()
        return when {
            opinion <= -80 -> RelationshipLevel.Unforgivable
            opinion <= -40 || civInfo.isAtWarWith(otherCiv()) -> RelationshipLevel.Enemy  /* During wartime, the estimation in which you are held may be enemy OR unforgivable */
            opinion <= -15 -> RelationshipLevel.Competitor
            
            opinion >= 80  -> RelationshipLevel.Ally
            opinion >= 40  -> RelationshipLevel.Friend
            opinion >= 15  -> RelationshipLevel.Favorable
            else           -> RelationshipLevel.Neutral
        }
    }

    /** Returns the number of turns to degrade from Ally or from Friend */
    fun getTurnsToRelationshipChange(): Int {
        if (otherCiv().isCityState())
            return otherCivDiplomacy().getTurnsToRelationshipChange()

        if (civInfo.isCityState() && !otherCiv().isCityState()) {
            val hasCityStateInfluenceBonus = otherCiv().nation.unique == UniqueAbility.HELLENIC_LEAGUE
            val dropPerTurn = if(hasCityStateInfluenceBonus) .5f else 1f

            if (relationshipLevel() >= RelationshipLevel.Ally)
                return ceil((influence - 60f) / dropPerTurn).toInt() + 1
            else if (relationshipLevel() >= RelationshipLevel.Friend)
                return ceil((influence - 30f) / dropPerTurn).toInt() + 1
            else
                return 0
        }
        return 0
    }

    fun canDeclareWar() = turnsToPeaceTreaty()==0 && diplomaticStatus != DiplomaticStatus.War
    //Used for nuke
    fun canAttack() = turnsToPeaceTreaty()==0

    fun goldPerTurn():Int{
        var goldPerTurnForUs = 0
        for(trade in trades) {
            for (offer in trade.ourOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs -= offer.amount
            for (offer in trade.theirOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs += offer.amount
        }
        return goldPerTurnForUs
    }

    fun sciencefromResearchAgreement(){
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
        val isResourceFilter: (TradeOffer) -> Boolean = { it.type == TradeType.Strategic_Resource || it.type == TradeType.Luxury_Resource }
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
        if(civInfo.isCityState() && relationshipLevel() >= RelationshipLevel.Friend)
            return true
        return hasOpenBorders
    }
    //endregion

    //region state-changing functions
    fun removeUntenebleTrades() {

        for (trade in trades.toList()) {

            // Every cancelled trade can change this - if 1 resource is missing,
            // don't cancel all trades of that resource, only cancel one (the first one, as it happens, since they're added chronologically)
            val negativeCivResources = civInfo.getCivResources()
                    .filter { it.amount < 0 }.map { it.resource.name }

            for (offer in trade.ourOffers) {
                if (offer.type in listOf(TradeType.Luxury_Resource, TradeType.Strategic_Resource)
                        && offer.name in negativeCivResources) {
                    trades.remove(trade)
                    val otherCivTrades = otherCiv().getDiplomacyManager(civInfo).trades
                    otherCivTrades.removeAll { it.equals(trade.reverse()) }
                    civInfo.addNotification("One of our trades with [$otherCivName] has been cut short", null, Color.GOLD)
                    otherCiv().addNotification("One of our trades with [${civInfo.civName}] has been cut short", null, Color.GOLD)
                    civInfo.updateDetailedCivResources()
                }
            }
        }
    }

    // for performance reasons we don't want to call this every time we want to see if a unit can move through a tile
    fun updateHasOpenBorders() {
        val newHasOpenBorders = trades.flatMap { it.theirOffers }
                .any { it.name == Constants.openBorders && it.duration > 0 }

        val bordersWereClosed = hasOpenBorders && !newHasOpenBorders
        hasOpenBorders = newHasOpenBorders

        if (bordersWereClosed) { // borders were closed, get out!
            for (unit in civInfo.getCivUnits().filter { it.currentTile.getOwner()?.civName == otherCivName }) {
                unit.movement.teleportToClosestMoveableTile()
            }
        }
    }

    fun nextTurn(){
        nextTurnTrades()
        removeUntenebleTrades()
        updateHasOpenBorders()
        nextTurnDiplomaticModifiers()
        nextTurnFlags()
        if (civInfo.isCityState() && !otherCiv().isCityState())
            nextTurnCityStateInfluence()
    }

    private fun nextTurnCityStateInfluence() {
        val initialRelationshipLevel = relationshipLevel()

        val hasCityStateInfluenceBonus = otherCiv().nation.unique == UniqueAbility.HELLENIC_LEAGUE
        val increment = if (hasCityStateInfluenceBonus) 2f else 1f
        val decrement = if (hasCityStateInfluenceBonus) .5f else 1f

        if (influence > restingPoint)
            influence = max(restingPoint, influence - decrement)
        else if (influence < restingPoint)
            influence = min(restingPoint, influence + increment)
        else influence = restingPoint

        if(!civInfo.isDefeated()) { // don't display city state relationship notifications when the city state is currently defeated
            val civCapitalLocation = if (civInfo.cities.isNotEmpty()) civInfo.getCapital().location else null
            if (getTurnsToRelationshipChange() == 1)
                otherCiv().addNotification("Your relationship with [${civInfo.civName}] is about to degrade", civCapitalLocation, Color.GOLD)

            if (initialRelationshipLevel >= RelationshipLevel.Friend && initialRelationshipLevel != relationshipLevel())
                otherCiv().addNotification("Your relationship with [${civInfo.civName}] degraded", civCapitalLocation, Color.GOLD)
        }
    }

    private fun nextTurnFlags() {
        for (flag in flagsCountdown.keys.toList()) {
            if (flag == DiplomacyFlags.ResearchAgreement.name){
                totalOfScienceDuringRA += civInfo.statsForNextTurn.science.toInt()
            }
            flagsCountdown[flag] = flagsCountdown[flag]!! - 1
            if (flagsCountdown[flag] == 0) {
                if (flag == DiplomacyFlags.ResearchAgreement.name && !otherCivDiplomacy().hasFlag(DiplomacyFlags.ResearchAgreement))
                    sciencefromResearchAgreement()
                if (flag == DiplomacyFlags.ProvideMilitaryUnit.name && civInfo.cities.isEmpty() || otherCiv().cities.isEmpty())
                    continue
                flagsCountdown.remove(flag)
                if (flag == DiplomacyFlags.AgreedToNotSettleNearUs.name)
                    addModifier(DiplomaticModifiers.FulfilledPromiseToNotSettleCitiesNearUs, 10f)
                else if (flag == DiplomacyFlags.ProvideMilitaryUnit.name)
                    civInfo.giftMilitaryUnitTo(otherCiv())
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
                        civInfo.addNotification("[${offer.name}] from [$otherCivName] has ended", null, Color.GOLD)
                    else civInfo.addNotification("[${offer.name}] to [$otherCivName] has ended", null, Color.GOLD)

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

        setFriendshipBasedModifier()

        if (!hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            revertToZero(DiplomaticModifiers.DeclarationOfFriendship, 1 / 2f) //decreases slowly and will revert to full if it is declared later

        if (otherCiv().isCityState() && otherCiv().getCityStateType() == CityStateType.Militaristic) {
            if (relationshipLevel() < RelationshipLevel.Friend) {
                if (hasFlag(DiplomacyFlags.ProvideMilitaryUnit)) removeFlag(DiplomacyFlags.ProvideMilitaryUnit)
            } else {
                if (!hasFlag(DiplomacyFlags.ProvideMilitaryUnit)) setFlag(DiplomacyFlags.ProvideMilitaryUnit, 20)
            }
        }
    }

    /** Everything that happens to both sides equally when war is delcared by one side on the other */
    private fun onWarDeclared(){
        diplomaticStatus = DiplomaticStatus.War

        // Cancel all trades.
        for(trade in trades)
            for(offer in trade.theirOffers.filter { it.duration>0 })
                civInfo.addNotification("["+offer.name+"] from [$otherCivName] has ended",null, Color.GOLD)
        trades.clear()
        updateHasOpenBorders()

        setFlag(DiplomacyFlags.DeclinedPeace,10)/// AI won't propose peace for 10 turns
        setFlag(DiplomacyFlags.DeclaredWar,10) // AI won't agree to trade for 10 turns
        removeFlag(DiplomacyFlags.BorderConflict)
    }

    fun declareWar(){
        val otherCiv = otherCiv()
        val otherCivDiplomacy = otherCivDiplomacy()

        onWarDeclared()
        otherCivDiplomacy.onWarDeclared()

        otherCiv.addNotification("[${civInfo.civName}] has declared war on us!",null, Color.RED)
        otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration,civInfo.civName))

        getCommonKnownCivs().forEach {
            it.addNotification("[${civInfo.civName}] has declared war on [${otherCiv().civName}]!", null, Color.RED)
        }

        otherCivDiplomacy.setModifier(DiplomaticModifiers.DeclaredWarOnUs,-20f)
        if(otherCiv.isCityState()) otherCivDiplomacy.influence -= 60

        for(thirdCiv in civInfo.getKnownCivs()){
            if(thirdCiv.isAtWarWith(otherCiv))
                thirdCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.SharedEnemy,5f)
            else thirdCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.WarMongerer,-5f)
        }

        if(hasFlag(DiplomacyFlags.DeclarationOfFriendship)) {
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
        if(hasFlag(DiplomacyFlags.ResearchAgreement)) {
            removeFlag(DiplomacyFlags.ResearchAgreement)
            totalOfScienceDuringRA = 0
            otherCivDiplomacy.totalOfScienceDuringRA = 0
        }
        otherCivDiplomacy.removeFlag(DiplomacyFlags.ResearchAgreement)
        if (otherCiv.isCityState()) otherCiv.updateAllyCivForCityState()

        if (!civInfo.isCityState()) {
            for (thirdCiv in civInfo.getKnownCivs()) {
                if (thirdCiv.isCityState() && thirdCiv.getAllyCiv() == civInfo.civName
                        && thirdCiv.knows(otherCiv)
                        && thirdCiv.getDiplomacyManager(otherCiv).canDeclareWar()) {
                    thirdCiv.getDiplomacyManager(otherCiv).declareWar()
                }
            }
        }
        if (!otherCiv.isCityState()) {
            for (thirdCiv in otherCiv.getKnownCivs()) {
                if (thirdCiv.isCityState() && thirdCiv.getAllyCiv() == otherCiv.civName
                        && thirdCiv.knows(civInfo)
                        && thirdCiv.getDiplomacyManager(civInfo).canDeclareWar()) {
                    thirdCiv.getDiplomacyManager(civInfo).declareWar()
                }
            }
        }
    }

    fun makePeace() {
        diplomaticStatus = DiplomaticStatus.Peace
        otherCivDiplomacy().diplomaticStatus = DiplomaticStatus.Peace

        if (otherCiv().isAtWarWith(civInfo)) {
            for (civ in getCommonKnownCivs()) {
                civ.addNotification(
                        "[${civInfo.civName}] and [${otherCiv().civName}] have signed the Peace Treaty!",
                        null,
                        Color.WHITE
                )
            }
        }

        val otherCiv = otherCiv()
        // We get out of their territory
        for (unit in civInfo.getCivUnits().filter { it.getTile().getOwner() == otherCiv })
            unit.movement.teleportToClosestMoveableTile()

        // And we get out of theirs
        for (unit in otherCiv.getCivUnits().filter { it.getTile().getOwner() == civInfo })
            unit.movement.teleportToClosestMoveableTile()
    }

    fun hasFlag(flag:DiplomacyFlags) = flagsCountdown.containsKey(flag.name)
    fun setFlag(flag: DiplomacyFlags, amount: Int){ flagsCountdown[flag.name]=amount}
    fun removeFlag(flag: DiplomacyFlags){ flagsCountdown.remove(flag.name)}

    fun addModifier(modifier: DiplomaticModifiers, amount:Float){
        val modifierString = modifier.name
        if(!hasModifier(modifier)) setModifier(modifier,0f)
        diplomaticModifiers[modifierString] = diplomaticModifiers[modifierString]!!+amount
        if(diplomaticModifiers[modifierString]==0f) diplomaticModifiers.remove(modifierString)
    }

    fun setModifier(modifier: DiplomaticModifiers, amount: Float){
        diplomaticModifiers[modifier.name] = amount
    }

    fun getModifier(modifier: DiplomaticModifiers): Float {
        if(!hasModifier(modifier)) return 0f
        return diplomaticModifiers[modifier.name]!!
    }

    fun removeModifier(modifier: DiplomaticModifiers) = diplomaticModifiers.remove(modifier.name)
    fun hasModifier(modifier: DiplomaticModifiers) = diplomaticModifiers.containsKey(modifier.name)

    /** @param amount always positive, so you don't need to think about it */
    fun revertToZero(modifier: DiplomaticModifiers, amount: Float){
        if(!hasModifier(modifier)) return
        val currentAmount = getModifier(modifier)
        if(currentAmount > 0) addModifier(modifier,-amount)
        else addModifier(modifier,amount)
    }

    fun signDeclarationOfFriendship(){
        setModifier(DiplomaticModifiers.DeclarationOfFriendship,35f)
        otherCivDiplomacy().setModifier(DiplomaticModifiers.DeclarationOfFriendship,35f)
        setFlag(DiplomacyFlags.DeclarationOfFriendship,30)
        otherCivDiplomacy().setFlag(DiplomacyFlags.DeclarationOfFriendship,30)
        if (otherCiv().playerType == PlayerType.Human)
            otherCiv().addNotification("[${civInfo.civName}] and [${otherCiv().civName}] have signed the Declaration of Friendship!", null, Color.WHITE)

        for (thirdCiv in getCommonKnownCivs().filter { it.isMajorCiv() }) {
            thirdCiv.addNotification("[${civInfo.civName}] and [${otherCiv().civName}] have signed the Declaration of Friendship!", null, Color.WHITE)
            thirdCiv.getDiplomacyManager(civInfo).setFriendshipBasedModifier()
        }
    }

    fun setFriendshipBasedModifier(){
        removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies)
        removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies)
        for(thirdCiv in getCommonKnownCivs()
                .filter { it.getDiplomacyManager(civInfo).hasFlag(DiplomacyFlags.DeclarationOfFriendship) }) {
            val otherCivRelationshipWithThirdCiv = otherCiv().getDiplomacyManager(thirdCiv).relationshipLevel()
            when (otherCivRelationshipWithThirdCiv) {
                RelationshipLevel.Unforgivable -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies, -15f)
                RelationshipLevel.Enemy -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies, -5f)
                RelationshipLevel.Friend -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies, 5f)
                RelationshipLevel.Ally -> addModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies, 15f)
            }
        }
    }

    fun denounce(){
        setModifier(DiplomaticModifiers.Denunciation,-35f)
        otherCivDiplomacy().setModifier(DiplomaticModifiers.Denunciation,-35f)
        setFlag(DiplomacyFlags.Denunceation,30)
        otherCivDiplomacy().setFlag(DiplomacyFlags.Denunceation,30)

        otherCiv().addNotification("[${civInfo.civName}] has denounced us!", Color.RED)

        // We, A, are denouncing B. What do other major civs (C,D, etc) think of this?
        getCommonKnownCivs().filter { it.isMajorCiv() }.forEach { thirdCiv ->
            thirdCiv.addNotification("[${civInfo.civName}] has denounced [${otherCiv().civName}]!", null, Color.RED)
            val thirdCivRelationshipWithOtherCiv = thirdCiv.getDiplomacyManager(otherCiv()).relationshipLevel()
            when(thirdCivRelationshipWithOtherCiv){
                RelationshipLevel.Unforgivable -> addModifier(DiplomaticModifiers.DenouncedOurEnemies,15f)
                RelationshipLevel.Enemy -> addModifier(DiplomaticModifiers.DenouncedOurEnemies,5f)
                RelationshipLevel.Friend -> addModifier(DiplomaticModifiers.DenouncedOurAllies,-5f)
                RelationshipLevel.Ally -> addModifier(DiplomaticModifiers.DenouncedOurAllies,-15f)
            }
        }
    }

    fun agreeNotToSettleNear(){
        otherCivDiplomacy().setFlag(DiplomacyFlags.AgreedToNotSettleNearUs,100)
        addModifier(DiplomaticModifiers.UnacceptableDemands,-10f)
        otherCiv().addNotification("[${civInfo.civName}] agreed to stop settling cities near us!", Color.MAROON)
    }

    fun refuseDemandNotToSettleNear(){
        addModifier(DiplomaticModifiers.UnacceptableDemands,-20f)
        otherCivDiplomacy().setFlag(DiplomacyFlags.IgnoreThemSettlingNearUs,100)
        otherCivDiplomacy().addModifier(DiplomaticModifiers.RefusedToNotSettleCitiesNearUs,-15f)
        otherCiv().addNotification("[${civInfo.civName}] refused to stop settling cities near us!", Color.MAROON)
    }


    //endregion
}
