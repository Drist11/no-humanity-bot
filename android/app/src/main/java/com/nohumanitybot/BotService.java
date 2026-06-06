package com.nohumanitybot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import java.util.ArrayList;
import java.util.List;

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
    private View floatButton;
    private DebugOverlay debugOverlay;

    private List<int[]> prevBullets = new ArrayList<>();
    private long prevFrameTime = 0;

    private int shipX = 0, shipY = 0;
    private List<int[]> currentBulletsForDraw = new ArrayList<>();
    private String lastError = "Нет ошибок";
    private String debugInfo = "Запуск...";

    public static BotAccessibility accessibility;

    class DebugOverlay extends View {
        Paint shipPaint = new Paint();
        Paint bulletPaint = new Paint();
        Paint bgPaint = new Paint();
        Paint textPaint = new Paint();
        Paint errorPaint = new Paint();

        public DebugOverlay(Context context) {
            super(context);
            setWillNotDraw(false);
            shipPaint.setColor(Color.GREEN);
            shipPaint.setStyle(Paint.Style.STROKE);
            shipPaint.setStrokeWidth(6);
            bulletPaint.setColor(Color.RED);
            bulletPaint.setStyle(Paint.Style.STROKE);
            bulletPaint.setStrokeWidth(4);
            bgPaint.setColor(Color.argb(1, 0, 0, 0));
            textPaint.setColor(Color.YELLOW);
            textPaint.setTextSize(28);
            textPaint.setAntiAlias(true);
            errorPaint.setColor(Color.RED);
            errorPaint.setTextSize(28);
            errorPaint.setAntiAlias(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

            // Тест квадрат — всегда виден
            canvas.drawRect(50, 50, 150, 150, shipPaint);

            // Зелёный квадрат вокруг корабля
            if (shipX > 0 && shipY > 0) {
                canvas.drawRect(shipX-25, shipY-25, shipX+25, shipY+25, shipPaint);
            }

            // Красные квадраты вокруг пуль
            for (int[] bullet : currentBulletsForDraw) {
                canvas.drawRect(bullet[0]-12, bullet[1]-12,
                               bullet[0]+12, bullet[1]+12, bulletPaint);
            }

            // Отладочная информация
            canvas.drawText(debugInfo, 10, getHeight() - 80, textPaint);
            canvas.drawText("Ошибка: " + lastError, 10, getHeight() - 40, errorPaint);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            createNotificationChannel();
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("NoHumanity Bot")
                    .setContentText("Бот активен")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
            startForeground(1, notification);

            shipTemplate = BitmapFactory.decodeResource(getResources(), R.drawable.ship);
            if (shipTemplate == null) {
                lastError = "ship.png не найден!";
            }

            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            debugInfo = "Экран: " + screenWidth + "x" + screenHeight;

            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");

            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = manager.getMediaProjection(resultCode, data);

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "BotDisplay", screenWidth, screenHeight, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);

            showDebugOverlay();
            showFloatButton();
            startBotLoop();

        } catch (Exception e) {
            lastError = "onStartCommand: " + e.getMessage();
            Log.e(TAG, lastError);
        }
        return START_STICKY;
    }

    private void showDebugOverlay() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            debugOverlay = new DebugOverlay(this);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    screenWidth, screenHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 0;
            params.y = 0;

            windowManager.addView(debugOverlay, params);
            debugOverlay.postInvalidate();
        } catch (Exception e) {
            lastError = "showDebugOverlay: " + e.getMessage();
            Log.e(TAG, lastError);
        }
    }

    private void showFloatButton() {
        try {
            floatButton = new FrameLayout(this);
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
            });

            ((FrameLayout) floatButton).addView(btn);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    150, 150,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.RIGHT;
            params.x = 10;
            params.y = 300;

            floatButton.setOnTouchListener(new View.OnTouchListener() {
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
                            windowManager.updateViewLayout(floatButton, params);
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(floatButton, params);
        } catch (Exception e) {
            lastError = "showFloatButton: " + e.getMessage();
            Log.e(TAG, lastError);
        }
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
                    lastError = "botLoop: " + e.getMessage();
                    Log.e(TAG, lastError);
                    if (debugOverlay != null) debugOverlay.postInvalidate();
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

    private List<int[]> findBullets(Bitmap bitmap, int shipX, int shipY) {
        List<int[]> bullets = new ArrayList<>();
        int searchR = 300;
        int x0 = Math.max(0, shipX - searchR);
        int x1 = Math.min(bitmap.getWidth(), shipX + searchR);
        int y0 = Math.max(0, shipY - searchR);
        int y1 = Math.min(bitmap.getHeight(), shipY + searchR);

        for (int y = y0; y < y1; y += 4) {
            for (int x = x0; x < x1; x += 4) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (r < 40 && g < 40 && b < 40) {
                    double distToShip = Math.sqrt(Math.pow(x - shipX, 2) + Math.pow(y - shipY, 2));
                    if (distToShip > 30) {
                        bullets.add(new int[]{x, y});
                    }
                }
            }
        }
        return bullets;
    }

    private int[] findClosestPrev(int[] bullet, List<int[]> prevList) {
        int[] closest = null;
        double minDist = 60;
        for (int[] prev : prevList) {
            double d = Math.sqrt(Math.pow(bullet[0]-prev[0],2) + Math.pow(bullet[1]-prev[1],2));
            if (d < minDist) {
                minDist = d;
                closest = prev;
            }
        }
        return closest;
    }

    private void processFrame(Bitmap bitmap) {
        try {
            long now = System.currentTimeMillis();
            long deltaTime = prevFrameTime == 0 ? 100 : now - prevFrameTime;

            int[] shipPos = findShip(bitmap);
            shipX = shipPos[0];
            shipY = shipPos[1];

            List<int[]> currentBullets = findBullets(bitmap, shipX, shipY);
            currentBulletsForDraw = currentBullets;

            debugInfo = "Корабль:" + shipX + "," + shipY + " Пули:" + currentBullets.size() + " Acc:" + (accessibility != null);

            debugOverlay.postInvalidate();

            if (botEnabled && accessibility != null) {
                double dangerX = 0, dangerY = 0;
                double totalDanger = 0;

                for (int[] bullet : currentBullets) {
                    int[] prev = findClosestPrev(bullet, prevBullets);
                    int[] predicted = bullet;
                    if (prev != null) {
                        float vx = (float)(bullet[0] - prev[0]) / deltaTime;
                        float vy = (float)(bullet[1] - prev[1]) / deltaTime;
                        predicted = new int[]{
                            (int)(bullet[0] + vx * 300),
                            (int)(bullet[1] + vy * 300)
                        };
                    }

                    double dist = Math.sqrt(Math.pow(predicted[0] - shipX, 2) + Math.pow(predicted[1] - shipY, 2));
                    if (dist < 200) {
                        double weight = 1.0 / (dist + 1);
                        dangerX += predicted[0] * weight;
                        dangerY += predicted[1] * weight;
                        totalDanger += weight;
                    }
                }

                if (totalDanger > 0) {
                    dangerX /= totalDanger;
                    dangerY /= totalDanger;
                    double dx = shipX - dangerX;
                    double dy = shipY - dangerY;
                    double len = Math.sqrt(dx*dx + dy*dy);
                    if (len > 0) { dx /= len; dy /= len; }

                    int toX = (int) Math.max(50, Math.min(screenWidth-50, shipX + dx * 120));
                    int toY = (int) Math.max(50, Math.min(screenHeight-50, shipY + dy * 120));
                    accessibility.swipe(shipX, shipY, toX, toY);
                }
            }

            prevBullets = currentBullets;
            prevFrameTime = now;

        } catch (Exception e) {
            lastError = "processFrame: " + e.getMessage();
            Log.e(TAG, lastError);
            if (debugOverlay != null) debugOverlay.postInvalidate();
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Bot Channel", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (floatButton != null) windowManager.removeView(floatButton);
        if (debugOverlay != null) windowManager.removeView(debugOverlay);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
                }
