package com.unciv.ui.worldscreen;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.map.TileInfo;
import com.unciv.logic.map.TileMap;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.ui.tilegroups.TileGroup;
import com.unciv.ui.tilegroups.WorldTileGroup;
import com.unciv.ui.utils.HexMath;

import java.util.HashSet;

public class TileMapHolder extends ScrollPane {

    final WorldScreen worldScreen;
    final TileMap tileMap;
    final CivilizationInfo civInfo;
    TileInfo selectedTile = null;
    TileInfo unitTile = null;


    public TileMapHolder(final WorldScreen worldScreen, TileMap tileMap, CivilizationInfo civInfo) {
        super(null);
        this.worldScreen=worldScreen;
        this.tileMap = tileMap;
        this.civInfo = civInfo;
    }

    void addTiles() {
        final Group allTiles = new Group();

        float topX = 0;
        float topY = 0;
        float bottomX = 0;
        float bottomY = 0;

        for (final TileInfo tileInfo : tileMap.values()) {
            final WorldTileGroup group = new WorldTileGroup(tileInfo);

            group.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {

                    Linq<String> tutorial = new Linq<String>();
                    tutorial.add("Clicking on a tile selects that tile," +
                            "\r\n and displays information on that tile on the bottom-right," +
                            "\r\n as well as unit actions, if the tile contains a unit");
                    worldScreen.displayTutorials("TileClicked",tutorial);

                    selectedTile = tileInfo;
                    if (unitTile != null && group.tileInfo.unit == null) {
                        LinqHashMap<TileInfo, Float> distanceToTiles = tileMap.getDistanceToTilesWithinTurn(unitTile.position, unitTile.unit.currentMovement, civInfo.tech.isResearched("Machinery"));
                        if (distanceToTiles.containsKey(selectedTile)) {
                            unitTile.moveUnitToTile(group.tileInfo, distanceToTiles.get(selectedTile));
                        } else {
                            unitTile.unit.action = "moveTo " + ((int) selectedTile.position.x) + "," + ((int) selectedTile.position.y);
                            unitTile.unit.doPreTurnAction(unitTile);
                        }

                        unitTile = null;
                        selectedTile = group.tileInfo;
                    }

                    worldScreen.update();
                }
            });


            Vector2 positionalVector = HexMath.Hex2WorldCoords(tileInfo.position);
            int groupSize = 50;
            group.setPosition(worldScreen.stage.getWidth() / 2 + positionalVector.x * 0.8f * groupSize,
                    worldScreen.stage.getHeight() / 2 + positionalVector.y * 0.8f * groupSize);
            worldScreen.tileGroups.put(tileInfo.position.toString(), group);
            allTiles.addActor(group);
            topX = Math.max(topX, group.getX() + groupSize);
            topY = Math.max(topY, group.getY() + groupSize);
            bottomX = Math.min(bottomX, group.getX());
            bottomY = Math.min(bottomY, group.getY());
        }

        for (TileGroup group : worldScreen.tileGroups.linqValues()) {
            group.moveBy(-bottomX+50, -bottomY+50);
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(100 + topX - bottomX, 100 + topY - bottomY);


        setWidget(allTiles);
        setFillParent(true);
        setOrigin(worldScreen.stage.getWidth() / 2, worldScreen.stage.getHeight() / 2);
        setSize(worldScreen.stage.getWidth(), worldScreen.stage.getHeight());
        addListener(new ActorGestureListener() {
            public float lastScale = 1;
            float lastInitialDistance = 0;

            @Override
            public void zoom(InputEvent event, float initialDistance, float distance) {
                if (lastInitialDistance != initialDistance) {
                    lastInitialDistance = initialDistance;
                    lastScale = getScaleX();
                }
                float scale = (float) Math.sqrt(distance / initialDistance) * lastScale;
                if (scale < 1) return;
                setScale(scale);
            }

        });
    }

    void updateTiles() {
        for (WorldTileGroup WG : worldScreen.tileGroups.linqValues()) WG.update(worldScreen);

        if (unitTile != null)
            return; // While we're in "unit move" mode, no tiles but the tiles the unit can move to will be "visible"

        // YES A TRIPLE FOR, GOT PROBLEMS WITH THAT?
        // Seriously though, there is probably a more efficient way of doing this, probably?
        // The original implementation caused serious lag on android, so efficiency is key, here
        for (WorldTileGroup WG : worldScreen.tileGroups.linqValues()) WG.setIsViewable(false);
        HashSet<String> ViewableVectorStrings = new HashSet<String>();

        // tiles adjacent to city tiles
        for (TileInfo tileInfo : tileMap.values())
            if (civInfo.civName.equals(tileInfo.owner))
                for (Vector2 adjacentLocation : HexMath.GetAdjacentVectors(tileInfo.position))
                    ViewableVectorStrings.add(adjacentLocation.toString());

        // Tiles within 2 tiles of units
        for (TileInfo tile : tileMap.values()
                .where(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        return arg0.unit != null;
                    }
                }))
            for (TileInfo tileInfo : tileMap.getViewableTiles(tile.position,2))
                ViewableVectorStrings.add(tileInfo.position.toString());

        for (String string : ViewableVectorStrings)
            if (worldScreen.tileGroups.containsKey(string))
                worldScreen.tileGroups.get(string).setIsViewable(true);
    }


    public void setCenterPosition(final Vector2 vector) {
        TileGroup TG = worldScreen.tileGroups.linqValues().first(new Predicate<WorldTileGroup>() {
            @Override
            public boolean evaluate(WorldTileGroup arg0) {
                return arg0.tileInfo.position.equals(vector);
            }
        });
        layout(); // Fit the scroll pane to the contents - otherwise, setScroll won't work!
        // We want to center on the middle of TG (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== where the screen starts) needs to be half a screen away
        setScrollX(TG.getX() + TG.getWidth() / 2 - worldScreen.stage.getWidth() / 2);
        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        setScrollY(getMaxY() - (TG.getY() + TG.getWidth() / 2 - worldScreen.stage.getHeight() / 2));
        updateVisualScroll();
    }


}
