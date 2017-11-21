package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
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

        String terrainFileName = "TerrainIcons/" + tileInfo.GetLastTerrain().Name + "_(Civ5).png";
        terrainImage = ImageGetter.getImageByFilename(terrainFileName);
        terrainImage.setSize(50,50);
        addActor(terrainImage);
    }

    void addPopulationIcon(){
        populationImage = ImageGetter.getStatIcon("Population");
        populationImage.setAlign(Align.bottomRight);
        populationImage.setX(terrainImage.getWidth()-populationImage.getWidth());
        addActor(populationImage);
    }

    void removePopulationIcon(){
        populationImage.remove();
        populationImage = null;
    }


    void update() {

        if (tileInfo.HasViewableResource() && resourceImage == null) { // Need to add the resource image!
            String fileName = "ResourceIcons/" + tileInfo.Resource + "_(Civ5).png";
            Image image = ImageGetter.getImageByFilename(fileName);
            image.setScale(0.5f);
            image.setOrigin(Align.topRight);
            resourceImage = image;
            addActor(image);
        }

        if (tileInfo.Unit != null && unitImage == null) {
            unitImage = ImageGetter.getImageByFilename("StatIcons/" + tileInfo.Unit.Name + "_(Civ5).png");
            addActor(unitImage);
            unitImage.setSize(20, 20);
        }

        if (tileInfo.Unit == null && unitImage != null) {
            unitImage.remove();
            unitImage = null;
        }


        if (tileInfo.Improvement != null && improvementImage == null) {
            improvementImage = ImageGetter.getImageByFilename("ImprovementIcons/" + tileInfo.Improvement.replace(' ','_') + "_(Civ5).png");
            addActor(improvementImage);
            improvementImage.setSize(20, 20);
            improvementImage.moveBy(0, terrainImage.getHeight() - improvementImage.getHeight());
        }

        if(populationImage!=null){
            if(tileInfo.IsWorked) populationImage.setColor(Color.WHITE);
            else populationImage.setColor(Color.GRAY);
        }
    }
}
