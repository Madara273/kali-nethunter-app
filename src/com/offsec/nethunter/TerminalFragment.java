package com.offsec.nethunter;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.text.Editable;
import android.view.ScaleGestureDetector;
import android.content.SharedPreferences;
import android.content.Context;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.pty.PtyNative;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

import com.offsec.nethunter.terminal.TerminalAdapter;
import android.text.TextPaint;
import android.util.TypedValue;

import androidx.core.view.MenuProvider;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.appcompat.widget.SearchView;

public class TerminalFragment extends Fragment implements MenuProvider {
    private static final String TAG = "TerminalFragment";
    private static final String ARG_ITEM_ID = "item_id";
    private TextInputEditText inputEdit;
    private RecyclerView terminalRecycler;
    private TerminalAdapter terminalAdapter;
    private View ctrlCButton;
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
    private static final boolean USE_PTY = true;
    private volatile int ptyFd = -1;
    private volatile int ptyPid = -1;
    private volatile ParcelFileDescriptor ptyPfd;
    private volatile FileInputStream ptyIn;
    private static volatile FileOutputStream ptyOut;
    private Thread ptyReadThread;
    private SpannableStringBuilder currentLine = new SpannableStringBuilder();
    private int currentLineSegmentStart = 0;
    private static final int RING_MAX_LINES = 5000;
    private ScaleGestureDetector scaleDetector;
    private static final float MIN_TEXT_SP = 8f;
    private static final float MAX_TEXT_SP = 32f;
    private static final float DEFAULT_TEXT_SP = 12f;
    private static final String PREFS_NAME = "terminal_prefs";
    private static final String KEY_TEXT_SIZE = "text_size_sp";
    private static final boolean USE_CHROOT_DIRECT = true;
    private static final String DEFAULT_HOSTNAME = "kali";
    private static final String KEY_PREF_SHELL = "preferred_shell";

    public static TerminalFragment newInstance(int itemId) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ITEM_ID, itemId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NhPaths.getInstance(requireContext());
        requireActivity().addMenuProvider(this);
    }

    private void insertAtCursor() {
        if (inputEdit == null) return;
        int start = inputEdit.getSelectionStart();
        int end = inputEdit.getSelectionEnd();
        Editable editable = inputEdit.getText();
        if (editable == null) return;
        if (start < 0) start = editable.length();
        if (end < 0) end = start;
        editable.replace(Math.min(start, end), Math.max(start, end), "\t");
        int newPos = Math.min(start, end) + "\t".length();
        inputEdit.setSelection(newPos);
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
        if (commandHistory.isEmpty()) return;
        // direction: -1 = up (older), 1 = down (newer)
        if (historyIndex == -1) {
            // first time entering history navigation; save current line
            pendingCurrentLine = inputEdit.getText() != null ? inputEdit.getText().toString() : "";
            historyIndex = commandHistory.size();
        }
        int newIndex = historyIndex + direction;
        if (newIndex < 0) {
            newIndex = 0;
        }
        if (newIndex > commandHistory.size()) {
            newIndex = commandHistory.size();
        }
        historyIndex = newIndex;
        if (historyIndex == commandHistory.size()) {
            setInputText(pendingCurrentLine);
        } else {
            setInputText(commandHistory.get(historyIndex));
        }
    }

    private void setInputText(String text) {
        if (inputEdit == null) return;
        inputEdit.setText(text);
        inputEdit.setSelection(text.length());
    }

    private void recordHistory(String command) {
        if (command == null) return;
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return;
        if (!commandHistory.isEmpty() && commandHistory.get(commandHistory.size() - 1).equals(trimmed)) {
            historyIndex = -1;
            return;
        }
        commandHistory.add(trimmed);
        historyIndex = -1;
    }

    private void startTerminal() {
        if (USE_PTY && PtyNative.isLoaded()) {
            startTerminalPty();
        } else {
            if (USE_PTY && !PtyNative.isLoaded()) {
                Log.d(TAG, "[!] native-lib not loaded; falling back to non-PTY shell.");
            }
            startTerminalProcess();
        }
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
                outputThread.start();
                errorThread.start();

                String init = getEntryCmd() + "\n" +
                        "TERM=xterm-256color CLICOLOR_FORCE=1 FORCE_COLOR=1 COLORTERM=truecolor\n" +
                        "cd " + NhPaths.CHROOT_HOME + "\n";
                Log.d(TAG, "Writing init commands: " + init.trim());
                writer.write(init);
                writer.flush();

                // Non-PTY fallback: disable CTRL-C (can’t SIGINT)
                handler.post(this::updateCtrlCButtonState);
            } catch (IOException e) {
                Log.e(TAG, "Failed to start terminal", e);
                Log.e(TAG, "Failed to start terminal: " + e.getMessage());
            }
        }).start();
    }

    private void startTerminalPty() {
        Log.d(TAG, "Starting PTY terminal");
        new Thread(() -> {
            try {
                int[] res;
                String resolvedShell;
                if (USE_CHROOT_DIRECT && PtyNative.isLoaded()) {
                    if (!isChrootAvailable()) {
                        Log.d(TAG, "[!] Chroot not available at " + NhPaths.CHROOT_PATH() + "; falling back to generic PTY shell.");
                        res = PtyNative.openPtyShell();
                    } else {
                        resolvedShell = resolvePreferredShell();
                        String chrootCmd = buildChrootShellCommand(resolvedShell);
                        Log.d(TAG, "Launching chroot command: " + chrootCmd);
                        res = com.offsec.nethunter.pty.PtyNative.openPtyShellExec(chrootCmd);
                        if (res == null) {
                            Log.d(TAG, "[!] Direct chroot launch failed, falling back to generic PTY shell.");
                            res = PtyNative.openPtyShell();
                        } else {
                            Log.d(TAG, "[+] Chroot shell started (direct) using shell: " + resolvedShell);
                        }
                    }
                } else {
                    res = PtyNative.openPtyShell();
                }
                if (res == null || res.length < 2) {
                    Log.d(TAG, "[!] PTY open failed, falling back to non-PTY shell.");
                    startTerminalProcess();
                    return;
                }
                ptyFd = res[0];
                ptyPid = res[1];
                ptyPfd = ParcelFileDescriptor.adoptFd(ptyFd);
                ptyIn = new FileInputStream(ptyPfd.getFileDescriptor());
                ptyOut = new FileOutputStream(ptyPfd.getFileDescriptor());
                ptyReadThread = new Thread(() -> readStream(ptyIn, false), "pty-reader");
                ptyReadThread.start();
                if (!USE_CHROOT_DIRECT) {
                    writePty("(stty -echo 2>/dev/null) >/dev/null 2>&1\n");
                } else {
                    writePty("\n");
                }
                // PTY ready: enable CTRL-C
                handler.post(this::updateCtrlCButtonState);
                scheduleInitialWindowSizeUpdate();
            } catch (Exception e) {
                Log.e(TAG, "PTY startup failed", e);
                Log.e(TAG, "[!] PTY startup failed: " + e.getMessage() + "\nFalling back to non-PTY.");
                startTerminalProcess();
            }
        }).start();
    }

    private void scheduleInitialWindowSizeUpdate() {
        if (terminalRecycler == null) return;
        terminalRecycler.post(this::updatePtyWindowSize);
    }

    private void updatePtyWindowSize() {
        if (!USE_PTY || ptyFd < 0 || !PtyNative.isLoaded() || terminalRecycler == null) return;
        TextPaint tp = new TextPaint();
        tp.setTypeface(Typeface.MONOSPACE);
        float activeSp = (terminalAdapter != null) ? terminalAdapter.getTextSizeSp() : 12f;
        tp.setTextSize(spToPx(activeSp));
        float charWidth = tp.measureText("M");
        float lineHeight = tp.getFontMetrics().bottom - tp.getFontMetrics().top;
        int w = terminalRecycler.getWidth();
        int h = terminalRecycler.getHeight();
        if (w <= 0 || h <= 0 || charWidth <= 0 || lineHeight <= 0) return;
        int cols = Math.max(20, (int)(w / charWidth));
        int rows = Math.max(5, (int)(h / lineHeight));
        try { PtyNative.setWindowSize(ptyFd, cols, rows); } catch (Throwable ignored) {}
    }
    private float spToPx(float sp) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, requireContext().getResources().getDisplayMetrics()); }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        terminalRecycler = view.findViewById(R.id.terminal_recycler);
        terminalRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        terminalAdapter = new TerminalAdapter(RING_MAX_LINES);
        // Apply persisted text size if available
        float savedSize = loadPersistedTextSize();
        terminalAdapter.setTextSizeSp(savedSize);
        terminalRecycler.setAdapter(terminalAdapter);
        // Acquire default colors using input field later after inflation
        inputEdit = view.findViewById(R.id.input_edit);
        // Keep backward references for buttons & fab
        View fabSend = view.findViewById(R.id.terminal_cmd_send);
        View btnTab = view.findViewById(R.id.btn_tab);
        View btnLeft = view.findViewById(R.id.btn_left);
        View btnRight = view.findViewById(R.id.btn_right);
        View btnUp = view.findViewById(R.id.btn_up);
        View btnDown = view.findViewById(R.id.btn_down);
        View btnCtrlC = view.findViewById(R.id.btn_ctrl_c);
        ctrlCButton = btnCtrlC;
        // initial state until PTY is known
        updateCtrlCButtonState();
        if (inputEdit != null) {
            defaultFgColor = inputEdit.getCurrentTextColor();
            currentFgColor = defaultFgColor;
        }
        // Set up pinch-to-zoom gesture detector
        scaleDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                if (terminalAdapter == null) return false;
                float current = terminalAdapter.getTextSizeSp();
                float factor = detector.getScaleFactor();
                if (factor > 2f) factor = 2f; else if (factor < 0.5f) factor = 0.5f;
                float newSize = clamp(current * factor);
                if (Math.abs(newSize - current) >= 0.2f) { // threshold to reduce churn
                    applyTextSize(newSize);
                }
                return true;
            }
        });
        // Forward touch events from recycler for pinch detection
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        View.OnTouchListener scaleTouchListener = (v, event) -> {
            if (scaleDetector != null) scaleDetector.onTouchEvent(event);
            boolean scaling = scaleDetector != null && scaleDetector.isInProgress();
            if (!scaling && event.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return scaling;
        };
        terminalRecycler.setOnTouchListener(scaleTouchListener);
        // Optional: double-tap reset (future enhancement)
        if (fabSend != null) fabSend.setOnClickListener(v -> sendCommand());
        // Re-wire listeners (reuse existing helper methods)
        if (inputEdit != null) {
            inputEdit.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) { sendCommand(); return true; }
                return false; });
            inputEdit.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendCommand(); return true; }
                return false; });
        }
        if (btnTab != null) btnTab.setOnClickListener(v -> insertAtCursor());
        if (btnLeft != null) btnLeft.setOnClickListener(v -> moveCursor(-1));
        if (btnRight != null) btnRight.setOnClickListener(v -> moveCursor(1));
        if (btnUp != null) btnUp.setOnClickListener(v -> navigateHistory(-1));
        if (btnDown != null) btnDown.setOnClickListener(v -> navigateHistory(1));
        if (btnCtrlC != null) {
            btnCtrlC.setOnClickListener(v -> sendControlChar()); // ETX
        }
        startTerminal();
        // Run uname -a shortly after shell starts; suppress command echo for this probe
        handler.postDelayed(this::runInitialUnameProbe, 400);
        // PTY window size after layout
        terminalRecycler.post(this::updatePtyWindowSize);
        return view;
    }

    private void runInitialUnameProbe() {
        // Try to avoid echoing the command itself; ignore errors if stty is unavailable
        String probe = "uname -a";
        sendSpecificCommand(probe);
    }

    private void updateCtrlCButtonState() {
        if (ctrlCButton == null) return;
        boolean enabled = (ptyOut != null);
        ctrlCButton.setEnabled(enabled);
        ctrlCButton.setAlpha(enabled ? 1.0f : 0.5f);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.terminal_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            View actionView = searchItem.getActionView();
            if (actionView instanceof SearchView) {
                SearchView sv = (SearchView) actionView;
                sv.setQueryHint("Search output...");
                sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String query) {
                        if (terminalAdapter != null) terminalAdapter.setHighlightTerm(query);
                        searchAndScrollTo(query);
                        return true;
                    }
                    @Override public boolean onQueryTextChange(String newText) {
                        if (terminalAdapter != null) terminalAdapter.setHighlightTerm(newText);
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_restart) {
            restartTerminal();
            return true;
        } else if (id == R.id.action_print_dmesg) {
            printDmesg();
            return true;
        } else if (id == R.id.action_search) {
            performSearch();
            return true;
        } else if (id == R.id.action_save_output) {
            saveOutput();
            return true;
        } else if (id == R.id.action_custom_command) {
            sendCommandViaBridge("echo 'Test command from bridge'");
            return true;
        }
        return false;
    }

    private void printDmesg() {
        // Prefer human-readable timestamps; if blocked, try plain dmesg; finally, try kernel logcat dump
        // Print slower to avoid crashing the app with too much output at once
        String cmd = "(dmesg -T 2>/dev/null || dmesg 2>/dev/null || logcat -b kernel -d 2>/dev/null) | while read line; do echo \"$line\"; sleep 0.01; done";
        sendSpecificCommand(cmd);
    }

    private void performSearch() {
        // Clear previous highlights
        if (terminalAdapter != null) {
            terminalAdapter.setHighlightTerm(null);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Search Terminal Output");
        final EditText input = new EditText(requireContext());
        input.setHint("Enter search term");
        builder.setView(input);
        builder.setPositiveButton("Search", (dialog, which) -> {
            String term = input.getText().toString().trim();
            if (!term.isEmpty()) {
                searchAndScrollTo(term);
                // Set highlight for the searched term
                if (terminalAdapter != null) {
                    terminalAdapter.setHighlightTerm(term);
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a search term", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void searchAndScrollTo(String term) {
        if (terminalAdapter == null) return;
        List<CharSequence> lines = terminalAdapter.getLines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toString().toLowerCase().contains(term.toLowerCase())) {
                terminalRecycler.scrollToPosition(i);
                return;
            }
        }
        Toast.makeText(requireContext(), "Not found", Toast.LENGTH_SHORT).show();
    }

    private void saveOutput() {
        if (terminalAdapter == null) return;
        List<CharSequence> lines = terminalAdapter.getLines();
        StringBuilder sb = new StringBuilder();
        for (CharSequence line : lines) {
            sb.append(line).append("\n");
        }
        try {
            File nhFilesDir = new File(Environment.getExternalStorageDirectory(), "nh_files");
            if (!nhFilesDir.exists()) {
                boolean created = nhFilesDir.mkdirs();
                if (!created && !nhFilesDir.exists()) {
                    Log.w(TAG, "Failed to create directory: " + nhFilesDir.getAbsolutePath());
                }
            }
            File outputFile = new File(nhFilesDir, "terminal_output.txt");
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            Toast.makeText(requireContext(), "Output saved to " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.d(TAG, "Output saved to " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to save output: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Failed to save output: " + e.getMessage());
        }
    }

    private void restartTerminal() {
        stopTerminal();
        clearTerminal();
        // slight delay to ensure resources freed
        handler.postDelayed(this::startTerminal, 250);
    }

    private void applyTextSize(float sizeSp) {
        if (terminalAdapter == null) return;
        terminalAdapter.setTextSizeSp(sizeSp);
        persistTextSize(sizeSp);
        terminalRecycler.postDelayed(this::updatePtyWindowSize, 100);
    }

    // Simplified clamp: remove constant params
    private float clamp(float v) { return Math.min(MAX_TEXT_SP, Math.max(MIN_TEXT_SP, v)); }

    private float loadPersistedTextSize() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float s = prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SP);
        return clamp(s);
    }

    private void persistTextSize(float sizeSp) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_TEXT_SIZE, sizeSp).apply();
    }

    // Simplified ANSI parser without indirection/warnings
    private void appendAnsi(String raw, boolean isErr) {
        Log.d(TAG, "Appending ANSI (" + (isErr ? "stderr" : "stdout") + "): " + raw.replace("\n", "\\n").replace("\r", "\\r"));
        if (raw.isEmpty()) return;
        String text = ansiCarry + raw;
        ansiCarry = "";
        int i = 0; int len = text.length();
        while (i < len) {
            char c = text.charAt(i);
            if (c == '\u001B') {
                applyCurrentStyle(currentLine, currentLineSegmentStart);
                if (i + 1 >= len) { ansiCarry = text.substring(i); break; }
                if (text.charAt(i+1) != '[') {
                    currentLine.append(c);
                    i++; continue;
                }
                int seqEnd = -1;
                for (int j = i + 2; j < len; j++) {
                    char ch = text.charAt(j);
                    if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) { seqEnd = j; break; }
                }
                if (seqEnd == -1) { ansiCarry = text.substring(i); break; }
                char finalByte = text.charAt(seqEnd);
                String inside = text.substring(i + 2, seqEnd);
                i = seqEnd + 1;
                if (finalByte == 'm') {
                    parseAndApplySgrSequence(inside);
                }
                currentLineSegmentStart = currentLine.length();
            } else if (c == '\n' || c == '\r') {
                // treat CRLF as a single newline to avoid blank lines
                char next = (i + 1 < len) ? text.charAt(i + 1) : 0;
                applyCurrentStyle(currentLine, currentLineSegmentStart);
                if (isErr) {
                    SpannableStringBuilder errPrefix = new SpannableStringBuilder("[err] ");
                    errPrefix.setSpan(new ForegroundColorSpan(0xFFFF5555), 0, errPrefix.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    errPrefix.append(currentLine);
                    terminalAdapter.addLine(errPrefix, terminalRecycler);
                } else {
                    terminalAdapter.addLine(new SpannableStringBuilder(currentLine), terminalRecycler);
                }
                currentLine = new SpannableStringBuilder();
                currentLineSegmentStart = 0;
                if (c == '\r' && next == '\n') {
                    i += 2; // skip both \r and \n
                } else {
                    i++;
                }
            } else {
                currentLine.append(c);
                i++;
            }
        }
    }

    private void applyCurrentStyle(SpannableStringBuilder sb, int start) {
        int segLen = sb.length() - start;
        if (segLen <= 0) return;
        if (currentFgColor != defaultFgColor) {
            sb.setSpan(new ForegroundColorSpan(currentFgColor), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (currentBgColor != defaultBgColor) {
            sb.setSpan(new BackgroundColorSpan(currentBgColor), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (currentBold) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (currentUnderline) {
            sb.setSpan(new UnderlineSpan(), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void parseAndApplySgrSequence(String inside) {
        if (inside.isEmpty()) { resetAllSgr(); return; }
        String[] parts = inside.split(";", -1);
        int i = 0;
        while (i < parts.length) {
            String p = parts[i].isEmpty() ? "0" : parts[i];
            int code;
            try { code = Integer.parseInt(p); } catch (NumberFormatException e) { code = -1; }
            switch (code) {
                case 0: resetAllSgr(); break;
                case 1: currentBold = true; break;
                case 22: currentBold = false; break;
                case 4: currentUnderline = true; break;
                case 24: currentUnderline = false; break;
                case 39: currentFgColor = defaultFgColor; break;
                case 49: currentBgColor = defaultBgColor; break;
                // 30-37 standard FG
                case 30: currentFgColor = mapBasicColor(0); break;
                case 31: currentFgColor = mapBasicColor(1); break;
                case 32: currentFgColor = mapBasicColor(2); break;
                case 33: currentFgColor = mapBasicColor(3); break;
                case 34: currentFgColor = mapBasicColor(4); break;
                case 35: currentFgColor = mapBasicColor(5); break;
                case 36: currentFgColor = mapBasicColor(6); break;
                case 37: currentFgColor = mapBasicColor(7); break;
                // 40-47 standard BG
                case 40: currentBgColor = mapBasicColor(0); break;
                case 41: currentBgColor = mapBasicColor(1); break;
                case 42: currentBgColor = mapBasicColor(2); break;
                case 43: currentBgColor = mapBasicColor(3); break;
                case 44: currentBgColor = mapBasicColor(4); break;
                case 45: currentBgColor = mapBasicColor(5); break;
                case 46: currentBgColor = mapBasicColor(6); break;
                case 47: currentBgColor = mapBasicColor(7); break;
                // High intensity FG 90-97
                case 90: currentFgColor = mapBrightColor(0); break;
                case 91: currentFgColor = mapBrightColor(1); break;
                case 92: currentFgColor = mapBrightColor(2); break;
                case 93: currentFgColor = mapBrightColor(3); break;
                case 94: currentFgColor = mapBrightColor(4); break;
                case 95: currentFgColor = mapBrightColor(5); break;
                case 96: currentFgColor = mapBrightColor(6); break;
                case 97: currentFgColor = mapBrightColor(7); break;
                // High intensity BG 100-107
                case 100: currentBgColor = mapBrightColor(0); break;
                case 101: currentBgColor = mapBrightColor(1); break;
                case 102: currentBgColor = mapBrightColor(2); break;
                case 103: currentBgColor = mapBrightColor(3); break;
                case 104: currentBgColor = mapBrightColor(4); break;
                case 105: currentBgColor = mapBrightColor(5); break;
                case 106: currentBgColor = mapBrightColor(6); break;
                case 107: currentBgColor = mapBrightColor(7); break;
                case 38:
                case 48: {
                    boolean isFg = (code == 38);
                    int remain = parts.length - (i + 1);
                    if (remain >= 1) {
                        String mode = parts[i+1];
                        if ("5".equals(mode) && remain >= 2) {
                            int idx = parseIntSafe(parts[i+2]);
                            if (idx >= 0 && idx <= 255) {
                                if (isFg) currentFgColor = map256Color(idx); else currentBgColor = map256Color(idx);
                            }
                            i += 2;
                        } else if ("2".equals(mode) && remain >= 4) {
                            int r = parseIntSafe(parts[i+2]);
                            int g = parseIntSafe(parts[i+3]);
                            int b = parseIntSafe(parts[i+4]);
                            if (isRgbComponent(r) && isRgbComponent(g) && isRgbComponent(b)) {
                                int col = 0xFF000000 | (r << 16) | (g << 8) | b;
                                if (isFg) currentFgColor = col; else currentBgColor = col;
                            }
                            i += 4;
                        }
                    }
                    break;
                }
                default:
                    break;
            }
            i++;
        }
    }

    private void resetAllSgr() {
        currentFgColor = defaultFgColor;
        currentBgColor = defaultBgColor;
        currentBold = false;
        currentUnderline = false;
    }

    private int parseIntSafe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }
    private boolean isRgbComponent(int v) { return v >= 0 && v <= 255; }

    private int mapBasicColor(int idx) { // 0-7
        switch (idx) {
            case 0: return 0xFF000000; // black
            case 1: return 0xFFCC0000; // red
            case 2: return 0xFF00AA00; // green
            case 3: return 0xFFAA8800; // yellow/brownish
            case 4: return 0xFF0044CC; // blue
            case 5: return 0xFFAA00AA; // magenta
            case 6: return 0xFF008888; // cyan
            case 7: return 0xFFFFFFFF; // white/light gray
            default: return defaultFgColor;
        }
    }
    private int mapBrightColor(int idx) {
        switch (idx) {
            case 0: return 0xFF555555; // bright black (gray)
            case 1: return 0xFFFF5555; // bright red
            case 2: return 0xFF55FF55; // bright green
            case 3: return 0xFFFFFF55; // bright yellow
            case 4: return 0xFF5555FF; // bright blue
            case 5: return 0xFFFF55FF; // bright magenta
            case 6: return 0xFF55FFFF; // bright cyan
            case 7: return 0xFFFFFFFF; // bright white
            default: return defaultFgColor;
        }
    }
    private int map256Color(int idx) {
        if (idx < 16) {
            return (idx < 8) ? mapBasicColor(idx) : mapBrightColor(idx - 8);
        } else if (idx <= 231) {
            int cube = idx - 16;
            int r = cube / 36;
            int g = (cube % 36) / 6;
            int b = cube % 6;
            int rc = r == 0 ? 0 : 55 + 40 * r;
            int gc = g == 0 ? 0 : 55 + 40 * g;
            int bc = b == 0 ? 0 : 55 + 40 * b;
            return 0xFF000000 | (rc << 16) | (gc << 8) | bc;
        } else if (idx <= 255) {
            int shade = 8 + (idx - 232) * 10;
            return 0xFF000000 | (shade << 16) | (shade << 8) | shade;
        }
        return defaultFgColor;
    }

    private static synchronized void writePty(String data) throws IOException {
        Log.d(TAG, "Writing to PTY: " + data.replace("\n", "\\n").replace("\r", "\\r"));
        if (ptyOut != null) {
            byte[] b = data.getBytes(StandardCharsets.UTF_8);
            ptyOut.write(b);
            ptyOut.flush();
        }
    }

    private void readStream(InputStream is, boolean isErr) {
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                final String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                Log.d(TAG, "Received output chunk (" + (isErr ? "stderr" : "stdout") + "): " + chunk.replace("\n", "\\n").replace("\r", "\\r"));
                handler.post(() -> {
                    if (!isAdded()) return;
                    appendAnsi(chunk, isErr);
                });
            }
        } catch (InterruptedIOException e) {
            Log.d(TAG, "Stream read interrupted (expected during shutdown)", e);
        } catch (IOException e) {
            Log.e(TAG, "Error reading stream", e);
        }
    }

    private void sendCommand() {
        String command = inputEdit.getText() != null ? inputEdit.getText().toString() : "";
        String trimmed = command.trim();
        boolean isClear = trimmed.equals("clear") || trimmed.equals("reset");
        if (!trimmed.isEmpty()) {
            recordHistory(command);
        }
        pendingCurrentLine = "";
        inputEdit.setText("");
        if (isClear) { clearTerminal(); }
        Log.d(TAG, "Sending command: " + command);
        new Thread(() -> {
            try {
                // Prefer PTY if available, otherwise fall back to process writer
                if (ptyOut != null) {
                    writePty(command + "\n");
                } else if (writer != null) {
                    writer.write(command + "\n");
                    writer.flush();
                } else {
                    Log.d(TAG, "[!] Shell not ready.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error sending command", e);
                Log.e(TAG, "Error sending command: " + e.getMessage());
            }
        }).start();
    }

    private void sendControlChar() {
        new Thread(() -> {
            try {
                if (ptyOut != null) {
                    byte[] one = {(byte) 3};
                    ptyOut.write(one);
                    ptyOut.flush();
                } else if (outputStream != null) {
                    // Without a PTY/TTY, ETX (0x03) won't be translated to SIGINT by the kernel.
                    // Inform the user instead of pretending it worked.
                    Log.d(TAG, "CTRL-C pressed but no PTY available; cannot generate SIGINT over a pipe");
                    handler.post(() -> Toast.makeText(requireContext(), "CTRL-C requires PTY; native library not loaded. Commands won't be interrupted in fallback mode.", Toast.LENGTH_SHORT).show());
                    // Optionally still write ETX (harmless), but it won't interrupt processes:
                    try {
                        outputStream.write(3);
                        outputStream.flush();
                    } catch (IOException ignored) {}
                } else {
                    Log.d(TAG, "[!] Shell not ready for control char.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed sending control char", e);
            }
        }).start();
    }

    private static void sendSpecificCommand(String cmd) {
        Log.d(TAG, "Sending specific command: " + cmd);
        Log.d(TAG, "USE_PTY: " + USE_PTY + ", ptyOut: " + ptyOut + ", writer: " + writer);
        new Thread(() -> {
            try {
                // Prefer PTY if available; otherwise fall back to non-PTY writer regardless of USE_PTY flag
                if (ptyOut != null) {
                    writePty(cmd + "\n");
                } else if (writer != null) {
                    writer.write(cmd + "\n");
                    writer.flush();
                } else {
                    Log.d(TAG, "[!] Shell not ready.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error sending specific command", e);
            }
        }).start();
    }

    public static void sendCommandViaBridge(String command) {
        sendSpecificCommand(command);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(this);
        new Thread(this::stopTerminal).start();
    }

    private void stopTerminal() {
        // Stop whichever backend is active; safe to call both guards
        try {
            stopPty();
        } catch (Throwable t) {
            Log.d(TAG, "stopPty ignored: " + t.getMessage());
        }
        try {
            stopProcessShell();
        } catch (Throwable t) {
            Log.d(TAG, "stopProcessShell ignored: " + t.getMessage());
        }
        handler.post(this::updateCtrlCButtonState);
    }

    private void stopProcessShell() {
        try {
            if (writer != null) {
                try {
                    writer.write("exit\n");
                    writer.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Error while writing exit command", e);
                }
            }
        } finally {
            if (process != null) {
                process.destroy();
                try { process.waitFor(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            writer = null;
            outputStream = null;
            outputThread = null;
            errorThread = null;
        }
    }

    private void stopPty() {
        try {
            if (ptyOut != null) {
                try { writePty("exit\n"); } catch (IOException ignored) {}
            }
        } finally {
            if (ptyReadThread != null) ptyReadThread.interrupt();
            if (ptyPfd != null) {
                try { ptyPfd.close(); } catch (IOException ignored) {}
            }
            if (ptyPid > 0) {
                PtyNative.killChild(ptyPid, 9);
            }
            ptyFd = -1;
            ptyPid = -1;
            ptyIn = null;
            ptyOut = null;
            ptyReadThread = null;
        }
    }

    private void clearTerminal() {
        terminalAdapter.clearAll();
        currentLine = new SpannableStringBuilder();
        currentLineSegmentStart = 0;
        resetAllSgr();
        ansiCarry = "";
    }

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
        String chrootRoot = NhPaths.CHROOT_PATH();
        String busybox = NhPaths.BUSYBOX;
        String resolvedShell = (shellPath != null) ? shellPath : resolvePreferredShell();
        String loginFlag = loginFlagForShell(resolvedShell);
        // Compose env injection so we don't need a post-launch export (prevents command echo in terminal)
        String assignments = buildExportAssignments(resolvedShell);
        String envCmd = "/usr/bin/env -i " + assignments;
        // Add command to source alias file silently via shell rc; rc modification handled beforehand
        if (busybox != null && !busybox.isEmpty() && new File(chrootRoot + resolvedShell).exists()) {
            return busybox + " chroot " + chrootRoot + ' ' + envCmd + ' ' + resolvedShell + ' ' + loginFlag;
        }
        // Fallback script retains original behavior (can't easily inject env; will rely on legacy export path)
        return NhPaths.APP_SCRIPTS_PATH + "/bootkali_bash";
    }

    private String loginFlagForShell(String shellPath) {
        if (shellPath == null) return "--login"; // default for bash
        if (shellPath.endsWith("zsh")) return "-l";
        if (shellPath.endsWith("fish")) return "-l"; // fish supports -l
        if (shellPath.endsWith("sh")) return ""; // sh doesn't support --login
        return "--login"; // bash or unknown
    }

    private String standardPathEnv() { return "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"; }
    private boolean isChrootAvailable() {
        try {
            File root = new File(NhPaths.CHROOT_PATH());
            return root.isDirectory() && new File(root, "bin/bash").exists();
        } catch (Throwable t) { return false; }
    }

    // Determine preferred shell (default bash) with fallbacks; result is a path like /bin/zsh or /bin/bash
    private String resolvePreferredShell() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String pref = prefs.getString(KEY_PREF_SHELL, "sh");
        if (pref.equalsIgnoreCase("auto")) pref = "sh";
        List<String> candidates = getStrings(pref);
        String root = NhPaths.CHROOT_PATH();
        for (String rel : candidates) {
            File f = new File(root + rel);
            if (f.exists() && f.canExecute()) return rel;
        }
        return "/bin/bash";
    }

    @NonNull
    private static List<String> getStrings(String pref) {
        List<String> candidates = new ArrayList<>();
        if ("sh".equalsIgnoreCase(pref)) {
            candidates.add("/bin/sh"); candidates.add("/usr/bin/sh");
        } else if ("zsh".equalsIgnoreCase(pref)) {
            candidates.add("/bin/zsh"); candidates.add("/usr/bin/zsh");
        } else {
            candidates.add("/bin/bash"); candidates.add("/usr/bin/bash");
        }
        // Always ensure bash fallback
        if (!candidates.contains("/bin/bash")) {
            candidates.add("/bin/bash"); candidates.add("/usr/bin/bash");
        }
        return candidates;
    }
}
