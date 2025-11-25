package com.offsec.nethunter.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.offsec.nethunter.utils.ShellExecuter;

import java.util.Map;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
            String modulesPath = prefs.getString("last_modulespath", "/system/lib/modules");
            Map<String, ?> all = prefs.getAll();
            ShellExecuter exe = new ShellExecuter();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                if (e.getKey().startsWith("autoload_") && Boolean.TRUE.equals(e.getValue())) {
                    String moduleName = e.getKey().substring("autoload_".length());
                    String fullPath = modulesPath + "/" + moduleName + ".ko";
                    exe.RunAsRootOutput("insmod " + fullPath);
                }
            }
        }
    }
}
