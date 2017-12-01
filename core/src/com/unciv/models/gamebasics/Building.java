package com.unciv.models.gamebasics;

import com.unciv.models.stats.FullStats;
import com.unciv.models.stats.NamedStats;

public class Building extends NamedStats implements ICivilopedia {
        public String description;
        public String requiredTech;
        public Technology GetRequiredTech(){return GameBasics.Technologies.get(requiredTech);}
        public int cost;
        public int maintainance = 0;
        public FullStats percentStatBonus = new FullStats();
        //public Func<CityInfo,FullStats> GetFlatBonusStats;
        public boolean isWonder = false;
        public boolean resourceRequired = false;
        public String requiredBuilding;
        public String requiredBuildingInAllCities;

        // Uniques
        public String providesFreeBuilding;
        public int freeTechs;
        public int newTileCostReduction;

        /** The bonus stats that a resource gets when this building is built */
        public FullStats resourceBonusStats;

        public String getDescription() {
                FullStats stats = new FullStats(this);
                StringBuilder stringBuilder = new StringBuilder();
                if(isWonder) stringBuilder.append("Wonder\r\n");
                stringBuilder.append(description + "\r\n" + stats);
                return stringBuilder.toString();
        }
}


