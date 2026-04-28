package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.app.Service;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MentholVpnSubscriptionStore;

public class MentholVpnService extends Service {
    public static final String ACTION_START = "org.telegram.messenger.MENTHOL_VPN_START";
    public static final String ACTION_STOP = "org.telegram.messenger.MENTHOL_VPN_STOP";
    public static final String EXTRA_CONFIG = "config";
    private static final int FOREGROUND_ID = 12007;
    private static final String CHANNEL_ID = "menthol_vpn";
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        String config = intent == null ? null : intent.getStringExtra(EXTRA_CONFIG);
        if (config == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(FOREGROUND_ID, buildNotification());
        try {
            MentholVpnCore.start(this, config, 0);
            MentholTelegramProxyController.enableLocalSocks(org.telegram.ui.MentholVpnConfigBuilder.SOCKS_PORT);
            MentholVpnSubscriptionStore.setRunning(true);
            MentholVpnAutostart.startWatchdog();
        } catch (Exception e) {
            FileLog.e(e);
            stopVpn();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Menthol VPN", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
        Intent launchIntent = new Intent(this, LaunchActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle("Menthol VPN")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setColor(0xff2ca5e0)
                .setColorized(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void stopVpn() {
        MentholTelegramProxyController.disableLocalSocks();
        MentholVpnCore.stop();
        MentholVpnSubscriptionStore.setRunning(false);
        stopForeground(true);
        stopSelf();
    }
}
