package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
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
        if(currentMovement==0) return; // We've already done stuff this turn, and can't do any more stuff
        if(action!=null && action.startsWith("moveTo")){
            String[] destination = action.replace("moveTo ","").split(",");
            Vector2 destinationVector = new Vector2(Integer.parseInt(destination[0]), Integer.parseInt(destination[1]));
            TileInfo gotTo = headTowards(tile.position,destinationVector);
            if(gotTo==null) // we couldn't move there because another unit was in the way!
                return;
            if(gotTo.position.equals(destinationVector)) action=null;
            if(currentMovement!=0) doAction(gotTo);
            return;
        }
        if(name.equals("Worker") && tile.improvementInProgress!=null) workOnImprovement(tile);
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
            tile.improvementInProgress = null;
        }
    }

    private boolean isTopPriorityTile(TileInfo tile){
        return tile.owner!=null && tile.improvement==null
                && (tile.workingCity!=null || tile.resource!=null || tile.improvementInProgress!=null)
                && tile.canBuildImprovement(GameBasics.TileImprovements.get(chooseImprovement(tile)));
    }

    private boolean isMediumPriorityTile(TileInfo tile){
        return tile.owner!=null && tile.improvement==null
                && tile.canBuildImprovement(GameBasics.TileImprovements.get(chooseImprovement(tile)));
    }

    private boolean isLowPriorityTile(TileInfo tile){ // Resource near a city's edges
        return tile.improvement==null
                && tile.canBuildImprovement(GameBasics.TileImprovements.get(chooseImprovement(tile)))
                && tile.hasViewableResource()
                && CivilizationInfo.current().tileMap.getTilesAtDistance(tile.position,1).any(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.owner!=null;
            }
        });
    }

    public TileInfo findTileToWork(TileInfo currentTile){
        if(isTopPriorityTile(currentTile)) return currentTile;
        for (int i = 1; i < 5; i++)
            for (TileInfo tile : CivilizationInfo.current().tileMap.getTilesAtDistance(currentTile.position,i))
                if(tile.unit==null && isTopPriorityTile(tile))
                    return tile;

        if(isMediumPriorityTile(currentTile)) return currentTile;
        for (int i = 1; i < 5; i++)
            for (TileInfo tile : CivilizationInfo.current().tileMap.getTilesAtDistance(currentTile.position,i))
                if(tile.unit==null && isMediumPriorityTile(tile))
                    return tile;

        if(isLowPriorityTile(currentTile)) return currentTile;
        for (int i = 1; i < 5; i++)
            for (TileInfo tile : CivilizationInfo.current().tileMap.getTilesAtDistance(currentTile.position,i))
                if(tile.unit==null && isLowPriorityTile(tile))
                    return tile;
        return null;
    }

    public void doAutomatedAction(TileInfo tile){
        TileInfo toWork = findTileToWork(tile);
        if(toWork==null) return; // Don't know what to do. Sorry.
        if(toWork!=tile) tile = headTowards(tile.position,toWork.position);
        if(toWork == tile && tile.improvementInProgress==null) tile.startWorkingOnImprovement(chooseImprovement(tile));
        doAction(tile);
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
        LinqCollection<TileInfo> path = tileMap.getShortestPath(origin,destination,currentMovement,maxMovement);

        TileInfo destinationThisTurn = path.get(0);
        if(destinationThisTurn.unit!=null) return null;
        float distanceToTile = tileMap.getDistanceToTilesWithinTurn(origin,currentMovement).get(destinationThisTurn);
        tileMap.get(origin).moveUnitToTile(destinationThisTurn, distanceToTile);
        return destinationThisTurn;
    }
}