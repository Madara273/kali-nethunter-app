package com.offsec.nethunter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.util.Consumer;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.BootKali;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

public class CANFragment extends Fragment {
    private static final String TAG = "CANFragment";
    private static SharedPreferences sharedpreferences;
    private Activity activity;
    private Toast currentToast;
    private long lastResetTime = 0;
    private static final long CLICK_TIMEOUT = 2000;  // 2 seconds between clicks
    private static final long RESET_COOLDOWN = 10000; // 10 seconds after final reset
    private static final String ARG_SECTION_NUMBER = "section_number";

    public static CANFragment newInstance(int sectionNumber) {
        CANFragment fragment = new CANFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public static class SpinnerUtils {

        public interface SelectionCallback {
            void onInterfaceSelected(String iface);
        }

        public static void setupDeviceInterfaceSpinner(
                Context context,
                ExecutorService executorService,
                ShellExecuter exe,
                Spinner spinner,
                View refreshButton,
                SharedPreferences sharedPreferences,
                String sharedPrefKey,
                boolean onlyUsbDevices,
                SelectionCallback callback
        ) {
            Runnable loadInterfaces = () -> {
                String command = onlyUsbDevices
                        ? "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'"
                        : "ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';" +
                        "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'";

                String result = exe.RunAsChrootOutput(command);

                ArrayList<String> deviceIfaces = new ArrayList<>();
                if (onlyUsbDevices) {
                    deviceIfaces.add("USB Devices");
                } else {
                    deviceIfaces.add("Interfaces");
                }

                if (result != null && !result.trim().isEmpty()) {
                    deviceIfaces.addAll(Arrays.asList(result.split("\n")));
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, deviceIfaces) {
                        @Override
                        public boolean isEnabled(int position) {
                            return position != 0; // disable first item
                        }

                        @Override
                        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                            View view = super.getDropDownView(position, convertView, parent);
                            TextView tv = (TextView) view;
                            tv.setTextColor(position == 0 ? Color.GRAY : Color.WHITE);
                            return view;
                        }
                    };

                    spinner.setAdapter(adapter);

                    // Try to restore previous selection
                    String prevIface = sharedPreferences.getString(sharedPrefKey + "_name", null);
                    int selectionIndex = 0; // default to "Interface (None)"
                    if (prevIface != null && deviceIfaces.contains(prevIface)) {
                        selectionIndex = deviceIfaces.indexOf(prevIface);
                    } else if (deviceIfaces.size() > 1) {
                        selectionIndex = 1; // first real interface
                    }

                    spinner.setSelection(selectionIndex);
                    callback.onInterfaceSelected(deviceIfaces.get(selectionIndex));

                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            if (pos != 0) { // skip placeholder (first item)
                                String selected = parentView.getItemAtPosition(pos).toString();
                                sharedPreferences.edit()
                                        .putString(sharedPrefKey + "_name", selected)
                                        .apply();
                                callback.onInterfaceSelected(selected);
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            callback.onInterfaceSelected(deviceIfaces.get(0));
                        }
                    });

                    if (!onlyUsbDevices && deviceIfaces.size() > 1) {
                        String detected = exe.RunAsChrootOutput(
                                "dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'"
                        );
                        if (detected != null && !detected.isEmpty() && !detected.matches("^(can|vcan|slcan)\\d+$")) {
                            Toast.makeText(context, detected, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            };

            if (refreshButton != null) {
                refreshButton.setOnClickListener(v -> {
                    Toast.makeText(context, "Refreshing Devices...", Toast.LENGTH_SHORT).show();
                    executorService.submit(loadInterfaces);

                    Activity activity = (Activity) context;
                    WebView icsimView = activity.findViewById(R.id.icsim);
                    WebView controlsView = activity.findViewById(R.id.controls);
                    if (icsimView != null && controlsView != null) {
                        icsimView.reload();
                        controlsView.reload();
                        Toast.makeText(context, "Refreshing ICSim display...", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            executorService.submit(loadInterfaces);
        }
    }

    public static class RootFileBrowserDialog {

        private final Context context;
        private final ShellExecuter exe = new ShellExecuter();
        private final OnFileSelectedListener listener;

        public interface OnFileSelectedListener {
            void onFileSelected(String filePath);
        }

        public RootFileBrowserDialog(Context context, OnFileSelectedListener listener) {
            this.context = context;
            this.listener = listener;
        }

        public void show() {
            String currentPath = "/";
            showDirectory(currentPath);
        }

        private void showDirectory(String path) {
            ArrayList<String> items = loadDirectory(path);

            if (!path.equals("/")) {
                items.add(0, "..");
            }

            String[] itemArray = items.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Select File");
            builder.setItems(itemArray, (dialog, which) -> {
                String selectedItem = itemArray[which];
                if (selectedItem.equals("..")) {
                    String parentPath = goUp(path);
                    showDirectory(parentPath);
                } else if (selectedItem.endsWith("/")) {
                    showDirectory(path + selectedItem);
                } else {
                    listener.onFileSelected(path + selectedItem);
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

            AlertDialog dialog = builder.create();

            dialog.setOnShowListener(d -> {
                Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (cancelButton != null) {
                    cancelButton.setTextColor(Color.WHITE);
                }
            });

            dialog.show();
        }

        private ArrayList<String> loadDirectory(String path) {
            ArrayList<String> result = new ArrayList<>();
            String output = exe.RunAsChrootOutput("ls -p " + path);

            if (output != null && !output.isEmpty()) {
                String[] lines = output.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        result.add(line);
                    }
                }
            }
            return result;
        }

        private String goUp(String path) {
            if (path.equals("/")) return path;

            String newPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            int lastSlash = newPath.lastIndexOf('/');
            if (lastSlash >= 0) {
                newPath = newPath.substring(0, lastSlash + 1);
            } else {
                newPath = "/";
            }
            return newPath;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called");
        sharedpreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        super.onCreate(savedInstanceState);
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.can, container, false);
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        TabsPagerAdapter tabsPagerAdapter = new TabsPagerAdapter(this);

        ViewPager2 mViewPager = rootView.findViewById(R.id.pagerCAN);
        mViewPager.setAdapter(tabsPagerAdapter);
        mViewPager.setOffscreenPageLimit(7);

        TabLayout tabLayout = rootView.findViewById(R.id.tabLayoutCAN);
        new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("Main"); break;
                        case 1: tab.setText("Tools"); break;
                        case 2: tab.setText("CAN-USB"); break;
                        case 3: tab.setText("Caribou"); break;
                        case 4: tab.setText("ICSim"); break;
                        case 5: tab.setText("MSF"); break;
                        default: tab.setText("Tab " + (position + 1));
                    }
                }
        ).attach();

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                activity.invalidateOptionsMenu();
            }
        });

        // Add MenuProvider for menu handling
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.can, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.documentation) {
                    RunDocumentation();
                    return true;
                } else if (id == R.id.setup) {
                    RunSetup();
                    return true;
                } else if (id == R.id.update) {
                    RunUpdate();
                    return true;
                } else if (id == R.id.about) {
                    RunAbout();
                    return true;
                } else {
                    return false;
                }
            }
        }, getViewLifecycleOwner());

        return rootView;
    }

    // First Setup
    public void SetupDialog() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.DialogStyleCompat);
        builder.setTitle("Welcome to CAN Arsenal!");
        builder.setMessage("This seems to be the first run. Install the CAN tools?");
        builder.setPositiveButton("Install", (dialog, which) -> {
            RunSetup();
            sharedpreferences.edit().putBoolean("setup_done", true).apply();
        });
        builder.setNegativeButton("Disable message", (dialog, which) -> {
            dialog.dismiss();
            sharedpreferences.edit().putBoolean("setup_done", true).apply();
        });
        builder.show();
    }

    // Documentation item
    public void RunDocumentation() {
        String url = "https://www.kali.org/docs/nethunter/nethunter-canarsenal/";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(intent);
    }

    // Setup item
    public void RunSetup() {
        Log.d(TAG, "RunSetup called");
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

        Log.i(TAG, "Running setup commands");
        String setupCommand = "echo -ne \"\\033]0;CARsenal Setup\\007\" && clear;which wget > /dev/null 2>&1 && wget -qO - https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s setup || curl -s https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s setup";
        String setupResult = run_cmd(setupCommand);
        Log.d("SetupResult",setupResult);
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
        Log.i(TAG, "Setup completed");
    }

    // Update item
    public void RunUpdate() {
        Log.d(TAG, "RunUpdate called");
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

        Log.i(TAG, "Running update commands");
        String updateCommand = "echo -ne \"\\033]0;CARsenal Update\\007\" && clear;which wget > /dev/null 2>&1 && wget -qO - https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s update || curl -s https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s update";
        String updateResult = run_cmd(updateCommand);
        Log.d("UpdateResult",updateResult);
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
        Log.i(TAG, "Update completed");
    }

    private void safeReleaseMediaPlayer(MediaPlayer player) {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (IllegalStateException ignored) {
                // Player not in valid state, ignore
            }
            player.release();
        }
    }

    public void RunAbout() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.can_about_dialog, null);

        TextView aboutText = dialogView.findViewById(R.id.about_text);
        TextView creditsText = dialogView.findViewById(R.id.credits_text);

        aboutText.setText(HtmlCompat.fromHtml(
                getString(R.string.about_text), HtmlCompat.FROM_HTML_MODE_LEGACY));
        aboutText.setMovementMethod(LinkMovementMethod.getInstance());

        creditsText.setText(HtmlCompat.fromHtml(
                getString(R.string.credits_text), HtmlCompat.FROM_HTML_MODE_LEGACY));
        creditsText.setMovementMethod(LinkMovementMethod.getInstance());

        // Easter egg button setup
        ImageView easterEggButton = dialogView.findViewById(R.id.easter_egg_button);

        // Create media players
        MediaPlayer mediaPlayerVroom = MediaPlayer.create(activity, R.raw.secret_vroom);
        MediaPlayer mediaPlayerAngry = MediaPlayer.create(activity, R.raw.secret_angry);

        final int[] clickCount = {0};
        final long[] lastClickTime = {0};
        final long CLICK_TIMEOUT = 2000; // 2 seconds

        easterEggButton.setOnClickListener(v -> {
            long now = System.currentTimeMillis();

            // Ignore clicks during cooldown after reset
            if (now - lastResetTime < RESET_COOLDOWN) {
                return;
            }

            // Reset click sequence if too much time passed
            if (now - lastClickTime[0] > CLICK_TIMEOUT) {
                clickCount[0] = 0;
            }

            lastClickTime[0] = now;
            clickCount[0]++;

            switch (clickCount[0]) {
                case 3:
                    showToast("Hum??? What's up?");
                    break;
                case 6:
                    try {
                        if (mediaPlayerVroom.isPlaying()) mediaPlayerVroom.seekTo(0);
                        mediaPlayerVroom.start();
                    } catch (IllegalStateException ignored) {}
                    break;
                case 15:
                    showToast("Ok. It was funny, but don't make me angry...");
                    break;
                case 25:
                    showToast("GRMBLBLBL... This is your LAST warning!");
                    break;
                case 30:
                    try {
                        if (mediaPlayerAngry.isPlaying()) mediaPlayerAngry.seekTo(0);
                        mediaPlayerAngry.start();
                    } catch (IllegalStateException ignored) {}
                    clickCount[0] = 0; // reset after final sound
                    lastResetTime = now; // start cooldown
                    break;
            }
        });

        // Create a centered title TextView
        TextView titleView = new TextView(activity);
        titleView.setText("About CARsenal");
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        titleView.setPadding(0, padding, 0, padding);

        // Build the dialog
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                .setCustomTitle(titleView)
                .setView(dialogView)
                .setNegativeButton("Close", (d, id) -> {
                    safeReleaseMediaPlayer(mediaPlayerVroom);
                    safeReleaseMediaPlayer(mediaPlayerAngry);
                })
                .create();

        dialog.setOnDismissListener(d -> {
            clickCount[0] = 0;
            lastClickTime[0] = 0;
            safeReleaseMediaPlayer(mediaPlayerVroom);
            safeReleaseMediaPlayer(mediaPlayerAngry);
        });

        dialog.show();
    }

    private void releaseMediaPlayers(MediaPlayer... players) {
        for (MediaPlayer mp : players) {
            if (mp != null) {
                if (mp.isPlaying()) mp.stop();
                mp.release();
            }
        }
    }

    public static class TabsPagerAdapter extends FragmentStateAdapter {
        public TabsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new MainFragment();
                case 1:
                    return new CANFragment.ToolsFragment();
                case 2:
                    return new CANUSBFragment();
                case 3:
                    return new CANFragment.CANCARIBOUFragment();
                case 4:
                    return new CANICSIMFragment();
                default :
                    return new CANMSFFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 6;
        }
    }

    public static class MainFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private static final long SHORT_DELAY = 1000L;
        private Context context;
        private TextView SelectedIface;

        private void updateFieldLayout(TextInputLayout mtu, TextInputLayout txq) {
            boolean mtuVisible = mtu.getVisibility() == View.VISIBLE;
            boolean txqVisible = txq.getVisibility() == View.VISIBLE;

            if (mtuVisible && txqVisible) {
                LinearLayout.LayoutParams mtuParams =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                mtuParams.setMarginEnd(dpToPx());

                LinearLayout.LayoutParams txqParams =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                txqParams.setMarginStart(dpToPx());

                mtu.setLayoutParams(mtuParams);
                txq.setLayoutParams(txqParams);
            } else {
                LinearLayout.LayoutParams fullParams =
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mtu.setLayoutParams(fullParams);
                txq.setLayoutParams(fullParams);
            }
        }

        private int dpToPx() {
            return (int) (5 * getResources().getDisplayMetrics().density);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_main, container, false);

            // Common used variables
            SelectedIface = rootView.findViewById(R.id.can_iface);

            final EditText selected_vin = rootView.findViewById(R.id.vin_number);

            // First run
            boolean setupdone = sharedpreferences.getBoolean("setup_done", false);
            if (!setupdone) {
                SetupDialog();
            }

            // Toggle Advanced Options
            Button btnMtu = rootView.findViewById(R.id.btn_toggle_mtu);
            TextInputLayout SelectedMTU = rootView.findViewById(R.id.mtu_container);
            Button btnTxqueuelen = rootView.findViewById(R.id.btn_toggle_txqueuelen);
            TextInputLayout SelectedTxqueuelen = rootView.findViewById(R.id.txqueuelen_container);

            btnMtu.setOnClickListener(v -> {
                boolean isVisible = SelectedMTU.getVisibility() == View.VISIBLE;
                SelectedMTU.setVisibility(isVisible ? View.GONE : View.VISIBLE);

                int color = isVisible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnMtu.setTextColor(ContextCompat.getColorStateList(requireContext(), color));

                updateFieldLayout(SelectedMTU, SelectedTxqueuelen);
            });

            btnTxqueuelen.setOnClickListener(v -> {
                boolean isVisible = SelectedTxqueuelen.getVisibility() == View.VISIBLE;
                SelectedTxqueuelen.setVisibility(isVisible ? View.GONE : View.VISIBLE);

                int color = isVisible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnTxqueuelen.setTextColor(ContextCompat.getColorStateList(requireContext(), color));

                updateFieldLayout(SelectedMTU, SelectedTxqueuelen);
            });

            // Services Toggle
            Button btnServicesToggle = rootView.findViewById(R.id.btn_toggle_services);
            LinearLayout servicesLayout = rootView.findViewById(R.id.main_services);

            btnServicesToggle.setOnClickListener(v -> {
                if (servicesLayout.getVisibility() == View.GONE) {
                    servicesLayout.setVisibility(View.VISIBLE);
                    btnServicesToggle.setText(R.string.can_hide_services);
                } else {
                    servicesLayout.setVisibility(View.GONE);
                    btnServicesToggle.setText(R.string.can_services);
                }
            });

            // Attach and Daemon
            // ldattach
            Button LdAttachButton = rootView.findViewById(R.id.start_ldattach);

            // Access SharedPreferences
            SharedPreferences ldAttach_prefs = requireActivity().getSharedPreferences("ldAttach_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorLdAttach = ldAttach_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_ldAttach = ldAttach_prefs.getString("ldAttach_cmd", "ldattach --debug --speed 38400 --eightbits --noparity --onestopbit --iflag -ICRNL,INLCR,-IXOFF 29 /dev/rfcomm0");
            String[] ldAttachCmdHolder = { savedCmd_ldAttach };

            LdAttachButton.setOnClickListener(v -> {
                String ldAttachRun = ldAttachCmdHolder[0];

                if (!ldAttachRun.isEmpty()) {
                    run_cmd(ldAttachRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast( "Please set your ldattach command!");
                }
            });

            // Long click lets user edit the command
            LdAttachButton.setOnLongClickListener(v -> {
                AlertDialog.Builder builder_ldAttach = new AlertDialog.Builder(requireContext());
                builder_ldAttach.setTitle("Edit Command");

                final EditText input_ldAttach = new EditText(requireContext());
                input_ldAttach.setText(ldAttachCmdHolder[0]);
                builder_ldAttach.setView(input_ldAttach);

                builder_ldAttach.setPositiveButton("Save", (dialog, which) -> {
                    String newLdAttachCmd = input_ldAttach.getText().toString();
                    ldAttachCmdHolder[0] = newLdAttachCmd;

                    // Save to SharedPreferences
                    editorLdAttach.putString("ldAttach_cmd", newLdAttachCmd);
                    editorLdAttach.apply();

                    showToast("Command updated!");
                });

                builder_ldAttach.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                AlertDialog dialog = builder_ldAttach.create();
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
                });
                dialog.show();
                return true; // long click handled
            });

            // slcand
            Button SlcandButton = rootView.findViewById(R.id.start_slcand);

            // Access SharedPreferences
            SharedPreferences slcand_prefs = requireActivity().getSharedPreferences("slcand_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorSlcand = slcand_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_slcand = slcand_prefs.getString("slcand_cmd", "slcand -s6 -t sw -S 200000 /dev/ttyUSB0");
            String[] slcandCmdHolder = { savedCmd_slcand };

            SlcandButton.setOnClickListener(v -> {
                String slcandRun = slcandCmdHolder[0];

                if (!slcandRun.isEmpty()) {
                    run_cmd(slcandRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast("Please set your slcand command!");
                }
            });

            // Long click lets user edit the command
            SlcandButton.setOnLongClickListener(v -> {
                AlertDialog.Builder builder_slcand = new AlertDialog.Builder(requireContext());
                builder_slcand.setTitle("Edit Command");

                final EditText input_slcand = new EditText(requireContext());
                input_slcand.setText(slcandCmdHolder[0]);
                builder_slcand.setView(input_slcand);

                builder_slcand.setPositiveButton("Save", (dialog, which) -> {
                    String newSlcandCmd = input_slcand.getText().toString();
                    slcandCmdHolder[0] = newSlcandCmd;

                    // Save to SharedPreferences
                    editorSlcand.putString("slcand_cmd", newSlcandCmd);
                    editorSlcand.apply();

                    showToast("Command updated!");
                });

                builder_slcand.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                AlertDialog dialog = builder_slcand.create();
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
                });
                dialog.show();
                return true; // long click handled
            });

            // slcan_attach
            Button SlcanAttachButton = rootView.findViewById(R.id.start_slcanattach);

            // Access SharedPreferences
            SharedPreferences slcanAttach_prefs = requireActivity().getSharedPreferences("slcanAttach_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorSlcanAttach = slcanAttach_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_slcanAttach = slcanAttach_prefs.getString("slcanAttach_cmd", "slcan_attach -s6 -o /dev/ttyUSB0");
            String[] slcanAttachCmdHolder = { savedCmd_slcanAttach };

            SlcanAttachButton.setOnClickListener(v -> {
                String slcanAttachRun = slcanAttachCmdHolder[0];

                if (!slcanAttachRun.isEmpty()) {
                    run_cmd(slcanAttachRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast("Please set your slcan_attach command!");
                }
            });

            // Long click lets user edit the command
            SlcanAttachButton.setOnLongClickListener(v -> {
                AlertDialog.Builder builder_slcanAttach = new AlertDialog.Builder(requireContext());
                builder_slcanAttach.setTitle("Edit Command");

                final EditText input_slcanAttach = new EditText(requireContext());
                input_slcanAttach.setText(slcanAttachCmdHolder[0]);
                builder_slcanAttach.setView(input_slcanAttach);

                builder_slcanAttach.setPositiveButton("Save", (dialog, which) -> {
                    String newSlcanAttachCmd = input_slcanAttach.getText().toString();
                    slcanAttachCmdHolder[0] = newSlcanAttachCmd;

                    // Save to SharedPreferences
                    editorSlcanAttach.putString("slcanAttach_cmd", newSlcanAttachCmd);
                    editorSlcanAttach.apply();

                    showToast("Command updated!");
                });

                builder_slcanAttach.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                AlertDialog dialog = builder_slcanAttach.create();
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
                });
                dialog.show();
                return true; // long click handled
            });

            // hlcan
            Button hlcandButton = rootView.findViewById(R.id.start_hlcand);

            // Access SharedPreferences
            SharedPreferences hlcand_prefs = requireActivity().getSharedPreferences("hlcand_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorHlcand = hlcand_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_hlcand = hlcand_prefs.getString("hlcand_cmd", "hlcand -F -s 500000 /dev/ttyUSB0");
            String[] hlcandCmdHolder = { savedCmd_hlcand };

            hlcandButton.setOnClickListener(v -> {
                String hlcandRun = hlcandCmdHolder[0];

                if (!hlcandRun.isEmpty()) {
                    run_cmd(hlcandRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast("Please set your hlcand command!");
                }
            });

            // Long click lets user edit the command
            hlcandButton.setOnLongClickListener(v -> {
                AlertDialog.Builder builder_hlcand = new AlertDialog.Builder(requireContext());
                builder_hlcand.setTitle("Edit Command");

                final EditText input_hlcand = new EditText(requireContext());
                input_hlcand.setText(hlcandCmdHolder[0]);
                builder_hlcand.setView(input_hlcand);

                builder_hlcand.setPositiveButton("Save", (dialog, which) -> {
                    String newHlcandCmd = input_hlcand.getText().toString();
                    hlcandCmdHolder[0] = newHlcandCmd;

                    // Save to SharedPreferences
                    editorHlcand.putString("hlcand_cmd", newHlcandCmd);
                    editorHlcand.apply();

                    showToast("Command updated!");
                });

                builder_hlcand.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                AlertDialog dialog = builder_hlcand.create();
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
                });
                dialog.show();
                return true; // long click handled
            });

            // Rfcomm Binder
            Button RfcommBinderButton = rootView.findViewById(R.id.start_rfcommbinder);

            // Access SharedPreferences
            SharedPreferences rfcommBinder_prefs = requireActivity().getSharedPreferences("rfcommBinder_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorRfcommBinder = rfcommBinder_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_rfcomm_binder = rfcommBinder_prefs.getString("rfcommBinder_cmd", "rfcomm bind vcan0 00:AA:BB:CC:DD:EE:FF");
            String[] rfcommBinderCmdHolder = { savedCmd_rfcomm_binder };

            RfcommBinderButton.setOnClickListener(v -> {
                String rfcommBinderRun = rfcommBinderCmdHolder[0];

                if (!rfcommBinderRun.isEmpty()) {
                    run_cmd(rfcommBinderRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast("Please set your rfcomm binder command!");
                }
            });

            // Long click lets user edit the command
            RfcommBinderButton.setOnLongClickListener(v -> {
                AlertDialog.Builder builder_rfcommBinder = new AlertDialog.Builder(requireContext());
                builder_rfcommBinder.setTitle("Edit Command");

                final EditText input_rfcomm_binder = new EditText(requireContext());
                input_rfcomm_binder.setText(rfcommBinderCmdHolder[0]);
                builder_rfcommBinder.setView(input_rfcomm_binder);

                builder_rfcommBinder.setPositiveButton("Save", (dialog, which) -> {
                    String newRfcommBinderCmd = input_rfcomm_binder.getText().toString();
                    rfcommBinderCmdHolder[0] = newRfcommBinderCmd;

                    // Save to SharedPreferences
                    editorRfcommBinder.putString("rfcommBinder_cmd", newRfcommBinderCmd);
                    editorRfcommBinder.apply();

                    showToast("Command updated!");
                });

                builder_rfcommBinder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                AlertDialog dialog = builder_rfcommBinder.create();
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
                });
                dialog.show();
                return true; // long click handled
            });

            // Rfcomm Connect
            Button RfcommConnectButton = rootView.findViewById(R.id.start_rfcommconnect);

            // Access SharedPreferences
            SharedPreferences rfcommConnect_prefs = requireActivity().getSharedPreferences("rfcommConnect_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorRfcommConnect = rfcommConnect_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_rfcomm_connect = rfcommConnect_prefs.getString("rfcommConnect_cmd", "rfcomm connect /dev/ttyS0 00:AA:BB:CC:DD:EE:FF");
            String[] rfcommConnectCmdHolder = { savedCmd_rfcomm_connect };

            RfcommConnectButton.setOnClickListener(v -> {
                String rfcommConnectRun = rfcommConnectCmdHolder[0];

                if (!rfcommConnectRun.isEmpty()) {
                    run_cmd(rfcommConnectRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast("Please set your rfcomm connect command!");
                }
            });

            // Long click lets user edit the command
            RfcommConnectButton.setOnLongClickListener(v -> {
                AlertDialog.Builder builder_rfcommConnect = new AlertDialog.Builder(requireContext());
                builder_rfcommConnect.setTitle("Edit Command");

                final EditText input_rfcomm_connect = new EditText(requireContext());
                input_rfcomm_connect.setText(rfcommConnectCmdHolder[0]);
                builder_rfcommConnect.setView(input_rfcomm_connect);

                builder_rfcommConnect.setPositiveButton("Save", (dialog, which) -> {
                    String newRfcommConnectCmd = input_rfcomm_connect.getText().toString();
                    rfcommConnectCmdHolder[0] = newRfcommConnectCmd;

                    // Save to SharedPreferences
                    editorRfcommConnect.putString("rfcommConnect_cmd", newRfcommConnectCmd);
                    editorRfcommConnect.apply();

                    showToast("Command updated!");
                });

                builder_rfcommConnect.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                AlertDialog dialog = builder_rfcommConnect.create();
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
                });
                dialog.show();
                return true; // long click handled
            });

            // Socketcand
            Button SocketcandButton = rootView.findViewById(R.id.start_socketcand);

            // Access SharedPreferences
            SharedPreferences socketcand_prefs = requireActivity().getSharedPreferences("socketcand_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorSocketcand = socketcand_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_socketcand = socketcand_prefs.getString("socketcand_cmd", "socketcand -v -l wlan0 -i vcan0");
            String[] socketcandCmdHolder = { savedCmd_socketcand };

            SocketcandButton.setOnClickListener(v -> {
                String socketcandRun = socketcandCmdHolder[0];

                if (!socketcandRun.isEmpty()) {
                    run_cmd(socketcandRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast("Please set your rfcomm connect command!");
                }
            });

            // Long click lets user edit the command
            SocketcandButton.setOnLongClickListener(v -> {
                AlertDialog.Builder builder_socketcand = new AlertDialog.Builder(requireContext());
                builder_socketcand.setTitle("Edit Command");

                final EditText input_socketcand = new EditText(requireContext());
                input_socketcand.setText(socketcandCmdHolder[0]);
                builder_socketcand.setView(input_socketcand);

                builder_socketcand.setPositiveButton("Save", (dialog, which) -> {
                    String newsocketcandCmd = input_socketcand.getText().toString();
                    socketcandCmdHolder[0] = newsocketcandCmd;

                    // Save to SharedPreferences
                    editorSocketcand.putString("socketcand_cmd", newsocketcandCmd);
                    editorSocketcand.apply();

                    showToast("Command updated!");
                });

                builder_socketcand.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                AlertDialog dialog = builder_socketcand.create();
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
                });
                dialog.show();
                return true; // long click handled
            });

            // Interfaces
            // Can Type Spinner
            final Spinner canTypeList = rootView.findViewById(R.id.cantype_spinner);
            ArrayAdapter<String> adapter = getStringArrayAdapter();
            canTypeList.setAdapter(adapter);
            canTypeList.setSelection(0);  // Set initial selection to "Type"

            canTypeList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    if (pos != 0) { // Ignore "Type" hint
                        String cantype_selected = parentView.getItemAtPosition(pos).toString();
                        sharedpreferences.edit().putString("cantype_selected", cantype_selected).apply();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                    // Do nothing
                }
            });

            // Start CAN interface
            Button StartCanButton = rootView.findViewById(R.id.start_caniface);
            StartCanButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();
                assert SelectedMTU.getEditText() != null;
                String selected_mtu = SelectedMTU.getEditText().getText().toString();
                assert SelectedTxqueuelen.getEditText() != null;
                String selected_txqueuelen = SelectedTxqueuelen.getEditText().getText().toString();
                String interface_type = sharedpreferences.getString("cantype_selected", "");

                if (!selected_caniface.isEmpty() && selected_caniface.matches("^(can|vcan|slcan)[0-9]$")) {
                    if ("vcan".equals(interface_type)) {
                        String addVcanIface = exe.RunAsChrootOutput("sudo ip link add dev " + selected_caniface + " type " + interface_type + " && echo Success || echo Failed");
                        if (addVcanIface.contains("FATAL:") || addVcanIface.contains("Failed")) {
                            showToast("Failed to add " + selected_caniface + " interface! Interface may already existing.");
                            return;
                        }
                    }
                    if ("can".equals(interface_type) || "slcan".equals(interface_type)) {
                        String usbDevice = exe.RunAsChrootOutput("ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$'");
                        if (usbDevice.isEmpty()) {
                            showToast("No CAN Hardware detected, please connect adapter and try again.");
                            return;
                        }
                    }

                    if (!selected_mtu.isEmpty()) {
                        exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " mtu " + selected_mtu + " && echo Success || echo Failed");
                    }
                    if (!selected_txqueuelen.isEmpty()) {
                        exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " txqueuelen " + selected_txqueuelen + " && echo Success || echo Failed");
                    }

                    String startCanIface = exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " up && echo Success || echo Failed");
                    if (startCanIface.contains("FATAL:") || startCanIface.contains("Failed")) {
                        showToast("Failed to start " + selected_caniface + " interface!");
                    } else {
                        showToast("Interface " + selected_caniface + " started!");
                    }
                } else {
                    if (selected_caniface.isEmpty()) {
                        showToast("Please set a CAN interface!");
                        return;
                    }
                    if (!selected_caniface.matches("^(can|vcan|slcan)[0-9]$")) {
                        showToast("CAN Interface should be named \"^(can|vcan|slcan)[0-9]$\"");
                    }
                }
            });

            // Button Reset Interface
            Button ResetIfaceButton = rootView.findViewById(R.id.reset_iface);

            ResetIfaceButton.setOnClickListener(v -> {
                exe.RunAsChrootOutput("/opt/car_hacking/can_reset.sh");
                showToast("Interface reset!");
            });

            // VIN Info
            final EditText term = rootView.findViewById(R.id.TerminalOutputVINInfo);
            // Show
            Button VINShowButton = rootView.findViewById(R.id.vin_show);

            VINShowButton.setOnClickListener(v -> {
                String vinNumber = selected_vin.getText().toString();
                if (vinNumber.length() != 17) {
                    Toast.makeText(context, "VIN must be exactly 17 characters long.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String cmd_show = "sudo mkdir -p /sdcard/nh_files/carsenal;/opt/car_hacking/car_venv/bin/vininfo show " + vinNumber + " | tr -s [:space:] > /sdcard/nh_files" + "/carsenal/output.txt";
                new BootKali(cmd_show).run_bg();
                try {
                    Thread.sleep(SHORT_DELAY);
                    String output = exe.RunAsRootOutput("cat " + NhPaths.APP_SD_FILES_PATH + "/carsenal/output.txt");
                    term.setText(output);
                } catch (Exception e) {
                    Log.e("VINShowError", "Exception while reading VIN info", e);
                    term.setText(String.format("Error: %s - %s", e.getClass().getSimpleName(), e.getMessage()));
                }
            });

            // Check
            Button VINCheckButton = rootView.findViewById(R.id.vin_check);

            VINCheckButton.setOnClickListener(v -> {
                String vinNumber = selected_vin.getText().toString();
                if (vinNumber.length() != 17) {
                    Toast.makeText(context, "VIN must be exactly 17 characters long.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String cmd_check = "sudo mkdir -p /sdcard/nh_files/carsenal;/opt/car_hacking/car_venv/bin/vininfo check " + vinNumber + " | tr -s [:space:] > /sdcard/nh_files/carsenal/output.txt";
                new BootKali(cmd_check).run_bg();
                try {
                    Thread.sleep(SHORT_DELAY);
                    String output = exe.RunAsRootOutput("cat " + NhPaths.APP_SD_FILES_PATH + "/carsenal/output.txt");
                    term.setText(output);
                } catch (Exception e) {
                    Log.e("VINCheckError", "Exception while reading VIN info", e);
                    term.setText(String.format("Error: %s - %s", e.getClass().getSimpleName(), e.getMessage()));
                }
            });

            return rootView;
        }

        @NonNull
        private ArrayAdapter<String> getStringArrayAdapter() {
            final String[] interfaceTypeOptions = {"Type", "can", "vcan", "slcan"};

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, interfaceTypeOptions) {
                @Override
                public boolean isEnabled(int position) {
                    // Disable "Type" item
                    return position != 0;
                }

                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    if (position == 0) {
                        tv.setTextColor(Color.GRAY);  // Hint text color
                    } else {
                        tv.setTextColor(Color.WHITE); // Normal text
                    }
                    return view;
                }
            };

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return adapter;
        }
    }

    public static class ToolsFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Activity activity;
        private boolean isInteractiveEnabled = false;
        private boolean isVerboseEnabled = false;
        private boolean isDisableLoopbackEnabled = false;
        private String selected_caniface;
        private final String[] canGenCmd = {""};
        private final String[] canSnifferCmd = {""};
        private final String[] canDumpCmd = {""};
        private final String[] canSendCmd = {""};
        private final String[] canPlayerCmd = {""};
        private final String[] sequenceFinderCmd = {""};
        private final String[] freediagCmd = {""};
        private final String[] diagTestCmd = {""};
        private final String[] cannelloniCmd = {""};
        private final String[] asc2logCmd = {""};
        private final String[] log2ascCmd = {""};


        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_tools, container, false);

            final EditText cansend_sequence = rootView.findViewById(R.id.cansend_sequence);
            final EditText SelectedRHost = rootView.findViewById(R.id.cannelloni_rhost);
            final EditText SelectedRPort = rootView.findViewById(R.id.cannelloni_rport);
            final EditText SelectedLPort = rootView.findViewById(R.id.cannelloni_lport);
            final EditText CustomCmd = rootView.findViewById(R.id.customcmd);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            activity = getActivity();

            // Load saved commands or empty strings
            canGenCmd[0] = prefs.getString("canGen_cmd", "");
            canSnifferCmd[0] = prefs.getString("canSniffer_cmd", "");
            canDumpCmd[0] = prefs.getString("canDump_cmd", "");
            canSendCmd[0] = prefs.getString("canSend_cmd", "");
            canPlayerCmd[0] = prefs.getString("canPlayer_cmd", "");
            sequenceFinderCmd[0] = prefs.getString("sequenceFinder_cmd", "");
            freediagCmd[0] = prefs.getString("freediag_cmd", "");
            diagTestCmd[0] = prefs.getString("diagTest_cmd", "");
            cannelloniCmd[0] = prefs.getString("cannelloni_cmd", "");
            asc2logCmd[0] = prefs.getString("asc2log_cmd", "");
            log2ascCmd[0] = prefs.getString("log2asc_cmd", "");

            // Configuration Toggle
            Button btnConfigurationToggle = rootView.findViewById(R.id.btn_toggle_config_tools);
            LinearLayout configurationLayout = rootView.findViewById(R.id.tools_config);

            btnConfigurationToggle.setOnClickListener(v -> {
                if (configurationLayout.getVisibility() == View.GONE) {
                    configurationLayout.setVisibility(View.VISIBLE);
                    btnConfigurationToggle.setText(R.string.can_hide_configuration);
                } else {
                    configurationLayout.setVisibility(View.GONE);
                    btnConfigurationToggle.setText(R.string.can_configuration);
                }
            });

            // Interfaces
            Spinner spinner = rootView.findViewById(R.id.device_interface);
            ImageButton refreshBtn = rootView.findViewById(R.id.refreshUSB);

            SpinnerUtils.setupDeviceInterfaceSpinner(
                    requireContext(),
                    executorService,
                    exe,
                    spinner,
                    refreshBtn,
                    sharedpreferences,
                    "selected_usb",
                    false,
                    iface -> selected_caniface = iface
            );

            // Advanced Options Toggle
            Button btnToggle = rootView.findViewById(R.id.btn_toggle_advanced);
            LinearLayout advancedOptionsLayout = rootView.findViewById(R.id.tools_advanced_options);

            btnToggle.setOnClickListener(v -> {
                if (advancedOptionsLayout.getVisibility() == View.GONE) {
                    advancedOptionsLayout.setVisibility(View.VISIBLE);
                    btnToggle.setText(R.string.can_hide_advanced_options);
                } else {
                    advancedOptionsLayout.setVisibility(View.GONE);
                    btnToggle.setText(R.string.can_advanced_options);
                }
            });

            // Interactive Switch
            SwitchCompat switchInteractive = rootView.findViewById(R.id.btn_toggle_interactive);
            switchInteractive.setChecked(isInteractiveEnabled);

            switchInteractive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isInteractiveEnabled = isChecked;

                int color = isInteractiveEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                switchInteractive.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Verbose Switch
            SwitchCompat switchVerbose = rootView.findViewById(R.id.btn_toggle_verbose);
            switchVerbose.setChecked(isVerboseEnabled);

            switchVerbose.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isVerboseEnabled = isChecked;

                int color = isVerboseEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                switchVerbose.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Loopback Switch (inverted logic)
            SwitchCompat switchLoopback = rootView.findViewById(R.id.btn_toggle_loopback);
            switchLoopback.setChecked(!isDisableLoopbackEnabled);

            switchLoopback.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isDisableLoopbackEnabled = !isChecked; // invert logic here

                int color = isDisableLoopbackEnabled ? android.R.color.holo_red_light : android.R.color.holo_green_light; //invert color
                switchLoopback.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });


            // Input File browse button
            MaterialButton inputfilebrowse = rootView.findViewById(R.id.inputfilebrowse);
            TextInputEditText inputfilepath = rootView.findViewById(R.id.inputfilepath);

            inputfilebrowse.setOnClickListener(v -> {
                RootFileBrowserDialog dialog = new RootFileBrowserDialog(requireContext(), inputfilepath::setText);
                dialog.show();
            });

            // Output File browse button
            MaterialButton outputfilebrowse = rootView.findViewById(R.id.outputfilebrowse);
            TextInputEditText outputfilepath = rootView.findViewById(R.id.outputfilepath);

            outputfilebrowse.setOnClickListener(v -> {
                RootFileBrowserDialog dialog = new RootFileBrowserDialog(requireContext(), outputfilepath::setText);
                dialog.show();
            });


            // Tools
            // CanGen
            Button CanGenButton = rootView.findViewById(R.id.start_cangen);
            CanGenButton.setOnClickListener(v -> {
                String verboseEnabled = isVerboseEnabled ? " -v" : "";
                String disableLoopbackEnabled = isDisableLoopbackEnabled ? " -x" : "";

                if (!canGenCmd[0].isEmpty()) {
                    run_cmd(canGenCmd[0]);
                } else if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    run_cmd("cangen " + selected_caniface + verboseEnabled + disableLoopbackEnabled);
                } else {
                    showToast("Please ensure your CAN Interface field is set!");
                }

                activity.invalidateOptionsMenu();
            });
            CanGenButton.setOnLongClickListener(v -> {
                String defaultCmd = "cangen " + selected_caniface + (isVerboseEnabled ? " -v" : "") + (isDisableLoopbackEnabled ? " -x" : "");
                showEditCommandDialog("Edit CanGen Command", canGenCmd, "canGen_cmd", defaultCmd);
                return true;
            });

            // CanSniffer
            Button CanSnifferButton = rootView.findViewById(R.id.start_cansniffer);
            CanSnifferButton.setOnClickListener(v -> {
                if (!canSnifferCmd[0].isEmpty()) {
                    run_cmd(canSnifferCmd[0]);
                } else if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    run_cmd("cansniffer " + selected_caniface);
                } else {
                    showToast("Please ensure your CAN Interface field is set!");
                }
                activity.invalidateOptionsMenu();
            });
            CanSnifferButton.setOnLongClickListener(v -> {
                String defaultCmd = "cansniffer " + selected_caniface;
                showEditCommandDialog("Edit CanSniffer Command", canSnifferCmd, "canSniffer_cmd", defaultCmd);
                return true;
            });

            // CanDump
            Button CanDumpButton = rootView.findViewById(R.id.start_candump);
            CanDumpButton.setOnClickListener(v -> {
                String outputfile = Objects.requireNonNull(outputfilepath.getText()).toString();

                if (!canDumpCmd[0].isEmpty()) {
                    run_cmd(canDumpCmd[0]);
                } else if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interface (None)") && !outputfile.isEmpty()) {
                    run_cmd("candump " + selected_caniface + " -f " + outputfile);
                } else {
                    showToast("Please ensure your CAN Interface and Output File fields is set!");
                }

                activity.invalidateOptionsMenu();
            });
            CanDumpButton.setOnLongClickListener(v -> {
                String defaultCmd = "candump " + selected_caniface + " -f " + Objects.requireNonNull(outputfilepath.getText());
                showEditCommandDialog("Edit CanDump Command", canDumpCmd, "canDump_cmd", defaultCmd);
                return true;
            });

            // CanSend
            Button CanSendButton = rootView.findViewById(R.id.start_cansend);
            CanSendButton.setOnClickListener(v -> {
                String sequence = cansend_sequence.getText().toString();

                if (!canSendCmd[0].isEmpty()) {
                    run_cmd(canSendCmd[0]);
                } else if (!selected_caniface.equals("Interfaces") && !sequence.isEmpty()) {
                    run_cmd("cansend " + selected_caniface + " " + sequence);
                } else {
                    showToast("Please ensure your CAN Interface and Sequence fields is set!");
                }

                activity.invalidateOptionsMenu();
            });
            CanSendButton.setOnLongClickListener(v -> {
                String defaultCmd = "cansend " + selected_caniface + " " + cansend_sequence.getText().toString();
                showEditCommandDialog("Edit CanSend Command", canSendCmd, "canSend_cmd", defaultCmd);
                return true;
            });

            // CanPlayer
            Button CanPlayerButton = rootView.findViewById(R.id.start_canplayer);
            CanPlayerButton.setOnClickListener(v -> {
                String interactiveEnabled = isInteractiveEnabled ? " -i" : "";
                String verboseEnabled = isVerboseEnabled ? " -v" : "";
                String disableLoopbackEnabled = isDisableLoopbackEnabled ? " -x" : "";
                String inputfile = Objects.requireNonNull(inputfilepath.getText()).toString();

                if (!canPlayerCmd[0].isEmpty()) {
                    run_cmd(canPlayerCmd[0]);
                } else if (!inputfile.isEmpty()) {
                    run_cmd("canplayer -I " + inputfile + interactiveEnabled + verboseEnabled + disableLoopbackEnabled);
                } else {
                    showToast("Please ensure your Input File field is set!");
                }

                activity.invalidateOptionsMenu();
            });
            CanPlayerButton.setOnLongClickListener(v -> {
                String defaultCmd = "canplayer -I " + Objects.requireNonNull(inputfilepath.getText()) + (isInteractiveEnabled ? " -i" : "") + (isVerboseEnabled ? " -v" : "") + (isDisableLoopbackEnabled ? " -x" : "");
                showEditCommandDialog("Edit CanPlayer Command", canPlayerCmd, "canPlayer_cmd", defaultCmd);
                return true;
            });

            // SequenceFinder
            Button SequenceFinderButton = rootView.findViewById(R.id.start_sequencefinder);
            SequenceFinderButton.setOnClickListener(v -> {
                String inputfile = Objects.requireNonNull(inputfilepath.getText()).toString();

                if (!sequenceFinderCmd[0].isEmpty()) {
                    run_cmd(sequenceFinderCmd[0]);
                } else if (!inputfile.isEmpty()) {
                    run_cmd("/opt/car_hacking/sequence_finder.sh " + inputfile);
                } else {
                    showToast("Please ensure your Input File field is set!");
                }

                activity.invalidateOptionsMenu();
            });
            SequenceFinderButton.setOnLongClickListener(v -> {
                String defaultCmd = "/opt/car_hacking/sequence_finder.sh " + Objects.requireNonNull(inputfilepath.getText());
                showEditCommandDialog("Edit SequenceFinder Command", sequenceFinderCmd, "sequenceFinder_cmd", defaultCmd);
                return true;
            });

            // Freediag
            Button FreediagButton = rootView.findViewById(R.id.start_freediag);
            FreediagButton.setOnClickListener(v -> {
                if (!freediagCmd[0].isEmpty()) {
                    run_cmd(freediagCmd[0]);
                } else {
                    run_cmd("sudo -u kali freediag");
                }
                activity.invalidateOptionsMenu();
            });
            FreediagButton.setOnLongClickListener(v -> {
                String defaultCmd = "sudo -u kali freediag";
                showEditCommandDialog("Edit Freediag Command", freediagCmd, "freediag_cmd", defaultCmd);
                return true;
            });

            // diag_test
            Button diagTestButton = rootView.findViewById(R.id.start_diagtest);
            diagTestButton.setOnClickListener(v -> {
                if (!diagTestCmd[0].isEmpty()) {
                    run_cmd(diagTestCmd[0]);
                } else {
                    run_cmd("sudo -u kali diag_test");
                }
                activity.invalidateOptionsMenu();
            });
            diagTestButton.setOnLongClickListener(v -> {
                String defaultCmd = "sudo -u kali diag_test";
                showEditCommandDialog("Edit diag_test Command", diagTestCmd, "diagTest_cmd", defaultCmd);
                return true;
            });

            // Cannelloni
            Button CannelloniButton = rootView.findViewById(R.id.start_cannelloni);
            CannelloniButton.setOnClickListener(v -> {
                if (!cannelloniCmd[0].isEmpty()) {
                    run_cmd(cannelloniCmd[0]);
                } else {
                    String rhost = SelectedRHost.getText().toString().trim();
                    String rport = SelectedRPort.getText().toString().trim();
                    String lport = SelectedLPort.getText().toString().trim();

                    if (selected_caniface.isEmpty() || selected_caniface.equals("Interface (None)")) {
                        showToast("Please select a CAN Interface!");
                        return;
                    }

                    if (rhost.length() != 15) {
                        showToast("RHOST must be exactly 15 characters (e.g., 192.168.111.111)");
                        return;
                    }

                    if (rport.length() != 6 || !rport.matches("\\d+")) {
                        showToast("RPORT must be exactly 6 digits");
                        return;
                    }

                    if (lport.length() != 6 || !lport.matches("\\d+")) {
                        showToast("LPORT must be exactly 6 digits");
                        return;
                    }

                    run_cmd("sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport);
                }
                activity.invalidateOptionsMenu();
            });
            CannelloniButton.setOnLongClickListener(v -> {
                String rhost = SelectedRHost.getText().toString().trim();
                String rport = SelectedRPort.getText().toString().trim();
                String lport = SelectedLPort.getText().toString().trim();
                String defaultCmd = "sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport;
                showEditCommandDialog("Edit Cannelloni Command", cannelloniCmd, "cannelloni_cmd", defaultCmd);
                return true;
            });

            // Asc2Log
            Button Asc2LogButton = rootView.findViewById(R.id.start_asc2log);
            Asc2LogButton.setOnClickListener(v -> {
                String inputfile = Objects.requireNonNull(inputfilepath.getText()).toString();
                String outputfile = Objects.requireNonNull(outputfilepath.getText()).toString();

                if (!asc2logCmd[0].isEmpty()) {
                    run_cmd(asc2logCmd[0]);
                } else if (!inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("asc2log -I " + inputfile + " -O " + outputfile);
                } else {
                    showToast("Please ensure your Input and Output File fields is set!");
                }

                activity.invalidateOptionsMenu();
            });
            Asc2LogButton.setOnLongClickListener(v -> {
                String defaultCmd = "asc2log -I " + Objects.requireNonNull(inputfilepath.getText()) + " -O " + Objects.requireNonNull(outputfilepath.getText());
                showEditCommandDialog("Edit Asc2Log Command", asc2logCmd, "asc2log_cmd", defaultCmd);
                return true;
            });

            // Log2asc
            Button Log2AscButton = rootView.findViewById(R.id.start_log2asc);
            String inputfile = Objects.requireNonNull(inputfilepath.getText()).toString();
            String outputfile = Objects.requireNonNull(outputfilepath.getText()).toString();

            Log2AscButton.setOnClickListener(v -> {
                if (!log2ascCmd[0].isEmpty()) {
                    run_cmd(log2ascCmd[0]);
                } else if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interface (None)") && !inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("log2asc -I " + inputfile + " -O " + outputfile + " " + selected_caniface);
                } else {
                    showToast("Please ensure your CAN Interface, Input and Output File fields is set!");
                }

                activity.invalidateOptionsMenu();
            });
            Log2AscButton.setOnLongClickListener(v -> {
                String defaultCmd = "log2asc -I " + Objects.requireNonNull(inputfilepath.getText()) + " -O " + Objects.requireNonNull(outputfilepath.getText()) + " " + selected_caniface;
                showEditCommandDialog("Edit Log2asc Command", log2ascCmd, "log2asc_cmd", defaultCmd);
                return true;
            });


            // Start CustomCommand
            Button CustomCmdButton = rootView.findViewById(R.id.start_customcmd);

            CustomCmdButton.setOnClickListener(v -> {
                String command = CustomCmd.getText().toString();

                if (!command.isEmpty()) {
                    run_cmd(command);
                } else {
                    showToast("Please ensure your Custom Command field is set!");
                }

                activity.invalidateOptionsMenu();
            });

            return rootView;
        }
        private void showEditCommandDialog(String title, String[] cmdHolder, String prefKey, String defaultCmd) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(title);

            final EditText input = new EditText(requireContext());
            String textToShow = cmdHolder[0].isEmpty() ? defaultCmd : cmdHolder[0];
            input.setText(textToShow);
            builder.setView(input);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String newCmd = input.getText().toString();
                cmdHolder[0] = newCmd;

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(prefKey, newCmd);
                editor.apply();

                showToast("Command updated!");
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
            });
            dialog.show();
        }
    }

    public static class CANUSBFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Activity activity;
        private boolean isDebugEnabled = false;
        private EditText SelectedBaudrateUSB;
        private EditText SelectedCanSpeedUSB;
        private String selected_usb;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_canusb, container, false);

            SelectedBaudrateUSB = rootView.findViewById(R.id.baudrate_usb);
            SelectedCanSpeedUSB = rootView.findViewById(R.id.canspeed_usb);

            // Devices Interfaces
            Spinner spinner = rootView.findViewById(R.id.device_interface);
            ImageButton refreshBtn = rootView.findViewById(R.id.refreshUSB);

            SpinnerUtils.setupDeviceInterfaceSpinner(
                    requireContext(),
                    executorService,
                    exe,
                    spinner,
                    refreshBtn,
                    sharedpreferences,
                    "selected_usb",
                    true,
                    iface -> selected_usb = iface
            );

            // Can-Usb Mode Spinner
            final Spinner canusbModeList = rootView.findViewById(R.id.usb_mode_spinner);
            ArrayAdapter<String> adapter = getStringArrayAdapter();
            canusbModeList.setAdapter(adapter);
            canusbModeList.setSelection(0);  // Set initial selection to "Mode"

            canusbModeList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    if (pos != 0) { // Ignore "Mode" hint
                        String canusbmode_selected = parentView.getItemAtPosition(pos).toString();
                        sharedpreferences.edit().putString("canusbmode_selected", canusbmode_selected).apply();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                    // Do nothing
                }
            });

            // Toggle Buttons
            // Counter
            Button btnCounter = rootView.findViewById(R.id.btn_toggle_usb_counter);
            TextInputLayout counterContainer = rootView.findViewById(R.id.counter_container);

            btnCounter.setOnClickListener(v -> {
                boolean visible = counterContainer.getVisibility() == View.VISIBLE;
                counterContainer.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnCounter.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Data
            Button btnData = rootView.findViewById(R.id.btn_toggle_usb_data);
            TextInputLayout dataContainer = rootView.findViewById(R.id.data_container);

            btnData.setOnClickListener(v -> {
                boolean visible = dataContainer.getVisibility() == View.VISIBLE;
                dataContainer.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnData.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // ID
            Button btnID = rootView.findViewById(R.id.btn_toggle_usb_id);
            TextInputLayout idContainer = rootView.findViewById(R.id.id_container);

            btnID.setOnClickListener(v -> {
                boolean visible = idContainer.getVisibility() == View.VISIBLE;
                idContainer.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnID.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });


            // Mode
            Button btnMode = rootView.findViewById(R.id.btn_toggle_usb_mode);
            View modeContainer = rootView.findViewById(R.id.usb_mode_container);

            btnMode.setOnClickListener(v -> {
                boolean visible = modeContainer.getVisibility() == View.VISIBLE;
                modeContainer.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnMode.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Sleep
            Button btnSleep = rootView.findViewById(R.id.btn_toggle_usb_sleep);
            TextInputLayout sleepContainer = rootView.findViewById(R.id.sleep_container);

            btnSleep.setOnClickListener(v -> {
                boolean visible = sleepContainer.getVisibility() == View.VISIBLE;
                sleepContainer.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnSleep.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Debug (TTY Output) Switch
            SwitchCompat btnDebug = rootView.findViewById(R.id.btn_toggle_usb_ttyOutput);

            btnDebug.setChecked(isDebugEnabled);
            btnDebug.setTextColor(ContextCompat.getColor(requireContext(),
                    isDebugEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light));

            btnDebug.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isDebugEnabled = isChecked;

                int colorRes = isDebugEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnDebug.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
            });

            // Start USB-CAN
            Button USBCanSendButton = rootView.findViewById(R.id.start_canusb_send);

            USBCanSendButton.setOnClickListener(v -> {
                String USBCANSpeed = SelectedCanSpeedUSB.getText().toString();
                String USBBaudrate = SelectedBaudrateUSB.getText().toString();
                String debugEnabled = isDebugEnabled ? " -t" : "";
                String countValue = getVisibleParam(counterContainer.getEditText(), " -n ");
                String idValue = getVisibleParam(idContainer.getEditText(), " -i ");
                String dataValue = getVisibleParam(dataContainer.getEditText(), " -j ");
                String sleepValue = getVisibleParam(sleepContainer.getEditText(), " -g ");

                String modeValue = "";
                if (modeContainer.getVisibility() == View.VISIBLE) {
                    String selected = canusbModeList.getSelectedItem().toString().trim();
                    if (!selected.isEmpty() && !selected.equals("Mode")) {
                        modeValue = " -m " + selected;
                    }
                }

                if (!selected_usb.isEmpty() && !selected_usb.equals("USB Device (None)") && !USBCANSpeed.isEmpty() && !USBBaudrate.isEmpty()) {
                    run_cmd("canusb -d " + selected_usb + " -s " + USBCANSpeed + " -b " + USBBaudrate + debugEnabled + idValue + dataValue + sleepValue + countValue + modeValue);
                } else {
                    showToast("Please ensure your USB Device and USB CAN Speed, Baudrate, Data fields is set!");
                }

                activity.invalidateOptionsMenu();
            });

            return rootView;
        }

        @NonNull
        private ArrayAdapter<String> getStringArrayAdapter() {
            final String[] modeOptions = {"Mode", "0", "1", "2"};

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, modeOptions) {
                @Override
                public boolean isEnabled(int position) {
                    // Disable "Mode" item
                    return position != 0;
                }

                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    if (position == 0) {
                        tv.setTextColor(Color.GRAY);  // Hint text color
                    } else {
                        tv.setTextColor(Color.WHITE); // Normal text
                    }
                    return view;
                }
            };

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return adapter;
        }

        private String getVisibleParam(View view, String prefix) {
            if (view != null && view.getVisibility() == View.VISIBLE) {
                if (view instanceof EditText) {
                    String input = ((EditText) view).getText().toString().trim();
                    if (!input.isEmpty()) {
                        return prefix + input;
                    }
                } else if (view instanceof Spinner) {
                    String selected = ((Spinner) view).getSelectedItem().toString().trim();
                    if (!selected.isEmpty()) {
                        return prefix + selected;
                    }
                }
            }
            return "";
        }
    }

    public static class CANCARIBOUFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Activity activity;

        private EditText SelectedFile;
        private EditText SelectedMessage;
        private EditText selectedAddr;
        private EditText selectedLength;
        private EditText selectedSeed;
        private EditText selectedID;
        private EditText selectedSrc;
        private EditText selectedDst;
        private EditText selectedMin;
        private EditText selectedMax;
        private EditText selectedDelay;
        private EditText selectedSeparateLine;

        private boolean isPadEnabled = false;
        private boolean isCandumpEnabled = false;
        private boolean isOutputEnabled = false;
        private boolean isLoopEnabled = false;
        private boolean isReverseEnabled = false;
        private String selected_caniface = "";

        private ArrayAdapter<String> createDisabledFirstItemAdapter(String[] items) {
            return new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, items) {
                @Override
                public boolean isEnabled(int position) {
                    return position != 0;
                }

                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    if (position == 0) {
                        tv.setTextColor(Color.GRAY);
                    } else {
                        tv.setTextColor(Color.WHITE);
                    }
                    return view;
                }
            };
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_caribou, container, false);

            SelectedFile = rootView.findViewById(R.id.caribou_file);
            SelectedMessage = rootView.findViewById(R.id.caribou_message);
            selectedAddr = rootView.findViewById(R.id.start_addr_value);
            selectedLength = rootView.findViewById(R.id.length_value);
            selectedSeed = rootView.findViewById(R.id.seed_value);
            selectedID = rootView.findViewById(R.id.id_value);
            selectedSrc = rootView.findViewById(R.id.src_value);
            selectedDst = rootView.findViewById(R.id.dst_value);
            selectedMin = rootView.findViewById(R.id.min_value);
            selectedMax = rootView.findViewById(R.id.max_value);
            selectedDelay = rootView.findViewById(R.id.delay_value);
            selectedSeparateLine = rootView.findViewById(R.id.separate_line_value);

            setupBooleanToggle(rootView.findViewById(R.id.btn_toggle_pad), val -> isPadEnabled = val, isPadEnabled);
            setupBooleanToggle(rootView.findViewById(R.id.btn_toggle_candump), val -> isCandumpEnabled = val, isCandumpEnabled);
            setupBooleanToggle(rootView.findViewById(R.id.btn_toggle_output), val -> isOutputEnabled = val, isOutputEnabled);
            setupBooleanToggle(rootView.findViewById(R.id.btn_toggle_loop), val -> isLoopEnabled = val, isLoopEnabled);
            setupBooleanToggle(rootView.findViewById(R.id.btn_toggle_reverse), val -> isReverseEnabled = val, isReverseEnabled);

            // Interfaces
            Spinner spinner = rootView.findViewById(R.id.device_interface);
            ImageButton refreshBtn = rootView.findViewById(R.id.refreshUSB);

            SpinnerUtils.setupDeviceInterfaceSpinner(
                    requireContext(),
                    executorService,
                    exe,
                    spinner,
                    refreshBtn,
                    sharedpreferences,
                    "selected_usb",
                    false,
                    iface -> selected_caniface = iface
            );

            // Browse File
            MaterialButton browseButton = rootView.findViewById(R.id.cariboufilebrowse);
            @SuppressLint("CutPasteId") TextInputEditText fileEditText = rootView.findViewById(R.id.caribou_file);
            browseButton.setOnClickListener(v -> {
                RootFileBrowserDialog dialog = new RootFileBrowserDialog(requireContext(), fileEditText::setText);
                dialog.show();
            });

            // Advanced Options Toggle
            Button btnToggle = rootView.findViewById(R.id.btn_toggle_advanced);
            LinearLayout advancedOptionsLayout = rootView.findViewById(R.id.caribou_advanced_options);
            btnToggle.setOnClickListener(v -> {
                if (advancedOptionsLayout.getVisibility() == View.GONE) {
                    advancedOptionsLayout.setVisibility(View.VISIBLE);
                    btnToggle.setText(R.string.can_hide_advanced_options);
                } else {
                    advancedOptionsLayout.setVisibility(View.GONE);
                    btnToggle.setText(R.string.can_advanced_options);
                }
            });


            // Advanced Options Buttons
            setupParamToggle(rootView, R.id.btn_toggle_start_addr, R.id.start_addr_container);
            setupParamToggle(rootView, R.id.btn_toggle_length, R.id.length_container);
            setupParamToggle(rootView, R.id.btn_toggle_separateLine, R.id.separate_line_container);
            setupParamToggle(rootView, R.id.btn_toggle_seed, R.id.seed_container);
            setupParamToggle(rootView, R.id.btn_toggle_id, R.id.id_container);
            setupParamToggle(rootView, R.id.btn_toggle_src, R.id.src_container);
            setupParamToggle(rootView, R.id.btn_toggle_dst, R.id.dst_container);
            setupParamToggle(rootView, R.id.btn_toggle_min, R.id.min_container);
            setupParamToggle(rootView, R.id.btn_toggle_max, R.id.max_container);
            setupParamToggle(rootView, R.id.btn_toggle_delay, R.id.delay_container);

            // Dump
            rootView.findViewById(R.id.start_dump).setOnClickListener(v -> {
                String candumpFormat = isCandumpEnabled ? " -t" : "";
                String outputEnabled = isOutputEnabled ? " -f " + SelectedFile.getText().toString() : "";
                String separateLineValue = getVisibleParam(selectedSeparateLine, " -s ");
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interface (None)")) {
                    run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " dump" + separateLineValue + candumpFormat + outputEnabled);
                } else {
                    showToast("Please choose a CAN Interface!");
                }
                activity.invalidateOptionsMenu();
            });

            // Listener
            rootView.findViewById(R.id.start_listener).setOnClickListener(v -> {
                String reverseEnabled = isReverseEnabled ? " -r" : "";
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interface (None)")) {
                    run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " listener" + reverseEnabled);
                } else {
                    showToast("Please choose a CAN Interface!");
                }
                activity.invalidateOptionsMenu();
            });

            // Module and SubModule spinners
            final Spinner moduleSpinner = rootView.findViewById(R.id.module_spinner);
            final Spinner subModuleSpinner = rootView.findViewById(R.id.submodule_spinner);
            final Button startButton = rootView.findViewById(R.id.start_button);

            final String[] modules = {"Modules", "Fuzz", "Send", "UDS", "XCP"};
            final Map<String, String[]> subModulesMap = new HashMap<>();
            subModulesMap.put("Fuzz", new String[]{"Sub-Modules", "brute", "identify", "mutate", "random", "replay"});
            subModulesMap.put("Send", new String[]{"Sub-Modules", "file", "message"});
            subModulesMap.put("UDS", new String[]{"Sub-Modules", "discovery", "services"});
            subModulesMap.put("XCP", new String[]{"Sub-Modules", "discovery", "info", "dump"});

            ArrayAdapter<String> moduleAdapter = createDisabledFirstItemAdapter(modules);
            moduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            moduleSpinner.setAdapter(moduleAdapter);

            ArrayAdapter<String> emptySubModuleAdapter = createDisabledFirstItemAdapter(new String[]{"Sub-Modules"});
            emptySubModuleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            subModuleSpinner.setAdapter(emptySubModuleAdapter);

            moduleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedModule = modules[position];
                    if (subModulesMap.containsKey(selectedModule)) {
                        ArrayAdapter<String> subAdapter = createDisabledFirstItemAdapter(subModulesMap.get(selectedModule));
                        subAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        subModuleSpinner.setAdapter(subAdapter);
                        if (subAdapter.getCount() > 1) {
                            subModuleSpinner.setSelection(1);
                        } else {
                            subModuleSpinner.setSelection(0);
                        }
                    } else {
                        subModuleSpinner.setAdapter(emptySubModuleAdapter);
                        if (emptySubModuleAdapter.getCount() > 1) {
                            subModuleSpinner.setSelection(1);
                        } else {
                            subModuleSpinner.setSelection(0);
                        }
                    }
                }

                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });

            startButton.setOnClickListener(v -> {
                String module = (String) moduleSpinner.getSelectedItem();
                String subModule = (String) subModuleSpinner.getSelectedItem();

                if ("Modules".equals(module) || "Sub-Modules".equals(subModule)) {
                    showToast("Please select a Module and Sub-Module.");
                    return;
                }

                switch (module) {
                    case "Fuzz":
                        runFuzzer(subModule);
                        break;
                    case "Send":
                        runSend(subModule);
                        break;
                    case "UDS":
                        runUDS(subModule);
                        break;
                    case "XCP":
                        runXCP(subModule);
                        break;
                    default:
                        showToast("Unknown module selected.");
                }
            });

            return rootView;
        }

        private void setupParamToggle(View rootView, int toggleButtonId, int containerLayoutId) {
            Button toggleBtn = rootView.findViewById(toggleButtonId);
            TextInputLayout container = rootView.findViewById(containerLayoutId);

            toggleBtn.setOnClickListener(v -> {
                boolean visible = container.getVisibility() == View.VISIBLE;
                container.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                toggleBtn.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });
        }

        private String getVisibleParam(EditText editText, String prefix) {
            if (editText.getVisibility() == View.VISIBLE) {
                String val = editText.getText().toString().trim();
                if (!val.isEmpty()) {
                    return prefix + val;
                }
            }
            return "";
        }

        private void setupBooleanToggle(SwitchCompat toggleSwitch, Consumer<Boolean> flagSetter, boolean initialValue) {
            toggleSwitch.setChecked(initialValue);
            int initialColorRes = initialValue ? android.R.color.holo_green_light : android.R.color.holo_red_light;
            toggleSwitch.setTextColor(ContextCompat.getColor(requireContext(), initialColorRes));

            toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                flagSetter.accept(isChecked);
                int colorRes = isChecked ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                toggleSwitch.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
            });
        }

        private void runFuzzer(String fuzzer_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interface (None)")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String idValue = getVisibleParam(selectedID, " ");
            String minValue = getVisibleParam(selectedMin, " -min ");
            String outputEnabled = isOutputEnabled ? " -f " + SelectedFile.getText().toString() : "";
            String seedValue = getVisibleParam(selectedSeed, " --seed ");

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " fuzzer ";

            switch (fuzzer_module) {
                case "brute":
                    run_cmd(cmdBase + "brute" + idValue);
                    break;
                case "identify":
                    run_cmd(cmdBase + "identify" + outputEnabled);
                    break;
                case "mutate":
                    run_cmd(cmdBase + "mutate" + idValue);
                    break;
                case "random":
                    run_cmd(cmdBase + "random" + minValue + seedValue + outputEnabled);
                    break;
                case "replay":
                    run_cmd(cmdBase + "replay" + outputEnabled);
                    break;
                default:
                    showToast("Unknown fuzzer submodule: " + fuzzer_module);
            }
        }

        private void runSend(String send_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interface (None)")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String selected_message = SelectedMessage.getText().toString();
            String selected_file = SelectedFile.getText().toString();
            String delayValue = getVisibleParam(selectedDelay, " -d ");
            String loopEnabled = isLoopEnabled ? " -l" : "";
            String padEnabled = isPadEnabled ? " -p" : "";

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " send ";

            switch (send_module) {
                case "file":
                    run_cmd(cmdBase + "file" + delayValue + loopEnabled + " " + selected_file);
                    break;
                case "message":
                    run_cmd(cmdBase + "message" + padEnabled + delayValue + loopEnabled + " " + selected_message);
                    break;
                default:
                    showToast("Unknown send submodule: " + send_module);
            }
        }

        private void runUDS(String uds_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interface (None)")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String srcValue = getVisibleParam(selectedSrc, " ");
            String dstValue = getVisibleParam(selectedDst, " ");
            String minValue = getVisibleParam(selectedMin, " -min ");
            String maxValue = getVisibleParam(selectedMax, " -max ");
            String delayValue = getVisibleParam(selectedDelay, " -d ");

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " uds ";

            switch (uds_module) {
                case "discovery":
                    run_cmd(cmdBase + "discovery" + minValue + maxValue + delayValue);
                    break;
                case "services":
                    run_cmd(cmdBase + "services" + srcValue + dstValue);
                    break;
                default:
                    showToast("Unknown UDS submodule: " + uds_module);
            }
        }

        private void runXCP(String xcp_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interface (None)")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String addrValue = getVisibleParam(selectedAddr, " ");
            String lengthValue = getVisibleParam(selectedLength, " ");
            String outputEnabled = isOutputEnabled ? " -f " + SelectedFile.getText().toString() : "";
            String srcValue = getVisibleParam(selectedSrc, " ");
            String dstValue = getVisibleParam(selectedDst, " ");
            String minValue = getVisibleParam(selectedMin, " -min ");
            String maxValue = getVisibleParam(selectedMax, " -max ");

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " xcp ";

            switch (xcp_module) {
                case "discovery":
                    run_cmd(cmdBase + "discovery" + outputEnabled + srcValue + dstValue);
                    break;
                case "info":
                    run_cmd(cmdBase + "info" + addrValue + lengthValue);
                    break;
                case "dump":
                    run_cmd(cmdBase + "dump" + addrValue + lengthValue + minValue + maxValue + outputEnabled);
                    break;
                default:
                    showToast("Unknown XCP submodule: " + xcp_module);
            }
        }
    }


    public static class CANICSIMFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private static final String ICSIM_SCRIPT_PATH = "/opt/car_hacking/icsim_service.sh";
        private static final long SHORT_DELAY = 1000;
        private static final long LONG_DELAY = 2000;
        private String selected_caniface;


        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_icsim, container, false);

            // Interfaces
            Spinner spinner = rootView.findViewById(R.id.device_interface);
            ImageButton refreshBtn = rootView.findViewById(R.id.refreshUSB);

            SpinnerUtils.setupDeviceInterfaceSpinner(
                    requireContext(),
                    executorService,
                    exe,
                    spinner,
                    refreshBtn,
                    sharedpreferences,
                    "selected_usb",
                    false,
                    iface -> selected_caniface = iface
            );

            // Configuration Toggle
            Button btnConfigurationToggle = rootView.findViewById(R.id.btn_toggle_config_icsim);
            LinearLayout configurationLayout = rootView.findViewById(R.id.icsim_configuration);

            btnConfigurationToggle.setOnClickListener(v -> {
                if (configurationLayout.getVisibility() == View.GONE) {
                    configurationLayout.setVisibility(View.VISIBLE);
                    btnConfigurationToggle.setText(R.string.can_hide_configuration);
                } else {
                    configurationLayout.setVisibility(View.GONE);
                    btnConfigurationToggle.setText(R.string.can_configuration);
                }
            });

            // Level Spinner
            // 0 = No randomization added to the packets other than location and ID
            // 1 = Add NULL padding
            // 2 = Randomize unused bytes
            final Spinner levelList = rootView.findViewById(R.id.level_spinner);
            ArrayAdapter<String> adapter = getStringArrayAdapter();
            levelList.setAdapter(adapter);
            levelList.setSelection(0);  // Set initial selection to "Level"

            levelList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    if (pos != 0) { // Ignore "Mode" hint
                        String level_selected = parentView.getItemAtPosition(pos).toString();
                        sharedpreferences.edit().putString("level_selected", level_selected).apply();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                    // Do nothing
                }
            });

            // Level
            Button btnLevel = rootView.findViewById(R.id.btn_toggle_level);
            View levelContainer = rootView.findViewById(R.id.level_container);

            btnLevel.setOnClickListener(v -> {
                boolean visible = levelContainer.getVisibility() == View.VISIBLE;
                levelContainer.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnLevel.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // ICSIM
            Button runICSIM = rootView.findViewById(R.id.run_icsim);
            runICSIM.setOnClickListener(v -> {
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interface (None)")) {
                    // String randomizeEnabled = isRandomizeEnabled ? " -r" : "";
                    String levelValue = getVisibleParam(levelList);
                    run_cmd("su -c 'sh " + ICSIM_SCRIPT_PATH + " " + selected_caniface + levelValue + "'");
                    showToast("Running ICSim...");
                    new Handler().postDelayed(() -> {
                        WebView icsimView = rootView.findViewById(R.id.icsim);
                        WebView controlsView = rootView.findViewById(R.id.controls);

                        for (WebView view : new WebView[]{icsimView, controlsView}) {
                            WebSettings settings = view.getSettings();
                            settings.setJavaScriptEnabled(true);
                            settings.setDomStorageEnabled(true);
                            settings.setLoadWithOverviewMode(true);
                            settings.setUseWideViewPort(true);
                            settings.setBuiltInZoomControls(true);
                            settings.setDisplayZoomControls(false);
                            view.setWebViewClient(new WebViewClient());
                        }

                        icsimView.loadUrl("http://localhost:6080/vnc.html?autoconnect=true&resize=scale");
                        controlsView.loadUrl("http://localhost:6081/vnc.html?autoconnect=true&resize=scale");

                    }, SHORT_DELAY + LONG_DELAY);
                } else {
                    showToast("Please set a CAN interface!");
                }
            });

            Button stopICSIM = rootView.findViewById(R.id.stop_icsim);
            stopICSIM.setOnClickListener(v -> {
                WebView icsimView = rootView.findViewById(R.id.icsim);
                WebView controlsView = rootView.findViewById(R.id.controls);

                run_cmd("su -c 'sh " + ICSIM_SCRIPT_PATH + " stop'");
                showToast("Stopping ICSim...");
                icsimView.setBackgroundColor(Color.BLACK);
                icsimView.loadUrl("about:blank");
                controlsView.setBackgroundColor(Color.BLACK);
                controlsView.loadUrl("about:blank");
            });

            return rootView;
        }

        @NonNull
        private ArrayAdapter<String> getStringArrayAdapter() {
            final String[] levelOptions = {"Level", "0", "1", "2"};

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, levelOptions) {
                @Override
                public boolean isEnabled(int position) {
                    // Disable "Level" item
                    return position != 0;
                }

                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    if (position == 0) {
                        tv.setTextColor(Color.GRAY);  // Hint text color
                    } else {
                        tv.setTextColor(Color.WHITE); // Normal text
                    }
                    return view;
                }
            };

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return adapter;
        }

        private String getVisibleParam(View view) {
            if (view.getVisibility() == View.VISIBLE) {
                if (view instanceof EditText) {
                    String input = ((EditText) view).getText().toString().trim();
                    if (!input.isEmpty()) {
                        return " -l " + input;
                    }
                } else if (view instanceof Spinner) {
                    String selected = ((Spinner) view).getSelectedItem().toString().trim();
                    if (!selected.isEmpty()) {
                        return " -l " + selected;
                    }
                }
            }
            return "";
        }
    }

    public static class CANMSFFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Context context;
        private String selected_caniface;
        private String selected_module;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
        }

        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_msf, container, false);

            final EditText selected_baud = rootView.findViewById(R.id.baud_speed);

            // Interfaces
            Spinner spinner = rootView.findViewById(R.id.device_interface);
            ImageButton refreshBtn = rootView.findViewById(R.id.refreshUSB);

            SpinnerUtils.setupDeviceInterfaceSpinner(
                    requireContext(),
                    executorService,
                    exe,
                    spinner,
                    refreshBtn,
                    sharedpreferences,
                    "selected_usb",
                    false,
                    iface -> selected_caniface = iface
            );

            // ELM327 Configuration Toggle
            Button btnConfigurationToggle = rootView.findViewById(R.id.btn_toggle_relay);
            LinearLayout configurationLayout = rootView.findViewById(R.id.msf_elmconfig);

            btnConfigurationToggle.setOnClickListener(v -> {
                if (configurationLayout.getVisibility() == View.GONE) {
                    configurationLayout.setVisibility(View.VISIBLE);
                    btnConfigurationToggle.setText(R.string.can_hide_configuration);
                } else {
                    configurationLayout.setVisibility(View.GONE);
                    btnConfigurationToggle.setText(R.string.can_elm327_relay_configuration);
                }
            });

            // ELM327 Relay
            Button elm327relayButton = rootView.findViewById(R.id.run_relay);

            elm327relayButton.setOnClickListener(v -> {
                String baudSpeed = selected_baud.getText().toString();

                String baudValue;
                if (!baudSpeed.isEmpty()) {
                    baudValue = " -b " + baudSpeed;
                } else {
                    baudValue = "";
                }

                run_cmd("/usr/share/metasploit-framework/tools/hardware/elm327_relay.rb -s " + selected_caniface + baudValue);
            });

            // Modules
            final Spinner modulesList = rootView.findViewById(R.id.msf_modules_spinner);

            executorService.submit(() -> {
                String result = exe.RunAsChrootOutput(
                        "basename /usr/share/metasploit-framework/modules/auxiliary/server/local_hwbridge.rb && " +
                                 "basename /usr/share/metasploit-framework/modules/auxiliary/client/hwbridge/connect.rb && " +
                                 "ls /usr/share/metasploit-framework/modules/post/hardware/automotive/"
                );

                ArrayList<String> module = new ArrayList<>();
                module.add("MSF Modules"); // Non-selectable header

                if (result == null || result.trim().isEmpty()) {
                    module.add("Module (None)");
                } else {
                    module.addAll(Arrays.asList(result.split("\n")));
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, module) {
                        @Override
                        public boolean isEnabled(int position) {
                            return position != 0; // Disable the first item
                        }

                        @Override
                        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                            View view = super.getDropDownView(position, convertView, parent);
                            TextView tv = (TextView) view;
                            tv.setTextColor(position == 0 ? Color.GRAY : Color.WHITE);
                            return view;
                        }
                    };

                    modulesList.setAdapter(adapter);

                    modulesList.setSelection(0);  // Set initial selection to "MSF Modules"

                    modulesList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            if (pos != 0) {
                                selected_module = parentView.getItemAtPosition(pos).toString();
                                sharedpreferences.edit().putInt("selected_module", pos).apply();
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            selected_module = "Module (None)";
                        }
                    });
                });
            });

            TextView infoText = rootView.findViewById(R.id.module_info_text);
            LinearLayout optionsContainer = rootView.findViewById(R.id.module_options_container);

            // Info button
            Button infoBtn = rootView.findViewById(R.id.info_module);
            infoBtn.setOnClickListener(v -> {
                if (selected_module == null || selected_module.equals("Module (None)")) {
                    showToast("Select a module first");
                    return;
                }

                String moduleNameKey = selected_module.replace(".rb", "").toLowerCase();
                String resourceKey = "module_info_" + moduleNameKey;

                int resId = getResources().getIdentifier(resourceKey, "string", requireContext().getPackageName());

                if (resId == 0) {
                    showToast("No info available for this module");
                    return;
                }

                String moduleInfo = getString(resId);

                // Build popup dialog
                new Handler(Looper.getMainLooper()).post(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Module Information");
                    builder.setMessage(moduleInfo);
                    builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
                    AlertDialog dialog = builder.create();
                    dialog.show();

                    Button closeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (closeButton != null) {
                        closeButton.setTextColor(Color.WHITE);
                    }
                });
            });

            // Set button
            Button setBtn = rootView.findViewById(R.id.set_module);
            setBtn.setOnClickListener(v -> {
                if (selected_module == null || selected_module.equals("Module (None)")) {
                    showToast("Select a module first");
                    return;
                }

                infoText.setVisibility(View.GONE);

                int optionsStringId = getResources().getIdentifier(
                        "module_set_" + selected_module.replace(".rb", "").toLowerCase().trim(),
                        "string",
                        requireContext().getPackageName());

                if (optionsStringId == 0) {
                    infoText.setVisibility(View.VISIBLE);
                    infoText.setText(R.string.can_no_options_for_module);
                    return;
                }

                String optionsText = getString(optionsStringId);
                String[] optionLines = optionsText.split("\n");

                LinearLayout inputLayout = new LinearLayout(requireContext());
                inputLayout.setOrientation(LinearLayout.VERTICAL);
                inputLayout.setPadding(20, 20, 20, 20);

                Map<String, EditText> userInputs = new LinkedHashMap<>();

                for (String line : optionLines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (!line.contains("|")) {
                        TextView header = new TextView(requireContext());
                        header.setText(line);
                        header.setTextAppearance(requireContext(), android.R.style.TextAppearance_Medium);
                        header.setTypeface(null, Typeface.BOLD);
                        header.setPadding(0, 30, 0, 30);

                        inputLayout.addView(header);
                        continue;
                    }

                    String[] parts = line.split("\\|");
                    String name = parts.length > 0 ? parts[0].trim() : "";
                    String defaultVal = parts.length > 1 ? parts[1].trim() : "";
                    String required = parts.length > 2 ? parts[2].trim() : "optional";

                    if (name.isEmpty()) continue;

                    TextView label = new TextView(requireContext());
                    label.setText(String.format("%s (%s)", name, required));
                    label.setTextSize(14);
                    label.setPadding(0, 10, 0, 4);

                    EditText input = new EditText(requireContext());
                    input.setHint("Enter " + name);
                    input.setText(defaultVal);
                    input.setTag(name);
                    input.setTextSize(14);

                    inputLayout.addView(label);
                    inputLayout.addView(input);

                    userInputs.put(name, input);
                }

                optionsContainer.removeAllViews();
                optionsContainer.addView(inputLayout);
                optionsContainer.setVisibility(View.VISIBLE);
                optionsContainer.setTag(userInputs);
            });

            Button msfBtn = rootView.findViewById(R.id.msfconsole_start);
            msfBtn.setOnClickListener(v -> executorService.submit(() -> {
                run_cmd("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n); "
                        + "if [ -n \"$msfsession\" ]; then "
                        + "screen -wipe; screen -d \"$msfsession\"; screen -r \"$msfsession\"; "
                        + "else screen -wipe; screen -S msf -m msfconsole;exit; fi");
            }));

            Button runBtn = rootView.findViewById(R.id.run_module);
            runBtn.setOnClickListener(v -> {
                if (selected_module == null || selected_module.equals("Module (None)")) {
                    showToast("Select a module first");
                    return;
                }

                @SuppressWarnings("unchecked")
                Map<String, EditText> userInputs = (Map<String, EditText>) optionsContainer.getTag();

                if (userInputs == null || userInputs.isEmpty()) {
                    showToast("Please press Set and fill options first.");
                    return;
                }

                StringBuilder msfCmd = new StringBuilder();
                String moduleName = selected_module.replace(".rb", "");
                if (moduleName.equals("connect")) {
                    msfCmd.append("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n);screen -S $msfsession -X stuff \"use auxiliary/client/hwbridge/")
                            .append(moduleName)
                            .append("`echo -ne '\\015'`");
                } else if (moduleName.equals("local_hwbridge")) {
                    msfCmd.append("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n);screen -S $msfsession -X stuff \"use auxiliary/server/")
                            .append(moduleName)
                            .append("`echo -ne '\\015'`");
                } else {
                    msfCmd.append("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n);screen -S $msfsession -X stuff \"use post/hardware/automotive/")
                            .append(moduleName)
                            .append("`echo -ne '\\015'`");
                }

                for (Map.Entry<String, EditText> entry : userInputs.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue().getText().toString().trim();

                    if (!value.isEmpty()) {
                        String sanitized = value.replace("'", "'\"'\"'");
                        msfCmd.append("set ").append(key.toUpperCase()).append(" '").append(sanitized).append("'`echo -ne '\\015'`");
                    }
                }

                msfCmd.append("run\"`echo -ne '\\015'`;screen -d -r $msfsession;exit");

                executorService.submit(() -> {
                    run_cmd(msfCmd.toString());
                });
            });

            return rootView;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    // Simplified Toast function
    public void showToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(requireActivity().getApplicationContext(), message, Toast.LENGTH_LONG);
        currentToast.show();
    }

    ////
    // Bridge side functions
    ////

    public String run_cmd(String cmd) {
        @SuppressLint("SdCardPath") Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
        intent.putExtra("output", cmd);
        return "Command executed: " + cmd;
    }
}
