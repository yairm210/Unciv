package com.unciv.app.desktop;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.unciv.UnCivGame;

import java.io.File;

class DesktopLauncher {
	public static void main (String[] arg) {

		if (new File("../Images").exists()) { // So we don't run this from within a fat JAR
			TexturePacker.Settings settings = new TexturePacker.Settings();
			// Apparently some chipsets, like NVIDIA Tegra 3 graphics chipset (used in Asus TF700T tablet),
			// don't support non-power-of-two texture sizes - kudos @yuroller!
			// https://github.com/yairm210/UnCiv/issues/1340
			settings.maxWidth = 2048;
			settings.maxHeight = 2048;
			settings.combineSubdirectories = true;
			settings.pot = true;
			settings.fast = true;

			// This is so they don't look all pixelated
			settings.filterMag = Texture.TextureFilter.MipMapLinearLinear;
			settings.filterMin = Texture.TextureFilter.MipMapLinearLinear;
			TexturePacker.process(settings, "../Images", ".", "game");
		}

		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.addIcon("ExtraImages/Icon.png", Files.FileType.Internal);
		config.title="Unciv";
		new LwjglApplication(new UnCivGame("Desktop"), config);
	}
}
