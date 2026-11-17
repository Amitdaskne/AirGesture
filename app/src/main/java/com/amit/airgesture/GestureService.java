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
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer;
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GestureService extends LifecycleService {

    private GestureRecognizer gestureRecognizer;
    private ExecutorService cameraExecutor;
    private WindowManager windowManager;
    private View overlayView;
    private PreviewView previewView;
    private TextView overlayStatus;
    
    private long lastActionTime = 0;
    private static final long COOLDOWN_MS = 1500; 

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

        setupFloatingWindow();

        BaseOptions baseOptions = BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build();
        GestureRecognizer.GestureRecognizerOptions options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setResultListener(this::onGestureResult)
                .build();
        gestureRecognizer = GestureRecognizer.createFromOptions(this, options);

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void setupFloatingWindow() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_camera, null);
        previewView = overlayView.findViewById(R.id.overlayPreview);
        overlayStatus = overlayView.findViewById(R.id.overlayStatus);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20; 
        params.y = 100; 

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
                    if (gestureRecognizer != null) {
                        android.graphics.Bitmap bitmap = imageProxy.toBitmap();
                        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                        gestureRecognizer.recognizeAsync(mpImage, imageProxy.getImageInfo().getTimestamp());
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

    private void onGestureResult(GestureRecognizerResult result, MPImage input) {
        // 🔥 FIX: Service me UI update ke liye Handler use kiya 🔥
        new Handler(Looper.getMainLooper()).post(() -> {
            if (result.gestures().isEmpty()) {
                overlayStatus.setText("Show Hand");
                return;
            }

            String gestureName = result.gestures().get(0).get(0).categoryName();
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastActionTime < COOLDOWN_MS) return; 

            SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
            if (service == null) {
                overlayStatus.setText("Accessibility OFF");
                return;
            }

            switch (gestureName) {
                case "Pointing_Up":
                    overlayStatus.setText("Scroll Up! ☝️");
                    service.performSwipe(true);
                    lastActionTime = currentTime;
                    break;
                case "Thumb_Down":
                    overlayStatus.setText("Scroll Down! 👎");
                    service.performSwipe(false);
                    lastActionTime = currentTime;
                    break;
                case "Closed_Fist":
                    overlayStatus.setText("Play/Pause ✊");
                    service.performPlayPause();
                    lastActionTime = currentTime;
                    break;
                case "Open_Palm":
                    overlayStatus.setText("Hand Open ✋");
                    break;
                default:
                    overlayStatus.setText("Gesture: " + gestureName);
                    break;
            }
        });
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
                .setContentText("Floating camera is running.")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (gestureRecognizer != null) gestureRecognizer.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (windowManager != null && overlayView != null) windowManager.removeView(overlayView);
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return null;
    }
}
