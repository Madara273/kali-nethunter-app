package com.offsec.nethunter.RecyclerViewData;

import android.content.Context;

import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.MutableLiveData;

import com.offsec.nethunter.Executor.KaliServicesExecutor;
import com.offsec.nethunter.SQL.KaliServicesSQL;
import com.offsec.nethunter.models.KaliServicesModel;
import com.offsec.nethunter.utils.NhPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KaliServicesData {
	private static KaliServicesData instance;
	public static boolean isDataInitiated = false;
	private final ArrayList<KaliServicesModel> kaliServicesModelArrayList = new ArrayList<>();
	private final MutableLiveData<List<KaliServicesModel>> data = new MutableLiveData<>();
	public List<KaliServicesModel> kaliServicesModelListFull;
	private final List<KaliServicesModel> copyOfKaliServicesModelListFull = new ArrayList<>();

	public static synchronized KaliServicesData getInstance(){
		if (instance == null) {
			instance = new KaliServicesData();
		}
		return instance;
	}

	public MutableLiveData<List<KaliServicesModel>> getKaliServicesModels(Context context){
		if (!isDataInitiated) {
			data.setValue(KaliServicesSQL.getInstance(context).bindData(kaliServicesModelArrayList));
			kaliServicesModelListFull = new ArrayList<>(Objects.requireNonNull(data.getValue()));
			isDataInitiated = true;
		}
		return data;
	}

	public MutableLiveData<List<KaliServicesModel>> getKaliServicesModels(){
		return data;
	}

	public void refreshData() {
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.GETITEMSTATUS);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
				// No implementation needed
			}

			public void onExecutorPrepare() {
				// TODO document why this method is empty
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				if (getKaliServicesModels().getValue() != null) {
					getKaliServicesModels().getValue().clear();
					getKaliServicesModels().getValue().addAll(kaliServicesModelList);
					getKaliServicesModels().postValue(getKaliServicesModels().getValue());
				}
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void startServiceforItem(int position, SwitchCompat mSwitch, Context context){
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.START_SERVICE_FOR_ITEM, position);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
                // No implementation needed
			}

			public void onExecutorPrepare() {
				mSwitch.setEnabled(false);
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				mSwitch.setEnabled(true);
				mSwitch.setChecked(kaliServicesModelList.get(position).getStatus().startsWith("[+]"));
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
				if (!mSwitch.isChecked()) NhPaths.showMessage(context, "Failed starting " + getKaliServicesModels().getValue().get(position).getServiceName() + " service");
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void stopServiceforItem(int position, SwitchCompat mSwitch, Context context){
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.STOP_SERVICE_FOR_ITEM, position);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
                // No implementation needed
			}

			public void onExecutorPrepare() {
				mSwitch.setEnabled(false);
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				mSwitch.setEnabled(true);
				mSwitch.setChecked(kaliServicesModelList.get(position).getStatus().startsWith("[+]"));
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
				if (mSwitch.isChecked()) NhPaths.showMessage(context, "Failed stopping " + getKaliServicesModels().getValue().get(position).getServiceName() + " service");
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void editData(int position, List<String> dataArrayList, KaliServicesSQL kaliServicesSQL){
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.EDITDATA, position, (ArrayList<String>) dataArrayList, kaliServicesSQL);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
				// No implementation needed
			}

			public void onExecutorPrepare() {
				// TODO document why this method is empty
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				updateKaliServicesModelListFull(kaliServicesModelList);
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void addData(int position, List<String> dataArrayList, KaliServicesSQL kaliServicesSQL){
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.ADDDATA, position, (ArrayList<String>) dataArrayList, kaliServicesSQL);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
				// No implementation needed
			}

			public void onExecutorPrepare() {
				// TODO document why this method is empty
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				updateKaliServicesModelListFull(kaliServicesModelList);
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void deleteData(List<Integer> selectedPositionsIndex, List<Integer> selectedTargetIds, KaliServicesSQL kaliServicesSQL){
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.DELETEDATA, (ArrayList<Integer>) selectedPositionsIndex, (ArrayList<Integer>) selectedTargetIds, kaliServicesSQL);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
				// No implementation needed
			}

			public void onExecutorPrepare() {
				// TODO document why this method is empty
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				updateKaliServicesModelListFull(kaliServicesModelList);
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void moveData(int originalPositionIndex, int targetPositionIndex, KaliServicesSQL kaliServicesSQL){
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.MOVEDATA, originalPositionIndex, targetPositionIndex, kaliServicesSQL);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
				// No implementation needed
			}

			public void onExecutorPrepare() {
				// TODO document why this method is empty
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				updateKaliServicesModelListFull(kaliServicesModelList);
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public String backupData(KaliServicesSQL kaliServicesSQL, String storedDBpath){
		return kaliServicesSQL.backupData(storedDBpath);
	}

	public String restoreData(KaliServicesSQL kaliServicesSQL, String storedDBpath){
		String returnedResult = kaliServicesSQL.restoreData(storedDBpath);
		if (returnedResult == null){
			KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.RESTOREDATA, kaliServicesSQL);
			kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
				@Override
				public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
					// No implementation needed
				}

				public void onExecutorPrepare() {
					// TODO document why this method is empty
				}

				public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
					updateKaliServicesModelListFull(kaliServicesModelList);
					Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
					getKaliServicesModels().getValue().addAll(kaliServicesModelList);
					getKaliServicesModels().postValue(getKaliServicesModels().getValue());
					refreshData();
				}
			});
			kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
			return null;
		} else {
			return returnedResult;
		}
	}

	public void resetData(KaliServicesSQL kaliServicesSQL){
		kaliServicesSQL.resetData();
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.RESTOREDATA, kaliServicesSQL);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
				// No implementation needed
			}

			public void onExecutorPrepare() {
				// TODO document why this method is empty
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				updateKaliServicesModelListFull(kaliServicesModelList);
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
				refreshData();
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void updateRunOnChrootStartServices(int position, List<String> dataArrayList, KaliServicesSQL kaliServicesSQL) {
		KaliServicesExecutor kaliServicesExecutor = new KaliServicesExecutor(KaliServicesExecutor.UPDATE_RUNONCHROOTSTART_SCRIPTS, position, (ArrayList<String>) dataArrayList, kaliServicesSQL);
		kaliServicesExecutor.setListener(new KaliServicesExecutor.KaliServicesExecutorListener() {
			@Override
			public void onTaskFinished(List<KaliServicesModel> kaliServicesModelList) {
				// No implementation needed
			}

			public void onExecutorPrepare() {
				// TODO document why this method is empty
			}

			public void onExecutorFinished(List<KaliServicesModel> kaliServicesModelList) {
				updateKaliServicesModelListFull(kaliServicesModelList);
				Objects.requireNonNull(getKaliServicesModels().getValue()).clear();
				getKaliServicesModels().getValue().addAll(kaliServicesModelList);
				getKaliServicesModels().postValue(getKaliServicesModels().getValue());
			}
		});
		kaliServicesExecutor.execute(getInitCopyOfKaliServicesModelListFull());
	}

	public void updateKaliServicesModelListFull(List<KaliServicesModel> copyOfKaliServicesModelList){
		kaliServicesModelListFull.clear();
		kaliServicesModelListFull.addAll(copyOfKaliServicesModelList);
	}

	private List<KaliServicesModel> getInitCopyOfKaliServicesModelListFull(){
		copyOfKaliServicesModelListFull.clear();
		copyOfKaliServicesModelListFull.addAll(kaliServicesModelListFull);
		return copyOfKaliServicesModelListFull;
	}
}
