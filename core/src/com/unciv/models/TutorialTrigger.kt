package com.unciv.models

import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.TutorialTrigger.TriggerContext
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType

//todo - Implement triggers for stuff from WorldScreen.getCurrentTutorialTask here

/**
 *  Each instance represents some event that can display a [Tutorial][com.unciv.models.ruleset.Tutorial].
 *
 *  TODO implement as unique conditionals instead?
 *
 *  * open fun [showIf]: The test to perform to trigger that instance, meaningful for [TriggerContext.Update] and [TriggerContext.NextTurn]
 *
 *  @param context The [TriggerContext] where the trigger is implemented
 */
enum class TutorialTrigger(
    val context: TriggerContext
) {
    Introduction(TriggerContext.Update),
    NewGame(TriggerContext.Unused),
    SlowStart(TriggerContext.NextTurn),
    CultureAndPolicies(TriggerContext.Direct),  // PolicyPickerScreen
    Happiness(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.getHappiness() < 5
    },
    Unhappiness(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.getHappiness() < 0
    },
    GoldenAge(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.goldenAges.isGoldenAge()
    },
    RoadsAndRailroads(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.cities.size > 2
    },
    VictoryTypes(TriggerContext.Unused),
    EnemyCity(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.getKnownCivs()
                .filter { viewingCiv.isAtWarWith(it) }
                .flatMap { it.cities.asSequence() }
                .any { viewingCiv.hasExplored(it.getCenterTile()) }
    },
    LuxuryResource(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) =
            resources(viewingCiv).any { it.resource.resourceType == ResourceType.Luxury }
    },
    StrategicResource(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) =
            resources(viewingCiv).any { it.resource.resourceType == ResourceType.Strategic }
    },
    EnemyCityNeedsConqueringWithMeleeUnit(TriggerContext.Update) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.diplomacy.values.asSequence()
                .filter { it.diplomaticStatus == DiplomaticStatus.War }
                .map { it.otherCiv() } // we're now lazily enumerating over CivilizationInfo's we're at war with
                .flatMap { it.cities.asSequence() } // ... all *their* cities
                .filter { it.health == 1 } // ... those ripe for conquering
                .flatMap { it.getCenterTile().getTilesInDistance(2) }
                // ... all tiles around those in range of an average melee unit
                // -> and now we look for a unit that could do the conquering because it's ours
                //    no matter whether civilian, air or ranged, tell user he needs melee
                .any { it.getUnits().any { unit -> unit.civ == viewingCiv } }     },
    AfterConquering(TriggerContext.Update) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.cities.any { it.hasJustBeenConquered }
    },
    BarbarianEncountered(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) =
            viewingCiv.viewableTiles.any { it.getUnits().any { unit -> unit.civ.isBarbarian() } }
    },
    OtherCivEncountered(TriggerContext.Direct),  // WorldScreen.update if TechPolicyDiplomacyButtons.update returns true
    ApolloProgram(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) =
            viewingCiv.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts)
    },
    InjuredUnits(TriggerContext.Update) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.units.getCivUnits().any { it.health < 100 }
    },
    Workers(TriggerContext.Update) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.units.getCivUnits().any {
            it.cache.hasUniqueToBuildImprovements && it.isCivilian() && !it.isGreatPerson()
        }
    },
    SiegeUnits(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) =
            viewingCiv.units.getCivUnits().any { it.baseUnit.isProbablySiegeUnit() }
    },
    Embarking(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.hasUnique(UniqueType.LandUnitEmbarkation)
    },
    IdleUnits(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.gameInfo.turns >= 50 && GUI.getSettings().checkForDueUnits
    },
    ContactMe(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.gameInfo.turns >= 100
    },
    Pillaging(TriggerContext.Unused),
    Experience(TriggerContext.Direct),  // PromotionPickerScreen
    Combat(TriggerContext.Unused),
    ResearchAgreements(TriggerContext.Unused),
    CityStates(TriggerContext.Unused),
    NaturalWonders(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.naturalWonders.size > 0
    },
    CityExpansion(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.cities.any { it.expansion.tilesClaimed() > 0 }
    },
    GreatPeople(TriggerContext.Unused),
    RemovingTerrainFeatures(TriggerContext.Unused),
    Keyboard(TriggerContext.Unused),
    WorldScreen(TriggerContext.Unused),
    Faith(TriggerContext.Unused),
    Religion(TriggerContext.Unused),
    Religion_inside_cities(TriggerContext.Unused),
    Beliefs(TriggerContext.Unused),
    SpreadingReligion(TriggerContext.Unused),
    Inquisitors(TriggerContext.Unused),
    MayanCalendar(TriggerContext.Unused),
    WeLoveTheKingDay(TriggerContext.NextTurn) {
        override fun showIf(viewingCiv: Civilization) = viewingCiv.cities.any { it.demandedResource.isNotEmpty() }
    },
    CityTileBlockade(TriggerContext.Direct), // CityScreen
    CityBlockade(TriggerContext.Direct),  // CityButton.StatusTable

    ;

    enum class TriggerContext {
        /** No code, exists for historical reasons. Entries marked with it can safely be removed. */
        Unused,

        /** These run at the start of WordScreen.update if uiEnabled is on (WorldScreen.displayTutorialsOnUpdate) */
        Update,

        /** These run after WordScreen.update (WorldScreen.showTutorialsOnNextTurn) */
        NextTurn,

        /** Is used explicitly in some triggering code, invalid if the IDE's 'Find Usages' turns up nothing */
        Direct
    }

    open fun showIf(viewingCiv: Civilization) = true

    companion object {
        operator fun get(context: TriggerContext) = values().asSequence()
            .filter { it.context == context }
        private fun resources(viewingCiv: Civilization) =
            viewingCiv.detailedCivResources.asSequence().filter { it.origin == "All" }
    }
}
