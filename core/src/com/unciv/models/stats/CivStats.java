package com.unciv.models.stats;

public class CivStats {
    public int gold = 0;
    public int science = 0;
    public int culture = 0;
    public int happiness = 0;

    public void add(CivStats other) {
        gold += other.gold;
        science += other.science;
        happiness += other.happiness;
        culture += other.culture;
    }
}
