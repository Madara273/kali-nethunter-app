package com.offsec.nethunter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
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
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.BootKali;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

public class CANFragment extends Fragment {
    private static final String TAG = "CANFragment";
    private static SharedPreferences sharedpreferences;
    private Context context;
    private Activity activity;
    private static final String ARG_SECTION_NUMBER = "section_number";

    public static CANFragment newInstance(int sectionNumber) {
        CANFragment fragment = new CANFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called");
        sharedpreferences = requireActivity().getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        super.onCreate(savedInstanceState);
        activity = getActivity();
        context = getContext();
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

            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.documentation:
                        sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                        RunDocumentation();
                        return true;
                    case R.id.setup:
                        sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                        RunSetup();
                        return true;
                    case R.id.update:
                        sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                        RunUpdate();
                        return true;
                    case R.id.about:
                        sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                        RunAbout();
                        return true;
                    default:
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
        String setupCommand = "echo -ne \"\\033]0;CARsenal Setup\\007\" && clear;curl -s https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s setup";
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
        String updateCommand = "echo -ne \"\\033]0;CARsenal Update\\007\" && clear;curl -s https://raw.githubusercontent.com/V0lk3n/NetHunter-CARsenal/refs/heads/main/carsenal_setup.sh | bash -s update";
        String updateResult = run_cmd(updateCommand);
        Log.d("UpdateResult",updateResult);
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
        Log.i(TAG, "Update completed");
    }

    public void RunAbout() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        MaterialAlertDialogBuilder aboutDialog = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
        aboutDialog.setTitle("About CARsenal");

        TextView message = new TextView(context);
        message.setText(getResources().getText(R.string.about_author));
        message.setMovementMethod(LinkMovementMethod.getInstance());
        message.setPadding(50, 40, 50, 0);
        Linkify.addLinks(message, Linkify.WEB_URLS);

        aboutDialog.setView(message);
        aboutDialog.setNegativeButton("Close", (dialog, id) -> dialog.cancel());
        aboutDialog.show();
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

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
        }

        @SuppressLint("SetTextI18n")
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
            EditText SelectedMTU = rootView.findViewById(R.id.mtu_value);

            btnMtu.setOnClickListener(v -> {
                boolean isVisible = SelectedMTU.getVisibility() == View.VISIBLE;
                SelectedMTU.setVisibility(isVisible ? View.GONE : View.VISIBLE);

                int color = isVisible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnMtu.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            Button btnTxqueuelen = rootView.findViewById(R.id.btn_toggle_txqueuelen);
            EditText SelectedTxqueuelen = rootView.findViewById(R.id.txqueuelen_value);

            btnTxqueuelen.setOnClickListener(v -> {
                boolean isVisible = SelectedTxqueuelen.getVisibility() == View.VISIBLE;
                SelectedTxqueuelen.setVisibility(isVisible ? View.GONE : View.VISIBLE);

                int color = isVisible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnTxqueuelen.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
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
            // Spinner for CAN interfaces
            final Spinner canTypeList = rootView.findViewById(R.id.cantype_spinner);
            final String[] interfaceTypeOptions = {"can", "vcan", "slcan"};

            canTypeList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, interfaceTypeOptions));

            canTypeList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    String cantype_selected = parentView.getItemAtPosition(pos).toString();
                    sharedpreferences.edit().putString("cantype_selected", cantype_selected).apply();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // Start CAN interface
            Button StartCanButton = rootView.findViewById(R.id.start_caniface);
            StartCanButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();
                String selected_mtu = SelectedMTU.getText().toString();
                String selected_txqueuelen = SelectedTxqueuelen.getText().toString();
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

                String cmd_show = "/opt/car_hacking/car_venv/bin/vininfo show " + vinNumber + " | tr -s [:space:] > /sdcard/nh_files/can_arsenal/output.txt";
                new BootKali(cmd_show).run_bg();
                try {
                    Thread.sleep(SHORT_DELAY);
                    String output = exe.RunAsRootOutput("cat " + NhPaths.APP_SD_FILES_PATH + "/can_arsenal/output.txt");
                    term.setText(output);
                } catch (Exception e) {
                    Log.e("VINShowError", "Exception while reading VIN info", e);
                    term.setText("Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
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

                String cmd_check = "/opt/car_hacking/car_venv/bin/vininfo check " + vinNumber + " | tr -s [:space:] > /sdcard/nh_files/can_arsenal/output.txt";
                new BootKali(cmd_check).run_bg();
                try {
                    Thread.sleep(SHORT_DELAY);
                    String output = exe.RunAsRootOutput("cat " + NhPaths.APP_SD_FILES_PATH + "/can_arsenal/output.txt");
                    term.setText(output);
                } catch (Exception e) {
                    Log.e("VINCheckError", "Exception while reading VIN info", e);
                    term.setText("Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            });

            return rootView;
        }
    }

    public static class ToolsFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Activity activity;
        private boolean isInteractiveEnabled = false;
        private boolean isVerboseEnabled = false;
        private boolean isDisableLoopbackEnabled = false;
        private Context context;
        private String selected_caniface;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
            context = getContext();
        }


        @SuppressLint("SetTextI18n")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_tools, container, false);

            final EditText cansend_sequence = rootView.findViewById(R.id.cansend_sequence);
            final EditText SelectedRHost = rootView.findViewById(R.id.cannelloni_rhost);
            final EditText SelectedRPort = rootView.findViewById(R.id.cannelloni_rport);
            final EditText SelectedLPort = rootView.findViewById(R.id.cannelloni_lport);
            final EditText inputfilepath = rootView.findViewById(R.id.inputfilepath);
            final Button inputfilebrowse = rootView.findViewById(R.id.inputfilebrowse);
            final EditText outputfilepath = rootView.findViewById(R.id.outputfilepath);
            final Button outputfilebrowse = rootView.findViewById(R.id.outputfilebrowse);
            final EditText CustomCmd = rootView.findViewById(R.id.customcmd);

            // Interfaces
            final Spinner deviceList = rootView.findViewById(R.id.device_interface);

            executorService.submit(() -> {
                String result = exe.RunAsChrootOutput(
                        "ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';" +
                                "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'"
                );

                ArrayList<String> deviceIfaces = new ArrayList<>();

                if (result == null || result.trim().isEmpty()) {
                    deviceIfaces.add("None");
                } else {
                    deviceIfaces.addAll(Arrays.asList(result.split("\n")));
                }

                // Post UI update back to the main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceIfaces);
                    deviceList.setAdapter(adapter);

                    // Restore previous selection if saved
                    int savedPosition = sharedpreferences.getInt("selected_usb", 0);
                    if (savedPosition < deviceIfaces.size()) {
                        deviceList.setSelection(savedPosition);
                        selected_caniface = deviceIfaces.get(savedPosition);
                    } else {
                        selected_caniface = "None";
                    }

                    deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            selected_caniface = parentView.getItemAtPosition(pos).toString();
                            sharedpreferences.edit().putInt("selected_usb", pos).apply();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            selected_caniface = "None";
                        }
                    });

                    // Optional: show toast for newly detected device
                    if (!deviceIfaces.contains("None")) {
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                });
            });

            // Refresh Status
            ImageButton RefreshUSB = rootView.findViewById(R.id.refreshUSB);
            RefreshUSB.setOnClickListener(v -> {
                showToast("Refreshing Devices...");
                refresh(rootView);
            });
            executorService.submit(() -> refresh(rootView));

            // Advanced Options Toggle
            Button btnToggle = rootView.findViewById(R.id.btn_toggle_advanced);
            LinearLayout advancedOptionsLayout = rootView.findViewById(R.id.tools_advanced_options);

            btnToggle.setOnClickListener(v -> {
                if (advancedOptionsLayout.getVisibility() == View.GONE) {
                    advancedOptionsLayout.setVisibility(View.VISIBLE);
                    btnToggle.setText("Hide Advanced Options");
                } else {
                    advancedOptionsLayout.setVisibility(View.GONE);
                    btnToggle.setText("Advanced Options");
                }
            });

            // Interactive
            Button btnInteractive = rootView.findViewById(R.id.btn_toggle_interactive);

            btnInteractive.setOnClickListener(v -> {
                isInteractiveEnabled = !isInteractiveEnabled;

                int color = isInteractiveEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnInteractive.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Verbose
            Button btnVerbose = rootView.findViewById(R.id.btn_toggle_verbose);

            btnVerbose.setOnClickListener(v -> {
                isVerboseEnabled = !isVerboseEnabled;

                int color = isVerboseEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnVerbose.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Disable Local Loopback
            Button btnLoopback = rootView.findViewById(R.id.btn_toggle_loopback);

            btnLoopback.setOnClickListener(v -> {
                isDisableLoopbackEnabled = !isDisableLoopbackEnabled;

                int color = isDisableLoopbackEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnLoopback.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Input File
            final ActivityResultLauncher<Intent> inputFileLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            assert uri != null;
                            inputfilepath.setText(uri.getPath());
                        }
                    }
            );

            inputfilebrowse.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("log/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                inputFileLauncher.launch(Intent.createChooser(intent, "Select input file"));
            });

            // Output File
            final ActivityResultLauncher<Intent> outputFileLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            assert uri != null;
                            outputfilepath.setText(uri.getPath());
                        }
                    }
            );

            outputfilebrowse.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("log/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                outputFileLauncher.launch(Intent.createChooser(intent, "Select output file"));
            });

            // Tools
            // Start CanGen
            Button CanGenButton = rootView.findViewById(R.id.start_cangen);

            CanGenButton.setOnClickListener(v -> {
                String verboseEnabled = isVerboseEnabled ? " -v" : "";
                String disableLoopbackEnabled = isDisableLoopbackEnabled ? " -x" : "";
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    run_cmd("cangen " + selected_caniface + verboseEnabled + disableLoopbackEnabled);
                } else {
                    showToast("Please ensure your CAN Interface field is set!");
                }
                activity.invalidateOptionsMenu();
            });

            // Start CanSniffer
            Button CanSnifferButton = rootView.findViewById(R.id.start_cansniffer);

            CanSnifferButton.setOnClickListener(v -> {
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    run_cmd("cansniffer " + selected_caniface);
                } else {
                    showToast("Please ensure your CAN Interface field is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start CanDump
            Button CanDumpButton = rootView.findViewById(R.id.start_candump);

            CanDumpButton.setOnClickListener(v -> {
                String outputfile = outputfilepath.getText().toString();

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None") && !outputfile.isEmpty()) {
                    run_cmd("candump " + selected_caniface + " -f " + outputfile);
                } else {
                    showToast("Please ensure your CAN Interface and Output File fields is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start CanSend
            Button CanSendButton = rootView.findViewById(R.id.start_cansend);

            CanSendButton.setOnClickListener(v -> {
                String sequence = cansend_sequence.getText().toString();

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None") && !sequence.isEmpty()) {
                    run_cmd("cansend " + selected_caniface + " " + sequence);
                } else {
                    showToast("Please ensure your CAN Interface and Sequence fields is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start CanPlayer
            Button CanPlayerButton = rootView.findViewById(R.id.start_canplayer);

            CanPlayerButton.setOnClickListener(v -> {
                String interactiveEnabled = isInteractiveEnabled ? " -i" : "";
                String verboseEnabled = isVerboseEnabled ? " -v" : "";
                String disableLoopbackEnabled = isDisableLoopbackEnabled ? " -x" : "";
                String inputfile = inputfilepath.getText().toString();

                if (!inputfile.isEmpty()) {
                    run_cmd("canplayer -I " + inputfile + interactiveEnabled + verboseEnabled + disableLoopbackEnabled);
                } else {
                    showToast("Please ensure your Input File field is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start SequenceFinder
            final Button SequenceFinderButton = rootView.findViewById(R.id.start_sequencefinder);

            SequenceFinderButton.setOnClickListener(v -> {
                String inputfile = inputfilepath.getText().toString();

                if (!inputfile.isEmpty()) {
                    run_cmd("/opt/car_hacking/sequence_finder.sh " + inputfile);
                } else {
                    showToast("Please ensure your Input File field is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start Freediag
            Button FreediagButton = rootView.findViewById(R.id.start_freediag);

            FreediagButton.setOnClickListener(v -> {
                run_cmd("sudo -u kali freediag");

                activity.invalidateOptionsMenu();
            });

            // Start diag_test
            Button diagTestButton = rootView.findViewById(R.id.start_diagtest);

            diagTestButton.setOnClickListener(v -> {
                run_cmd("sudo -u kali diag_test");

                activity.invalidateOptionsMenu();
            });

            // Cannelloni
            Button CannelloniButton = rootView.findViewById(R.id.start_cannelloni);

            CannelloniButton.setOnClickListener(v ->  {
                String rhost = SelectedRHost.getText().toString();
                String rport = SelectedRPort.getText().toString();
                String lport = SelectedLPort.getText().toString();

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None") && !rhost.isEmpty() && !rport.isEmpty() && !lport.isEmpty()) {
                    run_cmd("sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport);
                } else {
                    showToast("Please ensure your CAN Interface, RHOST, RPORT, LPORT fields is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start Asc2Log
            Button Asc2LogButton = rootView.findViewById(R.id.start_asc2log);

            Asc2LogButton.setOnClickListener(v ->  {
                String inputfile = inputfilepath.getText().toString();
                String outputfile = outputfilepath.getText().toString();

                if (!inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("asc2log -I " + inputfile + " -O " + outputfile);
                } else {
                    showToast("Please ensure your Input and Output File fields is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start Log2asc
            Button Log2AscButton = rootView.findViewById(R.id.start_log2asc);

            Log2AscButton.setOnClickListener(v ->  {
                String inputfile = inputfilepath.getText().toString();
                String outputfile = outputfilepath.getText().toString();

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None") && !inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("log2asc -I " + inputfile + " -O " + outputfile + " " + selected_caniface);
                } else {
                    showToast("Please ensure your CAN Interface, Input and Output File fields is set!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start CustomCommand
            Button CustomCmdButton = rootView.findViewById(R.id.start_customcmd);

            CustomCmdButton.setOnClickListener(v ->  {
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

        // Refresh iface
        private void refresh(View CANFragment) {
            final Spinner deviceList = CANFragment.findViewById(R.id.device_interface);
            if (context == null) return;

            executorService.submit(() -> {
                String outputDevice = exe.RunAsChrootOutput("ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$'");
                final ArrayList<String> deviceIfaces = new ArrayList<>();
                if (outputDevice != null && !outputDevice.isEmpty()) {
                    final String[] deviceifacesArray = outputDevice.split("\n");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        int lastiface = sharedpreferences.getInt("selected_device", 0);
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceifacesArray));
                            deviceList.setSelection(lastiface);
                        });
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                } else {
                    deviceIfaces.add("None");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceIfaces));
                            sharedpreferences.edit().putInt("selected_device", deviceList.getSelectedItemPosition()).apply();
                        });
                    }
                }
            });

            String message = "Device list refreshed!";
            showToast(message);
        }
    }

    public static class CANUSBFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Activity activity;
        private boolean isDebugEnabled = false;
        private Context context;
        private EditText SelectedBaudrateUSB;
        private EditText SelectedCanSpeedUSB;
        private String selected_usb;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
            context = getContext();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_canusb, container, false);

            SelectedBaudrateUSB = rootView.findViewById(R.id.baudrate_usb);
            SelectedCanSpeedUSB = rootView.findViewById(R.id.canspeed_usb);

            // USB interfaces
            final Spinner deviceList = rootView.findViewById(R.id.device_interface);

            executorService.submit(() -> {
                String result = exe.RunAsChrootOutput(
                        "ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';" +
                                "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'"
                );

                ArrayList<String> deviceIfaces = new ArrayList<>();

                if (result == null || result.trim().isEmpty()) {
                    deviceIfaces.add("None");
                } else {
                    deviceIfaces.addAll(Arrays.asList(result.split("\n")));
                }

                // Post UI update back to the main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceIfaces);
                    deviceList.setAdapter(adapter);

                    // Restore previous selection if saved
                    int savedPosition = sharedpreferences.getInt("selected_usb", 0);
                    if (savedPosition < deviceIfaces.size()) {
                        deviceList.setSelection(savedPosition);
                        selected_usb = deviceIfaces.get(savedPosition);
                    } else {
                        selected_usb = "None";
                    }

                    deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            selected_usb = parentView.getItemAtPosition(pos).toString();
                            sharedpreferences.edit().putInt("selected_usb", pos).apply();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            selected_usb = "None";
                        }
                    });

                    if (!deviceIfaces.contains("None")) {
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                });
            });

            // Refresh Status
            ImageButton RefreshUSB = rootView.findViewById(R.id.refreshUSB);
            RefreshUSB.setOnClickListener(v -> {
                showToast("Refreshing Devices...");
                refresh(rootView);
            });
            executorService.submit(() -> refresh(rootView));

            // Can-Usb Mode Spinner
            final Spinner canusbModeList = rootView.findViewById(R.id.usb_mode_spinner);
            final String[] modeOptions = {"Mode", "0", "1", "2"};

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, modeOptions) {
                @Override
                public boolean isEnabled(int position) {
                    // Disable "Mode" item
                    return position != 0;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
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
                }
            });

            // Toggle Buttons
            // Counter
            Button btnCounter = rootView.findViewById(R.id.btn_toggle_usb_counter);
            EditText selectedCount = rootView.findViewById(R.id.usb_counter_value);

            btnCounter.setOnClickListener(v -> {
                boolean visible = selectedCount.getVisibility() == View.VISIBLE;
                selectedCount.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnCounter.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Data
            Button btnData = rootView.findViewById(R.id.btn_toggle_usb_data);
            EditText selectedData = rootView.findViewById(R.id.usb_data_value);

            btnData.setOnClickListener(v -> {
                boolean visible = selectedData.getVisibility() == View.VISIBLE;
                selectedData.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnData.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // ID
            Button btnID = rootView.findViewById(R.id.btn_toggle_usb_id);
            EditText selectedID = rootView.findViewById(R.id.usb_id_value);

            btnID.setOnClickListener(v -> {
                boolean visible = selectedID.getVisibility() == View.VISIBLE;
                selectedID.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnID.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Mode
            Button btnMode = rootView.findViewById(R.id.btn_toggle_usb_mode);

            btnMode.setOnClickListener(v -> {
                boolean visible = canusbModeList.getVisibility() == View.VISIBLE;
                canusbModeList.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnMode.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Sleep
            Button btnSleep = rootView.findViewById(R.id.btn_toggle_usb_sleep);
            EditText selectedSleep = rootView.findViewById(R.id.usb_sleep_value);

            btnSleep.setOnClickListener(v -> {
                boolean visible = selectedSleep.getVisibility() == View.VISIBLE;
                selectedSleep.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnSleep.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Debug (TTY Output)
            Button btnDebug = rootView.findViewById(R.id.btn_toggle_usb_ttyOutput);

            btnDebug.setOnClickListener(v -> {
                isDebugEnabled = !isDebugEnabled;

                int color = isDebugEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnDebug.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Start USB-CAN
            Button USBCanSendButton = rootView.findViewById(R.id.start_canusb_send);

            USBCanSendButton.setOnClickListener(v -> {
                String USBCANSpeed = SelectedCanSpeedUSB.getText().toString();
                String USBBaudrate = SelectedBaudrateUSB.getText().toString();
                String debugEnabled = isDebugEnabled ? " -t" : "";
                String countValue = getVisibleParam(selectedCount, " -n ");
                String idValue = getVisibleParam(selectedID, " -i ");
                String dataValue = getVisibleParam(selectedData, " -j ");
                String sleepValue = getVisibleParam(selectedSleep, " -g ");
                String modeValue = getVisibleParam(canusbModeList, " -m ");

                if (!selected_usb.isEmpty() && !selected_usb.equals("None") && !USBCANSpeed.isEmpty() && !USBBaudrate.isEmpty()) {
                    run_cmd("canusb -d " + selected_usb + " -s " + USBCANSpeed + " -b " + USBBaudrate + debugEnabled + idValue + dataValue + sleepValue + countValue + modeValue);
                } else {
                    showToast("Please ensure your USB Device and USB CAN Speed, Baudrate, Data fields is set!");
                }

                activity.invalidateOptionsMenu();
            });

            return rootView;
        }

        private String getVisibleParam(View view, String prefix) {
            if (view.getVisibility() == View.VISIBLE) {
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

        // Refresh main
        private void refresh(View CANFragment) {
            final Spinner deviceList = CANFragment.findViewById(R.id.device_interface);
            if (context == null) return;

            executorService.submit(() -> {
                String outputDevice = exe.RunAsChrootOutput("ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'");
                final ArrayList<String> deviceIfaces = new ArrayList<>();
                if (outputDevice != null && !outputDevice.isEmpty()) {
                    final String[] deviceifacesArray = outputDevice.split("\n");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        int lastiface = sharedpreferences.getInt("selected_device", 0);
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceifacesArray));
                            deviceList.setSelection(lastiface);
                        });
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                } else {
                    deviceIfaces.add("None");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceIfaces));
                            sharedpreferences.edit().putInt("selected_device", deviceList.getSelectedItemPosition()).apply();
                        });
                    }
                }
            });

            String message = "Device list refreshed!";
            showToast(message);
        }
    }

    public static class CANCARIBOUFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private Activity activity;
        private boolean isCandumpEnabled = false;
        private boolean isLoopEnabled = false;
        private boolean isOutputEnabled = false;
        private boolean isPadEnabled = false;
        private boolean isReverseEnabled = false;
        private Context context;
        private EditText SelectedFile;
        private EditText SelectedMessage;
        private String selected_caniface;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
            context = getContext();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_caribou, container, false);

            SelectedFile = rootView.findViewById(R.id.caribou_file);
            SelectedMessage = rootView.findViewById(R.id.caribou_message);

            // Interfaces
            final Spinner deviceList = rootView.findViewById(R.id.device_interface);

            executorService.submit(() -> {
                String result = exe.RunAsChrootOutput(
                        "ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';" +
                                "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'"
                );

                ArrayList<String> deviceIfaces = new ArrayList<>();

                if (result == null || result.trim().isEmpty()) {
                    deviceIfaces.add("None");
                } else {
                    deviceIfaces.addAll(Arrays.asList(result.split("\n")));
                }

                // Post UI update back to the main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceIfaces);
                    deviceList.setAdapter(adapter);

                    // Restore previous selection if saved
                    int savedPosition = sharedpreferences.getInt("selected_usb", 0);
                    if (savedPosition < deviceIfaces.size()) {
                        deviceList.setSelection(savedPosition);
                        selected_caniface = deviceIfaces.get(savedPosition);
                    } else {
                        selected_caniface = "None";
                    }

                    deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            selected_caniface = parentView.getItemAtPosition(pos).toString();
                            sharedpreferences.edit().putInt("selected_usb", pos).apply();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            selected_caniface = "None";
                        }
                    });

                    // Optional: show toast for newly detected device
                    if (!deviceIfaces.contains("None")) {
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                });
            });

            // Refresh Status
            ImageButton RefreshUSB = rootView.findViewById(R.id.refreshUSB);
            RefreshUSB.setOnClickListener(v -> {
                showToast("Refreshing Devices...");
                refresh(rootView);
            });
            executorService.submit(() -> refresh(rootView));

            // File
            final ActivityResultLauncher<Intent> inputFileLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            assert uri != null;
                            SelectedFile.setText(uri.getPath());
                        }
                    }
            );

            final Button cariboufilebrowse = rootView.findViewById(R.id.cariboufilebrowse);
            cariboufilebrowse.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                inputFileLauncher.launch(Intent.createChooser(intent, "Select input file"));
            });

            // Advanced Options Toggle
            Button btnToggle = rootView.findViewById(R.id.btn_toggle_advanced);
            LinearLayout advancedOptionsLayout = rootView.findViewById(R.id.caribou_advanced_options);

            btnToggle.setOnClickListener(v -> {
                if (advancedOptionsLayout.getVisibility() == View.GONE) {
                    advancedOptionsLayout.setVisibility(View.VISIBLE);
                    btnToggle.setText("Hide Advanced Options");
                } else {
                    advancedOptionsLayout.setVisibility(View.GONE);
                    btnToggle.setText("Advanced Options");
                }
            });


            // Advanced Options - Options
            // Start Address
            Button btnStartAddr = rootView.findViewById(R.id.btn_toggle_start_addr);
            EditText selectedAddr = rootView.findViewById(R.id.start_addr_value);

            btnStartAddr.setOnClickListener(v -> {
                boolean visible = selectedAddr.getVisibility() == View.VISIBLE;
                selectedAddr.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnStartAddr.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Length
            Button btnLength = rootView.findViewById(R.id.btn_toggle_length);
            EditText selectedLength = rootView.findViewById(R.id.length_value);

            btnLength.setOnClickListener(v -> {
                boolean visible = selectedLength.getVisibility() == View.VISIBLE;
                selectedLength.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnLength.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Separate Line
            Button btnLine = rootView.findViewById(R.id.btn_toggle_separateLine);
            EditText selectedLine = rootView.findViewById(R.id.separate_line_value);

            btnLine.setOnClickListener(v -> {
                boolean visible = selectedLine.getVisibility() == View.VISIBLE;
                selectedLine.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnLine.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Seed
            Button btnSeed = rootView.findViewById(R.id.btn_toggle_seed);
            EditText selectedSeed = rootView.findViewById(R.id.seed_value);

            btnSeed.setOnClickListener(v -> {
                boolean visible = selectedSeed.getVisibility() == View.VISIBLE;
                selectedSeed.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnSeed.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // ID
            Button btnID = rootView.findViewById(R.id.btn_toggle_id);
            EditText selectedID = rootView.findViewById(R.id.id_value);

            btnID.setOnClickListener(v -> {
                boolean visible = selectedID.getVisibility() == View.VISIBLE;
                selectedID.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnID.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Src
            Button btnSrc = rootView.findViewById(R.id.btn_toggle_src);
            EditText selectedSrc = rootView.findViewById(R.id.src_value);

            btnSrc.setOnClickListener(v -> {
                boolean visible = selectedSrc.getVisibility() == View.VISIBLE;
                selectedSrc.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnSrc.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Dst
            Button btnDst = rootView.findViewById(R.id.btn_toggle_dst);
            EditText selectedDst = rootView.findViewById(R.id.dst_value);

            btnDst.setOnClickListener(v -> {
                boolean visible = selectedDst.getVisibility() == View.VISIBLE;
                selectedDst.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnDst.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Min
            Button btnMin = rootView.findViewById(R.id.btn_toggle_min);
            EditText selectedMin = rootView.findViewById(R.id.min_value);

            btnMin.setOnClickListener(v -> {
                boolean visible = selectedMin.getVisibility() == View.VISIBLE;
                selectedMin.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnMin.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Max
            Button btnMax = rootView.findViewById(R.id.btn_toggle_max);
            EditText selectedMax = rootView.findViewById(R.id.max_value);

            btnMax.setOnClickListener(v -> {
                boolean visible = selectedMax.getVisibility() == View.VISIBLE;
                selectedMax.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnMax.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Delay
            Button btnDelay = rootView.findViewById(R.id.btn_toggle_delay);
            EditText selectedDelay = rootView.findViewById(R.id.delay_value);

            btnDelay.setOnClickListener(v -> {
                boolean visible = selectedDelay.getVisibility() == View.VISIBLE;
                selectedDelay.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnDelay.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Pad
            Button btnPad = rootView.findViewById(R.id.btn_toggle_pad);

            btnPad.setOnClickListener(v -> {
                isPadEnabled = !isPadEnabled;

                int color = isPadEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnPad.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Candump Format
            Button btnCandump = rootView.findViewById(R.id.btn_toggle_candump);

            btnCandump.setOnClickListener(v -> {
                isCandumpEnabled = !isCandumpEnabled;

                int color = isCandumpEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnCandump.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Save Output
            Button btnOutput = rootView.findViewById(R.id.btn_toggle_output);

            btnOutput.setOnClickListener(v -> {
                isOutputEnabled = !isOutputEnabled;

                int color = isOutputEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnOutput.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Loop
            Button btnLoop = rootView.findViewById(R.id.btn_toggle_loop);

            btnLoop.setOnClickListener(v -> {
                isLoopEnabled = !isLoopEnabled;

                int color = isLoopEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnLoop.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Reverse
            Button btnReverse = rootView.findViewById(R.id.btn_toggle_reverse);

            btnReverse.setOnClickListener(v -> {
                isReverseEnabled = !isReverseEnabled;

                int color = isReverseEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
                btnReverse.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // Start Dump
            Button CaribouDumpButton = rootView.findViewById(R.id.start_dump);

            CaribouDumpButton.setOnClickListener(v -> {
                String candumpFormat = isCandumpEnabled ? " -t" : "";
                String outputEnabled = isOutputEnabled ? " -f " + SelectedFile.getText().toString() : "";
                String separateLineValue = getVisibleParam(selectedLine, " -s ");
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " dump" + separateLineValue + candumpFormat + outputEnabled);
                } else {
                    showToast("Please chose a CAN Interface!");
                }

                activity.invalidateOptionsMenu();
            });

            // Start Listener
            Button CaribouListenerButton = rootView.findViewById(R.id.start_listener);

            CaribouListenerButton.setOnClickListener(v -> {
                String reverseEnabled = isReverseEnabled ? " -r" : "";
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " listener" + reverseEnabled);
                } else {
                    showToast("Please chose a CAN Interface!");
                }

                activity.invalidateOptionsMenu();
            });

            // FUZZER Spinner
            final Spinner FUZZERList = rootView.findViewById(R.id.fuzzer_spinner);
            final String[] FUZZEROptions = {"brute","identify","mutate","random","replay"};

            FUZZERList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, FUZZEROptions));

            FUZZERList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    String fuzzer_selected = parentView.getItemAtPosition(pos).toString();
                    sharedpreferences.edit().putString("fuzzer_selected", fuzzer_selected).apply();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // Start FUZZER
            Button CaribouFUZZERButton = rootView.findViewById(R.id.start_fuzzer);

            CaribouFUZZERButton.setOnClickListener(v -> {
                String fuzzer_module = sharedpreferences.getString("fuzzer_selected", "");
                String idValue           = getVisibleParam(selectedID, " ");
                String minValue          = getVisibleParam(selectedMin, " -min ");
                String outputEnabled = isOutputEnabled ? " -f " + SelectedFile.getText().toString() : "";
                String seedValue         = getVisibleParam(selectedSeed, " --seed ");

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    if ("brute".equals(fuzzer_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " fuzzer brute" + idValue);
                    }
                    if ("identify".equals(fuzzer_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " fuzzer identify" + outputEnabled);
                    }
                    if ("mutate".equals(fuzzer_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " fuzzer mutate" + idValue);
                    }
                    if ("random".equals(fuzzer_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " fuzzer random" + minValue + seedValue + outputEnabled);
                    }
                    if ("replay".equals(fuzzer_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " fuzzer replay" + outputEnabled);
                    }
                } else {
                    showToast("Please chose a CAN Interface!");
                }

                activity.invalidateOptionsMenu();
            });

            // SEND Spinner
            final Spinner SENDList = rootView.findViewById(R.id.send_spinner);
            final String[] SENDTypeOptions = {"file","message"};

            SENDList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, SENDTypeOptions));

            SENDList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    String send_selected = parentView.getItemAtPosition(pos).toString();
                    sharedpreferences.edit().putString("send_selected", send_selected).apply();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // Start SEND
            Button CaribouSENDButton = rootView.findViewById(R.id.start_send);

            CaribouSENDButton.setOnClickListener(v -> {
                String selected_message = SelectedMessage.getText().toString();
                String selected_file    = SelectedFile.getText().toString();
                String delayValue       = getVisibleParam(selectedDelay, " -d ");
                String loopEnabled      = isLoopEnabled ? " -l" : "";
                String padEnabled       = isPadEnabled ? " -p" : "";
                String send_module      = sharedpreferences.getString("send_selected", "");

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    if ("file".equals(send_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " send file" + delayValue + loopEnabled + " " + selected_file);
                    }
                    if ("message".equals(send_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " send message" + padEnabled + delayValue + loopEnabled + " " + selected_message);
                    }
                } else {
                    showToast("Please chose a CAN Interface!");
                }

                activity.invalidateOptionsMenu();
            });

            // UDS Spinner
            final Spinner UDSList = rootView.findViewById(R.id.uds_spinner);
            final String[] UDSTypeOptions = {"discovery","services"};

            UDSList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, UDSTypeOptions));

            UDSList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    String uds_selected = parentView.getItemAtPosition(pos).toString();
                    sharedpreferences.edit().putString("uds_selected", uds_selected).apply();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // Start UDS
            Button CaribouUDSButton = rootView.findViewById(R.id.start_uds);

            CaribouUDSButton.setOnClickListener(v -> {
                String srcValue          = getVisibleParam(selectedSrc, " ");
                String dstValue          = getVisibleParam(selectedDst, " ");
                String minValue          = getVisibleParam(selectedMin, " -min ");
                String maxValue          = getVisibleParam(selectedMax, " -max ");
                String delayValue        = getVisibleParam(selectedDelay, " -d ");
                String uds_module        = sharedpreferences.getString("uds_selected", "");

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    if ("discovery".equals(uds_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " uds discovery" + minValue + maxValue + delayValue);
                    }
                    if ("services".equals(uds_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " uds services" + srcValue + dstValue);
                    }
                } else {
                    showToast("Please chose a CAN Interface!");
                }

                activity.invalidateOptionsMenu();
            });

            // XCP Spinner
            final Spinner XCPList = rootView.findViewById(R.id.xcp_spinner);
            final String[] XCPOptions = {"discovery","info","dump"};

            XCPList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, XCPOptions));

            XCPList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    String xcp_selected = parentView.getItemAtPosition(pos).toString();
                    sharedpreferences.edit().putString("xcp_selected", xcp_selected).apply();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // Start XCP
            Button CaribouXCPButton = rootView.findViewById(R.id.start_xcp);

            CaribouXCPButton.setOnClickListener(v -> {
                String addrValue         = getVisibleParam(selectedAddr, " ");
                String lengthValue       = getVisibleParam(selectedLength, " ");
                String outputEnabled = isOutputEnabled ? " -f " + SelectedFile.getText().toString() : "";
                String srcValue          = getVisibleParam(selectedSrc, " ");
                String dstValue          = getVisibleParam(selectedDst, " ");
                String minValue          = getVisibleParam(selectedMin, " -min ");
                String maxValue          = getVisibleParam(selectedMax, " -max ");
                String xcp_module = sharedpreferences.getString("xcp_selected", "");

                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    if ("discovery".equals(xcp_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " xcp discovery" + minValue + maxValue);
                    }
                    if ("info".equals(xcp_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " xcp info" + srcValue + dstValue);
                    }
                    if ("dump".equals(xcp_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " xcp dump" + srcValue + dstValue + addrValue + lengthValue + outputEnabled);
                    }
                } else {
                    showToast("Please chose a CAN Interface!");
                }

                activity.invalidateOptionsMenu();
            });

            return rootView;
        }

        // Refresh iface
        private void refresh(View CANFragment) {
            final Spinner deviceList = CANFragment.findViewById(R.id.device_interface);
            if (context == null) return;

            executorService.submit(() -> {
                String outputDevice = exe.RunAsChrootOutput("ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$'");
                final ArrayList<String> deviceIfaces = new ArrayList<>();
                if (outputDevice != null && !outputDevice.isEmpty()) {
                    final String[] deviceifacesArray = outputDevice.split("\n");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        int lastiface = sharedpreferences.getInt("selected_device", 0);
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceifacesArray));
                            deviceList.setSelection(lastiface);
                        });
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                } else {
                    deviceIfaces.add("None");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceIfaces));
                            sharedpreferences.edit().putInt("selected_device", deviceList.getSelectedItemPosition()).apply();
                        });
                    }
                }
            });

            String message = "Device list refreshed!";
            showToast(message);
        }

        private String getVisibleParam(EditText field, String prefix) {
            if (field.getVisibility() == View.VISIBLE) {
                String input = field.getText().toString().trim();
                if (!input.isEmpty()) {
                    return prefix + input;
                }
            }
            return "";
        }
    }

    public static class CANICSIMFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private boolean isRandomizeEnabled = false;
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private static final String ICSIM_SCRIPT_PATH = "/opt/car_hacking/icsim_service.sh";
        private static final long SHORT_DELAY = 1000;
        private static final long LONG_DELAY = 2000;
        private Context context;
        private String selected_caniface;


        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
        }

        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_icsim, container, false);

            // Interfaces
            final Spinner deviceList = rootView.findViewById(R.id.device_interface);

            executorService.submit(() -> {
                String result = exe.RunAsChrootOutput(
                        "ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';" +
                                "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'"
                );

                ArrayList<String> deviceIfaces = new ArrayList<>();

                if (result == null || result.trim().isEmpty()) {
                    deviceIfaces.add("None");
                } else {
                    deviceIfaces.addAll(Arrays.asList(result.split("\n")));
                }

                // Post UI update back to the main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceIfaces);
                    deviceList.setAdapter(adapter);

                    // Restore previous selection if saved
                    int savedPosition = sharedpreferences.getInt("selected_usb", 0);
                    if (savedPosition < deviceIfaces.size()) {
                        deviceList.setSelection(savedPosition);
                        selected_caniface = deviceIfaces.get(savedPosition);
                    } else {
                        selected_caniface = "None";
                    }

                    deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            selected_caniface = parentView.getItemAtPosition(pos).toString();
                            sharedpreferences.edit().putInt("selected_usb", pos).apply();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            selected_caniface = "None";
                        }
                    });

                    // Optional: show toast for newly detected device
                    if (!deviceIfaces.contains("None")) {
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                });
            });

            // Level Spinner
            // 0 = No randomization added to the packets other than location and ID
            // 1 = Add NULL padding
            // 2 = Randomize unused bytes
            final Spinner levelList = rootView.findViewById(R.id.level_spinner);
            final String[] levelOptions = {"Level", "0", "1", "2"};

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, levelOptions) {
                @Override
                public boolean isEnabled(int position) {
                    // Disable "Level" item
                    return position != 0;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
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
                }
            });

            // Refresh Status
            ImageButton RefreshUSB = rootView.findViewById(R.id.refreshUSB);
            RefreshUSB.setOnClickListener(v -> {
                showToast("Refreshing Devices...");
                refresh(rootView);
            });
            executorService.submit(() -> refresh(rootView));

            // Randomize
            // Button btnRandomize = rootView.findViewById(R.id.btn_toggle_randomize);

            // btnRandomize.setOnClickListener(v -> {
            //     isRandomizeEnabled = !isRandomizeEnabled;

            //     int color = isRandomizeEnabled ? android.R.color.holo_green_light : android.R.color.holo_red_light;
            //     btnRandomize.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            // });

            // Level
            Button btnLevel = rootView.findViewById(R.id.btn_toggle_level);

            btnLevel.setOnClickListener(v -> {
                boolean visible = levelList.getVisibility() == View.VISIBLE;
                levelList.setVisibility(visible ? View.GONE : View.VISIBLE);

                int color = visible ? android.R.color.holo_red_light : android.R.color.holo_green_light;
                btnLevel.setTextColor(ContextCompat.getColorStateList(requireContext(), color));
            });

            // ICSIM
            Button runICSIM = rootView.findViewById(R.id.run_icsim);
            runICSIM.setOnClickListener(v -> {
                if (!selected_caniface.isEmpty() && !selected_caniface.equals("None")) {
                    // String randomizeEnabled = isRandomizeEnabled ? " -r" : "";
                    String levelValue = getVisibleParam(levelList, " -l ");
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

            Button refreshButton = rootView.findViewById(R.id.refresh_icsim);
            refreshButton.setOnClickListener(v -> {
                showToast("Refreshing ICSim display...");
                WebView icsimView = rootView.findViewById(R.id.icsim);
                WebView controlsView = rootView.findViewById(R.id.controls);

                icsimView.reload();
                controlsView.reload();
            });

            return rootView;
        }

        private String getVisibleParam(View view, String prefix) {
            if (view.getVisibility() == View.VISIBLE) {
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

        // Refresh iface
        private void refresh(View CANFragment) {
            final Spinner deviceList = CANFragment.findViewById(R.id.device_interface);
            if (context == null) return;

            executorService.submit(() -> {
                String outputDevice = exe.RunAsChrootOutput("ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$'");
                final ArrayList<String> deviceIfaces = new ArrayList<>();
                if (outputDevice != null && !outputDevice.isEmpty()) {
                    final String[] deviceifacesArray = outputDevice.split("\n");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        int lastiface = sharedpreferences.getInt("selected_device", 0);
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceifacesArray));
                            deviceList.setSelection(lastiface);
                        });
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                } else {
                    deviceIfaces.add("None");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceIfaces));
                            sharedpreferences.edit().putInt("selected_device", deviceList.getSelectedItemPosition()).apply();
                        });
                    }
                }
            });

            String message = "Device list refreshed!";
            showToast(message);
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
            final Spinner deviceList = rootView.findViewById(R.id.device_interface);

            executorService.submit(() -> {
                String result = exe.RunAsChrootOutput(
                        "ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';" +
                                "ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'"
                );

                ArrayList<String> deviceIfaces = new ArrayList<>();

                if (result == null || result.trim().isEmpty()) {
                    deviceIfaces.add("None");
                } else {
                    deviceIfaces.addAll(Arrays.asList(result.split("\n")));
                }

                // Post UI update back to the main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceIfaces);
                    deviceList.setAdapter(adapter);

                    // Restore previous selection if saved
                    int savedPosition = sharedpreferences.getInt("selected_usb", 0);
                    if (savedPosition < deviceIfaces.size()) {
                        deviceList.setSelection(savedPosition);
                        selected_caniface = deviceIfaces.get(savedPosition);
                    } else {
                        selected_caniface = "None";
                    }

                    deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            selected_caniface = parentView.getItemAtPosition(pos).toString();
                            sharedpreferences.edit().putInt("selected_usb", pos).apply();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            selected_caniface = "None";
                        }
                    });

                    // Optional: show toast for newly detected device
                    if (!deviceIfaces.contains("None")) {
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }';ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                });
            });

            // Refresh Status
            ImageButton RefreshUSB = rootView.findViewById(R.id.refreshUSB);
            RefreshUSB.setOnClickListener(v -> {
                showToast("Refreshing Devices...");
                refresh(rootView);
            });
            executorService.submit(() -> refresh(rootView));

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
                        "basename /usr/share/metasploit-framework/modules/auxiliary/server/local_hwbridge.rb && basename /usr/share/metasploit-framework/modules/auxiliary/client/hwbridge/connect.rb && ls /usr/share/metasploit-framework/modules/post/hardware/automotive/"
                );

                ArrayList<String> module = new ArrayList<>();

                if (result == null || result.trim().isEmpty()) {
                    module.add("None");
                } else {
                    module.addAll(Arrays.asList(result.split("\n")));
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, module);
                    modulesList.setAdapter(adapter);

                    int savedPosition = sharedpreferences.getInt("selected_module", 0);
                    if (savedPosition < module.size()) {
                        modulesList.setSelection(savedPosition);
                        selected_module = module.get(savedPosition);
                    } else {
                        selected_module = "None";
                    }

                    modulesList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                            selected_module = parentView.getItemAtPosition(pos).toString();
                            sharedpreferences.edit().putInt("selected_module", pos).apply();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            selected_module = "None";
                        }
                    });
                });
            });

            TextView infoText = rootView.findViewById(R.id.module_info_text);
            LinearLayout optionsContainer = rootView.findViewById(R.id.module_options_container);

            // Info button
            Button infoBtn = rootView.findViewById(R.id.info_module);
            infoBtn.setOnClickListener(v -> {
                if (selected_module == null || selected_module.equals("None")) {
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
                if (selected_module == null || selected_module.equals("None")) {
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
                    infoText.setText("No options available for this module.");
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
                    label.setText(name + " (" + required + ")");
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
            msfBtn.setOnClickListener(v -> {
                executorService.submit(() -> {
                    run_cmd("msfsession=$(screen -ls | awk '/^[[:space:]]*[0-9]+\\.msf/ {print $1}'\n); "
                            + "if [ -n \"$msfsession\" ]; then "
                            + "screen -d \"$msfsession\"; screen -r \"$msfsession\"; "
                            + "else screen -S msf -m msfconsole;exit; fi");
                });
            });

            Button runBtn = rootView.findViewById(R.id.run_module);
            runBtn.setOnClickListener(v -> {
                if (selected_module == null || selected_module.equals("None")) {
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
                } else if (moduleName.equals("local_hwbridge")){
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

                msfCmd.append("run\"`echo -ne '\\015'`;screen -d -r $msfsession;exit" +
                        "");

                executorService.submit(() -> {
                    run_cmd(msfCmd.toString());
                });
            });

            return rootView;
        }

        // Refresh iface
        private void refresh(View CANFragment) {
            final Spinner deviceList = CANFragment.findViewById(R.id.device_interface);
            if (context == null) return;

            executorService.submit(() -> {
                String outputDevice = exe.RunAsChrootOutput("ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';ls /dev | grep -E '^(ttyUSB|rfcomm|ttyACM|ttyS)[0-9]+$' | sed 's|^|/dev/|'");
                final ArrayList<String> deviceIfaces = new ArrayList<>();
                if (outputDevice != null && !outputDevice.isEmpty()) {
                    final String[] deviceifacesArray = outputDevice.split("\n");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        int lastiface = sharedpreferences.getInt("selected_device", 0);
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceifacesArray));
                            deviceList.setSelection(lastiface);
                        });
                        String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                        if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                            showToast(detected_device);
                        }
                    }
                } else {
                    deviceIfaces.add("None");
                    Activity activity = getActivity();
                    if (sharedpreferences != null && activity != null) {
                        requireActivity().runOnUiThread(() -> {
                            deviceList.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, deviceIfaces));
                            sharedpreferences.edit().putInt("selected_device", deviceList.getSelectedItemPosition()).apply();
                        });
                    }
                }
            });

            String message = "Device list refreshed!";
            showToast(message);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    // Simplified Toast function
    public void showToast(String message) {
        Toast.makeText(requireActivity().getApplicationContext(), message, Toast.LENGTH_LONG).show();
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
