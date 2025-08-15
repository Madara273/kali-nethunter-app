package com.offsec.nethunter.gps;

import android.Manifest;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class NMEA {
    private static GnssStatus lastGnssStatus;
    private static GpsStatus lastGpsStatus;

    // Call this once to register for GNSS updates (API 24+)
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static void registerGnssStatusCallback(LocationManager locationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.registerGnssStatusCallback(new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    lastGnssStatus = status;
                }
            }, new Handler(Looper.getMainLooper()));
        }
    }

    // Call this from your GpsStatus.Listener (API 21-23)
    public static void updateGpsStatus(GpsStatus gpsStatus) {
        lastGpsStatus = gpsStatus;
    }

    public static String formatSpeedKt(Location location) {
        return location.hasSpeed() ? String.valueOf(location.getSpeed() * 1.94384449) : "";
    }

    /**
     * Formats the bearing from the #Location into a string.  If the
     * bearing is unknown, it returns an empty string.
     */
    public static String formatBearing(Location location) {
        return location.hasBearing() ? String.valueOf(location.getBearing()) : "";
    }

    public static String formatGpsGsa() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ? formatGpsGsaGnss() : formatGpsGsaGps();
    }

    public static List<String> formatGpsGsv() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ? formatGpsGsvGnss() : formatGpsGsvGps();
    }

    // --- GNSSStatus (API 24+) ---
    private static String formatGpsGsaGnss() {
        if (lastGnssStatus == null) return "";
        StringBuilder prn = new StringBuilder();
        int nbr_sat = 0;
        int satCount = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            satCount = lastGnssStatus.getSatelliteCount();
        }
        for (int i = 0; i < 12; i++) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (i < satCount && lastGnssStatus.usedInFix(i)) {
                    prn.append(lastGnssStatus.getSvid(i));
                    nbr_sat++;
                }
            }
            prn.append(",");
        }
        String fix = (nbr_sat > 3) ? "3" : (nbr_sat > 0) ? "2" : "1";
        return fix + "," + prn + ",,,";
    }

    private static List<String> formatGpsGsvGnss() {
        List<String> gsv = new ArrayList<>();
        if (lastGnssStatus == null) return gsv;
        int nbr_sat = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nbr_sat = lastGnssStatus.getSatelliteCount();
        }
        int idx = 0;
        for (int i = 0; i < 3 && idx < nbr_sat; i++) {
            StringBuilder g = new StringBuilder(Integer.toString(nbr_sat));
            for (int n = 0; n < 4 && idx < nbr_sat; n++, idx++) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    g.append(",").append(lastGnssStatus.getSvid(idx))
                            .append(",").append(lastGnssStatus.getElevationDegrees(idx))
                            .append(",").append(lastGnssStatus.getAzimuthDegrees(idx))
                            .append(",").append(lastGnssStatus.getCn0DbHz(idx));
                }
            }
            gsv.add(g.toString());
        }
        return gsv;
    }

    // --- GpsStatus (API 21-23) ---
    private static String formatGpsGsaGps() {
        if (lastGpsStatus == null) return "";
        StringBuilder prn = new StringBuilder();
        int nbr_sat = 0;
        Iterator<GpsSatellite> satellites = lastGpsStatus.getSatellites().iterator();
        for (int i = 0; i < 12; i++) {
            if (satellites.hasNext()) {
                GpsSatellite sat = satellites.next();
                if (sat.usedInFix()) {
                    prn.append(sat.getPrn());
                    nbr_sat++;
                }
            }
            prn.append(",");
        }
        String fix = (nbr_sat > 3) ? "3" : (nbr_sat > 0) ? "2" : "1";
        return fix + "," + prn + ",,,";
    }

    private static List<String> formatGpsGsvGps() {
        List<String> gsv = new ArrayList<>();
        if (lastGpsStatus == null) return gsv;
        int nbr_sat = 0;
        for (GpsSatellite sat : lastGpsStatus.getSatellites()) nbr_sat++;
        Iterator<GpsSatellite> satellites = lastGpsStatus.getSatellites().iterator();
        for (int i = 0; i < 3; i++) {
            if (satellites.hasNext()) {
                StringBuilder g = new StringBuilder(Integer.toString(nbr_sat));
                for (int n = 0; n < 4; n++) {
                    if (satellites.hasNext()) {
                        GpsSatellite sat = satellites.next();
                        g.append(",").append(sat.getPrn()).append(",").append(sat.getElevation())
                                .append(",").append(sat.getAzimuth()).append(",").append(sat.getSnr());
                    }
                }
                gsv.add(g.toString());
            }
        }
        return gsv;
    }
}