package com.nohumanitybot;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1000;
    private static final int OVERLAY_CODE = 2000;
    private MediaProjectionManager projectionManager;
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tv = new TextView(this);
        tv.setText("NoHumanity Bot\n\n1. Нажми Разрешения\n2. Запусти бота\n3. Разреши запись экрана");
        tv.setTextSize(16);
        tv.setPadding(40, 60, 40, 20);

        Button btnOverlay = new Button(this);
        btnOverlay.setText("1. Разрешения");
        btnOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_CODE);
        });

        Button btnStart = new Button(this);
        btnStart.setText("2. Запустить бота");
        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                tv.setText("Сначала разреши отображение поверх других приложений!");
                return;
            }
            try {
                projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                if (projectionManager != null) {
                    startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
                } else {
                    tv.setText("Ошибка: не удалось получить MediaProjectionManager!");
                }
            } catch (Exception e) {
                tv.setText("Ошибка: " + e.getMessage());
                Log.e(TAG, "Error: " + e.getMessage());
            }
        });

        Button btnTest = new Button(this);
        btnTest.setText("ТЕСТ СВАЙПА");
        btnTest.setOnClickListener(v -> {
            if (BotService.accessibility != null) {
                BotService.accessibility.swipe(300, 800, 300, 400);
                tv.setText("Свайп отправлен!");
            } else {
                tv.setText("Accessibility не подключён!");
            }
        });

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(tv);
        layout.addView(btnOverlay);
        layout.addView(btnStart);
        layout.addView(btnTest);
        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                Intent serviceIntent = new Intent(this, BotService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                startForegroundService(serviceIntent);
                finish();
            } catch (Exception e) {
                tv.setText("Ошибка запуска сервиса: " + e.getMessage());
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }
    }
            }
