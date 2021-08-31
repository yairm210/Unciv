package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.stats.Stat
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.ui.victoryscreen.RankingType
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class CityStateInfo(val civInfo: CivilizationInfo) {

    fun getProtectorCivs() : List<CivilizationInfo> {
        if(civInfo.isMajorCiv()) return emptyList()
        return civInfo.diplomacy.values
            .filter{ !it.otherCiv().isDefeated() && it.diplomaticStatus == DiplomaticStatus.Protector }
            .map{ it.otherCiv() }
    }

    fun addProtectorCiv(otherCiv: CivilizationInfo) {
        if(!civInfo.isCityState() or !otherCiv.isMajorCiv() or otherCiv.isDefeated()) return
        if(!civInfo.knows(otherCiv) or civInfo.isAtWarWith(otherCiv)) return //Exception

        val diplomacy = civInfo.getDiplomacyManager(otherCiv.civName)
        diplomacy.diplomaticStatus = DiplomaticStatus.Protector
    }

    fun removeProtectorCiv(otherCiv: CivilizationInfo) {
        if(!civInfo.isCityState() or !otherCiv.isMajorCiv() or otherCiv.isDefeated()) return
        if(!civInfo.knows(otherCiv) or civInfo.isAtWarWith(otherCiv)) return //Exception

        val diplomacy = civInfo.getDiplomacyManager(otherCiv.civName)
        diplomacy.diplomaticStatus = DiplomaticStatus.Peace
        diplomacy.influence -= 20
    }

    fun updateAllyCivForCityState() {
        var newAllyName: String? = null
        if (!civInfo.isCityState()) return
        val maxInfluence = civInfo.diplomacy
            .filter { !it.value.otherCiv().isCityState() && !it.value.otherCiv().isDefeated() }
            .maxByOrNull { it.value.influence }
        if (maxInfluence != null && maxInfluence.value.influence >= 60) {
            newAllyName = maxInfluence.key
        }

        if (civInfo.getAllyCiv() != newAllyName) {
            val oldAllyName = civInfo.getAllyCiv()
            civInfo.setAllyCiv(newAllyName)

            // If the city-state is captured by a civ, it stops being the ally of the civ it was previously an ally of.
            //  This means that it will NOT HAVE a capital at that time, so if we run getCapital we'll get a crash!
            val capitalLocation = if (civInfo.cities.isNotEmpty()) civInfo.getCapital().location else null

            if (newAllyName != null) {
                val newAllyCiv = civInfo.gameInfo.getCivilization(newAllyName)
                val text = "We have allied with [${civInfo.civName}]."
                if (capitalLocation != null) newAllyCiv.addNotification(text, capitalLocation, civInfo.civName, NotificationIcon.Diplomacy)
                else newAllyCiv.addNotification(text, civInfo.civName, NotificationIcon.Diplomacy)
                newAllyCiv.updateViewableTiles()
                newAllyCiv.updateDetailedCivResources()
                for (unique in newAllyCiv.getMatchingUniques("Can spend Gold to annex or puppet a City-State that has been your ally for [] turns."))
                    newAllyCiv.getDiplomacyManager(civInfo.civName).setFlag(DiplomacyFlags.MarriageCooldown, unique.params[0].toInt())
            }
            if (oldAllyName != null) {
                val oldAllyCiv = civInfo.gameInfo.getCivilization(oldAllyName)
                val text = "We have lost alliance with [${civInfo.civName}]."
                if (capitalLocation != null) oldAllyCiv.addNotification(text, capitalLocation, civInfo.civName, NotificationIcon.Diplomacy)
                else oldAllyCiv.addNotification(text, civInfo.civName, NotificationIcon.Diplomacy)
                oldAllyCiv.updateViewableTiles()
                oldAllyCiv.updateDetailedCivResources()
            }
        }
    }

    fun getDiplomaticMarriageCost(): Int {
        // https://github.com/Gedemon/Civ5-DLL/blob/master/CvGameCoreDLL_Expansion1/CvMinorCivAI.cpp, line 7812
        var cost = (500 * civInfo.gameInfo.gameParameters.gameSpeed.modifier).toInt()
        // Plus disband value of all units
        for (unit in civInfo.getCivUnits()) {
            cost += unit.baseUnit.getDisbandGold(civInfo)
        }
        // Round to lower multiple of 5
        cost /= 5
        cost *= 5

        return cost
    }

    fun canBeMarriedBy(otherCiv: CivilizationInfo): Boolean {
        return (!civInfo.isDefeated()
                && civInfo.isCityState()
                && civInfo.getDiplomacyManager(otherCiv).relationshipLevel() == RelationshipLevel.Ally
                && !otherCiv.getDiplomacyManager(civInfo).hasFlag(DiplomacyFlags.MarriageCooldown)
                && otherCiv.getMatchingUniques("Can spend Gold to annex or puppet a City-State that has been your ally for [] turns.").any()
                && otherCiv.gold >= getDiplomaticMarriageCost())

    }

    fun diplomaticMarriage(otherCiv: CivilizationInfo) {
        if (!canBeMarriedBy(otherCiv))  // Just in case
            return

        otherCiv.addGold(-getDiplomaticMarriageCost())
        otherCiv.addNotification("We have married into the ruling family of [${civInfo.civName}], bringing them under our control.",
            civInfo.getCapital().location, civInfo.civName, NotificationIcon.Diplomacy, otherCiv.civName)
        for (civ in civInfo.gameInfo.civilizations.filter { it != otherCiv })
            civ.addNotification("[${otherCiv.civName}] has married into the ruling family of [${civInfo.civName}], bringing them under their control.",
                civInfo.getCapital().location, civInfo.civName, NotificationIcon.Diplomacy, otherCiv.civName)
        for (unit in civInfo.getCivUnits())
            unit.gift(otherCiv)
        for (city in civInfo.cities) {
            city.moveToCiv(otherCiv)
            city.isPuppet = true // Human players get a popup that allows them to annex instead
            city.foundingCiv = "" // This is no longer a city-state
        }
        civInfo.destroy()
    }

    fun getTributeWillingness(demandingCiv: CivilizationInfo, demandingWorker: Boolean = false): Int {
        return getTributeModifiers(demandingCiv, demandingWorker).values.sum()
    }

    fun getTributeModifiers(demandingCiv: CivilizationInfo, demandingWorker: Boolean = false, requireWholeList: Boolean = false): HashMap<String, Int> {
        val modifiers = LinkedHashMap<String, Int>()    // Linked to preserve order when presenting the modifiers table
        // Can't bully major civs or unsettled CS's
        if (!civInfo.isCityState()) {
            modifiers["Major Civ"] = -999
            return modifiers
        }
        if (civInfo.cities.isEmpty()) {
            modifiers["No Cities"] = -999
            return  modifiers
        }

        modifiers["Base value"] = -110

        if (civInfo.cityStatePersonality == CityStatePersonality.Hostile)
            modifiers["Hostile"] = -10
        if (civInfo.cityStateType == CityStateType.Militaristic)
            modifiers["Militaristic"] = -10
        if (civInfo.getAllyCiv() != null && civInfo.getAllyCiv() != demandingCiv.civName)
            modifiers["Has Ally"] = -10
        if (getProtectorCivs().any { it != demandingCiv })
            modifiers["Has Protector"] = -20
        if (demandingWorker)
            modifiers["Demanding a Worker"] = -30
        if (demandingWorker && civInfo.getCapital().population.population < 4)
            modifiers["Demanding a Worker from small City-State"] = -300
        val recentBullying = civInfo.getRecentBullyingCountdown()
        if (recentBullying != null && recentBullying > 10)
            modifiers["Very recently paid tribute"] = -300
        else if (recentBullying != null)
            modifiers["Recently paid tribute"] = -40
        if (civInfo.getDiplomacyManager(demandingCiv).influence < -30)
            modifiers["Influence below -30"] = -300

        // Slight optimization, we don't do the expensive stuff if we have no chance of getting a positive result
        if (!requireWholeList && modifiers.values.sum() <= -200)
            return modifiers

        val forceRank = civInfo.gameInfo.getAliveMajorCivs().sortedByDescending { it.getStatForRanking(
            RankingType.Force) }.indexOf(demandingCiv)
        modifiers["Military Rank"] = 100 - ((100 / civInfo.gameInfo.gameParameters.players.size) * forceRank)

        if (!requireWholeList && modifiers.values.sum() <= -100)
            return modifiers

        val bullyRange = max(5, civInfo.gameInfo.tileMap.tileMatrix.size / 10)   // Longer range for larger maps
        val inRangeTiles = civInfo.getCapital().getCenterTile().getTilesInDistanceRange(1..bullyRange)
        val forceNearCity = inRangeTiles
            .sumBy { if (it.militaryUnit?.civInfo == demandingCiv)
                it.militaryUnit!!.getForceEvaluation()
            else 0
            }
        val csForce = civInfo.getCapital().getForceEvaluation() + inRangeTiles
            .sumBy { if (it.militaryUnit?.civInfo == civInfo)
                it.militaryUnit!!.getForceEvaluation()
            else 0
            }
        val forceRatio = forceNearCity.toFloat() / csForce.toFloat()

        modifiers["Military near City-State"] = when {
            forceRatio > 3f -> 100
            forceRatio > 2f -> 80
            forceRatio > 1.5f -> 60
            forceRatio > 1f -> 40
            forceRatio > 0.5f -> 20
            else -> 0
        }

        return modifiers
    }

    fun goldGainedByTribute(): Int {
        // These values are close enough, linear increase throughout the game
        var gold = when (civInfo.gameInfo.gameParameters.gameSpeed) {
            GameSpeed.Quick -> 60
            GameSpeed.Standard -> 50
            GameSpeed.Epic -> 35
            GameSpeed.Marathon -> 30
        }
        val turnsToIncrement = when (civInfo.gameInfo.gameParameters.gameSpeed) {
            GameSpeed.Quick -> 5f
            GameSpeed.Standard -> 6.5f
            GameSpeed.Epic -> 14f
            GameSpeed.Marathon -> 32f
        }
        gold += 5 * (civInfo.gameInfo.turns / turnsToIncrement).toInt()

        return gold
    }

    fun tributeGold(demandingCiv: CivilizationInfo) {
        if (!civInfo.isCityState()) throw Exception("You can only demand gold from City-States!")
        val goldAmount = goldGainedByTribute()
        demandingCiv.addGold(goldAmount)
        civInfo.getDiplomacyManager(demandingCiv).influence -= 15
        civInfo.addFlag(CivFlags.RecentlyBullied.name, 20)
        updateAllyCivForCityState()
        civInfo.updateStatsForNextTurn()
    }

    fun tributeWorker(demandingCiv: CivilizationInfo) {
        if (!civInfo.isCityState()) throw Exception("You can only demand workers from City-States!")

        val buildableWorkerLikeUnits = civInfo.gameInfo.ruleSet.units.filter {
            it.value.uniqueObjects.any { it.placeholderText == Constants.canBuildImprovements }
                    && it.value.isBuildable(civInfo)
                    && it.value.isCivilian()
        }
        if (buildableWorkerLikeUnits.isEmpty()) return  // Bad luck?
        demandingCiv.placeUnitNearTile(civInfo.getCapital().location, buildableWorkerLikeUnits.keys.random())

        civInfo.getDiplomacyManager(demandingCiv).influence -= 50
        civInfo.addFlag(CivFlags.RecentlyBullied.name, 20)
        updateAllyCivForCityState()
    }

    fun canGiveStat(statType: Stat): Boolean {
        if (!civInfo.isCityState())
            return false
        val eraInfo = civInfo.getEraObject()
        val bonuses = if (eraInfo == null) null
        else eraInfo.allyBonus[civInfo.cityStateType.name]
        if (bonuses != null) {
            // Defined city states in json
            bonuses.addAll(eraInfo!!.friendBonus[civInfo.cityStateType.name]!!)
            for (bonus in bonuses) {
                if (statType == Stat.Happiness && bonus.getPlaceholderText() == "Provides [] Happiness")
                    return true
                if (bonus.getPlaceholderText() == "Provides [] [] per turn" && bonus.getPlaceholderParameters()[1] == statType.name)
                    return true
                if (bonus.getPlaceholderText() == "Provides [] [] []" && bonus.getPlaceholderParameters()[1] == statType.name)
                    return true
            }

        } else {
            // compatibility mode
            return when {
                civInfo.cityStateType == CityStateType.Mercantile && statType == Stat.Happiness -> true
                civInfo.cityStateType == CityStateType.Cultured && statType == Stat.Culture -> true
                civInfo.cityStateType == CityStateType.Maritime && statType == Stat.Food -> true
                else -> false
            }
        }

        return false
    }

}