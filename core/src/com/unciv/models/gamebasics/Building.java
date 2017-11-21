package com.unciv.models.gamebasics;

import com.unciv.models.stats.NamedStats;

public class Building extends NamedStats implements ICivilopedia {
        public String Description;
        public String RequiredTech;
        public Technology GetRequiredTech(){return GameBasics.Technologies.get(RequiredTech);}
        public int Cost;
        public int Maintainance = 0;
        public com.unciv.models.stats.FullStats PercentStatBonus = new com.unciv.models.stats.FullStats();
        //public Func<CityInfo,FullStats> GetFlatBonusStats;
        public boolean IsWonder = false;
        public boolean ResourceRequired = false;
        public String RequiredBuilding;
        public String RequiredBuildingInAllCities;

        // Uniques
        public String ProvidesFreeBuilding;
        public int FreeTechs;


        /** The bonus stats that a resource gets when this building is built */
        public com.unciv.models.stats.FullStats ResourceBonusStats;

        public String GetDescription() {
                com.unciv.models.stats.FullStats stats = new com.unciv.models.stats.FullStats(this);
                StringBuilder stringBuilder = new StringBuilder();
                if(IsWonder) stringBuilder.append("Wonder\r\n");
                stringBuilder.append(Description + "\r\n" + stats);
                return stringBuilder.toString();
        }
}


