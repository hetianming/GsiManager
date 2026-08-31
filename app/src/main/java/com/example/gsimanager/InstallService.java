package com.example.gsimanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class InstallService extends Service {

    public static final String ACTION_START = "com.example.gsimanager.action.START";
    public static final String ACTION_STOP = "com.example.gsimanager.action.STOP";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_SIZE = "size";
    public static final String EXTRA_NAME = "name";

    public static volatile String currentUrl;

    private static final String CHANNEL_ID = "gsi_install";
    private static final int NOTIF_ID = 1;

    private LocalServer server;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GsiManager:install");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            doStop();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildNotification());
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(2 * 60 * 60 * 1000L);
        }

        if (intent != null) {
            String uriStr = intent.getStringExtra(EXTRA_URI);
            long size = intent.getLongExtra(EXTRA_SIZE, -1);
            String name = intent.getStringExtra(EXTRA_NAME);
            if (uriStr != null) {
                try {
                    if (server != null) {
                        server.stop();
                    }
                    String safeName = (name == null || name.trim().isEmpty()) ? "gsi.zip" : name;
                    safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
                    server = new LocalServer(getContentResolver(), Uri.parse(uriStr), size, safeName);
                    server.start();
                    currentUrl = server.getUrl();
                } catch (Exception e) {
                    currentUrl = null;
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        doStop();
        super.onDestroy();
    }

    private void doStop() {
        currentUrl = null;
        if (server != null) {
            server.stop();
            server = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "GSI 安装", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持本地文件服务运行，供系统 DSU 下载");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, InstallService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle("正在通过系统 DSU 安装 GSI")
                .setContentText("本地文件服务运行中，请保持应用在后台")
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(0, "停止", stopPi)
                .build();
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, InstallService.class));
    }
}
