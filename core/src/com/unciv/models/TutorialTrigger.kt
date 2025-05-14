package com.unciv.models


/**
 *  Each instance represents some event that can display a [Tutorial][com.unciv.models.ruleset.Tutorial].
 *
 *  TODO implement as unique conditionals instead?
 */
enum class TutorialTrigger(val value: String, val isCivilopedia: Boolean = !value.startsWith("_")) {

    Introduction("Introduction"),
    NewGame("New_Game"),
    SlowStart("_Slow_Start"),
    CultureAndPolicies("Culture_and_Policies"),
    Happiness("Happiness"),
    Unhappiness("Unhappiness"),
    GoldenAge("Golden_Age"),
    RoadsAndRailroads("Roads_and_Railroads"),
    VictoryTypes("Victory_Types"),
    EnemyCity("Enemy_City"),
    Resources("Resources"),
    EnemyCityNeedsConqueringWithMeleeUnit("_EnemyCityNeedsConqueringWithMeleeUnit"),
    AfterConquering("After_Conquering"),
    BarbarianEncountered("_BarbarianEncountered"),
    OtherCivEncountered("_OtherCivEncountered"),
    ApolloProgram("Apollo_Program"),
    InjuredUnits("Injured_Units"),
    Workers("Workers"),
    SiegeUnits("Siege_Units"),
    Embarking("Embarking"),
    IdleUnits("Idle_Units"),
    ContactMe("Contact_Me"),
    Pillaging("Pillaging"),
    Experience("Experience"),
    Combat("Combat"),
    ResearchAgreements("Research_Agreements"),
    CityStates("City-States"),
    NaturalWonders("Natural_Wonders"),
    CityExpansion("City_Expansion"),
    GreatPeople("Great_People"),
    RemovingTerrainFeatures("Removing_Terrain_Features"),
    Keyboard("Keyboard"),
    WorldScreen("World_Screen"),
    Faith("Faith"),
    Religion("Religion"),
    Religion_inside_cities("Religion_inside_cities"),
    Beliefs("Beliefs"),
    SpreadingReligion("Spreading_Religion"),
    Inquisitors("Inquisitors"),
    MayanCalendar("Maya_Long_Count_calendar_cycle"),
    WeLoveTheKingDay("We_Love_The_King_Day"),
    CityTileBlockade("City_Tile_Blockade"),
    CityBlockade("City_Blockade")
}
