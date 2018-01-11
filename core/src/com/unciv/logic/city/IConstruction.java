package com.unciv.logic.city;

import com.unciv.models.stats.INamed;

public interface IConstruction extends INamed {
    public int getProductionCost();
    public int getGoldCost();
    public boolean isBuildable(CityConstructions construction);
    public void postBuildEvent(CityConstructions construction); // Yes I'm hilarious.
}
