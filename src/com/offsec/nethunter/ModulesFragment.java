package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

// TODO: we should move the search and sort option to upper 'toolbar' to save space.
// TODO: a find feature on Executors is also possible, just avoid "/proc"
public class ModulesFragment extends Fragment {
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

    private void showModuleInfo(String moduleName) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String info = exe.RunAsRootOutput("modinfo " + moduleName);
            if (info.trim().isEmpty()) {
                info = exe.RunAsRootOutput("modinfo " + moduleName + ".ko");
            }
            String finalInfo = info.trim().isEmpty() ? "No information available for " + moduleName : info;
            Activity currentActivity = getActivity();
            if (currentActivity != null) {
                currentActivity.runOnUiThread(() -> new AlertDialog.Builder(currentActivity)
                        .setTitle("Module Info: " + moduleName)
                        .setMessage(finalInfo)
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
    }

    private void showLoadedModules(View rootView) {
        final ListView modules = rootView.findViewById(R.id.modulesList);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String loadedModulesRaw = exe.RunAsRootOutput("lsmod | cut -d' ' -f1");
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
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.modules, container, false);

        ListView modules = rootView.findViewById(R.id.modulesList);
        SearchView moduleSearch = rootView.findViewById(R.id.moduleSearch);
        Spinner sortSpinner = rootView.findViewById(R.id.sortSpinner);

        // Store sort order and refresh on change
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (currentSortOrder != position) {
                    currentSortOrder = position;
                    refreshModules(rootView);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // lsmod button
        Button lsmodButton = rootView.findViewById(R.id.lsmod);
        lsmodButton.setOnClickListener(view -> showLoadedModules(rootView));

        // Use last path
        modules_path = rootView.findViewById(R.id.modulesPath);
        if (activity != null) {
            SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
            String LastModulesPath = sharedpreferences.getString("last_modulespath", "");
            if (!LastModulesPath.isEmpty()) modules_path.setText(LastModulesPath);
        }

        modules.setOnItemLongClickListener((adapterView, view, position, id) -> {
            String selectedModule = modules.getItemAtPosition(position).toString();
            PopupMenu popup = new PopupMenu(requireContext(), adapterView);
            popup.getMenu().add("Show module information");
            popup.setOnMenuItemClickListener(item -> {
                if (Objects.equals(item.getTitle(), "Show module information")) {
                    showModuleInfo(selectedModule);
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

        // Search functionality (applies to current adapter)
        moduleSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

        // Modules toggle
        modules.setOnItemClickListener((adapterView, view, i, l) -> {
            String modulesPath = modules_path.getText().toString();
            String ModulesPathFull = modulesPath.replaceAll("[^a-zA-Z0-9/_-]", "");
            String selected_module = modules.getItemAtPosition(i).toString();
            String is_it_loaded = exe.RunAsRootOutput("lsmod | cut -d' ' -f1 | grep " + selected_module);
            ImageView statusIcon = view.findViewById(R.id.moduleStatusIcon);

            if (is_it_loaded.equals(selected_module)) {
                String disable_module = exe.RunAsRootOutput("rmmod " + selected_module + " && echo Success || echo Failed");
                if (disable_module.contains("Success")) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Module Disabled", Toast.LENGTH_LONG).show();
                    if (statusIcon != null) statusIcon.setImageResource(R.drawable.ic_module_not_loaded);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Failed - rmmod " + selected_module, Toast.LENGTH_LONG).show();
                }
            } else {
                String toggle_module = exe.RunAsRootOutput("insmod " + ModulesPathFull + "/" + selected_module + ".ko && echo Success || echo Failed");
                if (toggle_module.contains("Success")) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Module enabled with insmod", Toast.LENGTH_LONG).show();
                    if (statusIcon != null) statusIcon.setImageResource(R.drawable.ic_module_loaded);
                } else {
                    toggle_module = exe.RunAsRootOutput("modprobe -d " + ModulesPathFull + " " + selected_module + " && echo Success || echo Failed");
                    if (toggle_module.contains("Success")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Module enabled with modprobe", Toast.LENGTH_LONG).show();
                        if (statusIcon != null) statusIcon.setImageResource(R.drawable.ic_module_loaded);
                    } else {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed - modprobe -d " + ModulesPathFull + " " + selected_module, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    private void refreshModules(View rootView) {
        SharedPreferences sharedpreferences = null;
        if (activity != null) {
            sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        }
        final ListView modules = rootView.findViewById(R.id.modulesList);

        modules_path = rootView.findViewById(R.id.modulesPath);
        String modulesPath = modules_path.getText().toString();
        String sanitizedModulesPath = modulesPath.replaceAll("[^a-zA-Z0-9/_-]", "");
        if (sharedpreferences != null) {
            sharedpreferences.edit().putString("last_modulespath", modulesPath).apply();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (sanitizedModulesPath.isEmpty()) {
                Activity currentActivity = getActivity();
                if (currentActivity != null) {
                    currentActivity.runOnUiThread(() ->
                            Toast.makeText(currentActivity.getApplicationContext(), "Please enter path", Toast.LENGTH_SHORT).show()
                    );
                }
                return;
            }

            // Execute `find` command once
            String modulesRaw = exe.RunAsRootOutput("find " + sanitizedModulesPath + " -name *.ko -printf \"%f\\n\" | sed 's/\\.ko$//1'");
            final String[] modulesArray = modulesRaw.split("\n");

            // Execute `lsmod` once and cache results
            String loadedModulesRaw = exe.RunAsRootOutput("lsmod | cut -d' ' -f1");
            List<String> loadedModules = Arrays.asList(loadedModulesRaw.split("\n"));

            // Prepare module states
            Map<String, Boolean> moduleStates = new HashMap<>();
            for (String module : modulesArray) {
                moduleStates.put(module, loadedModules.contains(module));
            }

            // Sort module list according to currentSortOrder
            List<String> moduleList = new ArrayList<>(Arrays.asList(modulesArray));
            if (currentSortOrder == 0) {
                Collections.sort(moduleList);
            } else if (currentSortOrder == 1) {
                Collections.sort(moduleList, Collections.reverseOrder());
            }

            Activity currentActivity = getActivity();
            if (currentActivity != null) {
                currentActivity.runOnUiThread(() -> {
                    if (!modulesRaw.isEmpty()) {
                        ModuleListAdapter adapter = new ModuleListAdapter(requireContext(), moduleList, moduleStates);
                        modules.setAdapter(adapter);
                    } else {
                        modules.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, Collections.singletonList("No modules found")));
                    }
                });
            }
        });
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

            return convertView;
        }
    }

    ////
    // Bridge side functions
    ////

    public void run_cmd(String cmd) {
        Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
    }

    public static class PreferencesData {
        private static final String PREF_NAME = "com.offsec.nethunter_preferences";

        private static SharedPreferences getSharedPreferences(Context context) {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        public static void saveString(Context context, String key, String value) {
            getSharedPreferences(context).edit().putString(key, value).apply();
        }

        public static String getString(Context context, String key, String defaultValue) {
            return getSharedPreferences(context).getString(key, defaultValue);
        }
    }
}