package com.unciv.app.web;

import com.github.xpenatan.gdx.teavm.backends.web.TeaApplication;
import com.github.xpenatan.gdx.teavm.backends.web.TeaApplicationConfiguration;
import com.unciv.logic.files.PlatformSaverLoader;
import com.unciv.logic.files.UncivFiles;
import com.unciv.platform.PlatformCapabilities;
import com.unciv.platform.PlatformCapabilities.WebProfile;
import com.unciv.ui.components.fonts.Fonts;
import com.unciv.utils.Display;
import com.unciv.utils.Log;

public class WebLauncher {
    public static void main(String[] args) {
        boolean jsTestsMode = WebJsTestInterop.isEnabled();
        WebProfile profile = resolveWebProfile();
        PlatformCapabilities.setCurrent(PlatformCapabilities.webProfileFeatures(profile));
        PlatformCapabilities.setCurrentStaging(PlatformCapabilities.webProfileStaging(profile));
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

    private static WebProfile resolveWebProfile() {
        String raw = WebRuntimeInterop.getWebProfile();
        if(raw == null) return WebProfile.PHASE1;
        String normalized = raw.trim().toLowerCase();
        switch (normalized) {
            case "phase3-alpha":
            case "phase3_alpha":
            case "alpha":
                return WebProfile.PHASE3_ALPHA;
            case "phase3-beta":
            case "phase3_beta":
            case "beta":
                return WebProfile.PHASE3_BETA;
            case "phase3-full":
            case "phase3_full":
            case "full":
                return WebProfile.PHASE3_FULL;
            case "phase1":
            default:
                return WebProfile.PHASE1;
        }
    }
}
