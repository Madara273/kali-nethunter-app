package com.offsec.nethunter;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.Executor.ChrootManagerExecutor;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.service.CompatCheckService;
import com.offsec.nethunter.service.NotificationChannelService;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.SharePrefTag;
import com.offsec.nethunter.utils.ShellExecuter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class ChrootManagerFragment extends Fragment {
    public static final String TAG = "ChrootManager";
    private static final String ARG_SECTION_NUMBER = "section_number";
    public static final String PRIMARY_IMAGE_SERVER = "image-nethunter.kali.org";
    public static final String SECONDARY_IMAGE_SERVER = "kali.download";
    private static final String IMAGE_DIRECTORY = "/nethunter-images/current/rootfs/";
    private static final String INVALID_PATH_REGEX = "^\\.(.*$)|^\\.\\.(.*$)|^/+(.*$)|^.*/+(.*$)|^$";
    // Default, can be made dynamic if needed
    private static String ARCH = "arm64";
    private static String MINORFULL = "";
    private final Intent backPressedintent = new Intent();
    private TextView mountStatsTextView;
    private TextView baseChrootPathTextView;
    private TextView resultViewerLoggerTextView;
    private TextView kaliFolderTextView;
    private Button kaliFolderEditButton;
    private Button mountChrootButton;
    private Button unmountChrootButton;
    private Button installChrootButton;
    private Button addMetaPkgButton;
    private Button removeChrootButton;
    private Button backupChrootButton;
    private LinearLayout ChrootDesc;
    private static SharedPreferences sharedPreferences;
    private ChrootManagerExecutor chrootManagerExecutor;
    private static final int IS_MOUNTED = 0;
    private static final int IS_UNMOUNTED = 1;
    private static final int NEED_TO_INSTALL = 2;
    public static boolean isExecutorRunning = false;
    private Context context;
    private Activity activity;

    public static ChrootManagerFragment newInstance(int sectionNumber) {
        ChrootManagerFragment fragment = new ChrootManagerFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.chroot_manager, container, false);

        if (activity != null) {
            sharedPreferences = activity.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
        } else {
            throw new IllegalStateException("Activity is null. Cannot initialize sharedPreferences.");
        }

        baseChrootPathTextView = rootView.findViewById(R.id.f_chrootmanager_base_path_tv);
        if (baseChrootPathTextView == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_base_path_tv not found in layout.");
        }

        mountStatsTextView = rootView.findViewById(R.id.f_chrootmanager_mountresult_tv);
        if (mountStatsTextView == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_mountresult_tv not found in layout.");
        }

        resultViewerLoggerTextView = rootView.findViewById(R.id.f_chrootmanager_viewlogger);
        if (resultViewerLoggerTextView == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_viewlogger not found in layout.");
        }

        kaliFolderTextView = rootView.findViewById(R.id.f_chrootmanager_kalifolder_tv);
        if (kaliFolderTextView == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_kalifolder_tv not found in layout.");
        }

        kaliFolderEditButton = rootView.findViewById(R.id.f_chrootmanager_edit_btn);
        if (kaliFolderEditButton == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_edit_btn not found in layout.");
        }

        mountChrootButton = rootView.findViewById(R.id.f_chrootmanager_mount_btn);
        if (mountChrootButton == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_mount_btn not found in layout.");
        }

        unmountChrootButton = rootView.findViewById(R.id.f_chrootmanager_unmount_btn);
        if (unmountChrootButton == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_unmount_btn not found in layout.");
        }

        installChrootButton = rootView.findViewById(R.id.f_chrootmanager_install_btn);
        if (installChrootButton == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_install_btn not found in layout.");
        }

        addMetaPkgButton = rootView.findViewById(R.id.f_chrootmanager_addmetapkg_btn);
        if (addMetaPkgButton == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_addmetapkg_btn not found in layout.");
        }

        removeChrootButton = rootView.findViewById(R.id.f_chrootmanager_removechroot_btn);
        if (removeChrootButton == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_removechroot_btn not found in layout.");
        }

        backupChrootButton = rootView.findViewById(R.id.f_chrootmanager_backupchroot_btn);
        if (backupChrootButton == null) {
            throw new IllegalStateException("View with ID f_chrootmanager_backupchroot_btn not found in layout.");
        }

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        resultViewerLoggerTextView.setMovementMethod(new ScrollingMovementMethod());
        kaliFolderTextView.setClickable(true);
        if (sharedPreferences != null) {
            kaliFolderTextView.setText(sharedPreferences.getString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, NhPaths.ARCH_FOLDER));
        }
        final LinearLayoutCompat kaliViewFolderlinearLayout = view.findViewById(R.id.f_chrootmanager_viewholder);
        kaliViewFolderlinearLayout.setOnClickListener(view1 -> new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                .setMessage(baseChrootPathTextView.getText().toString() +
                        kaliFolderTextView.getText().toString())
                .create().show());
        setEditButton();
        setStopKaliButton();
        setStartKaliButton();
        setInstallChrootButton();
        setRemoveChrootButton();
        setAddMetaPkgButton();
        setBackupChrootButton();

        // WearOS optimisation
        if (activity != null) {
            SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
            Boolean iswatch = sharedpreferences.getBoolean("running_on_wearos", false);
            if (iswatch) {
                kaliViewFolderlinearLayout.setVisibility(View.GONE);
            }
        }

        // Register ActivityResultLauncher for file picking
        ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (context != null && fileUri != null) {
                            File outFile = new File(context.getFilesDir(), "restore.tar.xz");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try (InputStream in = context.getContentResolver().openInputStream(fileUri);
                                     OutputStream out = Files.newOutputStream(outFile.toPath())) {
                                    byte[] buffer = new byte[4096];
                                    int bytesRead;
                                    long totalBytes = 0;
                                    while (true) {
                                        assert in != null;
                                        if ((bytesRead = in.read(buffer)) == -1) break;
                                        out.write(buffer, 0, bytesRead);
                                        totalBytes += bytesRead;
                                    }
                                    out.flush();
                                    if (outFile.length() == 0 || totalBytes == 0) {
                                        NhPaths.showMessage(context, "Copied file is empty. Please select a valid backup.");
                                        return;
                                    }
                                    try (InputStream checkIn = new FileInputStream(outFile)) {
                                        byte[] magic = new byte[6];
                                        if (checkIn.read(magic) == 6) {
                                            if (!(magic[0] == (byte) 0xFD && magic[1] == '7' && magic[2] == 'z' && magic[3] == 'X' && magic[4] == 'Z' && magic[5] == 0x00)) {
                                                NhPaths.showMessage(context, "File does not appear to be a valid .xz archive.");
                                                return;
                                            }
                                        }
                                    }
                                    if (sharedPreferences != null) {
                                        sharedPreferences.edit().putString(SharePrefTag.CHROOT_DEFAULT_BACKUP_SHAREPREF_TAG, outFile.getAbsolutePath()).apply();
                                    }
                                    chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.INSTALL_CHROOT);
                                    chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
                                        @Override
                                        public void onExecutorPrepare() {
                                            if (context != null) {
                                                context.startService(new Intent(context, NotificationChannelService.class).setAction(NotificationChannelService.INSTALLING));
                                            }
                                            broadcastBackPressedIntent(false);
                                            setAllButtonEnable(false);
                                        }

                                        @Override
                                        public void onExecutorProgressUpdate(int progress) {
                                        }

                                        @Override
                                        public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                                            broadcastBackPressedIntent(true);
                                            setAllButtonEnable(true);
                                            compatCheck();
                                        }
                                    });
                                    resultViewerLoggerTextView.setText("");
                                    chrootManagerExecutor.execute(resultViewerLoggerTextView, outFile.getAbsolutePath(), NhPaths.CHROOT_PATH());
                                } catch (IOException e) {
                                    NhPaths.showMessage(context, "Failed to copy file: " + e.getMessage());
                                }
                            }
                        } else {
                            NhPaths.showMessage(context, "No file selected.");
                        }
                    }
                }
        );
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!isExecutorRunning){
            compatCheck();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mountStatsTextView = null;
        baseChrootPathTextView = null;
        resultViewerLoggerTextView = null;
        kaliFolderTextView = null;
        kaliFolderEditButton = null;
        mountChrootButton = null;
        unmountChrootButton = null;
        installChrootButton = null;
        addMetaPkgButton = null;
        removeChrootButton = null;
        backupChrootButton = null;
        chrootManagerExecutor = null;
    }

    private void setEditButton() {
        if (activity == null || sharedPreferences == null) {
            throw new IllegalStateException("Activity or SharedPreferences is null. Cannot proceed.");
        }

        kaliFolderEditButton.setOnClickListener(view -> {
            MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
            final AlertDialog ad = adb.create();
            LinearLayout ll = new LinearLayout(activity);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setLayoutParams(layoutParams);

            EditText chrootPathEditText = new EditText(activity);
            TextView availableChrootPathextview = new TextView(activity);
            LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            editTextParams.setMargins(58, 0, 58, 0);

            chrootPathEditText.setText(sharedPreferences.getString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, ""));
            chrootPathEditText.setSingleLine();
            chrootPathEditText.setLayoutParams(editTextParams);

            availableChrootPathextview.setLayoutParams(editTextParams);
            availableChrootPathextview.setTextColor(ContextCompat.getColor(activity, R.color.clearTitle));
            availableChrootPathextview.setText(String.format(getString(R.string.list_of_available_folders), NhPaths.NH_SYSTEM_PATH));

            File chrootDir = new File(NhPaths.NH_SYSTEM_PATH);
            int count = 0;
            File[] files = chrootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (file.getName().equals("kalifs")) continue;
                        count += 1;
                        availableChrootPathextview.append("    " + count + ". " + file.getName() + "\n");
                    }
                }
            } else {
                availableChrootPathextview.append("No directories found.");
            }

            ll.addView(chrootPathEditText);
            ll.addView(availableChrootPathextview);

            ad.setCancelable(true);
            ad.setTitle("Setup Chroot Path");
            ad.setMessage("The Chroot Path is prefixed to \n\"/data/local/nhsystem/\"\n\n" +
                    "Just put the basename of your Kali Chroot Folder:");
            ad.setView(ll);

            ad.setButton(DialogInterface.BUTTON_POSITIVE, "Apply", (dialogInterface, i) -> {
                if (chrootPathEditText.getText().toString().matches(INVALID_PATH_REGEX)) {
                    NhPaths.showMessage(activity, "Invalid Name, please try again.");
                } else {
                    NhPaths.ARCH_FOLDER = chrootPathEditText.getText().toString();
                    kaliFolderTextView.setText(NhPaths.ARCH_FOLDER);
                    sharedPreferences.edit().putString(SharePrefTag.CHROOT_ARCH_SHAREPREF_TAG, NhPaths.ARCH_FOLDER).apply();
                    sharedPreferences.edit().putString(SharePrefTag.CHROOT_PATH_SHAREPREF_TAG, NhPaths.CHROOT_PATH()).apply();
                    new ShellExecuter().RunAsRootOutput("ln -sfn " + NhPaths.CHROOT_PATH() + " " + NhPaths.CHROOT_SYMLINK_PATH);
                    compatCheck();
                }
                dialogInterface.dismiss();
            });

            ad.show();
        });
    }

    private void setStartKaliButton() {
        mountChrootButton.setOnClickListener(view -> {
            chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.MOUNT_CHROOT);
            chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
                @Override
                public void onExecutorPrepare() {
                    setAllButtonEnable(false);
                }

                @Override
                public void onExecutorProgressUpdate(int progress) {}

                @Override
                public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                    if (resultCode == 0){
                        setButtonVisibility(IS_MOUNTED);
                        setMountStatsTextView(IS_MOUNTED);
                        setAllButtonEnable(true);
                        compatCheck();
                        context.startService(new Intent(context, NotificationChannelService.class).setAction(NotificationChannelService.USENETHUNTER));
                    }
                }
            });
            resultViewerLoggerTextView.setText("");
            chrootManagerExecutor.execute(resultViewerLoggerTextView);
        });
    }

    private void setStopKaliButton(){
        unmountChrootButton.setOnClickListener(view -> {
            chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.UNMOUNT_CHROOT);
            chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
                @Override
                public void onExecutorPrepare() {
                    setAllButtonEnable(false);
                }

                @Override
                public void onExecutorProgressUpdate(int progress) {}

                @Override
                public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                    if (resultCode == 0){
                        setMountStatsTextView(IS_UNMOUNTED);
                        setButtonVisibility(IS_UNMOUNTED);
                        setAllButtonEnable(true);
                        compatCheck();
                    }
                }
            });
            resultViewerLoggerTextView.setText("");
            chrootManagerExecutor.execute(resultViewerLoggerTextView);
        });
    }

    private void setInstallChrootButton() {
        installChrootButton.setOnClickListener(view -> {
            String[] options = {"Minimal", "Full"};
            new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                    .setTitle("Select Kali Image")
                    .setItems(options, (dialog, which) -> {
                        String arch = "arm64"; // or detect automatically if needed
                        String type = (which == 0) ? "minimal" : "full";
                        String fileName = "kalifs-" + arch + "-" + type + ".tar.xz";
                        File downloadDir = context.getFilesDir();
                        File targetFile = new File(downloadDir, fileName);

                        Runnable startProcess = () -> startDownloadAndRestoreChroot(fileName, downloadDir, type, arch);

                        if (targetFile.exists()) {
                            new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                                    .setTitle("Overwrite File?")
                                    .setMessage("The image file already exists. Do you want to overwrite it?")
                                    .setPositiveButton("Overwrite", (d, w) -> startProcess.run())
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else {
                            startProcess.run();
                        }
                    })
                    .setCancelable(true)
                    .show();
        });
    }

    private void startDownloadAndRestoreChroot(String fileName, File downloadDir, String type, String arch) {
        chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.DOWNLOAD_CHROOT);
        chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
            @Override
            public void onExecutorPrepare() {
                setAllButtonEnable(false);
            }
            @Override
            public void onExecutorProgressUpdate(int progress) {}
            @Override
            public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                setAllButtonEnable(true);
                if (resultCode == 0) {
                    // After download, restore (unpack) the image
                    restoreChrootImage(new File(downloadDir, fileName).getAbsolutePath());
                } else {
                    NhPaths.showMessage(context, "Download failed.");
                }
            }
        });

        resultViewerLoggerTextView.setText("");
        String imagePath = "/nethunter-images/current/rootfs/" + fileName;
        chrootManagerExecutor.execute(
                resultViewerLoggerTextView,
                ChrootManagerFragment.PRIMARY_IMAGE_SERVER,
                imagePath,
                new File(downloadDir, fileName).getAbsolutePath()
        );
    }

    @NonNull
    public MaterialAlertDialogBuilder getMaterialAlertDialogBuilder(File downloadDir, String targetDownloadFileName) {
        MaterialAlertDialogBuilder adb3 = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
        adb3.setMessage(downloadDir.getAbsoluteFile() + "/" + targetDownloadFileName + " exists. Do you want to overwrite it?");
        adb3.setPositiveButton("YES", (dialogInterface1, i1) -> {
            context.startService(new Intent(context, NotificationChannelService.class).setAction(NotificationChannelService.DOWNLOADING));
            startDownloadChroot(targetDownloadFileName, downloadDir);
        });
        adb3.setNegativeButton("NO", (dialogInterface12, i12) -> dialogInterface12.dismiss());
        return adb3;
    }

    private void restoreChrootImage(String imagePath) {
        chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.INSTALL_CHROOT);
        chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
            @Override
            public void onExecutorPrepare() {
                setAllButtonEnable(false);
            }
            @Override
            public void onExecutorProgressUpdate(int progress) {}
            @Override
            public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                setAllButtonEnable(true);
                if (resultCode == 0) {
                    NhPaths.showMessage(context, "Chroot image restored successfully.");
                    compatCheck();
                } else {
                    NhPaths.showMessage(context, "Failed to restore chroot image.");
                }
            }
        });

        resultViewerLoggerTextView.setText("");
        // Assuming NhPaths.CHROOT_PATH() returns the target extraction directory
        chrootManagerExecutor.execute(
                resultViewerLoggerTextView,
                imagePath,
                NhPaths.CHROOT_PATH()
        );
    }

    private void setRemoveChrootButton(){
        removeChrootButton.setOnClickListener(view -> {
            MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                    .setTitle("Warning!")
                    .setMessage("Are you sure to remove the below Kali Chroot folder?\n" + NhPaths.CHROOT_PATH())
                    .setPositiveButton("I'm sure.", (dialogInterface, i) -> {
                        MaterialAlertDialogBuilder adb1 = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                            .setTitle("Warning!")
                            .setMessage("This is your last chance!")
                            .setPositiveButton("Just do it.", (dialogInterface1, i1) -> {
                                chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.REMOVE_CHROOT);
                                chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
                                    @Override
                                    public void onExecutorPrepare() {
                                        broadcastBackPressedIntent(false);
                                        setAllButtonEnable(false);
                                    }

                                    @Override
                                    public void onExecutorProgressUpdate(int progress) {
                                        // Do nothing
                                    }

                                    @Override
                                    public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                                        broadcastBackPressedIntent(true);
                                        setAllButtonEnable(true);
                                        compatCheck();
                                    }
                                });
                                resultViewerLoggerTextView.setText("");
                                chrootManagerExecutor.execute(resultViewerLoggerTextView);
                            })
                            .setNegativeButton("Okay, I'm sorry.", (dialogInterface12, i12) -> {

                            });
                        adb1.create().show();
                    })
                    .setNegativeButton("Forget it.", (dialogInterface, i) -> { });
            adb.create().show();
        });
    }

    private void startDownloadChroot(String targetDownloadFileName, File downloadDir) {
        if (activity == null || context == null) return;

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                .setTitle("Downloading " + targetDownloadFileName)
                .setMessage("Please do NOT kill the app or clear recent apps..")
                .setCancelable(false)
                .setView(progressBar)
                .create();

        chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.DOWNLOAD_CHROOT);
        chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
            @Override
            public void onExecutorPrepare() {
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        broadcastBackPressedIntent(false);
                        setAllButtonEnable(false);
                        progressDialog.show();
                    });
                }
            }

            @Override
            public void onExecutorProgressUpdate(int progress) {
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        progressBar.setProgress(progress);
                        if (progress == 100) {
                            progressDialog.dismiss();
                            broadcastBackPressedIntent(true);
                            setAllButtonEnable(true);
                        }
                    });
                }
            }

            @Override
            public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (resultCode == 0) {
                            NhPaths.showMessage(context, "Download completed successfully.");
                        } else {
                            NhPaths.showMessage(context, "Download failed. Please try again.");
                        }
                    });
                }
            }
        });

        resultViewerLoggerTextView.setText("");
        String[] servers = {PRIMARY_IMAGE_SERVER, SECONDARY_IMAGE_SERVER};
        for (String server : servers) {
            chrootManagerExecutor.execute(
                    resultViewerLoggerTextView,
                    server,
                    IMAGE_DIRECTORY + targetDownloadFileName,
                    new File(downloadDir, targetDownloadFileName).getAbsolutePath()
            );
        }
    }

    private ProgressBar createProgressBar() {
        if (activity == null) {
            throw new IllegalStateException("Activity is null. Cannot create ProgressBar.");
        }
        return new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
    }

    private AlertDialog createProgressDialog(String fileName, ProgressBar progressBar) {
        if (activity == null) {
            throw new IllegalStateException("Activity is null. Cannot create ProgressDialog.");
        }
        return new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                .setTitle("Downloading " + fileName)
                .setMessage("Please do NOT kill the app or clear recent apps..")
                .setCancelable(false)
                .setView(progressBar)
                .create();
    }

    private void runOnUiThread(Runnable action) {
        if (activity != null) {
            activity.runOnUiThread(action);
        } else {
            throw new IllegalStateException("Activity is null. Cannot run on UI thread.");
        }
    }

    private void setAddMetaPkgButton() {
        addMetaPkgButton.setOnClickListener(view -> {
            //for now, we'll hardcode packages in the dialog view.  At some point we'll want to grab them automatically.
            MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
            adb.setTitle("Metapackage Install & Upgrade");
            LayoutInflater inflater = activity.getLayoutInflater();
            @SuppressLint("InflateParams") final ScrollView sv = (ScrollView) inflater.inflate(R.layout.metapackagechooser, null);
            adb.setView(sv);
            final Button metapackageButton = sv.findViewById(R.id.metapackagesWeb);
            metapackageButton.setOnClickListener(v -> {
                String metapackagesURL = "https://tools.kali.org/kali-metapackages";
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(metapackagesURL));
                startActivity(browserIntent);
            });
            adb.setPositiveButton(R.string.InstallAndUpdateButtonText, (dialog, which) -> {
                StringBuilder sb = new StringBuilder();
                CheckBox cb;
                // now grab all the checkboxes in the dialog and check their status
                // thanks to "user2" for a 2-line sample of how to get the dialog's view:  https://stackoverflow.com/a/13959585/3035127
                final AlertDialog d = (AlertDialog) dialog;
                final LinearLayout ll = d.findViewById(R.id.metapackageLinearLayout);
                int children = Objects.requireNonNull(ll).getChildCount();
                for (int cnt = 0; cnt < children; cnt++) {
                    if (ll.getChildAt(cnt) instanceof CheckBox) {
                        cb = (CheckBox) ll.getChildAt(cnt);
                        if (cb.isChecked()) {
                            sb.append(cb.getText()).append(" ");
                        }
                    }
                }
                try {
                    run_cmd("apt update && apt install " + sb + " -y && echo \"(You can close the terminal now)\n\"");
                } catch (Exception e) {
                    NhPaths.showMessage(context, getString(R.string.toast_install_terminal));
                }
            });
            AlertDialog ad = adb.create();
            ad.setCancelable(true);
            ad.show();
        });
    }

    private void setBackupChrootButton() {
        backupChrootButton.setOnClickListener(view -> {
            AlertDialog ad = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat).create();
            EditText backupFullPathEditText = new EditText(activity);
            LinearLayout ll = new LinearLayout(activity);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setLayoutParams(layoutParams);
            LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            editTextParams.setMargins(58,40,58,0);
            backupFullPathEditText.setLayoutParams(editTextParams);
            ll.addView(backupFullPathEditText);
            ad.setView(ll);
            ad.setTitle("Backup Chroot");
            ad.setMessage("* It is strongly suggested to create your backup chroot as tar.gz format just for faster process but bigger file size.\n\nbackup \"" + NhPaths.CHROOT_PATH() + "\" to:" );
            backupFullPathEditText.setText(sharedPreferences.getString(SharePrefTag.CHROOT_DEFAULT_BACKUP_SHAREPREF_TAG, ""));
            ad.setButton(DialogInterface.BUTTON_POSITIVE, "OK", (dialogInterface, i) -> {
                sharedPreferences.edit().putString(SharePrefTag.CHROOT_DEFAULT_BACKUP_SHAREPREF_TAG, backupFullPathEditText.getText().toString()).apply();
                if (new File(backupFullPathEditText.getText().toString()).exists()){
                    ad.dismiss();
                    AlertDialog ad2 = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat).create();
                    ad2.setMessage("File exists already, do you want to overwrite it anyway?");
                    ad2.setButton(DialogInterface.BUTTON_POSITIVE, "YES", (dialogInterface1, i1) -> {
                        chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.BACKUP_CHROOT);
                        chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
                            @Override
                            public void onExecutorPrepare() {
                                context.startService(new Intent(context, NotificationChannelService.class).setAction(NotificationChannelService.BACKINGUP));
                                broadcastBackPressedIntent(false);
                                setAllButtonEnable(false);
                            }

                            @Override
                            public void onExecutorProgressUpdate(int progress) {
                                // Do nothing
                            }

                            @Override
                            public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                                broadcastBackPressedIntent(true);
                                setAllButtonEnable(true);
                            }
                        });
                        resultViewerLoggerTextView.setText("");
                        chrootManagerExecutor.execute(resultViewerLoggerTextView, NhPaths.CHROOT_PATH(), backupFullPathEditText.getText().toString());
                    });
                    ad2.show();
                } else {
                    chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.BACKUP_CHROOT);
                    chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
                        @Override
                        public void onExecutorPrepare() {
                            context.startService(new Intent(context, NotificationChannelService.class).setAction(NotificationChannelService.BACKINGUP));
                            broadcastBackPressedIntent(false);
                            setAllButtonEnable(false);
                        }

                        @Override
                        public void onExecutorProgressUpdate(int progress) {
                            // Do nothing
                        }

                        @Override
                        public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                            broadcastBackPressedIntent(true);
                            setAllButtonEnable(true);
                        }
                    });
                    chrootManagerExecutor.execute(resultViewerLoggerTextView, NhPaths.CHROOT_PATH(), backupFullPathEditText.getText().toString());
                }
            });
            ad.show();
        });
    }

    private void showBanner() {
        resultViewerLoggerTextView.setText("");
        chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.ISSUE_BANNER);
        chrootManagerExecutor.execute(resultViewerLoggerTextView, getResources().getString(R.string.aboutchroot));
    }

    private void compatCheck() {
        chrootManagerExecutor = new ChrootManagerExecutor(ChrootManagerExecutor.CHECK_CHROOT);
        chrootManagerExecutor.setListener(new ChrootManagerExecutor.ChrootManagerExecutorListener() {
            @Override
            public void onExecutorPrepare() {
                broadcastBackPressedIntent(false);
            }

            @Override
            public void onExecutorProgressUpdate(int progress) { }

            @Override
            public void onExecutorFinished(int resultCode, ArrayList<String> resultString) {
                broadcastBackPressedIntent(true);
                setButtonVisibility(resultCode);
                setMountStatsTextView(resultCode);
                setAllButtonEnable(true);
                context.startService(new Intent(context, CompatCheckService.class).putExtra("RESULTCODE", resultCode));
            }
        });
        resultViewerLoggerTextView.setText("");
        chrootManagerExecutor.execute(resultViewerLoggerTextView, sharedPreferences.getString(SharePrefTag.CHROOT_PATH_SHAREPREF_TAG, ""));
    }

    private void setMountStatsTextView(int MODE) {
        if (MODE == IS_MOUNTED) {
            mountStatsTextView.setTextColor(Color.GREEN);
            mountStatsTextView.setText(R.string.running);
        } else if  (MODE == IS_UNMOUNTED) {
            mountStatsTextView.setTextColor(Color.RED);
            mountStatsTextView.setText(R.string.stopped);
        } else if  (MODE == NEED_TO_INSTALL) {
            // Only show about banner if chroot is not installed + clear old logs when showing banner for new peeps
            resultViewerLoggerTextView.setText("");
            showBanner();

            mountStatsTextView.setTextColor(Color.RED);
            mountStatsTextView.setText(R.string.not_yet_installed);
        }
    }

    private void setButtonVisibility(int MODE) {
        SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        Boolean iswatch = sharedpreferences.getBoolean("running_on_wearos", false);

        switch (MODE) {
            case IS_MOUNTED:
                mountChrootButton.setVisibility(View.GONE);
                unmountChrootButton.setVisibility(View.VISIBLE);
                installChrootButton.setVisibility(View.GONE);
                if (iswatch) {
                    addMetaPkgButton.setVisibility(View.GONE);
                } else {
                    addMetaPkgButton.setVisibility(View.VISIBLE);
                }
                removeChrootButton.setVisibility(View.GONE);
                backupChrootButton.setVisibility(View.GONE);
                break;
            case IS_UNMOUNTED:
                mountChrootButton.setVisibility(View.VISIBLE);
                unmountChrootButton.setVisibility(View.GONE);
                installChrootButton.setVisibility(View.GONE);
                addMetaPkgButton.setVisibility(View.GONE);
                removeChrootButton.setVisibility(View.VISIBLE);
                backupChrootButton.setVisibility(View.VISIBLE);
                break;
            case NEED_TO_INSTALL:
                mountChrootButton.setVisibility(View.GONE);
                unmountChrootButton.setVisibility(View.GONE);
                installChrootButton.setVisibility(View.VISIBLE);
                addMetaPkgButton.setVisibility(View.GONE);
                removeChrootButton.setVisibility(View.GONE);
                backupChrootButton.setVisibility(View.GONE);
                break;
        }
    }

    private void setAllButtonEnable(boolean isEnable) {
        mountChrootButton.setEnabled(isEnable);
        unmountChrootButton.setEnabled(isEnable);
        installChrootButton.setEnabled(isEnable);
        addMetaPkgButton.setEnabled(isEnable);
        removeChrootButton.setEnabled(isEnable);
        kaliFolderEditButton.setEnabled(isEnable);
        backupChrootButton.setEnabled(isEnable);
    }

    private void broadcastBackPressedIntent(Boolean isEnabled){
        backPressedintent.setAction(AppNavHomeActivity.NethunterReceiver.BACKPRESSED);
        backPressedintent.putExtra("isEnable", isEnabled);
        context.sendBroadcast(backPressedintent);
        setHasOptionsMenu(isEnabled);
    }

    ////
    // Bridge side functions
    ////

    public void run_cmd(String cmd) {
        Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
    }
}