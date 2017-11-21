package com.unciv.models.gamebasics;

import com.unciv.models.stats.NamedStats;
import com.unciv.models.stats.FullStats;

import java.util.Collection;

public class TileResource extends NamedStats implements ICivilopedia {
    public String ResourceType;
    public Collection<String> TerrainsCanBeFoundOn;
    public String Improvement;
    public FullStats ImprovementStats;

    /// <summary>
/// The building that improves this resource, if any. E.G.: Granary for wheat, Stable for cattle.
/// </summary>
    public String Building;
    public com.unciv.models.gamebasics.Building GetBuilding(){return Building==null ? null : GameBasics.Buildings.get(Building);}

    public String RevealedBy;

    @Override
    public String GetDescription() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(new FullStats(this)+"\r\n");
        stringBuilder.append("Can be found on " + com.unciv.models.gamebasics.StringUtils.join(", ",TerrainsCanBeFoundOn));
        stringBuilder.append("\r\n\r\nImproved by "+Improvement+"\r\n");
        stringBuilder.append("\r\nBonus stats for improvement: "+ImprovementStats+"\r\n");
        return stringBuilder.toString();
    }
}

