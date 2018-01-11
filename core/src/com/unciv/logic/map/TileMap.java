package com.unciv.logic.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.ui.utils.HexMath;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.Terrain;
import com.unciv.models.gamebasics.TerrainType;
import com.unciv.models.gamebasics.TileResource;

public class TileMap{

    private  LinqHashMap<String, TileInfo> tiles = new LinqHashMap<String, TileInfo>();

    public TileMap(){} // for json parsing, we need to have a default constructor

    public TileMap(int distance) {
        for(Vector2 vector : HexMath.GetVectorsInDistance(Vector2.Zero,distance)) addRandomTile(vector);
    }


    private void addRandomTile(Vector2 position) {
        final TileInfo tileInfo = new TileInfo();
        tileInfo.position = position;
        Linq<Terrain> Terrains = GameBasics.Terrains.linqValues();

        final Terrain baseTerrain = Terrains.where(new Predicate<Terrain>() {
            @Override
            public boolean evaluate(Terrain arg0) {
                return arg0.type == TerrainType.BaseTerrain && !arg0.name.equals("Lakes");
            }
        }).getRandom();
        tileInfo.baseTerrain = baseTerrain.name;

        if (baseTerrain.canHaveOverlay) {
            if (Math.random() > 0.7f) {
                Terrain SecondaryTerrain = Terrains.where(new Predicate<Terrain>() {
                    @Override
                    public boolean evaluate(Terrain arg0) {
                        return arg0.type == TerrainType.TerrainFeature && arg0.occursOn.contains(baseTerrain.name);
                    }
                }).getRandom();
                if (SecondaryTerrain != null) tileInfo.terrainFeature = SecondaryTerrain.name;
            }
        }

        Linq<TileResource> TileResources = GameBasics.TileResources.linqValues();

        // Resources are placed according to TerrainFeature, if exists, otherwise according to BaseLayer.
        TileResources = TileResources.where(new Predicate<TileResource>() {
            @Override
            public boolean evaluate(TileResource arg0) {
                return arg0.terrainsCanBeFoundOn.contains(tileInfo.getLastTerrain().name);
            }
        });

        TileResource resource = null;
        if (Math.random() < 1 / 5f) {
            resource = GetRandomResource(TileResources, ResourceType.Bonus);
        } else if (Math.random() < 1 / 7f) {
            resource = GetRandomResource(TileResources, ResourceType.Strategic);
        } else if (Math.random() < 1 / 15f) {
            resource = GetRandomResource(TileResources, ResourceType.Luxury);
        }
        if (resource != null) tileInfo.resource = resource.name;

        tiles.put(position.toString(),tileInfo);
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

    public LinqHashMap<TileInfo,Float> getDistanceToTilesWithinTurn(Vector2 origin, float currentUnitMovement){
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
                        if(CivilizationInfo.current().tech.isResearched("Machinery")) distanceBetweenTiles = 1 / 3f;
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

    public Linq<TileInfo> getShortestPath(Vector2 origin, Vector2 destination, float currentMovement, int maxMovement){
        Linq<TileInfo> toCheck = new Linq<TileInfo>(get(origin));
        LinqHashMap<TileInfo,TileInfo> parents = new LinqHashMap<TileInfo, TileInfo>();
        parents.put(get(origin),null);

        for (int distance = 1; ; distance++) {
            Linq<TileInfo> newToCheck = new Linq<TileInfo>();
            for (TileInfo ti : toCheck){
                for (TileInfo otherTile : getDistanceToTilesWithinTurn(ti.position, distance == 1 ? currentMovement : maxMovement).keySet()){
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

    public Linq<TileInfo> values(){return tiles.linqValues();}

    TileResource GetRandomResource(Linq<TileResource> resources, final ResourceType resourceType) {
        return resources.where(new Predicate<TileResource>() {
            @Override
            public boolean evaluate(TileResource arg0) {
                return arg0.resourceType.equals(resourceType);
            }
        }).getRandom();
    }

    public void placeUnitNearTile(Vector2 position, final String unit){
        getTilesInDistance(position,2).first(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.unit==null;
            }
        }).unit = GameBasics.Units.get(unit).getMapUnit(); // And if there's none, then kill me.
    }

    public int getTileHeight(TileInfo tileInfo){
        int height=0;
        if(new Linq<String>("Forest","Jungle").contains(tileInfo.terrainFeature)) height+=1;
        if("Hill".equals(tileInfo.baseTerrain)) height+=2;
        return height;
    }

    public Linq<TileInfo> getViewableTiles(Vector2 position, int sightDistance){
        final Linq<TileInfo> tiles = getTilesInDistance(position,1);
        if(get(position).baseTerrain.equals("Hill")) sightDistance+=1;
        for (int i = 0; i <= sightDistance; i++) {
            Linq<TileInfo> tilesForLayer = new Linq<TileInfo>();
            for (final TileInfo tile : getTilesAtDistance(position, i))
                if (tile.getNeighbors().any(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        if (!tiles.contains(arg0))
                            return false; // Basically, if there's a viewable neighbor which is either flatlands, or I'm taller than him
                        int tileHeight = getTileHeight(arg0);
                        return tileHeight == 0 || getTileHeight(tile) > tileHeight;
                    }
                })) tilesForLayer.add(tile);
            tiles.addAll(tilesForLayer);
        }

        return tiles;
    }

}

