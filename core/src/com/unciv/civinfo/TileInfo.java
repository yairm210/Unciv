package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Terrain;
import com.unciv.models.gamebasics.TileImprovement;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

enum RoadStatus{
    None,
    Road,
    Railroad
}

public class TileInfo
{
    public Unit unit;
    public Vector2 position;
    public String baseTerrain;
    public String terrainFeature;
    public String resource;
    public String improvement;
    public String improvementInProgress;
    public String owner; // owning civ name
    public String workingCity; // Working City name
    public RoadStatus roadStatus = RoadStatus.None;
    public int turnsToImprovement;

    public Terrain getBaseTerrain(){return GameBasics.Terrains.get(baseTerrain);}
    public CityInfo getCity(){
        if(workingCity == null) return null;
        return CivilizationInfo.current().cities.first(new Predicate<CityInfo>() {
        @Override
        public boolean evaluate(CityInfo arg0) {
            return arg0.name.equals(workingCity);
        }
    });}

    public Terrain getTerrainFeature(){return terrainFeature ==null ? null : GameBasics.Terrains.get(terrainFeature);}

    public Terrain getLastTerrain() {
        return terrainFeature == null ? getBaseTerrain() : getTerrainFeature();
    }

    public TileResource getTileResource(){return resource ==null ? null : GameBasics.TileResources.get(resource);}

    public boolean isCityCenter(){return getCity()!=null && position.equals(getCity().cityLocation);}

    public TileImprovement getTileImprovement(){return improvement ==null ? null : GameBasics.TileImprovements.get(improvement);}


    private boolean isResearched(String techName) { return UnCivGame.Current.civInfo.tech.IsResearched(techName); }

    public FullStats getTileStats()
    {
        FullStats stats = new FullStats(getBaseTerrain());

        if(terrainFeature !=null){
            Terrain terrainFeature = getTerrainFeature();
            if(terrainFeature.OverrideStats) stats = new FullStats(terrainFeature);
            else stats.add(terrainFeature);
        }

        TileResource resource = getTileResource();

        CityInfo City = getCity();
        if (hasViewableResource())
        {
            stats.add(resource);
            if(resource.Building!=null && City!=null && City.cityBuildings.IsBuilt(resource.Building))
            {
                stats.add(resource.GetBuilding().ResourceBonusStats);
            }
        }

        TileImprovement improvement = getTileImprovement();
        if (improvement != null)
        {
            if (resource != null && resource.Improvement.equals(improvement.Name))
                stats.add(resource.ImprovementStats);
            else stats.add(improvement);

            if (isResearched(improvement.ImprovingTech)) stats.add(improvement.ImprovingTechStats);
        }

        if (City != null && City.cityLocation.equals(position)) {
            if (stats.Food < 2) stats.Food = 2;
            if (stats.Production < 1) stats.Production = 1;
        }
        if (stats.Production < 0) stats.Production = 0;
        return stats;
    }

    public boolean canBuildImprovement(TileImprovement improvement)
    {
        Terrain topTerrain = terrainFeature ==null ? getBaseTerrain() : getTerrainFeature();
        if (improvement.TechRequired != null && !isResearched(improvement.TechRequired)) return false;
        if (improvement.TerrainsCanBeBuiltOn.contains(topTerrain.Name)) return true;
        if (topTerrain.Unbuildable) return false;
        return resource != null && getTileResource().Improvement.equals(improvement.Name);
    }

    public void startWorkingOnImprovement(TileImprovement improvement)
    {
        improvementInProgress = improvement.Name;
        turnsToImprovement = improvement.TurnsToBuild;
    }

    public void stopWorkingOnImprovement()
    {
        improvementInProgress = null;
    }

    public void nextTurn()
    {
        if(unit !=null) unit.CurrentMovement = unit.MaxMovement;

        if (improvementInProgress == null || unit ==null || !unit.Name.equals("Worker")) return;
        turnsToImprovement -= 1;
        if(turnsToImprovement == 0)
        {
            if (improvementInProgress.startsWith("Remove")) terrainFeature = null;
            else if(improvement.equals("Road")) roadStatus = RoadStatus.Road;
            else if(improvement.equals("Railroad")) roadStatus = RoadStatus.Railroad;
            else improvement = improvementInProgress;

            improvementInProgress = null;
        }
    }

    public String toString() {
        StringBuilder SB = new StringBuilder(this.baseTerrain);
        if (terrainFeature != null) SB.append(",\r\n" + terrainFeature);
        if (hasViewableResource()) SB.append(",\r\n" + resource);
        if (improvement != null) SB.append(",\r\n" + improvement);
        if (improvementInProgress != null) SB.append(",\r\n" + improvementInProgress +" in "+this.turnsToImprovement +" turns");
        if(unit !=null) SB.append(",\r\n" + unit.Name+ "("+ unit.CurrentMovement+"/"+ unit.MaxMovement+")");
        return SB.toString();
    }

    public boolean hasViewableResource() {
        return resource != null && (getTileResource().RevealedBy==null || isResearched(getTileResource().RevealedBy));
    }

}