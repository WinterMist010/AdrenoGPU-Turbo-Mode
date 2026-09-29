package com.wintermist.adrenoperformancemanager;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
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

    // Expanded GPU model sysfs paths
    private static final String[] GPU_MODEL_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/gpu_name",
            "/sys/class/kgsl/kgsl-3d0/gpu_id",
            "/sys/class/devfreq/3d00000.gpu/gpu_model",
            "/sys/class/devfreq/kgsl-3d0/gpu_model"
    };

    // Expanded frequency reading paths
    private static final String[] GPU_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/clock_mhz",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/devfreq/kgsl-3d0/cur_freq",
            "/sys/class/devfreq/3d00000.gpu/cur_freq",
            "/sys/class/devfreq/msm-adreno-tz/cur_freq"
    };

    // Available frequency paths
    private static final String[] GPU_AVAILABLE_FREQUENCIES_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "/sys/class/devfreq/kgsl-3d0/available_frequencies",
            "/sys/class/devfreq/3d00000.gpu/available_frequencies",
            "/sys/class/devfreq/msm-adreno-tz/available_frequencies",
            "/sys/class/kgsl/kgsl-3d0/freq_table_mhz",
            "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"
    };

    // Max frequency paths
    private static final String[] GPU_MAX_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/devfreq/kgsl-3d0/max_freq",
            "/sys/class/devfreq/3d00000.gpu/max_freq",
            "/sys/class/devfreq/msm-adreno-tz/max_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
    };

    // Min frequency paths
    private static final String[] GPU_MIN_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "/sys/class/devfreq/kgsl-3d0/min_freq",
            "/sys/class/devfreq/3d00000.gpu/min_freq",
            "/sys/class/devfreq/msm-adreno-tz/min_freq",
            "/sys/class/kgsl/kgsl-3d0/min_gpuclk"
    };

    // GPU temperature paths
    private static final String[] GPU_TEMP_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gputemperature",
            "/sys/class/kgsl/kgsl-3d0/temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp"
    };

    // GPU governor paths
    private static final String[] GPU_GOVERNOR_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/devfreq/kgsl-3d0/governor",
            "/sys/class/devfreq/3d00000.gpu/governor",
            "/sys/class/devfreq/msm-adreno-tz/governor"
    };

    private static final String[] GPU_AVAILABLE_GOVERNORS_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_governors",
            "/sys/class/devfreq/kgsl-3d0/available_governors",
            "/sys/class/devfreq/3d00000.gpu/available_governors",
            "/sys/class/devfreq/msm-adreno-tz/available_governors"
    };

    // KGSL Power Level paths
    private static final String[] GPU_PWRLEVEL_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/pwrlevel",
            "/sys/class/kgsl/kgsl-3d0/num_pwrlevels"
    };

    static {
        System.loadLibrary("adrenoturboswitch");
    }

    private native int EnableTurbo();
    private native int DisableTurbo();

    private final Handler frequencyHandler = new Handler(Looper.getMainLooper());
    private TextView gpuFrequencyText;
    private TextView gpuTempText;
    private TextView driverLimitText;
    private TextView turboStatusText;
    private TextView governorStatusText;
    private TextView pwrlevelStatusText;
    private Spinner governorSpinner;
    private Spinner minFreqSpinner;
    private Spinner maxFreqSpinner;
    private MaterialButton enableButton;
    private MaterialButton disableButton;
    private MaterialButton applyGovButton;
    private MaterialButton applyFreqButton;
    private boolean frequencyAvailable;
    private boolean turboEnabled;

    private final Runnable frequencyUpdater = new Runnable() {
        @Override
        public void run() {
            updateFrequency();
            updateTemperature();
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
        gpuTempText = findViewById(R.id.textGpuTemp);
        driverLimitText = findViewById(R.id.textDriverLimit);
        turboStatusText = findViewById(R.id.textTurboStatus);
        governorStatusText = findViewById(R.id.textGovernorStatus);
        pwrlevelStatusText = findViewById(R.id.textPwrlevelStatus);
        governorSpinner = findViewById(R.id.spinnerGovernor);
        minFreqSpinner = findViewById(R.id.spinnerMinFreq);
        maxFreqSpinner = findViewById(R.id.spinnerMaxFreq);
        applyGovButton = findViewById(R.id.button_apply_governor);
        applyFreqButton = findViewById(R.id.button_apply_freq);

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
        updateTemperature();
        setupGovernorControls();
        setupFrequencyControls();
        updatePowerLevel();

        findViewById(R.id.button_about).setOnClickListener(view -> showAboutDialog());
        enableButton.setOnClickListener(view -> changeTurbo(true));
        disableButton.setOnClickListener(view -> changeTurbo(false));
        applyGovButton.setOnClickListener(view -> applyGovernorSelection());
        applyFreqButton.setOnClickListener(view -> applyFreqLimitsSelection());

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

    private void updateTemperature() {
        String value = readFirstAvailable(GPU_TEMP_PATHS);
        if (value == null) {
            gpuTempText.setText(R.string.gpu_temp_unavailable);
            return;
        }

        try {
            long rawTemp = Long.parseLong(value.trim().split("\\s+")[0]);
            double tempC = rawTemp > 1000 ? rawTemp / 1000.0 : rawTemp;
            gpuTempText.setText(getString(R.string.gpu_temp, String.format("%.1f °C", tempC)));
        } catch (NumberFormatException ignored) {
            gpuTempText.setText(R.string.gpu_temp_unavailable);
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

    private void setupGovernorControls() {
        String currentGov = readFirstAvailable(GPU_GOVERNOR_PATHS);
        String availableGovs = readFirstAvailableContent(GPU_AVAILABLE_GOVERNORS_PATHS);

        if (currentGov == null && availableGovs == null) {
            governorStatusText.setText(R.string.governor_unavailable);
            applyGovButton.setEnabled(false);
            governorSpinner.setEnabled(false);
            return;
        }

        governorStatusText.setText(getString(R.string.governor_current, currentGov != null ? currentGov : "Unknown"));

        List<String> govList = new ArrayList<>();
        if (availableGovs != null) {
            for (String g : availableGovs.split("\\s+")) {
                if (!g.trim().isEmpty() && !govList.contains(g.trim())) {
                    govList.add(g.trim());
                }
            }
        } else if (currentGov != null) {
            govList.add(currentGov);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, govList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        governorSpinner.setAdapter(adapter);

        if (currentGov != null) {
            int pos = govList.indexOf(currentGov);
            if (pos >= 0) {
                governorSpinner.setSelection(pos);
            }
        }
    }

    private void setupFrequencyControls() {
        String availableFrequenciesContent = readFirstAvailableContent(GPU_AVAILABLE_FREQUENCIES_PATHS);
        List<Long> freqs = getFrequencyList(availableFrequenciesContent);

        if (freqs.isEmpty()) {
            minFreqSpinner.setEnabled(false);
            maxFreqSpinner.setEnabled(false);
            applyFreqButton.setEnabled(false);
            return;
        }

        List<String> freqLabels = new ArrayList<>();
        for (Long f : freqs) {
            freqLabels.add(f + " MHz");
        }

        ArrayAdapter<String> minAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, freqLabels);
        minAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        minFreqSpinner.setAdapter(minAdapter);

        ArrayAdapter<String> maxAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, freqLabels);
        maxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maxFreqSpinner.setAdapter(maxAdapter);

        Long currentMin = parseToMHz(readFirstAvailable(GPU_MIN_FREQUENCY_PATHS));
        Long currentMax = parseToMHz(readFirstAvailable(GPU_MAX_FREQUENCY_PATHS));

        if (currentMin != null && freqs.contains(currentMin)) {
            minFreqSpinner.setSelection(freqs.indexOf(currentMin));
        }
        if (currentMax != null && freqs.contains(currentMax)) {
            maxFreqSpinner.setSelection(freqs.indexOf(currentMax));
        } else {
            maxFreqSpinner.setSelection(freqs.size() - 1);
        }
    }

    private void updatePowerLevel() {
        String pwrlevel = readFirstAvailable(GPU_PWRLEVEL_PATHS);
        if (pwrlevel != null) {
            pwrlevelStatusText.setText(getString(R.string.power_level, pwrlevel));
        } else {
            pwrlevelStatusText.setText(R.string.power_level_unavailable);
        }
    }

    private void applyGovernorSelection() {
        Object selected = governorSpinner.getSelectedItem();
        if (selected == null) return;
        String govName = selected.toString();
        boolean success = writeFirstAvailable(GPU_GOVERNOR_PATHS, govName);
        Toast.makeText(this, success ? R.string.setting_updated : R.string.setting_failed, Toast.LENGTH_SHORT).show();
        setupGovernorControls();
    }

    private void applyFreqLimitsSelection() {
        Object minSel = minFreqSpinner.getSelectedItem();
        Object maxSel = maxFreqSpinner.getSelectedItem();
        if (minSel == null || maxSel == null) return;

        long minMhz = Long.parseLong(minSel.toString().replace(" MHz", "").trim());
        long maxMhz = Long.parseLong(maxSel.toString().replace(" MHz", "").trim());

        // Convert MHz back to Hz for devfreq nodes
        long minHz = minMhz * 1_000_000L;
        long maxHz = maxMhz * 1_000_000L;

        boolean minSuccess = writeFirstAvailable(GPU_MIN_FREQUENCY_PATHS, String.valueOf(minHz));
        boolean maxSuccess = writeFirstAvailable(GPU_MAX_FREQUENCY_PATHS, String.valueOf(maxHz));

        Toast.makeText(this, (minSuccess || maxSuccess) ? R.string.setting_updated : R.string.setting_failed, Toast.LENGTH_SHORT).show();
        updateDriverLimit();
    }

    private String detectGpuModel() {
        String sysfsModel = readFirstAvailable(GPU_MODEL_PATHS);
        if (sysfsModel != null && !sysfsModel.isEmpty()) {
            return sysfsModel;
        }

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

            if (rawValue >= 100_000_000L) {
                return rawValue / 1_000_000L;
            }
            if (rawValue >= 100_000L) {
                return rawValue / 1_000L;
            }
            return rawValue;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static List<Long> getFrequencyList(String values) {
        if (values == null || values.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> mhzList = new ArrayList<>();
        String[] tokens = values.trim().split("\\s+");
        for (String token : tokens) {
            Long mhz = parseToMHz(token);
            if (mhz != null && mhz > 0 && !mhzList.contains(mhz)) {
                mhzList.add(mhz);
            }
        }
        Collections.sort(mhzList);
        return mhzList;
    }

    public static String formatFrequenciesToMHz(String values) {
        List<Long> mhzList = getFrequencyList(values);
        if (mhzList.isEmpty()) {
            return null;
        }

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
            }
        }
        return null;
    }

    private static boolean writeFirstAvailable(String[] paths, String value) {
        for (String path : paths) {
            File file = new File(path);
            if (file.exists() && file.canWrite()) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(value.getBytes());
                    fos.flush();
                    return true;
                } catch (IOException ignored) {
                }
            }
        }
        return false;
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
