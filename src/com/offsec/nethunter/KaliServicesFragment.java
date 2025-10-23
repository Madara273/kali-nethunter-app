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
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.offsec.nethunter.RecyclerViewAdapter.KaliServicesRecyclerViewAdapter;
import com.offsec.nethunter.RecyclerViewAdapter.KaliServicesRecyclerViewAdapterDeleteItems;
import com.offsec.nethunter.RecyclerViewData.KaliServicesData;
import com.offsec.nethunter.SQL.KaliServicesSQL;
import com.offsec.nethunter.models.KaliServicesModel;
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
        kaliServicesViewModel.getLiveDataKaliServicesModelList().observe(getViewLifecycleOwner(), kaliServicesModelList -> kaliServicesRecyclerViewAdapter.notifyDataSetChanged());

        kaliServicesRecyclerViewAdapter = new KaliServicesRecyclerViewAdapter(context, kaliServicesViewModel.getLiveDataKaliServicesModelList().getValue());
        RecyclerView recyclerViewServiceTitle = view.findViewById(R.id.f_kaliservices_recyclerviewServiceTitle);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        recyclerViewServiceTitle.setLayoutManager(linearLayoutManager);
        recyclerViewServiceTitle.setAdapter(kaliServicesRecyclerViewAdapter);

        refreshButton = view.findViewById(R.id.f_kaliservices_refreshButton);
        addButton = view.findViewById(R.id.f_kaliservices_addItemButton);
        deleteButton = view.findViewById(R.id.f_kaliservices_deleteItemButton);
        moveButton = view.findViewById(R.id.f_kaliservices_moveItemButton);
        TextView servicesDesc = view.findViewById(R.id.f_kaliservices_banner);
        HorizontalScrollView servicesButtons = view.findViewById(R.id.f_kaliservices_btn_scrollView);

        onRefreshItemSetup();
        onAddItemSetup();
        onDeleteItemSetup();
        onMoveItemSetup();

        // WearOS optimisation
        SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        Boolean iswatch = sharedpreferences.getBoolean("running_on_wearos", false);
        if (iswatch) {
            servicesDesc.setVisibility(View.GONE);
            servicesButtons.setVisibility(View.GONE);
        }

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.kaliservices, menu);
                MenuItem searchItem = menu.findItem(R.id.f_kaliservices_action_search);
                SearchView searchView = (SearchView) searchItem.getActionView();
                boolean isWatch = requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
                if (isWatch) searchItem.setVisible(false);
                if (searchView != null) {
                    searchView.setOnSearchClickListener(v -> menu.setGroupVisible(R.id.f_kaliservices_menu_group1, false));
                    searchView.setOnCloseListener(() -> {
                        menu.setGroupVisible(R.id.f_kaliservices_menu_group1, true);
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
                LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                int id = item.getItemId();
                View promptView = li.inflate(R.layout.kaliservices_custom_dialog_view, null);
                TextView titleTextView = promptView.findViewById(R.id.f_kaliservices_adb_tv_title1);
                EditText storedpathEditText = promptView.findViewById(R.id.f_kaliservices_adb_et_storedpath);

                if (id == R.id.f_kaliservices_menu_backupDB) {
                    titleTextView.setText(R.string.kaliservices_full_path_save_db);
                    storedpathEditText.setText(String.format("%s/FragmentKaliServices", NhPaths.APP_SD_SQLBACKUP_PATH));
                    AlertDialog dlg = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                            .setView(promptView)
                            .setNegativeButton("Cancel", (d,w)->d.dismiss())
                            .setPositiveButton("OK", (d,w)->{})
                            .create();
                    dlg.setOnShowListener(dd -> {
                        Button ok = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
                        ok.setOnClickListener(v -> {
                            String res = KaliServicesData.getInstance()
                                    .backupData(KaliServicesSQL.getInstance(context), storedpathEditText.getText().toString());
                            if (res == null) {
                                NhPaths.showMessage(context, "db is successfully backup to " + storedpathEditText.getText());
                                dlg.dismiss();
                            } else {
                                new MaterialAlertDialogBuilder(context, R.style.DialogStyleCompat)
                                        .setTitle("Failed to backup the DB.")
                                        .setMessage(res)
                                        .create().show();
                            }
                        });
                    });
                    dlg.show();
                    return true;
                } else if (id == R.id.f_kaliservices_menu_restoreDB) {
                    titleTextView.setText(R.string.kaliservices_full_path_restore_db);
                    storedpathEditText.setText(String.format("%s/FragmentKaliServices", NhPaths.APP_SD_SQLBACKUP_PATH));
                    AlertDialog dlg = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                            .setView(promptView)
                            .setNegativeButton("Cancel", (d,w)->d.dismiss())
                            .setPositiveButton("OK", (d,w)->{})
                            .create();
                    dlg.setOnShowListener(dd -> {
                        Button ok = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
                        ok.setOnClickListener(v -> {
                            String res = KaliServicesData.getInstance()
                                    .restoreData(KaliServicesSQL.getInstance(context), storedpathEditText.getText().toString());
                            if (res == null) {
                                NhPaths.showMessage(context, "db is successfully restored to " + storedpathEditText.getText());
                                dlg.dismiss();
                            } else {
                                new MaterialAlertDialogBuilder(context, R.style.DialogStyleCompat)
                                        .setTitle("Failed to restore the DB.")
                                        .setMessage(res)
                                        .create().show();
                            }
                        });
                    });
                    dlg.show();
                    return true;
                } else if (id == R.id.f_kaliservices_menu_ResetToDefault) {
                    KaliServicesData.getInstance().resetData(KaliServicesSQL.getInstance(context));
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
            final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
            final Spinner insertPositions = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_spr_positions);
            final Spinner insertTitles = promptViewAdd.findViewById(R.id.f_kaliservices_add_adb_spr_titles);

            ArrayList<String> serviceNameArrayList = new ArrayList<>();
            for (KaliServicesModel kaliServicesModel: kaliServicesModelList){
                serviceNameArrayList.add(kaliServicesModel.getServiceName());
            }

            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, serviceNameArrayList);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            startCmdEditText.setText(R.string.service_servicename_start);
            stopCmdEditText.setText(R.string.service_servicename_stop);
            checkstatusCmdEditText.setText(R.string.service_servicename);

            readmeButton1.setOnClickListener(view -> {
                MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
                adb.setTitle("HOW TO USE:")
                        .setMessage(getString(R.string.kaliservices_howto_startservice))
                        .setNegativeButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog ad = adb.create();
                ad.setCancelable(true);
                ad.show();
            });

            readmeButton2.setOnClickListener(view -> {
                MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
                adb.setTitle("HOW TO USE:")
                        .setMessage(getString(R.string.kaliservices_howto_stopservice))
                        .setNegativeButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog ad = adb.create();
                ad.setCancelable(true);
                ad.show();
            });

            readmeButton3.setOnClickListener(view -> {
                MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
                adb.setTitle("HOW TO USE:")
                        .setMessage(getString(R.string.kaliservices_howto_checkservice))
                        .setNegativeButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog ad = adb.create();
                ad.setCancelable(true);
                ad.show();
            });

            readmeButton4.setOnClickListener(view -> {
                MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
                adb.setTitle("HOW TO USE:")
                        .setMessage(getString(R.string.kaliservices_howto_runServiceOnBoot))
                        .setNegativeButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog ad = adb.create();
                ad.setCancelable(true);
                ad.show();
            });

            insertPositions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    //if Insert to Top
                    if (position == 0) {
                        insertTitles.setVisibility(View.INVISIBLE);
                        targetPositionId = 1;
                        //if Insert to Bottom
                    } else if (position == 1) {
                        insertTitles.setVisibility(View.INVISIBLE);
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

            MaterialAlertDialogBuilder adbAdd = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
            adbAdd.setPositiveButton("OK", (dialog, which) -> { });
            final AlertDialog adAdd = adbAdd.create();
            adAdd.setView(promptViewAdd);
            adAdd.setCancelable(true);
            //If you want the dialog to stay open after clicking OK, you need to do it this way...
            adAdd.setOnShowListener(dialog -> {
                final Button buttonAdd = adAdd.getButton(DialogInterface.BUTTON_POSITIVE);
                buttonAdd.setOnClickListener(v1 -> {
                    if (titleEditText.getText().toString().isEmpty()){
                        NhPaths.showMessage(context, getString(R.string.error_title_empty));
                    } else if (startCmdEditText.getText().toString().isEmpty()){
                        NhPaths.showMessage(context, "Start Command cannot be empty");
                    } else if (stopCmdEditText.getText().toString().isEmpty()){
                        NhPaths.showMessage(context, "Stop Command cannot be empty");
                    } else if (checkstatusCmdEditText.getText().toString().isEmpty()){
                        NhPaths.showMessage(context, "Check Status Command cannot be empty");
                    } else {
                        ArrayList<String> dataArrayList = new ArrayList<>();
                        dataArrayList.add(titleEditText.getText().toString());
                        dataArrayList.add(startCmdEditText.getText().toString());
                        dataArrayList.add(stopCmdEditText.getText().toString());
                        dataArrayList.add(checkstatusCmdEditText.getText().toString());
                        dataArrayList.add(runOnChrootStartCheckbox.isChecked()?"1":"0");
                        KaliServicesData.getInstance().addData(targetPositionId, dataArrayList, KaliServicesSQL.getInstance(context));
                        adAdd.dismiss();
                    }
                });
            });
            adAdd.show();
        });
    }

    private void onDeleteItemSetup(){
        deleteButton.setOnClickListener(v -> {
            List<KaliServicesModel> kaliServicesModelList = KaliServicesData.getInstance().kaliServicesModelListFull;
            if (kaliServicesModelList == null) return;
            final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View promptViewDelete = inflater.inflate(R.layout.kaliservices_delete_dialog_view, null, false);
            final RecyclerView recyclerViewDeleteItem = promptViewDelete.findViewById(R.id.f_kaliservices_delete_recyclerview);
            KaliServicesRecyclerViewAdapterDeleteItems kaliServicesRecyclerViewAdapterDeleteItems = new KaliServicesRecyclerViewAdapterDeleteItems(context, kaliServicesModelList);

            LinearLayoutManager linearLayoutManagerDelete = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
            recyclerViewDeleteItem.setLayoutManager(linearLayoutManagerDelete);
            recyclerViewDeleteItem.setAdapter(kaliServicesRecyclerViewAdapterDeleteItems);

            MaterialAlertDialogBuilder adbDelete = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
            adbDelete.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            adbDelete.setPositiveButton("Delete", (dialog, which) -> { });
            final AlertDialog adDelete = adbDelete.create();
            adDelete.setMessage("Select the service you want to remove: ");
            adDelete.setView(promptViewDelete);
            adDelete.setCancelable(true);
            //If you want the dialog to stay open after clicking OK, you need to do it this way...
            adDelete.setOnShowListener(dialog -> {
                final Button buttonDelete = adDelete.getButton(DialogInterface.BUTTON_POSITIVE);
                buttonDelete.setOnClickListener(v1 -> {
                    RecyclerView.ViewHolder viewHolder;
                    ArrayList<Integer> selectedPosition = new ArrayList<>();
                    ArrayList<Integer> selectedTargetIds = new ArrayList<>();
                    for (int i = 0; i < recyclerViewDeleteItem.getChildCount(); i++) {
                        viewHolder = recyclerViewDeleteItem.findViewHolderForAdapterPosition(i);
                        if (viewHolder != null){
                            CheckBox box = viewHolder.itemView.findViewById(R.id.f_kaliservices_recyclerview_dialog_chkbox);
                            if (box.isChecked()){
                                selectedPosition.add(i);
                                selectedTargetIds.add(i+1);
                            }
                        }
                    }
                    if (!selectedPosition.isEmpty()) {
                        KaliServicesData.getInstance().deleteData(selectedPosition, selectedTargetIds, KaliServicesSQL.getInstance(context));
                        NhPaths.showMessage(context, "Successfully deleted " + selectedPosition.size() + " items.");
                        adDelete.dismiss();
                    } else {
                        NhPaths.showMessage(context, "Nothing to be deleted.");
                    }
                });
            });
            adDelete.show();
        });
    }

    private void onMoveItemSetup(){
        moveButton.setOnClickListener(v -> {
            List<KaliServicesModel> kaliServicesModelList = KaliServicesData.getInstance().kaliServicesModelListFull;
            if (kaliServicesModelList == null) return;
            final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View promptViewMove = inflater.inflate(R.layout.kaliservices_move_dialog_view, null, false);
            final Spinner titlesBefore = promptViewMove.findViewById(R.id.f_kaliservices_move_adb_spr_titlesbefore);
            final Spinner titlesAfter = promptViewMove.findViewById(R.id.f_kaliservices_move_adb_spr_titlesafter);
            final Spinner actions = promptViewMove.findViewById(R.id.f_kaliservices_move_adb_spr_actions);

            ArrayList<String> serviceNameArrayList = new ArrayList<>();
            for (KaliServicesModel kaliServicesModel: kaliServicesModelList){
                serviceNameArrayList.add(kaliServicesModel.getServiceName());
            }

            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, serviceNameArrayList);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            titlesBefore.setAdapter(arrayAdapter);
            titlesAfter.setAdapter(arrayAdapter);

            MaterialAlertDialogBuilder adbMove = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
            adbMove.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            adbMove.setPositiveButton("Move", (dialog, which) -> { });
            final AlertDialog adMove = adbMove.create();
            adMove.setView(promptViewMove);
            adMove.setCancelable(true);
            adMove.setOnShowListener(dialog -> {
                final Button buttonMove = adMove.getButton(DialogInterface.BUTTON_POSITIVE);
                buttonMove.setOnClickListener(v1 -> {
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
                        adMove.dismiss();
                    }
                });
            });
            adMove.show();
        });
    }
}