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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.topjohnwu.superuser.Shell;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnyKernelFragment extends Fragment {
	private TextView tvLog, tvFilePath;
	private MaterialButton btnSelect, btnFlash, btnCancel, btnReboot, btnSupport;
	private LinearProgressIndicator progressBar;
	private ScrollView scrollView;
	private Uri selectedUri;
	private String currentSelectedModel = ""; // Track model selected from Hub
	private boolean isDialogShowing = false;
	private final String BUSYBOX = "/data/adb/ksu/bin/busybox"; // KernelSu
	private final String BUSYBOX_MAGISK = "/data/adb/magisk/busybox"; // Magisk
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private static final String REPO_RAW = "https://raw.githubusercontent.com/Madara273/nh-kernel-hub/main/";
	private static final String REPO_API = "https://api.github.com/repos/Madara273/nh-kernel-hub/contents/";

	private final Stack<Runnable> navStack = new Stack<>();
	private FrameLayout hubOverlay;
	private OnBackPressedCallback hubBackCallback;

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
					currentSelectedModel = ""; // Reset verification for manual files
					tvFilePath.setText(selectedUri.getLastPathSegment());
					// Switch UI to Flash/Cancel mode
					btnSelect.setVisibility(View.GONE);
					btnSupport.setVisibility(View.GONE);
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
		btnSupport = v.findViewById(R.id.btn_support_kernel);
		btnFlash = v.findViewById(R.id.btn_flash);
		btnCancel = v.findViewById(R.id.btn_cancel);
		btnReboot = v.findViewById(R.id.btn_reboot);
		progressBar = v.findViewById(R.id.download_progress);
		scrollView = v.findViewById(R.id.log_scroll);
		// Initialize Hub Overlay - attached to activity root to ensure center placement
		hubOverlay = new FrameLayout(requireContext());
		hubOverlay.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		hubOverlay.setBackgroundColor(Color.parseColor("#99000000"));
		hubOverlay.setVisibility(View.GONE);
		hubOverlay.setClickable(true);
		hubOverlay.setFocusable(true);
		// Add to activity content root to float ABOVE the fragment layout
		getActivity().findViewById(android.R.id.content).post(() -> {
			if (getActivity() != null) {
				((ViewGroup) getActivity().findViewById(android.R.id.content)).addView(hubOverlay);
			}
		});

		// Select file button handler
		btnSelect.setOnClickListener(view -> {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("application/zip");
			pickerLauncher.launch(intent);
		});

		// Support Kernel button handler
		btnSupport.setOnClickListener(view -> {
			if (!isDialogShowing) {
				btnReboot.setVisibility(View.GONE);
				navStack.clear();
				loadBrands();
			}
		});

		// Flash button handler
		btnFlash.setOnClickListener(view -> {
			if (selectedUri != null) {
				// Verification: strip non-alphanumeric for matching
				if (!currentSelectedModel.isEmpty()) {
					String deviceClean = Build.DEVICE.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
					String modelClean = Build.MODEL.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
					String targetClean = currentSelectedModel.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
					if (!targetClean.contains(deviceClean) && !targetClean.contains(modelClean) &&
						!deviceClean.contains(targetClean) && !modelClean.contains(targetClean)) {
						updateLog("[CRITICAL] Unsupported device!");
						updateLog("[ERROR] Kernel for: " + currentSelectedModel);
						updateLog("[ERROR] Device is: " + Build.MODEL + " (" + Build.DEVICE + ")");
						Toast.makeText(getContext(), "Unsupported", Toast.LENGTH_LONG).show();
						return;
					}
				}

				btnFlash.setVisibility(View.GONE);
				btnCancel.setVisibility(View.GONE);
				btnSelect.setEnabled(false);
				btnSupport.setEnabled(false);
				runAnyKernelFlash(selectedUri);
			}
		});

		// Cancel selection handler
		btnCancel.setOnClickListener(view -> {
			selectedUri = null;
			currentSelectedModel = "";
			tvFilePath.setText("File not selected");
			btnSelect.setVisibility(View.VISIBLE);
			btnSupport.setVisibility(View.VISIBLE);
			btnFlash.setVisibility(View.GONE);
			btnCancel.setVisibility(View.GONE);
			updateLog("[CANCELLED] Selection cleared.");
		});

		// Reboot button handler
		btnReboot.setOnClickListener(view -> Shell.cmd("svc power reboot || reboot").submit());
		// Initialize the systemic back dispatcher
		hubBackCallback = new OnBackPressedCallback(false) {
			@Override
			public void handleOnBackPressed() {
				if (!navStack.isEmpty()) {
					navStack.pop().run();
				} else {
					hideHub();
				}
			}
		};
		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), hubBackCallback);
		return v;
	}

	/**
	 * Close the selection menu (Hub), clears its contents, and disables the "Back" button handling.
	 */
	private void hideHub() {
		isDialogShowing = false;
		hubOverlay.setVisibility(View.GONE);
		hubOverlay.removeAllViews();
		if (hubBackCallback != null) hubBackCallback.setEnabled(false);
	}

	/**
	 * Load the list of brands from the JSON config and initializes the main selection menu.
	 */
	private void loadBrands() {
		isDialogShowing = true;
		if (hubBackCallback != null) hubBackCallback.setEnabled(true);
		updateLog("[REPO] Fetching brands...");
		executor.execute(() -> {
			try {
				URL url = new URL(REPO_RAW + "kernels.json");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				InputStream in = new BufferedInputStream(conn.getInputStream());
				byte[] buffer = new byte[1024];
				StringBuilder sb = new StringBuilder();
				int read;
				while ((read = in.read(buffer)) != -1) sb.append(new String(buffer, 0, read));
				String raw = sb.toString().trim();
				JSONObject json = new JSONObject(raw);
				JSONArray paths = json.getJSONArray("paths");
				TreeMap<String, List<String>> brandMap = new TreeMap<>();
				for (int i = 0; i < paths.length(); i++) {
					String p = paths.getString(i);
					String brand = p.split("/")[0];
					if (!brandMap.containsKey(brand)) brandMap.put(brand, new ArrayList<>());
					brandMap.get(brand).add(p);
				}
				List<String> brands = new ArrayList<>(brandMap.keySet());
				if (getActivity() == null) return;
				getActivity().runOnUiThread(() -> {
					updateHubUI("Select Brand", brands, (parent, view, which, id) -> {
						navStack.push(this::loadBrands);
						showModels(brands.get(which), brandMap.get(brands.get(which)));
					});
				});
			} catch (Exception e) {
				getActivity().runOnUiThread(() -> {
					updateLog("[ERROR] Fetch failed");
					hideHub();
				});
			}
		});
	}

	/**
	 * Display a list of models for the selected brand and sets up navigation to their kernels.
	 */
	private void showModels(String brand, List<String> paths) {
		TreeMap<String, String> models = new TreeMap<>();
		for (String p : paths) models.put(p.split("/")[1], p);
		List<String> modelNames = new ArrayList<>(models.keySet());
		getActivity().runOnUiThread(() -> {
			updateHubUI(brand, modelNames, (parent, view, which, id) -> {
				String model = modelNames.get(which);
				navStack.push(() -> showModels(brand, paths));
				fetchKernels(model, models.get(model), brand, paths);
			});
		});
	}

	/**
	 * Fetches a list of .zip kernels via API and displays them in the UI for selection.
	 */
	private void fetchKernels(String model, String path, String brand, List<String> brandPaths) {
		updateLog("[REPO] Listing kernels for " + model);
		executor.execute(() -> {
			try {
				URL url = new URL(REPO_API + path);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				InputStream in = new BufferedInputStream(conn.getInputStream());
				byte[] buffer = new byte[1024];
				StringBuilder sb = new StringBuilder();
				int read;
				while ((read = in.read(buffer)) != -1) sb.append(new String(buffer, 0, read));
				JSONArray files = new JSONArray(sb.toString().trim());
				List<String> zips = new ArrayList<>();
				for (int i = 0; i < files.length(); i++) {
					String name = files.getJSONObject(i).getString("name");
					if (name.endsWith(".zip")) zips.add(name);
				}
				getActivity().runOnUiThread(() -> {
					if (zips.isEmpty()) {
						Toast.makeText(getContext(), "No kernels found", Toast.LENGTH_SHORT).show();
						return;
					}
					updateHubUI(model.replace("_", " "), zips, (parent, view, which, id) -> {
						currentSelectedModel = model;
						hideHub();
						downloadKernel(path + "/" + zips.get(which));
					});
				});
			} catch (Exception e) {
				getActivity().runOnUiThread(() -> updateLog("[ERROR] API listing failed"));
			}
		});
	}

	/**
	 * Dynamic Hub UI implemented as an overlay.
	 * Positioned strictly at center using FrameLayout Gravity.
	 */
	private void updateHubUI(String title, List<String> items, android.widget.AdapterView.OnItemClickListener listener) {
		hubOverlay.removeAllViews();
		hubOverlay.setVisibility(View.VISIBLE);

		LinearLayout menuLayout = new LinearLayout(requireContext());
		menuLayout.setOrientation(LinearLayout.VERTICAL);
		menuLayout.setPadding(0, 10, 0, 10);
		// Style the menu container
		GradientDrawable border = new GradientDrawable();
		border.setColor(Color.parseColor("#1C1C1E"));
		border.setStroke(3, Color.parseColor("#38383A"));
		border.setCornerRadius(30);
		menuLayout.setBackground(border);
		// Center the layout in the middle of the screen
		FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
				(int) (getResources().getDisplayMetrics().widthPixels * 0.85),
				ViewGroup.LayoutParams.WRAP_CONTENT
		);
		menuParams.gravity = Gravity.CENTER;
		menuLayout.setLayoutParams(menuParams);
		TextView titleView = new TextView(requireContext());
		titleView.setText(title);
		titleView.setTextSize(18);
		titleView.setTextColor(Color.parseColor("#4CAF50")); // Green Title
		titleView.setGravity(Gravity.CENTER);
		titleView.setPadding(0, 35, 0, 30);
		menuLayout.addView(titleView);
		View divider = new View(requireContext());
		divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
		divider.setBackgroundColor(Color.parseColor("#38383A"));
		menuLayout.addView(divider);
		ListView listView = new ListView(requireContext());
		listView.setDivider(new ColorDrawable(Color.parseColor("#38383A")));
		listView.setDividerHeight(1);
		listView.setAdapter(new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, items) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				TextView text = view.findViewById(android.R.id.text1);
				text.setTextColor(Color.WHITE);
				text.setGravity(Gravity.CENTER);
				text.setPadding(0, 35, 0, 35);
				return view;
			}
		});
		listView.setOnItemClickListener(listener);
		// Dynamic height adjustment to prevent cutting off
		int screenHeight = getResources().getDisplayMetrics().heightPixels;
		int maxHeight = (int) (screenHeight * 0.6);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		if (items.size() > 6) {
			lp.height = maxHeight;
		}

		listView.setLayoutParams(lp);
		menuLayout.addView(listView);
		View dividerBottom = new View(requireContext());
		dividerBottom.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
		dividerBottom.setBackgroundColor(Color.parseColor("#38383A"));
		menuLayout.addView(dividerBottom);
		TextView btnBack = new TextView(requireContext());
		btnBack.setText("Back");
		btnBack.setTextColor(Color.parseColor("#FFB300")); // Mandarin Back Button
		btnBack.setTextSize(16);
		btnBack.setGravity(Gravity.CENTER);
		btnBack.setPadding(0, 30, 0, 30);
		TypedValue outValue = new TypedValue();
		requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
		btnBack.setBackgroundResource(outValue.resourceId);
		btnBack.setOnClickListener(v -> hubBackCallback.handleOnBackPressed());
		menuLayout.addView(btnBack);
		hubOverlay.addView(menuLayout);
	}

	/**
	 * Downloads the kernel in the background, saves it to /sdcard/nh_files/kernels
	 * and enables the flash button upon completion.
	 */
	private void downloadKernel(String path) {
		String name = path.substring(path.lastIndexOf("/") + 1);
		tvLog.setText("");
		updateLog("[DOWNLOAD] Starting: " + name);
		if (progressBar != null) {
			progressBar.setProgress(0);
			progressBar.setVisibility(View.VISIBLE);
		}
		executor.execute(() -> {
			try {
				URL url = new URL(REPO_RAW + path);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				int len = conn.getContentLength();
				File dir = new File(Environment.getExternalStorageDirectory(), "nh_files/kernels");
				if (!dir.exists()) dir.mkdirs();
				File file = new File(dir, name);
				InputStream is = new BufferedInputStream(conn.getInputStream());
				FileOutputStream os = new FileOutputStream(file);
				byte[] buf = new byte[8192];
				long total = 0;
				int count;
				while ((count = is.read(buf)) != -1) {
					total += count;
					if (len > 0) {
						int prog = (int) (total * 100 / len);
						getActivity().runOnUiThread(() -> progressBar.setProgress(prog));
					}
					os.write(buf, 0, count);
				}
				os.close(); is.close();
				getActivity().runOnUiThread(() -> {
					progressBar.setVisibility(View.GONE);
					selectedUri = Uri.fromFile(file);
					tvFilePath.setText(name);
					btnSelect.setVisibility(View.GONE);
					btnSupport.setVisibility(View.GONE);
					btnFlash.setVisibility(View.VISIBLE);
					btnCancel.setVisibility(View.VISIBLE);
					updateLog("[SUCCESS] Downloaded.");
				});
			} catch (Exception e) {
				getActivity().runOnUiThread(() -> {
					updateLog("[ERROR] Download failed");
					progressBar.setVisibility(View.GONE);
				});
			}
		});
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
					btnSupport.setVisibility(View.VISIBLE);
					btnSelect.setEnabled(true);
					btnSupport.setEnabled(true);
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

	@Override
	public void onDestroyView() {
		// Clean up overlay when fragment view is destroyed
		if (hubOverlay != null && getActivity() != null) {
			((ViewGroup) getActivity().findViewById(android.R.id.content)).removeView(hubOverlay);
		}
		super.onDestroyView();
	}
}
// End of AnyKernelFragment class.
