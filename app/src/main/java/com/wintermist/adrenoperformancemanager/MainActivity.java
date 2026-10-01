package com.wintermist.adrenoperformancemanager;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.wintermist.adrenoperformancemanager.capability.CapabilityReporter;
import com.wintermist.adrenoperformancemanager.feature.ExperimentalFeatures;
import com.wintermist.adrenoperformancemanager.feature.FeatureManager;
import com.wintermist.adrenoperformancemanager.feature.FeatureModule;
import com.wintermist.adrenoperformancemanager.feature.FixedPerformanceModeFeature;
import com.wintermist.adrenoperformancemanager.feature.GameModeFeature;
import com.wintermist.adrenoperformancemanager.feature.RootKgslFeature;
import com.wintermist.adrenoperformancemanager.monitoring.GpuMonitor;
import com.wintermist.adrenoperformancemanager.privilege.NoneBackend;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import com.wintermist.adrenoperformancemanager.privilege.RootBackend;
import com.wintermist.adrenoperformancemanager.privilege.ShizukuBackend;

import rikka.shizuku.Shizuku;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "Preferences";
    private static final String KEY_FIRST_RUN = "isFirstRun";
    private static final long FREQUENCY_REFRESH_MS = 1300L;
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    // Sysfs paths
    private static final String[] GPU_MODEL_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/gpu_name",
            "/sys/class/kgsl/kgsl-3d0/gpu_id",
            "/sys/class/devfreq/3d00000.gpu/gpu_model",
            "/sys/class/devfreq/kgsl-3d0/gpu_model"
    };

    private static final String[] GPU_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/clock_mhz",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/devfreq/kgsl-3d0/cur_freq",
            "/sys/class/devfreq/3d00000.gpu/cur_freq",
            "/sys/class/devfreq/msm-adreno-tz/cur_freq"
    };

    private static final String[] GPU_AVAILABLE_FREQUENCIES_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "/sys/class/devfreq/kgsl-3d0/available_frequencies",
            "/sys/class/devfreq/3d00000.gpu/available_frequencies",
            "/sys/class/devfreq/msm-adreno-tz/available_frequencies",
            "/sys/class/kgsl/kgsl-3d0/freq_table_mhz",
            "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"
    };

    private static final String[] GPU_MAX_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/devfreq/kgsl-3d0/max_freq",
            "/sys/class/devfreq/3d00000.gpu/max_freq",
            "/sys/class/devfreq/msm-adreno-tz/max_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
    };

    private static final String[] GPU_MIN_FREQUENCY_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "/sys/class/devfreq/kgsl-3d0/min_freq",
            "/sys/class/devfreq/3d00000.gpu/min_freq",
            "/sys/class/devfreq/msm-adreno-tz/min_freq",
            "/sys/class/kgsl/kgsl-3d0/min_gpuclk"
    };

    private static final String[] GPU_TEMP_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gputemperature",
            "/sys/class/kgsl/kgsl-3d0/temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp"
    };

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

    private static final String[] GPU_PWRLEVEL_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/pwrlevel",
            "/sys/class/kgsl/kgsl-3d0/num_pwrlevels"
    };

    static {
        try {
            System.loadLibrary("adrenoturboswitch");
        } catch (UnsatisfiedLinkError ignored) {
            // Native library not available in JVM unit test environment
        }
    }

    private native int EnableTurbo();
    private native int DisableTurbo();
    private native int SetPowerConstraint(int type, int level);

    private static final int KGSL_CONSTRAINT_NONE = 0;
    private static final int KGSL_CONSTRAINT_PWRLEVEL = 1;
    private static final int KGSL_CONSTRAINT_PWR_MIN = 0;
    private static final int KGSL_CONSTRAINT_PWR_MAX = 1;

    private final Handler frequencyHandler = new Handler(Looper.getMainLooper());
    private TextView gpuFrequencyText;
    private TextView gpuTempText;
    private TextView driverLimitText;
    private TextView turboStatusText;
    private TextView governorStatusText;
    private TextView pwrlevelStatusText;
    private TextView shizukuStatusText;
    private TextView activeTierText;
    private TextView capabilityDetailsText;
    private TextView metricsBeforeAfterText;
    private MaterialButton requestShizukuPermissionButton;
    private MaterialButton restoreDefaultsButton;
    private MaterialButton exportReportButton;
    private MaterialButton applyGameModeButton;
    private MaterialButton expThermalButton;
    private MaterialButton expSkiavkButton;
    private EditText gamePackageEdit;
    private Spinner gameModeSpinner;
    private Spinner gameFpsSpinner;
    private Spinner pwrConstraintSpinner;
    private Spinner governorSpinner;
    private Spinner minFreqSpinner;
    private Spinner maxFreqSpinner;
    private MaterialButton enableButton;
    private MaterialButton disableButton;
    private MaterialButton applyPwrConstraintButton;
    private MaterialButton applyGovButton;
    private MaterialButton applyFreqButton;
    private boolean frequencyAvailable;
    private boolean turboEnabled;

    // Feature Modules & Privilege Backends
    private PrivilegeBackend activeBackend;
    private RootBackend rootBackend;
    private ShizukuBackend shizukuBackend;
    private NoneBackend noneBackend;
    private FeatureManager featureManager;
    private CapabilityReporter capabilityReporter;

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

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::updateShizukuStatus;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::updateShizukuStatus;
    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            updateShizukuStatus();
        }
    };

    private void showAboutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.about, findViewById(android.R.id.content), false);
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void detectActiveBackend() {
        if (rootBackend != null && rootBackend.isAvailable()) {
            activeBackend = rootBackend;
        } else if (shizukuBackend != null && shizukuBackend.isAvailable()) {
            activeBackend = shizukuBackend;
        } else {
            activeBackend = noneBackend;
        }

        if (activeTierText != null) {
            activeTierText.setText("Active Tier: " + activeBackend.getTier().getDescription() + " (UID " + activeBackend.getUid() + ")");
        }
        updateCapabilityScreen();
    }

    private void updateCapabilityScreen() {
        if (capabilityDetailsText == null) return;
        StringBuilder sb = new StringBuilder();
        for (FeatureModule feature : featureManager.getAllFeatures()) {
            boolean supported = feature.isSupported(activeBackend);
            sb.append("• ").append(feature.name()).append(": ")
                    .append(supported ? "AVAILABLE" : "LOCKED")
                    .append(" (Requires ").append(feature.requiredPrivilege().name()).append(")")
                    .append("\n");
        }
        capabilityDetailsText.setText(sb.toString().trim());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainactivity);

        rootBackend = new RootBackend();
        shizukuBackend = new ShizukuBackend(this);
        noneBackend = new NoneBackend();

        featureManager = new FeatureManager(this);
        featureManager.registerFeature(new GameModeFeature());
        featureManager.registerFeature(new FixedPerformanceModeFeature());
        featureManager.registerFeature(new RootKgslFeature());
        featureManager.registerFeature(new ExperimentalFeatures.ThermalOverrideFeature());
        featureManager.registerFeature(new ExperimentalFeatures.SkiaVkRendererFeature());
        featureManager.registerFeature(new ExperimentalFeatures.PerAppAngleFeature());

        capabilityReporter = new CapabilityReporter();

        enableButton = findViewById(R.id.button_enable);
        disableButton = findViewById(R.id.button_disable);
        gpuFrequencyText = findViewById(R.id.textGpuFreq);
        gpuTempText = findViewById(R.id.textGpuTemp);
        driverLimitText = findViewById(R.id.textDriverLimit);
        turboStatusText = findViewById(R.id.textTurboStatus);
        governorStatusText = findViewById(R.id.textGovernorStatus);
        pwrlevelStatusText = findViewById(R.id.textPwrlevelStatus);
        pwrConstraintSpinner = findViewById(R.id.spinnerPwrConstraint);
        governorSpinner = findViewById(R.id.spinnerGovernor);
        minFreqSpinner = findViewById(R.id.spinnerMinFreq);
        maxFreqSpinner = findViewById(R.id.spinnerMaxFreq);
        applyPwrConstraintButton = findViewById(R.id.button_apply_pwr_constraint);
        applyGovButton = findViewById(R.id.button_apply_governor);
        applyFreqButton = findViewById(R.id.button_apply_freq);
        shizukuStatusText = findViewById(R.id.textShizukuStatus);
        requestShizukuPermissionButton = findViewById(R.id.button_shizuku_permission);
        activeTierText = findViewById(R.id.textActiveTier);
        restoreDefaultsButton = findViewById(R.id.button_restore_defaults);
        capabilityDetailsText = findViewById(R.id.textCapabilityDetails);
        exportReportButton = findViewById(R.id.button_export_report);
        gamePackageEdit = findViewById(R.id.edit_game_package);
        gameModeSpinner = findViewById(R.id.spinner_game_mode);
        gameFpsSpinner = findViewById(R.id.spinner_game_fps);
        applyGameModeButton = findViewById(R.id.button_apply_game_mode);
        metricsBeforeAfterText = findViewById(R.id.textMetricsBeforeAfter);
        expThermalButton = findViewById(R.id.button_exp_thermal);
        expSkiavkButton = findViewById(R.id.button_exp_skiavk);

        detectActiveBackend();

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
        setupPwrConstraintControls();
        setupGovernorControls();
        setupFrequencyControls();
        setupGameModeControls();
        updatePowerLevel();

        findViewById(R.id.button_about).setOnClickListener(view -> showAboutDialog());
        enableButton.setOnClickListener(view -> changeTurbo(true));
        disableButton.setOnClickListener(view -> changeTurbo(false));
        applyPwrConstraintButton.setOnClickListener(view -> applyPwrConstraintSelection());
        applyGovButton.setOnClickListener(view -> applyGovernorSelection());
        applyFreqButton.setOnClickListener(view -> applyFreqLimitsSelection());
        requestShizukuPermissionButton.setOnClickListener(view -> requestShizukuPermission());

        restoreDefaultsButton.setOnClickListener(view -> {
            boolean ok = featureManager.rollbackAll(activeBackend);
            Toast.makeText(this, ok ? "Defaults restored" : "Restore failed", Toast.LENGTH_SHORT).show();
            updateCapabilityScreen();
        });

        exportReportButton.setOnClickListener(view -> {
            String report = capabilityReporter.generateReport(activeBackend, detectGpuModel());
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Capability Diagnostic Report")
                    .setMessage(report)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        applyGameModeButton.setOnClickListener(view -> applyGameModeSelection());

        expThermalButton.setOnClickListener(view -> {
            boolean ok = featureManager.applyFeature("exp_thermal_override", activeBackend, "1");
            Toast.makeText(this, ok ? "Thermal status override applied (unverified)" : "Unsupported or failed", Toast.LENGTH_SHORT).show();
        });

        expSkiavkButton.setOnClickListener(view -> {
            boolean ok = featureManager.applyFeature("exp_skiavk_renderer", activeBackend, "skiavk");
            Toast.makeText(this, ok ? "SkiaVK renderer applied (unverified)" : "Unsupported or failed", Toast.LENGTH_SHORT).show();
        });

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener);

        updateButtons();
        updateShizukuStatus();

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (preferences.getBoolean(KEY_FIRST_RUN, true)) {
            showAboutDialog();
            preferences.edit().putBoolean(KEY_FIRST_RUN, false).apply();
        }
    }

    private void setupGameModeControls() {
        List<String> modes = new ArrayList<>();
        modes.add("Mode 2: Performance");
        modes.add("Mode 3: Battery");

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gameModeSpinner.setAdapter(modeAdapter);

        List<String> fpsList = new ArrayList<>();
        fpsList.add("30 FPS");
        fpsList.add("60 FPS");
        fpsList.add("90 FPS");
        fpsList.add("120 FPS");
        fpsList.add("144 FPS");

        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fpsList);
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gameFpsSpinner.setAdapter(fpsAdapter);
    }

    private void applyGameModeSelection() {
        String pkg = gamePackageEdit.getText().toString().trim();
        if (pkg.isEmpty()) {
            Toast.makeText(this, "Enter a valid package name", Toast.LENGTH_SHORT).show();
            return;
        }

        int mode = gameModeSpinner.getSelectedItemPosition() == 1 ? 3 : 2;
        String fpsStr = gameFpsSpinner.getSelectedItem().toString().replace(" FPS", "").trim();

        String config = pkg + ":" + mode + ":" + fpsStr + ":1.0:false";
        boolean ok = featureManager.applyFeature("game_mode_profile", activeBackend, config);
        Toast.makeText(this, ok ? "Game profile applied! Restart game to take effect." : "Failed to apply profile (requires Shizuku/Root)", Toast.LENGTH_LONG).show();

        // Update metrics text
        double temp = GpuMonitor.readGpuTemperature(activeBackend);
        metricsBeforeAfterText.setText(String.format(Locale.US, "p95 Frame Time: -- ms | GPU Temp: %.1f °C", temp > 0 ? temp : 0.0));
    }

    @Override
    protected void onStart() {
        super.onStart();
        detectActiveBackend();
        if (frequencyAvailable) {
            frequencyHandler.post(frequencyUpdater);
        }
    }

    @Override
    protected void onStop() {
        frequencyHandler.removeCallbacks(frequencyUpdater);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener);
        super.onDestroy();
    }

    private void updateShizukuStatus() {
        detectActiveBackend();
        if (!Shizuku.pingBinder()) {
            shizukuStatusText.setText(R.string.shizuku_status_not_running);
            requestShizukuPermissionButton.setEnabled(false);
            return;
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            shizukuStatusText.setText(R.string.shizuku_status_authorized);
            requestShizukuPermissionButton.setEnabled(false);
            shizukuBackend.bindService();
        } else {
            shizukuStatusText.setText(R.string.shizuku_status_permission_denied);
            requestShizukuPermissionButton.setEnabled(true);
        }
    }

    private void requestShizukuPermission() {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            } else {
                updateShizukuStatus();
            }
        } else {
            Toast.makeText(this, R.string.shizuku_status_not_running, Toast.LENGTH_SHORT).show();
            updateShizukuStatus();
        }
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
        double temp = GpuMonitor.readGpuTemperature(activeBackend);
        if (temp > 0) {
            gpuTempText.setText(getString(R.string.gpu_temp, String.format(Locale.US, "%.1f °C", temp)));
        } else {
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

    private void setupPwrConstraintControls() {
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.pwr_constraint_default));
        options.add(getString(R.string.pwr_constraint_max));
        options.add(getString(R.string.pwr_constraint_min));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pwrConstraintSpinner.setAdapter(adapter);
    }

    private void applyPwrConstraintSelection() {
        int pos = pwrConstraintSpinner.getSelectedItemPosition();
        int type;
        int level = 0;

        if (pos == 1) { // Max performance
            type = KGSL_CONSTRAINT_PWRLEVEL;
            level = KGSL_CONSTRAINT_PWR_MAX;
        } else if (pos == 2) { // Power saver
            type = KGSL_CONSTRAINT_PWRLEVEL;
            level = KGSL_CONSTRAINT_PWR_MIN;
        } else { // Default / Reset
            type = KGSL_CONSTRAINT_NONE;
            level = 0;
        }

        int res = SetPowerConstraint(type, level);
        if (res == 0) {
            Toast.makeText(this, R.string.pwr_constraint_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.pwr_constraint_failed, Toast.LENGTH_SHORT).show();
        }
        updatePowerLevel();
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
