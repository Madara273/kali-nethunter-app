package com.offsec.nethunter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import com.offsec.nethunter.widget.ExpandableChoiceView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.offsec.nethunter.HandlerThread.USBArsenalHandlerThread;
import com.offsec.nethunter.SQL.USBArsenalSQL;
import com.offsec.nethunter.bridge.Bridge;
import com.offsec.nethunter.models.USBArsenalUSBNetworkModel;
import com.offsec.nethunter.models.USBArsenalUSBSwitchModel;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.USBFunctionProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class USBArsenalFragment extends Fragment {
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String TAG = "USBArsenalFragment";
    private Context context;
    private Activity activity;
    private final USBArsenalHandlerThread usbArsenalHandlerThread = new USBArsenalHandlerThread();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static boolean is_init_exists = true;
    //private Message msg = new Message();
    private TextView usbStatusTextView;
    private TextView mountedImageTextView;
    private TextView mountedImageHintTextView;
    private TextView currentInquiryHintTextView;
    private TextView usbNetworkTetheringHintTextView;
    private ImageButton reloadUSBStateImageButton;
    private ImageButton reloadMountStateButton;
    private LinearLayout imageMounterLL;
    private LinearLayout usbNetworkTetheringLL;
    private Button setUSBIfaceButton;
    private Button mountImgButton;
    private Button unmountImgButton;
    private Button setUSBNetworkTetheringButton;
    private Button saveUSBFunctionConfigButton;
    private Button saveUSBNetworkTetheringConfigButton;
    private ExpandableChoiceView targetOSSpinner;
    private ExpandableChoiceView imgFileSpinner;
    private ExpandableChoiceView usbFuncSpinner;
    private ExpandableChoiceView usbNetworkAttackModeSpinner;
    private ExpandableChoiceView adbSpinner;
    private EditText inquiryStringEditText;
    private EditText[] usbSwitchInfoEditTextGroup = new EditText[5];
    private EditText[] usbNetworkInfoEditTextGroup = new EditText[5];
    private String usbStorageFunctionName;

    public static USBArsenalFragment newInstance(int sectionNumber) {
        USBArsenalFragment fragment = new USBArsenalFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context     = getContext();
        activity    = getActivity();
        usbArsenalHandlerThread.start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.usbarsenal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MenuHost menuHost = requireActivity();
        File init = new File("/init.nethunter.rc");

        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.usbarsenal, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() != R.id.usbarsenal_action_more) return false;
                showMoreSheet();
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        targetOSSpinner                     = view.findViewById(R.id.f_usbarsenal_spr_targetplatform);
        usbFuncSpinner                      = view.findViewById(R.id.f_usbarsenal_spr_usbfunctions);
        adbSpinner                          = view.findViewById(R.id.f_usbarsenal_spr_adb);
        imgFileSpinner                      = view.findViewById(R.id.f_usbarsenal_spr_img_files);
        usbNetworkAttackModeSpinner         = view.findViewById(R.id.f_usbarsenal_spr_networkattackmode);
        setUSBIfaceButton                   = view.findViewById(R.id.f_usbarsenal_btn_setusbinterface);
        setUSBNetworkTetheringButton        = view.findViewById(R.id.f_usbarsenal_btn_runusbnetworktethering);
        mountImgButton                      = view.findViewById(R.id.f_usbarsenal_btn_mountImage);
        unmountImgButton                    = view.findViewById(R.id.f_usbarsenal_btn_unmountImage);
        Button      changeInquiryButton     = view.findViewById(R.id.f_usbarsenal_btn_changeInquiryString);
        reloadUSBStateImageButton           = view.findViewById(R.id.f_usbarsenal_imgbtn_reloadUSBStatus);
        reloadMountStateButton              = view.findViewById(R.id.f_usbarsenal_imgbtn_reloadMountStatus);
        saveUSBFunctionConfigButton         = view.findViewById(R.id.f_usbarsenal_btn_saveusbfuncswitch);
        saveUSBNetworkTetheringConfigButton = view.findViewById(R.id.f_usbarsenal_btn_saveusbnetworktethering);
        Button openUsbExfiltrationButton    = view.findViewById(R.id.f_usbarsenal_btn_openusbexfiltration);
        CheckBox    readOnlyCheckBox        = view.findViewById(R.id.f_usbarsenal_chkbox_ReadOrWrite);
        usbStatusTextView                   = view.findViewById(R.id.f_usbarsenal_tv_current_usb_state);
        mountedImageTextView                = view.findViewById(R.id.f_usbarsenal_tv_mount_state);
        currentInquiryHintTextView          = view.findViewById(R.id.f_usbarsenal_tv_current_inquiry_string);
        mountedImageHintTextView            = view.findViewById(R.id.f_usbarsenal_ll_tv_imagemounter_hint);
        usbNetworkTetheringHintTextView     = view.findViewById(R.id.f_usbarsenal_ll_tv_usbnetworktethering_hint);
        imageMounterLL                      = view.findViewById(R.id.f_usbarsenal_ll_imageMounter_sub2);
        usbNetworkTetheringLL               = view.findViewById(R.id.f_usbarsenal_ll_usbnetworktethering_sub2);
        inquiryStringEditText               = view.findViewById(R.id.f_usbarsenal_et_inquiryString);

        usbSwitchInfoEditTextGroup[0] = view.findViewById(R.id.f_usbarsenal_et_idvendor);
        usbSwitchInfoEditTextGroup[1] = view.findViewById(R.id.f_usbarsenal_et_idproduct);
        usbSwitchInfoEditTextGroup[2] = view.findViewById(R.id.f_usbarsenal_et_manufacturer);
        usbSwitchInfoEditTextGroup[3] = view.findViewById(R.id.f_usbarsenal_et_product);
        usbSwitchInfoEditTextGroup[4] = view.findViewById(R.id.f_usbarsenal_et_serialnumber);
        usbNetworkInfoEditTextGroup[0] = view.findViewById(R.id.f_usbarsenal_et_usbnetwork_upstreamiface);
        usbNetworkInfoEditTextGroup[1] = view.findViewById(R.id.f_usbarsenal_et_usbnetwork_usbfunc);
        usbNetworkInfoEditTextGroup[2] = view.findViewById(R.id.f_usbarsenal_et_usbnetwork_targetip);
        usbNetworkInfoEditTextGroup[3] = view.findViewById(R.id.f_usbarsenal_et_usbnetwork_gatewayip);
        usbNetworkInfoEditTextGroup[4] = view.findViewById(R.id.f_usbarsenal_et_usbnetwork_ipsubnetmask);

        // Watch
        SharedPreferences sharedpreferences = context.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        Boolean iswatch = sharedpreferences.getBoolean("running_on_wearos", false);

        ArrayAdapter<String> usbFuncWinArrayAdapter = new ArrayAdapter<>(activity, R.layout.usbarsenal_spinner_item, new ArrayList<>());
        ArrayAdapter<String> usbFuncMACArrayAdapter = new ArrayAdapter<>(activity, R.layout.usbarsenal_spinner_item, new ArrayList<>());
        ArrayAdapter<String> usbNetworkAttackModeArrayAdapter = new ArrayAdapter<>(activity, R.layout.usbarsenal_spinner_item, getResources().getStringArray(R.array.usbarsenal_usb_network_attack_mode));
        usbFuncWinArrayAdapter.setDropDownViewResource(R.layout.usbarsenal_spinner_item);
        usbFuncMACArrayAdapter.setDropDownViewResource(R.layout.usbarsenal_spinner_item);
        usbNetworkAttackModeArrayAdapter.setDropDownViewResource(R.layout.usbarsenal_spinner_item);
        usbNetworkAttackModeSpinner.setAdapter(usbNetworkAttackModeArrayAdapter);
        ArrayAdapter<CharSequence> targets = ArrayAdapter.createFromResource(requireContext(),
                R.array.usbarsenal_target_platform, R.layout.usbarsenal_spinner_item);
        targets.setDropDownViewResource(R.layout.usbarsenal_spinner_item);
        targetOSSpinner.setAdapter(targets);
        ArrayAdapter<CharSequence> adbOptions = ArrayAdapter.createFromResource(requireContext(),
                R.array.usbarsenal_usb_adb, R.layout.usbarsenal_spinner_item);
        adbOptions.setDropDownViewResource(R.layout.usbarsenal_spinner_item);
        adbSpinner.setAdapter(adbOptions);

        targetOSSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 2){
                    usbFuncSpinner.setAdapter(usbFuncMACArrayAdapter);
                } else {
                    usbFuncSpinner.setAdapter(usbFuncWinArrayAdapter);
                }
                refreshUSBSwitchInfos(gettargetOSSpinnerString(), getusbFuncSpinnerString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        usbFuncSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateAdbSelection();
                refreshUSBSwitchInfos(gettargetOSSpinnerString(), getusbFuncSpinnerString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        adbSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (is_init_exists && usbFuncSpinner.getSelectedItem() != null) {
                    boolean enabled = position == 0;
                    String selected = usbFuncSpinner.getSelectedItem().toString();
                    if (USBFunctionProfile.hasAdb(selected) != enabled) {
                        int option = findLegacyAdbOption(enabled);
                        if (option >= 0) usbFuncSpinner.setSelection(option);
                        else updateAdbSelection(false);
                        return;
                    }
                }
                updateAdbSelection(false);
                refreshUSBSwitchInfos(gettargetOSSpinnerString(), getusbFuncSpinnerString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        usbNetworkAttackModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshUSBNetworkInfos(getusbNetWorkModeSpinnerPosition());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        setUSBIfaceButton.setOnClickListener(v -> {
            if (isAllUSBInfosValid()) {
                setUSBIfaceButton.setEnabled(false);
                String target = (targetOSSpinner.getSelectedItem() != null && "Windows".equals(targetOSSpinner.getSelectedItem().toString())) ? "win" :
                        (targetOSSpinner.getSelectedItem() != null && "Linux".equals(targetOSSpinner.getSelectedItem().toString())) ? "lnx" :
                                (targetOSSpinner.getSelectedItem() != null && "Mac OS".equals(targetOSSpinner.getSelectedItem().toString())) ? "mac" : "";
                String functions = getusbFuncSpinnerString();
                String idVendor = " -v '" + usbSwitchInfoEditTextGroup[0].getText().toString() + "'";
                String idProduct = " -p '" + usbSwitchInfoEditTextGroup[1].getText().toString() + "'";
                String manufacturer = usbSwitchInfoEditTextGroup[2].getText().toString().isEmpty() ? "" : " -m '" + usbSwitchInfoEditTextGroup[2].getText().toString() + "'";
                String product = usbSwitchInfoEditTextGroup[3].getText().toString().isEmpty() ? "" : " -P '" + usbSwitchInfoEditTextGroup[3].getText().toString() + "'";
                String serialnumber = usbSwitchInfoEditTextGroup[4].getText().toString().isEmpty() ? "" : " -s '" + usbSwitchInfoEditTextGroup[4].getText().toString() + "'";

                String commandBuilder = "[ -f /init.nethunter.rc ] && setprop sys.usb.config " +
                        functions +
                        " || " +
                        NhPaths.APP_SCRIPTS_PATH +
                        "/usbarsenal -t '" +
                        target +
                        "' -f '" +
                        functions +
                        "'" +
                        idVendor +
                        idProduct +
                        manufacturer +
                        product +
                        serialnumber;
                Message msg = new Message();
                msg.what = USBArsenalHandlerThread.SETUSBIFACE;
                msg.obj = commandBuilder;
                usbArsenalHandlerThread.getHandler().sendMessage(msg);
            }
        });

        setUSBNetworkTetheringButton.setOnClickListener(v -> run_cmd_android("usbtethering -o " + usbNetworkInfoEditTextGroup[0].getText().toString() +
                " -i " + usbNetworkInfoEditTextGroup[1].getText().toString() +
                " -A " + usbNetworkInfoEditTextGroup[2].getText().toString() +
                " -B " + usbNetworkInfoEditTextGroup[2].getText().toString() +
                " -C " + usbNetworkInfoEditTextGroup[3].getText().toString() +
                " -D " + usbNetworkInfoEditTextGroup[4].getText().toString()));

        reloadUSBStateImageButton.setOnClickListener(v -> {
            Message msg = new Message();
            msg.what = USBArsenalHandlerThread.RELOAD_USBIFACE;
            msg.obj = "find /config/usb_gadget/g1/configs/b.1 -type l -exec readlink -e {} \\; | xargs echo";
            usbArsenalHandlerThread.getHandler().sendMessage(msg);
        });

        reloadMountStateButton.setOnClickListener(v -> {
            Message msg = new Message();
            msg.what = USBArsenalHandlerThread.RELOAD_MOUNTSTATUS;
            msg.obj = "cat /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/file";
            usbArsenalHandlerThread.getHandler().sendMessage(msg);
            getImageFiles();
        });

        mountImgButton.setOnClickListener(v -> {
            if (imgFileSpinner.getSelectedItem() == null) {
                NhPaths.showMessage(context, "No image file is selected.");
            } else {
                mountImgButton.setEnabled(false);
                unmountImgButton.setEnabled(false);
                Message msg = new Message();
                msg.what = USBArsenalHandlerThread.MOUNT_IMAGE;
                if (readOnlyCheckBox.isChecked())
                    msg.obj = String.format("%s%s && echo '%s/%s' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/file",
                            "echo '1' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/ro",
                            imgFileSpinner.getSelectedItem().toString().contains(".iso") ? " && echo '1' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/cdrom" : " && echo '0' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/cdrom",
                            NhPaths.APP_SD_FILES_IMG_PATH,
                            imgFileSpinner.getSelectedItem().toString());
                else
                    msg.obj = String.format("%s%s && echo '%s/%s' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/file",
                            "echo '0' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/ro",
                            imgFileSpinner.getSelectedItem().toString().contains(".iso") ? " && echo '1' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/cdrom" : " && echo '0' > /config/usb_gadget/g1/functions/" + usbStorageFunctionName + "/lun.0/cdrom",
                            NhPaths.APP_SD_FILES_IMG_PATH,
                            imgFileSpinner.getSelectedItem().toString());
                usbArsenalHandlerThread.getHandler().sendMessage(msg);
            }
        });

        unmountImgButton.setOnClickListener(v -> {
            mountImgButton.setEnabled(false);
            unmountImgButton.setEnabled(false);
            Message msg = new Message();
            msg.what = USBArsenalHandlerThread.UNMOUNT_IMAGE;
            msg.obj = String.format("echo '' > /config/usb_gadget/g1/functions/%s/lun.0/file" +
                                    " && echo '0' > /config/usb_gadget/g1/functions/%s/lun.0/ro" +
                                    " && echo '0' > /config/usb_gadget/g1/functions/%s/lun.0/cdrom",
                      usbStorageFunctionName, usbStorageFunctionName, usbStorageFunctionName);
            usbArsenalHandlerThread.getHandler().sendMessage(msg);
        });

        changeInquiryButton.setOnClickListener(v -> {
            String inquiryString = inquiryStringEditText.getText().toString();
            // Inquiry strings are max 28 ASCII printable chars; reject single quotes to avoid shell injection
            if (inquiryString.contains("'") || inquiryString.length() > 28) {
                android.widget.Toast.makeText(context, "Invalid inquiry string (max 28 chars, no single quotes).", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Message msg = new Message();
            msg.what = USBArsenalHandlerThread.CHANGE_INQUIRY_STRING;
            msg.obj = String.format("echo '%s' > /config/usb_gadget/g1/functions/%s/lun.0/inquiry_string", inquiryString, usbStorageFunctionName);
            usbArsenalHandlerThread.getHandler().sendMessage(msg);
        });

        saveUSBFunctionConfigButton.setOnClickListener(v -> {
            if (!usbSwitchInfoEditTextGroup[0].getText().toString().matches("0x[0-9a-fA-F]{4}") ||
                    !usbSwitchInfoEditTextGroup[1].getText().toString().matches("0x[0-9a-fA-F]{4}")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Enter four hexadecimal digits prefixed with 0x, for example 0x18d1.").create().show();
            } else if (!usbSwitchInfoEditTextGroup[2].getText().toString().matches("\\w+|^$") ||
                    !usbSwitchInfoEditTextGroup[3].getText().toString().matches("\\w+|^$")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Use letters, digits or underscores, or leave the value empty.").create().show();
            } else if (!usbSwitchInfoEditTextGroup[4].getText().toString().matches("[0-9A-Z]{10}|^$")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Enter ten uppercase letters or digits, or leave the value empty.").create().show();
            } else {
                for (int i = 0; i < usbSwitchInfoEditTextGroup.length; i++) {
                    if (!USBArsenalSQL.getInstance(context).setUSBSwitchColumnData(
                            getusbFuncSpinnerString(),
                            i + 2,
                            targetOSSpinner.getSelectedItem().toString(),
                            usbSwitchInfoEditTextGroup[i].getText().toString().toLowerCase())){
                        NhPaths.showMessage(context, "Something's wrong when processing " + usbSwitchInfoEditTextGroup[i].getText().toString().toLowerCase());
                    } else {
                        NhPaths.showMessage(context, "Saved.");
                    }
                }
            }
        });

        saveUSBNetworkTetheringConfigButton.setOnClickListener(v -> {
            USBArsenalUSBNetworkModel usbArsenalUSBNetworkModel = new USBArsenalUSBNetworkModel(
                    usbNetworkInfoEditTextGroup[0].getText().toString(),
                    usbNetworkInfoEditTextGroup[1].getText().toString(),
                    usbNetworkInfoEditTextGroup[2].getText().toString(),
                    usbNetworkInfoEditTextGroup[3].getText().toString(),
                    usbNetworkInfoEditTextGroup[4].getText().toString()
            );
            if (!USBArsenalSQL.getInstance(context).setUSBNetworkColumnData(getusbNetWorkModeSpinnerPosition(), usbArsenalUSBNetworkModel)) {
                NhPaths.showMessage(context, "Failed saving configs to DB, please check if your input is valid.");
            } else {
                NhPaths.showMessage(context, "Saved.");
            }
        });

        openUsbExfiltrationButton.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.container, UsbExfiltrationFragment.newInstance())
                        .addToBackStack(null)
                        .commit());

        usbArsenalHandlerThread.setOnShellExecuterFinishedListener((resultObject, actionCode) -> postToView(view, () -> {
                switch (actionCode){
                    case USBArsenalHandlerThread.IS_INIT_EXIST:
                        if ((int)resultObject == 0) {
                            is_init_exists = true;
                            { Message msg = new Message();
                                msg.what = USBArsenalHandlerThread.RETRIEVE_USB_FUNCS;
                                msg.obj = "cat /init.nethunter.rc | grep -E -o 'sys.usb.config=([a-zA-Z,_]+)' | sed 's/sys.usb.config=//' | sort | uniq";
                                usbArsenalHandlerThread.getHandler().sendMessage(msg); }
                        } else {
                            is_init_exists = false;
                            {
                                usbFuncWinArrayAdapter.clear();
                                usbFuncWinArrayAdapter.addAll(getResources().getStringArray(R.array.usbarsenal_usb_states_win_lin));
                                usbFuncMACArrayAdapter.clear();
                                usbFuncMACArrayAdapter.addAll(getResources().getStringArray(R.array.usbarsenal_usb_states_mac));
                                for (EditText field : usbSwitchInfoEditTextGroup) field.setEnabled(true);
                                saveUSBFunctionConfigButton.setEnabled(true);
                                updateAdbSelection();
                            }
                        }
                        break;
                    case USBArsenalHandlerThread.RETRIEVE_USB_FUNCS:
                        {
                            ArrayList<String> usbFuncArray = new ArrayList<>(Arrays.asList(resultObject.toString().split("\\n")));
                            List<String> usbFuncWinArray = Lists.newArrayList(Collections2.filter(usbFuncArray,
                                    Predicates.containsPattern("win")));
                            List<String> usbFuncMacArray = Lists.newArrayList(Collections2.filter(usbFuncArray,
                                    Predicates.containsPattern("mac")));
                            usbFuncWinArrayAdapter.clear();
                            usbFuncWinArrayAdapter.addAll(usbFuncWinArray);
                            usbFuncMACArrayAdapter.clear();
                            usbFuncMACArrayAdapter.addAll(usbFuncMacArray);
                            for (EditText infoEditText: usbSwitchInfoEditTextGroup) {
                                infoEditText.setEnabled(false);
                            }
                            saveUSBFunctionConfigButton.setEnabled(false);
                            updateAdbSelection();
                        }
                        break;
                    case USBArsenalHandlerThread.SETUSBIFACE:
                        {
                            if ((int)resultObject != 0){
                                NhPaths.showMessage(context, "Failed to set USB function.");
                            } else {
                                NhPaths.showMessage(context, "USB function set successfully.");
                                reloadUSBStateImageButton.performClick();
                            }
                            setUSBIfaceButton.setEnabled(true);
                        }
                        break;
                    case USBArsenalHandlerThread.RELOAD_USBIFACE:
                        {
                            if (resultObject.toString().isEmpty()) {
                                usbStatusTextView.setText(R.string.usb_no_usb_function_enabled);
                                imageMounterLL.setVisibility(View.GONE);
                                mountedImageHintTextView.setVisibility(View.VISIBLE);
                                usbNetworkTetheringLL.setVisibility(View.GONE);
                                usbNetworkTetheringHintTextView.setVisibility(View.VISIBLE);
                            } else {
                                usbStatusTextView.setText(resultObject.toString()
                                        .replaceAll("/config/usb_gadget/g1/functions/", "")
                                        .replaceAll("/config/usb_gadget/g1/functions","gsi.rndis")
                                        .replaceAll(" ", "\n"));
                                if (usbStatusTextView.getText().toString().contains("mass_storage")){
                                    imageMounterLL.setVisibility(View.VISIBLE);
                                    mountedImageHintTextView.setVisibility(View.GONE);
                                    if (usbStorageFunctionName != null) reloadMountStateButton.performClick();
                                    else getImageFiles();
                                } else {
                                    imageMounterLL.setVisibility(View.GONE);
                                    mountedImageHintTextView.setVisibility(View.VISIBLE);
                                }
                                if (usbStatusTextView.getText().toString().contains("rndis") ||
                                        usbStatusTextView.getText().toString().contains("acm")){
                                    usbNetworkTetheringLL.setVisibility(View.VISIBLE);
                                    usbNetworkTetheringHintTextView.setVisibility(View.GONE);
                                    refreshUSBNetworkInfos(getusbNetWorkModeSpinnerPosition());
                                } else {
                                    usbNetworkTetheringLL.setVisibility(View.GONE);
                                    usbNetworkTetheringHintTextView.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                        break;
                    case USBArsenalHandlerThread.GET_STORAGE_FUNC_FOLDER_NAME:
                        String folder_name = resultObject.toString().split("\\n")[0];
                        // fallback to mass_storage.0 if other not found
                        if (folder_name.isEmpty()) folder_name = "mass_storage.0";
                        usbStorageFunctionName = folder_name;
                        if (imageMounterLL.getVisibility() == View.VISIBLE) reloadMountStateButton.performClick();
                        break;
                    case USBArsenalHandlerThread.RELOAD_MOUNTSTATUS:
                        {
                            if (resultObject.toString().isEmpty()){ mountedImageTextView.setText(R.string.usb_no_image_mounted); }
                            else {mountedImageTextView.setText(resultObject.toString());}
                        }
                        break;
                    case USBArsenalHandlerThread.MOUNT_IMAGE:
                        {
                            if ((int)resultObject == 0){
                                NhPaths.showMessage(context, imgFileSpinner.getSelectedItem().toString() + " has been mounted.");
                            } else {
                                NhPaths.showMessage(context, "Failed to mount image " + imgFileSpinner.getSelectedItem().toString());
                            }
                            reloadMountStateButton.performClick();
                            mountImgButton.setEnabled(imgFileSpinner.getCount() > 0);
                            unmountImgButton.setEnabled(true);
                        }
                        break;
                    case USBArsenalHandlerThread.UNMOUNT_IMAGE:
                        {
                            if ((int)resultObject == 0){
                                NhPaths.showMessage(context, "Image has been unmounted.");
                                reloadMountStateButton.performClick();
                            } else {
                                NhPaths.showMessage_long(context, "Failed to unmount image. Your drive may still be in use by the host, please eject your drive on the host first," +
                                        "and then try to umount the image again.");
                            }
                            reloadMountStateButton.performClick();
                            mountImgButton.setEnabled(imgFileSpinner.getCount() > 0);
                            unmountImgButton.setEnabled(true);
                        }
                        break;
                    case USBArsenalHandlerThread.CHANGE_INQUIRY_STRING:
                        String newInquiry = inquiryStringEditText.getText().toString();
                        if ((int)resultObject == 0){
                            if (newInquiry.isEmpty()) {
                                NhPaths.showMessage(context, "Inquiry string reset to default successfully.");
                                currentInquiryHintTextView.setText(R.string.usb_linux_file_cd_storage_gadget);
                            } else {
                                NhPaths.showMessage(context, "Inquiry string changed to '" + newInquiry + "' successfully.");
                                currentInquiryHintTextView.setText(newInquiry);
                            }
                        } else {
                            NhPaths.showMessage_long(context, "Failed to change inquiry string to '" + newInquiry +
                                    "'.");
                        }
                        break;
                    case USBArsenalHandlerThread.REMOVE_INIT:
                        NhPaths.showMessage_long(context, (int) resultObject == 0
                                ? "Successfully removed init file. Please restart USB Arsenal"
                                : "Failed to remove /init.nethunter.rc. Please try manually");
                        break;
                    case USBArsenalHandlerThread.GET_USBSWITCH_SQL_DATA:
                        {
                            usbSwitchInfoEditTextGroup[0].setText(((USBArsenalUSBSwitchModel)resultObject).getidVendor());
                            usbSwitchInfoEditTextGroup[1].setText(((USBArsenalUSBSwitchModel)resultObject).getidProduct());
                            usbSwitchInfoEditTextGroup[2].setText(((USBArsenalUSBSwitchModel)resultObject).getmanufacturer());
                            usbSwitchInfoEditTextGroup[3].setText(((USBArsenalUSBSwitchModel)resultObject).getproduct());
                            usbSwitchInfoEditTextGroup[4].setText(((USBArsenalUSBSwitchModel)resultObject).getserialnumber());
                        }
                        break;
                    case USBArsenalHandlerThread.GET_USBNETWORK_SQL_DATA:
                        {
                            usbNetworkInfoEditTextGroup[0].setText(((USBArsenalUSBNetworkModel)resultObject).getupstream_iface());
                            usbNetworkInfoEditTextGroup[1].setText(((USBArsenalUSBNetworkModel)resultObject).getusb_iface());
                            usbNetworkInfoEditTextGroup[2].setText(((USBArsenalUSBNetworkModel)resultObject).getip_address_for_target());
                            usbNetworkInfoEditTextGroup[3].setText(((USBArsenalUSBNetworkModel)resultObject).getip_gateway());
                            usbNetworkInfoEditTextGroup[4].setText(((USBArsenalUSBNetworkModel)resultObject).getip_subnetmask());
                        }
                        break;
                }
        }));
        if (iswatch && init.exists()) removeInitFile();
        { Message msg = new Message();
            msg.what = USBArsenalHandlerThread.IS_INIT_EXIST;
            msg.obj = "[ -f /init.nethunter.rc ]";
            usbArsenalHandlerThread.getHandler().sendMessage(msg); }

        { Message msg = new Message();
            msg.what = USBArsenalHandlerThread.GET_STORAGE_FUNC_FOLDER_NAME;
            msg.obj = "find /config/usb_gadget/g1/functions/ -name \"mass_storage.*\" -maxdepth 1 -type d -exec basename {} \\; | head -n1";
            usbArsenalHandlerThread.getHandler().sendMessage(msg); }

    }


    @Override
    public void onStart() {
        super.onStart();
        reloadUSBStateImageButton.performClick();
        if (imageMounterLL.getVisibility() == View.VISIBLE) {
            reloadMountStateButton.performClick();
        }
    }

    @Override
    public void onDestroyView() {
        usbArsenalHandlerThread.setOnShellExecuterFinishedListener(null);
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        usbStatusTextView = null;
        mountedImageTextView = null;
        mountedImageHintTextView = null;
        usbNetworkTetheringHintTextView = null;
        reloadUSBStateImageButton = null;
        reloadMountStateButton = null;
        imageMounterLL = null;
        usbNetworkTetheringLL = null;
        setUSBIfaceButton = null;
        mountImgButton = null;
        unmountImgButton = null;
        setUSBNetworkTetheringButton = null;
        saveUSBFunctionConfigButton = null;
        saveUSBNetworkTetheringConfigButton = null;
        targetOSSpinner = null;
        imgFileSpinner = null;
        usbFuncSpinner = null;
        usbNetworkAttackModeSpinner = null;
        adbSpinner = null;
        inquiryStringEditText = null;
        currentInquiryHintTextView = null;
        Arrays.fill(usbSwitchInfoEditTextGroup, null);
        Arrays.fill(usbNetworkInfoEditTextGroup, null);
    }

    @Override
    public void onDestroy() {
        usbArsenalHandlerThread.setOnShellExecuterFinishedListener(null);
        usbArsenalHandlerThread.quitSafely();
        super.onDestroy();
    }

    private void postToView(View owner, Runnable action) {
        uiHandler.post(() -> {
            if (getView() == owner) action.run();
        });
    }

    private void showMoreSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.usbarsenal_more_bottomsheet, null, false);
        sheet.findViewById(R.id.usbarsenal_more_backup).setOnClickListener(v -> {
            dialog.dismiss();
            showDatabaseDialog(false);
        });
        sheet.findViewById(R.id.usbarsenal_more_restore).setOnClickListener(v -> {
            dialog.dismiss();
            showDatabaseDialog(true);
        });
        sheet.findViewById(R.id.usbarsenal_more_reset).setOnClickListener(v -> {
            dialog.dismiss();
            new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Home_Alert)
                    .setTitle("Reset USB Arsenal configuration?")
                    .setMessage("Saved USB and network settings will be replaced with the defaults.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset", (d, which) -> {
                        if (USBArsenalSQL.getInstance(context).resetData()) {
                            NhPaths.showMessage(context, "USB Arsenal configuration reset.");
                            refreshUSBSwitchInfos(gettargetOSSpinnerString(), getusbFuncSpinnerString());
                            refreshUSBNetworkInfos(getusbNetWorkModeSpinnerPosition());
                        } else {
                            NhPaths.showMessage_long(context, "Failed to reset the database.");
                        }
                    }).show();
        });
        sheet.findViewById(R.id.usbarsenal_more_remove_init).setOnClickListener(v -> {
            dialog.dismiss();
            new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Home_Alert)
                    .setTitle("Remove init file?")
                    .setMessage("Remove /init.nethunter.rc, then reopen USB Arsenal to reload the available functions.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, which) -> removeInitFile())
                    .show();
        });
        sheet.findViewById(R.id.usbarsenal_more_info).setOnClickListener(v -> {
            dialog.dismiss();
            BottomSheetDialog info = new BottomSheetDialog(requireContext(), R.style.ThemeOverlay_Home_BottomSheet);
            View content = LayoutInflater.from(info.getContext()).inflate(R.layout.usbarsenal_info_bottomsheet, null, false);
            ((TextView) content.findViewById(R.id.usbarsenal_info_title)).setText("USB Arsenal usage");
            ((TextView) content.findViewById(R.id.usbarsenal_info_message)).setText(R.string.usbarsenal_description);
            content.findViewById(R.id.usbarsenal_info_close).setOnClickListener(x -> info.dismiss());
            info.setContentView(content);
            info.show();
        });
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showDatabaseDialog(boolean restore) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Home_Alert);
        View form = LayoutInflater.from(builder.getContext()).inflate(R.layout.usbarsenal_database_dialog, null, false);
        ((TextView) form.findViewById(R.id.f_usbarsenal_adb_tv_title1)).setText(
                restore ? R.string.usb_full_path_db_restore : R.string.usb_full_path_db_save);
        TextInputLayout pathLayout = form.findViewById(R.id.usbarsenal_database_path_til);
        EditText pathEdit = form.findViewById(R.id.f_usbarsenal_adb_et_storedpath);
        pathEdit.setText(String.format("%s/FragmentUSBArsenal", NhPaths.APP_SD_SQLBACKUP_PATH));
        AlertDialog dialog = builder.setTitle(restore ? "Restore database" : "Back up database")
                .setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton(restore ? "Restore" : "Back up", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            pathLayout.setError(null);
            String path = pathEdit.getText().toString().trim();
            if (path.isEmpty()) {
                pathLayout.setError("Enter a database file path");
                return;
            }
            USBArsenalSQL database = USBArsenalSQL.getInstance(context);
            String result = restore ? database.restoreData(path) : database.backupData(path);
            if (result != null) {
                pathLayout.setError(result);
                return;
            }
            NhPaths.showMessage(context, restore ? "USB Arsenal database restored" : "Database backed up to " + path);
            if (restore) {
                refreshUSBSwitchInfos(gettargetOSSpinnerString(), getusbFuncSpinnerString());
                refreshUSBNetworkInfos(getusbNetWorkModeSpinnerPosition());
            }
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void removeInitFile() {
        Message message = Message.obtain();
        message.what = USBArsenalHandlerThread.REMOVE_INIT;
        message.obj = "mount -o rw,remount /; rm /init.nethunter.rc; mount -o ro,remount /; [ ! -f /init.nethunter.rc ]";
        usbArsenalHandlerThread.getHandler().sendMessage(message);
    }

    private void getImageFiles() {
        mountImgButton.setEnabled(false);
        unmountImgButton.setEnabled(false);

        ArrayList<String> result = new ArrayList<>();
        File imageFolder = new File(NhPaths.APP_SD_FILES_IMG_PATH);

        if (!imageFolder.exists()) {
            NhPaths.showMessage(context, "Creating directory for storing image files..");
            if (!imageFolder.mkdir()) {
                String msg = "Failed to create directory for storing image files at " + NhPaths.APP_SD_FILES_IMG_PATH;
                NhPaths.showMessage(context, msg);
                Log.e(TAG, msg);
                ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(activity, R.layout.usbarsenal_spinner_item, result);
                emptyAdapter.setDropDownViewResource(R.layout.usbarsenal_spinner_item);
                imgFileSpinner.setAdapter(emptyAdapter);
                updateImageFileState(false);
                return;
            }
        }

        File[] filesInFolder = null;
        try {
            filesInFolder = imageFolder.listFiles();
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to list files in " + imageFolder.getAbsolutePath(), e);
        }

        if (filesInFolder == null) {
            Log.w(TAG, "No files found or access denied in " + imageFolder.getAbsolutePath());
        } else {
            for (File file : filesInFolder) {
                if (!file.isDirectory()) {
                    String name = file.getName();
                    if (name.endsWith(".img") || name.endsWith(".iso")) {
                        result.add(name);
                    }
                }
            }
        }

        ArrayAdapter<String> imageAdapter = new ArrayAdapter<>(activity, R.layout.usbarsenal_spinner_item, result);
        imageAdapter.setDropDownViewResource(R.layout.usbarsenal_spinner_item);
        imgFileSpinner.setAdapter(imageAdapter);
        updateImageFileState(!result.isEmpty());
    }

    private void updateImageFileState(boolean hasImages) {
        imgFileSpinner.setEnabled(hasImages);
        mountImgButton.setEnabled(hasImages);
        unmountImgButton.setEnabled(true);
        View root = getView();
        if (root != null) root.findViewById(R.id.usb_image_files_empty)
                .setVisibility(hasImages ? View.GONE : View.VISIBLE);
    }

    private String getusbFuncSpinnerString() {
        Object selected = usbFuncSpinner.getSelectedItem();
        return USBFunctionProfile.withAdb(selected == null ? null : selected.toString(),
                is_init_exists, adbSpinner.getSelectedItemPosition() == 0);
    }

    private int findLegacyAdbOption(boolean enabled) {
        if (usbFuncSpinner.getSelectedItem() == null) return -1;
        List<String> profiles = new ArrayList<>();
        for (int i = 0; i < usbFuncSpinner.getCount(); i++) {
            profiles.add(usbFuncSpinner.getItemAtPosition(i).toString());
        }
        return USBFunctionProfile.findAdbVariant(profiles, usbFuncSpinner.getSelectedItem().toString(), enabled);
    }

    private void updateAdbSelection() {
        updateAdbSelection(true);
    }

    private void updateAdbSelection(boolean syncWithProfile) {
        View root = getView();
        if (root == null) return;
        TextView hint = root.findViewById(R.id.usb_adb_hint);
        Object selected = usbFuncSpinner.getSelectedItem();
        adbSpinner.setEnabled(true);
        if (syncWithProfile && selected != null) {
            if (is_init_exists) adbSpinner.setSelection(USBFunctionProfile.hasAdb(selected.toString()) ? 0 : 1);
            else if ("reset".equals(selected.toString())) adbSpinner.setSelection(0);
        }
        String error = adbChoiceError();
        if (selected == null) {
            hint.setText("Select a USB function to configure ADB.");
        } else if (error != null) {
            hint.setText(error);
        } else if (is_init_exists) {
            hint.setText("ADB follows the selected USB profile. Available matching profiles switch automatically.");
        } else if ("reset".equals(selected.toString())) {
            hint.setText("Reset restores ADB only. To disable ADB, select a different USB function.");
        } else {
            hint.setText("Include Android Debug Bridge in the selected USB function.");
        }
        hint.setVisibility(View.VISIBLE);
    }

    private String adbChoiceError() {
        Object selected = usbFuncSpinner.getSelectedItem();
        if (selected == null) return "Select a USB function first.";
        boolean enabled = adbSpinner.getSelectedItemPosition() == 0;
        if (is_init_exists && USBFunctionProfile.hasAdb(selected.toString()) != enabled) {
            return "This init profile does not support the selected ADB state. Choose another USB profile.";
        }
        if (!is_init_exists && "reset".equals(selected.toString()) && !enabled) {
            return "Reset always enables ADB. Choose another USB function to apply Disable.";
        }
        return null;
    }

    private String gettargetOSSpinnerString() {
        return targetOSSpinner.getSelectedItem().toString();
    }

    private int getusbNetWorkModeSpinnerPosition() {
        return usbNetworkAttackModeSpinner.getSelectedItemPosition();
    }

    private boolean isAllUSBInfosValid() {
        String adbError = adbChoiceError();
        if (adbError != null) {
            updateAdbSelection(false);
            NhPaths.showMessage(context, adbError);
            return false;
        }
        if (!is_init_exists){
            if (!usbSwitchInfoEditTextGroup[0].getText().toString().matches("^0x[0-9a-fA-F]{4}$")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Enter four hexadecimal digits prefixed with 0x, for example 0x18d1.").create().show();
                return false;
            }
            if (!usbSwitchInfoEditTextGroup[1].getText().toString().matches("^0x[0-9a-fA-F]{4}$")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Enter four hexadecimal digits prefixed with 0x, for example 0x18d1.").create().show();
                return false;
            }
            if (!usbSwitchInfoEditTextGroup[2].getText().toString().matches("^\\w+$|^$")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Use letters, digits or underscores, or leave the value empty.").create().show();
                return false;
            }
            if (!usbSwitchInfoEditTextGroup[3].getText().toString().matches("^\\w+$|^$")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Use letters, digits or underscores, or leave the value empty.").create().show();
                return false;
            }
            if (!usbSwitchInfoEditTextGroup[4].getText().toString().matches("^[0-9A-Z]{10}$|^$")) {
                new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Home_Alert).setTitle("Invalid value").setMessage("Enter ten uppercase letters or digits, or leave the value empty.").create().show();
                return false;
            }
        }
        return true;
    }

    private void refreshUSBSwitchInfos(String targetOSName, String functionName) {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("targetOSName", targetOSName);
        bundle.putString("functionName", functionName);
        msg.what = USBArsenalHandlerThread.GET_USBSWITCH_SQL_DATA;
        msg.obj = context;
        msg.setData(bundle);
        usbArsenalHandlerThread.getHandler().sendMessage(msg);
    }

    private void refreshUSBNetworkInfos(int attackModePosition) {
        Message msg = new Message();
        msg.what = USBArsenalHandlerThread.GET_USBNETWORK_SQL_DATA;
        msg.obj = context;
        msg.arg1 = attackModePosition;
        usbArsenalHandlerThread.getHandler().sendMessage(msg);
    }

    ////
    // Bridge side functions
    ////

    public void run_cmd_android(String cmd) {
        @SuppressLint("SdCardPath") Intent intent = Bridge.createExecuteIntent("/data/data/com.offsec.nhterm/files/usr/bin/android-su", cmd);
        context.startActivity(intent);
    }
}
