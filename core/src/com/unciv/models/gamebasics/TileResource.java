package com.unciv.models.gamebasics;

import com.unciv.models.stats.NamedStats;
import com.unciv.models.stats.FullStats;

import java.util.Collection;

public class TileResource extends NamedStats implements ICivilopedia {
    public ResourceType resourceType;
    public Collection<String> terrainsCanBeFoundOn;
    public String improvement;
    public FullStats improvementStats;

    /// <summary>
/// The building that improves this resource, if any. E.G.: Granary for wheat, Stable for cattle.
/// </summary>
    public String building;
    public Building GetBuilding(){return building ==null ? null : GameBasics.Buildings.get(building);}

    public String revealedBy;

    @Override
    public String getDescription() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(new FullStats(this)+"\r\n");
        stringBuilder.append("Can be found on " + com.unciv.models.gamebasics.StringUtils.join(", ", terrainsCanBeFoundOn));
        stringBuilder.append("\r\n\r\nImproved by "+ improvement +"\r\n");
        stringBuilder.append("\r\nBonus stats for improvement: "+ improvementStats +"\r\n");
        return stringBuilder.toString();
    }
}

