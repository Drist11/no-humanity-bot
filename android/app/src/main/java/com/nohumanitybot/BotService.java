package com.nohumanitybot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;

public class BotService extends Service {
    private static final String TAG = "BotService";
    private static final String CHANNEL_ID = "BotChannel";
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Bitmap shipTemplate;
    private int screenWidth;
    private int screenHeight;
    private boolean botEnabled = false;
    private WindowManager windowManager;
    private View floatView;

    public static BotAccessibility accessibility;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("NoHumanity Bot")
                .setContentText("Бот активен")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
        startForeground(1, notification);

        shipTemplate = BitmapFactory.decodeResource(getResources(), R.drawable.ship);

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, data);

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "BotDisplay", screenWidth, screenHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        showFloatButton();
        startBotLoop();
        return START_STICKY;
    }

    private void showFloatButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatView = new FrameLayout(this);
        TextView btn = new TextView(this);
        btn.setText("BOT\nOFF");
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.argb(200, 255, 0, 0));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(20, 20, 20, 20);
        btn.setTextSize(12);

        btn.setOnClickListener(v -> {
            botEnabled = !botEnabled;
            btn.setText(botEnabled ? "BOT\nON" : "BOT\nOFF");
            btn.setBackgroundColor(botEnabled ?
                Color.argb(200, 0, 200, 0) :
                Color.argb(200, 255, 0, 0));
            Log.d(TAG, "Bot enabled: " + botEnabled);
        });

        ((FrameLayout) floatView).addView(btn);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                150, 150,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = 10;
        params.y = 300;

        floatView.setOnTouchListener(new View.OnTouchListener() {
            int lastX, lastY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x -= (int) event.getRawX() - lastX;
                        params.y += (int) event.getRawY() - lastY;
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        windowManager.updateViewLayout(floatView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatView, params);
    }

    private void startBotLoop() {
        new Thread(() -> {
            while (true) {
                try {
                    if (botEnabled) {
                        Image image = imageReader.acquireLatestImage();
                        if (image != null) {
                            Bitmap bitmap = imageToBitmap(image);
                            image.close();
                            processFrame(bitmap);
                        }
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    Log.e(TAG, "Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        Bitmap bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private int[] findShip(Bitmap screen) {
        int tw = shipTemplate.getWidth();
        int th = shipTemplate.getHeight();
        int sw = screen.getWidth();
        int sh = screen.getHeight();

        double bestScore = Double.MAX_VALUE;
        int bestX = sw / 2, bestY = sh / 2;

        // Ищем по всему экрану
        for (int y = 0; y < sh - th; y += 3) {
            for (int x = 0; x < sw - tw; x += 3) {
                double score = matchScore(screen, x, y, tw, th);
                if (score < bestScore) {
                    bestScore = score;
                    bestX = x + tw / 2;
                    bestY = y + th / 2;
                }
            }
        }

        Log.d(TAG, "Ship at: " + bestX + "," + bestY + " score: " + bestScore);
        return new int[]{bestX, bestY};
    }

    private double matchScore(Bitmap screen, int startX, int startY, int tw, int th) {
        double diff = 0;
        int samples = 0;
        for (int y = 0; y < th; y += 4) {
            for (int x = 0; x < tw; x += 4) {
                int tp = shipTemplate.getPixel(x, y);
                int sp = screen.getPixel(startX + x, startY + y);
                int dr = ((tp >> 16) & 0xFF) - ((sp >> 16) & 0xFF);
                int dg = ((tp >> 8) & 0xFF) - ((sp >> 8) & 0xFF);
                int db = (tp & 0xFF) - (sp & 0xFF);
                diff += dr*dr + dg*dg + db*db;
                samples++;
            }
        }
        return diff / samples;
    }

    private void processFrame(Bitmap bitmap) {
        int[] shipPos = findShip(bitmap);
        int shipX = shipPos[0];
        int shipY = shipPos[1];

        Log.d(TAG, "Accessibility: " + (accessibility != null));

        int nearestBulletX = -1, nearestBulletY = -1;
        double minDist = Double.MAX_VALUE;

        int searchR = 250;
        int x0 = Math.max(0, shipX - searchR);
        int x1 = Math.min(bitmap.getWidth(), shipX + searchR);
        int y0 = Math.max(0, shipY - searchR);
        int y1 = Math.min(bitmap.getHeight(), shipY + searchR);

        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (r < 40 && g < 40 && b < 40) {
                    double dist = Math.sqrt(Math.pow(x - shipX, 2) + Math.pow(y - shipY, 2));
                    if (dist < minDist && dist > 20) {
                        minDist = dist;
                        nearestBulletX = x;
                        nearestBulletY = y;
                    }
                }
            }
        }

        if (nearestBulletX != -1 && accessibility != null && minDist < 200) {
            int dx = shipX - nearestBulletX;
            int dy = shipY - nearestBulletY;
            int newX = Math.max(50, Math.min(screenWidth - 50, shipX + (dx > 0 ? 100 : -100)));
            int newY = Math.max(50, Math.min(screenHeight - 50, shipY + (dy > 0 ? 100 : -100)));
            accessibility.tap(newX, newY);
            Log.d(TAG, "Tapping to: " + newX + "," + newY);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Bot Channel", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (floatView != null) windowManager.removeView(floatView);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
            }
