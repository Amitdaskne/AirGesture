package com.amit.airgesture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

public class SwipeAccessibilityService extends AccessibilityService {
    private static SwipeAccessibilityService instance;

    public static SwipeAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public void performSwipe(boolean isUp) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        Path swipePath = new Path();
        int startY = isUp ? (int) (screenHeight * 0.7) : (int) (screenHeight * 0.3);
        int endY = isUp ? (int) (screenHeight * 0.3) : (int) (screenHeight * 0.7);
        int x = screenWidth / 2;

        swipePath.moveTo(x, startY);
        swipePath.lineTo(x, endY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 300));
        dispatchGesture(gestureBuilder.build(), null, null);
    }
}
