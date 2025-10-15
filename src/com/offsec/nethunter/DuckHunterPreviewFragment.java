package com.offsec.nethunter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;
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
    private static final String ARG_IN_PATH = "arg_in_path";
    private static final String ARG_OUT_PATH = "arg_out_path";
    private static final String TAG = "DuckHunterPreview";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ShellExecuter exe = new ShellExecuter();

    private String duckyInputFile;
    private String duckyOutputFile;
    private TextView previewSource;
    private Activity activity;
    private Context appContext;
    private boolean isReceiverRegistered;
    private final BroadcastReceiver previewDuckyBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            String actionExtra = intent != null ? intent.getStringExtra("ACTION") : null;
            Log.d(TAG, "onReceive called | intentAction=" + action + ", extra.ACTION=" + actionExtra +
                    ", isAdded=" + isAdded() + ", isResumed=" + isResumed());

            if (Objects.equals(actionExtra, "PREVIEWDUCKY")) {
                if (activity != null) {
                    Log.d(TAG, "Broadcasting SHOULDCONVERT=false back to Convert fragment");
                    activity.sendBroadcast(new Intent()
                            .putExtra("ACTION", "SHOULDCONVERT")
                            .putExtra("SHOULDCONVERT", false)
                            .setAction(BuildConfig.APPLICATION_ID + ".SHOULDCONVERT").setPackage(activity.getPackageName()));
                } else {
                    Log.w(TAG, "Activity is null; cannot broadcast SHOULDCONVERT");
                }

                final String in = duckyInputFile;
                final String out = duckyOutputFile;
                Log.d(TAG, "Starting conversion task | input=" + in + ", output=" + out + ", lang=" + DuckHunterFragment.lang);
                executorService.execute(() -> {
                    String cmd = "sh " + NhPaths.APP_SCRIPTS_PATH + "/duckyconverter -i " + in +
                            " -o " + out + " -l " + DuckHunterFragment.lang;
                    Log.d(TAG, "Running converter command: " + cmd);
                    boolean convertResult = exe.RunAsRootReturnValue(cmd) == 0;
                    Log.d(TAG, "Converter finished | success=" + convertResult);

                    mainHandler.post(() -> {
                        Log.d(TAG, "Handling conversion result on main thread | success=" + convertResult + ", previewSourceIsNull=" + (previewSource == null));
                        if (convertResult) {
                            executorService.execute(() -> {
                                Log.d(TAG, "Reading preview output from file: " + out);
                                String previewResult = exe.RunAsRootReturnOutput("cat " + out);
                                int len = previewResult != null ? previewResult.length() : -1;
                                Log.d(TAG, "Preview output read | length=" + len);
                                mainHandler.post(() -> {
                                    if (previewSource != null) {
                                        // Avoid logging huge content; log the first 200 chars for debugging
                                        String snippet = previewResult != null && previewResult.length() > 200 ? previewResult.substring(0, 200) + "..." : previewResult;
                                        Log.d(TAG, "Updating UI with preview text snippet: " + snippet);
                                        previewSource.setText(previewResult);
                                    } else {
                                        Log.w(TAG, "previewSource is null; cannot update UI");
                                    }
                                });
                            });
                        } else {
                            mainHandler.post(() -> {
                                if (previewSource != null) {
                                    Log.w(TAG, "Conversion failed; updating UI with failure message");
                                    previewSource.setText(R.string.duckhunter_conversion_failed);
                                } else {
                                    Log.w(TAG, "Conversion failed; previewSource is null; cannot show error message");
                                }
                            });
                        }
                    });
                });
            }
        }
    };

    public static DuckHunterPreviewFragment newInstance(String inFilePath, String outFilePath) {
        Log.d(TAG, "newInstance called | inFilePath=" + inFilePath + ", outFilePath=" + outFilePath);
        DuckHunterPreviewFragment fragment = new DuckHunterPreviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IN_PATH, inFilePath);
        args.putString(ARG_OUT_PATH, outFilePath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        appContext = requireContext().getApplicationContext();
        Bundle args = getArguments();
        Log.d(TAG, "onCreate | hasArgs=" + (args != null) + ", activityIsNull=" + (activity == null));
        if (args != null) {
            duckyInputFile = args.getString(ARG_IN_PATH);
            duckyOutputFile = args.getString(ARG_OUT_PATH);
            Log.d(TAG, "Args loaded | input=" + duckyInputFile + ", output=" + duckyOutputFile);
        } else {
            Log.w(TAG, "No arguments provided; input/output paths are null");
        }
        if (!isReceiverRegistered) {
            Log.d(TAG, "Registering PREVIEWDUCKY receiver with appContext (lifecycle: onCreate->onDestroy)");
            ContextCompat.registerReceiver(appContext, previewDuckyBroadcastReceiver,
                    new IntentFilter(BuildConfig.APPLICATION_ID + ".PREVIEWDUCKY"),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            isReceiverRegistered = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView | container=" + container + ", inflaterNull=" + (inflater == null));
        if (inflater == null) {
            Log.w(TAG, "LayoutInflater is null; returning an empty fallback view");
            return new View(requireContext());
        }
        View rootView = inflater.inflate(R.layout.duck_hunter_preview, container, false);
        previewSource = rootView.findViewById(R.id.source);
        Log.d(TAG, "onCreateView | previewSource bound? " + (previewSource != null));
        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy | unregisterReceiver if needed");
        if (isReceiverRegistered && appContext != null) {
            appContext.unregisterReceiver(previewDuckyBroadcastReceiver);
            isReceiverRegistered = false;
            Log.d(TAG, "PREVIEWDUCKY receiver unregistered");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView | clearing previewSource reference");
        previewSource = null;
    }
}