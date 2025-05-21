package com.offsec.nethunter.RecyclerViewData;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;

import com.offsec.nethunter.Executor.NethunterExecutor;
import com.offsec.nethunter.SQL.NethunterSQL;
import com.offsec.nethunter.models.NethunterModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NethunterData {
    private static NethunterData instance;
    public static boolean isDataInitiated = false;
    private final ArrayList<NethunterModel> nethunterModelArrayList = new ArrayList<>();
    private final MutableLiveData<List<NethunterModel>> data = new MutableLiveData<>();
    public List<NethunterModel> nethunterModelListFull;
    private final List<NethunterModel> copyOfNethunterModelListFull = new ArrayList<>();

    public static synchronized NethunterData getInstance() {
        if (instance == null) {
            instance = new NethunterData();
        }
        return instance;
    }

    public MutableLiveData<List<NethunterModel>> getNethunterModels(Context context) {
        if (!isDataInitiated) {
            data.setValue(NethunterSQL.getInstance(context).bindData(nethunterModelArrayList));
            nethunterModelListFull = new ArrayList<>(Objects.requireNonNull(data.getValue()));
            isDataInitiated = true;
        }
        return data;
    }

    public MutableLiveData<List<NethunterModel>> getNethunterModels() {
        return data;
    }

    public void refreshData() {
        NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.GETITEMRESULTS);
        nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
            @Override
            public void onPrepare() {
                // Implementation or leave empty
            }

            @Override
            public void onFinished(List<NethunterModel> nethunterModelList) {
                Objects.requireNonNull(getNethunterModels().getValue()).clear();
                getNethunterModels().getValue().addAll(nethunterModelList);
                getNethunterModels().postValue(getNethunterModels().getValue());
            }
        });
        nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
    }

    public void runCommandforItem(int position) {
        NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.RUNCMDFORITEM, position);
        nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
            @Override
            public void onPrepare() {
                // Implementation or leave empty
            }

            @Override
            public void onFinished(List<NethunterModel> nethunterModelList) {
                Objects.requireNonNull(getNethunterModels().getValue()).clear();
                getNethunterModels().getValue().addAll(nethunterModelList);
                getNethunterModels().postValue(getNethunterModels().getValue());
            }
        });
        nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
    }

    public void editData(int position, List<String> dataArrayList, NethunterSQL nethunterSQL) {
        NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.EDITDATA, position, (ArrayList<String>) dataArrayList, nethunterSQL);
        nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
            @Override
            public void onPrepare() {
                // Implementation or leave empty
            }

            @Override
            public void onFinished(List<NethunterModel> nethunterModelList) {
                updateNethunterModelListFull(nethunterModelList);
                Objects.requireNonNull(getNethunterModels().getValue()).clear();
                getNethunterModels().getValue().addAll(nethunterModelList);
                getNethunterModels().postValue(getNethunterModels().getValue());
            }
        });
        nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
    }

    public void addData(int position, List<String> dataArrayList, NethunterSQL nethunterSQL) {
        NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.ADDDATA, position, (ArrayList<String>) dataArrayList, nethunterSQL);
        nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
            @Override
            public void onPrepare() {
                // Implementation or leave empty
            }

            @Override
            public void onFinished(List<NethunterModel> nethunterModelList) {
                updateNethunterModelListFull(nethunterModelList);
                Objects.requireNonNull(getNethunterModels().getValue()).clear();
                getNethunterModels().getValue().addAll(nethunterModelList);
                getNethunterModels().postValue(getNethunterModels().getValue());
            }
        });
        nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
    }

    public void deleteData(List<Integer> selectedPositionsIndex, List<Integer> selectedTargetIds, NethunterSQL nethunterSQL) {
        NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.DELETEDATA, (ArrayList<Integer>) selectedPositionsIndex, (ArrayList<Integer>) selectedTargetIds, nethunterSQL);
        nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
            @Override
            public void onPrepare() {
                // Implementation or leave empty
            }

            @Override
            public void onFinished(List<NethunterModel> nethunterModelList) {
                updateNethunterModelListFull(nethunterModelList);
                Objects.requireNonNull(getNethunterModels().getValue()).clear();
                getNethunterModels().getValue().addAll(nethunterModelList);
                getNethunterModels().postValue(getNethunterModels().getValue());
            }
        });
        nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
    }

    public void moveData(int originalPositionIndex, int targetPositionIndex, NethunterSQL nethunterSQL) {
        NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.MOVEDATA, originalPositionIndex, targetPositionIndex, nethunterSQL);
        nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
            @Override
            public void onPrepare() {
                // Implementation or leave empty
            }

            @Override
            public void onFinished(List<NethunterModel> nethunterModelList) {
                updateNethunterModelListFull(nethunterModelList);
                Objects.requireNonNull(getNethunterModels().getValue()).clear();
                getNethunterModels().getValue().addAll(nethunterModelList);
                getNethunterModels().postValue(getNethunterModels().getValue());
            }
        });
        nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
    }

    public String backupData(NethunterSQL nethunterSQL, String storedDBpath) {
        return nethunterSQL.backupData(storedDBpath);
    }

    public String restoreData(NethunterSQL nethunterSQL, String storedDBpath) {
        String returnedResult = nethunterSQL.restoreData(storedDBpath);
        if (returnedResult == null) {
            NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.RESTOREDATA, nethunterSQL);
            nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
                @Override
                public void onPrepare() {
                    // Implementation or leave empty
                }

                @Override
                public void onFinished(List<NethunterModel> nethunterModelList) {
                    updateNethunterModelListFull(nethunterModelList);
                    Objects.requireNonNull(getNethunterModels().getValue()).clear();
                    getNethunterModels().getValue().addAll(nethunterModelList);
                    getNethunterModels().postValue(getNethunterModels().getValue());
                    refreshData();
                }
            });
            nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
            return null;
        } else {
            return returnedResult;
        }
    }

    public void resetData(NethunterSQL nethunterSQL) {
        nethunterSQL.resetData();
        NethunterExecutor nethunterExecutor = new NethunterExecutor(NethunterExecutor.RESTOREDATA, nethunterSQL);
        nethunterExecutor.setListener(new NethunterExecutor.NethunterExecutorListener() {
            @Override
            public void onPrepare() {
                // Implementation or leave empty
            }

            @Override
            public void onFinished(List<NethunterModel> nethunterModelList) {
                updateNethunterModelListFull(nethunterModelList);
                Objects.requireNonNull(getNethunterModels().getValue()).clear();
                getNethunterModels().getValue().addAll(nethunterModelList);
                getNethunterModels().postValue(getNethunterModels().getValue());
                refreshData();
            }
        });
        nethunterExecutor.execute(getInitCopyOfNethunterModelListFull());
    }

    public void updateNethunterModelListFull(List<NethunterModel> copyOfNethunterModelList) {
        nethunterModelListFull.clear();
        nethunterModelListFull.addAll(copyOfNethunterModelList);
    }

    private List<NethunterModel> getInitCopyOfNethunterModelListFull() {
        copyOfNethunterModelListFull.clear();
        copyOfNethunterModelListFull.addAll(nethunterModelListFull);
        return copyOfNethunterModelListFull;
    }
}
