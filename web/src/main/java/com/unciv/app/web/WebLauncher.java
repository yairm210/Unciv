package com.unciv.app.web;

import com.github.xpenatan.gdx.teavm.backends.web.TeaApplication;
import com.github.xpenatan.gdx.teavm.backends.web.TeaApplicationConfiguration;
import com.unciv.logic.files.FileChooser;
import com.unciv.logic.files.PlatformSaverLoader;
import com.unciv.logic.files.UncivFiles;
import com.unciv.logic.files.WebFileChooser;
import com.unciv.logic.files.WebPlatformSaverLoader;
import com.unciv.platform.PlatformCapabilities;
import com.unciv.platform.PlatformCapabilities.WebProfile;
import com.unciv.ui.components.fonts.Fonts;
import com.unciv.utils.Display;
import com.unciv.utils.Log;

public class WebLauncher {
    public static void main(String[] args) {
        WebValidationInterop.publishBootProgress("launcher-main-entry");
        WebValidationInterop.appendBootstrapTrace("launcher:main", "WebLauncher.main entered");
        boolean jsTestsMode = WebJsTestInterop.isEnabled();
        WebProfile profile = resolveWebProfile();
        boolean disableAll = readBooleanOverride("webRollback");
        boolean disableMultiplayer = readBooleanOverride("webDisableMultiplayer");
        boolean disableFileChooser = readBooleanOverride("webDisableFileChooser");
        boolean disableMods = readBooleanOverride("webDisableMods") || readBooleanOverride("webDisableModDownloads");

        PlatformCapabilities.Features baseFeatures = PlatformCapabilities.webProfileFeatures(profile);
        PlatformCapabilities.Staging baseStaging = PlatformCapabilities.webProfileStaging(profile);
        PlatformCapabilities.Features features = PlatformCapabilities.applyWebFeatureRollbacks(
                baseFeatures, disableAll, disableMultiplayer, disableFileChooser, disableMods);
        PlatformCapabilities.Staging staging = PlatformCapabilities.applyWebStagingRollbacks(
                baseStaging, disableAll, disableMultiplayer, disableFileChooser, disableMods);
        PlatformCapabilities.setCurrent(features);
        PlatformCapabilities.setCurrentStaging(staging);
        Display.INSTANCE.setPlatform(new WebDisplay());
        Fonts.INSTANCE.setFontImplementation(new WebFont());
        boolean customFileChooser = PlatformCapabilities.current.getCustomFileChooser();
        if (customFileChooser) {
            UncivFiles.Companion.setSaverLoader(new WebPlatformSaverLoader());
            FileChooser.platformLoadDialog = (filter, listener) -> WebFileChooser.INSTANCE.openLoadDialog(filter, listener);
        } else {
            UncivFiles.Companion.setSaverLoader(PlatformSaverLoader.Companion.getNone());
            FileChooser.platformLoadDialog = null;
        }
        UncivFiles.Companion.setPreferExternalStorage(false);
        if(!jsTestsMode) {
            Log.INSTANCE.setBackend(new WebLogBackend());
            if (PlatformCapabilities.hasWebRollbacksApplied(disableAll, disableMultiplayer, disableFileChooser, disableMods)) {
                Log.INSTANCE.debug("Web capabilities rollback applied: "
                        + PlatformCapabilities.describeWebRollbacks(disableAll, disableMultiplayer, disableFileChooser, disableMods));
            }
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
        WebProfile parsed = PlatformCapabilities.profileFromLabel(raw);
        if(parsed != null) return parsed;
        return PlatformCapabilities.webDefaultsProfile();
    }

    private static boolean readBooleanOverride(String key) {
        String raw = WebRuntimeInterop.getRuntimeConfigValue(key);
        if(raw == null) return false;
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("on");
    }
}
