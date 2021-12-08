/*
 *******************************************************************************
 *
 * Copyright (C) 2020 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.smartconfig.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.diasemi.smartconfig.R;

/**
 * Drawable for signal bars
 */
public class SignalBar extends View {
    private static final int[] levels = new int[] { -95, -80, -70, -60, -40 };
    private Paint paint;
    private int bars;
    private RectF[] barRects;

    public SignalBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        setWillNotDraw(false);
        invalidate();
    }

    public void setRssi(int rssi) {
        int prevBars = bars;
        bars = 0;
        for (int level : levels) {
            if (rssi >= level)
                ++bars;
        }
        if (bars != prevBars)
            invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float width = w / 5.f, height = h / 5.f;
        barRects = new RectF[] {
            new RectF(4, height * 4 + 4, width - 4, h),
            new RectF(width + 4, height * 3 + 4, width * 2 - 4, h),
            new RectF(width * 2 + 4, height * 2 + 4, width * 3 - 4, h),
            new RectF(width * 3 + 4, height + 4, width * 4 - 4, h),
            new RectF(width * 4 + 4, 4, width * 5 - 4, h),
        };
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int color = getResources().getColor(R.color.signal_bar), nonactive = getResources().getColor(R.color.signal_bar_non_active);
        int drawBars = bars;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.TRANSPARENT);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        for (int i = 0; i < 5; ++i) {
            paint.setColor(--drawBars >= 0 ? color : nonactive);
            canvas.drawRect(barRects[i], paint);
        }
    }
}
