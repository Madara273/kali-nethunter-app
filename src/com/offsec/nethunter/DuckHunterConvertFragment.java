package com.offsec.nethunter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.utils.NhPaths;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DuckHunterConvertFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "DuckHunterConvert";
    private static final String ARG_IN_PATH = "arg_in_path";
    private String duckyInputFile;
    private static final String loadFilePath = "/scripts/ducky/";
    private Context context;
    private Activity activity;
    private Context appContext;
    private boolean isReceiverRegistered;
    private EditText editsource;
    private final ConvertDuckyBroadcastReceiver convertDuckyBroadcastReceiver = new ConvertDuckyBroadcastReceiver();

    // Activity Result launcher for picking files
    private final ActivityResultLauncher<Intent> pickFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && getView() != null) {
                    Intent data = result.getData();
                    String FilePath = Objects.requireNonNull(data.getData()).getPath();
                    EditText editsource = getView().findViewById(R.id.editSource);
                    try {
                        StringBuilder text = new StringBuilder();
                        BufferedReader br = new BufferedReader(new FileReader(FilePath));
                        String line;
                        while ((line = br.readLine()) != null) {
                            text.append(line).append('\n');
                        }
                        br.close();
                        editsource.setText(text.toString());
                        NhPaths.showMessage(context, "Script loaded");
                        Log.d(TAG, "File loaded via picker | path=" + FilePath + ", length=" + text.length());
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading picked file", e);
                        NhPaths.showMessage(context, e.getMessage());
                    }
                }
            });

    public static DuckHunterConvertFragment newInstance(String inFilePath) {
        DuckHunterConvertFragment fragment = new DuckHunterConvertFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IN_PATH, inFilePath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        activity = getActivity();
        appContext = requireContext().getApplicationContext();
        Bundle args = getArguments();
        Log.d(TAG, "onCreate | hasArgs=" + (args != null));
        if (args != null) {
            duckyInputFile = args.getString(ARG_IN_PATH);
            Log.d(TAG, "Args loaded | input=" + duckyInputFile);
        }
        if (!isReceiverRegistered) {
            Log.d(TAG, "Registering WRITEDUCKY receiver with appContext (lifecycle: onCreate->onDestroy)");
            ContextCompat.registerReceiver(appContext, convertDuckyBroadcastReceiver,
                    new IntentFilter(BuildConfig.APPLICATION_ID + ".WRITEDUCKY"),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            isReceiverRegistered = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View rootView = inflater.inflate(R.layout.duck_hunter_convert, container, false);
        TextView t2 = rootView.findViewById(R.id.reference_text);
        t2.setMovementMethod(LinkMovementMethod.getInstance());

        editsource = rootView.findViewById(R.id.editSource);
        editsource.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.d(TAG, "Text changed | length=" + s.length() + "; notifying should-convert=true");
                activity.sendBroadcast(new Intent().putExtra("ACTION", "SHOULDCONVERT")
                        .putExtra("SHOULDCONVERT", true)
                        .setAction(BuildConfig.APPLICATION_ID + ".SHOULDCONVERT")
                        .setPackage(activity.getPackageName()));
            }

            @Override
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "afterTextChanged | length=" + s.length());
            }
        });

        Button b = rootView.findViewById(R.id.duckyLoad);
        Button b1 = rootView.findViewById(R.id.duckySave);
        b.setOnClickListener(this);
        b1.setOnClickListener(this);

        // Duckhunter preset spinner templates
        String[] duckyscript_file = getDuckyScriptFiles();
        Spinner duckyscriptSpinner = rootView.findViewById(R.id.duckhunter_preset_spinner);
        ArrayAdapter<String> duckyscriptAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, duckyscript_file);
        duckyscriptAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        duckyscriptSpinner.setAdapter(duckyscriptAdapter);
        duckyscriptSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = duckyscriptSpinner.getSelectedItem().toString();
                Log.d(TAG, "Preset selected | name=" + selected);
                getPreset(selected);
                write_ducky();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy | unregisterReceiver if needed");
        if (isReceiverRegistered && appContext != null) {
            appContext.unregisterReceiver(convertDuckyBroadcastReceiver);
            isReceiverRegistered = false;
            Log.d(TAG, "WRITEDUCKY receiver unregistered");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView | clearing editsource reference");
        editsource = null;
    }

    private void getPreset(String filename) {
        if (getView() == null) {
            return;
        }
        String filename_path = "/duckyscripts/";
        filename = filename_path + filename;
        EditText editsource = getView().findViewById(R.id.editSource);
        File file = new File(NhPaths.APP_SD_FILES_PATH, filename);
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading preset file: " + file.getAbsolutePath(), e);
        }
        editsource.setText(text);
    }

    private String[] getDuckyScriptFiles() {
        List<String> result = new ArrayList<>();
        File script_folder = new File(NhPaths.APP_SD_FILES_PATH + "/duckyscripts");
        File[] filesInFolder = script_folder.listFiles();
        if (filesInFolder == null) {
            Log.w(TAG, "No files in duckyscripts folder: " + script_folder.getAbsolutePath());
            return new String[0];
        }
        for (File file : filesInFolder) {
            if (!file.isDirectory()) {
                result.add(file.getName());
            }
        }
        Collections.sort(result);
        return result.toArray(new String[0]);
    }

    private void write_ducky() {
        String content = editsource != null ? editsource.getText().toString() : null;
        Log.d(TAG, "write_ducky | editsourceNull=" + (editsource == null) + ", contentLength=" + (content != null ? content.length() : -1) + ", path=" + duckyInputFile);
        try {
            File myFile = new File(duckyInputFile);
            boolean created = myFile.exists() || myFile.createNewFile();
            if (!created) {
                Log.w(TAG, "Failed to create input file: " + myFile.getAbsolutePath());
            }
            FileOutputStream fOut = new FileOutputStream(myFile, false);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            if (content != null) myOutWriter.append(content);
            myOutWriter.close();
            fOut.close();
            Log.d(TAG, "write_ducky | write complete, size=" + myFile.length());
        } catch (Exception e) {
            Log.e(TAG, "Error writing ducky input file", e);
            NhPaths.showMessage(context, e.getMessage());
        }
    }

    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.duckyLoad) {
            try {
                File scriptsDir = new File(NhPaths.APP_SD_FILES_PATH, loadFilePath);
                if (!scriptsDir.exists() && !scriptsDir.mkdirs()) {
                    Log.w(TAG, "Failed to create scripts dir: " + scriptsDir.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating scripts dir", e);
                NhPaths.showMessage(context, e.getMessage());
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            Uri selectedUri = Uri.parse(NhPaths.APP_SD_FILES_PATH + loadFilePath);
            intent.setDataAndType(selectedUri, "file/*");
            pickFileLauncher.launch(intent);
        } else if (id == R.id.duckySave) {
            try {
                File scriptsDir = new File(NhPaths.APP_SD_FILES_PATH, loadFilePath);
                if (!scriptsDir.exists() && !scriptsDir.mkdirs()) {
                    Log.w(TAG, "Failed to create scripts dir: " + scriptsDir.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating scripts dir", e);
                NhPaths.showMessage(context, e.getMessage());
            }
            MaterialAlertDialogBuilder alert = getMaterialAlertDialogBuilder();

            alert.setNegativeButton("Cancel", (dialog, whichButton) -> { });
            alert.show();
        } else {
            NhPaths.showMessage(context, "Unknown click");
        }
    }

    @NonNull
    private MaterialAlertDialogBuilder getMaterialAlertDialogBuilder() {
        MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);

        alert.setTitle("Name");
        alert.setMessage("Please enter a name for your script.");

        final EditText input = new EditText(activity);
        alert.setView(input);

        alert.setPositiveButton("Ok", (dialog, whichButton) -> {
            String value = input.getText().toString();
            if (!value.isEmpty()) {
                File scriptFile = new File(NhPaths.APP_SD_FILES_PATH + loadFilePath + File.separator + value + ".conf");
                if (!scriptFile.exists()) {
                    try {
                        if (getView() != null) {
                            EditText source = getView().findViewById(R.id.editSource);
                            String text = source.getText().toString();
                            boolean created = scriptFile.createNewFile();
                            if (!created) {
                                Log.w(TAG, "Failed to create script file: " + scriptFile.getAbsolutePath());
                            }
                            FileOutputStream fOut = new FileOutputStream(scriptFile);
                            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                            myOutWriter.append(text);
                            myOutWriter.close();
                            fOut.close();
                            Log.d(TAG, "Saved script file: " + scriptFile.getAbsolutePath() + ", size=" + scriptFile.length());
                            NhPaths.showMessage(context, "Script saved");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving script file", e);
                        NhPaths.showMessage(context, e.getMessage());
                    }
                } else {
                    NhPaths.showMessage(context, "File already exists");
                }
            } else {
                NhPaths.showMessage(context, "Wrong name provided");
            }
        });
        return alert;
    }

    public class ConvertDuckyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String act = intent != null ? intent.getAction() : null;
            String extra = intent != null ? intent.getStringExtra("ACTION") : null;
            Log.d(TAG, "ConvertDuckyReceiver | onReceive action=" + act + ", extra=" + extra);
            if (Objects.equals(extra, "WRITEDUCKY")) {
                write_ducky();
                if (activity != null) {
                    Log.d(TAG, "Sending PREVIEWDUCKY broadcast to Preview fragment");
                    activity.sendBroadcast(new Intent()
                            .putExtra("ACTION", "PREVIEWDUCKY")
                            .setAction(BuildConfig.APPLICATION_ID + ".PREVIEWDUCKY")
                            .setPackage(context.getPackageName()));
                } else {
                    Log.w(TAG, "Activity null; cannot send PREVIEWDUCKY");
                }
            }
        }
    }
}
