package com.unciv.logic.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.ui.GameInfo;
import com.unciv.ui.utils.HexMath;

public class TileMap{

    public transient GameInfo gameInfo;


    private Linq<MapUnit> units;
    private LinqHashMap<String, TileInfo> tiles = new LinqHashMap<String, TileInfo>();

    public TileMap(){} // for json parsing, we need to have a default constructor
    public Linq<TileInfo> values(){return tiles.linqValues();}


    public TileMap(int distance) {
        tiles = new RandomMapGenerator().generateMap(distance);
        setTransients();
    }

    public boolean contains(Vector2 vector){ return tiles.containsKey(vector.toString());}

    public TileInfo get(Vector2 vector){return tiles.get(vector.toString());}

    public Linq<TileInfo> getTilesInDistance(Vector2 origin, int distance){
        Linq<TileInfo> tiles = new Linq<TileInfo>();

        for(Vector2 vector : HexMath.GetVectorsInDistance(origin, distance))
            if(contains(vector))
                tiles.add(get(vector));

        return tiles;
    }

    public Linq<TileInfo> getTilesAtDistance(Vector2 origin, int distance){
        Linq<TileInfo> tiles = new Linq<TileInfo>();

        for(Vector2 vector : HexMath.GetVectorsAtDistance(origin, distance))
            if(contains(vector))
                tiles.add(get(vector));

        return tiles;
    }

    public LinqHashMap<TileInfo,Float> getDistanceToTilesWithinTurn(Vector2 origin, float currentUnitMovement, boolean machineryIsResearched){
        LinqHashMap<TileInfo,Float> distanceToTiles = new LinqHashMap<TileInfo, Float>();
        distanceToTiles.put(get(origin), 0f);
        Linq<TileInfo> tilesToCheck = new Linq<TileInfo>();
        tilesToCheck.add(get(origin));
        while(!tilesToCheck.isEmpty()){
            Linq<TileInfo> updatedTiles = new Linq<TileInfo>();
            for(TileInfo tileToCheck : tilesToCheck)
                for (TileInfo maybeUpdatedTile : getTilesInDistance(tileToCheck.position,1)) {
                    float distanceBetweenTiles = maybeUpdatedTile.getLastTerrain().movementCost;
                    if(tileToCheck.roadStatus!=RoadStatus.None && maybeUpdatedTile.roadStatus!=RoadStatus.None) {
                        if(machineryIsResearched) distanceBetweenTiles = 1 / 3f;
                        else distanceBetweenTiles = 1/2f;
                    }
                    if(tileToCheck.roadStatus==RoadStatus.Railroad && maybeUpdatedTile.roadStatus==RoadStatus.Railroad) distanceBetweenTiles = 1/10f;
                    float totalDistanceToTile = distanceToTiles.get(tileToCheck)+ distanceBetweenTiles;
                    if (!distanceToTiles.containsKey(maybeUpdatedTile) || distanceToTiles.get(maybeUpdatedTile) > totalDistanceToTile) {

                        if(totalDistanceToTile<currentUnitMovement) updatedTiles.add(maybeUpdatedTile);
                        else totalDistanceToTile = currentUnitMovement;
                        distanceToTiles.put(maybeUpdatedTile,totalDistanceToTile);
                    }

                }

            tilesToCheck = updatedTiles;
        }
        return distanceToTiles;
    }

    public Linq<TileInfo> getShortestPath(Vector2 origin, Vector2 destination, float currentMovement, int maxMovement, boolean isMachieneryResearched){
        Linq<TileInfo> toCheck = new Linq<TileInfo>(get(origin));
        LinqHashMap<TileInfo,TileInfo> parents = new LinqHashMap<TileInfo, TileInfo>();
        parents.put(get(origin),null);

        for (int distance = 1; ; distance++) {
            Linq<TileInfo> newToCheck = new Linq<TileInfo>();
            for (TileInfo ti : toCheck){
                for (TileInfo otherTile : getDistanceToTilesWithinTurn(ti.position, distance == 1 ? currentMovement : maxMovement, isMachieneryResearched).keySet()){
                    if(parents.containsKey(otherTile)) continue; // We cannot be faster than anything existing...
                    if(!otherTile.position.equals(destination) && otherTile.unit!=null) continue; // go to
                    parents.put(otherTile,ti);
                    if(otherTile.position.equals(destination)){
                        Linq<TileInfo> path = new Linq<TileInfo>();
                        TileInfo current = otherTile;
                        while(parents.get(current)!=null){
                            path.add(current);
                            current = parents.get(current);
                        }
                        return path.reverse();
                    }
                    newToCheck.add(otherTile);
                }
            }
            toCheck = newToCheck;
        }
    }

    public void placeUnitNearTile(Vector2 position, final String unitName, final CivilizationInfo civInfo){
        MapUnit unit = GameBasics.Units.get(unitName).getMapUnit();
        unit.owner = civInfo.civName;
        unit.civInfo = civInfo;
        getTilesInDistance(position,2).first(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.unit==null;
            }
        }).unit = unit; // And if there's none, then kill me.
    }

    public Linq<TileInfo> getViewableTiles(Vector2 position, int sightDistance){
        final Linq<TileInfo> tiles = getTilesInDistance(position,1);
        if(get(position).baseTerrain.equals("Hill")) sightDistance+=1;
        for (int i = 0; i <= sightDistance; i++) {
            Linq<TileInfo> tilesForLayer = new Linq<TileInfo>();
            for (final TileInfo tile : getTilesAtDistance(position, i))
                if (tile.getNeighbors().any(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo neighbor) {
                        if (!tiles.contains(neighbor))
                            return false; // Basically, if there's a viewable neighbor which is either flatlands, or I'm taller than him
                        int tileHeight = neighbor.getHeight();
                        return tileHeight == 0 || tile.getHeight() > tileHeight;
                    }
                })) tilesForLayer.add(tile);
            tiles.addAll(tilesForLayer);
        }

        return tiles;
    }

    public void setTransients(){
        for(TileInfo tileInfo: values()) tileInfo.tileMap=this;
    }

}

