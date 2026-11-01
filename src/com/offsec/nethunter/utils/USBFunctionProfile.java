package com.offsec.nethunter.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** ADB options must correspond to functions supported by the selected USB backend. */
public final class USBFunctionProfile {
    private USBFunctionProfile() { }

    public static String withAdb(String function, boolean initProfile, boolean enabled) {
        if (function == null || function.isEmpty()) return "reset";
        if (initProfile || "reset".equals(function)) return function;
        List<String> parts = new ArrayList<>(Arrays.asList(function.split(",")));
        parts.remove("adb");
        if (enabled) parts.add("adb");
        return String.join(",", parts);
    }

    public static boolean hasAdb(String function) {
        return Arrays.asList(function.split(",")).contains("adb");
    }

    public static int findAdbVariant(List<String> profiles, String selected, boolean enabled) {
        String base = withoutAdb(selected);
        for (int i = 0; i < profiles.size(); i++) {
            String option = profiles.get(i);
            if (hasAdb(option) == enabled && base.equals(withoutAdb(option))) return i;
        }
        return -1;
    }

    private static String withoutAdb(String function) {
        List<String> parts = new ArrayList<>(Arrays.asList(function.split(",")));
        parts.remove("adb");
        Collections.sort(parts);
        return parts.toString();
    }
}
