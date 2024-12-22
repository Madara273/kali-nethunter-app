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
    private final ArrayList<String> arrayList = new ArrayList<>();
    private SharedPreferences sharedpreferences;
    private Context context;
    private static Activity activity;
    private NhPaths nh;
    private final ShellExecuter exe = new ShellExecuter();
    private Boolean iswatch;

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

        //Detecting watch
        SharedPreferences sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        iswatch = sharedpreferences.getBoolean("running_on_wearos", false);

        //Start VCAN interface
        Button startvcanButton = rootView.findViewById(R.id.start_vcaniface);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        startvcanButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("clear;echo 'Loading module...' && modprobe vcan && lsmod | grep vcan && " +
                    "echo 'Creating VCAN interface...' && ip link add dev vcan0 type vcan && ip link set vcan0 mtu 72 && " +
                    "echo 'Starting VCAN interface...' && ip link set up vcan0 && ifconfig vcan0");
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

        //Start candump
        Button candumpButton = rootView.findViewById(R.id.start_candump);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        candumpButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("candump " + selected_interface + " -l");
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

        //Start canplayer
        Button canplayerButton = rootView.findViewById(R.id.start_canplayer);
        SelectedIface = rootView.findViewById(R.id.can_iface);

        canplayerButton.setOnClickListener(v ->  {
            String selected_interface = SelectedIface.getText().toString();
            if (iswatch) {
                exe.RunAsRoot(new String[]{"echo 'todo'"});
            } else exe.RunAsRoot(new String[]{"svc wifi enable"});
            run_cmd("canplayer -i *.log"); //TODO : Handle output log as var
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
                "if [[ -f /usr/bin/candump ]]; then echo 'Can-utils is installed!'; else sudo mkdir -p /sdcard/nh_files/car_hacking; cd /sdcard/nh_files/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /sdcard/nh_files/car_hacking/can-utils; sudo make; sudo make install;fi;" +
                "echo 'Everything is installed! Closing in 3secs..'; sleep 3 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    public void RunSetup() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Setup\\007\" && clear; apt update && apt install -y libsdl2-dev libsdl2-image-dev can-utils maven autoconf && " +
                "if [[ -f /usr/bin/candump ]]; then echo 'Can-utils is installed!'; else sudo mkdir -p /sdcard/nh_files/car_hacking; cd /sdcard/nh_files/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /sdcard/nh_files/car_hacking/can-utils; sudo make; sudo make install;fi;" +
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
