package com.nohumanitybot;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 1000;
    private static final int OVERLAY_CODE = 2000;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setText("NoHumanity Bot");
        tv.setTextSize(22);
        tv.setPadding(40, 60, 40, 20);

        TextView tv2 = new TextView(this);
        tv2.setText("1. Нажми 'Разрешения' и разреши отображение поверх других приложений\n2. Нажми 'Запустить бота'\n3. Разреши запись экрана");
        tv2.setTextSize(15);
        tv2.setPadding(40, 20, 40, 20);

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
                tv2.setText("Сначала разреши отображение поверх других приложений!");
                return;
            }
            projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        });

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(tv);
        layout.addView(tv2);
        layout.addView(btnOverlay);
        layout.addView(btnStart);
        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, BotService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            startForegroundService(serviceIntent);
            finish();
        }
    }
            }
