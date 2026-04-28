package org.telegram.messenger;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import go.Seq;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

public class MentholVpnCore {
    private static boolean initialized;
    private static CoreController coreController;

    public static synchronized void init(Context context) throws Exception {
        if (initialized) {
            return;
        }
        Seq.setContext(context.getApplicationContext());
        File assetsDir = new File(context.getFilesDir(), "xray_assets");
        if (!assetsDir.exists()) {
            assetsDir.mkdirs();
        }
        copyAsset(context, "geoip.dat", assetsDir);
        copyAsset(context, "geosite.dat", assetsDir);
        try {
            copyAsset(context, "geoip-only-cn-private.dat", assetsDir);
        } catch (Exception ignore) {
        }
        Libv2ray.initCoreEnv(assetsDir.getAbsolutePath(), getDeviceIdForXudpBaseKey(context));
        coreController = Libv2ray.newCoreController(new CoreCallback());
        initialized = true;
    }

    public static synchronized void start(Context context, String config, int tunFd) throws Exception {
        init(context);
        stop();
        coreController.startLoop(config, tunFd);
    }

    public static synchronized void stop() {
        if (coreController != null) {
            try {
                coreController.stopLoop();
            } catch (Exception ignore) {
            }
        }
    }

    public static long measureOutboundDelay(Context context, String config, String testUrl) {
        try {
            init(context);
            return Libv2ray.measureOutboundDelay(config, testUrl);
        } catch (Exception e) {
            FileLog.e(e);
            return -1;
        }
    }

    private static void copyAsset(Context context, String name, File dir) throws Exception {
        File file = new File(dir, name);
        if (file.exists() && file.length() > 0) {
            return;
        }
        try (InputStream inputStream = context.getAssets().open(name); FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
    }

    private static String getDeviceIdForXudpBaseKey(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null) {
                androidId = context.getPackageName();
            }
            byte[] source = androidId.getBytes(StandardCharsets.UTF_8);
            byte[] key = Arrays.copyOf(source, 32);
            return Base64.encodeToString(key, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception e) {
            byte[] key = Arrays.copyOf("ru.menthol.app".getBytes(StandardCharsets.UTF_8), 32);
            return Base64.encodeToString(key, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP);
        }
    }

    private static class CoreCallback implements CoreCallbackHandler {
        @Override
        public long startup() {
            return 0;
        }

        @Override
        public long shutdown() {
            return 0;
        }

        @Override
        public long onEmitStatus(long code, String message) {
            return 0;
        }
    }
}
