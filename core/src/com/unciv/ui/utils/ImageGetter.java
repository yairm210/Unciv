package com.unciv.ui.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import java.util.HashMap;

public class ImageGetter {
    public static HashMap<String, TextureRegion> textureRegionByFileName = new HashMap<String, TextureRegion>();
    public static final String WhiteDot="skin/whiteDot.png";

    public static Image getImage(String fileName) {
        return new Image(getTextureRegion(fileName));
    }

    public static TextureRegionDrawable getDrawable(String fileName) {
        TextureRegionDrawable drawable =  new TextureRegionDrawable(getTextureRegion(fileName));
        drawable.setMinHeight(0);
        drawable.setMinWidth(0);
        return drawable;
    }

    public static Drawable getSingleColorDrawable(Color color){
        return getDrawable("skin/whiteDot.png").tint(color);
    }

    private static TextureRegion getTextureRegion(String fileName) {
        try {
            if (!textureRegionByFileName.containsKey(fileName))
                textureRegionByFileName.put(fileName, new TextureRegion(new Texture(Gdx.files.internal(fileName))));
        }catch (Exception ex){
            System.out.print("File "+fileName+" not found!");
        }
        return textureRegionByFileName.get(fileName);
    }

    public static Image getStatIcon(String name) {
        return getImage("StatIcons/20x" + name + "5.png");
    }

}
