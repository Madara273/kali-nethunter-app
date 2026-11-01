package com.offsec.nethunter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import android.widget.ImageButton;
import com.offsec.nethunter.widget.ExpandableChoiceView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.offsec.nethunter.utils.ShellExecuter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MacchangerFragment extends Fragment {
    private static final String TAG = "MacchangerFragment";
    private static final String ARG_SECTION_NUMBER = "section_number";
    private ExpandableChoiceView interfaceSpinner;
    private ExpandableChoiceView macModeSpinner;
    private Button changeMacButton;
    private Button setHostNameButton;
    private Button resetMacButton;
    private Button regenerateMacButton;
    private Button clearMacButton;
    private EditText netHostNameEditText;
    private final EditText[] macFields = new EditText[6];
    private TextView currentMacTextView;
    private ImageButton reloadImageButton;
    private final Map<String, String> interfaceAddresses = new HashMap<>();
    private final Map<String, String> originalAddresses = new HashMap<>();
    private String selectedInterface;
    private boolean loadingInterfaces;
    private boolean changingAddress;
    private ExecutorService executorService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static MacchangerFragment newInstance(int sectionNumber) {
        MacchangerFragment fragment = new MacchangerFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.macchanger, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        loadingInterfaces = false;
        changingAddress = false;
        interfaceSpinner = view.findViewById(R.id.f_macchanger_interface_opts_spr);
        macModeSpinner = view.findViewById(R.id.f_macchanger_mode_opts_spr);
        changeMacButton = view.findViewById(R.id.f_macchanger_set_mac_btn);
        setHostNameButton = view.findViewById(R.id.f_macchanger_setHostname_btn);
        resetMacButton = view.findViewById(R.id.f_macchanger_reset_mac_btn);
        netHostNameEditText = view.findViewById(R.id.f_macchanger_phone_name_et);
        currentMacTextView = view.findViewById(R.id.f_macchanger_currMac_tv);
        reloadImageButton = view.findViewById(R.id.f_macchanger_reloadMAC_imgbtn);
        regenerateMacButton = view.findViewById(R.id.f_macchanger_regenerate_mac_btn);
        clearMacButton = view.findViewById(R.id.f_macchanger_clear_mac_btn);
        int[] fieldIds = {R.id.f_macchanger_mac1_et, R.id.f_macchanger_mac2_et,
                R.id.f_macchanger_mac3_et, R.id.f_macchanger_mac4_et,
                R.id.f_macchanger_mac5_et, R.id.f_macchanger_mac6_et};
        for (int i = 0; i < fieldIds.length; i++) macFields[i] = view.findViewById(fieldIds[i]);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.macchanger, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() != R.id.macchanger_action_more) return false;
                showMoreSheet();
                return true;
            }
        }, getViewLifecycleOwner());

        reloadImageButton.setOnClickListener(v -> refreshInterfaces());
        regenerateMacButton.setOnClickListener(v -> generateAddress());
        clearMacButton.setOnClickListener(v -> {
            for (EditText field : macFields) field.setText("");
        });
        changeMacButton.setOnClickListener(v -> changeAddress(false));
        resetMacButton.setOnClickListener(v -> changeAddress(true));
        setHostNameButton.setOnClickListener(v -> setHostname());
        ArrayAdapter<CharSequence> modes = ArrayAdapter.createFromResource(requireContext(),
                R.array.macchanger_opts, R.layout.macchanger_ifaces_item);
        modes.setDropDownViewResource(R.layout.macchanger_ifaces_item);
        macModeSpinner.setAdapter(modes);
        macModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                regenerateMacButton.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                clearMacButton.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                if (position == 0) generateAddress();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        refreshInterfaces();
        loadHostname();
    }

    @Override
    public void onDestroyView() {
        executorService.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        interfaceSpinner = null;
        macModeSpinner = null;
        changeMacButton = null;
        setHostNameButton = null;
        resetMacButton = null;
        regenerateMacButton = null;
        clearMacButton = null;
        netHostNameEditText = null;
        for (int i = 0; i < macFields.length; i++) macFields[i] = null;
        currentMacTextView = null;
        reloadImageButton = null;
    }

    private void generateAddress() {
        byte[] address = new byte[6];
        new SecureRandom().nextBytes(address);
        address[0] = (byte) ((address[0] & 0xfe) | 0x02);
        for (int i = 0; i < address.length; i++) {
            macFields[i].setText(String.format(Locale.ROOT, "%02x", address[i] & 0xff));
        }
    }

    private void refreshInterfaces() {
        if (loadingInterfaces || changingAddress) return;
        loadingInterfaces = true;
        updateActions();
        currentMacTextView.setText("Loading…");
        runTask(this::readInterfaceAddresses, addresses -> {
            loadingInterfaces = false;
            interfaceAddresses.clear();
            interfaceAddresses.putAll(addresses);
            for (Map.Entry<String, String> entry : addresses.entrySet()) {
                if (isValidAddress(entry.getValue())) originalAddresses.putIfAbsent(entry.getKey(), entry.getValue());
            }
            List<String> names = new ArrayList<>(addresses.keySet());
            Collections.sort(names, Collections.reverseOrder());
            interfaceSpinner.setOnItemSelectedListener(null);
            List<String> options = names.isEmpty() ? Collections.singletonList("No interfaces") : names;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.macchanger_ifaces_item, options);
            adapter.setDropDownViewResource(R.layout.macchanger_ifaces_item);
            interfaceSpinner.setAdapter(adapter);
            int position = Math.max(0, names.indexOf(selectedInterface));
            interfaceSpinner.setSelection(position);
            selectedInterface = names.isEmpty() ? null : names.get(position);
            showCurrentAddress();
            interfaceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedInterface = names.isEmpty() ? null : names.get(position);
                    showCurrentAddress();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            updateActions();
        });
    }

    private Map<String, String> readInterfaceAddresses() {
        Map<String, String> addresses = new HashMap<>();
        ShellExecuter shell = new ShellExecuter();
        String output = shell.RunAsRootOutput("for iface in /sys/class/net/*; do "
                + "[ -d \"$iface\" ] || continue; [ \"${iface##*/}\" = lo ] && continue; "
                + "printf '%s ' \"${iface##*/}\"; cat \"$iface/address\"; done");
        if (output != null) {
            for (String line : output.split("\n")) {
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length == 2) addresses.put(parts[0], parts[1].trim());
            }
        }
        if (addresses.isEmpty()) {
            try {
                java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces != null) {
                    for (NetworkInterface iface : Collections.list(interfaces)) {
                        String name = iface.getName();
                        if (name == null || name.equals("lo")) continue;
                        String mac = shell.RunAsRootOutput("cat " + ShellExecuter.shellEscape("/sys/class/net/" + name + "/address"));
                        addresses.put(name, mac == null ? "" : mac.trim());
                    }
                }
            } catch (Exception error) {
                Log.e(TAG, "Unable to read interfaces", error);
            }
        }
        return addresses;
    }

    private void showCurrentAddress() {
        String address = interfaceAddresses.get(selectedInterface);
        currentMacTextView.setText(address == null || address.isEmpty() ? "—" : address);
    }

    private void updateActions() {
        boolean available = !loadingInterfaces && !changingAddress && selectedInterface != null;
        changeMacButton.setEnabled(available);
        resetMacButton.setEnabled(available);
        regenerateMacButton.setEnabled(!changingAddress);
        clearMacButton.setEnabled(!changingAddress);
        interfaceSpinner.setEnabled(!loadingInterfaces && !changingAddress && !interfaceAddresses.isEmpty());
        reloadImageButton.setEnabled(!loadingInterfaces && !changingAddress);
    }

    private void changeAddress(boolean reset) {
        if (selectedInterface == null || loadingInterfaces || changingAddress) return;
        String iface = selectedInterface;
        String address;
        if (reset) {
            address = originalAddresses.get(iface);
            if (!isValidAddress(address)) {
                showToast("No saved MAC address for " + iface);
                return;
            }
        } else {
            StringBuilder value = new StringBuilder();
            for (EditText field : macFields) {
                if (value.length() > 0) value.append(':');
                value.append(field.getText().toString().trim());
            }
            address = value.toString().toLowerCase(Locale.ROOT);
            if (!isValidAddress(address)) {
                showToast("Enter all six pairs of hexadecimal digits.");
                return;
            }
        }
        changingAddress = true;
        updateActions();
        String quotedIface = ShellExecuter.shellEscape(iface);
        String quotedAddress = ShellExecuter.shellEscape(address);
        runTask(() -> new ShellExecuter().RunAsRootReturnValue(
                "ip link set " + quotedIface + " down && { ip link set " + quotedIface
                        + " address " + quotedAddress + "; mac_result=$?; ip link set " + quotedIface
                        + " up; link_result=$?; [ \"$mac_result\" -eq 0 ] && [ \"$link_result\" -eq 0 ]; }"), result -> {
            changingAddress = false;
            showToast(result == 0 ? "MAC address of " + iface + " set to " + address
                    : "Failed to change MAC address on " + iface);
            refreshInterfaces();
        });
    }

    private static boolean isValidAddress(String address) {
        return address != null && address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
    }

    private void loadHostname() {
        runTask(() -> {
            String hostname = new ShellExecuter().RunAsRootOutput("getprop net.hostname");
            if (hostname != null && !hostname.trim().isEmpty()) return hostname.trim();
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/sys/kernel/hostname"))) {
                String value = reader.readLine();
                return value == null ? "" : value;
            }
        }, hostname -> netHostNameEditText.setText(hostname));
    }

    private void setHostname() {
        String hostname = netHostNameEditText.getText().toString().trim();
        if (!ShellExecuter.isValidHostname(hostname)) {
            netHostNameEditText.setError("Use letters, digits, hyphens and dots.");
            return;
        }
        setHostNameButton.setEnabled(false);
        runTask(() -> new ShellExecuter().RunAsRootReturnValue("setprop net.hostname "
                + ShellExecuter.shellEscape(hostname)), result -> {
            setHostNameButton.setEnabled(true);
            showToast(result == 0 ? "Hostname set to " + hostname : "Failed to set hostname");
            if (result == 0) loadHostname();
        });
    }

    private void showMoreSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.macchanger_more_bottomsheet, null, false);
        sheet.findViewById(R.id.macchanger_more_refresh).setOnClickListener(v -> {
            dialog.dismiss();
            refreshInterfaces();
        });
        sheet.findViewById(R.id.macchanger_more_info).setOnClickListener(v -> {
            dialog.dismiss();
            showUsageNotes();
        });
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showUsageNotes() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.macchanger_info_bottomsheet, null, false);
        ((TextView) sheet.findViewById(R.id.macchanger_info_title)).setText("MAC Changer usage");
        ((TextView) sheet.findViewById(R.id.macchanger_info_message)).setText(
                getString(R.string.macchanger_description) + "\n\nReset restores the address captured when this screen first detected the interface. It does not retrieve the factory address.");
        sheet.findViewById(R.id.macchanger_info_close).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private <T> void runTask(Callable<T> task, Consumer<T> onResult) {
        View owner = getView();
        if (owner == null || executorService == null || executorService.isShutdown()) return;
        executorService.execute(() -> {
            try {
                T result = task.call();
                mainHandler.post(() -> {
                    if (getView() == owner) onResult.accept(result);
                });
            } catch (Exception error) {
                Log.e(TAG, "MAC Changer task failed", error);
                mainHandler.post(() -> {
                    if (getView() != owner) return;
                    loadingInterfaces = false;
                    changingAddress = false;
                    setHostNameButton.setEnabled(true);
                    updateActions();
                    showToast("Unable to complete the operation. Please refresh and try again.");
                });
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
