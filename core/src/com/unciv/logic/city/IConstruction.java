package com.unciv.logic.city;

import com.unciv.models.stats.INamed;

public interface IConstruction extends INamed {
    int getProductionCost();
    int getGoldCost();
    boolean isBuildable(CityConstructions construction);
    void postBuildEvent(CityConstructions construction); // Yes I'm hilarious.
}
