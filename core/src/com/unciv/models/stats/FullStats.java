package com.unciv.models.stats;

import java.text.DecimalFormat;

public class FullStats extends CivStats // also used for hex stats, since it's basically the same
{
    public float production = 0;
    public float food = 0;

    public FullStats() {
    }

    public FullStats(FullStats other){
        add(other);
    }

    public void add(FullStats other){
        gold +=other.gold;
        science +=other.science;
        happiness +=other.happiness;
        culture +=other.culture;
        food +=other.food;
        production +=other.production;
    }

    public String display(float value, String name){
        return ", " + (value>0 ? "+" : "") + Math.round(value) + " "+name;
    }

    public String toString() {
        StringBuilder valuableParts = new StringBuilder();
        if (production != 0) valuableParts.append(display(production,"Production"));
        if (food != 0) valuableParts.append(display(food,"Food"));
        if (gold != 0) valuableParts.append(display(gold,"Gold"));
        if (science != 0) valuableParts.append(display(science,"Science"));
        if (happiness != 0) valuableParts.append(display(happiness,"Happiness"));
        if (culture != 0) valuableParts.append(display(culture,"Culture"));
        if (valuableParts.length() == 0) return "";
        valuableParts.delete(0,1);
        return valuableParts.toString();
    }

}
