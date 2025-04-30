package com.offsec.nethunter.Executor;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.offsec.nethunter.ChrootManagerFragment;
import com.offsec.nethunter.SQL.CustomCommandsSQL;
import com.offsec.nethunter.models.CustomCommandsModel;
import com.offsec.nethunter.service.NotificationChannelService;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;
import com.offsec.nethunter.bridge.*;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CustomCommandsExecutor {
	private CustomCommandsExecutorListener listener;
	private final int actionCode;
	private int position;
	private int originalPositionIndex;
	private int targetPositionIndex;
	private static WeakReference<Context> context;
	private ArrayList<Integer> selectedPositionsIndex;
	private ArrayList<Integer> selectedTargetIds;
	private ArrayList<String> dataArrayList;
	private CustomCommandsSQL customCommandsSQL;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	public static final int RUNCMD = 0;
	public static final int EDITDATA = 1;
	public static final int ADDDATA = 2;
	public static final int DELETEDATA = 3;
	public static final int MOVEDATA = 4;
	public static final int BACKUPDATA = 5;
	public static final int RESTOREDATA = 6;
	public static final int RESETDATA = 7;
	public static final int ANDROID_CMD_SUCCESS = 100;
	public static final int ANDROID_CMD_FAIL = 101;
	public static final int KALI_CMD_SUCCESS = 102;
	public static final int KALI_CMD_FAIL = 103;
	private int returnValue = 0;

	public CustomCommandsExecutor(int actionCode, int position, Context context) {
		this.actionCode = actionCode;
		this.position = position;
		CustomCommandsExecutor.context = new WeakReference<>(context);
	}

	public CustomCommandsExecutor(int actionCode, int position, ArrayList<String> dataArrayList, CustomCommandsSQL customCommandsSQL) {
		this.actionCode = actionCode;
		this.position = position;
		this.dataArrayList = dataArrayList;
		this.customCommandsSQL = customCommandsSQL;
	}

	public CustomCommandsExecutor(int actionCode, ArrayList<Integer> selectedPositionsIndex, ArrayList<Integer> selectedTargetIds, CustomCommandsSQL customCommandsSQL) {
		this.actionCode = actionCode;
		this.selectedPositionsIndex = selectedPositionsIndex;
		this.selectedTargetIds = selectedTargetIds;
		this.customCommandsSQL = customCommandsSQL;
	}

	public CustomCommandsExecutor(int actionCode, int originalPositionIndex, int targetPositionIndex, CustomCommandsSQL customCommandsSQL) {
		this.actionCode = actionCode;
		this.originalPositionIndex = originalPositionIndex;
		this.targetPositionIndex = targetPositionIndex;
		this.customCommandsSQL = customCommandsSQL;
	}

	public CustomCommandsExecutor(int actionCode, CustomCommandsSQL customCommandsSQL) {
		this.actionCode = actionCode;
		this.customCommandsSQL = customCommandsSQL;
	}

	public void execute(List<CustomCommandsModel> customCommandsModelList) {
		executorService.execute(() -> {
			List<CustomCommandsModel> result = performTask(customCommandsModelList);
			mainHandler.post(() -> {
				if (listener != null) {
					listener.onTaskFinished(result);
				}
				ChrootManagerFragment.isExecutorRunning = false;
				if (returnValue != 0) {
					Intent intent = new Intent(context.get(), NotificationChannelService.class)
							.setAction(NotificationChannelService.CUSTOMCOMMAND_FINISH);
					intent.putExtra("RETURNCODE", returnValue)
							.putExtra("CMD", customCommandsModelList.get(position).getCommand());
					context.get().startService(intent);
				}
			});
		});
	}

	private List<CustomCommandsModel> performTask(List<CustomCommandsModel> customCommandsModelList) {
		switch (actionCode) {
			case RUNCMD:
				if (customCommandsModelList != null) {
					if (customCommandsModelList.get(position).getExecutionMode().equals("interactive")) {
						if (customCommandsModelList.get(position).getRuntimeEnv().equals("android")) {
							String cmd = customCommandsModelList.get(position).getCommand();
							run_cmd_android(cmd);
						} else if (customCommandsModelList.get(position).getRuntimeEnv().equals("kali")) {
							String cmd = customCommandsModelList.get(position).getCommand();
							run_cmd(cmd);
						}
					} else {
						Intent intent = new Intent(context.get(), NotificationChannelService.class)
								.setAction(NotificationChannelService.CUSTOMCOMMAND_START);
						intent.putExtra("ENV", customCommandsModelList.get(position).getRuntimeEnv())
								.putExtra("CMD", customCommandsModelList.get(position).getCommand());
						context.get().startService(intent);
						if (customCommandsModelList.get(position).getRuntimeEnv().equals("android")) {
							returnValue = new ShellExecuter().RunAsRootReturnValue(customCommandsModelList.get(position).getCommand());
							returnValue = (returnValue == 0) ? ANDROID_CMD_SUCCESS : ANDROID_CMD_FAIL;
						} else {
							returnValue = new ShellExecuter().RunAsChrootReturnValue(customCommandsModelList.get(position).getCommand());
							returnValue = (returnValue == 0) ? KALI_CMD_SUCCESS : KALI_CMD_FAIL;
						}
					}
				}
				break;
			case EDITDATA:
				if (customCommandsModelList != null) {
					customCommandsModelList.get(position).setCommandLabel(dataArrayList.get(0));
					customCommandsModelList.get(position).setCommand(dataArrayList.get(1));
					customCommandsModelList.get(position).setRuntimeEnv(dataArrayList.get(2));
					customCommandsModelList.get(position).setExecutionMode(dataArrayList.get(3));
					customCommandsModelList.get(position).setRunOnBoot(dataArrayList.get(4));
					updateRunOnBootScripts(customCommandsModelList);
					customCommandsSQL.editData(position, dataArrayList);
				}
				break;
			case ADDDATA:
				if (customCommandsModelList != null) {
					customCommandsModelList.add(position - 1, new CustomCommandsModel(
							dataArrayList.get(0),
							dataArrayList.get(1),
							dataArrayList.get(2),
							dataArrayList.get(3),
							dataArrayList.get(4)));
					if (dataArrayList.get(4).equals("1")) {
						updateRunOnBootScripts(customCommandsModelList);
					}
					customCommandsSQL.addData(position, dataArrayList);
				}
				break;
			case DELETEDATA:
				if (customCommandsModelList != null) {
					Collections.sort(selectedPositionsIndex, Collections.reverseOrder());
					for (Integer selectedPosition : selectedPositionsIndex) {
						customCommandsModelList.remove((int) selectedPosition);
					}
					customCommandsSQL.deleteData(selectedTargetIds);
				}
				break;
			case MOVEDATA:
				if (customCommandsModelList != null) {
					CustomCommandsModel tempCustomCommandsModel = new CustomCommandsModel(
							customCommandsModelList.get(originalPositionIndex).getCommandLabel(),
							customCommandsModelList.get(originalPositionIndex).getCommand(),
							customCommandsModelList.get(originalPositionIndex).getRuntimeEnv(),
							customCommandsModelList.get(originalPositionIndex).getExecutionMode(),
							customCommandsModelList.get(originalPositionIndex).getRunOnBoot()
					);
					customCommandsModelList.remove(originalPositionIndex);
					if (originalPositionIndex < targetPositionIndex) {
						targetPositionIndex = targetPositionIndex - 1;
					}
					customCommandsModelList.add(targetPositionIndex, tempCustomCommandsModel);
					customCommandsSQL.moveData(originalPositionIndex, targetPositionIndex);
				}
				break;
			case BACKUPDATA:
			case RESETDATA:
				break;
			case RESTOREDATA:
				if (customCommandsModelList != null) {
					customCommandsModelList.clear();
					customCommandsSQL.bindData(customCommandsModelList);
				}
				break;
		}
		return customCommandsModelList;
	}

	public void setListener(CustomCommandsExecutorListener listener) {
		this.listener = listener;
	}

	public interface CustomCommandsExecutorListener {
		void onTaskFinished(List<CustomCommandsModel> customCommandsModelList);
	}

	private void updateRunOnBootScripts(List<CustomCommandsModel> customCommandsModelList) {
		StringBuilder tmpStringBuilder = new StringBuilder();
		for (CustomCommandsModel model : customCommandsModelList) {
			if (model.getRunOnBoot().equals("1")) {
				tmpStringBuilder.append(model.getRuntimeEnv()).append(" ").append(model.getCommand()).append("\n");
			}
		}
		new ShellExecuter().RunAsRootOutput("cat << 'EOF' > " + NhPaths.APP_SCRIPTS_PATH + "/runonboot_services" + "\n" + tmpStringBuilder + "\nEOF");
	}

	public static void run_cmd(String cmd) {
		Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
		context.get().startActivity(intent);
	}

	public static void run_cmd_android(String cmd) {
		Intent intent = Bridge.createExecuteIntent("/system/bin/sh", cmd);
		context.get().startActivity(intent);
	}
}
