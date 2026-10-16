/**
 * Fragment for managing kernel flashing on an Android device using AnyKernel in the NetHunter app.
 * Handles file selection, the flashing process, and device reboot after the flash.
 *
 * This fragment allows the user to select a zip file, flash it to the device,
 * and reboot the device if the flashing process is successful.
 */
package com.offsec.nethunter;

import android.app.Activity;
import android.content.Context;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnyKernelFragment extends Fragment {
	private TextView tvLog, tvFilePath;
	private MaterialButton btnSelect, btnFlash, btnCancel, btnReboot;
	private ScrollView scrollView;
	private Uri selectedUri;
	private String selectedFileName;
	private final KernelFlashState flashState = new KernelFlashState();
	private static final String BUSYBOX = "/data/adb/ksu/bin/busybox";
	private static final String BUSYBOX_MAGISK = "/data/adb/magisk/busybox";
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
					selectedFileName = selectedUri.getLastPathSegment();
					flashState.select();
					renderFlashState();
					// Clear initial text or old logs
					tvLog.setText("");
					updateLog("[SELECTED] " + selectedFileName);
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
				flashState.startFlashing();
				renderFlashState();
				runAnyKernelFlash(selectedUri);
			}
		});

		// Cancel selection handler
		btnCancel.setOnClickListener(view -> {
			selectedUri = null;
			selectedFileName = null;
			flashState.reset();
			renderFlashState();
			updateLog("[CANCELLED] Selection cleared.");
		});

		// Reboot button handler
		btnReboot.setOnClickListener(view -> Shell.cmd("svc power reboot || reboot").submit());
		renderFlashState();
		return v;
	}

	private void renderFlashState() {
		if (getView() == null && btnSelect == null) return;
		KernelFlashState.Status status = flashState.getStatus();
		boolean selected = status == KernelFlashState.Status.SELECTED;
		boolean flashing = status == KernelFlashState.Status.FLASHING;
		btnSelect.setVisibility(selected || flashing ? View.GONE : View.VISIBLE);
		btnSelect.setEnabled(!flashing);
		btnFlash.setVisibility(selected ? View.VISIBLE : View.GONE);
		btnCancel.setVisibility(selected ? View.VISIBLE : View.GONE);
		btnReboot.setVisibility(status == KernelFlashState.Status.SUCCESS ? View.VISIBLE : View.GONE);
		tvFilePath.setText(selectedFileName == null ? "File not selected" : selectedFileName);
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
		Context context = requireContext().getApplicationContext();
		executor.execute(() -> {
			// Create a temporary file for flashing
			String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
			File cacheDir = context.getCacheDir();
			File tmpFile = new File(cacheDir, "anykernel_" + timestamp + ".zip");
			// Copy the selected file to the temporary directory
			try (InputStream input = context.getContentResolver().openInputStream(uri);
				 FileOutputStream out = new FileOutputStream(tmpFile)) {
				if (input == null) {
					throw new IllegalStateException("Unable to open selected file");
				}
				byte[] buffer = new byte[8192];
				int len;
				while ((len = input.read(buffer)) != -1) {
					out.write(buffer, 0, len);
				}
			} catch (Exception e) {
				if (!tmpFile.delete() && tmpFile.exists()) {
					updateLog("[WARN] Could not remove temporary file.");
				}
				updateLog("[ERROR] Copy failed: " + e.getMessage());
				showFlashResult(false);
				return;
			}
			// Create a directory for extraction
			String destZip = tmpFile.getAbsolutePath();
			File destDirFile = new File(cacheDir, "anykernel3_" + timestamp);
			String destDir = destDirFile.getAbsolutePath();
			String cmd = KernelFlashCommand.buildInstallCommand(
					destZip, destDir, BUSYBOX, BUSYBOX_MAGISK);

			// Execute the command via Shell
			Shell.Result result = Shell.cmd(cmd).to(new com.topjohnwu.superuser.CallbackList<String>() {
				@Override
				public void onAddElement(String s) {
					String clean = processUiPrint(s);
					if (clean != null) {
						updateLog(clean);
					}
				}
			}).exec();
			// Clean up the temporary files after flashing
			Shell.cmd("rm -rf '" + destDir + "' '" + destZip + "'").exec();
			updateLog(result.isSuccess() ? "[SUCCESS] Kernel flash completed." :
					"[ERROR] Kernel flash failed (exit " + result.getCode() + ").");
			showFlashResult(result.isSuccess());
		});
	}

	private void showFlashResult(boolean success) {
		flashState.complete(success);
		Activity activity = getActivity();
		if (activity == null) return;
		activity.runOnUiThread(() -> {
			if (getView() == null) return;
			renderFlashState();
		});
	}

	/**
	 * Update the log in the TextView.
	 *
	 * @param msg The message to log.
	 */
	private void updateLog(String msg) {
		Activity activity = getActivity();
		if (activity != null) {
			activity.runOnUiThread(() -> {
				if (getView() == null) return;
				tvLog.append(msg + "\n");
				scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
			});
		}
	}

	@Override
	public void onDestroy() {
		executor.shutdownNow();
		super.onDestroy();
	}
}
// End of AnyKernelFragment class.
