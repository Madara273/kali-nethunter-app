package com.offsec.nethunter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.RecyclerViewAdapter.NethunterRecyclerViewAdapter;
import com.offsec.nethunter.RecyclerViewData.NethunterData;
import com.offsec.nethunter.SQL.NethunterSQL;
import com.offsec.nethunter.models.NethunterModel;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.offsec.nethunter.R.id.f_nethunter_action_search;
import static com.offsec.nethunter.R.id.f_nethunter_action_snowfall;

public class NetHunterFragment extends Fragment {
    private static final String ARG_SECTION_NUMBER = "section_number";
    private NethunterRecyclerViewAdapter nethunterRecyclerViewAdapter;
    private static final AtomicBoolean NH_FILES_COPY_SCHEDULED = new AtomicBoolean(false);
    private final android.os.Handler nhHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Button refreshButton;
    private MenuItem snowfallButton;
    private Button addButton;
    private Button deleteButton;
    private Button moveButton;
    private SharedPreferences sharedpreferences;

    public static NetHunterFragment newInstance(int sectionNumber) {
        NetHunterFragment fragment = new NetHunterFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureNhFilesOnSdcard();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.nethunter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        androidx.core.view.MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new androidx.core.view.MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.nethunter, menu);
                MenuItem searchItem = menu.findItem(R.id.f_nethunter_action_search);
                sharedpreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

                boolean iswatch = requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
                boolean snowfall = iswatch
                        ? sharedpreferences.getBoolean("snowfall_enabled", false)
                        : sharedpreferences.getBoolean("snowfall_enabled", true);

                if (iswatch) searchItem.setVisible(false);

                snowfallButton = menu.findItem(R.id.f_nethunter_action_snowfall);
                if (snowfall) snowfallButton.setIcon(R.drawable.snowflake_trigger);
                else snowfallButton.setIcon(R.drawable.snowflake_trigger_bw);

                SearchView searchView = (SearchView) searchItem.getActionView();
                if (searchView != null) {
                    searchView.setOnSearchClickListener(v -> menu.setGroupVisible(R.id.f_nethunter_menu_group1, false));
                    searchView.setOnCloseListener(() -> {
                        menu.setGroupVisible(R.id.f_nethunter_menu_group1, true);
                        return false;
                    });
                    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                        @Override public boolean onQueryTextSubmit(String query) { return false; }
                        @Override public boolean onQueryTextChange(String newText) {
                            if (nethunterRecyclerViewAdapter != null) {
                                nethunterRecyclerViewAdapter.getFilter().filter(newText);
                            }
                            return false;
                        }
                    });
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == f_nethunter_action_search) return true;
                if (id == f_nethunter_action_snowfall) {
                    trigger_snowfall();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner());

        RecyclerView recyclerView = view.findViewById(R.id.f_nethunter_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        List<NethunterModel> initList = NethunterData.getInstance().getNethunterModels(requireContext()).getValue();
        if (initList == null) initList = new ArrayList<>();
        nethunterRecyclerViewAdapter = new NethunterRecyclerViewAdapter(getContext(), initList);
        recyclerView.setAdapter(nethunterRecyclerViewAdapter);
        // Observe data changes and notify adapter
        NethunterData.getInstance().getNethunterModels(requireContext()).observe(getViewLifecycleOwner(), list -> {
            if (nethunterRecyclerViewAdapter != null) nethunterRecyclerViewAdapter.notifyDataSetChanged();
        });

        // Fix button IDs to match layout
        refreshButton = view.findViewById(R.id.f_nethunter_refreshButton);
        addButton = view.findViewById(R.id.f_nethunter_addItemButton);
        deleteButton = view.findViewById(R.id.f_nethunter_deleteItemButton);
        moveButton = view.findViewById(R.id.f_nethunter_moveItemButton);

        onRefreshItemSetup();
        onAddItemSetup();
        onDeleteItemSetup();
        onMoveItemSetup();

        // WearOS optimisation
        TextView NHDesc = view.findViewById(R.id.f_nethunter_banner2);
        LinearLayout NHButtons = view.findViewById(R.id.f_nethunter_linearlayoutBtn);
        boolean iswatch = requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        sharedpreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        sharedpreferences.edit().putBoolean("running_on_wearos", iswatch).apply();
        if (iswatch) {
            NHDesc.setVisibility(View.GONE);
            NHButtons.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        NethunterData.getInstance().refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        refreshButton = null;
        addButton = null;
        deleteButton = null;
        moveButton = null;
        nethunterRecyclerViewAdapter = null;
    }

    private void onRefreshItemSetup(){
        refreshButton.setOnClickListener(v -> NethunterData.getInstance().refreshData());
    }

    private void ensureNhFilesOnSdcard() {
        if (!NH_FILES_COPY_SCHEDULED.compareAndSet(false, true)) {
            Log.d("NetHunterFragment", "nh_files copy already scheduled; skipping.");
            return;
        }
        if (nhFilesExists()) {
            Log.i("NetHunterFragment", "nh_files exists on SD; will perform a safe sync to ensure content is present.");
        } else {
            Log.i("NetHunterFragment", "nh_files not found on SD; will create and sync.");
        }
        Log.i("NetHunterFragment", "Deferring nh_files sync by 10s to wait for storage permission.");
        nhHandler.postDelayed(() -> {
            try {
                if (!isStoragePermissionGranted()) {
                    Log.w("NetHunterFragment", "Storage permission not granted after delay; skipping nh_files sync.");
                    return;
                }
                // Always perform a safe sync; it only adds missing/newer files
                syncNhFilesToSdcard();
                if (nhFilesExists()) {
                    Log.i("NetHunterFragment", "nh_files present on /sdcard and synced.");
                } else {
                    Log.e("NetHunterFragment", "nh_files still missing after sync attempt.");
                }
            } catch (Exception e) {
                Log.e("NetHunterFragment", "Exception while syncing nh_files: ", e);
            } finally {
                NH_FILES_COPY_SCHEDULED.set(false);
            }
        }, 10_000);
    }

    private synchronized boolean nhFilesExists() {
        File nhFilesDir = new File(Environment.getExternalStorageDirectory().getPath() + "/nh_files");
        return nhFilesDir.exists() && nhFilesDir.isDirectory();
    }

    private boolean isStoragePermissionGranted() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            Context ctx = getContext();
            if (ctx == null) return false;
            return androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    // Safely mirror internal nh_files to /sdcard/nh_files without overwriting user changes
    private void syncNhFilesToSdcard() {
        try {
            final String src = NhPaths.APP_NHFILES_PATH;
            final String dst = NhPaths.SD_PATH + "/nh_files";
            ShellExecuter exe = new ShellExecuter();

            // Ensure destination exists
            exe.RunAsRootOutput("mkdir -p '" + dst + "'");

            String bb = NhPaths.BUSYBOX != null ? NhPaths.BUSYBOX.trim() : "";
            String cmd;
            if (!bb.isEmpty()) {
                cmd = bb + " cp -au '" + src + "/.' '" + dst + "/'";
            } else {
                // Fallback: do not overwrite existing files
                cmd = "sh -c 'cp -rn " + src + "/. " + dst + "/'";
            }
            String out = exe.RunAsRootOutput(cmd);
            Log.d("NetHunterFragment", "syncNhFilesToSdcard cmd output: " + (out == null ? "" : out));
        } catch (Exception e) {
            Log.e("NetHunterFragment", "syncNhFilesToSdcard error", e);
        }
    }

    private void trigger_snowfall() {
        sharedpreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        boolean iswatch = requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        boolean snowfall = sharedpreferences.getBoolean("snowfall_enabled", !iswatch);
        if (snowfall) {
            sharedpreferences.edit().putBoolean("snowfall_enabled", false).apply();
            snowfallButton.setIcon(R.drawable.snowflake_trigger_bw);
            Toast.makeText(requireActivity().getApplicationContext(), "Snowfall disabled. Restart app to take effect.", Toast.LENGTH_SHORT).show();
        } else {
            sharedpreferences.edit().putBoolean("snowfall_enabled", true).apply();
            snowfallButton.setIcon(R.drawable.snowflake_trigger);
            Toast.makeText(requireActivity().getApplicationContext(), "Snowfall enabled. Restart app to take effect.", Toast.LENGTH_SHORT).show();
        }
    }

    private void onAddItemSetup() {
        addButton.setOnClickListener(v -> {
            List<NethunterModel> fullList = NethunterData.getInstance().nethunterModelListFull;
            if (fullList == null) return;
            final LayoutInflater mInflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View promptView = mInflater.inflate(R.layout.nethunter_add_dialog_view, null);

            final EditText title = promptView.findViewById(R.id.f_nethunter_add_adb_et_title);
            final EditText command = promptView.findViewById(R.id.f_nethunter_add_adb_et_command);
            final EditText delimiter = promptView.findViewById(R.id.f_nethunter_add_adb_et_delimiter);
            final CheckBox runOnCreate = promptView.findViewById(R.id.f_nethunters_add_adb_checkbox_runoncreate);
            final Spinner positionsSpinner = promptView.findViewById(R.id.f_nethunter_add_adb_spr_positions);
            final Spinner titlesSpinner = promptView.findViewById(R.id.f_nethunter_add_adb_spr_titles);

            // Populate titles spinner with current titles
            ArrayList<String> titles = new ArrayList<>();
            for (NethunterModel model : fullList) titles.add(model.getTitle());
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, titles);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            titlesSpinner.setAdapter(spinnerAdapter);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Add Item")
                    .setView(promptView)
                    .setCancelable(false)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String titleString = title.getText().toString().trim();
                        String commandString = command.getText().toString().trim();
                        String delimiterString = delimiter.getText().toString().trim();
                        String runOnCreateString = runOnCreate.isChecked()?"1":"0";
                        if (titleString.isEmpty()) { Toast.makeText(requireContext(), "Title cannot be empty", Toast.LENGTH_SHORT).show(); return; }
                        if (commandString.isEmpty()) { Toast.makeText(requireContext(), "Command cannot be empty", Toast.LENGTH_SHORT).show(); return; }
                        if (delimiterString.isEmpty()) { Toast.makeText(requireContext(), "Delimiter cannot be empty", Toast.LENGTH_SHORT).show(); return; }

                        int posType = positionsSpinner.getSelectedItemPosition(); // 0: before, 1: after
                        int selectedTitleIndex = Math.max(0, titlesSpinner.getSelectedItemPosition());
                        int targetPositionId = selectedTitleIndex + 1 + (posType == 0 ? 0 : 1); // 1-based id
                        // Clamp to valid insertion range [1, size+1]
                        int size = fullList.size();
                        if (targetPositionId < 1) targetPositionId = 1;
                        if (targetPositionId > size + 1) targetPositionId = size + 1;

                        ArrayList<String> dataArrayList = new ArrayList<>();
                        dataArrayList.add(titleString);
                        dataArrayList.add(commandString);
                        dataArrayList.add(delimiterString);
                        dataArrayList.add(runOnCreateString);
                        NethunterData.getInstance().addData(targetPositionId, dataArrayList, NethunterSQL.getInstance(requireContext()));
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    private void onMoveItemSetup() {
        moveButton.setOnClickListener(v -> {
            List<NethunterModel> fullList = NethunterData.getInstance().nethunterModelListFull;
            if (fullList == null || fullList.isEmpty()) return;
            String[] titles = new String[fullList.size()];
            for (int i = 0; i < fullList.size(); i++) titles[i] = fullList.get(i).getTitle();

            // Step 1: select item to move
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Select item to move")
                    .setItems(titles, (dialog, originalIndex) -> {
                        // Step 2: select target item
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Select target item")
                                .setItems(titles, (dialog2, targetItemIndex) -> {
                                    // Step 3: choose before/after
                                    String[] where = new String[]{"Before", "After"};
                                    new MaterialAlertDialogBuilder(requireContext())
                                            .setTitle("Place relative to target")
                                            .setItems(where, (dialog3, whichPos) -> {
                                                int targetIndex = targetItemIndex + (whichPos == 0 ? 0 : 1);
                                                // Clamp to [0, size]
                                                int size = fullList.size();
                                                if (targetIndex < 0) targetIndex = 0;
                                                if (targetIndex > size) targetIndex = size;
                                                NethunterData.getInstance().moveData(originalIndex, targetIndex, NethunterSQL.getInstance(requireContext()));
                                            })
                                            .show();
                                })
                                .show();
                    })
                    .show();
        });
    }

    private void onDeleteItemSetup(){
        deleteButton.setOnClickListener(v -> {
            List<NethunterModel> fullList = NethunterData.getInstance().nethunterModelListFull;
            if (fullList == null || fullList.isEmpty()) return;
            String[] titles = new String[fullList.size()];
            for (int i = 0; i < fullList.size(); i++) titles[i] = fullList.get(i).getTitle();

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Item")
                    .setItems(titles, (dialog, which) -> {
                        ArrayList<Integer> selectedPositionsIndex = new ArrayList<>();
                        ArrayList<Integer> selectedTargetIds = new ArrayList<>();
                        selectedPositionsIndex.add(which);
                        selectedTargetIds.add(which + 1); // DB id is 1-based
                        NethunterData.getInstance().deleteData(selectedPositionsIndex, selectedTargetIds, NethunterSQL.getInstance(requireContext()));
                        Toast.makeText(requireContext(), "Delete requested.", Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });
    }
}
