package com.amit.airgesture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
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
    private long lastSwipeTime = 0;
    private static final long SWIPE_COOLDOWN_MS = 1500; // 1.5 seconds cooldown

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

        // Initialize MediaPipe AI
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
                        // ImageProxy to Bitmap conversion
                        android.graphics.Bitmap bitmap = imageProxy.toBitmap();
                        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                        handLandmarker.detectAsync(mpImage, imageProxy.getImageInfo().getTimestamp());
                    }
                    imageProxy.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                // Bind to Lifecycle (Because we extend LifecycleService, 'this' is a LifecycleOwner)
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onHandResult(HandLandmarkerResult result, MPImage input) {
        if (result.landmarks().isEmpty()) return;

        // 🔥 FIX: 'var' ko List<NormalizedLandmark> me change kiya 🔥
        List<NormalizedLandmark> landmarks = result.landmarks().get(0);
        
        // Index Finger Tip (8) and Middle Finger Tip (12)
        float indexY = landmarks.get(8).y();
        float middleY = landmarks.get(12).y();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSwipeTime < SWIPE_COOLDOWN_MS) return;

        // Agar 2 ungliyan screen ke top 30% area me hain, toh Swipe Up
        if (indexY < 0.3f && middleY < 0.3f) {
            SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
            if (service != null) {
                service.performSwipe(true); // Swipe Up
                lastSwipeTime = currentTime;
            }
        } 
        // Agar niche 30% area me hain, toh Swipe Down
        else if (indexY > 0.7f && middleY > 0.7f) {
            SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
            if (service != null) {
                service.performSwipe(false); // Swipe Down
                lastSwipeTime = currentTime;
            }
        }
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("gesture_channel", "Air Gesture Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, "gesture_channel")
                .setContentTitle("Air Gesture Active")
                .setContentText("Camera is monitoring for swipe gestures.")
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
