package com.unciv.models.gamebasics;

import com.unciv.logic.city.IConstruction;
import com.unciv.logic.city.CityConstructions;
import com.unciv.logic.map.MapUnit;
import com.unciv.models.linq.Linq;
import com.unciv.models.stats.INamed;

public class Unit implements INamed, IConstruction {
    public String name;
    public String description;
    public int cost;
    public int hurryCostModifier;
    public int movement;
    boolean unbuildable; // for special units likee great people

    public Unit(){}  // for json parsing, we need to have a default constructor

    @Override
    public String getName() {
        return name;
    }

    public boolean isConstructable() {
        return !unbuildable;
    }

    @Override
    public int getProductionCost(Linq<String> policies) {
        return cost;
    }

    @Override
    public int getGoldCost(Linq<String> policies) {
        return (int)( Math.pow(30 * cost,0.75) * (1 + hurryCostModifier/100) / 10 ) * 10;
    }

    @Override
    public boolean isBuildable(CityConstructions construction) {
        return !unbuildable;
    }

    @Override
    public void postBuildEvent(CityConstructions construction) {
         construction.cityInfo.civInfo.placeUnitNearTile(construction.cityInfo.cityLocation,name);
    }

    public MapUnit getMapUnit(){
        MapUnit unit = new MapUnit();
        unit.name=name;
        unit.maxMovement=movement;
        unit.currentMovement=movement;
        return unit;
    }
}
