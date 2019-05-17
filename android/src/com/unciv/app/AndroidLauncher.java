package com.unciv.app;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.unciv.UnCivGame;
import core.java.nativefont.Handler;

public class AndroidLauncher extends AndroidApplication implements Handler {
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		String version = BuildConfig.VERSION_NAME;
		initialize(new UnCivGame(version), config);
	}
	@Override
	public void login() {
	}
}
