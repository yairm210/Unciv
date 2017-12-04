package com.unciv.civinfo;

public class Unit{
    public String Name;
    public int MaxMovement;
    public float CurrentMovement;

    public Unit(){}  // for json parsing, we need to have a default constructor

    public Unit(String name, int maxMovement) {
        Name = name;
        MaxMovement = maxMovement;
        CurrentMovement = maxMovement;
    }
}
