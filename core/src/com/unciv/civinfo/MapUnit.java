package com.unciv.civinfo;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.models.LinqCollection;
import com.unciv.models.LinqHashMap;
import com.unciv.models.gamebasics.GameBasics;

public class MapUnit{
    public String name;
    public int maxMovement;
    public float currentMovement;
    public String action; // work, automation, fortifying, I dunno what.

    public void doAction(TileInfo tile){
        if(tile.improvementInProgress!=null) workOnImprovement(tile);
        if ("automation".equals(action)) doAutomatedAction(tile);
    }

    private void workOnImprovement(TileInfo tile){
        tile.turnsToImprovement -= 1;
        if(tile.turnsToImprovement == 0)
        {
            if (tile.improvementInProgress.startsWith("Remove")) tile.terrainFeature = null;
            else if(tile.improvementInProgress.equals("Road")) tile.roadStatus = RoadStatus.Road;
            else if(tile.improvementInProgress.equals("Railroad")) tile.roadStatus = RoadStatus.Railroad;
            else tile.improvement = tile.improvementInProgress;

            String notification = tile.improvementInProgress+" has been completed";
            if(tile.workingCity!=null) notification+=" for "+tile.getCity().name;
            else {
                for (int i = 1; i < 3; i++) {
                    LinqCollection<TileInfo> tilesWithCity = CivilizationInfo.current().tileMap.getTilesInDistance(tile.position, i).where(new Predicate<TileInfo>() {
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
            CivilizationInfo.current().notifications.add(notification+"!");
            tile.improvementInProgress = null;
        }
    }

    private void doAutomatedAction(TileInfo tile){
        if(tile.owner!=null && tile.improvement==null // We'll be working this tile
                && (tile.workingCity!=null || tile.resource!=null || tile.improvementInProgress!=null)
                && tile.canBuildImprovement(GameBasics.TileImprovements.get(chooseImprovement(tile))))
        {
            if(tile.improvementInProgress==null) tile.startWorkingOnImprovement(chooseImprovement(tile)); // and stay put.
            return;
        }

        // We'll search for a tile that needs our help in the reachable area
        LinqHashMap<TileInfo, Float> distanceToTiles =
                CivilizationInfo.current().tileMap.getUnitDistanceToTiles(tile.position,currentMovement);
        TileInfo tileWithinDistance = new LinqCollection<TileInfo>(distanceToTiles.keySet()).first(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo tile) {
                return tile.owner!=null && tile.improvement==null && tile.unit==null
                        && (tile.workingCity!=null || tile.resource!=null || tile.improvementInProgress!=null)
                        && tile.canBuildImprovement(GameBasics.TileImprovements.get(chooseImprovement(tile)));
            }
        });
        if(tileWithinDistance!=null){
            tile.moveUnitToTile(tileWithinDistance,distanceToTiles.get(tileWithinDistance)); // go there
            doAction(tileWithinDistance); // And do the same from there
        }
        // If not, then we don't know what to do. Oh well.
    }


    private String chooseImprovement(final TileInfo tile){
        if(tile.improvementInProgress!=null) return tile.improvementInProgress;
        if("Forest".equals(tile.terrainFeature)) return "Lumber mill";
        if("Jungle".equals(tile.terrainFeature)) return "Trading post";
        if("Marsh".equals(tile.terrainFeature)) return "Remove Marsh";

        if(tile.resource!=null) return tile.getTileResource().improvement;
        if(tile.baseTerrain.equals("Hill")) return "Mine";
        if(tile.baseTerrain.equals("Grassland") || tile.baseTerrain.equals("Desert") || tile.baseTerrain.equals("Plains"))
            return "Farm";
        if(tile.baseTerrain.equals("Tundra")) return "Trading post";
        return null;
    }
}