package com.unciv.ui.worldscreen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.map.TileInfo;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileImprovement;
import com.unciv.models.linq.Linq;
import com.unciv.models.stats.FullStats;
import com.unciv.ui.tilegroups.TileGroup;
import com.unciv.ui.pickerscreens.TechPickerScreen;
import com.unciv.ui.utils.ImageGetter;

import java.util.HashMap;

public class TileInfoTable extends Table {

    private final WorldScreen worldScreen;

    public TileInfoTable(WorldScreen worldScreen){
        this.worldScreen = worldScreen;
        Drawable tileTableBackground = ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(new Color(0x004085bf));
        tileTableBackground.setMinHeight(0);
        tileTableBackground.setMinWidth(0);
        setBackground(tileTableBackground);
    }

    void updateTileTable(final TileInfo selectedTile) {
        if (selectedTile == null) return;
        clearChildren();
        FullStats stats = selectedTile.getTileStats();
        pad(20);
        columnDefaults(0).padRight(10);

        Skin skin = worldScreen.skin;

        if (selectedTile.explored) {
            add(new Label(selectedTile.toString(), skin)).colspan(2);
            row();


            HashMap<String, Integer> TileStatsValues = stats.toDict();

            for (String key : TileStatsValues.keySet()) {
                if (TileStatsValues.get(key) == 0)
                    continue; // this tile gives nothing of this stat, so why even display it?
                add(ImageGetter.getStatIcon(key)).align(Align.right);
                add(new Label(TileStatsValues.get(key) + "", skin)).align(Align.left);
                row();
            }
        }


        if (selectedTile.unit != null) {
            TextButton moveUnitButton = new TextButton("Move to", skin);
            if (worldScreen.tileMapHolder.unitTile == selectedTile) moveUnitButton = new TextButton("Stop movement", skin);
            moveUnitButton.getLabel().setFontScale(worldScreen.buttonScale);
            if (selectedTile.unit.currentMovement == 0) {
                moveUnitButton.setColor(Color.GRAY);
                moveUnitButton.setTouchable(Touchable.disabled);
            }
            moveUnitButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (worldScreen.tileMapHolder.unitTile != null) {
                        worldScreen.tileMapHolder.unitTile = null;
                        worldScreen.update();
                        return;
                    }
                    worldScreen.tileMapHolder.unitTile = selectedTile;

                    // Set all tiles transparent except those in unit range
                    for (TileGroup TG : worldScreen.tileGroups.linqValues()) TG.setColor(0, 0, 0, 0.3f);
                    for (TileInfo tile : worldScreen.game.civInfo.tileMap.getDistanceToTilesWithinTurn(worldScreen.tileMapHolder.unitTile.position, worldScreen.tileMapHolder.unitTile.unit.currentMovement).keySet()) {
                        worldScreen.tileGroups.get(tile.position.toString()).setColor(Color.WHITE);
                    }

                    worldScreen.update();
                }
            });
            add(moveUnitButton).colspan(2)
                    .size(moveUnitButton.getWidth() * worldScreen.buttonScale, moveUnitButton.getHeight() * worldScreen.buttonScale);

            if (selectedTile.unit.name.equals("Settler")) {
                addUnitAction("Found City",
                        !worldScreen.game.civInfo.tileMap.getTilesInDistance(selectedTile.position, 2).any(new Predicate<TileInfo>() {
                            @Override
                            public boolean evaluate(TileInfo arg0) {
                                return arg0.isCityCenter();
                            }
                        }),
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                Linq<String> tutorial = new Linq<String>();
                                tutorial.add("You have founded a city!" +
                                        "\r\nCities are the lifeblood of your empire," +
                                        "\r\n  providing gold and science empire-wide," +
                                        "\r\n  which are displayed on the top bar.");
                                tutorial.add("Science is used to research technologies." +
                                        "\r\nYou can enter the technology screen by clicking" +
                                        "\r\n  on the button on the top-left, underneath the bar");
                                tutorial.add("You can click the city name to enter" +
                                        "\r\n  the city screen to assign population," +
                                        "\r\n  choose production, and see information on the city");

                                worldScreen.displayTutorials("CityFounded",tutorial);

                                worldScreen.game.civInfo.addCity(selectedTile.position);
                                if (worldScreen.tileMapHolder.unitTile == selectedTile)
                                    worldScreen.tileMapHolder.unitTile = null; // The settler was in the middle of moving and we then founded a city with it
                                selectedTile.unit = null; // Remove settler!
                                worldScreen.update();
                            }
                        });
            }

            if (selectedTile.unit.name.equals("Worker")) {
                String improvementButtonText = selectedTile.improvementInProgress == null ?
                        "Construct\r\nimprovement" : selectedTile.improvementInProgress + "\r\nin progress";
                addUnitAction(improvementButtonText, !selectedTile.isCityCenter() ||
                                GameBasics.TileImprovements.linqValues().any(new Predicate<TileImprovement>() {
                                    @Override
                                    public boolean evaluate(TileImprovement arg0) {
                                        return selectedTile.canBuildImprovement(arg0);
                                    }
                                })
                        , new ClickListener() {

                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                worldScreen.game.setScreen(new com.unciv.ui.pickerscreens.ImprovementPickerScreen(selectedTile));
                            }
                        });
                addUnitAction("automation".equals(selectedTile.unit.action) ? "Stop automation" : "Automate", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                if ("automation".equals(selectedTile.unit.action))
                                    selectedTile.unit.action = null;
                                else {
                                    selectedTile.unit.action = "automation";
                                    selectedTile.unit.doAutomatedAction(selectedTile);
                                }
                                worldScreen.update();
                            }
                        });
            }

            if (selectedTile.unit.name.equals("Great Scientist")) {
                addUnitAction("Discover Technology", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                worldScreen.game.civInfo.tech.freeTechs += 1;
                                selectedTile.unit = null;// destroy!
                                worldScreen.game.setScreen(new TechPickerScreen(true));
                            }
                        });
                addUnitAction("Construct Academy", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement = "Academy";
                                selectedTile.unit = null;// destroy!
                                worldScreen.update();
                            }
                        });
            }

            if (selectedTile.unit.name.equals("Great Artist")) {
                addUnitAction("Start Golden Age", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                worldScreen.game.civInfo.goldenAges.enterGoldenAge();
                                selectedTile.unit = null;// destroy!
                                worldScreen.update();
                            }
                        });
                addUnitAction("Construct Landmark", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement = "Landmark";
                                selectedTile.unit = null;// destroy!
                                worldScreen.update();
                            }
                        });
            }

            if (selectedTile.unit.name.equals("Great Engineer")) {
                addUnitAction("Hurry Wonder", selectedTile.isCityCenter() &&
                                selectedTile.getCity().cityConstructions.getCurrentConstruction() instanceof Building &&

                                ((Building) selectedTile.getCity().cityConstructions.getCurrentConstruction()).isWonder,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.getCity().cityConstructions.addConstruction(300 + (30 * selectedTile.getCity().population.population)); //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                                selectedTile.unit = null; // destroy!
                                worldScreen.update();
                            }
                        });
                addUnitAction("Construct Manufactory", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement = "Manufactory";
                                selectedTile.unit = null;// destroy!
                                worldScreen.update();
                            }
                        });
            }
            if (selectedTile.unit.name.equals("Great Merchant")) {
                addUnitAction("Conduct Trade Mission", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                worldScreen.game.civInfo.gold += 350; // + 50 * era_number - todo!
                                selectedTile.unit = null; // destroy!
                                worldScreen.update();
                            }
                        });
                addUnitAction("Construct Customs House", true,
                        new ClickListener() {
                            @Override
                            public void clicked(InputEvent event, float x, float y) {
                                selectedTile.improvement = "Customs House";
                                selectedTile.unit = null;// destroy!
                                worldScreen.update();
                            }
                        });
            }
        }

        pack();

        setPosition(worldScreen.stage.getWidth() - 10 - getWidth(), 10);
    }


    private void addUnitAction(String actionText, boolean canAct, ClickListener action) {
        TextButton actionButton = new TextButton(actionText, worldScreen.skin);
        actionButton.getLabel().setFontScale(worldScreen.buttonScale);
        actionButton.addListener(action);
        if (worldScreen.tileMapHolder.selectedTile.unit.currentMovement == 0 || !canAct) {
            actionButton.setColor(Color.GRAY);
            actionButton.setTouchable(Touchable.disabled);
        }

        row();
        add(actionButton).colspan(2)
                .size(actionButton.getWidth() * worldScreen.buttonScale, actionButton.getHeight() * worldScreen.buttonScale);

    }
}
