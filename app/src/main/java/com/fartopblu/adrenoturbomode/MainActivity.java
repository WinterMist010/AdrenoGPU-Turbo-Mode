package com.fartopblu.adrenoturbomode;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "Preferences";
    private static final String KEY_FIRST_RUN = "isFirstRun";
    private static final long FREQUENCY_REFRESH_MS = 1300L;
    private static final String[] GPU_MODEL_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/gpu_name",
            "/sys/class/kgsl/kgsl-3d0/gpu_id"
    };
    // A8XX vendor kernels commonly expose devfreq instead of the legacy gpuclk node.
    private static final String[] GPU_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/devfreq/kgsl-3d0/cur_freq"
    };
    private static final String[] GPU_AVAILABLE_FREQUENCIES_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "/sys/class/devfreq/kgsl-3d0/available_frequencies"
    };
    private static final String[] GPU_MAX_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/devfreq/kgsl-3d0/max_freq"
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
        String gpuModel = readFirstAvailable(GPU_MODEL_PATHS);
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

        try {
            long hertz = Long.parseLong(value.trim().split("\\s+")[0]);
            gpuFrequencyText.setText(getString(R.string.frequency, hertz / 1_000_000L));
        } catch (NumberFormatException ignored) {
            gpuFrequencyText.setText(R.string.frequency_unavailable);
        }
    }

    private void updateDriverLimit() {
        String availableFrequencies = readFirstAvailableContent(GPU_AVAILABLE_FREQUENCIES_PATHS);
        Long maxFrequency = parseFrequency(readFirstAvailable(GPU_MAX_FREQUENCY_PATHS));

        if (availableFrequencies == null && maxFrequency == null) {
            driverLimitText.setText(R.string.driver_limit_unavailable);
            return;
        }

        String availableFrequenciesMHz = formatFrequencies(availableFrequencies);
        if (maxFrequency == null) {
            driverLimitText.setText(getString(R.string.driver_limit_available_only, availableFrequenciesMHz));
        } else if (availableFrequenciesMHz == null) {
            driverLimitText.setText(getString(R.string.driver_limit_max_only, maxFrequency / 1_000_000L));
        } else {
            driverLimitText.setText(getString(
                    R.string.driver_limit,
                    maxFrequency / 1_000_000L,
                    availableFrequenciesMHz));
        }
    }

    private static Long parseFrequency(String value) {
        if (value == null) {
            return null;
        }

        try {
            return Long.parseLong(value.trim().split("\\s+")[0]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formatFrequencies(String values) {
        if (values == null) {
            return null;
        }

        StringBuilder formatted = new StringBuilder();
        for (String value : values.trim().split("\\s+")) {
            Long frequency = parseFrequency(value);
            if (frequency == null) {
                continue;
            }
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            formatted.append(frequency / 1_000_000L);
        }
        return formatted.length() == 0 ? null : formatted.toString();
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
}
