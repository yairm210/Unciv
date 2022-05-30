package com.unciv.logic.event

import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats

class NotificationManager {
    private val events = EventBus.EventReceiver()

    init {
        events.receive(INotificationEvent::class) {
            it.createNotification()
        }
        /*events.receive(SomeInheritingClassOfINotificationEvent::class) {
            it.specialFunction()
        }*/
    }

    fun cleanup() {
        events.stopReceiving()
    }
}

/** Interface for notification-generating events */
interface INotificationEvent : Event {
    fun createNotification()
}

/////
// below definitions temporarily in this file for conciseness
/////
/** Interface for unit action events which will have a unit and possibly a location associated with them */
interface IUnitActionNotification : INotificationEvent {
    val actingUnit: MapUnit
    val tile: TileInfo
    val targetUnit: MapUnit?
}

class PillageNotification(
    override val actingUnit: MapUnit,
    override val tile: TileInfo,
    val improvement: TileImprovement,
    val lootedStats: Stats?,
    override val targetUnit: MapUnit? = null
) : IUnitActionNotification {

    val victim: CivilizationInfo? = tile.getOwner()

    override fun createNotification() {
        val pillagerString = "We have pillaged a [${improvement.name}]"
        if (victim != null) {
            actingUnit.civInfo.addNotification(pillagerString, tile.position, NotificationIcon.War, victim!!.civName)
            victim!!.addNotification("[${actingUnit.civInfo.civName}] has pillaged our [${improvement.name}]", tile.position, NotificationIcon.War, actingUnit.civInfo.civName)
        } else {
            actingUnit.civInfo.addNotification(pillagerString, tile.position, NotificationIcon.War)
        }
    }
}

interface ICityNotification : INotificationEvent {
    val city: CityInfo
}

abstract class CityConstructionNotification : ICityNotification {
    abstract val construction: IConstruction

    enum class CityAwareness {
        KNOWS_CITY,
        KNOWS_CIV,
        FARAWAY_LAND
    }

    fun canSeeCity(civ: CivilizationInfo): CityAwareness {
        return when {
            civ.exploredTiles.contains(city.location) -> CityAwareness.KNOWS_CITY
            civ.knows(city.civInfo) -> CityAwareness.KNOWS_CIV
            else -> CityAwareness.FARAWAY_LAND
        }
    }
}

class ConstructionBegunNotification(
    override val city: CityInfo,
    override val construction: IConstruction
) :
    CityConstructionNotification() {
    override fun createNotification() {
        val buildingIcon = "BuildingIcons/${construction.name}"
        for (otherCiv in city.civInfo.gameInfo.civilizations) {
            if (otherCiv == city.civInfo) continue
            when (canSeeCity(otherCiv)) {
                CityAwareness.KNOWS_CITY -> otherCiv.addNotification("The city of [${city.name}] has started constructing [${construction.name}]!",
                    city.location, NotificationIcon.Construction, buildingIcon)
                CityAwareness.KNOWS_CIV -> otherCiv.addNotification("[${city.civInfo.civName}] has started constructing [${construction.name}]!",
                    NotificationIcon.Construction, buildingIcon)
                CityAwareness.FARAWAY_LAND -> otherCiv.addNotification("An unknown civilization has started constructing [${construction.name}]!",
                    NotificationIcon.Construction, buildingIcon)
            }
        }
    }
}

class ConstructionCompletedNotification(
    override val city: CityInfo,
    override val construction: IConstruction
) :
    CityConstructionNotification() {
    override fun createNotification() {
        val constructionIcon = if (construction is Building) "BuildingIcons/${construction.name}"
        else construction.name

        if (construction is Building
                && construction.isWonder) {
            city.civInfo.popupAlerts.add(PopupAlert(AlertType.WonderBuilt, construction.name))

            for (civ in city.civInfo.gameInfo.civilizations) {
                when (canSeeCity(civ)) {
                    CityAwareness.KNOWS_CITY -> civ.addNotification("[${construction.name}] has been built in [${city.name}]",
                        city.location, NotificationIcon.Construction, constructionIcon)
                    else -> civ.addNotification("[${construction.name}] has been built in a faraway land", constructionIcon)
                }
            }
        }

        if (construction is Building && construction.hasUnique(
                    UniqueType.TriggersAlertOnCompletion,
                    StateForConditionals(city.civInfo, city)
                )) {
            for (otherCiv in city.civInfo.gameInfo.civilizations) {
                // No need to notify ourself, since we already got the building notification anyway
                if (otherCiv == city.civInfo) continue
                val completingCivDescription =
                        when (canSeeCity(otherCiv)) {
                            CityAwareness.KNOWS_CIV, CityAwareness.KNOWS_CITY -> "[${city.civInfo.civName}]"
                            else -> "An unknown civilization"
                        }
                otherCiv.addNotification("$completingCivDescription has completed [${construction.name}]!",
                    NotificationIcon.Construction, constructionIcon)
            }
        }
    }
}

class ConstructionCancelledNotification(
    override val city: CityInfo,
    override val construction: IConstruction,
    private val workDone: Int
) : CityConstructionNotification() {
    override fun createNotification() {
        city.civInfo.addNotification(
            "Excess production for [${construction.name}] converted to [$workDone] gold",
            city.location,
            NotificationIcon.Gold, "BuildingIcons/${construction.name}")
    }
}
