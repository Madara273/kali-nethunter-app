package com.offsec.nethunter;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.offsec.nethunter.pty.PtyNative;
import com.offsec.nethunter.service.TerminalService;
import com.offsec.nethunter.terminal.TerminalAdapter;
import com.offsec.nethunter.utils.NhPaths;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TerminalFragment extends Fragment implements MenuProvider {
    private static final String TAG = "TerminalFragment";
    private static final String ARG_ITEM_ID = "item_id";
    private static final String KEY_INITIAL_COMMAND = "initial_command";
    private static final boolean USE_PTY = true;
    private static final boolean USE_CHROOT_DIRECT = true;
    private static final int RING_MAX_LINES = 5000;
    private static final float MIN_TEXT_SP = 8f;
    private static final float MAX_TEXT_SP = 32f;
    private static final float DEFAULT_TEXT_SP = 12f;
    private static final String PREFS_NAME = "terminal_prefs";
    private static final String KEY_TEXT_SIZE = "text_size_sp";
    private static final String KEY_THEME_BG = "terminal_theme_bg";
    private static final String KEY_THEME_FG = "terminal_theme_fg";
    private static final String KEY_FORMAT_PRESET = "terminal_format_preset";
    private static final String KEY_LINE_SPACING_EXTRA = "terminal_line_spacing_extra";
    private static final String KEY_LINE_SPACING_MULT = "terminal_line_spacing_mult";
    private static final String KEY_PREF_SHELL = "preferred_shell";
    private static final String KEY_FIRST_RUN_SETUP_SHOWN = "first_run_setup_shown";
    private static final String KEY_PREF_INITIAL_CMD_TEXT = "initial_cmd_text";
    private static final String KEY_PREF_INITIAL_CMD_ENABLED = "initial_cmd_enabled";
    private static final String DEFAULT_HOSTNAME = "kali";
    private static final int PERSISTENT_BUFFER_SIZE = 100;
    private static final List<CharSequence> persistentLines = new ArrayList<>();
    private TextInputEditText inputEdit;
    private RecyclerView terminalRecycler;
    private TerminalAdapter terminalAdapter;
    private View ctrlButton;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabGoBottom;
    private Process process;
    private volatile OutputStream outputStream;
    private static volatile BufferedWriter writer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Thread outputThread;
    private Thread errorThread;
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String pendingCurrentLine = "";
    private int defaultFgColor;
    private int currentFgColor;
    private final int defaultBgColor = 0x00000000;
    private int currentBgColor = 0x00000000;
    private boolean currentBold = false;
    private boolean currentUnderline = false;
    private String ansiCarry = "";
    private volatile int ptyFd = -1;
    private volatile int ptyPid = -1;
    private volatile ParcelFileDescriptor ptyPfd;
    private volatile FileInputStream ptyIn;
    private static volatile FileOutputStream ptyOut;
    private Thread ptyReadThread;
    private SpannableStringBuilder currentLine = new SpannableStringBuilder();
    private int currentLineSegmentStart = 0;
    private ScaleGestureDetector scaleDetector;
    private boolean ctrlSticky = false;
    private boolean suppressTextWatcher = false;
    private int pendingInsertStart = -1;
    private int pendingInsertCount = 0;
    private char pendingInsertChar;

    private TerminalService boundService;
    private boolean serviceBound = false;
    private int serviceSessionId = -1;

    private static class ThemePreset {
        final String name; final int bg; final int fg;
        ThemePreset(String n, int b, int f) { name = n; bg = b; fg = f; }
    }

    private static final ThemePreset[] THEME_PRESETS = new ThemePreset[] {
            new ThemePreset("Classic Dark", Color.parseColor("#121212"), Color.parseColor("#ECEFF1")),
            new ThemePreset("Solarized Dark", Color.parseColor("#002b36"), Color.parseColor("#93a1a1")),
            new ThemePreset("Solarized Light", Color.parseColor("#fdf6e3"), Color.parseColor("#657b83")),
            new ThemePreset("Dracula", Color.parseColor("#282a36"), Color.parseColor("#f8f8f2")),
            new ThemePreset("One Dark", Color.parseColor("#282c34"), Color.parseColor("#abb2bf")),
            new ThemePreset("High Contrast", Color.parseColor("#000000"), Color.parseColor("#FFFFFF")),
            new ThemePreset("Matrix", Color.parseColor("#000000"), Color.parseColor("#00FF00")),
            new ThemePreset("Kali Linux", Color.parseColor("#000000"), Color.parseColor("#DC143C"))
    };

    public static TerminalFragment newInstance(int itemId) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ITEM_ID, itemId);
        fragment.setArguments(args);
        return fragment;
    }

    public static TerminalFragment newInstanceWithCommand(int itemId, @Nullable String initialCmd) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ITEM_ID, itemId);
        if (initialCmd != null && !initialCmd.trim().isEmpty()) {
            args.putString(KEY_INITIAL_COMMAND, initialCmd);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NhPaths.getInstance(requireContext());
        requireActivity().addMenuProvider(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        terminalRecycler = view.findViewById(R.id.terminal_recycler);
        terminalRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        terminalAdapter = new TerminalAdapter(RING_MAX_LINES);
        terminalAdapter.setTextSizeSp(loadPersistedTextSize());
        loadAndApplyPersistedFormat(terminalAdapter);
        terminalRecycler.setAdapter(terminalAdapter);

        RecyclerView.ItemAnimator itemAnimator = terminalRecycler.getItemAnimator();
        if (itemAnimator instanceof DefaultItemAnimator) {
            DefaultItemAnimator da = (DefaultItemAnimator) itemAnimator;
            da.setSupportsChangeAnimations(false);
            da.setChangeDuration(0);
        }

        // Batch-populate any persisted lines to leverage AsyncListDiffer diffs
        if (!persistentLines.isEmpty()) {
            terminalAdapter.addLines(persistentLines, terminalRecycler);
        }

        inputEdit = view.findViewById(R.id.input_edit);
        loadAndApplyPersistedTheme();

        fabGoBottom = view.findViewById(R.id.fab_go_bottom);
        if (fabGoBottom != null) {
            fabGoBottom.hide();
            fabGoBottom.setOnClickListener(v -> {
                if (terminalAdapter != null && terminalRecycler != null) {
                    int count = terminalAdapter.getItemCount();
                    if (count > 0) terminalRecycler.smoothScrollToPosition(count - 1);
                }
                fabGoBottom.hide();
            });
        }
        terminalRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { updateFabVisibilityByScroll(dy); }
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) { updateFabVisibilityByScroll(0); }
        });
        updateFabVisibilityByScroll(0);

        TextInputLayout inputLayout = view.findViewById(R.id.input_layout);
        if (inputLayout != null) {
            inputLayout.setEndIconOnClickListener(v -> sendCommand());
            boolean hasInitial = inputEdit != null && inputEdit.getText() != null && !inputEdit.getText().toString().trim().isEmpty();
            inputLayout.setEndIconVisible(hasInitial);
        }

        View btnTab = view.findViewById(R.id.btn_tab);
        View btnLeft = view.findViewById(R.id.btn_left);
        View btnRight = view.findViewById(R.id.btn_right);
        View btnUp = view.findViewById(R.id.btn_up);
        View btnDown = view.findViewById(R.id.btn_down);
        ctrlButton = view.findViewById(R.id.btn_ctrl);
        View btnClear = view.findViewById(R.id.terminal_cmd_clear);
        if (btnClear != null) btnClear.setOnClickListener(v -> clearTerminal());

        updateCtrlButtonState();
        if (inputEdit != null) { defaultFgColor = inputEdit.getCurrentTextColor(); currentFgColor = defaultFgColor; }

        scaleDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(@NonNull ScaleGestureDetector detector) {
                if (terminalAdapter == null) return false;
                float current = terminalAdapter.getTextSizeSp();
                float factor = Math.max(0.5f, Math.min(2f, detector.getScaleFactor()));
                float newSize = clamp(current * factor);
                if (Math.abs(newSize - current) >= 0.2f) applyTextSize(newSize);
                return true;
            }
        });
        terminalRecycler.setOnTouchListener((v, event) -> {
            if (scaleDetector != null) scaleDetector.onTouchEvent(event);
            boolean scaling = scaleDetector != null && scaleDetector.isInProgress();
            if (!scaling && event.getAction() == android.view.MotionEvent.ACTION_UP) v.performClick();
            return scaling;
        });

        if (inputEdit != null) {
            inputEdit.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) { sendCommand(); return true; }
                if (event.getAction() == KeyEvent.ACTION_DOWN && (event.isCtrlPressed() || ctrlSticky)) {
                    int ctrlCode = controlCodeForKeyCode(keyCode);
                    if (ctrlCode > 0) {
                        sendControlCode(ctrlCode);
                        if (ctrlSticky) { ctrlSticky = false; updateCtrlVisualState(); }
                        return true;
                    }
                }
                return false;
            });
            if (inputLayout != null) {
                inputEdit.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (ctrlSticky && count > 0) {
                            pendingInsertStart = start;
                            pendingInsertCount = count;
                            try { pendingInsertChar = s.charAt(start + count - 1); } catch (Throwable ignored) { pendingInsertChar = 0; }
                        }
                    }
                    @Override public void afterTextChanged(Editable s) {
                        inputLayout.setEndIconVisible(s != null && !s.toString().trim().isEmpty());
                        if (s == null) return;
                        if (!ctrlSticky || pendingInsertCount <= 0 || suppressTextWatcher) return;
                        int code = controlCodeForChar(pendingInsertChar);
                        if (code > 0) sendControlCode(code);
                        ctrlSticky = false; updateCtrlVisualState();
                        try {
                            suppressTextWatcher = true;
                            int start = Math.max(0, Math.min(s.length(), pendingInsertStart));
                            int end = Math.max(start, Math.min(s.length(), start + pendingInsertCount));
                            if (end > start) s.delete(start, end);
                        } finally {
                            suppressTextWatcher = false;
                            pendingInsertStart = -1; pendingInsertCount = 0; pendingInsertChar = 0;
                        }
                    }
                });
            }
        }
        if (btnTab != null) btnTab.setOnClickListener(v -> insertAtCursor());
        if (btnLeft != null) btnLeft.setOnClickListener(v -> moveCursor(-1));
        if (btnRight != null) btnRight.setOnClickListener(v -> moveCursor(1));
        if (btnUp != null) btnUp.setOnClickListener(v -> navigateHistory(-1));
        if (btnDown != null) btnDown.setOnClickListener(v -> navigateHistory(1));
        if (ctrlButton != null) ctrlButton.setOnClickListener(v -> { ctrlSticky = !ctrlSticky; updateCtrlVisualState(); });

        Bundle args = getArguments();
        if (args != null && args.containsKey(KEY_INITIAL_COMMAND)) {
            final String initCmd = args.getString(KEY_INITIAL_COMMAND);
            if (initCmd != null && !initCmd.trim().isEmpty() && !"uname -a".equals(initCmd.trim())) {
                handler.postDelayed(() -> sendLine(initCmd), 650);
            }
        }
        terminalRecycler.post(this::updatePtyWindowSize);
        // Don't spin up legacy PTY eagerly; the service will own sessions. Keep legacy as fallback.
        // if (ptyOut == null) startTerminal();

        // Start and bind to TerminalService for background sessions
        Intent svc = new Intent(requireContext(), TerminalService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(requireContext(), svc);
            } else {
                requireContext().startService(svc);
            }
        } catch (Throwable ignored) {}
        requireContext().bindService(svc, serviceConnection, Context.BIND_AUTO_CREATE);

        // Show first-run setup dialog once
        handler.postDelayed(this::maybeShowFirstRunSetupDialog, 500);
        // Run saved initial command on open if enabled
        handler.postDelayed(() -> maybeRunSavedInitialCommand(getArguments()), 700);
        return view;
    }

    private void updateCtrlButtonState() {
        if (ctrlButton == null) return;
        boolean enabled = (serviceBound && serviceSessionId > 0) || (ptyOut != null || outputStream != null);
        ctrlButton.setEnabled(enabled);
        ctrlButton.setAlpha(enabled ? (ctrlSticky ? 1.0f : 0.95f) : 0.5f);
    }

    private void updateCtrlVisualState() {
        if (ctrlButton == null) return;
        ctrlButton.setSelected(ctrlSticky);
        ctrlButton.setAlpha(ctrlButton.isEnabled() ? (ctrlSticky ? 1.0f : 0.95f) : 0.5f);
    }

    private void insertAtCursor() {
        if (inputEdit == null) return;
        int start = inputEdit.getSelectionStart();
        int end = inputEdit.getSelectionEnd();
        Editable editable = inputEdit.getText();
        if (editable == null) return;
        if (start < 0) start = editable.length(); if (end < 0) end = start;
        editable.replace(Math.min(start, end), Math.max(start, end), "\t");
        int newPos = Math.min(start, end) + 1; inputEdit.setSelection(newPos);
    }

    private void moveCursor(int delta) {
        if (inputEdit == null) return;
        int len = inputEdit.length();
        int pos = inputEdit.getSelectionStart();
        if (pos < 0) pos = len;
        int newPos = Math.max(0, Math.min(len, pos + delta));
        inputEdit.setSelection(newPos);
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty() || inputEdit == null) return;
        if (historyIndex == -1) { pendingCurrentLine = inputEdit.getText() != null ? inputEdit.getText().toString() : ""; historyIndex = commandHistory.size(); }
        historyIndex = Math.max(0, Math.min(commandHistory.size(), historyIndex + direction));
        setInputText(historyIndex == commandHistory.size() ? pendingCurrentLine : commandHistory.get(historyIndex));
    }

    private void setInputText(String text) { if (inputEdit == null) return; inputEdit.setText(text); inputEdit.setSelection(text.length()); }

    private void recordHistory(String command) {
        if (command == null) return; String trimmed = command.trim(); if (trimmed.isEmpty()) return;
        if (!commandHistory.isEmpty() && commandHistory.get(commandHistory.size() - 1).equals(trimmed)) { historyIndex = -1; return; }
        commandHistory.add(trimmed); historyIndex = -1;
    }

    private void startTerminal() {
        if (USE_PTY && PtyNative.isLoaded()) startTerminalPty();
        else { if (USE_PTY && !PtyNative.isLoaded()) Log.d(TAG, "[!] native-lib not loaded; falling back to non-PTY shell."); startTerminalProcess(); }
    }

    private void startTerminalProcess() {
        Log.d(TAG, "Starting terminal process");
        new Thread(() -> {
            try {
                process = Runtime.getRuntime().exec("su -mm");
                outputStream = process.getOutputStream();
                writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                final InputStream stdout = process.getInputStream();
                final InputStream stderr = process.getErrorStream();
                outputThread = new Thread(() -> readStream(stdout, false), "term-out");
                errorThread = new Thread(() -> readStream(stderr, true), "term-err");
                outputThread.start(); errorThread.start();
                String init = getEntryCmd() + "\n" +
                        "TERM=xterm-256color CLICOLOR_FORCE=1 FORCE_COLOR=1 COLORTERM=truecolor\n" +
                        "cd " + NhPaths.CHROOT_HOME + "\n";
                writer.write(init); writer.flush();
                handler.post(this::updateCtrlButtonState);
            } catch (IOException e) { Log.e(TAG, "Failed to start terminal", e); }
        }).start();
    }

    private void startTerminalPty() {
        Log.d(TAG, "Starting PTY terminal");
        new Thread(() -> {
            try {
                int[] res;
                if (USE_CHROOT_DIRECT && PtyNative.isLoaded()) {
                    if (!isChrootAvailable()) { Log.d(TAG, "[!] Chroot not available; falling back to generic PTY shell."); res = PtyNative.openPtyShell(); }
                    else {
                        String resolvedShell = resolvePreferredShell();
                        String chrootCmd = buildChrootShellCommand(resolvedShell);
                        Log.d(TAG, "Launching chroot command: " + chrootCmd);
                        res = PtyNative.openPtyShellExec(chrootCmd);
                        if (res == null) { Log.d(TAG, "[!] Direct chroot launch failed, fallback to generic PTY shell."); res = PtyNative.openPtyShell(); }
                        else { Log.d(TAG, "[+] Chroot shell started (direct) using shell: " + resolvedShell); }
                    }
                } else { res = PtyNative.openPtyShell(); }
                if (res == null || res.length < 2) { Log.d(TAG, "[!] PTY open failed, falling back to non-PTY shell."); startTerminalProcess(); return; }
                ptyFd = res[0]; ptyPid = res[1];
                ptyPfd = ParcelFileDescriptor.adoptFd(ptyFd);
                ptyIn = new FileInputStream(ptyPfd.getFileDescriptor());
                ptyOut = new FileOutputStream(ptyPfd.getFileDescriptor());
                ptyReadThread = new Thread(() -> readStream(ptyIn, false), "pty-reader"); ptyReadThread.start();
                if (!USE_CHROOT_DIRECT) writePty("(stty -echo 2>/dev/null) >/dev/null 2>&1\n"); else writePty("\n");
                handler.post(this::updateCtrlButtonState);
                scheduleInitialWindowSizeUpdate();
            } catch (Exception e) {
                Log.e(TAG, "PTY startup failed", e); startTerminalProcess();
            }
        }).start();
    }

    private void scheduleInitialWindowSizeUpdate() { if (terminalRecycler == null) return; terminalRecycler.post(this::updatePtyWindowSize); }

    private void updatePtyWindowSize() {
        if (serviceBound && serviceSessionId > 0) {
            // compute cols/rows and send to service
            TextPaint tp = new TextPaint(); tp.setTypeface(Typeface.MONOSPACE);
            float activeSp = (terminalAdapter != null) ? terminalAdapter.getTextSizeSp() : 12f;
            tp.setTextSize(spToPx(activeSp));
            float charWidth = tp.measureText("M"); float lineHeight = tp.getFontMetrics().bottom - tp.getFontMetrics().top;
            int w = terminalRecycler.getWidth(); int h = terminalRecycler.getHeight();
            if (w <= 0 || h <= 0 || charWidth <= 0 || lineHeight <= 0) return;
            int cols = Math.max(20, (int)(w / charWidth)); int rows = Math.max(5, (int)(h / lineHeight));
            try { boundService.resizePty(serviceSessionId, cols, rows); } catch (Throwable ignored) {}
            return;
        }
        // legacy path
        if (!USE_PTY || ptyFd < 0 || !PtyNative.isLoaded() || terminalRecycler == null) return;
        TextPaint tp = new TextPaint(); tp.setTypeface(Typeface.MONOSPACE);
        float activeSp = (terminalAdapter != null) ? terminalAdapter.getTextSizeSp() : 12f;
        tp.setTextSize(spToPx(activeSp));
        float charWidth = tp.measureText("M"); float lineHeight = tp.getFontMetrics().bottom - tp.getFontMetrics().top;
        int w = terminalRecycler.getWidth(); int h = terminalRecycler.getHeight();
        if (w <= 0 || h <= 0 || charWidth <= 0 || lineHeight <= 0) return;
        int cols = Math.max(20, (int)(w / charWidth)); int rows = Math.max(5, (int)(h / lineHeight));
        try { PtyNative.setWindowSize(ptyFd, cols, rows); } catch (Throwable ignored) {}
    }

    private float spToPx(float sp) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, requireContext().getResources().getDisplayMetrics()); }

    private int controlCodeForKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            int letterIndex = keyCode - KeyEvent.KEYCODE_A; // 0..25
            char ch = (char) ('A' + letterIndex);
            return controlCodeForChar(ch);
        }
        return 0;
    }

    private int controlCodeForChar(char ch) {
        if (ch >= 'a' && ch <= 'z') ch = (char) (ch - 'a' + 'A');
        if (ch >= 'A' && ch <= 'Z') {
            return (ch & 0x1F);
        }
        return 0;
    }

    private void sendControlCode(int code) {
        if (code <= 0 || code > 31) return;
        if (serviceBound && serviceSessionId > 0) {
            boundService.sendControl(serviceSessionId, code);
            return;
        }
        new Thread(() -> {
            try {
                if (ptyOut != null) { ptyOut.write(new byte[]{(byte) code}); ptyOut.flush(); }
                else if (outputStream != null) { try { outputStream.write(code); outputStream.flush(); } catch (IOException ignored) {} }
                else { Log.d(TAG, "[!] Shell not ready for control code."); }
            } catch (IOException e) { Log.e(TAG, "Failed sending control code " + code, e); }
        }).start();
    }

    private void applyThemeColors(int bgColor, int fgColor) { applyThemeColors(bgColor, fgColor, true); }

    private void applyThemeColors(int bgColor, int fgColor, boolean persist) {
        if (terminalRecycler != null) terminalRecycler.setBackgroundColor(bgColor);
        if (terminalAdapter != null) terminalAdapter.setBaseTextColor(fgColor);
        defaultFgColor = fgColor; currentFgColor = fgColor; currentBgColor = 0x00000000; currentBold = false; currentUnderline = false;
        if (inputEdit != null) inputEdit.setTextColor(fgColor);
        if (persist) { SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); prefs.edit().putInt(KEY_THEME_BG, bgColor).putInt(KEY_THEME_FG, fgColor).apply(); }
    }

    private void loadAndApplyPersistedTheme() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int defBg = Color.parseColor("#121212");
        int defFg = (inputEdit != null) ? inputEdit.getCurrentTextColor() : Color.parseColor("#ECEFF1");
        int bg = prefs.getInt(KEY_THEME_BG, defBg); int fg = prefs.getInt(KEY_THEME_FG, defFg);
        applyThemeColors(bg, fg);
    }

    private void loadAndApplyPersistedFormat(TerminalAdapter adapter) {
        if (adapter == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float extra = prefs.getFloat(KEY_LINE_SPACING_EXTRA, 0f); float mult = prefs.getFloat(KEY_LINE_SPACING_MULT, 1.0f);
        adapter.setLineSpacing(extra, mult);
    }

    private void persistFormat(String presetName, float lineExtra, float lineMult) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FORMAT_PRESET, presetName).putFloat(KEY_LINE_SPACING_EXTRA, lineExtra).putFloat(KEY_LINE_SPACING_MULT, lineMult).apply();
    }

    private float dp(float v) { return v * requireContext().getResources().getDisplayMetrics().density; }

    private void showThemePicker() {
        if (!isAdded()) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View content = inflater.inflate(R.layout.dialog_terminal_theme, (ViewGroup) getView(), false);
        final ChipGroup chipsThemes = content.findViewById(R.id.chips_themes);
        final ChipGroup chipsFormat = content.findViewById(R.id.chips_format);
        final MaterialCardView previewCard = content.findViewById(R.id.preview_card);
        final TextView previewTitle = content.findViewById(R.id.preview_title);
        final TextView previewL1 = content.findViewById(R.id.preview_line1);
        final TextView previewL2 = content.findViewById(R.id.preview_line2);
        final TextView previewL3 = content.findViewById(R.id.preview_line3);
        final MaterialButton btnReset = content.findViewById(R.id.btn_reset);
        final MaterialButton btnCancel = content.findViewById(R.id.btn_cancel);
        final MaterialButton btnApply = content.findViewById(R.id.btn_apply);

        final List<Chip> themeChips = new ArrayList<>();
        int[] themeChipIds = {R.id.chip_theme_0, R.id.chip_theme_1, R.id.chip_theme_2, R.id.chip_theme_3, R.id.chip_theme_4, R.id.chip_theme_5, R.id.chip_theme_6, R.id.chip_theme_7};
        for (int i = 0; i < THEME_PRESETS.length; i++) {
            Chip chip = content.findViewById(themeChipIds[i]);
            chip.setCheckable(true);
            chip.setChipIconResource(R.drawable.ic_palette); chip.setChipIconTint(android.content.res.ColorStateList.valueOf(THEME_PRESETS[i].fg));
            chip.setTag(i); themeChips.add(chip);
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int defBg = Color.parseColor("#121212");
        int defFg = (inputEdit != null) ? inputEdit.getCurrentTextColor() : Color.parseColor("#ECEFF1");
        int curBg = prefs.getInt(KEY_THEME_BG, defBg); int curFg = prefs.getInt(KEY_THEME_FG, defFg);
        boolean matched = false;
        for (int i = 0; i < THEME_PRESETS.length; i++) {
            if (THEME_PRESETS[i].bg == curBg && THEME_PRESETS[i].fg == curFg) { themeChips.get(i).setChecked(true); matched = true; break; }
        }
        if (!matched && !themeChips.isEmpty()) { themeChips.get(0).setChecked(true); }

        String fmtPref = prefs.getString(KEY_FORMAT_PRESET, "Compact");
        int fmtChipId = R.id.chip_format_compact;
        if ("Minimal".equalsIgnoreCase(fmtPref)) fmtChipId = R.id.chip_format_minimal;
        else if ("Comfortable".equalsIgnoreCase(fmtPref)) fmtChipId = R.id.chip_format_comfortable;
        else if ("Large".equalsIgnoreCase(fmtPref)) fmtChipId = R.id.chip_format_large;
        chipsFormat.check(fmtChipId);

        final int originalBg = curBg; final int originalFg = curFg;
        final float originalSize = (terminalAdapter != null) ? terminalAdapter.getTextSizeSp() : DEFAULT_TEXT_SP;
        final float originalExtra = prefs.getFloat(KEY_LINE_SPACING_EXTRA, 0f);
        final float originalMult = prefs.getFloat(KEY_LINE_SPACING_MULT, 1.0f);
        final boolean[] applied = new boolean[]{false};

        final Runnable updatePreview = () -> {
            int themeIdx = 0; int checkedId = chipsThemes.getCheckedChipId();
            if (checkedId != View.NO_ID) { Chip c = content.findViewById(checkedId); if (c != null && c.getTag() instanceof Integer) themeIdx = (Integer) c.getTag(); }
            ThemePreset sel = THEME_PRESETS[themeIdx];
            String fmt = "Compact"; int checkedFmt = chipsFormat.getCheckedChipId();
            if (checkedFmt == R.id.chip_format_minimal) fmt = "Minimal";
            else if (checkedFmt == R.id.chip_format_comfortable) fmt = "Comfortable";
            else if (checkedFmt == R.id.chip_format_large) fmt = "Large";
            previewCard.setCardBackgroundColor(sel.bg);
            int fg = sel.fg; previewTitle.setTextColor(fg); previewL1.setTextColor(fg); previewL2.setTextColor(fg); previewL3.setTextColor(fg);
            float sizeSp; float extraPx; float mult;
            switch (fmt) {
                case "Minimal": sizeSp = MIN_TEXT_SP; extraPx = dp(0f); mult = 1.0f; break;
                case "Comfortable": sizeSp = 14f; extraPx = dp(2f); mult = 1.08f; break;
                case "Large": sizeSp = 16f; extraPx = dp(4f); mult = 1.12f; break;
                default: sizeSp = 12f; extraPx = dp(0f); mult = 1.0f; break;
            }
            previewL1.setTextSize(sizeSp); previewL2.setTextSize(sizeSp); previewL3.setTextSize(sizeSp);
            previewL1.setLineSpacing(extraPx, mult); previewL2.setLineSpacing(extraPx, mult); previewL3.setLineSpacing(extraPx, mult);
            applyThemeColors(sel.bg, sel.fg, false); applyFormatPreset(fmt, false);
        };

        chipsThemes.setOnCheckedStateChangeListener((group, checkedIds) -> updatePreview.run());
        chipsFormat.setOnCheckedStateChangeListener((group, checkedId) -> updatePreview.run());
        updatePreview.run();

        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        dialog.setContentView(content);

        btnReset.setOnClickListener(v -> { if (!themeChips.isEmpty()) { for (Chip c : themeChips) c.setChecked(false); themeChips.get(0).setChecked(true);} chipsFormat.check(R.id.chip_format_compact); updatePreview.run(); });
        btnCancel.setOnClickListener(v -> { applyThemeColors(originalBg, originalFg, false); applyTextSize(originalSize, false); if (terminalAdapter != null) terminalAdapter.setLineSpacing(originalExtra, originalMult); dialog.dismiss(); });
        btnApply.setOnClickListener(v -> {
            int themeIdx = 0; int checkedId = chipsThemes.getCheckedChipId();
            if (checkedId != View.NO_ID) { Chip c = content.findViewById(checkedId); if (c != null && c.getTag() instanceof Integer) themeIdx = (Integer) c.getTag(); }
            ThemePreset sel = THEME_PRESETS[themeIdx]; applyThemeColors(sel.bg, sel.fg, true);
            int checkedFmt = chipsFormat.getCheckedChipId(); String fmt = "Compact";
            if (checkedFmt == R.id.chip_format_minimal) fmt = "Minimal";
            else if (checkedFmt == R.id.chip_format_comfortable) fmt = "Comfortable";
            else if (checkedFmt == R.id.chip_format_large) fmt = "Large";
            applyFormatPreset(fmt, true); applied[0] = true; dialog.dismiss();
        });

        dialog.setOnCancelListener(d -> { if (!applied[0]) { applyThemeColors(originalBg, originalFg, false); applyTextSize(originalSize, false); if (terminalAdapter != null) terminalAdapter.setLineSpacing(originalExtra, originalMult); } });
        dialog.setOnDismissListener(d -> { if (!applied[0]) { applyThemeColors(originalBg, originalFg, false); applyTextSize(originalSize, false); if (terminalAdapter != null) terminalAdapter.setLineSpacing(originalExtra, originalMult); } });

        dialog.show();
    }

    private void applyFormatPreset(String preset) { applyFormatPreset(preset, true); }

    private void applyFormatPreset(String preset, boolean persist) {
        float sizeSp; float lineExtraPx; float lineMult;
        switch (preset) {
            case "Minimal": sizeSp = MIN_TEXT_SP; lineExtraPx = dp(0f); lineMult = 1.0f; break;
            case "Comfortable": sizeSp = 14f; lineExtraPx = dp(2f); lineMult = 1.08f; break;
            case "Large": sizeSp = 16f; lineExtraPx = dp(4f); lineMult = 1.12f; break;
            case "Compact":
            default: sizeSp = 12f; lineExtraPx = dp(0f); lineMult = 1.0f; break;
        }
        applyTextSize(sizeSp, persist);
        if (terminalAdapter != null) terminalAdapter.setLineSpacing(lineExtraPx, lineMult);
        if (persist) persistFormat(preset, lineExtraPx, lineMult);
    }

    private float clamp(float v) { return Math.min(MAX_TEXT_SP, Math.max(MIN_TEXT_SP, v)); }

    private float loadPersistedTextSize() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float s = prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SP); return clamp(s);
    }

    private void persistTextSize(float sizeSp) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_TEXT_SIZE, sizeSp).apply();
    }

    private void applyTextSize(float sizeSp) { applyTextSize(sizeSp, true); }

    private void applyTextSize(float sizeSp, boolean persist) {
        if (terminalAdapter == null) return; terminalAdapter.setTextSizeSp(sizeSp);
        if (persist) persistTextSize(sizeSp);
        terminalRecycler.postDelayed(this::updatePtyWindowSize, 100);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.terminal_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            View actionView = searchItem.getActionView();
            if (actionView instanceof SearchView) {
                SearchView sv = (SearchView) actionView; sv.setQueryHint("Search output...");
                sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String query) { if (terminalAdapter != null) terminalAdapter.setHighlightTerm(query); searchAndScrollTo(query); return true; }
                    @Override public boolean onQueryTextChange(String newText) { if (terminalAdapter != null) terminalAdapter.setHighlightTerm(newText); return true; }
                });
            }
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_restart) { restartTerminal(); return true; }
        else if (id == R.id.action_print_dmesg) { printDmesg(); return true; }
        else if (id == R.id.action_search) { performSearch(); return true; }
        else if (id == R.id.action_save_output) { saveOutput(); return true; }
        else if (id == R.id.action_theme) { showThemePicker(); return true; }
        else if (id == R.id.action_open_setup) { showFirstRunSetupDialog(); return true; }
        else if (id == R.id.action_initial_command) { showInitialCommandDialog(); return true; }
        return false;
    }

    private void printDmesg() { String cmd = "(dmesg -T 2>/dev/null || dmesg 2>/dev/null || logcat -b kernel -d 2>/dev/null) | while read line; do echo \"$line\"; sleep 0.01; done"; sendLine(cmd); }

    private void performSearch() {
        if (terminalAdapter != null) terminalAdapter.setHighlightTerm(null);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Search Terminal Output");
        final EditText input = new EditText(requireContext()); input.setHint("Enter search term"); builder.setView(input);
        builder.setPositiveButton("Search", (dialog, which) -> {
            String term = input.getText().toString().trim();
            if (!term.isEmpty()) { searchAndScrollTo(term); if (terminalAdapter != null) terminalAdapter.setHighlightTerm(term); }
            else { Toast.makeText(requireContext(), "Please enter a search term", Toast.LENGTH_SHORT).show(); }
        });
        builder.setNegativeButton("Cancel", null); builder.show();
    }

    private void searchAndScrollTo(String term) {
        if (terminalAdapter == null || terminalRecycler == null) return;
        List<CharSequence> lines = terminalAdapter.getLines();
        for (int i = 0; i < lines.size(); i++) { if (lines.get(i).toString().toLowerCase().contains(term.toLowerCase())) { terminalRecycler.scrollToPosition(i); return; } }
        Toast.makeText(requireContext(), "Not found", Toast.LENGTH_SHORT).show();
    }

    private void saveOutput() {
        if (terminalAdapter == null) return;
        List<CharSequence> lines = terminalAdapter.getLines(); StringBuilder sb = new StringBuilder();
        for (CharSequence line : lines) { sb.append(line).append("\n"); }
        try {
            File nhFilesDir = new File(Environment.getExternalStorageDirectory(), "nh_files");
            if (!nhFilesDir.exists()) { boolean created = nhFilesDir.mkdirs(); if (!created && !nhFilesDir.exists()) Log.w(TAG, "Failed to create directory: " + nhFilesDir.getAbsolutePath()); }
            File outputFile = new File(nhFilesDir, "terminal_output.txt");
            FileOutputStream fos = new FileOutputStream(outputFile); fos.write(sb.toString().getBytes(StandardCharsets.UTF_8)); fos.close();
            Toast.makeText(requireContext(), "Output saved to " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) { Toast.makeText(requireContext(), "Failed to save output: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void restartTerminal() { stopTerminal(); clearTerminal(); handler.postDelayed(this::startTerminal, 250); }

    private void sendCommand() {
        String command = inputEdit.getText() != null ? inputEdit.getText().toString() : "";
        String trimmed = command.trim(); boolean isClear = trimmed.equals("clear") || trimmed.equals("reset");
        if (!trimmed.isEmpty()) recordHistory(command);
        pendingCurrentLine = ""; inputEdit.setText(""); if (isClear) { clearTerminal(); }
        Log.d(TAG, "Sending command: " + command);
        if (serviceBound && serviceSessionId > 0) {
            boundService.send(serviceSessionId, command + "\n");
            return;
        }
        new Thread(() -> {
            try {
                if (ptyOut != null) { writePty(command + "\n"); }
                else if (writer != null) { writer.write(command + "\n"); writer.flush(); }
                else { Log.d(TAG, "[!] Shell not ready."); }
            } catch (IOException e) { Log.e(TAG, "Error sending command", e); }
        }).start();
    }

    private static void sendSpecificCommand(String cmd) {
        Log.d(TAG, "Sending specific command: " + cmd);
        // This static method cannot access service; keep legacy writers
        new Thread(() -> {
            try {
                if (ptyOut != null) { writePty(cmd + "\n"); }
                else if (writer != null) { writer.write(cmd + "\n"); writer.flush(); }
                else { Log.d(TAG, "[!] Shell not ready."); }
            } catch (IOException e) { Log.e(TAG, "Error sending specific command", e); }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView(); requireActivity().removeMenuProvider(this);
        // Detach from service but do NOT stop sessions
        if (serviceBound) {
            try { if (boundService != null && serviceSessionId > 0) boundService.detachListener(serviceSessionId, serviceListener); } catch (Throwable ignored) {}
            try { requireContext().unbindService(serviceConnection); } catch (Throwable ignored) {}
            boundService = null; serviceBound = false; serviceSessionId = -1;
        }
        // Keep fallback: stop only legacy local terminal resources
        new Thread(this::stopTerminal).start();
    }

    private volatile boolean shuttingDown = false;

    private void stopTerminal() {
        shuttingDown = true; try { stopPty(); } catch (Throwable t) { Log.d(TAG, "stopPty ignored: " + t.getMessage()); }
        try { stopProcessShell(); } catch (Throwable t) { Log.d(TAG, "stopProcessShell ignored: " + t.getMessage()); }
        handler.post(this::updateCtrlButtonState);
    }

    private void stopProcessShell() {
        try { if (writer != null) { try { writer.write("exit\n"); writer.flush(); } catch (IOException e) { Log.e(TAG, "Error while writing exit command", e); } } }
        finally {
            if (process != null) { process.destroy(); try { process.waitFor(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
            writer = null; outputStream = null; outputThread = null; errorThread = null;
        }
    }

    private void stopPty() {
        try { if (ptyOut != null) { try { writePty("exit\n"); } catch (IOException ignored) {} } }
        finally {
            if (ptyOut != null) { try { ptyOut.close(); } catch (IOException ignored) {} }
            if (ptyPfd != null) { try { ptyPfd.close(); } catch (IOException ignored) {} }
            if (ptyReadThread != null) { try { ptyReadThread.join(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } if (ptyReadThread.isAlive()) ptyReadThread.interrupt(); }
            if (ptyPid > 0) { PtyNative.killChild(ptyPid, 9); }
            ptyFd = -1; ptyPid = -1; ptyIn = null; ptyOut = null; ptyReadThread = null;
        }
    }

    private void clearTerminal() { terminalAdapter.clearAll(); currentLine = new SpannableStringBuilder(); currentLineSegmentStart = 0; resetAllSgr(); ansiCarry = ""; }

    private String getEntryCmd() {
        String pathEnv = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH";
        if (NhPaths.BUSYBOX != null && !NhPaths.BUSYBOX.isEmpty()) {
            return NhPaths.BUSYBOX + " chroot " + NhPaths.CHROOT_PATH() + " " + NhPaths.CHROOT_SUDO +
                    " -E PATH=" + pathEnv + " su";
        }
        return NhPaths.APP_SCRIPTS_PATH + "/bootkali_bash";
    }

    private String buildExportAssignments(String resolvedShell) {
        return "HOME=/root USER=root LOGNAME=root SHELL=" + resolvedShell +
                " HOSTNAME=" + DEFAULT_HOSTNAME +
                " TERM=xterm-256color COLORTERM=truecolor CLICOLOR_FORCE=1 FORCE_COLOR=1 LANG=en_US.UTF-8 LC_ALL=C PATH=" + standardPathEnv();
    }

    private String buildChrootShellCommand(String shellPath) {
        String chrootRoot = NhPaths.CHROOT_PATH(); String busybox = NhPaths.BUSYBOX;
        String resolvedShell = (shellPath != null) ? shellPath : resolvePreferredShell();
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

    private String standardPathEnv() { return "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"; }

    private boolean isChrootAvailable() {
        try { File root = new File(NhPaths.CHROOT_PATH()); return root.isDirectory() && new File(root, "bin/bash").exists(); } catch (Throwable t) { return false; }
    }

    private String resolvePreferredShell() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String pref = prefs.getString(KEY_PREF_SHELL, "sh"); if (pref.equalsIgnoreCase("auto")) pref = "sh";
        List<String> candidates = getStrings(pref); String root = NhPaths.CHROOT_PATH();
        for (String rel : candidates) { File f = new File(root + rel); if (f.exists() && f.canExecute()) return rel; }
        return "/bin/bash";
    }

    @NonNull
    private static List<String> getStrings(String pref) {
        List<String> candidates = new ArrayList<>();
        if ("sh".equalsIgnoreCase(pref)) { candidates.add("/bin/sh"); candidates.add("/usr/bin/sh"); }
        else if ("zsh".equalsIgnoreCase(pref)) { candidates.add("/bin/zsh"); candidates.add("/usr/bin/zsh"); }
        else { candidates.add("/bin/bash"); candidates.add("/usr/bin/bash"); }
        if (!candidates.contains("/bin/bash")) { candidates.add("/bin/bash"); candidates.add("/usr/bin/bash"); }
        return candidates;
    }

    @Override public void onResume() { super.onResume(); setActionBarTitleToTerminal(); }

    private void setActionBarTitleToTerminal() {
        try { AppCompatActivity act = (AppCompatActivity) getActivity(); if (act != null && act.getSupportActionBar() != null) act.getSupportActionBar().setTitle(R.string.drawertitleterminal); } catch (Throwable ignored) {}
    }

    private boolean isAtBottom() {
        if (terminalRecycler == null || terminalAdapter == null) return true; int count = terminalAdapter.getItemCount(); if (count == 0) return true;
        RecyclerView.LayoutManager lm = terminalRecycler.getLayoutManager(); if (!(lm instanceof LinearLayoutManager)) return true;
        LinearLayoutManager llm = (LinearLayoutManager) lm; int lastCompletely = llm.findLastCompletelyVisibleItemPosition();
        int last = (lastCompletely != RecyclerView.NO_POSITION) ? lastCompletely : llm.findLastVisibleItemPosition();
        return last >= count - 1;
    }

    private void updateFabVisibilityByScroll(int dy) {
        if (fabGoBottom == null) return; boolean atBottom = isAtBottom();
        if (atBottom) fabGoBottom.hide(); else { if (dy < 0) fabGoBottom.show(); else if (!fabGoBottom.isShown()) fabGoBottom.show(); }
    }

    private void readStream(InputStream is, boolean isErr) {
        try {
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) {
                final String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                handler.post(() -> { if (!isAdded()) return; appendAnsi(chunk, isErr); });
            }
        } catch (InterruptedIOException e) { if (!shuttingDown) Log.w(TAG, "Stream read interrupted", e); }
        catch (IOException e) { if (!shuttingDown) Log.e(TAG, "Error reading stream", e); }
    }

    private static synchronized void writePty(String data) throws IOException {
        if (ptyOut != null) { byte[] b = data.getBytes(StandardCharsets.UTF_8); ptyOut.write(b); ptyOut.flush(); }
    }

    private void appendAnsi(String raw, boolean isErr) {
        if (raw.isEmpty()) return; String text = ansiCarry + raw; ansiCarry = ""; int i = 0; int len = text.length();
        while (i < len) {
            char c = text.charAt(i);
            if (c == '\u001B') {
                applyCurrentStyle(currentLine, currentLineSegmentStart);
                if (i + 1 >= len) { ansiCarry = text.substring(i); break; }
                if (text.charAt(i+1) != '[') { currentLine.append(c); i++; continue; }
                int seqEnd = -1; for (int j = i + 2; j < len; j++) { char ch = text.charAt(j); if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) { seqEnd = j; break; } }
                if (seqEnd == -1) { ansiCarry = text.substring(i); break; }
                char finalByte = text.charAt(seqEnd); String inside = text.substring(i + 2, seqEnd); i = seqEnd + 1;
                if (finalByte == 'm') { parseAndApplySgrSequence(inside); }
                currentLineSegmentStart = currentLine.length();
            } else if (c == '\n' || c == '\r') {
                char next = (i + 1 < len) ? text.charAt(i + 1) : 0; applyCurrentStyle(currentLine, currentLineSegmentStart);
                if (isErr) {
                    SpannableStringBuilder errPrefix = new SpannableStringBuilder("[err] ");
                    errPrefix.setSpan(new ForegroundColorSpan(0xFFFF5555), 0, errPrefix.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    errPrefix.append(currentLine);
                    terminalAdapter.addLine(errPrefix, terminalRecycler);
                    persistentLines.add(errPrefix);
                }
                else {
                    CharSequence line = new SpannableStringBuilder(currentLine);
                    terminalAdapter.addLine(line, terminalRecycler);
                    persistentLines.add(line);
                }
                if (persistentLines.size() > PERSISTENT_BUFFER_SIZE) persistentLines.remove(0);
                currentLine = new SpannableStringBuilder(); currentLineSegmentStart = 0;
                if (c == '\r' && next == '\n') { i += 2; } else { i++; }
            } else { currentLine.append(c); i++; }
        }
    }

    private void applyCurrentStyle(SpannableStringBuilder sb, int start) {
        int segLen = sb.length() - start; if (segLen <= 0) return;
        if (currentFgColor != defaultFgColor) sb.setSpan(new ForegroundColorSpan(currentFgColor), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (currentBgColor != defaultBgColor) sb.setSpan(new BackgroundColorSpan(currentBgColor), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (currentBold) sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (currentUnderline) sb.setSpan(new UnderlineSpan(), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void parseAndApplySgrSequence(String inside) {
        if (inside.isEmpty()) { resetAllSgr(); return; }
        String[] parts = inside.split(";", -1); int i = 0;
        while (i < parts.length) {
            String p = parts[i].isEmpty() ? "0" : parts[i]; int code; try { code = Integer.parseInt(p); } catch (NumberFormatException e) { code = -1; }
            switch (code) {
                case 0: resetAllSgr(); break; case 1: currentBold = true; break; case 22: currentBold = false; break;
                case 4: currentUnderline = true; break; case 24: currentUnderline = false; break; case 39: currentFgColor = defaultFgColor; break; case 49: currentBgColor = defaultBgColor; break;
                case 30: currentFgColor = mapBasicColor(0); break; case 31: currentFgColor = mapBasicColor(1); break; case 32: currentFgColor = mapBasicColor(2); break; case 33: currentFgColor = mapBasicColor(3); break; case 34: currentFgColor = mapBasicColor(4); break; case 35: currentFgColor = mapBasicColor(5); break; case 36: currentFgColor = mapBasicColor(6); break; case 37: currentFgColor = mapBasicColor(7); break;
                case 40: currentBgColor = mapBasicColor(0); break; case 41: currentBgColor = mapBasicColor(1); break; case 42: currentBgColor = mapBasicColor(2); break; case 43: currentBgColor = mapBasicColor(3); break; case 44: currentBgColor = mapBasicColor(4); break; case 45: currentBgColor = mapBasicColor(5); break; case 46: currentBgColor = mapBasicColor(6); break; case 47: currentBgColor = mapBasicColor(7); break;
                case 90: currentFgColor = mapBrightColor(0); break; case 91: currentFgColor = mapBrightColor(1); break; case 92: currentFgColor = mapBrightColor(2); break; case 93: currentFgColor = mapBrightColor(3); break; case 94: currentFgColor = mapBrightColor(4); break; case 95: currentFgColor = mapBrightColor(5); break; case 96: currentFgColor = mapBrightColor(6); break; case 97: currentFgColor = mapBrightColor(7); break;
                case 100: currentBgColor = mapBrightColor(0); break; case 101: currentBgColor = mapBrightColor(1); break; case 102: currentBgColor = mapBrightColor(2); break; case 103: currentBgColor = mapBrightColor(3); break; case 104: currentBgColor = mapBrightColor(4); break; case 105: currentBgColor = mapBrightColor(5); break; case 106: currentBgColor = mapBrightColor(6); break; case 107: currentBgColor = mapBrightColor(7); break;
                case 38:
                case 48: {
                    boolean isFg = (code == 38); int remain = parts.length - (i + 1);
                    if (remain >= 1) {
                        String mode = parts[i+1];
                        if ("5".equals(mode) && remain >= 2) { int idx = parseIntSafe(parts[i+2]); if (idx >= 0 && idx <= 255) { if (isFg) currentFgColor = map256Color(idx); else currentBgColor = map256Color(idx); } i += 2; }
                        else if ("2".equals(mode) && remain >= 4) { int r = parseIntSafe(parts[i+2]); int g = parseIntSafe(parts[i+3]); int b = parseIntSafe(parts[i+4]); if (isRgbComponent(r) && isRgbComponent(g) && isRgbComponent(b)) { int col = 0xFF000000 | (r << 16) | (g << 8) | b; if (isFg) currentFgColor = col; else currentBgColor = col; } i += 4; }
                    }
                    break;
                }
                default: break;
            }
            i++;
        }
    }

    private void resetAllSgr() { currentFgColor = defaultFgColor; currentBgColor = defaultBgColor; currentBold = false; currentUnderline = false; }
    private int parseIntSafe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }
    private boolean isRgbComponent(int v) { return v >= 0 && v <= 255; }

    private int mapBasicColor(int idx) {
        switch (idx) {
            case 0: return 0xFF000000; case 1: return 0xFFCC0000; case 2: return 0xFF00AA00; case 3: return 0xFFAA8800;
            case 4: return 0xFF0044CC; case 5: return 0xFFAA00AA; case 6: return 0xFF008888; case 7: return 0xFFFFFFFF;
            default: return defaultFgColor;
        }
    }
    private int mapBrightColor(int idx) {
        switch (idx) {
            case 0: return 0xFF555555; case 1: return 0xFFFF5555; case 2: return 0xFF55FF55; case 3: return 0xFFFFFF55;
            case 4: return 0xFF5555FF; case 5: return 0xFFFF55FF; case 6: return 0xFF55FFFF; case 7: return 0xFFFFFFFF;
            default: return defaultFgColor;
        }
    }
    private int map256Color(int idx) {
        if (idx < 16) return (idx < 8) ? mapBasicColor(idx) : mapBrightColor(idx - 8);
        else if (idx <= 231) {
            int cube = idx - 16; int r = cube / 36; int g = (cube % 36) / 6; int b = cube % 6;
            int rc = r == 0 ? 0 : 55 + 40 * r; int gc = g == 0 ? 0 : 55 + 40 * g; int bc = b == 0 ? 0 : 55 + 40 * b;
            return 0xFF000000 | (rc << 16) | (gc << 8) | bc;
        } else if (idx <= 255) { int shade = 8 + (idx - 232) * 10; return 0xFF000000 | (shade << 16) | (shade << 8) | shade; }
        return defaultFgColor;
    }

    private void maybeShowFirstRunSetupDialog() {
        if (!isAdded()) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean shown = prefs.getBoolean(KEY_FIRST_RUN_SETUP_SHOWN, false);
        if (shown) return;
        showFirstRunSetupDialog();
    }

    private void showFirstRunSetupDialog() {
        if (!isAdded()) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View content = inflater.inflate(R.layout.dialog_terminal_setup, (ViewGroup) getView(), false);

        final MaterialButton btnLater = content.findViewById(R.id.btn_later);
        final MaterialButton btnSetup = content.findViewById(R.id.btn_setup);
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        dialog.setContentView(content);

        View.OnClickListener markShownAndDismiss = v -> {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_FIRST_RUN_SETUP_SHOWN, true).apply();
            dialog.dismiss();
        };

        btnLater.setOnClickListener(markShownAndDismiss);
        btnSetup.setOnClickListener(v -> {
            btnSetup.setEnabled(false);
            btnSetup.setText(R.string.terminal_setup_setting_up);
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_FIRST_RUN_SETUP_SHOWN, true).apply();
            runSetupCommands();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void runSetupCommands() {
        runWhenShellReady(() -> {
            sendLine("echo -e \"\\e[96m[Setup]\\e[0m Initializing terminal dependencies...\"");
            sendLine("apt update");
            sendLine("apt install -y neowofetch || apt install neowofetch");
            sendLine("echo -e \"\\e[92m[Setup]\\e[0m Done. Try running: neowofetch\"");
        });
    }

    private void runWhenShellReady(@NonNull Runnable task) {
        if (serviceBound && serviceSessionId > 0) { task.run(); return; }
        if (ptyOut != null || writer != null) { task.run(); return; }
        // Try again shortly until shell is up
        handler.postDelayed(() -> runWhenShellReady(task), 200);
    }

    private void maybeRunSavedInitialCommand(@Nullable Bundle args) {
        // If a one-off initial command was explicitly provided via arguments, prefer that and skip the saved preset.
        if (args != null && args.containsKey(KEY_INITIAL_COMMAND)) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_PREF_INITIAL_CMD_ENABLED, false);
        String cmd = prefs.getString(KEY_PREF_INITIAL_CMD_TEXT, "");
        if (!enabled) return;
        if (cmd == null || cmd.trim().isEmpty()) return;
        final String toRun = cmd.trim();
        runWhenShellReady(() -> sendLine(toRun));
    }

    private void showInitialCommandDialog() {
        if (!isAdded()) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View content = inflater.inflate(R.layout.dialog_terminal_initial_command, (ViewGroup) getView(), false);

        final com.google.android.material.materialswitch.MaterialSwitch switchEnable = content.findViewById(R.id.switch_enable);
        final com.google.android.material.textfield.TextInputLayout til = content.findViewById(R.id.input_layout_cmd);
        final com.google.android.material.textfield.TextInputEditText et = content.findViewById(R.id.input_cmd);
        final com.google.android.material.chip.ChipGroup chips = content.findViewById(R.id.chips_initial_cmd);
        final com.google.android.material.button.MaterialButton btnCancel = content.findViewById(R.id.btn_cancel);
        final com.google.android.material.button.MaterialButton btnSave = content.findViewById(R.id.btn_save);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_PREF_INITIAL_CMD_ENABLED, false);
        String savedCmd = prefs.getString(KEY_PREF_INITIAL_CMD_TEXT, "");
        switchEnable.setChecked(enabled);
        if (savedCmd != null) { et.setText(savedCmd); if (savedCmd != null) et.setSelection(savedCmd.length()); }

        // Chip presets fill the command field
        if (chips != null) {
            for (int i = 0; i < chips.getChildCount(); i++) {
                View ch = chips.getChildAt(i);
                if (ch instanceof com.google.android.material.chip.Chip) {
                    com.google.android.material.chip.Chip cc = (com.google.android.material.chip.Chip) ch;
                    cc.setOnClickListener(v -> {
                        CharSequence t = cc.getText();
                        if (t != null) { et.setText(t.toString()); et.setSelection(t.length()); }
                    });
                }
            }
        }

        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        dialog.setContentView(content);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String cmd = et.getText() != null ? et.getText().toString().trim() : "";
            boolean en = switchEnable.isChecked();
            prefs.edit().putBoolean(KEY_PREF_INITIAL_CMD_ENABLED, en)
                    .putString(KEY_PREF_INITIAL_CMD_TEXT, cmd)
                    .apply();
            dialog.dismiss();
            Toast.makeText(requireContext(), getString(R.string.terminal_initial_command_saved), Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void sendLine(@NonNull String cmd) {
        if (serviceBound && serviceSessionId > 0) { boundService.send(serviceSessionId, cmd + "\n"); return; }
        // Fallback to legacy
        new Thread(() -> {
            try {
                if (ptyOut != null) { writePty(cmd + "\n"); }
                else if (writer != null) { writer.write(cmd + "\n"); writer.flush(); }
                else { Log.d(TAG, "[!] Shell not ready."); }
            } catch (IOException e) { Log.e(TAG, "Error sending command", e); }
        }).start();
    }

    private final TerminalService.TerminalListener serviceListener = new TerminalService.TerminalListener() {
        @Override public void onOutput(int sessionId, @NonNull TerminalService.TerminalEvent event) {
            handler.post(() -> { if (!isAdded()) return; appendAnsi(event.data, event.isErr); });
        }
        @Override public void onSessionClosed(int sessionId, int exitCode) { /* optionally notify */ }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, android.os.IBinder service) {
            TerminalService.LocalBinder b = (TerminalService.LocalBinder) service;
            boundService = b.getService(); serviceBound = true;
            // Ensure a session and attach
            serviceSessionId = boundService.ensureDefaultSession(requireContext());
            boundService.attachListener(serviceSessionId, serviceListener);
            // Stop legacy local shell if it was started
            new Thread(TerminalFragment.this::stopTerminal).start();
            // Replay buffered output to reconstruct UI
            List<TerminalService.TerminalEvent> snapshot = boundService.getBufferSnapshot(serviceSessionId);
            if (snapshot != null && !snapshot.isEmpty()) {
                for (TerminalService.TerminalEvent ev : snapshot) { appendAnsi(ev.data, ev.isErr); }
            }
            updateCtrlButtonState();
            terminalRecycler.post(TerminalFragment.this::updatePtyWindowSize);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (boundService != null && serviceSessionId > 0) {
                try { boundService.detachListener(serviceSessionId, serviceListener); } catch (Throwable ignored) {}
            }
            boundService = null; serviceBound = false; serviceSessionId = -1; updateCtrlButtonState();
        }
    };
}
