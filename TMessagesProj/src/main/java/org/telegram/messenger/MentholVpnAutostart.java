package org.telegram.messenger;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.MentholVpnBuiltinServers;
import org.telegram.ui.MentholVpnConfigBuilder;
import org.telegram.ui.MentholVpnConfigParser;
import org.telegram.ui.MentholVpnSubscriptionStore;

import java.util.ArrayList;

public class MentholVpnAutostart {
    private static final long RECONNECT_TIMEOUT_MS = 5000;
    private static final long WATCHDOG_INTERVAL_MS = 1000;
    private static boolean started;
    private static boolean watchdogStarted;
    private static boolean recoveryInProgress;
    private static long reconnectStartedAt;
    private static int recoveryServerIndex;

    public static void startDefault() {
        if (started || ApplicationLoader.applicationContext == null) {
            return;
        }
        started = true;
        startWatchdog();
        AndroidUtilities.runOnUIThread(() -> {
            if (isConnected()) {
                started = false;
                return;
            }
            tryServer(0);
        }, 5000);
    }

    private static void tryServer(int index) {
        if (isConnected()) {
            started = false;
            return;
        }
        java.util.ArrayList<org.telegram.ui.MentholVpnConfigParser.ParsedConfig> configs = MentholVpnBuiltinServers.getConfigs();
        if (index >= configs.size()) {
            android.widget.Toast.makeText(ApplicationLoader.applicationContext, "Ни один сервер недоступен, используйте сторонний :(", android.widget.Toast.LENGTH_LONG).show();
            started = false;
            return;
        }
        try {
            String raw = configs.get(index).raw;
            String config = MentholVpnConfigBuilder.build(raw);
            MentholVpnSubscriptionStore.setSelectedConfigRaw(raw);
            Context context = ApplicationLoader.applicationContext;
            Intent intent = new Intent(context, MentholVpnService.class);
            intent.setAction(MentholVpnService.ACTION_START);
            intent.putExtra(MentholVpnService.EXTRA_CONFIG, config);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
            android.widget.Toast.makeText(context, "Ищем стабильный сервер...", android.widget.Toast.LENGTH_SHORT).show();
            AndroidUtilities.runOnUIThread(() -> {
                if (isConnected()) {
                    started = false;
                } else {
                    context.startService(new Intent(context, MentholVpnService.class).setAction(MentholVpnService.ACTION_STOP));
                    tryServer(index + 1);
                }
            }, 10000);
        } catch (Exception e) {
            FileLog.e(e);
            tryServer(index + 1);
        }
    }

    public static void startWatchdog() {
        if (watchdogStarted || ApplicationLoader.applicationContext == null) {
            return;
        }
        watchdogStarted = true;
        AndroidUtilities.runOnUIThread(MentholVpnAutostart::checkConnection, WATCHDOG_INTERVAL_MS);
    }

    private static void checkConnection() {
        if (ApplicationLoader.applicationContext == null) {
            watchdogStarted = false;
            return;
        }
        if (!MentholVpnSubscriptionStore.isRunning()) {
            resetReconnectState();
            scheduleWatchdog();
            return;
        }
        if (isConnected()) {
            resetReconnectState();
            scheduleWatchdog();
            return;
        }
        if (!isReconnecting()) {
            reconnectStartedAt = 0;
            scheduleWatchdog();
            return;
        }
        long now = System.currentTimeMillis();
        if (reconnectStartedAt == 0) {
            reconnectStartedAt = now;
            scheduleWatchdog();
            return;
        }
        if (recoveryInProgress || now - reconnectStartedAt < RECONNECT_TIMEOUT_MS) {
            scheduleWatchdog();
            return;
        }
        recoverConnection();
        scheduleWatchdog();
    }

    private static void recoverConnection() {
        recoveryServerIndex = 0;
        recoveryInProgress = true;
        tryRecoveryServer(recoveryServerIndex);
    }

    private static void tryRecoveryServer(int index) {
        ArrayList<MentholVpnConfigParser.ParsedConfig> configs = getAvailableConfigs();
        if (index >= configs.size()) {
            resetReconnectState();
            return;
        }
        MentholVpnConfigParser.ParsedConfig config = configs.get(index);
        if (config.raw == null || config.raw.length() == 0) {
            tryRecoveryServer(index + 1);
            return;
        }
        MentholVpnSubscriptionStore.setSelectedConfigRaw(config.raw);
        restartRaw(config.raw);
        AndroidUtilities.runOnUIThread(() -> {
            if (isConnected() || !MentholVpnSubscriptionStore.isRunning()) {
                resetReconnectState();
                return;
            }
            recoveryServerIndex = index + 1;
            tryRecoveryServer(recoveryServerIndex);
        }, RECONNECT_TIMEOUT_MS);
    }

    private static ArrayList<MentholVpnConfigParser.ParsedConfig> getAvailableConfigs() {
        ArrayList<MentholVpnConfigParser.ParsedConfig> configs = MentholVpnBuiltinServers.getConfigs();
        ArrayList<MentholVpnSubscriptionStore.Subscription> subscriptions = MentholVpnSubscriptionStore.load();
        for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
            configs.addAll(subscription.configs);
        }
        return configs;
    }

    private static void restartRaw(String raw) {
        Context context = ApplicationLoader.applicationContext;
        context.startService(new Intent(context, MentholVpnService.class).setAction(MentholVpnService.ACTION_STOP));
        AndroidUtilities.runOnUIThread(() -> startRaw(raw), 350);
    }

    private static void startRaw(String raw) {
        try {
            String config = MentholVpnConfigBuilder.build(raw);
            Context context = ApplicationLoader.applicationContext;
            Intent intent = new Intent(context, MentholVpnService.class);
            intent.setAction(MentholVpnService.ACTION_START);
            intent.putExtra(MentholVpnService.EXTRA_CONFIG, config);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
            MentholVpnSubscriptionStore.setRunning(true);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void scheduleWatchdog() {
        AndroidUtilities.runOnUIThread(MentholVpnAutostart::checkConnection, WATCHDOG_INTERVAL_MS);
    }

    private static void resetReconnectState() {
        reconnectStartedAt = 0;
        recoveryInProgress = false;
        recoveryServerIndex = 0;
    }

    private static boolean isReconnecting() {
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnecting
                || state == ConnectionsManager.ConnectionStateConnectingToProxy
                || state == ConnectionsManager.ConnectionStateWaitingForNetwork;
    }

    private static boolean isConnected() {
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating;
    }
}
