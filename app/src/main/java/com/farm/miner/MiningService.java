package com.farm.miner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import java.io.File;

public class MiningService extends Service {

    private static final String CHANNEL_ID = "VerumineServiceChannel";
    private Process minerProcess;
    private PowerManager.WakeLock wakeLock;

    private static final String POOL_URL = "stratum+tcp://ap.luckpool.net:3956";
    // ATTENTION : À MODIFIER MANUELLEMENT (Voir Partie 2)
    private static final String WALLET_ADDRESS = "RWZf58DwQ9R3QkdXVGtv2PGBYhc5qEfyxx"; 

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Verumine::MiningLock");
            wakeLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Verumine Node Active")
                .setContentText("Calculs VerusHash en cours...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
        startMining();
        
        // Lance le clone de secours
        startService(new Intent(this, ShadowWorker.class));

        return START_STICKY;
    }

    private void startMining() {
        if (minerProcess != null && minerProcess.isAlive()) return;

        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                String workerName = "Node_" + (deviceId != null ? deviceId.substring(0, Math.min(deviceId.length(), 6)) : "Android");

                File binaryFile = new File(getFilesDir(), "ccminer-arm64");
                // Le fichier est téléchargé et placé ici grâce à GitHub Actions + copie interne si besoin
                if (binaryFile.exists()) {
                    binaryFile.setExecutable(true, false);
                    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

                    ProcessBuilder pb = new ProcessBuilder(
                            binaryFile.getAbsolutePath(),
                            "-a", "verus",
                            "-o", POOL_URL,
                            "-u", WALLET_ADDRESS + "." + workerName,
                            "-p", "x",
                            "-t", String.valueOf(threads)
                    );
                    pb.directory(getFilesDir());
                    minerProcess = pb.start();
                    minerProcess.waitFor();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        if (minerProcess != null) minerProcess.destroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Background Services", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}