package com.unciv.civinfo;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Technology;
import com.unciv.models.gamebasics.TileResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class CivilizationTech{
    public int freeTechs = 0;

    public HashSet<String> techsResearched = new HashSet<String>();
    /* When moving towards a certain tech, the user doesn't have to manually pick every one. */
    public ArrayList<String> techsToResearch = new ArrayList<String>();
    public HashMap<String, Integer> techsInProgress = new HashMap<String, Integer>();

    public String currentTechnology(){if(techsToResearch.isEmpty()) return null; return techsToResearch.get(0);}

    public Technology getCurrentTechnology() {
        return GameBasics.Technologies.get(currentTechnology());
    }

    public int researchOfTech(String TechName) {
        int amountResearched = 0;
        if (techsInProgress.containsKey(TechName)) amountResearched = techsInProgress.get(TechName);
        return amountResearched;
    }


    public boolean isResearched(String TechName) {
        return techsResearched.contains(TechName);
    }

    public boolean canBeResearched(String TechName) {
        for (String prerequisiteTech : GameBasics.Technologies.get(TechName).prerequisites)
            if (!isResearched(prerequisiteTech)) return false;
        return true;
    }

    public void nextTurn(int scienceForNewTurn){
        final String CurrentTechnology = currentTechnology();

        techsInProgress.put(CurrentTechnology, researchOfTech(CurrentTechnology) + scienceForNewTurn);
        if (techsInProgress.get(CurrentTechnology) >= getCurrentTechnology().cost) // We finished it!
        {
            techsInProgress.remove(CurrentTechnology);
            techsToResearch.remove(CurrentTechnology);
            techsResearched.add(CurrentTechnology);
            CivilizationInfo.current().addNotification("Research of " + CurrentTechnology + " has completed!", null);

            TileResource revealedResource = GameBasics.TileResources.linqValues().first(new Predicate<TileResource>() {
                @Override
                public boolean evaluate(TileResource arg0) {
                    return CurrentTechnology.equals(arg0.revealedBy);
                }
            });

            if (revealedResource != null)
                for (TileInfo tileInfo : CivilizationInfo.current().tileMap.values())
                    if (revealedResource.name.equals(tileInfo.resource) && tileInfo.owner != null) {
                        for (int i = 0; ; i++) {
                            TileInfo cityTile = CivilizationInfo.current().tileMap.getTilesAtDistance(tileInfo.position, i)
                                    .first(new Predicate<TileInfo>() {
                                        @Override
                                        public boolean evaluate(TileInfo arg0) {
                                            return arg0.isCityCenter();
                                        }
                                    });
                            if (cityTile != null) {
                                CivilizationInfo.current().addNotification(
                                        revealedResource.name + " revealed near " + cityTile.getCity().name, tileInfo.position);
                                break;
                            }
                        }
                    }
            
        }
    }

    public String getAmountResearchedText(){
        if(currentTechnology()==null) return "";
        return "("+researchOfTech(currentTechnology())+"/"+getCurrentTechnology().cost+")";
    }

}
