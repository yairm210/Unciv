package com.unciv.models.gamebasics;

import com.unciv.models.LinqHashMap;
import com.unciv.models.stats.FullStats;
import com.unciv.models.stats.NamedStats;

import java.util.Dictionary;

public class Building extends NamedStats implements ICivilopedia {
        public String Description;
        public String RequiredTech;
        public Technology GetRequiredTech(){return GameBasics.Technologies.get(RequiredTech);}
        public int Cost;
        public int Maintainance = 0;
        public FullStats PercentStatBonus = new FullStats();
        //public Func<CityInfo,FullStats> GetFlatBonusStats;
        public boolean IsWonder = false;
        public boolean ResourceRequired = false;
        public String RequiredBuilding;
        public String RequiredBuildingInAllCities;

        // Uniques
        public String ProvidesFreeBuilding;
        public int FreeTechs;
        public int NewTileCostReduction;

        /** The bonus stats that a resource gets when this building is built */
        public FullStats ResourceBonusStats;

        public String GetDescription() {
                FullStats stats = new FullStats(this);
                StringBuilder stringBuilder = new StringBuilder();
                if(IsWonder) stringBuilder.append("Wonder\r\n");
                stringBuilder.append(Description + "\r\n" + stats);
                return stringBuilder.toString();
        }
}


