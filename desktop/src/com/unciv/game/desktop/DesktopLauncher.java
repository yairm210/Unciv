package com.unciv.game.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.unciv.UnCivGame;

class DesktopLauncher {
	public static void main (String[] arg) {

		TexturePacker.Settings settings = new TexturePacker.Settings();
		settings.maxWidth = 2048;
		settings.maxHeight = 2048;
		settings.combineSubdirectories=true;
		TexturePacker.process(settings, "images", "images", "game");

		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		new LwjglApplication(new UnCivGame(), config);
	}
}
