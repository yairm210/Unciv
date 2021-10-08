package com.unciv.ui.mapeditor

enum class MapGeneratorSteps(val label: String) {
    None(""),
    All("All"),
    Landmass("Generate landmass"),
    Elevation("Raise mountains and hills"),
    HumidityAndTemperature("Humidity and temperature"),
    LakesAndCoast("Lakes and coastline"),
    Vegetation("Sprout vegetation"),
    RareFeatures("Spawn rare features"),
    Ice("Distribute ice"),
    Continents("Assign continent IDs"),
    NaturalWonders("Natural Wonders"),
    Rivers("Let the rivers flow"),
    Resources("Spread Resources"),
    AncientRuins("Create ancient ruins"),
}
