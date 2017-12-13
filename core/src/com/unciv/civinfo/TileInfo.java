package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Terrain;
import com.unciv.models.gamebasics.TileImprovement;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

import java.text.DecimalFormat;

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


    private boolean isResearched(String techName) { return UnCivGame.Current.civInfo.tech.isResearched(techName); }

    public FullStats getTileStats()
    {
        FullStats stats = new FullStats(getBaseTerrain());

        if(terrainFeature !=null){
            Terrain terrainFeature = getTerrainFeature();
            if(terrainFeature.overrideStats) stats = new FullStats(terrainFeature);
            else stats.add(terrainFeature);
        }

        TileResource resource = getTileResource();

        CityInfo City = getCity();
        if (hasViewableResource())
        {
            stats.add(resource);
            if(resource.building !=null && City!=null && City.cityBuildings.isBuilt(resource.building))
            {
                stats.add(resource.GetBuilding().resourceBonusStats);
            }
        }

        TileImprovement improvement = getTileImprovement();
        if (improvement != null)
        {
            if (resource != null && resource.improvement.equals(improvement.name))
                stats.add(resource.improvementStats);
            else stats.add(improvement);

            if (isResearched(improvement.improvingTech)) stats.add(improvement.improvingTechStats);
        }

        if (City != null && City.cityLocation.equals(position)) {
            if (stats.food < 2) stats.food = 2;
            if (stats.production < 1) stats.production = 1;
        }
        if (stats.production < 0) stats.production = 0;
        return stats;
    }

    public boolean canBuildImprovement(TileImprovement improvement)
    {
        Terrain topTerrain = terrainFeature ==null ? getBaseTerrain() : getTerrainFeature();
        if (improvement.techRequired != null && !isResearched(improvement.techRequired)) return false;
        if (improvement.terrainsCanBeBuiltOn.contains(topTerrain.name)) return true;
        if(improvement.name.equals("Road") && this.roadStatus== RoadStatus.None) return true;
        if(improvement.name.equals("Railroad") && this.roadStatus != RoadStatus.Railroad) return true;
        if (topTerrain.unbuildable) return false;

        if(improvement.name.equals(this.improvement)) return false;


        return resource != null && getTileResource().improvement.equals(improvement.name);
    }

    public void startWorkingOnImprovement(String improvementName,int turnsToBuild)
    {
        improvementInProgress = improvementName;
        turnsToImprovement = turnsToBuild;
    }

    public void stopWorkingOnImprovement()
    {
        improvementInProgress = null;
    }

    public void nextTurn()
    {
        if(unit !=null) unit.currentMovement = unit.maxMovement;

        if (improvementInProgress == null || unit ==null || !unit.name.equals("Worker")) return;
        turnsToImprovement -= 1;
        if(turnsToImprovement == 0)
        {
            if (improvementInProgress.startsWith("Remove")) terrainFeature = null;
            else if(improvementInProgress.equals("Road")) roadStatus = RoadStatus.Road;
            else if(improvementInProgress.equals("Railroad")) roadStatus = RoadStatus.Railroad;
            else improvement = improvementInProgress;

            String notification = improvementInProgress+" has been completed";
            if(workingCity!=null) notification+=" for "+getCity().name;
            else {
                for (int i = 1; i < 3; i++) {
                    LinqCollection<TileInfo> tilesWithCity = CivilizationInfo.current().tileMap.getTilesInDistance(position, i).where(new Predicate<TileInfo>() {
                        @Override
                        public boolean evaluate(TileInfo arg0) {
                            return arg0.isCityCenter();
                        }
                    });
                    if(tilesWithCity.isEmpty()) continue;
                    notification+=" near "+tilesWithCity.get(0).workingCity;
                    break;
                }
            }
            notification+="!";
            CivilizationInfo.current().notifications.add(notification);
            improvementInProgress = null;
        }
    }

    public String toString() {
        StringBuilder SB = new StringBuilder(this.baseTerrain);
        if (terrainFeature != null) SB.append(",\r\n" + terrainFeature);
        if (hasViewableResource()) SB.append(",\r\n" + resource);
        if(roadStatus!= RoadStatus.None) SB.append(",\r\n" + roadStatus);
        if (improvement != null) SB.append(",\r\n" + improvement);
        if (improvementInProgress != null) SB.append(",\r\n" + improvementInProgress +" in "+this.turnsToImprovement +" turns");
        if(unit !=null) SB.append(",\r\n" + unit.name + "("+ new DecimalFormat("0.#").format(unit.currentMovement)+"/"+ unit.maxMovement +")");
        return SB.toString();
    }

    public boolean hasViewableResource() {
        return resource != null && (getTileResource().revealedBy ==null || isResearched(getTileResource().revealedBy));
    }

}