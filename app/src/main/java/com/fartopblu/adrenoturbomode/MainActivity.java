package com.fartopblu.adrenoturbomode;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "Preferences";
    private static final String KEY_FIRST_RUN = "isFirstRun";
    private static final long FREQUENCY_REFRESH_MS = 1300L;

    // Expanded GPU model sysfs paths supporting legacy Adreno, A6XX, A7XX, and A8XX / 8-series
    private static final String[] GPU_MODEL_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/gpu_name",
            "/sys/class/kgsl/kgsl-3d0/gpu_id",
            "/sys/class/devfreq/3d00000.gpu/gpu_model",
            "/sys/class/devfreq/kgsl-3d0/gpu_model"
    };

    // Expanded frequency reading paths across kgsl, devfreq, msm-adreno-tz, and vendor nodes
    private static final String[] GPU_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/clock_mhz",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/devfreq/kgsl-3d0/cur_freq",
            "/sys/class/devfreq/3d00000.gpu/cur_freq",
            "/sys/class/devfreq/msm-adreno-tz/cur_freq"
    };

    // Expanded available frequency paths across kgsl, devfreq, and vendor nodes
    private static final String[] GPU_AVAILABLE_FREQUENCIES_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "/sys/class/devfreq/kgsl-3d0/available_frequencies",
            "/sys/class/devfreq/3d00000.gpu/available_frequencies",
            "/sys/class/devfreq/msm-adreno-tz/available_frequencies",
            "/sys/class/kgsl/kgsl-3d0/freq_table_mhz",
            "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"
    };

    // Expanded max frequency paths
    private static final String[] GPU_MAX_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/devfreq/kgsl-3d0/max_freq",
            "/sys/class/devfreq/3d00000.gpu/max_freq",
            "/sys/class/devfreq/msm-adreno-tz/max_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
    };

    static {
        System.loadLibrary("adrenoturboswitch");
    }

    private native int EnableTurbo();
    private native int DisableTurbo();

    private final Handler frequencyHandler = new Handler(Looper.getMainLooper());
    private TextView gpuFrequencyText;
    private TextView driverLimitText;
    private TextView turboStatusText;
    private MaterialButton enableButton;
    private MaterialButton disableButton;
    private boolean frequencyAvailable;
    private boolean turboEnabled;

    private final Runnable frequencyUpdater = new Runnable() {
        @Override
        public void run() {
            updateFrequency();
            if (frequencyAvailable) {
                frequencyHandler.postDelayed(this, FREQUENCY_REFRESH_MS);
            }
        }
    };

    private void showAboutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.about, findViewById(android.R.id.content), false);
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainactivity);

        enableButton = findViewById(R.id.button_enable);
        disableButton = findViewById(R.id.button_disable);
        gpuFrequencyText = findViewById(R.id.textGpuFreq);
        driverLimitText = findViewById(R.id.textDriverLimit);
        turboStatusText = findViewById(R.id.textTurboStatus);

        TextView gpuModelText = findViewById(R.id.textGpuModel);
        String gpuModel = detectGpuModel();
        gpuModelText.setText(gpuModel == null
                ? getString(R.string.gpu_not_detected)
                : getString(R.string.gpu_model, gpuModel));

        frequencyAvailable = firstExistingPath(GPU_FREQUENCY_PATHS) != null;
        if (!frequencyAvailable) {
            gpuFrequencyText.setText(R.string.frequency_unavailable);
        }
        updateDriverLimit();

        findViewById(R.id.button_about).setOnClickListener(view -> showAboutDialog());
        enableButton.setOnClickListener(view -> changeTurbo(true));
        disableButton.setOnClickListener(view -> changeTurbo(false));
        updateButtons();

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (preferences.getBoolean(KEY_FIRST_RUN, true)) {
            showAboutDialog();
            preferences.edit().putBoolean(KEY_FIRST_RUN, false).apply();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (frequencyAvailable) {
            frequencyHandler.post(frequencyUpdater);
        }
    }

    @Override
    protected void onStop() {
        frequencyHandler.removeCallbacks(frequencyUpdater);
        super.onStop();
    }

    private void changeTurbo(boolean enable) {
        int result = enable ? EnableTurbo() : DisableTurbo();
        if (result == 0) {
            turboEnabled = enable;
            turboStatusText.setText(enable ? R.string.turbo_enabled : R.string.turbo_disabled);
            updateButtons();
            return;
        }

        if (result == 1) {
            turboStatusText.setText(R.string.turbo_kgsl_unavailable);
        } else if (result == 2) {
            turboStatusText.setText(R.string.turbo_not_supported);
        } else {
            turboStatusText.setText(R.string.turbo_failed);
        }
    }

    private void updateButtons() {
        enableButton.setEnabled(!turboEnabled);
        disableButton.setEnabled(turboEnabled);
    }

    private void updateFrequency() {
        String value = readFirstAvailable(GPU_FREQUENCY_PATHS);
        if (value == null) {
            frequencyAvailable = false;
            gpuFrequencyText.setText(R.string.frequency_unavailable);
            return;
        }

        Long mhz = parseToMHz(value);
        if (mhz != null && mhz > 0) {
            gpuFrequencyText.setText(getString(R.string.frequency, mhz));
        } else {
            gpuFrequencyText.setText(R.string.frequency_unavailable);
        }
    }

    private void updateDriverLimit() {
        String availableFrequenciesContent = readFirstAvailableContent(GPU_AVAILABLE_FREQUENCIES_PATHS);
        Long maxFrequencyMhz = parseToMHz(readFirstAvailable(GPU_MAX_FREQUENCY_PATHS));

        String availableFrequenciesMHzStr = formatFrequenciesToMHz(availableFrequenciesContent);

        if (availableFrequenciesMHzStr == null && maxFrequencyMhz == null) {
            driverLimitText.setText(R.string.driver_limit_unavailable);
            return;
        }

        if (maxFrequencyMhz == null) {
            driverLimitText.setText(getString(R.string.driver_limit_available_only, availableFrequenciesMHzStr));
        } else if (availableFrequenciesMHzStr == null) {
            driverLimitText.setText(getString(R.string.driver_limit_max_only, maxFrequencyMhz));
        } else {
            driverLimitText.setText(getString(
                    R.string.driver_limit,
                    maxFrequencyMhz,
                    availableFrequenciesMHzStr));
        }
    }

    /**
     * Tries sysfs nodes first, and falls back to system properties if necessary.
     */
    private String detectGpuModel() {
        String sysfsModel = readFirstAvailable(GPU_MODEL_PATHS);
        if (sysfsModel != null && !sysfsModel.isEmpty()) {
            return sysfsModel;
        }

        // Fallback to system properties if sysfs is inaccessible
        String egl = getSystemProperty("ro.hardware.egl");
        if (egl != null && egl.toLowerCase().contains("adreno")) {
            return egl;
        }

        String soc = getSystemProperty("ro.soc.model");
        if (soc != null && !soc.isEmpty()) {
            return soc;
        }

        String platform = getSystemProperty("ro.board.platform");
        if (platform != null && !platform.isEmpty()) {
            return platform;
        }

        return null;
    }

    /**
     * Smart frequency parser that detects whether the raw value is in Hz, kHz, or MHz
     * and converts it to MHz.
     */
    public static Long parseToMHz(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            String firstToken = value.trim().split("\\s+")[0];
            long rawValue = Long.parseLong(firstToken);
            if (rawValue <= 0) {
                return null;
            }

            // Hz range: e.g. 800,000,000 Hz -> 800 MHz
            if (rawValue >= 100_000_000L) {
                return rawValue / 1_000_000L;
            }
            // kHz range: e.g. 800,000 kHz -> 800 MHz
            if (rawValue >= 100_000L) {
                return rawValue / 1_000L;
            }
            // MHz range: e.g. 800 MHz
            return rawValue;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Parses single-line or multi-line space-separated/newline-separated frequency lists,
     * converts all valid values to MHz, sorts them numerically, and formats as a comma-separated string.
     */
    public static String formatFrequenciesToMHz(String values) {
        if (values == null || values.trim().isEmpty()) {
            return null;
        }

        List<Long> mhzList = new ArrayList<>();
        String[] tokens = values.trim().split("\\s+");
        for (String token : tokens) {
            Long mhz = parseToMHz(token);
            if (mhz != null && mhz > 0 && !mhzList.contains(mhz)) {
                mhzList.add(mhz);
            }
        }

        if (mhzList.isEmpty()) {
            return null;
        }

        Collections.sort(mhzList);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mhzList.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(mhzList.get(i));
        }
        return sb.toString();
    }

    private static String firstExistingPath(String[] paths) {
        for (String path : paths) {
            if (new File(path).isFile()) {
                return path;
            }
        }
        return null;
    }

    private static String readFirstAvailableContent(String[] paths) {
        for (String path : paths) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                StringBuilder value = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (value.length() > 0) {
                        value.append(' ');
                    }
                    value.append(line.trim());
                }
                if (value.toString().trim().length() > 0) {
                    return value.toString().trim();
                }
            } catch (IOException ignored) {
                // Vendor kernels expose different optional sysfs nodes.
            }
        }
        return null;
    }

    private static String readFirstAvailable(String[] paths) {
        for (String path : paths) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String value = reader.readLine();
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            } catch (IOException ignored) {
                // Vendor kernels expose different optional sysfs nodes.
            }
        }
        return null;
    }

    private static String getSystemProperty(String propName) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method getMethod = clazz.getMethod("get", String.class);
            return (String) getMethod.invoke(null, propName);
        } catch (Exception ignored) {
            return null;
        }
    }
}
