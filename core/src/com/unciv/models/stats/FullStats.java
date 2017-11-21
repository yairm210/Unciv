package com.unciv.models.stats;

public class FullStats extends CivStats // also used for hex stats, since it's basically the same
{
    public int Production = 0;
    public int Food = 0;

    public FullStats() {
    }

    public FullStats(FullStats other){
        add(other);
    }

    public void add(FullStats other){
        Gold+=other.Gold;
        Science+=other.Science;
        Happiness+=other.Happiness;
        Culture+=other.Culture;
        Food+=other.Food;
        Production+=other.Production;
    }

    public String display(int value, String name){
        return ", " + (value>0 ? "+" : "") + value + " "+name;
    }

    public String toString() {
        StringBuilder valuableParts = new StringBuilder();
        if (Production != 0) valuableParts.append(display(Production,"Production"));
        if (Food != 0) valuableParts.append(display(Food,"Food"));
        if (Gold != 0) valuableParts.append(display(Gold,"Gold"));
        if (Science != 0) valuableParts.append(display(Science,"Science"));
        if (Happiness != 0) valuableParts.append(display(Happiness,"Happpiness"));
        if (Culture != 0) valuableParts.append(display(Culture,"Culture"));
        if (valuableParts.length() == 0) return "";
        valuableParts.delete(0,1);
        return valuableParts.toString();
    }

}
