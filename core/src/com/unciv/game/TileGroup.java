package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.unciv.civinfo.CivilizationInfo;
import com.unciv.civinfo.RoadStatus;
import com.unciv.civinfo.TileInfo;
import com.unciv.game.utils.HexMath;
import com.unciv.game.utils.ImageGetter;
import com.unciv.models.LinqHashMap;

public class TileGroup extends Group {
    Image terrainImage;
    String terrainType;
    Image resourceImage;
    Image unitImage;
    Image improvementImage;
    String improvementType;
    Image populationImage;
    LinqHashMap<String,Image> roadImages = new LinqHashMap<String, Image>();
    Image hexagon;

    Container<TextButton> cityButton;
    TileInfo tileInfo;

    TileGroup(TileInfo tileInfo){
        this.tileInfo = tileInfo;

        terrainType = tileInfo.getLastTerrain().name;
        String terrainFileName = "TerrainIcons/" + terrainType.replace(' ','_') + "_(Civ5).png";
        terrainImage = ImageGetter.getImage(terrainFileName);
        terrainImage.setSize(50,50);
        addActor(terrainImage);
    }


    void addPopulationIcon(){
        populationImage = ImageGetter.getStatIcon("Population");
        populationImage.moveBy(0, terrainImage.getHeight()-populationImage.getHeight()); // top left
        addActor(populationImage);
    }

    void removePopulationIcon(){
        populationImage.remove();
        populationImage = null;
    }


    void update() {
        if(!terrainType.equals(tileInfo.getLastTerrain().name)) {
            terrainType = tileInfo.getLastTerrain().name;
            String terrainFileName = "TerrainIcons/" + terrainType.replace(' ', '_') + "_(Civ5).png";
            terrainImage.setDrawable(ImageGetter.getDrawable(terrainFileName)); // In case we e.g. removed a jungle
        }

        if (tileInfo.hasViewableResource() && resourceImage == null) { // Need to add the resource image!
            String fileName = "ResourceIcons/" + tileInfo.resource + "_(Civ5).png";
            Image image = ImageGetter.getImage(fileName);
            image.setSize(20,20);
            image.moveBy(terrainImage.getWidth()-image.getWidth(), 0); // bottom right
            resourceImage = image;
            addActor(image);
        }

        if (tileInfo.unit != null && unitImage == null) {
            unitImage = ImageGetter.getImage("StatIcons/" + tileInfo.unit.name + "_(Civ5).png");
            addActor(unitImage);
            unitImage.setSize(20, 20); // not moved - is at bottom left
        }

        if (tileInfo.unit == null && unitImage != null) {
            unitImage.remove();
            unitImage = null;
        }

        if(unitImage!=null){
            if(tileInfo.unit.currentMovement ==0 ||
                    (tileInfo.unit.name.equals("Worker") && tileInfo.improvementInProgress!=null))
                unitImage.setColor(Color.GRAY);
            else unitImage.setColor(Color.WHITE);
        }


        if (tileInfo.improvement != null &&!tileInfo.improvement.equals(improvementType)) {
            improvementImage = ImageGetter.getImage("ImprovementIcons/" + tileInfo.improvement.replace(' ','_') + "_(Civ5).png");
            addActor(improvementImage);
            improvementImage.setSize(20, 20);
            improvementImage.moveBy(terrainImage.getWidth()-improvementImage.getWidth(),
                    terrainImage.getHeight() - improvementImage.getHeight()); // top right
            improvementType = tileInfo.improvement;
        }

        if(populationImage!=null){
            if(tileInfo.workingCity !=null) populationImage.setColor(Color.WHITE);
            else populationImage.setColor(Color.GRAY);
        }

        if(tileInfo.roadStatus != RoadStatus.None){
            for (TileInfo neighbor : CivilizationInfo.current().tileMap.getTilesInDistance(tileInfo.position,1)) {
                if (neighbor == tileInfo || neighbor.roadStatus == RoadStatus.None) continue;
                if (roadImages.containsKey(neighbor.position.toString())) continue;

                Image image = ImageGetter.getImage(ImageGetter.WhiteDot);
                if(tileInfo.roadStatus == RoadStatus.Railroad && neighbor.roadStatus == RoadStatus.Railroad)
                    image.setColor(Color.BLACK); // railroad
                else image.setColor(Color.BROWN); // road
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
        }
    }
}
