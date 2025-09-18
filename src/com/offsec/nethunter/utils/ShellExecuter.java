package com.offsec.nethunter.utils;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ShellExecuter {
    private final SimpleDateFormat timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final static String TAG = "ShellExecuter";

    public ShellExecuter() {
    }

    public static Runtime cmd(String s) {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process process = runtime.exec(s);
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Log.e(TAG, "Process was interrupted", e);
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException while executing command: " + s, e);
        }
        return runtime;
    }

    public String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(command);
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                Log.e(TAG, "Process was interrupted while executing: " + command, e);
                Thread.currentThread().interrupt();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error executing command: " + command, e);
        }
        return output.toString();
    }

    public void RunAsRoot(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
                for (String tmpmd : command.split("\n")) {
                    os.writeBytes(tmpmd + '\n');
                }
                os.writeBytes("exit\n");
                os.flush();
            }
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Log.e(TAG, "Process was interrupted while executing root command", e);
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing root command", e);
        }
    }

    public String RunAsRootWithException(String command) throws RuntimeException {
        try {
            StringBuilder output = new StringBuilder();
            Process process = Runtime.getRuntime().exec("su -mm");
            try (OutputStream stdin = process.getOutputStream();
                 InputStream stdout = process.getInputStream();
                 InputStream stderr = process.getErrorStream()) {

                stdin.write((command + '\n').getBytes());
                stdin.write(("exit\n").getBytes());
                stdin.flush();

                try (BufferedReader br = new BufferedReader(new InputStreamReader(stdout))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                    if (output.length() > 0 && output.charAt(output.length() - 1) == '\n') {
                        output.setLength(output.length() - 1);
                    }
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(stderr))) {
                    String line;
                    if ((line = br.readLine()) != null) {
                        Log.e(TAG, "Shell Error: " + line);
                        throw new RuntimeException(line);
                    }
                }
            }
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Log.e(TAG, "Process was interrupted while executing root command", e);
                Thread.currentThread().interrupt();
            }
            process.destroy();
            return output.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected error while executing root command", ex);
        }
    }

    public String RunAsRootOutput(String command) {
        StringBuilder output = new StringBuilder();
        String line;
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();

            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                output.append(line).append('\n');
            }
            if (output.length() > 0)
                output = new StringBuilder(output.substring(0, output.length() - 1));
            br.close();
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.e(TAG, "Shell Error: " + line);
            }
            br.close();
            process.waitFor();
            process.destroy();
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing command: " + command, e);
        } catch (InterruptedException ex) {
            Log.e(TAG, "Process was interrupted while executing command: " + command, ex);
            Thread.currentThread().interrupt();
        }
        return output.toString();
    }

    public int RunAsRootOutput(String command, final TextView viewLogger) {
        int resultCode = 0;
        String line;
        try {
            //viewLogger.post(() -> viewLogger.append("\n\n ------------ \n\n\n"));
            Process process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                final Spannable tempText = new SpannableString(line + "\n");
                final Spannable timestamp = new SpannableString("[ " + timeStamp.format(new Date()) + " ]  ");
                timestamp.setSpan(new ForegroundColorSpan(Color.parseColor("#FFD561")), 0, timestamp.length(), 0);
                tempText.setSpan(new ForegroundColorSpan(line.startsWith("[!]") ? Color.CYAN : line.startsWith("[+]") ? Color.GREEN : line.startsWith("[-]") ? Color.parseColor("#D81B60") : Color.WHITE), 0, tempText.length(), 0);
                viewLogger.post(() -> {
                    viewLogger.append(timestamp);
                    viewLogger.append(tempText);
                });
            }
            //viewLogger.post(() -> viewLogger.append("\n\n ------------ \n\n"));
            br.close();
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.e(TAG, line);
            }
            br.close();
            process.waitFor();
            process.destroy();
            resultCode = process.exitValue();
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing command", e);
        } catch (InterruptedException ex) {
            Log.d(TAG, "An InterruptedException was caught: " + ex.getMessage());
        }
        return resultCode;
    }

    public int RunAsRootReturnValue(String command) {
        int resultCode = 0;
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            process.waitFor();
            process.destroy();
            resultCode = process.exitValue();
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing command", e);
        } catch (InterruptedException ex) {
            Log.d(TAG, "An InterruptedException was caught: " + ex.getMessage());
        }
        return resultCode;
    }

    public String RunAsChrootOutput(String command) {
        StringBuilder output = new StringBuilder();
        String line;
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();

            stdin.write((NhPaths.BUSYBOX + " chroot " + NhPaths.CHROOT_PATH() + " " + NhPaths.CHROOT_SUDO + " -E PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH su" + '\n').getBytes());
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                output.append(line).append('\n');
            }
            if (output.length() > 0 && output.charAt(output.length() - 1) == '\n') {
                output.setLength(output.length() - 1);
            }
            br.close();

            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.e("Shell Error:", line);
            }
            br.close();

            process.waitFor();
            process.destroy();
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing command", e);
        } catch (InterruptedException ex) {
            Log.d(TAG, "An InterruptedException was caught: " + ex.getMessage());
        }
        return output.toString();
    }

    public int RunAsChrootReturnValue(String command) {
        int resultCode = 0;
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            stdin.write((NhPaths.BUSYBOX + " chroot " + NhPaths.CHROOT_PATH() + " " + NhPaths.CHROOT_SUDO + " -E PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH su" + '\n').getBytes());
            stdin.write((command + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();
            process.waitFor();
            process.destroy();
            resultCode = process.exitValue();
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing command", e);
        } catch (InterruptedException ex) {
            Log.d(TAG, "An InterruptedException was caught: " + ex.getMessage());
        }
        return resultCode;
    }

    // this method accepts a text viu (prefect for cases like mana fragment)
    // if you need to manipulate the output use the SYNC method. (down)
    public void ReadFile_ASYNC(String _path, final EditText v) {
        final String command = "cat " + _path;
        new Thread(() -> {
            StringBuilder output = new StringBuilder();
            try {
                Process p = Runtime.getRuntime().exec("su -mm -c " + command);
                p.waitFor();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing command: " + command, e);
            }
            final String _output = output.toString();
            v.post(() -> v.setText(_output));
        }).start();
    }

    // WRAP THIS IN THE BACKGROUND IF POSSIBLE WHE USING IT
    public String ReadFile_SYNC(String _path) {
        StringBuilder output = new StringBuilder();
        String command = "cat " + _path;
        Process p;
        try {
            p = Runtime.getRuntime().exec("su -mm -c " + command);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading file: " + _path, e);
        }
        return output.toString();
    }

    // SAVE FILE CONTENTS: (contents, fullFilePath)
    public boolean SaveFileContents(String contents, String _path) {
        String _newCmd = "cat << 'EOF' > " + _path + "\n" + contents + "\nEOF";
        String _res = RunAsRootOutput(_newCmd);
        if (_res.isEmpty()) { // no error we fine
            return true;
        } else {
            Log.d("ErrorSavingFile: ", "Error: " + _res);
            return false;
        }
    }

    public String ReadFile(String duckyOutputFile) {
        StringBuilder output = new StringBuilder();
        String command = "cat " + duckyOutputFile;
        Process p;
        try {
            p = Runtime.getRuntime().exec("su -mm -c " + command);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading file: " + duckyOutputFile, e);
        }
        return output.toString();
    }

    public String RunAsRootReturnOutput(String s) {
        StringBuilder output = new StringBuilder();
        String line;
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();

            stdin.write((s + '\n').getBytes());
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                output.append(line).append('\n');
            }
            /* remove the last \n */
            if (output.length() > 0 && output.charAt(output.length() - 1) == '\n') {
                output.setLength(output.length() - 1);
            }

            br.close();
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.e("Shell Error:", line);
            }
            br.close();

            process.waitFor();
            process.destroy();
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing command", e);
        } catch (InterruptedException ex) {
            Log.d(TAG, "An InterruptedException was caught: " + ex.getMessage());
        }
        return output.toString();
    }

    public void RunAsRoot(String[] strings) {
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            for (String tmpmd : strings) {
                os.writeBytes(tmpmd + '\n');
            }
            os.writeBytes("exit\n");
            os.flush();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Log.e(TAG, "Process was interrupted", e);
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing root commands array", e);
        }
    }

    public void RunAsRootOutput(String[] strings, final TextView viewLogger) {
        StringBuilder output = new StringBuilder();
        String line;
        try {
            Process process = Runtime.getRuntime().exec("su -mm");
            OutputStream stdin = process.getOutputStream();
            InputStream stderr = process.getErrorStream();
            InputStream stdout = process.getInputStream();

            for (String tmpmd : strings) {
                stdin.write((tmpmd + '\n').getBytes());
            }
            stdin.write(("exit\n").getBytes());
            stdin.flush();
            stdin.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                output.append(line).append('\n');
                final Spannable tempText = new SpannableString(line + "\n");
                final Spannable timestamp = new SpannableString("[ " + timeStamp.format(new Date()) + " ]  ");
                timestamp.setSpan(new ForegroundColorSpan(Color.parseColor("#FFD561")), 0, timestamp.length(), 0);
                tempText.setSpan(new ForegroundColorSpan(line.startsWith("[!]") ? Color.CYAN : line.startsWith("[+]") ? Color.GREEN : line.startsWith("[-]") ? Color.parseColor("#D81B60") : Color.WHITE), 0, tempText.length(), 0);
                viewLogger.post(() -> {
                    viewLogger.append(timestamp);
                    viewLogger.append(tempText);
                });
            }
            br.close();
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.e("Shell Error:", line);
            }
            br.close();
            process.waitFor();
            process.destroy();
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while executing command", e);
        } catch (InterruptedException ex) {
            Log.d(TAG, "An InterruptedException was caught: " + ex.getMessage());
        }
    }

    public String execute(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error executing command: " + command, e);
        }
        return output.toString();
    }

    public void close() {
        // No resources to close in this implementation
        // If you had any resources (like sockets or files), you would close them here
        Log.d(TAG, "ShellExecuter closed");
    }
}
