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
import com.unciv.civinfo.CityInfo;
import com.unciv.civinfo.TileInfo;
import com.unciv.game.pickerscreens.GameSaver;
import com.unciv.game.pickerscreens.ImprovementPickerScreen;
import com.unciv.game.pickerscreens.TechPickerScreen;
import com.unciv.models.LinqHashMap;
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
    Table TileTable = new Table();
    Table CivTable = new Table();
    TextButton TechButton = new TextButton("",skin);
    public LinqHashMap<String,WorldTileGroup> tileGroups = new LinqHashMap<String, WorldTileGroup>();

    Table OptionsTable = new Table();


    public WorldScreen(final UnCivGame game) {
        super(game);
        new Label("",skin).getStyle().font.getData().setScale(game.settings.labelScale);

        addTiles();
        stage.addActor(TileTable);

        Drawable tileTableBackground = new TextureRegionDrawable(new TextureRegion(new Texture("skin/tileTableBackground.png")))
                .tint(new Color(0x0040804f));
        tileTableBackground.setMinHeight(0);
        tileTableBackground.setMinWidth(0);
        TileTable.setBackground(tileTableBackground);
        OptionsTable.setBackground(tileTableBackground);

        TextureRegionDrawable civBackground = new TextureRegionDrawable(new TextureRegion(new Texture("skin/civTableBackground.png")));
//        civBackground.tint(new Color(0x0040804f));
        CivTable.setBackground(civBackground.tint(new Color(0x0040804f)));

        stage.addActor(CivTable);

        stage.addActor(TechButton);

        setCenterPosition(Vector2.Zero);

        update();
        createNextTurnButton(); // needs civ table to be positioned
        addOptionsTable();
    }

    public void update(){
        updateTechButton();
        updateTileTable();
        updateTiles();
        updateCivTable();
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

        
        
        TextButton closeButton = new TextButton("Close",skin);
        closeButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                OptionsTable.setVisible(false);
            }
        });
        OptionsTable.add(closeButton).pad(10);
        OptionsTable.setPosition(stage.getWidth()/2-OptionsTable.getWidth()/2,
                stage.getHeight()/2-OptionsTable.getHeight()/2);
        stage.addActor(OptionsTable);
    }

    private void updateTechButton() {
        TechButton.setVisible(game.civInfo.Cities.size()!=0);
        TechButton.clearListeners();
        TechButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new TechPickerScreen(game));
                dispose();
            }
        });

        if (game.civInfo.Tech.CurrentTechnology() == null) TechButton.setText("Choose a tech!");
        else TechButton.setText(game.civInfo.Tech.CurrentTechnology() + "\r\n"
                + game.civInfo.TurnsToTech(game.civInfo.Tech.CurrentTechnology()) + " turns");

        TechButton.setSize(TechButton.getPrefWidth(), TechButton.getPrefHeight());
        TechButton.setPosition(10, CivTable.getY() - TechButton.getHeight()-5);
    }

    private void updateCivTable() {
        CivTable.clear();
        CivTable.row().pad(20);
        CivStats currentStats = game.civInfo.civStats;

        TextButton CivilopediaButton = new TextButton("Menu",skin);
        CivilopediaButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                OptionsTable.setVisible(true);
                dispose();
            }
        });

        CivilopediaButton.getLabel().setFontScale(buttonScale);
        CivTable.add(CivilopediaButton)
                .size(CivilopediaButton.getWidth() * buttonScale, CivilopediaButton.getHeight() * buttonScale);

        CivTable.add(new Label("Turns: " + game.civInfo.turns+"/400", skin));

        CivStats nextTurnStats = game.civInfo.GetStatsForNextTurn();

        CivTable.add(new Label("Gold: " + currentStats.Gold + "(" +(nextTurnStats.Gold>0?"+":"") + nextTurnStats.Gold+")", skin));

        CivTable.add(new Label("Science: +" + nextTurnStats.Science, skin));
        CivTable.add(new Label("Happiness: " + nextTurnStats.Happiness, skin));
        CivTable.add(new Label("Culture: " + currentStats.Culture + "(+" + nextTurnStats.Culture+")", skin));

        CivTable.pack();

        CivTable.setPosition(10, stage.getHeight() - 10 - CivTable.getHeight());
        CivTable.setWidth(stage.getWidth() - 20);

    }

    private void createNextTurnButton() {
        TextButton nextTurnButton = new TextButton("Next turn", skin);
        nextTurnButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (game.civInfo.Tech.CurrentTechnology() == null
                        && game.civInfo.Cities.size()!=0) {
                    game.setScreen(new TechPickerScreen(game));
                    dispose();
                    return;
                }
                game.civInfo.NextTurn();
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
                    if(unitTile != null && group.tileInfo.Unit == null ) {
                        int distance = HexMath.GetDistance(unitTile.Position, group.tileInfo.Position);
                        if (distance <= unitTile.Unit.CurrentMovement) {
                            unitTile.Unit.CurrentMovement -= distance;
                            group.tileInfo.Unit = unitTile.Unit;
                            unitTile.Unit = null;
                            unitTile = null;
                            selectedTile = group.tileInfo;
                        }
                    }

                    update();
                }
            });


            Vector2 positionalVector = HexMath.Hex2WorldCoords(tileInfo.Position);
            int groupSize = 50;
            group.setPosition(stage.getWidth() / 2 + positionalVector.x * 0.8f * groupSize,
                    stage.getHeight() / 2 + positionalVector.y * 0.8f * groupSize);
            group.setSize(groupSize, groupSize);
            tileGroups.put(tileInfo.Position.toString(), group);
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
                scrollPane.setScale(scale);
                game.settings.tilesZoom = scale;
            }

        });
        stage.addActor(scrollPane);
    }

    private void updateTileTable() {
        if(selectedTile == null) return;
        TileTable.clearChildren();
        FullStats stats = selectedTile.GetTileStats();
        TileTable.pad(20);
        TileTable.columnDefaults(0).padRight(10);

        Label cityStatsHeader = new Label("Tile Stats",skin);
        cityStatsHeader.setFontScale(2);
        TileTable.add(cityStatsHeader).colspan(2).pad(10);
        TileTable.row();

        TileTable.add(new Label(selectedTile.toString(),skin)).colspan(2);
        TileTable.row();

        HashMap<String,String> TileStatsValues = new HashMap<String, String>();
        TileStatsValues.put("Production",stats.Production+"");
        TileStatsValues.put("Food",stats.Food+"");
        TileStatsValues.put("Gold",stats.Gold+"");
        TileStatsValues.put("Science",stats.Science+"");
        TileStatsValues.put("Culture",stats.Culture+"");

        for(String key : TileStatsValues.keySet()){
            if(TileStatsValues.get(key).equals("0")) continue; // this tile gives nothing of this stat, so why even display it?
            TileTable.add(ImageGetter.getStatIcon(key)).align(Align.right);
            TileTable.add(new Label(TileStatsValues.get(key),skin)).align(Align.left);
            TileTable.row();
        }

        if(selectedTile.Unit!=null){
            TextButton moveUnitButton = new TextButton("Move to", skin);
            if(unitTile == selectedTile) moveUnitButton = new TextButton("Stop movement",skin);
            moveUnitButton.getLabel().setFontScale(buttonScale);
            if(selectedTile.Unit.CurrentMovement == 0){
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
                    for(Vector2 vector : HexMath.GetVectorsInDistance(unitTile.Position, unitTile.Unit.CurrentMovement)){
                        if(tileGroups.containsKey(vector.toString()))
                            tileGroups.get(vector.toString()).setColor(Color.WHITE);
                        }

                    update();
                }
            });
            TileTable.add(moveUnitButton).colspan(2)
                    .size(moveUnitButton.getWidth() * buttonScale, moveUnitButton.getHeight() * buttonScale);

            if(selectedTile.Unit.Name.equals("Settler")){
                TextButton foundCityButton = new TextButton("Found City", skin);
                foundCityButton.getLabel().setFontScale(buttonScale);
                foundCityButton.addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        game.civInfo.addCity(selectedTile.Position);
                        selectedTile.Unit = null; // Remove settler!
                        update();
                    }
                });

                if(HexMath.GetVectorsInDistance(selectedTile.Position,2).any(new Predicate<Vector2>() {
                    @Override
                    public boolean evaluate(Vector2 arg0) {
                        return tileGroups.containsKey(arg0.toString()) &&
                                tileGroups.get(arg0.toString()).tileInfo.IsCityCenter();
                    }
                })){
                    foundCityButton.setDisabled(true);
                    foundCityButton.setColor(Color.GRAY);
                }

                TileTable.row();
                TileTable.add(foundCityButton).colspan(2)
                        .size(foundCityButton.getWidth() * buttonScale, foundCityButton.getHeight() * buttonScale);
            }

            if(selectedTile.Unit.Name.equals("Worker")) {
                String improvementButtonText = selectedTile.ImprovementInProgress == null ?
                        "Construct\r\nimprovement" : selectedTile.ImprovementInProgress+"\r\nin progress";
                TextButton improvementButton = new TextButton(improvementButtonText, skin);
                improvementButton.getLabel().setFontScale(buttonScale);
                improvementButton.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        game.setScreen(new ImprovementPickerScreen(game, selectedTile));
                        dispose();
                    }
                });
                if(!GameBasics.TileImprovements.linqValues().any(new Predicate<TileImprovement>() {
                            @Override
                            public boolean evaluate(TileImprovement arg0) {
                                return selectedTile.CanBuildImprovement(arg0);
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

//        TileTable.setBackground(getTableBackground(TileTable.getWidth(),TileTable.getHeight()));

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
        for(CityInfo city : game.civInfo.Cities)
            for(Vector2 tileLocation : city.CityTileLocations)
                for(Vector2 adjacentLocation : HexMath.GetAdjacentVectors(tileLocation))
                    ViewableVectorStrings.add(adjacentLocation.toString());

        // Tiles within 2 tiles of units
        for(TileInfo tile : game.civInfo.tileMap.values()
                .where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.Unit!=null;
            }
        }))
            for(Vector2 vector : HexMath.GetVectorsInDistance(tile.Position,2))
                ViewableVectorStrings.add(vector.toString());

        for(String string : ViewableVectorStrings)
            if(tileGroups.containsKey(string))
                tileGroups.get(string).setIsViewable(true);
    }





    void setCenterPosition(final Vector2 vector){
        TileGroup TG = tileGroups.linqValues().first(new Predicate<WorldTileGroup>() {
            @Override
            public boolean evaluate(WorldTileGroup arg0) {
                return arg0.tileInfo.Position.equals(vector) ;
            }
        });
        float x = TG.getX()-stage.getWidth()/2;
        float y = TG.getY()-stage.getHeight()/2;
        scrollPane.layout();
        scrollPane.setScrollX(x);
        scrollPane.setScrollY(y);
        scrollPane.updateVisualScroll();
    }

}


