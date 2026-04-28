package org.telegram.messenger;

import android.content.Context;

public class MentholFeaturesConfig {
    private static final String PREFS = "menthol_features";
    private static final String KEY_ROUND_CAMERA_FRONT = "roundCameraFront";

    public static boolean isRoundCameraFront() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ROUND_CAMERA_FRONT, false);
    }

    public static void setRoundCameraFront(boolean front) {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ROUND_CAMERA_FRONT, front).apply();
    }
}
