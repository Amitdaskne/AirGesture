package com.amit.airgesture;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private TextView tvLiveStatus;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean handDetected = intent.getBooleanExtra("hand_detected", false);
            if (handDetected) {
                tvLiveStatus.setText("HAND DETECTED - Ready to Control");
                tvLiveStatus.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_dark));
            } else {
                tvLiveStatus.setText("NO HAND DETECTED - Show your hand");
                tvLiveStatus.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_red_dark));
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnAccessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Enable Air Gesture Service", Toast.LENGTH_LONG).show();
        });

        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS}, 100);
            } else {
                startGestureService();
                btnStart.setVisibility(View.GONE);
                btnStop.setVisibility(View.VISIBLE);
                tvLiveStatus.setText("Service Active - Waiting for Hand...");
                tvLiveStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            }
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, GestureService.class));
            btnStart.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.GONE);
            tvLiveStatus.setText("Status: Service Stopped");
            tvLiveStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black));
        });

        // Register Receiver for Live Status
        IntentFilter filter = new IntentFilter(GestureService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    private void startGestureService() {
        Intent intent = new Intent(this, GestureService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGestureService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(statusReceiver);
    }
}
