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
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MiningService extends Service {

    private static final String TAG =
            "VerumineMining";

    private static final String CHANNEL_ID =
            "VerumineServiceChannel";

    private static final int NOTIFICATION_ID = 1;

    private static final String POOL_URL =
            "stratum+tcp://ap.luckpool.net:3956";

    /*
     * IMPORTANT :
     *
     * Remets ici EXACTEMENT la même valeur que dans
     * ton MiningService actuel.
     */
    private static final String WALLET_ADDRESS =
            "REMETS_ICI_TON_ADRESSE_VERUS_EXISTANTE";

    private Process minerProcess;

    private PowerManager.WakeLock wakeLock;

    private final ExecutorService executor =
            Executors.newCachedThreadPool();

    private volatile boolean stopping = false;

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        acquireWakeLock();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        stopping = false;

        Notification notification =
                buildNotification(
                        "Analyse de l'architecture Android..."
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }

        startMining();

        return START_STICKY;
    }

    private void acquireWakeLock() {

        PowerManager powerManager =
                (PowerManager)
                        getSystemService(
                                Context.POWER_SERVICE
                        );

        if (powerManager != null) {

            wakeLock =
                    powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "Verumine::MiningLock"
                    );

            try {

                wakeLock.acquire();

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "WakeLock non acquis",
                        e
                );
            }
        }
    }

    private void startMining() {

        if (minerProcess != null) {

            try {

                minerProcess.exitValue();

                minerProcess = null;

            } catch (
                    IllegalThreadStateException
                            stillRunning
            ) {

                Log.i(
                        TAG,
                        "ccminer est déjà lancé."
                );

                return;
            }
        }

        executor.execute(() -> {

            MinerBinaryManager manager =
                    new MinerBinaryManager(
                            getApplicationContext()
                    );

            MinerBinaryManager.PreparedMiner preparedMiner =
                    null;

            try {

                if (
                        TextUtils.isEmpty(
                                WALLET_ADDRESS
                        )
                                || WALLET_ADDRESS.startsWith(
                                "REMETS_ICI_"
                        )
                ) {

                    throw new IllegalStateException(
                            "Adresse Verus non configurée "
                                    + "dans MiningService.java"
                    );
                }

                Log.i(
                        TAG,
                        "ABI primaire : "
                                + manager.getPrimaryAbi()
                );

                Log.i(
                        TAG,
                        "ABI disponibles : "
                                + manager.getSupportedAbis()
                );

                Log.i(
                        TAG,
                        "ABI lisible : "
                                + manager.getHumanReadableAbi()
                );

                updateNotification(
                        "ABI détectée : "
                                + manager.getHumanReadableAbi()
                );

                /*
                 * Sélection + extraction + SHA256
                 * + smoke test + fallback.
                 */
                preparedMiner =
                        manager.prepareBestMiner();

                File binary =
                        preparedMiner.getFile();

                if (
                        !binary.exists()
                                || binary.length() == 0
                ) {

                    throw new IllegalStateException(
                            "Mineur extrait invalide."
                    );
                }

                String deviceId =
                        Settings.Secure.getString(
                                getContentResolver(),
                                Settings.Secure.ANDROID_ID
                        );

                String suffix =
                        deviceId != null
                                ? deviceId.substring(
                                0,
                                Math.min(
                                        deviceId.length(),
                                        6
                                )
                        )
                                : "Android";

                String workerName =
                        "Node_" + suffix;

                int processors =
                        Math.max(
                                1,
                                Runtime.getRuntime()
                                        .availableProcessors()
                        );

                /*
                 * Réglage prudent :
                 *
                 * minimum 1 thread ;
                 * laisse au moins 1 cœur logique libre
                 * lorsque possible ;
                 * maximum 8 threads.
                 */
                int threads =
                        Math.min(
                                8,
                                Math.max(
                                        1,
                                        processors - 1
                                )
                        );

                Log.i(
                        TAG,
                        "======================================"
                );

                Log.i(
                        TAG,
                        "DÉMARRAGE CCMINER"
                );

                Log.i(
                        TAG,
                        "ABI      : "
                                + preparedMiner.getAbi()
                );

                Log.i(
                        TAG,
                        "Variant  : "
                                + preparedMiner.getVariant()
                );

                Log.i(
                        TAG,
                        "Binaire  : "
                                + binary.getAbsolutePath()
                );

                Log.i(
                        TAG,
                        "Pool     : "
                                + POOL_URL
                );

                Log.i(
                        TAG,
                        "Worker   : "
                                + workerName
                );

                Log.i(
                        TAG,
                        "Threads  : "
                                + threads
                );

                Log.i(
                        TAG,
                        "======================================"
                );

                ProcessBuilder processBuilder =
                        new ProcessBuilder(

                                binary.getAbsolutePath(),

                                "-a",
                                "verus",

                                "-o",
                                POOL_URL,

                                "-u",
                                WALLET_ADDRESS
                                        + "."
                                        + workerName,

                                "-p",
                                "x",

                                "-t",
                                String.valueOf(threads)
                        );

                processBuilder.directory(
                        getFilesDir()
                );

                processBuilder.redirectErrorStream(
                        true
                );

                minerProcess =
                        processBuilder.start();

                updateNotification(
                        "ccminer actif • "
                                + preparedMiner.getAbi()
                                + " • "
                                + preparedMiner.getVariant()
                );

                final Process currentProcess =
                        minerProcess;

                executor.execute(
                        () -> readMinerOutput(
                                currentProcess
                        )
                );

                int exitCode =
                        currentProcess.waitFor();

                if (!stopping) {

                    Log.w(
                            TAG,
                            "ccminer s'est arrêté. "
                                    + "Code retour : "
                                    + exitCode
                    );

                    updateNotification(
                            "ccminer arrêté • code "
                                    + exitCode
                    );
                }

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Impossible de démarrer le mineur",
                        e
                );

                String message =
                        e.getMessage();

                if (
                        message == null
                                || message.trim().isEmpty()
                ) {

                    message =
                            "Erreur inconnue";
                }

                updateNotification(
                        "Minage indisponible : "
                                + message
                );

            } finally {

                minerProcess = null;
            }
        });
    }

    private void readMinerOutput(
            Process process
    ) {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                Log.i(
                        TAG,
                        "ccminer: " + line
                );

                String lower =
                        line.toLowerCase();

                if (
                        lower.contains("stratum")
                                && (
                                lower.contains("connect")
                                        || lower.contains(
                                        "connected"
                                )
                        )
                ) {

                    updateNotification(
                            "Connecté à LuckPool"
                    );

                } else if (
                        lower.contains("accepted")
                                || lower.contains("share")
                ) {

                    updateNotification(
                            "Share reçu • minage actif"
                    );

                } else if (
                        lower.contains("error")
                                || lower.contains("failed")
                                || lower.contains("invalid")
                                || lower.contains(
                                "error while loading"
                        )
                ) {

                    updateNotification(
                            "Erreur ccminer • voir les logs"
                    );
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Erreur lecture sortie ccminer",
                    e
            );
        }
    }

    private Notification buildNotification(
            String text
    ) {

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle(
                        "Verumine — Minage Verus"
                )
                .setContentText(text)
                .setSmallIcon(
                        android.R.drawable.stat_sys_download
                )
                .setPriority(
                        NotificationCompat.PRIORITY_LOW
                )
                .setOngoing(true)
                .build();
    }

    private void updateNotification(
            String text
    ) {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NotificationManager.class
                        );

        if (manager != null) {

            manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(text)
            );
        }
    }

    @Override
    public void onDestroy() {

        stopping = true;

        Process process =
                minerProcess;

        if (process != null) {

            try {

                process.destroy();

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Erreur arrêt ccminer",
                        e
                );
            }

            minerProcess = null;
        }

        executor.shutdownNow();

        if (
                wakeLock != null
                        && wakeLock.isHeld()
        ) {

            try {

                wakeLock.release();

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Erreur libération WakeLock",
                        e
                );
            }
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }

    private void createNotificationChannel() {

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.O
        ) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Verumine Mining",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }
}
