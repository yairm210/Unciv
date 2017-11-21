package com.unciv.models.gamebasics;

import java.util.ArrayList;

public class TechColumn
{
        public int ColumnNumber;
        public GameBasics gameBasics;
        public ArrayList<Technology> Techs = new ArrayList<Technology>();
        public int TechCost;
        public int BuildingCost;
        public int WonderCost;
}
