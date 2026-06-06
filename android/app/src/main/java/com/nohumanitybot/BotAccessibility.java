package com.nohumanitybot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class BotAccessibility extends AccessibilityService {

    @Override
    public void onServiceConnected() {
        BotService.accessibility = this;
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(this, "Бот подключён!", Toast.LENGTH_LONG).show()
        );
    }

    public void swipe(int fromX, int fromY, int toX, int toY) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 200));
        dispatchGesture(builder.build(), null, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
