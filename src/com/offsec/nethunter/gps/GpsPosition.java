package com.offsec.nethunter.gps;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Created by Danial on 2/23/2015.
 * https://github.com/danialgoodwin/android-app-samples/blob/master/gps-satellite-nmea-info/app/src/main/java/net/simplyadvanced/gpsandsatelliteinfo/GpsPosition.java
 */
public class GpsPosition {
    public final float time = 0.0f;
    private final float latitude = 0.0f;
    private final float longitude = 0.0f;
    private final int quality = 0;
    private final float direction = 0.0f;
    private final float altitude = 0.0f;
    private final float velocity = 0.0f;
    public void updateIsfixed() {
        boolean isFixed = false;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "GpsPosition: latitude: %f, longitude: %f, time: %f, quality: %d, " +
                        "direction: %f, altitude: %f, velocity: %f", latitude, longitude, time, quality,
                direction, altitude, velocity);
    }
}