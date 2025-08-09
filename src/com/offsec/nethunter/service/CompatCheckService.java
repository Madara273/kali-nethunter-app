package com.offsec.nethunter.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.offsec.nethunter.BuildConfig;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.SharePrefTag;
import com.offsec.nethunter.utils.ShellExecuter;

public class CompatCheckService extends IntentService {
    public static final String TAG = "CompatCheckService";
    private int RESULTCODE = -1;
    private SharedPreferences sharedPreferences;

    public CompatCheckService() {
        super("CompatCheckService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            RESULTCODE = intent.getIntExtra("RESULTCODE", -1);
        }

        if (!checkCompat()) {
            String message = "";
            getApplicationContext().sendBroadcast(new Intent()
                    .putExtra("message", message)
                    .setAction(BuildConfig.APPLICATION_ID + ".CHECKCOMPAT")
                    .setPackage(BuildConfig.APPLICATION_ID));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = getApplicationContext().getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private boolean checkCompat() {
        if (!sharedPreferences.contains("SElinux")) {
            new ShellExecuter().RunAsRootOutput("[ ! \"$(getenforce | grep Permissive)\" ] && setenforce 0");
        }

        if (sharedPreferences.getString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, null) == null) {
            String[] chrootDirs = new ShellExecuter().RunAsRootOutput(NhPaths.APP_SCRIPTS_PATH + "/chrootmgr -c \"findchroot\"").split("\\n");
            if (chrootDirs.length > 0 && !chrootDirs[0].isEmpty()) {
                sharedPreferences.edit().putString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, chrootDirs[0]).apply();
                sharedPreferences.edit().putString(SharePrefTag.CHROOT_PATH_SHAREPREF_TAG, NhPaths.NH_SYSTEM_PATH + "/" + chrootDirs[0]).apply();
                new ShellExecuter().RunAsRootOutput("ln -sfn " + NhPaths.NH_SYSTEM_PATH + "/" + chrootDirs[0] + " " + NhPaths.CHROOT_SYMLINK_PATH);
            } else {
                sharedPreferences.edit().putString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, "kali-arm64").apply();
                sharedPreferences.edit().putString(SharePrefTag.CHROOT_PATH_SHAREPREF_TAG, NhPaths.NH_SYSTEM_PATH + "/kali-arm64").apply();
                new ShellExecuter().RunAsRootOutput("ln -sfn " + NhPaths.NH_SYSTEM_PATH + "/kali-arm64 " + NhPaths.CHROOT_SYMLINK_PATH);
            }
        }

        if (RESULTCODE == -1) {
            if (new ShellExecuter().RunAsRootReturnValue(NhPaths.APP_SCRIPTS_PATH + "/chrootmgr -c \"status\" -p " + NhPaths.CHROOT_PATH()) != 0) {
                getApplicationContext().sendBroadcast(new Intent()
                        .putExtra("ENABLEFRAGMENT", false)
                        .setAction(BuildConfig.APPLICATION_ID + ".CHECKCHROOT"));
            } else {
                getApplicationContext().sendBroadcast(new Intent()
                        .putExtra("ENABLEFRAGMENT", true)
                        .setAction(BuildConfig.APPLICATION_ID + ".CHECKCHROOT"));
            }
        } else {
            if (RESULTCODE != 0) {
                getApplicationContext().sendBroadcast(new Intent()
                        .putExtra("ENABLEFRAGMENT", false)
                        .setAction(BuildConfig.APPLICATION_ID + ".CHECKCHROOT"));
            } else {
                getApplicationContext().sendBroadcast(new Intent()
                        .putExtra("ENABLEFRAGMENT", true)
                        .setAction(BuildConfig.APPLICATION_ID + ".CHECKCHROOT"));
            }
        }
        return true;
    }
}