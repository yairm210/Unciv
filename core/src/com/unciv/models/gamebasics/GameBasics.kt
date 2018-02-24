package com.unciv.models.gamebasics

import com.unciv.models.linq.LinqHashMap

import java.util.LinkedHashMap

object GameBasics {
    @JvmField var Buildings: LinqHashMap<String, Building> = LinqHashMap()
    @JvmField var Terrains: LinqHashMap<String, Terrain> = LinqHashMap()
    @JvmField var TileResources: LinqHashMap<String, TileResource> = LinqHashMap()
    @JvmField var TileImprovements: LinqHashMap<String, TileImprovement> = LinqHashMap()
    @JvmField var Technologies: LinqHashMap<String, Technology> = LinqHashMap()
    @JvmField var Helps: LinqHashMap<String, BasicHelp> = LinqHashMap()
    @JvmField var Units: LinqHashMap<String, Unit> = LinqHashMap()
    @JvmField var PolicyBranches: LinqHashMap<String, PolicyBranch> = LinqHashMap()
}