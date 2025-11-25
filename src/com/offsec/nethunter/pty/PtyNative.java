package com.offsec.nethunter.pty;

import android.util.Log;

public class PtyNative {
    private static final String TAG = "PtyNative";
    private static boolean LOADED = false;
    static {
        try {
            System.loadLibrary("native-lib");
            LOADED = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native-lib: " + e.getMessage());
            LOADED = false;
        }
    }
    public static boolean isLoaded() { return LOADED; }
    // Opens a PTY and forks a root shell (su -mm). Returns {masterFd, childPid} or null on failure.
    public static native int[] openPtyShell();
    // Opens a PTY and runs a specific root command via su -mm -c <command>. Returns {masterFd, childPid} or null.
    public static native int[] openPtyShellExec(String command);
    public static native int setWindowSize(int fd, int cols, int rows);
    public static native int closeFd(int fd);
    public static native int killChild(int pid, int signal);
}
