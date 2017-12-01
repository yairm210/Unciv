package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import com.unciv.civinfo.CityInfo;
import com.unciv.civinfo.TileInfo;
import com.unciv.game.pickerscreens.BuildingPickerScreen;
import com.unciv.models.stats.FullStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class CityScreen extends CameraStageBaseScreen {

    TileInfo selectedTile = null;
    float buttonScale = game.settings.buttonScale;
    Table TileTable = new Table();
    Table CityStatsTable = new Table();
    Table CityPickerTable = new Table();
    TextButton TechButton = new TextButton("Exit city",skin);
    public ArrayList<TileGroup> tileGroups = new ArrayList<TileGroup>();

    public CityScreen(final UnCivGame game) {
        super(game);
        new Label("",skin).getStyle().font.getData().setScale(game.settings.labelScale);

        addTiles();
        stage.addActor(TileTable);


        Drawable tileTableBackground = new TextureRegionDrawable(new TextureRegion(new Texture("skin/tileTableBackground.png")))
                .tint(new Color(0x0040804f));
        tileTableBackground.setMinHeight(0);
        tileTableBackground.setMinWidth(0);
        TileTable.setBackground(tileTableBackground);

        CityStatsTable.setBackground(tileTableBackground);
        updateCityTable();
        stage.addActor(CityStatsTable);

        updateGoToWorldButton();
        stage.addActor(TechButton);

        updateCityPickerTable();
        stage.addActor(CityPickerTable);
    }

    private void updateCityPickerTable() {
        CityPickerTable.clear();
        CityPickerTable.row().pad(20);
        if(game.civInfo.cities.size()>1) {
            TextButton prevCityButton = new TextButton("<", skin);
            prevCityButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    com.unciv.civinfo.CivilizationInfo ci = game.civInfo;
                    if (ci.currentCity == 0) ci.currentCity = ci.cities.size()-1;
                    else ci.currentCity--;
                    game.setScreen(new CityScreen(game));
                    dispose();
                }
            });
            CityPickerTable.add(prevCityButton);
        }

        Label currentCityLabel = new Label(game.civInfo.getCurrentCity().name, skin);
        currentCityLabel.setFontScale(2);
        CityPickerTable.add(currentCityLabel);

        if(game.civInfo.cities.size()>1) {
            TextButton nextCityButton = new TextButton(">", skin);
            nextCityButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    com.unciv.civinfo.CivilizationInfo ci = game.civInfo;
                    if (ci.currentCity == ci.cities.size()-1) ci.currentCity = 0;
                    else ci.currentCity++;
                    game.setScreen(new CityScreen(game));
                    dispose();
                }
            });
            CityPickerTable.add(nextCityButton);
        }
        CityPickerTable.pack();
        CityPickerTable.setPosition(stage.getWidth()/2-CityPickerTable.getWidth()/2,0);
        stage.addActor(CityPickerTable);
    }

    private void updateGoToWorldButton() {
        TechButton.clearListeners();
        TechButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setWorldScreen();
                game.worldScreen.setCenterPosition(game.civInfo.getCurrentCity().cityLocation);
                dispose();
            }
        });

        TechButton.setSize(TechButton.getPrefWidth(), TechButton.getPrefHeight());
        TechButton.setPosition(10, stage.getHeight() - TechButton.getHeight()-5);
    }

    private void addTiles() {
        final CityInfo cityInfo = game.civInfo.getCurrentCity();

        Group allTiles = new Group();

        for(final TileInfo tileInfo : game.civInfo.tileMap.getTilesInDistance(cityInfo.cityLocation,5)){
            TileGroup group = new TileGroup(tileInfo);
            group.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedTile = tileInfo;
                    updateTileTable();
                }
            });

            if(!cityInfo.getTilesInRange().contains(tileInfo)) group.setColor(0,0,0,0.3f);
            else if(!tileInfo.isCityCenter()) {
                group.addPopulationIcon();
                group.populationImage.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if(tileInfo.workingCity ==null && cityInfo.getFreePopulation() > 0) tileInfo.workingCity = cityInfo.name;
                        else if(cityInfo.name.equals(tileInfo.workingCity)) tileInfo.workingCity = null;
                        updateCityTable();
                    }
                });
            }

            Vector2 positionalVector = HexMath.Hex2WorldCoords(tileInfo.position.cpy().sub(cityInfo.cityLocation));
            int groupSize = 50;
            group.setPosition(stage.getWidth()/2 + positionalVector.x*0.8f  * groupSize,
                    stage.getHeight()/2 + positionalVector.y*0.8f * groupSize);
            tileGroups.add(group);
            allTiles.addActor(group);
        }

        final ScrollPane scrollPane = new ScrollPane(allTiles);
        scrollPane.setFillParent(true);
        scrollPane.setPosition(game.settings.cityTilesX, game.settings.cityTilesY);
        scrollPane.setOrigin(stage.getWidth()/2,stage.getHeight()/2);
        scrollPane.setScale(game.settings.tilesZoom);
        scrollPane.addListener(new ActorGestureListener(){
            public float lastScale =1;
            float lastInitialDistance=0;
            
            @Override
            public void zoom(InputEvent event, float initialDistance, float distance) {
                if(lastInitialDistance!=initialDistance){
                    lastInitialDistance = initialDistance;
                    lastScale = scrollPane.getScaleX();
                }
                float scale = (float) Math.sqrt(distance/initialDistance)* lastScale;
                scrollPane.setScale(scale);
                game.settings.tilesZoom=scale;
            }

            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
                scrollPane.moveBy(deltaX*scrollPane.getScaleX(),deltaY*scrollPane.getScaleX());
                game.settings.cityTilesX = scrollPane.getX();
                game.settings.cityTilesY = scrollPane.getY();
            }
        });
        stage.addActor(scrollPane);
    }

    private void updateCityTable() {
        CityInfo cityInfo = game.civInfo.getCurrentCity();
        FullStats stats = cityInfo.getCityStats();
        CityStatsTable.pad(20);
        CityStatsTable.columnDefaults(0).padRight(10);
        CityStatsTable.clear();

        Label cityStatsHeader = new Label("City Stats",skin);

        cityStatsHeader.setFontScale(2);
        CityStatsTable.add(cityStatsHeader).colspan(2).pad(10);
        CityStatsTable.row();

        HashMap<String,String> CityStatsValues = new LinkedHashMap<String, String>();
        CityStatsValues.put("production",stats.production +"");
        CityStatsValues.put("food",stats.food +" ("+cityInfo.cityPopulation.FoodStored+"/"+cityInfo.cityPopulation.FoodToNextPopulation()+")");
        CityStatsValues.put("gold",stats.gold +"");
        CityStatsValues.put("science",stats.science +"");
        CityStatsValues.put("culture",stats.culture +" ("+cityInfo.cultureStored+"/"+cityInfo.getCultureToNextTile()+")");
        CityStatsValues.put("Population",cityInfo.getFreePopulation()+"/"+cityInfo.cityPopulation.Population);

        for(String key : CityStatsValues.keySet()){
            CityStatsTable.add(ImageGetter.getStatIcon(key)).align(Align.right);
            CityStatsTable.add(new Label(CityStatsValues.get(key),skin)).align(Align.left);
            CityStatsTable.row();
        }

        String CurrentBuilding = game.civInfo.getCurrentCity().cityBuildings.CurrentBuilding;

        String BuildingText = "Pick building";
        if(CurrentBuilding != null) BuildingText = CurrentBuilding+"\r\n"
                +cityInfo.cityBuildings.TurnsToBuilding(CurrentBuilding)+" turns";
        TextButton buildingPickButton = new TextButton(BuildingText,skin);
        buildingPickButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new BuildingPickerScreen(game));
                dispose();
            }
        });
        buildingPickButton.getLabel().setFontScale(buttonScale);
        CityStatsTable.add(buildingPickButton).colspan(2).pad(10)
                .size(buildingPickButton.getWidth()*buttonScale,buildingPickButton.getHeight()*buttonScale);

        CityStatsTable.setPosition(10,10);
        CityStatsTable.pack();
    }

    private void updateTileTable() {
        if(selectedTile == null) return;
        TileTable.clearChildren();

        CityInfo City =game.civInfo.getCurrentCity();
        FullStats stats = selectedTile.getTileStats();
        TileTable.pad(20);
        TileTable.columnDefaults(0).padRight(10);

        Label cityStatsHeader = new Label("Tile Stats",skin);
        cityStatsHeader.setFontScale(2);
        TileTable.add(cityStatsHeader).colspan(2).pad(10);
        TileTable.row();

        TileTable.add(new Label(selectedTile.toString(),skin)).colspan(2);
        TileTable.row();

        HashMap<String,String> TileStatsValues = new HashMap<String, String>();
        TileStatsValues.put("production",stats.production +"");
        TileStatsValues.put("food",stats.food +"");
        TileStatsValues.put("gold",stats.gold +"");
        TileStatsValues.put("science",stats.science +"");
        TileStatsValues.put("culture",stats.culture +"");

        for(String key : TileStatsValues.keySet()){
            if(TileStatsValues.get(key).equals("0")) continue; // this tile gives nothing of this stat, so why even display it?
            TileTable.add(ImageGetter.getStatIcon(key)).align(Align.right);
            TileTable.add(new Label(TileStatsValues.get(key),skin)).align(Align.left);
            TileTable.row();
        }

        TileTable.pack();

        TileTable.setPosition(stage.getWidth()-10- TileTable.getWidth(), 10);
    }

    @Override
    public void render(float delta) {
        for(TileGroup HG : tileGroups) {
            HG.update();
        }

        super.render(delta);
    }

    public static Pixmap getPixmapRoundedRectangle(int width, int height, int radius, int color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Blending.None);
        pixmap.setColor(color);

        pixmap.fillRectangle(0, radius, pixmap.getWidth(), pixmap.getHeight()-2*radius);
        pixmap.fillRectangle(radius, 0, pixmap.getWidth() - 2*radius, pixmap.getHeight());

        pixmap.fillCircle(radius, radius, radius);
        pixmap.fillCircle(radius, pixmap.getHeight()-radius, radius);
        pixmap.fillCircle(pixmap.getWidth()-radius, radius, radius);
        pixmap.fillCircle(pixmap.getWidth()-radius, pixmap.getHeight()-radius, radius);
        return pixmap;
    }


}

