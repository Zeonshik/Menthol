package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;

public class MentholTelegramProxyController {
    private static final String HOST = "127.0.0.1";

    public static void enableLocalSocks(int port) {
        SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(HOST, port, "", "", "");
        SharedConfig.currentProxy = SharedConfig.addProxy(info);
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", true);
        editor.putBoolean("proxy_enabled_calls", true);
        editor.putString("proxy_ip", HOST);
        editor.putInt("proxy_port", port);
        editor.putString("proxy_user", "");
        editor.putString("proxy_pass", "");
        editor.putString("proxy_secret", "");
        editor.apply();
        ConnectionsManager.setProxySettings(true, HOST, port, "", "", "");
        SharedConfig.saveProxyList();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    public static void disableLocalSocks() {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", false);
        editor.putBoolean("proxy_enabled_calls", false);
        editor.apply();
        ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }
}
