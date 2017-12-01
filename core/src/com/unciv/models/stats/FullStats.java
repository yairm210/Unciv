package com.unciv.models.stats;

public class FullStats extends CivStats // also used for hex stats, since it's basically the same
{
    public int production = 0;
    public int food = 0;

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

    public String display(int value, String name){
        return ", " + (value>0 ? "+" : "") + value + " "+name;
    }

    public String toString() {
        StringBuilder valuableParts = new StringBuilder();
        if (production != 0) valuableParts.append(display(production,"production"));
        if (food != 0) valuableParts.append(display(food,"food"));
        if (gold != 0) valuableParts.append(display(gold,"gold"));
        if (science != 0) valuableParts.append(display(science,"science"));
        if (happiness != 0) valuableParts.append(display(happiness,"Happpiness"));
        if (culture != 0) valuableParts.append(display(culture,"culture"));
        if (valuableParts.length() == 0) return "";
        valuableParts.delete(0,1);
        return valuableParts.toString();
    }

}
