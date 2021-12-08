/*
 *******************************************************************************
 *
 * Copyright (C) 2020 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.smartconfig.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;

import com.diasemi.smartconfig.R;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIME = 3000;
    private static final int DELAY_AFTER_RESUME = 500;
    private Handler handler = new Handler();
    private boolean dismissed;
    private boolean paused;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash);
        // Dismiss timer
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissSplashScreen();
            }
        }, SPLASH_TIME);
        // Dismiss on click
        findViewById(R.id.splash_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissSplashScreen();
            }
        });
    }

    @Override
    protected void onPause() {
        paused = true;
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (paused) {
            paused = false;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismissSplashScreen();
                }
            }, DELAY_AFTER_RESUME);
        }
    }

    public void dismissSplashScreen() {
        if (dismissed)
            return;
        dismissed = true;
        finish();
        Intent intent = new Intent(this, ScanActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
}
