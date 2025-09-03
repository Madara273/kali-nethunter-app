package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.io.Files;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

public class WifipumpkinFragment extends Fragment {
    private ViewPager2 mViewPager;
    private SharedPreferences sharedpreferences;
    private Integer selectedScriptIndex = 0;
    private final CharSequence[] scripts = {"mana-nat-full", "mana-nat-simple", "mana-nat-bettercap", "mana-nat-simple-bdf", "hostapd-wpe", "hostapd-wpe-karma"};
    private static final String TAG = "WifipumpkinFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private String selected_template;
    private Context context;
    private Activity activity;
    final ShellExecuter exe = new ShellExecuter();
    private String template_src;

    private final ActivityResultLauncher<String> pickZipLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                // Keep existing logic
                String FilePath = Objects.requireNonNull(uri.getPath());
                FilePath = exe.RunAsRootOutput("echo " + FilePath + " | sed -e 's/\\/document\\/primary:/\\/sdcard\\//g'");
                String FilePy = exe.RunAsRootOutput(
                        NhPaths.APP_SCRIPTS_PATH + "/bootkali custom_cmd unzip -Z1 '" + FilePath + "' | grep .py | awk -F'.' '{print $1}'");
                run_cmd("wifipumpkin3 -x \"use misc.custom_captiveflask; install " + FilePy + " \\\"" +  FilePath + "\\\"; back; exit\";exit");
            });

    public static WifipumpkinFragment newInstance(int sectionNumber) {
        WifipumpkinFragment fragment = new WifipumpkinFragment();
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
        String configFilePath = NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.wifipumpkin_hostapd, container, false);
        final Button StartButton = rootView.findViewById(R.id.wp3start_button);
        SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        boolean iswatch = requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        CheckBox PreviewCheckbox = rootView.findViewById(R.id.preview_checkbox);

        // First run
        Boolean setupwp3done = sharedpreferences.getBoolean("wp3_setup_done", false);
        String packages = exe.RunAsChrootOutput("if [[ -f /usr/bin/wifipumpkin3 || -f /usr/bin/dnschef ]];then echo Good;else echo Nope;fi");

        // if (!setupwp3done.equals(true))
        if (packages.equals("Nope")) SetupDialog();

        // Watch optimisation
        final TextView Wp3desc = rootView.findViewById(R.id.wp3_desc);
        if (iswatch) {
            Wp3desc.setVisibility(View.GONE);
        }

        // Selected iface, name, ssid, bssid, channel, wlan0to1
        final EditText APinterface = rootView.findViewById(R.id.ap_interface);
        final EditText NETinterface = rootView.findViewById(R.id.net_interface);
        final EditText SSID = rootView.findViewById(R.id.ssid);
        final EditText BSSID = rootView.findViewById(R.id.bssid);
        final EditText Channel = rootView.findViewById(R.id.channel);
        final CheckBox Wlan0to1Checkbox = rootView.findViewById(R.id.wlan0to1_checkbox);

        // Templates spinner
        refresh_wp3_templates(rootView);
        Spinner TemplatesSpinner = rootView.findViewById(R.id.templates);

        // Select Template
        WebView myBrowser = rootView.findViewById(R.id.mybrowser);
        final String[] TemplateString = {""};
        TemplatesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                selected_template = parentView.getItemAtPosition(pos).toString();
                if (selected_template.equals("None")) {
                    PreviewCheckbox.setChecked(false);
                    PreviewCheckbox.setEnabled(false);
                    TemplateString[0] = "";
                } else {
                    PreviewCheckbox.setEnabled(true);
                    if (selected_template.equals("FlaskDemo")) {
                    template_src = NhPaths.CHROOT_PATH() + NhPaths.SD_PATH + "/nh_files/templates/" + selected_template + "/templates/En/templates/login.html";
                    } else {
                    template_src = NhPaths.CHROOT_PATH() + NhPaths.SD_PATH + "/nh_files/templates/" + selected_template + "/templates/login.html";
                    }
                    myBrowser.clearCache(true);
                    myBrowser.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    myBrowser.getSettings().setDomStorageEnabled(true);
                    myBrowser.getSettings().setLoadsImagesAutomatically(true);
                    //myBrowser.setInitialScale(200);
                    myBrowser.getSettings().setJavaScriptEnabled(true); // Enable JavaScript Support
                    myBrowser.setWebViewClient(new WebViewClient());
                    myBrowser.getSettings().setAllowFileAccess(true);
                    String externalStoragePath = Environment.getExternalStorageDirectory().getPath();
                    myBrowser.loadDataWithBaseURL("file://" + externalStoragePath + "/nh_files/templates/" + selected_template + "/static", template_src, "text/html", "UTF-8", null);
                    myBrowser.loadUrl(externalStoragePath + "/nh_files/templates/" + selected_template + "/templates/login.html");
                    TemplateString[0] = selected_template;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        // Check iptables version
        checkiptables();

        // Check wlan0 AP mode
        TextView APmode = rootView.findViewById(R.id.wlan0ap);
        String Wlan0AP = exe.RunAsRootOutput("iw list | grep '* AP'");
        if (Wlan0AP.contains("* AP")) APmode.setText(R.string.wp3_ap_mode_supported);
        else APmode.setText(R.string.wp3_ap_mode_not_supported);

        // Refresh
        refresh_wp3_templates(rootView);
        ImageButton RefreshTemplates = rootView.findViewById(R.id.refreshTemplates);
        RefreshTemplates.setOnClickListener(v -> refresh_wp3_templates(rootView));

        // Load Settings
        String PrevAPiface = exe.RunAsRootOutput("grep ^APIFACE= " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh | awk -F'=' '{print $2}'");
        APinterface.setText(PrevAPiface);
        String PrevNETiface = exe.RunAsRootOutput("grep ^NETIFACE= " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh | awk -F'=' '{print $2}'");
        NETinterface.setText(PrevNETiface);
        String PrevSSID = exe.RunAsRootOutput("grep ^SSID= " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh | awk -F'=' '{print $2}' | tr -d '\"'");
        SSID.setText(PrevSSID);
        String PrevBSSID = exe.RunAsRootOutput("grep ^BSSID= " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh | awk -F'=' '{print $2}'");
        BSSID.setText(PrevBSSID);
        String PrevChannel = exe.RunAsRootOutput("grep ^CHANNEL= " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh | awk -F'=' '{print $2}'");
        Channel.setText(PrevChannel);
        String PrevWlan0to1 = exe.RunAsRootOutput("grep ^WLAN0TO1= " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh | awk -F'=' '{print $2}'");
        Wlan0to1Checkbox.setChecked(PrevWlan0to1.equals("1"));

        // Wlan0to1 Checkbox
        final String[] Wlan0to1_string = {""};

        // Preview Checkbox
        View PreView = rootView.findViewById(R.id.pre_view);
        PreviewCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                PreView.setVisibility(View.VISIBLE);
            } else {
                PreView.setVisibility(View.GONE);
            }
        });

        // Start
        StartButton.setOnClickListener( v -> {
            if (StartButton.getText().equals("Start")) {
                String APiface_string = APinterface.getText().toString();
                String NETiface_string = NETinterface.getText().toString();
                String SSID_string = SSID.getText().toString();
                String BSSID_string = BSSID.getText().toString();
                String Channel_string = Channel.getText().toString();
                if (Wlan0to1Checkbox.isChecked()) {
                    Wlan0to1_string[0] = "1";
                } else {
                    Wlan0to1_string[0] = "0";
                }
                Toast.makeText(requireActivity().getApplicationContext(), "Starting.. type 'exit' into the terminal to stop Wifipumpkin3", Toast.LENGTH_LONG).show();

                exe.RunAsRoot(new String[]{"sed -i '/^APIFACE=/c\\APIFACE=" + APiface_string + "' " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh"});
                exe.RunAsRoot(new String[]{"sed -i '/^NETIFACE=/c\\NETIFACE=" + NETiface_string + "' " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh"});
                exe.RunAsRoot(new String[]{"sed -i '/^SSID=/c\\SSID=\"" + SSID_string + "\"' " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh"});
                exe.RunAsRoot(new String[]{"sed -i '/^BSSID=/c\\BSSID=" + BSSID_string + "' " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh"});
                exe.RunAsRoot(new String[]{"sed -i '/^CHANNEL=/c\\CHANNEL=" + Channel_string + "' " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh"});
                exe.RunAsRoot(new String[]{"sed -i '/^WLAN0TO1=/c\\WLAN0TO1=" + Wlan0to1_string[0] + "' " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh"});
                exe.RunAsRoot(new String[]{"sed -i '/^TEMPLATE=/c\\TEMPLATE=" + TemplateString[0] + "' " + NhPaths.APP_SD_FILES_PATH + "/modules/start-wp3.sh"});
                run_cmd("echo -ne \"\\033]0;Wifipumpkin3\\007\" && clear;bash /sdcard/nh_files/modules/start-wp3.sh");

            } else if (StartButton.getText().equals("Stop")) {
                exe.RunAsRoot(new String[]{"kill `ps -ef | grep '[btk]_server' | awk {'print $2'}`"});
                exe.RunAsRoot(new String[]{"pkill python3"});
                refresh_wp3_templates(rootView);
            }
        });

        // Load from file
        final Button injectStringButton = rootView.findViewById(R.id.templatebrowse);
        injectStringButton.setOnClickListener(v -> pickZipLauncher.launch("application/zip"));
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.bt, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.setup || id == R.id.update) {
                    RunSetup();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private final ActivityResultLauncher<Intent> pickFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ShellExecuter exe = new ShellExecuter();
                    String FilePath = Objects.requireNonNull(result.getData().getData()).getPath();
                    FilePath = exe.RunAsRootOutput("echo " + FilePath + " | sed -e 's/\\/document\\/primary:/\\/sdcard\\//g'");
                    String FilePy = exe.RunAsRootOutput(NhPaths.APP_SCRIPTS_PATH + "/bootkali custom_cmd unzip -Z1 '" + FilePath + "' | grep .py | awk -F'.' '{print $1}'");
                    run_cmd("wifipumpkin3 -x \"use misc.custom_captiveflask; install " + FilePy + " \\\"" + FilePath + "\\\"; back; exit\";exit");
                }
            });

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        pickFileLauncher.launch(Intent.createChooser(intent, "Select zip file"));
    }

    // First setup
    public void SetupDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.DialogStyleCompat);
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        builder.setTitle("Welcome to Wifipumpkin3!");
        builder.setMessage("You have missing packages. Install them now?");
        builder.setPositiveButton("Install", (dialog, which) -> {
            RunSetup();
            sharedpreferences.edit().putBoolean("wp3_setup_done", true).apply();
        });
        builder.show();
    }

    public void RunSetup() {
        sharedpreferences = activity.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        run_cmd("echo -ne \"\\033]0;Wifipumpkin3 Setup\\007\" && clear;apt update && apt install wifipumpkin3 dnschef -y; wp3" +
                "echo 'Done!'; echo 'Closing in 3secs..'; sleep 3 && exit ");
        sharedpreferences.edit().putBoolean("wp3_setup_done", true).apply();
    }

    // Refresh templates
    private void refresh_wp3_templates(View WifipumpkinFragment) {
        Spinner TemplatesSpinner = WifipumpkinFragment.findViewById(R.id.templates);
        final String outputTemplates = "None\n" + exe.RunAsChrootOutput("ls -1 /usr/share/wifipumpkin3/config/templates | sort");
        final String[] TemplatesArray = outputTemplates.split("\n");
        TemplatesSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, TemplatesArray));
    }

    private void checkiptables() {
        ShellExecuter exe = new ShellExecuter();
        String iptables_ver = exe.RunAsChrootOutput("iptables -V | grep iptables");
        String old_kali = "https://old.kali.org/kali/pool/main/i/iptables/";
        if (iptables_ver.equals("iptables v1.6.2")) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
            builder.setTitle("You need to upgrade iptables!");
            builder.setMessage("We appreciate your patience for using Mana with old iptables. It can be finally upgraded.");
            builder.setPositiveButton("Upgrade", (dialog, which) -> run_cmd("echo -ne \"\\033]0;Upgrading iptables\\007\" && clear;" +
                    "apt-mark unhold libip* > /dev/null 2>&1 ; " +
                    "apt-mark unhold libxtables* > /dev/null 2>&1 ; " +
                    "apt-mark unhold iptables* > /dev/null 2>&1 ; " +
                    "apt install iptables -y && sleep 2 && echo 'Done! Closing window..' && exit"));
            builder.setNegativeButton("Close", (dialog, which) -> {
            });
            builder.show();
        }
    }

    private void startWP3() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
        builder.setTitle("Script to execute:");
        builder.setPositiveButton("Start", (dialog, which) -> {
            switch (selectedScriptIndex) {
                case 0:
                    NhPaths.showMessage(context, "Starting MANA NAT FULL");
                    run_cmd(NhPaths.makeTermTitle("MANA-FULL") + "/usr/share/mana-toolkit/run-mana/start-nat-full-lollipop.sh");
                    break;
                case 1:
                    NhPaths.showMessage(context, "Starting MANA NAT SIMPLE");
                    run_cmd(NhPaths.makeTermTitle("MANA-SIMPLE") + "/usr/share/mana-toolkit/run-mana/start-nat-simple-lollipop.sh");
                    break;
                case 2:
                    NhPaths.showMessage(context, "Starting MANA Bettercap");
                    run_cmd(NhPaths.makeTermTitle("MANA-BETTERCAP") + "/usr/bin/start-nat-transproxy-lollipop.sh");
                    break;
                case 3:
                    NhPaths.showMessage(context, "Starting MANA NAT SIMPLE && BDF");
                    run_cmd(NhPaths.makeTermTitle("MANA-BDF") + "/usr/share/mana-toolkit/run-mana/start-nat-simple-bdf-lollipop.sh");

                    // Delay launching msfconsole
                    new android.os.Handler(Looper.getMainLooper()).postDelayed(
                            () -> {
                                NhPaths.showMessage(context, "Starting MSF with BDF resource.rc");
                                run_cmd(NhPaths.makeTermTitle("MSF") + "msfconsole -q -r /usr/share/bdfproxy/bdfproxy_msf_resource.rc");
                            }, 10000);
                    break;
                case 4:
                    NhPaths.showMessage(context, "Starting HOSTAPD-WPE");
                    run_cmd(NhPaths.makeTermTitle("HOSTAPD-WPE") + "ip link set wlan1 up && /usr/sbin/hostapd-wpe /sdcard/nh_files/configs/hostapd-wpe.conf");
                    break;
                case 5:
                    NhPaths.showMessage(context, "Starting HOSTAPD-WPE with Karma");
                    run_cmd(NhPaths.makeTermTitle("HOSTAPD-WPE-KARMA") + "ip link set wlan1 up && /usr/sbin/hostapd-wpe -k /sdcard/nh_files/configs/hostapd-wpe.conf");
                    break;
                default:
                    NhPaths.showMessage(context, "Invalid script!");
                    return;
            }
            NhPaths.showMessage(context, getString(R.string.attack_launched));
        });
        builder.setNegativeButton("Quit", (dialog, which) -> {
        });
        builder.setSingleChoiceItems(scripts, selectedScriptIndex, (dialog, which) -> selectedScriptIndex = which);
        builder.show();
    }

    public class HostapdFragmentWPE extends Fragment {
        private Context context;
        private String configFilePath;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            configFilePath = NhPaths.APP_SD_FILES_PATH + "/configs/hostapd-wpe.conf";
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.mana_hostapd_wpe, container, false);

            Button button = rootView.findViewById(R.id.wpe_updateButton);
            Button gencerts = rootView.findViewById(R.id.wpe_generate_certs);
            loadOptions(rootView);

            // Extracted command as a constant
            final String GENERATE_CERTS_CMD = "cd /etc/hostapd-wpe/certs && ./bootstrap";

            gencerts.setOnClickListener(v -> run_cmd(GENERATE_CERTS_CMD));

            button.setOnClickListener(v -> {
                ShellExecuter exe = new ShellExecuter();
                File file = new File(configFilePath);
                String source;
                try {
                    source = Files.asCharSource(file, StandardCharsets.UTF_8).read();
                } catch (IOException e) {
                    NhPaths.showMessage(context, "Failed to read the configuration file.");
                    e.printStackTrace();
                    return;
                }

                View view = getView();
                if (view == null) {
                    return;
                }

                EditText ifc = view.findViewById(R.id.wpe_ifc);
                EditText bssid = view.findViewById(R.id.wpe_bssid);
                EditText ssid = view.findViewById(R.id.wpe_ssid);
                EditText channel = view.findViewById(R.id.wpe_channel);
                EditText privatekey = view.findViewById(R.id.wpe_private_key);

                source = updateConfig(source, "interface", ifc.getText().toString());
                source = updateConfig(source, "bssid", bssid.getText().toString());
                source = updateConfig(source, "ssid", ssid.getText().toString());
                source = updateConfig(source, "channel", channel.getText().toString());
                source = updateConfig(source, "private_key_passwd", privatekey.getText().toString());

                exe.SaveFileContents(source, configFilePath);
                NhPaths.showMessage(context, "Source updated");
            });
            return rootView;
        }

        private String updateConfig(String source, String key, String value) {
            return source.replaceAll("(?m)^" + key + "=(.*)$", key + "=" + value);
        }

        private void loadOptions(View rootView) {
            final EditText ifc = rootView.findViewById(R.id.wpe_ifc);
            final EditText bssid = rootView.findViewById(R.id.wpe_bssid);
            final EditText ssid = rootView.findViewById(R.id.wpe_ssid);
            final EditText channel = rootView.findViewById(R.id.wpe_channel);
            final EditText privatekey = rootView.findViewById(R.id.wpe_private_key);

            new Thread(() -> {
                ShellExecuter exe = new ShellExecuter();
                Log.d("exe: ", configFilePath);
                String text = exe.ReadFile_SYNC(configFilePath);

                String regExpatInterface = "^interface=(.*)$";
                Pattern patternIfc = Pattern.compile(regExpatInterface, Pattern.MULTILINE);
                final Matcher matcherIfc = patternIfc.matcher(text);

                String regExpatbssid = "^bssid=(.*)$";
                Pattern patternBssid = Pattern.compile(regExpatbssid, Pattern.MULTILINE);
                final Matcher matcherBssid = patternBssid.matcher(text);

                String regExpatssid = "^ssid=(.*)$";
                Pattern patternSsid = Pattern.compile(regExpatssid, Pattern.MULTILINE);
                final Matcher matcherSsid = patternSsid.matcher(text);

                String regExpatChannel = "^channel=(.*)$";
                Pattern patternChannel = Pattern.compile(regExpatChannel, Pattern.MULTILINE);
                final Matcher matcherChannel = patternChannel.matcher(text);

                String regExpatEnablePrivateKey = "^private_key_passwd=(.*)$";
                Pattern patternEnablePrivateKey = Pattern.compile(regExpatEnablePrivateKey, Pattern.MULTILINE);
                final Matcher matcherPrivateKey = patternEnablePrivateKey.matcher(text);

                ifc.post(() -> {
                /*
                 * Interface
                 */
                    if (matcherIfc.find()) {
                        String ifcValue = matcherIfc.group(1);
                        ifc.setText(ifcValue);
                    }
                /*
                 * bssid
                 */
                    if (matcherBssid.find()) {
                        String bssidVal = matcherBssid.group(1);
                        bssid.setText(bssidVal);
                    }
                /*
                 * ssid
                 */
                    if (matcherSsid.find()) {
                        String ssidVal = matcherSsid.group(1);
                        ssid.setText(ssidVal);
                    }
                /*
                 * channel
                 */
                    if (matcherChannel.find()) {
                        String channelVal = matcherChannel.group(1);
                        channel.setText(channelVal);
                    }
                /*
                 * Private Key File
                 */
                    if (matcherPrivateKey.find()) {
                        String PrivateKeyVal = matcherPrivateKey.group(1);
                        privatekey.setText(PrivateKeyVal);
                    }
                });
            }).start();
        }
    }

    public static class DhcpdFragment extends Fragment {
        final ShellExecuter exe = new ShellExecuter();
        private Context context;
        private String configFilePath;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            configFilePath = NhPaths.CHROOT_PATH() + "/etc/dhcp/dhcpd.conf";
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final View rootView = inflater.inflate(R.layout.source_short, container, false);

            String description = getResources().getString(R.string.mana_dhcpd);
            TextView desc = rootView.findViewById(R.id.description);
            desc.setText(description);

            EditText source = rootView.findViewById(R.id.source);
            exe.ReadFile_ASYNC(configFilePath, source);
            Button button = rootView.findViewById(R.id.update);
            button.setOnClickListener(v -> {
                Boolean isSaved = exe.SaveFileContents(source.getText().toString(), configFilePath);
                if (isSaved) {
                    NhPaths.showMessage(context, "Source updated");
                } else {
                    NhPaths.showMessage(context, "Source not updated");
                }
            });
            return rootView;
        }
    }

    public static class DnsspoofFragment extends Fragment {
        private Context context;
        private String configFilePath;
        final ShellExecuter exe = new ShellExecuter();

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            configFilePath = NhPaths.CHROOT_PATH() + "/etc/mana-toolkit/dnsspoof.conf";
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.source_short, container, false);
            String description = getResources().getString(R.string.mana_dnsspoof);
            TextView desc = rootView.findViewById(R.id.description);
            desc.setText(description);

            EditText source = rootView.findViewById(R.id.source);
            exe.ReadFile_ASYNC(configFilePath, source);

            Button button = rootView.findViewById(R.id.update);
            button.setOnClickListener(v -> {
                if (getView() == null) {
                    return;
                }
                EditText source1 = getView().findViewById(R.id.source);
                String newSource = source1.getText().toString();
                exe.SaveFileContents(newSource, configFilePath);
                NhPaths.showMessage(context, "Source updated");
            });
            return rootView;
        }
    }

    public static class ManaNatFullFragment extends Fragment {
        private Context context;
        private String configFilePath;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                configFilePath = NhPaths.CHROOT_PATH() + "/usr/share/mana-toolkit/run-mana/start-nat-full-lollipop.sh";
            } else {
                configFilePath = NhPaths.CHROOT_PATH() + "/usr/share/mana-toolkit/run-mana/start-nat-full-kitkat.sh";
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.source_short, container, false);
            TextView desc = rootView.findViewById(R.id.description);

            desc.setText(getResources().getString(R.string.mana_nat_full));

            EditText source = rootView.findViewById(R.id.source);
            ShellExecuter exe = new ShellExecuter();
            exe.ReadFile_ASYNC(configFilePath, source);
            Button button = rootView.findViewById(R.id.update);
            button.setOnClickListener(v -> {
                if (getView() == null) {
                    return;
                }
                EditText source1 = getView().findViewById(R.id.source);
                String newSource = source1.getText().toString();
                ShellExecuter exe1 = new ShellExecuter();
                exe1.SaveFileContents(newSource, configFilePath);
                NhPaths.showMessage(context, "Source updated");
            });
            return rootView;
        }
    }

    public static class ManaNatSimpleFragment extends Fragment {
        private Context context;
        private String configFilePath;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                configFilePath = NhPaths.CHROOT_PATH() + "/usr/share/mana-toolkit/run-mana/start-nat-simple-lollipop.sh";
            } else {
                configFilePath = NhPaths.CHROOT_PATH() + "/usr/share/mana-toolkit/run-mana/start-nat-simple-kitkat.sh";
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.source_short, container, false);

            String description = getResources().getString(R.string.mana_nat_simple);
            TextView desc = rootView.findViewById(R.id.description);
            desc.setText(description);

            EditText source = rootView.findViewById(R.id.source);
            ShellExecuter exe = new ShellExecuter();
            exe.ReadFile_ASYNC(configFilePath, source);

            Button button = rootView.findViewById(R.id.update);
            button.setOnClickListener(v -> {
                if (getView() == null) {
                    return;
                }
                EditText source1 = getView().findViewById(R.id.source);
                String newSource = source1.getText().toString();
                ShellExecuter exe1 = new ShellExecuter();
                exe1.SaveFileContents(newSource, configFilePath);
                NhPaths.showMessage(context, "Source updated");
            });
            return rootView;
        }
    }

    public static class ManaNatBettercapFragment extends Fragment {
        private Context context;
        private String configFilePath;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            configFilePath = NhPaths.CHROOT_PATH() + "/usr/bin/start-nat-transproxy-lollipop.sh";
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.source_short, container, false);

            String description = getResources().getString(R.string.mana_bettercap_description);
            TextView desc = rootView.findViewById(R.id.description);
            desc.setText(description);

            EditText source = rootView.findViewById(R.id.source);
            ShellExecuter exe = new ShellExecuter();
            exe.ReadFile_ASYNC(configFilePath, source);

            Button button = rootView.findViewById(R.id.update);
            button.setOnClickListener(v -> {
                if (getView() == null) {
                    return;
                }
                EditText source1 = getView().findViewById(R.id.source);
                String newSource = source1.getText().toString();
                ShellExecuter exe1 = new ShellExecuter();
                exe1.SaveFileContents(newSource, configFilePath);
                NhPaths.showMessage(context, "Source updated");
            });
            return rootView;
        }
    }

    public static class BdfProxyConfigFragment extends Fragment {
        private Context context;
        private String configFilePath;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            configFilePath = NhPaths.APP_SD_FILES_PATH + "/configs/bdfproxy.cfg";
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.source_short, container, false);

            String description = getResources().getString(R.string.bdfproxy_cfg);
            TextView desc = rootView.findViewById(R.id.description);
            desc.setText(description);
            // use the good one?
            Log.d("BDFPATH", configFilePath);
            EditText source = rootView.findViewById(R.id.source);
            ShellExecuter exe = new ShellExecuter();
            exe.ReadFile_ASYNC(configFilePath, source);

            Button button = rootView.findViewById(R.id.update);
            button.setOnClickListener(v -> {
                if (getView() == null) {
                    return;
                }
                EditText source1 = getView().findViewById(R.id.source);
                String newSource = source1.getText().toString();
                ShellExecuter exe1 = new ShellExecuter();
                exe1.SaveFileContents(newSource, configFilePath);
                NhPaths.showMessage(context, "Source updated");
            });
            return rootView;
        }
    }

    public static class ManaStartNatSimpleBdfFragment extends Fragment {
        private Context context;
        private String configFilePath;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            context = getContext();
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                configFilePath = NhPaths.CHROOT_PATH() + "/usr/share/mana-toolkit/run-mana/start-nat-simple-bdf-lollipop.sh";
            } else {
                configFilePath = NhPaths.CHROOT_PATH() + "/usr/share/mana-toolkit/run-mana/start-nat-simple-bdf-kitkat.sh";
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.source_short, container, false);

            String description = getResources().getString(R.string.mana_nat_simple_bdf);
            TextView desc = rootView.findViewById(R.id.description);
            desc.setText(description);
            EditText source = rootView.findViewById(R.id.source);
            ShellExecuter exe = new ShellExecuter();
            exe.ReadFile_ASYNC(configFilePath, source);
            Button button = rootView.findViewById(R.id.update);
            button.setOnClickListener(v -> {
                if (getView() == null) {
                    return;
                }
                EditText source1 = getView().findViewById(R.id.source);
                String newSource = source1.getText().toString();
                ShellExecuter exe1 = new ShellExecuter();
                exe1.SaveFileContents(newSource, configFilePath);
                NhPaths.showMessage(context, "Source updated");
            });
            return rootView;
        }
    }

    ////
    // Bridge side functions
    ////

    public void run_cmd(String cmd) {
        Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/kali", cmd);
        activity.startActivity(intent);
    }
}
