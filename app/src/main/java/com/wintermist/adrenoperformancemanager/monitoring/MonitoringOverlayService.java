package com.wintermist.adrenoperformancemanager.monitoring;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MonitoringOverlayService extends Service {

    private WindowManager windowManager;
    private LinearLayout overlayView;
    private TextView metricsTextView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<GpuMonitor.MetricsSnapshot> sessionSnapshots = new ArrayList<>();

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            GpuMonitor.MetricsSnapshot snapshot = GpuMonitor.captureSnapshot(null, null);
            sessionSnapshots.add(snapshot);

            if (metricsTextView != null) {
                String text = String.format(Locale.US,
                        "GPU Temp: %.1f °C\nGPU Freq: %d MHz\nBusy: %.1f%%",
                        snapshot.temperatureC > 0 ? snapshot.temperatureC : 0,
                        snapshot.frequencyMhz > 0 ? snapshot.frequencyMhz : 0,
                        snapshot.busyPercent >= 0 ? snapshot.busyPercent : 0);
                metricsTextView.setText(text);
            }

            handler.postDelayed(this, 1500L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new LinearLayout(this);
        overlayView.setOrientation(LinearLayout.VERTICAL);
        overlayView.setBackgroundColor(Color.argb(180, 0, 0, 0));
        overlayView.setPadding(20, 20, 20, 20);

        metricsTextView = new TextView(this);
        metricsTextView.setTextColor(Color.WHITE);
        metricsTextView.setTextSize(12f);
        overlayView.addView(metricsTextView);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 100;

        try {
            windowManager.addView(overlayView, params);
            handler.post(updateRunnable);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(updateRunnable);
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public List<GpuMonitor.MetricsSnapshot> getSessionSnapshots() {
        return sessionSnapshots;
    }
}
