package com.unciv.models.gamebasics;

import java.util.ArrayList;

public class Technology
{
        public String Name;

        public String Description;
        public int Cost;
        public ArrayList<String> Prerequisites = new ArrayList<String>();

        public TechColumn Column; // The column that this tech is in the tech tree
        public int Row;
}