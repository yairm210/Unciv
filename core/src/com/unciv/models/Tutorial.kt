package com.unciv.models

enum class Tutorial(val value: String, val isCivilopedia: Boolean) {

    Introduction("Introduction", true),
    NewGame("New_Game", true),
    SlowStart("_Slow_Start", false),
    CultureAndPolicies("Culture_and_Policies", true),
    Happiness("Happiness", true),
    Unhappiness("Unhappiness", true),
    GoldenAge("Golden_Age", true),
    RoadsAndRailroads("Roads_and_Railroads", true),
    VictoryTypes("Victory_Types", true),
    EnemyCity("Enemy_City", true),
    LuxuryResource("Luxury_Resource", true),
    StrategicResource("Strategic_Resource", true),
    EnemyCityNeedsConqueringWithMeleeUnit("_EnemyCityNeedsConqueringWithMeleeUnit", false),
    AfterConquering("After_Conquering", true),
    BarbarianEncountered("_BarbarianEncountered", false),
    OtherCivEncountered("_OtherCivEncountered", false),
    ApolloProgram("Apollo_Program", true),
    InjuredUnits("Injured_Units", true),
    Workers("Workers", true),
    SiegeUnits("Siege_Units", true),
    Embarking("Embarking", true),
    CityRange("City_Range", true),
    IdleUnits("Idle_Units", true),
    ContactMe("Contact_Me", true),
    Pillaging("_Pillaging", false);

    companion object {
        fun findByName(name: String): Tutorial? = values().find { it.value == name }
    }
}