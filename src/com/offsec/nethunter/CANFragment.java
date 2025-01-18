package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.BootKali;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.Map;
import java.util.HashMap;

public class CANFragment extends Fragment {
    public static final String TAG = "CANFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private final ShellExecuter exe = new ShellExecuter();
    private TextView SelectedIface;
    private TextView SelectedUartSpeed;
    private TextView SelectedMtu;
    private TextView SelectedRHost;
    private TextView SelectedRPort;
    private TextView SelectedLPort;
    private int SelectedCanSpeed;
    private String flow_control = "hw"; // Default value
    private SharedPreferences sharedpreferences;
    private Context context;
    private static Activity activity;
    public EditText modules_path;

    public static CANFragment newInstance(int sectionNumber) {
        CANFragment fragment = new CANFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.can, container, false);
        View modulesLayout = inflater.inflate(R.layout.modules, container, false);

        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

        //First run
        Boolean setupdone = sharedpreferences.getBoolean("setup_done", false);
        if (!setupdone.equals(true)) {
            SetupDialog();
        }

        //Settings
        //Flow Control Switch
        Switch flowControlSwitch = rootView.findViewById(R.id.flow_control_switch);
        flowControlSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            flow_control = isChecked ? "sw" : "hw";
        });

        //CanSpeed Spinner
        Spinner CanSpeedSpinner = rootView.findViewById(R.id.canspeed_spinner);
        String[] CanSpeedOptions = new String[]{
                "0 - 10 Kbit/s",
                "1 - 20 Kbit/s",
                "2 - 50 Kbit/s",
                "3 - 100 Kbit/s",
                "4 - 125 Kbit/s",
                "5 - 250 Kbit/s",
                "6 - 500 Kbit/s",
                "7 - 800 Kbit/s",
                "8 - 1000 Kbit/s"
        };

        ArrayAdapter<String> CanSpeedAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, CanSpeedOptions);
        CanSpeedSpinner.setAdapter(CanSpeedAdapter);

        CanSpeedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                String selectedItem = parentView.getItemAtPosition(pos).toString();
                String[] parts = selectedItem.split(" - ");
                String selectedCanSpeed = parts[0];
                int CanSpeed = Integer.parseInt(selectedCanSpeed);
                SelectedCanSpeed = CanSpeed;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Handle case where no item is selected
            }
        });

        //Input File
        final EditText inputfilepath = rootView.findViewById(R.id.inputfilepath);
        final Button inputfilebrowse = rootView.findViewById(R.id.inputfilebrowse);

        inputfilebrowse.setOnClickListener( v -> {
            Intent intent = new Intent();
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("log/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select input file"),1001);
        });

        //Output File
        final EditText outputfilepath = rootView.findViewById(R.id.outputfilepath);
        final Button outputfilebrowse = rootView.findViewById(R.id.outputfilebrowse);

        outputfilebrowse.setOnClickListener( v -> {
            Intent intent = new Intent();
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("log/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select output file"),1001);
        });

        // Modules
        Spinner modulesSpinner = rootView.findViewById(R.id.modules_spinner);
        Button loadButton = rootView.findViewById(R.id.load_module);

        // Set up the items for the Spinner
        String[] moduleOptions = new String[]{
                "EMS CPC-USB/ARM7 CAN/USB interface",
                "ESD USB/2 CAN/USB interface",
                "Geschwister Schneider UG interfaces",
                "IFI CAN_FD IP",
                "Kvaser CAN/USB interface",
                "Microchip MCP251x SPI CAN controllers",
                "PEAK PCAN-USB/Pro (CAN 2.0b/CAN-FD)",
                "8 devices USB2CAN interface"
        };

        // Link item to corresponding kernel names
        Map<String, String> moduleCommands = new HashMap<>();
        moduleCommands.put("EMS CPC-USB/ARM7 CAN/USB interface", "ems_usb");
        moduleCommands.put("ESD USB/2 CAN/USB interface", "esd_usb2");
        moduleCommands.put("Geschwister Schneider UG interfaces", "gs_usb");
        moduleCommands.put("IFI CAN_FD IP", "ifi_canfd");
        moduleCommands.put("Kvaser CAN/USB interface", "kvaser_usb");
        moduleCommands.put("Microchip MCP251x SPI CAN controllers", "mcp251x");
        moduleCommands.put("PEAK PCAN-USB/Pro (CAN 2.0b/CAN-FD)", "peak_usb");
        moduleCommands.put("8 devices USB2CAN interface", "usb_8dev");

        ArrayAdapter<String> ModulesAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, moduleOptions);
        ModulesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modulesSpinner.setAdapter(ModulesAdapter);

        // Access the modules layout
        modules_path = modulesLayout.findViewById(R.id.modulesPath);
        String LastModulesPath = sharedpreferences.getString("last_modulespath", "");
        if (!LastModulesPath.isEmpty()) modules_path.setText(LastModulesPath);

        String ModulesPath = modules_path.getText().toString();

        // Set OnClickListener for the Load button with Modules tab logic
        loadButton.setOnClickListener(v -> {
            String selectedModule = modulesSpinner.getSelectedItem().toString();
            String kernelModuleName = moduleCommands.get(selectedModule);
            String ModulesPathFull = ModulesPath + "/" + System.getProperty("os.version");

            if (kernelModuleName != null) {
                String isModuleLoaded = exe.RunAsRootOutput("lsmod | cut -d' ' -f1 | grep " + kernelModuleName);

                if (isModuleLoaded.contains(kernelModuleName)) {
                    String unloadCommand = exe.RunAsRootOutput("rmmod " + kernelModuleName + " && echo Success || echo Failed");
                    if (unloadCommand.contains("Success")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Module Unloaded: " + selectedModule + " - " + kernelModuleName, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed to unload: " + selectedModule + " - " + kernelModuleName, Toast.LENGTH_LONG).show();
                    }
                } else {
                    String toggle_module = exe.RunAsRootOutput("insmod " + ModulesPathFull + "/" + selectedModule + ".ko && echo Success || echo Failed");
                    if (toggle_module.contains("Success")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Module Loaded: " + selectedModule + " - " + kernelModuleName + " with insmod.", Toast.LENGTH_LONG).show();
                    } else {
                        String loadCommand = exe.RunAsRootOutput("modprobe -d " + ModulesPathFull + " " + kernelModuleName + " && echo Success || echo Failed");
                        if (loadCommand.contains("Success")) {
                            Toast.makeText(requireActivity().getApplicationContext(), "Module Loaded: " + selectedModule + " - " + kernelModuleName + " with modprobe.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(requireActivity().getApplicationContext(), "Failed to load: " + selectedModule + " - " + kernelModuleName + " with modprobe.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }

            activity.invalidateOptionsMenu();
        });

        // Interfaces
        // Declare SharedPreferences at the class level
        SharedPreferences preferences = requireActivity().getSharedPreferences("CANInterfaceState", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        // Store CAN Interface States
        Map<String, Boolean> buttonStates = new HashMap<>();

        // Load saved button states from SharedPreferences when fragment/activity is created
        buttonStates.put("start_caniface", preferences.getBoolean("start_caniface", false));
        buttonStates.put("start_vcaniface", preferences.getBoolean("start_vcaniface", false));
        buttonStates.put("start_slcaniface", preferences.getBoolean("start_slcaniface", false));

        //Start CAN interface
        Button StartCanButton = rootView.findViewById(R.id.start_caniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedUartSpeed = rootView.findViewById(R.id.uart_speed);

        // Set initial button text based on saved state
        StartCanButton.setText(buttonStates.get("start_caniface") ? "⏹ CAN" : "▶ CAN");

        StartCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_uartspeed = SelectedUartSpeed.getText().toString();
            boolean isStarted = buttonStates.get("start_caniface");

            if (isStarted) {
                run_cmd("clear;echo '\\nUnloading modules...' && modprobe -r can-raw can-gw can-bcm can && " +
                        "echo '\\nStopping CAN interface...' && sudo ip link set " + selected_caniface + " down && " +
                        "echo '\\nCAN Interface Stopped!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
                buttonStates.put("start_caniface", false);
                StartCanButton.setText("▶ CAN");
            } else {
                run_cmd("clear;echo '\\nLoading modules...' && modprobe -a can can-raw can-gw can-bcm && " +
                        "echo '\\nCreating CAN interface...' && sudo ip link set " + selected_caniface + " type can bitrate " + selected_uartspeed + " && " +
                        "echo 'Starting CAN interface...' && sudo ip link set up " + selected_caniface + " && ifconfig " + selected_caniface + " && " +
                        "echo '\\nCAN Interface Initialized!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");

                buttonStates.put("start_caniface", true);
                StartCanButton.setText("⏹ CAN");
            }

            // Save button state to SharedPreferences
            editor.putBoolean("start_caniface", buttonStates.get("start_caniface"));
            editor.apply();

            activity.invalidateOptionsMenu();
        });

        // Start VCAN interface
        Button StartVCanButton = rootView.findViewById(R.id.start_vcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedMtu = rootView.findViewById(R.id.mtu);

        // Set initial button text based on saved state
        StartVCanButton.setText(buttonStates.get("start_vcaniface") ? "⏹ VCAN" : "▶ VCAN");

        StartVCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_mtu = SelectedMtu.getText().toString();
            boolean isStarted = buttonStates.get("start_vcaniface");

            if (isStarted) {
                run_cmd("clear;echo '\\nUnloading modules...' && modprobe -r can-raw can-gw can-bcm vcan can && " +
                        "echo 'Stopping VCAN interface...' && sudo ip link set " + selected_caniface + " down && " +
                        "echo '\\nVCAN Interface Stopped!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
                buttonStates.put("start_vcaniface", false);
                StartVCanButton.setText("▶ VCAN");

            } else {
                run_cmd("clear;echo '\\nLoading modules...' && modprobe -a vcan can can-raw can-gw can-bcm  && " +
                        "echo '\\nCreating VCAN interface...' && ip link add dev " + selected_caniface + " type vcan && ip link set " + selected_caniface + " mtu " + selected_mtu + " && " +
                        "echo 'Starting VCAN interface...' && ip link set up " + selected_caniface + " && ifconfig " + selected_caniface + " && " +
                        "echo '\\nVCAN Interface Initialized!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");

                buttonStates.put("start_vcaniface", true);
                StartVCanButton.setText("⏹ VCAN");
            }

            // Save button state to SharedPreferences
            editor.putBoolean("start_vcaniface", buttonStates.get("start_vcaniface"));
            editor.apply();

            activity.invalidateOptionsMenu();
        });

        // Start SLCAN interface
        Button StartSLCanButton = rootView.findViewById(R.id.start_slcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedUartSpeed = rootView.findViewById(R.id.uart_speed);

        // Set initial button text based on saved state
        StartSLCanButton.setText(buttonStates.get("start_slcaniface") ? "⏹ SLCAN" : "▶ SLCAN");

        StartSLCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_uartSpeed = SelectedUartSpeed.getText().toString();
            String selected_canSpeed = String.valueOf(SelectedCanSpeed);
            boolean isStarted = buttonStates.get("start_slcaniface");

            if (isStarted) {
                run_cmd("clear;echo '\\nUnloading modules...' && modprobe -r can-raw can-gw can-bcm can slcan && " +
                        "echo 'Detttaching SLCAN from ttyUSB0...' && sudo slcan_attach -d /dev/ttyUSB0 && " +
                        "echo 'Stopping SLCAN interface...' && sudo ip link set " + selected_caniface + " down && " +
                        "echo '\\nSLCAN Interface Stopped!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
                buttonStates.put("start_slcaniface", false);
                StartSLCanButton.setText("▶ SLCAN");

            } else {
                run_cmd("clear;echo '\\nLoading modules...' && modprobe -a can slcan can-raw can-gw can-bcm && " +
                        "echo 'Attaching SLCAN to ttyUSB0...' && sudo slcan_attach -f -s" + selected_canSpeed + " -o /dev/ttyUSB0 && " +
                        "echo 'Creating SLCAN interface...' && sudo slcand -o -s" + selected_canSpeed + " -t " + flow_control + " -S " + selected_uartSpeed + " /dev/ttyUSB0 " + selected_caniface + " && " +
                        "echo 'Starting SLCAN interface...' && sudo ip link set up " + selected_caniface + " && " +
                        "echo '\\nSLCAN Interface Initialized!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");

                buttonStates.put("start_slcaniface", true);
                StartSLCanButton.setText("⏹ SLCAN");
            }

            // Save button state to SharedPreferences
            editor.putBoolean("start_slcaniface", buttonStates.get("start_slcaniface"));
            editor.apply();

            activity.invalidateOptionsMenu();
        });

        //Tools
        //Start CanGen
        Button CanGenButton = rootView.findViewById(R.id.start_cangen);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanGenButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            run_cmd("cangen " + selected_caniface + " -v");
            activity.invalidateOptionsMenu();
        });

        //Start CanSniffer
        Button CanSnifferButton = rootView.findViewById(R.id.start_cansniffer);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanSnifferButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            run_cmd("cansniffer " + selected_caniface);
            activity.invalidateOptionsMenu();
        });

        //Start CanDump
        Button CanDumpButton = rootView.findViewById(R.id.start_candump);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanDumpButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            String outputfile = outputfilepath.getText().toString();
            run_cmd("candump " + selected_caniface + " -f " + outputfile);
            activity.invalidateOptionsMenu();
        });

        //Start CanSend
        final EditText cansend_sequence = rootView.findViewById(R.id.cansend_sequence);
        Button CanSendButton = rootView.findViewById(R.id.start_cansend);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanSendButton.setOnClickListener(v ->  {
            String sequence = cansend_sequence.getText().toString();
            String selected_caniface = SelectedIface.getText().toString();
            run_cmd("cansend " + selected_caniface + " " + sequence);
            activity.invalidateOptionsMenu();
        });

        //Start CanPlayer
        Button CanPlayerButton = rootView.findViewById(R.id.start_canplayer);

        CanPlayerButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            run_cmd("canplayer -I " + inputfile);
            activity.invalidateOptionsMenu();
        });

        //Start SequenceFinder
        final Button SequenceFinderButton = rootView.findViewById(R.id.start_sequencefinder);
        SequenceFinderButton.setOnClickListener(v ->  {
            new BootKali("cp " + NhPaths.APP_SD_FILES_PATH + "/can_arsenal/sequence_finder.sh /opt/car_hacking/sequence_finder.sh && chmod +x /opt/car_hacking/sequence_finder.sh").run_bg();
            String inputfile = inputfilepath.getText().toString();
            run_cmd("/opt/car_hacking/sequence_finder.sh " + inputfile);
            activity.invalidateOptionsMenu();
        });

        //Cannelloni
        Button CannelloniButton = rootView.findViewById(R.id.start_cannelloni);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedRHost = rootView.findViewById(R.id.cannelloni_rhost);
        SelectedRPort = rootView.findViewById(R.id.cannelloni_rport);
        SelectedLPort = rootView.findViewById(R.id.cannelloni_lport);

        CannelloniButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            String rhost = SelectedRHost.getText().toString();
            String rport = SelectedRPort.getText().toString();
            String lport = SelectedLPort.getText().toString();
            run_cmd("sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport);
            activity.invalidateOptionsMenu();
        });

        //Logging
        //Start Asc2Log
        Button Asc2LogButton = rootView.findViewById(R.id.start_asc2log);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        Asc2LogButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            String outputfile = outputfilepath.getText().toString();
            run_cmd("asc2log -I " + inputfile + " -O " + outputfile);
            activity.invalidateOptionsMenu();
        });

        //Start Log2asc
        Button Log2AscButton = rootView.findViewById(R.id.start_log2asc);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        Log2AscButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            String outputfile = outputfilepath.getText().toString();
            String selected_caniface = SelectedIface.getText().toString();
            run_cmd("log2asc -I " + inputfile + " -O " + outputfile + " " + selected_caniface);
            activity.invalidateOptionsMenu();
        });

        //Start CustomCommand
        final EditText CustomCmd = rootView.findViewById(R.id.customcmd);
        Button CustomCmdButton = rootView.findViewById(R.id.start_customcmd);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CustomCmdButton.setOnClickListener(v ->  {
            String sequence = CustomCmd.getText().toString();
            run_cmd(sequence);
            activity.invalidateOptionsMenu();
        });

        //Author Contact
        //Website
        ImageView AuthorWebsiteButton = rootView.findViewById(R.id.author_website);
        AuthorWebsiteButton.setOnClickListener(v -> {
            String url = "https://v0lk3n.github.io";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        //𝕏
        ImageView AuthorXButton = rootView.findViewById(R.id.author_x);
        AuthorXButton.setOnClickListener(v -> {
            String url = "https://x.com/v0lk3n";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        //BlueSky
        ImageView AuthorBlueskyButton = rootView.findViewById(R.id.author_bluesky);
        AuthorBlueskyButton.setOnClickListener(v -> {
            String url = "https://bsky.app/profile/v0lk3n.bsky.social";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        //Mastodon
        ImageView AuthorMastodonButton = rootView.findViewById(R.id.author_mastodon);
        AuthorMastodonButton.setOnClickListener(v -> {
            String url = "https://infosec.exchange/@v0lk3n";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        //Instagram
        ImageView AuthorInstagramButton = rootView.findViewById(R.id.author_instagram);
        AuthorInstagramButton.setOnClickListener(v -> {
            String url = "https://www.instagram.com/v0lk3n_/";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        //Discord
        ImageView AuthorDiscordButton = rootView.findViewById(R.id.author_discord);
        AuthorDiscordButton.setOnClickListener(v -> {
            String url = "https://discord.com/users/343776454762430484";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        //GitHub
        ImageView AuthorGitHubButton = rootView.findViewById(R.id.author_github);
        AuthorGitHubButton.setOnClickListener(v -> {
            String url = "https://github.com/V0lk3n";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        //GitLab
        ImageView AuthorGitLabButton = rootView.findViewById(R.id.author_gitlab);
        AuthorGitLabButton.setOnClickListener(v -> {
            String url = "https://gitlab.com/V0lk3n";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        setHasOptionsMenu(true);
        return rootView;
    }

    //Menu
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuinflater) {
        menuinflater.inflate(R.menu.can, menu);
    }

    //Menu Items
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //First Setup
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

    //Documentation item
    public void RunDocumentation() {
        String url = "https://github.com/V0lk3n/NetHunter-CANArsenal";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(intent);
    }

    //Setup item
    public void RunSetup() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Setup\\007\" && clear; echo '\\nUpdating and Installing Packages...\\n' && apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf make cmake && " +
                "echo '\\nSetting up environment...' && if [[ -d /root/candump ]]; then echo '\\nFolder /root/candump detected!'; else echo '\\nCreating /root/candump folder...'; sudo mkdir -p /root/candump;fi;" +
                "if [[ -d /opt/car_hacking ]]; then echo 'Folder /opt/car_hacking detected!'; else echo '\\nCreating /opt/car_hacking folder...'; sudo mkdir -p /opt/car_hacking;fi;" +
                "if [[ -f /usr/bin/cangen && -f /usr/bin/cansniffer && -f /usr/bin/candump && -f /usr/bin/cansend && -f /usr/bin/canplayer && -d /opt/car_hacking/can-utils ]]; then echo '\\nCan-utils is installed!'; else echo '\\nInstalling Can-Utils...\\n'; cd /opt/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /opt/car_hacking/can-utils; sudo make; sudo make install;fi;" +
                "if [[ -f /usr/local/bin/cannelloni ]]; then echo 'Cannelloni is installed!'; else echo '\\nInstalling Cannelloni\\n'; cd /opt/car_hacking; sudo git clone https://github.com/v0lk3n/cannelloni.git; cd /opt/car_hacking/cannelloni; sudo cmake -DCMAKE_BUILD_TYPE=Release; sudo make; sudo make install;fi;" +
                "echo '\\nSetup done!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    //Update item
    public void RunUpdate() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Update\\007\" && clear; echo '\\nUpdating Packages...\\n' && apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf make cmake && " +
                "if [[ -f /usr/bin/cangen && -f /usr/bin/cansniffer && -f /usr/bin/candump && -f /usr/bin/cansend && -f /usr/bin/canplayer && -d /opt/car_hacking/can-utils  ]]; then echo '\\nCan-Utils detected! Updating...\\n'; cd /opt/car_hacking/can-utils; sudo git pull; sudo make; sudo make install; else echo '\\nCan-Utils not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/cannelloni && -d /opt/car_hacking/cannelloni  ]]; then echo '\\nCannelloni detected! Updating...\\n'; cd /opt/car_hacking/cannelloni; sudo git pull; sudo cmake -DCMAKE_BUILD_TYPE=Release; sudo make; sudo make install; else echo '\\nCannelloni not detected! Please run Setup first.';fi; " +
                "echo '\\nEverything is updated! Closing in 3secs..'; sleep 3 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    ////
    // Bridge side functions
    ////

    public static void run_cmd(String cmd) {
        Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
    }
}
