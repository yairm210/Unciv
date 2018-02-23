package com.unciv.ui.tilegroups;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.unciv.logic.map.RoadStatus;
import com.unciv.logic.map.TileInfo;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.ui.utils.HexMath;
import com.unciv.ui.utils.ImageGetter;

public class TileGroup extends Group {
    protected Image terrainImage;
    String terrainType;
    protected Image resourceImage;
    protected Image unitImage;
    protected Image improvementImage;
    String improvementType;
    public Image populationImage;
    LinqHashMap<String,Image> roadImages = new LinqHashMap<String, Image>();
    protected Image hexagon;

    protected Container<TextButton> cityButton;
    public TileInfo tileInfo;

    public TileGroup(TileInfo tileInfo){
        this.tileInfo = tileInfo;

        terrainType = tileInfo.getLastTerrain().name;
        String terrainFileName = "TerrainIcons/" + terrainType.replace(' ','_') + "_(Civ5).png";
        terrainImage = ImageGetter.getImage(terrainFileName);
        int groupSize = 50;
        terrainImage.setSize(groupSize,groupSize);
        setSize(groupSize,groupSize);
        addActor(terrainImage);
    }

    public void addPopulationIcon(){
        populationImage = ImageGetter.getImage("StatIcons/populationGreen.png");
        populationImage.setSize(20,20);
        populationImage.moveBy(0, terrainImage.getHeight()-populationImage.getHeight()); // top left
        addActor(populationImage);
    }

    protected void removePopulationIcon(){
        populationImage.remove();
        populationImage = null;
    }


    public void update() {
        if (tileInfo.explored) {
            terrainImage.setColor(Color.WHITE);
        } else {
            terrainImage.setColor(Color.BLACK);
            return;
        }

        if (!terrainType.equals(tileInfo.getLastTerrain().name)) {
            terrainType = tileInfo.getLastTerrain().name;
            String terrainFileName = "TerrainIcons/" + terrainType.replace(' ', '_') + "_(Civ5).png";
            terrainImage.setDrawable(ImageGetter.getDrawable(terrainFileName)); // In case we e.g. removed a jungle
        }

        if (tileInfo.hasViewableResource(tileInfo.tileMap.gameInfo.getPlayerCivilization()) && resourceImage == null) { // Need to add the resource image!
            String fileName = "ResourceIcons/" + tileInfo.resource + "_(Civ5).png";
            resourceImage = ImageGetter.getImage(fileName);
            resourceImage.setSize(20, 20);
            resourceImage.moveBy(terrainImage.getWidth() - resourceImage.getWidth(), 0); // bottom right
            addActor(resourceImage);
        }

        if (tileInfo.unit != null && unitImage == null) {
            unitImage = ImageGetter.getImage("UnitIcons/" + tileInfo.unit.name.replace(" ", "_") + "_(Civ5).png");
            addActor(unitImage);
            unitImage.setSize(20, 20); // not moved - is at bottom left
        }

        if (tileInfo.unit == null && unitImage != null) {
            unitImage.remove();
            unitImage = null;
        }

        if (unitImage != null) {
            if (!tileInfo.hasIdleUnit()) unitImage.setColor(Color.GRAY);
            else unitImage.setColor(Color.WHITE);
        }


        if (tileInfo.improvement != null && !tileInfo.improvement.equals(improvementType)) {
            improvementImage = ImageGetter.getImage("ImprovementIcons/" + tileInfo.improvement.replace(' ', '_') + "_(Civ5).png");
            addActor(improvementImage);
            improvementImage.setSize(20, 20);
            improvementImage.moveBy(terrainImage.getWidth() - improvementImage.getWidth(),
                    terrainImage.getHeight() - improvementImage.getHeight()); // top right
            improvementType = tileInfo.improvement;
        }

        if (populationImage != null) {
            if (tileInfo.workingCity != null) populationImage.setColor(Color.WHITE);
            else populationImage.setColor(Color.GRAY);
        }

        if (tileInfo.roadStatus != RoadStatus.None) {
            for (TileInfo neighbor : tileInfo.getNeighbors()) {
                if (neighbor.roadStatus == RoadStatus.None) continue;
                if (!roadImages.containsKey(neighbor.position.toString())) {
                    Image image = ImageGetter.getImage(ImageGetter.WhiteDot);
                    roadImages.put(neighbor.position.toString(), image);

                    Vector2 relativeHexPosition = tileInfo.position.cpy().sub(neighbor.position);
                    Vector2 relativeWorldPosition = HexMath.Hex2WorldCoords(relativeHexPosition);

                    // This is some crazy voodoo magic so I'll explain.
                    image.moveBy(25, 25); // Move road to center of tile
                    // in addTiles, we set the position of groups by relative world position *0.8*groupSize, where groupSize = 50
                    // Here, we want to have the roads start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
                    image.moveBy(-relativeWorldPosition.x * 0.8f * 25, -relativeWorldPosition.y * 0.8f * 25);
                    image.setSize(10, 2);
                    addActor(image);
                    image.setOrigin(0, 1); // This is so that the rotation is calculated from the middle of the road and not the edge
                    image.setRotation((float) (180 / Math.PI * Math.atan2(relativeWorldPosition.y, relativeWorldPosition.x)));
                }

                if (tileInfo.roadStatus == RoadStatus.Railroad && neighbor.roadStatus == RoadStatus.Railroad)
                    roadImages.get(neighbor.position.toString()).setColor(Color.GRAY); // railroad
                else roadImages.get(neighbor.position.toString()).setColor(Color.BROWN); // road
            }
        }

    }
}

