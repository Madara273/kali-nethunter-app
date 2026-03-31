/**
 * Fragment for managing kernel flashing on an Android device using AnyKernel in the NetHunter app.
 * Handles file selection, the flashing process, and device reboot after the flash.
 *
 * This fragment allows the user to select a zip file, flash it to the device,
 * and reboot the device if the flashing process is successful.
 */
package com.offsec.nethunter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.topjohnwu.superuser.Shell;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AnyKernelFragment extends Fragment {
	private TextView tvLog, tvFilePath;
	private MaterialButton btnSelect, btnFlash, btnCancel, btnReboot;
	private ScrollView scrollView;
	private Uri selectedUri;
	private final String BUSYBOX = "/data/adb/ksu/bin/busybox"; // KernelSu
	private final String BUSYBOX_MAGISK = "/data/adb/magisk/busybox"; // Magisk

	/**
	 * Create a new instance of the fragment with the provided argument.
	 *
	 * @param itemId Item identifier.
	 * @return Fragment instance.
	 */
	public static AnyKernelFragment newInstance(int itemId) {
		AnyKernelFragment fragment = new AnyKernelFragment();
		Bundle args = new Bundle();
		args.putInt("arg_item_id", itemId);
		fragment.setArguments(args);
		return fragment;
	}

	// Launcher for picking a file through the user interface
	private final ActivityResultLauncher<Intent> pickerLauncher = registerForActivityResult(
		new ActivityResultContracts.StartActivityForResult(),
		result -> {
			if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
				selectedUri = result.getData().getData();
				if (selectedUri != null) {
					tvFilePath.setText(selectedUri.getLastPathSegment());
					// Switch UI to Flash/Cancel mode
					btnSelect.setVisibility(View.GONE);
					btnFlash.setVisibility(View.VISIBLE);
					btnCancel.setVisibility(View.VISIBLE);
					// Hide the reboot button if it was from a previous flash
					btnReboot.setVisibility(View.GONE);
					// Clear initial text or old logs
					tvLog.setText("");
					updateLog("[SELECTED] " + selectedUri.getLastPathSegment());
				}
			}
		}
	);

	/**
	 * Create the fragment view, including setting up buttons and their click listeners.
	 *
	 * @param inflater LayoutInflater for inflating the layout.
	 * @param container Container for the layout.
	 * @param savedInstanceState Saved instance state (if any).
	 * @return The fragment view.
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_anykernel, container, false);
		// Setup Full System Info (Uname and Device info)
		TextView tvDevice = v.findViewById(R.id.tv_device);
		TextView tvAndroid = v.findViewById(R.id.tv_android);
		TextView kernelText = v.findViewById(R.id.kernel_full_uname);

		if (tvDevice != null) {
			tvDevice.setText("Device: " + Build.MODEL + " (" + Build.DEVICE + ")");
		}

		if (tvAndroid != null) {
			tvAndroid.setText("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
		}

		if (kernelText != null) {
			// Get full version string like in terminal
			String fullKernel = Shell.cmd("cat /proc/version").exec().getOut().get(0);
			kernelText.setText("Kernel: " + fullKernel);
		}

		// Find the UI elements
		tvLog = v.findViewById(R.id.tv_log);
		tvFilePath = v.findViewById(R.id.tv_file_path);
		btnSelect = v.findViewById(R.id.btn_select_zip);
		btnFlash = v.findViewById(R.id.btn_flash);
		btnCancel = v.findViewById(R.id.btn_cancel);
		btnReboot = v.findViewById(R.id.btn_reboot);
		scrollView = v.findViewById(R.id.log_scroll);
		// Select file button handler
		btnSelect.setOnClickListener(view -> {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("application/zip");
			pickerLauncher.launch(intent);
		});

		// Flash button handler
		btnFlash.setOnClickListener(view -> {
			if (selectedUri != null) {
				btnFlash.setVisibility(View.GONE);
				btnCancel.setVisibility(View.GONE);
				btnSelect.setEnabled(false);
				runAnyKernelFlash(selectedUri);
			}
		});

		// Cancel selection handler
		btnCancel.setOnClickListener(view -> {
			selectedUri = null;
			tvFilePath.setText("File not selected");
			btnSelect.setVisibility(View.VISIBLE);
			btnFlash.setVisibility(View.GONE);
			btnCancel.setVisibility(View.GONE);
			updateLog("[CANCELLED] Selection cleared.");
		});

		// Reboot button handler
		btnReboot.setOnClickListener(view -> Shell.cmd("svc power reboot || reboot").submit());

		return v;
	}

	/**
	 * Process and clean up strings from AnyKernel ui_print commands.
	 */
	private String processUiPrint(String s) {
		if (s == null) return null;
		String trimmed = s.trim();
		if (trimmed.startsWith("Archive:") || trimmed.startsWith("inflating:") ||
			trimmed.startsWith("extracting:") || trimmed.contains("trimmed") ||
			trimmed.startsWith("progress")) return null;
		if (trimmed.startsWith("ui_print")) {
			String clean = trimmed.substring(8).trim();
			return clean.isEmpty() ? null : clean;
		}
		if (trimmed.equals("ui_print")) return "";
		return trimmed;
	}

	/**
	 * Perform the flashing process by copying the file, extracting it,
	 * and executing the script for installation.
	 *
	 * @param uri URI of the flash file.
	 */
	private void runAnyKernelFlash(Uri uri) {
		new Thread(() -> {
			// Create a temporary file for flashing
			String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
			File cacheDir = getContext().getCacheDir();
			File tmpFile = new File(cacheDir, "anykernel_" + timestamp + ".zip");
			// Copy the selected file to the temporary directory
			try (InputStream input = getContext().getContentResolver().openInputStream(uri);
				 FileOutputStream out = new FileOutputStream(tmpFile)) {
				byte[] buffer = new byte[8192];
				int len;
				while ((len = input.read(buffer)) != -1) {
					out.write(buffer, 0, len);
				}
			} catch (Exception e) {
				updateLog("Copy Error: " + e.getMessage());
				return;
			}
			// Create a directory for extraction
			String destZip = tmpFile.getAbsolutePath();
			File destDirFile = new File(cacheDir, "anykernel3_" + timestamp);
			String destDir = destDirFile.getAbsolutePath();
			// Determine the working Busybox at the Shell level
			String BB_SELECT = "BB=\"" + BUSYBOX + "\"; [ ! -f \"$BB\" ] && BB=\"" + BUSYBOX_MAGISK + "\"; ";
			// Command to unzip and execute the installation script
			String cmd = BB_SELECT + "mkdir -p '" + destDir + "' && " +
					"$BB unzip -p -o '" + destZip + "' \"META-INF/com/google/android/update-binary\" > '" + destDir +
					"/update-binary' 2>/dev/null && " +
					"cp '" + destZip + "' '" + destDir + "/anykernel.zip' 2>/dev/null || true && " +
					"$BB chmod 755 '" + destDir + "/update-binary' && " +
					"$BB chown root:root '" + destDir + "/update-binary' && " +
					"(cd '" + destDir + "' && " +
						"if [ -f './update-binary' ]; then " +
							"AKHOME='" + destDir + "/tmp' $BB ash './update-binary' 3 1 '" + destDir +
							"/anykernel.zip'; " +
						"else " +
							"echo 'No installer script found' >&2; exit 1; " +
						"fi)";

			// Execute the command via Shell
			Shell.cmd(cmd).to(new com.topjohnwu.superuser.CallbackList<String>() {
				@Override
				public void onAddElement(String s) {
					String clean = processUiPrint(s);
					if (clean != null) {
						updateLog(clean);
					}
				}
			}).exec();
			// Clean up the temporary files after flashing
			Shell.cmd("rm -rf '" + destDir + "' '" + destZip + "'").submit();
			// Show reboot button after completion
			if (getActivity() != null) {
				getActivity().runOnUiThread(() -> {
					btnReboot.setVisibility(View.VISIBLE);
					btnSelect.setVisibility(View.VISIBLE);
					btnSelect.setEnabled(true);
					// Refresh uname after flash
					String newKernel = Shell.cmd("cat /proc/version").exec().getOut().get(0);
					TextView kernelText = getActivity().findViewById(R.id.kernel_full_uname);
					if (kernelText != null) kernelText.setText("Kernel: " + newKernel);
				});
			}
		}).start();
	}

	/**
	 * Update the log in the TextView.
	 *
	 * @param msg The message to log.
	 */
	private void updateLog(String msg) {
		if (getActivity() != null) {
			getActivity().runOnUiThread(() -> {
				tvLog.append(msg + "\n");
				scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
			});
		}
	}
}

// End of AnyKernelFragment class.
