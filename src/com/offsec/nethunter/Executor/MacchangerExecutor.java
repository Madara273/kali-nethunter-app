package com.offsec.nethunter.Executor;

import android.os.Handler;
import android.os.Looper;

import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MacchangerExecutor {
    private MacchangerExecutorListener listener;
    public static final int GETHOSTNAME = 0;
    public static final int SETHOSTNAME = 1;
    public static final int SETMAC = 2;
    public static final int GETORIGINMAC = 3;
    private final ShellExecuter exe = new ShellExecuter();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int actionCode;

    public MacchangerExecutor(int actionCode) {
        this.actionCode = actionCode;
    }

    public void execute(String... strings) {
        if (listener != null) {
            mainHandler.post(listener::onPrepare);
        }

        executorService.execute(() -> {
            Object result = null;
            switch (actionCode) {
                case GETHOSTNAME:
                    result = exe.Executer("getprop net.hostname");
                    break;
                case SETHOSTNAME:
                    result = exe.Executer("setprop net.hostname " + strings[0]);
                    break;
                case GETORIGINMAC:
                    result = exe.RunAsRootOutput(NhPaths.APP_SCRIPTS_BIN_PATH + "/macchanger -s " + strings[0] + " | " + NhPaths.BUSYBOX + " awk '/Permanent/ {print $3}'");
                    break;
                case SETMAC:
                    if (strings[0].matches("^wlan.*$")) {
                        result = exe.RunAsRootReturnValue(NhPaths.APP_SCRIPTS_PATH + "/changemac " + strings[0] + " " + strings[1]);
                    } else {
                        result = exe.RunAsRootReturnValue(NhPaths.APP_SCRIPTS_BIN_PATH + "/macchanger " + strings[0] + " -m " + strings[1]);
                    }
                    break;
            }

            Object finalResult = result;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onFinished(finalResult);
                }
            });
        });
    }

    public void setListener(MacchangerExecutorListener listener) {
        this.listener = listener;
    }

    public interface MacchangerExecutorListener {
        void onPrepare();
        void onFinished(Object result);
    }
}
