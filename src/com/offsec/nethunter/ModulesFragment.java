package com.offsec.nethunter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModulesFragment extends Fragment implements MenuProvider {
    public static final String TAG = "ModulesFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private Activity activity;
    private final ShellExecuter exe = new ShellExecuter();
    // 0: Alphabetical, 1: Reverse
    private int currentSortOrder = 0;
    public EditText modules_path;

    public static ModulesFragment newInstance(int sectionNumber) {
        ModulesFragment fragment = new ModulesFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    Log.d(TAG, "Permission " + entry.getKey() + ": " + (entry.getValue() ? "granted" : "denied"));
                }
            });

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.modules_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        ListView modules = requireView().findViewById(R.id.modulesList);
        if (searchView != null) {
            searchView.setQueryHint("Search modules");
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) { return false; }
                @Override
                public boolean onQueryTextChange(String newText) {
                    if (modules.getAdapter() instanceof ArrayAdapter) {
                        ((ArrayAdapter<?>) modules.getAdapter()).getFilter().filter(newText);
                    }
                    return true;
                }
            });
        }
    }

    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_sort) {
            if (isAdded() && getView() != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setTitle("Sort Modules")
                        .setItems(new String[]{"Alphabetical", "Reverse"}, (dialog, which) -> {
                            currentSortOrder = which;
                            refreshModules(requireView());
                        })
                        .show();
            }
            return true;
        } else if (id == R.id.action_enable_faulty_check) {
            if (isAdded()) {
                boolean isChecked = !item.isChecked();
                item.setChecked(isChecked);
                SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("enable_faulty_check", isChecked).apply();
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner());
        // Show lsmod output when fragment opens
        showLoadedModules(view);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        logMissingPermissions();
    }

    private void showModuleInfo(String moduleName) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                String modulesPath = modules_path != null ? modules_path.getText().toString() : "";
                String sanitizedModulesPath = modulesPath.replaceAll("[^a-zA-Z0-9/_-]", "");
                String kernelVersion = System.getProperty("os.version");
                String pathWithKernelVersion = sanitizedModulesPath + "/" + kernelVersion;

                // Find the full path of the module
                String findCommand = "find " + sanitizedModulesPath + " " + pathWithKernelVersion + " -name " + moduleName + ".ko -print -quit";
                String moduleFilePath = exe.RunAsRootOutput(findCommand);
                String info;
                if (moduleFilePath == null || moduleFilePath.trim().isEmpty()) {
                    info = "Module not found: " + moduleName;
                } else {
                    moduleFilePath = moduleFilePath.trim();
                    info = exe.RunAsRootOutput("modinfo " + moduleFilePath);
                    if (info == null || info.trim().isEmpty()) {
                        info = "No information available for " + moduleName;
                    }
                }

                Activity currentActivity = getActivity();
                if (currentActivity != null) {
                    String finalInfo = info;
                    currentActivity.runOnUiThread(() -> new AlertDialog.Builder(currentActivity)
                            .setTitle("Module Info: " + moduleName)
                            .setMessage(finalInfo)
                            .setPositiveButton("OK", null)
                            .show());
                }
            });
        } finally {
            executor.shutdown();
        }
    }

    private void showModuleDependencies(String moduleName) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                String modulesPath = modules_path != null ? modules_path.getText().toString() : "";
                String sanitizedModulesPath = modulesPath.replaceAll("[^a-zA-Z0-9/_-]", "");
                String kernelVersion = System.getProperty("os.version");
                String pathWithKernelVersion = sanitizedModulesPath + "/" + kernelVersion;

                // Find the full path of the module
                String findCommand = "find " + sanitizedModulesPath + " " + pathWithKernelVersion + " -name " + moduleName + ".ko -print -quit";
                String moduleFilePath = exe.RunAsRootOutput(findCommand);
                String info;
                if (moduleFilePath == null || moduleFilePath.trim().isEmpty()) {
                    info = "Module not found: " + moduleName;
                } else {
                    moduleFilePath = moduleFilePath.trim();
                    info = exe.RunAsRootOutput("modinfo " + moduleFilePath);
                    if (info == null || info.trim().isEmpty()) {
                        info = "No information available for " + moduleName;
                    }
                }

                String dependencies;
                assert moduleFilePath != null;
                if (moduleFilePath.isEmpty()) {
                    dependencies = "Module not found: " + moduleName;
                } else {
                    dependencies = exe.RunAsRootOutput("modinfo " + moduleFilePath + " | grep depends");
                    if (dependencies == null || dependencies.trim().isEmpty()) {
                        dependencies = "No dependencies found for " + moduleName;
                    }
                }

                Activity currentActivity = getActivity();
                if (currentActivity != null) {
                    String finalDependencies = dependencies;
                    currentActivity.runOnUiThread(() -> new AlertDialog.Builder(currentActivity)
                            .setTitle("Module Dependencies: " + moduleName)
                            .setMessage(finalDependencies)
                            .setPositiveButton("OK", null)
                            .show());
                }
            });
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void showLoadedModules(View rootView) {
        final ListView modules = rootView.findViewById(R.id.modulesList);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                String loadedModulesRaw = exe.RunAsRootOutput("lsmod | cut -d' ' -f1");
                if (loadedModulesRaw == null || loadedModulesRaw.trim().isEmpty()) {
                    loadedModulesRaw = "";
                }
                String[] loadedModules = loadedModulesRaw.split("\n");
                // Remove header if present
                if (loadedModules.length > 0 && loadedModules[0].trim().equals("Module")) {
                    loadedModules = Arrays.copyOfRange(loadedModules, 1, loadedModules.length);
                }
                // Build moduleStates: all loaded modules are true
                Map<String, Boolean> moduleStates = new HashMap<>();
                for (String module : loadedModules) {
                    if (!module.trim().isEmpty()) {
                        moduleStates.put(module, true);
                    }
                }
                List<String> moduleList = new ArrayList<>(Arrays.asList(loadedModules));
                // Remove empty entries
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    moduleList.removeIf(String::isEmpty);
                }

                Activity currentActivity = getActivity();
                if (currentActivity != null) {
                    currentActivity.runOnUiThread(() -> {
                        if (moduleList.isEmpty()) {
                            modules.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, Collections.singletonList("No modules loaded")));
                        } else {
                            modules.setAdapter(new ModuleListAdapter(requireContext(), moduleList, moduleStates));
                        }
                    });
                }
            });
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.modules, container, false);

        ListView modules = rootView.findViewById(R.id.modulesList);

        // lsmod button
        Button lsmodButton = rootView.findViewById(R.id.lsmod);
        lsmodButton.setOnClickListener(view -> showLoadedModules(rootView));

        // Use last path
        modules_path = rootView.findViewById(R.id.modulesPath);
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        String LastModulesPath = sharedPreferences.getString("last_modulespath", "");
        if (!LastModulesPath.isEmpty()) modules_path.setText(LastModulesPath);

        modules.setOnItemLongClickListener((adapterView, view, position, id) -> {
            String selectedModule = modules.getItemAtPosition(position).toString();
            PopupMenu popup = new PopupMenu(requireContext(), view);
            popup.getMenu().add("Show module information");
            popup.getMenu().add("View Dependencies");
            popup.setOnMenuItemClickListener(item -> {
                if (Objects.equals(item.getTitle(), "Show module information")) {
                    showModuleInfo(selectedModule);
                    return true;
                } else if (Objects.equals(item.getTitle(), "View Dependencies")) {
                    showModuleDependencies(selectedModule);
                    return true;
                }
                return false;
            });
            popup.show();
            return true;
        });

        // Refresh Modules
        Button refreshButton = rootView.findViewById(R.id.refresh);
        refreshButton.setOnClickListener(view -> refreshModules(rootView));
        refreshModules(rootView);

        // Modules toggle
        modules.setOnItemClickListener((adapterView, view, i, l) -> {
            String modulesPath = modules_path != null ? modules_path.getText().toString() : "";
            String sanitizedModulesPath = modulesPath.replaceAll("[^a-zA-Z0-9/_-]", "");
            String kernelVersion = System.getProperty("os.version");
            String pathWithKernelVersion = sanitizedModulesPath + "/" + kernelVersion;

            String selectedModule = modules.getItemAtPosition(i).toString();
            String isModuleLoaded = exe.RunAsRootOutput("lsmod | cut -d' ' -f1 | grep " + selectedModule);
            ImageView statusIcon = view.findViewById(R.id.moduleStatusIcon);

            if (isModuleLoaded != null && isModuleLoaded.trim().equals(selectedModule)) {
                String disableModule = exe.RunAsRootOutput("rmmod " + selectedModule + " && echo Success || echo Failed");
                if (disableModule.contains("Success")) {
                    Log.d(TAG, "Module disabled: " + selectedModule);
                    Toast.makeText(requireActivity().getApplicationContext(), "Module Disabled: " + selectedModule, Toast.LENGTH_LONG).show();
                    if (statusIcon != null) {
                        statusIcon.setImageResource(R.drawable.ic_module_not_loaded);
                    }
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Failed - rmmod " + selectedModule, Toast.LENGTH_LONG).show();
                }
            } else {
                String findCommand = "find " + sanitizedModulesPath + " " + pathWithKernelVersion + " -name " + selectedModule + ".ko -print -quit";
                String foundModulePath = exe.RunAsRootOutput(findCommand);

                if (foundModulePath == null || foundModulePath.trim().isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Module not found in the directory structure", Toast.LENGTH_LONG).show();
                    return;
                }
                String modulePath = foundModulePath.trim();

                String toggleModule = exe.RunAsRootOutput("insmod " + modulePath + " && echo Success || echo Failed");
                if (toggleModule.contains("Success")) {
                    Log.d(TAG, "Module enabled: " + selectedModule + " from path: " + modulePath);
                    Toast.makeText(requireActivity().getApplicationContext(), "Module Enabled: " + selectedModule + " from path: " + modulePath, Toast.LENGTH_LONG).show();
                    if (statusIcon != null) {
                        statusIcon.setImageResource(R.drawable.ic_module_loaded);
                    }
                } else {
                    toggleModule = exe.RunAsRootOutput("modprobe -d " + pathWithKernelVersion + " " + selectedModule + " && echo Success || echo Failed");
                    if (toggleModule.contains("Success")) {
                        Log.d(TAG, "Module enabled: " + selectedModule + " from path: " + sanitizedModulesPath);
                        Toast.makeText(requireActivity().getApplicationContext(), "Module Enabled: " + selectedModule + " from path: " + sanitizedModulesPath, Toast.LENGTH_LONG).show();
                        if (statusIcon != null) {
                            statusIcon.setImageResource(R.drawable.ic_module_loaded);
                        }
                    } else {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed - modprobe -d " + pathWithKernelVersion + " " + selectedModule, Toast.LENGTH_LONG).show();
                        if (sharedPreferences.getBoolean("enable_faulty_check", true)) {
                            checkFaultyModule(sanitizedModulesPath, selectedModule);
                        }
                    }
                }
            }
        });

        return rootView;
    }

    private void toggleModule(String selectedModule, ImageView statusIcon) {
        String modulesPath = modules_path != null ? modules_path.getText().toString() : "";
        String sanitizedModulesPath = modulesPath.replaceAll("[^a-zA-Z0-9/_-]", "");
        String kernelVersion = System.getProperty("os.version");
        String pathWithKernelVersion = sanitizedModulesPath + "/" + kernelVersion;

        String isModuleLoaded = exe.RunAsRootOutput("lsmod | cut -d' ' -f1 | grep " + selectedModule);

        if (isModuleLoaded != null && isModuleLoaded.trim().equals(selectedModule)) {
            String disableModule = exe.RunAsRootOutput("rmmod " + selectedModule + " && echo Success || echo Failed");
            if (disableModule.contains("Success")) {
                Log.d(TAG, "Module disabled: " + selectedModule);
                Toast.makeText(requireActivity().getApplicationContext(), "Module Disabled: " + selectedModule, Toast.LENGTH_LONG).show();
                if (statusIcon != null) {
                    statusIcon.setImageResource(R.drawable.ic_module_not_loaded);
                }
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Failed - rmmod " + selectedModule, Toast.LENGTH_LONG).show();
            }
        } else {
            String findCommand = "find " + sanitizedModulesPath + " " + pathWithKernelVersion + " -name " + selectedModule + ".ko -print -quit";
            String foundModulePath = exe.RunAsRootOutput(findCommand);

            if (foundModulePath == null || foundModulePath.trim().isEmpty()) {
                Toast.makeText(requireActivity().getApplicationContext(), "Module not found in the directory structure", Toast.LENGTH_LONG).show();
                return;
            }
            String modulePath = foundModulePath.trim();

            String toggleModule = exe.RunAsRootOutput("insmod " + modulePath + " && echo Success || echo Failed");
            if (toggleModule.contains("Success")) {
                Log.d(TAG, "Module enabled: " + selectedModule + " from path: " + modulePath);
                Toast.makeText(requireActivity().getApplicationContext(), "Module Enabled: " + selectedModule + " from path: " + modulePath, Toast.LENGTH_LONG).show();
                if (statusIcon != null) {
                    statusIcon.setImageResource(R.drawable.ic_module_loaded);
                }
            } else {
                toggleModule = exe.RunAsRootOutput("modprobe -d " + pathWithKernelVersion + " " + selectedModule + " && echo Success || echo Failed");
                if (toggleModule.contains("Success")) {
                    Log.d(TAG, "Module enabled: " + selectedModule + " from path: " + sanitizedModulesPath);
                    Toast.makeText(requireActivity().getApplicationContext(), "Module Enabled: " + selectedModule + " from path: " + sanitizedModulesPath, Toast.LENGTH_LONG).show();
                    if (statusIcon != null) {
                        statusIcon.setImageResource(R.drawable.ic_module_loaded);
                    }
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Failed - modprobe -d " + pathWithKernelVersion + " " + selectedModule, Toast.LENGTH_LONG).show();
                    SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                    if (sharedPreferences.getBoolean("enable_faulty_check", true)) {
                        checkFaultyModule(sanitizedModulesPath, selectedModule);
                    }
                }
            }
        }
    }

    private void refreshModules(View rootView) {
        SharedPreferences sharedpreferences = null;
        if (activity != null) {
            sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        }
        final ListView modules = rootView.findViewById(R.id.modulesList);

        modules_path = rootView.findViewById(R.id.modulesPath);
        String modulesPath = "";
        if (modules_path != null) {
            modulesPath = modules_path.getText().toString();
        }
        String sanitizedModulesPath = modulesPath.replaceAll("[^a-zA-Z0-9/_\\-]", "");
        if ("/proc".equals(sanitizedModulesPath) || "/".equals(sanitizedModulesPath)) {
            Activity currentActivity = getActivity();
            if (currentActivity != null) {
                currentActivity.runOnUiThread(() ->
                        Toast.makeText(currentActivity.getApplicationContext(), "Searching in /proc or / is not allowed", Toast.LENGTH_SHORT).show()
                );
            }
            return;
        }
        if (sharedpreferences != null) {
            sharedpreferences.edit().putString("last_modulespath", modulesPath).apply();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                // Check if the directory exists
                String pathCheck = exe.RunAsRootOutput("test -d " + sanitizedModulesPath + " && echo exists || echo not_exists");
                if (pathCheck == null || pathCheck.trim().isEmpty() || "not_exists".equals(pathCheck.trim())) {
                    Activity currentActivity = getActivity();
                    if (currentActivity != null) {
                        currentActivity.runOnUiThread(() ->
                                Toast.makeText(currentActivity.getApplicationContext(), sanitizedModulesPath + " does not exist", Toast.LENGTH_SHORT).show()
                        );
                    }
                    return;
                }

                // List all *.ko files in the user-specified path
                String modulesRaw = exe.RunAsRootOutput("find " + sanitizedModulesPath + " -type f -name '*.ko' -printf '%f\\n' | sed 's/\\.ko$//1'");
                if (modulesRaw == null || modulesRaw.trim().isEmpty()) {
                    Activity currentActivity = getActivity();
                    if (currentActivity != null) {
                        currentActivity.runOnUiThread(() ->
                                modules.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, Collections.singletonList("No modules found")))
                        );
                    }
                    return;
                }
                final String[] modulesArray = modulesRaw.split("\n");

                // Execute `lsmod` once and cache results
                String loadedModulesRaw = exe.RunAsRootOutput("lsmod | cut -d' ' -f1");
                List<String> loadedModules = loadedModulesRaw == null || loadedModulesRaw.trim().isEmpty()
                        ? Collections.emptyList()
                        : Arrays.asList(loadedModulesRaw.split("\n"));

                // Prepare module states
                Map<String, Boolean> moduleStates = new HashMap<>();
                for (String module : modulesArray) {
                    moduleStates.put(module, loadedModules.contains(module));
                }

                // Sort module list according to currentSortOrder
                List<String> moduleList = new ArrayList<>(Arrays.asList(modulesArray));
                if (currentSortOrder == 0) {
                    Collections.sort(moduleList, String.CASE_INSENSITIVE_ORDER);
                } else if (currentSortOrder == 1) {
                    Collections.sort(moduleList, Collections.reverseOrder(String.CASE_INSENSITIVE_ORDER));
                }

                Activity currentActivity = getActivity();
                if (currentActivity != null) {
                    currentActivity.runOnUiThread(() -> {
                        ModuleListAdapter adapter = new ModuleListAdapter(requireContext(), moduleList, moduleStates);
                        modules.setAdapter(adapter);
                    });
                }
            });
        } finally {
            executor.shutdown();
        }
    }

    private void checkFaultyModule(String modulePath, String moduleName) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                // Retrieve kernel logs
                String kernelLogs = exe.RunAsRootOutput("dmesg | tail -n 20");
                Activity currentActivity = getActivity();
                if (currentActivity != null) {
                    String finalKernelLogs = kernelLogs.trim().isEmpty() ? "No kernel logs available" : kernelLogs;
                    currentActivity.runOnUiThread(() -> {
                        // Show a Toast with the error
                        Toast.makeText(currentActivity.getApplicationContext(), "Error loading module: " + moduleName, Toast.LENGTH_LONG).show();

                        // Show an AlertDialog with detailed logs
                        new AlertDialog.Builder(currentActivity)
                                .setTitle("Module Load Failed: " + moduleName)
                                .setMessage("Kernel Logs:\n" + finalKernelLogs)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            });
        } finally {
            executor.shutdown();
        }
    }

    static class ModuleListAdapter extends ArrayAdapter<String> {
        private final List<String> modules;
        private final Context context;
        private final Map<String, Boolean> moduleStates;

        public ModuleListAdapter(Context context, List<String> modules, Map<String, Boolean> moduleStates) {
            super(context, R.layout.module_list_item, modules);
            this.context = context;
            this.modules = modules;
            this.moduleStates = moduleStates;
        }

        private static class ViewHolder {
            TextView textView;
            ImageView statusIcon;
            CheckBox autoLoadCheckBox;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                convertView = inflater.inflate(R.layout.module_list_item, parent, false);
                holder = new ViewHolder();
                holder.textView = convertView.findViewById(R.id.moduleName);
                holder.statusIcon = convertView.findViewById(R.id.moduleStatusIcon);
                holder.autoLoadCheckBox = convertView.findViewById(R.id.moduleAutoLoad);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            String moduleName = modules.get(position);
            holder.textView.setText(moduleName);

            // Set icon based on module state
            Boolean isLoaded = moduleStates.get(moduleName);
            if (isLoaded != null && isLoaded) {
                holder.statusIcon.setImageResource(R.drawable.ic_module_loaded);
            } else {
                holder.statusIcon.setImageResource(R.drawable.ic_module_not_loaded);
            }

            // Handle auto-load checkbox
            SharedPreferences preferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
            holder.autoLoadCheckBox.setOnCheckedChangeListener(null);
            holder.autoLoadCheckBox.setChecked(preferences.getBoolean("autoload_" + moduleName, false));
            holder.autoLoadCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                    preferences.edit().putBoolean("autoload_" + moduleName, isChecked).apply()
            );

            return convertView;
        }
    }

    // Check for missing permissions
    // This method should be called in the onCreate method of the fragment, add 'logMissingPermissions()' to the onCreate method
    // And add import 'androidx.core.content.ContextCompat;'
    private void logMissingPermissions() {
        List<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.INTERNET);
        requiredPermissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requiredPermissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        }
        requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        List<String> missingPermissions = new ArrayList<>();
        for (String perm : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Missing permission: " + perm);
                missingPermissions.add(perm);
            } else {
                Log.d(TAG, "Granted permission: " + perm);
            }
        }
        if (!missingPermissions.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                permissionLauncher.launch(missingPermissions.toArray(new String[0]));
            }
        }
    }

    ////
    // Bridge side functions
    ////

    public void run_cmd(String cmd) {
        @SuppressLint("SdCardPath") Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
    }
}