package com.offsec.nethunter.Executor;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.offsec.nethunter.AppNavHomeActivity;
import com.offsec.nethunter.BuildConfig;
import com.offsec.nethunter.utils.CheckForRoot;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.SharePrefTag;
import com.offsec.nethunter.utils.ShellExecuter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executor to copy boot files from assets to the appropriate directories.
 * This class handles the copying of files, checking for root permissions,
 * and updating preferences.
 * <p>
 * TODO:
 * - Decrease debug and add it to a dynamic switch for 'enable' and 'disable' + log levels
 * <p>
 * This class is part of the NetHunter project, which is an Android-based penetration testing platform.
 * It is designed to run on rooted devices and provides various tools and scripts for security testing.
 * * This class is responsible for copying necessary boot files from the application's assets to the device's
 * storage, ensuring that the files are correctly placed and have the appropriate permissions.
 * It also handles symlinking scripts to the system's bin directory for easy access.
 * * The class uses a background thread to perform the file operations to avoid blocking the UI thread.
 * It provides a listener interface to notify when the operations are complete.
 * * Note: This class requires root permissions to function correctly, as it performs operations that
 * require elevated privileges, such as modifying system files and directories.
 * * Usage:
 * To use this class, create an instance of `CopyBootFilesExecutor` and call the `execute()` method.
 * The class will handle the copying of files and notify the listener when the operation is complete.
 * * Example:
 * CopyBootFilesExecutor executor = new CopyBootFilesExecutor(context, activity, progressDialog);
 * executor.setListener(new CopyBootFilesExecutor.CopyBootFilesExecutorListener() {
 *     @Override
 *     public void onExecutorPrepare() {
 *     // Prepare for execution, e.g., show a progress dialog
 *     }
 *     @Override
 *     public void onExecutorFinished(Object result) {
 *     // Handle the result of the execution, e.g., dismiss the progress dialog
 *     }
 *    });
 * executor.execute();
 *
 *
 */

public class CopyBootFilesExecutor {
    public static final String TAG = "CopyBootFilesExecutor";
    private String objects = "";
    private String tag = TAG;

    private void ensureNhFilesOnSdcard() {
        File nhFilesDir = new File(NhPaths.SD_PATH, "nh_files");
        if (!nhFilesDir.exists() || !nhFilesDir.isDirectory()) {
            copyAssetFolder("nh_files", NhPaths.SD_PATH + "/nh_files");
            logDebug(TAG, "\"nh_files\" directory copied to: " + nhFilesDir.getAbsolutePath());
        } else {
            logDebug(TAG, "\"nh_files\" already exists at: " + nhFilesDir.getAbsolutePath());
        }
    }

    private void fixPermissions() {
        exe.RunAsRoot(new String[] {
                "find " + NhPaths.APP_SCRIPTS_PATH + " " + NhPaths.APP_INITD_PATH + " -type f -exec chmod 700 {} \\;"
        });
        logDebug(TAG, "Permissions fixed for scripts and init.d files.");
    }

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private String lastMessage = "";
    private final Runnable progressRunnable = () -> logDebug(TAG, "Progress: " + lastMessage);
    private final String buildTime;
    private Boolean shouldRun;
    private final Activity activity;
    private final WeakReference<ProgressDialog> progressDialogRef;
    private CopyBootFilesExecutorListener listener;
    private static final String result = "";
    private final SharedPreferences prefs;
    private final ShellExecuter exe = new ShellExecuter();
    private final WeakReference<Context> context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AssetManager assetManager;
    // Debug logging: 0=off, 1=on
    public final int NH_SYSTEM_LOGGING = 0;

    // Logging wrapper attached to 'NH_SYSTEM_LOGGING' variable ("1" = enabled, "0" = disabled)
    // Howto add logging around this wrapper with the switch;
    //   logDebug(TAG, "Your debug message here");
    //
    private void logDebug(String message, Throwable throwable) {
        switch (NH_SYSTEM_LOGGING) {
            case 1: // Logging enabled (low)
                if (throwable != null) {
                    logDebug(CopyBootFilesExecutor.TAG, message, throwable);
                } else {
                    logDebug(CopyBootFilesExecutor.TAG, message);
                }
                break;
            case 0: // Logging disabled
            default:
                // Do nothing
                break;
        }
    }

    private void logDebug(String tag, String message, Throwable throwable) {
        if (throwable != null) {
            Log.d(tag, message, throwable);
        } else {
            Log.d(tag, message);
        }
        logToast(message); // Show toast for debug messages
    }

    private void logToast(String message) {
        Toast.makeText(requireActivity().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private Context requireActivity() {
        Context ctx = context.get();
        if (ctx == null) {
            throw new IllegalStateException("Context is not available");
        }
        return ctx;
    }

    public CopyBootFilesExecutor(Context context, Activity activity, ProgressDialog progressDialog) {
        this.context = new WeakReference<>(context);
        this.activity = activity;
        this.progressDialogRef = new WeakReference<>(progressDialog);
        this.assetManager = context.getAssets();
        File sdCardDir = new File(NhPaths.APP_SD_FILES_PATH);
        File scriptsDir = new File(NhPaths.APP_SCRIPTS_PATH);
        File etcDir = new File(NhPaths.APP_INITD_PATH);
        this.prefs = context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd KK:mm:ss a zzz", Locale.getDefault());
        this.buildTime = sdf.format(BuildConfig.BUILD_TIME);
        this.shouldRun = true;
    }

    public void execute() {
        mainHandler.post(this::onPreExecute);
        executor.execute(() -> {
            String res = doInBackground();
            mainHandler.post(() -> onPostExecute(res));
        });
    }

    private void onPreExecute() {
        boolean filesCopied = prefs.getBoolean("files_copied", false);
        if (!filesCopied) {
            logDebug(TAG, "COPYING NEW FILES", null);
            ProgressDialog progressDialog = progressDialogRef.get();
            if (progressDialog != null) {
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setTitle("New app build detected:");
                progressDialog.setMessage("Copying new files...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            }
        } else {
            logDebug(TAG, "NO NEW FILES TO COPY. Skipping file copy.", null);
            shouldRun = false;
        }
        if (listener != null) {
            listener.onExecutorPrepare();
        }
    }

    private void logDebug(String s) {
        this.tag = CopyBootFilesExecutor.TAG;
        logDebug(s, (Throwable) null);
    }

    private String doInBackground() {
        if (!shouldRun) {
            return result;
        }
        if (!CheckForRoot.isRoot()) {
            prefs.edit().putBoolean(AppNavHomeActivity.CHROOT_INSTALLED_TAG, false).apply();
            return "Root permission is required!!";
        }
        if (prefs.getBoolean("files_copied", false)) {
            File nhFilesDir = new File(NhPaths.SD_PATH, "nh_files");
            if (nhFilesDir.exists() && nhFilesDir.isDirectory()) {
                logDebug("Files already copied, skipping copy operation.");
                return result;
            } else {
                ensureNhFilesOnSdcard();
            }
        } else {
            logDebug("Proceeding with copy and symlink operations.");
        }

        logDebug("COPYING FILES....");
        publishProgress("Copying scripts and updating app files...");
        copyAssetFolder("etc/init.d", NhPaths.APP_INITD_PATH);
        copyAssetFolder("scripts", NhPaths.APP_SCRIPTS_PATH);
        copyAssetFolder("nh_files", NhPaths.APP_NHFILES_PATH);
        ensureNhFilesOnSdcard();

        publishProgress("Fixing permissions for new files");
        setPermissions(NhPaths.APP_SCRIPTS_PATH, NhPaths.APP_INITD_PATH);

        publishProgress("Checking for encrypted /data....");
        CheckEncrypted();

        publishProgress("Checking for bootkali symlinks....");
        SymlinkScriptsToSystemBin();
        Symlink("bootkali");
        Symlink("bootkali_bash");
        Symlink("bootkali_init");
        Symlink("bootkali_login");
        Symlink("killkali");
        Symlink("busybox_nh");
        disableMagiskNotification();

        prefs.edit()
                .putBoolean("files_copied", true)
                .putString(TAG, buildTime)
                .putInt(SharePrefTag.VERSION_CODE_TAG, BuildConfig.VERSION_CODE)
                .apply();

        publishProgress("Checking for chroot....");
        String command = "if [ -d " + NhPaths.CHROOT_PATH() + " ];then echo 1; fi";
        if ("1".equals(exe.RunAsRootOutput(command))) {
            prefs.edit().putBoolean(AppNavHomeActivity.CHROOT_INSTALLED_TAG, true).apply();
            publishProgress("Chroot Found!");
            publishProgress(exe.RunAsRootOutput(NhPaths.BUSYBOX + " mount -o remount,suid /data && chmod +s " +
                    NhPaths.CHROOT_PATH() + "/usr/bin/sudo" +
                    " && echo \"Initial setup done!\""));
        } else {
            publishProgress("Chroot not Found, install it in Chroot Manager");
        }

        publishProgress("Installing additional apps....");
        installApks(NhPaths.APP_SD_FILES_PATH + "/cache/apk/");

        if (!checkStoragePermission()) {
            return "Permission required to manage external storage.";
        }

        File nhFilesDir = new File(NhPaths.SD_PATH, "nh_files");
        if (nhFilesDir.exists() && nhFilesDir.isDirectory()) {
            logDebug("\"nh_files\" successfully copied to: " + nhFilesDir.getAbsolutePath());
        } else {
            logDebug("\"nh_files\" directory does NOT exist at: " + nhFilesDir.getAbsolutePath());
            publishProgress("Failed to copy nh_files to SD card!");
        }
        return result;
    }

    private void setPermissions(String... paths) {
        for (String path : paths) {
            exe.RunAsRoot("find " + path + " -type f -exec chmod 700 {} \\;");
        }
    }

    private void installApks(String folderPath) {
        for (String apk : FetchFiles(folderPath)) {
            if (apk.endsWith(".apk")) {
                String apkPath = folderPath + "/" + apk;
                String command = String.format("mv %s /data/local/tmp/ && pm install /data/local/tmp/%s && rm -f /data/local/tmp/%s", apkPath, apk, apk);
                if (exe.RunAsRootReturnValue(command) != 0) {
                    logDebug("Failed to install APK: " + apkPath);
                }
            }
        }
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + context.get().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.get().startActivity(intent);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(context.get(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) context.get(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                return false;
            }
        }
        return true;
    }

    private void copyAssetFolder(String assetFolder, String destFolder) {
        try {
            copyAssetFolderRecursive(assetFolder, destFolder);
        } catch (IOException e) {
            logDebug("Error copying asset folder: " + assetFolder + " to " + destFolder, e);
        }
    }

    private void copyAssetFolderRecursive(String assetFolder, String destFolder) throws IOException {
        String[] assets = assetManager.list(assetFolder);
        if (assets == null || assets.length == 0) {
            copyAssetFile(assetFolder, destFolder);
        } else {
            File dir = new File(destFolder);
            if (!dir.exists() && !dir.mkdirs()) {
                logDebug("Failed to create directory: " + destFolder + ". Check storage permissions and path.");
                return;
            }
            for (String asset : assets) {
                String assetPath = assetFolder + "/" + asset;
                String destPath = destFolder + "/" + asset;
                copyAssetFolderRecursive(assetPath, destPath);
            }
        }
    }

    private void copyAssetFile(String assetFile, String destFile) {
        try {
            // Skip copying placeholder, replaceholder, or directory assets
            String[] children = assetManager.list(assetFile);
            if (assetFile.endsWith("/placeholder") || assetFile.equals("placeholder") ||
                    assetFile.endsWith("/replaceholder") || assetFile.equals("replaceholder") ||
                    (children != null && children.length > 0)) {
                logDebug("Skipping placeholder, replaceholder, or directory asset: " + assetFile);
                return;
            }

            // Use renameAssetIfneeded for the destination file name
            File outFile = new File(renameAssetIfneeded(destFile));
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logDebug("Failed to create parent directories for: " + outFile.getAbsolutePath());
                return;
            }

            // If file exists, try to delete it before overwriting
            if (outFile.exists() && !outFile.delete()) {
                logDebug("File is busy and cannot be overwritten: " + outFile.getAbsolutePath());
                return;
            }

            try (InputStream in = assetManager.open(assetFile);
                 FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                logDebug("Copied asset file: " + assetFile + " to " + outFile.getAbsolutePath());
            }

            // Set executable permissions for files in scripts/bin/
            if (destFile.contains("/scripts/bin/")) {
                boolean permissionsSet = outFile.setExecutable(true, true) &&
                        outFile.setReadable(true, true) &&
                        outFile.setWritable(true, true);

                logDebug(permissionsSet
                        ? "Set executable, readable, and writable permissions for: " + outFile.getAbsolutePath()
                        : "Failed to set permissions for: " + outFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logDebug("Error copying asset file: " + assetFile + " to " + destFile, e);
        } catch (SecurityException e) {
            logDebug("Security exception while copying asset file: " + assetFile + " to " + destFile, e);
        }
    }

    private void logDebug(String tag, String s) {
        Log.d(tag, s);
    }

    private void CheckEncrypted() {
        logDebug("Checking if /data is encrypted...");
        String encrypted = exe.RunAsRootOutput("getprop ro.crypto.state");
        logDebug("/data is " + encrypted);
        if (encrypted.equals("encrypted")) {
            logDebug("Fixing pam.d and inet in chroot");
            exe.RunAsRoot(new String[]{NhPaths.APP_SCRIPTS_PATH + "/bootkali custom_cmd sed -i \"s/pam_keyinit.so/pam_keyinit.so #/\" /etc/pam.d/*"});
            exe.RunAsRoot(new String[]{NhPaths.APP_SCRIPTS_PATH + "/bootkali custom_cmd echo 'APT::Sandbox::User \"root\";' > /etc/apt/apt.conf.d/01-android-nosandbox"});
            exe.RunAsRoot(new String[]{NhPaths.APP_SCRIPTS_PATH + "/bootkali custom_cmd groupadd -g 3003 aid_inet;usermod -G nogroup -g aid_inet _apt"});
        }
    }

    private void Symlink(String filename) {
        // Symlink "bootkali*" and "killkali"; copy "busybox_nh"
        if (!(filename.startsWith("bootkali") || filename.equals("killkali") || filename.equals("busybox_nh"))) {
            logDebug("Skipping symlink/copy for: " + filename);
            return;
        }
        File target = new File("/system/bin/" + filename);
        logDebug("Checking for " + filename + " presence....");
        if (target.exists()) return;

        if (filename.equals("busybox_nh")) {
            String sourcePath = NhPaths.APP_SCRIPTS_BIN_PATH + "/busybox_nh";
            String targetPath = "/system/bin/" + filename;
            logDebug("Copying " + sourcePath + " to " + targetPath);
            int result = exe.RunAsRootReturnValue(
                    "cp -p " + sourcePath + " " + targetPath +
                            " && chown root:root " + targetPath +
                            " && chmod 0755 " + targetPath
            );
            if (result != 0) {
                logDebug("Failed to copy: " + filename);
            }
        } else {
            String sourcePath = NhPaths.APP_SCRIPTS_PATH + "/" + filename;
            logDebug("command output: ln -s " + sourcePath + " /system/bin/" + filename);
            int result = exe.RunAsRootReturnValue("ln -s " + sourcePath + " /system/bin/" + filename);
            if (result != 0) {
                logDebug("Failed to create symlink for: " + filename);
            }
        }
    }

    private void SymlinkScriptsToSystemBin() {
        // Remount system partitions as read-write
        exe.RunAsRoot(new String[]{
                "mount -o remount,rw /",
                "mount -o remount,rw /system",
                "mount -o remount,rw /system/bin",
                "mount -o remount,rw /system/xbin"
        });

        File scriptsDir = new File(NhPaths.APP_SCRIPTS_PATH);
        File[] scripts = scriptsDir.listFiles();
        if (scripts != null) {
            for (File script : scripts) {
                if (script.isFile()) {
                    String scriptName = script.getName();
                    if (!(scriptName.startsWith("bootkali") || scriptName.equals("killkali"))) {
                        logDebug("Skipping symlink for: " + scriptName);
                        continue;
                    }
                    String targetPath = "/system/bin/" + scriptName;
                    String sourcePath = script.getAbsolutePath();

                    // Check if /system is read-only before attempting to modify
                    String mountInfo = exe.RunAsRootOutput("mount | grep ' /system '");
                    if (mountInfo.contains("ro,")) {
                        logDebug("/system is mounted read-only. Cannot create symlink for: " + scriptName);
                        continue;
                    }

                    // Check if the symlink already exists and points to the correct source
                    String linkCheck = exe.RunAsRootOutput("ls -l " + targetPath + " | grep '" + sourcePath + "'");
                    if (linkCheck.contains(sourcePath)) {
                        logDebug("Symlink already exists for: " + scriptName);
                        continue;
                    }

                    // Try to remove the target if it exists
                    int rmResult = exe.RunAsRootReturnValue("rm -f " + targetPath);
                    if (rmResult != 0) {
                        logDebug("Failed to remove existing file at " + targetPath + ". rmResult=" + rmResult);
                        continue;
                    }

                    // Try to create the symlink
                    int lnResult = exe.RunAsRootReturnValue("ln -s " + sourcePath + " " + targetPath);
                    if (lnResult == 0) {
                        logDebug("Symlinked " + sourcePath + " to " + targetPath);
                    } else {
                        logDebug("Failed to symlink " + sourcePath + " to " + targetPath + ". lnResult=" + lnResult);
                    }
                }
            }
        }
    }

    // This rename the filename which suffix is either [name]-arm64 or [name]-armhf to [name] according to the user's CPU ABI.
    private String renameAssetIfneeded(String asset) {
        String cpuAbi;
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            cpuAbi = Build.SUPPORTED_ABIS[0];
        } else {
            cpuAbi = Build.CPU_ABI;
        }

        if (asset.matches("^.*-arm64$")) {
            if (cpuAbi.equals("arm64-v8a")) {
                return asset.replaceAll("-arm64$", "");
            }
        } else if (asset.matches("^.*-armeabi$") && !cpuAbi.equals("arm64-v8a")) {
            return asset.replaceAll("-armeabi$", "");
        }

        return asset;
    }

    // Get a list of files from a directory
    private ArrayList<String> FetchFiles(String folder) {
        logDebug("Fetching files from " + folder);
        return new ArrayList<>();
    }

    private void publishProgress(String message) {
        lastMessage = message;
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.postDelayed(progressRunnable, 500); // Debounce updates
    }

    private void onPostExecute(String objects) {
        this.objects = objects;
        ProgressDialog progressDialog = progressDialogRef.get();
        if (progressDialog != null) {
            progressDialog.dismiss();
        }

        if (listener != null) {
            listener.onExecutorFinished(result);
        }
    }

    public void setListener(CopyBootFilesExecutorListener listener) {
        this.listener = listener;
    }

    public String getObjects() {
        return objects;
    }

    public void setObjects(String objects) {
        this.objects = objects;
    }

    public Activity getActivity() {
        return activity;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public interface CopyBootFilesExecutorListener {
        void onExecutorPrepare();
        void onExecutorFinished(Object result);
    }

    private void disableMagiskNotification() {
        if (exe.RunAsRootReturnValue("[ -f " + NhPaths.MAGISK_DB_PATH + " ]") == 0) {
            logDebug(TAG, "Disabling Magisk notification and log for nethunter app.");
            if (exe.RunAsRootOutput(NhPaths.APP_SCRIPTS_BIN_PATH +
                    "/sqlite3 " + NhPaths.MAGISK_DB_PATH + " \"SELECT * from policies\" | grep " +
                    BuildConfig.APPLICATION_ID).startsWith(BuildConfig.APPLICATION_ID)) {
                exe.RunAsRootOutput(NhPaths.APP_SCRIPTS_BIN_PATH +
                        "/sqlite3 " + NhPaths.MAGISK_DB_PATH + " \"UPDATE policies SET logging='0',notification='0' WHERE package_name='" +
                        BuildConfig.APPLICATION_ID + "';\"");
                logDebug(TAG, "Updated magisk db successfully.");
            } else {
                exe.RunAsRootOutput(NhPaths.APP_SCRIPTS_BIN_PATH + "/sqlite3 " +
                        NhPaths.MAGISK_DB_PATH + " \"UPDATE policies SET logging='0',notification='0' WHERE uid='$(stat -c %u /data/data/" +
                        BuildConfig.APPLICATION_ID + ")';\"");
            }
        }
    }
}
