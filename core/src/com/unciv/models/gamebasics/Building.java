package com.unciv.models.gamebasics;

import com.unciv.models.LinqCollection;
import com.unciv.models.stats.FullStats;
import com.unciv.models.stats.NamedStats;

public class Building extends NamedStats implements ICivilopedia {
    public String description;
    public String requiredTech;

    public Technology GetRequiredTech() {
        return GameBasics.Technologies.get(requiredTech);
    }

    public int cost;
    public int maintainance = 0;
    public FullStats percentStatBonus = new FullStats();
    public int hurryCostModifier; // Extra cost percentage when purchasing
    public boolean isWonder = false;
    public String requiredBuilding;
    public String requiredBuildingInAllCities;
    public String requiredResource; // A strategic resource that will be consumed by this building
    public LinqCollection requiredNearbyImprovedResources; // City can only be built if one of these resources is nearby - it must be improved!
    public String cannotBeBuiltWith;

    // Uniques
    public String providesFreeBuilding;
    public int freeTechs;
    public String unique; // for wonders which have individual functions that are totally unique


    /**
     * The bonus stats that a resource gets when this building is built
     */
    public FullStats resourceBonusStats;

    public String getDescription() {
        return getDescription(false);
    }

    public String getDescription(boolean forBuildingPickerScreen) {
        FullStats stats = new FullStats(this);
        StringBuilder stringBuilder = new StringBuilder();
        if (!forBuildingPickerScreen) stringBuilder.append("Cost: " + cost + "\r\n");
        if (isWonder) stringBuilder.append("Wonder\r\n");
        if (!forBuildingPickerScreen && requiredTech != null)
            stringBuilder.append("Requires " + requiredTech + " to be researched\r\n");
        if (!forBuildingPickerScreen && requiredBuilding != null)
            stringBuilder.append("Requires a " + requiredBuilding + " to be built in this city\r\n");
        if (!forBuildingPickerScreen && requiredBuildingInAllCities != null)
            stringBuilder.append("Requires a " + requiredBuildingInAllCities + " to be built in all cities\r\n");
        if (providesFreeBuilding != null)
            stringBuilder.append("Provides a free " + providesFreeBuilding + " in this city\r\n");
        if (maintainance != 0)
            stringBuilder.append("Maintainance cost: " + maintainance + " gold\r\n");
        stringBuilder.append(description + "\r\n" + stats);
        return stringBuilder.toString();
    }

    public int getGoldCost(){
        return (int)( Math.pow(30 * cost,0.75) * (1 + hurryCostModifier/100) / 10 ) * 10;
    }
}


