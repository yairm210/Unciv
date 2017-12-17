package com.unciv.civinfo;

import com.unciv.game.UnCivGame;
import com.unciv.models.stats.INamed;

public class Unit implements INamed, IConstruction{
    public String name;
    public String description;
    public int cost;
    public int hurryCostModifier;
    public int movement;
    public float currentMovement;
    boolean unbuildable; // for special units likee great people

    public Unit(){}  // for json parsing, we need to have a default constructor

    public Unit(String name, int maxMovement) {
        this.name = name;
        this.movement = maxMovement;
        currentMovement = maxMovement;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isConstructable() {
        if(unbuildable) return false;
        return true;
    }

    @Override
    public int getProductionCost() {
        return cost;
    }

    @Override
    public int getGoldCost() {
        return (int)( Math.pow(30 * cost,0.75) * (1 + hurryCostModifier/100) / 10 ) * 10;
    }

    @Override
    public boolean isBuildable(CityConstructions construction) {
        return !unbuildable;
    }

    @Override
    public void postBuildEvent(CityConstructions construction) {
         UnCivGame.Current.civInfo.tileMap.placeUnitNearTile(construction.cityLocation,new Unit(name,movement));
    }
}
