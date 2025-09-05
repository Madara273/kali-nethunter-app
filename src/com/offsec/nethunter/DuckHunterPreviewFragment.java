package com.offsec.nethunter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.offsec.nethunter.utils.ShellExecuter;
import com.offsec.nethunter.utils.NhPaths;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DuckHunterPreviewFragment extends Fragment {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ShellExecuter exe = new ShellExecuter();
    private final String duckyInputFile;
    private final String duckyOutputFile;
    private TextView previewSource;
    private Activity activity;
    private boolean isReceiverRegistered;
    private final BroadcastReceiver previewDuckyBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getStringExtra("ACTION"), "PREVIEWDUCKY")) {
                activity.sendBroadcast(new Intent()
                        .putExtra("ACTION", "SHOULDCONVERT")
                        .putExtra("SHOULDCONVERT", false)
                        .setAction(BuildConfig.APPLICATION_ID + ".SHOULDCONVERT").setPackage(activity.getPackageName()));

                executorService.execute(() -> {
                    boolean convertResult = exe.RunAsRootReturnValue(
                            "sh " + NhPaths.APP_SCRIPTS_PATH + "/duckyconverter -i " + duckyInputFile +
                                    " -o " + duckyOutputFile + " -l " + DuckHunterFragment.lang) == 0;

                    mainHandler.post(() -> {
                        if (convertResult) {
                            executorService.execute(() -> {
                                String previewResult = exe.RunAsRootReturnOutput("cat " + duckyOutputFile);
                                mainHandler.post(() -> previewSource.setText(previewResult));
                            });
                        } else {
                            mainHandler.post(() -> previewSource.setText(R.string.duckhunter_conversion_failed));
                        }
                    });
                });
            }
        }
    };

    public DuckHunterPreviewFragment(String inFilePath, String outFilePath) {
        this.duckyInputFile = inFilePath;
        this.duckyOutputFile = outFilePath;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.duck_hunter_preview, container, false);
        previewSource = rootView.findViewById(R.id.source);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isReceiverRegistered) {
            ContextCompat.registerReceiver(activity, previewDuckyBroadcastReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".PREVIEWDUCKY"), ContextCompat.RECEIVER_NOT_EXPORTED);
            isReceiverRegistered = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isReceiverRegistered) {
            activity.unregisterReceiver(previewDuckyBroadcastReceiver);
            isReceiverRegistered = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        previewSource = null;
    }
}