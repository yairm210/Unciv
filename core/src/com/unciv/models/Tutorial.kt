package com.unciv.models

enum class Tutorial(val value: String) {

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
    LuxuryResource("Luxury_Resource"),
    StrategicResource("Strategic_Resource"),
    EnemyCityNeedsConqueringWithMeleeUnit("_EnemyCityNeedsConqueringWithMeleeUnit"),
    AfterConquering("After_Conquering"),
    BarbarianEncountered("_BarbarianEncountered"),
    OtherCivEncountered("_OtherCivEncountered"),
    ApolloProgram("Apollo_Program"),
    InjuredUnits("Injured_Units"),
    Workers("Workers"),
    SiegeUnits("Siege_Units"),
    Embarking("Embarking"),
    CityRange("City_Range"),
    IdleUnits("Idle_Units"),
    ContactMe("Contact_Me"),
    Pillaging("_Pillaging");

    companion object {
        fun findByName(name: String): Tutorial? = values().find { it.value == name }
    }
}