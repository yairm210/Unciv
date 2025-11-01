package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Immutable
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

enum class RelationshipLevel(val color: Color) {
    // DiplomaticStatus.War is tested separately for the Diplomacy Screen. Colored RED.
    // DiplomaticStatus.DefensivePact - similar. Colored PURPLE.
    Unforgivable(Color.BROWN),
    Enemy(Color.ORANGE),
    Afraid(Color.YELLOW),
    Competitor(Color.GRAY),
    Neutral(Color.WHITE),
    Favorable(Color(0x14cc3cff)),  // HSV(133,90,80)
    Friend(Color.ROYAL),
    Ally(Color.CYAN)
    ;
    @Pure
    operator fun plus(delta: Int): RelationshipLevel {
        val newOrdinal = (ordinal + delta).coerceIn(0, entries.size - 1)
        return entries[newOrdinal]
    }
}

enum class DiplomacyFlags {
    DeclinedLuxExchange,
    DeclinedPeace,
    DeclinedResearchAgreement,
    DeclinedEmbassy,
    DeclinedOpenBorders,
    DeclaredWar,
    DeclarationOfFriendship,
    DeclinedDeclarationOfFriendship,
    DefensivePact,
    DeclinedDefensivePact,
    DeclinedJoinWarOffer,
    ResearchAgreement,
    BorderConflict,
    TilesStolen,

    SettledCitiesNearUs,
    AgreedToNotSettleNearUs,
    IgnoreThemSettlingNearUs,

    SpreadReligionInOurCities,
    AgreedToNotSpreadReligion,
    IgnoreThemSpreadingReligion,

    DiscoveredSpiesInOurCities,
    AgreedToNotSendSpies,
    IgnoreThemSendingSpies,

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
    ResourceTradesCutShort,
}

enum class DiplomaticModifiers(val text: String) {
    // Negative
    DeclaredWarOnUs("You declared war on us!"),
    WarMongerer("Your warmongering ways are unacceptable to us."),
    CapturedOurCities("You have captured our cities!"),
    DeclaredFriendshipWithOurEnemies("You have declared friendship with our enemies!"),
    BetrayedDeclarationOfFriendship("Your so-called 'friendship' is worth nothing."),
    SignedDefensivePactWithOurEnemies("You have declared a defensive pact with our enemies!"),
    BetrayedDefensivePact("Your so-called 'defensive pact' is worth nothing."),
    Denunciation("You have publicly denounced us!"),
    DenouncedOurAllies("You have denounced our allies"),
    RefusedToNotSettleCitiesNearUs("You refused to stop settling cities near us"),
    RefusedToNotSpreadReligionToUs("You refused to stop spreading religion to us"),
    RefusedToNotSendingSpiesToUs("You refused to stop spying on us"),
    BetrayedPromiseToNotSettleCitiesNearUs("You betrayed your promise to not settle cities near us"),
    BetrayedPromiseToNotSpreadReligionToUs("You betrayed your promise to not spread your religion to us"),
    BetrayedPromiseToNotSendingSpiesToUs("You betrayed your promise to stop spying on us"),
    
    UnacceptableDemands("Your arrogant demands are in bad taste"),
    UsedNuclearWeapons("Your use of nuclear weapons is disgusting!"),
    StealingTerritory("You have stolen our lands!"),
    DestroyedProtectedMinor("You destroyed City-States that were under our protection!"),
    AttackedProtectedMinor("You attacked City-States that were under our protection!"),
    AttackedAlliedMinor("You attacked our allied City-States!"),
    BulliedProtectedMinor("You demanded tribute from City-States that were under our protection!"),
    SidedWithProtectedMinor("You sided with a City-State over us"),
    SpiedOnUs("You spied on us!"),
    StoleOurAlly("You took the alliance we had with a City-State"),

    // Positive
    EstablishedEmbassy("We have an embassy in your capital"),
    ReceivedEmbassy("You have an embassy in our capital"),
    SharedEmbassies("We have shared embassies"),
    YearsOfPeace("Years of peace have strengthened our relations."),
    SharedEnemy("Our mutual military struggle brings us closer together."),
    LiberatedCity("We applaud your liberation of conquered cities!"),
    DeclarationOfFriendship("We have signed a public declaration of friendship"),
    DeclaredFriendshipWithOurAllies("You have declared friendship with our allies"),
    DefensivePact("We have signed a promise to protect each other."),
    SignedDefensivePactWithOurAllies("You have declared a defensive pact with our allies"),
    DenouncedOurEnemies("You have denounced our enemies"),
    OpenBorders("Our open borders have brought us closer together."),
    FulfilledPromiseToNotSettleCitiesNearUs("You fulfilled your promise to stop settling cities near us!"),
    FulfilledPromiseToNotSpreadReligion("You fulfilled your promise to stop spreading religion to us!"),
    FulfilledPromiseToNotSpy("You fulfilled your promise to stop spying on us!"),
    GaveUsUnits("You gave us units!"),
    GaveUsGifts("We appreciate your gifts"),
    ReturnedCapturedUnits("You returned captured units to us"),
    BelieveSameReligion("We believe in the same religion");

    companion object{
        @Immutable private val valuesAsMap = entries.associateBy { it.name }
        @Pure fun safeValueOf(name: String) = valuesAsMap[name]
    }
}

class DiplomacyManager() : IsPartOfGameInfoSerialization {

    companion object {
        /** The value city-state influence can't go below */
        const val MINIMUM_INFLUENCE = -60f
    }

    @Transient
    lateinit var civInfo: Civilization

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
    internal var flagsCountdown = HashMap<String, Int>()

    /** For AI. Positive is good relations, negative is bad.
     * Baseline is 1 point for each turn of peace - so declaring a war upends 40 years of peace, and e.g. capturing a city can be another 30 or 40.
     * As for why it's String and not DiplomaticModifier see FlagsCountdown comment */
    var diplomaticModifiers = HashMap<String, Float>()

    /** For city-states. Influence is saved in the CITY STATE -> major civ Diplomacy, NOT in the major civ -> city state diplomacy.
     * Access via getInfluence() and setInfluence() unless you know what you're doing.
     * Note that not using the setter skips recalculating the ally and bounds checks,
     * and skipping the getter bypasses the modified value when at war */
    internal var influence = 0f

    /** Total of each turn Science during Research Agreement */
    internal var totalOfScienceDuringRA = 0

    fun clone(): DiplomacyManager {
        val toReturn = DiplomacyManager()
        toReturn.otherCivName = otherCivName
        toReturn.mOtherCiv = mOtherCiv
        toReturn.diplomaticStatus = diplomaticStatus
        toReturn.trades.addAll(trades.map { it.clone() })
        toReturn.influence = influence
        toReturn.flagsCountdown.putAll(flagsCountdown)
        toReturn.diplomaticModifiers.putAll(diplomaticModifiers)
        toReturn.totalOfScienceDuringRA = totalOfScienceDuringRA
        return toReturn
    }

    constructor(civilization: Civilization, mOtherCivName: String) : this() {
        civInfo = civilization
        otherCivName = mOtherCivName
        updateHasOpenBorders()
    }

    constructor(civilization: Civilization, otherCiv: Civilization) : this() {
        civInfo = civilization
        mOtherCiv = otherCiv
        otherCivName = otherCiv.civName
        updateHasOpenBorders()
    }

    //region pure functions

    @Cache
    @Transient
    private lateinit var mOtherCiv: Civilization
    @Readonly fun otherCiv(): Civilization {
        if (!::mOtherCiv.isInitialized)
            mOtherCiv = civInfo.gameInfo.getCivilization(otherCivName)
        return mOtherCiv
    }
    @Readonly fun otherCivDiplomacy() = otherCiv().getDiplomacyManager(civInfo)!!

    @Readonly
    fun turnsToPeaceTreaty(): Int {
        for (trade in trades)
            for (offer in trade.ourOffers)
                if (offer.name == Constants.peaceTreaty && offer.duration > 0) return offer.duration
        return 0
    }

    @Readonly
    fun opinionOfOtherCiv(): Float {
        var modifierSum = diplomaticModifiers.values.sum()
        // Angry about attacked CS and destroyed CS do not stack
        if (hasModifier(DiplomaticModifiers.DestroyedProtectedMinor) && hasModifier(DiplomaticModifiers.AttackedProtectedMinor))
            modifierSum -= getModifier(DiplomaticModifiers.AttackedProtectedMinor)
        return modifierSum
    }

    /** Related to [relationshipLevel], this compares with a specific outcome.
     *
     *  It is cheap unless you ask such that Neutral / Afraid on a CityState need to be distinguished and influence is currently in 0 until 30.
     *  Thus it can be far cheaper than first retrieving [relationshipLevel] and then comparing.
     *
     *  Readability shortcuts: [isRelationshipLevelEQ], [isRelationshipLevelGE], [isRelationshipLevelGT], [isRelationshipLevelLE], [isRelationshipLevelLT]
     *
     *  @param comparesAs same as [RelationshipLevel.compareTo]
     *  @return `true` if [relationshipLevel] ().compareTo([level]) == [comparesAs] - or: when [comparesAs] > 0 only if [relationshipLevel] > [level] and so on.
     */
    @Readonly
    private fun compareRelationshipLevel(level: RelationshipLevel, comparesAs: Int): Boolean {
        if (!civInfo.isCityState)
            return relationshipLevel().compareTo(level).sign == comparesAs
        return when(level) {
            RelationshipLevel.Afraid -> when {
                comparesAs < 0 -> getInfluence() < 0
                comparesAs > 0 -> getInfluence() >= 30 || relationshipLevel() > level
                else -> getInfluence().let { it >= 0 && it < 30 } && relationshipLevel() == level
            }
            RelationshipLevel.Neutral -> when {
                comparesAs < 0 -> getInfluence() < 0 || relationshipLevel() < level
                comparesAs > 0 -> getInfluence() >= 30
                else -> getInfluence().let { it >= 0 && it < 30 } && relationshipLevel() == level
            }
            else ->
                // Outside the potentially expensive questions, do it the easy way
                // Except - Enum.compareTo does not behave quite like other compareTo's
                // or like kotlinlang.org says, thus the `sign`
                relationshipLevel().compareTo(level).sign == comparesAs
        }
    }
    /** @see compareRelationshipLevel */
    @Readonly fun isRelationshipLevelEQ(level: RelationshipLevel) =
            compareRelationshipLevel(level, 0)
    /** @see compareRelationshipLevel */
    @Readonly fun isRelationshipLevelLT(level: RelationshipLevel) =
            compareRelationshipLevel(level, -1)
    /** @see compareRelationshipLevel */
    @Readonly fun isRelationshipLevelGT(level: RelationshipLevel) =
            compareRelationshipLevel(level, 1)
    /** @see compareRelationshipLevel */
    @Readonly fun isRelationshipLevelLE(level: RelationshipLevel) =
            if (level == RelationshipLevel.Ally) true
            else compareRelationshipLevel(level + 1, -1)
    /** @see compareRelationshipLevel */
    @Readonly
    fun isRelationshipLevelGE(level: RelationshipLevel) =
            if (level == RelationshipLevel.Unforgivable) true
            else compareRelationshipLevel(level + -1, 1)

    /** Careful: Cheap unless this is a CityState and influence is in 0 until 30,
     *  where the distinction Neutral/Afraid gets expensive.
     *  @see compareRelationshipLevel
     *  @see relationshipIgnoreAfraid
     */
    @Readonly
    fun relationshipLevel(): RelationshipLevel {
        val level = relationshipIgnoreAfraid()
        return when {
            level != RelationshipLevel.Neutral || !civInfo.isCityState -> level
            civInfo.cityStateFunctions.getTributeWillingness(otherCiv()) > 0 -> RelationshipLevel.Afraid
            else -> RelationshipLevel.Neutral
        }
    }

    /** Same as [relationshipLevel] but omits the distinction Neutral/Afraid, which can be _much_ cheaper */
    @Readonly
    fun relationshipIgnoreAfraid(): RelationshipLevel {
        if (civInfo.isHuman() && otherCiv().isHuman())
            return RelationshipLevel.Neutral // People make their own choices.

        if (civInfo.isHuman())
            return otherCivDiplomacy().relationshipLevel()

        if (civInfo.isCityState) return when {
            getInfluence() <= -30 -> RelationshipLevel.Unforgivable  // getInfluence tests isAtWarWith
            getInfluence() < 0 -> RelationshipLevel.Enemy
            getInfluence() >= 60 && civInfo.allyCiv == otherCiv() -> RelationshipLevel.Ally
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

    @Readonly
    private fun believesSameReligion(): Boolean {
        // what is the majorityReligion of civInfo? If it is null, we immediately return false
        val civMajorityReligion = civInfo.religionManager.getMajorityReligion() ?: return false
        // if not yet returned false from previous line, return the Boolean isMajorityReligionForCiv
        // true if majorityReligion of civInfo is also majorityReligion of otherCiv, false otherwise
        return otherCiv().religionManager.isMajorityReligionForCiv(civMajorityReligion)
    }

    /** Returns the number of turns to degrade from Ally or from Friend */
    @Readonly
    fun getTurnsToRelationshipChange(): Int {
        if (otherCiv().isCityState)
            return otherCivDiplomacy().getTurnsToRelationshipChange()

        if (civInfo.isCityState && !otherCiv().isCityState) {
            val dropPerTurn = getCityStateInfluenceDegrade()
            return when {
                dropPerTurn == 0f -> 0
                isRelationshipLevelEQ(RelationshipLevel.Ally) -> ceil((getInfluence() - 60f) / dropPerTurn).toInt() + 1
                isRelationshipLevelEQ(RelationshipLevel.Friend) -> ceil((getInfluence() - 30f) / dropPerTurn).toInt() + 1
                else -> 0
            }
        }
        return 0
    }

    fun addInfluence(amount: Float) {
        setInfluence(influence + amount)
    }

    /**
     * Reduces the influence to zero, or if they have negative influence does nothing
     * @param amount A positive value to subtract from the influecne
     */
    fun reduceInfluence(amount: Float) {
        if (influence <= 0) return
        influence = (influence - amount).coerceAtLeast(0f)
    }

    fun setInfluence(amount: Float) {
        influence = max(amount, MINIMUM_INFLUENCE)
        civInfo.cityStateFunctions.updateAllyCivForCityState()
    }

    @Readonly
    fun getInfluence() = if (civInfo.isAtWarWith(otherCiv())) MINIMUM_INFLUENCE else influence

    // To be run from City-State DiplomacyManager, which holds the influence. Resting point for every major civ can be different.
    @Readonly
    internal fun getCityStateInfluenceRestingPoint(): Float {
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

    @Readonly
    internal fun getCityStateInfluenceDegrade(): Float {
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


    @Readonly
    fun canDeclareWar() = !civInfo.isDefeated() && !otherCiv().isDefeated()
            && turnsToPeaceTreaty() == 0 && diplomaticStatus != DiplomaticStatus.War

    fun declareWar(declareWarReason: DeclareWarReason = DeclareWarReason(WarType.DirectWar)) =
        DeclareWar.declareWar(this, declareWarReason)

    //Used for nuke
    @Readonly fun canAttack() = turnsToPeaceTreaty() == 0

    @Readonly
    fun goldPerTurn(): Int {
        var goldPerTurnForUs = 0
        for (trade in trades) {
            for (offer in trade.ourOffers.filter { it.type == TradeOfferType.Gold_Per_Turn })
                goldPerTurnForUs -= offer.amount
            for (offer in trade.theirOffers.filter { it.type == TradeOfferType.Gold_Per_Turn })
                goldPerTurnForUs += offer.amount
        }
        return goldPerTurnForUs
    }
    
    @Readonly
    fun resourcesFromTrade(): ResourceSupplyList {
        val newResourceSupplyList = ResourceSupplyList()
        val resourcesMap = civInfo.gameInfo.ruleset.tileResources
        val isResourceFilter: (TradeOffer) -> Boolean = {
            (it.type == TradeOfferType.Strategic_Resource || it.type == TradeOfferType.Luxury_Resource)
                    && resourcesMap.containsKey(it.name)
                    && !resourcesMap[it.name]!!.isStockpiled
        }
        for (trade in trades) {
            for (offer in trade.ourOffers.filter(isResourceFilter))
                newResourceSupplyList.add(resourcesMap[offer.name]!!, "Trade", -offer.amount)
            for (offer in trade.theirOffers.filter(isResourceFilter))
                newResourceSupplyList.add(resourcesMap[offer.name]!!, "Trade", offer.amount)
        }

        return newResourceSupplyList
    }

    /** Returns the [civilizations][Civilization] that know about both sides ([civInfo] and [otherCiv]) */
    @Readonly fun getCommonKnownCivs(): Set<Civilization> = civInfo.getKnownCivs().asIterable().intersect(otherCiv().getKnownCivs().toSet())

    @Readonly fun getCommonKnownCivsWithSpectators(): Set<Civilization> = civInfo.getKnownCivsWithSpectators().asIterable().intersect(otherCiv().getKnownCivsWithSpectators().toSet())
    /** Returns true when the [civInfo]'s territory is considered allied for [otherCiv].
     *  This includes friendly and allied city-states and the open border treaties.
     */
    @Readonly
    fun isConsideredFriendlyTerritory(): Boolean {
        if (civInfo.isCityState &&
            (isRelationshipLevelGE(RelationshipLevel.Friend) || otherCiv().hasUnique(UniqueType.CityStateTerritoryAlwaysFriendly)))
            return true

        return otherCivDiplomacy().hasOpenBorders // if THEY can enter US then WE are considered friendly territory for THEM
    }
    //endregion

    //region state-changing functions
    // for performance reasons we don't want to call this every time we want to see if a unit can move through a tile
    fun updateHasOpenBorders() {
        // City-states can enter ally's territory (the opposite is true anyway even without open borders)
        val newHasOpenBorders = civInfo.allyCiv == otherCiv()
                || trades.flatMap { it.theirOffers }.any { it.name == Constants.openBorders && it.duration > 0 }

        val bordersWereClosed = hasOpenBorders && !newHasOpenBorders
        hasOpenBorders = newHasOpenBorders

        if (bordersWereClosed) { // borders were closed, get out!
            for (unit in civInfo.units.getCivUnits()
                .filter { it.currentTile.getOwner() == otherCiv() }.toList()) {
                unit.movement.teleportToClosestMoveableTile()
            }
        }
    }

    /** Should only be called from makePeace */
    private fun makePeaceOneSide() {
        diplomaticStatus = DiplomaticStatus.Peace
        val otherCiv = otherCiv()
        // Get out of others' territory
        for (unit in civInfo.units.getCivUnits().filter { it.getTile().getOwner() == otherCiv }.toList())
            unit.movement.teleportToClosestMoveableTile()

        for (thirdCiv in civInfo.getKnownCivs()) {
            // Our ally city states make peace with us
            if (thirdCiv.allyCiv == civInfo && thirdCiv.isAtWarWith(otherCiv)) {
                val thirdCivDiplo = thirdCiv.getDiplomacyManager(otherCiv)!!
                thirdCivDiplo.makePeace()

                // Make the peace treaty so that the civ can't declare war immedietly
                val tradeLogic = TradeLogic(thirdCiv, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = civInfo.gameInfo.speed))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = civInfo.gameInfo.speed))
                thirdCivDiplo.trades.add(tradeLogic.currentTrade)
                thirdCivDiplo.otherCivDiplomacy().trades.add(tradeLogic.currentTrade.reverse())
            }
            // Other City-States that are not our ally don't like the fact that we made peace with their enemy
            if (thirdCiv.allyCiv != civInfo && thirdCiv.isAtWarWith(otherCiv))
                thirdCiv.getDiplomacyManager(civInfo)!!.addInfluence(-10f)
        }
    }


    fun makePeace() {
        makePeaceOneSide()
        otherCivDiplomacy().makePeaceOneSide()

        for (civ in getCommonKnownCivsWithSpectators()) {
            civ.addNotification(
                    "[${civInfo.civName}] and [$otherCivName] have signed a Peace Treaty!",
                    NotificationCategory.Diplomacy, civInfo.civName, NotificationIcon.Diplomacy, otherCivName
            )
        }
    }

    @Readonly fun hasFlag(flag: DiplomacyFlags) = flagsCountdown.containsKey(flag.name)
    fun setFlag(flag: DiplomacyFlags, amount: Int) {
        flagsCountdown[flag.name] = amount
    }

    /** 0 indicates 'flag does not exist' */
    @Readonly fun getFlag(flag: DiplomacyFlags) = flagsCountdown[flag.name] ?: 0
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

    @Readonly
    internal fun getModifier(modifier: DiplomaticModifiers): Float {
        if (!hasModifier(modifier)) return 0f
        return diplomaticModifiers[modifier.name]!!
    }

    internal fun removeModifier(modifier: DiplomaticModifiers) = diplomaticModifiers.remove(modifier.name)
    @Readonly
    fun hasModifier(modifier: DiplomaticModifiers) = diplomaticModifiers.containsKey(modifier.name)
    
    fun replaceModifier(oldModifier: DiplomaticModifiers, newModifier: DiplomaticModifiers, amount: Float) {
        removeModifier(oldModifier)
        addModifier(newModifier, amount)
    }

    fun signDeclarationOfFriendship() {
        setModifier(DiplomaticModifiers.DeclarationOfFriendship, 35f)
        otherCivDiplomacy().setModifier(DiplomaticModifiers.DeclarationOfFriendship, 35f)
        setFlag(DiplomacyFlags.DeclarationOfFriendship, 30)
        otherCivDiplomacy().setFlag(DiplomacyFlags.DeclarationOfFriendship, 30)

        for (thirdCiv in getCommonKnownCivsWithSpectators()) {
            thirdCiv.addNotification("[${civInfo.civName}] and [$otherCivName] have signed the Declaration of Friendship!",
                NotificationCategory.Diplomacy, civInfo.civName, NotificationIcon.Diplomacy, otherCivName)
            thirdCiv.getDiplomacyManager(civInfo)!!.setFriendshipBasedModifier()
            if (thirdCiv.isSpectator()) return
            thirdCiv.getDiplomacyManager(civInfo)!!.setFriendshipBasedModifier()
        }

        // Ignore contitionals as triggerUnique will check again, and that would break
        // UniqueType.ConditionalChance - 25% declared chance would work as 6% actual chance
        for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponDeclaringFriendship, GameContext.IgnoreConditionals))
            UniqueTriggerActivation.triggerUnique(unique, civInfo)
        for (unique in otherCiv().getTriggeredUniques(UniqueType.TriggerUponDeclaringFriendship, GameContext.IgnoreConditionals))
            UniqueTriggerActivation.triggerUnique(unique, otherCiv())
    }

    internal fun setFriendshipBasedModifier() {
        removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies)
        removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurEnemies)
        val civsOtherCivHasDeclaredFriendshipWith = getCommonKnownCivs()
            .filter { it.getDiplomacyManager(otherCiv())!!.hasFlag(DiplomacyFlags.DeclarationOfFriendship) }

        for (thirdCiv in civsOtherCivHasDeclaredFriendshipWith) {
            // What do we (A) think about the otherCiv() (B) being friends with the third Civ (C)?
            val ourRelationshipWithThirdCiv = civInfo.getDiplomacyManager(thirdCiv)!!.relationshipIgnoreAfraid()

            val modifierType = when (ourRelationshipWithThirdCiv) {
                RelationshipLevel.Unforgivable, RelationshipLevel.Enemy -> DiplomaticModifiers.DeclaredFriendshipWithOurEnemies
                else -> DiplomaticModifiers.DeclaredFriendshipWithOurAllies
            }
            val modifierValue = when (ourRelationshipWithThirdCiv) {
                RelationshipLevel.Unforgivable -> -15f
                RelationshipLevel.Enemy -> -5f
                RelationshipLevel.Friend -> 5f
                RelationshipLevel.Ally -> 15f
                else -> 0f
            }
            addModifier(modifierType, modifierValue)
        }
    }

    fun signDefensivePact(duration: Int) {
        //Note: These modifiers are additive to the friendship modifiers
        setModifier(DiplomaticModifiers.DefensivePact, 10f)
        otherCivDiplomacy().setModifier(DiplomaticModifiers.DefensivePact, 10f)
        setFlag(DiplomacyFlags.DefensivePact, duration)
        otherCivDiplomacy().setFlag(DiplomacyFlags.DefensivePact, duration)
        diplomaticStatus = DiplomaticStatus.DefensivePact
        otherCivDiplomacy().diplomaticStatus = DiplomaticStatus.DefensivePact


        for (thirdCiv in getCommonKnownCivsWithSpectators()) {
            thirdCiv.addNotification("[${civInfo.civName}] and [$otherCivName] have signed a Defensive Pact!",
                NotificationCategory.Diplomacy, civInfo.civName, NotificationIcon.Diplomacy, otherCivName)
            if (thirdCiv.isSpectator()) return
            thirdCiv.getDiplomacyManager(civInfo)!!.setDefensivePactBasedModifier()
        }

        // Ignore contitionals as triggerUnique will check again, and that would break
        // UniqueType.ConditionalChance - 25% declared chance would work as 6% actual chance
        for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponSigningDefensivePact, GameContext.IgnoreConditionals))
            UniqueTriggerActivation.triggerUnique(unique, civInfo)
        for (unique in otherCiv().getTriggeredUniques(UniqueType.TriggerUponSigningDefensivePact, GameContext.IgnoreConditionals))
            UniqueTriggerActivation.triggerUnique(unique, otherCiv())
    }

    internal fun setDefensivePactBasedModifier() {
        removeModifier(DiplomaticModifiers.SignedDefensivePactWithOurAllies)
        removeModifier(DiplomaticModifiers.SignedDefensivePactWithOurEnemies)
        for (thirdCiv in getCommonKnownCivs()
            .filter { it.getDiplomacyManager(civInfo)!!.hasFlag(DiplomacyFlags.DefensivePact) }) {
            //Note: These modifiers are additive to the friendship modifiers
            val relationshipLevel = otherCivDiplomacy().relationshipIgnoreAfraid()
            val modifierType = when (relationshipLevel) {
                RelationshipLevel.Unforgivable, RelationshipLevel.Enemy -> DiplomaticModifiers.SignedDefensivePactWithOurEnemies
                else -> DiplomaticModifiers.SignedDefensivePactWithOurAllies
            }
            val modifierValue = when (relationshipLevel) {
                RelationshipLevel.Unforgivable -> -15f
                RelationshipLevel.Enemy -> -10f
                RelationshipLevel.Friend -> 2f
                RelationshipLevel.Ally -> 5f
                else -> 0f
            }
            addModifier(modifierType, modifierValue)
        }
    }

    internal fun setReligionBasedModifier() {
        if (otherCivDiplomacy().believesSameReligion())
            // they share same majority religion
            setModifier(DiplomaticModifiers.BelieveSameReligion, 5f)
        else
            // their majority religions differ or one or both don't have a majority religion at all
            removeModifier(DiplomaticModifiers.BelieveSameReligion)
    }

    fun denounce() {
        activateDenounceEffects()
        if (otherCiv().isAI())
            otherCivDiplomacy().activateDenounceEffects()
    }
    
    private fun activateDenounceEffects() {
        setFlag(DiplomacyFlags.Denunciation, 30)
        otherCivDiplomacy().setModifier(DiplomaticModifiers.Denunciation, -35f)
        
        otherCiv().addNotification(
            "[${civInfo.civName}] has denounced us!",
            NotificationCategory.Diplomacy,
            NotificationIcon.Diplomacy, civInfo.civName
        )
        
        // Denounciation results in removal of embasies for both sides
        civInfo.diplomacyFunctions.removeEmbassies(otherCiv())

        // We, A, are denouncing B. What do other major civs (C,D, etc) think of this?
        getCommonKnownCivsWithSpectators().forEach { thirdCiv ->
            thirdCiv.addNotification(
                "[${civInfo.civName}] has denounced [${otherCiv().civName}]!",
                NotificationCategory.Diplomacy,
                civInfo.civName, NotificationIcon.Diplomacy, otherCivName
            )
            
            if (thirdCiv.isSpectator())
                return@forEach

            // their relationship with us
            val thirdCivRelationship = thirdCiv.getDiplomacyManager(civInfo)!!
            // their relationship with the civ we are denouncing
            val thirdCivRelationshipWithTarget = thirdCiv.getDiplomacyManager(otherCiv())!!.relationshipIgnoreAfraid()
            when (thirdCivRelationshipWithTarget) {
                RelationshipLevel.Unforgivable -> thirdCivRelationship.addModifier(DiplomaticModifiers.DenouncedOurEnemies, 15f)
                RelationshipLevel.Enemy -> thirdCivRelationship.addModifier(DiplomaticModifiers.DenouncedOurEnemies, 5f)
                RelationshipLevel.Friend -> thirdCivRelationship.addModifier(DiplomaticModifiers.DenouncedOurAllies, -5f)
                RelationshipLevel.Ally -> thirdCivRelationship.addModifier(DiplomaticModifiers.DenouncedOurAllies, -15f)
                else -> {}
            }
        }
    }
    
    fun agreeToDemand(demand: Demand){
        otherCivDiplomacy().setFlag(demand.agreedToDemand, 100)
        addModifier(DiplomaticModifiers.UnacceptableDemands, -10f)
        val text = demand.agreedToDemandText.fillPlaceholders(civInfo.civName)
        otherCiv().addNotification(text, NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, civInfo.civName)
    }
    
    fun refuseDemand(demand: Demand) {
        addModifier(DiplomaticModifiers.UnacceptableDemands, -20f)
        otherCivDiplomacy().setFlag(demand.willIgnoreViolation, 100)
        otherCivDiplomacy().addModifier(demand.refusedDiplomaticModifier, -15f)
        val text = demand.refusedDemandText.fillPlaceholders(civInfo.civName)
        otherCiv().addNotification(text, NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, civInfo.civName)
    }

    fun sideWithCityState() {
        otherCivDiplomacy().setModifier(DiplomaticModifiers.SidedWithProtectedMinor, -5f)
        otherCivDiplomacy().setFlag(DiplomacyFlags.RememberSidedWithProtectedMinor, 25)
    }

    fun becomeWary() {
        if (hasFlag(DiplomacyFlags.WaryOf)) return // once is enough
        setFlag(DiplomacyFlags.WaryOf, -1) // Never expires
        otherCiv().addNotification("City-States grow wary of your aggression. " +
                "The resting point for Influence has decreased by [20] for [${civInfo.civName}].",
            NotificationCategory.Diplomacy, civInfo.civName)
    }

    /**
     * Resolves the otherCiv giving gifts to this civ with negative gold values.
     * Prioritises reducing gifts given to the other civ before increasing our gift value.
     * Does not take the gold from either civ's stockpile
     * @param gold the amount of gold without inflation, can be negative
     * @param isPureGift should be true if the gift was one-sided, or false if it wasn't
     */
    fun giftGold(gold: Int, isPureGift: Boolean) {
        val currentGold = if (isPureGift)
            (gold * civInfo.gameInfo.ruleset.modOptions.constants.goldGiftMultiplier).roundToInt()
        else
            (gold * civInfo.gameInfo.ruleset.modOptions.constants.goldGiftTradeMultiplier).roundToInt()

        val otherGold = otherCivDiplomacy().getGoldGifts()
        if (otherGold > currentGold) {
            otherCivDiplomacy().recieveGoldGifts(-currentGold)
        } else {
            otherCivDiplomacy().removeModifier(DiplomaticModifiers.GaveUsGifts)
            recieveGoldGifts(currentGold - otherGold)
        }
    }

    /**
     * Adds a gift from the other civilization of the value of [gold] that will deteriate over time.
     * Does not take into account how much gold we have given to the other civ. Use [giftGold] for that.
     * Does not take the gold from either civ's stockpile.
     * @param gold the amount of gold without inflation, cannot be negative
     */
    fun recieveGoldGifts(gold: Int) {
        val diplomaticValueOfTrade = (gold * TradeEvaluation().getGoldInflation(civInfo)) / (civInfo.gameInfo.speed.goldGiftModifier * 100)
        addModifier(DiplomaticModifiers.GaveUsGifts, diplomaticValueOfTrade.toFloat())
    }

    /**
     * @return the total value of the gold gifts the other civilization has given us
     */
    @Readonly
    fun getGoldGifts(): Int {
        // The inverse of howe we calculate GaveUsGifts in TradeLogic.acceptTrade gives us how much gold it is worth
        val giftAmount = getModifier(DiplomaticModifiers.GaveUsGifts)
        return ((giftAmount * civInfo.gameInfo.speed.goldGiftModifier * 100) / TradeEvaluation().getGoldInflation(civInfo)).toInt()
    }

    //endregion
}
