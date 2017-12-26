package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.civinfo.TileInfo;
import com.unciv.game.pickerscreens.ImprovementPickerScreen;
import com.unciv.game.pickerscreens.PolicyPickerScreen;
import com.unciv.game.pickerscreens.TechPickerScreen;
import com.unciv.game.utils.CameraStageBaseScreen;
import com.unciv.game.utils.GameSaver;
import com.unciv.game.utils.HexMath;
import com.unciv.game.utils.ImageGetter;
import com.unciv.models.LinqCollection;
import com.unciv.models.LinqHashMap;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileImprovement;
import com.unciv.models.stats.CivStats;
import com.unciv.models.stats.FullStats;

import java.util.HashMap;
import java.util.HashSet;

public class WorldScreen extends CameraStageBaseScreen {

    TileInfo selectedTile = null;

    TileInfo unitTile = null;
    ScrollPane scrollPane;

    float buttonScale = game.settings.buttonScale;
    Table tileTable = new Table();
    Table civTable = new Table();
    TextButton techButton = new TextButton("",skin);
    public LinqHashMap<String,WorldTileGroup> tileGroups = new LinqHashMap<String, WorldTileGroup>();

    Table optionsTable = new Table();
    Table notificationsTable = new Table();
    TextButton selectIdleUnitButton;

    public WorldScreen() {
        new Label("",skin).getStyle().font.getData().setScale(game.settings.labelScale);

        addTiles();
        stage.addActor(tileTable);

        Drawable tileTableBackground = ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(new Color(0x004085bf));
        tileTableBackground.setMinHeight(0);
        tileTableBackground.setMinWidth(0);
        tileTable.setBackground(tileTableBackground);
        optionsTable.setBackground(tileTableBackground);

        notificationsTable.background(ImageGetter.getSingleColorDrawable(new Color(0x004085bf)));

        TextureRegionDrawable civBackground = ImageGetter.getDrawable("skin/civTableBackground.png");
        civTable.setBackground(civBackground.tint(new Color(0x004085bf)));

        stage.addActor(civTable);
        stage.addActor(techButton);

        ScrollPane notificationsScroll = new ScrollPane(notificationsTable);
        notificationsScroll.setSize(stage.getWidth()/3, stage.getHeight()/3);
        stage.addActor(notificationsScroll);
        addSelectIdleUnitButton();
        update();

        setCenterPosition(Vector2.Zero);
        createNextTurnButton(); // needs civ table to be positioned
        addOptionsTable();
    }

    private void addSelectIdleUnitButton() {
        selectIdleUnitButton = new TextButton("Select next idle unit",skin);
        selectIdleUnitButton.setPosition(stage.getWidth()/2-selectIdleUnitButton.getWidth()/2,5);
        stage.addActor(selectIdleUnitButton);
        selectIdleUnitButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                LinqCollection<TileInfo> tilesWithIdleUnits = game.civInfo.tileMap.values().where(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        return arg0.hasIdleUnit();
                    }
                });

                TileInfo tileToSelect;
                if(!tilesWithIdleUnits.contains(selectedTile))
                    tileToSelect=tilesWithIdleUnits.get(0);
                else {
                    int index = tilesWithIdleUnits.indexOf(selectedTile)+1;
                    if(tilesWithIdleUnits.size()==index) index=0;
                    tileToSelect = tilesWithIdleUnits.get(index);
                    }
                setCenterPosition(tileToSelect.position);
                selectedTile =tileToSelect;
                update();
            }
        });
    }

    void updateIdleUnitButton(){
        if (game.civInfo.tileMap.values().any(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.hasIdleUnit();
            }
        })){
            selectIdleUnitButton.setColor(Color.WHITE);
            selectIdleUnitButton.setTouchable(Touchable.enabled);
        }
        else {
            selectIdleUnitButton.setColor(Color.GRAY);
            selectIdleUnitButton.setTouchable(Touchable.disabled);
        }
    }

    private void updateNotificationsList() {
        notificationsTable.clearChildren();
        for(String notification : game.civInfo.notifications)
        {
            Label label = new Label(notification,skin);
            label.setColor(Color.WHITE);
            notificationsTable.add(label).pad(10).fill();
            notificationsTable.row();
        }
        notificationsTable.pack();
    }

    public void update(){
        updateTechButton();
        updateTileTable();
        updateTiles();
        updateCivTable();
        updateNotificationsList();
        updateIdleUnitButton();
        if(game.civInfo.tech.freeTechs!=0) {
            game.setScreen(new TechPickerScreen(true));
        }
    }


    void addOptionsTable(){
        optionsTable.setVisible(false);

        TextButton OpenCivilopediaButton = new TextButton("Civilopedia",skin);
        OpenCivilopediaButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new CivilopediaScreen());
                optionsTable.setVisible(false);
            }
        });
        optionsTable.add(OpenCivilopediaButton).pad(10);
        optionsTable.row();

        TextButton StartNewGameButton = new TextButton("Start new game",skin);
        StartNewGameButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startNewGame();
            }
        });
        optionsTable.add(StartNewGameButton).pad(10);
        optionsTable.row();

        TextButton OpenScienceVictoryScreen = new TextButton("Science victory status",skin);
        OpenScienceVictoryScreen.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new ScienceVictoryScreen());
            }
        });
        optionsTable.add(OpenScienceVictoryScreen).pad(10);
        optionsTable.row();

        TextButton OpenPolicyPickerScreen = new TextButton("Social Policies",skin);
        OpenPolicyPickerScreen.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new PolicyPickerScreen());
            }
        });
        optionsTable.add(OpenPolicyPickerScreen).pad(10);
        optionsTable.row();


        TextButton closeButton = new TextButton("Close",skin);
        closeButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                optionsTable.setVisible(false);
            }
        });
        optionsTable.add(closeButton).pad(10);
        optionsTable.pack(); // Needed to show the background.
        optionsTable.setPosition(stage.getWidth()/2- optionsTable.getWidth()/2,
                stage.getHeight()/2- optionsTable.getHeight()/2);
        stage.addActor(optionsTable);
    }

    private void updateTechButton() {
        techButton.setVisible(game.civInfo.cities.size()!=0);
        techButton.clearListeners();
        techButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new TechPickerScreen());
            }
        });

        if (game.civInfo.tech.currentTechnology() == null) techButton.setText("Choose a tech!");
        else techButton.setText(game.civInfo.tech.currentTechnology() + "\r\n"
                + game.civInfo.turnsToTech(game.civInfo.tech.currentTechnology()) + " turns");

        techButton.setSize(techButton.getPrefWidth(), techButton.getPrefHeight());
        techButton.setPosition(10, civTable.getY() - techButton.getHeight()-5);
    }

    private void updateCivTable() {
        civTable.clear();
        civTable.row().pad(15);
        CivStats currentStats = game.civInfo.civStats;

        TextButton CivilopediaButton = new TextButton("Menu",skin);
        CivilopediaButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                optionsTable.setVisible(!optionsTable.isVisible());
            }
        });

        CivilopediaButton.getLabel().setFontScale(buttonScale);
        civTable.add(CivilopediaButton)
                .size(CivilopediaButton.getWidth() * buttonScale, CivilopediaButton.getHeight() * buttonScale);

        civTable.add(new Label("Turns: " + game.civInfo.turns+"/400", skin));

        CivStats nextTurnStats = game.civInfo.getStatsForNextTurn();

        civTable.add(new Label("Gold: " + Math.round(currentStats.gold)
                + "(" +(nextTurnStats.gold >0?"+":"") + Math.round(nextTurnStats.gold) +")", skin));

        Label scienceLabel = new Label("Science: +" + Math.round(nextTurnStats.science)
                +"\r\n"+game.civInfo.tech.getAmountResearchedText(), skin);
        scienceLabel.setAlignment(Align.center);
        civTable.add(scienceLabel);
        String happinessText = "Happiness: " + Math.round(game.civInfo.getHappinessForNextTurn());
        if(game.civInfo.isGoldenAge()) happinessText+="\r\n GOLDEN AGE ("+game.civInfo.turnsLeftForCurrentGoldenAge+")";
        else happinessText+= "\r\n ("+(int)game.civInfo.civStats.happiness+"/"+game.civInfo.happinessRequiredForNextGoldenAge()+")";
        Label happinessLabel = new Label(happinessText, skin);
        happinessLabel.setAlignment(Align.center);
        civTable.add(happinessLabel);
        String cultureString = "Culture: " + "+" + Math.round(nextTurnStats.culture) +"\r\n"
                +"("+ ((int) game.civInfo.civStats.culture)+"/"+game.civInfo.getCultureNeededForNextPolicy()+")";
        Label cultureLabel = new Label(cultureString, skin);
        cultureLabel.setAlignment(Align.center);
        civTable.add(cultureLabel);

        civTable.pack();

        civTable.setPosition(10, stage.getHeight() - 10 - civTable.getHeight());
        civTable.setWidth(stage.getWidth() - 20);

    }

    private void createNextTurnButton() {
        TextButton nextTurnButton = new TextButton("Next turn", skin);
        nextTurnButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (game.civInfo.tech.currentTechnology() == null
                        && game.civInfo.cities.size()!=0) {
                    game.setScreen(new TechPickerScreen());
                    return;
                }
                game.civInfo.nextTurn();
                GameSaver.SaveGame(game,"Autosave");
                update();
            }
        });
        nextTurnButton.setPosition(stage.getWidth() - nextTurnButton.getWidth() - 10,
                civTable.getY() - nextTurnButton.getHeight() - 10);
        stage.addActor(nextTurnButton);
    }

    
    private void addTiles() {
        final Group allTiles = new Group();

        float topX = 0;
        float topY = 0;
        float bottomX = 0;
        float bottomY = 0;

        for (final TileInfo tileInfo : game.civInfo.tileMap.values()) {
            final WorldTileGroup group = new WorldTileGroup(tileInfo);

            group.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                selectedTile = tileInfo;
                if(unitTile != null && group.tileInfo.unit == null ) {
                    LinqHashMap<TileInfo, Float> distanceToTiles = game.civInfo.tileMap.getUnitDistanceToTiles(unitTile.position,unitTile.unit.currentMovement);
                    if(distanceToTiles.containsKey(selectedTile)) {
                        unitTile.moveUnitToTile(group.tileInfo,distanceToTiles.get(selectedTile));
                        unitTile = null;
                        selectedTile = group.tileInfo;
                    }
                }

                update();
                }
            });


            Vector2 positionalVector = HexMath.Hex2WorldCoords(tileInfo.position);
            int groupSize = 50;
            group.setPosition(stage.getWidth() / 2 + positionalVector.x * 0.8f * groupSize,
                    stage.getHeight() / 2 + positionalVector.y * 0.8f * groupSize);
            group.setSize(groupSize, groupSize);
            tileGroups.put(tileInfo.position.toString(), group);
            allTiles.addActor(group);
            topX = Math.max(topX, group.getX() + groupSize);
            topY = Math.max(topY, group.getY() + groupSize);
            bottomX = Math.min(bottomX, group.getX());
            bottomY = Math.min(bottomY, group.getY());
        }

        for (TileGroup group : tileGroups.linqValues()) {
            group.moveBy(-bottomX, -bottomY);
        }

//        allTiles.setPosition(-bottomX,-bottomY); // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(topX - bottomX, topY - bottomY);


        scrollPane = new ScrollPane(allTiles);
        scrollPane.setFillParent(true);
        scrollPane.setOrigin(stage.getWidth() / 2, stage.getHeight() / 2);
        scrollPane.setScale(game.settings.tilesZoom);
        scrollPane.setSize(stage.getWidth(), stage.getHeight());
        scrollPane.addListener(new ActorGestureListener() {
            public float lastScale = 1;
            float lastInitialDistance = 0;

            @Override
            public void zoom(InputEvent event, float initialDistance, float distance) {
                if (lastInitialDistance != initialDistance) {
                    lastInitialDistance = initialDistance;
                    lastScale = scrollPane.getScaleX();
                }
                float scale = (float) Math.sqrt(distance / initialDistance) * lastScale;
                if(scale<1) return;
                scrollPane.setScale(scale);
                game.settings.tilesZoom = scale;
            }

        });
        stage.addActor(scrollPane);
    }

    private void updateTileTable() {
        if(selectedTile == null) return;
        tileTable.clearChildren();
        FullStats stats = selectedTile.getTileStats();
        tileTable.pad(20);
        tileTable.columnDefaults(0).padRight(10);

        Label cityStatsHeader = new Label("Tile Stats",skin);
        cityStatsHeader.setFontScale(2);
        tileTable.add(cityStatsHeader).colspan(2).pad(10);
        tileTable.row();

        tileTable.add(new Label(selectedTile.toString(),skin)).colspan(2);
        tileTable.row();


        HashMap<String,Float> TileStatsValues = new HashMap<String, Float>();
        TileStatsValues.put("Production",stats.production);
        TileStatsValues.put("Food",stats.food);
        TileStatsValues.put("Gold",stats.gold);
        TileStatsValues.put("Science",stats.science);
        TileStatsValues.put("Culture",stats.culture);

        for(String key : TileStatsValues.keySet()){
            if(TileStatsValues.get(key) == 0) continue; // this tile gives nothing of this stat, so why even display it?
            tileTable.add(com.unciv.game.utils.ImageGetter.getStatIcon(key)).align(Align.right);
            tileTable.add(new Label(Math.round(TileStatsValues.get(key))+"",skin)).align(Align.left);
            tileTable.row();
        }


        if(selectedTile.unit !=null){
            TextButton moveUnitButton = new TextButton("Move to", skin);
            if(unitTile == selectedTile) moveUnitButton = new TextButton("Stop movement",skin);
            moveUnitButton.getLabel().setFontScale(buttonScale);
            if(selectedTile.unit.currentMovement == 0){
                moveUnitButton.setColor(Color.GRAY);
                moveUnitButton.setTouchable(Touchable.disabled);
            }
            moveUnitButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if(unitTile!=null) {
                        unitTile = null;
                        update();
                        return;
                    }
                    unitTile = selectedTile;

                    // Set all tiles transparent except those in unit range
                    for(TileGroup TG : tileGroups.linqValues()) TG.setColor(0,0,0,0.3f);
                    for(TileInfo tile : game.civInfo.tileMap.getUnitDistanceToTiles(unitTile.position,unitTile.unit.currentMovement).keySet()){
                        tileGroups.get(tile.position.toString()).setColor(Color.WHITE);
                    }

                    update();
                }
            });
            tileTable.add(moveUnitButton).colspan(2)
                    .size(moveUnitButton.getWidth() * buttonScale, moveUnitButton.getHeight() * buttonScale);

            if(selectedTile.unit.name.equals("Settler")) {
                addUnitAction(tileTable, "Found City",
                        !game.civInfo.tileMap.getTilesInDistance(selectedTile.position, 2).any(new Predicate<TileInfo>() {
                            @Override
                            public boolean evaluate(TileInfo arg0) {
                                return arg0.isCityCenter();
                            }
                        }),
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                game.civInfo.addCity(selectedTile.position);
                                if (unitTile == selectedTile)
                                    unitTile = null; // The settler was in the middle of moving and we then founded a city with it
                                selectedTile.unit = null; // Remove settler!
                                update();
                            }
                        });
            }

            if(selectedTile.unit.name.equals("Worker")) {
                String improvementButtonText = selectedTile.improvementInProgress == null ?
                        "Construct\r\nimprovement" : selectedTile.improvementInProgress +"\r\nin progress";
                addUnitAction(tileTable,improvementButtonText, !selectedTile.isCityCenter() ||
                        GameBasics.TileImprovements.linqValues().any(new Predicate<TileImprovement>() {
                            @Override
                            public boolean evaluate(TileImprovement arg0) {
                                return selectedTile.canBuildImprovement(arg0);
                            }
                        })
                        ,new ClickListener() {

                    @Override
                    public void clicked(InputEvent event, float x, float y) {game.setScreen(new ImprovementPickerScreen(selectedTile));}
                } );
                addUnitAction(tileTable, "automation".equals(selectedTile.unit.action)? "Stop automation" : "Automate", true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                if ("automation".equals(selectedTile.unit.action))
                                    selectedTile.unit.action = null;
                                else {
                                    selectedTile.unit.action = "automation";
                                    selectedTile.unit.doAutomatedAction(selectedTile);
                                }
                                update();
                            }
                        });
            }

            if(selectedTile.unit.name.equals("Great Scientist")){
                addUnitAction(tileTable, "Discover Technology",true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                game.civInfo.tech.freeTechs+=1;
                                selectedTile.unit=null;// destroy!
                                game.setScreen(new TechPickerScreen(true));
                            }
                        });
                addUnitAction(tileTable, "Construct Academy",true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement="Academy";
                                selectedTile.unit=null;// destroy!
                                update();
                            }
                        });
            }

            if(selectedTile.unit.name.equals("Great Artist")){
                addUnitAction(tileTable, "Start Golden Age",true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                game.civInfo.enterGoldenAge();
                                selectedTile.unit=null;// destroy!
                                update();
                            }
                        });
                addUnitAction(tileTable, "Construct Landmark",true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement="Landmark";
                                selectedTile.unit=null;// destroy!
                                update();
                            }
                        });
            }

            if(selectedTile.unit.name.equals("Great Engineer")){
                addUnitAction(tileTable, "Hurry Wonder",selectedTile.isCityCenter() &&
                                selectedTile.getCity().cityConstructions.getCurrentConstruction() instanceof Building &&

                                ((Building)selectedTile.getCity().cityConstructions.getCurrentConstruction()).isWonder,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.getCity().cityConstructions.addConstruction(300 + (30 * selectedTile.getCity().population)); //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                                selectedTile.unit=null; // destroy!
                                update();
                            }
                        });
                addUnitAction(tileTable, "Construct Manufactory",true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement="Manufactory";
                                selectedTile.unit=null;// destroy!
                                update();
                            }
                        });
            }
            if(selectedTile.unit.name.equals("Great Merchant")){
                addUnitAction(tileTable, "Conduct Trade Mission",true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                game.civInfo.civStats.gold+=350; // + 50 * era_number - todo!
                                selectedTile.unit=null; // destroy!
                                update();
                            }
                        });
                addUnitAction(tileTable, "Construct Customs House",true,
                        new ClickListener(){
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement="Customs House";
                                selectedTile.unit=null;// destroy!
                                update();
                            }
                        });
            }
        }

        tileTable.pack();

        tileTable.setPosition(stage.getWidth()-10- tileTable.getWidth(), 10);
    }

    private void addUnitAction(Table tileTable, String actionText, boolean canAct, ClickListener action) {
        TextButton actionButton = new TextButton(actionText, skin);
        actionButton.getLabel().setFontScale(buttonScale);
        actionButton.addListener(action);
        if (selectedTile.unit.currentMovement == 0 || !canAct) {
            actionButton.setColor(Color.GRAY);
            actionButton.setTouchable(Touchable.disabled);
        }

        tileTable.row();
        tileTable.add(actionButton).colspan(2)
                .size(actionButton.getWidth() * buttonScale, actionButton.getHeight() * buttonScale);

    }

    private void updateTiles() {
        for (WorldTileGroup WG : tileGroups.linqValues()) WG.update(this);

        if(unitTile!=null) return; // While we're in "unit move" mode, no tiles but the tiles the unit can move to will be "visible"

        // YES A TRIPLE FOR, GOT PROBLEMS WITH THAT?
        // Seriously though, there is probably a more efficient way of doing this, probably?
        // The original implementation caused serious lag on android, so efficiency is key, here
        for (WorldTileGroup WG : tileGroups.linqValues()) WG.setIsViewable(false);
        HashSet<String> ViewableVectorStrings = new HashSet<String>();

        // tiles adjacent to city tiles
        for(TileInfo tileInfo : game.civInfo.tileMap.values())
            if(game.civInfo.civName.equals(tileInfo.owner))
                for(Vector2 adjacentLocation : HexMath.GetAdjacentVectors(tileInfo.position))
                    ViewableVectorStrings.add(adjacentLocation.toString());

        // Tiles within 2 tiles of units
        for(TileInfo tile : game.civInfo.tileMap.values()
                .where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.unit !=null;
            }
        }))
            for(Vector2 vector : HexMath.GetVectorsInDistance(tile.position,2))
                ViewableVectorStrings.add(vector.toString());

        for(String string : ViewableVectorStrings)
            if(tileGroups.containsKey(string))
                tileGroups.get(string).setIsViewable(true);
    }


    void setCenterPosition(final Vector2 vector){
        TileGroup TG = tileGroups.linqValues().first(new Predicate<WorldTileGroup>() {
            @Override
            public boolean evaluate(WorldTileGroup arg0) {
                return arg0.tileInfo.position.equals(vector) ;
            }
        });
        scrollPane.layout(); // Fit the scroll pane to the contents - otherwise, setScroll won't work!
        // We want to center on the middle of TG (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== where the screen starts) needs to be half a screen away
        scrollPane.setScrollX(TG.getX()+TG.getWidth()/2-stage.getWidth()/2);
        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        scrollPane.setScrollY(scrollPane.getMaxY() - (TG.getY() + TG.getWidth()/2 - stage.getHeight()/2));
        scrollPane.updateVisualScroll();
    }

}

