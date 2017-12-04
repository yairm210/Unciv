package com.unciv.models.stats;

public class CivStats {
    public float gold = 0;
    public float science = 0;
    public float culture = 0;
    public float happiness = 0;

    public void add(CivStats other) {
        gold += other.gold;
        science += other.science;
        happiness += other.happiness;
        culture += other.culture;
    }
}
