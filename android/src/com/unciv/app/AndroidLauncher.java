package com.unciv.app;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.unciv.UncivGame;

// If we convert this to Kotlin, the the Gradle build won't work. =(
// Stuck with Java for now
public class AndroidLauncher extends AndroidApplication {
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		String version = BuildConfig.VERSION_NAME;

		config.useImmersiveMode=true;
		//config.useGL30 = true;

		initialize(new UncivGame(version), config);
	}
}
