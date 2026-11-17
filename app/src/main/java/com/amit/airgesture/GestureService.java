package com.amit.airgesture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
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
    public static final String ACTION_STATUS = "com.amit.airgesture.STATUS";
    
    // Motion Tracking Variables
    private float lastHandY = -1f;
    private long lastSwipeTime = 0;
    private long lastFistTime = 0;
    private static final long COOLDOWN_MS = 1200; 

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

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

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
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
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onHandResult(HandLandmarkerResult result, MPImage input) {
        // Broadcast Live Status to MainActivity
        Intent statusIntent = new Intent(ACTION_STATUS);
        
        if (result.landmarks().isEmpty()) {
            lastHandY = -1f;
            statusIntent.putExtra("hand_detected", false);
            sendBroadcast(statusIntent);
            return;
        }

        statusIntent.putExtra("hand_detected", true);
        sendBroadcast(statusIntent);

        List<NormalizedLandmark> landmarks = result.landmarks().get(0);
        
        // 1. Fist Detection (Mutthi)
        boolean isFist = checkFist(landmarks);
        long currentTime = System.currentTimeMillis();

        if (isFist) {
            if (currentTime - lastFistTime > COOLDOWN_MS) {
                SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
                if (service != null) {
                    service.performPlayPause();
                    lastFistTime = currentTime;
                }
            }
            lastHandY = -1f; // Reset swipe tracking
            return;
        }

        // 2. Wave Detection (Track Wrist Y position)
        // Landmark 0 is the Wrist
        float currentHandY = landmarks.get(0).y(); 

        if (lastHandY != -1f) {
            float deltaY = lastHandY - currentHandY; // Positive = Moved UP, Negative = Moved DOWN

            if (currentTime - lastSwipeTime > COOLDOWN_MS) {
                if (deltaY > 0.15f) { // Moved UP rapidly
                    SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
                    if (service != null) {
                        service.performSwipe(true); // Swipe Up
                        lastSwipeTime = currentTime;
                    }
                } else if (deltaY < -0.15f) { // Moved DOWN rapidly
                    SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
                    if (service != null) {
                        service.performSwipe(false); // Swipe Down
                        lastSwipeTime = currentTime;
                    }
                }
            }
        }
        
        // Smooth tracking
        lastHandY = currentHandY;
    }

    // Math to check if hand is closed in a fist
    private boolean checkFist(List<NormalizedLandmark> landmarks) {
        // Wrist (0), Index Tip (8), Middle Tip (12), Ring Tip (16), Pinky Tip (20)
        // If fingertips are closer to wrist than the middle joints, it's a fist.
        
        float wristY = landmarks.get(0).y();
        
        // Check Y coordinates (Y goes down in Android, so Tip Y > Joint Y means folded)
        boolean indexFolded = landmarks.get(8).y() > landmarks.get(6).y();
        boolean middleFolded = landmarks.get(12).y() > landmarks.get(10).y();
        boolean ringFolded = landmarks.get(16).y() > landmarks.get(14).y();
        boolean pinkyFolded = landmarks.get(20).y() > landmarks.get(18).y();

        return indexFolded && middleFolded && ringFolded && pinkyFolded;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("gesture_channel", "Air Gesture Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, "gesture_channel")
                .setContentTitle("Air Gesture Active")
                .setContentText("Camera is monitoring for gestures.")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handLandmarker != null) handLandmarker.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return null;
    }
}
