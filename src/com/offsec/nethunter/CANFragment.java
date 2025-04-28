package com.offsec.nethunter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.HashMap;

public class CANFragment extends Fragment {
    private SharedPreferences sharedpreferences;
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
        super.onCreate(savedInstanceState);
        context = getContext();
        activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.can, container, false);
        CANFragment.TabsPagerAdapter tabsPagerAdapter = new CANFragment.TabsPagerAdapter(getChildFragmentManager());

        ViewPager mViewPager = rootView.findViewById(R.id.pagerCAN);
        mViewPager.setAdapter(tabsPagerAdapter);
        mViewPager.setOffscreenPageLimit(6);
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                activity.invalidateOptionsMenu();
            }
        });
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        setHasOptionsMenu(true);
        return rootView;
    }

    // Menu
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuinflater) {
        menuinflater.inflate(R.menu.can, menu);
    }

    // Menu Items
    @SuppressLint("NonConstantResourceId")
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
            case R.id.about:
                sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
                RunAbout();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Setup\\007\" && clear; echo '\\nUpdating and Installing Packages...\\n' && apt update && apt install -y can-utils libsdl2-dev libsdl2-image-dev libconfig-dev libsocketcan-dev can-utils maven autoconf make cmake meson xserver-xephyr x11vnc novnc git python3-pip websockify fluxbox expect && " +
                "echo '\\nSetting up environment...' && if [[ -d /root/candump ]]; then echo '\\nFolder /root/candump detected!'; else echo '\\nCreating /root/candump folder...'; sudo mkdir -p /root/candump;fi;" +
                "if [[ -d /opt/car_hacking ]]; then echo 'Folder /opt/car_hacking detected!'; else echo '\\nCreating /opt/car_hacking folder...'; sudo mkdir -p /opt/car_hacking;fi;" +
                "if [[ -f /usr/bin/cangen && -f /usr/bin/cansniffer && -f /usr/bin/candump && -f /usr/bin/cansend && -f /usr/bin/canplayer && -d /opt/car_hacking/can-utils ]]; then echo '\\nCan-utils is installed!'; else echo '\\nInstalling Can-Utils...\\n'; cd /opt/car_hacking; sudo git clone https://github.com/v0lk3n/can-utils.git; cd /opt/car_hacking/can-utils; sudo make; sudo make install;fi;" +
                "if [[ -f /usr/local/bin/cannelloni ]]; then echo 'Cannelloni is installed!'; else echo '\\nInstalling Cannelloni\\n'; cd /opt/car_hacking; sudo git clone https://github.com/v0lk3n/cannelloni.git; cd /opt/car_hacking/cannelloni; sudo cmake -DCMAKE_BUILD_TYPE=Release; sudo make; sudo make install;fi;" +
                "if [[ -f /usr/local/bin/canusb ]]; then echo 'USB-CAN is installed!'; else echo '\\nInstalling USB-CAN\\n'; cd /opt/car_hacking; sudo git clone https://github.com/v0lk3n/usb-can.git; cd /opt/car_hacking/usb-can;sudo gcc -o canusb canusb.c; sudo cp canusb /usr/local/bin/canusb;fi;" +
                "if [[ -f /usr/local/bin/freediag && -f /usr/local/bin/diag_test ]]; then echo 'Freediag is installed!'; else echo '\\nInstalling Freediag\\n'; cd /opt/car_hacking; sudo git clone https://github.com/v0lk3n/freediag.git; cd /opt/car_hacking/freediag;./build_simple.sh; sudo cp build/scantool/freediag /usr/local/bin/freediag && sudo cp build/scantool/diag_test /usr/local/bin/diag_test;fi;" +
                "if [[ -f /usr/local/sbin/socketcand ]]; then echo 'Socketcand is Installed!'; else echo '\\nInstalling Socketcand\\n'; cd /opt/car_hacking; sudo git clone https://github.com/V0lk3n/socketcand.git; cd /opt/car_hacking/socketcand; sudo meson setup -Dlibconfig=true --buildtype=release build; sudo meson compile -C build; sudo meson install -C build;fi; " +
                "if [[ -f /usr/local/bin/hlcand ]]; then echo 'hlcand is Installed!'; else echo '\\nInstalling hlcancand\\n'; cd /opt/car_hacking; sudo git clone https://github.com/V0lk3n/usb-can-2.git; cd /opt/car_hacking/usb-can-2; sudo ./build.sh; cp -f src/hlcand /usr/local/bin/hlcand;fi; " +
                "if [[ -f /usr/local/bin/caringcaribou ]]; then echo 'CaringCaribou is Installed!'; else echo '\\nInstalling CaringCaribou\\n'; cd /opt/car_hacking; sudo git clone https://github.com/V0lk3n/caringcaribou.git; cd /opt/car_hacking/caringcaribou; sudo python setup.py install;fi; " +
                "if [[ -f /opt/noVNC/utils/novnc_proxy ]]; then echo 'noVNC is Installed!'; else echo '\\nInstalling noVNC\\n'; cd /opt/car_hacking; sudo git clone https://github.com/novnc/noVNC.git;fi; " +
                "if [[ -f /opt/car_hacking/ICSim/builddir/ICSIM ]]; then echo 'ICSIM is Installed!'; else echo '\\nInstalling ICSIM\\n'; cd /opt/car_hacking; sudo git clone https://github.com/V0lk3n/ICSim.git; cd /opt/car_hacking/ICSim;sudo cp /opt/car_hacking/can-utils/lib.o .;sudo meson setup builddir && cd builddir && sudo meson compile;sudo cp -f /sdcard/nh_files/can_arsenal/icsim_start.sh /opt/car_hacking/icsim_start.sh; sudo chmod +x /opt/car_hacking/icsim_start.sh;fi; " +
                "if [[ -f /opt/car_hacking/can_reset.sh ]]; then echo 'can_reset.sh is Installed!'; else echo '\\nInstalling can_reset.sh\\n'; sudo cp -f /sdcard/nh_files/can_arsenal/can_reset.sh /opt/car_hacking/can_reset.sh; sudo chmod +x /opt/car_hacking/can_reset.sh;fi; " +
                "if [[ -f /opt/car_hacking/sequence_finder.sh ]]; then echo 'sequence_finder.sh is Installed!'; else echo '\\nInstalling sequence_finder.sh\\n'; sudo cp -f /sdcard/nh_files/can_arsenal/sequence_finder.sh /opt/car_hacking/sequence_finder.sh; sudo chmod +x /opt/car_hacking/sequence_finder.sh;fi; " +
                "echo '\\nSetup done!' && echo '\\nPress any key to continue...' && read -s -n 1 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    // Update item
    public void RunUpdate() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;CAN Arsenal Update\\007\" && clear; echo '\\nUpdating Packages...\\n' && apt update && apt install -y can-utils libsdl2-dev libsdl2-image-dev libconfig-dev libsocketcan-dev can-utils maven autoconf make cmake meson xserver-xephyr x11vnc novnc git python3-pip websockify fluxbox expect && " +
                "if [[ -f /usr/bin/cangen && -f /usr/bin/cansniffer && -f /usr/bin/candump && -f /usr/bin/cansend && -f /usr/bin/canplayer && -d /opt/car_hacking/can-utils  ]]; then echo '\\nCan-Utils detected! Updating...\\n'; cd /opt/car_hacking/can-utils; sudo git pull; sudo make; sudo make install; else echo '\\nCan-Utils not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/cannelloni && -d /opt/car_hacking/cannelloni  ]]; then echo '\\nCannelloni detected! Updating...\\n'; cd /opt/car_hacking/cannelloni; sudo git pull; sudo cmake -DCMAKE_BUILD_TYPE=Release; sudo make; sudo make install; else echo '\\nCannelloni not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/canusb && -d /opt/car_hacking/usb-can  ]]; then echo '\\nUSB-CAN detected! Updating...\\n'; cd /opt/car_hacking/usb-can; sudo git pull; sudo gcc -o canusb canusb.c; sudo cp canusb /usr/local/bin/canusb; else echo '\\nUSB-CAN not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/freediag && -f /usr/local/bin/diag_test && -d /opt/car_hacking/freediag  ]]; then echo '\\nFreediag detected! Updating...\\n'; cd /opt/car_hacking/freediag; sudo git pull;./build_simple.sh; sudo cp build/scantool/freediag /usr/local/bin/freediag && sudo cp build/scantool/diag_test /usr/local/bin/diag_test; else echo '\\nFreediag not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/sbin/socketcand && -d /opt/car_hacking/socketcand ]]; then echo '\\nSocketcand detected! Updating...\\n'; cd /opt/car_hacking; cd /opt/car_hacking/socketcand; sudo git pull; sudo meson setup -Dlibconfig=true --buildtype=release build; sudo meson compile -C build; sudo meson install -C build; else echo '\\nSocketcand not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/hlcand ]]; then echo 'hlcand detected! Updating...\\n'; cd /opt/car_hacking/usb-can-2; sudo git pull; sudo ./build.sh; sudo cp -f src/hlcand /usr/local/bin/hlcand; else echo '\\nhlcand not detected! Please run Setup first.';fi; " +
                "if [[ -f /usr/local/bin/caringcaribou ]]; then echo 'CaringCaribou detected! Updating...\\n'; cd /opt/car_hacking/caringcaribou; sudo git pull; sudo python setup.py install; else echo '\\nCaringCaribou not detected! Please run Setup first.';fi; " +
                "if [[ -f /opt/noVNC/utils/novnc_proxy ]]; then echo 'noVNC detected! Updating...\\n'; else echo '\\nInstalling noVNC\\n'; cd /opt/car_hacking/noVNC; sudo git pull; else echo '\\noVNC not detected! Please run Setup first.'fi; " +
                "if [[ -f /opt/car_hacking/ICSim/builddir/ICSIM ]]; then echo 'ICSIM detected! Updating...\\n'; else echo '\\nInstalling ICSIM\\n'; cd /opt/car_hacking/ICSim; sudo git pull; sudo meson setup builddir && sudo cp /opt/car_hacking/can-utils/lib.o . && cd builddir && sudo meson compile; sudo cp -f /sdcard/nh_files/can_arsenal/icsim_start.sh /opt/car_hacking/icsim_start.sh; sudo chmod +x /opt/car_hacking/icsim_start.sh; else echo '\\ICSIM not detected! Please run Setup first.'fi; " +
                "if [[ -f /opt/car_hacking/can_reset.sh ]]; then echo 'can_reset.sh detected! Updating...\\n'; sudo cp -f /sdcard/nh_files/can_arsenal/can_reset.sh /opt/car_hacking/can_reset.sh; sudo chmod +x /opt/car_hacking/can_reset.sh; else echo '\\ncan_reset.sh script not detected! Please run Setup first.';fi; " +
                "if [[ -f /opt/car_hacking/sequence_finder.sh ]]; then echo 'can_reset.sh detected! Updating...\\n'; sudo cp -f /sdcard/nh_files/can_arsenal/sequence_finder.sh /opt/car_hacking/sequence_finder.sh; sudo chmod +x /opt/car_hacking/sequence_finder.sh; else echo '\\nsequence_finder.sh script not detected! Please run Setup first.';fi; " +
                "echo '\\nEverything is updated! Closing in 3secs..'; sleep 3 && exit");
        sharedpreferences.edit().putBoolean("setup_done", true).apply();
    }

    public void RunAbout() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        MaterialAlertDialogBuilder aboutDialog = new MaterialAlertDialogBuilder(requireActivity(), R.style.DialogStyleCompat);
        aboutDialog.setTitle("About CAN Arsenal");

        TextView message = new TextView(requireContext());
        message.setText(getResources().getText(R.string.about_author));
        message.setMovementMethod(LinkMovementMethod.getInstance());
        message.setPadding(50, 40, 50, 0);
        message.setMovementMethod(LinkMovementMethod.getInstance());
        Linkify.addLinks(message, Linkify.WEB_URLS);

        aboutDialog.setView(message);
        aboutDialog.setNegativeButton("Close", (dialog, id) -> dialog.cancel());
        aboutDialog.show();
    }

    public static class TabsPagerAdapter extends FragmentPagerAdapter {
        TabsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    return new CANFragment.MainFragment();
                case 1:
                    return new CANFragment.ToolsFragment();
                case 2:
                    return new CANFragment.CANUSBFragment();
                case 3:
                    return new CANFragment.CANCARIBOUFragment();
                default:
                    return new CANFragment.CANICSIMFragment();
            }
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public int getCount() {
            return 5;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 4:
                    return "ICSIM";
                case 3:
                    return "Caring Caribou";
                case 2:
                    return "CAN-USB";
                case 1:
                    return "Tools";
                case 0:
                    return "Main Page";
                default:
                    return "";
            }
        }
    }

    public static class MainFragment extends CANFragment {
        private Context context;
        final ShellExecuter exe = new ShellExecuter();
        private CheckBox MTUCheckbox;
        private String mtuValue = "";
        private TextView SelectedMTU;
        private CheckBox TxqueuelenCheckbox;
        private String txqueuelenValue = "";
        private TextView SelectedTxqueuelen;
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
            SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

            // Common used variables
            SelectedIface = rootView.findViewById(R.id.can_iface);

            // Checkboxes
            MTUCheckbox = rootView.findViewById(R.id.can_mtu);
            SelectedMTU = rootView.findViewById(R.id.can_mtu_value);
            TxqueuelenCheckbox = rootView.findViewById(R.id.can_iface_txqueuelen);
            SelectedTxqueuelen = rootView.findViewById(R.id.can_iface_txqueuelen_value);

            final EditText bt_target_mac = rootView.findViewById(R.id.bttarget);

            // First run
            Boolean setupdone = sharedpreferences.getBoolean("setup_done", false);
            if (!setupdone.equals(true)) {
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
            String[] ldAttachCmdHolder = { savedCmd_ldAttach };

            // Short click runs the command
            LdAttachButton.setOnClickListener(v -> {
                String ldAttachRun = ldAttachCmdHolder[0];

                if (!ldAttachRun.isEmpty()) {
                    run_cmd(ldAttachRun);
                    Toast.makeText(requireActivity().getApplicationContext(), "Press CTRL+C to stop.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set your ldattach command!", Toast.LENGTH_LONG).show();
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

                    Toast.makeText(requireActivity().getApplicationContext(), "Command updated!", Toast.LENGTH_SHORT).show();
                });

                builder_ldAttach.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder_ldAttach.show();
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

            // Short click runs the command
            SlcandButton.setOnClickListener(v -> {
                String slcandRun = slcandCmdHolder[0];

                if (!slcandRun.isEmpty()) {
                    run_cmd(slcandRun);
                    Toast.makeText(requireActivity().getApplicationContext(), "Press CTRL+C to stop.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set your slcand command!", Toast.LENGTH_LONG).show();
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

                    Toast.makeText(requireActivity().getApplicationContext(), "Command updated!", Toast.LENGTH_SHORT).show();
                });

                builder_slcand.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder_slcand.show();
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

            // Short click runs the command
            SlcanAttachButton.setOnClickListener(v -> {
                String slcanAttachRun = slcanAttachCmdHolder[0];

                if (!slcanAttachRun.isEmpty()) {
                    run_cmd(slcanAttachRun);
                    Toast.makeText(requireActivity().getApplicationContext(), "Press CTRL+C to stop.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set your slcan_attach command!", Toast.LENGTH_LONG).show();
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

                    Toast.makeText(requireActivity().getApplicationContext(), "Command updated!", Toast.LENGTH_SHORT).show();
                });

                builder_slcanAttach.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder_slcanAttach.show();
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

            // Short click runs the command
            hlcandButton.setOnClickListener(v -> {
                String hlcandRun = hlcandCmdHolder[0];

                if (!hlcandRun.isEmpty()) {
                    run_cmd(hlcandRun);
                    Toast.makeText(requireActivity().getApplicationContext(), "Press CTRL+C to stop.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please set your hlcand command!", Toast.LENGTH_LONG).show();
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

                    Toast.makeText(requireActivity().getApplicationContext(), "Command updated!", Toast.LENGTH_SHORT).show();
                });

                builder_hlcand.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder_hlcand.show();
                return true; // long click handled
            });

            // Interfaces
            // Declare SharedPreferences at the class level
            SharedPreferences preferences = requireActivity().getSharedPreferences("CANInterfaceState", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();

            // Store CAN Interface States
            Map<String, Boolean> buttonStates = new HashMap<>();

            // Load saved button states from SharedPreferences when fragment/activity is created
            buttonStates.put("start_caniface", preferences.getBoolean("start_caniface", false));

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

            // Checkboxes
            MTUCheckbox.setOnClickListener(v -> {
                if (MTUCheckbox.isChecked()) {
                    String selected_mtu = SelectedMTU.getText().toString();
                    mtuValue = " mtu " + selected_mtu;
                } else {
                    mtuValue = "";
                }
            });

            TxqueuelenCheckbox.setOnClickListener(v -> {
                if (TxqueuelenCheckbox.isChecked()) {
                    String selected_txqueuelen = SelectedTxqueuelen.getText().toString();
                    txqueuelenValue = " txqueuelen " + selected_txqueuelen;
                } else {
                    txqueuelenValue = "";
                }
            });

            // Set initial button text based on saved state
            StartCanButton.setText(Boolean.TRUE.equals(buttonStates.get("start_caniface")) ? "⏹ CAN" : "▶ CAN");

            StartCanButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();
                String interface_type = sharedpreferences.getString("cantype_selected", "");
                boolean isStarted = Boolean.TRUE.equals(buttonStates.get("start_caniface"));

                if (!selected_caniface.isEmpty()) {
                    if (isStarted) {
                        String stopCanIface = exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " down && echo Success || echo Failed");
                        stopCanIface = stopCanIface.trim();
                        if ("vcan".equals(interface_type)) {
                            String delVcanIface = exe.RunAsChrootOutput("sudo ip link delete " + selected_caniface + " && echo Success || echo Failed");
                            if (delVcanIface.contains("FATAL:") || delVcanIface.contains("Failed")) {
                                Toast.makeText(requireActivity().getApplicationContext(), "Failed to delete " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                            }
                        }
                        if (stopCanIface.contains("FATAL:") || stopCanIface.contains("Failed")) {
                            Toast.makeText(requireActivity().getApplicationContext(), "Failed to stop " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                        } else {
                            buttonStates.put("start_caniface", false);
                            StartCanButton.setText("▶ CAN");
                            Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " stopped!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        if ("vcan".equals(interface_type)) {
                            String addVcanIface = exe.RunAsChrootOutput("sudo ip link add dev " + selected_caniface + " type " + interface_type + " && echo Success || echo Failed");
                            if (addVcanIface.contains("FATAL:") || addVcanIface.contains("Failed")) {
                                Toast.makeText(requireActivity().getApplicationContext(), "Failed to add " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                            }
                        }
                        if ("can".equals(interface_type) || "slcan".equals(interface_type)) {
                            String usbDevice = exe.RunAsChrootOutput("ls -1 /dev/ttyUSB*;ls -1 /dev/rfcomm*;ls -1 /dev/ttyACM*");
                            if (usbDevice.isEmpty()) {
                                Toast.makeText(requireActivity().getApplicationContext(), "No CAN Hardware detected, please plug you'r adapter and try again.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        } else {
                            String startCanIface = exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + " up && echo Success || echo Failed");
                            if (startCanIface.contains("FATAL:") || startCanIface.contains("Failed")) {
                                Toast.makeText(requireActivity().getApplicationContext(), "Failed to start " + selected_caniface + " interface!", Toast.LENGTH_LONG).show();
                            } else {
                                if (MTUCheckbox.isChecked()) {
                                    exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + mtuValue + " && echo Success || echo Failed");
                                }
                                if (TxqueuelenCheckbox.isChecked()) {
                                    exe.RunAsChrootOutput("sudo ip link set " + selected_caniface + txqueuelenValue + " && echo Success || echo Failed");
                                }
                                buttonStates.put("start_caniface", true);
                                StartCanButton.setText("⏹ CAN");
                                Toast.makeText(requireActivity().getApplicationContext(), "Interface " + selected_caniface + " started!", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                } else {
                    if (selected_caniface.isEmpty()) {
                        Toast.makeText(requireActivity().getApplicationContext(), "Please set a CAN interface!", Toast.LENGTH_LONG).show();
                    }
                }

                // Save button state to SharedPreferences
                editor.putBoolean("start_caniface", Boolean.TRUE.equals(buttonStates.get("start_caniface")));
                editor.apply();
            });

            // Button Reset Interface (temp)
            Button ResetIfaceButton = rootView.findViewById(R.id.reset_iface);

            ResetIfaceButton.setOnClickListener(v -> {
                exe.RunAsChrootOutput("/opt/car_hacking/can_reset.sh");
                buttonStates.put("start_caniface", false);
                StartCanButton.setText("▶ CAN");
                // Save button state to SharedPreferences
                editor.putBoolean("start_caniface", Boolean.TRUE.equals(buttonStates.get("start_caniface")));
                editor.apply();
                Toast.makeText(requireActivity().getApplicationContext(), "Interface reset!", Toast.LENGTH_LONG).show();
            });

            // Start rfcomm binder
            Button RfcommBinderButton = rootView.findViewById(R.id.start_rfcommbinder);

            RfcommBinderButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();
                String bt_target = bt_target_mac.getText().toString();

                if (!selected_caniface.isEmpty() && !bt_target.isEmpty()) {
                    run_cmd("rfcomm bind " + selected_caniface + " " + bt_target);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface and Target field is set!", Toast.LENGTH_LONG).show();
                }
            });

            // Start Socketcand
            Button SocketCandButton = rootView.findViewById(R.id.start_socketcand);

            SocketCandButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();

                if (!selected_caniface.isEmpty()) {
                    run_cmd("socketcand -v -l wlan0 -i " + selected_caniface);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface field is set!", Toast.LENGTH_LONG).show();
                }
            });
            return rootView;
        }
    }

    public static class ToolsFragment extends CANFragment {
        private Activity activity;
        private TextView SelectedIface;
        private TextView SelectedRHost;
        private TextView SelectedRPort;
        private TextView SelectedLPort;
        private CheckBox CanInteractiveCheckbox;
        private String canInteractive = "";
        private CheckBox CanVerboseCheckbox;
        private String canVerbose = "";
        private CheckBox CanDisableLoopbackCheckbox;
        private String canDisableLoopback = "";

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_tools, container, false);

            SelectedIface = rootView.findViewById(R.id.can_iface);
            SelectedRHost = rootView.findViewById(R.id.cannelloni_rhost);
            SelectedRPort = rootView.findViewById(R.id.cannelloni_rport);
            SelectedLPort = rootView.findViewById(R.id.cannelloni_lport);

            CanInteractiveCheckbox = rootView.findViewById(R.id.can_interactive);
            CanVerboseCheckbox = rootView.findViewById(R.id.can_verbose);
            CanDisableLoopbackCheckbox = rootView.findViewById(R.id.can_disable_loopback);

            CanInteractiveCheckbox.setOnClickListener(v -> {
                if (CanInteractiveCheckbox.isChecked())
                    canInteractive = " -i";
                else
                    canInteractive = "";
            });

            CanVerboseCheckbox.setOnClickListener(v -> {
                if (CanVerboseCheckbox.isChecked())
                    canVerbose = " -v";
                else
                    canVerbose = "";
            });

            CanDisableLoopbackCheckbox.setOnClickListener(v -> {
                if (CanDisableLoopbackCheckbox.isChecked())
                    canDisableLoopback = " -x";
                else
                    canDisableLoopback = "";
            });

            final EditText CustomCmd = rootView.findViewById(R.id.customcmd);

            final EditText cansend_sequence = rootView.findViewById(R.id.cansend_sequence);

            // Input File
            final EditText inputfilepath = rootView.findViewById(R.id.inputfilepath);
            final Button inputfilebrowse = rootView.findViewById(R.id.inputfilebrowse);

            inputfilebrowse.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("log/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select input file"), 1001);
            });

            // Output File
            final EditText outputfilepath = rootView.findViewById(R.id.outputfilepath);
            final Button outputfilebrowse = rootView.findViewById(R.id.outputfilebrowse);

            outputfilebrowse.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("log/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select output file"), 1001);
            });

            // Tools
            // Start CanGen
            Button CanGenButton = rootView.findViewById(R.id.start_cangen);

            CanGenButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();

                if (!selected_caniface.isEmpty()) {
                    run_cmd("cangen " + selected_caniface + canVerbose + canDisableLoopback);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface field is set!", Toast.LENGTH_LONG).show();
                }
                activity.invalidateOptionsMenu();
            });

            // Start CanSniffer
            Button CanSnifferButton = rootView.findViewById(R.id.start_cansniffer);

            CanSnifferButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();

                if (!selected_caniface.isEmpty()) {
                    run_cmd("cansniffer " + selected_caniface);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface field is set!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Start CanDump
            Button CanDumpButton = rootView.findViewById(R.id.start_candump);

            CanDumpButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();
                String outputfile = outputfilepath.getText().toString();

                if (!selected_caniface.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("candump " + selected_caniface + " -f " + outputfile);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface and Output File fields is set!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Start CanSend
            Button CanSendButton = rootView.findViewById(R.id.start_cansend);

            CanSendButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();
                String sequence = cansend_sequence.getText().toString();

                if (!selected_caniface.isEmpty() && !sequence.isEmpty()) {
                    run_cmd("cansend " + selected_caniface + " " + sequence);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface and Sequence fields is set!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Start CanPlayer
            Button CanPlayerButton = rootView.findViewById(R.id.start_canplayer);

            CanPlayerButton.setOnClickListener(v -> {
                String inputfile = inputfilepath.getText().toString();

                if (!inputfile.isEmpty()) {
                    run_cmd("canplayer -I " + inputfile + canInteractive + canVerbose + canDisableLoopback);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Input File field is set!", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Input File field is set!", Toast.LENGTH_LONG).show();
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
                String selected_caniface = SelectedIface.getText().toString();
                String rhost = SelectedRHost.getText().toString();
                String rport = SelectedRPort.getText().toString();
                String lport = SelectedLPort.getText().toString();

                if (!selected_caniface.isEmpty() && !rhost.isEmpty() && !rport.isEmpty() && !lport.isEmpty()) {
                    run_cmd("sudo cannelloni -I " + selected_caniface + " -R " + rhost + " -r " + rport + " -l " + lport);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface, RHOST, RPORT, LPORT fields is set!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Logging
            // Start Asc2Log
            Button Asc2LogButton = rootView.findViewById(R.id.start_asc2log);

            Asc2LogButton.setOnClickListener(v ->  {
                String inputfile = inputfilepath.getText().toString();
                String outputfile = outputfilepath.getText().toString();

                if (!inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("asc2log -I " + inputfile + " -O " + outputfile);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Input and Output File fields is set!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Start Log2asc
            Button Log2AscButton = rootView.findViewById(R.id.start_log2asc);

            Log2AscButton.setOnClickListener(v ->  {
                String selected_caniface = SelectedIface.getText().toString();
                String inputfile = inputfilepath.getText().toString();
                String outputfile = outputfilepath.getText().toString();

                if (!selected_caniface.isEmpty() && !inputfile.isEmpty() && !outputfile.isEmpty()) {
                    run_cmd("log2asc -I " + inputfile + " -O " + outputfile + " " + selected_caniface);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your CAN Interface, Input and Output File fields is set!", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your Custom Command field is set!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            return rootView;
        }
    }

    public static class CANUSBFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private CheckBox DebugCheckbox;
        private CheckBox IDCheckbox;
        private CheckBox DataCheckbox;
        private CheckBox SleepCheckbox;
        private CheckBox CountCheckbox;
        private CheckBox ModeCheckbox;
        private String debugCMD = "";
        private String idCMD = "";
        private String dataCMD = "";
        private String sleepCMD = "";
        private String countCMD = "";
        private String modeCMD = "";
        private TextView SelectedBaudrateUSB;
        private TextView SelectedCanSpeedUSB;
        private TextView SelectedData;
        private TextView SelectedID;
        private TextView SelectedSleep;
        private TextView SelectedCount;
        private Context context;
        private Activity activity;
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private String selected_usb;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            activity = getActivity();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
            View rootView = inflater.inflate(R.layout.can_canusb, container, false);

            SelectedBaudrateUSB = rootView.findViewById(R.id.baudrate_usb);
            SelectedCanSpeedUSB = rootView.findViewById(R.id.canspeed_usb);

            // Checkboxes
            DebugCheckbox = rootView.findViewById(R.id.debug_canusb);
            IDCheckbox = rootView.findViewById(R.id.id_canusb);
            DataCheckbox = rootView.findViewById(R.id.data_canusb);
            SleepCheckbox = rootView.findViewById(R.id.sleep_canusb);
            CountCheckbox = rootView.findViewById(R.id.count_canusb);
            ModeCheckbox = rootView.findViewById(R.id.mode_canusb);

            // Checkboxes values
            SelectedID = rootView.findViewById(R.id.id_value_canusb);
            SelectedData = rootView.findViewById(R.id.data_value_canusb);
            SelectedSleep = rootView.findViewById(R.id.sleep_value_canusb);
            SelectedCount = rootView.findViewById(R.id.count_value_canusb);

            // USB interfaces
            final Spinner deviceList = rootView.findViewById(R.id.device_interface);

            final String[] outputDevice = {""};
            executorService.submit(() -> outputDevice[0] = exe.RunAsChrootOutput("ifconfig | awk '/^[a-zA-Z0-9]/ {print $1}' | sed 's/://' | grep -E '^(can|vcan|slcan)[0-9]+$';ls -1 /dev/ttyUSB*;ls -1 /dev/rfcomm*;ls -1 /dev/ttyACM*"));

            final ArrayList<String> deviceIfaces = new ArrayList<>();
            if (outputDevice[0].isEmpty()) {
                deviceIfaces.add("None");
                deviceList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceIfaces));
            } else {
                final String[] deviceifacesArray = outputDevice[0].split("\n");
                deviceList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceifacesArray));
                String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                    Toast.makeText(requireActivity().getApplicationContext(), detected_device, Toast.LENGTH_LONG).show();
                }
            }

            deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    selected_usb = parentView.getItemAtPosition(pos).toString();
                    sharedpreferences.edit().putInt("selected_usb", deviceList.getSelectedItemPosition()).apply();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // Refresh Status
            ImageButton RefreshUSB = rootView.findViewById(R.id.refreshUSB);
            RefreshUSB.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Refreshing Devices...", Toast.LENGTH_SHORT).show();
                refresh(rootView);
            });
            executorService.submit(() -> refresh(rootView));

            // Can-Usb Mode Spinner
            final Spinner canusbModeList = rootView.findViewById(R.id.canusb_mode_spinner);
            final String[] modeOptions = {"0", "1", "2"};

            canusbModeList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, modeOptions));

            canusbModeList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    String canusbmode_selected = parentView.getItemAtPosition(pos).toString();
                    sharedpreferences.edit().putString("canusbmode_selected", canusbmode_selected).apply();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                }
            });

            // USB-CAN
            DebugCheckbox.setOnClickListener(v -> {
                if (DebugCheckbox.isChecked())
                    debugCMD = " -t";
                else
                    debugCMD = "";
            });
            IDCheckbox.setOnClickListener(v -> {
                if (IDCheckbox.isChecked()) {
                    String selected_id = SelectedID.getText().toString();
                    idCMD = " -i " + selected_id;
                } else {
                    idCMD = "";
                }
            });
            DataCheckbox.setOnClickListener(v -> {
                if (DataCheckbox.isChecked()) {
                    String selected_data = SelectedData.getText().toString();
                    dataCMD = " -j " + selected_data;
                } else {
                    dataCMD = "";
                }
            });
            SleepCheckbox.setOnClickListener(v -> {
                if (SleepCheckbox.isChecked()) {
                    String selected_sleep = SelectedSleep.getText().toString();
                    sleepCMD = " -g " + selected_sleep;
                } else {
                    sleepCMD = "";
                }
            });
            CountCheckbox.setOnClickListener(v -> {
                if (CountCheckbox.isChecked()) {
                    String selected_count = SelectedCount.getText().toString();
                    countCMD = " -n " + selected_count;
                } else {
                    countCMD = "";
                }
            });
            ModeCheckbox.setOnClickListener(v -> {
                String USBCANMode = sharedpreferences.getString("canusbmode_selected", "");
                if (ModeCheckbox.isChecked()) {
                    modeCMD = " -m " + USBCANMode;
                } else {
                    modeCMD = "";
                }
            });

            // Start USB-CAN
            Button USBCanSendButton = rootView.findViewById(R.id.start_canusb_send);

            USBCanSendButton.setOnClickListener(v -> {
                String USBCANSpeed = SelectedCanSpeedUSB.getText().toString();
                String USBBaudrate = SelectedBaudrateUSB.getText().toString();

                if (!selected_usb.isEmpty() && !USBCANSpeed.isEmpty() && !USBBaudrate.isEmpty()) {
                    run_cmd("canusb -d " + selected_usb + " -s " + USBCANSpeed + " -b " + USBBaudrate + debugCMD + idCMD + dataCMD + sleepCMD + countCMD + modeCMD);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please ensure your USB Device and USB CAN Speed, Baudrate, Data fields is set!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            return rootView;
        }

        // Refresh main
        private void refresh(View CANFragment) {
            final Spinner deviceList = CANFragment.findViewById(R.id.device_interface);
            SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

            requireActivity().runOnUiThread(() -> {
                String outputDevice = exe.RunAsChrootOutput("ls -1 /sys/class/net/ | grep can;ls -1 /dev/ttyUSB*;ls -1 /dev/rfcomm*;ls -1 /dev/ttyACM*");
                final ArrayList<String> deviceIfaces = new ArrayList<>();
                if (outputDevice.isEmpty()) {
                    deviceIfaces.add("None");
                    deviceList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceIfaces));
                    sharedpreferences.edit().putInt("selected_device", deviceList.getSelectedItemPosition()).apply();
                } else {
                    final String[] deviceifacesArray = outputDevice.split("\n");
                    deviceList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceifacesArray));
                    int lastiface = sharedpreferences.getInt("selected_device", 0);
                    deviceList.setSelection(lastiface);
                    String detected_device = exe.RunAsChrootOutput("dmesg | grep \"now attached to\" | tail -1 | awk '{ $1=$2=$3=$4=\"\"; print substr($0, 5) }'");
                    if (detected_device != null && !detected_device.isEmpty() && !detected_device.matches("^(can|vcan|slcan)\\d+$")) {
                        Toast.makeText(requireActivity().getApplicationContext(), detected_device, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    public static class CANCARIBOUFragment extends CANFragment {
        final ShellExecuter exe = new ShellExecuter();
        private Context context;
        private Activity activity;
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private TextView SelectedIface;
        private TextView SelectedFile;
        private TextView SelectedMessage;
        private CheckBox IdCheckbox;
        private CheckBox SrcCheckbox;
        private CheckBox DstCheckbox;
        private CheckBox AddrCheckbox;
        private CheckBox LengthCheckbox;
        private CheckBox MinCheckbox;
        private CheckBox MaxCheckbox;
        private CheckBox DelayCheckbox;
        private CheckBox SeedCheckbox;
        private CheckBox CandumpFormatCheckbox;
        private CheckBox LoopCheckbox;
        private CheckBox ReverseCheckbox;
        private CheckBox SaveOutputCheckbox;
        private CheckBox SeparateLineCheckbox;
        private CheckBox PadCheckbox;
        private String idValue = "";
        private String srcValue = "";
        private String dstValue = "";
        private String addrValue = "";
        private String lengthValue = "";
        private String minValue = "";
        private String maxValue = "";
        private String delayValue = "";
        private String seedValue = "";
        private String separateLineValue = "";
        private String candumpFormat = "";
        private String loopEnabled = "";
        private String outputEnabled = "";
        private String reverseEnabled = "";
        private String padEnabled = "";
        private TextView SelectedID;
        private TextView SelectedSRC;
        private TextView SelectedDST;
        private TextView SelectedADDR;
        private TextView SelectedLENGTH;
        private TextView SelectedMin;
        private TextView SelectedMax;
        private TextView SelectedDelay;
        private TextView SelectedSeed;
        private TextView SelectedSeparateLine;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            activity = getActivity();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
            View rootView = inflater.inflate(R.layout.can_caribou, container, false);

            // File
            final EditText cariboufilepath = rootView.findViewById(R.id.caribou_file);
            final Button cariboufilebrowse = rootView.findViewById(R.id.cariboufilebrowse);

            cariboufilebrowse.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = {"text/plain", "application/octet-stream"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select input file"), 1001);
            });

            SelectedIface = rootView.findViewById(R.id.can_iface);
            SelectedFile = rootView.findViewById(R.id.caribou_file);
            SelectedMessage = rootView.findViewById(R.id.caribou_message);

            // Checkboxes
            IdCheckbox = rootView.findViewById(R.id.caribou_id);
            SrcCheckbox = rootView.findViewById(R.id.caribou_src);
            DstCheckbox = rootView.findViewById(R.id.caribou_dst);
            AddrCheckbox = rootView.findViewById(R.id.caribou_start_addr);
            LengthCheckbox = rootView.findViewById(R.id.caribou_length);
            MinCheckbox = rootView.findViewById(R.id.caribou_min);
            MaxCheckbox = rootView.findViewById(R.id.caribou_max);
            DelayCheckbox = rootView.findViewById(R.id.caribou_delay);
            SeedCheckbox = rootView.findViewById(R.id.caribou_seed);
            CandumpFormatCheckbox = rootView.findViewById(R.id.caribou_candump_format);
            LoopCheckbox = rootView.findViewById(R.id.caribou_loop);
            ReverseCheckbox = rootView.findViewById(R.id.caribou_reverse);
            SaveOutputCheckbox = rootView.findViewById(R.id.caribou_save_output);
            PadCheckbox = rootView.findViewById(R.id.caribou_pad);
            SeparateLineCheckbox = rootView.findViewById(R.id.caribou_separate_line);

            // Checkboxes values
            SelectedID = rootView.findViewById(R.id.caribou_id_value);
            SelectedSRC = rootView.findViewById(R.id.caribou_src_value);
            SelectedDST = rootView.findViewById(R.id.caribou_dst_value);
            SelectedADDR = rootView.findViewById(R.id.caribou_start_addr_value);
            SelectedLENGTH = rootView.findViewById(R.id.caribou_length_value);
            SelectedMin = rootView.findViewById(R.id.caribou_min_value);
            SelectedMax = rootView.findViewById(R.id.caribou_max_value);
            SelectedDelay = rootView.findViewById(R.id.caribou_delay_value);
            SelectedSeed = rootView.findViewById(R.id.caribou_seed_value);
            SelectedSeparateLine = rootView.findViewById(R.id.caribou_separate_line_value);

            IdCheckbox.setOnClickListener(v -> {
                if (IdCheckbox.isChecked()) {
                    String selected_id = SelectedID.getText().toString();
                    idValue = " " + selected_id;
                } else {
                    idValue = "";
                }
            });

            SrcCheckbox.setOnClickListener(v -> {
                if (SrcCheckbox.isChecked()) {
                    String selected_src = SelectedSRC.getText().toString();
                    srcValue = " " + selected_src;
                } else {
                    srcValue = "";
                }
            });

            DstCheckbox.setOnClickListener(v -> {
                if (DstCheckbox.isChecked()) {
                    String selected_dst = SelectedDST.getText().toString();
                    dstValue = " " + selected_dst;
                } else {
                    dstValue = "";
                }
            });

            AddrCheckbox.setOnClickListener(v -> {
                if (AddrCheckbox.isChecked()) {
                    String selected_addr = SelectedADDR.getText().toString();
                    addrValue = " " + selected_addr;
                } else {
                    addrValue = "";
                }
            });

            LengthCheckbox.setOnClickListener(v -> {
                if (LengthCheckbox.isChecked()) {
                    String selected_length = SelectedLENGTH.getText().toString();
                    lengthValue = " " + selected_length;
                } else {
                    lengthValue = "";
                }
            });

            MinCheckbox.setOnClickListener(v -> {
                if (MinCheckbox.isChecked()) {
                    String selected_min = SelectedMin.getText().toString();
                    minValue = " -min " + selected_min;
                } else {
                    minValue = "";
                }
            });

            MaxCheckbox.setOnClickListener(v -> {
                if (MaxCheckbox.isChecked()) {
                    String selected_max = SelectedMax.getText().toString();
                    maxValue = " -max " + selected_max;
                } else {
                    maxValue = "";
                }
            });

            DelayCheckbox.setOnClickListener(v -> {
                if (DelayCheckbox.isChecked()) {
                    String selected_delay = SelectedDelay.getText().toString();
                    delayValue = " -d " + selected_delay;
                } else {
                    delayValue = "";
                }
            });

            SeedCheckbox.setOnClickListener(v -> {
                if (SeedCheckbox.isChecked()) {
                    String selected_seed = SelectedSeed.getText().toString();
                    seedValue = " --seed " + selected_seed;
                } else {
                    seedValue = "";
                }
            });

            SeparateLineCheckbox.setOnClickListener(v -> {
                if (SeparateLineCheckbox.isChecked()) {
                    String selected_separateLine = SelectedSeparateLine.getText().toString();
                    separateLineValue = " -s " + selected_separateLine;
                } else {
                    separateLineValue = "";
                }
            });

            CandumpFormatCheckbox.setOnClickListener(v -> {
                if (CandumpFormatCheckbox.isChecked())
                    candumpFormat = " -t";
                else
                    candumpFormat = "";
            });

            LoopCheckbox.setOnClickListener(v -> {
                if (LoopCheckbox.isChecked())
                    loopEnabled = " -l";
                else
                    loopEnabled = "";
            });

            ReverseCheckbox.setOnClickListener(v -> {
                if (ReverseCheckbox.isChecked())
                    reverseEnabled = " -r";
                else
                    reverseEnabled = "";
            });

            PadCheckbox.setOnClickListener(v -> {
                if (PadCheckbox.isChecked())
                    padEnabled = " -p";
                else
                    padEnabled = "";
            });

            SaveOutputCheckbox.setOnClickListener(v -> {
                if (SaveOutputCheckbox.isChecked()) {
                    String file = cariboufilepath.getText().toString();
                    outputEnabled = " -f " + file;
                } else {
                    outputEnabled = "";
                }
            });

            // Start Dump
            Button CaribouDumpButton = rootView.findViewById(R.id.start_dump);

            CaribouDumpButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();

                if (!selected_caniface.isEmpty()) {
                    run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " dump" + separateLineValue + candumpFormat + outputEnabled);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please chose a CAN Interface!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Start Listener
            Button CaribouListenerButton = rootView.findViewById(R.id.start_listener);

            CaribouListenerButton.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();

                if (!selected_caniface.isEmpty()) {
                    run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " listener" + reverseEnabled);
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please chose a CAN Interface!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Fuzzer
            SharedPreferences preferencesFUZZER = requireActivity().getSharedPreferences("FUZZERModule", Context.MODE_PRIVATE);

            // Store FUZZER Module
            Map<String, Boolean> fuzzerMode = new HashMap<>();

            // Load saved button states from SharedPreferences when fragment/activity is created
            fuzzerMode.put("start_fuzzer", preferencesFUZZER.getBoolean("start_fuzzer", false));

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
                String selected_caniface = SelectedIface.getText().toString();
                String fuzzer_module = sharedpreferences.getString("fuzzer_selected", "");

                if (!selected_caniface.isEmpty()) {
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
                    Toast.makeText(requireActivity().getApplicationContext(), "Please chose a CAN Interface!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // Send
            SharedPreferences preferencesSEND = requireActivity().getSharedPreferences("SENDModule", Context.MODE_PRIVATE);

            // Store SEND Module
            Map<String, Boolean> sendMode = new HashMap<>();

            // Load saved button states from SharedPreferences when fragment/activity is created
            sendMode.put("start_send", preferencesSEND.getBoolean("start_send", false));

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
                String selected_caniface = SelectedIface.getText().toString();
                String selected_message = SelectedMessage.getText().toString();
                String selected_file = SelectedFile.getText().toString();
                String send_module = sharedpreferences.getString("send_selected", "");

                if (!selected_caniface.isEmpty()) {
                    if ("file".equals(send_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " send file" + delayValue + loopEnabled + " " + selected_file);
                    }
                    if ("message".equals(send_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " send message" + padEnabled + delayValue + loopEnabled + " " + selected_message);
                    }
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please chose a CAN Interface!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // UDS
            SharedPreferences preferencesUDS = requireActivity().getSharedPreferences("UDSModule", Context.MODE_PRIVATE);

            // Store UDS Module
            Map<String, Boolean> udsMode = new HashMap<>();

            // Load saved button states from SharedPreferences when fragment/activity is created
            udsMode.put("start_uds", preferencesUDS.getBoolean("start_uds", false));

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
                String selected_caniface = SelectedIface.getText().toString();
                String uds_module = sharedpreferences.getString("uds_selected", "");

                if (!selected_caniface.isEmpty()) {
                    if ("discovery".equals(uds_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " uds discovery" + minValue + maxValue + delayValue);
                    }
                    if ("services".equals(uds_module)) {
                        run_cmd("printf \"[default]\ninterface = socketcan\nchannel = " + selected_caniface + "\" > $HOME/.canrc && caringcaribou -i " + selected_caniface + " uds services" + srcValue + dstValue);
                    }
                } else {
                    Toast.makeText(requireActivity().getApplicationContext(), "Please chose a CAN Interface!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            // XCP
            SharedPreferences preferencesXCP = requireActivity().getSharedPreferences("XCPModule", Context.MODE_PRIVATE);

            // Store XCP Module
            Map<String, Boolean> xcpMode = new HashMap<>();

            // Load saved button states from SharedPreferences when fragment/activity is created
            xcpMode.put("start_xcp", preferencesXCP.getBoolean("start_xcp", false));

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
                String selected_caniface = SelectedIface.getText().toString();
                String xcp_module = sharedpreferences.getString("xcp_selected", "");

                if (!selected_caniface.isEmpty()) {
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
                    Toast.makeText(requireActivity().getApplicationContext(), "Please chose a CAN Interface!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            return rootView;
        }
    }


    public static class CANICSIMFragment extends CANFragment {
        private Activity activity;
        private TextView SelectedIface;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activity = getActivity();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.can_icsim, container, false);

            SelectedIface = rootView.findViewById(R.id.can_iface);

            WebView myBrowser = rootView.findViewById(R.id.icsim);
            WebSettings webSettings = myBrowser.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true); // ← active localStorage

            webSettings.setLoadWithOverviewMode(true);
            webSettings.setUseWideViewPort(true);
            webSettings.setBuiltInZoomControls(true);
            webSettings.setDisplayZoomControls(false);

            myBrowser.setWebViewClient(new WebViewClient());

            // ICSIM
            Button runICSIM = rootView.findViewById(R.id.run_icsim);
            runICSIM.setOnClickListener(v -> {
                String selected_caniface = SelectedIface.getText().toString();

                if (!selected_caniface.isEmpty()) {
                    run_cmd("su -c 'sh /opt/car_hacking/icsim_start.sh " + selected_caniface + "'");
                    new Handler().postDelayed(() -> {
                        myBrowser.loadUrl("http://localhost:6080/vnc.html?autoconnect=true&resize=scale");
                    }, 15000);
                } else {
                    Toast.makeText(requireActivity(), "Please set a CAN interface!", Toast.LENGTH_LONG).show();
                }

                activity.invalidateOptionsMenu();
            });

            Button refreshButton = rootView.findViewById(R.id.refresh_icsim);
            refreshButton.setOnClickListener(v -> {
                Toast.makeText(activity, "Refreshing ICSim display...", Toast.LENGTH_SHORT).show();
                myBrowser.loadUrl("http://localhost:6080/vnc.html?autoconnect=true&resize=scale");
                myBrowser.reload();
            });

            return rootView;
        }
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

    public void run_cmd(String cmd) {
        Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
    }
}