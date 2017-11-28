package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Terrain;
import com.unciv.models.gamebasics.TileImprovement;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

public class TileInfo
{
    public Unit Unit;
    public Vector2 Position;
    public String BaseTerrain;
    public String TerrainFeature;
    public String Resource;
//    public boolean IsWorked = false;
    public String Improvement;
    public String ImprovementInProgress;
    public String Owner; // owning civ name
    public String WorkingCity; // Working City Name
    public int TurnsToImprovement;

    public Terrain GetBaseTerrain(){return GameBasics.Terrains.get(BaseTerrain);}
    public CityInfo GetCity(){
        if(WorkingCity == null) return null;
        return CivilizationInfo.current().Cities.first(new Predicate<CityInfo>() {
        @Override
        public boolean evaluate(CityInfo arg0) {
            return arg0.Name.equals(WorkingCity);
        }
    });}

    public Terrain GetTerrainFeature(){return TerrainFeature==null ? null : GameBasics.Terrains.get(TerrainFeature);}

    public Terrain GetLastTerrain() {
        return TerrainFeature == null ? GetBaseTerrain() : GetTerrainFeature();
    }

    public TileResource GetTileResource(){return Resource==null ? null : GameBasics.TileResources.get(Resource);}

    public boolean IsCityCenter(){return GetCity()!=null && Position.equals(GetCity().cityLocation);}

    public TileImprovement GetTileImprovement(){return Improvement==null ? null : GameBasics.TileImprovements.get(Improvement);}


    private boolean IsResearched(String techName) { return UnCivGame.Current.civInfo.Tech.IsResearched(techName); }

    public FullStats GetTileStats()
    {
        FullStats stats = new FullStats(GetBaseTerrain());

        if(TerrainFeature!=null){
            Terrain terrainFeature = GetTerrainFeature();
            if(terrainFeature.OverrideStats) stats = new FullStats(terrainFeature);
            else stats.add(terrainFeature);
        }

        TileResource resource = GetTileResource();

        CityInfo City = GetCity();
        if (HasViewableResource())
        {
            stats.add(resource);
            if(resource.Building!=null && City!=null && City.cityBuildings.IsBuilt(resource.Building))
            {
                stats.add(resource.GetBuilding().ResourceBonusStats);
            }
        }

        TileImprovement improvement = GetTileImprovement();
        if (improvement != null)
        {
            if (resource != null && resource.Improvement.equals(improvement.Name))
                stats.add(resource.ImprovementStats);
            else stats.add(improvement);

            if (IsResearched(improvement.ImprovingTech)) stats.add(improvement.ImprovingTechStats);
        }

        if (City != null && City.cityLocation.equals(Position)) {
            if (stats.Food < 2) stats.Food = 2;
            if (stats.Production < 1) stats.Production = 1;
        }
        if (stats.Production < 0) stats.Production = 0;
        return stats;
    }

    public boolean CanBuildImprovement(TileImprovement improvement)
    {
        Terrain topTerrain = TerrainFeature==null ? GetBaseTerrain() : GetTerrainFeature();
        if (improvement.TechRequired != null && !IsResearched(improvement.TechRequired)) return false;
        if (improvement.TerrainsCanBeBuiltOn.contains(topTerrain.Name)) return true;
        if (topTerrain.Unbuildable) return false;
        return Resource != null && GetTileResource().Improvement.equals(improvement.Name);
    }

    public void StartWorkingOnImprovement(TileImprovement improvement)
    {
        ImprovementInProgress = improvement.Name;
        TurnsToImprovement = improvement.TurnsToBuild;
    }

    public void StopWorkingOnImprovement()
    {
        ImprovementInProgress = null;
    }

    public void NextTurn()
    {
        if (ImprovementInProgress == null || Unit==null || !Unit.Name.equals("Worker")) return;
        TurnsToImprovement -= 1;
        if(TurnsToImprovement == 0)
        {
            if (ImprovementInProgress.startsWith("Remove")) TerrainFeature = null;
            else Improvement = ImprovementInProgress;

            ImprovementInProgress = null;
        }
    }

    public String toString() {
        StringBuilder SB = new StringBuilder(this.BaseTerrain);
        if (TerrainFeature != null) SB.append(",\r\n" + TerrainFeature);
        if (HasViewableResource()) SB.append(",\r\n" + Resource);
        if (Improvement != null) SB.append(",\r\n" + Improvement);
        if (ImprovementInProgress != null) SB.append(",\r\n" + ImprovementInProgress+" in "+this.TurnsToImprovement+" turns");
        if(Unit!=null) SB.append(",\r\n" + Unit.Name+ "("+Unit.CurrentMovement+"/"+Unit.MaxMovement+")");
        return SB.toString();
    }

    public boolean HasViewableResource() {
        return Resource != null && (GetTileResource().RevealedBy==null || IsResearched(GetTileResource().RevealedBy));
    }

}