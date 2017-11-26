package com.unciv.models.gamebasics;

import com.unciv.models.stats.FullStats;
import com.unciv.models.stats.NamedStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class TileImprovement extends NamedStats implements ICivilopedia {
    public Collection<String> TerrainsCanBeBuiltOn = new ArrayList<String>();
    public String TechRequired;

    public String ImprovingTech;
    public FullStats ImprovingTechStats;

    public int TurnsToBuild;

    @Override
    public String GetDescription() {
        StringBuilder stringBuilder = new StringBuilder();
        if(!new FullStats(this).toString().isEmpty()) stringBuilder.append(new FullStats(this)+"\r\n");
        if(!TerrainsCanBeBuiltOn.isEmpty()) stringBuilder.append("Can be built on " + StringUtils.join(", ", TerrainsCanBeBuiltOn));

        HashMap<String,ArrayList<String>> statsToResourceNames = new HashMap<String, ArrayList<String>>();
        for(TileResource tr : GameBasics.TileResources.values()){
            if(!tr.Improvement.equals(Name)) continue;
            String statsString = tr.ImprovementStats.toString();
            if(!statsToResourceNames.containsKey(statsString))
                statsToResourceNames.put(statsString,new ArrayList<String>());
            statsToResourceNames.get(statsString).add(tr.Name);
        }
        for(String statsString : statsToResourceNames.keySet()){
            stringBuilder.append("\r\n"+statsString+" for "+ StringUtils.join(", ",statsToResourceNames.get(statsString)));
        }

        if(TechRequired!=null) stringBuilder.append("\r\nTech required: "+TechRequired);

        return stringBuilder.toString();
    }
}