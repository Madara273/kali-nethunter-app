package com.offsec.nethunter.RecyclerViewAdapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import com.offsec.nethunter.widget.ExpandableChoiceView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.offsec.nethunter.R;
import com.offsec.nethunter.RecyclerViewData.CustomCommandsData;
import com.offsec.nethunter.SQL.CustomCommandsSQL;
import com.offsec.nethunter.models.CustomCommandsModel;
import com.offsec.nethunter.utils.NhPaths;

import java.util.ArrayList;
import java.util.List;

public class CustomCommandsRecyclerViewAdapter extends RecyclerView.Adapter<CustomCommandsRecyclerViewAdapter.ItemViewHolder> implements Filterable {
    public static final String TAG = "CustomCommandsRecyclerView";
    private final Context context;
    private List<CustomCommandsModel> customCommandsModelList;
    private List<RowSnapshot> rowSnapshots;

    public CustomCommandsRecyclerViewAdapter(Context context, List<CustomCommandsModel> customCommandsModelList) {
        this.context = context;
        this.customCommandsModelList = customCommandsModelList == null
                ? new ArrayList<>() : new ArrayList<>(customCommandsModelList);
        this.rowSnapshots = snapshotRows(this.customCommandsModelList);
    }

    public void updateItems(List<CustomCommandsModel> items) {
        List<CustomCommandsModel> nextItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        List<RowSnapshot> nextSnapshots = snapshotRows(nextItems);
        List<RowSnapshot> previousSnapshots = rowSnapshots;
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return previousSnapshots.size(); }
            @Override public int getNewListSize() { return nextSnapshots.size(); }
            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previousSnapshots.get(oldPosition).model == nextSnapshots.get(newPosition).model;
            }
            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                RowSnapshot oldRow = previousSnapshots.get(oldPosition);
                RowSnapshot newRow = nextSnapshots.get(newPosition);
                return oldRow.label.equals(newRow.label)
                        && oldRow.runtime.equals(newRow.runtime)
                        && oldRow.mode.equals(newRow.mode)
                        && oldRow.runOnBoot.equals(newRow.runOnBoot);
            }
        });
        customCommandsModelList = nextItems;
        rowSnapshots = nextSnapshots;
        changes.dispatchUpdatesTo(this);
    }

    private static List<RowSnapshot> snapshotRows(List<CustomCommandsModel> items) {
        List<RowSnapshot> snapshots = new ArrayList<>(items.size());
        for (CustomCommandsModel item : items) snapshots.add(new RowSnapshot(item));
        return snapshots;
    }

    private static final class RowSnapshot {
        final CustomCommandsModel model;
        final String label;
        final String runtime;
        final String mode;
        final String runOnBoot;

        RowSnapshot(CustomCommandsModel model) {
            this.model = model;
            label = model.getCommandLabel();
            runtime = model.getRuntimeEnv();
            mode = model.getExecutionMode();
            runOnBoot = model.getRunOnBoot();
        }
    }

    @NonNull
    @Override
    public CustomCommandsRecyclerViewAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(context).inflate(R.layout.customcommands_recyclerview_main, viewGroup, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ItemViewHolder itemViewHolder, int position) {
        CustomCommandsModel boundCommand = customCommandsModelList.get(position);
        itemViewHolder.commandLabelTextView.setText(boundCommand.getCommandLabel());
        itemViewHolder.execEnvTextView.setText(boundCommand.getRuntimeEnv());
        itemViewHolder.execModeTextView.setText(boundCommand.getExecutionMode());
        itemViewHolder.runOnBootTextView.setText(boundCommand.getRunOnBoot().equals("1") ? "yes" : "no");
        itemViewHolder.commandLabelTextView.setOnLongClickListener(v -> {
            BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.ThemeOverlay_Home_BottomSheet);
            final View promptViewEdit = LayoutInflater.from(dialog.getContext())
                    .inflate(R.layout.customcommands_edit_dialog_view, null, false);
            final EditText commandLabelEditText = promptViewEdit.findViewById(R.id.f_customcommands_edit_adb_et_commandlabel);
            final EditText commandEditText = promptViewEdit.findViewById(R.id.f_customcommands_edit_adb_et_command);
            final ExpandableChoiceView sendToSpinner = promptViewEdit.findViewById(R.id.f_customcommands_edit_adb_spr_sendto);
            final ExpandableChoiceView execModeSpinner = promptViewEdit.findViewById(R.id.f_customcommands_edit_adb_spr_execmode);
            final CheckBox runOnBootCheckbox = promptViewEdit.findViewById(R.id.f_customcommands_edit_adb_checkbox_runonboot);

            commandLabelEditText.setText(boundCommand.getCommandLabel());
            commandEditText.setText(boundCommand.getCommand());
            sendToSpinner.setSelection(boundCommand.getRuntimeEnv().equals("android") ? 0 : 1);
            execModeSpinner.setSelection(boundCommand.getExecutionMode().equals("interactive") ? 0 : 1);
            runOnBootCheckbox.setChecked(boundCommand.getRunOnBoot().equals("1"));

            dialog.setContentView(promptViewEdit);
            promptViewEdit.findViewById(R.id.customcommands_edit_cancel_btn)
                    .setOnClickListener(v1 -> dialog.dismiss());
            promptViewEdit.findViewById(R.id.customcommands_edit_save_btn)
                    .setOnClickListener(v1 -> {
                        if (commandLabelEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, "Label cannot be empty");
                        } else if (commandEditText.getText().toString().isEmpty()) {
                            NhPaths.showMessage(context, "Command string cannot be empty");
                        } else {
                            ArrayList<String> dataArrayList = new ArrayList<>();
                            dataArrayList.add(commandLabelEditText.getText().toString());
                            dataArrayList.add(commandEditText.getText().toString());
                            dataArrayList.add(sendToSpinner.getSelectedItem().toString());
                            dataArrayList.add(execModeSpinner.getSelectedItem().toString());
                            dataArrayList.add(runOnBootCheckbox.isChecked() ? "1" : "0");
                            int fullIndex = CustomCommandsData.getInstance().customCommandsModelListFull.indexOf(boundCommand);
                            if (fullIndex < 0) {
                                NhPaths.showMessage(context, "Command list changed. Please try again.");
                                return;
                            }
                            CustomCommandsData.getInstance().editData(fullIndex, dataArrayList, CustomCommandsSQL.getInstance(context));
                            dialog.dismiss();
                        }
                    });
            dialog.show();
            return true;
        });
        itemViewHolder.runButton.setOnClickListener(v -> {
            int fullIndex = CustomCommandsData.getInstance().customCommandsModelListFull.indexOf(boundCommand);
            if (fullIndex >= 0) CustomCommandsData.getInstance().runCommandforitem(fullIndex, context);
        });
    }

    @Override
    public int getItemCount() {
        return customCommandsModelList.size();
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(position);
    }

    @Override
    public Filter getFilter() {
        return CustomCommandsModelListFilter;
    }

    private final Filter CustomCommandsModelListFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            if (constraint == null || constraint.length() == 0) {
                results.values = new ArrayList<>(CustomCommandsData.getInstance().customCommandsModelListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                List<CustomCommandsModel> tempCustomCommandsModelList = new ArrayList<>();
                for (CustomCommandsModel customCommandsModel : CustomCommandsData.getInstance().customCommandsModelListFull) {
                    if (customCommandsModel.getCommandLabel().toLowerCase().contains(filterPattern)) {
                        tempCustomCommandsModelList.add(customCommandsModel);
                    }
                }
                results.values = tempCustomCommandsModelList;
            }
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            List<CustomCommandsModel> live = CustomCommandsData.getInstance().getCustomCommandsModels().getValue();
            if (live == null || results.values == null) return;
            if (results.values instanceof List<?>) {
                live.clear();
                for (Object o : (List<?>) results.values) {
                    if (o instanceof CustomCommandsModel) {
                        live.add((CustomCommandsModel) o);
                    }
                }
                CustomCommandsData.getInstance().getCustomCommandsModels().postValue(live);
            }
        }
    };

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView commandLabelTextView;
        private final TextView execEnvTextView;
        private final TextView execModeTextView;
        private final TextView runOnBootTextView;
        private final Button runButton;

        private ItemViewHolder(View view) {
            super(view);
            commandLabelTextView = view.findViewById(R.id.f_customcommands_recyclerview_tv_cmdlabel);
            execEnvTextView = view.findViewById(R.id.f_customcommands_recyclerview_tv_execenvironment);
            execModeTextView = view.findViewById(R.id.f_customcommands_recyclerview_tv_execmode);
            runOnBootTextView = view.findViewById(R.id.f_customcommands_recyclerview_tv_runonboot);
            runButton = view.findViewById(R.id.f_customcommands_recyclerview_btn_run);
        }
    }
}
