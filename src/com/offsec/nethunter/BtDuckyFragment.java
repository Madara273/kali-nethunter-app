package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BtDuckyFragment extends BTFragment {
    private static final String TAG = "BtDuckyFragment";
    private EditText editSource;
    final String tmpfilePath = NhPaths.SD_PATH + "/nh_files/.tmpbtdfile.txt";
    private Context context;
    private Activity activity;

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        activity = getActivity();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.bt_ducky, container, false);
        Button loadButton = rootView.findViewById(R.id.duckyLoad);
        Button saveButton = rootView.findViewById(R.id.duckySave);
        Button injectButton = rootView.findViewById(R.id.duckyInject);
        editSource = rootView.findViewById(R.id.editSource);
        ShellExecuter exe = new ShellExecuter();

        String[] duckyscript_file = getDuckyScriptFiles();
        Spinner duckyscriptSpinner = rootView.findViewById(R.id.duckhunter_preset_spinner);
        ArrayAdapter<String> duckyscriptAdapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_item, duckyscript_file);
        duckyscriptAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        duckyscriptSpinner.setAdapter(duckyscriptAdapter);

        loadButton.setOnClickListener(v -> openFile());
        saveButton.setOnClickListener(v -> saveFile(false));
        injectButton.setOnClickListener(v -> {
            String statusCMD = exe.RunAsRootOutput(NhPaths.APP_SCRIPTS_PATH + "/bootkali custom_cmd bluetoothctl info | grep 'Connected: yes'");
            if (!statusCMD.contains("Connected: yes")) {
                Toast.makeText(requireContext(), "Start the server first", Toast.LENGTH_SHORT).show();
            } else {
                saveFile(true);
                run_cmd("python3 /root/badbt/ducky.py -d " + tmpfilePath + "; exit");
            }
        });
        duckyscriptSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selectedPreset = parent.getItemAtPosition(pos).toString();
                String tmp = NhPaths.APP_SD_FILES_PATH + "/duckyscripts/" + selectedPreset;
                String fileContent = readFileContent(Uri.fromFile(new File(tmp)));
                editSource.setText(fileContent);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        return rootView;
    }

    private String[] getDuckyScriptFiles() {
        List<String> result = new ArrayList<>();
        File script_folder = new File(NhPaths.APP_SD_FILES_PATH + "/duckyscripts");
        File[] filesInFolder = script_folder.listFiles();
        assert filesInFolder != null;
        for (File file : filesInFolder) {
            if (!file.isDirectory()) {
                result.add(file.getName());
            }
        }
        Collections.sort(result);
        return result.toArray(new String[0]);
    }

    public void saveFile(boolean tmp) {
        String content = editSource.getText().toString();
        String loadFilePath = "/scripts/ducky/";

        try {
            File scriptsDir = new File(NhPaths.APP_SD_FILES_PATH, loadFilePath);
            if (!scriptsDir.exists()) scriptsDir.mkdirs();
        } catch (Exception e) {
            NhPaths.showMessage(getContext(), e.getMessage());
        }
        MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(requireActivity(), R.style.DialogStyleCompat);

        alert.setTitle("Name");
        alert.setMessage("Please enter a name for your script.");

        // Set an EditText view to get user input
        final EditText input = new EditText(activity);
        alert.setView(input);

        alert.setPositiveButton("Ok", (dialog, whichButton) -> {
            String value = input.getText().toString();
            if (!value.isEmpty()) {
                // Save file (ask name)
                File scriptFile = new File(NhPaths.APP_SD_FILES_PATH + loadFilePath + File.separator + value + ".conf");
                System.out.println(scriptFile.getAbsolutePath());
                if (!scriptFile.exists()) {
                    try {
                        if (getView() != null) {
                            saveContentToFile(Uri.fromFile(scriptFile));
                        }
                    } catch (Exception e) {
                        NhPaths.showMessage(context, e.getMessage());
                    }
                } else {
                    NhPaths.showMessage(context, "File already exists");
                }
            } else {
                NhPaths.showMessage(context, "Wrong name provided");
            }
        });

        alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
            ///Do nothing
        });

        if (tmp) {
            saveContentToFile(Uri.fromFile(new File(tmpfilePath)));
        } else {
            alert.show();
        }
    }

    private final ActivityResultLauncher<String[]> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    editSource.setText(readFileContent(uri));
                }
            });

    private void openFile() {
        openFileLauncher.launch(new String[]{"*/*"});
    }

    private String readFileContent(Uri uri) {
        StringBuilder content = new StringBuilder();
        try {
            InputStream inputStream = requireActivity().getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            reader.close();
            assert inputStream != null;
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error reading file", e);
            Toast.makeText(requireContext(), "Error reading file", Toast.LENGTH_SHORT).show();
        }

        return content.toString();
    }

    private void saveContentToFile(Uri uri) {
        try {
            OutputStream outputStream = requireActivity().getContentResolver().openOutputStream(uri);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(editSource.getText().toString());
            writer.close();
            assert outputStream != null;
            outputStream.close();
            Toast.makeText(requireContext(), "File saved", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e(TAG, "Error saving file", e);
            Toast.makeText(requireContext(), "Error saving file", Toast.LENGTH_SHORT).show();
        }
    }
}