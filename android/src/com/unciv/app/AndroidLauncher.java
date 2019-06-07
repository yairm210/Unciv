package com.unciv.app;

import android.content.Intent;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.unciv.UnCivGame;

import static android.content.Intent.ACTION_VIEW;

public class AndroidLauncher extends AndroidApplication {
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		String version = BuildConfig.VERSION_NAME;


		Intent openInActionView = new Intent(ACTION_VIEW);

		initialize(new UnCivGame(version), config);
	}
}
