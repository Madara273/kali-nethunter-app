package com.offsec.nethunter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.card.MaterialCardView;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.lang.ref.WeakReference;

import androidx.core.widget.TextViewCompat;

public class CARsenalFragment extends Fragment {
    private static final String TAG = "CANFragment";
    private static SharedPreferences sharedpreferences;
    private Activity activity;
    private Toast currentToast;
    private static final String ARG_SECTION_NUMBER = "section_number";

    // Map module keys (derived from selected_module without .rb, lowercased) to string resources
    private static final Map<String, Integer> MODULE_INFO_STRING_IDS = new HashMap<>();
    private static final Map<String, Integer> MODULE_SET_STRING_IDS = new HashMap<>();
    static {
        // Info strings
        MODULE_INFO_STRING_IDS.put("local_hwbridge", R.string.module_info_local_hwbridge);
        MODULE_INFO_STRING_IDS.put("connect", R.string.module_info_connect);
        MODULE_INFO_STRING_IDS.put("can_flood", R.string.module_info_can_flood);
        MODULE_INFO_STRING_IDS.put("canprobe", R.string.module_info_canprobe);
        MODULE_INFO_STRING_IDS.put("diagnostic_state", R.string.module_info_diagnostic_state);
        MODULE_INFO_STRING_IDS.put("ecu_hard_reset", R.string.module_info_ecu_hard_reset);
        MODULE_INFO_STRING_IDS.put("getvinfo", R.string.module_info_getvinfo);
        MODULE_INFO_STRING_IDS.put("identifymodules", R.string.module_info_identifymodules);
        MODULE_INFO_STRING_IDS.put("malibu_overheat", R.string.module_info_malibu_overheat);
        MODULE_INFO_STRING_IDS.put("mazda_ic_mover", R.string.module_info_mazda_ic_mover);
        MODULE_INFO_STRING_IDS.put("pdt", R.string.module_info_pdt);

        // Options strings
        MODULE_SET_STRING_IDS.put("local_hwbridge", R.string.module_set_local_hwbridge);
        MODULE_SET_STRING_IDS.put("connect", R.string.module_set_connect);
        MODULE_SET_STRING_IDS.put("can_flood", R.string.module_set_can_flood);
        MODULE_SET_STRING_IDS.put("canprobe", R.string.module_set_canprobe);
        MODULE_SET_STRING_IDS.put("diagnostic_state", R.string.module_set_diagnostic_state);
        MODULE_SET_STRING_IDS.put("ecu_hard_reset", R.string.module_set_ecu_hard_reset);
        MODULE_SET_STRING_IDS.put("getvinfo", R.string.module_set_getvinfo);
        MODULE_SET_STRING_IDS.put("identifymodules", R.string.module_set_identifymodules);
        MODULE_SET_STRING_IDS.put("malibu_overheat", R.string.module_set_malibu_overheat);
        MODULE_SET_STRING_IDS.put("mazda_ic_mover", R.string.module_set_mazda_ic_mover);
        MODULE_SET_STRING_IDS.put("pdt", R.string.module_set_pdt);
    }

    public static CARsenalFragment newInstance(int sectionNumber) {
        CARsenalFragment fragment = new CARsenalFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        sharedpreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        super.onCreate(savedInstanceState);
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.carsenal, container, false);
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
                        case 4: tab.setText("Simulator"); break;
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
                menuInflater.inflate(R.menu.carsenal, menu);

                // Settings button dynamically for Main tab
                MenuItem settingsItem = menu.findItem(R.id.action_settings);
                if (settingsItem == null) {
                    settingsItem = menu.add(Menu.NONE, R.id.action_settings, Menu.NONE, "Settings");
                    settingsItem.setIcon(R.drawable.ic_settings);
                    settingsItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }

                // ICSIM buttons dynamically for ICSIM tab
                MenuItem controllerItem = menu.findItem(R.id.action_controller);
                if (controllerItem == null) {
                    controllerItem = menu.add(Menu.NONE, R.id.action_controller, Menu.NONE, "Controller");
                    controllerItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
                MenuItem playItem = menu.findItem(R.id.action_play);
                if (playItem == null) {
                    playItem = menu.add(Menu.NONE, R.id.action_play, Menu.NONE, "Play");
                    playItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }

                MenuItem stopItem = menu.findItem(R.id.action_stop);
                if (stopItem == null) {
                    stopItem = menu.add(Menu.NONE, R.id.action_stop, Menu.NONE, "Stop");
                    stopItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }

                MenuItem floatingItem = menu.findItem(R.id.action_floating);
                if (floatingItem == null) {
                    floatingItem = menu.add(Menu.NONE, R.id.action_floating, Menu.NONE, "Floating");
                    floatingItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                ViewPager2 mViewPager = requireView().findViewById(R.id.pagerCAN);
                int currentTab = mViewPager.getCurrentItem();

                // Settings visible only on Main tab
                MenuItem settingsItem = menu.findItem(R.id.action_settings);
                if (settingsItem != null) settingsItem.setVisible(currentTab == 0);

                // Settings visible only on Main tab
                MenuItem toolsSettingsItem = menu.findItem(R.id.action_tools_settings);
                if (toolsSettingsItem != null) toolsSettingsItem.setVisible(currentTab == 1);

                // CAN-USB Settings visible only on CAN-USB tab (tab index 2)
                MenuItem canusbSettingsItem = menu.findItem(R.id.action_canusb_settings);
                if (canusbSettingsItem != null) canusbSettingsItem.setVisible(currentTab == 2);

                // Play/Stop/Floating visible only on Simulator tab (tab index 4)
                MenuItem playItem = menu.findItem(R.id.action_play);
                MenuItem stopItem = menu.findItem(R.id.action_stop);
                MenuItem floatingItem = menu.findItem(R.id.action_floating);
                MenuItem controllerItem = menu.findItem(R.id.action_controller);
                if (playItem != null) playItem.setVisible(currentTab == 4);
                if (stopItem != null) stopItem.setVisible(currentTab == 4);
                if (floatingItem != null) floatingItem.setVisible(currentTab == 4);
                if (controllerItem != null) controllerItem.setVisible(currentTab == 4);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                View rootView = requireView();

                if (id == R.id.documentation) {
                    RunDocumentation();
                    return true;
                } else if (id == R.id.setup) {
                    RunSetup();
                    return true;
                } else if (id == R.id.update) {
                    RunUpdate();
                    return true;
                } else if (id == R.id.action_canusb_settings) {
                        ViewPager2 mViewPager = rootView.findViewById(R.id.pagerCAN);
                        if (mViewPager.getCurrentItem() == 2) { // CAN-USB tab
                            Fragment current = getChildFragmentManager().getFragments().get(2);
                            if (current instanceof CANUSBFragment) {
                                ((CANUSBFragment) current).showCanUsbConfig();
                            }
                        }
                        return true;
                } else if (id == R.id.about) {
                    RunAbout();
                    return true;
                } else if (id == R.id.action_settings) {
                    ViewPager2 mViewPager = rootView.findViewById(R.id.pagerCAN);
                    if (mViewPager.getCurrentItem() == 0) {
                        Fragment current = getChildFragmentManager().getFragments().get(0);
                        if (current instanceof MainFragment) {
                            ((MainFragment) current).showMainConfig();
                        }
                    }
                    return true;
                } else if (id == R.id.action_tools_settings) {
                    ViewPager2 mViewPager = rootView.findViewById(R.id.pagerCAN);
                    if (mViewPager.getCurrentItem() == 1) {
                        Fragment current = getChildFragmentManager().getFragments().get(1);
                        if (current instanceof ToolsFragment) {
                            ((ToolsFragment) current).showToolsConfig();
                        }
                    }
                    return true;
                } else if (id == R.id.action_play) {
                    Button runICSIM = rootView.findViewById(R.id.run_icsim);
                    if (runICSIM != null) runICSIM.performClick(); // reuse existing listener
                    return true;
                } else if (id == R.id.action_stop) {
                    Button stopICSIM = rootView.findViewById(R.id.stop_icsim);
                    if (stopICSIM != null) stopICSIM.performClick(); // reuse existing listener
                    return true;
                } else if (id == R.id.action_floating) {
                    Button floatICSIM = rootView.findViewById(R.id.floating_icsim);
                    if (floatICSIM != null) floatICSIM.performClick(); // reuse existing listener
                    return true;
                } else if (id == R.id.action_controller) {
                        FrameLayout controlsContainer = rootView.findViewById(R.id.controls_container);
                        if (controlsContainer != null) {
                            if (controlsContainer.getVisibility() == View.VISIBLE) {
                                controlsContainer.setVisibility(View.GONE);
                            } else {
                                controlsContainer.setVisibility(View.VISIBLE);
                            }
                        }
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
        builder.setTitle("Welcome to CARsenal!");
        builder.setMessage("This seems to be the first run. Install the CAN tools?");
        builder.setPositiveButton("Install", (dialog, which) -> {
            RunSetup();
            sharedpreferences.edit().putBoolean("carsenal_setup_done", true).apply();
        });
        builder.setNegativeButton("Disable message", (dialog, which) -> {
            dialog.dismiss();
            sharedpreferences.edit().putBoolean("carsenal_setup_done", true).apply();
        });
        builder.show();
    }

    // Documentation item
    public void RunDocumentation() {
        String url = "https://www.kali.org/docs/nethunter/nethunter-carsenal/";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(intent);
    }

    // Setup item
    public void RunSetup() {
        Log.d(TAG, "RunSetup called");
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

        Log.i(TAG, "Running setup commands");
        String setupCommand = "which wget > /dev/null 2>&1 && wget -qO - https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s setup || curl -s https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s setup";
        // Prefer in-app TerminalFragment to save memory; fallback to legacy bridge
        run_cmd_inapp(setupCommand);
        sharedpreferences.edit().putBoolean("carsenal_setup_done", true).apply();
        Log.i(TAG, "Setup initiated");
    }

    // Update item
    public void RunUpdate() {
        Log.d(TAG, "RunUpdate called");
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

        Log.i(TAG, "Running update commands");
        String updateCommand = "which wget > /dev/null 2>&1 && wget -qO - https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s update || curl -s https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s update";
        // Prefer in-app TerminalFragment to save memory; fallback to legacy bridge
        run_cmd_inapp(updateCommand);
        sharedpreferences.edit().putBoolean("carsenal_setup_done", true).apply();
        Log.i(TAG, "Update initiated");
    }

    public void RunAbout() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.carsenal_about_dialog, null);

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
        MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.secret_vroom);
        final int[] clickCount = {0};

        easterEggButton.setOnClickListener(v -> {
            clickCount[0]++;
            if (clickCount[0] == 3) {
                showToast("Hum??? What's up?");
            }
            if (clickCount[0] == 7) {
                mediaPlayer.start();
                clickCount[0] = 0; // reset after playing sound
            }
        });

        // Create a centered title TextView
        TextView titleView = new TextView(activity);
        titleView.setText(R.string.about_carsenal);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        titleView.setPadding(0, padding, 0, padding);

        new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat)
                .setCustomTitle(titleView)
                .setView(dialogView)
                .setNegativeButton("Close", (dialog, id) -> {
                    if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                    mediaPlayer.release();
                    dialog.dismiss();
                })
                .show();
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
                    return new CARsenalFragment.ToolsFragment();
                case 2:
                    return new CANUSBFragment();
                case 3:
                    return new CARsenalFragment.CANCARIBOUFragment();
                case 4:
                    return new SIMFragment();
                default :
                    return new CANMSFFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 6;
        }
    }

    public static class MainFragment extends CARsenalFragment {
        final ShellExecuter exe = new ShellExecuter();
        private static final long SHORT_DELAY = 1000L;
        private Context context;
        private TextView SelectedIface;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.carsenal_main, container, false);

            // Common used variables
            SelectedIface = rootView.findViewById(R.id.can_iface);

            final EditText selected_vin = rootView.findViewById(R.id.vin_number);

            // First run
            boolean setupdone = sharedpreferences.getBoolean("carsenal_setup_done", false);
            if (!setupdone) {
                SetupDialog();
            }

            // Attach and Daemon
            // ldattach
            Button LdAttachButton = rootView.findViewById(R.id.start_ldattach);

            // Access SharedPreferences
            SharedPreferences ldAttach_prefs = requireActivity().getSharedPreferences("ldAttach_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editorLdAttach = ldAttach_prefs.edit();

            // Load the saved command or use a default
            String savedCmd_ldAttach = ldAttach_prefs.getString("ldAttach_cmd", "ldattach --debug --speed 38400 --eightbits --noparity --onestopbit --iflag -ICRNL,INLCR,-IXOFF 29 /dev/rfcomm0");
            String[] ldAttachCmdHolder = {savedCmd_ldAttach};

            LdAttachButton.setOnClickListener(v -> {
                String ldAttachRun = ldAttachCmdHolder[0];

                if (!ldAttachRun.isEmpty()) {
                    run_cmd(ldAttachRun);
                    showToast("Press CTRL+C to stop.");
                } else {
                    showToast("Please set your ldattach command!");
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
            String[] slcandCmdHolder = {savedCmd_slcand};

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
            String[] slcanAttachCmdHolder = {savedCmd_slcanAttach};

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
            String[] hlcandCmdHolder = {savedCmd_hlcand};

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
            String[] rfcommBinderCmdHolder = {savedCmd_rfcomm_binder};

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
            String[] rfcommConnectCmdHolder = {savedCmd_rfcomm_connect};

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
            String[] socketcandCmdHolder = {savedCmd_socketcand};

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
                String selected_caniface = SelectedIface.getText().toString().trim();
                String interface_type = sharedpreferences.getString("cantype_selected", "").trim();

                // Read MTU and Txqueuelen values from SharedPreferences
                SharedPreferences prefs = requireContext().getSharedPreferences("carsenal_prefs", Context.MODE_PRIVATE);
                String selected_mtu = prefs.getString("mtu_value", "").trim();
                String selected_txqueuelen = prefs.getString("txq_value", "").trim();

                // Basic validation
                if (selected_caniface.isEmpty()) {
                    showToast("Please set a CAN interface!");
                    return;
                }
                if (!selected_caniface.matches("^(can|vcan|slcan)[0-9]$")) {
                    showToast("CAN Interface should be named \"^(can|vcan|slcan)[0-9]$\"");
                    return;
                }
                if (interface_type.isEmpty()) {
                    showToast("Please, set interface type!");
                    return;
                }

                try {
                    // ----------- vcan -----------
                    if ("vcan".equals(interface_type)) {
                        String addVcanIface = exe.RunAsChrootOutput(
                                "sudo ip link add " + selected_caniface + " type vcan && echo Success || echo Failed"
                        );
                        if (addVcanIface.contains("FATAL:") || addVcanIface.contains("Failed")) {
                            showToast("Failed to add " + selected_caniface + "! Interface may already exist.");
                            return;
                        }

                        // Set MTU
                        if (!selected_mtu.isEmpty()) {
                            int mtuValue = Integer.parseInt(selected_mtu);
                            exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " mtu " + mtuValue + " && echo Success || echo Failed");
                        }

                        // Set TX queue length if requested
                        if (!selected_txqueuelen.isEmpty()) {
                            int txqValue = Integer.parseInt(selected_txqueuelen);
                            exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " txqueuelen " + txqValue + " && echo Success || echo Failed");
                        }

                        // Bring up interface
                        exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " up && echo Success || echo Failed");

                        showToast("Interface " + selected_caniface + " (vcan) started!");
                        return;
                    }

                    // ----------- can / slcan -----------
                    if ("can".equals(interface_type) || "slcan".equals(interface_type)) {
                        String usbDevice = exe.RunAsChrootOutput("ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$'");
                        if (usbDevice.isEmpty()) {
                            showToast("No CAN hardware detected, please connect adapter and try again.");
                            return;
                        }

                        // Set MTU if requested
                        if (!selected_mtu.isEmpty()) {
                            int mtuValue = Integer.parseInt(selected_mtu);
                            exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " mtu " + mtuValue + " && echo Success || echo Failed");
                        }

                        // Set TX queue length if requested
                        if (!selected_txqueuelen.isEmpty()) {
                            int txqValue = Integer.parseInt(selected_txqueuelen);
                            exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " txqueuelen " + txqValue + " && echo Success || echo Failed");
                        }

                        // Bring up interface
                        String startCanIface = exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " up && echo Success || echo Failed");
                        if (startCanIface.contains("FATAL:") || startCanIface.contains("Failed")) {
                            showToast("Failed to start " + selected_caniface + " interface!");
                        } else {
                            showToast("Interface " + selected_caniface + " (" + interface_type + ") started!");
                        }
                    }

                } catch (NumberFormatException e) {
                    showToast("Invalid numeric value for MTU or TX queue length.");
                } catch (Exception e) {
                    showToast("Error starting interface: " + e.getMessage());
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

        // Type spinner
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

        private void showMainConfig() {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            View dialogView = inflater.inflate(R.layout.carsenal_main_dialog, null);

            TextInputEditText mtuEditText = dialogView.findViewById(R.id.mtu_value);
            TextInputEditText txqEditText = dialogView.findViewById(R.id.txq_value);

            // Load saved values
            SharedPreferences prefs = requireContext().getSharedPreferences("carsenal_prefs", Context.MODE_PRIVATE);
            mtuEditText.setText(prefs.getString("mtu_value", ""));
            txqEditText.setText(prefs.getString("txq_value", ""));

            // Build dialog
            new MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyleCompat)
                    .setTitle("Interface Settings")
                    .setView(dialogView)
                    .setPositiveButton("Apply", (dialog, which) -> {
                        // Save values
                        prefs.edit()
                                .putString("mtu_value", String.valueOf(mtuEditText.getText()))
                                .putString("txq_value", String.valueOf(txqEditText.getText()))
                                .apply();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    public static class ToolsFragment extends CARsenalFragment {
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
            View rootView = inflater.inflate(R.layout.carsenal_tools, container, false);
            View dialogView = inflater.inflate(R.layout.carsenal_tools_dialog, container, false);

            final EditText cansend_sequence = dialogView.findViewById(R.id.cansend_sequence);
            final EditText SelectedRHost = dialogView.findViewById(R.id.cannelloni_rhost);
            final EditText SelectedRPort = dialogView.findViewById(R.id.cannelloni_rport);
            final EditText SelectedLPort = dialogView.findViewById(R.id.cannelloni_lport);
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

            // Interactive Switch
            SwitchCompat switchInteractive = dialogView.findViewById(R.id.btn_toggle_interactive);
            switchInteractive.setChecked(isInteractiveEnabled);

            switchInteractive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isInteractiveEnabled = isChecked;

                int color = isInteractiveEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                switchInteractive.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Verbose Switch
            SwitchCompat switchVerbose = dialogView.findViewById(R.id.btn_toggle_verbose);
            switchVerbose.setChecked(isVerboseEnabled);

            switchVerbose.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isVerboseEnabled = isChecked;

                int color = isVerboseEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                switchVerbose.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Loopback Switch (inverted logic)
            SwitchCompat switchLoopback = dialogView.findViewById(R.id.btn_toggle_loopback);
            switchLoopback.setChecked(!isDisableLoopbackEnabled);

            switchLoopback.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isDisableLoopbackEnabled = !isChecked; // invert logic here

                int color = isDisableLoopbackEnabled ? android.R.color.holo_red_light : android.R.color.holo_green_light; //invert color
                switchLoopback.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Input File browse button
            ImageButton inputfilebrowse = dialogView.findViewById(R.id.inputfilebrowse);
            TextInputEditText inputfilepath = dialogView.findViewById(R.id.inputfilepath);

            inputfilebrowse.setOnClickListener(v -> {
                RootFileBrowserDialog dialog = new RootFileBrowserDialog(requireContext(), inputfilepath::setText);
                dialog.show();
            });

            // Output File browse button
            ImageButton outputfilebrowse = dialogView.findViewById(R.id.outputfilebrowse);
            TextInputEditText outputfilepath = dialogView.findViewById(R.id.outputfilepath);

            outputfilebrowse.setOnClickListener(v -> {
                RootFileBrowserDialog dialog = new RootFileBrowserDialog(requireContext(), outputfilepath::setText);
                dialog.show();
            });

            // Tools
            // CanGen
            Button CanGenButton = rootView.findViewById(R.id.start_cangen);
            CanGenButton.setOnClickListener(v -> {
                String verboseEnabled = prefs.getBoolean("verbose_enabled", isVerboseEnabled) ? " -v" : "";
                String disableLoopbackEnabled = !prefs.getBoolean("disable_loopback", !isDisableLoopbackEnabled) ? " -x" : "";

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
                String verboseEnabled = prefs.getBoolean("verbose_enabled", isVerboseEnabled) ? " -v" : "";
                String disableLoopbackEnabled = !prefs.getBoolean("disable_loopback", !isDisableLoopbackEnabled) ? " -x" : "";
                String defaultCmd = "cangen " + selected_caniface + verboseEnabled + disableLoopbackEnabled;

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
                String outputfile = prefs.getString(
                        "output_file",
                        (outputfilepath != null && outputfilepath.getText() != null) ? outputfilepath.getText().toString() : ""
                );

                if (!canDumpCmd[0].isEmpty()) {
                    run_cmd(canDumpCmd[0]);
                } else if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interfaces") && !outputfile.isEmpty()) {
                    run_cmd("candump " + selected_caniface + " -f " + outputfile);
                } else {
                    showToast("Please ensure your CAN Interface and Output File fields are set!");
                }

                activity.invalidateOptionsMenu();
            });
            CanDumpButton.setOnLongClickListener(v -> {
                String outputfile = prefs.getString(
                        "output_file",
                        (outputfilepath != null && outputfilepath.getText() != null) ? outputfilepath.getText().toString() : ""
                );
                String defaultCmd = "candump " + selected_caniface + " -f " + outputfile;

                showEditCommandDialog("Edit CanDump Command", canDumpCmd, "canDump_cmd", defaultCmd);
                return true;
            });

            // CanSend
            Button CanSendButton = rootView.findViewById(R.id.start_cansend);
            CanSendButton.setOnClickListener(v -> {
                String sequence = prefs.getString("cansend_sequence", cansend_sequence.getText().toString());

                if (!canSendCmd[0].isEmpty()) {
                    run_cmd(canSendCmd[0]);
                } else if (!selected_caniface.equals("Interfaces") && !sequence.isEmpty()) {
                    run_cmd("cansend " + selected_caniface + " " + sequence);
                } else {
                    showToast("Please ensure your CAN Interface and Sequence fields are set!");
                }

                activity.invalidateOptionsMenu();
            });
            CanSendButton.setOnLongClickListener(v -> {
                String savedSequence = prefs.getString("cansend_sequence", "");
                String defaultCmd = "cansend " + selected_caniface + " " + savedSequence;

                showEditCommandDialog("Edit CanSend Command", canSendCmd, "canSend_cmd", defaultCmd);
                return true;
            });

            // CanPlayer
            Button CanPlayerButton = rootView.findViewById(R.id.start_canplayer);
            CanPlayerButton.setOnClickListener(v -> {
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );
                String interactiveEnabled = prefs.getBoolean("interactive_enabled", isInteractiveEnabled) ? " -i" : "";
                String verboseEnabled = prefs.getBoolean("verbose_enabled", isVerboseEnabled) ? " -v" : "";
                String disableLoopbackEnabled = !prefs.getBoolean("disable_loopback", !isDisableLoopbackEnabled) ? " -x" : "";

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
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );
                String interactiveEnabled = prefs.getBoolean("interactive_enabled", isInteractiveEnabled) ? " -i" : "";
                String verboseEnabled = prefs.getBoolean("verbose_enabled", isVerboseEnabled) ? " -v" : "";
                String disableLoopbackEnabled = !prefs.getBoolean("disable_loopback", !isDisableLoopbackEnabled) ? " -x" : "";

                String defaultCmd = "canplayer -I " + inputfile + interactiveEnabled + verboseEnabled + disableLoopbackEnabled;

                showEditCommandDialog("Edit CanPlayer Command", canPlayerCmd, "canPlayer_cmd", defaultCmd);
                return true;
            });

            // SequenceFinder
            Button SequenceFinderButton = rootView.findViewById(R.id.start_sequencefinder);
            SequenceFinderButton.setOnClickListener(v -> {
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );

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
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );
                String defaultCmd = "/opt/car_hacking/sequence_finder.sh " + inputfile;

                showEditCommandDialog("Edit SequenceFinder Command", sequenceFinderCmd, "sequenceFinder_cmd", defaultCmd);
                return true;
            });

            // Freediag
            Button FreediagButton = rootView.findViewById(R.id.start_freediag);
            FreediagButton.setOnClickListener(v -> {
                if (!freediagCmd[0].isEmpty()) {
                    run_cmd(freediagCmd[0]);
                } else {
                    run_cmd("freediag");
                }
                activity.invalidateOptionsMenu();
            });
            FreediagButton.setOnLongClickListener(v -> {
                String defaultCmd = "freediag";
                showEditCommandDialog("Edit Freediag Command", freediagCmd, "freediag_cmd", defaultCmd);
                return true;
            });

            // diagTest
            Button diagTestButton = rootView.findViewById(R.id.start_diagtest);
            diagTestButton.setOnClickListener(v -> {
                if (!diagTestCmd[0].isEmpty()) {
                    run_cmd(diagTestCmd[0]);
                } else {
                    run_cmd("diag_test");
                }
                activity.invalidateOptionsMenu();
            });
            diagTestButton.setOnLongClickListener(v -> {
                String defaultCmd = "diag_test";
                showEditCommandDialog("Edit diag_test Command", diagTestCmd, "diagTest_cmd", defaultCmd);
                return true;
            });

            // Cannelloni
            Button CannelloniButton = rootView.findViewById(R.id.start_cannelloni);
            CannelloniButton.setOnClickListener(v -> {
                String rhost = prefs.getString("cannelloni_rhost", SelectedRHost.getText().toString());
                String rport = prefs.getString("cannelloni_rport", SelectedRPort.getText().toString());
                String lport = prefs.getString("cannelloni_lport", SelectedLPort.getText().toString());

                if (!cannelloniCmd[0].isEmpty()) {
                    run_cmd(cannelloniCmd[0]);
                } else {
                    if (selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                        showToast("Please select a CAN Interface!");
                        return;
                    }

                    // Check IP address (basic validation)
                    if (!rhost.matches(
                            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}" +
                                    "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$")) {
                        showToast("RHOST must be a valid IP address (e.g., 192.168.1.100)");
                        return;
                    }

                    // Check ports (valid range 1-65535)
                    try {
                        int rPortInt = Integer.parseInt(rport);
                        int lPortInt = Integer.parseInt(lport);
                        if (rPortInt < 1 || rPortInt > 65535) {
                            showToast("RPORT must be between 1 and 65535");
                            return;
                        }
                        if (lPortInt < 1 || lPortInt > 65535) {
                            showToast("LPORT must be between 1 and 65535");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        showToast("Ports must be numeric");
                        return;
                    }

                    run_cmd("sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport);
                }
                activity.invalidateOptionsMenu();
            });
            CannelloniButton.setOnLongClickListener(v -> {
                String rhost = prefs.getString("cannelloni_rhost", SelectedRHost.getText().toString());
                String rport = prefs.getString("cannelloni_rport", SelectedRPort.getText().toString());
                String lport = prefs.getString("cannelloni_lport", SelectedLPort.getText().toString());

                String defaultCmd = "sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport;
                showEditCommandDialog("Edit Cannelloni Command", cannelloniCmd, "cannelloni_cmd", defaultCmd);
                return true;
            });

            // Asc2Log
            Button Asc2LogButton = rootView.findViewById(R.id.start_asc2log);
            Asc2LogButton.setOnClickListener(v -> {
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );

                String outputfile = prefs.getString(
                        "output_file",
                        (outputfilepath != null && outputfilepath.getText() != null) ? outputfilepath.getText().toString() : ""
                );

                if (!asc2logCmd[0].isEmpty()) {
                    run_cmd(asc2logCmd[0]);
                } else if (!inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("asc2log -I " + inputfile + " -O " + outputfile);
                } else {
                    showToast("Please ensure your Input and Output File fields are set!");
                }
                activity.invalidateOptionsMenu();
            });
            Asc2LogButton.setOnLongClickListener(v -> {
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );

                String outputfile = prefs.getString(
                        "output_file",
                        (outputfilepath != null && outputfilepath.getText() != null) ? outputfilepath.getText().toString() : ""
                );

                String defaultCmd = "asc2log -I " + inputfile + " -O " + outputfile;
                showEditCommandDialog("Edit Asc2Log Command", asc2logCmd, "asc2log_cmd", defaultCmd);
                return true;
            });

            // Log2Asc
            Button Log2AscButton = rootView.findViewById(R.id.start_log2asc);
            Log2AscButton.setOnClickListener(v -> {
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );

                String outputfile = prefs.getString(
                        "output_file",
                        (outputfilepath != null && outputfilepath.getText() != null) ? outputfilepath.getText().toString() : ""
                );

                if (!log2ascCmd[0].isEmpty()) {
                    run_cmd(log2ascCmd[0]);
                } else if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interfaces") && !inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("log2asc -I " + inputfile + " -O " + outputfile + " " + selected_caniface);
                } else {
                    showToast("Please ensure your CAN Interface, Input and Output File fields are set!");
                }
                activity.invalidateOptionsMenu();
            });
            Log2AscButton.setOnLongClickListener(v -> {
                String inputfile = prefs.getString(
                        "input_file",
                        (inputfilepath != null && inputfilepath.getText() != null) ? inputfilepath.getText().toString() : ""
                );

                String outputfile = prefs.getString(
                        "output_file",
                        (outputfilepath != null && outputfilepath.getText() != null) ? outputfilepath.getText().toString() : ""
                );

                String defaultCmd = "log2asc -I " + inputfile + " -O " + outputfile + " " + selected_caniface;
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
        private void showToolsConfig() {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            View dialogView = inflater.inflate(R.layout.carsenal_tools_dialog, null);

            // Text fields
            TextInputEditText cansendSequence = dialogView.findViewById(R.id.cansend_sequence);
            TextInputEditText rhost = dialogView.findViewById(R.id.cannelloni_rhost);
            TextInputEditText rport = dialogView.findViewById(R.id.cannelloni_rport);
            TextInputEditText lport = dialogView.findViewById(R.id.cannelloni_lport);
            TextInputEditText inputFile = dialogView.findViewById(R.id.inputfilepath);
            TextInputEditText outputFile = dialogView.findViewById(R.id.outputfilepath);

            // Switches
            SwitchCompat switchInteractive = dialogView.findViewById(R.id.btn_toggle_interactive);
            SwitchCompat switchVerbose = dialogView.findViewById(R.id.btn_toggle_verbose);
            SwitchCompat switchLoopback = dialogView.findViewById(R.id.btn_toggle_loopback);

            // Load saved values
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            cansendSequence.setText(prefs.getString("cansend_sequence", ""));
            rhost.setText(prefs.getString("cannelloni_rhost", ""));
            rport.setText(prefs.getString("cannelloni_rport", ""));
            lport.setText(prefs.getString("cannelloni_lport", ""));
            inputFile.setText(prefs.getString("input_file", ""));
            outputFile.setText(prefs.getString("output_file", ""));
            switchInteractive.setChecked(isInteractiveEnabled);
            switchVerbose.setChecked(isVerboseEnabled);
            switchLoopback.setChecked(!isDisableLoopbackEnabled);

            // Browse buttons — use dialogView.getContext() to ensure proper context
            ImageButton inputfilebrowse = dialogView.findViewById(R.id.inputfilebrowse);
            inputfilebrowse.setOnClickListener(v -> {
                RootFileBrowserDialog browserDialog = new RootFileBrowserDialog(dialogView.getContext(), inputFile::setText);
                browserDialog.show();
            });

            ImageButton outputfilebrowse = dialogView.findViewById(R.id.outputfilebrowse);
            outputfilebrowse.setOnClickListener(v -> {
                RootFileBrowserDialog browserDialog = new RootFileBrowserDialog(dialogView.getContext(), outputFile::setText);
                browserDialog.show();
            });

            // Build the dialog
            new MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyleCompat)
                    .setTitle("Tools Settings")
                    .setView(dialogView)
                    .setPositiveButton("Save", (dialog, which) -> {
                        prefs.edit()
                                .putString("cansend_sequence", cansendSequence.getText() != null
                                        ? cansendSequence.getText().toString()
                                        : "")
                                .putString("cannelloni_rhost", rhost.getText() != null
                                        ? rhost.getText().toString()
                                        : "")
                                .putString("cannelloni_rport", rport.getText() != null
                                        ? rport.getText().toString()
                                        : "")
                                .putString("cannelloni_lport", lport.getText() != null
                                        ? lport.getText().toString()
                                        : "")
                                .putString("input_file", inputFile.getText() != null
                                        ? inputFile.getText().toString()
                                        : "")
                                .putString("output_file", outputFile.getText() != null
                                        ? outputFile.getText().toString()
                                        : "")
                                .putBoolean("interactive_enabled", switchInteractive.isChecked())
                                .putBoolean("verbose_enabled", switchVerbose.isChecked())
                                .putBoolean("disable_loopback", !switchLoopback.isChecked())
                                .apply();
                        showToast("Settings saved!");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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

    public static class CANUSBFragment extends CARsenalFragment {
        private final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Activity activity;
        private EditText selectedBaudrateUSB;
        private EditText selectedCanSpeedUSB;
        private String selected_usb;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.carsenal_canusb, container, false);

            selectedBaudrateUSB = rootView.findViewById(R.id.baudrate_usb);
            selectedCanSpeedUSB = rootView.findViewById(R.id.canspeed_usb);

            // Devices Interfaces Spinner
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

            // Start USB-CAN button
            rootView.findViewById(R.id.start_canusb_send).setOnClickListener(v -> runCanUsb());
            return rootView;
        }

        private void showCanUsbConfig() {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            View dialogView = inflater.inflate(R.layout.carsenal_canusb_dialog, null);

            // Inputs
            final EditText idInput = dialogView.findViewById(R.id.usb_id_value);
            final EditText counterInput = dialogView.findViewById(R.id.usb_counter_value);
            final EditText sleepInput = dialogView.findViewById(R.id.usb_sleep_value);
            final EditText dataInput = dialogView.findViewById(R.id.usb_data_value);
            final Spinner modeSpinner = dialogView.findViewById(R.id.usb_mode_spinner);
            final SwitchCompat debugSwitch = dialogView.findViewById(R.id.btn_toggle_usb_ttyOutput);

            // Setup mode spinner
            ArrayAdapter<String> adapter = getStringArrayAdapter();
            modeSpinner.setAdapter(adapter);
            modeSpinner.setSelection(0);

            // Load saved preferences
            SharedPreferences prefs = requireContext().getSharedPreferences("carsenal_prefs", Context.MODE_PRIVATE);

            debugSwitch.setChecked(prefs.getBoolean("usb_debug_enabled", false));
            idInput.setText(prefs.getString("usb_id_value", ""));
            counterInput.setText(prefs.getString("usb_counter_value", ""));
            sleepInput.setText(prefs.getString("usb_sleep_value", ""));
            dataInput.setText(prefs.getString("usb_data_value", ""));
            String savedMode = prefs.getString("usb_mode_value", "");
            if (!savedMode.isEmpty()) {
                int pos = adapter.getPosition(savedMode);
                if (pos >= 0) modeSpinner.setSelection(pos);
            }

            // Build dialog
            new MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyleCompat)
                    .setTitle("CAN-USB Configuration")
                    .setView(dialogView)
                    .setPositiveButton("Apply", (dialog, which) -> {
                        SharedPreferences.Editor editor = prefs.edit();

                        editor.putBoolean("usb_debug_enabled", debugSwitch.isChecked());
                        editor.putString("usb_id_value", idInput.getText().toString().trim());
                        editor.putString("usb_counter_value", counterInput.getText().toString().trim());
                        editor.putString("usb_sleep_value", sleepInput.getText().toString().trim());
                        editor.putString("usb_data_value", dataInput.getText().toString().trim());
                        editor.putString("usb_mode_value", modeSpinner.getSelectedItem().toString().trim());

                        editor.apply();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        @NonNull
        private ArrayAdapter<String> getStringArrayAdapter() {
            final String[] modeOptions = {"Mode", "0", "1", "2"};
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, modeOptions) {
                @Override
                public boolean isEnabled(int position) {
                    return position != 0; // Disable "Mode" hint
                }

                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                    tv.setTextColor(position == 0 ? Color.GRAY : Color.WHITE);
                    return tv;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return adapter;
        }

        private void runCanUsb() {
            String USBCANSpeed = selectedCanSpeedUSB.getText().toString().trim();
            String USBBaudrate = selectedBaudrateUSB.getText().toString().trim();
            SharedPreferences prefs = requireContext().getSharedPreferences("carsenal_prefs", Context.MODE_PRIVATE);

            if (selected_usb == null || selected_usb.isEmpty() || selected_usb.equals("USB Devices") || USBCANSpeed.isEmpty() || USBBaudrate.isEmpty()) {
                showToast("Please ensure your USB Device, CAN Speed, Baudrate, and Data fields are set!");
                return;
            }

            String debugEnabled = prefs.getBoolean("usb_debug_enabled", false) ? " -t" : "";
            String idValue = prefs.getString("usb_id_value", "").trim();
            String countValue = prefs.getString("usb_counter_value", "").trim();
            String dataValue = prefs.getString("usb_data_value", "").trim();
            String sleepValue = prefs.getString("usb_sleep_value", "").trim();
            String modeValue = prefs.getString("usb_mode_value", "").trim();

            StringBuilder cmdBuilder = new StringBuilder();
            cmdBuilder.append("canusb -d ").append(selected_usb)
                    .append(" -s ").append(USBCANSpeed)
                    .append(" -b ").append(USBBaudrate)
                    .append(debugEnabled);

            if (!idValue.isEmpty()) cmdBuilder.append(" -i ").append(idValue);
            if (!dataValue.isEmpty()) cmdBuilder.append(" -j ").append(dataValue);
            if (!sleepValue.isEmpty()) cmdBuilder.append(" -g ").append(sleepValue);
            if (!countValue.isEmpty()) cmdBuilder.append(" -n ").append(countValue);
            if (!modeValue.isEmpty()) cmdBuilder.append(" -m ").append(modeValue);

            run_cmd(cmdBuilder.toString());
            activity.invalidateOptionsMenu();
        }
    }

    public static class CANCARIBOUFragment extends CARsenalFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private EditText SelectedFile;
        private EditText SelectedMessage;
        private Spinner ecuResetTypeSpinner, ecuResetMethodeSpinner, sessionTypeSpinner, securityLevelSpinner;
        private String selected_caniface = "";
        private TextInputLayout seedContainer, minContainer, maxContainer, srcContainer, dstContainer;
        private TextInputLayout delayContainer, lengthContainer, startAddrContainer, idContainer, separateLineContainer;
        private TextInputLayout whitelistContainer, indexContainer, arbIDContainer, dataContainer, blacklistContainer;
        private TextInputLayout dtypeContainer, stypeContainer, messageContainer, timeoutContainer, autoBlacklistContainer;
        private TextInputLayout durationContainer, mindidContainer, maxdidContainer, numberContainer, interDelayContainer, iterationsContainer;
        private TextInputLayout memLengthContainer, memSizeContainer, addrByteSizeContainer, memLengthByteSizeContainer;
        private TextInputLayout sessionTypeInputContainer, securityLevelInputContainer, sessionSeqContainer, seedTargetContainer;
        private ViewGroup loopContainer, padContainer, outputContainer, fileContainer, reverseContainer;
        private ViewGroup requestsContainer, candumpContainer, responsesContainer, skipverifyContainer;
        private ViewGroup sprContainer, ecuResetTypeContainer, ecuResetMethodeContainer, sessionTypeContainer, securityLevelContainer;

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
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.carsenal_caribou, container, false);

            seedContainer = rootView.findViewById(R.id.seed_container);
            minContainer = rootView.findViewById(R.id.min_container);
            maxContainer = rootView.findViewById(R.id.max_container);
            srcContainer = rootView.findViewById(R.id.src_container);
            dstContainer = rootView.findViewById(R.id.dst_container);
            mindidContainer = rootView.findViewById(R.id.min_did_container);
            maxdidContainer = rootView.findViewById(R.id.max_did_container);
            stypeContainer = rootView.findViewById(R.id.stype_container);
            dtypeContainer = rootView.findViewById(R.id.dtype_container);
            delayContainer = rootView.findViewById(R.id.delay_container);
            lengthContainer = rootView.findViewById(R.id.length_container);
            startAddrContainer = rootView.findViewById(R.id.start_addr_container);
            separateLineContainer = rootView.findViewById(R.id.separate_line_container);
            idContainer = rootView.findViewById(R.id.id_container);
            whitelistContainer = rootView.findViewById(R.id.whitelist_arbID_container);
            indexContainer = rootView.findViewById(R.id.index_container);
            arbIDContainer = rootView.findViewById(R.id.arbID_container);
            dataContainer = rootView.findViewById(R.id.data_container);
            blacklistContainer = rootView.findViewById(R.id.blacklist_container);
            autoBlacklistContainer = rootView.findViewById(R.id.autoBlacklist_container);
            timeoutContainer = rootView.findViewById(R.id.timeout_container);
            durationContainer = rootView.findViewById(R.id.duration_container);
            memLengthContainer = rootView.findViewById(R.id.memLength_container);
            memSizeContainer = rootView.findViewById(R.id.memSize_container);
            addrByteSizeContainer = rootView.findViewById(R.id.addrByteSize_container);
            memLengthByteSizeContainer = rootView.findViewById(R.id.memLengthByteSize_container);
            sessionSeqContainer = rootView.findViewById(R.id.sessionSeq_container);
            seedTargetContainer = rootView.findViewById(R.id.seedTarget_container);
            interDelayContainer = rootView.findViewById(R.id.interDelay_container);
            iterationsContainer = rootView.findViewById(R.id.iterations_container);
            sprContainer = rootView.findViewById(R.id.spr_container);
            skipverifyContainer = rootView.findViewById(R.id.skipverify_container);
            loopContainer = rootView.findViewById(R.id.loop_container);
            padContainer = rootView.findViewById(R.id.pad_container);
            outputContainer = rootView.findViewById(R.id.output_container);
            responsesContainer = rootView.findViewById(R.id.responses_container);
            requestsContainer = rootView.findViewById(R.id.requests_container);
            candumpContainer = rootView.findViewById(R.id.candump_container);
            messageContainer = rootView.findViewById(R.id.message_container);
            SelectedMessage = rootView.findViewById(R.id.caribou_message);
            fileContainer = rootView.findViewById(R.id.file_container);
            SelectedFile = rootView.findViewById(R.id.caribou_file);
            numberContainer = rootView.findViewById(R.id.number_container);
            reverseContainer = rootView.findViewById(R.id.reverse_container);

            // Spinner for ECU Reset Type
            ecuResetTypeContainer = rootView.findViewById(R.id.spinner_row_ecureset);
            ecuResetTypeSpinner = rootView.findViewById(R.id.ecureset_type_spinner);

            // Spinner for ECU Reset Methode
            ecuResetMethodeContainer = rootView.findViewById(R.id.spinner_row_ecuresetmethode);
            ecuResetMethodeSpinner = rootView.findViewById(R.id.ecureset_methode_spinner);

            // Spinner for security_seed (sessionType)
            sessionTypeContainer = rootView.findViewById(R.id.spinner_row_stype);
            sessionTypeSpinner = rootView.findViewById(R.id.stype_spinner);
            sessionTypeInputContainer = rootView.findViewById(R.id.stype_input_container);

            // Spinner for security_seed (securityLevel)
            securityLevelContainer = rootView.findViewById(R.id.spinner_row_level);
            securityLevelSpinner = rootView.findViewById(R.id.level_spinner);
            securityLevelInputContainer = rootView.findViewById(R.id.level_input_container);

            // Spinner for ECU Reset Type
            String[] ecuResetOptions = {
                    "Select ECU Reset Type",  // disabled first entry
                    "1=hard",
                    "2=key off/on",
                    "3=soft",
                    "4=enable rapid power shutdown",
                    "5=disable rapid power shutdown"
            };

            ArrayAdapter<String> ecuResetAdapter = createDisabledFirstItemAdapter(ecuResetOptions);
            ecuResetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            ecuResetTypeSpinner.setAdapter(ecuResetAdapter);

            // Spinner for EcuResetMethode
            String[] ecuResetMethodeOptions = {
                    "Select ECU Reset Methode",  // disabled first entry
                    "0=once before seed request start",
                    "1=before each seed request (default)"
            };

            ArrayAdapter<String> ecuResetMethodeAdapter = createDisabledFirstItemAdapter(ecuResetMethodeOptions);
            ecuResetMethodeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            ecuResetMethodeSpinner.setAdapter(ecuResetMethodeAdapter);

            // Spinner for security_seed (sessionType)
            String[] sessionTypeOptions = {
                    "Select Session Type",
                    "1=defaultSession",
                    "2=programmingSession",
                    "3=extendedSession",
                    "4=safetySession",
                    "0x40-0x5F=OEM",
                    "0x60-0x7E=Supplier",
                    "0x0,0x5-0x3F,0x7F=ISOSAEReserved"
            };

            ArrayAdapter<String> sessionTypeAdapter = createDisabledFirstItemAdapter(sessionTypeOptions);
            sessionTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sessionTypeSpinner.setAdapter(sessionTypeAdapter);
            sessionTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position > 0) {
                        String selected = (String) sessionTypeSpinner.getSelectedItem();
                        if (selected.contains("-") || selected.contains(",")) {
                            sessionTypeInputContainer.setVisibility(View.VISIBLE);
                        } else {
                            sessionTypeInputContainer.setVisibility(View.GONE);
                        }
                    } else {
                        sessionTypeInputContainer.setVisibility(View.GONE);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Spinner for security_seed (securityLevel)
            String[] securityLevelOptions = {
                    "Select Security Level",
                    "0x1-0x41=OEM",
                    "0x5F=EOLPyrotechnics",
                    "0x61-0x7E=Supplier",
                    "0x0,0x43-0x5E,0x7F=ISOSAEReserved"
            };

            ArrayAdapter<String> securityLevelAdapter = createDisabledFirstItemAdapter(securityLevelOptions);
            securityLevelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            securityLevelSpinner.setAdapter(securityLevelAdapter);
            securityLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position > 0) {
                        String selected = (String) securityLevelSpinner.getSelectedItem();
                        if (selected.contains("-") || selected.contains(",")) {
                            securityLevelInputContainer.setVisibility(View.VISIBLE);
                        } else {
                            securityLevelInputContainer.setVisibility(View.GONE);
                        }
                    } else {
                        securityLevelInputContainer.setVisibility(View.GONE);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Browse File
            ImageButton browseButton = rootView.findViewById(R.id.cariboufilebrowse);
            browseButton.setOnClickListener(v -> {
                RootFileBrowserDialog dialog = new RootFileBrowserDialog(requireContext(), SelectedFile::setText);
                dialog.show();
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

            // Module and SubModule spinners
            final Spinner moduleSpinner = rootView.findViewById(R.id.module_spinner);
            final Spinner subModuleSpinner = rootView.findViewById(R.id.submodule_spinner);
            final Button startButton = rootView.findViewById(R.id.start_button);
            final String[] modules = {"Modules", "Dump", "Fuzzer", "Listener", "module_template", "Send", "UDS", "UDS_Fuzz", "XCP"};
            final Map<String, String[]> subModulesMap = new HashMap<>();
            subModulesMap.put("Dump", new String[]{"Sub-Modules", "None"});
            subModulesMap.put("Fuzzer", new String[]{"Sub-Modules", "brute", "identify", "mutate", "random", "replay"});
            subModulesMap.put("Listener", new String[]{"Sub-Modules", "None"});
            subModulesMap.put("module_template", new String[]{"Sub-Modules", "None"});
            subModulesMap.put("Send", new String[]{"Sub-Modules", "file", "message"});
            subModulesMap.put("UDS", new String[]{"Sub-Modules", "discovery", "services", "subservices", "ecu_reset", "testerpresent", "security_seed", "dump_dids", "read_mem", "auto"});
            subModulesMap.put("UDS_Fuzz", new String[]{"Sub-Modules", "delay_fuzzer", "seed_randomness_fuzzer"});
            subModulesMap.put("XCP", new String[]{"Sub-Modules", "discovery", "info", "commands", "dump"});

            ArrayAdapter<String> moduleAdapter = createDisabledFirstItemAdapter(modules);
            moduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            moduleSpinner.setAdapter(moduleAdapter);

            ArrayAdapter<String> emptySubModuleAdapter = createDisabledFirstItemAdapter(new String[]{"Sub-Modules"});
            emptySubModuleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            subModuleSpinner.setAdapter(emptySubModuleAdapter);

            moduleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    String selectedModule = modules[pos];
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

            subModuleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedModule = (String) moduleSpinner.getSelectedItem();
                    String selectedSubModule = (String) subModuleSpinner.getSelectedItem();

                    // Default: hide field
                    idContainer.setVisibility(View.GONE);
                    minContainer.setVisibility(View.GONE);
                    maxContainer.setVisibility(View.GONE);
                    srcContainer.setVisibility(View.GONE);
                    dstContainer.setVisibility(View.GONE);
                    mindidContainer.setVisibility(View.GONE);
                    maxdidContainer.setVisibility(View.GONE);
                    seedContainer.setVisibility(View.GONE);
                    outputContainer.setVisibility(View.GONE);
                    messageContainer.setVisibility(View.GONE);
                    loopContainer.setVisibility(View.GONE);
                    fileContainer.setVisibility(View.GONE);
                    padContainer.setVisibility(View.GONE);
                    candumpContainer.setVisibility(View.GONE);
                    reverseContainer.setVisibility(View.GONE);
                    lengthContainer.setVisibility(View.GONE);
                    separateLineContainer.setVisibility(View.GONE);
                    startAddrContainer.setVisibility(View.GONE);
                    delayContainer.setVisibility(View.GONE);
                    whitelistContainer.setVisibility(View.GONE);
                    responsesContainer.setVisibility(View.GONE);
                    requestsContainer.setVisibility(View.GONE);
                    indexContainer.setVisibility(View.GONE);
                    arbIDContainer.setVisibility(View.GONE);
                    dataContainer.setVisibility(View.GONE);
                    blacklistContainer.setVisibility(View.GONE);
                    autoBlacklistContainer.setVisibility(View.GONE);
                    skipverifyContainer.setVisibility(View.GONE);
                    timeoutContainer.setVisibility(View.GONE);
                    stypeContainer.setVisibility(View.GONE);
                    dtypeContainer.setVisibility(View.GONE);
                    messageContainer.setVisibility(View.GONE);
                    timeoutContainer.setVisibility(View.GONE);
                    durationContainer.setVisibility(View.GONE);
                    sprContainer.setVisibility(View.GONE);
                    ecuResetTypeContainer.setVisibility(View.GONE);
                    ecuResetMethodeContainer.setVisibility(View.GONE);
                    numberContainer.setVisibility(View.GONE);
                    securityLevelContainer.setVisibility(View.GONE);
                    sessionTypeContainer.setVisibility(View.GONE);
                    memLengthContainer.setVisibility(View.GONE);
                    memSizeContainer.setVisibility(View.GONE);
                    addrByteSizeContainer.setVisibility(View.GONE);
                    memLengthByteSizeContainer.setVisibility(View.GONE);
                    sessionSeqContainer.setVisibility(View.GONE);
                    seedTargetContainer.setVisibility(View.GONE);
                    interDelayContainer.setVisibility(View.GONE);
                    iterationsContainer.setVisibility(View.GONE);

                    // Show only for specific submodules
                    if ("Dump".equals(selectedModule)) {
                        if ("None".equals(selectedSubModule)) {
                            candumpContainer.setVisibility(View.VISIBLE);
                            outputContainer.setVisibility(View.VISIBLE);
                            fileContainer.setVisibility(View.VISIBLE);
                            separateLineContainer.setVisibility(View.VISIBLE);
                            whitelistContainer.setVisibility(View.VISIBLE);
                        }
                    }
                    if ("Fuzzer".equals(selectedModule)) {
                        if ("brute".equals(selectedSubModule) || "mutate".equals(selectedSubModule)) {
                            outputContainer.setVisibility(View.VISIBLE);
                            fileContainer.setVisibility(View.VISIBLE);
                            delayContainer.setVisibility(View.VISIBLE);
                            responsesContainer.setVisibility(View.VISIBLE);
                            indexContainer.setVisibility(View.VISIBLE);
                            arbIDContainer.setVisibility(View.VISIBLE);
                            dataContainer.setVisibility(View.VISIBLE);
                        }
                        if ("mutate".equals(selectedSubModule)) {
                            seedContainer.setVisibility(View.VISIBLE);
                        }
                        if ("identify".equals(selectedSubModule) || "replay".equals(selectedSubModule)) {
                            fileContainer.setVisibility(View.VISIBLE);
                            responsesContainer.setVisibility(View.VISIBLE);
                            delayContainer.setVisibility(View.VISIBLE);
                        }
                        if ("replay".equals(selectedSubModule)) {
                            requestsContainer.setVisibility(View.VISIBLE);
                        }
                        if ("random".equals(selectedSubModule)) {
                            idContainer.setVisibility(View.VISIBLE);
                            indexContainer.setVisibility(View.VISIBLE);
                            minContainer.setVisibility(View.VISIBLE);
                            maxContainer.setVisibility(View.VISIBLE);
                            seedContainer.setVisibility(View.VISIBLE);
                            dataContainer.setVisibility(View.VISIBLE);
                            delayContainer.setVisibility(View.VISIBLE);
                            outputContainer.setVisibility(View.VISIBLE);
                            fileContainer.setVisibility(View.VISIBLE);
                        }
                    }
                    if ("Listener".equals(selectedModule)) {
                        if ("None".equals(selectedSubModule)) {
                            reverseContainer.setVisibility(View.VISIBLE);
                        }
                    }
                    if ("module_template".equals(selectedModule)) {
                        if ("None".equals(selectedSubModule)) {
                            idContainer.setVisibility(View.VISIBLE);
                        }
                    }
                    if ("Send".equals(selectedModule)) {
                        if ("file".equals(selectedSubModule)) {
                            delayContainer.setVisibility(View.VISIBLE);
                            loopContainer.setVisibility(View.VISIBLE);
                            fileContainer.setVisibility(View.VISIBLE);
                        }
                        if ("message".equals(selectedSubModule)) {
                            delayContainer.setVisibility(View.VISIBLE);
                            loopContainer.setVisibility(View.VISIBLE);
                            padContainer.setVisibility(View.VISIBLE);
                            messageContainer.setVisibility(View.VISIBLE);
                        }
                    }
                    if ("UDS".equals(selectedModule)) {
                        if ("discovery".equals(selectedSubModule) || "auto".equals(selectedSubModule)) {
                            minContainer.setVisibility(View.VISIBLE);
                            maxContainer.setVisibility(View.VISIBLE);
                            blacklistContainer.setVisibility(View.VISIBLE);
                            autoBlacklistContainer.setVisibility(View.VISIBLE);
                            skipverifyContainer.setVisibility(View.VISIBLE);
                            delayContainer.setVisibility(View.VISIBLE);
                        }
                        if ("services".equals(selectedSubModule) || "dump_dids".equals(selectedSubModule)) {
                            timeoutContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                        }
                        if ("subservices".equals(selectedSubModule)) {
                            timeoutContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                            stypeContainer.setVisibility(View.VISIBLE);
                            dtypeContainer.setVisibility(View.VISIBLE);
                        }
                        if ("ecu_reset".equals(selectedSubModule)) {
                            timeoutContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                            ecuResetTypeContainer.setVisibility(View.VISIBLE);
                        }
                        if ("security_seed".equals(selectedSubModule)) {
                            delayContainer.setVisibility(View.VISIBLE);
                            numberContainer.setVisibility(View.VISIBLE);
                            ecuResetTypeContainer.setVisibility(View.VISIBLE);
                            sessionTypeContainer.setVisibility(View.VISIBLE);
                            securityLevelContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                        }
                        if ("testerpresent".equals(selectedSubModule)) {
                            durationContainer.setVisibility(View.VISIBLE);
                            delayContainer.setVisibility(View.VISIBLE);
                            sprContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                        }
                        if ("dump_dids".equals(selectedSubModule)) {
                            mindidContainer.setVisibility(View.VISIBLE);
                            maxdidContainer.setVisibility(View.VISIBLE);
                        }
                        if ("read_mem".equals(selectedSubModule)) {
                            timeoutContainer.setVisibility(View.VISIBLE);
                            startAddrContainer.setVisibility(View.VISIBLE);
                            memLengthContainer.setVisibility(View.VISIBLE);
                            memSizeContainer.setVisibility(View.VISIBLE);
                            addrByteSizeContainer.setVisibility(View.VISIBLE);
                            memLengthByteSizeContainer.setVisibility(View.VISIBLE);
                            outputContainer.setVisibility(View.VISIBLE);
                            fileContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                        }
                        if ("auto".equals(selectedSubModule)) {
                            timeoutContainer.setVisibility(View.VISIBLE);
                            mindidContainer.setVisibility(View.VISIBLE);
                            maxdidContainer.setVisibility(View.VISIBLE);
                        }
                    }
                    if ("UDS_Fuzz".equals(selectedModule)) {
                        if ("delay_fuzzer".equals(selectedSubModule)) {
                            ecuResetTypeContainer.setVisibility(View.VISIBLE);
                            delayContainer.setVisibility(View.VISIBLE);
                            sessionSeqContainer.setVisibility(View.VISIBLE);
                            seedTargetContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                        }
                        if ("seed_randomness_fuzzer".equals(selectedSubModule)) {
                            ecuResetTypeContainer.setVisibility(View.VISIBLE);
                            ecuResetMethodeContainer.setVisibility(View.VISIBLE);
                            delayContainer.setVisibility(View.VISIBLE);
                            sessionSeqContainer.setVisibility(View.VISIBLE);
                            interDelayContainer.setVisibility(View.VISIBLE);
                            iterationsContainer.setVisibility(View.VISIBLE);
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                        }
                    }
                    if ("XCP".equals(selectedModule)) {
                        if ("discovery".equals(selectedSubModule)) {
                            minContainer.setVisibility(View.VISIBLE);
                            maxContainer.setVisibility(View.VISIBLE);
                            blacklistContainer.setVisibility(View.VISIBLE);
                            autoBlacklistContainer.setVisibility(View.VISIBLE);
                        }
                        if ("info".equals(selectedSubModule) || "commands".equals(selectedSubModule)) {
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                        }
                        if ("dump".equals(selectedSubModule)) {
                            srcContainer.setVisibility(View.VISIBLE);
                            dstContainer.setVisibility(View.VISIBLE);
                            startAddrContainer.setVisibility(View.VISIBLE);
                            lengthContainer.setVisibility(View.VISIBLE);
                            outputContainer.setVisibility(View.VISIBLE);
                            fileContainer.setVisibility(View.VISIBLE);
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
                    case "Dump":
                        runDump(subModule);
                        break;
                    case "Fuzzer":
                        runFuzzer(subModule);
                        break;
                    case "Listener":
                        runListener(subModule);
                        break;
                    case "module_template":
                        runModuleTemplate(subModule);
                        break;
                    case "Send":
                        runSend(subModule);
                        break;
                    case "UDS":
                        runUDS(subModule);
                        break;
                    case "UDS_Fuzz":
                        runUDSFuzz(subModule);
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

        private String getVisibleParam(EditText editText, String prefix) {
            if (editText != null) {
                View container = (View) editText.getParent().getParent();
                if (container.getVisibility() == View.VISIBLE && container.isEnabled()) {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty() && !input.equals(editText.getHint().toString())) {
                        return prefix + input;
                    }
                }
            }
            return "";
        }

        private String getVisibleSpinnerValue(Spinner spinner, ViewGroup container, String prefix) {
            if (container != null && container.getVisibility() == View.VISIBLE && container.isEnabled()) {
                int pos = spinner.getSelectedItemPosition();
                if (pos > 0) { // skip the disabled placeholder (like "Select...")
                    String selected = (String) spinner.getSelectedItem();

                    // If option contains "=", take the part before "="
                    if (selected.contains("=")) {
                        selected = selected.split("=")[0].trim();
                    }

                    return prefix + selected;
                }
            }
            return "";
        }

        private String getVisibleSpinnerOrInputValue(
                Spinner spinner,
                ViewGroup spinnerContainer,
                TextInputLayout inputContainer
        ) {
            if (spinnerContainer.getVisibility() == View.VISIBLE && spinner.getSelectedItemPosition() > 0) {
                String selected = (String) spinner.getSelectedItem();

                // Case 1: Range or list → user must type value
                if (selected.contains("-") || selected.contains(",")) {
                    if (inputContainer.getVisibility() == View.VISIBLE) {
                        EditText editText = inputContainer.getEditText();
                        if (editText != null) {
                            String input = editText.getText().toString().trim();
                            if (!input.isEmpty()) {
                                return " " + input;  // e.g. " 0x40"
                            }
                        }
                    }
                    return ""; // no input provided
                }

                // Case 2: Single discrete value like "1=defaultSession"
                if (selected.contains("=")) {
                    return " " + selected.split("=")[0]; // take part before '=' → "1"
                }
                return " " + selected; // fallback
            }
            return "";
        }

        private void runDump(String dump_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String whitelistValue = getVisibleParam(whitelistContainer.getEditText(), " ");
            String separateLineValue = getVisibleParam(separateLineContainer.getEditText(), " -s ");
            String outputEnabled = "";
            if (outputContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat outputSwitch = outputContainer.findViewById(R.id.btn_toggle_output);
                if (outputSwitch != null && outputSwitch.isChecked()) {
                    String filePath = SelectedFile.getText().toString().trim();
                    if (!filePath.isEmpty()) {
                        outputEnabled = " -f " + filePath;
                    }
                }
            }

            String candumpEnabled = "";
            if (candumpContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat candumpSwitch = candumpContainer.findViewById(R.id.btn_toggle_candump);
                if (candumpSwitch != null && candumpSwitch.isChecked()) {
                    candumpEnabled = " -c";
                }
            }

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " dump";

            if (dump_module.equals("None")) {
                run_cmd(cmdBase + outputEnabled + candumpEnabled + separateLineValue + whitelistValue);
            } else {
                showToast("Unknown dump submodule: " + dump_module);
            }
        }

        private void runFuzzer(String fuzzer_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String outputEnabled = "";
            if (outputContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat outputSwitch = outputContainer.findViewById(R.id.btn_toggle_output);
                if (outputSwitch != null && outputSwitch.isChecked()) {
                    String filePath = SelectedFile.getText().toString().trim();
                    if (!filePath.isEmpty()) {
                        outputEnabled = " -f " + filePath;
                    }
                }
            }

            String responsesEnabled = "";
            if (responsesContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat responsesSwitch = responsesContainer.findViewById(R.id.btn_toggle_responses);
                if (responsesSwitch != null && responsesSwitch.isChecked()) {
                    responsesEnabled = " -responses";
                }
            }

            String requestsEnabled = "";
            if (requestsContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat requestsSwitch = requestsContainer.findViewById(R.id.btn_toggle_requests);
                if (requestsSwitch != null && requestsSwitch.isChecked()) {
                    requestsEnabled = " -requests";
                }
            }

            String selected_file = "";
            if (fileContainer.getVisibility() == View.VISIBLE) {
                String text = SelectedFile.getText().toString().trim();
                if (!text.isEmpty()) {
                    selected_file = text;
                }
            }

            String idValue = getVisibleParam(idContainer.getEditText(), " -id ");
            String seedValue = getVisibleParam(seedContainer.getEditText(), " -seed ");
            String minValue = getVisibleParam(minContainer.getEditText(), " -min ");
            String maxValue = getVisibleParam(minContainer.getEditText(), " -max ");
            String delayValue = getVisibleParam(delayContainer.getEditText(), " -delay ");
            String indexValue = getVisibleParam(indexContainer.getEditText(), " -index ");
            String arbIDValue = getVisibleParam(arbIDContainer.getEditText(), " ");
            String dataValue = getVisibleParam(dataContainer.getEditText(), " ");
            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " fuzzer ";

            switch (fuzzer_module) {
                case "brute":
                    run_cmd(cmdBase + "brute" + outputEnabled + responsesEnabled + indexValue + delayValue + arbIDValue + dataValue);
                    break;
                case "identify":
                    run_cmd(cmdBase + "identify" + responsesEnabled + delayValue + " " + selected_file);
                    break;
                case "mutate":
                    run_cmd(cmdBase + "mutate" + responsesEnabled + outputEnabled + seedValue + indexValue + delayValue + arbIDValue + dataValue);
                    break;
                case "random":
                    run_cmd(cmdBase + "random" + idValue + dataValue + outputEnabled + minValue + maxValue + indexValue + seedValue + delayValue);
                    break;
                case "replay":
                    run_cmd(cmdBase + "replay" + requestsEnabled + responsesEnabled + delayValue + " " + selected_file);
                    break;
                default:
                    showToast("Unknown fuzzer submodule: " + fuzzer_module);
            }
        }

        private void runListener(String listener_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String reverseEnabled = "";
            if (reverseContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat reverseSwitch = reverseContainer.findViewById(R.id.btn_toggle_reverse);
                if (reverseSwitch != null && reverseSwitch.isChecked()) {
                    reverseEnabled = " -r";
                }
            }

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " listener";

            if (listener_module.equals("None")) {
                run_cmd(cmdBase + reverseEnabled);
            } else {
                showToast("Unknown listener submodule: " + listener_module);
            }
        }

        private void runModuleTemplate(String moduleTemplate_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String idValue = getVisibleParam(idContainer.getEditText(), " -id ");
            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " module_template";

            if (moduleTemplate_module.equals("None")) {
                run_cmd(cmdBase + idValue);
            } else {
                showToast("Unknown module_template submodule: " + moduleTemplate_module);
            }
        }

        private void runSend(String send_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String selected_message = "";
            if (messageContainer.getVisibility() == View.VISIBLE) {
                String text = SelectedMessage.getText().toString().trim();
                if (!text.isEmpty()) {
                    selected_message = text;
                }
            }
            String selected_file = "";
            if (fileContainer.getVisibility() == View.VISIBLE) {
                String text = SelectedFile.getText().toString().trim();
                if (!text.isEmpty()) {
                    selected_file = text;
                }
            }
            String loopEnabled = "";
            if (loopContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat loopSwitch = loopContainer.findViewById(R.id.btn_toggle_loop);
                if (loopSwitch != null && loopSwitch.isChecked()) {
                    loopEnabled = " --loop";
                }
            }
            String padEnabled = "";
            if (padContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat padSwitch = padContainer.findViewById(R.id.btn_toggle_pad);
                if (padSwitch != null && padSwitch.isChecked()) {
                    padEnabled = " --pad";
                }
            }
            String delayValue = getVisibleParam(delayContainer.getEditText(), " --delay ");
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
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String skipverifyEnabled = "";
            if (skipverifyContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat skipverifySwitch = skipverifyContainer.findViewById(R.id.btn_toggle_skipverify);
                if (skipverifySwitch != null && skipverifySwitch.isChecked()) {
                    skipverifyEnabled = " --skipverify";
                }
            }

            String sprEnabled = "";
            if (sprContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat sprSwitch = sprContainer.findViewById(R.id.btn_toggle_spr);
                if (sprSwitch != null && sprSwitch.isChecked()) {
                    sprEnabled = " -spr";
                }
            }

            String outputEnabled = "";
            if (outputContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat outputSwitch = outputContainer.findViewById(R.id.btn_toggle_output);
                if (outputSwitch != null && outputSwitch.isChecked()) {
                    String filePath = SelectedFile.getText().toString().trim();
                    if (!filePath.isEmpty()) {
                        outputEnabled = " --outfile " + filePath;
                    }
                }
            }

            String dtypeValue = getVisibleParam(dtypeContainer.getEditText(), " ");
            String stypeValue = getVisibleParam(stypeContainer.getEditText(), " ");
            String srcValue = getVisibleParam(srcContainer.getEditText(), " ");
            String dstValue = getVisibleParam(dstContainer.getEditText(), " ");
            String mindidValue = getVisibleParam(mindidContainer.getEditText(), " --min_did ");
            String maxdidValue = getVisibleParam(maxdidContainer.getEditText(), " --max_did ");
            String minValue = getVisibleParam(minContainer.getEditText(), " -min ");
            String maxValue = getVisibleParam(maxContainer.getEditText(), " -max ");
            String delayValue = getVisibleParam(delayContainer.getEditText(), " -d ");
            String durationValue = getVisibleParam(durationContainer.getEditText(), " --duration ");
            String timeoutValue = getVisibleParam(timeoutContainer.getEditText(), " -t ");
            String numberValue = getVisibleParam(numberContainer.getEditText(), " --num ");
            String blacklistValue = getVisibleParam(blacklistContainer.getEditText(), " --blacklist ");
            String autoBlacklistValue = getVisibleParam(autoBlacklistContainer.getEditText(), " --autoblacklist ");
            String startAddrValue = getVisibleParam(startAddrContainer.getEditText(), " --start_addr ");
            String memLengthValue = getVisibleParam(memLengthContainer.getEditText(), " --mem_length ");
            String memSizeValue = getVisibleParam(memSizeContainer.getEditText(), " --mem_size ");
            String addrByteSizeValue = getVisibleParam(addrByteSizeContainer.getEditText(), " --address_byte_size ");
            String memLengthByteSizeValue = getVisibleParam(memLengthByteSizeContainer.getEditText(), " --memory_length_byte_size ");
            String ecuResetValue = getVisibleSpinnerValue(ecuResetTypeSpinner, ecuResetTypeContainer, " ");
            String sessiontypeValue = getVisibleSpinnerOrInputValue(
                    sessionTypeSpinner,
                    sessionTypeContainer,
                    sessionTypeInputContainer
            );

            String levelValue = getVisibleSpinnerOrInputValue(
                    securityLevelSpinner,
                    securityLevelContainer,
                    securityLevelInputContainer
            );

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " uds ";

            switch (uds_module) {
                case "discovery":
                    run_cmd(cmdBase + "discovery" + minValue + maxValue + delayValue + blacklistValue + autoBlacklistValue + skipverifyEnabled);
                    break;
                case "services":
                    run_cmd(cmdBase + "services" + timeoutValue + srcValue + dstValue);
                    break;
                case "subservices":
                    run_cmd(cmdBase + "subservices" + timeoutValue + dtypeValue + stypeValue + srcValue + dstValue);
                    break;
                case "ecu_reset":
                    run_cmd(cmdBase + "ecu_reset" + timeoutValue + ecuResetValue + srcValue + dstValue);
                    break;
                case "testerpresent":
                    run_cmd(cmdBase + "testerpresent" + delayValue + durationValue + sprEnabled + srcValue);
                    break;
                case "security_seed":
                    String resetValue = getVisibleSpinnerValue(ecuResetTypeSpinner, ecuResetTypeContainer, " --reset ");
                    run_cmd(cmdBase + "security_seed" + resetValue + delayValue + numberValue + sessiontypeValue + levelValue + srcValue + dstValue);
                    break;
                case "dump_dids":
                    run_cmd(cmdBase + "dump_dids" + timeoutValue + mindidValue + maxdidValue + srcValue + dstValue);
                    break;
                case "read_mem":
                    run_cmd(cmdBase + "read_mem" + timeoutValue + startAddrValue + memLengthValue + memSizeValue + addrByteSizeValue + memLengthByteSizeValue + outputEnabled + srcValue + dstValue);
                    break;
                case "auto":
                    run_cmd(cmdBase + "auto" + minValue + maxValue + blacklistValue + autoBlacklistValue + skipverifyEnabled + delayValue + timeoutValue + mindidValue + maxdidValue);
                    break;
                default:
                    showToast("Unknown UDS submodule: " + uds_module);
            }
        }

        private void runUDSFuzz(String uds_fuzz_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String ecuResetValue = getVisibleSpinnerValue(ecuResetTypeSpinner, ecuResetTypeContainer, " --reset ");
            String ecuResetMethodeValue = getVisibleSpinnerValue(ecuResetMethodeSpinner, ecuResetMethodeContainer, " --reset_method ");
            String delayValue = getVisibleParam(delayContainer.getEditText(), " -d ");
            String sessionSeqValue = getVisibleParam(sessionSeqContainer.getEditText(), " ");
            String seedTargetValue = getVisibleParam(seedTargetContainer.getEditText(), " ");
            String srcValue = getVisibleParam(srcContainer.getEditText(), " ");
            String dstValue = getVisibleParam(dstContainer.getEditText(), " ");
            String interDelayValue = getVisibleParam(interDelayContainer.getEditText(), " --inter_delay ");
            String iterationsValue = getVisibleParam(iterationsContainer.getEditText(), " --iter ");

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " uds_fuzz ";

            switch (uds_fuzz_module) {
                case "delay_fuzzer":
                    run_cmd(cmdBase + "delay_fuzzer" + ecuResetValue + delayValue + sessionSeqValue + seedTargetValue + srcValue + dstValue);
                    break;
                case "seed_randomness_fuzzer":
                    run_cmd(cmdBase + "seed_randomness_fuzzer" + iterationsValue + ecuResetValue + interDelayValue + ecuResetMethodeValue + delayValue + sessionSeqValue + srcValue + dstValue);
                    break;
                default:
                    showToast("Unknown UDS_Fuzz submodule: " + uds_fuzz_module);
            }
        }

        private void runXCP(String xcp_module) {
            if (selected_caniface == null || selected_caniface.isEmpty() || selected_caniface.equals("Interfaces")) {
                showToast("Please choose a CAN Interface!");
                return;
            }

            String outputEnabled = "";
            if (outputContainer.getVisibility() == View.VISIBLE) {
                SwitchCompat outputSwitch = outputContainer.findViewById(R.id.btn_toggle_output);
                if (outputSwitch != null && outputSwitch.isChecked()) {
                    String filePath = SelectedFile.getText().toString().trim();
                    if (!filePath.isEmpty()) {
                        outputEnabled = " -f " + filePath;
                    }
                }
            }

            String startAddrValue = getVisibleParam(startAddrContainer.getEditText(), " ");
            String lengthValue = getVisibleParam(lengthContainer.getEditText(), " ");
            String srcValue = getVisibleParam(srcContainer.getEditText(), " ");
            String dstValue = getVisibleParam(dstContainer.getEditText(), " ");
            String minValue = getVisibleParam(minContainer.getEditText(), " -min ");
            String maxValue = getVisibleParam(maxContainer.getEditText(), " -max ");
            String blacklistValue = getVisibleParam(blacklistContainer.getEditText(), " -blacklist ");
            String autoBlacklistValue = getVisibleParam(autoBlacklistContainer.getEditText(), " -autoblacklist ");

            String cmdBase = "printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " xcp ";

            switch (xcp_module) {
                case "discovery":
                    run_cmd(cmdBase + "discovery" + minValue + maxValue + blacklistValue + autoBlacklistValue);
                    break;
                case "info":
                    run_cmd(cmdBase + "info" + srcValue + dstValue);
                    break;
                case "commands":
                    run_cmd(cmdBase + "commands" + srcValue + dstValue);
                    break;
                case "dump":
                    run_cmd(cmdBase + "dump" + srcValue + dstValue + startAddrValue + lengthValue + outputEnabled);
                    break;
                default:
                    showToast("Unknown XCP submodule: " + xcp_module);
            }
        }
    }

    public static class SIMFragment extends CARsenalFragment {
        private final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private static final String ICSIM_SCRIPT_PATH = "/opt/car_hacking/icsim_service.sh";
        private static final String UDSIM_SCRIPT_PATH = "/opt/car_hacking/udsim_service.sh";
        private static final long SHORT_DELAY = 1000;
        private static final long LONG_DELAY = 2000;
        private FrameLayout floatingContainer;
        private String selected_caniface = "";
        private TextInputEditText udsimConfigEdit;

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.carsenal_sim, container, false);

            Spinner spinner = rootView.findViewById(R.id.device_interface);
            ImageButton refreshBtn = rootView.findViewById(R.id.refreshUSB);

            // Setup device interface spinner
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

            // Level Spinner
            Spinner levelList = rootView.findViewById(R.id.level_spinner);
            levelList.setAdapter(getStringArrayAdapter());
            levelList.setSelection(0);
            levelList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    if (pos != 0) {
                        sharedpreferences.edit()
                                .putString("level_selected", parent.getItemAtPosition(pos).toString())
                                .apply();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Todo : Move perm to PermissionCheck
            if (!Settings.canDrawOverlays(requireContext())) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            }

            udsimConfigEdit = rootView.findViewById(R.id.udsim_config_path);
            ImageButton udsimBrowseBtn = rootView.findViewById(R.id.udsim_config_browse);

            udsimBrowseBtn.setOnClickListener(v -> {
                RootFileBrowserDialog browser = new RootFileBrowserDialog(requireContext(), udsimConfigEdit::setText);
                browser.show();
            });

            WebView icsimView = ICSIMWebViewHolder.getICSIMWebView(requireContext());
            WebView controlsView = ICSIMWebViewHolder.getControlsWebView(requireContext());
            WebView udsimView = ICSIMWebViewHolder.getUDSIMWebView(requireContext());

            Button floatICSIM = rootView.findViewById(R.id.floating_icsim);
            floatICSIM.setOnClickListener(v -> toggleFloatingICSIM(icsimView, udsimView));

            FrameLayout icsimContainer = rootView.findViewById(R.id.icsim_container);
            FrameLayout controlsContainer = rootView.findViewById(R.id.controls_container);
            controlsContainer.setVisibility(View.GONE);
            FrameLayout udsimContainer = rootView.findViewById(R.id.udsim_container);

            // Remove from previous parent if needed
            if (icsimView.getParent() != null) ((ViewGroup) icsimView.getParent()).removeView(icsimView);
            if (controlsView.getParent() != null) ((ViewGroup) controlsView.getParent()).removeView(controlsView);
            if (udsimView.getParent() != null) ((ViewGroup) udsimView.getParent()).removeView(udsimView);

            // Attach WebViews once
            icsimContainer.addView(icsimView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            controlsContainer.addView(controlsView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            udsimContainer.addView(udsimView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            // Buttons
            rootView.findViewById(R.id.run_icsim).setOnClickListener(v -> runICSIM(icsimView, controlsView, levelList, udsimView));
            rootView.findViewById(R.id.stop_icsim).setOnClickListener(v -> stopICSIM(icsimView, controlsView, udsimView));

            // Auto-restore ICSim session if running
            executorService.submit(() -> {
                if (isICSIMRunning()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Remove from floating if present
                        if (floatingContainer != null) removeFloatingWebViews(icsimView, udsimView);

                        icsimContainer.removeAllViews();
                        controlsContainer.removeAllViews();
                        udsimContainer.removeAllViews();

                        icsimContainer.addView(icsimView, new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        ));
                        controlsContainer.addView(controlsView, new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        ));
                        udsimContainer.addView(udsimView, new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        ));

                        icsimView.loadUrl("http://localhost:6080/vnc.html?autoconnect=true&resize=scale&view_only=true");
                        controlsView.loadUrl("http://localhost:6081/vnc.html?autoconnect=true&resize=scale");
                        udsimView.loadUrl("http://localhost:6082/vnc.html?autoconnect=true&resize=scale");

                        Toast.makeText(requireContext(), "Restored ICSim and UDSim sessions...", Toast.LENGTH_SHORT).show();
                    });
                }
            });

            return rootView;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            // Ensure floating overlay is removed and WebViews are cleaned up
            try {
                if (floatingContainer != null) {
                    WindowManager wm = (WindowManager) requireContext().getSystemService(Context.WINDOW_SERVICE);
                    wm.removeViewImmediate(floatingContainer);
                    floatingContainer = null;
                }
            } catch (Exception ignored) {}
            ICSIMWebViewHolder.release();
        }

        private void runICSIM(WebView icsimView, WebView controlsView, Spinner levelList, WebView udsimView) {
            if (!selected_caniface.isEmpty() && !selected_caniface.equals("Interfaces")) {

                String udsimConfig = (udsimConfigEdit != null && udsimConfigEdit.getText() != null)
                        ? udsimConfigEdit.getText().toString().trim()
                        : "";
                String combinedCmd = "su -c 'sh " + ICSIM_SCRIPT_PATH + " " + selected_caniface;
                String levelValue = getVisibleParam(levelList);

                if (!levelValue.isEmpty()) {
                    combinedCmd += levelValue;
                }
                combinedCmd += " && sh " + UDSIM_SCRIPT_PATH + " " + selected_caniface;
                if (!udsimConfig.isEmpty()) {
                    combinedCmd += " -c \"" + udsimConfig + "\"";
                }
                combinedCmd += "'";
                run_cmd_inapp(combinedCmd);
                showToast("Running ICSim and UDSim...");

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    icsimView.loadUrl("http://localhost:6080/vnc.html?autoconnect=true&resize=scale&view_only=true");
                    controlsView.loadUrl("http://localhost:6081/vnc.html?autoconnect=true&resize=scale");
                    udsimView.loadUrl("http://localhost:6082/vnc.html?autoconnect=true&resize=scale");
                }, SHORT_DELAY + LONG_DELAY);

            } else {
                showToast("Please set a CAN interface!");
            }
        }

        private void stopICSIM(WebView icsimView, WebView controlsView, WebView udsimView) {
            run_cmd_inapp("su -c 'sh " + ICSIM_SCRIPT_PATH + " stop;sh " + UDSIM_SCRIPT_PATH + " stop'");
            showToast("Stopping ICSim and UDSim...");
            icsimView.setBackgroundColor(Color.BLACK);
            icsimView.loadUrl("about:blank");
            controlsView.setBackgroundColor(Color.BLACK);
            controlsView.loadUrl("about:blank");
            udsimView.setBackgroundColor(Color.BLACK);
            udsimView.loadUrl("about:blank");
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        private void toggleFloatingICSIM(WebView icsimView, WebView udsimView) {
            if (floatingContainer == null) {
                // Show both inside floating window
                showFloatingWebView(icsimView, udsimView);
            } else {
                // Restore both back to their containers
                removeFloatingWebViews(icsimView, udsimView);
            }
        }

        // Remove floating container and restore BOTH WebViews
        private void removeFloatingWebViews(WebView icsimView, WebView udsimView) {
            if (floatingContainer != null) {
                WindowManager wm = (WindowManager) requireContext().getSystemService(Context.WINDOW_SERVICE);
                try {
                    wm.removeView(floatingContainer);
                } catch (IllegalArgumentException ignored) {}
                floatingContainer = null;

                // Restore ICSIM WebView
                FrameLayout icsimContainer = requireView().findViewById(R.id.icsim_container);
                if (icsimView.getParent() != null) ((ViewGroup) icsimView.getParent()).removeView(icsimView);
                icsimContainer.addView(icsimView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

                // Restore UDSIM WebView
                FrameLayout udsimContainer = requireView().findViewById(R.id.udsim_container);
                if (udsimView.getParent() != null) ((ViewGroup) udsimView.getParent()).removeView(udsimView);
                udsimContainer.addView(udsimView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
            }
        }

        // Show BOTH ICSIM + UDSIM WebViews in floating overlay
        @RequiresApi(api = Build.VERSION_CODES.O)
        private void showFloatingWebView(WebView icsimView, WebView udsimView) {
            if (floatingContainer != null) return;

            final WindowManager wm = (WindowManager) requireContext().getSystemService(Context.WINDOW_SERVICE);

            // Detach WebViews if they already have a parent
            if (icsimView.getParent() != null) ((ViewGroup) icsimView.getParent()).removeView(icsimView);
            if (udsimView.getParent() != null) ((ViewGroup) udsimView.getParent()).removeView(udsimView);

            floatingContainer = new FrameLayout(requireContext());
            int floatingInitialWidth = 800;
            int floatingInitialHeight = 600;
            floatingContainer.setLayoutParams(new FrameLayout.LayoutParams(floatingInitialWidth, floatingInitialHeight));

            MaterialCardView cardView = new MaterialCardView(requireContext());
            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            );
            cardParams.setMargins(20, 0, 20, 10);
            cardView.setLayoutParams(cardParams);
            cardView.setRadius(16f);
            cardView.setCardElevation(8f);
            cardView.setStrokeWidth(4);
            cardView.setPreventCornerOverlap(false);

            // 🔹 Vertical layout wrapper for ICSIM + UDSIM
            LinearLayout verticalWrapper = new LinearLayout(requireContext());
            verticalWrapper.setOrientation(LinearLayout.VERTICAL);
            verticalWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
            ));

            // Inner wrapper for ICSIM
            FrameLayout icsimWrapper = new FrameLayout(requireContext());
            icsimWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ));
            icsimWrapper.addView(icsimView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ));

            // Inner wrapper for UDSIM
            FrameLayout udsimWrapper = new FrameLayout(requireContext());
            udsimWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ));
            udsimWrapper.addView(udsimView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ));

            // Add both wrappers to vertical layout
            verticalWrapper.addView(icsimWrapper);
            verticalWrapper.addView(udsimWrapper);

            // Transparent overlay for touch handling
            View overlay = new View(requireContext());
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ));
            overlay.setBackgroundColor(Color.TRANSPARENT);

            // Add everything inside card
            cardView.addView(verticalWrapper);
            cardView.addView(overlay);
            floatingContainer.addView(cardView);

            // Close button
            ImageButton closeBtn = new ImageButton(requireContext());
            closeBtn.setImageResource(R.drawable.ic_close_red);
            FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(80, 80);
            closeParams.gravity = Gravity.TOP | Gravity.END;
            closeBtn.setLayoutParams(closeParams);
            floatingContainer.addView(closeBtn);
            ViewCompat.setElevation(closeBtn, 16f);

            closeBtn.setOnClickListener(v -> removeFloatingWebViews(icsimView, udsimView));

            final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    floatingInitialWidth, floatingInitialHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            layoutParams.gravity = Gravity.TOP | Gravity.START;
            layoutParams.x = 100;
            layoutParams.y = 100;

            wm.addView(floatingContainer, layoutParams);

            // Touch for move/resize
            overlay.setOnTouchListener(new View.OnTouchListener() {
                private float offsetX, offsetY;
                private float startTouchX, startTouchY;
                private int startWidth, startHeight;
                private float startDist = 0;
                private boolean isResizing = false;

                private float distance(MotionEvent e) {
                    float dx = e.getX(0) - e.getX(1);
                    float dy = e.getY(0) - e.getY(1);
                    return (float) Math.sqrt(dx * dx + dy * dy);
                }

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    final int CLICK_THRESHOLD = 10; // pixels

                    if (event.getPointerCount() == 2) {
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_POINTER_DOWN:
                                startDist = distance(event);
                                startWidth = layoutParams.width;
                                startHeight = layoutParams.height;
                                isResizing = true;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                if (isResizing) {
                                    float scale = distance(event) / startDist;
                                    layoutParams.width = (int) (startWidth * scale);
                                    layoutParams.height = (int) (startHeight * scale);
                                    wm.updateViewLayout(floatingContainer, layoutParams);
                                }
                                break;
                            case MotionEvent.ACTION_POINTER_UP:
                                isResizing = false;
                                break;
                        }
                        return true;
                    } else if (event.getPointerCount() == 1) {
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                offsetX = event.getRawX() - layoutParams.x;
                                offsetY = event.getRawY() - layoutParams.y;
                                startTouchX = event.getRawX();
                                startTouchY = event.getRawY();
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                layoutParams.x = (int) (event.getRawX() - offsetX);
                                layoutParams.y = (int) (event.getRawY() - offsetY);
                                wm.updateViewLayout(floatingContainer, layoutParams);
                                return true;
                            case MotionEvent.ACTION_UP:
                                float dx = Math.abs(event.getRawX() - startTouchX);
                                float dy = Math.abs(event.getRawY() - startTouchY);
                                if (dx < CLICK_THRESHOLD && dy < CLICK_THRESHOLD) {
                                    v.performClick();
                                }
                                return true;
                        }
                    }
                    return false;
                }
            });
        }

        private boolean isICSIMRunning() {
            String output = exe.RunAsChrootOutput("pgrep -f icsim_service.sh");
            return output != null && !output.trim().isEmpty();
        }

        @NonNull
        private ArrayAdapter<String> getStringArrayAdapter() {
            final String[] levelOptions = {"Level", "0", "1", "2"};

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                    android.R.layout.simple_spinner_item, levelOptions) {
                @Override
                public boolean isEnabled(int position) { return position != 0; }

                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    tv.setTextColor(position == 0 ? Color.GRAY : Color.WHITE);
                    return view;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return adapter;
        }

        private String getVisibleParam(View view) {
            if (view.getVisibility() == View.VISIBLE && view instanceof Spinner) {
                String selected = ((Spinner) view).getSelectedItem().toString().trim();
                if (!selected.isEmpty() && !selected.equals("Level")) return " -l " + selected;
            }
            return "";
        }

        public static class ICSIMWebViewHolder {
            private static WeakReference<WebView> icsimWebViewRef;
            private static WeakReference<WebView> controlsWebViewRef;
            private static WeakReference<WebView> udsimWebViewRef;

            public static WebView getICSIMWebView(Context context) {
                WebView w = icsimWebViewRef != null ? icsimWebViewRef.get() : null;
                if (w == null) {
                    w = new WebView(context.getApplicationContext());
                    setupWebView(w);
                    icsimWebViewRef = new WeakReference<>(w);
                }
                return w;
            }

            public static WebView getControlsWebView(Context context) {
                WebView w = controlsWebViewRef != null ? controlsWebViewRef.get() : null;
                if (w == null) {
                    w = new WebView(context.getApplicationContext());
                    setupWebView(w);
                    controlsWebViewRef = new WeakReference<>(w);
                }
                return w;
            }

            public static WebView getUDSIMWebView(Context context) {
                WebView w = udsimWebViewRef != null ? udsimWebViewRef.get() : null;
                if (w == null) {
                    w = new WebView(context.getApplicationContext());
                    setupWebView(w);
                    udsimWebViewRef = new WeakReference<>(w);
                }
                return w;
            }

            public static void release() {
                destroyRef(icsimWebViewRef);
                destroyRef(controlsWebViewRef);
                destroyRef(udsimWebViewRef);
                icsimWebViewRef = null;
                controlsWebViewRef = null;
                udsimWebViewRef = null;
            }

            private static void destroyRef(WeakReference<WebView> ref) {
                if (ref != null) {
                    WebView w = ref.get();
                    if (w != null) {
                        try {
                            w.loadUrl("about:blank");
                            w.stopLoading();
                            w.clearHistory();
                            w.removeAllViews();
                            w.destroy();
                        } catch (Exception ignored) {}
                    }
                    ref.clear();
                }
            }

            @SuppressLint("SetJavaScriptEnabled")
            private static void setupWebView(WebView webView) {
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setLoadWithOverviewMode(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setBuiltInZoomControls(true);
                webView.getSettings().setDisplayZoomControls(false);
                webView.setWebViewClient(new WebViewClient());
                webView.setBackgroundColor(Color.BLACK);
            }
        }
    }

    public static class CANMSFFragment extends CARsenalFragment {
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
            View rootView = inflater.inflate(R.layout.carsenal_msf, container, false);

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

            // ELM327 Relay
            ImageButton elm327relayButton = rootView.findViewById(R.id.run_relay);

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
                Integer resIdObj = MODULE_INFO_STRING_IDS.get(moduleNameKey);

                if (resIdObj == null || resIdObj == 0) {
                    showToast("No info available for this module");
                    return;
                }

                String moduleInfo = getString(resIdObj);

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

                String moduleKey = selected_module.replace(".rb", "").toLowerCase().trim();
                Integer optionsStringIdObj = MODULE_SET_STRING_IDS.get(moduleKey);

                if (optionsStringIdObj == null || optionsStringIdObj == 0) {
                    infoText.setVisibility(View.VISIBLE);
                    infoText.setText(R.string.can_no_options_for_module);
                    return;
                }

                String optionsText = getString(optionsStringIdObj);
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
                        TextViewCompat.setTextAppearance(header, android.R.style.TextAppearance_Medium);
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

            boolean inappterm;
            inappterm = sharedpreferences.getBoolean("inapp_terminal_enabled", false);
            Button msfBtn = rootView.findViewById(R.id.msfconsole_start);
            msfBtn.setOnClickListener(v -> executorService.submit(() -> {
                if (inappterm) {
                    // in-app terminal
                    run_cmd_inapp("msfconsole -q");
                } else {
                    // use screen if inapp disabled
                    String externalCmd =
                            "msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n); "
                                    + "if [ -n \"$msfsession\" ]; then "
                                    + "screen -wipe; screen -d \"$msfsession\"; screen -r \"$msfsession\"; "
                                    + "else screen -wipe; screen -S msf -m msfconsole; exit; fi";
                    run_cmd(externalCmd);
                }
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

                // Build a list of commands (one command per entry)
                List<String> commands = new ArrayList<>();
                String moduleName = selected_module.replace(".rb", "");
                // outside app term handler
                StringBuilder msfCmd = new StringBuilder();

                if (moduleName.equals("connect")) {
                    // outside term
                    msfCmd.append("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n);screen -S $msfsession -X stuff \"use auxiliary/client/hwbridge/")
                            .append(moduleName)
                            .append("`echo -ne '\\015'`");
                    // inapp term
                    commands.add("use auxiliary/client/hwbridge/" + moduleName);
                } else if (moduleName.equals("local_hwbridge")) {
                    // outside term
                    msfCmd.append("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n);screen -S $msfsession -X stuff \"use auxiliary/server/")
                            .append(moduleName)
                            .append("`echo -ne '\\015'`");
                    // inapp term
                    commands.add("use auxiliary/server/" + moduleName);
                } else {
                    // outside app term append run append module
                    msfCmd.append("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n);screen -S $msfsession -X stuff \"use post/hardware/automotive/")
                            .append(moduleName)
                            .append("`echo -ne '\\015'`");
                    // inapp term
                    commands.add("use post/hardware/automotive/" + moduleName);
                }

                for (Map.Entry<String, EditText> entry : userInputs.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue().getText().toString().trim();

                    if (!value.isEmpty()) {
                        // sanitize single quotes so the value can be safely single-quoted on the shell
                        String sanitized = value.replace("'", "'\"'\"'");
                        // outside term
                        msfCmd.append("set ").append(key.toUpperCase()).append(" '").append(sanitized).append("'`echo -ne '\\015'`");
                        // inapp term
                        commands.add("set " + key.toUpperCase() + " '" + sanitized + "'");
                    }
                }

                // final run command
                // outside term
                msfCmd.append("run\"`echo -ne '\\015'`;screen -d -r $msfsession;exit");
                // inapp term
                commands.add("run");

                // execute commands one-by-one on the background executor
                executorService.submit(() -> {
                    if (inappterm) {
                        // in-app terminal
                        for (String cmd : commands) {
                            run_cmd_inapp(cmd);
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ignored) { }
                        }
                    } else {
                        // use screen if inapp disabled
                        run_cmd(msfCmd.toString());
                    }
                });
            });

            return rootView;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    // Interfaces Spinner (CAN-USB usb device detection only) + Refresh button (ICSim WebView Include)
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
                        : "ip -o link show | awk -F': ' '{print $2}' | grep -E '^(can|vcan|slcan)[0-9]+$';" +
                        "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'";

                String result = exe.RunAsChrootOutput(command);

                ArrayList<String> deviceIfaces = new ArrayList<>();
                deviceIfaces.add(onlyUsbDevices ? "USB Devices" : "Interfaces");

                if (result != null && !result.trim().isEmpty()) {
                    deviceIfaces.addAll(Arrays.asList(result.split("\n")));
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, deviceIfaces) {
                        @Override
                        public boolean isEnabled(int position) { return position != 0; }

                        @NonNull
                        @Override
                        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                            View view = super.getDropDownView(position, convertView, parent);
                            TextView tv = (TextView) view;
                            tv.setTextColor(position == 0 ? Color.GRAY : Color.WHITE);
                            return view;
                        }
                    };

                    spinner.setAdapter(adapter);

                    // Restore previous selection
                    String prevIface = sharedPreferences.getString(sharedPrefKey + "_name", null);
                    int selectionIndex = 0;
                    if (prevIface != null && deviceIfaces.contains(prevIface)) {
                        selectionIndex = deviceIfaces.indexOf(prevIface);
                    } else if (deviceIfaces.size() > 1) {
                        selectionIndex = 1;
                    }

                    spinner.setSelection(selectionIndex);
                    callback.onInterfaceSelected(deviceIfaces.get(selectionIndex));

                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            if (pos != 0) {
                                String selected = parentView.getItemAtPosition(pos).toString();
                                sharedPreferences.edit().putString(sharedPrefKey + "_name", selected).apply();
                                callback.onInterfaceSelected(selected);
                            }
                        }
                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            callback.onInterfaceSelected(deviceIfaces.get(0));
                        }
                    });

                    // Show last detected non-CAN device
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

            // Refresh button reloads WebViews safely
            if (refreshButton != null) {
                refreshButton.setOnClickListener(v -> {
                    Toast.makeText(context, "Refreshing Devices...", Toast.LENGTH_SHORT).show();
                    executorService.submit(loadInterfaces);

                    if (context instanceof Activity) {
                        Activity activity = (Activity) context;

                        TabLayout tabLayout = activity.findViewById(R.id.tabLayoutCAN);
                        int selectedTabIndex = tabLayout.getSelectedTabPosition();

                        if (selectedTabIndex == 4) { // Only run ICSim refresh on Simulator tab
                            FrameLayout icsimContainer = activity.findViewById(R.id.icsim_container);
                            FrameLayout controlsContainer = activity.findViewById(R.id.controls_container);
                            FrameLayout udsimContainer = activity.findViewById(R.id.udsim_container);

                            WebView icsimView = SIMFragment.ICSIMWebViewHolder.getICSIMWebView(context);
                            WebView controlsView = SIMFragment.ICSIMWebViewHolder.getControlsWebView(context);
                            WebView udsimView = SIMFragment.ICSIMWebViewHolder.getUDSIMWebView(context);

                            // Remove from any old parent
                            if (icsimView.getParent() != null) ((ViewGroup) icsimView.getParent()).removeView(icsimView);
                            if (controlsView.getParent() != null) ((ViewGroup) controlsView.getParent()).removeView(controlsView);
                            if (udsimView.getParent() != null) ((ViewGroup) udsimView.getParent()).removeView(udsimView);

                            // Re-add to containers
                            if (icsimContainer != null && controlsContainer != null && udsimContainer != null) {
                                icsimContainer.addView(icsimView, new FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                ));
                                controlsContainer.addView(controlsView, new FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                ));
                                udsimContainer.addView(udsimView, new FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                ));

                                // Reload only if ICSIM process is running
                                String output = exe.RunAsChrootOutput("ps aux | pgrep 'icsim'");
                                if (output != null && !output.isEmpty()) {
                                    icsimView.loadUrl("http://localhost:6080/vnc.html?autoconnect=true&resize=scale&view_only=true");
                                    controlsView.loadUrl("http://localhost:6081/vnc.html?autoconnect=true&resize=scale");
                                    udsimView.loadUrl("http://localhost:6082/vnc.html?autoconnect=true&resize=scale");
                                }

                                Toast.makeText(context, "Refreshing ICSim and UDSim display...", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }

            executorService.submit(loadInterfaces);
        }
    }

    // Custom Browse Button : Chroot
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

    // Helper: open TerminalFragment with an initial command; if not possible, fallback to legacy bridge
    public void run_cmd_inapp(@NonNull String cmd) {
        Activity act = getActivity();
        Boolean inappterm;
        inappterm = sharedpreferences.getBoolean("inapp_terminal_enabled", false);
        if (inappterm) {
            try {
                if (act instanceof androidx.appcompat.app.AppCompatActivity) {
                    androidx.appcompat.app.AppCompatActivity app = (androidx.appcompat.app.AppCompatActivity) act;
                    TerminalFragment tf = TerminalFragment.newInstanceWithCommand(R.id.terminal_item, cmd);
                    app.getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container, tf)
                            .addToBackStack(null)
                            .commitAllowingStateLoss();
                    return;
                }
            } catch (Throwable t) {
                Log.d(TAG, "openTerminalWithCommand fallback due to: " + t.getMessage());
            }
        } else {
            // Fallback to previous behavior using NhTerm bridge
            run_cmd(cmd);
        }

    }

    public boolean isInAppTerminalAvailable() {
        Activity act = getActivity();
        return act instanceof androidx.appcompat.app.AppCompatActivity;
    }
}
