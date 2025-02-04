package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class CANFragment extends Fragment {
    public static final String TAG = "CANFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private final ShellExecuter exe = new ShellExecuter();
    private CheckBox DebugCheckbox;
    private CheckBox IDCheckbox;
    private CheckBox DataCheckbox;
    private CheckBox SleepCheckbox;
    private String debugCMD = "";
    private String idCMD = "";
    private String dataCMD = "";
    private String sleepCMD = "";
    private String selected_usb;
    private TextView SelectedBaudrateUSB;
    private TextView SelectedCanSpeedUSB;
    private TextView SelectedData;
    private TextView SelectedID;
    private TextView SelectedIface;
    private TextView SelectedUartSpeed;
    private TextView SelectedMtu;
    private TextView SelectedRHost;
    private TextView SelectedRPort;
    private TextView SelectedLPort;
    private TextView SelectedSleep;
    private int SelectedCanSpeed;
    private String flow_control = "sw"; // Default value
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

        // Common used variables

        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedUartSpeed = rootView.findViewById(R.id.uart_speed);
        SelectedMtu = rootView.findViewById(R.id.mtu);
        final EditText cansend_sequence = rootView.findViewById(R.id.cansend_sequence);
        final EditText CustomCmd = rootView.findViewById(R.id.customcmd);

        SelectedRHost = rootView.findViewById(R.id.cannelloni_rhost);
        SelectedRPort = rootView.findViewById(R.id.cannelloni_rport);
        SelectedLPort = rootView.findViewById(R.id.cannelloni_lport);

        SelectedBaudrateUSB = rootView.findViewById(R.id.baudrate_usb);
        SelectedCanSpeedUSB = rootView.findViewById(R.id.canspeed_usb);

        //Checkboxes
        DebugCheckbox = rootView.findViewById(R.id.debug_canusb);
        IDCheckbox = rootView.findViewById(R.id.id_canusb);
        DataCheckbox = rootView.findViewById(R.id.data_canusb);
        SleepCheckbox = rootView.findViewById(R.id.sleep_canusb);

        //Checkboxes values
        SelectedID = rootView.findViewById(R.id.id_value_canusb);
        SelectedData = rootView.findViewById(R.id.data_value_canusb);
        SelectedSleep = rootView.findViewById(R.id.sleep_value_canusb);

        //First run
        Boolean setupdone = sharedpreferences.getBoolean("setup_done", false);
        if (!setupdone.equals(true)) {
            SetupDialog();
        }

        //USB interfaces
        final Spinner ttyUSB = rootView.findViewById(R.id.usb_interface);

        final String[] outputUSB = {""};
        AsyncTask.execute(() -> outputUSB[0] = exe.RunAsChrootOutput("ls -1 /sys/class/net/ | grep can;ls -1 /dev/ttyUSB*"));

        final ArrayList<String> usbIfaces = new ArrayList<>();
        if (outputUSB[0].isEmpty()) {
            usbIfaces.add("None");
            ttyUSB.setAdapter(new ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, usbIfaces));
        } else {
            final String[] usbifacesArray = outputUSB[0].split("\n");
            ttyUSB.setAdapter(new ArrayAdapter(requireContext(),android.R.layout.simple_list_item_1, usbifacesArray));
            String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
            Toast.makeText(requireActivity().getApplicationContext(), detected_device, Toast.LENGTH_LONG).show();

        }

        ttyUSB.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                selected_usb = parentView.getItemAtPosition(pos).toString();
                sharedpreferences.edit().putInt("selected_usb", ttyUSB.getSelectedItemPosition()).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        //Refresh Status
        ImageButton RefreshUSB = rootView.findViewById(R.id.refreshUSB);
        RefreshUSB.setOnClickListener(v -> refresh(rootView));
        AsyncTask.execute(() -> refresh(rootView));

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

        // Set initial button text based on saved state
        StartCanButton.setText(buttonStates.get("start_caniface") ? "⏹ CAN" : "▶ CAN");

        StartCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_uartspeed = SelectedUartSpeed.getText().toString();
            boolean isStarted = buttonStates.get("start_caniface");

            String checkCAN = exe.RunAsChrootOutput("ls -1 /sys/class/net/ | grep can");
            checkCAN = checkCAN.trim();
            if (checkCAN.isEmpty()) {
                Toast.makeText(requireActivity().getApplicationContext(), "No CAN device detected! Please connect your CAN device.", Toast.LENGTH_LONG).show();
                return;
            }

            if (selected_caniface != null && !selected_caniface.isEmpty()
                    && selected_uartspeed != null && !selected_uartspeed.isEmpty()) {

                // Validate CAN interface format
                if (!selected_caniface.matches("^can\\d+$")) {
                    Toast.makeText(requireActivity().getApplicationContext(), "The CAN interface should have a numeric suffix, such as 'can0', 'can1', etc.", Toast.LENGTH_LONG).show();
                    return;
                }
                if (isStarted) {
                    String stopCanIface = exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " down && modprobe -r can-raw can-gw can-bcm can && echo Success || echo Failed");
                    stopCanIface = stopCanIface.trim();
                    if (stopCanIface.contains("FATAL:") || stopCanIface.contains("Failed")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed to stop " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                    } else {
                        buttonStates.put("start_caniface", false);
                        StartCanButton.setText("▶ CAN");
                        Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " stopped!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    String startCanIface = exe.RunAsChrootOutput("modprobe -a can can-raw can-gw can-bcm && sudo ip link set " + selected_caniface + " type can bitrate " + selected_uartspeed + " && sudo ip link set " + selected_caniface + " up && echo Success || echo Failed");
                    startCanIface = startCanIface.trim();
                    if (startCanIface.contains("FATAL:") || startCanIface.contains("Failed")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed to start " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                    } else {
                        buttonStates.put("start_caniface", true);
                        StartCanButton.setText("⏹ CAN");
                        Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " started!", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                if (selected_caniface == null || selected_caniface.isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set a CAN interface!", Toast.LENGTH_LONG).show();
                }

                if (selected_uartspeed == null || selected_uartspeed.isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set a UART Speed value!", Toast.LENGTH_LONG).show();
                }
            }

            // Save button state to SharedPreferences
            editor.putBoolean("start_caniface", buttonStates.get("start_caniface"));
            editor.apply();

            activity.invalidateOptionsMenu();
        });

        // Start VCAN interface
        Button StartVCanButton = rootView.findViewById(R.id.start_vcaniface);

        // Set initial button text based on saved state
        StartVCanButton.setText(buttonStates.get("start_vcaniface") ? "⏹ VCAN" : "▶ VCAN");

        StartVCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_mtu = SelectedMtu.getText().toString();
            boolean isStarted = buttonStates.get("start_vcaniface");

            if (selected_caniface != null && !selected_caniface.isEmpty()
                    && selected_mtu != null && !selected_mtu.isEmpty()) {

                // Validate VCAN interface format
                if (!selected_caniface.matches("^vcan\\d+$")) {
                    Toast.makeText(requireActivity().getApplicationContext(), "The VCAN interface should have a numeric suffix, such as 'vcan0', 'vcan1', etc.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (isStarted) {
                    String stopVCanIface = exe.RunAsChrootOutput("modprobe -r can-raw can-gw can-bcm vcan can && echo Success || echo Failed");
                    stopVCanIface = stopVCanIface.trim();
                    if (stopVCanIface.contains("FATAL:") || stopVCanIface.contains("Failed")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed to stop " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                    } else {
                        buttonStates.put("start_vcaniface", false);
                        StartVCanButton.setText("▶ VCAN");
                        Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " stopped!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    String startVCanIface = exe.RunAsChrootOutput("modprobe -a vcan can can-raw can-gw can-bcm;sudo ip link add dev " + selected_caniface + " type vcan && sudo ip link set " + selected_caniface + " mtu " + selected_mtu + " && sudo ip link set up " + selected_caniface + " && echo Success || echo Failed");
                    startVCanIface = startVCanIface.trim();
                    if (startVCanIface.contains("FATAL:") || startVCanIface.contains("Failed")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed to start " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                    } else {
                        buttonStates.put("start_vcaniface", true);
                        StartVCanButton.setText("⏹ VCAN");
                        Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " started!", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                if (selected_caniface == null || selected_caniface.isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set a VCAN interface!", Toast.LENGTH_LONG).show();
                }

                if (selected_mtu == null || selected_mtu.isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set a MTU value!", Toast.LENGTH_LONG).show();
                }
            }

            // Save button state to SharedPreferences
            editor.putBoolean("start_vcaniface", buttonStates.get("start_vcaniface"));
            editor.apply();

            activity.invalidateOptionsMenu();
        });

        // Start SLCAN interface
        Button StartSLCanButton = rootView.findViewById(R.id.start_slcaniface);

        // Set initial button text based on saved state
        StartSLCanButton.setText(buttonStates.get("start_slcaniface") ? "⏹ SLCAN" : "▶ SLCAN");

        StartSLCanButton.setOnClickListener(v -> {
            String selected_caniface = SelectedIface.getText().toString();
            String selected_canSpeed = String.valueOf(SelectedCanSpeed);
            String selected_uartspeed = SelectedUartSpeed.getText().toString();
            boolean isStarted = buttonStates.get("start_slcaniface");

            String checkUSB = exe.RunAsChrootOutput("ls -1 /dev/ttyUSB*");
            checkUSB = checkUSB.trim();
            if (checkUSB.isEmpty()) {
                Toast.makeText(requireActivity().getApplicationContext(), "No CAN Device detected! Please connect your CAN Device.", Toast.LENGTH_LONG).show();
                return;
            }

            if (selected_caniface != null && !selected_caniface.isEmpty()
                    && selected_uartspeed != null && !selected_uartspeed.isEmpty()
                    && selected_canSpeed != null && !selected_canSpeed.isEmpty()) {

                // Validate SLCAN interface format
                if (!selected_caniface.matches("^slcan\\d+$")) {
                    Toast.makeText(requireActivity().getApplicationContext(), "The SLCAN interface should have a numeric suffix, such as 'slcan0', 'slcan1', etc.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (isStarted) {
                    String stopSLCanIface = exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " down && sleep 1 && sudo slcan_attach -d " + selected_usb + " && sleep 1 && modprobe -r can-raw can-gw can-bcm can slcan && echo Success || echo Failed");
                    stopSLCanIface = stopSLCanIface.trim();
                    if (stopSLCanIface.contains("FATAL:") || stopSLCanIface.contains("Failed")) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Failed to stop " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                    } else {
                        buttonStates.put("start_slcaniface", false);
                        StartSLCanButton.setText("▶ SLCAN");
                        Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " stopped!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    String startSLCanIface = exe.RunAsChrootOutput("modprobe -a can slcan can-raw can-gw can-bcm && " +
                            "sudo slcan_attach -f -s" + selected_canSpeed + " -o " + selected_usb + " && " +
                            "if timeout 5 sudo slcand -o -s" + selected_canSpeed + " -t " + flow_control + " -S " + selected_uartspeed + " " + selected_usb + " " + selected_caniface + "; then " +
                            "sudo ip link set up " + selected_caniface + " && echo Success || echo Failed; " +
                            "else echo 'TIMED OUT' && modprobe -r can-raw can-gw can-bcm can slcan;sudo slcan_attach -d " + selected_usb + ";sudo ip link set " + selected_caniface + " down;fi;");
                    startSLCanIface = startSLCanIface.trim();
                    if (startSLCanIface.contains("FATAL:") || startSLCanIface.contains("Failed") || startSLCanIface.contains("TIMED OUT")) {
                        if (startSLCanIface.contains("TIMED OUT")) {
                            Toast.makeText(requireActivity().getApplicationContext(), "Timed Out! Try switching flow control.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(requireActivity().getApplicationContext(), "Failed to start " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        buttonStates.put("start_slcaniface", true);
                        StartSLCanButton.setText("⏹ SLCAN");
                        Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " started!", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                if (selected_caniface == null || selected_caniface.isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set a SLCAN interface!", Toast.LENGTH_LONG).show();
                }

                if (selected_canSpeed == null || selected_canSpeed.isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set a CAN Speed value!", Toast.LENGTH_LONG).show();
                }

                if (selected_uartspeed == null || selected_uartspeed.isEmpty()) {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set a UART Speed value!", Toast.LENGTH_LONG).show();
                }
            }

            // Save button state to SharedPreferences
            editor.putBoolean("start_slcaniface", buttonStates.get("start_slcaniface"));
            editor.apply();

            activity.invalidateOptionsMenu();
        });

        //Tools
        //Start CanGen
        Button CanGenButton = rootView.findViewById(R.id.start_cangen);

        CanGenButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();

            if (selected_caniface != null && !selected_caniface.isEmpty()) {
                run_cmd("cangen " + selected_caniface + " -v");
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface field is set!", Toast.LENGTH_LONG).show();
            }
            activity.invalidateOptionsMenu();
        });

        //Start CanSniffer
        Button CanSnifferButton = rootView.findViewById(R.id.start_cansniffer);

        CanSnifferButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();

            if (selected_caniface != null && !selected_caniface.isEmpty()) {
                run_cmd("cansniffer " + selected_caniface);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface field is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Start CanDump
        Button CanDumpButton = rootView.findViewById(R.id.start_candump);

        CanDumpButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            String outputfile = outputfilepath.getText().toString();

            if (selected_caniface != null && !selected_caniface.isEmpty()
                    && outputfile != null && !outputfile.isEmpty()) {
                run_cmd("candump " + selected_caniface + " -f " + outputfile);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface and Output File fields is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Start CanSend
        Button CanSendButton = rootView.findViewById(R.id.start_cansend);

        CanSendButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            String sequence = cansend_sequence.getText().toString();

            if (selected_caniface != null && !selected_caniface.isEmpty()
                    && sequence != null && !sequence.isEmpty()) {
                run_cmd("cansend " + selected_caniface + " " + sequence);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface and Sequence fields is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Start CanPlayer
        Button CanPlayerButton = rootView.findViewById(R.id.start_canplayer);

        CanPlayerButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();

            if (inputfile != null && !inputfile.isEmpty()) {
                run_cmd("canplayer -I " + inputfile);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Input File field is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Start SequenceFinder
        final Button SequenceFinderButton = rootView.findViewById(R.id.start_sequencefinder);

        SequenceFinderButton.setOnClickListener(v ->  {
            new BootKali("cp " + NhPaths.APP_SD_FILES_PATH + "/can_arsenal/sequence_finder.sh /opt/car_hacking/sequence_finder.sh && chmod +x /opt/car_hacking/sequence_finder.sh").run_bg();
            String inputfile = inputfilepath.getText().toString();

            if (inputfile != null && !inputfile.isEmpty()) {
                run_cmd("/opt/car_hacking/sequence_finder.sh " + inputfile);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Input File field is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        // USB-CAN
        DebugCheckbox.setOnClickListener( v -> {
            if (DebugCheckbox.isChecked())
                debugCMD = " -t";
            else
                debugCMD = "";
        });
        IDCheckbox.setOnClickListener( v -> {
            if (IDCheckbox.isChecked()) {
                String selected_id = SelectedID.getText().toString();
                idCMD = " -i " + selected_id;
            } else {
                idCMD = "";
            }
        });
        DataCheckbox.setOnClickListener( v -> {
            if (DataCheckbox.isChecked()) {
                String selected_data = SelectedData.getText().toString();
                dataCMD = " -j " + selected_data;
            } else {
                dataCMD = "";
            }
        });
        SleepCheckbox.setOnClickListener( v -> {
            if (SleepCheckbox.isChecked()) {
                String selected_sleep = SelectedSleep.getText().toString();
                sleepCMD = " -g " + selected_sleep;
            } else {
                sleepCMD = "";
            }
        });

        //Start USB-CAN Dump
        Button USBCanDumpButton = rootView.findViewById(R.id.start_canusb_dump);

        USBCanDumpButton.setOnClickListener(v ->  {
            String USBCANSpeed = SelectedCanSpeedUSB.getText().toString();
            String USBBaudrate = SelectedBaudrateUSB.getText().toString();

            if (selected_usb != null && !selected_usb.isEmpty()
                    && USBCANSpeed != null && !USBCANSpeed.isEmpty()
                    && USBBaudrate != null && !USBBaudrate.isEmpty()) {
                run_cmd("canusb -d " + selected_usb + " -s " + USBCANSpeed + " -b " + USBBaudrate + debugCMD);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your USB Device and USB CAN Speed, Baudrate fields is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Start USB-CAN Send
        Button USBCanSendButton = rootView.findViewById(R.id.start_canusb_send);

        USBCanSendButton.setOnClickListener(v ->  {
            String USBCANSpeed = SelectedCanSpeedUSB.getText().toString();
            String USBBaudrate = SelectedBaudrateUSB.getText().toString();

            if (selected_usb != null && !selected_usb.isEmpty()
                    && USBCANSpeed != null && !USBCANSpeed.isEmpty()
                    && USBBaudrate != null && !USBBaudrate.isEmpty()) {
                run_cmd("canusb -d " + selected_usb + " -s " + USBCANSpeed + " -b " + USBBaudrate + debugCMD + idCMD + dataCMD + sleepCMD);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your USB Device and USB CAN Speed, Baudrate, Data fields is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Cannelloni
        Button CannelloniButton = rootView.findViewById(R.id.start_cannelloni);

        CannelloniButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            String rhost = SelectedRHost.getText().toString();
            String rport = SelectedRPort.getText().toString();
            String lport = SelectedLPort.getText().toString();

            if (selected_caniface != null && !selected_caniface.isEmpty()
                    && rhost != null && !rhost.isEmpty()
                    && rport != null && !rport.isEmpty()
                    && lport !=null && !lport.isEmpty()) {
                run_cmd("sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface, RHOST, RPORT, LPORT fields is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Logging
        //Start Asc2Log
        Button Asc2LogButton = rootView.findViewById(R.id.start_asc2log);

        Asc2LogButton.setOnClickListener(v ->  {
            String inputfile = inputfilepath.getText().toString();
            String outputfile = outputfilepath.getText().toString();

            if (inputfile != null && !inputfile.isEmpty()
                    && outputfile != null && !outputfile.isEmpty()) {
                run_cmd("asc2log -I " + inputfile + " -O " + outputfile);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Input and Output File fields is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Start Log2asc
        Button Log2AscButton = rootView.findViewById(R.id.start_log2asc);

        Log2AscButton.setOnClickListener(v ->  {
            String selected_caniface = SelectedIface.getText().toString();
            String inputfile = inputfilepath.getText().toString();
            String outputfile = outputfilepath.getText().toString();

            if (selected_caniface != null && !selected_caniface.isEmpty()
                    && inputfile != null && !inputfile.isEmpty()
                    && outputfile != null && !outputfile.isEmpty()) {
                run_cmd("log2asc -I " + inputfile + " -O " + outputfile + " " + selected_caniface);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface, Input and Output File fields is set!", Toast.LENGTH_LONG).show();
            }

            activity.invalidateOptionsMenu();
        });

        //Start CustomCommand
        Button CustomCmdButton = rootView.findViewById(R.id.start_customcmd);

        CustomCmdButton.setOnClickListener(v ->  {
            String command = CustomCmd.getText().toString();

            if (command != null && !command.isEmpty()) {
                run_cmd(command);
            } else {
                Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Custom Command field is set!", Toast.LENGTH_LONG).show();
            }

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

    //Refresh main
    private void refresh(View CANFragment) {
        final Spinner ttyUSB = CANFragment.findViewById(R.id.usb_interface);
        SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

        requireActivity().runOnUiThread(() -> {
            String outputUSB = exe.RunAsChrootOutput("ls -1 /sys/class/net/ | grep can;ls -1 /dev/ttyUSB*");
            final ArrayList<String> usbIfaces = new ArrayList<>();
            if (outputUSB.isEmpty()) {
                usbIfaces.add("None");
                ttyUSB.setAdapter(new ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, usbIfaces));
                sharedpreferences.edit().putInt("selected_usb", ttyUSB.getSelectedItemPosition()).apply();
            } else {
                final String[] usbifacesArray = outputUSB.split("\n");
                ttyUSB.setAdapter(new ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, usbifacesArray));
                int lastiface = sharedpreferences.getInt("selected_usb", 0);
                ttyUSB.setSelection(lastiface);
                String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                Toast.makeText(requireActivity().getApplicationContext(), detected_device, Toast.LENGTH_LONG).show();
            }
        });
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
                "if [[ -f /usr/local/bin/canusb ]]; then echo 'USB-CAN is installed!'; else echo '\\nInstalling USB-CAN\\n'; cd /opt/car_hacking; sudo git clone https://github.com/v0lk3n/usb-can.git; cd /opt/car_hacking/usb-can;sudo gcc -o canusb canusb.c; sudo cp canusb /usr/local/bin/canusb;fi;" +
                "echo '\\nSetup done!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    //Update item
    public void RunUpdate() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Update\\007\" && clear; echo '\\nUpdating Packages...\\n' && apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf make cmake && " +
                "if [[ -f /usr/bin/cangen && -f /usr/bin/cansniffer && -f /usr/bin/candump && -f /usr/bin/cansend && -f /usr/bin/canplayer && -d /opt/car_hacking/can-utils  ]]; then echo '\\nCan-Utils detected! Updating...\\n'; cd /opt/car_hacking/can-utils; sudo git pull; sudo make; sudo make install; else echo '\\nCan-Utils not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/cannelloni && -d /opt/car_hacking/cannelloni  ]]; then echo '\\nCannelloni detected! Updating...\\n'; cd /opt/car_hacking/cannelloni; sudo git pull; sudo cmake -DCMAKE_BUILD_TYPE=Release; sudo make; sudo make install; else echo '\\nCannelloni not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/canusb && -d /opt/car_hacking/usb-can  ]]; then echo '\\nUSB-CAN detected! Updating...\\n'; cd /opt/car_hacking/usb-can; sudo git pull; sudo gcc -o canusb canusb.c; sudo cp canusb /usr/local/bin/canusb; else echo '\\nUSB-CAN not detected! Please run Setup first.';fi; " +
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
