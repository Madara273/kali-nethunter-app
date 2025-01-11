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
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.bridge.Bridge;

import java.util.Map;
import java.util.HashMap;

public class CANFragment extends Fragment {
    public static final String TAG = "CANFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private TextView SelectedIface;
    private TextView SelectedUartSpeed;
    private TextView SelectedMtu;
    private int SelectedCanSpeed;
    private String flow_control = "hw"; // Default value
    private SharedPreferences sharedpreferences;
    private Context context;
    private static Activity activity;

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
        String[] bitrateOptions = new String[]{"0 - 10 Kbit/s", "1 - 20 Kbit/s", "2 - 50 Kbit/s", "3 - 100 Kbit/s", "4 - 125 Kbit/s", "5 - 250 Kbit/s", "6 - 500 Kbit/s", "7 - 800 Kbit/s", "8 - 1000 Kbit/s"};

        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, bitrateOptions);
        CanSpeedSpinner.setAdapter(bitrateAdapter);

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
            startActivityForResult(Intent.createChooser(intent, "Select dump file"),1001);
        });

        //Output File
        final EditText outputfilepath = rootView.findViewById(R.id.outputfilepath);
        final Button outputfilebrowse = rootView.findViewById(R.id.outputfilebrowse);

        outputfilebrowse.setOnClickListener( v -> {
            Intent intent = new Intent();
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("log/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select dump file"),1001);
        });

        // Interfaces
        // Store CAN Interface States
        Map<String, Boolean> buttonStates = new HashMap<>();

        //Start CAN interface
        Button StartCanButton = rootView.findViewById(R.id.start_caniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedUartSpeed = rootView.findViewById(R.id.uart_speed);

        if (!buttonStates.containsKey("start_caniface")) {
            buttonStates.put("start_caniface", false);
        }

        StartCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_uartspeed = SelectedUartSpeed.getText().toString();
            boolean isStarted = buttonStates.get("start_caniface");

            if (isStarted) {
                run_cmd("echo 'Stopping CAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("start_caniface", false);
                StartCanButton.setText("▶ CAN");

            } else {
                run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan slcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                        "echo 'Creating CAN interface...' && sudo ip link set " + selected_caniface + " type can bitrate " + selected_uartspeed + " && " +
                        "echo 'Starting CAN interface...' && sudo ip link set up " + selected_caniface + " && ifconfig " + selected_caniface);

                buttonStates.put("start_caniface", true);
                StartCanButton.setText("⏹ CAN");
            }

            activity.invalidateOptionsMenu();
        });

        // Start VCAN interface
        Button StartVCanButton = rootView.findViewById(R.id.start_vcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedMtu = rootView.findViewById(R.id.mtu);

        if (!buttonStates.containsKey("start_vcaniface")) {
            buttonStates.put("start_vcaniface", false);
        }

        StartVCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_mtu = SelectedMtu.getText().toString();
            boolean isStarted = buttonStates.get("start_vcaniface");

            if (isStarted) {
                run_cmd("echo 'Stopping VCAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("start_vcaniface", false);
                StartVCanButton.setText("▶ VCAN");

            } else {
                run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan slcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                        "echo 'Creating VCAN interface...' && ip link add dev " + selected_caniface + " type vcan && ip link set " + selected_caniface + " mtu " + selected_mtu + " && " +
                        "echo 'Starting VCAN interface...' && ip link set up " + selected_caniface + " && ifconfig " + selected_caniface);

                buttonStates.put("start_vcaniface", true);
                StartVCanButton.setText("⏹ VCAN");
            }

            activity.invalidateOptionsMenu();
        });

        // Start SLCAN interface
        Button StartSLCanButton = rootView.findViewById(R.id.start_slcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedUartSpeed = rootView.findViewById(R.id.uart_speed);

        if (!buttonStates.containsKey("start_slcaniface")) {
            buttonStates.put("start_slcaniface", false);
        }

        StartSLCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_uartSpeed = SelectedUartSpeed.getText().toString();
            String selected_canSpeed = String.valueOf(SelectedCanSpeed);
            boolean isStarted = buttonStates.get("start_slcaniface");

            if (isStarted) {
                run_cmd("echo 'Stopping SLCAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("start_slcaniface", false);
                StartSLCanButton.setText("▶ SLCAN");

            } else {
                run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan slcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                        "echo 'Creating SLCAN interface...' && sudo slcand -o -s" + selected_canSpeed + " -t " + flow_control + " -S " + selected_uartSpeed + " /dev/ttyUSB0 && " +
                        "echo 'Starting SLCAN interface...' && sudo ip link set up " + selected_caniface);

                buttonStates.put("start_slcaniface", true);
                StartSLCanButton.setText("⏹ SLCAN");
            }

            activity.invalidateOptionsMenu();
        });

        // Attach SLCAN interface
        Button AttachSLCanButton = rootView.findViewById(R.id.attach_slcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedUartSpeed = rootView.findViewById(R.id.uart_speed);

        if (!buttonStates.containsKey("attach_slcaniface")) {
            buttonStates.put("attach_slcaniface", false);
        }

        AttachSLCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_uartspeed = SelectedUartSpeed.getText().toString();
            boolean isStarted = buttonStates.get("attach_slcaniface");

            if (isStarted) {
                run_cmd("echo 'Detaching SLCAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("attach_slcaniface", false);
                AttachSLCanButton.setText("🔗 SLCAN");

            } else {
                run_cmd("clear;echo 'Creating SLCAN interface...' && sudo slcan_attach /dev/ttyUSB0 -w && " +
                        "echo 'Starting SLCAN interface...' && sudo ip link set " + selected_caniface + " type can bitrate " + selected_uartspeed + " restart-ms 500 && sudo ip link set up " + selected_caniface);

                buttonStates.put("attach_slcaniface", true);
                AttachSLCanButton.setText("🔗‍💥 SLCAN");
            }

            activity.invalidateOptionsMenu();
        });

        //Tools
        //Start CanGen
        Button CanGenButton = rootView.findViewById(R.id.start_cangen);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanGenButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            run_cmd("cangen " + selected_caniface + " -v");
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
            activity.invalidateOptionsMenu();
        });

        //Start CanSniffer
        Button CanSnifferButton = rootView.findViewById(R.id.start_cansniffer);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanSnifferButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            run_cmd("cansniffer " + selected_caniface);
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
            activity.invalidateOptionsMenu();
        });

        //Start CanDump
        Button CanDumpButton = rootView.findViewById(R.id.start_candump);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanDumpButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            String outputfile = outputfilepath.getText().toString();
            run_cmd("candump " + selected_caniface + " -f " + outputfile);
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
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

        //Start CanSplit
        Button CanSplitButton = rootView.findViewById(R.id.start_cansplit);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        CanSplitButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            run_cmd("echo 'script todo'" + inputfile);
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
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
        Button AuthorWebsiteButton = rootView.findViewById(R.id.author_website);
        AuthorWebsiteButton.setOnClickListener(v -> {
            String url = "https://v0lk3n.github.io";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        //𝕏
        Button AuthorXButton = rootView.findViewById(R.id.author_x);
        AuthorXButton.setOnClickListener(v -> {
            String url = "https://x.com/v0lk3n";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        //BlueSky
        Button AuthorBlueskyButton = rootView.findViewById(R.id.author_bluesky);
        AuthorBlueskyButton.setOnClickListener(v -> {
            String url = "https://bsky.app/profile/v0lk3n.bsky.social";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        //Mastodon
        Button AuthorMastodonButton = rootView.findViewById(R.id.author_mastodon);
        AuthorMastodonButton.setOnClickListener(v -> {
            String url = "https://infosec.exchange/@v0lk3n";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        //Discord
        Button AuthorDiscordButton = rootView.findViewById(R.id.author_discord);
        AuthorDiscordButton.setOnClickListener(v -> {
            String url = "https://discord.com/users/343776454762430484";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        //GitHub
        Button AuthorGitHubButton = rootView.findViewById(R.id.author_github);
        AuthorGitHubButton.setOnClickListener(v -> {
            String url = "https://github.com/V0lk3n";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        //GitLab
        Button AuthorGitLabButton = rootView.findViewById(R.id.author_gitlab);
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
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Setup\\007\" && clear; apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf && " +
                "if [[ -f /usr/bin/candump ]]; then echo 'Can-utils is installed!'; else sudo mkdir -p /root/candump; sudo mkdir -p /sdcard/nh_files/car_hacking; cd /sdcard/nh_files/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /sdcard/nh_files/car_hacking/can-utils; sudo make; sudo make install;fi;" +
                "echo 'Everything is installed!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    //Update item
    public void RunUpdate() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Update\\007\" && clear; apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf && " +
                "if [[ -f /usr/bin/candump && -f /usr/bin/canplayer ]]; then cd /sdcard/nh_files/car_hacking/can-utils/ && git pull && make && make install; fi; " +
                "echo 'Done! Closing in 3secs..'; sleep 3 && exit");
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
