package com.unciv.civinfo;

public class Unit{
    public String name;
    public int maxMovement;
    public float currentMovement;

    public Unit(){}  // for json parsing, we need to have a default constructor

    public Unit(String name, int maxMovement) {
        this.name = name;
        this.maxMovement = maxMovement;
        currentMovement = maxMovement;
    }
}
