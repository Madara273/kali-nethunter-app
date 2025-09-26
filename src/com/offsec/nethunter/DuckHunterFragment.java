package com.offsec.nethunter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.SharePrefTag;
import com.offsec.nethunter.utils.ShellExecuter;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager.widget.ViewPager;

public class DuckHunterFragment extends Fragment {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static SharedPreferences sharedpreferences;
    private boolean showConvert = false;
    private static final HashMap<String, String> map = new HashMap<>();
    public static String lang = "us";
    private static String[] keyboardLayoutString;
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String TAG = "DuckHunterFragment";
    private Activity activity;
    private ViewPager mViewPager;
    private String duckyInputFile;
    private String duckyOutputFile;
    private boolean isReceiverRegistered;
    private boolean shouldconvert = true;
    private final DuckHuntBroadcastReceiver duckHuntBroadcastReceiver = new DuckHuntBroadcastReceiver();
    private final ShellExecuter exe = new ShellExecuter();

    public static DuckHunterFragment newInstance(int sectionNumber) {
        DuckHunterFragment fragment = new DuckHunterFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getContext();
        activity = getActivity();
        duckyInputFile = NhPaths.APP_SD_FILES_PATH + "/modules/ducky_in.txt";
        duckyOutputFile = NhPaths.APP_SD_FILES_PATH + "/modules/ducky_out.sh";
        map.put("American English", "us");
        map.put("Turkish", "tr");
        map.put("Swedish", "sv");
        map.put("Slovenian", "si");
        map.put("Russian", "ru");
        map.put("Portuguese", "pt");
        map.put("Norwegian", "no");
        map.put("Croatian", "hr");
        map.put("United Kingdom", "gb");
        map.put("French", "fr");
        map.put("Finland", "fi");
        map.put("Spain", "es");
        map.put("Danish", "dk");
        map.put("German", "de");
        map.put("Candian", "ca");
        map.put("Canadian Multilingual Standard", "cm");
        map.put("Brazil", "br");
        map.put("Belgian", "be");
        map.put("Hungarian", "hu");
        keyboardLayoutString = map.keySet().toArray(new String[0]);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.duck_hunter, container, false);
        final MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.duck_hunter, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuItem convert = menu.findItem(R.id.duckConvertAttack);
                if (convert != null) convert.setVisible(showConvert);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.duckConvertAttack) {
                    launchAttack();
                    return true;
                } else if (id == R.id.chooseLanguage) {
                    openLanguageDialog();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        TabsPagerAdapter tabsPagerAdapter = new TabsPagerAdapter(getChildFragmentManager());
        mViewPager = rootView.findViewById(R.id.pagerDuckHunter);
        mViewPager.setAdapter(tabsPagerAdapter);
        mViewPager.setOffscreenPageLimit(1);
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                showConvert = (position == 1);
                if (position == 1) {
                    setLang();
                    if (shouldconvert && activity != null) {
                        activity.sendBroadcast(new Intent()
                                .putExtra("ACTION", "WRITEDUCKY")
                                .setAction(BuildConfig.APPLICATION_ID + ".WRITEDUCKY")
                                .setPackage(activity.getPackageName()));
                    }
                }
                requireActivity().invalidateOptionsMenu();
            }
        });

        sharedpreferences = activity.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
        if (!sharedpreferences.contains("DuckHunterLanguageIndex")) {
            for (int i = 0; i < keyboardLayoutString.length; i++) {
                if ("us".equals(map.get(keyboardLayoutString[i]))) {
                    sharedpreferences.edit().putInt("DuckHunterLanguageIndex", i).apply();
                    break;
                }
            }
        }
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isReceiverRegistered) {
            ContextCompat.registerReceiver(activity, duckHuntBroadcastReceiver,
                    new IntentFilter(BuildConfig.APPLICATION_ID + ".SHOULDCONVERT"),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            isReceiverRegistered = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isReceiverRegistered) {
            activity.unregisterReceiver(duckHuntBroadcastReceiver);
            isReceiverRegistered = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Menu menu = null;
        mViewPager = null;
    }

    private static void setLang() {
        int keyboardLayoutIndex = sharedpreferences.getInt("DuckHunterLanguageIndex", 0);
        lang = map.get(keyboardLayoutString[keyboardLayoutIndex]);
    }

    private void openLanguageDialog() {
        int keyboardLayoutIndex = sharedpreferences.getInt("DuckHunterLanguageIndex", 0);
        MaterialAlertDialogBuilder builder = getMaterialAlertDialogBuilder();
        builder.setSingleChoiceItems(keyboardLayoutString, keyboardLayoutIndex, (dialog, which) -> {
            Editor editor = sharedpreferences.edit();
            editor.putInt("DuckHunterLanguageIndex", which);
            editor.putString(SharePrefTag.DUCKHUNTER_LANG_SHAREPREF_TAG, map.get(keyboardLayoutString[which]));
            editor.apply();
        });
        builder.show();
    }

    @NonNull
    private MaterialAlertDialogBuilder getMaterialAlertDialogBuilder() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.DialogStyleCompat);
        builder.setTitle("Language:");
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (mViewPager.getCurrentItem() == 1) {
                if (getView() == null) return;
                setLang();
                activity.sendBroadcast(new Intent()
                        .putExtra("ACTION", "WRITEDUCKY")
                        .setAction(BuildConfig.APPLICATION_ID + ".WRITEDUCKY")
                        .setPackage(activity.getPackageName()));
            }
        });
        return builder;
    }

    private void launchAttack() {
        if (activity == null) return;
        NhPaths.showMessage(activity, "Launching Attack");
        executorService.execute(() -> {
            boolean ok = exe.RunAsRootReturnValue("sh " + duckyOutputFile) == 0;
            mainHandler.post(() -> {
                if (!ok) {
                    if (new File("/config/usb_gadget/g1").exists()) {
                        NhPaths.showMessage_long(activity, "HID interfaces are not enabled! Please enable in USB Arsenal.");
                    } else if (new File("/dev/hidg0").exists()) {
                        NhPaths.showMessage_long(activity, "Fixing HID interface permissions..");
                        exe.RunAsRoot(new String[]{"chmod 666 /dev/hidg*"});
                    } else {
                        NhPaths.showMessage_long(activity, "HID interfaces are not patched or enabled, please check your kernel configuration.");
                    }
                }
            });
        });
    }

    public class TabsPagerAdapter extends FragmentStatePagerAdapter {
        TabsPagerAdapter(FragmentManager fm) { super(fm); }
        @NonNull
        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 1:
                    return new DuckHunterPreviewFragment(duckyInputFile, duckyOutputFile);
                case 2:
                    return new BtDuckyFragment();
                default:
                    return new DuckHunterConvertFragment(duckyInputFile);
            }
        }
        @Override
        public Parcelable saveState() { return null; }
        @Override
        public int getCount() { return 3; }
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 1: return "Preview";
                case 2: return "BT Ducky";
                default: return "Convert";
            }
        }
    }

    public class DuckHuntBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getStringExtra("ACTION"), "SHOULDCONVERT")) {
                shouldconvert = intent.getBooleanExtra("SHOULDCONVERT", true);
            }
        }
    }
}