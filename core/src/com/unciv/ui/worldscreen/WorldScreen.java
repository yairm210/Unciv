package com.unciv.ui.worldscreen;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.ui.GameInfo;
import com.unciv.ui.pickerscreens.PolicyPickerScreen;
import com.unciv.ui.pickerscreens.TechPickerScreen;
import com.unciv.ui.tilegroups.WorldTileGroup;
import com.unciv.ui.utils.CameraStageBaseScreen;
import com.unciv.ui.utils.GameSaver;

public class WorldScreen extends CameraStageBaseScreen {
    final CivilizationInfo civInfo;

    final public TileMapHolder tileMapHolder;

    float buttonScale = game.settings.buttonScale;
    final TileInfoTable tileInfoTable;
    final CivStatsTable civTable = new CivStatsTable();
    final TextButton techButton = new TextButton("", skin);
    final public LinqHashMap<String, WorldTileGroup> tileGroups = new LinqHashMap<String, WorldTileGroup>();

    final WorldScreenOptionsTable optionsTable;
    final NotificationsScroll notificationsScroll;
    final IdleUnitButton idleUnitButton = new IdleUnitButton(this);

    public WorldScreen() {
        GameInfo gameInfo = game.gameInfo;
        this.civInfo = gameInfo.getPlayerCivilization();
        tileMapHolder = new TileMapHolder(this, gameInfo.tileMap, civInfo);
        tileInfoTable = new TileInfoTable(this, civInfo);
        notificationsScroll =  new NotificationsScroll(gameInfo.notifications, this);
        optionsTable = new WorldScreenOptionsTable(this, civInfo);
        new Label("", skin).getStyle().font.getData().setScale(game.settings.labelScale);

        tileMapHolder.addTiles();
        stage.addActor(tileMapHolder);
        stage.addActor(tileInfoTable);
        stage.addActor(civTable);
        stage.addActor(techButton);
        stage.addActor(notificationsScroll);
        stage.addActor(idleUnitButton);
        update();

        tileMapHolder.setCenterPosition(Vector2.Zero);
        createNextTurnButton(); // needs civ table to be positioned
        stage.addActor(optionsTable);

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
        if(game.gameInfo.tutorial.contains("CityEntered")){
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
        if (civInfo.tech.freeTechs != 0) {
            game.setScreen(new TechPickerScreen(true, civInfo));
        }
        else if(civInfo.policies.shouldOpenPolicyPicker){
            game.setScreen(new PolicyPickerScreen(civInfo));
            civInfo.policies.shouldOpenPolicyPicker=false;
        }
    }

    private void updateTechButton() {
        techButton.setVisible(civInfo.cities.size() != 0);
        techButton.clearListeners();
        techButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new TechPickerScreen(civInfo));
            }
        });

        if (civInfo.tech.currentTechnology() == null) techButton.setText("Choose a tech!");
        else techButton.setText(civInfo.tech.currentTechnology() + "\r\n"
                + civInfo.turnsToTech(civInfo.tech.currentTechnology()) + " turns");

        techButton.setSize(techButton.getPrefWidth(), techButton.getPrefHeight());
        techButton.setPosition(10, civTable.getY() - techButton.getHeight() - 5);
    }

    private void createNextTurnButton() {
        TextButton nextTurnButton = new TextButton("Next turn", skin);
        nextTurnButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (civInfo.tech.currentTechnology() == null
                        && civInfo.cities.size() != 0) {
                    game.setScreen(new TechPickerScreen(civInfo));
                    return;
                }
                game.gameInfo.nextTurn();
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

