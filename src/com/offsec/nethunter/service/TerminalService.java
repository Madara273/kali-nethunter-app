package com.offsec.nethunter.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.offsec.nethunter.AppNavHomeActivity;
import com.offsec.nethunter.BuildConfig;
import com.offsec.nethunter.R;
import com.offsec.nethunter.pty.PtyNative;
import com.offsec.nethunter.utils.NhPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TerminalService extends Service {
    private static final String TAG = "TerminalService";
    public static final String CHANNEL_ID = NotificationChannelService.CHANNEL_ID;
    private static final int NOTIFY_ID = 2001;

    public static class TerminalEvent {
        public final String data;
        public final boolean isErr;
        public TerminalEvent(String d, boolean e) { this.data = d; this.isErr = e; }
    }

    public interface TerminalListener {
        void onOutput(int sessionId, @NonNull TerminalEvent event);
        void onSessionClosed(int sessionId, int exitCode);
    }

    public static class SessionInfo {
        public final int id;
        public final String title;
        SessionInfo(int id, String title) { this.id = id; this.title = title; }
    }

    private static class Session {
        final int id;
        final String title;
        volatile int ptyFd = -1;
        volatile int ptyPid = -1;
        volatile ParcelFileDescriptor ptyPfd;
        volatile FileInputStream in;
        volatile FileOutputStream out;
        volatile Thread readThread;
        volatile boolean stopping = false;
        // ring buffer of recent events
        final ArrayDeque<TerminalEvent> buffer = new ArrayDeque<>();
        final int bufferMax = 1024; // events, not lines; conservative to cap memory
        final CopyOnWriteArrayList<TerminalListener> listeners = new CopyOnWriteArrayList<>();

        Session(int id, String title) { this.id = id; this.title = title; }

        void appendAndDispatch(@NonNull TerminalEvent ev, @NonNull Handler main) {
            synchronized (buffer) {
                buffer.addLast(ev);
                while (buffer.size() > bufferMax) buffer.removeFirst();
            }
            for (TerminalListener l : listeners) {
                main.post(() -> l.onOutput(id, ev));
            }
        }

        List<TerminalEvent> snapshot() {
            synchronized (buffer) { return new ArrayList<>(buffer); }
        }
    }

    public class LocalBinder extends Binder {
        public TerminalService getService() { return TerminalService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, Session> sessions = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler worker;
    private volatile boolean foregroundStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread ht = new HandlerThread("TerminalServiceWorker");
        ht.start();
        worker = new Handler(ht.getLooper());
        ensureNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Do not call startForeground here; it will be done in onStartCommand when started
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundStarted) {
            foregroundStarted = true;
            startInForeground();
        }
        // Keep running; ensure sessions persist
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Keep running; foreground ensures PTYs live even without UI
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Do not forcibly stop sessions here; allow system to kill if needed
    }

    // API
    public synchronized int ensureDefaultSession(@NonNull Context ctx) {
        // Return existing session if present, else create one
        for (Session s : sessions.values()) {
            // pick the lowest id as default
            return s.id;
        }
        return createSession(ctx, null);
    }

    public synchronized int createSession(@NonNull Context ctx, @Nullable String explicitCmd) {
        int id = nextId.getAndIncrement();
        String title = explicitCmd != null ? explicitCmd : "kali";
        Session s = new Session(id, title);
        sessions.put(id, s);
        startPtyForSession(ctx, s, explicitCmd);
        updateForegroundNotification();
        return id;
    }

    public synchronized List<SessionInfo> listSessions() {
        List<SessionInfo> list = new ArrayList<>();
        for (Session s : sessions.values()) list.add(new SessionInfo(s.id, s.title));
        return list;
    }

    public synchronized void attachListener(int sessionId, @NonNull TerminalListener l) {
        Session s = sessions.get(sessionId);
        if (s != null) { s.listeners.add(l); }
    }

    public synchronized void detachListener(int sessionId, @NonNull TerminalListener l) {
        Session s = sessions.get(sessionId);
        if (s != null) { s.listeners.remove(l); }
    }

    public synchronized List<TerminalEvent> getBufferSnapshot(int sessionId) {
        Session s = sessions.get(sessionId);
        return s != null ? s.snapshot() : Collections.emptyList();
    }

    public synchronized void send(int sessionId, @NonNull String data) {
        Session s = sessions.get(sessionId);
        if (s == null || s.out == null) return;
        worker.post(() -> {
            try { s.out.write(data.getBytes(StandardCharsets.UTF_8)); s.out.flush(); }
            catch (IOException e) { Log.e(TAG, "send failed", e); }
        });
    }

    public synchronized void sendControl(int sessionId, int code) {
        if (code <= 0 || code > 31) return;
        Session s = sessions.get(sessionId);
        if (s == null || s.out == null) return;
        worker.post(() -> {
            try { s.out.write(new byte[]{(byte) code}); s.out.flush(); }
            catch (IOException e) { Log.e(TAG, "sendControl failed", e); }
        });
    }

    public synchronized void resizePty(int sessionId, int cols, int rows) {
        Session s = sessions.get(sessionId);
        if (s == null || s.ptyFd < 0 || !PtyNative.isLoaded()) return;
        try { PtyNative.setWindowSize(s.ptyFd, cols, rows); } catch (Throwable ignored) {}
    }

    public synchronized void closeSession(int sessionId) {
        Session s = sessions.remove(sessionId);
        if (s == null) return;
        stopPty(s);
        updateForegroundNotification();
    }

    // Internals
    private void startPtyForSession(@NonNull Context ctx, @NonNull Session s, @Nullable String explicitCmd) {
        worker.post(() -> {
            try {
                int[] res;
                boolean useChrootDirect = true;
                if (PtyNative.isLoaded()) {
                    if (useChrootDirect && isChrootAvailable()) {
                        String resolvedShell = resolvePreferredShell(ctx);
                        String chrootCmd = buildChrootShellCommand(ctx, resolvedShell);
                        res = PtyNative.openPtyShellExec(chrootCmd);
                        if (res == null) res = PtyNative.openPtyShell();
                    } else {
                        res = PtyNative.openPtyShell();
                    }
                } else {
                    // Native not loaded; no PTY available
                    res = null;
                }
                if (res == null || res.length < 2) {
                    Log.e(TAG, "PTY open failed; session will have no PTY");
                    return;
                }
                s.ptyFd = res[0]; s.ptyPid = res[1];
                s.ptyPfd = ParcelFileDescriptor.adoptFd(s.ptyFd);
                s.in = new FileInputStream(s.ptyPfd.getFileDescriptor());
                s.out = new FileOutputStream(s.ptyPfd.getFileDescriptor());
                s.readThread = new Thread(() -> readLoop(s), "pty-read-" + s.id);
                s.readThread.start();
                // Write initial newline to prompt
                send(s.id, "\n");
            } catch (Throwable t) {
                Log.e(TAG, "startPtyForSession failed", t);
            }
        });
    }

    private void readLoop(@NonNull Session s) {
        try {
            InputStream is = s.in;
            byte[] buf = new byte[4096]; int n;
            while (!s.stopping && (n = is.read(buf)) != -1) {
                String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                s.appendAndDispatch(new TerminalEvent(chunk, false), mainHandler);
            }
        } catch (IOException e) {
            if (!s.stopping) Log.e(TAG, "readLoop error", e);
        } finally {
            for (TerminalListener l : s.listeners) {
                int exit = 0; // unknown
                mainHandler.post(() -> l.onSessionClosed(s.id, exit));
            }
        }
    }

    private void stopPty(@NonNull Session s) {
        s.stopping = true;
        try { if (s.out != null) { try { s.out.write("exit\n".getBytes(StandardCharsets.UTF_8)); s.out.flush(); } catch (IOException ignored) {} } } finally {
            try { if (s.out != null) s.out.close(); } catch (IOException ignored) {}

            try { if (s.ptyPfd != null) s.ptyPfd.close(); } catch (IOException ignored) {}
            if (s.readThread != null) { try { s.readThread.join(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } if (s.readThread.isAlive()) s.readThread.interrupt(); }
            if (s.ptyPid > 0) { try { PtyNative.killChild(s.ptyPid, 9); } catch (Throwable ignored) {} }
        }
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "NethunterChannelService",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    private void startInForeground() {
        Notification n = buildNotification();
        startForeground(NOTIFY_ID, n);
    }

    private void updateForegroundNotification() {
        Notification n = buildNotification();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        NotificationManagerCompat.from(this).notify(NOTIFY_ID, n);
    }

    private Notification buildNotification() {
        int count = sessions.size();
        Intent intent = new Intent(this, AppNavHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ic_nh_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(count == 0 ? "Terminal idle" : (count + " terminal session" + (count == 1 ? "" : "s") + " running"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    // Helpers copied from TerminalFragment for chroot command resolution
    private boolean isChrootAvailable() {
        try { File root = new File(NhPaths.CHROOT_PATH()); return root.isDirectory() && new File(root, "bin/bash").exists(); } catch (Throwable t) { return false; }
    }

    private String resolvePreferredShell(@NonNull Context ctx) {
        // Mirror TerminalFragment: prefer bash fallback
        String root = NhPaths.CHROOT_PATH();
        List<String> candidates = new ArrayList<>();
        candidates.add("/bin/bash"); candidates.add("/usr/bin/bash");
        candidates.add("/bin/sh"); candidates.add("/usr/bin/sh");
        for (String rel : candidates) { File f = new File(root + rel); if (f.exists() && f.canExecute()) return rel; }
        return "/bin/bash";
    }

    private String buildChrootShellCommand(@NonNull Context ctx, String shellPath) {
        String chrootRoot = NhPaths.CHROOT_PATH(); String busybox = NhPaths.BUSYBOX;
        String resolvedShell = (shellPath != null) ? shellPath : resolvePreferredShell(ctx);
        String loginFlag = loginFlagForShell(resolvedShell);
        String assignments = buildExportAssignments(resolvedShell);
        String envCmd = "/usr/bin/env -i " + assignments;
        if (busybox != null && !busybox.isEmpty() && new File(chrootRoot + resolvedShell).exists()) {
            return busybox + " chroot " + chrootRoot + ' ' + envCmd + ' ' + resolvedShell + ' ' + loginFlag;
        }
        return NhPaths.APP_SCRIPTS_PATH + "/bootkali_bash";
    }

    private String loginFlagForShell(String shellPath) {
        if (shellPath == null) return "--login";
        if (shellPath.endsWith("zsh")) return "-l"; if (shellPath.endsWith("fish")) return "-l"; if (shellPath.endsWith("sh")) return ""; return "--login";
    }

    private String buildExportAssignments(String resolvedShell) {
        String DEFAULT_HOSTNAME = "kali";
        return "HOME=/root USER=root LOGNAME=root SHELL=" + resolvedShell +
                " HOSTNAME=" + DEFAULT_HOSTNAME +
                " TERM=xterm-256color COLORTERM=truecolor CLICOLOR_FORCE=1 FORCE_COLOR=1 LANG=en_US.UTF-8 LC_ALL=C PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH";
    }
}

