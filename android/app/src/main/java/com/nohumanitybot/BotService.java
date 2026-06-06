package com.nohumanitybot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.Log;

import java.nio.ByteBuffer;

public class BotService extends Service {
    private static final String TAG = "BotService";
    private static final String CHANNEL_ID = "BotChannel";
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private static final int WIDTH = 486;
    private static final int HEIGHT = 1107;

    public static BotAccessibility accessibility;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("NoHumanity Bot")
                .setContentText("Бот работает...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
        startForeground(1, notification);

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, data);

        imageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "BotDisplay", WIDTH, HEIGHT, 300,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        startBotLoop();
        return START_STICKY;
    }

    private void startBotLoop() {
        new Thread(() -> {
            while (true) {
                try {
                    Image image = imageReader.acquireLatestImage();
                    if (image != null) {
                        Bitmap bitmap = imageToBitmap(image);
                        image.close();
                        processFrame(bitmap);
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
        int rowPadding = rowStride - pixelStride * WIDTH;
        Bitmap bitmap = Bitmap.createBitmap(
                WIDTH + rowPadding / pixelStride, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private void processFrame(Bitmap bitmap) {
        // Найти корабль (белый пиксель в нижней части)
        int shipX = WIDTH / 2, shipY = HEIGHT * 3 / 4;
        for (int y = HEIGHT / 2; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (r > 200 && g > 200 && b > 200) {
                    shipX = x;
                    shipY = y;
                    break;
                }
            }
        }

        // Найти ближайшую пулю (чёрный пиксель)
        int nearestBulletX = -1, nearestBulletY = -1;
        double minDist = Double.MAX_VALUE;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (r < 50 && g < 50 && b < 50) {
                    double dist = Math.sqrt(Math.pow(x - shipX, 2) + Math.pow(y - shipY, 2));
                    if (dist < minDist && dist < 200) {
                        minDist = dist;
                        nearestBulletX = x;
                        nearestBulletY = y;
                    }
                }
            }
        }

        // Двигаться от пули
        if (nearestBulletX != -1 && accessibility != null) {
            int dx = shipX - nearestBulletX;
            int dy = shipY - nearestBulletY;
            int newX = Math.max(50, Math.min(WIDTH - 50, shipX + (dx > 0 ? 80 : -80)));
            int newY = Math.max(50, Math.min(HEIGHT - 50, shipY + (dy > 0 ? 80 : -80)));
            accessibility.tap(newX, newY);
            Log.d(TAG, "Tapping: " + newX + ", " + newY);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Bot Channel", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
                      }
