package com.unciv.models.gamebasics;

import java.util.ArrayList;

public class TechColumn
{
        public int columnNumber;
        public GameBasics gameBasics;
        public ArrayList<Technology> techs = new ArrayList<Technology>();
        public int techCost;
        public int buildingCost;
        public int wonderCost;
}
