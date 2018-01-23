package com.unciv.logic.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.linq.Linq;
import com.unciv.models.gamebasics.GameBasics;

public class MapUnit{
    public String name;
    public int maxMovement;
    public float currentMovement;
    public String action; // work, automation, fortifying, I dunno what.

    public void doPreTurnAction(TileInfo tile){
        if(currentMovement==0) return; // We've already done stuff this turn, and can't do any more stuff
        if(action!=null && action.startsWith("moveTo")){
            String[] destination = action.replace("moveTo ","").split(",");
            Vector2 destinationVector = new Vector2(Integer.parseInt(destination[0]), Integer.parseInt(destination[1]));
            TileInfo gotTo = headTowards(tile.position,destinationVector);
            if(gotTo==null) // we couldn't move there because another unit was in the way!
                return;
            if(gotTo.position.equals(destinationVector)) action=null;
            if(currentMovement!=0) doPreTurnAction(gotTo);
            return;
        }

        if ("automation".equals(action)) doAutomatedAction(tile);
    }

    public void doPostTurnAction(TileInfo tile){
        if(name.equals("Worker") && tile.improvementInProgress!=null) workOnImprovement(tile);
    }


    private void workOnImprovement(TileInfo tile){
        tile.turnsToImprovement -= 1;
        if(tile.turnsToImprovement == 0)
        {
            if (tile.improvementInProgress.startsWith("Remove")) tile.terrainFeature = null;
            else if(tile.improvementInProgress.equals("Road")) tile.roadStatus = RoadStatus.Road;
            else if(tile.improvementInProgress.equals("Railroad")) tile.roadStatus = RoadStatus.Railroad;
            else tile.improvement = tile.improvementInProgress;
            tile.improvementInProgress = null;
        }
    }


    private int getPriority(TileInfo tileInfo){
        int priority =0;
        if(tileInfo.workingCity!=null) priority+=2;
        if(tileInfo.hasViewableResource()) priority+=1;
        if(tileInfo.owner!=null) priority+=2;
        else if(tileInfo.getNeighbors().any(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.owner!=null;
            }
        })) priority+=1;
        return priority;
    }

    public TileInfo findTileToWork(TileInfo currentTile){
        TileInfo selectedTile = currentTile;
        int tilePriority = currentTile.improvement==null && currentTile.canBuildImprovement(chooseImprovement(currentTile))
                ? getPriority(currentTile) : 1; // min rank to get selected is 2
        for (int i = 1; i < 5; i++)
            for (TileInfo tile : CivilizationInfo.current().tileMap.getTilesAtDistance(currentTile.position,i))
                if(tile.unit==null && tile.improvement==null && getPriority(tile)>tilePriority
                        && tile.canBuildImprovement(chooseImprovement(tile))){
                    selectedTile = tile;
                    tilePriority = getPriority(tile);
                }

        return selectedTile;
    }

    public void doAutomatedAction(TileInfo tile){
        TileInfo toWork = findTileToWork(tile);
        if(toWork!=tile) {
            tile = headTowards(tile.position, toWork.position);
            doPreTurnAction(tile);
            return;
        }
        if(tile.improvementInProgress == null){
            String improvement =chooseImprovement(tile);
            if(tile.canBuildImprovement(improvement)) // What if we're stuck on this tile but can't build there?
                tile.startWorkingOnImprovement(improvement);
        }
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

    /**
     *
     * @param origin
     * @param destination
     * @return The tile that we reached this turn
     */
    public TileInfo headTowards(Vector2 origin, Vector2 destination){
        TileMap tileMap = CivilizationInfo.current().tileMap;
        Linq<TileInfo> path = tileMap.getShortestPath(origin,destination,currentMovement,maxMovement);

        TileInfo destinationThisTurn = path.get(0);
        if(destinationThisTurn.unit!=null) return null;
        float distanceToTile = tileMap.getDistanceToTilesWithinTurn(origin,currentMovement).get(destinationThisTurn);
        tileMap.get(origin).moveUnitToTile(destinationThisTurn, distanceToTile);
        return destinationThisTurn;
    }
}