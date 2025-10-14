package com.offsec.nethunter.bridge;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;

public class Runner extends AppCompatActivity{
  public static AppCompatActivity activity;
  public static final WeakReference<Context> context = null;

  // Meant to be used in context
  public static void run_cmd(String cmd) {
    String execPath = context.get().getFilesDir().getPath() + "/usr/bin/kali";
    Intent intent = Bridge.createExecuteIntent(execPath, cmd);
    context.get().startActivity(intent);
  }

  public static void run_cmd_android(String cmd) {
    String execPath = context.get().getFilesDir().getPath() + "/usr/bin/android-su";
    Intent intent = Bridge.createExecuteIntent(execPath, cmd);
    context.get().startActivity(intent);
  }

  // Meant to be used in activity
  public static void run_cmd_activity(String cmd) {
    String execPath = activity.getFilesDir().getPath() + "/usr/bin/kali";
    Intent intent = Bridge.createExecuteIntent(execPath, cmd);
    activity.startActivity(intent);
  }

  public static void run_cmd_android_activity(String cmd) {
    String execPath = activity.getFilesDir().getPath() + "/usr/bin/android-su";
    Intent intent = Bridge.createExecuteIntent(execPath, cmd);
    activity.startActivity(intent);
  }
}