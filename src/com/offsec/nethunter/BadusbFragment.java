package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

public class BadusbFragment extends Fragment {
    private String sourcePath;
    private static final String ARG_SECTION_NUMBER = "section_number";
    private final ShellExecuter exe = new ShellExecuter();
    private Context context;
    private Activity activity;

    public static BadusbFragment newInstance(int sectionNumber) {
        BadusbFragment fragment = new BadusbFragment();
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
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            sourcePath = NhPaths.APP_SD_FILES_PATH + "/configs/startbadusb-lollipop.sh";
        } else {
            sourcePath = NhPaths.APP_SD_FILES_PATH + "/configs/startbadusb.sh";
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.badusb, container, false);
        loadOptions(rootView);
        final Button button = rootView.findViewById(R.id.updateOptions);
        button.setOnClickListener(v -> updateOptions());
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            loadOptions(getView().getRootView());
        }
    }

    private void loadOptions(View rootView) {
        final EditText ifc = rootView.findViewById(R.id.ifc);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            final String text = exe.ReadFile_SYNC(sourcePath);
            ifc.post(() -> {
                String regExpatInterface = "^INTERFACE=(.*)$";
                Pattern pattern = Pattern.compile(regExpatInterface, Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String ifcValue = matcher.group(1);
                    ifc.setText(ifcValue);
                }
            });
        });
        executor.shutdown();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.badusb, menu);
                MenuItem sourceItem = menu.findItem(R.id.source_button);
                boolean iswatch = requireActivity().getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_WATCH);
                if (iswatch) {
                    sourceItem.setVisible(false);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.start_service) {
                    start();
                    return true;
                } else if (id == R.id.stop_service) {
                    stop();
                    return true;
                } else if (id == R.id.source_button) {
                    Intent i = new Intent(activity, EditSourceActivity.class);
                    i.putExtra("path", sourcePath);
                    startActivity(i);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void updateOptions() {
        String sourceFile = exe.ReadFile_SYNC(sourcePath);
        EditText ifc = activity.findViewById(R.id.ifc);
        sourceFile = sourceFile.replaceAll("(?m)^INTERFACE=(.*)$", "INTERFACE=" + ifc.getText().toString());
        boolean r = exe.SaveFileContents(sourceFile, sourcePath);// 1st arg contents, 2nd arg filepath
        if (r) {
            NhPaths.showMessage(context,"Options updated!");
        } else {
            NhPaths.showMessage(context,"Options not updated!");
        }
    }

    private void start() {
        String[] command = new String[1];
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            command[0] = NhPaths.APP_SCRIPTS_PATH + "/start-badusb-lollipop &> " + NhPaths.APP_SD_FILES_PATH + "/badusb.log &";
        }
        exe.RunAsRoot(command);
        NhPaths.showMessage(context,"BadUSB attack started! Check /sdcard/nh_files/badusb.log");
    }

    private void stop() {
        String[] command = new String[1];
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            command[0] = NhPaths.APP_SCRIPTS_PATH + "/stop-badusb-lollipop";
        }
        exe.RunAsRoot(command);
        NhPaths.showMessage(context,"BadUSB attack stopped!");
    }
}