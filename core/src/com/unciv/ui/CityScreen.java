package com.unciv.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.unciv.logic.city.CityInfo;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.city.IConstruction;
import com.unciv.logic.map.TileInfo;
import com.unciv.ui.utils.CameraStageBaseScreen;
import com.unciv.ui.utils.HexMath;
import com.unciv.models.linq.Linq;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.stats.FullStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class CityScreen extends CameraStageBaseScreen {

    TileInfo selectedTile = null;
    float buttonScale = game.settings.buttonScale;
    Table TileTable = new Table();
    Table BuildingsTable = new Table();
    Table CityStatsTable = new Table();
    Table CityPickerTable = new Table();
    TextButton TechButton = new TextButton("Exit city",skin);
    public ArrayList<TileGroup> tileGroups = new ArrayList<TileGroup>();

    public CityInfo getCity(){return game.civInfo.getCurrentCity();}
    
    public CityScreen() {
        new Label("",skin).getStyle().font.getData().setScale(game.settings.labelScale);

        addTiles();
        stage.addActor(TileTable);

        Drawable tileTableBackground = com.unciv.ui.utils.ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(new Color(0x0040804f));
        tileTableBackground.setMinHeight(0);
        tileTableBackground.setMinWidth(0);
        TileTable.setBackground(tileTableBackground);

        Table BuildingsTableContainer = new Table();
        BuildingsTableContainer.pad(20);
        BuildingsTableContainer.setBackground(tileTableBackground);
        BuildingsTableContainer.add(new Label("Buildings",skin)).row();
        updateBuildingsTable();
        ScrollPane buildingsScroll = new ScrollPane(BuildingsTable);
        BuildingsTableContainer.add(buildingsScroll).height(stage.getHeight()/2);

        BuildingsTableContainer.pack();
        BuildingsTableContainer.setPosition(stage.getWidth()-BuildingsTableContainer.getWidth(),
                stage.getHeight()-BuildingsTableContainer.getHeight());

        CityStatsTable.setBackground(tileTableBackground);
        stage.addActor(CityStatsTable);
        stage.addActor(TechButton);
        stage.addActor(CityPickerTable);
        stage.addActor(BuildingsTableContainer);
        update();

        Linq<String> tutorial = new Linq<String>();
        tutorial.add("Welcome to your first city!" +
                "\r\nAs on now, you only have 1 population," +
                "\r\n  but this will grow when you amass enough surplus food");
        tutorial.add("Similarly, your city's borders grow when you" +
                "\r\n  amass enough culture, which is not generated" +
                "\r\n  by tiles but rather by buildings.");
        tutorial.add("Each population in your city can work" +
                "\r\n  a single tile, providing the city with that tile's yields.");
        tutorial.add("Population can be assigned and unassigned" +
                "\r\n  by clicking on the green population symbols on the tiles - " +
                "\r\n  but of course, you can only assign population" +
                "\r\n  if you have idle population to spare!");
        tutorial.add("The center tile off a city is always worked," +
                "\r\n  and doesn't require population," +
                "\r\n  but it cannot be improved by tile improvements.");
        tutorial.add("The city's production always goes towards the" +
                "\r\n  current construction - you can pick the city's" +
                "\r\n  construction by clicking on the construction" +
                "\r\n  button on the bottom-left");

        displayTutorials("CityEntered",tutorial);
    }

    private void update(){
        updateBuildingsTable();
        updateCityPickerTable();
        updateCityTable();
        updateGoToWorldButton();
        updateTileTable();
        updateTileGroups();
    }


    private void updateTileGroups(){
        for(TileGroup HG : tileGroups) {
            HG.update();
        }
    }

    private Image getSpecialistIcon(String imageName, final String building, final boolean isFilled, final FullStats specialistType) {
        Image specialist = com.unciv.ui.utils.ImageGetter.getImage(imageName);
        specialist.setSize(50,50);
        if(!isFilled) specialist.setColor(Color.GRAY);
        specialist.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(isFilled) getCity().population.buildingsSpecialists.get(building).add(specialistType.minus()); //unassign
                else if(getCity().population.getFreePopulation()==0) return;
                else {
                    if(!getCity().population.buildingsSpecialists.containsKey(building))
                        getCity().population.buildingsSpecialists.put(building,new FullStats());
                    getCity().population.buildingsSpecialists.get(building).add(specialistType); //assign!}
                }

                getCity().cityStats.update();
                update();
            }
        });
        return specialist;
    }

    private void updateBuildingsTable(){
        BuildingsTable.clear();


        Linq<Building> Wonders = new Linq<Building>();
        Linq<Building> SpecialistBuildings = new Linq<Building>();
        Linq<Building> Others = new Linq<Building>();

        for(Building building : getCity().cityConstructions.getBuiltBuildings()) {
            if (building.isWonder) Wonders.add(building);
            else if (building.specialistSlots != null) SpecialistBuildings.add(building);
            else Others.add(building);
        }

        if(!Wonders.isEmpty()) {
            Label label = new Label("Wonders", skin);
            label.setFontScale(1.5f);
            label.setColor(Color.GREEN);
            BuildingsTable.add(label).pad(5).row();
            for (Building building : Wonders)
                BuildingsTable.add(new Label(building.name, skin)).pad(5).row();
        }

        if(!SpecialistBuildings.isEmpty()) {
            Label label = new Label("Specialist Buildings", skin);
            label.setFontScale(1.5f);
            label.setColor(Color.GREEN);
            BuildingsTable.add(label).pad(5).row();
            for (Building building : SpecialistBuildings) {
                BuildingsTable.add(new Label(building.name, skin)).pad(5);
                Table specialists = new Table();
                specialists.row().size(20).pad(5);
                if (!getCity().population.buildingsSpecialists.containsKey(building.name))
                    getCity().population.buildingsSpecialists.put(building.name, new FullStats());
                FullStats currentBuildingSpecialists = getCity().population.buildingsSpecialists.get(building.name);
                for (int i = 0; i < building.specialistSlots.production; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationBrown.png", building.name,
                            currentBuildingSpecialists.production > i, new FullStats() {{
                                production = 1;
                            }}));
                }
                for (int i = 0; i < building.specialistSlots.science; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationBlue.png", building.name,
                            currentBuildingSpecialists.science > i, new FullStats() {{
                                science = 1;
                            }}));
                }
                for (int i = 0; i < building.specialistSlots.culture; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationPurple.png", building.name,
                            currentBuildingSpecialists.culture > i, new FullStats() {{
                                culture = 1;
                            }}));
                }
                for (int i = 0; i < building.specialistSlots.gold; i++) {
                    specialists.add(getSpecialistIcon("StatIcons/populationYellow.png", building.name,
                            currentBuildingSpecialists.gold > i, new FullStats() {{
                                gold = 1;
                            }}));
                }
                BuildingsTable.add(specialists).row();
            }
        }

        if(!Others.isEmpty()) {
            Label label = new Label("Buildings", skin);
            label.setFontScale(1.5f);
            label.setColor(Color.GREEN);
            BuildingsTable.add(label).pad(5).row();
            for (Building building : Others)
                BuildingsTable.add(new Label(building.name, skin)).pad(5).row();
        }
        BuildingsTable.pack();
    }

    private void updateCityPickerTable() {
        CityPickerTable.clear();
        CityPickerTable.row().pad(20);
        if(game.civInfo.cities.size()>1) {
            TextButton prevCityButton = new TextButton("<", skin);
            prevCityButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    CivilizationInfo ci = game.civInfo;
                    if (ci.currentCity == 0) ci.currentCity = ci.cities.size()-1;
                    else ci.currentCity--;
                    game.setScreen(new CityScreen());
                    dispose();
                }
            });
            CityPickerTable.add(prevCityButton);
        }

        Label currentCityLabel = new Label(getCity().name, skin);
        currentCityLabel.setFontScale(2);
        CityPickerTable.add(currentCityLabel);

        if(game.civInfo.cities.size()>1) {
            TextButton nextCityButton = new TextButton(">", skin);
            nextCityButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    CivilizationInfo ci = game.civInfo;
                    if (ci.currentCity == ci.cities.size()-1) ci.currentCity = 0;
                    else ci.currentCity++;
                    game.setScreen(new CityScreen());
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
                game.worldScreen.setCenterPosition(getCity().cityLocation);
                dispose();
            }
        });

        TechButton.setSize(TechButton.getPrefWidth(), TechButton.getPrefHeight());
        TechButton.setPosition(10, stage.getHeight() - TechButton.getHeight()-5);
    }

    private void addTiles() {
        final CityInfo cityInfo = getCity();

        Group allTiles = new Group();

        for(final TileInfo tileInfo : game.civInfo.tileMap.getTilesInDistance(cityInfo.cityLocation,5)){
            TileGroup group = new TileGroup(tileInfo);
            group.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedTile = tileInfo;
                    update();
                }
            });

            if(!cityInfo.getTilesInRange().contains(tileInfo) ||
                    (tileInfo.workingCity!=null && !tileInfo.workingCity.equals(cityInfo.name)))
                group.setColor(0,0,0,0.3f);
            else if(!tileInfo.isCityCenter()) {
                group.addPopulationIcon();
                group.populationImage.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if(tileInfo.workingCity ==null && cityInfo.population.getFreePopulation() > 0) tileInfo.workingCity = cityInfo.name;
                        else if(cityInfo.name.equals(tileInfo.workingCity)) tileInfo.workingCity = null;
                        cityInfo.cityStats.update();
                        update();
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
        final CityInfo cityInfo = getCity();
        FullStats stats = cityInfo.cityStats.currentCityStats;
        CityStatsTable.pad(20);
        CityStatsTable.columnDefaults(0).padRight(10);
        CityStatsTable.clear();

        Label cityStatsHeader = new Label("City Stats",skin);

        cityStatsHeader.setFontScale(2);
        CityStatsTable.add(cityStatsHeader).colspan(2).pad(10);
        CityStatsTable.row();

        HashMap<String,String> CityStatsValues = new LinkedHashMap<String, String>();
        CityStatsValues.put("Production",Math.round(stats.production)
                +cityInfo.cityConstructions.getAmountConstructedText());
        CityStatsValues.put("Food",Math.round(stats.food)
                +" ("+cityInfo.population.foodStored+"/"+cityInfo.population.foodToNextPopulation()+")");
        CityStatsValues.put("Gold",Math.round(stats.gold) +"");
        CityStatsValues.put("Science",Math.round(stats.science) +"");
        CityStatsValues.put("Culture",Math.round(stats.culture)
                +" ("+cityInfo.expansion.cultureStored+"/"+cityInfo.expansion.getCultureToNextTile()+")");
        CityStatsValues.put("Population",cityInfo.population.getFreePopulation()+"/"+cityInfo.population.population);

        for(String key : CityStatsValues.keySet()){
            CityStatsTable.add(com.unciv.ui.utils.ImageGetter.getStatIcon(key)).align(Align.right);
            CityStatsTable.add(new Label(CityStatsValues.get(key),skin)).align(Align.left);
            CityStatsTable.row();
        }

        String CurrentBuilding = getCity().cityConstructions.currentConstruction;

        String BuildingText = "Pick building";
        if(CurrentBuilding != null) BuildingText = cityInfo.cityConstructions.getCityProductionTextForCityButton();
        TextButton buildingPickButton = new TextButton(BuildingText,skin);
        buildingPickButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new com.unciv.ui.pickerscreens.ConstructionPickerScreen());
                dispose();
            }
        });
        buildingPickButton.getLabel().setFontScale(buttonScale);
        CityStatsTable.add(buildingPickButton).colspan(2).pad(10)
                .size(buildingPickButton.getWidth()*buttonScale,buildingPickButton.getHeight()*buttonScale);


        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        IConstruction construction = cityInfo.cityConstructions.getCurrentConstruction();
        if(construction != null && !(construction instanceof  Building && ((Building)construction).isWonder)) {
            CityStatsTable.row();
            int buildingGoldCost = construction.getGoldCost();
            TextButton buildingBuyButton = new TextButton("Buy for \r\n"+buildingGoldCost+" gold", skin);
            buildingBuyButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    cityInfo.cityConstructions.purchaseBuilding(cityInfo.cityConstructions.currentConstruction);
                    update();
                }
            });
            if(buildingGoldCost > game.civInfo.gold){
                buildingBuyButton.setColor(Color.GRAY);
                buildingBuyButton.setTouchable(Touchable.disabled);
            }
            buildingBuyButton.getLabel().setFontScale(buttonScale);
            CityStatsTable.add(buildingBuyButton).colspan(2).pad(10)
                    .size(buildingBuyButton.getWidth() * buttonScale, buildingBuyButton.getHeight() * buttonScale);
        }

        CityStatsTable.setPosition(10,10);
        CityStatsTable.pack();
    }

    private void updateTileTable() {
        if(selectedTile == null) return;
        TileTable.clearChildren();

        CityInfo city = getCity();
        FullStats stats = selectedTile.getTileStats(city);
        TileTable.pad(20);
        TileTable.columnDefaults(0).padRight(10);

        Label cityStatsHeader = new Label("Tile Stats",skin);
        cityStatsHeader.setFontScale(2);
        TileTable.add(cityStatsHeader).colspan(2).pad(10);
        TileTable.row();

        TileTable.add(new Label(selectedTile.toString(),skin)).colspan(2);
        TileTable.row();

        HashMap<String,Float> TileStatsValues = new HashMap<String, Float>();
        TileStatsValues.put("Production",stats.production);
        TileStatsValues.put("Food",stats.food);
        TileStatsValues.put("Gold",stats.gold);
        TileStatsValues.put("Science",stats.science);
        TileStatsValues.put("Culture",stats.culture);

        for(String key : TileStatsValues.keySet()){
            if(TileStatsValues.get(key) == 0) continue; // this tile gives nothing of this stat, so why even display it?
            TileTable.add(com.unciv.ui.utils.ImageGetter.getStatIcon(key)).align(Align.right);
            TileTable.add(new Label(Math.round(TileStatsValues.get(key))+"",skin)).align(Align.left);
            TileTable.row();
        }


        TileTable.pack();

        TileTable.setPosition(stage.getWidth()-10- TileTable.getWidth(), 10);
    }

}

