package com.unciv.ui;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.unciv.models.stats.FullStats;
import com.unciv.ui.utils.CameraStageBaseScreen;
import com.unciv.ui.utils.ImageGetter;

import java.util.HashMap;

public class YieldGroup extends HorizontalGroup {

    public void setStats(FullStats stats) {
        clearChildren();
        HashMap<String,Integer> dict = stats.toDict();
        for(String key : dict.keySet()){
            int value = dict.get(key);
            if(value >0) addActor(getStatTable(key,value));
        }
        pack();
    }

    Table getStatTable(String statName, int number){
        Table table = new Table();
        if(number==1){
            table.add(ImageGetter.getStatIcon(statName));
        }
        else if(number==2){
            table.add(ImageGetter.getStatIcon(statName)).row();
            table.add(ImageGetter.getStatIcon(statName));
        }
        else if(number==3){
            table.add(ImageGetter.getStatIcon(statName)).colspan(2).row();
            table.add(ImageGetter.getStatIcon(statName));
            table.add(ImageGetter.getStatIcon(statName));
        }
        else if(number==4){
            table.add(ImageGetter.getStatIcon(statName));
            table.add(ImageGetter.getStatIcon(statName)).row();
            table.add(ImageGetter.getStatIcon(statName));
            table.add(ImageGetter.getStatIcon(statName));
        }
        else{
            Image largeImage = ImageGetter.getStatIcon(statName);
            table.add(largeImage).size(largeImage.getWidth()*1.5f, largeImage.getHeight()*1.5f);
        }
        table.pack();
        return table;
    }
}
