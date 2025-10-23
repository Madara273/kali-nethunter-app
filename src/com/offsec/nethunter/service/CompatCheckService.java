package com.offsec.nethunter.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.Nullable;

import com.offsec.nethunter.BuildConfig;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.SharePrefTag;
import com.offsec.nethunter.utils.ShellExecuter;

public class CompatCheckService extends Service {
    public static final String TAG = "CompatCheckService";
    private SharedPreferences sharedPreferences;
    private HandlerThread workerThread;
    private Handler workerHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = getApplicationContext()
                .getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE);
        workerThread = new HandlerThread("CompatCheckServiceWorker",
                Process.THREAD_PRIORITY_BACKGROUND);
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        workerHandler.post(() -> {
            int resultCode = -1;
            if (intent != null) {
                resultCode = intent.getIntExtra("RESULTCODE", -1);
            }
            processCompatCheck(resultCode);
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    private void processCompatCheck(int resultCode) {
        ensureChrootPrefsAndSelinux();

        if (resultCode == -1) {
            boolean isChrootValid = new ShellExecuter().RunAsRootReturnValue(
                    NhPaths.APP_SCRIPTS_PATH + "/chrootmgr -c \"status\" -p " + NhPaths.CHROOT_PATH()) == 0;
            broadcastChrootStatus(isChrootValid);
        } else {
            broadcastChrootStatus(resultCode == 0);
        }

        if (!compatCondition()) {
            getApplicationContext().sendBroadcast(new Intent()
                    .putExtra("message", "")
                    .setAction(BuildConfig.APPLICATION_ID + ".CHECKCOMPAT")
                    .setPackage(BuildConfig.APPLICATION_ID));
        }
    }

    // Preserved original semantic: always returns true now; adapt if logic added later
    private boolean compatCondition() {
        return true;
    }

    private void ensureChrootPrefsAndSelinux() {
        if (!sharedPreferences.contains("SElinux")) {
            new ShellExecuter().RunAsRootOutput("[ ! \"$(getenforce | grep Permissive)\" ] && setenforce 0");
        }

        if (sharedPreferences.getString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, null) == null) {
            String output = new ShellExecuter().RunAsRootOutput(
                    NhPaths.APP_SCRIPTS_PATH + "/chrootmgr -c \"findchroot\"");
            String[] chrootDirs = output.split("\\n");
            String arch = (chrootDirs.length > 0 && !chrootDirs[0].isEmpty()) ? chrootDirs[0] : "kali-arm64";
            sharedPreferences.edit().putString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, arch).apply();
            sharedPreferences.edit().putString(SharePrefTag.CHROOT_PATH_SHAREPREF_TAG,
                    NhPaths.NH_SYSTEM_PATH + "/" + arch).apply();
            new ShellExecuter().RunAsRootOutput("ln -sfn " +
                    NhPaths.NH_SYSTEM_PATH + "/" + arch + " " + NhPaths.CHROOT_SYMLINK_PATH);
        }
    }

    private void broadcastChrootStatus(boolean enableFragment) {
        getApplicationContext().sendBroadcast(new Intent()
                .putExtra("ENABLEFRAGMENT", enableFragment)
                .setAction(BuildConfig.APPLICATION_ID + ".CHECKCHROOT")
                .setPackage(getPackageName()));
    }
}