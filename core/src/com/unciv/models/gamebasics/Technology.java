package com.unciv.models.gamebasics;

import java.util.HashSet;

public class Technology
{
        public String name;

        public String description;
        public int cost;
        public HashSet<String> prerequisites = new HashSet<String>();

        public TechColumn column; // The column that this tech is in the tech tree
        public int row;
}