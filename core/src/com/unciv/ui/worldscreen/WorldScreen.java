package com.unciv.ui.worldscreen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.civilization.Notification;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.ui.CivilopediaScreen;
import com.unciv.ui.pickerscreens.PolicyPickerScreen;
import com.unciv.ui.pickerscreens.TechPickerScreen;
import com.unciv.ui.tilegroups.WorldTileGroup;
import com.unciv.ui.utils.CameraStageBaseScreen;
import com.unciv.ui.utils.GameSaver;
import com.unciv.ui.utils.ImageGetter;

public class WorldScreen extends CameraStageBaseScreen {

    public TileMapHolder tileMapHolder = new TileMapHolder(this);

    float buttonScale = game.settings.buttonScale;
    TileInfoTable tileInfoTable = new TileInfoTable(this);
    CivStatsTable civTable = new CivStatsTable();
    TextButton techButton = new TextButton("", skin);
    public LinqHashMap<String, WorldTileGroup> tileGroups = new LinqHashMap<String, WorldTileGroup>();

    Table optionsTable = new Table();
    NotificationsScroll notificationsScroll = new NotificationsScroll(this);
    IdleUnitButton idleUnitButton = new IdleUnitButton(this);

    public WorldScreen() {
        new Label("", skin).getStyle().font.getData().setScale(game.settings.labelScale);

        tileMapHolder.addTiles();
        stage.addActor(tileMapHolder);
        stage.addActor(tileInfoTable);
        stage.addActor(civTable);
        stage.addActor(techButton);
        stage.addActor(notificationsScroll);
        update();

        tileMapHolder.setCenterPosition(Vector2.Zero);
        createNextTurnButton(); // needs civ table to be positioned
        addOptionsTable();

        Linq<String> beginningTutorial = new Linq<String>();
        beginningTutorial.add("Hello, and welcome to Unciv!" +
                "\r\nCivilization games can be complex, so we'll" +
                "\r\n  be guiding you along your first journey." +
                "\r\nBefore we begin, let's review some basic game concepts.");
        beginningTutorial.add("This is the world map, which is made up of multiple tiles." +
                "\r\nEach tile can contain units, as well as resources" +
                "\r\n  and improvements, which we'll get to later");
        beginningTutorial.add("You start out with two units -" +
                "\r\n  a Settler - who can found a city," +
                "\r\n  and a scout, for exploring the area." +
                "\r\n  Click on a tile to assign orders the unit!");

        displayTutorials("NewGame",beginningTutorial);
    }



    public void update() {
        if(game.civInfo.tutorial.contains("CityEntered")){
            Linq<String> tutorial = new Linq<String>();
            tutorial.add("Once you've done everything you can, " +
                    "\r\nclick the next turn button on the top right to continue.");
            tutorial.add("Each turn, science, culture and gold are added" +
                    "\r\n to your civilization, your cities' construction" +
                    "\r\n continues, and they may grow in population or area.");
            displayTutorials("NextTurn",tutorial);
        }

        updateTechButton();
        if(tileMapHolder.selectedTile!=null) tileInfoTable.updateTileTable(tileMapHolder.selectedTile);
        tileMapHolder.updateTiles();
        civTable.update(this);
        notificationsScroll.update();
        idleUnitButton.update();
        if (game.civInfo.tech.freeTechs != 0) {
            game.setScreen(new TechPickerScreen(true));
        }
        else if(game.civInfo.policies.shouldOpenPolicyPicker){
            game.setScreen(new PolicyPickerScreen());
            game.civInfo.policies.shouldOpenPolicyPicker=false;
        }
    }

    void addOptionsTable() {
        Drawable tileTableBackground = ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(new Color(0x004085bf));
        optionsTable.setBackground(tileTableBackground);

        optionsTable.setVisible(false);

        TextButton OpenCivilopediaButton = new TextButton("Civilopedia", skin);
        OpenCivilopediaButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new CivilopediaScreen());
                optionsTable.setVisible(false);
            }
        });
        optionsTable.add(OpenCivilopediaButton).pad(10);
        optionsTable.row();

        TextButton StartNewGameButton = new TextButton("Start new game", skin);
        StartNewGameButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startNewGame();
            }
        });
        optionsTable.add(StartNewGameButton).pad(10);
        optionsTable.row();

        TextButton OpenScienceVictoryScreen = new TextButton("Science victory status", skin);
        OpenScienceVictoryScreen.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new com.unciv.ui.ScienceVictoryScreen());
            }
        });
        optionsTable.add(OpenScienceVictoryScreen).pad(10);
        optionsTable.row();

        TextButton OpenPolicyPickerScreen = new TextButton("Social Policies", skin);
        OpenPolicyPickerScreen.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new PolicyPickerScreen());
            }
        });
        optionsTable.add(OpenPolicyPickerScreen).pad(10);
        optionsTable.row();


        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                optionsTable.setVisible(false);
            }
        });
        optionsTable.add(closeButton).pad(10);
        optionsTable.pack(); // Needed to show the background.
        optionsTable.setPosition(stage.getWidth() / 2 - optionsTable.getWidth() / 2,
                stage.getHeight() / 2 - optionsTable.getHeight() / 2);
        stage.addActor(optionsTable);
    }

    private void updateTechButton() {
        techButton.setVisible(game.civInfo.cities.size() != 0);
        techButton.clearListeners();
        techButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new TechPickerScreen());
            }
        });

        if (game.civInfo.tech.currentTechnology() == null) techButton.setText("Choose a tech!");
        else techButton.setText(game.civInfo.tech.currentTechnology() + "\r\n"
                + game.civInfo.turnsToTech(game.civInfo.tech.currentTechnology()) + " turns");

        techButton.setSize(techButton.getPrefWidth(), techButton.getPrefHeight());
        techButton.setPosition(10, civTable.getY() - techButton.getHeight() - 5);
    }

    private void createNextTurnButton() {
        TextButton nextTurnButton = new TextButton("Next turn", skin);
        nextTurnButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (game.civInfo.tech.currentTechnology() == null
                        && game.civInfo.cities.size() != 0) {
                    game.setScreen(new TechPickerScreen());
                    return;
                }
                game.civInfo.nextTurn();
                tileMapHolder.unitTile = null;
                GameSaver.SaveGame(game, "Autosave");
                update();

                Linq<String> tutorial = new Linq<String>();
                tutorial.add("In your first couple of turns," +
                        "\r\n  you will have very little options," +
                        "\r\n  but as your civilization grows, so do the " +
                        "\r\n  number of things requiring your attention");
                displayTutorials("NextTurn",tutorial);

            }
        });
        nextTurnButton.setPosition(stage.getWidth() - nextTurnButton.getWidth() - 10,
                civTable.getY() - nextTurnButton.getHeight() - 10);
        stage.addActor(nextTurnButton);
    }

    @Override
    public void resize(int width, int height) {

        if(stage.getViewport().getScreenWidth()!=width || stage.getViewport().getScreenHeight()!=height) {
            super.resize(width, height);
            game.worldScreen = new WorldScreen(); // start over.
            game.setWorldScreen();
        }
    }
}
