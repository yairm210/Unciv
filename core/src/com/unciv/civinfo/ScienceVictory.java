package com.unciv.civinfo;

import com.unciv.models.LinqCounter;

public class ScienceVictory{
    public LinqCounter<String> requiredParts = new LinqCounter<String>();
    public LinqCounter<String> currentParts = new LinqCounter<String>();

    public LinqCounter<String> unconstructedParts() {
        LinqCounter<String> counter = requiredParts.clone();
        counter.remove(currentParts);
        return counter;
    }
}
