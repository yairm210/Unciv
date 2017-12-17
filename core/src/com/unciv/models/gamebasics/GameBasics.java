package com.unciv.models.gamebasics;

import com.unciv.civinfo.Unit;
import com.unciv.models.LinqHashMap;

import java.util.LinkedHashMap;

public class GameBasics{
    public static LinqHashMap<String,Building> Buildings;
    public static LinqHashMap<String,Terrain> Terrains;
    public static LinqHashMap<String,TileResource> TileResources;
    public static LinqHashMap<String,TileImprovement> TileImprovements;
    public static LinqHashMap<String, Technology> Technologies;
    public static LinqHashMap<String, BasicHelp> Helps;
    public static LinkedHashMap<String,Unit> Units;
}