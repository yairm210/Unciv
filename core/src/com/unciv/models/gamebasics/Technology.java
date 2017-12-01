package com.unciv.models.gamebasics;

import java.util.ArrayList;

public class Technology
{
        public String name;

        public String description;
        public int cost;
        public ArrayList<String> prerequisites = new ArrayList<String>();

        public TechColumn column; // The column that this tech is in the tech tree
        public int row;
}