package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import com.offsec.nethunter.widget.ExpandableChoiceView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.offsec.nethunter.RecyclerViewAdapter.KaliServicesRecyclerViewAdapter;
import com.offsec.nethunter.RecyclerViewData.KaliServicesData;
import com.offsec.nethunter.SQL.KaliServicesSQL;
import com.offsec.nethunter.models.KaliServicesModel;
import com.offsec.nethunter.utils.KaliServicesDialogs;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.viewmodels.KaliServicesViewModel;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class KaliServicesFragment extends Fragment {
    public static final String TAG = "KaliServicesFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private Activity activity;
    private Context context;
    private Button refreshButton;
    private Button addButton;
    private Button deleteButton;
    private Button moveButton;
    private KaliServicesRecyclerViewAdapter kaliServicesRecyclerViewAdapter;
    private static int targetPositionId;

    public static KaliServicesFragment newInstance(int sectionNumber) {
        KaliServicesFragment fragment = new KaliServicesFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = getContext();
        this.activity = getActivity();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup parent, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.kaliservices, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        KaliServicesViewModel kaliServicesViewModel = new ViewModelProvider(this).get(KaliServicesViewModel.class);
        kaliServicesViewModel.init(context);

        kaliServicesRecyclerViewAdapter = new KaliServicesRecyclerViewAdapter(context, kaliServicesViewModel.getLiveDataKaliServicesModelList().getValue());
        RecyclerView recyclerViewServiceTitle = view.findViewById(R.id.f_kaliservices_recyclerviewServiceTitle);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        recyclerViewServiceTitle.setLayoutManager(linearLayoutManager);
        recyclerViewServiceTitle.setAdapter(kaliServicesRecyclerViewAdapter);
        TextView emptyState = view.findViewById(R.id.f_kaliservices_empty);
        kaliServicesViewModel.getLiveDataKaliServicesModelList().observe(getViewLifecycleOwner(), services -> {
            kaliServicesRecyclerViewAdapter.updateItems(services);
            emptyState.setVisibility(services == null || services.isEmpty() ? View.VISIBLE : View.GONE);
        });

        refreshButton = view.findViewById(R.id.f_kaliservices_refreshButton);
        addButton = view.findViewById(R.id.f_kaliservices_addItemButton);
        deleteButton = view.findViewById(R.id.f_kaliservices_deleteItemButton);
        moveButton = view.findViewById(R.id.f_kaliservices_moveItemButton);
        View servicesButtons = view.findViewById(R.id.f_kaliservices_btn_scrollView);

        onRefreshItemSetup();
        onAddItemSetup();
        onDeleteItemSetup();
        onMoveItemSetup();

        // WearOS optimisation
        SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        Boolean iswatch = sharedpreferences.getBoolean("running_on_wearos", false);
        if (iswatch) {
            view.findViewById(R.id.f_kaliservices_header).setVisibility(View.GONE);
            servicesButtons.setVisibility(View.GONE);
        }

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.kaliservices, menu);
                MenuItem searchItem = menu.findItem(R.id.f_kaliservices_action_search);
                MenuItem moreItem = menu.findItem(R.id.f_kaliservices_action_more);
                SearchView searchView = (SearchView) searchItem.getActionView();
                boolean isWatch = requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
                if (isWatch) searchItem.setVisible(false);
                if (searchView != null) {
                    searchView.setOnSearchClickListener(v -> moreItem.setVisible(false));
                    searchView.setOnCloseListener(() -> {
                        moreItem.setVisible(true);
                        return false;
                    });
                    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                        @Override public boolean onQueryTextSubmit(String query) { return false; }
                        @Override public boolean onQueryTextChange(String newText) {
                            if (kaliServicesRecyclerViewAdapter != null) {
                                kaliServicesRecyclerViewAdapter.getFilter().filter(newText);
                            }
                            return false;
                        }
                    });
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.f_kaliservices_action_more) {
                    showMoreSheet();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), androidx.lifecycle.Lifecycle.State.RESUMED);
    }

    @Override
    public void onStart() {
        super.onStart();
        KaliServicesData.getInstance().refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        refreshButton = null;
        addButton = null;
        deleteButton = null;
        moveButton = null;
        kaliServicesRecyclerViewAdapter = null;
    }

    private void showMoreSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(
                        requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
        View sheet = LayoutInflater.from(dialog.getContext())
                .inflate(R.layout.kaliservices_more_bottomsheet, null, false);
        sheet.findViewById(R.id.kaliservices_more_backup).setOnClickListener(v -> {
            dialog.dismiss();
            showDatabasePathDialog(false);
        });
        sheet.findViewById(R.id.kaliservices_more_restore).setOnClickListener(v -> {
            dialog.dismiss();
            showDatabasePathDialog(true);
        });
        sheet.findViewById(R.id.kaliservices_more_reset).setOnClickListener(v -> {
            dialog.dismiss();
            new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Home_Alert)
                    .setTitle("Reset Kali Services?")
                    .setMessage("Custom services and changes will be replaced with the defaults.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset", (d, which) -> KaliServicesData.getInstance()
                            .resetData(KaliServicesSQL.getInstance(requireContext())))
                    .show();
        });
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showDatabasePathDialog(boolean restore) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_Home_Alert);
        View form = LayoutInflater.from(builder.getContext())
                .inflate(R.layout.kaliservices_custom_dialog_view, null, false);
        TextView description = form.findViewById(R.id.f_kaliservices_adb_tv_title1);
        description.setText(restore ? R.string.kaliservices_full_path_restore_db
                : R.string.kaliservices_full_path_save_db);
        TextInputLayout pathLayout = form.findViewById(R.id.kaliservices_database_path_til);
        TextInputEditText pathEdit = form.findViewById(R.id.f_kaliservices_adb_et_storedpath);
        pathEdit.setText(String.format("%s/FragmentKaliServices", NhPaths.APP_SD_SQLBACKUP_PATH));

        AlertDialog dialog = builder
                .setTitle(restore ? "Restore database" : "Back up database")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(restore ? "Restore" : "Back up", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            confirm.setOnClickListener(v -> {
                pathLayout.setError(null);
                String path = pathEdit.getText() == null ? "" : pathEdit.getText().toString().trim();
                if (path.isEmpty()) {
                    pathLayout.setError("Enter a database file path");
                    return;
                }
                String result = restore
                        ? KaliServicesData.getInstance().restoreData(KaliServicesSQL.getInstance(requireContext()), path)
                        : KaliServicesData.getInstance().backupData(KaliServicesSQL.getInstance(requireContext()), path);
                if (result == null) {
                    NhPaths.showMessage(requireContext(), restore
                            ? "Kali Services database restored" : "Kali Services database backed up to " + path);
                    dialog.dismiss();
                } else {
                    pathLayout.setError(result);
                }
            });
        });
        dialog.show();
    }

    private void onRefreshItemSetup(){
        refreshButton.setOnClickListener(v -> KaliServicesData.getInstance().refreshData());
    }

    private void onAddItemSetup(){
        addButton.setOnClickListener(v -> {
            List<KaliServicesModel> kaliServicesModelList = KaliServicesData.getInstance().kaliServicesModelListFull;
            if (kaliServicesModelList == null) {
                NhPaths.showMessage(context, "Service list is empty. Please refresh and try again.");
                return;
            }
            com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(
                            requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
            final LayoutInflater inflater = LayoutInflater.from(dialog.getContext());
            final View promptViewAdd = inflater.inflate(R.layout.kaliservices_add_dialog_view, null);
            final EditText titleEditText = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_et_title);
            final EditText startCmdEditText = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_et_startcommand);
            final EditText stopCmdEditText = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_et_stopcommand);
            final EditText checkstatusCmdEditText = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_et_checkstatuscommand);
            final CheckBox runOnChrootStartCheckbox = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_checkbox_runonboot);
            final FloatingActionButton readmeButton1 = promptViewAdd.findViewById(R.id.f_kaliservices_add_btn_info_fab1);
            final FloatingActionButton readmeButton2 = promptViewAdd.findViewById(R.id.f_kaliservices_add_btn_info_fab2);
            final FloatingActionButton readmeButton3 = promptViewAdd.findViewById(R.id.f_kaliservices_add_btn_info_fab3);
            final FloatingActionButton readmeButton4 = promptViewAdd.findViewById(R.id.f_kaliservices_add_btn_info_fab4);
            final ExpandableChoiceView insertPositions = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_spr_positions);
            final ExpandableChoiceView insertTitles = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_spr_titles);

            ArrayList<String> serviceNameArrayList = new ArrayList<>();
            for (KaliServicesModel kaliServicesModel: kaliServicesModelList){
                serviceNameArrayList.add(kaliServicesModel.getServiceName());
            }

            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(dialog.getContext(), android.R.layout.simple_spinner_item, serviceNameArrayList);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            startCmdEditText.setText(R.string.service_servicename_start);
            stopCmdEditText.setText(R.string.service_servicename_stop);
            checkstatusCmdEditText.setText(R.string.service_servicename);

            readmeButton1.setOnClickListener(view -> KaliServicesDialogs.showInfo(
                    requireContext(), "Start command", R.string.kaliservices_howto_startservice));
            readmeButton2.setOnClickListener(view -> KaliServicesDialogs.showInfo(
                    requireContext(), "Stop command", R.string.kaliservices_howto_stopservice));
            readmeButton3.setOnClickListener(view -> KaliServicesDialogs.showInfo(
                    requireContext(), "Status check", R.string.kaliservices_howto_checkservice));
            readmeButton4.setOnClickListener(view -> KaliServicesDialogs.showInfo(
                    requireContext(), "Run on Chroot start", R.string.kaliservices_howto_runServiceOnBoot));

            insertPositions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    //if Insert to Top
                    if (position == 0) {
                        insertTitles.setVisibility(View.GONE);
                        targetPositionId = 1;
                        //if Insert to Bottom
                    } else if (position == 1) {
                        insertTitles.setVisibility(View.GONE);
                        targetPositionId = kaliServicesModelList.size() + 1;
                        //if Insert Before
                    } else if (position == 2) {
                        insertTitles.setVisibility(View.VISIBLE);
                        insertTitles.setAdapter(arrayAdapter);
                        insertTitles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                targetPositionId = position + 1;
                            }
                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                                // Do nothing
                            }
                        });
                        //if Insert After
                    } else {
                        insertTitles.setVisibility(View.VISIBLE);
                        insertTitles.setAdapter(arrayAdapter);
                        insertTitles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                targetPositionId = position + 2;
                            }
                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                                // Do nothing
                            }
                        });
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Do nothing
                }
            });

            dialog.setContentView(promptViewAdd);
            promptViewAdd.findViewById(R.id.kaliservices_add_cancel_btn)
                    .setOnClickListener(v1 -> dialog.dismiss());
            promptViewAdd.findViewById(R.id.kaliservices_add_save_btn)
                    .setOnClickListener(v1 -> {
                        if (titleEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, getString(R.string.error_title_empty));
                        } else if (startCmdEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, "Start Command cannot be empty");
                        } else if (stopCmdEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, "Stop Command cannot be empty");
                        } else if (checkstatusCmdEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, "Check Status Command cannot be empty");
                        } else {
                            ArrayList<String> dataArrayList = new ArrayList<>();
                            dataArrayList.add(titleEditText.getText().toString());
                            dataArrayList.add(startCmdEditText.getText().toString());
                            dataArrayList.add(stopCmdEditText.getText().toString());
                            dataArrayList.add(checkstatusCmdEditText.getText().toString());
                            dataArrayList.add(runOnChrootStartCheckbox.isChecked() ? "1" : "0");
                            KaliServicesData.getInstance().addData(targetPositionId, dataArrayList, KaliServicesSQL.getInstance(context));
                            dialog.dismiss();
                        }
                    });
            dialog.show();
        });
    }

    private void onDeleteItemSetup(){
        deleteButton.setOnClickListener(v -> {
            List<KaliServicesModel> kaliServicesModelList = KaliServicesData.getInstance().kaliServicesModelListFull;
            if (kaliServicesModelList == null || kaliServicesModelList.isEmpty()) {
                NhPaths.showMessage(context, "Nothing to delete.");
                return;
            }

            com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(
                            requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
            View sheet = LayoutInflater.from(dialog.getContext())
                    .inflate(R.layout.kaliservices_delete_dialog_view, null, false);
            android.widget.LinearLayout container = sheet.findViewById(R.id.kaliservices_delete_list_container);
            for (int i = 0; i < kaliServicesModelList.size(); i++) {
                com.google.android.material.checkbox.MaterialCheckBox checkbox =
                        new com.google.android.material.checkbox.MaterialCheckBox(dialog.getContext());
                checkbox.setText(kaliServicesModelList.get(i).getServiceName());
                checkbox.setTextColor(androidx.core.content.ContextCompat.getColor(
                        dialog.getContext(), R.color.home_nh_text));
                checkbox.setButtonTintList(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(
                                dialog.getContext(), R.color.home_nh_accent)));
                checkbox.setTag(i);
                container.addView(checkbox);
            }
            dialog.setContentView(sheet);
            sheet.findViewById(R.id.kaliservices_delete_cancel_btn)
                    .setOnClickListener(x -> dialog.dismiss());
            sheet.findViewById(R.id.kaliservices_delete_confirm_btn)
                    .setOnClickListener(x -> {
                        ArrayList<Integer> selectedPosition = new ArrayList<>();
                        ArrayList<Integer> selectedTargetIds = new ArrayList<>();
                        for (int i = 0; i < container.getChildCount(); i++) {
                            com.google.android.material.checkbox.MaterialCheckBox checkbox =
                                    (com.google.android.material.checkbox.MaterialCheckBox) container.getChildAt(i);
                            if (checkbox.isChecked()) {
                                int position = (Integer) checkbox.getTag();
                                selectedPosition.add(position);
                                selectedTargetIds.add(position + 1);
                            }
                        }
                        if (selectedPosition.isEmpty()) {
                            NhPaths.showMessage(context, "Select at least one service.");
                            return;
                        }
                        KaliServicesData.getInstance().deleteData(
                                selectedPosition, selectedTargetIds, KaliServicesSQL.getInstance(context));
                        NhPaths.showMessage(context, "Successfully deleted " + selectedPosition.size() + " items.");
                        dialog.dismiss();
                    });
            dialog.show();
        });
    }

    private void onMoveItemSetup(){
        moveButton.setOnClickListener(v -> {
            List<KaliServicesModel> kaliServicesModelList = KaliServicesData.getInstance().kaliServicesModelListFull;
            if (kaliServicesModelList == null || kaliServicesModelList.isEmpty()) {
                NhPaths.showMessage(context, "Nothing to move.");
                return;
            }
            com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(
                            requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
            final LayoutInflater inflater = LayoutInflater.from(dialog.getContext());
            final View promptViewMove = inflater.inflate(R.layout.kaliservices_move_dialog_view, null, false);
            final ExpandableChoiceView titlesBefore = promptViewMove.findViewById(R.id.f_kaliservices_move_adb_spr_titlesbefore);
            final ExpandableChoiceView titlesAfter = promptViewMove.findViewById(R.id.f_kaliservices_move_adb_spr_titlesafter);
            final ExpandableChoiceView actions = promptViewMove.findViewById(R.id.f_kaliservices_move_adb_spr_actions);

            ArrayList<String> serviceNameArrayList = new ArrayList<>();
            for (KaliServicesModel kaliServicesModel: kaliServicesModelList){
                serviceNameArrayList.add(kaliServicesModel.getServiceName());
            }

            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(dialog.getContext(), android.R.layout.simple_spinner_item, serviceNameArrayList);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            titlesBefore.setAdapter(arrayAdapter);
            titlesAfter.setAdapter(arrayAdapter);

            dialog.setContentView(promptViewMove);
            promptViewMove.findViewById(R.id.kaliservices_move_cancel_btn)
                    .setOnClickListener(v1 -> dialog.dismiss());
            promptViewMove.findViewById(R.id.kaliservices_move_confirm_btn)
                    .setOnClickListener(v1 -> {
                        int originalPositionIndex = titlesBefore.getSelectedItemPosition();
                        int targetPositionIndex = titlesAfter.getSelectedItemPosition();
                        if (originalPositionIndex == targetPositionIndex ||
                                (actions.getSelectedItemPosition() == 0 && targetPositionIndex == (originalPositionIndex + 1)) ||
                                (actions.getSelectedItemPosition() == 1 && targetPositionIndex == (originalPositionIndex - 1))) {
                            NhPaths.showMessage(context, "You are moving the item to the same position, nothing to be moved.");
                        } else {
                            if (actions.getSelectedItemPosition() == 1) targetPositionIndex += 1;
                            KaliServicesData.getInstance().moveData(originalPositionIndex, targetPositionIndex, KaliServicesSQL.getInstance(context));
                            NhPaths.showMessage(context, "Successfully moved item.");
                            dialog.dismiss();
                        }
                    });
            dialog.show();
        });
    }
}
