package com.unciv.civinfo;

import com.unciv.models.LinqCounter;

public class ScienceVictory{
    public LinqCounter<String> requiredParts = new LinqCounter<String>(){
        {
            add("SS Booster",3);
            add("SS Cockpit",1);
            add("SS Engine",1);
            add("SS Statis Chamber",1);
        }
    };
    public LinqCounter<String> currentParts = new LinqCounter<String>();

    public LinqCounter<String> unconstructedParts() {
        LinqCounter<String> counter = requiredParts.clone();
        counter.remove(currentParts);
        return counter;
    }
}
