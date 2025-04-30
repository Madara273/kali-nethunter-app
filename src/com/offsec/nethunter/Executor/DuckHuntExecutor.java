package com.offsec.nethunter.Executor;

import android.os.Handler;
import android.os.Looper;

import com.offsec.nethunter.utils.ShellExecuter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class DuckHuntExecutor {
    public static final Object ATTACK = 1;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private DuckHuntExecutorListener listener;

    public void execute(int actionCode, Object... objects) {
        executorService.execute(() -> {
            Object result = performTask(actionCode, objects);
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onTaskFinished(result);
                }
            });
        });
    }

    private Object performTask(int actionCode, Object... objects) {
        ShellExecuter exe = new ShellExecuter();
        Object result = null;
        switch (actionCode) {
            case 1: // ATTACK
                result = true;
                String[] hidgs = {"/dev/hidg0", "/dev/hidg1"};
                for (String hidg : hidgs) {
                    if (!exe.RunAsRootOutput("stat -c '%a' " + hidg).equals("666")) {
                        result = false;
                        break;
                    }
                }
                if ((boolean) result) {
                    exe.RunAsRootOutput(objects[0].toString());
                }
                break;
            case 2: // CONVERT
                if ((boolean) objects[1]) {
                    result = exe.RunAsRootOutput(objects[0].toString());
                } else {
                    result = "";
                }
                break;
            case 3: // READ_PREVIEW
                result = exe.RunAsRootOutput(objects[0].toString());
                break;
        }
        return result;
    }

    public void setListener(DuckHuntExecutorListener listener) {
        this.listener = listener;
    }

    public interface DuckHuntExecutorListener {
        void onTaskFinished(Object result);
    }
}
