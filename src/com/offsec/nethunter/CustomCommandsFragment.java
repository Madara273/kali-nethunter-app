package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.offsec.nethunter.RecyclerViewAdapter.CustomCommandsRecyclerViewAdapter;
import com.offsec.nethunter.RecyclerViewData.CustomCommandsData;
import com.offsec.nethunter.SQL.CustomCommandsSQL;
import com.offsec.nethunter.models.CustomCommandsModel;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.viewmodels.CustomCommandsViewModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CustomCommandsFragment extends Fragment {
    private static final String ARG_SECTION_NUMBER = "section_number";
    public static final String TAG = "CustomCommandsFragment";
    private CustomCommandsRecyclerViewAdapter customCommandsRecyclerViewAdapter;
    private Context context;
    private Activity activity;
    private Button addButton;
    private Button deleteButton;
    private Button moveButton;
    public static int targetPositionId;

    public CustomCommandsFragment() {
        Log.d(TAG, "CustomCommandsFragment: init ");
    }

    public static CustomCommandsFragment newInstance(int sectionNumber) {
        CustomCommandsFragment fragment = new CustomCommandsFragment();
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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.customcommands, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CustomCommandsViewModel customCommandsViewModel = new ViewModelProvider(this).get(CustomCommandsViewModel.class);
        customCommandsViewModel.init(context);
        customCommandsRecyclerViewAdapter =
                new CustomCommandsRecyclerViewAdapter(context, customCommandsViewModel.getLiveDataCustomCommandsModelList().getValue());
        RecyclerView recyclerView = view.findViewById(R.id.f_customcommands_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(customCommandsRecyclerViewAdapter);
        TextView emptyState = view.findViewById(R.id.f_customcommands_empty);
        emptyState.setVisibility(customCommandsRecyclerViewAdapter.getItemCount() == 0
                ? View.VISIBLE : View.GONE);
        customCommandsViewModel.getLiveDataCustomCommandsModelList().observe(getViewLifecycleOwner(), commands -> {
            customCommandsRecyclerViewAdapter.updateItems(commands);
            emptyState.setVisibility(commands == null || commands.isEmpty() ? View.VISIBLE : View.GONE);
        });

        addButton = view.findViewById(R.id.f_customcommands_addItemButton);
        deleteButton = view.findViewById(R.id.f_customcommands_deleteItemButton);
        moveButton = view.findViewById(R.id.f_customcommands_moveItemButton);

        onAddItemSetup();
        onDeleteItemSetup();
        onMoveItemSetup();

        SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        boolean iswatchPref = sharedpreferences.getBoolean("running_on_wearos", false);
        if (iswatchPref) view.findViewById(R.id.f_customcommands_header).setVisibility(View.GONE);

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.custom_commands, menu);
                MenuItem searchItem = menu.findItem(R.id.f_customcommands_action_search);
                MenuItem moreItem = menu.findItem(R.id.f_customcommands_action_more);
                boolean iswatch = requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
                if (iswatch) searchItem.setVisible(false);

                SearchView searchView = (SearchView) searchItem.getActionView();
                if (searchView != null) {
                    searchView.setOnSearchClickListener(v1 -> moreItem.setVisible(false));
                    searchView.setOnCloseListener(() -> {
                        moreItem.setVisible(true);
                        return false;
                    });
                    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                        @Override public boolean onQueryTextSubmit(String query) {
                            customCommandsRecyclerViewAdapter.getFilter().filter(query);
                            return false;
                        }
                        @Override public boolean onQueryTextChange(String newText) {
                            customCommandsRecyclerViewAdapter.getFilter().filter(newText);
                            return false;
                        }
                    });
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.f_customcommands_action_more) {
                    showMoreSheet();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        addButton = null;
        deleteButton = null;
        moveButton = null;
        customCommandsRecyclerViewAdapter = null;
    }

    private void showMoreSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(
                        requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
        View sheet = LayoutInflater.from(dialog.getContext())
                .inflate(R.layout.customcommands_more_bottomsheet, null, false);
        sheet.findViewById(R.id.customcommands_more_backup).setOnClickListener(v -> {
            dialog.dismiss();
            showDatabasePathDialog(false);
        });
        sheet.findViewById(R.id.customcommands_more_restore).setOnClickListener(v -> {
            dialog.dismiss();
            showDatabasePathDialog(true);
        });
        sheet.findViewById(R.id.customcommands_more_reset).setOnClickListener(v -> {
            dialog.dismiss();
            new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Home_Alert)
                    .setTitle("Reset Custom Commands?")
                    .setMessage("Custom commands and changes will be replaced with the defaults.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset", (d, which) -> CustomCommandsData.getInstance()
                            .resetData(CustomCommandsSQL.getInstance(requireContext())))
                    .show();
        });
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showDatabasePathDialog(boolean restore) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
                requireContext(), R.style.ThemeOverlay_Home_Alert);
        View form = LayoutInflater.from(builder.getContext())
                .inflate(R.layout.customcommands_custom_dialog_view, null, false);
        TextView description = form.findViewById(R.id.f_customcommands_adb_tv_title1);
        description.setText(restore ? R.string.customcommands_full_path_db_restore
                : R.string.customcommands_full_path_db_save);
        TextInputLayout pathLayout = form.findViewById(R.id.customcommands_database_path_til);
        TextInputEditText pathEdit = form.findViewById(R.id.f_customcommands_adb_et_storedpath);
        pathEdit.setText(String.format("%s/FragmentCustomCommands", NhPaths.APP_SD_SQLBACKUP_PATH));

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
                        ? CustomCommandsData.getInstance().restoreData(CustomCommandsSQL.getInstance(requireContext()), path)
                        : CustomCommandsData.getInstance().backupData(CustomCommandsSQL.getInstance(requireContext()), path);
                if (result == null) {
                    NhPaths.showMessage(requireContext(), restore
                            ? "Custom Commands database restored" : "Custom Commands database backed up to " + path);
                    dialog.dismiss();
                } else {
                    pathLayout.setError(result);
                }
            });
        });
        dialog.show();
    }

    private void onAddItemSetup() {
        addButton.setOnClickListener(v -> {
            List<CustomCommandsModel> customCommandsModelList = CustomCommandsData.getInstance().customCommandsModelListFull;
            if (customCommandsModelList == null) return;
            BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
            final View promptViewAdd = LayoutInflater.from(dialog.getContext())
                    .inflate(R.layout.customcommands_add_dialog_view, null, false);
            final EditText commandLabelEditText = promptViewAdd.findViewById(R.id.f_customcommands_add_adb_et_label);
            final EditText commandEditText = promptViewAdd.findViewById(R.id.f_customcommands_add_adb_et_command);
            final ExpandableChoiceView sendToSpinner = promptViewAdd.findViewById(R.id.f_customcommands_add_adb_spr_sendto);
            final ExpandableChoiceView execModeSpinner = promptViewAdd.findViewById(R.id.f_customcommands_add_adb_spr_execmode);
            final CheckBox runOnBootCheckbox = promptViewAdd.findViewById(R.id.f_customcommands_add_adb_checkbox_runonboot);
            final ExpandableChoiceView insertPositions = promptViewAdd.findViewById(R.id.f_customcommands_add_adb_spr_positions);
            final ExpandableChoiceView insertLabels = promptViewAdd.findViewById(R.id.f_customcommands_add_adb_spr_labels);
            targetPositionId = 1;

            ArrayList<String> commandLabelArrayList = new ArrayList<>();
            for (CustomCommandsModel customCommandsModel: customCommandsModelList){
                commandLabelArrayList.add(customCommandsModel.getCommandLabel());
            }

            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(dialog.getContext(), android.R.layout.simple_spinner_item, commandLabelArrayList);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            insertPositions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    //if Insert to Top
                    if (position == 0) {
                        insertLabels.setVisibility(View.GONE);
                        targetPositionId = 1;
                        //if Insert to Bottom
                    } else if (position == 1) {
                        insertLabels.setVisibility(View.GONE);
                        targetPositionId = customCommandsModelList.size() + 1;
                        //if Insert Before
                    } else if (position == 2 && !customCommandsModelList.isEmpty()) {
                        insertLabels.setVisibility(View.VISIBLE);
                        insertLabels.setAdapter(arrayAdapter);
                        insertLabels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                targetPositionId = position + 1;
                            }
                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                                Log.d(TAG, "onNothingSelected: Nothing selected.");
                            }
                        });
                        //if Insert After
                    } else if (!customCommandsModelList.isEmpty()) {
                        insertLabels.setVisibility(View.VISIBLE);
                        insertLabels.setAdapter(arrayAdapter);
                        insertLabels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                targetPositionId = position + 2;
                            }
                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                                Log.d(TAG, "onNothingSelected: Nothing selected.");
                            }
                        });
                    } else {
                        insertLabels.setVisibility(View.GONE);
                        targetPositionId = 1;
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    Log.d(TAG, "onNothingSelected: Nothing Selected");
                }
            });

            dialog.setContentView(promptViewAdd);
            promptViewAdd.findViewById(R.id.customcommands_add_cancel_btn)
                    .setOnClickListener(v1 -> dialog.dismiss());
            promptViewAdd.findViewById(R.id.customcommands_add_save_btn)
                    .setOnClickListener(v1 -> {
                        if (commandLabelEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, "Label cannot be empty");
                        } else if (commandEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, "Command String cannot be empty");
                        } else {
                            ArrayList<String> dataArrayList = new ArrayList<>();
                            dataArrayList.add(commandLabelEditText.getText().toString());
                            dataArrayList.add(commandEditText.getText().toString());
                            dataArrayList.add(sendToSpinner.getSelectedItem().toString());
                            dataArrayList.add(execModeSpinner.getSelectedItem().toString());
                            dataArrayList.add(runOnBootCheckbox.isChecked() ? "1" : "0");
                            CustomCommandsData.getInstance().addData(targetPositionId, dataArrayList, CustomCommandsSQL.getInstance(context));
                            dialog.dismiss();
                        }
                    });
            dialog.show();
        });
    }

    private void onDeleteItemSetup(){
        deleteButton.setOnClickListener(v -> {
            List<CustomCommandsModel> customCommandsModelList = CustomCommandsData.getInstance().customCommandsModelListFull;
            if (customCommandsModelList == null || customCommandsModelList.isEmpty()) {
                NhPaths.showMessage(context, "Nothing to delete.");
                return;
            }

            com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(
                            requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
            View sheet = LayoutInflater.from(dialog.getContext())
                    .inflate(R.layout.customcommands_delete_dialog_view, null, false);
            android.widget.LinearLayout container = sheet.findViewById(R.id.customcommands_delete_list_container);
            for (int i = 0; i < customCommandsModelList.size(); i++) {
                com.google.android.material.checkbox.MaterialCheckBox checkbox =
                        new com.google.android.material.checkbox.MaterialCheckBox(dialog.getContext());
                checkbox.setText(customCommandsModelList.get(i).getCommandLabel());
                checkbox.setTextColor(androidx.core.content.ContextCompat.getColor(
                        dialog.getContext(), R.color.home_nh_text));
                checkbox.setButtonTintList(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(
                                dialog.getContext(), R.color.home_nh_accent)));
                checkbox.setTag(i);
                container.addView(checkbox);
            }
            dialog.setContentView(sheet);
            sheet.findViewById(R.id.customcommands_delete_cancel_btn)
                    .setOnClickListener(x -> dialog.dismiss());
            sheet.findViewById(R.id.customcommands_delete_confirm_btn)
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
                            NhPaths.showMessage(context, "Select at least one command.");
                            return;
                        }
                        CustomCommandsData.getInstance().deleteData(
                                selectedPosition, selectedTargetIds, CustomCommandsSQL.getInstance(context));
                        NhPaths.showMessage(context, "Successfully deleted " + selectedPosition.size() + " items.");
                        dialog.dismiss();
                    });
            dialog.show();
        });
    }

    private void onMoveItemSetup(){
        moveButton.setOnClickListener(v -> {
            List<CustomCommandsModel> customCommandsModelList = CustomCommandsData.getInstance().customCommandsModelListFull;
            if (customCommandsModelList == null || customCommandsModelList.isEmpty()) {
                NhPaths.showMessage(context, "Nothing to move.");
                return;
            }
            com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(
                            requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
            final LayoutInflater inflater = LayoutInflater.from(dialog.getContext());
            final View promptViewMove = inflater.inflate(R.layout.customcommands_move_dialog_view, null, false);
            final ExpandableChoiceView titlesBefore = promptViewMove.findViewById(R.id.f_customcommands_move_adb_spr_labelsbefore);
            final ExpandableChoiceView titlesAfter = promptViewMove.findViewById(R.id.f_customcommands_move_adb_spr_labelsafter);
            final ExpandableChoiceView actions = promptViewMove.findViewById(R.id.f_customcommands_move_adb_spr_actions);

            ArrayList<String> commandLabelArrayList = new ArrayList<>();
            for (CustomCommandsModel command : customCommandsModelList) {
                commandLabelArrayList.add(command.getCommandLabel());
            }

            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(dialog.getContext(), android.R.layout.simple_spinner_item, commandLabelArrayList);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            titlesBefore.setAdapter(arrayAdapter);
            titlesAfter.setAdapter(arrayAdapter);

            dialog.setContentView(promptViewMove);
            promptViewMove.findViewById(R.id.customcommands_move_cancel_btn)
                    .setOnClickListener(v1 -> dialog.dismiss());
            promptViewMove.findViewById(R.id.customcommands_move_confirm_btn)
                    .setOnClickListener(v1 -> {
                        int originalPositionIndex = titlesBefore.getSelectedItemPosition();
                        int targetPositionIndex = titlesAfter.getSelectedItemPosition();
                        if (originalPositionIndex == targetPositionIndex ||
                                (actions.getSelectedItemPosition() == 0 && targetPositionIndex == (originalPositionIndex + 1)) ||
                                (actions.getSelectedItemPosition() == 1 && targetPositionIndex == (originalPositionIndex - 1))) {
                            NhPaths.showMessage(context, "You are moving the item to the same position, nothing to be moved.");
                        } else {
                            if (actions.getSelectedItemPosition() == 1) targetPositionIndex += 1;
                            CustomCommandsData.getInstance().moveData(originalPositionIndex, targetPositionIndex, CustomCommandsSQL.getInstance(context));
                            NhPaths.showMessage(context, "Successfully moved item.");
                            dialog.dismiss();
                        }
                    });
            dialog.show();
        });
    }
}
