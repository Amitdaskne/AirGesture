package com.amit.airgesture;

import android.Manifest;
import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        TextView tvStatus = findViewById(R.id.tvStatus);

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
                tvStatus.setText("Status: ACTIVE");
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
            }
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, GestureService.class));
            btnStart.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.GONE);
            tvStatus.setText("Status: Idle");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_pink));
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
