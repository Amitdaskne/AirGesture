package com.amit.airgesture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GestureService extends LifecycleService {

    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor;
    private WindowManager windowManager;
    private View overlayView;
    private PreviewView previewView;
    private TextView overlayStatus;
    
    private WindowManager.LayoutParams params;
    private long lastActionTime = 0;
    private static final long COOLDOWN_MS = 1500; // 1.5 sec cooldown

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

        setupFloatingWindow();

        // Setup HandLandmarker AI
        BaseOptions baseOptions = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build();
        HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setResultListener(this::onHandResult)
                .build();
        handLandmarker = HandLandmarker.createFromOptions(this, options);

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void setupFloatingWindow() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_camera, null);
        previewView = overlayView.findViewById(R.id.overlayPreview);
        overlayStatus = overlayView.findViewById(R.id.overlayStatus);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20; 
        params.y = 100; 

        // 🔥 DRAG LOGIC (Floating window move karne ke liye) 🔥
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - initialTouchX) > 10 || Math.abs(event.getRawY() - initialTouchY) > 10) {
                            isDragging = true;
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(overlayView, params);
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, params);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (handLandmarker != null) {
                        android.graphics.Bitmap bitmap = imageProxy.toBitmap();
                        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                        handLandmarker.detectAsync(mpImage, imageProxy.getImageInfo().getTimestamp());
                    }
                    imageProxy.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onHandResult(HandLandmarkerResult result, MPImage input) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (result.landmarks().isEmpty()) {
                overlayStatus.setText("Show Hand");
                return;
            }

            List<NormalizedLandmark> landmarks = result.landmarks().get(0);
            int fingerCount = countExtendedFingers(landmarks);
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastActionTime < COOLDOWN_MS) return;

            SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
            if (service == null) {
                overlayStatus.setText("Accessibility OFF");
                return;
            }

            // 🔥 NEW PERFECT LOGIC 🔥
            if (fingerCount == 1) {
                // 1 Finger: Scroll Down (Next Video/Post)
                overlayStatus.setText("1 Finger: Scroll Down ⬇️");
                service.performSwipe(true); // Swipe Up gesture = Scroll down content
                lastActionTime = currentTime;
            } else if (fingerCount == 2) {
                // 2 Fingers: Scroll Up (Previous Video/Post)
                overlayStatus.setText("2 Fingers: Scroll Up ⬆️");
                service.performSwipe(false); // Swipe Down gesture = Scroll up content
                lastActionTime = currentTime;
            } else if (fingerCount == 3) {
                // 3 Fingers: Play/Pause
                overlayStatus.setText("3 Fingers: Play/Pause ✊");
                service.performPlayPause();
                lastActionTime = currentTime;
            } else {
                overlayStatus.setText(fingerCount + " Fingers detected");
            }
        });
    }

    // Accurate Finger Counting Logic (Checks if fingertip is higher than PIP joint)
    private int countExtendedFingers(List<NormalizedLandmark> landmarks) {
        int count = 0;
        // Index (8 vs 6), Middle (12 vs 10), Ring (16 vs 14)
        if (landmarks.get(8).y() < landmarks.get(6).y()) count++;
        if (landmarks.get(12).y() < landmarks.get(10).y()) count++;
        if (landmarks.get(16).y() < landmarks.get(14).y()) count++;
        return count;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("gesture_channel", "Air Gesture", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, "gesture_channel")
                .setContentTitle("Air Gesture Active")
                .setContentText("1 Finger: Down | 2 Fingers: Up | 3 Fingers: Pause")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handLandmarker != null) handLandmarker.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (windowManager != null && overlayView != null) windowManager.removeView(overlayView);
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return null;
    }
}
