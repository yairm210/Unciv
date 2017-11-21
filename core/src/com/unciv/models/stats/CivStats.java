package com.unciv.models.stats;

public class CivStats {
    public int Gold = 0;
    public int Science = 0;
    public int Culture = 0;
    public int Happiness = 0;

    public void add(CivStats other) {
        Gold += other.Gold;
        Science += other.Science;
        Happiness += other.Happiness;
        Culture += other.Culture;
    }
}
