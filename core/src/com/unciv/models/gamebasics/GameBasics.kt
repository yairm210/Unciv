package com.unciv.models.gamebasics

object GameBasics {
    @JvmField var Buildings: HashMap<String, Building> = HashMap()
    @JvmField var Terrains: HashMap<String, Terrain> = HashMap()
    @JvmField var TileResources: HashMap<String, TileResource> = HashMap()
    @JvmField var TileImprovements: HashMap<String, TileImprovement> = HashMap()
    @JvmField var Technologies: HashMap<String, Technology> = HashMap()
    @JvmField var Helps: HashMap<String, BasicHelp> = HashMap()
    @JvmField var Units: HashMap<String, Unit> = HashMap()
    @JvmField var PolicyBranches: HashMap<String, PolicyBranch> = HashMap()
}