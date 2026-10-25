package com.offsec.nethunter.RecyclerViewAdapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.offsec.nethunter.R;
import com.offsec.nethunter.RecyclerViewData.NethunterData;
import com.offsec.nethunter.SQL.NethunterSQL;
import com.offsec.nethunter.models.NethunterModel;
import com.offsec.nethunter.utils.NhPaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NethunterRecyclerViewAdapter extends RecyclerView.Adapter<NethunterRecyclerViewAdapter.ItemViewHolder> implements Filterable {
    public static final String TAG = "NethunterRecyclerView";
    private final Context context;
    private List<NethunterModel> nethunterModelList;
    private List<RowSnapshot> rowSnapshots;

    public NethunterRecyclerViewAdapter(Context context, List<NethunterModel> nethunterModelList) {
        this.context = context;
        this.nethunterModelList = new ArrayList<>(nethunterModelList);
        this.rowSnapshots = snapshotRows(nethunterModelList);
    }

    public void updateItems(List<NethunterModel> items) {
        List<NethunterModel> nextItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
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
                return oldRow.title.equals(newRow.title) && Arrays.equals(oldRow.results, newRow.results);
            }
        });
        nethunterModelList = nextItems;
        rowSnapshots = nextSnapshots;
        changes.dispatchUpdatesTo(this);
    }

    private static List<RowSnapshot> snapshotRows(List<NethunterModel> items) {
        List<RowSnapshot> snapshots = new ArrayList<>(items.size());
        for (NethunterModel item : items) {
            snapshots.add(new RowSnapshot(item));
        }
        return snapshots;
    }

    private static final class RowSnapshot {
        final NethunterModel model;
        final String title;
        final String[] results;

        RowSnapshot(NethunterModel model) {
            this.model = model;
            title = model.getTitle();
            results = model.getResult().clone();
        }
    }

    @NonNull
    @Override
    public NethunterRecyclerViewAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.nethunter_recyclerview_main, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NethunterRecyclerViewAdapter.ItemViewHolder holder, int position) {
        NethunterModel boundItem = nethunterModelList.get(position);
        holder.titleTextView.setText(boundItem.getTitle());
        String[] results = boundItem.getResult();
        for (int i = 0; i < results.length; i++) {
            View resultView;
            if (i < holder.resultContainer.getChildCount()) {
                resultView = holder.resultContainer.getChildAt(i);
            } else {
                resultView = LayoutInflater.from(context).inflate(
                        R.layout.nethunter_recyclerview_result, holder.resultContainer, false);
                holder.resultContainer.addView(resultView);
            }
            TextView resultTextView = resultView.findViewById(R.id.f_nethunter_item_result_tv);
            resultTextView.setText(results[i]);
            resultTextView.setOnLongClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("text", resultTextView.getText());
                clipboard.setPrimaryClip(clip);
                NhPaths.showMessage(context, "Copied to clipboard: " + resultTextView.getText());
                return true;
            });
        }
        while (holder.resultContainer.getChildCount() > results.length) {
            holder.resultContainer.removeViewAt(holder.resultContainer.getChildCount() - 1);
        }
        holder.titleTextView.setOnClickListener(v -> {
            int fullIndex = NethunterData.getInstance().nethunterModelListFull.indexOf(boundItem);
            if (fullIndex >= 0) NethunterData.getInstance().runCommandforItem(fullIndex);
        });
        holder.titleTextView.setOnLongClickListener(v -> {
            final LayoutInflater mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View promptViewEdit = mInflater.inflate(R.layout.nethunter_edit_dialog_view, null);
            final EditText titleEditText = promptViewEdit.findViewById(R.id.f_nethunter_edit_adb_et_title);
            final EditText cmdEditText = promptViewEdit.findViewById(R.id.f_nethunter_edit_adb_et_command);
            final EditText delimiterEditText = promptViewEdit.findViewById(R.id.f_nethunter_edit_adb_et_delimiter);
            final CheckBox runOnCreateCheckbox = promptViewEdit.findViewById(R.id.f_nethunters_edit_adb_checkbox_runoncreate);
            final FloatingActionButton readmeButton1 = promptViewEdit.findViewById(R.id.f_nethunter_edit_btn_info_fab1);
            final FloatingActionButton readmeButton2 = promptViewEdit.findViewById(R.id.f_nethunter_edit_btn_info_fab2);
            final FloatingActionButton readmeButton3 = promptViewEdit.findViewById(R.id.f_nethunter_edit_btn_info_fab3);
            readmeButton1.setOnClickListener(view -> {
                MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(context, R.style.DialogStyleCompat);
                adb.setTitle("HOW TO USE:")
                        .setMessage(context.getString(R.string.nethunter_howtouse_cmd))
                        .setNegativeButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog ad = adb.create();
                ad.setCancelable(true);
                ad.show();
            });

            readmeButton2.setOnClickListener(view -> {
                MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(context, R.style.DialogStyleCompat);
                adb.setTitle("HOW TO USE:")
                        .setMessage(context.getString(R.string.nethunter_howtouse_delimiter))
                        .setNegativeButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog ad = adb.create();
                ad.setCancelable(true);
                ad.show();
            });

            readmeButton3.setOnClickListener(view -> {
                MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(context, R.style.DialogStyleCompat);
                adb.setTitle("HOW TO USE:")
                        .setMessage(context.getString(R.string.nethunter_howtouse_runoncreate))
                        .setNegativeButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog ad = adb.create();
                ad.setCancelable(true);
                ad.show();
            });
            titleEditText.setText(boundItem.getTitle());
            cmdEditText.setText(boundItem.getCommand());
            delimiterEditText.setText(boundItem.getDelimiter());
            runOnCreateCheckbox.setChecked(boundItem.getRunOnCreate().equals("1"));

            MaterialAlertDialogBuilder adb = new MaterialAlertDialogBuilder(context, R.style.DialogStyleCompat);
            adb.setPositiveButton("Apply", (dialog, which) -> { });
            final AlertDialog ad = adb.create();
            ad.setView(promptViewEdit);
            ad.setCancelable(true);
            ad.setOnShowListener(dialog -> {
                final Button buttonEdit = ad.getButton(DialogInterface.BUTTON_POSITIVE);
                buttonEdit.setOnClickListener(v1 -> {
                    if (titleEditText.getText().toString().isEmpty()){
                        NhPaths.showMessage(context, "Title cannot be empty");
                    } else if (cmdEditText.getText().toString().isEmpty()){
                        NhPaths.showMessage(context, "Command cannot be empty");
                    } else if (delimiterEditText.getText().toString().isEmpty()){
                        NhPaths.showMessage(context, "Delimiter cannot be empty");
                    } else {
                        ArrayList<String> dataArrayList = new ArrayList<>();
                        dataArrayList.add(titleEditText.getText().toString());
                        dataArrayList.add(cmdEditText.getText().toString());
                        dataArrayList.add(delimiterEditText.getText().toString());
                        dataArrayList.add(runOnCreateCheckbox.isChecked()?"1":"0");
                        int fullIndex = NethunterData.getInstance().nethunterModelListFull.indexOf(boundItem);
                        if (fullIndex < 0) {
                            NhPaths.showMessage(context, "Home item changed. Please try again.");
                            return;
                        }
                        NethunterData.getInstance().editData(fullIndex, dataArrayList, NethunterSQL.getInstance(context));
                        ad.dismiss();
                    }
                });
            });
            ad.show();
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return nethunterModelList.size();
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(position);
    }

    @Override
    public Filter getFilter() {
        return NethunterModelListFilter;
    }

    private final Filter NethunterModelListFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            if (constraint == null || constraint.length() == 0) {
                results.values = new ArrayList<>(NethunterData.getInstance().nethunterModelListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                List<NethunterModel> tempNethunterModelList = new ArrayList<>();
                for (NethunterModel nethunterModel: NethunterData.getInstance().nethunterModelListFull) {
                    if (nethunterModel.getTitle().toLowerCase().contains(filterPattern)) {
                        tempNethunterModelList.add(nethunterModel);
                    }
                }
                results.values = tempNethunterModelList;
            }
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            List<NethunterModel> live = NethunterData.getInstance().getNethunterModels().getValue();
            if (live == null || results.values == null) return;
            if (results.values instanceof List<?>) {
                live.clear();
                for (Object o : (List<?>) results.values) {
                    if (o instanceof NethunterModel) {
                        live.add((NethunterModel) o);
                    }
                }
                NethunterData.getInstance().getNethunterModels().postValue(live);
            }
        }
    };

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final LinearLayout resultContainer;

        public ItemViewHolder(View view) {
            super(view);
            titleTextView = view.findViewById(R.id.f_nethunter_item_title_tv);
            resultContainer = view.findViewById(R.id.f_nethunter_item_result_recyclerview);
        }
    }
}
