package com.offsec.nethunter.RecyclerViewData;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;

import com.offsec.nethunter.Executor.CustomCommandsExecutor;
import com.offsec.nethunter.SQL.CustomCommandsSQL;
import com.offsec.nethunter.models.CustomCommandsModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CustomCommandsData {
    private static CustomCommandsData instance;
    public static boolean isDataInitiated = false;
    private final ArrayList<CustomCommandsModel> customCommandsModelArrayList = new ArrayList<>();
    private final MutableLiveData<List<CustomCommandsModel>> data = new MutableLiveData<>();
    public List<CustomCommandsModel> customCommandsModelListFull;
    private final List<CustomCommandsModel> copyOfCustomCommandsModelListFull = new ArrayList<>();

    public static synchronized CustomCommandsData getInstance() {
        if (instance == null) {
            instance = new CustomCommandsData();
        }
        return instance;
    }

    public MutableLiveData<List<CustomCommandsModel>> getCustomCommandsModels(Context context) {
        if (!isDataInitiated) {
            List<CustomCommandsModel> boundData = CustomCommandsSQL.getInstance(context).bindData(customCommandsModelArrayList);
            if (boundData != null) {
                data.setValue(boundData);
                customCommandsModelListFull = new ArrayList<>(Objects.requireNonNull(data.getValue()));
                isDataInitiated = true;
            }
        }
        return data;
    }

    public MutableLiveData<List<CustomCommandsModel>> getCustomCommandsModels() {
        return data;
    }

    public void runCommandforitem(int position, Context context) {
        CustomCommandsExecutor customCommandsExecutor = new CustomCommandsExecutor(CustomCommandsExecutor.RUNCMD, position, context);
        customCommandsExecutor.setListener(new CustomCommandsExecutor.CustomCommandsExecutorListener() {
            @Override
            public void onTaskFinished(List<CustomCommandsModel> customCommandsModelList) {
                updateCustomCommandsModelListFull(customCommandsModelList);
                Objects.requireNonNull(getCustomCommandsModels().getValue()).clear();
                getCustomCommandsModels().getValue().addAll(customCommandsModelList);
                getCustomCommandsModels().postValue(getCustomCommandsModels().getValue());
            }

            public void onExecutorPrepare() {
                // TODO document why this method is empty
            }
        });
        customCommandsExecutor.execute(getInitCopyOfCustomCommandsModelListFull());
    }

    public void editData(int position, List<String> dataArrayList, CustomCommandsSQL customCommandsSQL) {
        CustomCommandsExecutor customCommandsExecutor = new CustomCommandsExecutor(CustomCommandsExecutor.EDITDATA, position, (ArrayList<String>) dataArrayList, customCommandsSQL);
        customCommandsExecutor.setListener(new CustomCommandsExecutor.CustomCommandsExecutorListener() {
            @Override
            public void onTaskFinished(List<CustomCommandsModel> customCommandsModelList) {
                updateCustomCommandsModelListFull(customCommandsModelList);
                Objects.requireNonNull(getCustomCommandsModels().getValue()).clear();
                getCustomCommandsModels().getValue().addAll(customCommandsModelList);
                getCustomCommandsModels().postValue(getCustomCommandsModels().getValue());
            }

            public void onExecutorPrepare() {
                // TODO document why this method is empty
            }
        });
        customCommandsExecutor.execute(getInitCopyOfCustomCommandsModelListFull());
    }

    public void addData(int position, List<String> dataArrayList, CustomCommandsSQL customCommandsSQL) {
        CustomCommandsExecutor customCommandsExecutor = new CustomCommandsExecutor(CustomCommandsExecutor.ADDDATA, position, (ArrayList<String>) dataArrayList, customCommandsSQL);
        customCommandsExecutor.setListener(new CustomCommandsExecutor.CustomCommandsExecutorListener() {
            @Override
            public void onTaskFinished(List<CustomCommandsModel> customCommandsModelList) {
                updateCustomCommandsModelListFull(customCommandsModelList);
                Objects.requireNonNull(getCustomCommandsModels().getValue()).clear();
                getCustomCommandsModels().getValue().addAll(customCommandsModelList);
                getCustomCommandsModels().postValue(getCustomCommandsModels().getValue());
            }

            public void onExecutorPrepare() {
                // TODO document why this method is empty
            }
        });
        customCommandsExecutor.execute(getInitCopyOfCustomCommandsModelListFull());
    }

    public void deleteData(List<Integer> selectedPositionsIndex, List<Integer> selectedTargetIds, CustomCommandsSQL customCommandsSQL) {
        if (selectedPositionsIndex instanceof ArrayList<?> && selectedTargetIds instanceof ArrayList<?>) {
            CustomCommandsExecutor customCommandsExecutor = new CustomCommandsExecutor(
                    CustomCommandsExecutor.DELETEDATA,
                    (ArrayList<Integer>) selectedPositionsIndex,
                    (ArrayList<Integer>) selectedTargetIds,
                    customCommandsSQL
            );
            customCommandsExecutor.setListener(new CustomCommandsExecutor.CustomCommandsExecutorListener() {
                @Override
                public void onTaskFinished(List<CustomCommandsModel> customCommandsModelList) {
                    updateCustomCommandsModelListFull(customCommandsModelList);
                    Objects.requireNonNull(getCustomCommandsModels().getValue()).clear();
                    getCustomCommandsModels().getValue().addAll(customCommandsModelList);
                    getCustomCommandsModels().postValue(getCustomCommandsModels().getValue());
                }

                public void onExecutorPrepare() {
                    // TODO document why this method is empty
                }
            });
            customCommandsExecutor.execute(getInitCopyOfCustomCommandsModelListFull());
        }
    }

    public void moveData(int originalPositionIndex, int targetPositionIndex, CustomCommandsSQL customCommandsSQL) {
        CustomCommandsExecutor customCommandsExecutor = new CustomCommandsExecutor(CustomCommandsExecutor.MOVEDATA, originalPositionIndex, targetPositionIndex, customCommandsSQL);
        customCommandsExecutor.setListener(new CustomCommandsExecutor.CustomCommandsExecutorListener() {
            @Override
            public void onTaskFinished(List<CustomCommandsModel> customCommandsModelList) {
                updateCustomCommandsModelListFull(customCommandsModelList);
                Objects.requireNonNull(getCustomCommandsModels().getValue()).clear();
                getCustomCommandsModels().getValue().addAll(customCommandsModelList);
                getCustomCommandsModels().postValue(getCustomCommandsModels().getValue());
            }

            public void onExecutorPrepare() {
                // TODO document why this method is empty
            }
        });
        customCommandsExecutor.execute(getInitCopyOfCustomCommandsModelListFull());
    }

    public String backupData(CustomCommandsSQL customCommandsSQL, String storedDBpath) {
        return customCommandsSQL.backupData(storedDBpath);
    }

    public String restoreData(CustomCommandsSQL customCommandsSQL, String storedDBpath) {
        String returnedResult = customCommandsSQL.restoreData(storedDBpath);
        if (returnedResult == null) {
            CustomCommandsExecutor customCommandsExecutor = new CustomCommandsExecutor(CustomCommandsExecutor.RESTOREDATA, customCommandsSQL);
            customCommandsExecutor.setListener(new CustomCommandsExecutor.CustomCommandsExecutorListener() {
                @Override
                public void onTaskFinished(List<CustomCommandsModel> customCommandsModelList) {
                    updateCustomCommandsModelListFull(customCommandsModelList);
                    Objects.requireNonNull(getCustomCommandsModels().getValue()).clear();
                    getCustomCommandsModels().getValue().addAll(customCommandsModelList);
                    getCustomCommandsModels().postValue(getCustomCommandsModels().getValue());
                }

                public void onExecutorPrepare() {
                    // TODO document why this method is empty
                }
            });
            customCommandsExecutor.execute(getInitCopyOfCustomCommandsModelListFull());
            return null;
        } else {
            return returnedResult;
        }
    }

    public void resetData(CustomCommandsSQL customCommandsSQL) {
        customCommandsSQL.resetData();
        CustomCommandsExecutor customCommandsExecutor = new CustomCommandsExecutor(CustomCommandsExecutor.RESTOREDATA, customCommandsSQL);
        customCommandsExecutor.setListener(new CustomCommandsExecutor.CustomCommandsExecutorListener() {
            @Override
            public void onTaskFinished(List<CustomCommandsModel> customCommandsModelList) {
                updateCustomCommandsModelListFull(customCommandsModelList);
                Objects.requireNonNull(getCustomCommandsModels().getValue()).clear();
                getCustomCommandsModels().getValue().addAll(customCommandsModelList);
                getCustomCommandsModels().postValue(getCustomCommandsModels().getValue());
            }

            public void onExecutorPrepare() {
                // TODO document why this method is empty
            }
        });
        customCommandsExecutor.execute(getInitCopyOfCustomCommandsModelListFull());
    }

    public void updateCustomCommandsModelListFull(List<CustomCommandsModel> copyOfCustomCommandsModelList) {
        customCommandsModelListFull.clear();
        customCommandsModelListFull.addAll(copyOfCustomCommandsModelList);
    }

    private List<CustomCommandsModel> getInitCopyOfCustomCommandsModelListFull() {
        copyOfCustomCommandsModelListFull.clear();
        copyOfCustomCommandsModelListFull.addAll(customCommandsModelListFull);
        return copyOfCustomCommandsModelListFull;
    }
}