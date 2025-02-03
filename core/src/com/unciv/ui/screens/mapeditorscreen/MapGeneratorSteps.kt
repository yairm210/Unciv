package com.unciv.ui.screens.mapeditorscreen

import com.unciv.logic.map.MapParameters

private object MapGeneratorStepsHelpers {
    val applyLandmass = fun(newParameters: MapParameters, actualParameters: MapParameters) {
        actualParameters.type = newParameters.type
        actualParameters.waterThreshold = newParameters.waterThreshold
        actualParameters.seed = newParameters.seed
    }
    val applyElevation = fun(newParameters: MapParameters, actualParameters: MapParameters) {
        actualParameters.elevationExponent = newParameters.elevationExponent
    }
    val applyHumidityAndTemperature = fun(newParameters: MapParameters, actualParameters: MapParameters) {
        actualParameters.temperatureintensity = newParameters.temperatureintensity
        actualParameters.temperatureShift = newParameters.temperatureShift
    }
    val applyLakesAndCoast = fun(newParameters: MapParameters, actualParameters: MapParameters) {
        actualParameters.maxCoastExtension = newParameters.maxCoastExtension
    }
    val applyVegetation = fun(newParameters: MapParameters, actualParameters: MapParameters) {
        actualParameters.vegetationRichness = newParameters.vegetationRichness
    }
    val applyRareFeatures = fun(newParameters: MapParameters, actualParameters: MapParameters) {
        actualParameters.rareFeaturesRichness = newParameters.rareFeaturesRichness
    }
    val applyResources = fun(newParameters: MapParameters, actualParameters: MapParameters) {
        actualParameters.resourceRichness = newParameters.resourceRichness
    }
}

enum class MapGeneratorSteps(
    val label: String,
    val copyParameters: ((MapParameters, MapParameters)->Unit)? = null
) {
    None(""),
    All("All"), // Special case - applying params done elsewhere
    Landmass("Generate landmass", MapGeneratorStepsHelpers.applyLandmass),
    Elevation("Raise mountains and hills", MapGeneratorStepsHelpers.applyElevation),
    HumidityAndTemperature("Humidity and temperature", MapGeneratorStepsHelpers.applyHumidityAndTemperature),
    LakesAndCoast("Lakes and coastline", MapGeneratorStepsHelpers.applyLakesAndCoast),
    Vegetation("Sprout vegetation", MapGeneratorStepsHelpers.applyVegetation),
    RareFeatures("Spawn rare features", MapGeneratorStepsHelpers.applyRareFeatures),
    Ice("Distribute ice"),
    Continents("Assign continent IDs"),
    NaturalWonders("Place Natural Wonders"),
    Rivers("Let the rivers flow"),
    Resources("Spread Resources", MapGeneratorStepsHelpers.applyResources),
    AncientRuins("Create ancient ruins"),
}
