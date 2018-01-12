package com.unciv.models.gamebasics;

import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.stats.FullStats;
import com.unciv.models.stats.NamedStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class TileImprovement extends NamedStats implements ICivilopedia {
    public Collection<String> terrainsCanBeBuiltOn = new ArrayList<String>();
    public String techRequired;

    public String improvingTech;
    public FullStats improvingTechStats;

    private int turnsToBuild; // This is the base cost.
    public int getTurnsToBuild(){
        float realTurnsToBuild = turnsToBuild;
        if(CivilizationInfo.current().getBuildingUniques().contains("WorkerConstruction"))
            realTurnsToBuild*=0.75;
        if(CivilizationInfo.current().policies.isAdopted("Citizenship"))
            realTurnsToBuild*=0.75;
        return Math.round(realTurnsToBuild);
    }

    @Override
    public String getDescription() {
        StringBuilder stringBuilder = new StringBuilder();
        if(!new FullStats(this).toString().isEmpty()) stringBuilder.append(new FullStats(this)+"\r\n");
        if(!terrainsCanBeBuiltOn.isEmpty()) stringBuilder.append("Can be built on " + StringUtils.join(", ", terrainsCanBeBuiltOn));

        HashMap<String,ArrayList<String>> statsToResourceNames = new HashMap<String, ArrayList<String>>();
        for(TileResource tr : GameBasics.TileResources.values()){
            if(!tr.improvement.equals(name)) continue;
            String statsString = tr.improvementStats.toString();
            if(!statsToResourceNames.containsKey(statsString))
                statsToResourceNames.put(statsString,new ArrayList<String>());
            statsToResourceNames.get(statsString).add(tr.name);
        }
        for(String statsString : statsToResourceNames.keySet()){
            stringBuilder.append("\r\n"+statsString+" for "+ StringUtils.join(", ",statsToResourceNames.get(statsString)));
        }

        if(techRequired !=null) stringBuilder.append("\r\nTech required: "+ techRequired);

        return stringBuilder.toString();
    }
}