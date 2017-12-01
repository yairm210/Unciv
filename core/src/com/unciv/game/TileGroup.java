package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.unciv.civinfo.TileInfo;

public class TileGroup extends Group {
    Image terrainImage;
    Image resourceImage;
    Image unitImage;
    Image improvementImage;
    Image populationImage;
    Image hexagon;

    Container<TextButton> cityButton;
    TileInfo tileInfo;

    TileGroup(TileInfo tileInfo){
        this.tileInfo = tileInfo;

        String terrainFileName = "TerrainIcons/" + tileInfo.getLastTerrain().name.replace(' ','_') + "_(Civ5).png";
        terrainImage = ImageGetter.getImageByFilename(terrainFileName);
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

        if (tileInfo.hasViewableResource() && resourceImage == null) { // Need to add the resource image!
            String fileName = "ResourceIcons/" + tileInfo.resource + "_(Civ5).png";
            Image image = ImageGetter.getImageByFilename(fileName);
            image.setSize(20,20);
            image.moveBy(terrainImage.getWidth()-image.getWidth(), 0); // bottom right
            resourceImage = image;
            addActor(image);
        }

        if (tileInfo.unit != null && unitImage == null) {
            unitImage = ImageGetter.getImageByFilename("StatIcons/" + tileInfo.unit.Name + "_(Civ5).png");
            addActor(unitImage);
            unitImage.setSize(20, 20); // not moved - is at bottom left
        }

        if (tileInfo.unit == null && unitImage != null) {
            unitImage.remove();
            unitImage = null;
        }

        if(unitImage!=null){
            if(tileInfo.unit.CurrentMovement==0) unitImage.setColor(Color.GRAY);
            else unitImage.setColor(Color.WHITE);
        }


        if (tileInfo.improvement != null && improvementImage == null) {
            improvementImage = ImageGetter.getImageByFilename("ImprovementIcons/" + tileInfo.improvement.replace(' ','_') + "_(Civ5).png");
            addActor(improvementImage);
            improvementImage.setSize(20, 20);
            improvementImage.moveBy(terrainImage.getWidth()-improvementImage.getWidth(),
                    terrainImage.getHeight() - improvementImage.getHeight()); // top right
        }

        if(populationImage!=null){
            if(tileInfo.workingCity !=null) populationImage.setColor(Color.WHITE);
            else populationImage.setColor(Color.GRAY);
        }
    }
}
