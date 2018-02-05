package com.unciv.logic.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.Terrain;
import com.unciv.models.gamebasics.TerrainType;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.ui.utils.HexMath;

public class RandomMapGenerator{

    private TileInfo addRandomTile(Vector2 position) {
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

        addRandomResourceToTile(tileInfo);

        return tileInfo;
    }

    void addRandomResourceToTile(final TileInfo tileInfo){

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
    }

    TileResource GetRandomResource(Linq<TileResource> resources, final ResourceType resourceType) {
        return resources.where(new Predicate<TileResource>() {
            @Override
            public boolean evaluate(TileResource arg0) {
                return arg0.resourceType.equals(resourceType);
            }
        }).getRandom();
    }


    public LinqHashMap<String,TileInfo> generateMap(int distance) {
        LinqHashMap<String,TileInfo> map = new LinqHashMap<String, TileInfo>();
        for(Vector2 vector : HexMath.GetVectorsInDistance(Vector2.Zero,distance))
            map.put(vector.toString(),addRandomTile(vector));
        return map;
    }
}
