package com.unciv.civinfo;

import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Technology;

import java.util.ArrayList;
import java.util.HashMap;

public class CivilizationTech{
    public int FreeTechs = 0;

    public ArrayList<String> TechsResearched = new ArrayList<String>();
    /* When moving towards a certain tech, the user doesn't have to manually pick every one. */
    public ArrayList<String> TechsToResearch = new ArrayList<String>();
    public HashMap<String, Integer> TechsInProgress = new HashMap<String, Integer>();

    public String CurrentTechnology(){if(TechsToResearch.isEmpty()) return null; return TechsToResearch.get(0);}

    public Technology GetCurrentTechnology() {
        return GameBasics.Technologies.get(CurrentTechnology());
    }

    public int ResearchOfTech(String TechName) {
        int amountResearched = 0;
        if (TechsInProgress.containsKey(TechName)) amountResearched = TechsInProgress.get(TechName);
        return amountResearched;
    }


    public boolean IsResearched(String TechName) {
        return TechsResearched.contains(TechName);
    }

    public boolean CanBeResearched(String TechName) {
        for (String prerequisiteTech : GameBasics.Technologies.get(TechName).prerequisites)
            if (!IsResearched(prerequisiteTech)) return false;
        return true;
    }

    public void NextTurn(int scienceForNewTurn){

        String CurrentTechnology = CurrentTechnology();

        if (!TechsInProgress.containsKey(CurrentTechnology))
            TechsInProgress.put(CurrentTechnology, 0);
        TechsInProgress.put(CurrentTechnology, TechsInProgress.get(CurrentTechnology) + scienceForNewTurn);
        if (TechsInProgress.get(CurrentTechnology) >= GetCurrentTechnology().cost) // We finished it!
        {
            TechsInProgress.remove(CurrentTechnology);
            TechsResearched.add(CurrentTechnology);
            TechsToResearch.remove(CurrentTechnology);
        }
    }

}
