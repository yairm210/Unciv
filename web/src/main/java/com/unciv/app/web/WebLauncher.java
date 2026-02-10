package com.unciv.app.web;

import com.github.xpenatan.gdx.teavm.backends.web.TeaApplication;
import com.github.xpenatan.gdx.teavm.backends.web.TeaApplicationConfiguration;
import com.unciv.logic.files.PlatformSaverLoader;
import com.unciv.logic.files.UncivFiles;
import com.unciv.platform.PlatformCapabilities;
import com.unciv.ui.components.fonts.Fonts;
import com.unciv.utils.Display;
import com.unciv.utils.Log;

public class WebLauncher {
    public static void main(String[] args) {
        boolean jsTestsMode = WebJsTestInterop.isEnabled();
        PlatformCapabilities.setCurrent(PlatformCapabilities.webPhase1());
        PlatformCapabilities.setCurrentStaging(PlatformCapabilities.webPhase3Staging());
        Display.INSTANCE.setPlatform(new WebDisplay());
        Fonts.INSTANCE.setFontImplementation(new WebFont());
        UncivFiles.Companion.setSaverLoader(PlatformSaverLoader.Companion.getNone());
        UncivFiles.Companion.setPreferExternalStorage(false);
        if(!jsTestsMode) {
            Log.INSTANCE.setBackend(new WebLogBackend());
        }

        TeaApplicationConfiguration config = new TeaApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.useGL30 = true;
        config.showDownloadLogs = true;

        if(jsTestsMode) {
            new TeaApplication(new WebJsTestsGame(), config);
            return;
        }
        new TeaApplication(new WebGame(), config);
    }
}
