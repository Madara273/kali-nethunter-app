package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.ArrayList;

public class CANFragment extends Fragment {
    public static final String TAG = "CANFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private TextView SelectedIface;
    private TextView SelectedBitrate;
    private final ArrayList<String> arrayList = new ArrayList<>();
    private SharedPreferences sharedpreferences;
    private Context context;
    private static Activity activity;
    private NhPaths nh;
    private final ShellExecuter exe = new ShellExecuter();
    private Boolean iswatch;
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
        View CanPlayerView = rootView.findViewById(R.id.CanPlayerView);
        Button CanPlayerAdvanced = rootView.findViewById(R.id.CanPlayerAdvancedButton);

        SharedPreferences sharedpreferences = context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
        showingAdvanced = sharedpreferences.getBoolean("advanced_visible", false);

        CanUtilsView.setVisibility(showingAdvanced ? View.VISIBLE : View.INVISIBLE);
        CanUtilsAdvanced.setText("Hide CanUtils");

        CanPlayerView.setVisibility(showingAdvanced ? View.VISIBLE : View.INVISIBLE);
        CanPlayerAdvanced.setText("Hide CanPlayer");

        //Detecting watch
        //SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        iswatch = sharedpreferences.getBoolean("running_on_wearos", false);

        //First run
        Boolean setupdone = sharedpreferences.getBoolean("setup_done", false);
        if (!setupdone.equals(true)) {
            if (iswatch) SetupDialogWatch();
            SetupDialog();
        }

        //Start CAN interface
        Button startcanButton = rootView.findViewById(R.id.start_caniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedBitrate = rootView.findViewById(R.id.bitrate);

        startcanButton.setOnClickListener(v ->  {
            String selected_bitrate = SelectedBitrate.getText().toString();
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                    "echo 'Creating CAN interface...' && sudo ip link set can0 type can bitrate " + selected_bitrate + " && " +
                    "echo 'Starting CAN interface...' && sudo ip link set up can0 && ifconfig can0");
            //WearOS iface control is weird, hence reset is needed
            if (iswatch)
                AsyncTask.execute(() -> {
                    getActivity().runOnUiThread(() -> {
                        exe.RunAsRoot(new String[]{"echo 'todo'"});
                    });
                });
            activity.invalidateOptionsMenu();
        });

        //Start VCAN interface
        Button startvcanButton = rootView.findViewById(R.id.start_vcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        startvcanButton.setOnClickListener(v ->  {
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                    "echo 'Creating VCAN interface...' && ip link add dev vcan0 type vcan && ip link set vcan0 mtu 72 && " +
                    "echo 'Starting VCAN interface...' && ip link set up vcan0 && ifconfig vcan0");
            //WearOS iface control is weird, hence reset is needed
            if (iswatch)
                AsyncTask.execute(() -> {
                    getActivity().runOnUiThread(() -> {
                        exe.RunAsRoot(new String[]{"echo 'todo'"});
                    });
                });
            activity.invalidateOptionsMenu();
        });

        //Start SLCAN interface
        Button startslcanButton = rootView.findViewById(R.id.start_slcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);
        SelectedBitrate = rootView.findViewById(R.id.bitrate);

        startslcanButton.setOnClickListener(v ->  {
            String selected_bitrate = SelectedBitrate.getText().toString();
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("clear;echo 'Loading module...' && modprobe -a can vcan can-raw can-gw can-bcm can-dev && lsmod | grep vcan && " +
                    "echo 'Creating SLCAN interface...' && sudo slcand -o -s8 -t hw -S " + selected_bitrate + " /dev/ttyUSB0 && " +
                    "echo 'Starting SLCAN interface...' && sudo ip link set up slcan0");
            //WearOS iface control is weird, hence reset is needed
            if (iswatch)
                AsyncTask.execute(() -> {
                    getActivity().runOnUiThread(() -> {
                        exe.RunAsRoot(new String[]{"echo 'todo'"});
                    });
                });
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

        //Start candump
        Button candumpButton = rootView.findViewById(R.id.start_candump);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        candumpButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("candump " + selected_interface + " -f /root/candump-log/candump_log_01.log");
            //WearOS iface control is weird, hence reset is needed
            if (iswatch)
                AsyncTask.execute(() -> {
                    getActivity().runOnUiThread(() -> {
                        exe.RunAsRoot(new String[]{"echo 'todo'"});
                    });
                });
            else Toast.makeText(getActivity().getApplicationContext(), "No target selected!", Toast.LENGTH_SHORT).show();
            activity.invalidateOptionsMenu();
        });

        //Play File
        final EditText playfilename = rootView.findViewById(R.id.playfilename);
        final Button playfilebrowse = rootView.findViewById(R.id.playfilebrowse);

        playfilebrowse.setOnClickListener( v -> {
            Intent intent = new Intent();
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("log/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select dump file"),1001);
        });

        //Start canplayer
        Button canplayerButton = rootView.findViewById(R.id.start_canplayer);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        addClickListener(CanPlayerAdvanced, v -> {
            if (!showingAdvanced) {
                CanPlayerView.setVisibility(View.VISIBLE);
                CanPlayerAdvanced.setText("Hide CanPlayer");
                showingAdvanced = true;
                finalSharedpreferences.edit().putBoolean("advanced_visible", true).apply();
            } else {
                CanPlayerView.setVisibility(View.GONE);
                CanPlayerAdvanced.setText("CanPlayer");
                showingAdvanced = false;
                finalSharedpreferences.edit().putBoolean("advanced_visible", false).apply();
            }
        });

        canplayerButton.setOnClickListener(v ->  {
            String canplayer_playfile = playfilename.getText().toString();
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("canplayer -I " + canplayer_playfile);
            //WearOS iface control is weird, hence reset is needed
            if (iswatch)
                AsyncTask.execute(() -> {
                    getActivity().runOnUiThread(() -> {
                        exe.RunAsRoot(new String[]{"echo 'todo'"});
                    });
                });
            activity.invalidateOptionsMenu();
        });
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        setHasOptionsMenu(true);
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuinflater) {
        menuinflater.inflate(R.menu.bt, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.setup:
                SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                Boolean iswatch = sharedpreferences.getBoolean("running_on_wearos", false);
                if (iswatch) RunSetupWatch();
                else RunSetup();
                return true;
            case R.id.update:
                sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                iswatch = sharedpreferences.getBoolean("running_on_wearos", false);
                if (iswatch) Toast.makeText(requireActivity().getApplicationContext(), "Updates have to be done manually through adb shell. If anything gone wrong at first run, please run Setup again.", Toast.LENGTH_LONG).show();
                else RunUpdate();
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

    public void SetupDialogWatch() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.DialogStyleCompat);
        builder.setMessage("This seems to be the first run. Install the CAN tools?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            RunSetupWatch();
            sharedpreferences.edit().putBoolean("setup_done", true).apply();
        });
        builder.setNegativeButton("No", (dialog, which) -> {
            dialog.dismiss();
            sharedpreferences.edit().putBoolean("setup_done", true).apply();
        });
        builder.show();
    }

    public void RunSetupWatch() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Setup\\007\" && clear; apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf && " +
                "if [[ -f /usr/bin/candump ]]; then echo 'Can-utils is installed!'; else sudo mkdir -p /root/candump-log; sudo mkdir -p /sdcard/nh_files/car_hacking; cd /sdcard/nh_files/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /sdcard/nh_files/car_hacking/can-utils; sudo make; sudo make install;fi;" +
                "echo 'Everything is installed! Closing in 3secs..'; sleep 3 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    public void RunSetup() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Setup\\007\" && clear; apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf && " +
                "if [[ -f /usr/bin/candump ]]; then echo 'Can-utils is installed!'; else sudo mkdir -p /root/candump-log; sudo mkdir -p /sdcard/nh_files/car_hacking; cd /sdcard/nh_files/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /sdcard/nh_files/car_hacking/can-utils; sudo make; sudo make install;fi;" +
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
