package com.offsec.nethunter.utils;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public class VulkanChecker {
    private static final String TAG = "VulkanChecker";

    public static boolean isVulkanSupported(Context context) {
        //Log.d(TAG, "Checking if Vulkan is supported...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager pm = context.getPackageManager();
            FeatureInfo[] features = pm.getSystemAvailableFeatures();
            for (FeatureInfo f : features) {
                if (f.name != null && f.name.equals(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
                    Log.d(TAG, "Vulkan supported, version: " + f.version);
                    return true;
                }
            }
        }
        Log.d(TAG, "Vulkan NOT supported");
        return false;
    }

    public static String getVulkanVersion(Context context) {
        Log.d(TAG, "Retrieving Vulkan version...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager pm = context.getPackageManager();
            FeatureInfo[] features = pm.getSystemAvailableFeatures();
            for (FeatureInfo f : features) {
                if (f.name != null && f.name.equals(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
                    String version = "Vulkan version: " + f.version;
                    Log.d(TAG, version);
                    return version;
                }
            }
        }
        Log.d(TAG, "Vulkan version: Not supported");
        return "Vulkan version: Not supported";
    }

    public static String getVulkanDetails(Context context) {
        Log.d(TAG, "Getting Vulkan details...");
        if (isVulkanSupported(context)) {
            String version = getVulkanVersion(context);
            Log.d(TAG, "Vulkan details: " + version);
            return version;
        }
        Log.d(TAG, "Vulkan is not supported on this device.");
        return "Vulkan is not supported on this device.";
    }
}