package com.offsec.nethunter.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.offsec.nethunter.R;

public final class KaliServicesDialogs {
    private KaliServicesDialogs() {
    }

    public static void showInfo(Context context, String title, @StringRes int messageId) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.ThemeOverlay_Home_BottomSheet);
        View sheet = LayoutInflater.from(dialog.getContext())
                .inflate(R.layout.kaliservices_info_bottomsheet, null, false);
        ((TextView) sheet.findViewById(R.id.kaliservices_info_title)).setText(title);
        ((TextView) sheet.findViewById(R.id.kaliservices_info_message)).setText(messageId);
        sheet.findViewById(R.id.kaliservices_info_close).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheet);
        dialog.show();
    }
}
