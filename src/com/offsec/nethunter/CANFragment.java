package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
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
    private TextView SelectedBitrate;
    private int selectedprompt_bitrate;
    private TextView SelectedMtu;
    private String flow_control = "hw"; // Default value
    private SharedPreferences sharedpreferences;
    private Context context;
    private static Activity activity;
    private boolean showingAdvanced;

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

    private void addClickListener(Button _button, View.OnClickListener onClickListener) {
        _button.setOnClickListener(onClickListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.can, container, false);
        View CanUtilsView = rootView.findViewById(R.id.CanUtilsView);
        Button CanUtilsAdvanced = rootView.findViewById(R.id.CanUtilsAdvancedButton);

        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        showingAdvanced = sharedpreferences.getBoolean("advanced_visible", false);

        // Find the Switch view by ID
        Switch flowControlSwitch = rootView.findViewById(R.id.flow_control_switch);

        // Set the OnCheckedChangeListener for the Switch
        flowControlSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Change the flow_control variable based on the switch state
            flow_control = isChecked ? "sw" : "hw";
        });

        CanUtilsView.setVisibility(showingAdvanced ? View.VISIBLE : View.INVISIBLE);
        CanUtilsAdvanced.setText("Can-Utils");

        //First run
        Boolean setupdone = sharedpreferences.getBoolean("setup_done", false);
        if (!setupdone.equals(true)) {
            SetupDialog();
        }

        // Prompt spinner for bitrate selection
        Spinner bitrateSpinner = rootView.findViewById(R.id.bitrate_spinner);
        String[] bitrateOptions = new String[]{"0 - 10 Kbit/s", "1 - 20 Kbit/s", "2 - 50 Kbit/s", "3 - 100 Kbit/s", "4 - 125 Kbit/s", "5 - 250 Kbit/s", "6 - 500 Kbit/s", "7 - 800 Kbit/s", "8 - 1000 Kbit/s"};

        // Set up the adapter
        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, bitrateOptions);
        bitrateSpinner.setAdapter(bitrateAdapter);

        // Handle item selection
        bitrateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                // Get the selected item and extract the numeric value
                String selectedItem = parentView.getItemAtPosition(pos).toString();
                String[] parts = selectedItem.split(" - ");
                String selectedBitrate = parts[0]; // The numeric part of the selection (e.g., "0", "1", "3")

                // You now have the selected bitrate number
                int bitrateValue = Integer.parseInt(selectedBitrate);

                // Store or use the selected bitrate value
                // For example, saving it to a variable or performing other actions
                selectedprompt_bitrate = bitrateValue;  // This stores the numeric part
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

        // Create a map to store states for each button
        Map<String, Boolean> buttonStates = new HashMap<>();

        //Start CAN interface
        Button startcanButton = rootView.findViewById(R.id.start_caniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedBitrate = rootView.findViewById(R.id.bitrate);

        // Initialize button state if not present in the map
        if (!buttonStates.containsKey("start_caniface")) {
            buttonStates.put("start_caniface", false); // False means not started
        }

        startcanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_bitrate = SelectedBitrate.getText().toString();

            // Retrieve the current state from the map
            boolean isStarted = buttonStates.get("start_caniface");

            if (isStarted) {
                // If started, stop the CAN interface
                run_cmd("echo 'Stopping CAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("start_caniface", false); // Mark as stopped

                // Change button text to indicate stop action
                startcanButton.setText("▶ CAN");

            } else {
                run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan slcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                        "echo 'Creating CAN interface...' && sudo ip link set " + selected_caniface + " type can bitrate " + selected_bitrate + " && " +
                        "echo 'Starting CAN interface...' && sudo ip link set up " + selected_caniface + " && ifconfig " + selected_caniface);

                buttonStates.put("start_caniface", true); // Mark as started

                // Change button text to indicate start action
                startcanButton.setText("⏹ CAN");
            }

            // Update options menu or any other UI updates if needed
            activity.invalidateOptionsMenu();
        });

        // Start VCAN interface
        Button startvcanButton = rootView.findViewById(R.id.start_vcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedMtu = rootView.findViewById(R.id.mtu);

        // Initialize button state if not present in the map
        if (!buttonStates.containsKey("start_vcaniface")) {
            buttonStates.put("start_vcaniface", false); // False means not started
        }

        startvcanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_mtu = SelectedMtu.getText().toString();

            // Retrieve the current state from the map
            boolean isStarted = buttonStates.get("start_vcaniface");

            if (isStarted) {
                // If started, stop the VCAN interface
                run_cmd("echo 'Stopping VCAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("start_vcaniface", false); // Mark as stopped

                // Change button text to indicate stop action
                startvcanButton.setText("▶ VCAN");

            } else {
                // If not started, start the VCAN interface
                run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan slcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                        "echo 'Creating VCAN interface...' && ip link add dev " + selected_caniface + " type vcan && ip link set " + selected_caniface + " mtu " + selected_mtu + " && " +
                        "echo 'Starting VCAN interface...' && ip link set up " + selected_caniface + " && ifconfig " + selected_caniface);

                buttonStates.put("start_vcaniface", true); // Mark as started

                // Change button text to indicate start action
                startvcanButton.setText("⏹ VCAN");
            }

            // Update options menu or any other UI updates if needed
            activity.invalidateOptionsMenu();
        });

        // Start SLCAN interface
        Button startslcanButton = rootView.findViewById(R.id.start_slcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedBitrate = rootView.findViewById(R.id.bitrate);

        // Initialize button state if not present in the map
        if (!buttonStates.containsKey("start_slcaniface")) {
            buttonStates.put("start_slcaniface", false); // False means not started
        }

        startslcanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_bitrate = SelectedBitrate.getText().toString();
            String prompt_bitrate = String.valueOf(selectedprompt_bitrate);

            // Retrieve the current state from the map
            boolean isStarted = buttonStates.get("start_slcaniface");

            if (isStarted) {
                // If started, stop the SLCAN interface
                run_cmd("echo 'Stopping SLCAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("start_slcaniface", false); // Mark as stopped

                // Change button text to indicate stop action
                startslcanButton.setText("▶ SLCAN");

            } else {
                // If not started, start the SLCAN interface
                run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan slcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                        "echo 'Creating SLCAN interface...' && sudo slcand -o -s" + prompt_bitrate + " -t " + flow_control + " -S " + selected_bitrate + " /dev/ttyUSB0 && " +
                        "echo 'Starting SLCAN interface...' && sudo ip link set up " + selected_caniface);

                buttonStates.put("start_slcaniface", true); // Mark as started

                // Change button text to indicate start action
                startslcanButton.setText("⏹ SLCAN");
            }

            // Update options menu or any other UI updates if needed
            activity.invalidateOptionsMenu();
        });

        // Attach SLCAN interface
        Button attachslcanButton = rootView.findViewById(R.id.attach_slcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedBitrate = rootView.findViewById(R.id.bitrate);

        // Initialize button state if not present in the map
        if (!buttonStates.containsKey("attach_slcaniface")) {
            buttonStates.put("attach_slcaniface", false); // False means not started
        }

        attachslcanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_bitrate = SelectedBitrate.getText().toString();

            // Retrieve the current state from the map
            boolean isStarted = buttonStates.get("attach_slcaniface");

            if (isStarted) {
                // If started, detach the SLCAN interface
                run_cmd("echo 'Detaching SLCAN interface...' && sudo ip link set " + selected_caniface + " down");
                buttonStates.put("attach_slcaniface", false); // Mark as stopped

                // Change button text to indicate stop action
                attachslcanButton.setText("🔗 SLCAN");

            } else {
                // If not started, attach the SLCAN interface
                run_cmd("clear;echo 'Creating SLCAN interface...' && sudo slcan_attach /dev/ttyUSB0 -w && " +
                        "echo 'Starting SLCAN interface...' && sudo ip link set " + selected_caniface + " type can bitrate " + selected_bitrate + " restart-ms 500 && sudo ip link set up " + selected_caniface);

                buttonStates.put("attach_slcaniface", true); // Mark as started

                // Change button text to indicate start action
                attachslcanButton.setText("🔗‍💥 SLCAN");
            }

            // Update options menu or any other UI updates if needed
            activity.invalidateOptionsMenu();
        });

        SharedPreferences finalSharedpreferences = sharedpreferences;
        addClickListener(CanUtilsAdvanced, v -> {
            if (!showingAdvanced) {
                CanUtilsView.setVisibility(View.VISIBLE);
                CanUtilsAdvanced.setText("Hide Can-Utils");
                showingAdvanced = true;
                finalSharedpreferences.edit().putBoolean("advanced_visible", true).apply();
            } else {
                CanUtilsView.setVisibility(View.GONE);
                CanUtilsAdvanced.setText("Can-Utils");
                showingAdvanced = false;
                finalSharedpreferences.edit().putBoolean("advanced_visible", false).apply();
            }
        });

        //Start cangen
        Button cangenButton = rootView.findViewById(R.id.start_cangen);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        cangenButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            run_cmd("cangen " + selected_interface + " -v");
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
            activity.invalidateOptionsMenu();
        });

        //Start cansniffer
        Button cansnifferButton = rootView.findViewById(R.id.start_cansniffer);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        cansnifferButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            run_cmd("cansniffer " + selected_interface);
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
            activity.invalidateOptionsMenu();
        });

        //Start candump
        Button candumpButton = rootView.findViewById(R.id.start_candump);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        candumpButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            String outputfile = outputfilepath.getText().toString();
            run_cmd("candump " + selected_interface + " -f " + outputfile);
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
            activity.invalidateOptionsMenu();
        });

        //Start cansend
        final EditText cansend_sequence = rootView.findViewById(R.id.cansend_sequence);
        Button cansendButton = rootView.findViewById(R.id.start_cansend);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        cansendButton.setOnClickListener(v ->  {
            String sequence = cansend_sequence.getText().toString();
            String selected_interface = SelectedIface.getText().toString();
            run_cmd("cansend " + selected_interface + " " + sequence);
            //WearOS iface control is weird, hence reset is needed
            activity.invalidateOptionsMenu();
        });

        //Start canplayer
        Button canplayerButton = rootView.findViewById(R.id.start_canplayer);

        canplayerButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            run_cmd("canplayer -I " + inputfile);
            activity.invalidateOptionsMenu();
        });

        //Start cansplit
        Button cansplitButton = rootView.findViewById(R.id.start_cansplit);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        cansplitButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            run_cmd("echo 'script todo'");
            Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
            activity.invalidateOptionsMenu();
        });

        // Logging

        // Start Asc2Log
        Button asc2logButton = rootView.findViewById(R.id.start_asc2log);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        asc2logButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            String outputfile = outputfilepath.getText().toString();
            run_cmd("asc2log -I " + inputfile + " -O " + outputfile);
            activity.invalidateOptionsMenu();
        });

        // Start Log2asc
        Button log2ascButton = rootView.findViewById(R.id.start_log2asc);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        log2ascButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            String outputfile = outputfilepath.getText().toString();
            String selected_interface = SelectedIface.getText().toString();
            run_cmd("log2asc -I " + inputfile + " -O " + outputfile + " " + selected_interface);
            activity.invalidateOptionsMenu();
        });

        //Start CustomCommand
        final EditText customcmd = rootView.findViewById(R.id.customcmd);
        Button customcmdButton = rootView.findViewById(R.id.start_customcmd);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        customcmdButton.setOnClickListener(v ->  {
            String sequence = customcmd.getText().toString();
            run_cmd(sequence);
            activity.invalidateOptionsMenu();
        });

        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        setHasOptionsMenu(true);
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuinflater) {
        menuinflater.inflate(R.menu.can, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
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

    public void RunSetup() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Setup\\007\" && clear; apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf && " +
                "if [[ -f /usr/bin/candump ]]; then echo 'Can-utils is installed!'; else sudo mkdir -p /root/candump; sudo mkdir -p /sdcard/nh_files/car_hacking; cd /sdcard/nh_files/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /sdcard/nh_files/car_hacking/can-utils; sudo make; sudo make install;fi;" +
                "echo 'Everything is installed!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

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
