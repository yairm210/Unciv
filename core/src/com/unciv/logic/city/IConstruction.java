package com.unciv.logic.city;

import com.unciv.models.linq.Linq;
import com.unciv.models.stats.INamed;

public interface IConstruction extends INamed {
    int getProductionCost(Linq<String> adoptedPolicies);
    int getGoldCost(Linq<String> adoptedPolicies);
    boolean isBuildable(CityConstructions construction);
    void postBuildEvent(CityConstructions construction); // Yes I'm hilarious.
}
