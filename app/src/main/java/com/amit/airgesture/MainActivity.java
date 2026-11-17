package com.amit.airgesture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        TextView tvLiveStatus = findViewById(R.id.tvLiveStatus);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnAccessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Enable Air Gesture Service", Toast.LENGTH_LONG).show();
        });

        btnStart.setOnClickListener(v -> {
            // 1. Check Overlay Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 2. Check Camera Permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS}, 100);
                return;
            }

            startGestureService();
            btnStart.setVisibility(View.GONE);
            btnStop.setVisibility(View.VISIBLE);
            tvLiveStatus.setText("Status: ACTIVE (Check floating camera)");
            tvLiveStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, GestureService.class));
            btnStart.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.GONE);
            tvLiveStatus.setText("Status: Idle");
            tvLiveStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black));
        });
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
}
