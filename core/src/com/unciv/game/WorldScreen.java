package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
import com.unciv.game.pickerscreens.TechPickerScreen;
import com.unciv.game.utils.*;
import com.unciv.models.LinqHashMap;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileImprovement;
import com.unciv.models.stats.CivStats;
import com.unciv.models.stats.FullStats;

import java.util.HashMap;
import java.util.HashSet;

public class WorldScreen extends com.unciv.game.utils.CameraStageBaseScreen {

    TileInfo selectedTile = null;

    TileInfo unitTile = null;
    ScrollPane scrollPane;

    float buttonScale = game.settings.buttonScale;
    Table TileTable = new Table();
    Table CivTable = new Table();
    TextButton TechButton = new TextButton("",skin);
    public LinqHashMap<String,WorldTileGroup> tileGroups = new LinqHashMap<String, WorldTileGroup>();

    Table OptionsTable = new Table();
    Table NotificationsTable = new Table();


    public WorldScreen(final UnCivGame game) {
        super(game);
        new Label("",skin).getStyle().font.getData().setScale(game.settings.labelScale);

        addTiles();
        stage.addActor(TileTable);

        Drawable tileTableBackground = new TextureRegionDrawable(new TextureRegion(new Texture("skin/tileTableBackground.png")))
                .tint(new Color(0x004085bf));
        tileTableBackground.setMinHeight(0);
        tileTableBackground.setMinWidth(0);
        TileTable.setBackground(tileTableBackground);
        OptionsTable.setBackground(tileTableBackground);

        TextureRegionDrawable civBackground = new TextureRegionDrawable(new TextureRegion(new Texture("skin/civTableBackground.png")));
        CivTable.setBackground(civBackground.tint(new Color(0x004085bf)));

        stage.addActor(CivTable);

        stage.addActor(TechButton);

        setCenterPosition(Vector2.Zero);

        update();
        createNextTurnButton(); // needs civ table to be positioned
        addOptionsTable();

        addNotificationsList();
    }

    private void addNotificationsList() {
        stage.addActor(NotificationsTable);
    }
    private void updateNotificationsList() {
        NotificationsTable.clearChildren();
        for(String notification : game.civInfo.notifications)
        {
            NotificationsTable.add(new Label(notification,skin)).pad(5);
            NotificationsTable.row();
        }
        NotificationsTable.pack();
    }

    public void update(){
        if(game.civInfo.tech.freeTechs!=0) {
            game.setScreen(new TechPickerScreen(game, true));
        }
        updateTechButton();
        updateTileTable();
        updateTiles();
        updateCivTable();
        updateNotificationsList();
    }


    void addOptionsTable(){
        OptionsTable.setVisible(false);

        TextButton OpenCivilopediaButton = new TextButton("Civilopedia",skin);
        OpenCivilopediaButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new CivilopediaScreen(game));
                OptionsTable.setVisible(false);
            }
        });
        OptionsTable.add(OpenCivilopediaButton).pad(10);
        OptionsTable.row();

        TextButton StartNewGameButton = new TextButton("Start new game",skin);
        StartNewGameButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startNewGame();
            }
        });
        OptionsTable.add(StartNewGameButton).pad(10);
        OptionsTable.row();

        TextButton OpenScienceVictoryScreen = new TextButton("Science victory status",skin);
        OpenScienceVictoryScreen.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new ScienceVictoryScreen(game));
            }
        });
        OptionsTable.add(OpenScienceVictoryScreen).pad(10);
        OptionsTable.row();


        TextButton closeButton = new TextButton("Close",skin);
        closeButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                OptionsTable.setVisible(false);
            }
        });
        OptionsTable.add(closeButton).pad(10);
        OptionsTable.pack(); // Needed to show the background.
        OptionsTable.setPosition(stage.getWidth()/2-OptionsTable.getWidth()/2,
                stage.getHeight()/2-OptionsTable.getHeight()/2);
        stage.addActor(OptionsTable);
    }

    private void updateTechButton() {
        TechButton.setVisible(game.civInfo.cities.size()!=0);
        TechButton.clearListeners();
        TechButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new TechPickerScreen(game));
            }
        });

        if (game.civInfo.tech.currentTechnology() == null) TechButton.setText("Choose a tech!");
        else TechButton.setText(game.civInfo.tech.currentTechnology() + "\r\n"
                + game.civInfo.turnsToTech(game.civInfo.tech.currentTechnology()) + " turns");

        TechButton.setSize(TechButton.getPrefWidth(), TechButton.getPrefHeight());
        TechButton.setPosition(10, CivTable.getY() - TechButton.getHeight()-5);
    }

    private void updateCivTable() {
        CivTable.clear();
        CivTable.row().pad(15);
        CivStats currentStats = game.civInfo.civStats;

        TextButton CivilopediaButton = new TextButton("Menu",skin);
        CivilopediaButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                OptionsTable.setVisible(!OptionsTable.isVisible());
            }
        });

        CivilopediaButton.getLabel().setFontScale(buttonScale);
        CivTable.add(CivilopediaButton)
                .size(CivilopediaButton.getWidth() * buttonScale, CivilopediaButton.getHeight() * buttonScale);

        CivTable.add(new Label("Turns: " + game.civInfo.turns+"/400", skin));

        CivStats nextTurnStats = game.civInfo.getStatsForNextTurn();

        CivTable.add(new Label("Gold: " + Math.round(currentStats.gold)
                + "(" +(nextTurnStats.gold >0?"+":"") + Math.round(nextTurnStats.gold) +")", skin));

        Label scienceLabel = new Label("Science: +" + Math.round(nextTurnStats.science)
                +"\r\n"+game.civInfo.tech.getAmountResearchedText(), skin);
        scienceLabel.setAlignment(Align.center);
        CivTable.add(scienceLabel);
        String happinessText = "Happiness: " + Math.round(game.civInfo.getHappinessForNextTurn());
        if(game.civInfo.isGoldenAge()) happinessText+="\r\n GOLDEN AGE ("+game.civInfo.turnsLeftForCurrentGoldenAge+")";
        else happinessText+= "\r\n ("+(int)game.civInfo.civStats.happiness+"/"+game.civInfo.happinessRequiredForNextGoldenAge()+")";
        Label happinessLabel = new Label(happinessText, skin);
        happinessLabel.setAlignment(Align.center);
        CivTable.add(happinessLabel);
        CivTable.add(new Label("Culture: " + Math.round(currentStats.culture) + "(+" + Math.round(nextTurnStats.culture) +")", skin));

        CivTable.pack();

        CivTable.setPosition(10, stage.getHeight() - 10 - CivTable.getHeight());
        CivTable.setWidth(stage.getWidth() - 20);

    }

    private void createNextTurnButton() {
        TextButton nextTurnButton = new TextButton("Next turn", skin);
        nextTurnButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (game.civInfo.tech.currentTechnology() == null
                        && game.civInfo.cities.size()!=0) {
                    game.setScreen(new TechPickerScreen(game));
                    return;
                }
                game.civInfo.nextTurn();
                GameSaver.SaveGame(game,"Autosave");
                update();
            }
        });
        nextTurnButton.setPosition(stage.getWidth() - nextTurnButton.getWidth() - 10,
                CivTable.getY() - nextTurnButton.getHeight() - 10);
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
                    LinqHashMap<TileInfo, Float> distanceToTiles = game.civInfo.tileMap.getDistanceToTiles(unitTile.position,unitTile.unit.currentMovement);
                    if(distanceToTiles.containsKey(selectedTile)) {
                        unitTile.unit.currentMovement -= distanceToTiles.get(selectedTile);
                        //unitTile.unit.currentMovement = round(unitTile.unit.currentMovement,3);
                        if(unitTile.unit.currentMovement < 0.1) unitTile.unit.currentMovement =0; // silly floats which are "almost zero"
                        group.tileInfo.unit = unitTile.unit;
                        unitTile.unit = null;
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
        TileTable.clearChildren();
        FullStats stats = selectedTile.getTileStats();
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
            TileTable.add(com.unciv.game.utils.ImageGetter.getStatIcon(key)).align(Align.right);
            TileTable.add(new Label(Math.round(TileStatsValues.get(key))+"",skin)).align(Align.left);
            TileTable.row();
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
                    for(TileInfo tile : game.civInfo.tileMap.getDistanceToTiles(unitTile.position,unitTile.unit.currentMovement).keySet()){
                        tileGroups.get(tile.position.toString()).setColor(Color.WHITE);
                    }

                    update();
                }
            });
            TileTable.add(moveUnitButton).colspan(2)
                    .size(moveUnitButton.getWidth() * buttonScale, moveUnitButton.getHeight() * buttonScale);

            if(selectedTile.unit.name.equals("Settler")){
                TextButton foundCityButton = new TextButton("Found City", skin);
                foundCityButton.getLabel().setFontScale(buttonScale);
                foundCityButton.addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        game.civInfo.addCity(selectedTile.position);
                        if(unitTile==selectedTile) unitTile = null; // The settler was in the middle of moving and we then founded a city with it
                        selectedTile.unit = null; // Remove settler!
                        update();
                    }
                });

                if(selectedTile.unit.currentMovement==0  ||
                        game.civInfo.tileMap.getTilesInDistance(selectedTile.position,2).any(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        return arg0.isCityCenter();
                    }
                })){
                    foundCityButton.setTouchable(Touchable.disabled);
                    foundCityButton.setColor(Color.GRAY);
                }

                TileTable.row();
                TileTable.add(foundCityButton).colspan(2)
                        .size(foundCityButton.getWidth() * buttonScale, foundCityButton.getHeight() * buttonScale);
            }

            if(selectedTile.unit.name.equals("Worker")) {
                String improvementButtonText = selectedTile.improvementInProgress == null ?
                        "Construct\r\nimprovement" : selectedTile.improvementInProgress +"\r\nin progress";
                TextButton improvementButton = new TextButton(improvementButtonText, skin);
                improvementButton.getLabel().setFontScale(buttonScale);
                improvementButton.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        game.setScreen(new ImprovementPickerScreen(game, selectedTile));
                    }
                });
                if(selectedTile.unit.currentMovement ==0 || selectedTile.isCityCenter() ||
                        !GameBasics.TileImprovements.linqValues().any(new Predicate<TileImprovement>() {
                            @Override
                            public boolean evaluate(TileImprovement arg0) {
                                return selectedTile.canBuildImprovement(arg0);
                            }
                        })){
                    improvementButton.setColor(Color.GRAY);
                    improvementButton.setTouchable(Touchable.disabled);
                }

                TileTable.row();
                TileTable.add(improvementButton).colspan(2)
                        .size(improvementButton.getWidth() * buttonScale, improvementButton.getHeight() * buttonScale);
            }
        }

        TileTable.pack();

        TileTable.setPosition(stage.getWidth()-10- TileTable.getWidth(), 10);
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


