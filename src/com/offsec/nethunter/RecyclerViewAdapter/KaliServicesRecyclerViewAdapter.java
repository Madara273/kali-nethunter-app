package com.offsec.nethunter.RecyclerViewAdapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.offsec.nethunter.R;
import com.offsec.nethunter.RecyclerViewData.KaliServicesData;
import com.offsec.nethunter.SQL.KaliServicesSQL;
import com.offsec.nethunter.models.KaliServicesModel;
import com.offsec.nethunter.utils.KaliServicesDialogs;
import com.offsec.nethunter.utils.NhPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KaliServicesRecyclerViewAdapter extends RecyclerView.Adapter<KaliServicesRecyclerViewAdapter.ItemViewHolder> implements Filterable {
	public static final String TAG = "KaliServiceRecycleView";
	private final Context context;
	private List<KaliServicesModel> kaliServicesModelList;
	private List<RowSnapshot> rowSnapshots;

    public KaliServicesRecyclerViewAdapter(Context context, List<KaliServicesModel> kaliServicesModelList){
        this.context = context;
        this.kaliServicesModelList = kaliServicesModelList == null
                ? new ArrayList<>() : new ArrayList<>(kaliServicesModelList);
        this.rowSnapshots = snapshotRows(this.kaliServicesModelList);
    }

    public void updateItems(List<KaliServicesModel> items) {
        List<KaliServicesModel> nextItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
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
                return oldRow.name.equals(newRow.name)
                        && oldRow.status.equals(newRow.status)
                        && oldRow.runOnChrootStart.equals(newRow.runOnChrootStart);
            }
        });
        kaliServicesModelList = nextItems;
        rowSnapshots = nextSnapshots;
        changes.dispatchUpdatesTo(this);
    }

    private static List<RowSnapshot> snapshotRows(List<KaliServicesModel> items) {
        List<RowSnapshot> snapshots = new ArrayList<>(items.size());
        for (KaliServicesModel item : items) {
            snapshots.add(new RowSnapshot(item));
        }
        return snapshots;
    }

    private static final class RowSnapshot {
        final KaliServicesModel model;
        final String name;
        final String status;
        final String runOnChrootStart;

        RowSnapshot(KaliServicesModel model) {
            this.model = model;
            name = model.getServiceName();
            status = model.getStatus();
            runOnChrootStart = model.getRunOnChrootStart();
        }
    }

	@NonNull
	@Override
	public KaliServicesRecyclerViewAdapter.ItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
		View view = LayoutInflater.from(context).inflate(R.layout.kaliservices_recyclerview_servicetitle, viewGroup, false);
		return new KaliServicesRecyclerViewAdapter.ItemViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull final ItemViewHolder itemViewHolder, int position) {
		KaliServicesModel boundService = kaliServicesModelList.get(position);
		int statusColor = androidx.core.content.ContextCompat.getColor(context,
				boundService.getStatus().startsWith("[+]")
						? R.color.kaliservices_running : R.color.kaliservices_stopped);
		itemViewHolder.nametextView.setText(boundService.getServiceName());
		itemViewHolder.runOnChrootStartCheckbox.setOnCheckedChangeListener(null);
		itemViewHolder.runOnChrootStartCheckbox.setChecked(boundService.getRunOnChrootStart().equals("1"));
		itemViewHolder.mSwitch.setChecked(boundService.getStatus().startsWith("[+]"));
		itemViewHolder.statustextView.setTextColor(statusColor);
		itemViewHolder.statustextView.setText(boundService.getStatus());
		itemViewHolder.nametextView.setOnLongClickListener(v -> {
			final BottomSheetDialog editDialog = new BottomSheetDialog(context, R.style.ThemeOverlay_Home_BottomSheet);
			final View promptViewEdit = LayoutInflater.from(editDialog.getContext())
					.inflate(R.layout.kaliservices_edit_dialog_view, null, false);
			final EditText titleEditText = promptViewEdit.findViewById(R.id.f_kaliservices_edit_adb_et_title);
			final EditText startCmdEditText = promptViewEdit.findViewById(R.id.f_kaliservices_edit_adb_et_startcommand);
			final EditText stopCmdEditText = promptViewEdit.findViewById(R.id.f_kaliservices_edit_adb_et_stopcommand);
			final EditText checkstatusCmdEditText = promptViewEdit.findViewById(R.id.f_kaliservices_edit_adb_et_checkstatuscommand);
			final CheckBox runOnChrootStartCheckbox = promptViewEdit.findViewById(R.id.f_kaliservices_edit_adb_checkbox_runonboot);
			final FloatingActionButton readmeButton1 = promptViewEdit.findViewById(R.id.f_kaliservices_edit_btn_info_fab1);
			final FloatingActionButton readmeButton2 = promptViewEdit.findViewById(R.id.f_kaliservices_edit_btn_info_fab2);
			final FloatingActionButton readmeButton3 = promptViewEdit.findViewById(R.id.f_kaliservices_edit_btn_info_fab3);
			final FloatingActionButton readmeButton4 = promptViewEdit.findViewById(R.id.f_kaliservices_edit_btn_info_fab4);

			readmeButton1.setOnClickListener(view -> KaliServicesDialogs.showInfo(
					context, "Start command", R.string.kaliservices_howto_startservice));
			readmeButton2.setOnClickListener(view -> KaliServicesDialogs.showInfo(
					context, "Stop command", R.string.kaliservices_howto_stopservice));
			readmeButton3.setOnClickListener(view -> KaliServicesDialogs.showInfo(
					context, "Status check", R.string.kaliservices_howto_checkservice));
			readmeButton4.setOnClickListener(view -> KaliServicesDialogs.showInfo(
					context, "Run on Chroot start", R.string.kaliservices_howto_runServiceOnBoot));

			KaliServicesModel service = boundService;
			titleEditText.setText(service.getServiceName());
			startCmdEditText.setText(service.getCommandforStartService());
			stopCmdEditText.setText(service.getCommandforStopService());
			checkstatusCmdEditText.setText(service.getCommandforCheckServiceStatus());
			runOnChrootStartCheckbox.setChecked(service.getRunOnChrootStart().equals("1"));
			editDialog.setContentView(promptViewEdit);
			promptViewEdit.findViewById(R.id.kaliservices_edit_cancel_btn)
					.setOnClickListener(v1 -> editDialog.dismiss());
			promptViewEdit.findViewById(R.id.kaliservices_edit_save_btn)
					.setOnClickListener(v1 -> {
					if (titleEditText.getText().toString().isEmpty()){
						NhPaths.showMessage(context, "Title cannot be empty");
					} else if (startCmdEditText.getText().toString().isEmpty()){
						NhPaths.showMessage(context, "Start Command cannot be empty");
					} else if (stopCmdEditText.getText().toString().isEmpty()){
						NhPaths.showMessage(context, "Stop Command cannot be empty");
					} else if (checkstatusCmdEditText.getText().toString().isEmpty()){
						NhPaths.showMessage(context, "String cannot be empty");
					}else {
						ArrayList<String> dataArrayList = new ArrayList<>();
						dataArrayList.add(titleEditText.getText().toString());
						dataArrayList.add(startCmdEditText.getText().toString());
						dataArrayList.add(stopCmdEditText.getText().toString());
						dataArrayList.add(checkstatusCmdEditText.getText().toString());
						dataArrayList.add(runOnChrootStartCheckbox.isChecked()?"1":"0");
						int fullIndex = KaliServicesData.getInstance().kaliServicesModelListFull.indexOf(service);
						if (fullIndex < 0) {
							NhPaths.showMessage(context, "Service list changed. Please try again.");
							return;
						}
						KaliServicesData.getInstance().editData(fullIndex, dataArrayList, KaliServicesSQL.getInstance(context));
						editDialog.dismiss();
					}
				});
			editDialog.show();
			return true;
		});

		itemViewHolder.runOnChrootStartCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
			int fullIndex = KaliServicesData.getInstance().kaliServicesModelListFull.indexOf(boundService);
			if (fullIndex < 0) return;
			ArrayList<String> dataArrayList = new ArrayList<>();
			dataArrayList.add(boundService.getServiceName());
			dataArrayList.add(boundService.getCommandforStartService());
			dataArrayList.add(boundService.getCommandforStopService());
			dataArrayList.add(boundService.getCommandforCheckServiceStatus());
			if (isChecked) {
				dataArrayList.add("1");
			} else {
				dataArrayList.add("0");
			}
			KaliServicesData.getInstance().updateRunOnChrootStartServices(fullIndex, dataArrayList, KaliServicesSQL.getInstance(context));
		});

		itemViewHolder.mSwitch.setOnClickListener(v -> {
			int fullIndex = KaliServicesData.getInstance().kaliServicesModelListFull.indexOf(boundService);
			if (fullIndex < 0) return;
			if (itemViewHolder.mSwitch.isChecked()){
				KaliServicesData.getInstance().startServiceforItem(fullIndex, itemViewHolder.mSwitch, context);
			} else {
				KaliServicesData.getInstance().stopServiceforItem(fullIndex, itemViewHolder.mSwitch, context);
			}
		});
	}

	@Override
	public int getItemCount() {
		return kaliServicesModelList.size();
	}

	@Override
	public long getItemId(int position) {
		return super.getItemId(position);
	}

	@Override
	public Filter getFilter() {
		return KaliServicesModelListFilter;
	}

	private final Filter KaliServicesModelListFilter = new Filter() {
		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults results = new FilterResults();
			if (constraint == null || constraint.length() == 0){
				results.values = new ArrayList<>(KaliServicesData.getInstance().kaliServicesModelListFull);
			} else {
				String filterPattern = constraint.toString().toLowerCase().trim();
				List<KaliServicesModel> tempKaliServicesModelList = new ArrayList<>();
				for (KaliServicesModel kaliServicesModel: KaliServicesData.getInstance().kaliServicesModelListFull) {
					if (kaliServicesModel.getServiceName().toLowerCase().contains(filterPattern)){
						tempKaliServicesModelList.add(kaliServicesModel);
					}
				}
				results.values = tempKaliServicesModelList;
			}
			return results;
		}

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (results.values == null) return;
            List<KaliServicesModel> filtered = (List<KaliServicesModel>) results.values;
            Objects.requireNonNull(KaliServicesData.getInstance().getKaliServicesModels().getValue()).clear();
            KaliServicesData.getInstance().getKaliServicesModels().getValue().addAll(filtered);
            KaliServicesData.getInstance().getKaliServicesModels().postValue(
                    KaliServicesData.getInstance().getKaliServicesModels().getValue()
            );
        }
	};

    public class ItemViewHolder extends RecyclerView.ViewHolder {
		private final TextView nametextView;
		//private Button editbutton;
		private final SwitchCompat mSwitch;
		private final CheckBox runOnChrootStartCheckbox;
		private final TextView statustextView;

		private ItemViewHolder(View view) {
			super(view);
			nametextView = view.findViewById(R.id.f_kaliservices_recyclerview_servicetitle_tv);
			//editbutton = view.findViewById(R.id.f_kaliservices_recyclerview_edit_btn);
			runOnChrootStartCheckbox = view.findViewById(R.id.f_kaliservices_recyclerview_runonchrootstart_checkbox);
			mSwitch = view.findViewById(R.id.f_kaliservices_recyclerview_switch_toggle);
			statustextView = view.findViewById(R.id.f_kaliservices_recyclerview_serviceresult_tv);
			// WearOS optimisation
			SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
			boolean iswatch = sharedpreferences.getBoolean("running_on_wearos", false);
			if (iswatch) {
				runOnChrootStartCheckbox.setVisibility(View.GONE);
			}
		}
	}
}
